package com.omnia.backend.catalog;

import com.omnia.backend.dto.request.ProductRequest;
import com.omnia.backend.enums.CategoryKind;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Columns, template and per-row validation of the CSV product import.
 * Every message is in Albanian and names the column, so a seller can fix
 * the file without help.
 */
public final class ProductCsvRules {

    public static final int MAX_ROWS = 500;

    public static final int MAX_NAME = 150;

    public static final int MAX_DESCRIPTION = 5000;

    public static final String NAME = "emri";
    public static final String DESCRIPTION = "pershkrimi";
    public static final String PRICE = "cmimi";
    public static final String DISCOUNT = "cmimi_me_zbritje";
    public static final String STOCK = "stoku";
    public static final String SIZES = "masat";

    private ProductCsvRules() {
    }

    public record Column(String key, String label, boolean required, String hint) { }

    public record RowResult(int row, String name, ProductRequest request, List<String> errors) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }

    /** Columns of the import for a category kind, in template order. */
    public static List<Column> columns(CategoryKind kind) {
        CategoryKind k = CategoryKind.fromNullable(kind);
        boolean stockRequired = k.usesCart() && !ProductSizes.supportsSizes(k);
        List<Column> columns = new ArrayList<>();
        columns.add(new Column(NAME, "Emri", true, "Deri në " + MAX_NAME + " shkronja"));
        columns.add(new Column(DESCRIPTION, "Përshkrimi", false, "Opsional"));
        columns.add(new Column(PRICE, "Çmimi (€)", true, "P.sh. 19.99"));
        columns.add(new Column(DISCOUNT, "Çmimi me zbritje (€)", false, "Më i vogël se çmimi"));
        columns.add(new Column(STOCK, "Stoku", stockRequired,
                ProductSizes.supportsSizes(k) ? "Bosh kur plotësohen masat"
                        : k.usesCart() ? "Numër i plotë, 0 ose më shumë" : "Bosh = 1"));
        if (ProductSizes.supportsSizes(k)) {
            columns.add(new Column(SIZES, "Masat", false,
                    k == CategoryKind.SHOES ? "P.sh. 40:3|41:5" : "P.sh. S:5|M:3|L:0"));
        }
        for (CategoryField field : CategoryFieldCatalog.fieldsFor(k)) {
            columns.add(new Column(field.key(), field.label(), field.required(), hint(field)));
        }
        return columns;
    }

    private static String hint(CategoryField field) {
        return switch (field.type()) {
            case SELECT -> "Një nga: " + String.join(", ",
                    field.options().stream().map(CategoryField.Option::label).toList());
            case NUMBER -> "Numër"
                    + (field.min() != null && field.max() != null
                    ? " nga " + field.min().toPlainString() + " deri " + field.max().toPlainString() : "")
                    + (field.unit() != null ? " (" + field.unit() + ")" : "");
            default -> "Tekst";
        };
    }

    /** Template: header row plus one example row, separated by ";" so Excel opens it in columns. */
    public static String template(CategoryKind kind) {
        CategoryKind k = CategoryKind.fromNullable(kind);
        List<Column> columns = columns(k);
        char sep = ';';
        StringBuilder out = new StringBuilder("﻿");
        out.append(String.join(String.valueOf(sep), columns.stream().map(Column::key).toList()))
                .append("\r\n");
        List<String> example = new ArrayList<>();
        for (Column column : columns) {
            example.add(CsvTable.quote(exampleValue(k, column.key()), sep));
        }
        out.append(String.join(String.valueOf(sep), example)).append("\r\n");
        return out.toString();
    }

    private static String exampleValue(CategoryKind kind, String key) {
        return switch (key) {
            case NAME -> switch (kind) {
                case CAR -> "Volkswagen Golf 7";
                case PROPERTY -> "Apartament 2+1 në Bllok";
                case SHOES -> "Atlete vrapimi";
                case CLOTHING -> "Bluzë pambuku";
                case SKINCARE -> "Krem hidratues 50 ml";
                default -> "Produkti im";
            };
            case DESCRIPTION -> "Përshkrim i shkurtër";
            case PRICE -> kind == CategoryKind.CAR ? "15000" : kind == CategoryKind.PROPERTY ? "650" : "19.99";
            case DISCOUNT -> "";
            case STOCK -> ProductSizes.supportsSizes(kind) ? "" : kind.usesCart() ? "10" : "1";
            case SIZES -> kind == CategoryKind.SHOES ? "40:3|41:5" : "S:5|M:3";
            case "make" -> "Volkswagen";
            case "model" -> "Golf 7";
            case "year" -> "2017";
            case "mileageKm" -> "145000";
            case "fuel" -> "Naftë";
            case "gearbox" -> "Manuale";
            case "location" -> "Tiranë";
            case "dealType" -> "Me qira";
            case "propertyType" -> "Apartament";
            case "areaM2" -> "85";
            case "rooms" -> "2";
            case "color" -> "E zezë";
            case "brand" -> "Omnia";
            case "condition" -> "E re";
            case "productType" -> "Krem hidratues";
            case "skinType" -> "Për të gjitha llojet";
            case "volumeMl" -> "50";
            default -> "";
        };
    }

    /**
     * Maps each header cell to a column key. A header may be the key
     * ("cmimi") or the Albanian label ("Çmimi (€)"), in any case.
     * Returns the errors for unknown, duplicated or missing columns.
     */
    public static Map<Integer, String> mapHeader(CategoryKind kind, List<String> header, List<String> errors) {
        List<Column> columns = columns(kind);
        Map<Integer, String> mapping = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String cell = header.get(i) == null ? "" : header.get(i).trim();
            if (cell.isEmpty()) continue;
            Optional<Column> column = columns.stream()
                    .filter(c -> simplify(c.key()).equals(simplify(cell))
                            || simplify(c.label()).equals(simplify(cell)))
                    .findFirst();
            if (column.isEmpty()) {
                errors.add("Kolonë e panjohur: \"" + cell + "\"");
            } else if (mapping.containsValue(column.get().key())) {
                errors.add("Kolona \"" + cell + "\" është dy herë");
            } else {
                mapping.put(i, column.get().key());
            }
        }
        for (Column column : columns) {
            if (column.required() && !mapping.containsValue(column.key())) {
                errors.add("Mungon kolona e detyrueshme \"" + column.key() + "\" (" + column.label() + ")");
            }
        }
        return mapping;
    }

    /** Lower case, without accents or spaces: "Çmimi (€)" -> "cmimi€". */
    static String simplify(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[\\s_()\\-]", "");
    }

    /** Builds and checks one row. Row numbers count the header as row 1, like Excel. */
    public static RowResult validateRow(CategoryKind kind, Long categoryId, Long organizationId,
                                        int rowNumber, Map<Integer, String> mapping, List<String> cells) {
        CategoryKind k = CategoryKind.fromNullable(kind);
        Map<String, String> values = new LinkedHashMap<>();
        mapping.forEach((index, key) -> {
            String value = index < cells.size() ? cells.get(index) : "";
            values.put(key, value == null ? "" : value.trim());
        });
        List<String> errors = new ArrayList<>();

        String name = values.getOrDefault(NAME, "");
        if (name.isEmpty()) {
            errors.add("Emri mungon");
        } else if (name.length() > MAX_NAME) {
            errors.add("Emri është më i gjatë se " + MAX_NAME + " shkronja");
        }
        String description = values.getOrDefault(DESCRIPTION, "");
        if (description.length() > MAX_DESCRIPTION) {
            errors.add("Përshkrimi është më i gjatë se " + MAX_DESCRIPTION + " shkronja");
        }

        BigDecimal price = money(values.get(PRICE), "Çmimi", true, errors);
        BigDecimal discount = money(values.get(DISCOUNT), "Çmimi me zbritje", false, errors);
        if (price != null && discount != null && discount.compareTo(price) >= 0) {
            errors.add("Çmimi me zbritje duhet të jetë më i vogël se çmimi");
        }

        List<ProductRequest.SizeRequest> sizes = sizes(k, values.get(SIZES), errors);

        Integer stock = null;
        String stockText = values.getOrDefault(STOCK, "");
        if (!stockText.isEmpty()) {
            try {
                stock = Integer.valueOf(stockText);
                if (stock < 0 || stock > ProductSizes.MAX_STOCK) {
                    errors.add("Stoku duhet të jetë nga 0 deri " + ProductSizes.MAX_STOCK);
                    stock = null;
                }
            } catch (NumberFormatException e) {
                errors.add("Stoku duhet të jetë numër i plotë");
            }
        } else if (!k.usesCart()) {
            stock = 1;
        } else if (sizes.isEmpty()) {
            errors.add(ProductSizes.supportsSizes(k)
                    ? "Plotëso stokun ose masat"
                    : "Stoku mungon");
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        String brand = null;
        for (CategoryField field : CategoryFieldCatalog.fieldsFor(k)) {
            String raw = values.get(field.key());
            if (raw == null || raw.isEmpty()) continue;
            if (field.storedAsBrand()) {
                brand = raw;
                continue;
            }
            attributes.put(field.key(), field.type() == CategoryField.FieldType.SELECT
                    ? optionCode(field, raw) : raw);
        }
        try {
            ListingRules.normalize(k, attributes, brand);
        } catch (IllegalArgumentException e) {
            errors.add(albanian(k, e.getMessage()));
        }

        ProductRequest request = ProductRequest.builder()
                .name(name)
                .description(description.isEmpty() ? null : description)
                .price(price)
                .discountPrice(discount)
                .stock(sizes.isEmpty() ? stock : null)
                .categoryId(categoryId)
                .organizationId(organizationId)
                .brand(brand)
                .attributes(attributes.isEmpty() ? null : attributes)
                .sizes(sizes.isEmpty() ? null : sizes)
                .build();
        return new RowResult(rowNumber, name, request, errors);
    }

    private static BigDecimal money(String text, String label, boolean required, List<String> errors) {
        if (text == null || text.isBlank()) {
            if (required) errors.add(label + " mungon");
            return null;
        }
        String cleaned = text.replace("€", "").replace(" ", "").replace(" ", "");
        // "1.234,50" or "19,99" (Albanian) and "1234.50" (English) are all accepted.
        if (cleaned.contains(",")) {
            cleaned = cleaned.replace(".", "").replace(',', '.');
        }
        try {
            BigDecimal value = new BigDecimal(cleaned);
            if (value.signum() <= 0) {
                errors.add(label + " duhet të jetë më i madh se 0");
                return null;
            }
            if (value.scale() > 2) {
                errors.add(label + " mund të ketë të shumtën 2 shifra pas presjes");
                return null;
            }
            if (value.compareTo(new BigDecimal("99999999.99")) > 0) {
                errors.add(label + " është shumë i madh");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            errors.add(label + " duhet të jetë numër (p.sh. 19.99)");
            return null;
        }
    }

    /* "S:5|M:3" -> sizes with their stock. */
    private static List<ProductRequest.SizeRequest> sizes(CategoryKind kind, String text, List<String> errors) {
        List<ProductRequest.SizeRequest> sizes = new ArrayList<>();
        if (text == null || text.isBlank()) return sizes;
        if (!ProductSizes.supportsSizes(kind)) {
            errors.add("Masat lejohen vetëm për veshje dhe këpucë");
            return sizes;
        }
        List<ProductSizes.SizeInput> inputs = new ArrayList<>();
        for (String part : text.split("\\|")) {
            String item = part.trim();
            if (item.isEmpty()) continue;
            int colon = item.lastIndexOf(':');
            if (colon <= 0 || colon == item.length() - 1) {
                errors.add("Masa \"" + item + "\" duhet të jetë në formën masë:stok (p.sh. M:3)");
                return List.of();
            }
            try {
                int stock = Integer.parseInt(item.substring(colon + 1).trim());
                if (stock < 0 || stock > ProductSizes.MAX_STOCK) {
                    errors.add("Stoku i masës \"" + item + "\" nuk është i vlefshëm");
                    return List.of();
                }
                inputs.add(new ProductSizes.SizeInput(item.substring(0, colon).trim(), stock));
            } catch (NumberFormatException e) {
                errors.add("Stoku i masës \"" + item + "\" duhet të jetë numër i plotë");
                return List.of();
            }
        }
        try {
            for (ProductSizes.SizeInput size : ProductSizes.normalize(kind, inputs)) {
                sizes.add(ProductRequest.SizeRequest.builder().value(size.value()).stock(size.stock()).build());
            }
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
            return List.of();
        }
        return sizes;
    }

    /* "Naftë" or "diesel" -> "DIESEL"; unknown text is passed on so validation reports it. */
    static String optionCode(CategoryField field, String raw) {
        String simple = simplify(raw);
        return field.options().stream()
                .filter(o -> simplify(o.value()).equals(simple) || simplify(o.label()).equals(simple))
                .map(CategoryField.Option::value)
                .findFirst()
                .orElse(raw);
    }

    /** Turns ListingRules' messages into Albanian with the field's label. */
    static String albanian(CategoryKind kind, String message) {
        if (message == null) return "Vlerë e pavlefshme";
        String[][] patterns = {
                {"Required field missing: ", "Mungon fusha e detyrueshme: "},
                {"Invalid value for ", "Vlerë e pavlefshme për "},
                {"Value too long for ", "Vlera është shumë e gjatë për "},
                {"Not a number for ", "Duhet të jetë numër: "},
                {"Value out of range for ", "Vlera është jashtë kufijve për "},
                {"Unknown field for this category: ", "Fushë e panjohur për këtë kategori: "},
        };
        for (String[] pattern : patterns) {
            if (message.startsWith(pattern[0])) {
                String key = message.substring(pattern[0].length()).trim();
                return pattern[1] + label(kind, key);
            }
        }
        if (message.startsWith("Invalid option for ")) {
            String rest = message.substring("Invalid option for ".length());
            int colon = rest.indexOf(':');
            String key = colon < 0 ? rest : rest.substring(0, colon);
            String value = colon < 0 ? "" : rest.substring(colon + 1).trim();
            Optional<CategoryField> field = CategoryFieldCatalog.field(kind, key);
            String allowed = field.map(f -> " (lejohen: " + String.join(", ",
                    f.options().stream().map(CategoryField.Option::label).toList()) + ")").orElse("");
            return "Vlerë e pavlefshme për " + label(kind, key) + ": \"" + value + "\"" + allowed;
        }
        if (message.equals("Brand is too long")) {
            return "Marka është më e gjatë se 100 shkronja";
        }
        return message;
    }

    private static String label(CategoryKind kind, String key) {
        return CategoryFieldCatalog.field(kind, key)
                .map(field -> field.label() + " (" + key + ")")
                .orElse(key);
    }
}
