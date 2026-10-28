package com.caremao.storemanager.history;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.caremao.storemanager.auth.Actor;
import com.caremao.storemanager.history.HistoryDtos.HistoryPage;

@RestController
public class HistoryController {

    private final HistoryService history;

    public HistoryController(HistoryService history) {
        this.history = history;
    }

    @GetMapping("/api/history")
    public HistoryPage list(Actor actor,
            @RequestParam(required = false) UUID inventoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return history.list(actor, inventoryId, page, size);
    }
}
