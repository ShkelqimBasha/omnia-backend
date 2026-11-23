package com.omnia.backend.catalog;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 reader for the product import: quoted fields, doubled
 * quotes, line breaks inside quotes, a UTF-8 BOM, and either "," or ";" as
 * separator (Excel in Albanian/European locales saves with ";").
 */
public final class CsvTable {

    private CsvTable() {
    }

    public record Parsed(char separator, List<List<String>> rows) { }

    public static Parsed parse(String text) {
        String content = text == null ? "" : text;
        if (!content.isEmpty() && content.charAt(0) == '﻿') {
            content = content.substring(1);
        }
        char separator = detectSeparator(content);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }
            if (c == '"' && !fieldStarted) {
                quoted = true;
                fieldStarted = true;
            } else if (c == separator) {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                addRow(rows, row);
                row = new ArrayList<>();
            } else {
                field.append(c);
                fieldStarted = true;
            }
        }
        if (fieldStarted || field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            addRow(rows, row);
        }
        return new Parsed(separator, rows);
    }

    private static void addRow(List<List<String>> rows, List<String> row) {
        boolean blank = row.stream().allMatch(value -> value.isBlank());
        if (!blank) {
            rows.add(row);
        } else {
            rows.add(List.of()); // keep the line number for error messages
        }
    }

    /* The separator is the one that appears more often in the header line. */
    static char detectSeparator(String content) {
        int end = content.indexOf('\n');
        String header = end < 0 ? content : content.substring(0, end);
        long semicolons = header.chars().filter(c -> c == ';').count();
        long commas = header.chars().filter(c -> c == ',').count();
        return semicolons > commas ? ';' : ',';
    }

    /** Quotes a value for writing when needed. */
    public static String quote(String value, char separator) {
        if (value == null) return "";
        boolean needs = value.indexOf(separator) >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        return needs ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }
}
