package com.omnia.backend.integration;

import com.omnia.backend.common.exception.ConflictException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.*;
import com.omnia.backend.repository.ReviewRepository;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.service.impl.NotificationInbox;
import com.omnia.backend.service.impl.NotificationWriter;
import com.omnia.backend.service.impl.SavedSearchAlertJob;
import com.omnia.backend.service.impl.SavedSearchService;
import com.omnia.backend.repository.SavedSearchRepository;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Saved searches with alerts, price drops of favourites and verified reviews. */
@Transactional
class BuyerToolsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NotificationWriter writer;
    @Autowired private SavedSearchRepository searches;
    @Autowired private SavedSearchAlertJob job;
    @Autowired private ReviewRepository reviews;

    private CurrentUserService current;
    private SavedSearchService service;
    private NotificationInbox inbox;
    private User buyer;
    private User other;
    private Organization seller;
    private Category cars;

    @BeforeEach
    void setUp() {
        current = mock(CurrentUserService.class);
        service = new SavedSearchService(searches, current, jdbc);
        inbox = new NotificationInbox(jdbc, current, em);
        buyer = user();
        other = user();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        seller = Organization.builder().name("Auto " + suffix).slug("auto-" + suffix)
                .status(OrganizationStatus.ACTIVE).build();
        em.persist(seller);
        cars = Category.builder().name("Makina " + suffix).status(CategoryStatus.ACTIVE)
                .kind(CategoryKind.CAR).build();
        em.persist(cars);
        em.flush();
    }

    private Role role(String name) {
        return em.createQuery("select r from Role r where r.name=:n", Role.class)
                .setParameter("n", name).getResultList().stream().findFirst().orElseGet(() -> {
                    Role r = Role.builder().name(name).build();
                    em.persist(r);
                    return r;
                });
    }

    private User user() {
        String id = "bt" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        User u = User.builder().role(role("USER")).username(id).email(id + "@example.test")
                .firstName("Blerës").passwordHash("unused").emailVerified(true).status(UserStatus.ACTIVE).build();
        em.persist(u);
        return u;
    }

    private void as(User u) {
        when(current.requireCurrentUser()).thenReturn(u);
        when(current.hasPlatformAdminAccess(u)).thenReturn(false);
    }

    private Product car(String name, String make, String location, String price) {
        Product p = Product.builder().organization(seller).category(cars).name(name)
                .price(new BigDecimal(price)).stock(1).status(ProductStatus.ACTIVE)
                .attributes(new java.util.HashMap<>(Map.of("make", make, "model", name, "year", 2016,
                        "mileageKm", 120000, "fuel", "DIESEL", "gearbox", "MANUAL", "location", location)))
                .build();
        em.persist(p);
        em.flush();
        return p;
    }

    private Map<String, String> golfFilters() {
        return Map.of("categoryId", cars.getId().toString(), "keyword", "golf",
                "maxPrice", "8000.00", "attr.location", "Tiranë", "attr.year.min", "2010");
    }

    @Test
    void savedSearchAlertsOnlyAboutNewMatchingProductsOnce() {
        car("Golf e vjetër", "Volkswagen", "Tiranë", "6000"); // listed before saving: not "new"
        as(buyer);
        SavedSearchService.Response saved = service.create(
                new SavedSearchService.CreateRequest("Golf, nën 8.000 €, Tiranë", golfFilters(), true));
        assertEquals("8000", saved.filters().get("maxPrice"), "prices are normalized");
        assertTrue(saved.alertsEnabled());

        Product match = car("Golf 7", "Volkswagen", "Tiranë", "7500");
        car("Golf 8", "Volkswagen", "Tiranë", "9500");   // too expensive
        car("Golf 6", "Volkswagen", "Durrës", "5000");   // other city

        assertTrue(job.runOnce() >= 1);
        as(buyer);
        var notice = inbox.list("all", 0).items().stream()
                .filter(n -> n.kind().equals("SAVED_SEARCH")).findFirst().orElseThrow();
        assertEquals("SEARCH", notice.targetType());
        assertEquals(saved.id(), notice.targetId());
        assertEquals(1, notice.quantity());
        assertEquals("BUYER", notice.category());
        assertEquals(match.getId(), searches.findById(saved.id()).orElseThrow().getLastSeenProductId());
        Map<String, Object> detail = inbox.open(notice.id());
        assertEquals("Golf, nën 8.000 €, Tiranë", detail.get("name"));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM push_outbox WHERE notification_id=?", Long.class, notice.id()));

        // Nothing new: no second notice.
        job.runOnce();
        as(buyer);
        assertEquals(1, inbox.list("all", 0).items().stream().filter(n -> n.kind().equals("SAVED_SEARCH")).count());

        // Another buyer never sees it.
        as(other);
        assertTrue(inbox.list("all", 0).items().isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> inbox.open(notice.id()));

        // Deleting the search removes its notices.
        as(buyer);
        service.delete(saved.id());
        assertTrue(inbox.list("all", 0).items().isEmpty());
    }

    @Test
    void alertsCanBeTurnedOffAndBackOnWithoutReportingTheGap() {
        as(buyer);
        var saved = service.create(new SavedSearchService.CreateRequest("Golf", golfFilters(), true));
        service.update(saved.id(), new SavedSearchService.UpdateRequest(null, false));
        car("Golf 7", "Volkswagen", "Tiranë", "7000");
        job.runOnce();
        service.update(saved.id(), new SavedSearchService.UpdateRequest("Golf Tiranë", true));
        job.runOnce();
        as(buyer);
        assertTrue(inbox.list("all", 0).items().isEmpty());
        assertEquals("Golf Tiranë", service.mine().get(0).name());
    }

    @Test
    void savedSearchesAreValidatedDeduplicatedAndPrivate() {
        as(buyer);
        service.create(new SavedSearchService.CreateRequest("Golf", golfFilters(), true));
        Map<String, String> same = new java.util.HashMap<>(golfFilters());
        same.put("maxPrice", "8000");
        assertThrows(ConflictException.class,
                () -> service.create(new SavedSearchService.CreateRequest("Golf 2", same, true)));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new SavedSearchService.CreateRequest("X", Map.of("password", "x"), true)));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new SavedSearchService.CreateRequest("X", Map.of("attr.unknown", "x"), true)));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new SavedSearchService.CreateRequest("X", Map.of("minPrice", "9", "maxPrice", "1"), true)));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new SavedSearchService.CreateRequest("X", Map.of("keyword", " "), true)));

        Long id = service.mine().get(0).id();
        as(other);
        assertTrue(service.mine().isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.delete(id));
        assertThrows(ResourceNotFoundException.class,
                () -> service.update(id, new SavedSearchService.UpdateRequest("mine", null)));
    }

    @Test
    void priceDropNotifiesOnlyThoseWhoFavouritedAnActiveProduct() {
        Product golf = car("Golf 7", "Volkswagen", "Tiranë", "9000");
        em.persist(Favorite.builder().user(buyer).product(golf).build());
        em.flush();

        assertEquals(0, writer.priceDrop(golf, new BigDecimal("9000"), new BigDecimal("9500")), "no notice for a rise");
        assertEquals(1, writer.priceDrop(golf, new BigDecimal("9000"), new BigDecimal("8500")));
        assertEquals(0, writer.priceDrop(golf, new BigDecimal("9000"), new BigDecimal("8500")), "idempotent");

        as(buyer);
        var notice = inbox.list("all", 0).items().get(0);
        assertEquals("PRICE_DROP", notice.kind());
        assertEquals(0, new BigDecimal("8500").compareTo(notice.amount()));
        assertEquals(0, new BigDecimal("9000").compareTo(notice.previousAmount()));
        as(other);
        assertTrue(inbox.list("all", 0).items().isEmpty());

        golf.setStatus(ProductStatus.INACTIVE);
        em.flush();
        as(buyer);
        assertTrue(inbox.list("all", 0).items().isEmpty(), "unlisted products are not shown");
    }

    @Test
    void onlyADeliveredOrderMakesAVerifiedBuyer() {
        Product golf = car("Golf 7", "Volkswagen", "Tiranë", "9000");
        Order pending = order(buyer, golf, OrderStatus.SHIPPED);
        assertFalse(reviews.hasDeliveredPurchase(buyer.getId(), golf.getId()));
        pending.setStatus(OrderStatus.DELIVERED);
        em.flush();
        assertTrue(reviews.hasDeliveredPurchase(buyer.getId(), golf.getId()));
        assertFalse(reviews.hasDeliveredPurchase(other.getId(), golf.getId()));
    }

    private Order order(User user, Product product, OrderStatus status) {
        Order o = Order.builder().user(user).organization(seller).organizationName(seller.getName())
                .totalAmount(product.getPrice()).status(status).build();
        em.persist(o);
        em.persist(OrderItem.builder().order(o).productId(product.getId()).productName(product.getName())
                .unitPrice(product.getPrice()).quantity(1).subtotal(product.getPrice()).build());
        em.flush();
        return o;
    }
}
