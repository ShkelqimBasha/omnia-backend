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

/**
 * Listing fields and filters of each category kind, with Albanian labels.
 *
 * <p>Clients (web and Android) build the seller form, the detail page and the
 * filters from these definitions, so a field added here shows up everywhere.
 * A key must keep the same type in every kind that uses it (filters look keys up
 * across kinds). Only the first fields of each kind are required; existing
 * listings stay valid when optional fields are added.
 */
public final class CategoryFieldCatalog {

    private static final List<Option> CONDITION = List.of(
            new Option("NEW", "E re"),
            new Option("LIKE_NEW", "Si e re"),
            new Option("USED", "E përdorur"));

    private static final List<Option> GENERAL_CONDITION = List.of(
            new Option("NEW", "E re"),
            new Option("LIKE_NEW", "Si e re"),
            new Option("USED", "E përdorur"),
            new Option("REFURBISHED", "E rinovuar (refurbished)"));

    private static final List<Option> YES_NO = List.of(
            new Option("YES", "Po"),
            new Option("NO", "Jo"));

    private static final List<Option> GENDERS = List.of(
            new Option("WOMEN", "Femra"),
            new Option("MEN", "Meshkuj"),
            new Option("KIDS", "Fëmijë"),
            new Option("UNISEX", "Unisex"));

    private static final List<Option> CLOTHING_MATERIALS = List.of(
            new Option("COTTON", "Pambuk"),
            new Option("LINEN", "Li"),
            new Option("WOOL", "Lesh"),
            new Option("LEATHER", "Lëkurë"),
            new Option("POLYESTER", "Poliester"),
            new Option("SILK", "Mëndafsh"),
            new Option("DENIM", "Denim (xhins)"),
            new Option("OTHER", "Tjetër"));

    private static final List<Option> STYLES = List.of(
            new Option("CASUAL", "Casual"),
            new Option("FORMAL", "Formal"),
            new Option("SPORT", "Sportiv"),
            new Option("STREETWEAR", "Streetwear"),
            new Option("ELEGANT", "Elegant"));

    private static final List<Option> FITS = List.of(
            new Option("SLIM", "Slim fit"),
            new Option("REGULAR", "Regular fit"),
            new Option("OVERSIZED", "Oversized"),
            new Option("RELAXED", "Relaxed fit"));

    private static final List<Option> CLOTHING_SEASONS = List.of(
            new Option("SPRING", "Pranverë"),
            new Option("SUMMER", "Verë"),
            new Option("AUTUMN", "Vjeshtë"),
            new Option("WINTER", "Dimër"),
            new Option("ALL_SEASON", "Gjithë vitin"));

    private static final List<Option> SHOE_TYPES = List.of(
            new Option("SNEAKERS", "Atlete"),
            new Option("RUNNING", "Vrapim"),
            new Option("BOOTS", "Çizme"),
            new Option("SANDALS", "Sandale"),
            new Option("FORMAL", "Këpucë klasike"),
            new Option("LOAFERS", "Mokasina"),
            new Option("HEELS", "Taka"),
            new Option("SLIPPERS", "Pantofla"),
            new Option("OTHER", "Tjetër"));

    private static final List<Option> SHOE_MATERIALS = List.of(
            new Option("LEATHER", "Lëkurë"),
            new Option("SUEDE", "Kamosh"),
            new Option("TEXTILE", "Tekstil"),
            new Option("SYNTHETIC", "Sintetik"),
            new Option("OTHER", "Tjetër"));

    private static final List<Option> SHOE_USES = List.of(
            new Option("EVERYDAY", "Përditshmëri"),
            new Option("RUNNING", "Vrapim"),
            new Option("GYM", "Palestër"),
            new Option("FOOTBALL", "Futboll"),
            new Option("HIKING", "Hiking"),
            new Option("FORMAL", "Formal"));

    private static final List<Option> SHOE_HEIGHTS = List.of(
            new Option("LOW", "E ulët (low-top)"),
            new Option("MID", "Mesatare (mid-top)"),
            new Option("HIGH", "E lartë (high-top)"));

    private static final List<Option> SHOE_SEASONS = List.of(
            new Option("SUMMER", "Verë"),
            new Option("WINTER", "Dimër"),
            new Option("ALL_SEASON", "Gjithë vitin"));

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

    private static final List<Option> SKIN_CONCERNS = List.of(
            new Option("ACNE", "Akne"),
            new Option("SPOTS", "Njolla"),
            new Option("WRINKLES", "Rrudha"),
            new Option("DEHYDRATION", "Dehidratim"),
            new Option("REDNESS", "Skuqje"),
            new Option("PORES", "Pore"));

    private static final List<Option> ACTIVE_INGREDIENTS = List.of(
            new Option("RETINOL", "Retinol"),
            new Option("NIACINAMIDE", "Niacinamide"),
            new Option("VITAMIN_C", "Vitaminë C"),
            new Option("HYALURONIC_ACID", "Acid hialuronik"),
            new Option("AHA", "AHA"),
            new Option("BHA", "BHA"),
            new Option("OTHER", "Tjetër"));

