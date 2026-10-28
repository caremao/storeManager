package com.caremao.storemanager.trade;

import java.time.Instant;
import java.util.UUID;

import com.caremao.storemanager.common.ApiException;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "trade_request")
public class TradeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID campaignId;
    private UUID shopId;
    private UUID characterId;
    private UUID itemId;
    private String itemName;

    @Enumerated(EnumType.STRING)
    private TradeKind kind;

    private int quantity;
    /** Null si el item no tiene precio: el GM lo fija al aprobar. */
    private Long unitPriceCp;

    @Enumerated(EnumType.STRING)
    private TradeStatus status = TradeStatus.PENDING;

    private String note;

    /** Evita aprobar dos veces la misma solicitud desde dos pestañas. */
    @Version
    private Long version;

    private Instant createdAt = Instant.now();
    private Instant resolvedAt;

    protected TradeRequest() {
    }

    public TradeRequest(UUID campaignId, UUID shopId, UUID characterId, UUID itemId, String itemName, TradeKind kind,
            int quantity, Long unitPriceCp, String note) {
        this.campaignId = campaignId;
        this.shopId = shopId;
        this.characterId = characterId;
        this.itemId = itemId;
        this.itemName = itemName;
        this.kind = kind;
        this.quantity = quantity;
        this.unitPriceCp = unitPriceCp;
        this.note = note;
    }

    public void requirePending() {
        if (status != TradeStatus.PENDING) {
            throw ApiException.conflict("Esta solicitud ya fue resuelta");
        }
    }

    void resolve(TradeStatus newStatus) {
        requirePending();
        status = newStatus;
        resolvedAt = Instant.now();
    }

    void approve(long finalUnitPriceCp) {
        unitPriceCp = finalUnitPriceCp;
        resolve(TradeStatus.APPROVED);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public UUID getShopId() {
        return shopId;
    }

    public UUID getCharacterId() {
        return characterId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public TradeKind getKind() {
        return kind;
    }

    public int getQuantity() {
        return quantity;
    }

    public Long getUnitPriceCp() {
        return unitPriceCp;
    }

    public TradeStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
