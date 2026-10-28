package com.caremao.storemanager.history;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HistoryDtos {

    /** Campos que un jugador no ve en el historial: el valor real de un item puede no conocerlo. */
    private static final List<String> HIDDEN_FROM_PLAYERS = List.of("valueCp", "priceDenomination");

    private HistoryDtos() {
    }

    public record HistoryEntryDto(UUID id, UUID inventoryId, String inventoryName, UUID relatedInventoryId,
            String actor, HistoryAction action, String entityName, Map<String, Object> details, Instant createdAt) {

        static HistoryEntryDto of(HistoryEntry e) {
            return new HistoryEntryDto(e.getId(), e.getInventoryId(), e.getInventoryName(), e.getRelatedInventoryId(),
                    e.getActor(), e.getAction(), e.getEntityName(), e.getDetails(), e.getCreatedAt());
        }

        /** Para un jugador. Los precios de compras y ventas sí quedan: son lo que pagó o cobró. */
        static HistoryEntryDto forPlayer(HistoryEntry e) {
            return new HistoryEntryDto(e.getId(), e.getInventoryId(), e.getInventoryName(), e.getRelatedInventoryId(),
                    e.getActor(), e.getAction(), e.getEntityName(), withoutRealValue(e.getDetails()), e.getCreatedAt());
        }

        private static Map<String, Object> withoutRealValue(Map<String, Object> details) {
            if (details == null) {
                return null;
            }
            Map<String, Object> copy = new LinkedHashMap<>(details);
            HIDDEN_FROM_PLAYERS.forEach(copy::remove);
            if (copy.get("changes") instanceof Map<?, ?> changes) {
                Map<Object, Object> visible = new LinkedHashMap<>(changes);
                HIDDEN_FROM_PLAYERS.forEach(visible::remove);
                copy.put("changes", visible);
            }
            return copy;
        }
    }

    public record HistoryPage(List<HistoryEntryDto> entries, long total) {
    }
}
