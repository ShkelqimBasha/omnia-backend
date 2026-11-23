/*
 * Inquiry workflow: E re (NEW) -> U kontaktua (CONTACTED) -> Vizitë e caktuar (VISIT_SCHEDULED) -> Mbyllur (CLOSED),
 * and the visit slots a property seller offers for "Kërko vizitë".
 */
ALTER TABLE product_inquiries
    ADD COLUMN visit_at DATETIME NULL AFTER preferred_date,
    ADD COLUMN status_updated_at TIMESTAMP NULL AFTER status,
    ADD KEY idx_product_inquiries_status (status, id);

/* A time the seller is available for a visit; booked by at most one inquiry. Times are local (Europe/Tirane). */
CREATE TABLE product_visit_slots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    starts_at DATETIME NOT NULL,
    inquiry_id BIGINT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_visit_slots_time (product_id, starts_at),
    UNIQUE KEY uk_product_visit_slots_inquiry (inquiry_id),
    CONSTRAINT fk_product_visit_slots_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_visit_slots_inquiry FOREIGN KEY (inquiry_id) REFERENCES product_inquiries (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
