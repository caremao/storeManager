package com.caremao.storemanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.caremao.storemanager.support.ApiClient;
import com.caremao.storemanager.support.ApiClient.Response;
import com.caremao.storemanager.support.IntegrationTest;

import tools.jackson.databind.JsonNode;

/** El portal /admin: ve todas las campañas, entra como GM, cambia contraseñas, cierra sesiones y borra. */
@TestPropertySource(properties = "app.admin-key=" + AdminIT.KEY)
class AdminIT extends IntegrationTest {

    static final String KEY = "clave-admin-de-prueba-2026";

    @Test
    void theKeyOpensASessionWhoseCookieOnlyTravelsToTheAdminApi() {
        ApiClient browser = browser();
        assertThat(browser.get("/api/admin/campaigns").status()).isEqualTo(401);
        assertThat(browser.post("/api/admin/login", Map.of("key", "no-es-la-clave")).status()).isEqualTo(401);

        Response login = browser.post("/api/admin/login", Map.of("key", KEY));
        assertThat(login.status()).as(login.body()).isEqualTo(204);
        assertThat(login.header("Set-Cookie")).contains("sm_admin=", "Path=/api/admin", "HttpOnly", "SameSite=Strict");
        assertThat(browser.get("/api/admin/session").status()).isEqualTo(204);
        assertThat(browser.get("/api/me").status()).as("el portal no es una sesión de GM").isEqualTo(401);

        assertThat(browser.post("/api/admin/logout", null).status()).isEqualTo(204);
        assertThat(browser.get("/api/admin/session").status()).isEqualTo(401);
    }

    @Test
    void wrongKeysAreRateLimitedPerClient() {
        for (int i = 0; i < 5; i++) {
            assertThat(loginFrom("10.9.0.1", "mala").status()).isEqualTo(401);
        }
        assertThat(loginFrom("10.9.0.1", KEY).status()).as("bloqueada esa IP, ni la clave buena entra").isEqualTo(429);
        assertThat(loginFrom("10.9.0.2", KEY).status()).isEqualTo(204);
    }

    @Test
    void writesFromOtherSitesAreRejected() {
        assertThat(browser().send("POST", "/api/admin/login", Map.of("key", KEY),
                Map.of("Sec-Fetch-Site", "cross-site")).status()).isEqualTo(403);
    }

    @Test
    void listsEveryCampaignWithWhatItHas() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Herrería", Map.of("visibleToPlayers", true));
        String sword = addItem(gm, shop, Map.of("name", "Espada", "quantity", 2, "valueCp", 1_500));
        addItem(gm, shop, Map.of("name", "Escudo", "quantity", 1, "valueCp", 1_000));
        String[] pj = createCharacter(gm, "Brenna", 30);
        ApiClient player = join(pj[1]);
        assertThat(player.post("/api/shops/" + shop + "/trades",
                Map.of("kind", "BUY", "itemId", sword, "quantity", 1)).status()).isEqualTo(201);
        ApiClient other = newGm();

        JsonNode campaigns = admin().get("/api/admin/campaigns").json();
        JsonNode campaign = byId(campaigns, campaignIdOf(gm));
        assertThat(byId(campaigns, campaignIdOf(other))).as("ve todas las campañas").isNotNull();
        assertThat(campaign.get("openTrades").asLong()).isEqualTo(1);
        assertThat(campaign.get("lastActivityAt").isNull()).isFalse();

        JsonNode herreria = byId(campaign.get("inventories"), shop);
        assertThat(herreria.get("kind").asString()).isEqualTo("SHOP");
        assertThat(herreria.get("items").asLong()).isEqualTo(2);
        assertThat(herreria.get("visibleToPlayers").asBoolean()).isTrue();
        JsonNode brenna = byId(campaign.get("inventories"), pj[0]);
        assertThat(brenna.get("totalCp").asLong()).isEqualTo(3_000);
        assertThat(brenna.get("visibleToPlayers").isNull()).isTrue();

