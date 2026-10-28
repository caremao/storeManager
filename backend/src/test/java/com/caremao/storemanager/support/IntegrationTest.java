package com.caremao.storemanager.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.JsonNode;

/** Levanta la app en un puerto aleatorio con H2 en memoria y habla con ella por HTTP real. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:storemanager-test;DB_CLOSE_DELAY=-1",
        "app.public-base-url=http://mesa.test"
})
public abstract class IntegrationTest {

    @Value("${local.server.port}")
    protected int port;

    protected ApiClient browser() {
        return new ApiClient("http://localhost:" + port);
    }

    /** Un navegador con sesión de GM en una campaña nueva. */
    protected ApiClient newGm() {
        ApiClient gm = browser();
        var response = gm.post("/api/campaigns", Map.of(
                "name", uniqueCampaignName("Prueba"),
                "passphrase", "secreto-largo"));
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        return gm;
    }

    /** Un nombre de campaña válido (una palabra de letras y números) que no choca con el de otro test. */
    protected static String uniqueCampaignName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    protected static String slugOf(ApiClient gm) {
        return gm.get("/api/me").json().get("campaign").get("slug").asString();
    }

    protected static String createShop(ApiClient gm, String name, Map<String, Object> settings) {
        Map<String, Object> body = new HashMap<>(settings);
        body.put("kind", "SHOP");
        body.put("name", name);
        var response = gm.post("/api/inventories", body);
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        return response.json().get("id").asString();
    }

    /** Crea un personaje con oro y devuelve {id, token del QR}. */
    protected static String[] createCharacter(ApiClient gm, String name, int gold) {
        var response = gm.post("/api/inventories", Map.of(
                "kind", "CHARACTER",
                "name", name,
                "purse", Map.of("copper", 0, "silver", 0, "gold", gold, "platinum", 0)));
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        JsonNode json = response.json();
        String joinUrl = json.get("joinUrl").asString();
        assertThat(joinUrl).as("el token va en el fragmento, nunca llega al servidor").startsWith("http://mesa.test/join#");
        return new String[] { json.get("id").asString(), joinUrl.substring(joinUrl.indexOf('#') + 1) };
    }

    protected static String addItem(ApiClient client, String inventoryId, Map<String, Object> item) {
        var response = client.post("/api/inventories/" + inventoryId + "/items", item);
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        return response.json().get("id").asString();
    }

    protected ApiClient join(String token) {
        ApiClient player = browser();
        var response = player.post("/api/join", Map.of("token", token));
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return player;
    }

    protected static JsonNode purse(ApiClient client, String inventoryId) {
        return client.get("/api/inventories/" + inventoryId).json().get("purse");
    }

    protected static JsonNode findByName(JsonNode array, String name) {
        for (JsonNode node : array) {
            if (node.get("name").asString().equals(name)) {
                return node;
            }
        }
        return null;
    }
}
