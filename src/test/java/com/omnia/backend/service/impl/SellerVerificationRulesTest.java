package com.omnia.backend.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SellerVerificationRulesTest {

    @Test
    void niptIsALetterEightDigitsAndALetter() {
        assertEquals("K12345678A", SellerVerificationService.normalizeNipt(" k1234 5678a "));
        assertEquals("L01234567B", SellerVerificationService.normalizeNipt("L01234567B"));
        for (String bad : new String[]{null, "", "12345678", "K1234567A", "K123456789A", "112345678A", "K12345678"}) {
            assertThrows(IllegalArgumentException.class, () -> SellerVerificationService.normalizeNipt(bad), bad);
        }
    }

    @Test
    void refusalNeedsAShortReason() {
        assertEquals("Dokument i palexueshëm", SellerVerificationService.requireReason("  Dokument i palexueshëm "));
        assertThrows(IllegalArgumentException.class, () -> SellerVerificationService.requireReason(" a "));
        assertThrows(IllegalArgumentException.class, () -> SellerVerificationService.requireReason(null));
        assertThrows(IllegalArgumentException.class, () -> SellerVerificationService.requireReason("x".repeat(501)));
    }
}
