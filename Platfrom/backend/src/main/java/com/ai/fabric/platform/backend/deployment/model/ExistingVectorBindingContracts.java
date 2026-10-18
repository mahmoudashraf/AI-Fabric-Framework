package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** Staff-reviewed connection to an existing cluster; this contract never provisions provider resources. */
public final class ExistingVectorBindingContracts {
    private ExistingVectorBindingContracts() { }

    public enum AuthenticationType {
        USERNAME_PASSWORD, TOKEN;

        @JsonCreator
        public static AuthenticationType fromJson(JsonNode value) {
            if (value == null || !value.isTextual()) throw new IllegalArgumentException("EXTERNAL_VECTOR_AUTH_TYPE_INVALID");
            try { return AuthenticationType.valueOf(value.textValue()); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("EXTERNAL_VECTOR_AUTH_TYPE_INVALID"); }
        }
    }

    public record Request(String versionId, String clusterId, String projectId, String regionId,
                          String expectedDeploymentOption, String databaseName, String username,
                          String password, String approvalReference, AuthenticationType authType, String token) {
        @JsonCreator
        public Request {
            if (authType == null) throw new IllegalArgumentException("EXTERNAL_VECTOR_AUTH_TYPE_REQUIRED");
        }

        public Request(String versionId, String clusterId, String projectId, String regionId,
                       String expectedDeploymentOption, String databaseName, String username,
                       String password, String approvalReference) {
            this(versionId, clusterId, projectId, regionId, expectedDeploymentOption, databaseName,
                username, password, approvalReference, AuthenticationType.USERNAME_PASSWORD, null);
        }

        @Override public String toString() { return "ExistingVectorBindingRequest[credentials redacted]"; }
    }

    public record Summary(String bindingId, String deploymentId, String targetProfileId, String versionId,
                          String clusterId, String deploymentOption, String endpointHost, String databaseName,
                          String collectionPrefix, String lifecycle, String fingerprint, Instant checkedAt,
                          AuthenticationType authType) { }
}
