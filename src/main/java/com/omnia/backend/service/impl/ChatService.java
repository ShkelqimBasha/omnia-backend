package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.common.exception.TooManyRequestsException;
import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.UserStatus;
import com.omnia.backend.mapper.ProductMapper;
import com.omnia.backend.security.service.CurrentUserService;
import jakarta.persistence.EntityManager;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chat between a buyer and the company selling a product, and between Omnia and one user.
 *
 * <p>PRODUCT: one conversation per (buyer, company, product). Only the buyer, the company's
 * active owners/admins (the same people who manage its orders) and platform admins can read it.
 * Platform admins also write in it as "Omnia" (side ADMIN) but never move its read markers.
 *
 * <p>SUPPORT: one conversation per user with Omnia, opened by a platform admin. No company or product;
 * the user is the BUYER side and the admins share the seller read marker.
 */
@Service
public class ChatService {

    static final int MAX_MESSAGES_PER_MINUTE = 10;
    static final int MAX_MESSAGES_PER_HOUR = 120;
    static final int MAX_NEW_CONVERSATIONS_PER_DAY = 20;
    static final int PAGE = 30;
    static final int MAX_NEW = 100;

    public enum Side { BUYER, SELLER, ADMIN }

    static final String PRODUCT = "PRODUCT";
    static final String SUPPORT = "SUPPORT";
    static final String OMNIA = "Omnia";

    public record ConversationView(
            long id,
            long productId,
            String productName,
            String productImage,
            long organizationId,
            String organizationName,
            String buyerName,
            String lastMessage,
            String lastMessageAt,
            String lastSenderSide,
            Long lastMessageId,
            long unread,
            String mySide,
            boolean canSend,
            long otherLastReadId,
            String createdAt,
            String kind,
            long buyerId) {
    }

    public record Message(long id, String side, String body, String createdAt) {
    }

    public record Messages(List<Message> items, boolean hasMore, long otherLastReadId, long unread) {
    }

    public record Unread(long buyer, long seller, long total) {
    }

    private final JdbcTemplate jdbc;
    private final CurrentUserService current;
    private final NotificationWriter notifications;
    private final EntityManager em;

    public ChatService(JdbcTemplate jdbc, CurrentUserService current,
                       NotificationWriter notifications, EntityManager em) {
        this.jdbc = jdbc;
        this.current = current;
        this.notifications = notifications;
        this.em = em;
    }

    /* ------------------------------------------------------------------ start */

