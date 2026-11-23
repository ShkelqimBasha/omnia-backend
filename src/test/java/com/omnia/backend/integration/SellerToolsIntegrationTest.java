package com.omnia.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.CategoryStatus;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UploadedFileRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.service.impl.ProductActivityCounter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUser(username = SellerToolsIntegrationTest.ADMIN_EMAIL, roles = "ADMIN")
class SellerToolsIntegrationTest extends AbstractIntegrationTest {

    static final String ADMIN_EMAIL = "seller-tools-admin@example.com";
    static final String OUTSIDER_EMAIL = "seller-tools-outsider@example.com";

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UploadedFileRepository uploadedFileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ProductActivityCounter counter;

    private User admin;
    private Organization seller;
    private Category cars;
    private Category general;
    private Product car;
    private Product mug;
    private final List<Long> fileIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        admin = userRepository.findByEmail(ADMIN_EMAIL).orElseGet(() -> userRepository.saveAndFlush(
                User.builder().firstName("Admin").lastName("Shitës").username("seller_tools_admin")
                        .email(ADMIN_EMAIL).passwordHash("hash")
                        .role(roleRepository.findByName("ADMIN").orElseThrow())
                        .emailVerified(true).build()));
        if (userRepository.findByEmail(OUTSIDER_EMAIL).isEmpty()) {
            userRepository.saveAndFlush(User.builder().firstName("Tjetër").lastName("Përdorues")
                    .username("seller_tools_outsider").email(OUTSIDER_EMAIL).passwordHash("hash")
                    .role(roleRepository.findByName("USER").orElseThrow()).emailVerified(true).build());
        }
        seller = organizationRepository.save(Organization.builder().name("Shitësi " + suffix)
                .slug("shitesi-" + suffix).status(OrganizationStatus.ACTIVE).build());
        cars = categoryRepository.save(Category.builder().name("Makina " + suffix)
                .status(CategoryStatus.ACTIVE).kind(CategoryKind.CAR).sortOrder(4).build());
        general = categoryRepository.save(Category.builder().name("Shtëpi " + suffix)
                .status(CategoryStatus.ACTIVE).kind(CategoryKind.GENERAL).sortOrder(9).build());
        car = productRepository.save(Product.builder().category(cars).organization(seller)
                .name("Golf 7").price(new BigDecimal("15000")).stock(1).status(ProductStatus.ACTIVE)
                .attributes(new HashMap<>(Map.of("make", "Volkswagen", "model", "Golf 7", "year", 2017,
                        "mileageKm", 145000, "fuel", "DIESEL", "gearbox", "MANUAL", "location", "Tiranë")))
                .build());
        mug = productRepository.save(Product.builder().category(general).organization(seller)
                .name("Filxhan").price(new BigDecimal("4.50")).stock(20).status(ProductStatus.ACTIVE).build());
    }

    @AfterEach
    void tearDown() {
        counter.flush();
        List<Long> productIds = jdbcTemplate.queryForList(
                "SELECT id FROM products WHERE organization_id = ?", Long.class, seller.getId());
        for (Long id : productIds) {
            jdbcTemplate.update("DELETE FROM cart_items WHERE product_id = ?", id);
            jdbcTemplate.update("DELETE FROM favorites WHERE product_id = ?", id);
            jdbcTemplate.update("DELETE FROM product_images WHERE product_id = ?", id);
            jdbcTemplate.update("DELETE FROM product_variants WHERE product_id = ?", id);
            jdbcTemplate.update("DELETE FROM product_daily_stats WHERE product_id = ?", id);
            jdbcTemplate.update("DELETE FROM products WHERE id = ?", id);
        }
        for (Long id : fileIds) {
            jdbcTemplate.update("DELETE FROM uploaded_files WHERE id = ?", id);
        }
        categoryRepository.deleteAll(List.of(cars, general));
        organizationRepository.delete(seller);
    }

    private long uploadedFile() {
        UploadedFile file = uploadedFileRepository.saveAndFlush(UploadedFile.builder()
                .originalName("foto.jpg").storedName(UUID.randomUUID() + ".jpg")
                .contentType("image/jpeg").size(100L).uploadedBy(admin)
                .uploadedAt(LocalDateTime.now()).build());
        fileIds.add(file.getId());
        return file.getId();
    }

    private long addImage(long productId) throws Exception {
        String body = mockMvc.perform(post("/api/product-images/product/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadedFileId\":" + uploadedFile() + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private List<Long> galleryIds(long productId) throws Exception {
        JsonNode list = objectMapper.readTree(mockMvc.perform(
                        get("/api/product-images/product/{id}", productId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            assertEquals(i, list.get(i).get("sortOrder").asInt(), "positions are 0..n-1");
            assertEquals(i == 0, list.get(i).get("isPrimary").asBoolean(), "the first photo is the cover");
            ids.add(list.get(i).get("id").asLong());
        }
        return ids;
    }

    @Test
    void carGalleryTakesTwentyPhotosThatCanBeReorderedAndGetANewCover() throws Exception {
        List<Long> added = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            added.add(addImage(car.getId()));
        }
        assertEquals(added, galleryIds(car.getId()), "first photo becomes the cover, others follow");

        mockMvc.perform(post("/api/product-images/product/{id}", car.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadedFileId\":" + uploadedFile() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Të shumtën 20 foto")));

        List<Long> reversed = new ArrayList<>(added);
        Collections.reverse(reversed);
        mockMvc.perform(put("/api/product-images/product/{id}/order", car.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("imageIds", reversed))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(reversed.get(0)))
                .andExpect(jsonPath("$[0].isPrimary").value(true));
        assertEquals(reversed, galleryIds(car.getId()));

        // An incomplete order is refused.
        mockMvc.perform(put("/api/product-images/product/{id}/order", car.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("imageIds", reversed.subList(0, 3)))))
                .andExpect(status().isBadRequest());

        long chosen = reversed.get(5);
        mockMvc.perform(put("/api/product-images/{id}/cover", chosen)).andExpect(status().isOk());
        List<Long> afterCover = galleryIds(car.getId());
        assertEquals(chosen, afterCover.get(0));
        assertEquals(reversed.get(0), afterCover.get(1), "the rest keep their order");

        mockMvc.perform(delete("/api/product-images/{id}", chosen)).andExpect(status().isNoContent());
        List<Long> afterDelete = galleryIds(car.getId());
        assertEquals(19, afterDelete.size());
        assertEquals(reversed.get(0), afterDelete.get(0), "the next photo becomes the cover");

        mockMvc.perform(get("/api/products/{id}", car.getId()))
                .andExpect(jsonPath("$.imageUrls.length()").value(19))
                .andExpect(jsonPath("$.imageUrls[0]").value("/api/files/"
                        + jdbcTemplate.queryForObject("SELECT uploaded_file_id FROM product_images WHERE id = ?",
                        Long.class, afterDelete.get(0))))
                .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.endsWith(
                        String.valueOf(jdbcTemplate.queryForObject(
                                "SELECT uploaded_file_id FROM product_images WHERE id = ?",
                                Long.class, afterDelete.get(0))))));
    }

    @Test
    void generalListingStopsAtTenPhotos() throws Exception {
        for (int i = 0; i < 10; i++) {
            addImage(mug.getId());
        }
        mockMvc.perform(post("/api/product-images/product/{id}", mug.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadedFileId\":" + uploadedFile() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Të shumtën 10 foto")));
    }

    @Test
    void viewsCartAddsAndFavouritesShowUpInTheSellerStatistics() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/products/{id}/view", mug.getId()).with(anonymous()))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(post("/api/products/{id}/view", car.getId()).with(anonymous()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/cart").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + mug.getId() + ",\"quantity\":2}"))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(post("/api/favorites/{id}", mug.getId())).andExpect(status().is2xxSuccessful());
        // A view of an unknown listing is accepted but never stored.
        mockMvc.perform(post("/api/products/{id}/view", 987654321L).with(anonymous()))
                .andExpect(status().isNoContent());

        counter.flush();

        mockMvc.perform(get("/api/organizations/{org}/statistics/products", seller.getId()).param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.totals.views").value(4))
                .andExpect(jsonPath("$.products[0].productId").value(mug.getId()))
                .andExpect(jsonPath("$.products[0].totals.views").value(3))
                .andExpect(jsonPath("$.products[0].totals.cartAdds").value(1))
                .andExpect(jsonPath("$.products[0].totals.favorites").value(1))
                .andExpect(jsonPath("$.products[0].favoritesNow").value(1))
                .andExpect(jsonPath("$.products[1].totals.views").value(1));

        mockMvc.perform(get("/api/organizations/{org}/statistics/products/{id}", seller.getId(), mug.getId())
                        .param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daily.length()").value(30))
                .andExpect(jsonPath("$.daily[29].views").value(3))
                .andExpect(jsonPath("$.totals.views").value(3));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_daily_stats WHERE product_id = 987654321", Integer.class));

        mockMvc.perform(get("/api/organizations/{org}/statistics/products", seller.getId()).param("days", "5"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/organizations/{org}/statistics/products", seller.getId())
                        .with(user(OUTSIDER_EMAIL).roles("USER")))
                .andExpect(status().isForbidden());
    }

    private MockMultipartFile csv(String text) {
        return new MockMultipartFile("file", "produkte.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void csvImportShowsAlbanianErrorsPerRowAndImportsOnlyACleanFile() throws Exception {
        mockMvc.perform(get("/api/organizations/{org}/products/import/template", seller.getId())
                        .param("categoryId", cars.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".csv")))
                .andExpect(content().string(containsString("emri;pershkrimi;cmimi;cmimi_me_zbritje;stoku;make")));

        String bad = "emri;cmimi;make;model;year;mileageKm;fuel;gearbox;location\n"
                + "Audi A4;21000;Audi;A4;2012;210000;Benzinë;Automatike;Durrës\n"
                + ";abc;BMW;X5;;100;Avull;Manuale;Tiranë\n";
        mockMvc.perform(multipart("/api/organizations/{org}/products/import/preview", seller.getId())
                        .file(csv(bad)).param("categoryId", cars.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.rows[1].row").value(3))
                .andExpect(jsonPath("$.rows[1].errors[0]").value("Emri mungon"))
                .andExpect(jsonPath("$.rows[1].errors[1]").value("Çmimi duhet të jetë numër (p.sh. 19.99)"))
                .andExpect(jsonPath("$.rows[1].errors[2]").value("Mungon fusha e detyrueshme: Viti (year)"));

        mockMvc.perform(multipart("/api/organizations/{org}/products/import", seller.getId())
                        .file(csv(bad)).param("categoryId", cars.getId().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.imported").value(0));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE organization_id = ?", Integer.class, seller.getId()),
                "nothing imported from a file with errors");

        String good = "emri;cmimi;make;model;year;mileageKm;fuel;gearbox;location\n"
                + "Audi A4;21.000,00;Audi;A4;2012;210000;Benzinë;Automatike;Durrës\n"
                + "\"BMW X5; 3.0d\";32000;BMW;X5;2015;180000;diesel;Manuale;Tiranë\n";
        mockMvc.perform(multipart("/api/organizations/{org}/products/import", seller.getId())
                        .file(csv(good)).param("categoryId", cars.getId().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.productIds.length()").value(2));

        Map<String, Object> bmw = jdbcTemplate.queryForMap(
                "SELECT price, stock, brand, JSON_UNQUOTE(JSON_EXTRACT(attributes, '$.fuel')) fuel "
                        + "FROM products WHERE organization_id = ? AND name = 'BMW X5; 3.0d'", seller.getId());
        assertEquals(0, new BigDecimal("32000").compareTo((BigDecimal) bmw.get("price")));
        assertEquals("DIESEL", bmw.get("fuel"));
        assertEquals("BMW", bmw.get("brand"));
        assertEquals(1, ((Number) bmw.get("stock")).intValue(), "cars get stock 1 when the column is empty");
        assertTrue(jdbcTemplate.queryForObject("SELECT price FROM products WHERE organization_id = ? AND name = 'Audi A4'",
                BigDecimal.class, seller.getId()).compareTo(new BigDecimal("21000")) == 0);
    }

    @Test
    void csvImportIsRefusedToSomeoneOutsideTheCompany() throws Exception {
        mockMvc.perform(get("/api/organizations/{org}/products/import/template", seller.getId())
                        .param("categoryId", cars.getId().toString())
                        .with(user(OUTSIDER_EMAIL).roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void carListingCanBePromotedLikeAnyOtherListing() throws Exception {
        mockMvc.perform(get("/api/listing-promotions/{id}/offer", car.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(5.00))
                .andExpect(jsonPath("$.durationDays").value(7));
    }
}
