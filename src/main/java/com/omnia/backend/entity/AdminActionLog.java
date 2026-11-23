package com.omnia.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One change a platform admin made. Never holds request bodies, secrets or passwords. */
@Entity
@Table(name = "admin_action_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_label", nullable = false, length = 150)
    private String actorLabel;

    @Column(name = "http_method", nullable = false, length = 8)
    private String httpMethod;

    /** The route pattern, e.g. /api/organizations/{organizationId}/status — never the query string. */
    @Column(nullable = false, length = 200)
    private String route;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(length = 500)
    private String detail;

    @Column(name = "response_status", nullable = false)
    private Integer responseStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
