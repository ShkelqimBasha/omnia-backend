package com.omnia.backend.service.impl;

import com.omnia.backend.catalog.CsvTable;
import com.omnia.backend.catalog.ProductCsvRules;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.entity.Category;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.CategoryStatus;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.security.service.OrganizationAccessService;
import com.omnia.backend.service.interfaces.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bulk upload of listings from a CSV file: template, preview with errors per
 * row, then import. The import is all-or-nothing: it runs only when every row
 * is valid, so a seller never ends up with half a file imported.
 */
@Service
public class ProductCsvImportService {

    static final long MAX_BYTES = 1024 * 1024;

    private final CategoryRepository categoryRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationAccessService accessService;
    private final CurrentUserService currentUserService;
    private final ProductService productService;

    public ProductCsvImportService(CategoryRepository categoryRepository,
                                   OrganizationRepository organizationRepository,
                                   OrganizationAccessService accessService,
                                   CurrentUserService currentUserService,
                                   ProductService productService) {
        this.categoryRepository = categoryRepository;
        this.organizationRepository = organizationRepository;
        this.accessService = accessService;
        this.currentUserService = currentUserService;
        this.productService = productService;
    }

    public record RowView(int row, String name, boolean valid, List<String> errors) { }

    public record Preview(Long categoryId, String categoryName, String categoryKind,
                          int totalRows, int validRows, int errorRows,
                          List<String> fileErrors, List<RowView> rows,
                          int imported, List<Long> productIds) {
        public boolean importable() {
            return fileErrors.isEmpty() && errorRows == 0 && totalRows > 0;
        }
    }

    public record Template(String fileName, String content) { }

    @Transactional(readOnly = true)
    public List<ProductCsvRules.Column> columns(Long organizationId, Long categoryId) {
        Category category = requireAccess(organizationId, categoryId);
        return ProductCsvRules.columns(category.getKind());
    }

    @Transactional(readOnly = true)
    public Template template(Long organizationId, Long categoryId) {
        Category category = requireAccess(organizationId, categoryId);
        String slug = category.getName() == null ? "produkte" : category.getName()
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return new Template("omnia-" + (slug.isEmpty() ? "produkte" : slug) + ".csv",
                ProductCsvRules.template(category.getKind()));
    }

    @Transactional(readOnly = true)
    public Preview preview(Long organizationId, Long categoryId, MultipartFile file) {
        Category category = requireAccess(organizationId, categoryId);
        return check(organizationId, category, file).preview(0, List.of());
    }

    @Transactional
    public Preview importFile(Long organizationId, Long categoryId, MultipartFile file) {
        Category category = requireAccess(organizationId, categoryId);
        Checked checked = check(organizationId, category, file);
        Preview preview = checked.preview(0, List.of());
        if (!preview.importable()) {
            return preview;
        }
        List<Long> ids = new ArrayList<>();
        for (ProductCsvRules.RowResult row : checked.rows()) {
            ids.add(productService.createProduct(row.request()).getId());
        }
        return checked.preview(ids.size(), ids);
    }

    private record Checked(Category category, List<String> fileErrors,
                           List<ProductCsvRules.RowResult> rows) {
        Preview preview(int imported, List<Long> ids) {
            int valid = (int) rows.stream().filter(ProductCsvRules.RowResult::valid).count();
            List<RowView> views = rows.stream()
                    .map(r -> new RowView(r.row(), r.name(), r.valid(), r.errors()))
                    .toList();
            return new Preview(category.getId(), category.getName(),
                    CategoryKind.fromNullable(category.getKind()).name(),
                    rows.size(), valid, rows.size() - valid, fileErrors, views, imported, ids);
        }
    }

    private Checked check(Long organizationId, Category category, MultipartFile file) {
        List<String> fileErrors = new ArrayList<>();
        List<ProductCsvRules.RowResult> results = new ArrayList<>();
        if (file == null || file.isEmpty()) {
            fileErrors.add("Zgjidh një skedar CSV");
            return new Checked(category, fileErrors, results);
        }
        if (file.getSize() > MAX_BYTES) {
            fileErrors.add("Skedari është më i madh se 1 MB");
            return new Checked(category, fileErrors, results);
        }
        String text;
        try {
            text = decode(file.getBytes());
        } catch (IOException e) {
            fileErrors.add("Skedari nuk u lexua");
            return new Checked(category, fileErrors, results);
        }

        List<List<String>> rows = CsvTable.parse(text).rows();
        int headerIndex = 0;
        while (headerIndex < rows.size() && rows.get(headerIndex).isEmpty()) headerIndex++;
        if (headerIndex >= rows.size()) {
            fileErrors.add("Skedari është bosh");
            return new Checked(category, fileErrors, results);
        }
        Map<Integer, String> mapping = ProductCsvRules.mapHeader(
                category.getKind(), rows.get(headerIndex), fileErrors);
        if (!fileErrors.isEmpty()) {
            return new Checked(category, fileErrors, results);
        }
        for (int i = headerIndex + 1; i < rows.size(); i++) {
            List<String> cells = rows.get(i);
            if (cells.isEmpty()) continue;
            if (results.size() >= ProductCsvRules.MAX_ROWS) {
                fileErrors.add("Të shumtën " + ProductCsvRules.MAX_ROWS
                        + " produkte për skedar. Ndaje skedarin në disa pjesë.");
                break;
            }
            results.add(ProductCsvRules.validateRow(category.getKind(), category.getId(),
                    organizationId, i + 1, mapping, cells));
        }
        if (results.isEmpty() && fileErrors.isEmpty()) {
            fileErrors.add("Skedari nuk ka asnjë produkt pas rreshtit të titujve");
        }
        return new Checked(category, fileErrors, results);
    }

    /* UTF-8 (with or without BOM); files saved by older Excel come as Windows-1252. */
    static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    private Category requireAccess(Long organizationId, Long categoryId) {
        if (organizationId == null || organizationId <= 0 || categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException("Zgjidh kompaninë dhe kategorinë");
        }
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Kompania nuk u gjet");
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Kategoria nuk u gjet"));
        if (category.getStatus() != CategoryStatus.ACTIVE) {
            throw new IllegalArgumentException("Kategoria nuk është aktive");
        }
        boolean platformAdmin = currentUserService.hasCurrentPlatformAdminAuthority()
                || currentUserService.hasPlatformAdminAccess(
                currentUserService.findCurrentUser().orElse(null));
        if (!platformAdmin) {
            accessService.requireCanCreateProduct(organizationId, categoryId);
        }
        return category;
    }
}
