package com.omnia.backend.service.impl;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Order;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.OrganizationMember;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.CategoryStatus;
import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.enums.OrganizationMemberRole;
import com.omnia.backend.enums.OrganizationMemberStatus;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.enums.UserStatus;
import com.omnia.backend.integration.AbstractIntegrationTest;
import com.omnia.backend.security.service.CurrentUserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for push delivery decisions that the happy-path tests in
 * {@link PushIntegrationTest} do not reach: who may receive which notification kind,
 * how FCM failures are handled, and which device registrations and preference
 * changes are refused.
 */
@Transactional
class PushDeliveryRegressionTest extends AbstractIntegrationTest {

    private static final String SECRET = "a".repeat(64);

    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;

    private CurrentUserService current;
    private FirebasePushSender sender;
    private PushPreferences prefs;
    private PushDevices devices;
    private PushDispatcher dispatcher;

    private User buyer;
    private User superAdmin;
    private Order order;

    @BeforeEach
    void setUp() {
        // Earlier tests may leave committed PENDING rows; they must not be picked up here.
        jdbc.update("UPDATE push_outbox SET state='DONE' WHERE state='PENDING'");
        current = mock(CurrentUserService.class);
        sender = mock(FirebasePushSender.class);
        when(sender.ready()).thenReturn(true);
        prefs = new PushPreferences(jdbc, current, sender);
        devices = new PushDevices(jdbc, prefs);
        dispatcher = new PushDispatcher(jdbc, sender, prefs, transactions);

        buyer = user("USER");
        superAdmin = user("SUPER_ADMIN");
        order = Order.builder().user(buyer).status(OrderStatus.CONFIRMED).totalAmount(BigDecimal.TEN).build();
        em.persist(order);
        em.flush();
    }

    // ---------------------------------------------------------------- dispatcher

    @Test
    void superAdminReceivesOrderStatusForAnotherBuyersOrderThroughAdminOrderPreference() throws Exception {
        optIn(superAdmin, "admin-token");
        long id = notice(superAdmin, "ORDER_STATUS", "ORDER", order.getId());

        dispatcher.dispatch();

        verify(sender).send(eq("admin-token"), argThat(data -> "ORDER_STATUS".equals(data.get("kind"))
                && String.valueOf(id).equals(data.get("noticeId"))));
        assertEquals("DONE", outboxState(id));
    }

    @Test
    void superAdminReceivesAuditStockAndPayPalReviewKindsButNeverUnknownKinds() throws Exception {
        optIn(superAdmin, "admin-token");
        Product product = product(null, 1);
        long registered = notice(superAdmin, "USER_REGISTERED", "USER", buyer.getId());
        Organization company = organization();
        long companyCreated = notice(superAdmin, "COMPANY_CREATED", "COMPANY", company.getId());
        long lowStock = notice(superAdmin, "LOW_STOCK", "PRODUCT", product.getId());
        long review = notice(superAdmin, "PAYPAL_REVIEW", "ORDER", order.getId());
        long unknown = notice(superAdmin, "MARKETING", "ORDER", order.getId());

        dispatcher.dispatch();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> sent = ArgumentCaptor.forClass(Map.class);
        verify(sender, times(4)).send(eq("admin-token"), sent.capture());
        assertEquals(List.of("USER_REGISTERED", "COMPANY_CREATED", "LOW_STOCK", "PAYPAL_REVIEW"),
                sent.getAllValues().stream().map(data -> data.get("kind")).toList());
        for (long id : List.of(registered, companyCreated, lowStock, review, unknown)) {
            assertEquals("DONE", outboxState(id));
        }
    }

    @Test
    void platformAdminWithoutSuperAdminRoleDoesNotReceiveAuditOrPayPalReviewPushes() throws Exception {
        User admin = user("ADMIN");
        optIn(admin, "admin-token");
        long companyCreated = notice(admin, "COMPANY_CREATED", "COMPANY", organization().getId());
        long review = notice(admin, "PAYPAL_REVIEW", "ORDER", order.getId());

        dispatcher.dispatch();

        verify(sender, never()).send(anyString(), anyMap());
        assertEquals("DONE", outboxState(companyCreated));
        assertEquals("DONE", outboxState(review));
    }

