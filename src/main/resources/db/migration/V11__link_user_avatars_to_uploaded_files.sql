/*
 * Link user avatars to securely uploaded files.
 *
 * Existing profile_image values remain available as legacy URLs.
 * New avatars must use avatar_file_id.
 */

ALTER TABLE users
    ADD COLUMN avatar_file_id BIGINT NULL
        AFTER profile_image;

/*
 * One uploaded file can be used as the avatar
 * of at most one user.
 */
CREATE UNIQUE INDEX uk_users_avatar_file
    ON users (avatar_file_id);

/*
 * Prevent uploaded-file metadata from being deleted
 * while it is attached as a user avatar.
 */
ALTER TABLE users
    ADD CONSTRAINT fk_users_avatar_file
        FOREIGN KEY (avatar_file_id)
            REFERENCES uploaded_files (id)
            ON DELETE RESTRICT;

/*
 * A user can have:
 *
 * 1. An uploaded-file avatar;
 * 2. A legacy profile-image URL;
 * 3. No avatar.
 *
 * A user cannot have both avatar sources simultaneously.
 */
ALTER TABLE users
    ADD CONSTRAINT chk_users_single_avatar_source
        CHECK (
            avatar_file_id IS NULL
                OR profile_image IS NULL
            );