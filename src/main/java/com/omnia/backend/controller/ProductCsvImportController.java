package com.omnia.backend.controller;

import com.omnia.backend.catalog.ProductCsvRules;
import com.omnia.backend.service.impl.ProductCsvImportService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** CSV bulk upload of listings for a company (seller panel). */
@RestController
@RequestMapping("/api/organizations/{organizationId}/products/import")
@Validated
public class ProductCsvImportController {

    private final ProductCsvImportService importService;

    public ProductCsvImportController(ProductCsvImportService importService) {
        this.importService = importService;
    }

    @GetMapping("/columns")
    public List<ProductCsvRules.Column> columns(@PathVariable @Positive Long organizationId,
                                                @RequestParam @Positive Long categoryId) {
        return importService.columns(organizationId, categoryId);
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@PathVariable @Positive Long organizationId,
                                           @RequestParam @Positive Long categoryId) {
        ProductCsvImportService.Template template = importService.template(organizationId, categoryId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(template.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(template.content().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductCsvImportService.Preview preview(@PathVariable @Positive Long organizationId,
                                                   @RequestParam @Positive Long categoryId,
                                                   @RequestPart("file") MultipartFile file) {
        return importService.preview(organizationId, categoryId, file);
    }

    /** Imports every row, or nothing (422 with the preview) when any row has an error. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductCsvImportService.Preview> importFile(
            @PathVariable @Positive Long organizationId,
            @RequestParam @Positive Long categoryId,
            @RequestPart("file") MultipartFile file) {
        ProductCsvImportService.Preview result = importService.importFile(organizationId, categoryId, file);
        return ResponseEntity.status(result.imported() > 0 ? HttpStatus.CREATED : HttpStatus.UNPROCESSABLE_ENTITY)
                .body(result);
    }
}