    @Test
    void noticeTheRecipientCanNoLongerSeeIsClosedWithoutSending() throws Exception {
        optIn(buyer, "buyer-token");
        // A regular user is never allowed to see USER targets, even if a row was written for them.
        long id = notice(buyer, "USER_REGISTERED", "USER", superAdmin.getId());

        dispatcher.dispatch();

        verify(sender, never()).send(anyString(), anyMap());
        assertEquals("DONE", outboxState(id));
    }

    @Test
    void recipientWhoNeverOpenedPushSettingsIsNotSent() throws Exception {
        as(buyer);
        devices.register(registration(UUID.randomUUID().toString(), "buyer-token", 1, true));
        long id = notice(buyer, "ORDER_STATUS", "ORDER", order.getId());

        dispatcher.dispatch();

        verify(sender, never()).send(anyString(), anyMap());
        assertEquals("DONE", outboxState(id));
    }

    @Test
    void unconfiguredSenderLeavesTheQueueUntouched() throws Exception {
        optIn(buyer, "buyer-token");
        long id = notice(buyer, "ORDER_STATUS", "ORDER", order.getId());
        when(sender.ready()).thenReturn(false);

        dispatcher.dispatch();

        verify(sender, never()).send(anyString(), anyMap());
        assertEquals("PENDING", outboxState(id));
        assertEquals(0, attempts(id));
    }

    @Test
    void unregisteredTokenDisablesTheDeviceAndClosesTheNotice() throws Exception {
        String installation = optIn(buyer, "dead-token");
        long id = notice(buyer, "ORDER_STATUS", "ORDER", order.getId());
        doThrow(firebaseError(MessagingErrorCode.UNREGISTERED)).when(sender).send(eq("dead-token"), anyMap());

        dispatcher.dispatch();

        Map<String, Object> device = jdbc.queryForMap(
                "SELECT enabled,token,token_hash FROM push_devices WHERE installation_id=?", installation);
        assertFalse((Boolean) device.get("enabled"));
        assertNull(device.get("token"));
        assertNull(device.get("token_hash"));
        assertEquals("DONE", outboxState(id));
        assertEquals(0L, deliveries(id));
    }

