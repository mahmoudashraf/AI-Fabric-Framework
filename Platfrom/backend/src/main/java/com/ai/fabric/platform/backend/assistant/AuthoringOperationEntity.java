package com.ai.fabric.platform.backend.assistant;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "platform_authoring_operations", uniqueConstraints = @UniqueConstraint(columnNames = {"goalId", "operationId"}))
public class AuthoringOperationEntity {
    @Id public String id;
    @Column(nullable = false) public String goalId;
    @Column(nullable = false, length = 200) public String operationId;
    @Column(nullable = false) public String parameterFingerprint;
    @Column(nullable = false) public int segment;
    @Column(length = 200) public String undoes;
    @Column(nullable = false, columnDefinition = "TEXT") public String encryptedReceipt;
    @Column(nullable = false) public Instant createdAt;
}
