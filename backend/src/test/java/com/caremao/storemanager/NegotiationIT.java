package com.caremao.storemanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.caremao.storemanager.support.ApiClient;
import com.caremao.storemanager.support.IntegrationTest;

import tools.jackson.databind.JsonNode;

/** Regateo por turnos: el jugador ofrece, el GM contraoferta, alguien acepta. */
class NegotiationIT extends IntegrationTest {

    private static Map<String, Object> buy(String itemId, Long offer) {
        return offer == null
                ? Map.of("kind", "BUY", "itemId", itemId, "quantity", 1)
                : Map.of("kind", "BUY", "itemId", itemId, "quantity", 1, "offerUnitPriceCp", offer);
    }

    @Test
    void offerCounterAndAccept() throws Exception {
        ApiClient gm = newGm();
        // Sin aprobación: aun así, una oferta siempre espera al GM.
        String shop = createShop(gm, "Zoco", Map.of("visibleToPlayers", true, "requiresApproval", false));
        String carpet = addItem(gm, shop, Map.of("name", "Alfombra", "valueCp", 10_000, "quantity", 1,
                "negotiable", true));
        String[] pj = createCharacter(gm, "Regateador", 100);
        ApiClient player = join(pj[1]);

        var offer = player.post("/api/shops/" + shop + "/trades", buy(carpet, 6_000L));
        assertThat(offer.status()).as(offer.body()).isEqualTo(201);
        JsonNode trade = offer.json();
        String id = trade.get("id").asString();
        assertThat(trade.get("status").asString()).isEqualTo("PENDING");
        assertThat(trade.get("unitPriceCp").asLong()).isEqualTo(6_000);
        assertThat(trade.get("listUnitPriceCp").asLong()).isEqualTo(10_000);
        assertThat(trade.get("lastOfferBy").asString()).isEqualTo("PLAYER");

        HttpResponse<Stream<String>> events = player.openEvents().get(5, TimeUnit.SECONDS);
        CompletableFuture<String> notice = CompletableFuture.supplyAsync(() -> events.body()
                .filter(line -> line.startsWith("data:") && line.contains(id)).findFirst().orElse(null));

        var counter = gm.post("/api/trades/" + id + "/counter", Map.of("unitPriceCp", 8_000));
        assertThat(counter.status()).as(counter.body()).isEqualTo(200);
        assertThat(counter.json().get("status").asString()).isEqualTo("COUNTERED");
        assertThat(notice.get(10, TimeUnit.SECONDS)).as("el jugador se entera por SSE").contains("tradeIds");

        assertThat(gm.post("/api/trades/" + id + "/approve", null).status()).as("no es turno del GM").isEqualTo(409);

        var reOffer = player.post("/api/trades/" + id + "/counter", Map.of("unitPriceCp", 7_000));
        assertThat(reOffer.json().get("status").asString()).isEqualTo("PENDING");
        assertThat(player.post("/api/trades/" + id + "/accept", null).status()).as("no es turno del jugador").isEqualTo(409);

        assertThat(gm.post("/api/trades/" + id + "/counter", Map.of("unitPriceCp", 7_500)).status()).isEqualTo(200);
        var accepted = player.post("/api/trades/" + id + "/accept", null);
        assertThat(accepted.status()).as(accepted.body()).isEqualTo(200);
        assertThat(accepted.json().get("status").asString()).isEqualTo("APPROVED");
        assertThat(accepted.json().get("totalCp").asLong()).isEqualTo(7_500);
        assertThat(purse(player, pj[0]).get("totalCp").asLong()).isEqualTo(10_000 - 7_500);
    }

    @Test
    void gmCanAcceptThePlayersOffer() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Feria", Map.of("visibleToPlayers", true));
        String lute = addItem(gm, shop, Map.of("name", "Laúd", "valueCp", 3_500, "quantity", 1, "negotiable", true));
        String[] pj = createCharacter(gm, "Bardo", 50);
        ApiClient player = join(pj[1]);

        String id = player.post("/api/shops/" + shop + "/trades", buy(lute, 3_000L)).json().get("id").asString();
        var approved = gm.post("/api/trades/" + id + "/approve", null);
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        assertThat(approved.json().get("totalCp").asLong()).isEqualTo(3_000);
    }

    @Test
    void offersOnlyOnNegotiableProducts() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Precio fijo", Map.of("visibleToPlayers", true));
        String bread = addItem(gm, shop, Map.of("name", "Pan", "valueCp", 2, "quantity", 10));
        String[] pj = createCharacter(gm, "Hambriento", 1);
        ApiClient player = join(pj[1]);

        var offer = player.post("/api/shops/" + shop + "/trades", buy(bread, 1L));
        assertThat(offer.status()).isEqualTo(400);
        assertThat(offer.json().get("detail").asString()).contains("no se negocia");
    }

    @Test
    void fundsAreCheckedWhenTheDealCloses() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Joyería", Map.of("visibleToPlayers", true));
        String crown = addItem(gm, shop, Map.of("name", "Corona", "valueCp", 5_000, "quantity", 1, "negotiable", true));
        String[] pj = createCharacter(gm, "Gastador", 40);
        ApiClient player = join(pj[1]);

        String id = player.post("/api/shops/" + shop + "/trades", buy(crown, 3_000L)).json().get("id").asString();
        gm.post("/api/trades/" + id + "/counter", Map.of("unitPriceCp", 4_500));
        player.post("/api/inventories/" + pj[0] + "/currency", Map.of("copper", 0, "silver", 0, "gold", -10, "platinum", 0));

        var accepted = player.post("/api/trades/" + id + "/accept", null);
        assertThat(accepted.status()).isEqualTo(409);
        assertThat(gm.get("/api/trades?status=COUNTERED").json()).as("sigue abierta").hasSize(1);
    }

    @Test
    void eitherSideCanWalkAway() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Mercado negro", Map.of("visibleToPlayers", true));
        String dagger = addItem(gm, shop, Map.of("name", "Daga", "valueCp", 200, "quantity", 2, "negotiable", true));
        String[] pj = createCharacter(gm, "Sombra", 10);
        ApiClient player = join(pj[1]);

        String first = player.post("/api/shops/" + shop + "/trades", buy(dagger, 100L)).json().get("id").asString();
        gm.post("/api/trades/" + first + "/counter", Map.of("unitPriceCp", 180));
        assertThat(player.post("/api/trades/" + first + "/cancel", null).json().get("status").asString())
                .isEqualTo("CANCELLED");

        String second = player.post("/api/shops/" + shop + "/trades", buy(dagger, 100L)).json().get("id").asString();
        gm.post("/api/trades/" + second + "/counter", Map.of("unitPriceCp", 180));
        assertThat(gm.post("/api/trades/" + second + "/reject", null).json().get("status").asString())
                .as("el GM puede cortar aunque sea turno del jugador").isEqualTo("REJECTED");
    }
}
