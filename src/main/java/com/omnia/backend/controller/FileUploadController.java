package com.omnia.backend.controller;

import com.omnia.backend.dto.response.UploadedFileResponse;
import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.service.interfaces.FileStorageService;
import jakarta.validation.constraints.Positive;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@RestController
@RequestMapping("/api/files")
@Validated
public class FileUploadController {

    private final FileStorageService fileStorageService;

    public FileUploadController(
            FileStorageService fileStorageService
    ) {
        this.fileStorageService =
                fileStorageService;
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<UploadedFileResponse> uploadFile(
            @RequestPart("file") MultipartFile file
    ) {
        UploadedFile uploadedFile =
                fileStorageService.storeFile(file);

        UploadedFileResponse response =
                UploadedFileResponse.from(
                        uploadedFile
                );

        URI location =
                ServletUriComponentsBuilder
                        .fromCurrentContextPath()
                        .path("/api/files/{fileId}")
                        .buildAndExpand(
                                uploadedFile.getId()
                        )
                        .toUri();

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable
            @Positive(
                    message = "File ID must be a positive number"
            )
            Long fileId
    ) {
        UploadedFile metadata =
                fileStorageService
                        .getFileMetadata(fileId);

        Resource resource =
                fileStorageService
                        .loadFileAsResource(fileId);

        ContentDisposition contentDisposition =
                ContentDisposition.inline()
                        .filename(
                                metadata.getOriginalName(),
                                StandardCharsets.UTF_8
                        )
                        .build();

        MediaType mediaType =
                MediaType.parseMediaType(
                        metadata.getContentType()
                );

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(metadata.getSize())
                .cacheControl(
                        CacheControl
                                .maxAge(
                                        Duration.ofHours(1)
                                )
                                .cachePrivate()
                                .mustRevalidate()
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition.toString()
                )
                .header(
                        "X-Content-Type-Options",
                        "nosniff"
                )
                .body(resource);
    }

    @GetMapping("/{fileId}/metadata")
    public ResponseEntity<UploadedFileResponse> getMetadata(
            @PathVariable
            @Positive(
                    message = "File ID must be a positive number"
            )
            Long fileId
    ) {
        UploadedFile uploadedFile =
                fileStorageService
                        .getFileMetadata(fileId);

        return ResponseEntity.ok(
                UploadedFileResponse.from(
                        uploadedFile
                )
        );
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable
            @Positive(
                    message = "File ID must be a positive number"
            )
            Long fileId
    ) {
        fileStorageService.deleteFile(fileId);

        return ResponseEntity
                .noContent()
                .build();
    }
}