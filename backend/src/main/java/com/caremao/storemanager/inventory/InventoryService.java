package com.caremao.storemanager.inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.caremao.storemanager.auth.Actor;
import com.caremao.storemanager.auth.AppSessionRepository;
import com.caremao.storemanager.auth.Tokens;
import com.caremao.storemanager.campaign.Campaign;
import com.caremao.storemanager.campaign.CampaignRepository;
import com.caremao.storemanager.common.ApiException;
import com.caremao.storemanager.config.AppProperties;
import com.caremao.storemanager.events.CampaignEvent;
import com.caremao.storemanager.history.HistoryAction;
import com.caremao.storemanager.history.HistoryService;
import com.caremao.storemanager.inventory.InventoryDtos.CreateInventoryRequest;
import com.caremao.storemanager.inventory.InventoryDtos.CurrencyChangeRequest;
import com.caremao.storemanager.inventory.InventoryDtos.CurrencyConvertRequest;
import com.caremao.storemanager.inventory.InventoryDtos.InventoryDto;
import com.caremao.storemanager.inventory.InventoryDtos.PurseDto;
import com.caremao.storemanager.inventory.InventoryDtos.UpdateInventoryRequest;

@Service
public class InventoryService {

    private static final int TOKEN_BYTES = 16;

    private final InventoryRepository inventories;
    private final CampaignRepository campaigns;
    private final AppSessionRepository sessions;
    private final HistoryService history;
    private final ApplicationEventPublisher events;
    private final AppProperties props;

    public InventoryService(InventoryRepository inventories, CampaignRepository campaigns,
            AppSessionRepository sessions, HistoryService history, ApplicationEventPublisher events,
            AppProperties props) {
        this.inventories = inventories;
        this.campaigns = campaigns;
        this.sessions = sessions;
        this.history = history;
        this.events = events;
        this.props = props;
    }

    /**
     * El GM ve todo; un jugador, su personaje y las tiendas abiertas. Lo que no puede ver
     * responde 404, para no revelar que existe.
     */
    public Inventory requireReadable(Actor actor, UUID id) {
        Inventory inventory = inventories.findByIdAndCampaignId(id, actor.campaignId())
                .orElseThrow(() -> ApiException.notFound("Inventario"));
        boolean visible = actor.isGm()
                || actor.isCharacter(inventory.getId())
                || (inventory.isShop() && inventory.isVisibleToPlayers());
        if (!visible) {
            throw ApiException.notFound("Inventario");
        }
        return inventory;
    }

    /** El GM modifica todo; un jugador, solo su propio personaje. */
    public Inventory requireWritable(Actor actor, UUID id) {
        Inventory inventory = requireReadable(actor, id);
        if (!actor.isGm() && !actor.isCharacter(inventory.getId())) {
            throw ApiException.forbidden();
        }
        return inventory;
    }

    @Transactional(readOnly = true)
    public List<InventoryDto> list(Actor actor, InventoryKind kind) {
        List<Inventory> result;
        if (actor.isGm()) {
            result = kind == null
                    ? inventories.findByCampaignIdOrderByKindAscNameAsc(actor.campaignId())
                    : inventories.findByCampaignIdAndKindOrderByNameAsc(actor.campaignId(), kind);
        } else {
            result = new ArrayList<>();
            if (kind == null || kind == InventoryKind.CHARACTER) {
                inventories.findById(actor.characterId()).ifPresent(result::add);
            }
            if (kind == null || kind == InventoryKind.SHOP) {
                result.addAll(inventories.findByCampaignIdAndKindAndVisibleToPlayersTrueOrderByNameAsc(
                        actor.campaignId(), InventoryKind.SHOP));
            }
        }
        return result.stream().map(inv -> toDto(actor, inv)).toList();
    }

    @Transactional(readOnly = true)
    public InventoryDto get(Actor actor, UUID id) {
        return toDto(actor, requireReadable(actor, id));
    }

    @Transactional
    public InventoryDto create(Actor actor, CreateInventoryRequest request) {
        actor.requireGm();
        if (request.kind() == InventoryKind.PARTY) {
            throw ApiException.badRequest("El alijo del grupo todavía no está disponible");
        }
        Inventory inventory = new Inventory(actor.campaignId(), request.kind(), request.name().trim());
        inventory.setDescription(request.description());
        if (inventory.isShop()) {
            applyShopSettings(inventory, request.visibleToPlayers(), request.requiresApproval(),
                    request.unlimitedFunds(), request.sellMarkupPct(), request.buybackPct());
            inventory.setPublicToken(Tokens.random(TOKEN_BYTES));
        } else {
            inventory.setPlayerName(request.playerName());
            inventory.setAccessToken(Tokens.random(TOKEN_BYTES));
        }
        if (request.purse() != null) {
            var p = request.purse();
            inventory.setPurse(new Purse(p.copper(), p.silver(), p.gold(), p.platinum()));
        }
        inventories.save(inventory);
        events.publishEvent(CampaignEvent.inventories(actor.campaignId(), inventory.getId()));
        return toDto(actor, inventory);
    }

    @Transactional
    public InventoryDto update(Actor actor, UUID id, UpdateInventoryRequest request) {
        actor.requireGm();
        Inventory inventory = requireReadable(actor, id);
        if (request.name() != null) {
            inventory.setName(request.name().trim());
        }
        if (request.description() != null) {
            inventory.setDescription(request.description());
        }
        if (inventory.isShop()) {
            applyShopSettings(inventory, request.visibleToPlayers(), request.requiresApproval(),
                    request.unlimitedFunds(), request.sellMarkupPct(), request.buybackPct());
        } else if (request.playerName() != null) {
            inventory.setPlayerName(request.playerName());
        }
        events.publishEvent(CampaignEvent.inventories(actor.campaignId(), inventory.getId()));
        return toDto(actor, inventory);
    }

