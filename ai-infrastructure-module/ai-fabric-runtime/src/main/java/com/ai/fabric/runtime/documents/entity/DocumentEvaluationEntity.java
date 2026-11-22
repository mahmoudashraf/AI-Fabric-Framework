package com.ai.fabric.runtime.documents.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name = "loomai_document_evaluation") @Getter @Setter
public class DocumentEvaluationEntity {
    @Id @Column(length=64) private String id;
    @Column(name="source_id", nullable=false, length=64) private String sourceId;
    @Column(name="tenant_id", nullable=false, length=128) private String tenantId;
    @Column(name="customer_id", nullable=false, length=128) private String customerId;
    @Column(name="deployment_id", nullable=false, length=128) private String deploymentId;
    @Column(name="target_profile_id", nullable=false, length=128) private String targetProfileId;
    @Column(name="release_id", nullable=false, length=128) private String releaseId;
    @Column(name="result_json", nullable=false, columnDefinition="TEXT") private String resultJson;
    @Column(nullable=false, length=32) private String status;
    @Column(name="created_at", nullable=false) private Instant createdAt;
}
