package com.caremao.storemanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.caremao.storemanager.support.ApiClient;
import com.caremao.storemanager.support.ApiClient.Response;
import com.caremao.storemanager.support.IntegrationTest;

/** Cabeceras, origen de las escrituras, tamaño del pedido y límite de intentos de login. */
class SecurityIT extends IntegrationTest {

    private static final Map<String, Object> SHOP = Map.of("kind", "SHOP", "name", "Tienda");

    @Test
    void responsesCarrySecurityHeaders() {
        ApiClient anonymous = browser();
        for (String path : new String[] { "/", "/api/config" }) {
            Response r = anonymous.get(path);
            assertThat(r.header("Content-Security-Policy")).as(path).contains("default-src 'self'", "frame-ancestors 'none'");
            assertThat(r.header("X-Frame-Options")).as(path).isEqualTo("DENY");
            assertThat(r.header("X-Content-Type-Options")).as(path).isEqualTo("nosniff");
            assertThat(r.header("Referrer-Policy")).as(path).isEqualTo("no-referrer");
            assertThat(r.header("Strict-Transport-Security")).as("HSTS solo por HTTPS").isNull();
        }
        assertThat(anonymous.get("/api/config").header("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void writesFromOtherSitesAreRejected() {
        ApiClient gm = newGm();
        assertThat(gm.send("POST", "/api/inventories", SHOP, Map.of("Sec-Fetch-Site", "cross-site")).status()).isEqualTo(403);
        assertThat(gm.send("POST", "/api/inventories", SHOP, Map.of("Sec-Fetch-Site", "same-site")).status())
                .as("Foundry u otro *.srv.home.arpa").isEqualTo(403);
        assertThat(gm.send("POST", "/api/inventories", SHOP, Map.of("Origin", "https://evil.example")).status()).isEqualTo(403);
        assertThat(gm.send("POST", "/api/inventories", SHOP, Map.of("Origin", "null")).status()).isEqualTo(403);

        assertThat(gm.send("POST", "/api/inventories", SHOP, Map.of("Sec-Fetch-Site", "same-origin")).status()).isEqualTo(201);
        assertThat(gm.send("POST", "/api/inventories", SHOP, Map.of("Origin", gm.baseUrl())).status()).isEqualTo(201);
        assertThat(gm.send("GET", "/api/inventories", null, Map.of("Sec-Fetch-Site", "cross-site")).status())
                .as("las lecturas no cambian nada").isEqualTo(200);
    }

    @Test
    void oversizedBodiesAreCutBeforeBeingRead() {
        String huge = "{\"name\":\"" + "z".repeat(1_100_000) + "\",\"passphrase\":\"secreto-largo\"}";
        Response withLength = browser().sendRaw("POST", "/api/campaigns", BodyPublishers.ofString(huge), Map.of());
        assertThat(withLength.status()).isEqualTo(413);

        Response chunked = browser().sendRaw("POST", "/api/campaigns",
                BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(huge.getBytes(StandardCharsets.UTF_8))), Map.of());
        assertThat(chunked.status()).as("sin Content-Length también se corta").isEqualTo(413);

        String big = "{\"name\":\"" + "z".repeat(300_000) + "\",\"passphrase\":\"secreto-largo\"}";
        assertThat(browser().sendRaw("POST", "/api/campaigns", BodyPublishers.ofString(big), Map.of()).status())
                .as("bajo el tope llega a la validación").isEqualTo(400);
    }

    @Test
    void campaignLoginLimitHoldsEvenWhenTheClientRotatesIps() {
        ApiClient gm = newGm();
        String slug = slugOf(gm);
        // Desde loopback (un proxy de confianza) X-Forwarded-For cuenta: cada intento parece otra IP.
        for (int i = 0; i < 30; i++) {
            assertThat(loginFrom("10.0.0." + i, slug, "no-es-esta").status()).isEqualTo(401);
        }
        assertThat(loginFrom("10.0.1.1", slug, "no-es-esta").status()).isEqualTo(429);
        assertThat(loginFrom("10.0.1.2", slug, "secreto-largo").status())
                .as("bloqueada la campaña, ni la contraseña correcta entra").isEqualTo(429);
    }

    @Test
    void failuresOnUnknownCampaignsDoNotFillTheCampaignCounter() {
        String slug = "no-existe-" + UUID.randomUUID();
        for (int i = 0; i < 35; i++) {
            assertThat(loginFrom("10.1.0." + i, slug, "x").status()).isEqualTo(401);
        }
    }

    private Response loginFrom(String ip, String slug, String passphrase) {
        return browser().send("POST", "/api/campaigns/" + slug + "/gm-login", Map.of("passphrase", passphrase),
                Map.of("X-Forwarded-For", ip));
    }
}