    @Transactional
    public void delete(Actor actor, UUID id) {
        actor.requireGm();
        Inventory inventory = requireReadable(actor, id);
        // Items, solicitudes y sesiones caen en cascada desde la base; el historial se conserva.
        inventories.delete(inventory);
        events.publishEvent(CampaignEvent.inventories(actor.campaignId(), id));
    }

    @Transactional
    public InventoryDto changeCurrency(Actor actor, UUID id, CurrencyChangeRequest request) {
        Inventory inventory = requireWritable(actor, id);
        boolean adds = request.copper() > 0 || request.silver() > 0 || request.gold() > 0 || request.platinum() > 0;
        if (adds && !actor.isGm()
                && !campaigns.findById(actor.campaignId()).map(Campaign::isPlayersCanAddCoins).orElse(false)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "En esta mesa, las monedas nuevas las da el GM");
        }
        Map<String, Object> before = purseSnapshot(inventory.getPurse());
        inventory.getPurse().apply(request.copper(), request.silver(), request.gold(), request.platinum());
        logCurrency(actor, inventory, before, request.note());
        return toDto(actor, inventory);
    }

    @Transactional
    public InventoryDto convertCurrency(Actor actor, UUID id, CurrencyConvertRequest request) {
        Inventory inventory = requireWritable(actor, id);
        Map<String, Object> before = purseSnapshot(inventory.getPurse());
        inventory.getPurse().convert(request.from(), request.to(), request.amount());
        logCurrency(actor, inventory, before, "Conversión");
        return toDto(actor, inventory);
    }

    /** Nuevo QR para el personaje: el anterior deja de servir y se cierran sus sesiones abiertas. */
    @Transactional
    public InventoryDto regenerateToken(Actor actor, UUID id) {
        actor.requireGm();
        Inventory inventory = requireReadable(actor, id);
        if (!inventory.isCharacter()) {
            throw ApiException.badRequest("Solo los personajes tienen QR");
        }
        inventory.setAccessToken(Tokens.random(TOKEN_BYTES));
        sessions.deleteByCharacterId(inventory.getId());
        return toDto(actor, inventory);
    }

    /** Nuevo QR de vitrina para la tienda: el anterior deja de abrir. */
    @Transactional
    public InventoryDto regenerateStorefrontToken(Actor actor, UUID id) {
        actor.requireGm();
        Inventory inventory = requireReadable(actor, id);
        if (!inventory.isShop()) {
            throw ApiException.badRequest("Solo las tiendas tienen vitrina");
        }
        inventory.setPublicToken(Tokens.random(TOKEN_BYTES));
        events.publishEvent(CampaignEvent.inventories(actor.campaignId(), inventory.getId()));
        return toDto(actor, inventory);
    }

    public InventoryDto toDto(Actor actor, Inventory inv) {
        boolean shop = inv.isShop();
        boolean showPurse = actor.isGm() || !shop;
        return new InventoryDto(
                inv.getId(), inv.getKind(), inv.getName(), inv.getDescription(),
                showPurse ? PurseDto.of(inv.getPurse()) : null,
                shop ? inv.isVisibleToPlayers() : null,
                shop ? inv.isRequiresApproval() : null,
                shop ? inv.isUnlimitedFunds() : null,
                shop ? inv.getSellMarkupPct() : null,
                shop ? inv.getBuybackPct() : null,
                inv.isCharacter() ? inv.getPlayerName() : null,
                inv.isCharacter() && actor.isGm() ? props.joinUrl(inv.getAccessToken()) : null,
                shop && actor.isGm() && inv.getPublicToken() != null ? props.storefrontUrl(inv.getPublicToken()) : null,
                inv.getUpdatedAt());
    }

    private void logCurrency(Actor actor, Inventory inventory, Map<String, Object> before, String note) {
        Map<String, Object> changes = HistoryService.computeChanges(before, purseSnapshot(inventory.getPurse()));
        if (!changes.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("changes", changes);
            if (note != null && !note.isBlank()) {
                details.put("note", note);
            }
            history.log(actor, HistoryAction.CURRENCY_UPDATED, inventory, null, null, details);
        }
        events.publishEvent(CampaignEvent.inventories(actor.campaignId(), inventory.getId()));
    }

    private static Map<String, Object> purseSnapshot(Purse purse) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("copper", purse.getCopper());
        s.put("silver", purse.getSilver());
        s.put("gold", purse.getGold());
        s.put("platinum", purse.getPlatinum());
        return s;
    }

    private static void applyShopSettings(Inventory shop, Boolean visible, Boolean approval, Boolean unlimitedFunds,
            Integer sellMarkupPct, Integer buybackPct) {
        if (visible != null) {
            shop.setVisibleToPlayers(visible);
        }
        if (approval != null) {
            shop.setRequiresApproval(approval);
        }
        if (unlimitedFunds != null) {
            shop.setUnlimitedFunds(unlimitedFunds);
        }
        if (sellMarkupPct != null) {
            shop.setSellMarkupPct(sellMarkupPct);
        }
        if (buybackPct != null) {
            shop.setBuybackPct(buybackPct);
        }
    }
}
