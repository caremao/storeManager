package com.caremao.storemanager.inventory;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.caremao.storemanager.auth.Actor;
import com.caremao.storemanager.inventory.InventoryDtos.CreateInventoryRequest;
import com.caremao.storemanager.inventory.InventoryDtos.CurrencyChangeRequest;
import com.caremao.storemanager.inventory.InventoryDtos.CurrencyConvertRequest;
import com.caremao.storemanager.inventory.InventoryDtos.InventoryDto;
import com.caremao.storemanager.inventory.InventoryDtos.UpdateInventoryRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/inventories")
public class InventoryController {

    private final InventoryService inventories;

    public InventoryController(InventoryService inventories) {
        this.inventories = inventories;
    }

    @GetMapping
    public List<InventoryDto> list(Actor actor, @RequestParam(required = false) InventoryKind kind) {
        return inventories.list(actor, kind);
    }

    @GetMapping("/{id}")
    public InventoryDto get(Actor actor, @PathVariable UUID id) {
        return inventories.get(actor, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryDto create(Actor actor, @Valid @RequestBody CreateInventoryRequest body) {
        return inventories.create(actor, body);
    }

    @PatchMapping("/{id}")
    public InventoryDto update(Actor actor, @PathVariable UUID id, @Valid @RequestBody UpdateInventoryRequest body) {
        return inventories.update(actor, id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Actor actor, @PathVariable UUID id) {
        inventories.delete(actor, id);
    }

    @PostMapping("/{id}/currency")
    public InventoryDto changeCurrency(Actor actor, @PathVariable UUID id,
            @Valid @RequestBody CurrencyChangeRequest body) {
        return inventories.changeCurrency(actor, id, body);
    }

    @PostMapping("/{id}/currency/convert")
    public InventoryDto convertCurrency(Actor actor, @PathVariable UUID id,
            @Valid @RequestBody CurrencyConvertRequest body) {
        return inventories.convertCurrency(actor, id, body);
    }

    @PostMapping("/{id}/token")
    public InventoryDto regenerateToken(Actor actor, @PathVariable UUID id) {
        return inventories.regenerateToken(actor, id);
    }

    @PostMapping("/{id}/storefront-token")
    public InventoryDto regenerateStorefrontToken(Actor actor, @PathVariable UUID id) {
        return inventories.regenerateStorefrontToken(actor, id);
    }
}
