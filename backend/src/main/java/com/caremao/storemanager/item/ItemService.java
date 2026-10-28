package com.caremao.storemanager.item;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.caremao.storemanager.auth.Actor;
import com.caremao.storemanager.common.ApiException;
import com.caremao.storemanager.common.Limits;
import com.caremao.storemanager.events.CampaignEvent;
import com.caremao.storemanager.history.HistoryAction;
import com.caremao.storemanager.history.HistoryService;
import com.caremao.storemanager.inventory.Inventory;
import com.caremao.storemanager.inventory.InventoryService;
import com.caremao.storemanager.item.ItemDtos.ItemDto;
import com.caremao.storemanager.item.ItemDtos.ItemRequest;

import tools.jackson.databind.json.JsonMapper;

@Service
public class ItemService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ItemRepository items;
    private final InventoryService inventories;
    private final HistoryService history;
    private final ApplicationEventPublisher events;

    public ItemService(ItemRepository items, InventoryService inventories, HistoryService history,
            ApplicationEventPublisher events) {
        this.items = items;
        this.inventories = inventories;
        this.history = history;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<ItemDto> list(Actor actor, UUID inventoryId) {
        Inventory inventory = inventories.requireReadable(actor, inventoryId);
        return items.findByInventoryIdOrderByNameAsc(inventoryId).stream()
                .filter(item -> actor.isGm() || !item.isHidden())
                .map(item -> toDto(actor, inventory, item))
                .toList();
    }

    @Transactional
    public ItemDto create(Actor actor, UUID inventoryId, ItemRequest request) {
        Inventory inventory = inventories.requireWritable(actor, inventoryId);
        Item item = new Item(inventory.getId(), request.name().trim());
        apply(actor, inventory, item, request, true);
        items.save(item);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("quantity", item.getQuantity());
        details.put("valueCp", item.getValueCp());
        history.log(actor, HistoryAction.ITEM_ADDED, inventory, null, item.getName(), details);
        events.publishEvent(CampaignEvent.inventories(actor.campaignId(), inventory.getId()));
        return toDto(actor, inventory, item);
    }

    @Transactional
    public ItemDto update(Actor actor, UUID inventoryId, UUID itemId, ItemRequest request) {
        Inventory inventory = inventories.requireWritable(actor, inventoryId);
        Item item = items.findByIdAndInventoryId(itemId, inventoryId)
                .orElseThrow(() -> ApiException.notFound("Artículo"));
        Map<String, Object> before = item.snapshot();
        item.setName(request.name().trim());
        apply(actor, inventory, item, request, false);
        Map<String, Object> changes = HistoryService.computeChanges(before, item.snapshot());
        if (!changes.isEmpty()) {
            history.log(actor, HistoryAction.ITEM_UPDATED, inventory, null, item.getName(),
                    Map.of("changes", changes));
        }
        events.publishEvent(CampaignEvent.inventories(actor.campaignId(), inventory.getId()));
        return toDto(actor, inventory, item);
    }

    @Transactional
    public void delete(Actor actor, UUID inventoryId, UUID itemId) {
        Inventory inventory = inventories.requireWritable(actor, inventoryId);
        Item item = items.findByIdAndInventoryId(itemId, inventoryId)
                .orElseThrow(() -> ApiException.notFound("Artículo"));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("quantity", item.getQuantity());
        details.put("valueCp", item.getValueCp());
        items.delete(item);
        history.log(actor, HistoryAction.ITEM_REMOVED, inventory, null, item.getName(), details);
        events.publishEvent(CampaignEvent.inventories(actor.campaignId(), inventory.getId()));
    }

    /** El GM descubre el valor real al dueño (p. ej. tras Identificar o una tasación). */
    @Transactional
    public ItemDto reveal(Actor actor, UUID inventoryId, UUID itemId) {
        actor.requireGm();
        Inventory inventory = inventories.requireReadable(actor, inventoryId);
        Item item = items.findByIdAndInventoryId(itemId, inventoryId)
                .orElseThrow(() -> ApiException.notFound("Artículo"));
        Map<String, Object> before = item.snapshot();
        item.setValueRevealed(true);
        item.setPerceivedValueCp(item.getValueCp());
        item.setPerceivedDenomination(item.getPriceDenomination());
        Map<String, Object> changes = HistoryService.computeChanges(before, item.snapshot());
        if (!changes.isEmpty()) {
            history.log(actor, HistoryAction.ITEM_UPDATED, inventory, null, item.getName(), Map.of("changes", changes));
        }
        events.publishEvent(CampaignEvent.inventories(actor.campaignId(), inventory.getId()));
        return toDto(actor, inventory, item);
    }

    /**
     * El GM lo ve todo. Un jugador ve el valor real de sus cosas solo si está revelado, ve lo que
     * cree que valen, y en una tienda ve el precio pero no el valor base ni las notas.
     */
    public ItemDto toDto(Actor actor, Inventory inventory, Item item) {
        boolean gm = actor.isGm();
        boolean shop = inventory.isShop();
        boolean seesRealValue = gm || (!shop && item.isValueRevealed());
        return new ItemDto(item.getId(), item.getInventoryId(), item.getName(), item.getType(), item.getCategory(),
                item.getRarity(), item.getDescription(),
                shop && !gm ? null : item.getNotes(),
                item.getQuantity(), item.isUnlimitedStock(), item.getWeight(),
                seesRealValue ? item.getValueCp() : null,
                shop ? Pricing.apply(item.getValueCp(), inventory.getSellMarkupPct()) : null,
                item.getPriceDenomination(),
                gm || !shop ? item.getPerceivedValueCp() : null,
                gm || !shop ? item.getPerceivedDenomination() : null,
                item.isValueRevealed(), item.isNegotiable(), item.isHidden(),
                item.getSrdIndex(), item.getProperties(), item.getUpdatedAt());
    }

    private static void apply(Actor actor, Inventory inventory, Item item, ItemRequest request, boolean creating) {
        int quantity = request.quantity() == null ? 1 : request.quantity();
        // En una tienda un artículo puede quedar agotado (0); en un personaje se borra.
        if (!inventory.isShop() && quantity < 1) {
            throw ApiException.badRequest("La cantidad mínima es 1");
        }
        if (request.type() != null) {
            item.setType(request.type());
        }
        if (request.rarity() != null) {
            item.setRarity(request.rarity());
        }
        item.setCategory(request.category());
        item.setDescription(request.description());
        item.setNotes(request.notes());
        item.setQuantity(quantity);
        item.setUnlimitedStock(inventory.isShop() && Boolean.TRUE.equals(request.unlimitedStock()));
        item.setWeight(request.weight());
        item.setPerceivedValueCp(request.perceivedValueCp());
        item.setPerceivedDenomination(request.perceivedDenomination());
        if (actor.isGm()) {
            item.setValueCp(request.valueCp());
            item.setPriceDenomination(request.priceDenomination());
            item.setValueRevealed(request.valueRevealed() == null || request.valueRevealed());
            item.setNegotiable(inventory.isShop() && Boolean.TRUE.equals(request.negotiable()));
            item.setHidden(inventory.isShop() && Boolean.TRUE.equals(request.hidden()));
        } else if (creating) {
            // Lo que anota el jugador es lo que cree que vale; el valor real lo fija el GM.
            item.setValueCp(null);
            item.setPriceDenomination(null);
            item.setValueRevealed(false);
            item.setNegotiable(false);
        }
        if (request.srdIndex() != null) {
            item.setSrdIndex(request.srdIndex());
        }
        item.setProperties(requireReasonableProperties(request.properties()));
    }

    /** Las propiedades son JSON libre: se acotan su tamaño y su anidamiento para que no rompan la base. */
    static Map<String, Object> requireReasonableProperties(Map<String, Object> properties) {
        if (properties == null) {
            return null;
        }
        if (depth(properties) > Limits.MAX_PROPERTIES_DEPTH) {
            throw ApiException.badRequest("Las propiedades del item están demasiado anidadas");
        }
        if (JSON.writeValueAsString(properties).length() > Limits.MAX_PROPERTIES_CHARS) {
            throw ApiException.badRequest("Las propiedades del item son demasiado grandes");
        }
        return properties;
    }

    private static int depth(Object value) {
        if (value instanceof Map<?, ?> map) {
            return 1 + map.values().stream().mapToInt(ItemService::depth).max().orElse(0);
        }
        if (value instanceof Collection<?> list) {
            return 1 + list.stream().mapToInt(ItemService::depth).max().orElse(0);
        }
        return 0;
    }
}
