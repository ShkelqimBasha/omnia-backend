INSERT INTO roles (name, description)
SELECT 'ADMIN', 'System administrator'
    WHERE NOT EXISTS (
    SELECT 1
    FROM roles
    WHERE name = 'ADMIN'
);

INSERT INTO roles (name, description)
SELECT 'USER', 'Default application user'
    WHERE NOT EXISTS (
    SELECT 1
    FROM roles
    WHERE name = 'USER'
);