    private static final List<Option> SKIN_AREAS = List.of(
            new Option("FACE", "Fytyrë"),
            new Option("BODY", "Trup"),
            new Option("EYES", "Sy"),
            new Option("LIPS", "Buzë"),
            new Option("HANDS", "Duar"));

    private static final List<Option> TEXTURES = List.of(
            new Option("CREAM", "Krem"),
            new Option("GEL", "Xhel"),
            new Option("SERUM", "Serum"),
            new Option("OIL", "Vaj"),
            new Option("FOAM", "Shkumë"),
            new Option("LOTION", "Locion"));

    private static final List<Option> FUELS = List.of(
            new Option("PETROL", "Benzinë"),
            new Option("DIESEL", "Naftë"),
            new Option("HYBRID", "Hibrid"),
            new Option("PLUGIN_HYBRID", "Hibrid plug-in"),
            new Option("ELECTRIC", "Elektrike"),
            new Option("LPG", "Gaz"),
            new Option("OTHER", "Tjetër"));

    private static final List<Option> GEARBOXES = List.of(
            new Option("MANUAL", "Manuale"),
            new Option("AUTOMATIC", "Automatike"));

    private static final List<Option> BODY_TYPES = List.of(
            new Option("SEDAN", "Sedan"),
            new Option("HATCHBACK", "Hatchback"),
            new Option("SUV", "SUV / Fuoristradë"),
            new Option("COUPE", "Kupe"),
            new Option("CABRIO", "Kabriolet"),
            new Option("WAGON", "Karavan"),
            new Option("VAN", "Furgon"),
            new Option("PICKUP", "Pick-up"),
            new Option("OTHER", "Tjetër"));

    private static final List<Option> CAR_CONDITION = List.of(
            new Option("NEW", "E re"),
            new Option("USED", "E përdorur"),
            new Option("DAMAGED", "E dëmtuar"));

    private static final List<Option> DRIVETRAINS = List.of(
            new Option("FWD", "Përpara (FWD)"),
            new Option("RWD", "Prapa (RWD)"),
            new Option("AWD", "4x4 / AWD"));

    private static final List<Option> CUSTOMS = List.of(
            new Option("PAID", "E zhdoganuar"),
            new Option("UNPAID", "E pazhdoganuar"));

    private static final List<Option> REGISTRATION = List.of(
            new Option("ALBANIAN", "Targa shqiptare"),
            new Option("FOREIGN", "Targa të huaja"),
            new Option("NONE", "Pa targa"));

    private static final List<Option> EMISSIONS = List.of(
            new Option("EURO3", "Euro 3"),
            new Option("EURO4", "Euro 4"),
            new Option("EURO5", "Euro 5"),
            new Option("EURO6", "Euro 6"));

