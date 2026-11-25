package com.omnia.backend.service.impl;

import com.omnia.backend.dto.request.SellerApplicationRequest;
import com.omnia.backend.enums.SellerType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Input rules for "Bëhu shitës" (seller applications) and the private-seller listing limit.
 * Pure functions with Albanian messages; every failure is an IllegalArgumentException (400).
 */
public final class SellerApplicationRules {

    /** A PRIVATE seller ("Shitës privat") may have at most this many ACTIVE products. */
    public static final int PRIVATE_ACTIVE_PRODUCT_LIMIT = 30;
    public static final String PRIVATE_LIMIT_MESSAGE =
            "Shitësit privatë mund të kenë deri në 30 njoftime aktive.";

    public static final int MAX_CATEGORIES = 6;

    /** Albanian NIPT: a letter, 8 digits, a letter (e.g. K12345678A). */
    static final Pattern NIPT = Pattern.compile("^[A-Z][0-9]{8}[A-Z]$");

    /** +355 followed by 8–9 digits, or a national number starting with 0 (e.g. 0691234567). */
    static final Pattern PHONE = Pattern.compile("^(\\+355[0-9]{8,9}|0[0-9]{8,9})$");

    private SellerApplicationRules() {
    }

    /** The application after normalisation, ready to be stored. */
    public record Normalized(
            SellerType sellerType,
            String storeName,
            String phone,
            String city,
            String nipt,
            List<Long> categoryIds,
            String description
    ) {
    }

    public static Normalized normalize(SellerApplicationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Plotëso formularin e aplikimit.");
        }
        SellerType type = sellerType(request.getSellerType());
        return new Normalized(
                type,
                storeName(request.getStoreName()),
                phone(request.getPhone()),
                city(request.getCity()),
                type == SellerType.BUSINESS ? nipt(request.getNipt()) : null,
                categoryIds(request.getCategoryIds()),
                description(request.getDescription()));
    }

    static SellerType sellerType(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        try {
            return SellerType.valueOf(value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Zgjidh llojin e shitësit: biznes ose privat.");
        }
    }

    static String storeName(String raw) {
        String name = collapse(raw);
        if (name.length() < 2 || name.length() > 80) {
            throw new IllegalArgumentException("Emri i dyqanit duhet të ketë 2 deri në 80 karaktere.");
        }
        return name;
    }

    static String city(String raw) {
        String city = collapse(raw);
        if (city.length() < 2 || city.length() > 60) {
            throw new IllegalArgumentException("Qyteti duhet të ketë 2 deri në 60 karaktere.");
        }
        return city;
    }

    /** Removes spaces, dashes, dots and brackets; 00355… becomes +355…. */
    static String phone(String raw) {
        String phone = raw == null ? "" : raw.replaceAll("[\\s().-]", "");
        if (phone.startsWith("00355")) {
            phone = "+" + phone.substring(2);
        }
        if (!PHONE.matcher(phone).matches()) {
            throw new IllegalArgumentException(
                    "Numri i telefonit duhet të jetë si +355691234567 ose 0691234567.");
        }
        return phone;
    }

    static String nipt(String raw) {
        String nipt = raw == null ? "" : raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (nipt.isEmpty()) {
            throw new IllegalArgumentException("NIPT është i detyrueshëm për bizneset.");
        }
        if (!NIPT.matcher(nipt).matches()) {
            throw new IllegalArgumentException("NIPT duhet të ketë formatin K12345678A.");
        }
        return nipt;
    }

    static String description(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String description = raw.trim();
        if (description.length() > 1000) {
            throw new IllegalArgumentException("Përshkrimi mund të ketë deri në 1000 karaktere.");
        }
        return description;
    }

    static List<Long> categoryIds(List<Long> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Zgjidh të paktën një kategori.");
        }
        if (raw.size() > MAX_CATEGORIES) {
            throw new IllegalArgumentException("Mund të zgjedhësh deri në 6 kategori.");
        }
        Set<Long> seen = new HashSet<>();
        for (Long id : raw) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("Një nga kategoritë nuk është e vlefshme.");
            }
            if (!seen.add(id)) {
                throw new IllegalArgumentException("Kategoritë nuk duhet të përsëriten.");
            }
        }
        return List.copyOf(raw);
    }

    /** "Shkruaj arsyen" — 3 to 500 characters. */
    static String rejectReason(String raw) {
        String reason = raw == null ? "" : raw.trim();
        if (reason.length() < 3) {
            throw new IllegalArgumentException("Shkruaj arsyen e refuzimit (të paktën 3 karaktere).");
        }
        if (reason.length() > 500) {
            throw new IllegalArgumentException("Arsyeja mund të ketë deri në 500 karaktere.");
        }
        return reason;
    }

    static String joinIds(List<Long> ids) {
        return String.join(",", ids.stream().map(String::valueOf).toList());
    }

    static List<Long> splitIds(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : Arrays.asList(stored.split(","))) {
            String value = part.trim();
            if (!value.isEmpty()) {
                try {
                    ids.add(Long.parseLong(value));
                } catch (NumberFormatException ignored) {
                    // Stored by this service only; skip anything malformed rather than fail a read.
                }
            }
        }
        return List.copyOf(ids);
    }

    /** "Butiku", then "Butiku (2)", "Butiku (3)"… */
    static String numberedName(String base, int attempt) {
        return attempt <= 1 ? base : base + " (" + attempt + ")";
    }

    private static String collapse(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }
}
