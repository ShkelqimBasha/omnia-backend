package com.omnia.backend.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Counts of active car and property listings by make / location / deal type / property type.
 * The web builds its SEO landing pages ("Makina në Tiranë", "BMW të përdorura") and their
 * sitemap entries from these, so it only publishes pages that have listings. Public, no personal data.
 */
@RestController
public class SeoFacetController {

    private final JdbcTemplate jdbc;

    public SeoFacetController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Facet(long categoryId, String kind, String make, String location, String dealType,
                        String propertyType, boolean used, long count) {}

    @GetMapping("/api/products/seo-facets")
    public ResponseEntity<List<Facet>> facets() {
        List<Facet> rows = jdbc.query("""
                SELECT c.id AS category_id, c.kind,
                       NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(p.attributes,'$.make'))),'') AS make,
                       NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(p.attributes,'$.location'))),'') AS location,
                       JSON_UNQUOTE(JSON_EXTRACT(p.attributes,'$.dealType')) AS deal_type,
                       JSON_UNQUOTE(JSON_EXTRACT(p.attributes,'$.propertyType')) AS property_type,
                       COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(p.attributes,'$.mileageKm')) AS DECIMAL(12,0)) >= 1000, FALSE) AS used,
                       COUNT(*) AS n
                FROM products p JOIN categories c ON c.id = p.category_id
                WHERE p.status = 'ACTIVE' AND c.status = 'ACTIVE' AND c.kind IN ('CAR','PROPERTY')
                GROUP BY c.id, c.kind, make, location, deal_type, property_type, used
                ORDER BY n DESC
                LIMIT 2000""",
                (r, i) -> new Facet(r.getLong("category_id"), r.getString("kind"), cut(r.getString("make")),
                        cut(r.getString("location")), r.getString("deal_type"), r.getString("property_type"),
                        r.getBoolean("used"), r.getLong("n")));
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic()).body(rows);
    }

    private static String cut(String value) {
        return value == null || value.equals("null") ? null : value.length() > 80 ? value.substring(0, 80) : value;
    }
}