    /** "Shkruaj shitësit": finds or opens the buyer's conversation about a product and sends the first message. */
    @Transactional
    public ConversationView start(long productId, String body) {
        User buyer = actor();
        String text = ChatText.clean(body);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT p.id, p.name, p.status, p.organization_id, g.status AS org_status "
                        + "FROM products p LEFT JOIN organizations g ON g.id = p.organization_id WHERE p.id = ?",
                productId);
        if (rows.isEmpty() || !"ACTIVE".equals(rows.get(0).get("status"))) {
            throw new ResourceNotFoundException("Product not found");
        }
        Map<String, Object> product = rows.get(0);
        if (product.get("organization_id") == null || !"ACTIVE".equals(product.get("org_status"))) {
            throw new IllegalArgumentException("Ky produkt nuk ka shitës që pranon mesazhe.");
        }
        long organizationId = ((Number) product.get("organization_id")).longValue();
        if (isMember(buyer.getId(), organizationId)) {
            throw new IllegalArgumentException("Nuk mund t'i shkruani kompanisë suaj.");
        }
        Long existing = findId(buyer.getId(), organizationId, productId);
        if (existing == null) {
            Integer today = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM chat_conversations WHERE buyer_id = ? "
                            + "AND created_at >= DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 DAY)",
                    Integer.class, buyer.getId());
            if (today != null && today >= MAX_NEW_CONVERSATIONS_PER_DAY) {
                throw new TooManyRequestsException("Keni nisur shumë biseda sot. Provoni përsëri nesër.");
            }
            jdbc.update("INSERT INTO chat_conversations (buyer_id, organization_id, product_id, product_name, created_at) "
                            + "VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE id = id",
                    buyer.getId(), organizationId, productId, limit((String) product.get("name"), 255));
            existing = findId(buyer.getId(), organizationId, productId);
        }
        Access access = new Access(load(existing), Side.BUYER, buyer);
        append(access, text);
        return view(existing, Side.BUYER);
    }

    /** The buyer's conversation about a product, if one exists (the product page uses it to reopen the chat). */
    @Transactional(readOnly = true)
    public ConversationView forProduct(long productId) {
        User buyer = actor();
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM chat_conversations WHERE buyer_id = ? AND product_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, buyer.getId(), productId);
        if (ids.isEmpty()) {
            throw new ResourceNotFoundException("Conversation not found");
        }
        return view(ids.get(0), Side.BUYER);
    }

    /* ------------------------------------------------------------------ lists */

    @Transactional(readOnly = true)
    public PagedResponse<ConversationView> mine(int page, int size) {
        User buyer = actor();
        return list("c.buyer_id = ?", List.of(buyer.getId()), Side.BUYER, page, size);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ConversationView> forOrganization(long organizationId, int page, int size) {
        User user = actor();
        Side side;
        if (isManager(user.getId(), organizationId)) {
            side = Side.SELLER;
        } else if (current.hasPlatformAdminAccess(user)) {
            side = Side.ADMIN;
        } else {
            throw new AccessDeniedException("Duhet të jeni pronar ose administrator i kompanisë.");
        }
        return list("c.organization_id = ?", List.of(organizationId), side, page, size);
    }

    /**
     * Omnia's inbox: every conversation with messages, newest activity first. {@code kind} is ALL, PRODUCT or SUPPORT;
     * {@code q} matches the product, the company, or the user's name or e-mail (case-insensitive).
     */
    @Transactional(readOnly = true)
    public PagedResponse<ConversationView> adminList(int page, int size, String kind, String q) {
        requireAdmin();
        String k = kind == null || kind.isBlank() ? "ALL" : kind.strip().toUpperCase(Locale.ROOT);
        StringBuilder where = new StringBuilder("1 = 1");
        List<Object> args = new ArrayList<>();
        switch (k) {
            case "ALL" -> { }
            case PRODUCT, SUPPORT -> {
                where.append(" AND c.kind = ?");
                args.add(k);
            }
            default -> throw new IllegalArgumentException("Invalid conversation kind");
        }
        if (q != null && !q.isBlank()) {
            String term = "%" + likeEscape(q.strip().toLowerCase(Locale.ROOT)) + "%";
            where.append(" AND (LOWER(c.product_name) LIKE ?"
                    + " OR EXISTS (SELECT 1 FROM organizations g WHERE g.id = c.organization_id AND LOWER(g.name) LIKE ?)"
                    + " OR EXISTS (SELECT 1 FROM users u WHERE u.id = c.buyer_id AND (LOWER(u.first_name) LIKE ?"
                    + " OR LOWER(u.last_name) LIKE ? OR LOWER(u.email) LIKE ?"
                    + " OR LOWER(CONCAT_WS(' ', u.first_name, u.last_name)) LIKE ?)))");
            for (int i = 0; i < 6; i++) args.add(term);
        }
        return list(where.toString(), args, Side.ADMIN, page, size);
    }

    /**
     * Omnia writes to a user: finds or opens that user's single SUPPORT conversation and appends the message as ADMIN.
     * Not limited by the "new conversations per day" rule; the per-sender message limits still apply.
     */
    @Transactional
    public ConversationView adminStart(Long userId, String body) {
        User admin = requireAdmin();
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        String text = ChatText.clean(body);
        // Locking the user row serializes find-or-create, so each user has one SUPPORT conversation.
        List<String> status = jdbc.queryForList(
                "SELECT status FROM users WHERE id = ? AND deleted_at IS NULL FOR UPDATE", String.class, userId);
        if (status.isEmpty() || !UserStatus.ACTIVE.name().equals(status.get(0))) {
            throw new ResourceNotFoundException("User not found");
        }
        if (userId.longValue() == admin.getId()) {
            throw new IllegalArgumentException("Nuk mund t'i shkruani vetes.");
        }
        Long id = supportId(userId);
        if (id == null) {
            jdbc.update("INSERT INTO chat_conversations (kind, buyer_id, organization_id, product_id, product_name, created_at) "
                    + "VALUES ('SUPPORT', ?, NULL, NULL, ?, UTC_TIMESTAMP(6))", userId, OMNIA);
            id = supportId(userId);
        }
        append(new Access(load(id), Side.ADMIN, admin), text);
        return view(id, Side.ADMIN);
    }

    /** Conversations with unread messages, for the badges in the header and the company panel. */
    @Transactional(readOnly = true)
    public Unread unread() {
        User user = actor();
        Long buyer = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_conversations c WHERE c.buyer_id = ? "
                        + "AND c.last_sender_side <> 'BUYER' AND c.last_message_id > c.buyer_last_read_id",
                Long.class, user.getId());
        Long seller = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_conversations c WHERE " + managerOf("c.organization_id")
                        + " AND c.last_sender_side <> 'SELLER' AND c.last_message_id > c.seller_last_read_id",
                Long.class, user.getId());
        long b = buyer == null ? 0 : buyer;
        long s = seller == null ? 0 : seller;
        return new Unread(b, s, b + s);
    }

    /* -------------------------------------------------------------- one chat */

    @Transactional(readOnly = true)
    public ConversationView get(long conversationId) {
        Access access = access(conversationId);
        return view(conversationId, access.side());
    }

    /**
     * Messages of a conversation. With {@code after} (polling) the messages newer than that id, oldest first;
     * with {@code before} the older page; with neither the latest page. Always oldest first.
     */
    @Transactional(readOnly = true)
    public Messages messages(long conversationId, Long after, Long before) {
        Access access = access(conversationId);
        if ((after != null && after < 0) || (before != null && before < 1)) {
            throw new IllegalArgumentException("Invalid message cursor");
        }
        List<Message> items;
        boolean more;
        if (after != null) {
            items = jdbc.query("SELECT * FROM chat_messages WHERE conversation_id = ? AND id > ? ORDER BY id LIMIT ?",
                    MESSAGE, conversationId, after, MAX_NEW + 1);
            more = items.size() > MAX_NEW;
            if (more) items = new ArrayList<>(items.subList(0, MAX_NEW));
        } else {
            items = before == null
                    ? jdbc.query("SELECT * FROM chat_messages WHERE conversation_id = ? ORDER BY id DESC LIMIT ?",
                    MESSAGE, conversationId, PAGE + 1)
                    : jdbc.query("SELECT * FROM chat_messages WHERE conversation_id = ? AND id < ? ORDER BY id DESC LIMIT ?",
                    MESSAGE, conversationId, before, PAGE + 1);
            more = items.size() > PAGE;
            items = new ArrayList<>(items.subList(0, Math.min(PAGE, items.size())));
            Collections.reverse(items);
        }
        Row c = access.row();
        return new Messages(List.copyOf(items), more, otherLastRead(c, access.side()), unreadFor(c, access.side()));
    }

    /** A new message from the buyer, the company, or Omnia (a platform admin who is neither: side ADMIN). */
    @Transactional
    public Message send(long conversationId, String body) {
        Access access = access(conversationId);
        return append(access, ChatText.clean(body));
    }

    /** Read marker: everything up to {@code throughId} has been read by the caller's side. */
    @Transactional
    public Map<String, Object> read(long conversationId, long throughId) {
        if (throughId <= 0) {
            throw new IllegalArgumentException("Invalid read marker");
        }
        Access access = access(conversationId);
        String column = readColumn(access.row(), access.side());
        if (column != null) {
            jdbc.update("UPDATE chat_conversations SET " + column + " = GREATEST(" + column
                            + ", LEAST(?, COALESCE(last_message_id, 0))) WHERE id = ?",
                    throughId, conversationId);
        }
        jdbc.update("UPDATE notification_inbox SET read_at = COALESCE(read_at, UTC_TIMESTAMP(6)) "
                        + "WHERE recipient_id = ? AND target_type = 'CHAT' AND target_id = ?",
                access.user().getId(), conversationId);
        Row c = load(conversationId);
        return Map.of("unread", unreadFor(c, access.side()));
    }

    /* ---------------------------------------------------------------- internals */

    private Message append(Access access, String text) {
        long userId = access.user().getId();
        Integer minute = jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE sender_id = ? "
                + "AND created_at >= DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 MINUTE)", Integer.class, userId);
        Integer hour = jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE sender_id = ? "
                + "AND created_at >= DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 HOUR)", Integer.class, userId);
        if ((minute != null && minute >= MAX_MESSAGES_PER_MINUTE) || (hour != null && hour >= MAX_MESSAGES_PER_HOUR)) {
            throw new TooManyRequestsException("Po dërgoni mesazhe shumë shpejt. Prisni pak dhe provoni përsëri.");
        }
        Row c = access.row();
        String side = access.side().name();
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO chat_messages (conversation_id, sender_id, sender_side, body, created_at) "
                            + "VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6))", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, c.id());
            ps.setLong(2, userId);
            ps.setString(3, side);
            ps.setString(4, text);
            return ps;
        }, keys);
        long messageId = keys.getKey().longValue();
        String ownRead = readColumn(c, access.side());
        List<Object> args = new ArrayList<>(List.of(messageId, ChatText.preview(text), side));
        String marker = "";
        if (ownRead != null) {
            marker = ", " + ownRead + " = GREATEST(" + ownRead + ", ?)";
            args.add(messageId);
        }
        args.add(c.id());
        args.add(messageId);
        jdbc.update("UPDATE chat_conversations SET last_message_id = ?, last_message_at = UTC_TIMESTAMP(6), "
                        + "last_message_preview = ?, last_sender_side = ?" + marker
                        + " WHERE id = ? AND (last_message_id IS NULL OR last_message_id < ?)", args.toArray());
        if (c.support()) {
            if (access.side() == Side.ADMIN) {
                notifications.chatFromOmnia(c.id(), messageId, null, OMNIA, c.buyerId(), userId);
            } else {
                notifications.chatToOmnia(c.id(), messageId, userId);
            }
        } else if (access.side() == Side.ADMIN) {
            notifications.chatFromOmnia(c.id(), messageId, c.organizationId(), c.productName(), c.buyerId(), userId);
        } else {
            notifications.chat(c.id(), messageId, c.organizationId(), c.productName(),
                    access.side() == Side.BUYER, c.buyerId(), userId);
        }
        return jdbc.queryForObject("SELECT * FROM chat_messages WHERE id = ?", MESSAGE, messageId);
    }

    private Access access(long conversationId) {
        User user = actor();
        Row c = load(conversationId);
        if (c.buyerId() == user.getId()) {
            return new Access(c, Side.BUYER, user);
        }
        if (!c.support() && isManager(user.getId(), c.organizationId())) {
            return new Access(c, Side.SELLER, user);
        }
        if (current.hasPlatformAdminAccess(user)) {
            return new Access(c, Side.ADMIN, user);
        }
        // Same answer as a missing conversation: never reveal that it exists.
        throw new ResourceNotFoundException("Conversation not found");
    }

    private User requireAdmin() {
        User user = actor();
        if (!current.hasPlatformAdminAccess(user)) {
            throw new AccessDeniedException("Platform administrator access is required");
        }
        return user;
    }

    private Long supportId(long userId) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM chat_conversations WHERE buyer_id = ? AND kind = 'SUPPORT' "
                + "ORDER BY id LIMIT 1", Long.class, userId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * The read marker a side moves: the buyer's, or the seller's (shared by the company's managers, and by
     * the admins in a SUPPORT chat). Admins never move the markers of a PRODUCT chat.
     */
    private static String readColumn(Row c, Side side) {
        return switch (side) {
            case BUYER -> "buyer_last_read_id";
            case SELLER -> "seller_last_read_id";
            case ADMIN -> c.support() ? "seller_last_read_id" : null;
        };
    }

    private static String likeEscape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private User actor() {
        User user = current.requireCurrentUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedException("Active account required");
        }
        return user;
    }

    private Row load(long id) {
        try {
            return jdbc.queryForObject("SELECT * FROM chat_conversations WHERE id = ?", ROW, id);
        } catch (EmptyResultDataAccessException missing) {
            throw new ResourceNotFoundException("Conversation not found");
        }
    }

    private Long findId(long buyerId, long organizationId, long productId) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM chat_conversations WHERE buyer_id = ? "
                + "AND organization_id = ? AND product_id = ?", Long.class, buyerId, organizationId, productId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** Owners and admins of an active company: the members who answer chats (and manage orders). */
    private static String managerOf(String organizationColumn) {
        return "EXISTS (SELECT 1 FROM organization_members m JOIN organizations g ON g.id = m.organization_id "
                + "WHERE m.user_id = ? AND m.organization_id = " + organizationColumn + " AND m.status = 'ACTIVE' "
                + "AND m.membership_role IN ('OWNER', 'ADMIN') AND g.status = 'ACTIVE')";
    }

    private boolean isManager(long userId, long organizationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT " + managerOf("?"), Boolean.class, userId, organizationId));
    }

    private boolean isMember(long userId, long organizationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM organization_members "
                + "WHERE user_id = ? AND organization_id = ? AND status = 'ACTIVE')", Boolean.class, userId, organizationId));
    }

    private PagedResponse<ConversationView> list(String where, List<Object> args, Side side, int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.max(1, Math.min(size, 50));
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM chat_conversations c WHERE " + where
                + " AND c.last_message_id IS NOT NULL", Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(s);
        pageArgs.add((long) p * s);
        List<Row> rows = jdbc.query("SELECT c.* FROM chat_conversations c WHERE " + where
                + " AND c.last_message_id IS NOT NULL ORDER BY c.last_message_id DESC LIMIT ? OFFSET ?", ROW, pageArgs.toArray());
        Map<Long, String> images = new HashMap<>();
        List<ConversationView> content = new ArrayList<>();
        for (Row c : rows) {
            content.add(view(c, side, images));
        }
        long count = total == null ? 0 : total;
        int pages = (int) ((count + s - 1) / s);
        return new PagedResponse<>(content, p, s, count, pages, p == 0, p + 1 >= pages, p + 1 < pages, p > 0);
    }

    private ConversationView view(long id, Side side) {
        return view(load(id), side, new HashMap<>());
    }

    private ConversationView view(Row c, Side side, Map<Long, String> images) {
        Map<String, Object> buyer = jdbc.queryForMap("SELECT first_name, last_name, email FROM users WHERE id = ?", c.buyerId());
        String first = (String) buyer.get("first_name");
        String last = (String) buyer.get("last_name");
        String name = side == Side.ADMIN ? fullName(first, last, (String) buyer.get("email")) : buyerName(first, last);
        String productName = OMNIA;
        String organizationName = OMNIA;
        String image = null;
        if (!c.support()) {
            productName = c.productName();
            organizationName = jdbc.queryForObject("SELECT name FROM organizations WHERE id = ?", String.class, c.organizationId());
            image = images.computeIfAbsent(c.productId(), id -> {
                Product product = em.find(Product.class, id);
                return product == null ? null : ProductMapper.primaryImageUrl(product);
            });
        }
        return new ConversationView(c.id(), c.productId(), productName, image, c.organizationId(), organizationName,
                name, c.preview(), c.lastMessageAt(), c.lastSenderSide(), c.lastMessageId(),
                unreadFor(c, side), side.name(), true, otherLastRead(c, side), c.createdAt(), c.kind(), c.buyerId());
    }

    /** Omnia's admins see the user's full name (or e-mail when the account has no name). */
    static String fullName(String first, String last, String email) {
        String name = ((first == null ? "" : first.strip()) + " " + (last == null ? "" : last.strip())).strip();
        if (!name.isEmpty()) return name;
        return email == null || email.isBlank() ? "Blerës" : email.strip();
    }

    /** Sellers see the buyer's first name and last initial, never their contact details. */
    static String buyerName(String first, String last) {
        String f = first == null ? "" : first.strip();
        String l = last == null || last.isBlank() ? "" : " " + last.strip().substring(0, 1).toUpperCase() + ".";
        String name = (f + l).strip();
        return name.isEmpty() ? "Blerës" : name;
    }

    /** Messages from any other side after the caller's read marker (an ADMIN message is unread for buyer and seller). */
    private long unreadFor(Row c, Side side) {
        String column = readColumn(c, side);
        if (column == null) return 0;
        long lastRead = side == Side.BUYER ? c.buyerLastReadId() : c.sellerLastReadId();
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE conversation_id = ? "
                + "AND sender_side <> ? AND id > ?", Long.class, c.id(), side.name(), lastRead);
        return count == null ? 0 : count;
    }

    private static long otherLastRead(Row c, Side side) {
        return switch (side) {
            case BUYER -> c.sellerLastReadId();
            case SELLER -> c.buyerLastReadId();
            case ADMIN -> c.support() ? c.buyerLastReadId() : 0;
        };
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String instant(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime().toInstant(ZoneOffset.UTC).toString();
    }

    /** organizationId and productId are 0 in a SUPPORT conversation (NULL in the table). */
    private record Row(long id, long buyerId, long organizationId, long productId, String productName,
                       Long lastMessageId, String lastMessageAt, String preview, String lastSenderSide,
                       long buyerLastReadId, long sellerLastReadId, String createdAt, String kind) {
        boolean support() {
            return SUPPORT.equals(kind);
        }
    }

    private record Access(Row row, Side side, User user) {
    }

    private static final RowMapper<Row> ROW = (ResultSet r, int i) -> new Row(
            r.getLong("id"), r.getLong("buyer_id"), r.getLong("organization_id"), r.getLong("product_id"),
            r.getString("product_name"), r.getObject("last_message_id", Long.class),
            instant(r.getTimestamp("last_message_at")), r.getString("last_message_preview"),
            r.getString("last_sender_side"), r.getLong("buyer_last_read_id"), r.getLong("seller_last_read_id"),
            instant(r.getTimestamp("created_at")), r.getString("kind"));

    private static final RowMapper<Message> MESSAGE = (ResultSet r, int i) -> message(r);

    private static Message message(ResultSet r) throws SQLException {
        return new Message(r.getLong("id"), r.getString("sender_side"), r.getString("body"),
                instant(r.getTimestamp("created_at")));
    }
}
