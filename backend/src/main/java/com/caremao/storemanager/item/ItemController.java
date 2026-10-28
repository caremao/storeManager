package com.caremao.storemanager.item;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.caremao.storemanager.auth.Actor;
import com.caremao.storemanager.item.ItemDtos.ItemDto;
import com.caremao.storemanager.item.ItemDtos.ItemRequest;

import jakarta.validation.Valid;

/** Items de una tienda o un personaje; los permisos los resuelve InventoryService. */
@RestController
@RequestMapping("/api/inventories/{inventoryId}/items")
public class ItemController {

    private final ItemService items;

    public ItemController(ItemService items) {
        this.items = items;
    }

    @GetMapping
    public List<ItemDto> list(Actor actor, @PathVariable UUID inventoryId) {
        return items.list(actor, inventoryId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemDto create(Actor actor, @PathVariable UUID inventoryId, @Valid @RequestBody ItemRequest body) {
        return items.create(actor, inventoryId, body);
    }

    @PutMapping("/{itemId}")
    public ItemDto update(Actor actor, @PathVariable UUID inventoryId, @PathVariable UUID itemId,
            @Valid @RequestBody ItemRequest body) {
        return items.update(actor, inventoryId, itemId, body);
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Actor actor, @PathVariable UUID inventoryId, @PathVariable UUID itemId) {
        items.delete(actor, inventoryId, itemId);
    }
}
