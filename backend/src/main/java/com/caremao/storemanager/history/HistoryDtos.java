package com.caremao.storemanager.history;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HistoryDtos {

    private HistoryDtos() {
    }

    public record HistoryEntryDto(UUID id, UUID inventoryId, String inventoryName, UUID relatedInventoryId,
            String actor, HistoryAction action, String entityName, Map<String, Object> details, Instant createdAt) {

        static HistoryEntryDto of(HistoryEntry e) {
            return new HistoryEntryDto(e.getId(), e.getInventoryId(), e.getInventoryName(), e.getRelatedInventoryId(),
                    e.getActor(), e.getAction(), e.getEntityName(), e.getDetails(), e.getCreatedAt());
        }
    }

    public record HistoryPage(List<HistoryEntryDto> entries, long total) {
    }
}
