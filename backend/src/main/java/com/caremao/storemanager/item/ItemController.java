package com.caremao.storemanager.item;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.caremao.storemanager.auth.Actor;
import com.caremao.storemanager.item.ItemCsvImporter.ImportResult;
import com.caremao.storemanager.item.ItemDtos.ItemDto;
import com.caremao.storemanager.item.ItemDtos.ItemRequest;

import jakarta.validation.Valid;

/** Items de una tienda o un personaje; los permisos los resuelve InventoryService. */
@RestController
@RequestMapping("/api/inventories/{inventoryId}/items")
public class ItemController {

    private final ItemService items;
    private final ItemCsvImporter importer;

    public ItemController(ItemService items, ItemCsvImporter importer) {
        this.items = items;
        this.importer = importer;
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

    @PostMapping("/{itemId}/reveal")
    public ItemDto reveal(Actor actor, @PathVariable UUID inventoryId, @PathVariable UUID itemId) {
        return items.reveal(actor, inventoryId, itemId);
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Actor actor, @PathVariable UUID inventoryId, @PathVariable UUID itemId) {
        items.delete(actor, inventoryId, itemId);
    }

    @GetMapping("/template.csv")
    public ResponseEntity<byte[]> template(Actor actor, @PathVariable UUID inventoryId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla-items.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(importer.template(actor, inventoryId));
    }

    /**
     * Cuerpo {@code text/csv}, leído siempre como UTF-8. Con {@code dryRun} (por defecto) solo devuelve la
     * vista previa; sin él importa, y si hay errores responde 422 sin escribir nada.
     */
    @PostMapping(path = "/import", consumes = { "text/csv", "text/plain" })
    public ResponseEntity<ImportResult> importCsv(Actor actor, @PathVariable UUID inventoryId,
            @RequestBody byte[] body, @RequestParam(defaultValue = "true") boolean dryRun) {
        ImportResult result = importer.importCsv(actor, inventoryId, new String(body, StandardCharsets.UTF_8), dryRun);
        HttpStatus status = !dryRun && result.hasErrors() ? HttpStatus.UNPROCESSABLE_CONTENT : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }
}
