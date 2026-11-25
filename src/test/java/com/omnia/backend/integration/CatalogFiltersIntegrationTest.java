package com.omnia.backend.integration;

import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.Product;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.CategoryStatus;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.enums.SaleMode;
import com.omnia.backend.enums.SellerType;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * General catalog filters and sorts of GET /api/products on a small data set:
 *
 * <pre>
 *  #  name               category  seller             price  discount stock  notes
 *  1  Telefon Alfa       general   A verified/BUSINESS  120    99      5     reviews 5,4
 *  2  Alfa Kufje Beta    general   A                    40     -       0     hidden review only
 *  3  Karikues Gama      general   B private/no PayPal  60     45      3     brand Alfa, review 5
 *  4  Bluzë Delta        clothing  B                    80     -       0     size M stock 2, reviews 3,3,3
 *  5  Xhaketë Epsilon    clothing  A                    70     -       4     sizes all 0, reviews 5,4,4,5
 *  6  Makinë Zeta        car       A                    9000   -       0     contact only
 *  7  Tavolinë Eta       general   A                    55     60      2     published 40 days ago, "alfa" in text
 * </pre>
 */
@WithMockUser(username = "catalog-filters@example.com", roles = "USER")
class CatalogFiltersIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_EMAIL_PREFIX = "catalog-filter-";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Category general;
    private Category clothing;
    private Category cars;
    private Organization verifiedBusiness;
    private Organization privateSeller;
    private final List<Long> users = new ArrayList<>();

    private Product p1, p2, p3, p4, p5, p6, p7;

    @BeforeEach
    void setUp() {
        deleteOrderData();
        jdbc.update("DELETE FROM reviews");
        jdbc.update("DELETE FROM product_variants");
        jdbc.update("DELETE FROM product_daily_stats");
        jdbc.update("DELETE FROM users WHERE email LIKE ?", USER_EMAIL_PREFIX + "%");
        productRepository.deleteAll();
        organizationRepository.deleteAll();
        categoryRepository.deleteAll();

        general = category("Elektronikë", CategoryKind.GENERAL);
        clothing = category("Veshje", CategoryKind.CLOTHING);
        cars = category("Makina", CategoryKind.CAR);

        verifiedBusiness = organizationRepository.save(Organization.builder()
                .name("Dyqani A").slug("catalog-filters-a").status(OrganizationStatus.ACTIVE)
                .verifiedAt(LocalDateTime.now().minusDays(10))
                .sellerType(SellerType.BUSINESS).acceptsPaypal(true).build());
        privateSeller = organizationRepository.save(Organization.builder()
                .name("Shitës B").slug("catalog-filters-b").status(OrganizationStatus.ACTIVE)
                .sellerType(SellerType.PRIVATE).acceptsPaypal(false).build());

        p1 = product("Telefon Alfa", "Telefon i ri", "Omnia", general, verifiedBusiness, "120", "99", 5);
        p2 = product("Alfa Kufje Beta", "Kufje", "Sound", general, verifiedBusiness, "40", null, 0);
        p3 = product("Karikues Gama", "Karikues i shpejtë", "Alfa", general, privateSeller, "60", "45", 3);
        p4 = product("Bluzë Delta", "Bluzë pambuku", "Moda", clothing, privateSeller, "80", null, 0);
        p5 = product("Xhaketë Epsilon", "Xhaketë dimri", "Moda", clothing, verifiedBusiness, "70", null, 4);
        p6 = product("Makinë Zeta", "Makinë familjare", "Auto", cars, verifiedBusiness, "9000", null, 0);
        p6.setSaleMode(SaleMode.CONTACT_ONLY);
        p6 = productRepository.save(p6);
        p7 = product("Tavolinë Eta", "Tavolinë druri, modeli alfa", "Shtëpi", general, verifiedBusiness,
                "55", "60", 2);

        // Newest first: p1 … p6 one hour apart, p7 forty days ago.
        LocalDateTime now = LocalDateTime.now();
        List<Product> recent = List.of(p1, p2, p3, p4, p5, p6);
        for (int i = 0; i < recent.size(); i++) {
            createdAt(recent.get(i), now.minusHours(i + 1L));
        }
        createdAt(p7, now.minusDays(40));

        size(p4, "M", 2);
        size(p4, "L", 0);
        size(p5, "S", 0);
        size(p5, "M", 0);

        for (int i = 0; i < 4; i++) {
            users.add(user(i));
        }
        reviews(p1, false, 5, 4);
        reviews(p2, true, 5);
        reviews(p3, false, 5);
        reviews(p4, false, 3, 3, 3);
        reviews(p5, false, 5, 4, 4, 5);

        LocalDate today = LocalDate.now(ZoneId.of("Europe/Tirane"));
        views(p2, today, 100);
        views(p1, today, 30);
        views(p1, today.minusDays(3), 40);
        views(p6, today.minusDays(5), 50);
        views(p6, today.minusDays(40), 500);

        long delivered = order("DELIVERED");
        item(delivered, p3, 5);
        item(delivered, p1, 1);
        long paidPending = order("PENDING");
        payment(paidPending, "SUCCESS");
        item(paidPending, p1, 3);
        long cancelled = order("CANCELLED");
        item(cancelled, p4, 10);
        long unpaid = order("PENDING");
        payment(unpaid, "PENDING");
        item(unpaid, p7, 20);
        long cancelledPaid = order("CANCELLED");
        payment(cancelledPaid, "SUCCESS");
        item(cancelledPaid, p2, 50);
    }

    // ---------------------------------------------------------------- filters

    @Test
    void inStock_KeepsProductsWithStockOrASizeInStockAndActiveContactListings() throws Exception {
        expectNames(list().param("inStock", "true"),
                "Telefon Alfa", "Karikues Gama", "Bluzë Delta", "Makinë Zeta", "Tavolinë Eta");
    }

    @Test
    void inStock_False_IsNoFilter() throws Exception {
        list(list().param("inStock", "false")).andExpect(jsonPath("$.totalElements").value(7));
    }

    @Test
    void onSale_KeepsProductsWhoseDiscountIsBelowThePrice() throws Exception {
        expectNames(list().param("onSale", "true"), "Telefon Alfa", "Karikues Gama");
    }

    @Test
    void verifiedSeller_KeepsProductsOfVerifiedOrganizations() throws Exception {
        expectNames(list().param("verifiedSeller", "true"),
                "Telefon Alfa", "Alfa Kufje Beta", "Xhaketë Epsilon", "Makinë Zeta", "Tavolinë Eta");
    }

    @Test
    void sellerType_FiltersByTheOrganizationsSellerType() throws Exception {
        expectNames(list().param("sellerType", "PRIVATE"), "Karikues Gama", "Bluzë Delta");
        expectNames(list().param("sellerType", "business"),
                "Telefon Alfa", "Alfa Kufje Beta", "Xhaketë Epsilon", "Makinë Zeta", "Tavolinë Eta");
    }

    @Test
    void minRating_KeepsProductsWithVisibleReviewsAveragingAtLeastTheValue() throws Exception {
        expectNames(list().param("minRating", "4.5"), "Telefon Alfa", "Karikues Gama", "Xhaketë Epsilon");
        expectNames(list().param("minRating", "1"),
                "Telefon Alfa", "Karikues Gama", "Bluzë Delta", "Xhaketë Epsilon");
    }

    @Test
    void freeShipping_KeepsShippableProductsWhoseEffectivePriceIsAboveTheThreshold() throws Exception {
        expectNames(list().param("freeShipping", "true"),
                "Telefon Alfa", "Bluzë Delta", "Xhaketë Epsilon", "Tavolinë Eta");
    }

    @Test
    void publishedWithinDays_KeepsRecentProducts() throws Exception {
        list(list().param("publishedWithinDays", "30")).andExpect(jsonPath("$.totalElements").value(6));
        expectNames(list().param("publishedWithinDays", "365").param("categoryId", id(general)),
                "Telefon Alfa", "Alfa Kufje Beta", "Karikues Gama", "Tavolinë Eta");
        expectNames(list().param("publishedWithinDays", "30").param("categoryId", id(general)),
                "Telefon Alfa", "Alfa Kufje Beta", "Karikues Gama");
    }

    @Test
    void paymentOnline_KeepsCartProductsOfSellersAcceptingPaypal() throws Exception {
        expectNames(list().param("paymentOnline", "true"),
                "Telefon Alfa", "Alfa Kufje Beta", "Xhaketë Epsilon", "Tavolinë Eta");
    }

    @Test
    void filters_CombineWithCategoryAndPrice() throws Exception {
        expectNames(list()
                        .param("inStock", "true")
                        .param("onSale", "true")
                        .param("verifiedSeller", "true")
                        .param("categoryId", id(general))
                        .param("maxPrice", "200"),
                "Telefon Alfa");
        expectNames(list()
                        .param("freeShipping", "true")
                        .param("paymentOnline", "true")
                        .param("categoryId", id(clothing))
                        .param("minPrice", "60"),
                "Xhaketë Epsilon");
        expectNames(list()
                        .param("minRating", "4")
                        .param("sellerType", "PRIVATE")
                        .param("categoryId", id(general)),
                "Karikues Gama");
    }

    @Test
    void filters_PageWithCorrectTotals() throws Exception {
        list(list().param("inStock", "true").param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.content[*].name").value(contains("Bluzë Delta", "Makinë Zeta")));
    }

    // ---------------------------------------------------------------- sorts

    @Test
    void sortByRating_BestAverageThenMostReviewsAndUnreviewedLast() throws Exception {
        expectNames(sorted("rating"),
                "Karikues Gama", "Xhaketë Epsilon", "Telefon Alfa", "Bluzë Delta",
                "Tavolinë Eta", "Makinë Zeta", "Alfa Kufje Beta");
    }

    @Test
    void sortByRating_PagesAndCombinesWithFilters() throws Exception {
        list(sorted("rating").param("page", "1").param("size", "3"))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[*].name").value(
                        contains("Bluzë Delta", "Tavolinë Eta", "Makinë Zeta")));
        expectNames(sorted("rating").param("categoryId", id(general)).param("minPrice", "50"),
                "Karikues Gama", "Telefon Alfa", "Tavolinë Eta");
    }

    @Test
    void sortByPopular_MostViewsInTheLast30DaysFirst() throws Exception {
        expectNames(sorted("popular"),
                "Alfa Kufje Beta", "Telefon Alfa", "Makinë Zeta",
                "Tavolinë Eta", "Xhaketë Epsilon", "Bluzë Delta", "Karikues Gama");
        list(sorted("popular").param("categoryId", id(general)).param("maxPrice", "100")
                .param("page", "0").param("size", "2"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].name").value(contains("Alfa Kufje Beta", "Tavolinë Eta")));
    }

    @Test
    void sortByBestselling_UnitsInDeliveredOrPaidOrdersFirst() throws Exception {
        expectNames(sorted("bestselling"),
                "Karikues Gama", "Telefon Alfa",
                "Tavolinë Eta", "Makinë Zeta", "Xhaketë Epsilon", "Bluzë Delta", "Alfa Kufje Beta");
        list(sorted("bestselling").param("onSale", "true").param("categoryId", id(general))
                .param("minPrice", "50").param("page", "0").param("size", "1"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[*].name").value(contains("Karikues Gama")));
    }

    @Test
    void sortByRelevance_WithKeyword_NameMatchesFirst() throws Exception {
        expectNames(sorted("relevance").param("keyword", "alfa"),
                "Alfa Kufje Beta", "Telefon Alfa", "Karikues Gama", "Tavolinë Eta");
        list(sorted("relevance").param("keyword", "alfa").param("categoryId", id(general))
                .param("minPrice", "50").param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].name").value(contains("Tavolinë Eta")));
    }

    @Test
    void sortByRelevance_WithoutKeyword_IsNewestFirst() throws Exception {
        expectNames(sorted("relevance"),
                "Telefon Alfa", "Alfa Kufje Beta", "Karikues Gama", "Bluzë Delta",
                "Xhaketë Epsilon", "Makinë Zeta", "Tavolinë Eta");
    }

    @Test
    void existingSorts_StillWork() throws Exception {
        list(list().param("sortBy", "price").param("sortDir", "desc").param("size", "2"))
                .andExpect(jsonPath("$.content[*].name").value(contains("Makinë Zeta", "Telefon Alfa")));
    }

    @Test
    void responses_CarryAverageRatingAndReviewCount() throws Exception {
        list(sorted("rating").param("size", "1"))
                .andExpect(jsonPath("$.content[0].averageRating").value(5.0))
                .andExpect(jsonPath("$.content[0].reviewCount").value(1));
        mockMvc.perform(get("/api/products/{id}", p5.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.reviewCount").value(4));
        mockMvc.perform(get("/api/products/{id}", p2.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").doesNotExist())
                .andExpect(jsonPath("$.reviewCount").value(0));
    }

    // ---------------------------------------------------------------- validation

    @ParameterizedTest
    @CsvSource({
            "inStock, maybe, inStock",
            "onSale, yes, onSale",
            "verifiedSeller, 2, verifiedSeller",
            "freeShipping, x, freeShipping",
            "paymentOnline, po, paymentOnline",
            "sellerType, SHOP, BUSINESS ose PRIVATE",
            "minRating, 0, nga 1 deri në 5",
            "minRating, 5.5, nga 1 deri në 5",
            "minRating, abc, nga 1 deri në 5",
            "publishedWithinDays, 0, nga 1 deri në 365",
            "publishedWithinDays, 366, nga 1 deri në 365",
            "publishedWithinDays, 7.5, nga 1 deri në 365"
    })
    void invalidValues_ReturnBadRequestWithAnAlbanianMessage(String name, String value, String message)
            throws Exception {
        mockMvc.perform(list().param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString(message)));
    }

    // ---------------------------------------------------------------- helpers

    private MockHttpServletRequestBuilder list() {
        // Default page size (10) holds the whole data set; tests that page set their own size.
        return get("/api/products").accept(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder sorted(String sortBy) {
        return list().param("sortBy", sortBy).param("sortDir", "desc");
    }

    private ResultActions list(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andExpect(status().isOk());
    }

    /** Expects exactly these names in this order (filters without sortBy are sorted by id asc). */
    private void expectNames(MockHttpServletRequestBuilder request, String... names) throws Exception {
        list(request)
                .andExpect(jsonPath("$.totalElements").value(names.length))
                .andExpect(jsonPath("$.content[*].name").value(contains(names)));
    }

    private static String id(Category category) {
        return String.valueOf(category.getId());
    }

    private Category category(String name, CategoryKind kind) {
        Category category = Category.builder().name(name).status(CategoryStatus.ACTIVE).build();
        category.setKind(kind);
        return categoryRepository.save(category);
    }

    private Product product(String name, String description, String brand, Category category,
                            Organization organization, String price, String discount, int stock) {
        return productRepository.save(Product.builder()
                .name(name).description(description).brand(brand)
                .category(category).organization(organization)
                .price(new BigDecimal(price))
                .discountPrice(discount == null ? null : new BigDecimal(discount))
                .stock(stock).status(ProductStatus.ACTIVE).build());
    }

    private void createdAt(Product product, LocalDateTime at) {
        jdbc.update("UPDATE products SET created_at = ? WHERE id = ?", Timestamp.valueOf(at), product.getId());
    }

    private void size(Product product, String value, int stock) {
        jdbc.update("INSERT INTO product_variants (product_id, variant_name, variant_value, stock) "
                + "VALUES (?, 'SIZE', ?, ?)", product.getId(), value, stock);
    }

    private long user(int index) {
        long role = jdbc.queryForObject("SELECT id FROM roles WHERE name = 'USER'", Long.class);
        String email = USER_EMAIL_PREFIX + index + "@example.com";
        jdbc.update("INSERT INTO users (role_id, first_name, email, password_hash) VALUES (?, 'Blerës', ?, 'h')",
                role, email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void reviews(Product product, boolean hidden, int... ratings) {
        for (int i = 0; i < ratings.length; i++) {
            jdbc.update("INSERT INTO reviews (user_id, product_id, rating, comment, hidden) VALUES (?, ?, ?, 'ok', ?)",
                    users.get(i), product.getId(), ratings[i], hidden);
        }
    }

    private void views(Product product, LocalDate day, int views) {
        jdbc.update("INSERT INTO product_daily_stats (product_id, stat_date, views) VALUES (?, ?, ?)",
                product.getId(), java.sql.Date.valueOf(day), views);
    }

    private long order(String status) {
        jdbc.update("INSERT INTO orders (user_id, total_amount, status) VALUES (?, 10, ?)", users.get(0), status);
        return jdbc.queryForObject("SELECT MAX(id) FROM orders", Long.class);
    }

    private void payment(long order, String status) {
        jdbc.update("INSERT INTO payments (order_id, method, status) VALUES (?, 'PAYPAL', ?)", order, status);
    }

    private void item(long order, Product product, int quantity) {
        jdbc.update("INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity, subtotal) "
                + "VALUES (?, ?, ?, 10, ?, ?)", order, product.getId(), product.getName(), quantity, quantity * 10);
    }
}
