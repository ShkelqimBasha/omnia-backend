SET NAMES utf8mb4;

/*
 * Five main categories plus Elektronike.
 *
 * Mapping approved by the owner on 2026-11-23:
 *   Modë               -> Veshje               (renamed, same id, products stay)
 *   Bukuri dhe Kujdes  -> Kujdes për Lëkurën   (renamed, same id, products stay)
 *   Elektronike        -> stays as it is
 *   Atlete & Këpucë, Makina, Prona -> new
 *   Shtëpi dhe Kuzhinë, Sport dhe Fitness, Libra dhe Kancelari,
 *   Lodra dhe Fëmijë, Ushqime dhe Pije -> INACTIVE (hidden, not deleted;
 *   their ids, links and products are untouched)
 *
 * Every statement matches by name, never by id, so it is safe on databases
 * whose ids differ, and it is idempotent.
 */

ALTER TABLE categories
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'GENERAL' AFTER status,
    ADD COLUMN sort_order INT NULL AFTER kind;

ALTER TABLE products
    ADD COLUMN attributes JSON NULL AFTER brand,
    ADD COLUMN sale_mode VARCHAR(20) NULL AFTER attributes;

/* Rename only when the target name does not exist yet. */
UPDATE categories c
    LEFT JOIN (SELECT id FROM categories WHERE LOWER(TRIM(name)) = LOWER('Veshje') LIMIT 1) existing
        ON 1 = 1
SET c.name = 'Veshje'
WHERE c.parent_id IS NULL
  AND LOWER(TRIM(c.name)) = LOWER('Modë')
  AND existing.id IS NULL;

UPDATE categories c
    LEFT JOIN (SELECT id FROM categories WHERE LOWER(TRIM(name)) = LOWER('Kujdes për Lëkurën') LIMIT 1) existing
        ON 1 = 1
SET c.name = 'Kujdes për Lëkurën'
WHERE c.parent_id IS NULL
  AND LOWER(TRIM(c.name)) = LOWER('Bukuri dhe Kujdes')
  AND existing.id IS NULL;

INSERT INTO categories (parent_id, name, description, image_url, status)
SELECT NULL, 'Veshje', NULL, NULL, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE LOWER(TRIM(name)) = LOWER('Veshje'));

INSERT INTO categories (parent_id, name, description, image_url, status)
SELECT NULL, 'Atlete & Këpucë', NULL, NULL, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE LOWER(TRIM(name)) = LOWER('Atlete & Këpucë'));

INSERT INTO categories (parent_id, name, description, image_url, status)
SELECT NULL, 'Kujdes për Lëkurën', NULL, NULL, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE LOWER(TRIM(name)) = LOWER('Kujdes për Lëkurën'));

INSERT INTO categories (parent_id, name, description, image_url, status)
SELECT NULL, 'Makina', NULL, NULL, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE LOWER(TRIM(name)) = LOWER('Makina'));

INSERT INTO categories (parent_id, name, description, image_url, status)
SELECT NULL, 'Prona', NULL, NULL, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE LOWER(TRIM(name)) = LOWER('Prona'));

UPDATE categories
SET kind = 'CLOTHING', sort_order = 1, status = 'ACTIVE',
    description = 'Zbulo veshje për përditshmërinë, punën dhe rastet e veçanta.'
WHERE LOWER(TRIM(name)) = LOWER('Veshje');

UPDATE categories
SET kind = 'SHOES', sort_order = 2, status = 'ACTIVE',
    description = 'Gjej modelin që i përshtatet stilit dhe përditshmërisë tënde.'
WHERE LOWER(TRIM(name)) = LOWER('Atlete & Këpucë');

UPDATE categories
SET kind = 'SKINCARE', sort_order = 3, status = 'ACTIVE',
    description = 'Zbulo produkte për pastrimin, hidratimin dhe mbrojtjen e lëkurës.'
WHERE LOWER(TRIM(name)) = LOWER('Kujdes për Lëkurën');

UPDATE categories
SET kind = 'CAR', sort_order = 4, status = 'ACTIVE',
    description = 'Zbulo makina të reja dhe të përdorura. Kontakto shitësin ose bli online me çmimin e plotë.'
WHERE LOWER(TRIM(name)) = LOWER('Makina');

UPDATE categories
SET kind = 'PROPERTY', sort_order = 5, status = 'ACTIVE',
    description = 'Gjej pronën për banim ose biznes, për shitje apo me qira.'
WHERE LOWER(TRIM(name)) = LOWER('Prona');

/* Elektronike stays as it is, listed after the five main categories. */
UPDATE categories
SET sort_order = 6
WHERE parent_id IS NULL
  AND LOWER(TRIM(name)) = LOWER('Elektronike');

UPDATE categories
SET status = 'INACTIVE'
WHERE parent_id IS NULL
  AND LOWER(TRIM(name)) IN (
      LOWER('Shtëpi dhe Kuzhinë'),
      LOWER('Sport dhe Fitness'),
      LOWER('Libra dhe Kancelari'),
      LOWER('Lodra dhe Fëmijë'),
      LOWER('Ushqime dhe Pije')
  );

/* Car listings: contact only unless the seller turns on online purchase. */
UPDATE products p
    JOIN categories c ON c.id = p.category_id
SET p.sale_mode = 'CONTACT_ONLY'
WHERE c.kind = 'CAR' AND p.sale_mode IS NULL;

/* "Kontakto" and "Kërko vizitë" requests sent to the seller. */
CREATE TABLE product_inquiries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    organization_id BIGINT NULL,
    sender_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    contact_name VARCHAR(120) NOT NULL,
    contact_phone VARCHAR(40) NOT NULL,
    message VARCHAR(1000) NULL,
    preferred_date DATE NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_product_inquiries_org (organization_id, id),
    KEY idx_product_inquiries_sender (sender_id, id),
    CONSTRAINT fk_product_inquiries_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_inquiries_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_product_inquiries_sender FOREIGN KEY (sender_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
