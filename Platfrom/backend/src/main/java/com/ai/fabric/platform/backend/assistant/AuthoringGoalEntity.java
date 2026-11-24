package com.ai.fabric.platform.backend.assistant;

import jakarta.persistence.*;
import java.time.Instant;

/** Authority and bounded UI projection, never a second native execution/checkpoint store. */
@Entity
@Table(name = "platform_authoring_goals")
public class AuthoringGoalEntity {
    @Id public String id;
    @Column(nullable = false) public String sessionId;
    @Column(nullable = false) public String turnId;
    @Column(nullable = false) public String actorId;
    @Column(nullable = false) public String state;
    public String workId;
    public String reasonCode;
    @Column(nullable = false) public long requestRevision;
    @Column(nullable = false) public long cancellationEpoch;
    @Column(nullable = false) public int segment;
    @Column(nullable = false, columnDefinition = "TEXT") public String encryptedAuthority;
    @Column(nullable = false, columnDefinition = "TEXT") public String encryptedProjection;
    @Column(nullable = false) public Instant validUntil;
    @Column(nullable = false) public Instant createdAt;
    @Column(nullable = false) public Instant updatedAt;
    public Instant nextPollAt;
    public String pendingControl;
    public Instant erasedAt;
}
