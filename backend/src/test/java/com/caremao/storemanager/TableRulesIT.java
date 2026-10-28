package com.caremao.storemanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.caremao.storemanager.support.ApiClient;
import com.caremao.storemanager.support.IntegrationTest;

/** Reglas de la mesa que decide el GM. */
class TableRulesIT extends IntegrationTest {

    private static final Map<String, Object> PLUS_10_GOLD = Map.of("copper", 0, "silver", 0, "gold", 10, "platinum", 0);
    private static final Map<String, Object> MINUS_1_GOLD = Map.of("copper", 0, "silver", 0, "gold", -1, "platinum", 0);

    @Test
    void byDefaultPlayersMayAddCoins() {
        ApiClient gm = newGm();
        String[] pj = createCharacter(gm, "Saqueador", 0);
        ApiClient player = join(pj[1]);

        assertThat(player.get("/api/me").json().get("campaign").get("playersCanAddCoins").asBoolean()).isTrue();
        assertThat(player.post("/api/inventories/" + pj[0] + "/currency", PLUS_10_GOLD).status()).isEqualTo(200);
    }

    @Test
    void gmCanForbidPlayersFromAddingCoinsButTheyCanStillSpend() {
        ApiClient gm = newGm();
        String[] pj = createCharacter(gm, "Honrado", 5);
        ApiClient player = join(pj[1]);

        assertThat(player.patch("/api/campaign", Map.of("playersCanAddCoins", false)).status())
                .as("solo el GM cambia las reglas").isEqualTo(403);
        var updated = gm.patch("/api/campaign", Map.of("playersCanAddCoins", false));
        assertThat(updated.status()).as(updated.body()).isEqualTo(200);
        assertThat(updated.json().get("playersCanAddCoins").asBoolean()).isFalse();

        var added = player.post("/api/inventories/" + pj[0] + "/currency", PLUS_10_GOLD);
        assertThat(added.status()).isEqualTo(403);
        assertThat(added.json().get("detail").asString()).contains("las da el GM");
        assertThat(player.post("/api/inventories/" + pj[0] + "/currency", MINUS_1_GOLD).status()).isEqualTo(200);
        assertThat(gm.post("/api/inventories/" + pj[0] + "/currency", PLUS_10_GOLD).status())
                .as("el GM sí puede dar monedas").isEqualTo(200);
        assertThat(purse(gm, pj[0]).get("gold").asInt()).isEqualTo(14);
    }
}
