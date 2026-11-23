package com.omnia.backend.catalog;

import com.omnia.backend.catalog.CategoryField.FieldType;
import com.omnia.backend.catalog.CategoryField.Option;
import com.omnia.backend.enums.CategoryKind;

import java.math.BigDecimal;
import java.time.Year;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Listing fields and filters of each category kind, with Albanian labels. */
public final class CategoryFieldCatalog {

    private static final List<Option> CONDITION = List.of(
            new Option("NEW", "E re"),
            new Option("LIKE_NEW", "Si e re"),
            new Option("USED", "E përdorur"));

    private static final List<Option> CLOTHING_SIZES = List.of(
            new Option("XS", "XS"), new Option("S", "S"), new Option("M", "M"),
            new Option("L", "L"), new Option("XL", "XL"), new Option("XXL", "XXL"),
            new Option("XXXL", "3XL"), new Option("ONE_SIZE", "Një masë"));

    private static final List<Option> SKINCARE_TYPES = List.of(
            new Option("CLEANSER", "Pastrues"),
            new Option("MOISTURIZER", "Krem hidratues"),
            new Option("SERUM", "Serum"),
            new Option("SUNSCREEN", "Mbrojtje nga dielli"),
            new Option("TONER", "Tonik"),
            new Option("MASK", "Maskë"),
            new Option("EXFOLIANT", "Eksfoliant"),
            new Option("EYE_CARE", "Kujdes për sytë"),
            new Option("OTHER", "Tjetër"));

    private static final List<Option> SKIN_TYPES = List.of(
            new Option("ALL", "Për të gjitha llojet"),
            new Option("NORMAL", "Normale"),
            new Option("DRY", "E thatë"),
            new Option("OILY", "E yndyrshme"),
            new Option("COMBINATION", "E përzier"),
            new Option("SENSITIVE", "E ndjeshme"));

    private static final List<Option> FUELS = List.of(
            new Option("PETROL", "Benzinë"),
            new Option("DIESEL", "Naftë"),
            new Option("HYBRID", "Hibrid"),
            new Option("ELECTRIC", "Elektrike"),
            new Option("LPG", "Gaz"),
            new Option("OTHER", "Tjetër"));

    private static final List<Option> GEARBOXES = List.of(
            new Option("MANUAL", "Manuale"),
            new Option("AUTOMATIC", "Automatike"));

    private static final List<Option> DEAL_TYPES = List.of(
            new Option("SALE", "Në shitje"),
            new Option("RENT", "Me qira"));

    private static final List<Option> PROPERTY_TYPES = List.of(
            new Option("APARTMENT", "Apartament"),
            new Option("HOUSE", "Shtëpi"),
            new Option("VILLA", "Vilë"),
            new Option("LAND", "Truall"),
            new Option("OFFICE", "Zyrë"),
            new Option("SHOP", "Dyqan"),
            new Option("WAREHOUSE", "Magazinë"),
            new Option("OTHER", "Tjetër"));

    private static final Map<CategoryKind, List<CategoryField>> FIELDS =
            new EnumMap<>(CategoryKind.class);

    static {
        FIELDS.put(CategoryKind.GENERAL, List.of());
        FIELDS.put(CategoryKind.CLOTHING, List.of(
                select("size", "Masa", false, CLOTHING_SIZES),
                text("color", "Ngjyra", false),
                text(CategoryField.BRAND, "Marka", false),
                select("condition", "Gjendja", false, CONDITION)));
        FIELDS.put(CategoryKind.SHOES, List.of(
                number("shoeSize", "Numri", false, null, "15", "50"),
                text(CategoryField.BRAND, "Marka", false),
                text("color", "Ngjyra", false),
                select("condition", "Gjendja", false, CONDITION)));
        FIELDS.put(CategoryKind.SKINCARE, List.of(
                text(CategoryField.BRAND, "Marka", false),
                select("productType", "Lloji i produktit", false, SKINCARE_TYPES),
                select("skinType", "Lloji i lëkurës", false, SKIN_TYPES),
                number("volumeMl", "Vëllimi", false, "ml", "1", "5000"),
                new CategoryField("ingredients", "Përbërësit", FieldType.TEXTAREA, false,
                        List.of(), null, null, null, 1000, true)));
        FIELDS.put(CategoryKind.CAR, List.of(
                text("make", "Marka", true),
                text("model", "Modeli", true),
                number("year", "Viti", true, null, "1950",
                        String.valueOf(Year.now().getValue() + 1)),
                number("mileageKm", "Kilometrat", true, "km", "0", "2000000"),
                select("fuel", "Karburanti", true, FUELS),
                select("gearbox", "Kambio", true, GEARBOXES),
                text("location", "Vendndodhja", true)));
        FIELDS.put(CategoryKind.PROPERTY, List.of(
                select("dealType", "Shitje apo qira", true, DEAL_TYPES),
                select("propertyType", "Lloji i pronës", true, PROPERTY_TYPES),
                text("location", "Vendndodhja", true),
                number("areaM2", "Sipërfaqja", true, "m²", "1", "1000000"),
                number("rooms", "Dhoma", false, null, "0", "100")));
    }

    private CategoryFieldCatalog() {
    }

    public static List<CategoryField> fieldsFor(CategoryKind kind) {
        return FIELDS.getOrDefault(CategoryKind.fromNullable(kind), List.of());
    }

    public static Optional<CategoryField> field(CategoryKind kind, String key) {
        return fieldsFor(kind).stream().filter(f -> f.key().equals(key)).findFirst();
    }

    /** Every attribute key used by any kind (used to validate filter keys). */
    public static Optional<CategoryField> anyField(String key) {
        return FIELDS.values().stream().flatMap(List::stream)
                .filter(f -> f.key().equals(key) && !f.storedAsBrand())
                .findFirst();
    }

    private static CategoryField text(String key, String label, boolean required) {
        return new CategoryField(key, label, FieldType.TEXT, required, List.of(),
                null, null, null, 120, true);
    }

    private static CategoryField select(String key, String label, boolean required,
                                        List<Option> options) {
        return new CategoryField(key, label, FieldType.SELECT, required, options,
                null, null, null, null, true);
    }

    private static CategoryField number(String key, String label, boolean required,
                                        String unit, String min, String max) {
        return new CategoryField(key, label, FieldType.NUMBER, required, List.of(), unit,
                min == null ? null : new BigDecimal(min),
                max == null ? null : new BigDecimal(max), null, true);
    }
}
