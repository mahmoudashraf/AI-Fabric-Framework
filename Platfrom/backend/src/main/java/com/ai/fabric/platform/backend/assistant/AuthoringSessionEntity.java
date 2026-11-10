package com.ai.fabric.platform.backend.assistant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "platform_authoring_sessions")
public class AuthoringSessionEntity {
    @Id public String id;
    @Column(nullable = false) public String actorId;
    @Column(nullable = false) public String customerId;
    @Column(nullable = false) public String resourceKind;
    @Column(nullable = false) public String resourceId;
    @Column(nullable = false) public String task;
    @Column(nullable = false) public String status;
    public String activeTurnId;
    public Instant deadline;
    @Column(nullable = false, columnDefinition = "TEXT") public String encryptedContent;
    @Column(nullable = false) public Instant createdAt;
    @Column(nullable = false) public Instant updatedAt;
    @Column(nullable = false) public Instant expiresAt;
}
