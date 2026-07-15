CREATE TABLE password_reset_tokens (
                                       id BIGINT NOT NULL AUTO_INCREMENT,
                                       token VARCHAR(100) NOT NULL,
                                       user_id BIGINT NOT NULL,
                                       created_at DATETIME(6) NOT NULL,
                                       expires_at DATETIME(6) NOT NULL,
                                       used BOOLEAN NOT NULL DEFAULT FALSE,

                                       PRIMARY KEY (id),
                                       CONSTRAINT uk_password_reset_token UNIQUE (token),
                                       CONSTRAINT fk_password_reset_token_user
                                           FOREIGN KEY (user_id)
                                               REFERENCES users(id)
                                               ON DELETE CASCADE
);