package com.ai.fabric.runtime.documents;

import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentRuntimeIdentityTest {

    private final DocumentRuntimeIdentity identity = new DocumentRuntimeIdentity(
        "tenant", "customer", "deployment", "staging", "release-a");

    @Test
    void targetAndReleaseComeOnlyFromTheConfiguredRuntime() {
        var boundary = identity.requireBoundary(context("tenant", "customer", "deployment"));
        assertThat(boundary.targetProfileId()).isEqualTo("staging");
        assertThat(boundary.releaseId()).isEqualTo("release-a");
        assertThat(boundary.metadata()).hasSize(5);
    }

    @Test
    void persistentOwnerIsTargetScopedButTheRuntimeStillRequiresItsExactRelease() {
        var original = identity.requireConfiguredBoundary();
        var cold = new DocumentRuntimeIdentity("tenant", "customer", "deployment", "staging", "release-b")
            .requireConfiguredBoundary();
        assertThat(cold.ownerMetadata()).isEqualTo(original.ownerMetadata()).doesNotContainKey("releaseId");
        assertThat(cold.matchesOwner(original.metadata())).isTrue();
        assertThat(cold.matches(original.metadata())).isFalse();
        var otherTarget = new DocumentRuntimeIdentity("tenant", "customer", "deployment", "production", "release-b")
            .requireConfiguredBoundary();
        assertThat(cold.matchesOwner(otherTarget.metadata())).isFalse();
    }

    @Test
    void missingIdentityAndForeignTenantCustomerOrDeploymentFailClosed() {
        assertThatThrownBy(() -> identity.requireBoundary(null))
            .isInstanceOf(DocumentKnowledgeException.class)
            .extracting(error -> ((DocumentKnowledgeException) error).code()).isEqualTo("DOCUMENT_AUTH_CONTEXT_REQUIRED");
        for (RuntimeResolvedIdentity foreign : new RuntimeResolvedIdentity[]{
            context("other", "customer", "deployment"), context("tenant", "other", "deployment"),
            context("tenant", "customer", "other")}) {
            assertThatThrownBy(() -> identity.requireBoundary(foreign))
                .isInstanceOf(DocumentKnowledgeException.class)
                .extracting(error -> ((DocumentKnowledgeException) error).code()).isEqualTo("DOCUMENT_SCOPE_MISMATCH");
        }
    }

    @Test
    void missingPartialOrMalformedConfiguredScopeCannotOwnDocuments() {
        for (DocumentRuntimeIdentity invalid : new DocumentRuntimeIdentity[]{
            new DocumentRuntimeIdentity("tenant", "customer", "deployment", "", ""),
            new DocumentRuntimeIdentity("tenant", "customer", "deployment", "staging", ""),
            new DocumentRuntimeIdentity("tenant", "customer", "deployment", "", "release-a"),
            new DocumentRuntimeIdentity("tenant", "customer", "deployment", " staging", "release-a"),
            new DocumentRuntimeIdentity("tenant", "", "deployment", "staging", "release-a")}) {
            assertThat(invalid.configuredBoundary()).isEmpty();
            assertThatThrownBy(invalid::requireConfiguredBoundary)
                .isInstanceOf(DocumentKnowledgeException.class)
                .extracting(error -> ((DocumentKnowledgeException) error).code()).isEqualTo("DOCUMENT_RUNTIME_SCOPE_REQUIRED");
        }
    }

    private RuntimeResolvedIdentity context(String tenant, String customer, String deployment) {
        return RuntimeResolvedIdentity.builder().authContext(RuntimeAuthContext.builder()
            .tenantId(tenant).customerId(customer).deploymentId(deployment).build()).build();
    }
}
