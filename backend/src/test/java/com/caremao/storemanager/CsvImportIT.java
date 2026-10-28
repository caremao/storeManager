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
        JsonNode ringRow = preview.json().get("rows").get(1);
        assertThat(ringRow.get("line").asInt()).as("la descripción de la cuerda ocupa dos líneas").isEqualTo(4);
        assertThat(ringRow.get("row").asInt()).as("pero en Excel es una sola fila").isEqualTo(3);
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
        assertError(errors.get(0), 1, "color", "No es una columna");
        assertError(errors.get(1), 3, "tipo", "«Dragón» no es un valor válido; usa Equipo, Poción, Pergamino, Consumible o Varios");
        assertError(errors.get(2), 4, "nombre", "Está vacío y es obligatorio");
        assertError(errors.get(3), 5, "cantidad", "«-2» tiene que estar entre 0");
        assertError(errors.get(4), 6, "valor", "«mucho» no es un número");
        assertError(errors.get(5), 7, "srd", "«no-existe» no existe en el SRD");
        assertThat(errors.get(1).get("row").asInt()).as("la fila de los datos, con el encabezado como fila 1").isEqualTo(3);

        Response real = upload(gm, shop, csv, false);
        assertThat(real.status()).isEqualTo(422);
        assertThat(gm.get("/api/inventories/" + shop + "/items").json()).as("todo o nada").isEmpty();
    }

    @Test
    void linesCountBlankLinesAndEveryEnumeratedValueIsChecked() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Sastrería", Map.of());
        String csv = "﻿nombre;rareza;descripcion;moneda;negociable\r\n"
                + "\r\n"
                + "Capa;Rara;\"Larga,\r\nde lana\";po;sí\r\n"
                + "Botas;Épica;;dólares;quizás\r\n"
                + ";Común;;;\r\n"
                + "Daga;Común;;oro;no;extra\r\n";

        JsonNode errors = upload(gm, shop, csv, true).json().get("errors");
        assertThat(errors).hasSize(5);
        assertError(errors.get(0), 5, "rareza", "«Épica» no es un valor válido; usa Común, Poco común, Raro, Muy raro, Legendario o Artefacto");
        assertThat(errors.get(0).get("row").asInt()).as("la línea vacía cuenta; la celda de dos líneas, no").isEqualTo(4);
        assertError(errors.get(1), 5, "moneda", "«dólares» no es un valor válido; usa ppt, po, pp o pc");
        assertError(errors.get(2), 5, "negociable", "«quizás» no es un valor válido; usa sí o no");
        assertError(errors.get(3), 6, "nombre", "Está vacío y es obligatorio");
        assertError(errors.get(4), 7, null, "«extra» está en la columna F, que no tiene encabezado");
    }

    @Test
    void headerProblemsAreReportedAndTheRowsAreStillChecked() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Sin nombre", Map.of());
        JsonNode errors = upload(gm, shop, "Tipo;tipo;rareza\nVarios;Varios;Mítica\n", true).json().get("errors");
        assertThat(errors).hasSize(3);
        assertError(errors.get(0), 1, "tipo", "La columna está repetida");
        assertError(errors.get(1), 1, "nombre", "Falta esta columna, que es obligatoria");
        assertError(errors.get(2), 2, "rareza", "«Mítica» no es un valor válido");

        assertError(upload(gm, shop, "\n;;\n", true).json().get("errors").get(0), 1, null, "El archivo está vacío");
    }

    @Test
    void brokenQuotesPointToTheirLine() {
        ApiClient gm = newGm();
        String shop = createShop(gm, "Comillas", Map.of());
        String unclosed = "nombre;descripcion\nArco;Bueno\nFlecha;\"sin cerrar\nCarcaj;Cuero\n";
        JsonNode errors = upload(gm, shop, unclosed, true).json().get("errors");
        assertThat(errors).hasSize(1);
        assertError(errors.get(0), 3, null, "no se cierran");

        String trailing = "nombre;descripcion\nArco;Bueno\n\nCuerda;\"De seda\" larga\n";
        errors = upload(gm, shop, trailing, true).json().get("errors");
        assertThat(errors).hasSize(1);
        assertError(errors.get(0), 4, null, "Hay texto pegado a unas comillas de cierre");
    }

    private static void assertError(JsonNode error, int line, String column, String message) {
        assertThat(error.get("line").asInt()).as(error.toString()).isEqualTo(line);
        if (column == null) {
            assertThat(error.get("column").isNull()).as(error.toString()).isTrue();
        } else {
            assertThat(error.get("column").asString()).as(error.toString()).isEqualTo(column);
        }
        assertThat(error.get("message").asString()).contains(message);
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
