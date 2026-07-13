package com.omnia.backend.service.interfaces;

import com.omnia.backend.entity.UploadedFile;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    UploadedFile storeFile(MultipartFile file);

    Resource loadFileAsResource(Long fileId);

    UploadedFile getFileMetadata(Long fileId);

    void deleteFile(Long fileId);
}