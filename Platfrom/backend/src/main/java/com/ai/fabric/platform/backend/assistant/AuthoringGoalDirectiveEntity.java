package com.ai.fabric.platform.backend.assistant;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "platform_authoring_goal_directives", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"goalId", "requestKey"}), @UniqueConstraint(columnNames = {"goalId", "requestRevision"})})
public class AuthoringGoalDirectiveEntity {
    @Id public String id;
    @Column(nullable = false) public String goalId;
    @Column(nullable = false) public long requestRevision;
    @Column(nullable = false, length = 128) public String requestKey;
    @Column(nullable = false) public String commandHash;
    @Column(nullable = false, columnDefinition = "TEXT") public String encryptedRequest;
    @Column(nullable = false) public Instant createdAt;
}
