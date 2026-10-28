package com.caremao.storemanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.RepeatedTest;

import com.caremao.storemanager.support.ApiClient;
import com.caremao.storemanager.support.ApiClient.Response;
import com.caremao.storemanager.support.IntegrationTest;

/** Dos jugadores piden la última poción al mismo tiempo: solo uno se la lleva. */
class TradeConcurrencyIT extends IntegrationTest {

    @RepeatedTest(5)
    void lastUnitIsSoldOnlyOnce() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Última poción", Map.of("visibleToPlayers", true, "requiresApproval", false));
        String potion = addItem(gm, shop, Map.of("name", "Potion of Healing", "quantity", 1, "valueCp", 100));
        String[] a = createCharacter(gm, "A", 10);
        String[] b = createCharacter(gm, "B", 10);
        ApiClient playerA = join(a[1]);
        ApiClient playerB = join(b[1]);

        Map<String, Object> buy = Map.of("kind", "BUY", "itemId", potion, "quantity", 1);
        CompletableFuture<Response> first = playerA.postAsync("/api/shops/" + shop + "/trades", buy);
        CompletableFuture<Response> second = playerB.postAsync("/api/shops/" + shop + "/trades", buy);
        List<Integer> statuses = List.of(first.join().status(), second.join().status());

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(gm.get("/api/inventories/" + shop + "/items").json().get(0).get("quantity").asInt()).isZero();
        long spent = (1_000 - purse(gm, a[0]).get("totalCp").asLong()) + (1_000 - purse(gm, b[0]).get("totalCp").asLong());
        assertThat(spent).as("solo un jugador pagó").isEqualTo(100);
    }
}
