package com.caremao.storemanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.caremao.storemanager.support.ApiClient;
import com.caremao.storemanager.support.IntegrationTest;

import tools.jackson.databind.JsonNode;

class AccessIT extends IntegrationTest {

    @Test
    void anonymousRequestsNeedASession() {
        ApiClient anonymous = browser();
        assertThat(anonymous.get("/api/me").status()).isEqualTo(401);
        assertThat(anonymous.get("/api/inventories").status()).isEqualTo(401);
        assertThat(anonymous.get("/api/config").status()).isEqualTo(200);
    }

    @Test
    void withoutAnAdminKeyThePortalDoesNotExist() {
        ApiClient anonymous = browser();
        assertThat(anonymous.get("/api/admin/session").status()).isEqualTo(404);
        assertThat(anonymous.post("/api/admin/login", Map.of("key", "cualquier-cosa-larga")).status()).isEqualTo(404);
    }

    @Test
    void playersOnlySeeTheirCharacterAndOpenShops() {
        ApiClient gm = newGm();
        String open = createShop(gm, "Abierta", Map.of("visibleToPlayers", true));
        String hidden = createShop(gm, "Cerrada", Map.of());
        String[] uno = createCharacter(gm, "Uno", 1);
        String[] otro = createCharacter(gm, "Otro", 1);
        ApiClient player = join(uno[1]);

        JsonNode visible = player.get("/api/inventories").json();
        assertThat(visible).hasSize(2);
        assertThat(findByName(visible, "Abierta")).isNotNull();
        assertThat(findByName(visible, "Uno").get("joinUrl").isNull()).as("el QR solo lo ve el GM").isTrue();

        assertThat(player.get("/api/inventories/" + hidden).status()).isEqualTo(404);
        assertThat(player.get("/api/inventories/" + otro[0]).status()).isEqualTo(404);
        assertThat(player.post("/api/inventories/" + open + "/items", Map.of("name", "Robo")).status())
                .isEqualTo(403);
        assertThat(player.post("/api/inventories", Map.of("kind", "SHOP", "name", "Mía")).status())
                .isEqualTo(403);
        assertThat(player.post("/api/inventories/" + uno[0] + "/currency",
                Map.of("copper", 0, "silver", 0, "gold", 5, "platinum", 0)).status())
                .as("el jugador administra su propia bolsa").isEqualTo(200);
    }

    @Test
    void campaignsAreIsolatedFromEachOther() {
        ApiClient gmA = newGm();
        ApiClient gmB = newGm();
        String shopA = createShop(gmA, "Tienda A", Map.of());
        assertThat(gmB.get("/api/inventories/" + shopA).status()).isEqualTo(404);
    }

    @Test
    void regeneratingTheQrRevokesOldSessions() {
        ApiClient gm = newGm();
        String[] pj = createCharacter(gm, "Pj", 1);
        ApiClient player = join(pj[1]);
        assertThat(player.get("/api/me").status()).isEqualTo(200);

        var regenerated = gm.post("/api/inventories/" + pj[0] + "/token", null);
        assertThat(regenerated.status()).isEqualTo(200);
        assertThat(regenerated.json().get("joinUrl").asString()).doesNotEndWith(pj[1]);

        assertThat(player.get("/api/me").status()).isEqualTo(401);
        assertThat(browser().post("/api/join", Map.of("token", pj[1])).status()).isEqualTo(404);
    }

    @Test
    void gmLogsInWithThePassphrase() {
        ApiClient gm = newGm();
        String slug = gm.get("/api/me").json().get("campaign").get("slug").asString();

        assertThat(browser().post("/api/campaigns/" + slug + "/gm-login", Map.of("passphrase", "otra")).status())
                .isEqualTo(401);
        ApiClient again = browser();
        assertThat(again.post("/api/campaigns/" + slug + "/gm-login", Map.of("passphrase", "secreto-largo")).status())
                .isEqualTo(200);
        assertThat(again.get("/api/me").json().get("role").asString()).isEqualTo("GM");
        assertThat(again.post("/api/logout", null).status()).isEqualTo(204);
        assertThat(again.get("/api/me").status()).isEqualTo(401);
    }

    @Test
    void srdCatalogIsSearchable() {
        ApiClient gm = newGm();
        JsonNode results = gm.get("/api/srd/items?q=longsw").json();
        JsonNode longsword = findByName(results, "Longsword");
        assertThat(longsword).isNotNull();
        assertThat(longsword.get("valueCp").asLong()).isEqualTo(1_500);
        assertThat(longsword.get("properties").get("damage").asString()).isEqualTo("1d8 Slashing");
    }
}
