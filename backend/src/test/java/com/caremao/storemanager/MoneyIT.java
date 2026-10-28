package com.caremao.storemanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.caremao.storemanager.support.ApiClient;
import com.caremao.storemanager.support.IntegrationTest;

import tools.jackson.databind.JsonNode;

/** Precios en cualquier moneda y conversión de la bolsa. */
class MoneyIT extends IntegrationTest {

    @Test
    void priceKeepsTheDenominationItWasSetIn() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Mercado", Map.of("visibleToPlayers", true, "requiresApproval", false));
        String torch = addItem(gm, shop, Map.of("name", "Torch", "quantity", 5, "valueCp", 1,
                "priceDenomination", "COPPER"));
        String rope = addItem(gm, shop, Map.of("name", "Rope", "quantity", 5, "valueCp", 30,
                "priceDenomination", "SILVER"));

        JsonNode stock = gm.get("/api/inventories/" + shop + "/items").json();
        assertThat(findByName(stock, "Torch").get("priceDenomination").asString()).isEqualTo("COPPER");
        assertThat(findByName(stock, "Rope").get("priceDenomination").asString()).isEqualTo("SILVER");

        String[] pj = createCharacter(gm, "Compradora", 1);
        ApiClient player = join(pj[1]);
        var bought = player.post("/api/shops/" + shop + "/trades", Map.of("kind", "BUY", "itemId", rope, "quantity", 1));
        assertThat(bought.status()).as(bought.body()).isEqualTo(201);
        assertThat(bought.json().get("priceDenomination").asString()).isEqualTo("SILVER");
        JsonNode bag = player.get("/api/inventories/" + pj[0] + "/items").json();
        assertThat(findByName(bag, "Rope").get("priceDenomination").asString())
                .as("la copia conserva la moneda").isEqualTo("SILVER");

        var noDenomination = gm.post("/api/inventories/" + shop + "/items", Map.of("name", "Cuerda", "valueCp", 100));
        assertThat(noDenomination.json().get("priceDenomination").isNull()).as("null = oro").isTrue();
        assertThat(torch).isNotBlank();
    }

    @Test
    void playersCanConvertCoinsEvenWhenTheyCannotAddThem() {
        ApiClient gm = newGm();
        gm.patch("/api/campaign", Map.of("playersCanAddCoins", false));
        String[] pj = createCharacter(gm, "Cambista", 3);
        ApiClient player = join(pj[1]);

        var converted = player.post("/api/inventories/" + pj[0] + "/currency/convert",
                Map.of("from", "GOLD", "to", "SILVER", "amount", 2));
        assertThat(converted.status()).as(converted.body()).isEqualTo(200);
        JsonNode purse = converted.json().get("purse");
        assertThat(purse.get("gold").asInt()).isEqualTo(1);
        assertThat(purse.get("silver").asInt()).isEqualTo(20);
        assertThat(purse.get("totalCp").asLong()).as("convertir no crea valor").isEqualTo(300);
    }
}
