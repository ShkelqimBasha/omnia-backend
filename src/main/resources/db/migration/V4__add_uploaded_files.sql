CREATE TABLE uploaded_files (

                                id BIGINT PRIMARY KEY AUTO_INCREMENT,

                                original_name VARCHAR(255) NOT NULL,

                                stored_name VARCHAR(255) NOT NULL UNIQUE,

                                content_type VARCHAR(255) NOT NULL,

                                size BIGINT NOT NULL,

                                path VARCHAR(500) NOT NULL,

                                uploaded_by BIGINT,

                                uploaded_at DATETIME NOT NULL,

                                CONSTRAINT fk_uploaded_file_user
                                    FOREIGN KEY (uploaded_by)
                                        REFERENCES users(id)
                                        ON DELETE SET NULL
);