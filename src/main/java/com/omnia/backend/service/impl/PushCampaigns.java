package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ConflictException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.entity.User;
import com.omnia.backend.security.service.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Segmented push campaigns written by platform admins.
 *
 * <p>Flow: DRAFT → (mandatory test send to the admin only) TESTED → SCHEDULED → SENDING → SENT.
 * Any edit sends a campaign back to DRAFT, so what goes out is always what was tested.
 *
 * <p>A campaign reaches only users who turned push on <em>and</em> opted into offers
 * ({@code push_preferences.offers_enabled}, off by default), have a recently seen device and
 * did not get another campaign within the frequency cap. Delivery goes through the normal
 * inbox + outbox, so quiet hours and the no-personal-data FCM payload rules still apply.
 */
@Service
public class PushCampaigns {

    private static final Logger LOG = LoggerFactory.getLogger(PushCampaigns.class);
    static final Set<String> INTERESTS = Set.of("ANY", "VIEWED", "FAVORITED");
    static final Set<String> PLATFORMS = Set.of("ALL", "ANDROID");
    private static final Pattern LINK = Pattern.compile("^/(?!/)[A-Za-z0-9\\-._~/%?=&+]*$");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n]]");
    /** Recipients written per campaign at most; the dispatcher's 6 h window bounds it anyway. */
    static final int MAX_RECIPIENTS = 20_000;

    private final JdbcTemplate jdbc;
    private final CurrentUserService current;
    private final TransactionTemplate tx;
    private final int capDays;

    public PushCampaigns(JdbcTemplate jdbc, CurrentUserService current, PlatformTransactionManager transactions,
                         @Value("${omnia.campaigns.cap-days:3}") int capDays) {
        this.jdbc = jdbc;
        this.current = current;
        this.tx = new TransactionTemplate(transactions);
        this.capDays = Math.max(1, capDays);
    }

    public record Draft(String title, String body, String linkPath, Long categoryId, String interest,
                        Integer activeDays, String city, String platform) {}

    public record Segment(Long categoryId, String interest, int activeDays, String city, String platform) {}

    public record Audience(long matched, long optedIn, long reachable, int capDays) {}

    public record Campaign(long id, String title, String body, String linkPath, Long categoryId, String categoryName,
                           String interest, int activeDays, String city, String platform, String status,
                           String scheduledAt, String testedAt, String sentAt, Integer recipientCount,
                           String createdAt, long revision) {}

    public record TestResult(Campaign campaign, boolean pushEnabled, boolean hasDevice) {}

    // ----------------------------------------------------------------- validation

    static Segment segment(Draft d) {
        Long category = d.categoryId() == null || d.categoryId() <= 0 ? null : d.categoryId();
        String interest = upper(d.interest(), "ANY");
        if (!INTERESTS.contains(interest)) throw new IllegalArgumentException("Invalid interest");
        int days = d.activeDays() == null ? 90 : d.activeDays();
        if (days < 1 || days > 365) throw new IllegalArgumentException("Active days must be 1-365");
        String city = d.city() == null || d.city().isBlank() ? null : d.city().trim();
        if (city != null && city.length() > 100) throw new IllegalArgumentException("City too long");
        String platform = upper(d.platform(), "ALL");
        if (!PLATFORMS.contains(platform)) throw new IllegalArgumentException("Invalid platform");
        return new Segment(category, category == null ? "ANY" : interest, days, city, platform);
    }

    static String text(String value, int max, String name) {
        String clean = value == null ? "" : CONTROL.matcher(value).replaceAll("").trim();
        if (clean.isEmpty() || clean.length() > max) throw new IllegalArgumentException(name + " must be 1-" + max + " characters");
        return clean;
    }

    /** Only paths on myomniaapp.com ("/produkt/7", "/kategori/3?attr.make=BMW"): no external or protocol-relative links. */
    static String link(String value) {
        if (value == null || value.isBlank()) return null;
        String path = value.trim();
        if (path.length() > 255 || !LINK.matcher(path).matches()) throw new IllegalArgumentException("Link must be a path on the site, e.g. /kategori/3");
        return path;
    }

    private static String upper(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    // ----------------------------------------------------------------- audience

    /** WHERE clause over {@code users u} for the segment (no push settings involved). */
    private String segmentWhere(Segment s, List<Object> args) {
        StringBuilder sql = new StringBuilder("u.deleted_at IS NULL AND u.status='ACTIVE'");
        if (s.categoryId() != null) {
            String tree = "(SELECT c.id FROM categories c WHERE c.id=? OR c.parent_id=?)";
            String viewed = "EXISTS (SELECT 1 FROM user_category_views v WHERE v.user_id=u.id AND v.category_id IN " + tree
                    + " AND v.last_viewed_at>=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL ? DAY))";
            String favorited = "EXISTS (SELECT 1 FROM favorites f JOIN products p ON p.id=f.product_id WHERE f.user_id=u.id AND p.category_id IN " + tree + ")";
            switch (s.interest()) {
                case "VIEWED" -> { sql.append(" AND ").append(viewed); args.add(s.categoryId()); args.add(s.categoryId()); args.add(s.activeDays()); }
                case "FAVORITED" -> { sql.append(" AND ").append(favorited); args.add(s.categoryId()); args.add(s.categoryId()); }
                default -> {
                    sql.append(" AND (").append(viewed).append(" OR ").append(favorited).append(')');
                    args.add(s.categoryId()); args.add(s.categoryId()); args.add(s.activeDays());
                    args.add(s.categoryId()); args.add(s.categoryId());
                }
            }
        }
        if (s.city() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM addresses a WHERE a.user_id=u.id AND LOWER(TRIM(a.city))=LOWER(?))");
            args.add(s.city());
        }
        if (s.platform().equals("ANDROID")) {
            // Push devices are Android installations (FCM) today; the web has no push.
            sql.append(" AND EXISTS (SELECT 1 FROM push_devices d WHERE d.user_id=u.id)");
        }
        return sql.toString();
    }

    private static final String OPTED_IN = " AND EXISTS (SELECT 1 FROM push_preferences pp WHERE pp.user_id=u.id AND pp.enabled=TRUE AND pp.offers_enabled=TRUE)"
            + " AND EXISTS (SELECT 1 FROM push_devices d2 WHERE d2.user_id=u.id AND d2.enabled=TRUE AND d2.last_seen>=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 45 DAY))";

    private String capped(List<Object> args) {
        args.add(capDays);
        return " AND NOT EXISTS (SELECT 1 FROM push_campaign_recipients r WHERE r.user_id=u.id AND r.sent_at>=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL ? DAY))";
    }

    private long count(String where, List<Object> args) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM users u WHERE " + where, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    @Transactional(readOnly = true)
    public Audience audience(Draft draft) {
        current.requirePlatformAdmin();
        Segment s = segment(draft);
        requireCategory(s.categoryId());
        return audience(s);
    }

    Audience audience(Segment s) {
        List<Object> a = new ArrayList<>();
        String where = segmentWhere(s, a);
        long matched = count(where, a);
        long optedIn = count(where + OPTED_IN, new ArrayList<>(a));
        List<Object> c = new ArrayList<>(a);
        long reachable = count(where + OPTED_IN + capped(c), c);
        return new Audience(matched, optedIn, reachable, capDays);
    }

    List<Long> recipients(Segment s) {
        List<Object> args = new ArrayList<>();
        String where = segmentWhere(s, args) + OPTED_IN + capped(args);
        args.add(MAX_RECIPIENTS);
        return jdbc.queryForList("SELECT u.id FROM users u WHERE " + where + " ORDER BY u.id LIMIT ?", Long.class, args.toArray());
    }

    // ----------------------------------------------------------------- CRUD

    private static final String SELECT = "SELECT c.*, cat.name AS category_name FROM push_campaigns c LEFT JOIN categories cat ON cat.id=c.category_id";

    private static String iso(Timestamp t) {
        return t == null ? null : t.toLocalDateTime().toInstant(ZoneOffset.UTC).toString();
    }

    private static final RowMapper<Campaign> MAPPER = (r, i) -> new Campaign(r.getLong("id"), r.getString("title"), r.getString("body"),
            r.getString("link_path"), r.getObject("category_id", Long.class), r.getString("category_name"), r.getString("interest"),
            r.getInt("active_days"), r.getString("city"), r.getString("platform"), r.getString("status"), iso(r.getTimestamp("scheduled_at")),
            iso(r.getTimestamp("tested_at")), iso(r.getTimestamp("sent_at")), r.getObject("recipient_count", Integer.class),
            iso(r.getTimestamp("created_at")), r.getLong("revision"));

    private void requireCategory(Long id) {
        if (id != null && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM categories WHERE id=?)", Boolean.class, id))) {
            throw new IllegalArgumentException("Unknown category");
        }
    }

    private Campaign find(long id, boolean lock) {
        List<Campaign> rows = jdbc.query(SELECT + " WHERE c.id=?" + (lock ? " FOR UPDATE" : ""), MAPPER, id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Campaign not found");
        return rows.get(0);
    }

    @Transactional(readOnly = true)
    public List<Campaign> list() {
        current.requirePlatformAdmin();
        return jdbc.query(SELECT + " ORDER BY c.id DESC LIMIT 50", MAPPER);
    }

    @Transactional(readOnly = true)
    public Campaign get(long id) {
        current.requirePlatformAdmin();
        return find(id, false);
    }

    @Transactional
    public Campaign create(Draft d) {
        User admin = admin();
        Segment s = segment(d);
        requireCategory(s.categoryId());
        jdbc.update("INSERT INTO push_campaigns(title,body,link_path,category_id,interest,active_days,city,platform,status,created_by,created_at,updated_at)"
                        + " VALUES(?,?,?,?,?,?,?,?,'DRAFT',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                text(d.title(), 65, "Title"), text(d.body(), 180, "Body"), link(d.linkPath()), s.categoryId(), s.interest(),
                s.activeDays(), s.city(), s.platform(), admin.getId());
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return find(id == null ? 0 : id, false);
    }

    /** Edits are allowed before scheduling; they always return the campaign to DRAFT (re-test needed). */
    @Transactional
    public Campaign update(long id, Draft d, long revision) {
        admin();
        Segment s = segment(d);
        requireCategory(s.categoryId());
        Campaign c = find(id, true);
        if (!Set.of("DRAFT", "TESTED").contains(c.status())) throw new ConflictException("Only draft campaigns can be edited");
        int n = jdbc.update("UPDATE push_campaigns SET title=?,body=?,link_path=?,category_id=?,interest=?,active_days=?,city=?,platform=?,"
                        + "status='DRAFT',tested_at=NULL,tested_by=NULL,updated_at=UTC_TIMESTAMP(6),revision=revision+1 WHERE id=? AND revision=?",
                text(d.title(), 65, "Title"), text(d.body(), 180, "Body"), link(d.linkPath()), s.categoryId(), s.interest(),
                s.activeDays(), s.city(), s.platform(), id, revision);
        if (n != 1) throw new ConflictException("Campaign changed. Reload before saving.");
        return find(id, false);
    }

    /** The mandatory test: one notice to the calling admin only, never recorded against the frequency cap. */
    @Transactional
    public TestResult test(long id) {
        User admin = admin();
        Campaign c = find(id, true);
        if (!Set.of("DRAFT", "TESTED").contains(c.status())) throw new ConflictException("Campaign is already scheduled or sent");
        String key = "campaign-test:" + id + ":" + System.nanoTime();
        jdbc.update("INSERT INTO notification_inbox(recipient_id,event_key,kind,category,target_type,target_id,label,created_at)"
                + " VALUES(?,?,'CAMPAIGN_TEST','SYSTEM','CAMPAIGN',?,?,UTC_TIMESTAMP(6))", admin.getId(), key, id, c.title());
        jdbc.update("INSERT INTO push_outbox(notification_id,available_at) SELECT id,UTC_TIMESTAMP(6) FROM notification_inbox WHERE recipient_id=? AND event_key=?",
                admin.getId(), key);
        jdbc.update("UPDATE push_campaigns SET status='TESTED',tested_at=UTC_TIMESTAMP(6),tested_by=?,revision=revision+1 WHERE id=?", admin.getId(), id);
        boolean enabled = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM push_preferences WHERE user_id=? AND enabled=TRUE)", Boolean.class, admin.getId()));
        boolean device = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM push_devices WHERE user_id=? AND enabled=TRUE AND last_seen>=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 45 DAY))",
                Boolean.class, admin.getId()));
        return new TestResult(find(id, false), enabled, device);
    }

    /** Schedules a tested campaign; {@code at == null} sends at the next scheduler tick. */
    @Transactional
    public Campaign schedule(long id, Instant at, long revision) {
        admin();
        Campaign c = find(id, true);
        if (!c.status().equals("TESTED")) throw new ConflictException("Send a test to yourself before scheduling");
        if (c.revision() != revision) throw new ConflictException("Campaign changed. Reload before scheduling.");
        Instant now = Instant.now();
        Instant when = at == null ? now : at;
        if (when.isBefore(now.minus(Duration.ofMinutes(5))) || when.isAfter(now.plus(Duration.ofDays(60)))) {
            throw new IllegalArgumentException("Schedule time must be within the next 60 days");
        }
        jdbc.update("UPDATE push_campaigns SET status='SCHEDULED',scheduled_at=?,updated_at=UTC_TIMESTAMP(6),revision=revision+1 WHERE id=?",
                Timestamp.valueOf(when.atZone(ZoneOffset.UTC).toLocalDateTime()), id);
        return find(id, false);
    }

    @Transactional
    public Campaign cancel(long id) {
        admin();
        Campaign c = find(id, true);
        if (!Set.of("DRAFT", "TESTED", "SCHEDULED").contains(c.status())) throw new ConflictException("Campaign can no longer be cancelled");
        jdbc.update("UPDATE push_campaigns SET status='CANCELLED',updated_at=UTC_TIMESTAMP(6),revision=revision+1 WHERE id=?", id);
        return find(id, false);
    }

    private User admin() {
        current.requirePlatformAdmin();
        User u = current.requireCurrentUser();
        if (!current.hasPlatformAdminAccess(u)) throw new AccessDeniedException("Platform admin required");
        return u;
    }

    // ----------------------------------------------------------------- sending

    @Scheduled(fixedDelayString = "${omnia.campaigns.poll-ms:60000}", initialDelay = 30000)
    public void sendDue() {
        try {
            for (int i = 0; i < 5; i++) {
                Boolean sent = tx.execute(status -> sendOne());
                if (!Boolean.TRUE.equals(sent)) return;
            }
        } catch (Exception e) {
            LOG.warn("Campaign sending deferred ({})", e.getClass().getSimpleName());
        }
    }

    /** Sends one due campaign inside the caller's transaction. Returns false when none is due. */
    boolean sendOne() {
        List<Long> due = jdbc.queryForList("SELECT id FROM push_campaigns WHERE status='SCHEDULED' AND scheduled_at<=UTC_TIMESTAMP(6)"
                + " ORDER BY scheduled_at, id LIMIT 1 FOR UPDATE SKIP LOCKED", Long.class);
        if (due.isEmpty()) return false;
        long id = due.get(0);
        Campaign c = find(id, false);
        jdbc.update("UPDATE push_campaigns SET status='SENDING',updated_at=UTC_TIMESTAMP(6) WHERE id=?", id);
        Segment s = new Segment(c.categoryId(), c.interest(), c.activeDays(), c.city(), c.platform());
        List<Long> users = recipients(s);
        String key = "campaign:" + id;
        for (long user : users) {
            jdbc.update("INSERT INTO notification_inbox(recipient_id,event_key,kind,category,target_type,target_id,label,created_at)"
                    + " VALUES(?,?,'CAMPAIGN','SYSTEM','CAMPAIGN',?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE id=id", user, key, id, c.title());
            // priority 1: order/stock notices (priority 0) are always dispatched before campaign traffic.
            jdbc.update("INSERT INTO push_outbox(notification_id,available_at,priority) SELECT id,UTC_TIMESTAMP(6),1 FROM notification_inbox"
                    + " WHERE recipient_id=? AND event_key=? ON DUPLICATE KEY UPDATE notification_id=notification_id", user, key);
            jdbc.update("INSERT INTO push_campaign_recipients(campaign_id,user_id,sent_at) VALUES(?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE user_id=user_id", id, user);
        }
        jdbc.update("UPDATE push_campaigns SET status='SENT',sent_at=UTC_TIMESTAMP(6),recipient_count=?,updated_at=UTC_TIMESTAMP(6),revision=revision+1 WHERE id=?",
                users.size(), id);
        LOG.info("Campaign {} queued for {} recipients", id, users.size());
        return true;
    }
}
