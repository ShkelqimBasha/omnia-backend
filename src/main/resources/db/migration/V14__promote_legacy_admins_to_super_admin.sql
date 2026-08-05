/*
 * Promote existing platform administrators to
 * the new SUPER_ADMIN role.
 *
 * The legacy ADMIN role remains in the roles table
 * temporarily for backward compatibility.
 */
UPDATE users AS user_account
    INNER JOIN roles AS current_role
ON current_role.id = user_account.role_id
    INNER JOIN roles AS super_admin_role
    ON super_admin_role.name = 'SUPER_ADMIN'
    SET user_account.role_id = super_admin_role.id
WHERE current_role.name = 'ADMIN';
