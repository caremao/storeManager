package com.caremao.storemanager.item;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.caremao.storemanager.auth.Actor;
import com.caremao.storemanager.common.Limits;
import com.caremao.storemanager.events.CampaignEvent;
import com.caremao.storemanager.history.HistoryAction;
import com.caremao.storemanager.history.HistoryService;
import com.caremao.storemanager.inventory.Denomination;
import com.caremao.storemanager.inventory.Inventory;
import com.caremao.storemanager.inventory.InventoryService;
import com.caremao.storemanager.srd.SrdItem;
import com.caremao.storemanager.srd.SrdItemRepository;

/**
 * Carga masiva de items desde un CSV (la plantilla la arma {@link #template}).
 *
 * <p>Pensado para planillas hechas a mano o en Excel: acepta {@code ,} o {@code ;}, BOM, coma
 * decimal, celdas con saltos de línea, y tipos, rarezas y monedas en español. El *dry run* devuelve
 * la vista previa con errores por fila; la importación real es todo o nada. Si el inventario ya
 * tiene un item con el mismo nombre, suma la cantidad (para reponer stock).
 */
@Service
public class ItemCsvImporter {

    static final List<String> COLUMNS = List.of("nombre", "tipo", "rareza", "categoria", "cantidad", "valor",
            "moneda", "valor_percibido", "valor_revelado", "negociable", "oculto", "stock_ilimitado", "peso",
            "descripcion", "notas", "srd");
    static final int MAX_ROWS = 1000;

    public record RowError(int row, String message) {
    }

    /** {@code merges}: se suma a un item que ya existe con ese nombre (o a una fila anterior del archivo). */
    public record PreviewRow(int row, String name, int quantity, Long valueCp, Denomination denomination,
            boolean merges) {
    }

    public record ImportResult(List<PreviewRow> rows, List<RowError> errors, int imported) {

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    /** Una fila ya validada, lista para convertirse en item. */
    private record Draft(int row, String name, ItemType type, ItemRarity rarity, String category, int quantity,
            Long valueCp, Denomination denomination, Long perceivedCp, boolean revealed, boolean negotiable,
            boolean hidden, boolean unlimitedStock, Double weight, String description, String notes, String srdIndex,
            Map<String, Object> properties) {
    }

    private final ItemRepository items;
    private final InventoryService inventories;
    private final SrdItemRepository srd;
    private final HistoryService history;
    private final ApplicationEventPublisher events;

    public ItemCsvImporter(ItemRepository items, InventoryService inventories, SrdItemRepository srd,
            HistoryService history, ApplicationEventPublisher events) {
        this.items = items;
        this.inventories = inventories;
        this.srd = srd;
        this.history = history;
        this.events = events;
    }

