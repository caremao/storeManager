package com.caremao.storemanager.inventory;

import static com.caremao.storemanager.common.Limits.MAX_COINS;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record PurseDto(int copper, int silver, int gold, int platinum, long totalCp) {

        public static PurseDto of(Purse p) {
            return new PurseDto(p.getCopper(), p.getSilver(), p.getGold(), p.getPlatinum(), p.totalCopper());
        }
    }

    /**
     * Tienda o personaje. Los campos de tienda solo vienen en tiendas y los de personaje en personajes;
     * {@code purse} no viene cuando un jugador mira una tienda, y los QR ({@code joinUrl} del personaje,
     * {@code storefrontUrl} de la vitrina) solo los ve el GM.
     */
    public record InventoryDto(UUID id, InventoryKind kind, String name, String description, PurseDto purse,
            Boolean visibleToPlayers, Boolean requiresApproval, Boolean unlimitedFunds,
            Integer sellMarkupPct, Integer buybackPct,
            String playerName, String joinUrl, String storefrontUrl, Instant updatedAt) {
    }

    public record PurseInput(
            @PositiveOrZero @Max(MAX_COINS) int copper,
            @PositiveOrZero @Max(MAX_COINS) int silver,
            @PositiveOrZero @Max(MAX_COINS) int gold,
            @PositiveOrZero @Max(MAX_COINS) int platinum) {
    }

    public record CreateInventoryRequest(
            @NotNull InventoryKind kind,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            @Size(max = 120) String playerName,
            Boolean visibleToPlayers,
            Boolean requiresApproval,
            Boolean unlimitedFunds,
            @Min(0) @Max(1000) Integer sellMarkupPct,
            @Min(0) @Max(1000) Integer buybackPct,
            @Valid PurseInput purse) {
    }

    public record UpdateInventoryRequest(
            @Size(min = 1, max = 120) String name,
            @Size(max = 2000) String description,
            @Size(max = 120) String playerName,
            Boolean visibleToPlayers,
            Boolean requiresApproval,
            Boolean unlimitedFunds,
            @Min(0) @Max(1000) Integer sellMarkupPct,
            @Min(0) @Max(1000) Integer buybackPct) {
    }

    /** Deltas con signo por denominación: positivos suman, negativos gastan. */
    public record CurrencyChangeRequest(
            @Min(-MAX_COINS) @Max(MAX_COINS) int copper,
            @Min(-MAX_COINS) @Max(MAX_COINS) int silver,
            @Min(-MAX_COINS) @Max(MAX_COINS) int gold,
            @Min(-MAX_COINS) @Max(MAX_COINS) int platinum,
            @Size(max = 200) String note) {
    }

    public record CurrencyConvertRequest(@NotNull Denomination from, @NotNull Denomination to,
            @Min(1) @Max(MAX_COINS) int amount) {
    }
}
