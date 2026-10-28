package com.caremao.storemanager.history;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.caremao.storemanager.auth.Actor;
import com.caremao.storemanager.history.HistoryDtos.HistoryEntryDto;
import com.caremao.storemanager.history.HistoryDtos.HistoryPage;
import com.caremao.storemanager.inventory.Inventory;
import com.caremao.storemanager.inventory.InventoryRepository;

@Service
public class HistoryService {

    private final HistoryRepository history;
    private final InventoryRepository inventories;

    public HistoryService(HistoryRepository history, InventoryRepository inventories) {
        this.history = history;
        this.inventories = inventories;
    }

    /** Diff campo a campo: {@code {campo: {old, new}}}, solo con lo que cambió (portado de history.py). */
    public static Map<String, Object> computeChanges(Map<String, Object> oldValues, Map<String, Object> newValues) {
        Set<String> keys = new LinkedHashSet<>(oldValues.keySet());
        keys.addAll(newValues.keySet());
        Map<String, Object> changes = new LinkedHashMap<>();
        for (String key : keys) {
            Object oldValue = oldValues.get(key);
            Object newValue = newValues.get(key);
            if (!Objects.equals(oldValue, newValue)) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("old", oldValue);
                change.put("new", newValue);
                changes.put(key, change);
            }
        }
        return changes;
    }

    /** Se llama dentro de la transacción del cambio, así historial y cambio son atómicos. */
    public void log(Actor actor, HistoryAction action, Inventory inventory, Inventory related, String entityName,
            Map<String, Object> details) {
        history.save(new HistoryEntry(inventory.getCampaignId(), inventory.getId(), inventory.getName(),
                related == null ? null : related.getId(), actorName(actor), action, entityName, details));
    }

    public String actorName(Actor actor) {
        if (actor.isGm()) {
            return "GM";
        }
        return inventories.findById(actor.characterId()).map(Inventory::getName).orElse("Jugador");
    }

    /** El GM ve todo (o un inventario); un jugador solo lo de su personaje. */
    @Transactional(readOnly = true)
    public HistoryPage list(Actor actor, UUID inventoryId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100));
        UUID scope = actor.isGm() ? inventoryId : actor.characterId();
        Page<HistoryEntry> result = scope == null
                ? history.findByCampaignIdOrderByCreatedAtDesc(actor.campaignId(), pageable)
                : history.findForInventory(actor.campaignId(), scope, pageable);
        Function<HistoryEntry, HistoryEntryDto> toDto = actor.isGm() ? HistoryEntryDto::of : HistoryEntryDto::forPlayer;
        return new HistoryPage(result.map(toDto).getContent(), result.getTotalElements());
    }
}
