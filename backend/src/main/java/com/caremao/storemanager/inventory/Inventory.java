package com.caremao.storemanager.inventory;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Tienda o personaje: los dos tienen items, bolsa e historial. */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID campaignId;

    @Enumerated(EnumType.STRING)
    private InventoryKind kind;

    private String name;
    private String description;

    @Embedded
    private Purse purse = new Purse();

    // Solo tiendas
    private boolean visibleToPlayers;
    private boolean requiresApproval = true;
    private boolean unlimitedFunds;
    private int sellMarkupPct = 100;
    private int buybackPct = 50;

    /** Solo tiendas: token del QR de la vitrina pública; se regenera para revocarlo. */
    private String publicToken;

    // Solo personajes
    private String playerName;
    /** Token del QR; es una credencial, se regenera para revocar el acceso. */
    private String accessToken;

    @Version
    private Long version;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    protected Inventory() {
    }

    public Inventory(UUID campaignId, InventoryKind kind, String name) {
        this.campaignId = campaignId;
        this.kind = kind;
        this.name = name;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public boolean isShop() {
        return kind == InventoryKind.SHOP;
    }

    public boolean isCharacter() {
        return kind == InventoryKind.CHARACTER;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public InventoryKind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Purse getPurse() {
        return purse;
    }

    public void setPurse(Purse purse) {
        this.purse = purse;
    }

    public boolean isVisibleToPlayers() {
        return visibleToPlayers;
    }

    public void setVisibleToPlayers(boolean visibleToPlayers) {
        this.visibleToPlayers = visibleToPlayers;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public boolean isUnlimitedFunds() {
        return unlimitedFunds;
    }

    public void setUnlimitedFunds(boolean unlimitedFunds) {
        this.unlimitedFunds = unlimitedFunds;
    }

    public int getSellMarkupPct() {
        return sellMarkupPct;
    }

    public void setSellMarkupPct(int sellMarkupPct) {
        this.sellMarkupPct = sellMarkupPct;
    }

    public int getBuybackPct() {
        return buybackPct;
    }

    public void setBuybackPct(int buybackPct) {
        this.buybackPct = buybackPct;
    }

    public String getPublicToken() {
        return publicToken;
    }

    public void setPublicToken(String publicToken) {
        this.publicToken = publicToken;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
