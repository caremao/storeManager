package com.caremao.storemanager.item;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.caremao.storemanager.common.Limits;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class ItemDtos {

    private ItemDtos() {
    }

    /**
     * {@code priceCp} es lo que cobra la tienda (valor × recargo); solo viene en tiendas.
     * Las notas de una tienda son del GM y no se envían a los jugadores.
     */
    public record ItemDto(UUID id, UUID inventoryId, String name, ItemType type, String category, ItemRarity rarity,
            String description, String notes, int quantity, boolean unlimitedStock, Double weight, Long valueCp,
            Long priceCp, String srdIndex, Map<String, Object> properties, Instant updatedAt) {
    }

    /** Alta o reemplazo completo de un item (el formulario siempre manda todos los campos). */
    public record ItemRequest(
            @NotBlank @Size(max = 200) String name,
            ItemType type,
            @Size(max = 100) String category,
            ItemRarity rarity,
            @Size(max = 20000) String description,
            @Size(max = 2000) String notes,
            @PositiveOrZero @Max(Limits.MAX_QUANTITY) Integer quantity,
            Boolean unlimitedStock,
            @PositiveOrZero @DecimalMax("1000000") Double weight,
            @PositiveOrZero @Max(Limits.MAX_VALUE_CP) Long valueCp,
            @Size(max = 100) String srdIndex,
            Map<String, Object> properties) {
    }
}
