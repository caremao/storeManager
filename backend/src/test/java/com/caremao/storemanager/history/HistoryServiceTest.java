package com.caremao.storemanager.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HistoryServiceTest {

    @Test
    void computeChangesKeepsOnlyChangedFields() {
        Map<String, Object> before = new HashMap<>(Map.of("name", "Cuerda", "quantity", 1));
        before.put("notes", null);
        Map<String, Object> after = new HashMap<>(Map.of("name", "Cuerda", "quantity", 3));
        after.put("notes", null);

        Map<String, Object> changes = HistoryService.computeChanges(before, after);

        assertThat(changes).containsOnlyKeys("quantity");
        assertThat(changes.get("quantity")).isEqualTo(Map.of("old", 1, "new", 3));
    }

    @Test
    void computeChangesIsEmptyWhenNothingChanged() {
        assertThat(HistoryService.computeChanges(Map.of("a", 1), Map.of("a", 1))).isEmpty();
    }
}
