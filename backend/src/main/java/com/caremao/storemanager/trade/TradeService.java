package com.caremao.storemanager.trade;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.caremao.storemanager.auth.Actor;
import com.caremao.storemanager.common.ApiException;
import com.caremao.storemanager.events.CampaignEvent;
import com.caremao.storemanager.history.HistoryAction;
import com.caremao.storemanager.history.HistoryService;
import com.caremao.storemanager.inventory.InsufficientFundsException;
import com.caremao.storemanager.inventory.Inventory;
import com.caremao.storemanager.inventory.InventoryRepository;
import com.caremao.storemanager.inventory.InventoryService;
import com.caremao.storemanager.inventory.Purse;
import com.caremao.storemanager.item.Item;
import com.caremao.storemanager.item.ItemRepository;
import com.caremao.storemanager.item.Pricing;
import com.caremao.storemanager.trade.TradeDtos.DirectTradeRequest;
import com.caremao.storemanager.trade.TradeDtos.TradeCreateRequest;
import com.caremao.storemanager.trade.TradeDtos.TradeDto;

/**
 * Compras y ventas entre personajes y tiendas. Toda ejecución pasa por {@link #execute}, que mueve
 * stock, monedas e historial en una sola transacción. Los {@code @Version} de item, inventario y
 * solicitud hacen que dos operaciones simultáneas sobre lo mismo terminen en 409 en vez de vender
 * dos veces la última unidad.
 */
@Service
public class TradeService {

    private final TradeRepository trades;
    private final InventoryRepository inventoryRepo;
    private final InventoryService inventories;
    private final ItemRepository items;
    private final HistoryService history;
    private final ApplicationEventPublisher events;

    public TradeService(TradeRepository trades, InventoryRepository inventoryRepo, InventoryService inventories,
            ItemRepository items, HistoryService history, ApplicationEventPublisher events) {
        this.trades = trades;
        this.inventoryRepo = inventoryRepo;
        this.inventories = inventories;
        this.items = items;
        this.history = history;
        this.events = events;
    }

    /** Un jugador pide comprar o vender. Si la tienda no exige aprobación y hay precio, se ejecuta ya. */
    @Transactional
    public TradeDto request(Actor actor, UUID shopId, TradeCreateRequest request) {
        if (actor.isGm()) {
            throw ApiException.badRequest("El GM registra las ventas directamente desde la tienda");
        }
        Inventory shop = requireShop(actor, shopId);
        Inventory character = inventoryRepo.findById(actor.characterId()).orElseThrow(ApiException::unauthorized);
        int quantity = request.quantity();
        Item item;
        Long unit;
        if (request.kind() == TradeKind.BUY) {
            item = items.findByIdAndInventoryId(request.itemId(), shop.getId())
                    .orElseThrow(() -> ApiException.notFound("Artículo"));
            requireStock(item, quantity);
            unit = Pricing.apply(item.getValueCp(), shop.getSellMarkupPct());
            if (unit != null && character.getPurse().totalCopper() < Math.multiplyExact(unit, quantity)) {
                throw new InsufficientFundsException(
                        "No te alcanza: cuesta " + Pricing.format(Math.multiplyExact(unit, quantity)));
            }
        } else {
            item = items.findByIdAndInventoryId(request.itemId(), character.getId())
                    .orElseThrow(() -> ApiException.notFound("Artículo"));
            requireOwned(item, quantity, "Solo tienes ");
            unit = Pricing.apply(item.getValueCp(), shop.getBuybackPct());
        }
        TradeRequest trade = trades.save(new TradeRequest(actor.campaignId(), shop.getId(), character.getId(),
                item.getId(), item.getName(), request.kind(), quantity, unit, blankToNull(request.note())));
        if (!shop.isRequiresApproval() && unit != null) {
            execute(actor, trade, shop, character, unit);
        }
        publish(trade);
        return toDto(trade, shop.getName(), character.getName());
    }

