package com.ai.fabric.runtime.documents;

import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;

@Component
public class DocumentRuntimeIdentity {

    private final Boundary configured;

    public DocumentRuntimeIdentity(
        @Value("${ai.fabric.runtime.auth.public-tokens.defaults.tenant-id:${AI_FABRIC_RUNTIME_TENANT_ID:}}") String tenantId,
        @Value("${ai.fabric.runtime.auth.public-tokens.defaults.customer-id:${AI_FABRIC_RUNTIME_CUSTOMER_ID:}}") String customerId,
        @Value("${ai.fabric.runtime.auth.public-tokens.defaults.deployment-id:${AI_FABRIC_RUNTIME_DEPLOYMENT_ID:}}") String deploymentId,
        @Value("${loomai.runtime.target-profile-id:${LOOMAI_RUNTIME_TARGET_PROFILE_ID:}}") String targetProfileId,
        @Value("${loomai.runtime.release-id:${LOOMAI_RUNTIME_RELEASE_ID:}}") String releaseId
    ) {
        configured = new Boundary(tenantId, customerId, deploymentId, targetProfileId, releaseId);
    }

    public Optional<Boundary> configuredBoundary() {
        return validId(configured.tenantId()) && validId(configured.customerId())
            && validId(configured.deploymentId()) && validId(configured.targetProfileId())
            && validId(configured.releaseId()) ? Optional.of(configured) : Optional.empty();
    }

    public Boundary requireConfiguredBoundary() {
        return configuredBoundary().orElseThrow(() -> new DocumentKnowledgeException(
            "DOCUMENT_RUNTIME_SCOPE_REQUIRED", HttpStatus.SERVICE_UNAVAILABLE,
            "Document knowledge requires configured tenant, customer, deployment, target profile and release IDs."
        ));
    }

    public Boundary requireBoundary(RuntimeResolvedIdentity identity) {
        RuntimeAuthContext context = identity != null ? identity.getAuthContext() : null;
        if (context == null) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_AUTH_CONTEXT_REQUIRED", HttpStatus.UNAUTHORIZED,
                "Verified document administration context is required."
            );
        }
        Boundary boundary = requireConfiguredBoundary();
        if (!boundary.tenantId().equals(context.getTenantId())
            || !boundary.customerId().equals(context.getCustomerId())
            || !boundary.deploymentId().equals(context.getDeploymentId())) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_SCOPE_MISMATCH", HttpStatus.FORBIDDEN,
                "Verified document context does not belong to this runtime."
            );
        }
        return boundary;
    }

    private boolean validId(String value) {
        return StringUtils.hasText(value) && value.equals(value.trim()) && value.length() <= 128;
    }

    public record Boundary(String tenantId, String customerId, String deploymentId, String targetProfileId, String releaseId) {
        public Map<String, Object> ownerMetadata() {
            return Map.of("tenantId", tenantId, "customerId", customerId, "deploymentId", deploymentId,
                "targetProfileId", targetProfileId);
        }

        public Map<String, Object> metadata() {
            return Map.of("tenantId", tenantId, "customerId", customerId, "deploymentId", deploymentId,
                "targetProfileId", targetProfileId, "releaseId", releaseId);
        }

        public boolean matches(Map<String, Object> metadata) {
            return metadata != null && metadata().entrySet().stream()
                .allMatch(entry -> entry.getValue().equals(metadata.get(entry.getKey())));
        }

        public boolean matchesOwner(Map<String, Object> metadata) {
            return metadata != null && ownerMetadata().entrySet().stream()
                .allMatch(entry -> entry.getValue().equals(metadata.get(entry.getKey())));
        }
    }
}
