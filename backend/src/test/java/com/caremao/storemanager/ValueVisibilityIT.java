package com.caremao.storemanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.caremao.storemanager.support.ApiClient;
import com.caremao.storemanager.support.IntegrationTest;

import tools.jackson.databind.JsonNode;

/** Valor real (lo sabe el GM y quien tasa) frente a valor percibido (lo que cree el dueño). */
class ValueVisibilityIT extends IntegrationTest {

    @Test
    void playersSeeTheirPerceivedValueUntilTheGmRevealsTheRealOne() {
        ApiClient gm = newGm();
        String[] pj = createCharacter(gm, "Saqueadora", 0);
        ApiClient player = join(pj[1]);
        String idol = addItem(gm, pj[0], Map.of("name", "Ídolo de jade", "valueCp", 50_000,
                "perceivedValueCp", 500, "valueRevealed", false));

        JsonNode seen = findByName(player.get("/api/inventories/" + pj[0] + "/items").json(), "Ídolo de jade");
        assertThat(seen.get("valueCp").isNull()).as("el real no le llega").isTrue();
        assertThat(seen.get("perceivedValueCp").asLong()).isEqualTo(500);
        assertThat(seen.get("valueRevealed").asBoolean()).isFalse();
        assertThat(findByName(gm.get("/api/inventories/" + pj[0] + "/items").json(), "Ídolo de jade")
                .get("valueCp").asLong()).isEqualTo(50_000);

        var revealed = gm.post("/api/inventories/" + pj[0] + "/items/" + idol + "/reveal", null);
        assertThat(revealed.status()).as(revealed.body()).isEqualTo(200);
        seen = findByName(player.get("/api/inventories/" + pj[0] + "/items").json(), "Ídolo de jade");
        assertThat(seen.get("valueCp").asLong()).isEqualTo(50_000);
        assertThat(seen.get("valueRevealed").asBoolean()).isTrue();
        assertThat(player.post("/api/inventories/" + pj[0] + "/items/" + idol + "/reveal", null).status())
                .as("revelar es cosa del GM").isEqualTo(403);
    }

    @Test
    void aPlayerCannotSetTheRealValueNorRevealIt() {
        ApiClient gm = newGm();
        String[] pj = createCharacter(gm, "Tramposo", 0);
        ApiClient player = join(pj[1]);
        String gem = addItem(gm, pj[0], Map.of("name", "Gema", "valueCp", 100, "valueRevealed", false));

        Map<String, Object> edit = new HashMap<>(Map.of("name", "Gema", "valueCp", 9_999_999, "valueRevealed", true,
                "perceivedValueCp", 9_999_999));
        assertThat(player.put("/api/inventories/" + pj[0] + "/items/" + gem, edit).status()).isEqualTo(200);

        JsonNode asGm = findByName(gm.get("/api/inventories/" + pj[0] + "/items").json(), "Gema");
        assertThat(asGm.get("valueCp").asLong()).as("el real no cambia").isEqualTo(100);
        assertThat(asGm.get("valueRevealed").asBoolean()).isFalse();
        assertThat(asGm.get("perceivedValueCp").asLong()).as("lo que cree, sí").isEqualTo(9_999_999);

        String own = addItem(player, pj[0], Map.of("name", "Piedra rara", "valueCp", 5_000_000,
                "perceivedValueCp", 5_000_000));
        JsonNode created = findByName(gm.get("/api/inventories/" + pj[0] + "/items").json(), "Piedra rara");
        assertThat(created.get("valueCp").isNull()).as("lo que crea un jugador no tiene valor real").isTrue();
        assertThat(created.get("valueRevealed").asBoolean()).isFalse();
        assertThat(own).isNotBlank();
    }

    @Test
    void shopsAppraiseWithTheRealValue() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Tasador", Map.of("visibleToPlayers", true, "buybackPct", 50));
        String[] pj = createCharacter(gm, "Vendedora", 0);
        ApiClient player = join(pj[1]);
        String ring = addItem(gm, pj[0], Map.of("name", "Anillo", "valueCp", 20_000,
                "perceivedValueCp", 100, "valueRevealed", false));
        String junk = addItem(player, pj[0], Map.of("name", "Chatarra", "perceivedValueCp", 1_000_000));

        var sale = player.post("/api/shops/" + shop + "/trades", Map.of("kind", "SELL", "itemId", ring, "quantity", 1));
        assertThat(sale.json().get("unitPriceCp").asLong()).as("50% del real, no del percibido").isEqualTo(10_000);

        var unknown = player.post("/api/shops/" + shop + "/trades", Map.of("kind", "SELL", "itemId", junk, "quantity", 1));
        assertThat(unknown.json().get("unitPriceCp").isNull()).as("sin valor real, el GM propone").isTrue();
        assertThat(gm.post("/api/trades/" + unknown.json().get("id").asString() + "/approve", null).status())
                .isEqualTo(400);
    }

    @Test
    void playerHistoryDoesNotLeakTheRealValue() {
        ApiClient gm = newGm();
        String[] pj = createCharacter(gm, "Curiosa", 0);
        ApiClient player = join(pj[1]);
        String map = addItem(gm, pj[0], Map.of("name", "Mapa", "valueCp", 77_700, "valueRevealed", false));
        gm.put("/api/inventories/" + pj[0] + "/items/" + map, Map.of("name", "Mapa", "valueCp", 88_800,
                "valueRevealed", false));

        String playerHistory = player.get("/api/history").body();
        assertThat(playerHistory).contains("Mapa").doesNotContain("77700").doesNotContain("88800");
        assertThat(gm.get("/api/history?inventoryId=" + pj[0]).body()).contains("88800");
    }
}
