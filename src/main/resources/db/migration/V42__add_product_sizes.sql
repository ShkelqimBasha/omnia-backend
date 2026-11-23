SET NAMES utf8mb4;

/*
 * Sizes for clothing and shoes (decided 2026-11-23).
 *
 * Every size is a product_variants row named 'SIZE' with its own stock; the
 * product's stock is the sum of its sizes. The single-value listing fields
 * "size" (clothing) and "shoeSize" (shoes) are replaced by these rows.
 */

/* 1. product_variants: one row per (product, name, value), stock never NULL or negative. */

/* Older duplicate rows (if any): cart lines pointing at a duplicate move to the kept row
   unless the cart already has that line; the rest are dropped with the duplicate. */
UPDATE cart_items ci
    JOIN product_variants dup ON dup.id = ci.variant_id
    JOIN (SELECT product_id, variant_name, variant_value, MIN(id) AS keep_id
          FROM product_variants
          GROUP BY product_id, variant_name, variant_value
          HAVING COUNT(*) > 1) k
        ON k.product_id = dup.product_id
            AND k.variant_name = dup.variant_name
            AND k.variant_value = dup.variant_value
            AND dup.id <> k.keep_id
    LEFT JOIN cart_items other
        ON other.cart_id = ci.cart_id AND other.product_id = ci.product_id AND other.variant_id = k.keep_id
SET ci.variant_id = k.keep_id
WHERE other.id IS NULL;

DELETE ci FROM cart_items ci
    JOIN product_variants dup ON dup.id = ci.variant_id
    JOIN product_variants keep
        ON keep.product_id = dup.product_id
            AND keep.variant_name = dup.variant_name
            AND keep.variant_value = dup.variant_value
            AND keep.id < dup.id;

DELETE dup FROM product_variants dup
    JOIN product_variants keep
        ON keep.product_id = dup.product_id
            AND keep.variant_name = dup.variant_name
            AND keep.variant_value = dup.variant_value
            AND keep.id < dup.id;

UPDATE product_variants SET stock = 0 WHERE stock IS NULL OR stock < 0;

ALTER TABLE product_variants
    MODIFY stock INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT uk_product_variants_product_name_value
        UNIQUE (product_id, variant_name, variant_value),
    ADD CONSTRAINT chk_product_variants_stock CHECK (stock >= 0);

/* 2. Order lines remember the size they were bought in; the text snapshot stays in variant_info. */
ALTER TABLE order_items
    ADD COLUMN variant_id BIGINT NULL AFTER product_id,
    ADD CONSTRAINT fk_order_items_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE SET NULL;

/* 3. Existing single sizes become one SIZE row holding the product's whole stock. */
INSERT INTO product_variants (product_id, variant_name, variant_value, price_adjustment, stock)
SELECT p.id, 'SIZE', UPPER(JSON_UNQUOTE(JSON_EXTRACT(p.attributes, '$.size'))), 0.00, GREATEST(COALESCE(p.stock, 0), 0)
FROM products p
    JOIN categories c ON c.id = p.category_id
WHERE c.kind = 'CLOTHING'
  AND JSON_TYPE(JSON_EXTRACT(p.attributes, '$.size')) = 'STRING'
  AND UPPER(JSON_UNQUOTE(JSON_EXTRACT(p.attributes, '$.size')))
      IN ('XS', 'S', 'M', 'L', 'XL', 'XXL', 'XXXL', 'ONE_SIZE')
  AND NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.variant_name = 'SIZE');

/* Shoe numbers were free decimals 15–50; they are rounded to the nearest half size
   and written like the API does: "42", "42.5". */
INSERT INTO product_variants (product_id, variant_name, variant_value, price_adjustment, stock)
SELECT s.id, 'SIZE',
       IF(s.half = FLOOR(s.half), CAST(CAST(s.half AS SIGNED) AS CHAR), CAST(s.half AS CHAR)),
       0.00, s.stock
FROM (SELECT p.id,
             CAST(ROUND(CAST(JSON_UNQUOTE(JSON_EXTRACT(p.attributes, '$.shoeSize')) AS DECIMAL(8, 3)) * 2) / 2
                 AS DECIMAL(4, 1)) AS half,
             GREATEST(COALESCE(p.stock, 0), 0) AS stock
      FROM products p
          JOIN categories c ON c.id = p.category_id
      WHERE c.kind = 'SHOES'
        AND JSON_TYPE(JSON_EXTRACT(p.attributes, '$.shoeSize')) IN ('INTEGER', 'UNSIGNED INTEGER', 'DECIMAL', 'DOUBLE')
        AND NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.variant_name = 'SIZE')) s
WHERE s.half BETWEEN 15 AND 50;

/* 4. The old fields are gone from the catalog: drop them from every listing. */
UPDATE products
SET attributes = JSON_REMOVE(attributes, '$.size')
WHERE attributes IS NOT NULL AND JSON_CONTAINS_PATH(attributes, 'one', '$.size');

UPDATE products
SET attributes = JSON_REMOVE(attributes, '$.shoeSize')
WHERE attributes IS NOT NULL AND JSON_CONTAINS_PATH(attributes, 'one', '$.shoeSize');

UPDATE products
SET attributes = NULL
WHERE attributes IS NOT NULL AND JSON_LENGTH(attributes) = 0;

/* 5. A product with sizes holds the sum of its sizes. */
UPDATE products p
    JOIN (SELECT product_id, SUM(stock) AS total
          FROM product_variants
          WHERE variant_name = 'SIZE'
          GROUP BY product_id) s ON s.product_id = p.id
SET p.stock = s.total;