    @Transactional
    public TradeDto approve(Actor actor, UUID tradeId, Long unitPriceCp) {
        actor.requireGm();
        TradeRequest trade = find(actor, tradeId);
        trade.requirePending();
        Long unit = unitPriceCp != null ? unitPriceCp : trade.getUnitPriceCp();
        if (unit == null) {
            throw ApiException.badRequest("Este artículo no tiene precio: indícalo para aprobar");
        }
        Inventory shop = inventoryRepo.findById(trade.getShopId()).orElseThrow(() -> ApiException.notFound("Tienda"));
        Inventory character = inventoryRepo.findById(trade.getCharacterId())
                .orElseThrow(() -> ApiException.notFound("Personaje"));
        execute(actor, trade, shop, character, unit);
        publish(trade);
        return toDto(trade, shop.getName(), character.getName());
    }

    @Transactional
    public TradeDto reject(Actor actor, UUID tradeId, String note) {
        actor.requireGm();
        TradeRequest trade = find(actor, tradeId);
        trade.resolve(TradeStatus.REJECTED);
        if (note != null && !note.isBlank()) {
            trade.setNote(note);
        }
        publish(trade);
        return toDto(trade);
    }

    @Transactional
    public TradeDto cancel(Actor actor, UUID tradeId) {
        TradeRequest trade = find(actor, tradeId);
        if (!actor.isCharacter(trade.getCharacterId())) {
            throw ApiException.notFound("Solicitud");
        }
        trade.resolve(TradeStatus.CANCELLED);
        publish(trade);
        return toDto(trade);
    }

    /** El GM registra en la mesa una compra o venta ya acordada; se ejecuta en el acto. */
    @Transactional
    public TradeDto direct(Actor actor, UUID shopId, DirectTradeRequest request) {
        actor.requireGm();
        Inventory shop = requireShop(actor, shopId);
        Inventory character = inventories.requireReadable(actor, request.characterId());
        if (!character.isCharacter()) {
            throw ApiException.notFound("Personaje");
        }
        Item item = items.findByIdAndInventoryId(request.itemId(),
                request.kind() == TradeKind.BUY ? shop.getId() : character.getId())
                .orElseThrow(() -> ApiException.notFound("Artículo"));
        Long unit = request.unitPriceCp() != null
                ? request.unitPriceCp()
                : Pricing.apply(item.getValueCp(),
                        request.kind() == TradeKind.BUY ? shop.getSellMarkupPct() : shop.getBuybackPct());
        if (unit == null) {
            throw ApiException.badRequest("Este artículo no tiene precio: indícalo");
        }
        TradeRequest trade = trades.save(new TradeRequest(actor.campaignId(), shop.getId(), character.getId(),
                item.getId(), item.getName(), request.kind(), request.quantity(), unit, null));
        execute(actor, trade, shop, character, unit);
        publish(trade);
        return toDto(trade, shop.getName(), character.getName());
    }

    /** El GM ve las solicitudes de la campaña; un jugador, solo las suyas. */
    @Transactional(readOnly = true)
    public List<TradeDto> list(Actor actor, TradeStatus status) {
        List<TradeRequest> result;
        if (actor.isGm()) {
            result = status == null
                    ? trades.findTop100ByCampaignIdOrderByCreatedAtDesc(actor.campaignId())
                    : trades.findByCampaignIdAndStatusOrderByCreatedAtDesc(actor.campaignId(), status);
        } else {
            result = trades.findTop100ByCharacterIdOrderByCreatedAtDesc(actor.characterId()).stream()
                    .filter(t -> status == null || t.getStatus() == status)
                    .toList();
        }
        Map<UUID, String> names = inventoryRepo.findByCampaignIdOrderByKindAscNameAsc(actor.campaignId()).stream()
                .collect(Collectors.toMap(Inventory::getId, Inventory::getName));
        return result.stream()
                .map(t -> toDto(t, names.get(t.getShopId()), names.get(t.getCharacterId())))
                .toList();
    }

