package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ConflictException;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.integration.AbstractIntegrationTest;
import com.omnia.backend.security.service.CurrentUserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Transactional
class PushCampaignIntegrationTest extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;

    private CurrentUserService current;
    private PushCampaigns campaigns;
    private CategoryViews views;
    private User admin, viewer, fan, optedOut, stranger;
    private Category cars, other;
    private Product car, shirt;

    private Role role(String name) {
        var list = em.createQuery("select r from Role r where r.name=:n", Role.class).setParameter("n", name).getResultList();
        if (!list.isEmpty()) return list.get(0);
        Role r = Role.builder().name(name).build();
        em.persist(r);
        return r;
    }

    private User user(String role) {
        String id = "pc" + UUID.randomUUID().toString().replace("-", "");
        User u = User.builder().role(role(role)).username(id).email(id + "@example.test").firstName("Test").passwordHash("unused").emailVerified(true).build();
        em.persist(u);
        return u;
    }

    private Category category(String name) {
        Category c = Category.builder().name(name + UUID.randomUUID()).status(com.omnia.backend.enums.CategoryStatus.ACTIVE).build();
        em.persist(c);
        return c;
    }

    private Product product(Category c) {
        Product p = Product.builder().name("Listing " + UUID.randomUUID()).price(BigDecimal.TEN).stock(3)
                .category(c).status(com.omnia.backend.enums.ProductStatus.ACTIVE).build();
        em.persist(p);
        return p;
    }

    /** Push on, offers on, one fresh device. */
    private void reachable(User u) {
        jdbc.update("INSERT INTO push_preferences(user_id,enabled,offers_enabled) VALUES(?,TRUE,TRUE)", u.getId());
        jdbc.update("INSERT INTO push_devices(installation_id,secret_hash,user_id,token,token_hash,binding_version,binding_tag,enabled,last_seen) VALUES(?,?,?,?,?,1,?,TRUE,UTC_TIMESTAMP(6))",
                UUID.randomUUID().toString(), "a".repeat(64), u.getId(), "tok-" + u.getId(), PushRules.hash("tok-" + u.getId()), UUID.randomUUID().toString());
    }

    private void as(User u) {
        when(current.requireCurrentUser()).thenReturn(u);
        when(current.findCurrentUser()).thenReturn(Optional.of(u));
        boolean platform = Set.of("ADMIN", "SUPER_ADMIN").contains(u.getRole().getName());
        when(current.hasPlatformAdminAccess(u)).thenReturn(platform);
        if (platform) doNothing().when(current).requirePlatformAdmin();
        else doThrow(new AccessDeniedException("no")).when(current).requirePlatformAdmin();
    }

    private PushCampaigns.Draft draft(Long category, String interest) {
        return new PushCampaigns.Draft("Oferta të reja", "Shiko makinat e reja në Omnia", "/kategori/" + category, category, interest, 30, null, "ALL");
    }

    @BeforeEach
    void setup() {
        current = mock(CurrentUserService.class);
        campaigns = new PushCampaigns(jdbc, current, transactions, 3);
        views = new CategoryViews(jdbc, current);
        admin = user("SUPER_ADMIN");
        viewer = user("USER");
        fan = user("USER");
        optedOut = user("USER");
        stranger = user("USER");
        cars = category("Makina");
        other = category("Tjeter");
        car = product(cars);
        shirt = product(other);
        em.flush();
        reachable(admin);
        reachable(viewer);
        reachable(fan);
        reachable(stranger);
        jdbc.update("INSERT INTO push_preferences(user_id,enabled,offers_enabled) VALUES(?,TRUE,FALSE)", optedOut.getId());
        // viewer and optedOut viewed a car; fan favourited one; stranger only viewed a shirt.
        views.record(viewer.getId(), car.getId());
        views.record(optedOut.getId(), car.getId());
        views.record(stranger.getId(), shirt.getId());
        jdbc.update("INSERT INTO favorites(user_id,product_id) VALUES(?,?)", fan.getId(), car.getId());
        as(admin);
    }

    @Test
    void audienceCountsSegmentOptInAndCap() {
        var viewed = campaigns.audience(draft(cars.getId(), "VIEWED"));
        assertEquals(2, viewed.matched());
        assertEquals(1, viewed.optedIn());
        assertEquals(1, viewed.reachable());
        var any = campaigns.audience(draft(cars.getId(), "ANY"));
        assertEquals(3, any.matched());
        assertEquals(2, any.reachable());
        var fav = campaigns.audience(draft(cars.getId(), "FAVORITED"));
        assertEquals(1, fav.matched());
    }

    @Test
    void cityAndPlatformNarrowTheSegment() {
        jdbc.update("INSERT INTO addresses(user_id,city) VALUES(?,'Durrës')", fan.getId());
        var durres = campaigns.audience(new PushCampaigns.Draft("T", "B", null, cars.getId(), "ANY", 30, " durrës ", "ANDROID"));
        assertEquals(1, durres.reachable());
        assertThrows(IllegalArgumentException.class, () -> campaigns.audience(new PushCampaigns.Draft("T", "B", null, null, "ANY", 30, null, "IOS")));
    }

    @Test
    void repeatViewsWithinAMinuteCountOnce() {
        views.record(viewer.getId(), car.getId());
        assertEquals(1, jdbc.queryForObject("SELECT view_count FROM user_category_views WHERE user_id=? AND category_id=?", Integer.class, viewer.getId(), cars.getId()));
    }

    @Test
    void schedulingRequiresATestAndEditsResetIt() {
        var c = campaigns.create(draft(cars.getId(), "ANY"));
        assertEquals("DRAFT", c.status());
        assertThrows(ConflictException.class, () -> campaigns.schedule(c.id(), null, c.revision()));

        var tested = campaigns.test(c.id());
        assertEquals("TESTED", tested.campaign().status());
        assertTrue(tested.hasDevice());
        // The test goes to the admin only and is not counted against the frequency cap.
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_inbox WHERE target_type='CAMPAIGN' AND target_id=?", Integer.class, c.id()));
        assertEquals(admin.getId(), jdbc.queryForObject("SELECT recipient_id FROM notification_inbox WHERE target_type='CAMPAIGN' AND target_id=?", Long.class, c.id()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM push_campaign_recipients WHERE campaign_id=?", Integer.class, c.id()));

        var edited = campaigns.update(c.id(), draft(cars.getId(), "VIEWED"), tested.campaign().revision());
        assertEquals("DRAFT", edited.status());
        assertThrows(ConflictException.class, () -> campaigns.schedule(c.id(), null, edited.revision()));
    }

    @Test
    void sendingQueuesOnlyReachableUsersAndTheCapBlocksTheNext() {
        var c = campaigns.create(draft(cars.getId(), "ANY"));
        var tested = campaigns.test(c.id()).campaign();
        assertThrows(IllegalArgumentException.class, () -> campaigns.schedule(c.id(), Instant.now().plusSeconds(90L * 86400), tested.revision()));
        campaigns.schedule(c.id(), null, tested.revision());
        assertTrue(campaigns.sendOne());
        assertFalse(campaigns.sendOne());

        var sent = campaigns.get(c.id());
        assertEquals("SENT", sent.status());
        assertEquals(2, sent.recipientCount());
        var recipients = jdbc.queryForList("SELECT recipient_id FROM notification_inbox WHERE kind='CAMPAIGN' AND target_id=?", Long.class, c.id());
        assertEquals(Set.of(viewer.getId(), fan.getId()), Set.copyOf(recipients));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM push_outbox b JOIN notification_inbox n ON n.id=b.notification_id WHERE n.kind='CAMPAIGN' AND n.target_id=? AND b.priority=1", Integer.class, c.id()));

        // Frequency cap: the same users are not reachable again for 3 days.
        assertEquals(0, campaigns.audience(draft(cars.getId(), "ANY")).reachable());
        assertThrows(ConflictException.class, () -> campaigns.cancel(c.id()));
    }

    @Test
    void campaignPushRespectsOffersSettingAndCarriesNoText() throws Exception {
        var c = campaigns.create(draft(cars.getId(), "ANY"));
        campaigns.schedule(c.id(), null, campaigns.test(c.id()).campaign().revision());
        campaigns.sendOne();
        jdbc.update("UPDATE push_outbox SET state='DONE' WHERE notification_id NOT IN (SELECT id FROM notification_inbox WHERE kind='CAMPAIGN' AND target_id=?)", c.id());
        // fan turns offers off after the campaign was queued: the dispatcher must drop it.
        jdbc.update("UPDATE push_preferences SET offers_enabled=FALSE WHERE user_id=?", fan.getId());
        FirebasePushSender sender = mock(FirebasePushSender.class);
        when(sender.ready()).thenReturn(true);
        new PushDispatcher(jdbc, sender, new PushPreferences(jdbc, current, sender), transactions).dispatch();
        verify(sender, times(1)).send(eq("tok-" + viewer.getId()), argThat(data -> data.get("kind").equals("CAMPAIGN") && !data.toString().contains("Oferta")));
        verify(sender, never()).send(eq("tok-" + fan.getId()), anyMap());
    }

    @Test
    void onlyPlatformAdminsAndOnlySafeLinks() {
        as(viewer);
        assertThrows(AccessDeniedException.class, () -> campaigns.create(draft(cars.getId(), "ANY")));
        assertThrows(AccessDeniedException.class, () -> campaigns.audience(draft(cars.getId(), "ANY")));
        as(admin);
        assertThrows(IllegalArgumentException.class, () -> campaigns.create(new PushCampaigns.Draft("T", "B", "https://evil.example", null, "ANY", 30, null, "ALL")));
        assertThrows(IllegalArgumentException.class, () -> campaigns.create(new PushCampaigns.Draft("T", "B", "//evil.example", null, "ANY", 30, null, "ALL")));
        assertThrows(IllegalArgumentException.class, () -> campaigns.create(new PushCampaigns.Draft(" ", "B", null, null, "ANY", 30, null, "ALL")));
        assertThrows(IllegalArgumentException.class, () -> campaigns.create(new PushCampaigns.Draft("x".repeat(66), "B", null, null, "ANY", 30, null, "ALL")));
    }
}