    @Test
    void transientFcmFailureRequeuesTheNoticeForALaterAttempt() throws Exception {
        String installation = optIn(buyer, "buyer-token");
        long id = notice(buyer, "ORDER_STATUS", "ORDER", order.getId());
        doThrow(firebaseError(MessagingErrorCode.UNAVAILABLE)).when(sender).send(eq("buyer-token"), anyMap());

        dispatcher.dispatch();

        assertEquals("PENDING", outboxState(id));
        assertEquals(1, attempts(id));
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT available_at>UTC_TIMESTAMP(6) AND lease_until IS NULL FROM push_outbox WHERE notification_id=?",
                Boolean.class, id));
        assertTrue(jdbc.queryForObject(
                "SELECT enabled FROM push_devices WHERE installation_id=?", Boolean.class, installation));
        assertEquals(0L, deliveries(id));
    }

    @Test
    void redeliveryOfARequeuedNoticeSkipsDevicesThatAlreadyReceivedIt() throws Exception {
        optIn(buyer, "buyer-token");
        long id = notice(buyer, "ORDER_STATUS", "ORDER", order.getId());
        dispatcher.dispatch();
        verify(sender, times(1)).send(eq("buyer-token"), anyMap());

        // Simulate the row being requeued (for example after another device failed).
        jdbc.update("UPDATE push_outbox SET state='PENDING',lease_until=NULL WHERE notification_id=?", id);
        dispatcher.dispatch();

        verify(sender, times(1)).send(eq("buyer-token"), anyMap());
        assertEquals(1L, deliveries(id));
        assertEquals("DONE", outboxState(id));
    }

    // ---------------------------------------------------------------- device registration

    @Test
    void malformedRegistrationsAreRejectedBeforeAnyDeviceIsStored() {
        as(buyer);
        String id = UUID.randomUUID().toString();
        String tag = UUID.randomUUID().toString();
        List<PushDevices.Registration> invalid = List.of(
                new PushDevices.Registration(id, null, "token", 1, tag, true),
                new PushDevices.Registration(id, "A".repeat(64), "token", 1, tag, true),
                new PushDevices.Registration(id, SECRET, "token", 0, tag, true),
                new PushDevices.Registration(id, SECRET, null, 1, tag, true),
                new PushDevices.Registration(id, SECRET, "t".repeat(4097), 1, tag, true),
                new PushDevices.Registration(id, SECRET, "  ", 1, tag, true),
                new PushDevices.Registration("not-a-uuid", SECRET, "token", 1, tag, true),
                new PushDevices.Registration(id.toUpperCase(), SECRET, "token", 1, tag, true),
                new PushDevices.Registration(id, SECRET, "token", 1, "not-a-uuid", true));
        assertThrows(IllegalArgumentException.class, () -> devices.register(null));
        for (PushDevices.Registration registration : invalid) {
            assertThrows(IllegalArgumentException.class, () -> devices.register(registration), registration.toString());
        }
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM push_devices WHERE installation_id=?", Long.class, id));
    }

    @Test
    void disabledRegistrationStoresNoTokenAndMayBeRepeatedAtTheSameBinding() {
        as(buyer);
        String id = UUID.randomUUID().toString();
        String tag = UUID.randomUUID().toString();
        PushDevices.Registration disabled = new PushDevices.Registration(id, SECRET, "", 1, tag, false);

        devices.register(disabled);
        devices.register(disabled);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT user_id,token,token_hash,enabled FROM push_devices WHERE installation_id=?", id);
        assertEquals(buyer.getId(), ((Number) row.get("user_id")).longValue());
        assertNull(row.get("token"));
        assertNull(row.get("token_hash"));
        assertFalse((Boolean) row.get("enabled"));
        // Same version with a different tag is a stale replay, not an update.
        assertThrows(ResourceAlreadyExistsException.class, () -> devices.register(
                new PushDevices.Registration(id, SECRET, "", 1, UUID.randomUUID().toString(), false)));
    }

    @Test
    void tokenAlreadyBoundToAnotherInstallationIsRefused() {
        as(buyer);
        devices.register(registration(UUID.randomUUID().toString(), "shared-token", 1, true));

        String second = UUID.randomUUID().toString();
        assertThrows(ResourceAlreadyExistsException.class,
                () -> devices.register(registration(second, "shared-token", 1, true)));
    }

    @Test
    void activeDeviceLimitStopsTheEleventhEnabledInstallation() {
        as(buyer);
        for (int i = 0; i < 10; i++) {
            devices.register(registration(UUID.randomUUID().toString(), "token-" + i, 1, true));
        }

        assertThrows(IllegalArgumentException.class,
                () -> devices.register(registration(UUID.randomUUID().toString(), "token-10", 1, true)));
        // A disabled installation does not count against the limit and can still be bound.
        devices.register(new PushDevices.Registration(
                UUID.randomUUID().toString(), SECRET, "", 1, UUID.randomUUID().toString(), false));
    }

    @Test
    void inactiveAccountCannotRegisterDevicesOrReadPreferences() {
        buyer.setStatus(UserStatus.BANNED);
        em.flush();
        as(buyer);

        assertThrows(AccessDeniedException.class,
                () -> devices.register(registration(UUID.randomUUID().toString(), "token", 1, true)));
        assertThrows(AccessDeniedException.class, () -> prefs.get());
    }

    // ---------------------------------------------------------------- preferences

    @Test
    void superAdminKeepsEveryPrivilegedCategoryTheyChoose() {
        as(superAdmin);

        PushPreferences.Response on = prefs.save(values(true, 0));
        assertTrue(on.manager());
        assertTrue(on.superAdmin());
        assertTrue(on.configured());
        assertTrue(on.preferences().adminOrders());
        assertTrue(on.preferences().stock());
        assertTrue(on.preferences().audit());
        assertTrue(on.preferences().reports());

        PushPreferences.Response off = prefs.save(values(false, 1));
        assertFalse(off.preferences().adminOrders());
        assertFalse(off.preferences().stock());
        assertFalse(off.preferences().audit());
        assertFalse(off.preferences().reports());
    }

    @Test
    void companyManagerKeepsManagerCategoriesButAuditStaysMasked() {
        User manager = user("USER");
        Organization company = organization();
        em.persist(OrganizationMember.builder().organization(company).user(manager)
                .membershipRole(OrganizationMemberRole.OWNER).status(OrganizationMemberStatus.ACTIVE).build());
        em.flush();
        as(manager);

        PushPreferences.Response saved = prefs.save(values(true, 0));

        assertTrue(saved.manager());
        assertFalse(saved.superAdmin());
        assertTrue(saved.preferences().adminOrders());
        assertTrue(saved.preferences().stock());
        assertTrue(saved.preferences().reports());
        assertFalse(saved.preferences().audit());
    }

    @Test
    void missingPreferencesPayloadIsRejected() {
        as(buyer);
        assertThrows(IllegalArgumentException.class, () -> prefs.save(null));
    }

    // ---------------------------------------------------------------- fixtures

    private Role role(String name) {
        var rows = em.createQuery("select r from Role r where r.name=:n", Role.class)
                .setParameter("n", name).getResultList();
        if (!rows.isEmpty()) return rows.get(0);
        Role role = Role.builder().name(name).build();
        em.persist(role);
        return role;
    }

    private User user(String role) {
        String id = "pr" + UUID.randomUUID().toString().replace("-", "");
        User user = User.builder().role(role(role)).username(id).email(id + "@example.test")
                .firstName("Push").passwordHash("unused").emailVerified(true).build();
        em.persist(user);
        return user;
    }

    private Organization organization() {
        Organization organization = Organization.builder().name("pr-" + UUID.randomUUID())
                .slug("pr-" + UUID.randomUUID()).status(OrganizationStatus.ACTIVE).build();
        em.persist(organization);
        em.flush();
        return organization;
    }

    private Product product(Organization organization, int stock) {
        Category category = Category.builder().name("Push fixtures " + UUID.randomUUID())
                .status(CategoryStatus.ACTIVE).build();
        em.persist(category);
        Product product = Product.builder().organization(organization).category(category).name("Push product")
                .price(BigDecimal.TEN).stock(stock).status(ProductStatus.ACTIVE).build();
        em.persist(product);
        em.flush();
        return product;
    }

    private void as(User user) {
        when(current.requireCurrentUser()).thenReturn(user);
        String role = user.getRole().getName();
        when(current.hasPlatformAdminAccess(user)).thenReturn(role.equals("ADMIN") || role.equals("SUPER_ADMIN"));
    }

    private PushPreferences.Values values(boolean privileged, long revision) {
        return new PushPreferences.Values(true, true, true, true, true, privileged, privileged, privileged,
                privileged, false, 1320, 480, "UTC", true, true, revision);
    }

    private PushDevices.Registration registration(String installation, String token, long version, boolean enabled) {
        return new PushDevices.Registration(installation, SECRET, token, version, UUID.randomUUID().toString(), enabled);
    }

    /** Enables every push category the user is allowed and registers one device; returns its installation id. */
    private String optIn(User user, String token) {
        as(user);
        prefs.save(values(true, 0));
        String installation = UUID.randomUUID().toString();
        devices.register(registration(installation, token, 1, true));
        return installation;
    }

    private long notice(User recipient, String kind, String targetType, long targetId) {
        String key = "push-regression:" + UUID.randomUUID();
        jdbc.update("INSERT INTO notification_inbox(recipient_id,event_key,kind,category,target_type,target_id,label,created_at) "
                + "VALUES(?,?,?,'USER',?,?,'Fixture',UTC_TIMESTAMP(6))", recipient.getId(), key, kind, targetType, targetId);
        long id = jdbc.queryForObject("SELECT id FROM notification_inbox WHERE recipient_id=? AND event_key=?",
                Long.class, recipient.getId(), key);
        jdbc.update("INSERT INTO push_outbox(notification_id,available_at) VALUES(?,UTC_TIMESTAMP(6))", id);
        return id;
    }

    private String outboxState(long id) {
        return jdbc.queryForObject("SELECT state FROM push_outbox WHERE notification_id=?", String.class, id);
    }

    private int attempts(long id) {
        return jdbc.queryForObject("SELECT attempts FROM push_outbox WHERE notification_id=?", Integer.class, id);
    }

    private long deliveries(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM push_deliveries WHERE notification_id=?", Long.class, id);
    }

    private static FirebaseMessagingException firebaseError(MessagingErrorCode code) {
        FirebaseMessagingException error = mock(FirebaseMessagingException.class);
        when(error.getMessagingErrorCode()).thenReturn(code);
        return error;
    }
}
