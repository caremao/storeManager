package com.caremao.storemanager.item;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.caremao.storemanager.common.Limits;
import com.caremao.storemanager.inventory.Denomination;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class ItemDtos {

    private ItemDtos() {
    }

    /**
     * Lo que ve cada uno:
     * <ul>
     * <li>{@code priceCp} es lo que cobra la tienda (valor × recargo); solo viene en tiendas.</li>
     * <li>{@code valueCp} es el valor real: el jugador lo recibe solo si {@code valueRevealed}, y nunca
     * el de los productos de una tienda.</li>
     * <li>{@code perceivedValueCp} es lo que cree el dueño; null = desconocido.</li>
     * <li>Las notas de una tienda son del GM y no se envían a los jugadores.</li>
     * </ul>
     */
    public record ItemDto(UUID id, UUID inventoryId, String name, ItemType type, String category, ItemRarity rarity,
            String description, String notes, int quantity, boolean unlimitedStock, Double weight, Long valueCp,
            Long priceCp, Denomination priceDenomination, Long perceivedValueCp, Denomination perceivedDenomination,
            boolean valueRevealed, boolean negotiable, String srdIndex, Map<String, Object> properties,
            Instant updatedAt) {
    }

    /**
     * Alta o reemplazo completo de un item (el formulario siempre manda todos los campos). Si lo manda
     * un jugador, el servidor ignora el valor real, si está revelado y si es negociable.
     */
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
            Denomination priceDenomination,
            @PositiveOrZero @Max(Limits.MAX_VALUE_CP) Long perceivedValueCp,
            Denomination perceivedDenomination,
            Boolean valueRevealed,
            Boolean negotiable,
            @Size(max = 100) String srdIndex,
            Map<String, Object> properties) {
    }
}
