/* Electronics gets its own kind (device fields: type, storage, RAM, screen, OS…). It still uses the cart like GENERAL. */
UPDATE categories
SET kind = 'ELECTRONICS'
WHERE parent_id IS NULL
  AND LOWER(TRIM(name)) = LOWER('Elektronike');

/* Its sub-categories, if any, follow the parent. */
UPDATE categories c
    JOIN (SELECT id FROM categories WHERE parent_id IS NULL AND kind = 'ELECTRONICS') p ON c.parent_id = p.id
SET c.kind = 'ELECTRONICS';