        JsonNode sessions = campaign.get("sessions");
        assertThat(sessions).hasSize(2);
        assertThat(sessions.findValuesAsString("role")).containsExactlyInAnyOrder("GM", "PLAYER");
        assertThat(sessions.findValuesAsString("characterName")).contains("Brenna");
        assertThat(campaigns.toString()).as("ni hashes ni tokens").doesNotContain("$2a$", pj[1]);
    }

    @Test
    void enteringAsGmNeedsNoPassphraseAndReplacesTheBrowsersSession() {
        String a = campaignIdOf(newGm());
        String b = campaignIdOf(newGm());
        ApiClient admin = admin();

        Response entered = admin.post("/api/admin/campaigns/" + a + "/gm-session", null);
        assertThat(entered.status()).as(entered.body()).isEqualTo(200);
        assertThat(admin.get("/api/me").json().get("campaign").get("id").asString()).isEqualTo(a);
        assertThat(gmSessions(admin, a)).isEqualTo(2);

        assertThat(admin.post("/api/admin/campaigns/" + b + "/gm-session", null).status()).isEqualTo(200);
        assertThat(admin.get("/api/me").json().get("campaign").get("id").asString()).isEqualTo(b);
        assertThat(gmSessions(admin, a)).as("la sesión anterior del navegador se cerró").isEqualTo(1);
        assertThat(admin.get("/api/admin/session").status()).as("el portal sigue abierto").isEqualTo(204);

        assertThat(browser().post("/api/admin/campaigns/" + a + "/gm-session", null).status())
                .as("sin sesión del portal no hay atajo").isEqualTo(401);
        assertThat(admin.post("/api/admin/campaigns/" + UUID.randomUUID() + "/gm-session", null).status())
                .isEqualTo(404);
    }

    @Test
    void aNewPassphraseLocksOutTheOldOneAndItsSessions() {
        ApiClient gm = newGm();
        String id = campaignIdOf(gm);
        String slug = slugOf(gm);
        String[] pj = createCharacter(gm, "Testigo", 0);
        ApiClient player = join(pj[1]);
        ApiClient admin = admin();

        assertThat(admin.put("/api/admin/campaigns/" + id + "/passphrase", Map.of("passphrase", "corta")).status())
                .isEqualTo(400);
        assertThat(admin.put("/api/admin/campaigns/" + id + "/passphrase",
                Map.of("passphrase", "contraseña-nueva")).status()).isEqualTo(204);

        assertThat(gm.get("/api/me").status()).as("las sesiones de GM se cierran").isEqualTo(401);
        assertThat(player.get("/api/me").status()).as("las de los jugadores no").isEqualTo(200);
        assertThat(browser().post("/api/campaigns/" + slug + "/gm-login", Map.of("passphrase", "secreto-largo")).status())
                .isEqualTo(401);
        assertThat(browser().post("/api/campaigns/" + slug + "/gm-login",
                Map.of("passphrase", "contraseña-nueva")).status()).isEqualTo(200);
    }

    @Test
    void closingASessionLogsThatBrowserOutAndCutsItsEvents() throws Exception {
        ApiClient gm = newGm();
        String id = campaignIdOf(gm);
        String[] pj = createCharacter(gm, "Expulsado", 0);
        ApiClient player = join(pj[1]);
        HttpResponse<Stream<String>> stream = player.openEvents().get(5, TimeUnit.SECONDS);
        assertThat(stream.statusCode()).isEqualTo(200);
        CompletableFuture<Long> closed = CompletableFuture.supplyAsync(() -> stream.body().count());

        ApiClient admin = admin();
        JsonNode session = null;
        for (JsonNode s : byId(admin.get("/api/admin/campaigns").json(), id).get("sessions")) {
            if (s.get("role").asString().equals("PLAYER")) {
                session = s;
            }
        }
        assertThat(admin.delete("/api/admin/sessions/" + session.get("id").asString()).status()).isEqualTo(204);

        assertThat(player.get("/api/me").status()).isEqualTo(401);
        assertThat(gm.get("/api/me").status()).isEqualTo(200);
        assertThat(closed.get(10, TimeUnit.SECONDS)).as("el servidor corta su SSE").isNotNull();
        assertThat(admin.delete("/api/admin/sessions/" + session.get("id").asString()).status()).isEqualTo(404);
    }

    @Test
    void deletingACampaignRemovesEverythingInIt() {
        ApiClient gm = newGm();
        String id = campaignIdOf(gm);
        String slug = slugOf(gm);
        String shop = createShop(gm, "Efímera", Map.of());
        addItem(gm, shop, Map.of("name", "Vela", "quantity", 3));
        String[] pj = createCharacter(gm, "Fugaz", 1);
        ApiClient player = join(pj[1]);
        ApiClient survivor = newGm();
        ApiClient admin = admin();

        assertThat(admin.delete("/api/admin/campaigns/" + id).status()).isEqualTo(204);

        assertThat(gm.get("/api/me").status()).isEqualTo(401);
        assertThat(player.get("/api/me").status()).isEqualTo(401);
        assertThat(browser().post("/api/join", Map.of("token", pj[1])).status()).isEqualTo(404);
        assertThat(browser().post("/api/campaigns/" + slug + "/gm-login", Map.of("passphrase", "secreto-largo")).status())
                .isEqualTo(401);
        JsonNode campaigns = admin.get("/api/admin/campaigns").json();
        assertThat(byId(campaigns, id)).isNull();
        assertThat(byId(campaigns, campaignIdOf(survivor))).isNotNull();
        assertThat(survivor.get("/api/me").status()).isEqualTo(200);
        assertThat(admin.delete("/api/admin/campaigns/" + id).status()).isEqualTo(404);
    }

    private ApiClient admin() {
        ApiClient admin = browser();
        Response login = admin.post("/api/admin/login", Map.of("key", KEY));
        assertThat(login.status()).as(login.body()).isEqualTo(204);
        return admin;
    }

    private Response loginFrom(String ip, String key) {
        return browser().send("POST", "/api/admin/login", Map.of("key", key), Map.of("X-Forwarded-For", ip));
    }

    private static String campaignIdOf(ApiClient gm) {
        return gm.get("/api/me").json().get("campaign").get("id").asString();
    }

    private static long gmSessions(ApiClient admin, String campaignId) {
        JsonNode sessions = byId(admin.get("/api/admin/campaigns").json(), campaignId).get("sessions");
        return sessions.findValuesAsString("role").stream().filter("GM"::equals).count();
    }

    private static JsonNode byId(JsonNode array, String id) {
        for (JsonNode node : array) {
            if (node.get("id").asString().equals(id)) {
                return node;
            }
        }
        return null;
    }
}
