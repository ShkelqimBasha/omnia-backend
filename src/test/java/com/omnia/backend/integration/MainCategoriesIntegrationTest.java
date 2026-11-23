package com.omnia.backend.integration;

import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.CategoryStatus;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.enums.SaleMode;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.ProductInquiryRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUser(username = MainCategoriesIntegrationTest.EMAIL, roles = "USER")
class MainCategoriesIntegrationTest extends AbstractIntegrationTest {

    static final String EMAIL = "categories-buyer@example.com";

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private ProductInquiryRepository inquiryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Category cars;
    private Category property;
    private Organization seller;
    private Product golf;
    private Product audi;
    private Product flat;

    /**
     * Runs the real migrations on a fresh schema: up to V39, adds products
     * to the legacy categories, then V40. Checks the approved mapping.
     */
    @Test
    void v40AppliesTheApprovedMappingWithoutDeletingOrMovingAnything() {
        String schema = "omnia_v40_" + UUID.randomUUID().toString().substring(0, 8);
        String rootUrl = MYSQL_CONTAINER.getJdbcUrl().replace("/omnia_test", "/" + schema)
                + (MYSQL_CONTAINER.getJdbcUrl().contains("?") ? "&" : "?")
                + "createDatabaseIfNotExist=true";
        DriverManagerDataSource ds = new DriverManagerDataSource(rootUrl, "root",
                MYSQL_CONTAINER.getPassword());
        JdbcTemplate db = new JdbcTemplate(ds);

        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("39").load().migrate();

        Map<String, Long> before = ids(db);
        long fashion = before.get("Modë");
        long kitchen = before.get("Shtëpi dhe Kuzhinë");
        db.update("INSERT INTO products (category_id, name, price, stock, status) VALUES (?, 'Bluzë', 19.99, 3, 'ACTIVE')", fashion);
        db.update("INSERT INTO products (category_id, name, price, stock, status) VALUES (?, 'Filxhan', 4.50, 9, 'ACTIVE')", kitchen);

        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();

        Map<String, Long> after = ids(db);
        assertEquals(fashion, after.get("Veshje"), "Modë keeps its id as Veshje");
        assertEquals(before.get("Bukuri dhe Kujdes"), after.get("Kujdes për Lëkurën"));
        assertEquals(before.get("Elektronike"), after.get("Elektronike"));
        assertEquals(before.size() + 3, after.size(), "nothing deleted, three added");

        assertEquals("Zbulo veshje për përditshmërinë, punën dhe rastet e veçanta.", description(db, "Veshje"));
        assertEquals("Gjej modelin që i përshtatet stilit dhe përditshmërisë tënde.", description(db, "Atlete & Këpucë"));
        assertEquals("Zbulo produkte për pastrimin, hidratimin dhe mbrojtjen e lëkurës.", description(db, "Kujdes për Lëkurën"));
        assertEquals("Zbulo makina të reja dhe të përdorura. Kontakto shitësin ose bli online me çmimin e plotë.", description(db, "Makina"));
        assertEquals("Gjej pronën për banim ose biznes, për shitje apo me qira.", description(db, "Prona"));

        List<String> active = db.queryForList(
                "SELECT name FROM categories WHERE status = 'ACTIVE' ORDER BY sort_order", String.class);
        assertEquals(List.of("Veshje", "Atlete & Këpucë", "Kujdes për Lëkurën", "Makina", "Prona", "Elektronike"), active);
        assertEquals(5, db.queryForObject(
                "SELECT COUNT(*) FROM categories WHERE status = 'INACTIVE'", Integer.class));
        assertEquals("CAR", db.queryForObject("SELECT kind FROM categories WHERE name = 'Makina'", String.class));

        assertEquals(fashion, db.queryForObject("SELECT category_id FROM products WHERE name = 'Bluzë'", Long.class));
        assertEquals(kitchen, db.queryForObject("SELECT category_id FROM products WHERE name = 'Filxhan'", Long.class));
        assertEquals("ACTIVE", db.queryForObject("SELECT status FROM products WHERE name = 'Filxhan'", String.class));

        db.execute("DROP DATABASE " + schema);
    }

