package com.caremao.storemanager.srd;

import java.util.Map;

import com.caremao.storemanager.common.MapJsonConverter;
import com.caremao.storemanager.item.ItemRarity;
import com.caremao.storemanager.item.ItemType;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catálogo de solo lectura del SRD 5.1 (equipo y objetos mágicos). Lo carga {@link SrdImporter}. */
@Entity
@Table(name = "srd_item")
public class SrdItem {

    @Id
    private String srdIndex;

    private String name;
    private String source;

    @Enumerated(EnumType.STRING)
    private ItemType type;

    private String category;

    @Enumerated(EnumType.STRING)
    private ItemRarity rarity;

    private Double weight;
    private Long valueCp;
    private String description;

    @Convert(converter = MapJsonConverter.class)
    private Map<String, Object> properties;

    protected SrdItem() {
    }

    public String getSrdIndex() {
        return srdIndex;
    }

    public String getName() {
        return name;
    }

    public String getSource() {
        return source;
    }

    public ItemType getType() {
        return type;
    }

    public String getCategory() {
        return category;
    }

    public ItemRarity getRarity() {
        return rarity;
    }

    public Double getWeight() {
        return weight;
    }

    public Long getValueCp() {
        return valueCp;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
