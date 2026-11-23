package com.omnia.backend.enums;

/** "Raporto njoftimin" reasons, with the Albanian labels the apps show. */
public enum ProductReportReason {
    FRAUD("Mashtrim"),
    PROHIBITED("Produkt i ndaluar"),
    FAKE_PRICE("Çmim i rremë"),
    OTHER("Tjetër");

    private final String label;

    ProductReportReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
