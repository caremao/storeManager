package com.caremao.storemanager.trade;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.caremao.storemanager.auth.Actor;
import com.caremao.storemanager.trade.TradeDtos.DirectTradeRequest;
import com.caremao.storemanager.trade.TradeDtos.TradeCounterRequest;
import com.caremao.storemanager.trade.TradeDtos.TradeCreateRequest;
import com.caremao.storemanager.trade.TradeDtos.TradeDto;
import com.caremao.storemanager.trade.TradeDtos.TradeRejectRequest;

import jakarta.validation.Valid;

/**
 * Solicitudes y su negociación por turnos: en {@code PENDING} le toca al GM (aprobar, contraofertar,
 * rechazar); en {@code COUNTERED}, al jugador (aceptar, volver a ofertar, cancelar).
 */
@RestController
@RequestMapping("/api")
public class TradeController {

    private final TradeService trades;

    public TradeController(TradeService trades) {
        this.trades = trades;
    }

    @PostMapping("/shops/{shopId}/trades")
    @ResponseStatus(HttpStatus.CREATED)
    public TradeDto request(Actor actor, @PathVariable UUID shopId, @Valid @RequestBody TradeCreateRequest body) {
        return trades.request(actor, shopId, body);
    }

    @PostMapping("/shops/{shopId}/direct-trades")
    @ResponseStatus(HttpStatus.CREATED)
    public TradeDto direct(Actor actor, @PathVariable UUID shopId, @Valid @RequestBody DirectTradeRequest body) {
        return trades.direct(actor, shopId, body);
    }

    @GetMapping("/trades")
    public List<TradeDto> list(Actor actor, @RequestParam(required = false) TradeStatus status) {
        return trades.list(actor, status);
    }

    @PostMapping("/trades/{id}/approve")
    public TradeDto approve(Actor actor, @PathVariable UUID id) {
        return trades.approve(actor, id);
    }

    @PostMapping("/trades/{id}/accept")
    public TradeDto accept(Actor actor, @PathVariable UUID id) {
        return trades.accept(actor, id);
    }

    @PostMapping("/trades/{id}/counter")
    public TradeDto counter(Actor actor, @PathVariable UUID id, @Valid @RequestBody TradeCounterRequest body) {
        return trades.counter(actor, id, body.unitPriceCp());
    }

    @PostMapping("/trades/{id}/reject")
    public TradeDto reject(Actor actor, @PathVariable UUID id,
            @Valid @RequestBody(required = false) TradeRejectRequest body) {
        return trades.reject(actor, id, body == null ? null : body.note());
    }

    @PostMapping("/trades/{id}/cancel")
    public TradeDto cancel(Actor actor, @PathVariable UUID id) {
        return trades.cancel(actor, id);
    }
}
