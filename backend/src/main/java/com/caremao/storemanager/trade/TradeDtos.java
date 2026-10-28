package com.caremao.storemanager.trade;

import java.time.Instant;
import java.util.UUID;

import com.caremao.storemanager.common.Limits;
import com.caremao.storemanager.inventory.Denomination;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class TradeDtos {

    private TradeDtos() {
    }

    /**
     * {@code unitPriceCp} es el precio que está sobre la mesa; {@code listUnitPriceCp}, el de la tienda
     * al pedir; {@code lastOfferBy}, quién lo propuso (null = precio de lista).
     */
    public record TradeDto(UUID id, UUID shopId, String shopName, UUID characterId, String characterName,
            UUID itemId, String itemName, TradeKind kind, int quantity, Long unitPriceCp, Long totalCp,
            Long listUnitPriceCp, OfferBy lastOfferBy, Denomination priceDenomination, TradeStatus status,
            String note, Instant createdAt, Instant resolvedAt) {
    }

    /**
     * Lo que pide un jugador desde el mostrador. {@code offerUnitPriceCp} propone otro precio: en una
     * compra solo si el producto es negociable; en una venta, siempre.
     */
    public record TradeCreateRequest(
            @NotNull TradeKind kind,
            @NotNull UUID itemId,
            @Min(1) @Max(10_000) int quantity,
            @PositiveOrZero @Max(Limits.MAX_VALUE_CP) Long offerUnitPriceCp,
            @Size(max = 500) String note) {
    }

    /** Una contraoferta (del GM) o una nueva oferta (del jugador). */
    public record TradeCounterRequest(@NotNull @PositiveOrZero @Max(Limits.MAX_VALUE_CP) Long unitPriceCp) {
    }

    public record TradeRejectRequest(@Size(max = 500) String note) {
    }

    /** Venta o compra que el GM registra directamente en la mesa, sin solicitud previa. */
    public record DirectTradeRequest(
            @NotNull TradeKind kind,
            @NotNull UUID characterId,
            @NotNull UUID itemId,
            @Min(1) @Max(10_000) int quantity,
            @PositiveOrZero @Max(Limits.MAX_VALUE_CP) Long unitPriceCp) {
    }
}
