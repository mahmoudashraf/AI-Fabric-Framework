package com.ai.fabric.runtime.documents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(
    name = "loomai_document_work",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_loomai_document_work_framework",
        columnNames = {"manifest_id", "operation", "framework_work_id"}
    ),
    indexes = @Index(name = "idx_loomai_document_work_manifest", columnList = "manifest_id,operation")
)
public class DocumentWorkEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "manifest_id", nullable = false, length = 64)
    private String manifestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 16)
    private Operation operation;

    @Column(name = "framework_work_id", nullable = false, length = 64)
    private String frameworkWorkId;

    @Column(name = "submission_attempt", nullable = false)
    private int submissionAttempt;

    @Column(name = "last_work_state", nullable = false, length = 32)
    private String lastWorkState;

    @Column(name = "terminal", nullable = false)
    private boolean terminal;

    @Column(name = "successful", nullable = false)
    private boolean successful;

    @Column(name = "failure_code", length = 128)
    private String failureCode;

    @Column(name = "failure_message", length = 512)
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_observed_at", nullable = false)
    private Instant lastObservedAt;

    public enum Operation {
        INDEX,
        DELETE
    }
}