    private void execute(Actor actor, TradeRequest trade, Inventory shop, Inventory character, long unit) {
        trade.requirePending();
        int quantity = trade.getQuantity();
        long total = Math.multiplyExact(unit, quantity);
        if (trade.getKind() == TradeKind.BUY) {
            Item stock = items.findByIdAndInventoryId(trade.getItemId(), shop.getId())
                    .orElseThrow(() -> ApiException.conflict("El artículo ya no está en la tienda"));
            requireStock(stock, quantity);
            pay(character.getPurse(), total,
                    character.getName() + " no tiene fondos suficientes (" + Pricing.format(total) + ")");
            if (!shop.isUnlimitedFunds()) {
                shop.getPurse().receive(total);
            }
            if (!stock.isUnlimitedStock()) {
                stock.setQuantity(stock.getQuantity() - quantity);
            }
            stackInto(character.getId(), stock, quantity);
        } else {
            Item owned = items.findByIdAndInventoryId(trade.getItemId(), character.getId())
                    .orElseThrow(() -> ApiException.conflict(character.getName() + " ya no tiene ese artículo"));
            requireOwned(owned, quantity, character.getName() + " solo tiene ");
            if (!shop.isUnlimitedFunds()) {
                pay(shop.getPurse(), total, shop.getName() + " no tiene fondos suficientes");
            }
            character.getPurse().receive(total);
            stackInto(shop.getId(), owned, quantity);
            owned.setQuantity(owned.getQuantity() - quantity);
            if (owned.getQuantity() == 0) {
                items.delete(owned);
            }
        }
        trade.approve(unit);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("kind", trade.getKind().name());
        details.put("quantity", quantity);
        details.put("unitPriceCp", unit);
        details.put("totalCp", total);
        details.put("characterName", character.getName());
        history.log(actor, HistoryAction.TRADE_EXECUTED, shop, character, trade.getItemName(), details);
    }

    /** Suma al item equivalente del destino (mismo SRD o mismo nombre) o crea una copia. */
    private void stackInto(UUID targetInventoryId, Item source, int quantity) {
        Item existing = (source.getSrdIndex() != null
                ? items.findFirstByInventoryIdAndSrdIndex(targetInventoryId, source.getSrdIndex())
                : Optional.<Item>empty())
                .or(() -> items.findFirstByInventoryIdAndNameIgnoreCase(targetInventoryId, source.getName()))
                .orElse(null);
        if (existing == null) {
            items.save(source.copyTo(targetInventoryId, quantity));
        } else if (!existing.isUnlimitedStock()) {
            existing.setQuantity(existing.getQuantity() + quantity);
        }
    }

    private Inventory requireShop(Actor actor, UUID shopId) {
        Inventory shop = inventories.requireReadable(actor, shopId);
        if (!shop.isShop()) {
            throw ApiException.notFound("Tienda");
        }
        return shop;
    }

    private TradeRequest find(Actor actor, UUID tradeId) {
        return trades.findByIdAndCampaignId(tradeId, actor.campaignId())
                .orElseThrow(() -> ApiException.notFound("Solicitud"));
    }

    private static void requireStock(Item item, int quantity) {
        if (!item.isUnlimitedStock() && item.getQuantity() < quantity) {
            throw ApiException.conflict(item.getQuantity() == 0
                    ? item.getName() + " está agotado"
                    : "Solo quedan " + item.getQuantity() + " de " + item.getName());
        }
    }

    private static void requireOwned(Item item, int quantity, String prefix) {
        if (item.getQuantity() < quantity) {
            throw ApiException.conflict(prefix + item.getQuantity() + " de " + item.getName());
        }
    }

    private static void pay(Purse purse, long total, String message) {
        try {
            purse.spend(total);
        } catch (InsufficientFundsException e) {
            throw new InsufficientFundsException(message);
        }
    }

    private void publish(TradeRequest trade) {
        events.publishEvent(CampaignEvent.trade(trade.getCampaignId(), trade.getId(),
                trade.getShopId(), trade.getCharacterId()));
    }

    private TradeDto toDto(TradeRequest trade) {
        Function<UUID, String> name = id -> inventoryRepo.findById(id).map(Inventory::getName).orElse(null);
        return toDto(trade, name.apply(trade.getShopId()), name.apply(trade.getCharacterId()));
    }

    private static TradeDto toDto(TradeRequest t, String shopName, String characterName) {
        Long total = t.getUnitPriceCp() == null ? null : t.getUnitPriceCp() * t.getQuantity();
        return new TradeDto(t.getId(), t.getShopId(), shopName, t.getCharacterId(), characterName, t.getItemId(),
                t.getItemName(), t.getKind(), t.getQuantity(), t.getUnitPriceCp(), total, t.getStatus(), t.getNote(),
                t.getCreatedAt(), t.getResolvedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
