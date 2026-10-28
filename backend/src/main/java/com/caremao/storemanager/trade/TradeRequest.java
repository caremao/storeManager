package com.caremao.storemanager.trade;

import java.time.Instant;
import java.util.UUID;

import com.caremao.storemanager.common.ApiException;
import com.caremao.storemanager.inventory.Denomination;

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
    /** Precio que está sobre la mesa. Null si el item no tiene precio: el GM lo propone. */
    private Long unitPriceCp;
    /** Precio de la tienda al pedir (recargo o recompra sobre el valor real). */
    private Long listUnitPriceCp;
    /** Quién propuso {@code unitPriceCp}; null = es el precio de lista. */
    @Enumerated(EnumType.STRING)
    private OfferBy lastOfferBy;
    /** Moneda en que está fijado el precio del item, para mostrarlo igual. */
    @Enumerated(EnumType.STRING)
    private Denomination priceDenomination;

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
        this.listUnitPriceCp = unitPriceCp;
        this.note = note;
    }

    /** Sigue abierta (pendiente o con contraoferta). */
    public void requireOpen() {
        if (!status.isOpen()) {
            throw ApiException.conflict("Esta solicitud ya fue resuelta");
        }
    }

    /** Es el turno de quien quiere actuar: el GM en {@code PENDING}, el jugador en {@code COUNTERED}. */
    void requireTurn(TradeStatus expected) {
        requireOpen();
        if (status != expected) {
            throw ApiException.conflict(expected == TradeStatus.PENDING
                    ? "Ahora le toca al jugador responder la contraoferta"
                    : "Ahora le toca al GM responder");
        }
    }

    void resolve(TradeStatus newStatus) {
        requireOpen();
        status = newStatus;
        resolvedAt = Instant.now();
    }

    void approve(long finalUnitPriceCp) {
        unitPriceCp = finalUnitPriceCp;
        resolve(TradeStatus.APPROVED);
    }

    /** Pone un precio sobre la mesa y pasa el turno al otro. */
    void offer(long priceCp, OfferBy by) {
        requireOpen();
        unitPriceCp = priceCp;
        lastOfferBy = by;
        status = by == OfferBy.GM ? TradeStatus.COUNTERED : TradeStatus.PENDING;
    }

    public Long getListUnitPriceCp() {
        return listUnitPriceCp;
    }

    void setListUnitPriceCp(Long listUnitPriceCp) {
        this.listUnitPriceCp = listUnitPriceCp;
    }

    public OfferBy getLastOfferBy() {
        return lastOfferBy;
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

    public Denomination getPriceDenomination() {
        return priceDenomination;
    }

    void setPriceDenomination(Denomination priceDenomination) {
        this.priceDenomination = priceDenomination;
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
