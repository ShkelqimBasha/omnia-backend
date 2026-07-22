/*
 * Preserve existing image_url rows as legacy product images.
 * New product images must reference uploaded_files.
 */

ALTER TABLE product_images
    MODIFY COLUMN image_url VARCHAR(500) NULL,
    MODIFY COLUMN is_primary TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN uploaded_file_id BIGINT NULL
    AFTER product_id,
    ADD COLUMN primary_product_id BIGINT NULL
    AFTER is_primary;

/*
 * Repair legacy data before enforcing one primary image.
 * If a product currently has multiple primary images,
 * retain only the image with the smallest id.
 */
UPDATE product_images image
    JOIN (
    SELECT product_id, MIN(id) AS retained_primary_id
    FROM product_images
    WHERE is_primary = 1
    GROUP BY product_id
    HAVING COUNT(*) > 1
    ) duplicate_primary
ON duplicate_primary.product_id = image.product_id
    SET image.is_primary = 0
WHERE image.is_primary = 1
  AND image.id <> duplicate_primary.retained_primary_id;

/*
 * Populate the primary uniqueness key for existing data.
 * Non-primary images retain NULL.
 */
UPDATE product_images
SET primary_product_id =
        CASE
            WHEN is_primary = 1 THEN product_id
            ELSE NULL
            END;

/*
 * An uploaded file can belong to only one product image.
 */
CREATE UNIQUE INDEX uk_product_images_uploaded_file
    ON product_images (uploaded_file_id);

/*
 * MySQL permits multiple NULL values in a UNIQUE index.
 * Therefore, every product can have many non-primary images,
 * but at most one primary image.
 */
CREATE UNIQUE INDEX uk_product_images_primary_product
    ON product_images (primary_product_id);

/*
 * Improve product-image lookup and ordering.
 */
CREATE INDEX idx_product_images_product_primary
    ON product_images (product_id, is_primary, id);

/*
 * Prevent deletion of uploaded-file metadata while the file
 * is still attached to a product image.
 */
ALTER TABLE product_images
    ADD CONSTRAINT fk_product_images_uploaded_file
        FOREIGN KEY (uploaded_file_id)
            REFERENCES uploaded_files (id)
            ON DELETE RESTRICT;

/*
 * A product image must reference exactly one source:
 * either an uploaded file or a legacy external URL.
 */
ALTER TABLE product_images
    ADD CONSTRAINT chk_product_images_single_source
        CHECK (
            (
                uploaded_file_id IS NOT NULL
                    AND image_url IS NULL
                )
                OR
            (
                uploaded_file_id IS NULL
                    AND image_url IS NOT NULL
                )
            );

/*
 * Keep primary_product_id consistent with is_primary.
 *
 * Primary:
 *     primary_product_id must equal product_id.
 *
 * Non-primary:
 *     primary_product_id must be NULL.
 */
ALTER TABLE product_images
    ADD CONSTRAINT chk_product_images_primary_key
        CHECK (
            (
                is_primary = 1
                    AND primary_product_id = product_id
                )
                OR
            (
                is_primary = 0
                    AND primary_product_id IS NULL
                )
            );