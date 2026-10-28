package com.caremao.storemanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.caremao.storemanager.support.ApiClient;
import com.caremao.storemanager.support.ApiClient.Response;
import com.caremao.storemanager.support.IntegrationTest;

import tools.jackson.databind.JsonNode;

/** Carga masiva de items por CSV, con plantilla. */
class CsvImportIT extends IntegrationTest {

    private static Response upload(ApiClient client, String inventoryId, String csv, boolean dryRun) {
        return client.sendRaw("POST", "/api/inventories/" + inventoryId + "/items/import?dryRun=" + dryRun,
                BodyPublishers.ofString(csv, StandardCharsets.UTF_8), Map.of("Content-Type", "text/csv; charset=utf-8"));
    }

    @Test
    void templateHasTheColumnsAndOnlyTheGmGetsIt() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Almacén", Map.of());
        Response template = gm.get("/api/inventories/" + shop + "/items/template.csv");
        assertThat(template.status()).isEqualTo(200);
        assertThat(template.header("Content-Disposition")).contains("plantilla-items.csv");
        assertThat(template.body()).startsWith("﻿nombre;tipo;rareza;categoria;cantidad;valor;moneda");

        String[] pj = createCharacter(gm, "Curioso", 0);
        ApiClient player = join(pj[1]);
        assertThat(player.get("/api/inventories/" + shop + "/items/template.csv").status()).isEqualTo(403);
        assertThat(upload(player, pj[0], "nombre\nPiedra\n", false).status()).isEqualTo(403);
    }

    @Test
    void importsExcelStyleCsvWithSemicolonsDecimalCommaAndMultilineCells() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Bazar", Map.of());
        String csv = "﻿Nombre;Tipo;Rareza;Categoría;Cantidad;Valor;Moneda;Negociable;Oculto;Descripción\r\n"
                + "Cuerda de seda;Varios;Común;Aventura;4;3;pp;no;no;\"Resistente,\r\nliviana\"\r\n"
                + "Anillo;Varios;Raro;Joyas;1;2,5;ppt;sí;sí;\r\n"
                + "Perla negra;Varios;Muy rara;;1;1;po;no;no;\r\n"
                + ";;;;;;;;;\r\n";

        Response preview = upload(gm, shop, csv, true);
        assertThat(preview.status()).as(preview.body()).isEqualTo(200);
        assertThat(preview.json().get("errors")).isEmpty();
        assertThat(preview.json().get("rows")).hasSize(3);
        assertThat(preview.json().get("rows").get(1).get("category").asString()).isEqualTo("Joyas");
        assertThat(gm.get("/api/inventories/" + shop + "/items").json()).as("la vista previa no escribe").isEmpty();

        Response done = upload(gm, shop, csv, false);
        assertThat(done.status()).as(done.body()).isEqualTo(200);
        assertThat(done.json().get("imported").asInt()).isEqualTo(3);
        JsonNode stock = gm.get("/api/inventories/" + shop + "/items").json();
        JsonNode rope = findByName(stock, "Cuerda de seda");
        assertThat(rope.get("valueCp").asLong()).isEqualTo(30);
        assertThat(rope.get("priceDenomination").asString()).isEqualTo("SILVER");
        assertThat(rope.get("description").asString()).isEqualTo("Resistente,\r\nliviana");
        assertThat(rope.get("category").asString()).isEqualTo("Aventura");
        assertThat(findByName(stock, "Perla negra").get("category").isNull()).as("celda vacía").isTrue();
        JsonNode ring = findByName(stock, "Anillo");
        assertThat(ring.get("valueCp").asLong()).isEqualTo(2_500);
        assertThat(ring.get("rarity").asString()).isEqualTo("RARE");
        assertThat(ring.get("negotiable").asBoolean()).isTrue();
        assertThat(ring.get("hidden").asBoolean()).isTrue();
        assertThat(findByName(stock, "Perla negra").get("rarity").asString()).isEqualTo("VERY_RARE");
        assertThat(gm.get("/api/history?inventoryId=" + shop).json().get("entries").get(0).get("action").asString())
                .isEqualTo("ITEMS_IMPORTED");
    }

    @Test
    void reportsEveryErrorWithItsRowAndImportsNothing() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Desorden", Map.of());
        String csv = "nombre,tipo,cantidad,valor,moneda,srd,color\n"
                + "Bien,Varios,1,1,po,,\n"
                + "Raro,Dragón,1,1,po,,\n"
                + ",Varios,1,1,po,,\n"
                + "Negativo,Varios,-2,1,po,,\n"
                + "Caro,Varios,1,mucho,po,,\n"
                + "Inventado,,1,,,no-existe,\n";

        Response preview = upload(gm, shop, csv, true);
        JsonNode errors = preview.json().get("errors");
        assertThat(errors).hasSize(6);
        assertThat(errors.get(0).get("row").asInt()).isEqualTo(1);
        assertThat(errors.get(0).get("message").asString()).contains("color");
        assertThat(preview.body()).contains("Dragón").contains("Falta el nombre").contains("entre 0")
                .contains("mucho").contains("no-existe");
        assertThat(errors.get(1).get("row").asInt()).as("la fila de los datos, con el encabezado como fila 1").isEqualTo(3);

        Response real = upload(gm, shop, csv, false);
        assertThat(real.status()).isEqualTo(422);
        assertThat(gm.get("/api/inventories/" + shop + "/items").json()).as("todo o nada").isEmpty();
    }

    @Test
    void sameNameAddsToTheExistingStockAndSrdFillsTheBlanks() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Armería", Map.of());
        addItem(gm, shop, Map.of("name", "Flecha", "quantity", 20, "valueCp", 5));
        String csv = "nombre,cantidad,srd\nflecha,30,\nLongsword,2,longsword\nLongsword,1,longsword\n";

        Response preview = upload(gm, shop, csv, true);
        assertThat(preview.json().get("rows").get(0).get("merges").asBoolean()).isTrue();
        assertThat(preview.json().get("rows").get(2).get("merges").asBoolean()).as("repetida en el archivo").isTrue();

        assertThat(upload(gm, shop, csv, false).status()).isEqualTo(200);
        JsonNode stock = gm.get("/api/inventories/" + shop + "/items").json();
        assertThat(stock).hasSize(2);
        assertThat(findByName(stock, "Flecha").get("quantity").asInt()).isEqualTo(50);
        JsonNode sword = findByName(stock, "Longsword");
        assertThat(sword.get("quantity").asInt()).isEqualTo(3);
        assertThat(sword.get("valueCp").asLong()).as("del SRD").isEqualTo(1_500);
        assertThat(sword.get("type").asString()).isEqualTo("EQUIPMENT");
        assertThat(sword.get("properties").get("damage").asString()).isEqualTo("1d8 Slashing");
    }

    @Test
    void tooManyRowsAreRejected() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Enorme", Map.of());
        StringBuilder csv = new StringBuilder("nombre\n");
        for (int i = 0; i < 1_001; i++) {
            csv.append("Item ").append(i).append('\n');
        }
        Response preview = upload(gm, shop, csv.toString(), true);
        assertThat(preview.body()).contains("más de 1000 filas");
    }
}
