package com.omnia.backend.controller;

import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.service.interfaces.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private final FileStorageService fileStorageService;

    public FileUploadController(
            FileStorageService fileStorageService
    ) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestPart("file") MultipartFile file
    ) {
        UploadedFile uploadedFile =
                fileStorageService.storeFile(file);

        return ResponseEntity.ok(
                Map.of(
                        "id", uploadedFile.getId(),
                        "originalName", uploadedFile.getOriginalName(),
                        "storedName", uploadedFile.getStoredName(),
                        "contentType", uploadedFile.getContentType(),
                        "size", uploadedFile.getSize(),
                        "uploadedAt", uploadedFile.getUploadedAt()
                )
        );
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long fileId
    ) {
        UploadedFile metadata =
                fileStorageService.getFileMetadata(fileId);

        Resource resource =
                fileStorageService.loadFileAsResource(fileId);

        ContentDisposition contentDisposition =
                ContentDisposition.inline()
                        .filename(
                                metadata.getOriginalName(),
                                StandardCharsets.UTF_8
                        )
                        .build();

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                metadata.getContentType()
                        )
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition.toString()
                )
                .contentLength(metadata.getSize())
                .body(resource);
    }

    @GetMapping("/{fileId}/metadata")
    public ResponseEntity<Map<String, Object>> getMetadata(
            @PathVariable Long fileId
    ) {
        UploadedFile uploadedFile =
                fileStorageService.getFileMetadata(fileId);

        return ResponseEntity.ok(
                Map.of(
                        "id", uploadedFile.getId(),
                        "originalName", uploadedFile.getOriginalName(),
                        "storedName", uploadedFile.getStoredName(),
                        "contentType", uploadedFile.getContentType(),
                        "size", uploadedFile.getSize(),
                        "uploadedAt", uploadedFile.getUploadedAt()
                )
        );
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable Long fileId
    ) {
        fileStorageService.deleteFile(fileId);

        return ResponseEntity.noContent().build();
    }
}