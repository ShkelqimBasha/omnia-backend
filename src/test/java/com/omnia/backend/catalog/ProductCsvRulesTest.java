package com.omnia.backend.catalog;

import com.omnia.backend.enums.CategoryKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductCsvRulesTest {

    @Test
    void csvReadsQuotesSemicolonsBomAndLineBreaksInsideQuotes() {
        CsvTable.Parsed parsed = CsvTable.parse("﻿emri;pershkrimi\r\n\"Bluzë; e zezë\";\"rresht 1\nrresht \"\"2\"\"\"\r\n");
        assertEquals(';', parsed.separator());
        assertEquals(List.of("emri", "pershkrimi"), parsed.rows().get(0));
        assertEquals(List.of("Bluzë; e zezë", "rresht 1\nrresht \"2\""), parsed.rows().get(1));
    }

    @Test
    void commaIsTheSeparatorWhenTheHeaderUsesCommas() {
        CsvTable.Parsed parsed = CsvTable.parse("emri,cmimi\nFilxhan,4.50");
        assertEquals(',', parsed.separator());
        assertEquals(List.of("Filxhan", "4.50"), parsed.rows().get(1));
    }

    @Test
    void headerAcceptsKeysOrAlbanianLabelsAndReportsUnknownAndMissingColumns() {
        List<String> errors = new ArrayList<>();
        Map<Integer, String> mapping = ProductCsvRules.mapHeader(CategoryKind.GENERAL,
                List.of("Emri", "Çmimi (€)", "STOKU"), errors);
        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(Map.of(0, "emri", 1, "cmimi", 2, "stoku"), mapping);

        errors.clear();
        ProductCsvRules.mapHeader(CategoryKind.CAR, List.of("emri", "cmimi", "ngjyra"), errors);
        assertTrue(errors.contains("Kolonë e panjohur: \"ngjyra\""), errors.toString());
        assertTrue(errors.contains("Mungon kolona e detyrueshme \"year\" (Viti)"), errors.toString());
    }

    @Test
    void clothingRowWithSizesNeedsNoStock() {
        List<String> header = List.of("emri", "cmimi", "masat", "condition");
        List<String> errors = new ArrayList<>();
        Map<Integer, String> mapping = ProductCsvRules.mapHeader(CategoryKind.CLOTHING, header, errors);
        ProductCsvRules.RowResult row = ProductCsvRules.validateRow(CategoryKind.CLOTHING, 3L, 7L, 2,
                mapping, List.of("Bluzë", "19,99", "S:5|m:3", "E re"));
        assertTrue(row.valid(), row.errors().toString());
        assertEquals(new BigDecimal("19.99"), row.request().getPrice());
        assertNull(row.request().getStock());
        assertEquals(2, row.request().getSizes().size());
        assertEquals("NEW", row.request().getAttributes().get("condition"));
    }

    @Test
    void generalRowReportsEveryProblemInAlbanian() {
        List<String> errors = new ArrayList<>();
        Map<Integer, String> mapping = ProductCsvRules.mapHeader(CategoryKind.GENERAL,
                List.of("emri", "cmimi", "cmimi_me_zbritje", "stoku"), errors);
        ProductCsvRules.RowResult row = ProductCsvRules.validateRow(CategoryKind.GENERAL, 3L, 7L, 5,
                mapping, List.of("Filxhan", "4.50", "6", "-1"));
        assertEquals(List.of(
                "Çmimi me zbritje duhet të jetë më i vogël se çmimi",
                "Stoku duhet të jetë nga 0 deri 1000000"), row.errors());
        assertEquals(5, row.row());
    }

    @Test
    void wrongOptionListsTheAllowedLabels() {
        assertEquals("Vlerë e pavlefshme për Karburanti (fuel): \"Avull\" (lejohen: Benzinë, Naftë, "
                        + "Hibrid, Elektrike, Gaz, Tjetër)",
                ProductCsvRules.albanian(CategoryKind.CAR, "Invalid option for fuel: Avull"));
        assertEquals("DIESEL", ProductCsvRules.optionCode(
                CategoryFieldCatalog.field(CategoryKind.CAR, "fuel").orElseThrow(), "naftë"));
    }

    @Test
    void templateHasHeaderAndAnExampleThatPassesValidation() {
        for (CategoryKind kind : CategoryKind.values()) {
            List<List<String>> rows = CsvTable.parse(ProductCsvRules.template(kind)).rows();
            List<String> errors = new ArrayList<>();
            Map<Integer, String> mapping = ProductCsvRules.mapHeader(kind, rows.get(0), errors);
            assertTrue(errors.isEmpty(), kind + ": " + errors);
            ProductCsvRules.RowResult row = ProductCsvRules.validateRow(kind, 1L, 1L, 2, mapping, rows.get(1));
            assertTrue(row.valid(), kind + ": " + row.errors());
        }
    }

    @Test
    void carsAndPropertyMayHaveTwentyPhotosOthersTen() {
        assertEquals(20, ProductImageLimits.maxFor(CategoryKind.CAR));
        assertEquals(20, ProductImageLimits.maxFor(CategoryKind.PROPERTY));
        assertEquals(10, ProductImageLimits.maxFor(CategoryKind.CLOTHING));
        assertEquals(10, ProductImageLimits.maxFor(null));
    }
}
