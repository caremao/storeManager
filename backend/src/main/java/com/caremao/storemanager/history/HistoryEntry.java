package com.caremao.storemanager.history;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.caremao.storemanager.common.MapJsonConverter;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "history_entry")
public class HistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID campaignId;
    private UUID inventoryId;
    private String inventoryName;
    /** En un trade: el personaje (inventoryId es la tienda). */
    private UUID relatedInventoryId;
    private String actor;

    @Enumerated(EnumType.STRING)
    private HistoryAction action;

    private String entityName;

    @Convert(converter = MapJsonConverter.class)
    private Map<String, Object> details;

    private Instant createdAt = Instant.now();

    protected HistoryEntry() {
    }

    public HistoryEntry(UUID campaignId, UUID inventoryId, String inventoryName, UUID relatedInventoryId,
            String actor, HistoryAction action, String entityName, Map<String, Object> details) {
        this.campaignId = campaignId;
        this.inventoryId = inventoryId;
        this.inventoryName = inventoryName;
        this.relatedInventoryId = relatedInventoryId;
        this.actor = actor;
        this.action = action;
        this.entityName = entityName;
        this.details = details;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public UUID getInventoryId() {
        return inventoryId;
    }

    public String getInventoryName() {
        return inventoryName;
    }

    public UUID getRelatedInventoryId() {
        return relatedInventoryId;
    }

    public String getActor() {
        return actor;
    }

    public HistoryAction getAction() {
        return action;
    }

    public String getEntityName() {
        return entityName;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