    /** Solo encabezados, separados por {@code ;} y con BOM: así Excel en español la abre bien. */
    public byte[] template(Actor actor, UUID inventoryId) {
        actor.requireGm();
        inventories.requireReadable(actor, inventoryId);
        return ("﻿" + String.join(";", COLUMNS) + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public ImportResult importCsv(Actor actor, UUID inventoryId, String csv, boolean dryRun) {
        actor.requireGm();
        Inventory inventory = inventories.requireReadable(actor, inventoryId);
        List<RowError> errors = new ArrayList<>();
        List<Draft> drafts = parse(csv, inventory, errors);

        Map<String, Item> byName = new HashMap<>();
        items.findByInventoryIdOrderByNameAsc(inventory.getId())
                .forEach(item -> byName.putIfAbsent(key(item.getName()), item));
        Map<String, Integer> totals = new HashMap<>();
        byName.forEach((name, item) -> totals.put(name, item.getQuantity()));
        List<PreviewRow> preview = new ArrayList<>();
        for (Draft d : drafts) {
            boolean merges = totals.containsKey(key(d.name()));
            long total = (long) totals.getOrDefault(key(d.name()), 0) + d.quantity();
            if (total > Limits.MAX_QUANTITY) {
                errors.add(new RowError(d.row(), "Con esta fila " + d.name() + " pasaría de " + Limits.MAX_QUANTITY
                        + " unidades"));
            }
            totals.put(key(d.name()), (int) Math.min(total, Limits.MAX_QUANTITY));
            preview.add(new PreviewRow(d.row(), d.name(), d.quantity(), d.valueCp(), d.denomination(), merges));
        }
        errors.sort((a, b) -> Integer.compare(a.row(), b.row()));
        if (dryRun || !errors.isEmpty()) {
            return new ImportResult(preview, errors, 0);
        }

        int created = 0;
        int merged = 0;
        for (Draft d : drafts) {
            Item existing = byName.get(key(d.name()));
            if (existing != null) {
                if (!existing.isUnlimitedStock()) {
                    existing.setQuantity(existing.getQuantity() + d.quantity());
                }
                merged++;
            } else {
                Item item = toItem(inventory, d);
                items.save(item);
                byName.put(key(d.name()), item);
                created++;
            }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rows", drafts.size());
        details.put("created", created);
        details.put("merged", merged);
        history.log(actor, HistoryAction.ITEMS_IMPORTED, inventory, null, null, details);
        events.publishEvent(CampaignEvent.inventories(actor.campaignId(), inventory.getId()));
        return new ImportResult(preview, List.of(), drafts.size());
    }

    private List<Draft> parse(String csv, Inventory inventory, List<RowError> errors) {
        String text = csv.startsWith("﻿") ? csv.substring(1) : csv;
        String firstLine = text.lines().findFirst().orElse("");
        char delimiter = count(firstLine, ';') > count(firstLine, ',') ? ';' : ',';
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setAllowMissingColumnNames(true)
                .get();
        List<Draft> drafts = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            Map<String, String> columns = new HashMap<>();
            for (String header : parser.getHeaderNames()) {
                String normalized = normalize(header);
                if (normalized.isEmpty()) {
                    continue;
                }
                if (!COLUMNS.contains(normalized)) {
                    errors.add(new RowError(1, "Columna desconocida: «" + header + "»"));
                }
                columns.put(normalized, header);
            }
            if (!columns.containsKey("nombre")) {
                errors.add(new RowError(1, "Falta la columna «nombre» (descarga la plantilla)"));
                return drafts;
            }
            int row = 1;
            for (CSVRecord record : parser) {
                row++;
                // Excel suele dejar filas con solo separadores (";;;;") al final.
                if (record.stream().allMatch(String::isBlank)) {
                    continue;
                }
                if (row - 1 > MAX_ROWS) {
                    errors.add(new RowError(row, "Hay más de " + MAX_ROWS + " filas; divide el archivo"));
                    break;
                }
                Draft draft = toDraft(row, record, columns, inventory, errors);
                if (draft != null) {
                    drafts.add(draft);
                }
            }
        } catch (IOException | UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
            errors.add(new RowError(0, "El archivo no es un CSV válido: " + e.getMessage()));
        }
        return drafts;
    }

    /** Valida una fila; si tiene errores los anota y devuelve null. */
    private Draft toDraft(int row, CSVRecord record, Map<String, String> columns, Inventory inventory,
            List<RowError> errors) {
        Cells cells = new Cells(row, record, columns, errors);
        String name = cells.text("nombre", 200);
        if (name == null) {
            cells.error("Falta el nombre");
        }
        SrdItem base = null;
        String srdIndex = cells.text("srd", 100);
        if (srdIndex != null) {
            base = srd.findById(srdIndex).orElse(null);
            if (base == null) {
                cells.error("No existe en el SRD: «" + srdIndex + "»");
            }
        }
        boolean shop = inventory.isShop();
        Denomination denomination = cells.denomination("moneda");
        Integer quantity = cells.integer("cantidad", shop ? 0 : 1, Limits.MAX_QUANTITY);
        Long valueCp = cells.money("valor", denomination);
        Long perceived = cells.money("valor_percibido", denomination);
        Double weight = cells.decimal("peso", Limits.MAX_WEIGHT);
        ItemType type = cells.choice("tipo", TYPES, "un tipo (Equipo, Poción, Pergamino, Consumible, Varios)");
        ItemRarity rarity = cells.choice("rareza", RARITIES, "una rareza (Común, Poco común, Raro, Muy raro…)");
        Boolean revealed = cells.bool("valor_revelado");
        Boolean negotiable = cells.bool("negociable");
        Boolean hidden = cells.bool("oculto");
        Boolean unlimited = cells.bool("stock_ilimitado");
        String category = cells.text("categoria", 100);
        String description = cells.text("descripcion", 20_000);
        String notes = cells.text("notas", 2_000);
        if (cells.failed()) {
            return null;
        }
        if (base != null) {
            type = type != null ? type : base.getType();
            rarity = rarity != null ? rarity : base.getRarity();
            category = category != null ? category : base.getCategory();
            weight = weight != null ? weight : base.getWeight();
            description = description != null ? description : base.getDescription();
            if (valueCp == null && base.getValueCp() != null) {
                valueCp = base.getValueCp();
                denomination = cells.has("moneda") ? denomination : natural(valueCp);
            }
        }
        return new Draft(row, name, type == null ? ItemType.MISC : type, rarity == null ? ItemRarity.COMMON : rarity,
                category, quantity == null ? 1 : quantity, valueCp, denomination, perceived,
                revealed == null || revealed, shop && Boolean.TRUE.equals(negotiable),
                shop && Boolean.TRUE.equals(hidden), shop && Boolean.TRUE.equals(unlimited), weight, description,
                notes, base == null ? null : base.getSrdIndex(), base == null ? null : base.getProperties());
    }

    private static Item toItem(Inventory inventory, Draft d) {
        Item item = new Item(inventory.getId(), d.name());
        item.setType(d.type());
        item.setRarity(d.rarity());
        item.setCategory(d.category());
        item.setQuantity(d.quantity());
        item.setValueCp(d.valueCp());
        item.setPriceDenomination(d.denomination());
        item.setPerceivedValueCp(d.revealed() ? d.valueCp() : d.perceivedCp());
        item.setPerceivedDenomination(d.denomination());
        item.setValueRevealed(d.revealed());
        item.setNegotiable(d.negotiable());
        item.setHidden(d.hidden());
        item.setUnlimitedStock(d.unlimitedStock());
        item.setWeight(d.weight());
        item.setDescription(d.description());
        item.setNotes(d.notes());
        item.setSrdIndex(d.srdIndex());
        item.setProperties(d.properties());
        return item;
    }

    private static final Map<String, ItemType> TYPES = Map.ofEntries(
            Map.entry("equipo", ItemType.EQUIPMENT), Map.entry("equipment", ItemType.EQUIPMENT),
            Map.entry("pocion", ItemType.POTION), Map.entry("potion", ItemType.POTION),
            Map.entry("pergamino", ItemType.SCROLL), Map.entry("scroll", ItemType.SCROLL),
            Map.entry("consumible", ItemType.CONSUMABLE), Map.entry("consumable", ItemType.CONSUMABLE),
            Map.entry("varios", ItemType.MISC), Map.entry("misc", ItemType.MISC));

    /** Con las formas femeninas, que concuerdan con el objeto ("Perla negra; Rara"). */
    private static final Map<String, ItemRarity> RARITIES = Map.ofEntries(
            Map.entry("comun", ItemRarity.COMMON), Map.entry("common", ItemRarity.COMMON),
            Map.entry("poco_comun", ItemRarity.UNCOMMON), Map.entry("uncommon", ItemRarity.UNCOMMON),
            Map.entry("raro", ItemRarity.RARE), Map.entry("rara", ItemRarity.RARE), Map.entry("rare", ItemRarity.RARE),
            Map.entry("muy_raro", ItemRarity.VERY_RARE), Map.entry("muy_rara", ItemRarity.VERY_RARE),
            Map.entry("very_rare", ItemRarity.VERY_RARE),
            Map.entry("legendario", ItemRarity.LEGENDARY), Map.entry("legendaria", ItemRarity.LEGENDARY),
            Map.entry("legendary", ItemRarity.LEGENDARY),
            Map.entry("artefacto", ItemRarity.ARTIFACT), Map.entry("artifact", ItemRarity.ARTIFACT));

    /** En español "pp" es plata (como en la app), no platino. */
    private static final Map<String, Denomination> DENOMINATIONS = Map.ofEntries(
            Map.entry("ppt", Denomination.PLATINUM), Map.entry("platino", Denomination.PLATINUM),
            Map.entry("platinum", Denomination.PLATINUM),
            Map.entry("po", Denomination.GOLD), Map.entry("oro", Denomination.GOLD), Map.entry("gold", Denomination.GOLD),
            Map.entry("gp", Denomination.GOLD),
            Map.entry("pp", Denomination.SILVER), Map.entry("plata", Denomination.SILVER),
            Map.entry("silver", Denomination.SILVER), Map.entry("sp", Denomination.SILVER),
            Map.entry("pc", Denomination.COPPER), Map.entry("cobre", Denomination.COPPER),
            Map.entry("copper", Denomination.COPPER), Map.entry("cp", Denomination.COPPER));

    /** Las celdas de una fila, con conversiones que anotan el error y devuelven null si algo no cuadra. */
    private static final class Cells {
        private final int row;
        private final CSVRecord record;
        private final Map<String, String> columns;
        private final List<RowError> errors;
        private boolean failed;

        Cells(int row, CSVRecord record, Map<String, String> columns, List<RowError> errors) {
            this.row = row;
            this.record = record;
            this.columns = columns;
            this.errors = errors;
        }

        boolean has(String column) {
            return raw(column) != null;
        }

        void error(String message) {
            failed = true;
            errors.add(new RowError(row, message));
        }

        boolean failed() {
            return failed;
        }

        String raw(String column) {
            String header = columns.get(column);
            if (header == null || !record.isSet(header)) {
                return null;
            }
            String value = record.get(header).trim();
            return value.isEmpty() ? null : value;
        }

        String text(String column, int max) {
            String value = raw(column);
            if (value != null && value.length() > max) {
                error("«" + column + "» tiene más de " + max + " caracteres");
                return null;
            }
            return value;
        }

        Integer integer(String column, int min, int max) {
            String value = raw(column);
            if (value == null) {
                return null;
            }
            try {
                int n = Integer.parseInt(value);
                if (n < min || n > max) {
                    error("«" + column + "» tiene que estar entre " + min + " y " + max);
                    return null;
                }
                return n;
            } catch (NumberFormatException e) {
                error("«" + column + "» no es un número entero: «" + value + "»");
                return null;
            }
        }

        Double decimal(String column, double max) {
            String value = raw(column);
            if (value == null) {
                return null;
            }
            if (!value.matches("\\d+([.,]\\d+)?")) {
                error("«" + column + "» no es un número: «" + value + "»");
                return null;
            }
            double n = Double.parseDouble(value.replace(',', '.'));
            if (n > max) {
                error("«" + column + "» es demasiado grande");
                return null;
            }
            return n;
        }

        Long money(String column, Denomination denomination) {
            Double amount = decimal(column, Double.MAX_VALUE);
            if (amount == null) {
                return null;
            }
            long cp = Math.round(amount * denomination.copperValue());
            if (cp > Limits.MAX_VALUE_CP) {
                error("«" + column + "» es demasiado grande");
                return null;
            }
            return cp;
        }

        Denomination denomination(String column) {
            Denomination d = choice(column, DENOMINATIONS, "una moneda (ppt, po, pp, pc)");
            return d == null ? Denomination.GOLD : d;
        }

        <T> T choice(String column, Map<String, T> options, String expected) {
            String value = raw(column);
            if (value == null) {
                return null;
            }
            T found = options.get(normalize(value));
            if (found == null) {
                error("«" + column + "» tiene que ser " + expected + ", no «" + value + "»");
            }
            return found;
        }

        Boolean bool(String column) {
            String value = raw(column);
            if (value == null) {
                return null;
            }
            return switch (normalize(value)) {
                case "si", "s", "true", "1", "x", "yes", "y" -> true;
                case "no", "n", "false", "0" -> false;
                default -> {
                    error("«" + column + "» tiene que ser sí o no, no «" + value + "»");
                    yield null;
                }
            };
        }
    }

    /** "Categoría" → "categoria", "Poco común" → "poco_comun". */
    static String normalize(String text) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return ascii.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static long count(String text, char c) {
        return text.chars().filter(ch -> ch == c).count();
    }

    /** Igual que naturalDenomination del frontend: la moneda más grande en que el monto es exacto. */
    private static Denomination natural(long cp) {
        if (cp % 100 == 0) {
            return Denomination.GOLD;
        }
        return cp % 10 == 0 ? Denomination.SILVER : Denomination.COPPER;
    }
}