    private static Map<String, Long> ids(JdbcTemplate db) {
        Map<String, Long> ids = new java.util.HashMap<>();
        db.query("SELECT id, name FROM categories", rs -> {
            ids.put(rs.getString("name"), rs.getLong("id"));
        });
        return ids;
    }

    private static String description(JdbcTemplate db, String name) {
        return db.queryForObject("SELECT description FROM categories WHERE name = ?", String.class, name);
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Role role = roleRepository.findByName("USER").orElseThrow();
        if (userRepository.findByEmail("categories-admin@example.com").isEmpty()) {
            userRepository.saveAndFlush(User.builder().firstName("Admin").lastName("Test")
                    .username("categories_admin").email("categories-admin@example.com").passwordHash("hash")
                    .role(roleRepository.findByName("ADMIN").orElseThrow()).emailVerified(true).build());
        }
        if (userRepository.findByEmail(EMAIL).isEmpty()) {
            userRepository.saveAndFlush(User.builder().firstName("Blerës").lastName("Test")
                    .username("categories_buyer").email(EMAIL).passwordHash("hash")
                    .role(role).emailVerified(true).build());
        }
        seller = organizationRepository.save(Organization.builder().name("AutoSallon " + suffix)
                .slug("autosallon-" + suffix).status(OrganizationStatus.ACTIVE)
                .phone("+355 69 000 0000").businessEmail("shitje@example.com").build());
        cars = categoryRepository.save(Category.builder().name("Makina " + suffix)
                .status(CategoryStatus.ACTIVE).kind(CategoryKind.CAR).sortOrder(4).build());
        property = categoryRepository.save(Category.builder().name("Prona " + suffix)
                .status(CategoryStatus.ACTIVE).kind(CategoryKind.PROPERTY).sortOrder(5).build());

        golf = productRepository.save(listing(cars, "Golf 7", "15000", Map.of(
                "make", "Volkswagen", "model", "Golf 7", "year", 2017, "mileageKm", 145000,
                "fuel", "DIESEL", "gearbox", "MANUAL", "location", "Tiranë")));
        golf.setSaleMode(SaleMode.CONTACT_ONLY);
        golf = productRepository.save(golf);
        audi = productRepository.save(listing(cars, "Audi A4", "21000", Map.of(
                "make", "Audi", "model", "A4", "year", 2012, "mileageKm", 210000,
                "fuel", "PETROL", "gearbox", "AUTOMATIC", "location", "Durrës")));
        flat = productRepository.save(listing(property, "Apartament 2+1", "650", Map.of(
                "dealType", "RENT", "propertyType", "APARTMENT", "location", "Tiranë, Blloku",
                "areaM2", 85, "rooms", 2)));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM push_outbox WHERE notification_id IN "
                + "(SELECT id FROM notification_inbox WHERE event_key LIKE 'inquiry:%')");
        jdbcTemplate.update("DELETE FROM notification_inbox WHERE event_key LIKE 'inquiry:%'");
        jdbcTemplate.update("DELETE FROM product_inquiries WHERE organization_id = ?", seller.getId());
        jdbcTemplate.update("DELETE FROM cart_items WHERE product_id IN (?, ?, ?)",
                golf.getId(), audi.getId(), flat.getId());
        productRepository.deleteAll(List.of(golf, audi, flat));
        categoryRepository.deleteAll(List.of(cars, property));
        organizationRepository.delete(seller);
    }

    private Product listing(Category category, String name, String price, Map<String, Object> attributes) {
        return Product.builder().category(category).organization(seller).name(name)
                .price(new BigDecimal(price)).stock(1).status(ProductStatus.ACTIVE)
                .attributes(new java.util.HashMap<>(attributes)).build();
    }

    @Test
    void categoryCarriesFieldsAndBehaviour() throws Exception {
        mockMvc.perform(get("/api/categories/{id}", cars.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("CAR"))
                .andExpect(jsonPath("$.cartEnabled").value(false))
                .andExpect(jsonPath("$.contactEnabled").value(true))
                .andExpect(jsonPath("$.onlinePurchaseOption").value(false))
                .andExpect(jsonPath("$.fields[2].key").value("year"))
                .andExpect(jsonPath("$.fields[2].label").value("Viti"));
        mockMvc.perform(get("/api/categories/{id}", property.getId()))
                .andExpect(jsonPath("$.visitRequestEnabled").value(true))
                .andExpect(jsonPath("$.cartEnabled").value(false));
    }

    @Test
    void productShowsAttributesContactAndIsNotPurchasable() throws Exception {
        mockMvc.perform(get("/api/products/{id}", golf.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryKind").value("CAR"))
                .andExpect(jsonPath("$.saleMode").value("CONTACT_ONLY"))
                .andExpect(jsonPath("$.purchasable").value(false))
                .andExpect(jsonPath("$.contactEnabled").value(true))
                .andExpect(jsonPath("$.visitRequestEnabled").value(false))
                .andExpect(jsonPath("$.sellerPhone").value("+355 69 000 0000"))
                .andExpect(jsonPath("$.attributes.fuel").value("DIESEL"))
                .andExpect(jsonPath("$.attributeDetails[4].label").value("Karburanti"))
                .andExpect(jsonPath("$.attributeDetails[4].displayValue").value("Naftë"))
                .andExpect(jsonPath("$.attributeDetails[3].displayValue").value("145 000 km"))
                .andExpect(jsonPath("$.attributeDetails[2].displayValue").value("2017"));
    }

    @Test
    void filtersOnListingFields() throws Exception {
        mockMvc.perform(get("/api/products").param("categoryId", cars.getId().toString())
                        .param("attr.year.min", "2015").param("attr.fuel", "diesel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Golf 7"));
        mockMvc.perform(get("/api/products").param("categoryId", cars.getId().toString())
                        .param("attr.mileageKm.max", "300000").param("attr.location", "durr"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Audi A4"));
        mockMvc.perform(get("/api/products").param("categoryId", property.getId().toString())
                        .param("attr.dealType", "RENT").param("attr.rooms.min", "2")
                        .param("attr.areaM2.min", "80"))
                .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(get("/api/products").param("attr.unknown", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cartRefusesPropertyAndContactOnlyCars() throws Exception {
        for (Product listing : List.of(flat, golf)) {
            mockMvc.perform(post("/api/cart").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":" + listing.getId() + ",\"quantity\":1}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void visitRequestIsStoredForTheSellerAndRefusedForCars() throws Exception {
        String visit = "{\"type\":\"VISIT\",\"name\":\"Ana\",\"phone\":\"+355691112233\","
                + "\"message\":\"A mund ta shoh të shtunën?\",\"preferredDate\":\""
                + LocalDate.now().plusDays(3) + "\"}";
        mockMvc.perform(post("/api/products/{id}/inquiries", flat.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(visit))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("VISIT"))
                .andExpect(jsonPath("$.organizationId").value(seller.getId()));
        mockMvc.perform(post("/api/products/{id}/inquiries", golf.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(visit))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/products/{id}/inquiries", golf.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CONTACT\",\"name\":\"Ana\",\"phone\":\"0691112233\"}"))
                .andExpect(status().isCreated());
        Long adminId = userRepository.findByEmail("categories-admin@example.com").orElseThrow().getId();
        Long buyerId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification_inbox WHERE recipient_id = ? "
                + "AND kind = 'INQUIRY_VISIT' AND target_id = ?", Integer.class, adminId, flat.getId()));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification_inbox WHERE recipient_id = ? "
                + "AND kind LIKE 'INQUIRY_%'", Integer.class, buyerId), "the buyer is not notified of their own request");
        mockMvc.perform(get("/api/inquiries/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }
}
