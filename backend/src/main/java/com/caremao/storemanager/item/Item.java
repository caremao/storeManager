package com.caremao.storemanager.item;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.caremao.storemanager.common.MapJsonConverter;
import com.caremao.storemanager.inventory.Denomination;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "item")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID inventoryId;

    private String name;

    @Enumerated(EnumType.STRING)
    private ItemType type = ItemType.MISC;

    private String category;

    @Enumerated(EnumType.STRING)
    private ItemRarity rarity = ItemRarity.COMMON;

    private String description;
    private String notes;
    private int quantity = 1;
    /** Solo tiendas: no se agota al vender. */
    private boolean unlimitedStock;
    private Double weight;
    /** Precio base en piezas de cobre; null si nadie lo fijó. */
    private Long valueCp;
    /** Moneda en que se fijó el precio, para mostrarlo igual ("3 pp"); null = oro. */
    @Enumerated(EnumType.STRING)
    private Denomination priceDenomination;
    /** Lo que cree el dueño que vale; null = "desconocido". El real ({@code valueCp}) lo sabe el GM. */
    private Long perceivedValueCp;
    @Enumerated(EnumType.STRING)
    private Denomination perceivedDenomination;
    /** Si el dueño conoce el valor real (p. ej. lo compró, o lo identificó). */
    private boolean valueRevealed = true;
    /** En una tienda: acepta que el jugador ofrezca otro precio. */
    private boolean negotiable;
    /** En una tienda: solo lo ve el GM (lo menciona en la mesa y lo vende directo). */
    private boolean hidden;
    private String srdIndex;

    @Convert(converter = MapJsonConverter.class)
    private Map<String, Object> properties;

    @Version
    private Long version;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    protected Item() {
    }

    public Item(UUID inventoryId, String name) {
        this.inventoryId = inventoryId;
        this.name = name;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /**
     * Copia para otro inventario (al comprar o vender). No copia notas, stock ilimitado ni si es
     * negociable; quien recibe el objeto conoce su valor (lo pagó o lo tasó).
     */
    public Item copyTo(UUID targetInventoryId, int quantity) {
        Item copy = new Item(targetInventoryId, name);
        copy.type = type;
        copy.category = category;
        copy.rarity = rarity;
        copy.description = description;
        copy.quantity = quantity;
        copy.weight = weight;
        copy.valueCp = valueCp;
        copy.priceDenomination = priceDenomination;
        copy.perceivedValueCp = valueCp;
        copy.perceivedDenomination = priceDenomination;
        copy.valueRevealed = true;
        copy.srdIndex = srdIndex;
        copy.properties = properties == null ? null : new LinkedHashMap<>(properties);
        return copy;
    }

    /** Campos que el historial compara para registrar cambios. */
    public Map<String, Object> snapshot() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("type", type.name());
        s.put("category", category);
        s.put("rarity", rarity.name());
        s.put("description", description);
        s.put("notes", notes);
        s.put("quantity", quantity);
        s.put("unlimitedStock", unlimitedStock);
        s.put("weight", weight);
        s.put("valueCp", valueCp);
        s.put("priceDenomination", priceDenomination == null ? null : priceDenomination.name());
        s.put("perceivedValueCp", perceivedValueCp);
        s.put("valueRevealed", valueRevealed);
        s.put("negotiable", negotiable);
        s.put("hidden", hidden);
        return s;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInventoryId() {
        return inventoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ItemType getType() {
        return type;
    }

    public void setType(ItemType type) {
        this.type = type;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public ItemRarity getRarity() {
        return rarity;
    }

    public void setRarity(ItemRarity rarity) {
        this.rarity = rarity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public boolean isUnlimitedStock() {
        return unlimitedStock;
    }

    public void setUnlimitedStock(boolean unlimitedStock) {
        this.unlimitedStock = unlimitedStock;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public Long getValueCp() {
        return valueCp;
    }

    public void setValueCp(Long valueCp) {
        this.valueCp = valueCp;
    }

    public Denomination getPriceDenomination() {
        return priceDenomination;
    }

    public void setPriceDenomination(Denomination priceDenomination) {
        this.priceDenomination = priceDenomination;
    }

    public Long getPerceivedValueCp() {
        return perceivedValueCp;
    }

    public void setPerceivedValueCp(Long perceivedValueCp) {
        this.perceivedValueCp = perceivedValueCp;
    }

    public Denomination getPerceivedDenomination() {
        return perceivedDenomination;
    }

    public void setPerceivedDenomination(Denomination perceivedDenomination) {
        this.perceivedDenomination = perceivedDenomination;
    }

    public boolean isValueRevealed() {
        return valueRevealed;
    }

    public void setValueRevealed(boolean valueRevealed) {
        this.valueRevealed = valueRevealed;
    }

    public boolean isNegotiable() {
        return negotiable;
    }

    public void setNegotiable(boolean negotiable) {
        this.negotiable = negotiable;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public String getSrdIndex() {
        return srdIndex;
    }

    public void setSrdIndex(String srdIndex) {
        this.srdIndex = srdIndex;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