    private static final List<Option> ACCIDENTS = List.of(
            new Option("NONE", "Pa aksidente"),
            new Option("YES", "Me aksidente"),
            new Option("UNKNOWN", "E padeklaruar"));

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
            new Option("GARAGE", "Garazh"),
            new Option("OTHER", "Tjetër"));

    private static final List<Option> FURNISHED = List.of(
            new Option("FURNISHED", "E mobiluar"),
            new Option("PARTIAL", "Pjesërisht e mobiluar"),
            new Option("UNFURNISHED", "E pamobiluar"));

    private static final List<Option> PROPERTY_CONDITION = List.of(
            new Option("NEW", "Ndërtim i ri"),
            new Option("RENOVATED", "E rinovuar"),
            new Option("GOOD", "Në gjendje të mirë"),
            new Option("NEEDS_RENOVATION", "Kërkon rinovim"),
            new Option("UNDER_CONSTRUCTION", "Në ndërtim"));

    private static final List<Option> HEATING = List.of(
            new Option("CENTRAL", "Qendrore"),
            new Option("AIR_CONDITIONING", "Kondicioner"),
            new Option("INDIVIDUAL", "Individuale"),
            new Option("NONE", "Pa ngrohje"));

    private static final List<Option> DOCUMENTS = List.of(
            new Option("CERTIFICATE", "Me certifikatë pronësie"),
            new Option("IN_PROGRESS", "Në proces"),
            new Option("UNKNOWN", "E padeklaruar"));

    private static final List<Option> PUBLISHERS = List.of(
            new Option("OWNER", "Pronar"),
            new Option("AGENCY", "Agjenci imobiliare"));

    private static final Map<CategoryKind, List<CategoryField>> FIELDS =
            new EnumMap<>(CategoryKind.class);

    static {
        String nextYear = String.valueOf(Year.now().getValue() + 1);

        FIELDS.put(CategoryKind.GENERAL, List.of(
                text(CategoryField.BRAND, "Marka", false),
                select("condition", "Gjendja", false, GENERAL_CONDITION),
                text("color", "Ngjyra", false),
                number("warrantyMonths", "Garancia", false, "muaj", "0", "120")));

        // Sizes of clothing and shoes are not fields: each size has its own stock (ProductSizes).
        FIELDS.put(CategoryKind.CLOTHING, List.of(
                select("gender", "Për", false, GENDERS),
                text("color", "Ngjyra", false),
                text(CategoryField.BRAND, "Marka", false),
                select("material", "Materiali", false, CLOTHING_MATERIALS),
                select("style", "Stili", false, STYLES),
                select("fit", "Prerja", false, FITS),
                select("season", "Sezoni", false, CLOTHING_SEASONS),
                select("condition", "Gjendja", false, CONDITION)));
        FIELDS.put(CategoryKind.SHOES, List.of(
                select("gender", "Për", false, GENDERS),
                select("shoeType", "Lloji", false, SHOE_TYPES),
                text(CategoryField.BRAND, "Marka", false),
                text("color", "Ngjyra", false),
                select("material", "Materiali", false, SHOE_MATERIALS),
                select("usage", "Përdorimi", false, SHOE_USES),
                select("shoeHeight", "Lartësia", false, SHOE_HEIGHTS),
                select("season", "Sezoni", false, SHOE_SEASONS),
                select("condition", "Gjendja", false, CONDITION)));
        FIELDS.put(CategoryKind.SKINCARE, List.of(
                text(CategoryField.BRAND, "Marka", false),
                select("productType", "Lloji i produktit", false, SKINCARE_TYPES),
                select("skinType", "Lloji i lëkurës", false, SKIN_TYPES),
                select("skinConcern", "Problematika", false, SKIN_CONCERNS),
                select("activeIngredient", "Përbërësi aktiv", false, ACTIVE_INGREDIENTS),
                number("spf", "SPF", false, null, "0", "100"),
                select("skinArea", "Zona", false, SKIN_AREAS),
                select("texture", "Tekstura", false, TEXTURES),
                number("volumeMl", "Vëllimi", false, "ml", "1", "5000"),
                info("expiryDate", "Data e skadencës (MM/VVVV)", 7),
                info("batchNumber", "Numri i lotit", 40),
                new CategoryField("ingredients", "Përbërësit", FieldType.TEXTAREA, false,
                        List.of(), null, null, null, 1000, true)));
        FIELDS.put(CategoryKind.CAR, List.of(
                text("make", "Marka", true),
                text("model", "Modeli", true),
                number("year", "Viti", true, null, "1950", nextYear),
                number("mileageKm", "Kilometrat", true, "km", "0", "2000000"),
                select("fuel", "Karburanti", true, FUELS),
                select("gearbox", "Kambio", true, GEARBOXES),
                text("location", "Vendndodhja", true),
                select("bodyType", "Karroceria", false, BODY_TYPES),
                select("condition", "Gjendja", false, CAR_CONDITION),
                number("powerHp", "Fuqia", false, "HP", "1", "2000"),
                number("engineCc", "Motori", false, "cm³", "0", "10000"),
                select("drivetrain", "Tërheqja", false, DRIVETRAINS),
                text("color", "Ngjyra", false),
                number("doors", "Dyert", false, null, "2", "5"),
                number("seats", "Vendet", false, null, "1", "9"),
                select("customs", "Dogana", false, CUSTOMS),
                select("registration", "Targat", false, REGISTRATION),
                select("emissionStandard", "Standardi i emetimeve", false, EMISSIONS),
                select("accidents", "Aksidente", false, ACCIDENTS),
                number("batteryKwh", "Bateria (elektrike/hibride)", false, "kWh", "1", "300"),
                number("rangeKm", "Autonomia (elektrike)", false, "km", "1", "2000")));
        FIELDS.put(CategoryKind.PROPERTY, List.of(
                select("dealType", "Shitje apo qira", true, DEAL_TYPES),
                select("propertyType", "Lloji i pronës", true, PROPERTY_TYPES),
                text("location", "Vendndodhja", true),
                number("areaM2", "Sipërfaqja", true, "m²", "1", "1000000"),
                number("rooms", "Dhoma", false, null, "0", "100"),
                text("city", "Qyteti", false),
                number("bathrooms", "Tualete", false, null, "0", "20"),
                number("floor", "Kati", false, null, "-3", "100"),
                number("totalFloors", "Kate gjithsej", false, null, "1", "100"),
                select("furnished", "Mobilimi", false, FURNISHED),
                select("propertyCondition", "Gjendja", false, PROPERTY_CONDITION),
                number("yearBuilt", "Viti i ndërtimit", false, null, "1800", nextYear),
                select("elevator", "Ashensor", false, YES_NO),
                select("parking", "Parkim", false, YES_NO),
                select("balcony", "Ballkon", false, YES_NO),
                select("heating", "Ngrohja", false, HEATING),
                select("documents", "Dokumentacioni", false, DOCUMENTS),
                select("publisher", "Publikuesi", false, PUBLISHERS)));
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

    /** Optional free text shown on the listing but not offered as a filter. */
    private static CategoryField info(String key, String label, int maxLength) {
        return new CategoryField(key, label, FieldType.TEXT, false, List.of(),
                null, null, null, maxLength, false);
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
