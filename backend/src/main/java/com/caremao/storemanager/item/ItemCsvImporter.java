package com.caremao.storemanager.item;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * decimal, celdas con saltos de línea, y tipos, rarezas y monedas en español. El *dry run* es el
 * barrido de validación: recorre todo el archivo y devuelve la vista previa y cada error con su
 * línea, su columna y los valores que se esperaban. La importación real es todo o nada. Si el
 * inventario ya tiene un item con el mismo nombre, suma la cantidad (para reponer stock).
 */
@Service
public class ItemCsvImporter {

    static final List<String> COLUMNS = List.of("nombre", "tipo", "rareza", "categoria", "cantidad", "valor",
            "moneda", "valor_percibido", "valor_revelado", "negociable", "oculto", "stock_ilimitado", "peso",
            "descripcion", "notas", "srd");
    static final int MAX_ROWS = 5000;

    /**
     * {@code line}: la línea del archivo donde empieza la fila (0 si no se sabe); {@code row}: la fila
     * en Excel (0 si no se sabe), que se adelanta a la línea cuando una celda anterior trae saltos de
     * línea. {@code column}: el encabezado tal como viene en el archivo, o null si el error es de la
     * fila entera o del archivo.
     */
    public record RowError(int line, int row, String column, String message) {
    }

    /** {@code merges}: se suma a un item que ya existe con ese nombre (o a una fila anterior del archivo). */
    public record PreviewRow(int line, int row, String name, String category, int quantity, Long valueCp,
            Denomination denomination, boolean merges) {
    }

    public record ImportResult(List<PreviewRow> rows, List<RowError> errors, int imported) {

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    /** Dónde empieza una fila: la línea del archivo y la fila de Excel. */
    private record Position(int line, int row) {
    }

