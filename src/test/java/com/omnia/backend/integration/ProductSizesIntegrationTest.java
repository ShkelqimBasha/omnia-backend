package com.omnia.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.CategoryStatus;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.OrganizationRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sizes end to end on real MySQL: the seller lists sizes, the buyer picks one in the
 * cart, checkout takes stock per size, cancelling puts it back; plus the V42 data migration.
 * Not @Transactional: every request commits as in production.
 */
class ProductSizesIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN = "sizes-admin@example.com";
    private static final String BUYER = "sizes-buyer@example.com";

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JdbcTemplate jdbc;

    private Category clothing;
    private Category general;
    private Organization seller;
    private final List<Long> products = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        if (userRepository.findByEmail(ADMIN).isEmpty()) {
            userRepository.saveAndFlush(User.builder().firstName("Admin").lastName("Masa")
                    .username("sizes_admin").email(ADMIN).passwordHash("hash")
                    .role(roleRepository.findByName("ADMIN").orElseThrow()).emailVerified(true).build());
        }
        if (userRepository.findByEmail(BUYER).isEmpty()) {
            userRepository.saveAndFlush(User.builder().firstName("Ana").lastName("Blerëse")
                    .username("sizes_buyer").email(BUYER).passwordHash("hash")
                    .role(roleRepository.findByName("USER").orElseThrow()).emailVerified(true).build());
        }
        seller = organizationRepository.save(Organization.builder().name("Butiku " + suffix)
                .slug("butiku-" + suffix).status(OrganizationStatus.ACTIVE).build());
        clothing = categoryRepository.save(Category.builder().name("Veshje " + suffix)
                .status(CategoryStatus.ACTIVE).kind(CategoryKind.CLOTHING).build());
        general = categoryRepository.save(Category.builder().name("Shtëpi " + suffix)
                .status(CategoryStatus.ACTIVE).kind(CategoryKind.GENERAL).build());
    }

    @AfterEach
    void tearDown() {
        Long buyerId = userRepository.findByEmail(BUYER).orElseThrow().getId();
        jdbc.update("DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM cart WHERE user_id = ?)", buyerId);
        jdbc.update("DELETE FROM order_status_history WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", buyerId);
        jdbc.update("DELETE FROM orders WHERE user_id = ?", buyerId);
        jdbc.update("DELETE FROM checkout_groups WHERE user_id = ?", buyerId);
        for (Long product : products) {
            jdbc.update("DELETE FROM products WHERE id = ?", product);
        }
        categoryRepository.deleteAll(List.of(clothing, general));
        organizationRepository.delete(seller);
    }

    private ResultActions as(String email, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(user(email).roles(ADMIN.equals(email) ? "ADMIN" : "USER")));
    }

    private ResultActions json(String email, MockHttpServletRequestBuilder request, String body) throws Exception {
        return as(email, request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long createProduct(String body) throws Exception {
        JsonNode created = body(json(ADMIN, post("/api/products"), body).andExpect(status().isCreated()));
        long id = created.get("id").asLong();
        products.add(id);
        return id;
    }

    private String productJson(long categoryId, String extra) {
        return "{\"name\":\"Bluzë pambuku\",\"price\":20.00,\"categoryId\":" + categoryId
                + ",\"organizationId\":" + seller.getId() + extra + "}";
    }

    private static String checkout(String items) {
        return "{\"shippingName\":\"Ana\",\"shippingEmail\":\"ana@example.com\",\"shippingPhone\":\"+355690000000\","
                + "\"shippingAddress\":\"Rruga e Kavajës, Tiranë\",\"items\":[" + items + "]}";
    }

    private int sizeStock(long variantId) {
        return jdbc.queryForObject("SELECT stock FROM product_variants WHERE id = ?", Integer.class, variantId);
    }

    private int productStock(long productId) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
    }

    @Test
    void sellerListsSizesBuyerPicksOneCheckoutTakesItAndCancelPutsItBack() throws Exception {
        long shirt = createProduct(productJson(clothing.getId(),
                ",\"stock\":99,\"sizes\":[{\"value\":\"L\",\"stock\":1},{\"value\":\"m\",\"stock\":3},"
                        + "{\"value\":\"3XL\",\"stock\":0}]"));
        long mug = createProduct(productJson(general.getId(), ",\"stock\":4"));

        // Public product payload, no login.
        JsonNode product = body(mockMvc.perform(get("/api/products/{id}", shirt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sizeRequired").value(true))
                .andExpect(jsonPath("$.stock").value(4))
                .andExpect(jsonPath("$.sizes.length()").value(3))
                .andExpect(jsonPath("$.sizes[0].value").value("M"))
                .andExpect(jsonPath("$.sizes[0].stock").value(3))
                .andExpect(jsonPath("$.sizes[1].label").value("L"))
                .andExpect(jsonPath("$.sizes[2].value").value("XXXL"))
                .andExpect(jsonPath("$.sizes[2].label").value("3XL")));
        long medium = product.get("sizes").get(0).get("id").asLong();
        long large = product.get("sizes").get(1).get("id").asLong();
        long xxxl = product.get("sizes").get(2).get("id").asLong();
        mockMvc.perform(get("/api/products").param("categoryId", clothing.getId().toString()))
                .andExpect(jsonPath("$.content[0].sizes.length()").value(3));
        mockMvc.perform(get("/api/products/{id}", mug))
                .andExpect(jsonPath("$.sizeRequired").value(false))
                .andExpect(jsonPath("$.sizes.length()").value(0));

        // Cart: a size is required, must be this product's, at most its stock.
        json(BUYER, post("/api/cart"), "{\"productId\":" + shirt + ",\"quantity\":1}")
                .andExpect(status().isBadRequest());
        json(BUYER, post("/api/cart"), "{\"productId\":" + mug + ",\"variantId\":" + medium + ",\"quantity\":1}")
                .andExpect(status().isBadRequest());
        json(BUYER, post("/api/cart"), "{\"productId\":" + shirt + ",\"variantId\":" + xxxl + ",\"quantity\":1}")
                .andExpect(status().isBadRequest());
        json(BUYER, post("/api/cart"), "{\"productId\":" + shirt + ",\"variantId\":" + medium + ",\"quantity\":4}")
                .andExpect(status().isBadRequest());
        json(BUYER, post("/api/cart"), "{\"productId\":" + shirt + ",\"variantId\":" + medium + ",\"quantity\":2}")
                .andExpect(status().isOk());
        json(BUYER, post("/api/cart"), "{\"productId\":" + shirt + ",\"variantId\":" + large + ",\"quantity\":1}")
                .andExpect(status().isOk());
        json(BUYER, post("/api/cart"), "{\"productId\":" + mug + ",\"quantity\":1}")
                .andExpect(status().isOk());
        as(BUYER, get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[?(@.variantId == " + medium + ")].sizeLabel").value("M"))
                .andExpect(jsonPath("$.items[?(@.variantId == " + medium + ")].availableStock").value(3))
                .andExpect(jsonPath("$.items[?(@.variantId == " + large + ")].sizeLabel").value("L"))
                .andExpect(jsonPath("$.items[?(@.productId == " + mug + ")].availableStock").value(4));

        // Checkout: size required, stock per size.
        json(BUYER, post("/api/orders/checkout"), checkout("{\"productId\":" + shirt + ",\"quantity\":1}"))
                .andExpect(status().isBadRequest());
        json(BUYER, post("/api/orders/checkout"), checkout(
                "{\"productId\":" + shirt + ",\"variantId\":" + large + ",\"quantity\":2}"))
                .andExpect(status().isBadRequest());
        assertEquals(1, sizeStock(large), "refused checkout takes nothing");

        JsonNode placed = body(json(BUYER, post("/api/orders/checkout"), checkout(
                "{\"productId\":" + shirt + ",\"variantId\":" + medium + ",\"quantity\":2},"
                        + "{\"productId\":" + shirt + ",\"variantId\":" + large + ",\"quantity\":1},"
                        + "{\"productId\":" + mug + ",\"quantity\":1}"))
                .andExpect(status().isCreated()));
        assertEquals(1, sizeStock(medium));
        assertEquals(0, sizeStock(large));
        assertEquals(1, productStock(shirt), "product stock is the sum of its sizes");
        assertEquals(3, productStock(mug), "unsized products work as before");

        JsonNode order = placed.get("orders").get(0);
        long orderId = order.get("id").asLong();
        Map<Long, String> infoBySize = new java.util.HashMap<>();
        order.get("items").forEach(item -> {
            if (!item.get("variantId").isNull()) {
                infoBySize.put(item.get("variantId").asLong(), item.get("variantInfo").asText());
            } else {
                assertTrue(item.get("variantInfo").isNull());
            }
        });
        assertEquals(Map.of(medium, "Masa: M", large, "Masa: L"), infoBySize);
        assertEquals("Masa: M", jdbc.queryForObject(
                "SELECT variant_info FROM order_items WHERE order_id = ? AND variant_id = ?",
                String.class, orderId, medium));

        // The buyer's order view shows the size too.
        as(BUYER, get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.variantId == " + large + ")].variantInfo").value("Masa: L"));

        // A sold-out size stays listed with stock 0; the seller removes L while it is in a cart line.
        json(ADMIN, put("/api/products/{id}", shirt), productJson(clothing.getId(),
                ",\"sizes\":[{\"value\":\"M\",\"stock\":1},{\"value\":\"XXXL\",\"stock\":0}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sizes.length()").value(3))
                .andExpect(jsonPath("$.sizes[1].value").value("L"))
                .andExpect(jsonPath("$.sizes[1].stock").value(0));

        // Cancelling puts stock back on each size, and the product sum follows.
        as(BUYER, patch("/api/orders/{id}/cancel", orderId)).andExpect(status().isOk());
        assertEquals(3, sizeStock(medium));
        assertEquals(1, sizeStock(large));
        assertEquals(4, productStock(shirt));
        assertEquals(4, productStock(mug));
    }

    @Test
    void catalogSearchFindsProductsWithTheSizeInStock() throws Exception {
        long withM = createProduct(productJson(clothing.getId(),
                ",\"sizes\":[{\"value\":\"M\",\"stock\":2},{\"value\":\"L\",\"stock\":0}]"));
        long withL = createProduct(productJson(clothing.getId(),
                ",\"sizes\":[{\"value\":\"L\",\"stock\":5}]"));
        String category = clothing.getId().toString();

        mockMvc.perform(get("/api/products").param("categoryId", category).param("sizes", "m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(withM));
        // L of the first shirt is sold out: only the second one has it.
        mockMvc.perform(get("/api/products").param("categoryId", category).param("sizes", "L"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(withL));
        mockMvc.perform(get("/api/products").param("categoryId", category).param("sizes", "M,L"))
                .andExpect(jsonPath("$.content.length()").value(2));
        mockMvc.perform(get("/api/products").param("categoryId", category).param("sizes", "XS"))
                .andExpect(jsonPath("$.content.length()").value(0));
        // Nothing usable in the parameter: no size filter at all.
        mockMvc.perform(get("/api/products").param("categoryId", category).param("sizes", "abc"))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void sizesAreRefusedOutsideClothingAndShoesAndValidated() throws Exception {
        json(ADMIN, post("/api/products"), productJson(general.getId(),
                ",\"stock\":1,\"sizes\":[{\"value\":\"M\",\"stock\":1}]"))
                .andExpect(status().isBadRequest());
        json(ADMIN, post("/api/products"), productJson(clothing.getId(),
                ",\"sizes\":[{\"value\":\"M\",\"stock\":1},{\"value\":\"m\",\"stock\":2}]"))
                .andExpect(status().isBadRequest());
        json(ADMIN, post("/api/products"), productJson(clothing.getId(),
                ",\"sizes\":[{\"value\":\"42\",\"stock\":1}]"))
                .andExpect(status().isBadRequest());
        json(ADMIN, post("/api/products"), productJson(clothing.getId(),
                ",\"sizes\":[{\"value\":\"M\",\"stock\":-1}]"))
                .andExpect(status().isBadRequest());
        json(ADMIN, post("/api/products"), productJson(clothing.getId(), ""))
                .andExpect(status().isBadRequest());
        json(ADMIN, post("/api/products"), productJson(clothing.getId(),
                ",\"attributes\":{\"size\":\"M\"},\"stock\":1"))
                .andExpect(status().isBadRequest());

        // A clothing product without sizes (older data) keeps working without a size.
        long plain = createProduct(productJson(clothing.getId(), ",\"stock\":2"));
        mockMvc.perform(get("/api/products/{id}", plain))
                .andExpect(jsonPath("$.sizeRequired").value(false));
        json(BUYER, post("/api/cart"), "{\"productId\":" + plain + ",\"quantity\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sizeLabel").isEmpty());
    }

    /**
     * Runs the real migrations on a fresh schema up to V41, adds listings with the old
     * single size fields, then V42.
     */
    @Test
    void v42TurnsTheOldSizeFieldsIntoSizeRows() {
        String schema = "omnia_v42_" + UUID.randomUUID().toString().substring(0, 8);
        String url = MYSQL_CONTAINER.getJdbcUrl().replace("/omnia_test", "/" + schema)
                + (MYSQL_CONTAINER.getJdbcUrl().contains("?") ? "&" : "?") + "createDatabaseIfNotExist=true";
        DriverManagerDataSource ds = new DriverManagerDataSource(url, "root", MYSQL_CONTAINER.getPassword());
        JdbcTemplate db = new JdbcTemplate(ds);
        try {
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("41").load().migrate();

            long clothes = db.queryForObject("SELECT id FROM categories WHERE kind = 'CLOTHING' LIMIT 1", Long.class);
            long shoes = db.queryForObject("SELECT id FROM categories WHERE kind = 'SHOES' LIMIT 1", Long.class);
            long other = db.queryForObject("SELECT id FROM categories WHERE kind = 'GENERAL' LIMIT 1", Long.class);
            long shirt = insert(db, clothes, "Bluzë", 4, "{\"size\": \"M\", \"color\": \"E bardhë\"}");
            long onlySize = insert(db, clothes, "Fustan", 2, "{\"size\": \"XXXL\"}");
            long half = insert(db, shoes, "Atlete", 3, "{\"shoeSize\": 42.5, \"brand\": \"x\"}");
            long whole = insert(db, shoes, "Sandale", 1, "{\"shoeSize\": 38}");
            long odd = insert(db, shoes, "Çizme", 5, "{\"shoeSize\": 41.3}");
            long none = insert(db, shoes, "Pantofla", 6, null);
            long mug = insert(db, other, "Filxhan", 9, "{\"material\": \"qeramikë\"}");

            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();

            assertEquals(List.of("M:4"), sizes(db, shirt));
            assertEquals(List.of("XXXL:2"), sizes(db, onlySize));
            assertEquals(List.of("42.5:3"), sizes(db, half));
            assertEquals(List.of("38:1"), sizes(db, whole));
            assertEquals(List.of("41.5:5"), sizes(db, odd), "rounded to the nearest half size");
            assertEquals(List.of(), sizes(db, none));
            assertEquals(List.of(), sizes(db, mug));

            assertEquals("{\"color\": \"E bardhë\"}", attributes(db, shirt));
            assertNull(attributes(db, onlySize), "empty attributes become NULL");
            assertEquals("{\"brand\": \"x\"}", attributes(db, half));
            assertEquals("{\"material\": \"qeramikë\"}", attributes(db, mug));
            assertEquals(4, db.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, shirt));

            // New constraints: one row per size, no negative stock, order lines keep their size.
            long size = db.queryForObject("SELECT id FROM product_variants WHERE product_id = ?", Long.class, shirt);
            assertThrowsSql(() -> db.update("INSERT INTO product_variants (product_id, variant_name, variant_value, stock) "
                    + "VALUES (?, 'SIZE', 'M', 1)", shirt));
            assertThrowsSql(() -> db.update("UPDATE product_variants SET stock = -1 WHERE id = ?", size));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? "
                    + "AND table_name = 'order_items' AND column_name = 'variant_id'", Integer.class, schema));
        } finally {
            db.execute("DROP DATABASE " + schema);
        }
    }

    private static long insert(JdbcTemplate db, long category, String name, int stock, String attributes) {
        db.update("INSERT INTO products (category_id, name, price, stock, status, attributes) "
                + "VALUES (?, ?, 10.00, ?, 'ACTIVE', ?)", category, name, stock, attributes);
        // Each statement may run on a new connection, so LAST_INSERT_ID() is not usable here.
        return db.queryForObject("SELECT id FROM products WHERE name = ?", Long.class, name);
    }

    private static List<String> sizes(JdbcTemplate db, long product) {
        return db.queryForList("SELECT CONCAT(variant_value, ':', stock) FROM product_variants "
                + "WHERE product_id = ? AND variant_name = 'SIZE' ORDER BY id", String.class, product);
    }

    private static String attributes(JdbcTemplate db, long product) {
        return db.queryForObject("SELECT CAST(attributes AS CHAR) FROM products WHERE id = ?", String.class, product);
    }

    private static void assertThrowsSql(Runnable statement) {
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataAccessException.class, statement::run);
    }
}
