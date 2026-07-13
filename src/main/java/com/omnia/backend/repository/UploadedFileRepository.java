
package com.omnia.backend.repository;

import com.omnia.backend.entity.UploadedFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedFileRepository
        extends JpaRepository<UploadedFile, Long> {
}