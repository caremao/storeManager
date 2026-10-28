package com.caremao.storemanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest.BodyPublishers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.caremao.storemanager.common.Limits;
import com.caremao.storemanager.support.ApiClient;
import com.caremao.storemanager.support.IntegrationTest;

import tools.jackson.databind.JsonNode;

/** Entradas hostiles o fuera de rango: todas deben dar 4xx y no cambiar nada. */
class ValidationIT extends IntegrationTest {

    @Test
    void passphrasesLongerThanBcryptAcceptsAreRejected() {
        String name = "Larga " + UUID.randomUUID();
        assertThat(browser().post("/api/campaigns", Map.of("name", name, "passphrase", "a".repeat(73))).status())
                .isEqualTo(400);
        var accents = browser().post("/api/campaigns", Map.of("name", name, "passphrase", "ñ".repeat(40)));
        assertThat(accents.status()).as("40 eñes son 80 bytes").isEqualTo(400);
        assertThat(accents.json().get("detail").asString()).contains("72 bytes");

        ApiClient gm = newGm();
        assertThat(browser().post("/api/campaigns/" + slugOf(gm) + "/gm-login", Map.of("passphrase", "b".repeat(150)))
                .status()).isEqualTo(401);
    }

    @Test
    void numbersOutOfRangeAreRejected() {
        ApiClient gm = newGm();
        String[] pj = createCharacter(gm, "Contable", 0);
        String items = "/api/inventories/" + pj[0] + "/items";

        assertThat(gm.post(items, Map.of("name", "x", "quantity", Limits.MAX_QUANTITY + 1)).status()).isEqualTo(400);
        assertThat(gm.post(items, Map.of("name", "x", "valueCp", Limits.MAX_VALUE_CP + 1)).status()).isEqualTo(400);
        assertThat(gm.post(items, Map.of("name", "x", "weight", 2_000_000)).status()).isEqualTo(400);
        assertThat(gm.post("/api/inventories/" + pj[0] + "/currency",
                Map.of("copper", 0, "silver", 0, "gold", Limits.MAX_COINS + 1, "platinum", 0)).status()).isEqualTo(400);
        assertThat(gm.post(items, Map.of("name", "x", "quantity", Limits.MAX_QUANTITY)).status()).isEqualTo(201);
    }

    @Test
    void stackingBeyondTheMaximumFailsAndChangesNothing() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Flechería", Map.of("visibleToPlayers", true, "requiresApproval", false));
        String arrows = addItem(gm, shop, Map.of("name", "Flecha", "quantity", 10, "valueCp", 5));
        String[] pj = createCharacter(gm, "Arquera", 1);
        ApiClient player = join(pj[1]);
        addItem(player, pj[0], Map.of("name", "Flecha", "quantity", Limits.MAX_QUANTITY));

        var buy = player.post("/api/shops/" + shop + "/trades", Map.of("kind", "BUY", "itemId", arrows, "quantity", 1));
        assertThat(buy.status()).isEqualTo(409);
        assertThat(purse(player, pj[0]).get("totalCp").asLong()).as("no pagó").isEqualTo(100);
        assertThat(gm.get("/api/inventories/" + shop + "/items").json().get(0).get("quantity").asInt()).isEqualTo(10);
    }

    @Test
    void aPurseThatWouldOverflowRollsTheTradeBack() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Banco", Map.of("unlimitedFunds", true));
        String[] pj = createCharacter(gm, "Magnate", 0);
        String gem = addItem(gm, pj[0], Map.of("name", "Diamante", "quantity", 10_000, "valueCp", Limits.MAX_VALUE_CP));

        // 10.000 × 100 millones de po no entran en una bolsa: se rechaza y la gema no se mueve.
        var sale = gm.post("/api/shops/" + shop + "/direct-trades", Map.of("kind", "SELL", "characterId", pj[0],
                "itemId", gem, "quantity", 10_000, "unitPriceCp", Limits.MAX_VALUE_CP));
        assertThat(sale.status()).as(sale.body()).isEqualTo(400);
        assertThat(findByName(gm.get("/api/inventories/" + pj[0] + "/items").json(), "Diamante").get("quantity").asInt())
                .isEqualTo(10_000);
    }

    @Test
    void itemPropertiesAreBounded() {
        ApiClient gm = newGm();
        String[] pj = createCharacter(gm, "Coleccionista", 0);
        String items = "/api/inventories/" + pj[0] + "/items";

        assertThat(gm.post(items, Map.of("name", "Pesado", "properties", Map.of("x", "y".repeat(11_000)))).status())
                .isEqualTo(400);
        Map<String, Object> nested = new HashMap<>(Map.of("a", 1));
        for (int i = 0; i < 5; i++) {
            nested = new HashMap<>(Map.of("n", nested));
        }
        assertThat(gm.post(items, Map.of("name", "Hondo", "properties", nested)).status()).isEqualTo(400);

        Map<String, Object> srdLike = Map.of("damage", "1d8 Slashing", "properties", List.of("Versatile"));
        var ok = gm.post(items, Map.of("name", "Longsword", "properties", srdLike));
        assertThat(ok.status()).as(ok.body()).isEqualTo(201);
    }

    @Test
    void malformedBodiesGetAClearMessage() {
        ApiClient gm = newGm();
        var broken = gm.sendRaw("POST", "/api/inventories", BodyPublishers.ofString("{nope"), Map.of());
        assertThat(broken.status()).isEqualTo(400);
        JsonNode problem = broken.json();
        assertThat(problem.get("detail").asString()).contains("no es válido");
        assertThat(gm.post("/api/inventories", Map.of("kind", "DRAGON", "name", "x")).status()).isEqualTo(400);
    }
}
