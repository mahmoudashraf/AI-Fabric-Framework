package com.ai.fabric.platform.backend.deployment.dependency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "deployment_managed_dependency_specs")
@Getter
@Setter
public class ManagedDependencySpecEntity {
    @Id private String id;
    @Column(nullable = false) private String fingerprint;
    @Column(nullable = false, columnDefinition = "TEXT") private String specJson;
    @Column(nullable = false) private String reviewedBy;
    @Column(nullable = false) private Instant reviewedAt;
    @Column(nullable = false) private boolean active;
}
