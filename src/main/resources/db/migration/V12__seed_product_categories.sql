SET NAMES utf8mb4;

INSERT INTO `categories`
    (`parent_id`, `name`, `description`, `image_url`, `status`)
SELECT
    NULL,
    'Elektronike',
    'Pajisje elektronike dhe teknologji',
    NULL,
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM `categories`
    WHERE LOWER(TRIM(`name`)) = LOWER(TRIM('Elektronike'))
);

INSERT INTO `categories`
    (`parent_id`, `name`, `description`, `image_url`, `status`)
SELECT NULL, 'Modë', 'Veshje, këpucë dhe aksesorë', NULL, 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM `categories`
    WHERE LOWER(TRIM(`name`)) = LOWER(TRIM('Modë'))
);

INSERT INTO `categories`
    (`parent_id`, `name`, `description`, `image_url`, `status`)
SELECT NULL, 'Shtëpi dhe Kuzhinë',
       'Produkte për shtëpinë dhe kuzhinën', NULL, 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM `categories`
    WHERE LOWER(TRIM(`name`)) = LOWER(TRIM('Shtëpi dhe Kuzhinë'))
);

INSERT INTO `categories`
    (`parent_id`, `name`, `description`, `image_url`, `status`)
SELECT NULL, 'Bukuri dhe Kujdes',
       'Produkte bukurie dhe kujdesi personal', NULL, 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM `categories`
    WHERE LOWER(TRIM(`name`)) = LOWER(TRIM('Bukuri dhe Kujdes'))
);

INSERT INTO `categories`
    (`parent_id`, `name`, `description`, `image_url`, `status`)
SELECT NULL, 'Sport dhe Fitness',
       'Pajisje dhe produkte sportive', NULL, 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM `categories`
    WHERE LOWER(TRIM(`name`)) = LOWER(TRIM('Sport dhe Fitness'))
);

INSERT INTO `categories`
    (`parent_id`, `name`, `description`, `image_url`, `status`)
SELECT NULL, 'Libra dhe Kancelari',
       'Libra dhe artikuj kancelarie', NULL, 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM `categories`
    WHERE LOWER(TRIM(`name`)) = LOWER(TRIM('Libra dhe Kancelari'))
);

INSERT INTO `categories`
    (`parent_id`, `name`, `description`, `image_url`, `status`)
SELECT NULL, 'Lodra dhe Fëmijë',
       'Lodra dhe produkte për fëmijë', NULL, 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM `categories`
    WHERE LOWER(TRIM(`name`)) = LOWER(TRIM('Lodra dhe Fëmijë'))
);

INSERT INTO `categories`
    (`parent_id`, `name`, `description`, `image_url`, `status`)
SELECT NULL, 'Ushqime dhe Pije',
       'Ushqime dhe produkte të konsumit', NULL, 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM `categories`
    WHERE LOWER(TRIM(`name`)) = LOWER(TRIM('Ushqime dhe Pije'))
);
