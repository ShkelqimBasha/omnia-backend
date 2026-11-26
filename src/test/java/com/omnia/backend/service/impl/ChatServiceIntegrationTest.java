package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.common.exception.TooManyRequestsException;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.*;
import com.omnia.backend.integration.AbstractIntegrationTest;
import com.omnia.backend.security.service.CurrentUserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Transactional
class ChatServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationWriter writer;

    private CurrentUserService current;
    private ChatService chat;
    private NotificationInbox inbox;
    private User buyer, stranger, owner, staff, admin;
    private Organization org;
    private Product product;

    private Role role(String name) {
        var rows = em.createQuery("select r from Role r where r.name=:n", Role.class).setParameter("n", name).getResultList();
        if (!rows.isEmpty()) return rows.get(0);
        Role r = Role.builder().name(name).build();
        em.persist(r);
        return r;
    }

    private User user(String role, String first, String last) {
        String id = "chat" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        User u = User.builder().role(role(role)).username(id).email(id + "@example.test").firstName(first).lastName(last)
                .passwordHash("unused").emailVerified(true).status(UserStatus.ACTIVE).build();
        em.persist(u);
        return u;
    }

    private void member(User u, OrganizationMemberRole r) {
        em.persist(OrganizationMember.builder().organization(org).user(u).membershipRole(r)
                .status(OrganizationMemberStatus.ACTIVE).build());
    }

    @BeforeEach
    void setUp() {
        current = mock(CurrentUserService.class);
        chat = new ChatService(jdbc, current, writer, em);
        inbox = new NotificationInbox(jdbc, current, em);
        buyer = user("USER", "Ana", "kola");
        stranger = user("USER", "Tjetër", null);
        owner = user("USER", "Pronar", "Test");
        staff = user("USER", "Staf", "Test");
        admin = user("SUPER_ADMIN", "Admin", "Test");
        org = Organization.builder().name("Chat " + UUID.randomUUID()).slug("chat-" + UUID.randomUUID())
                .status(OrganizationStatus.ACTIVE).build();
        em.persist(org);
        member(owner, OrganizationMemberRole.OWNER);
        member(staff, OrganizationMemberRole.STAFF);
        Category cat = Category.builder().name("Chat fixtures " + UUID.randomUUID()).status(CategoryStatus.ACTIVE).build();
        em.persist(cat);
        product = Product.builder().organization(org).category(cat).name("Golf 7").price(BigDecimal.TEN).stock(1)
                .status(ProductStatus.ACTIVE).build();
        em.persist(product);
        em.flush();
    }

    private void as(User u) {
        when(current.requireCurrentUser()).thenReturn(u);
        when(current.hasPlatformAdminAccess(u)).thenReturn(u.getRole().getName().endsWith("ADMIN"));
    }

    private long notices(User u) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification_inbox WHERE recipient_id=? AND target_type='CHAT'",
                Long.class, u.getId());
    }

    @Test
    void buyerStartsOneConversationPerProductAndOwnerAnswers() {
        as(buyer);
        var first = chat.start(product.getId(), "  Përshëndetje, a është ende në shitje?  ");
        var again = chat.start(product.getId(), "Pyetje e dytë");
        assertEquals(first.id(), again.id(), "one conversation per buyer, company and product");
        assertEquals("BUYER", first.mySide());
        assertEquals(0, again.unread());
        assertEquals(first.id(), chat.forProduct(product.getId()).id());

        as(owner);
        var list = chat.forOrganization(org.getId(), 0, 20);
        assertEquals(1, list.totalElements());
        var seen = list.content().get(0);
        assertEquals("Ana K.", seen.buyerName(), "sellers see a first name and an initial only");
        assertEquals(2, seen.unread());
        assertEquals("Pyetje e dytë", seen.lastMessage());
        assertEquals(1, chat.unread().seller());

        var page = chat.messages(first.id(), null, null);
        assertEquals(List.of("Përshëndetje, a është ende në shitje?", "Pyetje e dytë"),
                page.items().stream().map(ChatService.Message::body).toList());
        assertEquals(0, page.otherLastReadId() - again.lastMessageId(), "the buyer has read their own messages");

        var reply = chat.send(first.id(), "Po, është.");
        assertEquals("SELLER", reply.side());
        assertEquals(0, chat.get(first.id()).unread(), "replying marks the seller side read");

        as(buyer);
        assertEquals(1, chat.unread().buyer());
        var polled = chat.messages(first.id(), page.items().get(1).id(), null);
        assertEquals(1, polled.items().size());
        assertEquals(1, polled.unread());
        assertEquals(0L, chat.read(first.id(), reply.id()).get("unread"));
        assertEquals(0, chat.unread().total());
    }

    @Test
    void onlyParticipantsAndPlatformAdminsCanRead() {
        as(buyer);
        long id = chat.start(product.getId(), "Pyetje").id();

        as(stranger);
        assertThrows(ResourceNotFoundException.class, () -> chat.get(id));
        assertThrows(ResourceNotFoundException.class, () -> chat.messages(id, null, null));
        assertThrows(ResourceNotFoundException.class, () -> chat.send(id, "hi"));
        assertThrows(AccessDeniedException.class, () -> chat.forOrganization(org.getId(), 0, 20));

        as(staff);
        assertThrows(ResourceNotFoundException.class, () -> chat.get(id), "staff members without order rights cannot read");

        as(admin);
        assertEquals("ADMIN", chat.get(id).mySide());
        assertTrue(chat.get(id).canSend(), "Omnia can write in any conversation");
        assertEquals(1, chat.messages(id, null, null).items().size());
        assertEquals("ADMIN", chat.forOrganization(org.getId(), 0, 20).content().get(0).mySide());

        jdbc.update("UPDATE organization_members SET status='INACTIVE' WHERE user_id=?", owner.getId());
        as(owner);
        assertThrows(ResourceNotFoundException.class, () -> chat.get(id), "a removed member loses access");
    }

    @Test
    void newMessagesNotifyTheOtherSideOnceUntilRead() {
        as(buyer);
        long id = chat.start(product.getId(), "Një").id();
        chat.send(id, "Dy");
        assertEquals(1, notices(owner), "one unread notice per chat");
        assertEquals(0, notices(staff));
        assertEquals(0, notices(buyer), "the sender is never notified");
        assertEquals(0, notices(admin));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM push_outbox o JOIN notification_inbox n ON n.id=o.notification_id "
                + "WHERE n.recipient_id=? AND n.target_type='CHAT'", Long.class, owner.getId()));

        as(owner);
        var notice = inbox.list("all", 0).items().get(0);
        assertEquals("CHAT_MESSAGE", notice.kind());
        assertEquals("Golf 7", notice.label());
        assertEquals(id, ((Number) inbox.open(notice.id()).get("conversationId")).longValue());
        long last = chat.messages(id, null, null).items().get(1).id();
        chat.read(id, last);
        assertEquals(0, inbox.list("unread", 0).unread(), "reading the chat clears its notice");

        var reply = chat.send(id, "Përgjigje");
        assertEquals(1, notices(buyer));
        as(buyer);
        assertEquals(1, inbox.list("unread", 0).unread());
        as(stranger);
        assertTrue(inbox.list("all", 0).items().isEmpty());

        as(buyer);
        chat.read(id, reply.id());
        chat.send(id, "Faleminderit");
        assertEquals(2, notices(owner), "after reading, the next message notifies again");
    }

    /* ------------------------------------------------------------ Omnia (admin) chat */

    @Test
    void adminListsEveryConversationAndOthersGet403() {
        as(buyer);
        long id = chat.start(product.getId(), "Pyetje").id();

        as(admin);
        var all = chat.adminList(0, 20, "ALL", null);
        var seen = all.content().stream().filter(v -> v.id() == id).findFirst().orElseThrow();
        assertEquals("ADMIN", seen.mySide());
        assertTrue(seen.canSend());
        assertEquals("PRODUCT", seen.kind());
        assertEquals(buyer.getId(), seen.buyerId());
        assertEquals("Ana kola", seen.buyerName(), "admins see the full name");
        assertTrue(chat.adminList(0, 20, "product", "GOLF").content().stream().anyMatch(v -> v.id() == id));
        assertTrue(chat.adminList(0, 20, "ALL", "ana KOLA").content().stream().anyMatch(v -> v.id() == id));
        assertTrue(chat.adminList(0, 20, "ALL", org.getName().toUpperCase()).content().stream().anyMatch(v -> v.id() == id));
        assertFalse(chat.adminList(0, 20, "SUPPORT", null).content().stream().anyMatch(v -> v.id() == id));
        assertFalse(chat.adminList(0, 20, "ALL", "nuk-ekziston-%").content().stream().anyMatch(v -> v.id() == id));
        assertThrows(IllegalArgumentException.class, () -> chat.adminList(0, 20, "OTHER", null));

        as(owner);
        assertThrows(AccessDeniedException.class, () -> chat.adminList(0, 20, "ALL", null));
        assertThrows(AccessDeniedException.class, () -> chat.adminStart(buyer.getId(), "Tung"));
        as(buyer);
        assertThrows(AccessDeniedException.class, () -> chat.adminList(0, 20, "ALL", null));
    }

    @Test
    void adminWritesInAProductConversationAsOmnia() {
        as(buyer);
        long id = chat.start(product.getId(), "Pyetje").id();
        as(owner);
        var answer = chat.send(id, "Përgjigje");
        as(buyer);
        chat.read(id, answer.id());
        assertEquals(0, chat.unread().buyer());
        as(owner);
        chat.read(id, answer.id());
        assertEquals(0, chat.get(id).unread());
        long ownerNotices = notices(owner);
        long buyerNotices = notices(buyer);

        as(admin);
        var msg = chat.send(id, "Përshëndetje nga Omnia");
        assertEquals("ADMIN", msg.side());
        assertEquals(0, chat.get(id).unread(), "admins have no unread count in product chats");
        assertEquals(0L, chat.read(id, msg.id()).get("unread"));

        as(buyer);
        var mine = chat.get(id);
        assertEquals(1, mine.unread());
        assertEquals("ADMIN", mine.lastSenderSide());
        assertEquals(1, chat.unread().buyer());
        as(owner);
        assertEquals(1, chat.get(id).unread());
        assertEquals(1, chat.unread().seller());
        assertEquals(ownerNotices + 1, notices(owner), "the company is notified");
        assertEquals(buyerNotices + 1, notices(buyer), "the buyer is notified");
        assertEquals(0, notices(admin), "the sender is never notified");
        assertEquals("ADMIN", jdbc.queryForObject("SELECT status FROM notification_inbox WHERE recipient_id=? "
                + "AND target_type='CHAT' ORDER BY id DESC LIMIT 1", String.class, buyer.getId()));
    }

    @Test
    void adminStartsSupportChatAndTheUserReplies() {
        User otherAdmin = user("ADMIN", "Admin", "Dy");
        as(admin);
        var started = chat.adminStart(buyer.getId(), "Tung nga Omnia");
        assertEquals("SUPPORT", started.kind());
        assertEquals(0, started.productId());
        assertEquals(0, started.organizationId());
        assertEquals("Omnia", started.productName());
        assertEquals("Omnia", started.organizationName());
        assertNull(started.productImage());
        assertEquals("ADMIN", started.mySide());
        assertEquals(buyer.getId(), started.buyerId());
        assertEquals(started.id(), chat.adminStart(buyer.getId(), "Edhe një").id(), "one SUPPORT chat per user");
        assertThrows(ResourceNotFoundException.class, () -> chat.adminStart(999_999_999L, "Hi"));
        stranger.setStatus(UserStatus.BANNED);
        em.flush();
        assertThrows(ResourceNotFoundException.class, () -> chat.adminStart(stranger.getId(), "Hi"));
        assertEquals(1, notices(buyer), "one unread notice per chat");

        as(buyer);
        var mine = chat.mine(0, 20).content();
        assertEquals(1, mine.size());
        var support = mine.get(0);
        assertEquals(started.id(), support.id());
        assertEquals("SUPPORT", support.kind());
        assertEquals("BUYER", support.mySide());
        assertEquals(2, support.unread());
        assertEquals(1, chat.unread().buyer());
        var page = chat.messages(started.id(), null, null);
        assertEquals(List.of("ADMIN", "ADMIN"), page.items().stream().map(ChatService.Message::side).toList());
        assertEquals(0L, chat.read(started.id(), page.items().get(1).id()).get("unread"));
        var reply = chat.send(started.id(), "Faleminderit");
        assertEquals("BUYER", reply.side());
        assertEquals(1, notices(admin));
        assertEquals(1, notices(otherAdmin), "every platform admin is notified");

        as(admin);
        var row = chat.adminList(0, 20, "SUPPORT", null).content().stream().filter(v -> v.id() == started.id()).findFirst().orElseThrow();
        assertEquals(1, row.unread(), "the user's reply is unread for Omnia");
        assertEquals(reply.id(), row.otherLastReadId(), "Omnia sees how far the user has read");
        assertEquals(0L, chat.read(started.id(), reply.id()).get("unread"));
        assertEquals(0, chat.get(started.id()).unread());
        var notice = inbox.list("all", 0).items().get(0);
        assertEquals("Omnia", notice.label());
        assertEquals(started.id(), ((Number) inbox.open(notice.id()).get("conversationId")).longValue());

        as(owner);
        assertThrows(ResourceNotFoundException.class, () -> chat.get(started.id()));
    }

    @Test
    void textIsCleanedAndValidated() {
        as(buyer);
        long id = chat.start(product.getId(), "<b>Tung</b>‮\u0000\r\n\n\n\nok").id();
        assertEquals("<b>Tung</b>\n\nok", chat.messages(id, null, null).items().get(0).body(),
                "stored as plain text; clients render it as text");
        assertThrows(IllegalArgumentException.class, () -> chat.send(id, "   "));
        assertThrows(IllegalArgumentException.class, () -> chat.send(id, null));
        assertThrows(IllegalArgumentException.class, () -> chat.send(id, "a".repeat(2001)));
        assertEquals(2000, chat.send(id, "a".repeat(2000)).body().length());
    }

    @Test
    void sendingIsRateLimited() {
        as(buyer);
        long id = chat.start(product.getId(), "1").id();
        for (int i = 2; i <= ChatService.MAX_MESSAGES_PER_MINUTE; i++) {
            chat.send(id, String.valueOf(i));
        }
        assertThrows(TooManyRequestsException.class, () -> chat.send(id, "tepër"));
    }

    @Test
    void cannotStartForInactiveProductsOrOwnCompany() {
        as(owner);
        assertThrows(IllegalArgumentException.class, () -> chat.start(product.getId(), "Vetes"));
        product.setStatus(ProductStatus.INACTIVE);
        em.flush();
        as(buyer);
        assertThrows(ResourceNotFoundException.class, () -> chat.start(product.getId(), "Hi"));
        assertThrows(ResourceNotFoundException.class, () -> chat.forProduct(product.getId()));
    }

    @Test
    void blockedAccountsCannotUseChat() {
        buyer.setStatus(UserStatus.BANNED);
        em.flush();
        as(buyer);
        assertThrows(AccessDeniedException.class, () -> chat.mine(0, 20));
    }
}
