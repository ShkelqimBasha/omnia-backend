package com.omnia.backend.service.impl;

import com.omnia.backend.dto.request.SellerApplicationRequest;
import com.omnia.backend.enums.SellerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SellerApplicationRulesTest {

    private static SellerApplicationRequest.SellerApplicationRequestBuilder valid() {
        return SellerApplicationRequest.builder()
                .sellerType("BUSINESS")
                .storeName("  Butiku   Ana ")
                .phone("+355 69 123 4567")
                .city(" Tiranë ")
                .nipt("k12345678a")
                .categoryIds(List.of(3L, 7L))
                .description("  Rroba  ");
    }

    private static String message(SellerApplicationRequest request) {
        return assertThrows(IllegalArgumentException.class,
                () -> SellerApplicationRules.normalize(request)).getMessage();
    }

    @Test
    void normalizesAValidBusinessApplication() {
        SellerApplicationRules.Normalized n = SellerApplicationRules.normalize(valid().build());
        assertEquals(SellerType.BUSINESS, n.sellerType());
        assertEquals("Butiku Ana", n.storeName());
        assertEquals("+355691234567", n.phone());
        assertEquals("Tiranë", n.city());
        assertEquals("K12345678A", n.nipt());
        assertEquals(List.of(3L, 7L), n.categoryIds());
        assertEquals("Rroba", n.description());
    }

    @Test
    void privateSellersIgnoreTheNipt() {
        SellerApplicationRules.Normalized n = SellerApplicationRules.normalize(
                valid().sellerType("private").nipt("anything").description("  ").build());
        assertEquals(SellerType.PRIVATE, n.sellerType());
        assertNull(n.nipt());
        assertNull(n.description());
    }

    @Test
    void businessNeedsAWellFormedNipt() {
        assertEquals("NIPT është i detyrueshëm për bizneset.", message(valid().nipt(null).build()));
        assertEquals("NIPT duhet të ketë formatin K12345678A.", message(valid().nipt("K1234567A").build()));
        assertEquals("NIPT duhet të ketë formatin K12345678A.", message(valid().nipt("112345678A").build()));
        assertEquals("K12345678A", SellerApplicationRules.nipt(" k 12345678 a "));
    }

    @Test
    void sellerTypeIsRequired() {
        message(valid().sellerType(null).build());
        message(valid().sellerType("COMPANY").build());
    }

    @Test
    void storeNameCityAndDescriptionLengths() {
        message(valid().storeName(" A ").build());
        message(valid().storeName("x".repeat(81)).build());
        assertEquals("x".repeat(80), SellerApplicationRules.storeName("x".repeat(80)));
        message(valid().city("T").build());
        message(valid().city("x".repeat(61)).build());
        message(valid().description("x".repeat(1001)).build());
        assertEquals(1000, SellerApplicationRules.description("x".repeat(1000)).length());
    }

    @ParameterizedTest
    @ValueSource(strings = {"+355691234567", "0691234567", "069 123 4567", "00355 69 123 4567", "+355 4 222 3333", "(069) 123-4567"})
    void acceptsAlbanianPhones(String phone) {
        SellerApplicationRules.phone(phone);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345", "+39 333 1234567", "691234567", "+3556912345678901", "06912abc67"})
    void rejectsOtherPhones(String phone) {
        assertThrows(IllegalArgumentException.class, () -> SellerApplicationRules.phone(phone));
    }

    @Test
    void oneToSixDistinctCategories() {
        assertEquals("Zgjidh të paktën një kategori.", message(valid().categoryIds(List.of()).build()));
        assertEquals("Zgjidh të paktën një kategori.", message(valid().categoryIds(null).build()));
        assertEquals("Mund të zgjedhësh deri në 6 kategori.",
                message(valid().categoryIds(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L)).build()));
        assertEquals("Kategoritë nuk duhet të përsëriten.", message(valid().categoryIds(List.of(1L, 1L)).build()));
        message(valid().categoryIds(Arrays.asList(1L, null)).build());
        message(valid().categoryIds(List.of(0L)).build());
        assertEquals(6, SellerApplicationRules.categoryIds(List.of(1L, 2L, 3L, 4L, 5L, 6L)).size());
    }

    @Test
    void storedIdsRoundTrip() {
        assertEquals("3,7,11", SellerApplicationRules.joinIds(List.of(3L, 7L, 11L)));
        assertEquals(List.of(3L, 7L, 11L), SellerApplicationRules.splitIds("3, 7,,11"));
        assertEquals(List.of(), SellerApplicationRules.splitIds(null));
    }

    @Test
    void rejectReasonIsThreeToFiveHundredCharacters() {
        assertThrows(IllegalArgumentException.class, () -> SellerApplicationRules.rejectReason(null));
        assertThrows(IllegalArgumentException.class, () -> SellerApplicationRules.rejectReason("  ab "));
        assertThrows(IllegalArgumentException.class, () -> SellerApplicationRules.rejectReason("x".repeat(501)));
        assertEquals("abc", SellerApplicationRules.rejectReason(" abc "));
    }

    @Test
    void takenNamesAreNumbered() {
        assertEquals("Butiku", SellerApplicationRules.numberedName("Butiku", 1));
        assertEquals("Butiku (3)", SellerApplicationRules.numberedName("Butiku", 3));
        assertEquals("butiku-ana-cele", SellerApplicationService.slugBase("Butiku Ana & Çelë"));
        assertEquals("organization", SellerApplicationService.slugBase("!!!"));
    }

    @Test
    void privateLimitIsThirty() {
        assertEquals(30, SellerApplicationRules.PRIVATE_ACTIVE_PRODUCT_LIMIT);
    }
}
