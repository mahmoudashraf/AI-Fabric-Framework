package com.ai.fabric.platform.backend.desk;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "platform_desk_bindings")
@Getter
@Setter
public class DeskBindingEntity {
    @Id private String id;
    private String customerId;
    private String tenantId;
    private String deploymentId;
    private String targetProfileId;
    private String sourceReleaseId;
    private String operationKey;
    private String status;
    private String releaseId;
    private String versionId;
    private String providerKeyRef;
    private String callbackKeyRef;
    private String runtimeCallbackKeyRef;
    private String bridgeKeyRef;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
