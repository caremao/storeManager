package com.caremao.storemanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.caremao.storemanager.support.ApiClient;
import com.caremao.storemanager.support.IntegrationTest;

import tools.jackson.databind.JsonNode;

/** El recorrido de la mesa: el GM arma la tienda, el jugador entra por QR, pide, el GM aprueba. */
class ShopFlowIT extends IntegrationTest {

    @Test
    void purchaseWithGmApprovalMovesStockAndCoins() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Botica de Wren", Map.of("visibleToPlayers", true));
        String potion = addItem(gm, shop, Map.of("name", "Potion of Healing", "type", "POTION",
                "quantity", 2, "valueCp", 5_000, "notes", "solo para el GM"));
        String[] thorin = createCharacter(gm, "Thorin", 100);
        ApiClient player = join(thorin[1]);

        assertThat(player.get("/api/me").json().get("characterName").asString()).isEqualTo("Thorin");
        JsonNode stock = player.get("/api/inventories/" + shop + "/items").json();
        assertThat(stock.get(0).get("priceCp").asLong()).isEqualTo(5_000);
        assertThat(stock.get(0).get("notes").isNull()).as("las notas de la tienda son del GM").isTrue();
        assertThat(player.get("/api/inventories/" + shop).json().get("purse").isNull())
                .as("el jugador no ve la caja de la tienda").isTrue();

        var requested = player.post("/api/shops/" + shop + "/trades",
                Map.of("kind", "BUY", "itemId", potion, "quantity", 1));
        assertThat(requested.status()).as(requested.body()).isEqualTo(201);
        assertThat(requested.json().get("status").asString()).isEqualTo("PENDING");
        String trade = requested.json().get("id").asString();

        assertThat(gm.get("/api/trades?status=PENDING").json()).hasSize(1);
        assertThat(player.post("/api/trades/" + trade + "/approve", null).status())
                .as("un jugador no aprueba").isEqualTo(403);

        var approved = gm.post("/api/trades/" + trade + "/approve", null);
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        assertThat(approved.json().get("status").asString()).isEqualTo("APPROVED");

        assertThat(gm.get("/api/inventories/" + shop + "/items").json().get(0).get("quantity").asInt()).isEqualTo(1);
        assertThat(purse(gm, shop).get("gold").asInt()).isEqualTo(50);
        assertThat(purse(player, thorin[0]).get("gold").asInt()).isEqualTo(50);
        JsonNode bag = player.get("/api/inventories/" + thorin[0] + "/items").json();
        assertThat(findByName(bag, "Potion of Healing").get("quantity").asInt()).isEqualTo(1);
        assertThat(findByName(bag, "Potion of Healing").get("notes").isNull()).isTrue();

        JsonNode history = player.get("/api/history").json().get("entries");
        assertThat(history.get(0).get("action").asString()).isEqualTo("TRADE_EXECUTED");
        assertThat(gm.post("/api/trades/" + trade + "/approve", null).status())
                .as("no se aprueba dos veces").isEqualTo(409);
    }

    @Test
    void sellingAlwaysWaitsForTheGmEvenInAShopWithoutApproval() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Casa de empeño", Map.of("visibleToPlayers", true,
                "requiresApproval", false, "buybackPct", 50,
                "purse", Map.of("copper", 0, "silver", 0, "gold", 100, "platinum", 0)));
        String[] mira = createCharacter(gm, "Mira", 0);
        ApiClient player = join(mira[1]);
        // El valor real lo fija el GM; aun así la venta espera, porque el jugador puede editar sus items.
        String gem = addItem(gm, mira[0], Map.of("name", "Ágata", "quantity", 2, "valueCp", 1_000));

        var sold = player.post("/api/shops/" + shop + "/trades", Map.of("kind", "SELL", "itemId", gem, "quantity", 1));
        assertThat(sold.status()).as(sold.body()).isEqualTo(201);
        assertThat(sold.json().get("status").asString()).isEqualTo("PENDING");
        assertThat(sold.json().get("totalCp").asLong()).isEqualTo(500);
        assertThat(purse(player, mira[0]).get("gold").asInt()).as("nada se movió todavía").isZero();

        var approved = gm.post("/api/trades/" + sold.json().get("id").asString() + "/approve", null);
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        assertThat(purse(player, mira[0]).get("gold").asInt()).isEqualTo(5);
        assertThat(purse(gm, shop).get("totalCp").asLong()).isEqualTo(9_500);
        assertThat(findByName(gm.get("/api/inventories/" + shop + "/items").json(), "Ágata").get("quantity").asInt())
                .isEqualTo(1);
        assertThat(findByName(player.get("/api/inventories/" + mira[0] + "/items").json(), "Ágata")
                .get("quantity").asInt()).isEqualTo(1);
    }

    @Test
    void cannotBuyWithoutEnoughGold() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Armería", Map.of("visibleToPlayers", true, "requiresApproval", false));
        String sword = addItem(gm, shop, Map.of("name", "Longsword", "quantity", 1, "valueCp", 1_500));
        String[] pobre = createCharacter(gm, "Pobre", 1);
        ApiClient player = join(pobre[1]);

        var response = player.post("/api/shops/" + shop + "/trades",
                Map.of("kind", "BUY", "itemId", sword, "quantity", 1));
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.json().get("detail").asString()).contains("No te alcanza");
        assertThat(gm.get("/api/trades").json()).isEmpty();
    }

    @Test
    void gmCanRecordADirectSaleAtTheTable() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Herrería", Map.of());
        String rope = addItem(gm, shop, Map.of("name", "Rope", "quantity", 5, "valueCp", 100,
                "unlimitedStock", true));
        String[] ana = createCharacter(gm, "Ana", 10);

        var sale = gm.post("/api/shops/" + shop + "/direct-trades",
                Map.of("kind", "BUY", "characterId", ana[0], "itemId", rope, "quantity", 3, "unitPriceCp", 80));
        assertThat(sale.status()).as(sale.body()).isEqualTo(201);
        assertThat(sale.json().get("totalCp").asLong()).isEqualTo(240);
        assertThat(gm.get("/api/inventories/" + shop + "/items").json().get(0).get("quantity").asInt())
                .as("stock ilimitado no baja").isEqualTo(5);
        assertThat(purse(gm, ana[0]).get("totalCp").asLong()).isEqualTo(760);
    }

    @Test
    void eventsArriveOverSse() throws Exception {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Mercado", Map.of("visibleToPlayers", true));
        String[] lia = createCharacter(gm, "Lia", 1);
        ApiClient player = join(lia[1]);

        HttpResponse<Stream<String>> stream = player.openEvents().get(5, TimeUnit.SECONDS);
        assertThat(stream.statusCode()).isEqualTo(200);
        CompletableFuture<String> change = CompletableFuture.supplyAsync(() -> stream.body()
                .filter(line -> line.startsWith("data:") && line.contains(shop))
                .findFirst()
                .orElse(null));

        addItem(gm, shop, Map.of("name", "Torch", "quantity", 10, "valueCp", 1));

        assertThat(change.get(10, TimeUnit.SECONDS)).contains("inventoryIds");
    }
}
