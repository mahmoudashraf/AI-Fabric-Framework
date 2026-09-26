package com.ai.fabric.runtime.documents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(
    name = "loomai_document_command",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_loomai_document_command_idempotency",
        columnNames = {"tenant_id", "deployment_id", "operation", "idempotency_key"}
    )
)
public class DocumentCommandEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "deployment_id", nullable = false, length = 128)
    private String deploymentId;

    @Column(name = "operation", nullable = false, length = 64)
    private String operation;

    @Column(name = "resource_key", nullable = false, length = 256)
    private String resourceKey;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
