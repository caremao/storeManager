package com.caremao.storemanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.caremao.storemanager.support.ApiClient;
import com.caremao.storemanager.support.IntegrationTest;

/** Objetos ocultos: los ve y los vende solo el GM, que los menciona en la mesa. */
class HiddenItemsIT extends IntegrationTest {

    @Test
    void playersNeitherSeeNorOrderHiddenItems() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Trastienda", Map.of("visibleToPlayers", true, "requiresApproval", false));
        addItem(gm, shop, Map.of("name", "Cuerda", "valueCp", 100, "quantity", 5));
        String secret = addItem(gm, shop, Map.of("name", "Mapa del tesoro", "valueCp", 1_000, "quantity", 1,
                "hidden", true));
        String[] pj = createCharacter(gm, "Fisgón", 50);
        ApiClient player = join(pj[1]);

        var stock = player.get("/api/inventories/" + shop + "/items").json();
        assertThat(stock).hasSize(1);
        assertThat(findByName(stock, "Mapa del tesoro")).isNull();
        assertThat(player.post("/api/shops/" + shop + "/trades", Map.of("kind", "BUY", "itemId", secret, "quantity", 1))
                .status()).isEqualTo(404);
        assertThat(findByName(gm.get("/api/inventories/" + shop + "/items").json(), "Mapa del tesoro")
                .get("hidden").asBoolean()).isTrue();
    }

    @Test
    void theGmSellsAHiddenItemDirectlyAndTheCopyIsNotHidden() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Contrabando", Map.of("visibleToPlayers", true));
        String secret = addItem(gm, shop, Map.of("name", "Llave maestra", "valueCp", 2_000, "quantity", 1,
                "hidden", true));
        String[] pj = createCharacter(gm, "Ladrona", 30);
        ApiClient player = join(pj[1]);

        var sale = gm.post("/api/shops/" + shop + "/direct-trades",
                Map.of("kind", "BUY", "characterId", pj[0], "itemId", secret, "quantity", 1));
        assertThat(sale.status()).as(sale.body()).isEqualTo(201);
        var bag = player.get("/api/inventories/" + pj[0] + "/items").json();
        assertThat(findByName(bag, "Llave maestra").get("hidden").asBoolean()).isFalse();
    }

    @Test
    void playersCannotHideTheirOwnItemsNorShopItems() {
        ApiClient gm = newGm();
        String[] pj = createCharacter(gm, "Mañoso", 1);
        ApiClient player = join(pj[1]);
        addItem(player, pj[0], Map.of("name", "Daga", "hidden", true));
        assertThat(findByName(gm.get("/api/inventories/" + pj[0] + "/items").json(), "Daga").get("hidden").asBoolean())
                .isFalse();
    }
}