    /** Una fila ya validada, lista para convertirse en item. */
    private record Draft(Position at, String name, ItemType type, ItemRarity rarity, String category, int quantity,
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
                errors.add(new RowError(d.at().line(), d.at().row(), "cantidad", "Con esta fila " + d.name()
                        + " pasaría de " + Limits.MAX_QUANTITY + " unidades"));
            }
            totals.put(key(d.name()), (int) Math.min(total, Limits.MAX_QUANTITY));
            preview.add(new PreviewRow(d.at().line(), d.at().row(), d.name(), d.category(), d.quantity(),
                    d.valueCp(), d.denomination(), merges));
        }
        // Estable: los errores de una misma línea siguen en el orden de sus columnas.
        errors.sort(Comparator.comparingInt(RowError::line));
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
        String firstLine = text.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
        char delimiter = count(firstLine, ';') > count(firstLine, ',') ? ';' : ',';
        // Sin saltarse las líneas vacías: así cada registro es una fila de Excel. El encabezado es el
        // primer registro con datos, y lo resuelve header() para poder avisar de columnas repetidas.
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setIgnoreEmptyLines(false)
                .setTrim(true)
                .get();
        int[] lineStarts = lineStarts(text);
        List<Draft> drafts = new ArrayList<>();
        Header header = null;
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            int row = 0;
            int dataRows = 0;
            for (CSVRecord record : parser) {
                row++;
                // Excel suele dejar filas con solo separadores (";;;;") al final.
                if (record.stream().allMatch(String::isBlank)) {
                    continue;
                }
                Position at = new Position(lineAt(lineStarts, record.getCharacterPosition()), row);
                if (header == null) {
                    header = header(record, at, errors);
                    continue;
                }
                if (++dataRows > MAX_ROWS) {
                    errors.add(new RowError(at.line(), at.row(), null,
                            "Hay más de " + MAX_ROWS + " filas; divide el archivo"));
                    break;
                }
                Draft draft = toDraft(at, record, header, delimiter, inventory, errors);
                if (draft != null) {
                    drafts.add(draft);
                }
            }
        } catch (IOException | UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
            errors.add(unreadable(e));
            return drafts;
        }
        if (header == null) {
            errors.add(new RowError(1, 1, null, "El archivo está vacío: descarga la plantilla y llénala"));
        }
        return drafts;
    }

    /** {@code columns}: columna de la plantilla → índice; {@code names}: los encabezados tal como vienen. */
    private record Header(Map<String, Integer> columns, List<String> names) {

        boolean has(String column) {
            return columns.containsKey(column);
        }

        String label(String column) {
            Integer index = columns.get(column);
            return index == null ? column : names.get(index);
        }

        boolean named(int index) {
            return index < names.size() && !names.get(index).isBlank();
        }
    }

    private static Header header(CSVRecord record, Position at, List<RowError> errors) {
        List<String> names = record.toList();
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            String normalized = normalize(name);
            if (normalized.isEmpty()) {
                continue;
            }
            if (!COLUMNS.contains(normalized)) {
                errors.add(new RowError(at.line(), at.row(), name, "No es una columna de la plantilla"));
            } else if (columns.putIfAbsent(normalized, i) != null) {
                errors.add(new RowError(at.line(), at.row(), name, "La columna está repetida"));
            }
        }
        if (!columns.containsKey("nombre")) {
            errors.add(new RowError(at.line(), at.row(), "nombre",
                    "Falta esta columna, que es obligatoria (descarga la plantilla)"));
        }
        return new Header(columns, names);
    }

    /** Valida la fila entera, anotando cada error; si hubo alguno devuelve null. */
    private Draft toDraft(Position at, CSVRecord record, Header header, char delimiter, Inventory inventory,
            List<RowError> errors) {
        Cells cells = new Cells(at, record, header, errors);
        cells.strays(delimiter);
        String name = cells.text("nombre", 200);
        // Sin la columna basta con el error del encabezado.
        if (name == null && header.has("nombre")) {
            cells.error("nombre", "Está vacío y es obligatorio");
        }
        SrdItem base = null;
        String srdIndex = cells.text("srd", 100);
        if (srdIndex != null) {
            base = srd.findById(srdIndex).orElse(null);
            if (base == null) {
                cells.error("srd", "«" + srdIndex + "» no existe en el SRD");
            }
        }
        boolean shop = inventory.isShop();
        Denomination denomination = cells.denomination("moneda");
        Integer quantity = cells.integer("cantidad", shop ? 0 : 1, Limits.MAX_QUANTITY);
        Long valueCp = cells.money("valor", denomination);
        Long perceived = cells.money("valor_percibido", denomination);
        Double weight = cells.decimal("peso", Limits.MAX_WEIGHT);
        ItemType type = cells.choice("tipo", TYPES, "Equipo, Poción, Pergamino, Consumible o Varios");
        ItemRarity rarity = cells.choice("rareza", RARITIES, "Común, Poco común, Raro, Muy raro, Legendario o Artefacto");
        Boolean revealed = cells.bool("valor_revelado");
        Boolean negotiable = cells.bool("negociable");
        Boolean hidden = cells.bool("oculto");
        Boolean unlimited = cells.bool("stock_ilimitado");
        String category = cells.text("categoria", 100);
        String description = cells.text("descripcion", 20_000);
        String notes = cells.text("notas", 2_000);
        if (cells.flushErrors() || name == null) {
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
        return new Draft(at, name, type == null ? ItemType.MISC : type, rarity == null ? ItemRarity.COMMON : rarity,
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

    /**
     * Las celdas de una fila, por el nombre de su columna en la plantilla, con conversiones que anotan
     * el error y devuelven null si algo no cuadra.
     */
    private static final class Cells {
        private final Position at;
        private final CSVRecord record;
        private final Header header;
        private final List<RowError> errors;
        /** Los errores de la fila con el índice de su columna (-1 si es de la fila entera). */
        private final List<Map.Entry<Integer, RowError>> found = new ArrayList<>();

        Cells(Position at, CSVRecord record, Header header, List<RowError> errors) {
            this.at = at;
            this.record = record;
            this.header = header;
            this.errors = errors;
        }

        boolean has(String column) {
            return raw(column) != null;
        }

        /** {@code column}: la columna de la plantilla, o null si el error es de la fila entera. */
        void error(String column, String message) {
            int index = column == null ? -1 : header.columns().getOrDefault(column, -1);
            found.add(Map.entry(index, new RowError(at.line(), at.row(),
                    column == null ? null : header.label(column), message)));
        }

        /**
         * Pasa los errores de la fila a la lista, en el orden de las columnas del archivo y no en el que
         * se validaron, y dice si hubo alguno.
         */
        boolean flushErrors() {
            found.sort(Map.Entry.comparingByKey());
            found.forEach(entry -> errors.add(entry.getValue()));
            return !found.isEmpty();
        }

        String raw(String column) {
            Integer index = header.columns().get(column);
            if (index == null || index >= record.size()) {
                return null;
            }
            String value = record.get(index).trim();
            return value.isEmpty() ? null : value;
        }

        /** Un valor sin encabezado suele ser un separador sin comillas que corrió las celdas siguientes. */
        void strays(char delimiter) {
            for (int i = 0; i < record.size(); i++) {
                if (!header.named(i) && !record.get(i).isBlank()) {
                    error(null, "«" + shorten(record.get(i)) + "» está en la columna " + columnLetter(i)
                            + ", que no tiene encabezado: si algún valor lleva «" + delimiter
                            + "», enciérralo entre comillas");
                    return;
                }
            }
        }

        String text(String column, int max) {
            String value = raw(column);
            if (value != null && value.length() > max) {
                error(column, "Tiene " + value.length() + " caracteres; el máximo es " + max);
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
                    error(column, "«" + value + "» tiene que estar entre " + min + " y " + max);
                    return null;
                }
                return n;
            } catch (NumberFormatException e) {
                error(column, "«" + value + "» no es un número entero");
                return null;
            }
        }

        Double decimal(String column, double max) {
            String value = raw(column);
            if (value == null) {
                return null;
            }
            if (!value.matches("\\d+([.,]\\d+)?")) {
                error(column, "«" + value + "» no es un número (sin signo, con coma o punto decimal)");
                return null;
            }
            double n = Double.parseDouble(value.replace(',', '.'));
            if (n > max) {
                error(column, "«" + value + "» es demasiado grande");
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
                error(column, "«" + raw(column) + "» es demasiado grande");
                return null;
            }
            return cp;
        }

        Denomination denomination(String column) {
            Denomination d = choice(column, DENOMINATIONS, "ppt, po, pp o pc (platino, oro, plata o cobre)");
            return d == null ? Denomination.GOLD : d;
        }

        /** {@code expected}: los valores válidos, para el mensaje de error. */
        <T> T choice(String column, Map<String, T> options, String expected) {
            String value = raw(column);
            if (value == null) {
                return null;
            }
            T found = options.get(normalize(value));
            if (found == null) {
                error(column, "«" + value + "» no es un valor válido; usa " + expected);
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
                    error(column, "«" + value + "» no es un valor válido; usa sí o no");
                    yield null;
                }
            };
        }
    }

    private static final Pattern UNCLOSED_QUOTE = Pattern.compile("\\(startline (.+?)\\)");
    private static final Pattern AFTER_QUOTE = Pattern.compile("at line: (.+?), position");

    /** Lo que Commons CSV no logra leer, en castellano y con la línea cuando su mensaje la trae. */
    private static RowError unreadable(Exception e) {
        String message = String.valueOf(e.getMessage());
        Matcher unclosed = UNCLOSED_QUOTE.matcher(message);
        if (unclosed.find()) {
            return new RowError(digits(unclosed.group(1)), 0, null,
                    "Unas comillas que se abren en esta línea no se cierran nunca");
        }
        Matcher after = AFTER_QUOTE.matcher(message);
        if (after.find()) {
            return new RowError(digits(after.group(1)), 0, null, "Hay texto pegado a unas comillas de cierre: si"
                    + " un valor lleva comillas, enciérralo entero entre comillas y duplica las de adentro (\"\")");
        }
        return new RowError(0, 0, null, "El archivo no es un CSV válido: " + message);
    }

    /** Commons CSV formatea la línea con separador de miles según el locale ("1,234" o "1.234"). */
    private static int digits(String formatted) {
        String digits = formatted.replaceAll("\\D", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }

    /** Dónde empieza cada línea, contando como salto {@code \r\n}, {@code \n} o {@code \r} (igual que el parser). */
    private static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' && (i + 1 == text.length() || text.charAt(i + 1) != '\n')) {
                starts.add(i + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    /** El número de línea (desde 1) de una posición del texto. */
    private static int lineAt(int[] lineStarts, long position) {
        int found = Arrays.binarySearch(lineStarts, (int) position);
        return (found >= 0 ? found : -found - 2) + 1;
    }

    /** 0 → A, 25 → Z, 26 → AA: como las columnas de Excel. */
    private static String columnLetter(int index) {
        StringBuilder letters = new StringBuilder();
        for (int n = index + 1; n > 0; n = (n - 1) / 26) {
            letters.insert(0, (char) ('A' + (n - 1) % 26));
        }
        return letters.toString();
    }

    private static String shorten(String value) {
        String flat = value.strip().replaceAll("\\s+", " ");
        return flat.length() > 40 ? flat.substring(0, 40) + "…" : flat;
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
