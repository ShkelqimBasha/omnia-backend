package com.omnia.backend.enums;

/**
 * What kind of listings a category holds. It decides the listing fields,
 * the filters and how a buyer can act on a listing.
 */
public enum CategoryKind {
    GENERAL,
    CLOTHING,
    SHOES,
    SKINCARE,
    CAR,
    PROPERTY,
    /** Phones, laptops, TVs…: like GENERAL (cart) but with device fields. */
    ELECTRONICS;

    /** Cart and online payment, as today. */
    public boolean usesCart() {
        return this == GENERAL || this == CLOTHING || this == SHOES || this == SKINCARE || this == ELECTRONICS;
    }

    /** "Kontakto shitësin" / "Kontakto". */
    public boolean allowsContact() {
        return this == CAR || this == PROPERTY;
    }

    /** "Kërko vizitë". */
    public boolean allowsVisitRequest() {
        return this == PROPERTY;
    }

    public static CategoryKind fromNullable(CategoryKind kind) {
        return kind == null ? GENERAL : kind;
    }
}
