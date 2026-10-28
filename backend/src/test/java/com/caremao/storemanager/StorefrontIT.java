package com.caremao.storemanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.caremao.storemanager.support.ApiClient;
import com.caremao.storemanager.support.IntegrationTest;

import tools.jackson.databind.JsonNode;

/** Vitrina pública: lo que abre el QR de una tienda, sin sesión. */
class StorefrontIT extends IntegrationTest {

    private static String tokenOf(String storefrontUrl) {
        assertThat(storefrontUrl).startsWith("http://mesa.test/vitrina#");
        return storefrontUrl.substring(storefrontUrl.indexOf('#') + 1);
    }

    @Test
    void anyoneWithTheQrSeesTheVisibleStockAndNothingElse() {
        ApiClient gm = newGm();
        var shop = gm.post("/api/inventories", Map.of("kind", "SHOP", "name", "Botica", "visibleToPlayers", true,
                "sellMarkupPct", 110, "purse", Map.of("copper", 0, "silver", 0, "gold", 999, "platinum", 0))).json();
        String shopId = shop.get("id").asString();
        addItem(gm, shopId, Map.of("name", "Poción", "valueCp", 5_000, "quantity", 3, "notes", "secreto del GM"));
        addItem(gm, shopId, Map.of("name", "Veneno", "valueCp", 9_000, "quantity", 1, "hidden", true));
        String token = tokenOf(shop.get("storefrontUrl").asString());

        var front = browser().get("/api/storefront/" + token);
        assertThat(front.status()).as(front.body()).isEqualTo(200);
        JsonNode body = front.json();
        assertThat(body.get("name").asString()).isEqualTo("Botica");
        assertThat(body.get("items")).hasSize(1);
        JsonNode potion = body.get("items").get(0);
        assertThat(potion.get("name").asString()).isEqualTo("Poción");
        assertThat(potion.get("priceCp").asLong()).isEqualTo(5_500);
        assertThat(body.get("shopId").isNull()).as("sin sesión no hay a dónde pedir").isTrue();
        assertThat(front.body()).doesNotContain("secreto del GM").doesNotContain("Veneno")
                .doesNotContain("purse").doesNotContain("totalCp").doesNotContain("valueCp").doesNotContain("campaign");
    }

    @Test
    void aPlayerOfTheCampaignGetsTheWayToTheCounter() {
        ApiClient gm = newGm();
        JsonNode shop = gm.post("/api/inventories", Map.of("kind", "SHOP", "name", "Herrería",
                "visibleToPlayers", true)).json();
        String token = tokenOf(shop.get("storefrontUrl").asString());
        String[] pj = createCharacter(gm, "Cliente", 1);

        assertThat(join(pj[1]).get("/api/storefront/" + token).json().get("shopId").asString())
                .isEqualTo(shop.get("id").asString());

        ApiClient otherGm = newGm();
        String[] stranger = createCharacter(otherGm, "Forastero", 1);
        assertThat(join(stranger[1]).get("/api/storefront/" + token).json().get("shopId").isNull())
                .as("un jugador de otra campaña solo mira").isTrue();
    }

    @Test
    void closedShopsAndRevokedQrsShowNothing() {
        ApiClient gm = newGm();
        JsonNode shop = gm.post("/api/inventories", Map.of("kind", "SHOP", "name", "Cerrada")).json();
        String shopId = shop.get("id").asString();
        String token = tokenOf(shop.get("storefrontUrl").asString());
        assertThat(browser().get("/api/storefront/" + token).status()).as("cerrada").isEqualTo(404);

        gm.patch("/api/inventories/" + shopId, Map.of("visibleToPlayers", true));
        assertThat(browser().get("/api/storefront/" + token).status()).isEqualTo(200);

        var renewed = gm.post("/api/inventories/" + shopId + "/storefront-token", null);
        String fresh = tokenOf(renewed.json().get("storefrontUrl").asString());
        assertThat(fresh).isNotEqualTo(token);
        assertThat(browser().get("/api/storefront/" + token).status()).as("el QR viejo ya no abre").isEqualTo(404);
        assertThat(browser().get("/api/storefront/" + fresh).status()).isEqualTo(200);
        assertThat(browser().get("/api/storefront/no-existe").status()).isEqualTo(404);
    }

    @Test
    void onlyTheGmSeesTheStorefrontQr() {
        ApiClient gm = newGm();
        String shopId = createShop(gm, "Visible", Map.of("visibleToPlayers", true));
        String[] pj = createCharacter(gm, "Curioso", 1);
        ApiClient player = join(pj[1]);
        assertThat(player.get("/api/inventories/" + shopId).json().get("storefrontUrl").isNull()).isTrue();
        assertThat(player.post("/api/inventories/" + shopId + "/storefront-token", null).status()).isEqualTo(403);
    }
}
