package com.omnia.backend.controller;

import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.service.impl.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Buyer ↔ company chat about a product, and Omnia ↔ user support chat. Clients poll; there are no websockets. */
@RestController
@PreAuthorize("isAuthenticated()")
public class ChatController {

    /** Request body for a new message. */
    public record MessageRequest(String body) {
    }

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    /** "Shkruaj shitësit": opens (or reuses) the conversation about this product and sends the message. */
    @PostMapping("/api/products/{productId}/conversation")
    public ResponseEntity<ChatService.ConversationView> start(@PathVariable long productId,
                                                              @RequestBody MessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chat.start(productId, request == null ? null : request.body()));
    }

    /** The caller's existing conversation about this product (404 when there is none yet). */
    @GetMapping("/api/products/{productId}/conversation")
    public ChatService.ConversationView forProduct(@PathVariable long productId) {
        return chat.forProduct(productId);
    }

    @GetMapping("/api/conversations/mine")
    public PagedResponse<ChatService.ConversationView> mine(@RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return chat.mine(page, size);
    }

    @GetMapping("/api/conversations/unread")
    public ChatService.Unread unread() {
        return chat.unread();
    }

    @GetMapping("/api/organizations/{organizationId}/conversations")
    public PagedResponse<ChatService.ConversationView> forOrganization(@PathVariable long organizationId,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        return chat.forOrganization(organizationId, page, size);
    }

    @GetMapping("/api/conversations/{id}")
    public ChatService.ConversationView get(@PathVariable long id) {
        return chat.get(id);
    }

    @GetMapping("/api/conversations/{id}/messages")
    public ChatService.Messages messages(@PathVariable long id,
                                         @RequestParam(required = false) Long after,
                                         @RequestParam(required = false) Long before) {
        return chat.messages(id, after, before);
    }

    @PostMapping("/api/conversations/{id}/messages")
    public ResponseEntity<ChatService.Message> send(@PathVariable long id, @RequestBody MessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chat.send(id, request == null ? null : request.body()));
    }

    /** Request body for Omnia's first message to a user. */
    public record AdminStartRequest(Long userId, String body) {
    }

    /** Omnia's inbox: every conversation with messages (platform admins only). */
    @GetMapping("/api/admin/conversations")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public PagedResponse<ChatService.ConversationView> adminList(@RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size,
                                                                 @RequestParam(defaultValue = "ALL") String kind,
                                                                 @RequestParam(required = false) String q) {
        return chat.adminList(page, size, kind, q);
    }

    /** Omnia writes to a user: opens (or reuses) the user's SUPPORT conversation and sends the message. */
    @PostMapping("/api/admin/conversations")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ChatService.ConversationView> adminStart(@RequestBody AdminStartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chat.adminStart(
                request == null ? null : request.userId(), request == null ? null : request.body()));
    }

    @PutMapping("/api/conversations/{id}/read")
    public Map<String, Object> read(@PathVariable long id, @RequestParam long throughId) {
        return chat.read(id, throughId);
    }
}
