package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.vectorization.entity.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupportResourceServiceTest {
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void deploymentProjectionReportsReleaseStatesWithoutMisrepresentingLatestAttemptAsServing() throws Exception {
        var f = new SupportFixture(); var d = SupportFixture.deployment("dep-customer", "customer-a", "tenant-a");
        d.setRuntimeBaseUrl("https://private-host.example"); d.setConnectorBaseUrl("https://secret-connector.example");
        when(f.deployments.findById(d.getId())).thenReturn(Optional.of(d));
        var release = new DeploymentReleaseEntity(); release.setId("failed-latest"); release.setDeploymentId(d.getId());
        release.setTargetProfileId("customer-staging"); release.setStatus("FAILED"); release.setVerificationStatus("FAILED");
        release.setErrorMessage("password=DO_NOT_DISCLOSE"); release.setProvisioningDetailsJson("{\"apiKey\":\"DO_NOT_DISCLOSE\"}");
        when(f.releases.findByDeploymentIdOrderByCreatedAtDesc(d.getId())).thenReturn(List.of(release));
        var c = f.create(true, new ResourceRef(ResourceType.DEPLOYMENT, d.getId()));
        var result = f.diagnostics.read("support_get_deployment_status", f.headers(f.service.requireOwnedCase(c.id())));
        var payload = f.json.writeValueAsString(result);
        assertThat(payload).contains("FAILED", "failed-latest", "customer-staging").doesNotContain("DO_NOT_DISCLOSE", "private-host", "secret-connector", "servingRelease");
        assertThat(result.resources().getFirst().facts()).containsEntry("liveHealthProbed", false);
    }

    @Test void engageReadinessOmitsRawCheckMessagesUrlsAndAuthenticationConfiguration() throws Exception {
        var f = new SupportFixture();
        var app = new AIWorkspaceInstallationEntity(); app.setId("app-row"); app.setInstallationId("public-installation");
        app.setCustomerId("customer-a"); app.setConsumerEntityId("consumer"); app.setStatus(AIWorkspaceInstallationStatus.DRAFT);
        app.setAuthConfigurationJson("{\"secret\":\"DO_NOT_DISCLOSE\"}");
        var consumer = new PlatformConsumerEntity(); consumer.setId("consumer"); consumer.setCustomerId("customer-a");
        when(f.apps.findByCustomerIdAndInstallationId("customer-a", app.getInstallationId())).thenReturn(Optional.of(app));
        when(f.consumers.findById("consumer")).thenReturn(Optional.of(consumer));
        when(f.readiness.evaluateLocal(app)).thenReturn(new AIWorkspaceReadinessSummary(false, app.getInstallationId(), "private", "consumer", "dep", "release",
            "https://private.example", "rev", List.of(new AIWorkspaceReadinessCheck("ASSIGNMENT", "BLOCKED", "DO_NOT_DISCLOSE raw exception"))));
        var c = f.create(true, new ResourceRef(ResourceType.ENGAGE_APP, app.getInstallationId()));
        var result = f.diagnostics.read("support_get_engage_readiness", f.headers(f.service.requireOwnedCase(c.id())));
        assertThat(f.json.writeValueAsString(result)).contains("ASSIGNMENT", "BLOCKED").doesNotContain("DO_NOT_DISCLOSE", "private.example");
        assertThat(result.availability()).isEqualTo(Availability.INCOMPLETE);
        verify(f.readiness, never()).evaluate(app);
    }

    @Test void connectionProjectsOnlyObservedSyncFactsNotBusinessRecordsOrProviderErrors() throws Exception {
        var f = new SupportFixture(); var d = SupportFixture.deployment("dep-a", "customer-a", "tenant-a");
        when(f.deployments.findById(d.getId())).thenReturn(Optional.of(d));
        var source = new VectorizationSourceConnectionEntity(); source.setId("source-a"); source.setDeploymentId(d.getId());
        source.setCustomerId("customer-a"); source.setTenantId("tenant-a"); source.setStatus("ACTIVE");
        source.setConnectionConfigJson("DO_NOT_DISCLOSE"); source.setSecretReferencesJson("DO_NOT_DISCLOSE"); source.setDiscoverySummaryJson("DO_NOT_DISCLOSE");
        when(f.connections.findById(source.getId())).thenReturn(Optional.of(source));
        var plan = new VectorizationPlanEntity(); plan.setId("plan"); plan.setDeploymentId(d.getId()); plan.setSourceConnectionId(source.getId());
        plan.setCustomerId("customer-a"); plan.setTenantId("tenant-a");
        plan.setSyncState("IN_SYNC"); plan.setLastRunId("run"); plan.setLastSuccessfulRunId("run");
        when(f.plans.findByDeploymentId(d.getId())).thenReturn(Optional.of(plan));
        var run = new VectorizationRunEntity(); run.setId("run"); run.setPlanId(plan.getId()); run.setDeploymentId(d.getId());
        run.setCustomerId("customer-a"); run.setTenantId("tenant-a"); run.setStatus("COMPLETED");
        run.setCompletedAt(Instant.parse("2026-10-29T10:00:00Z")); run.setErrorSummaryJson("DO_NOT_DISCLOSE");
        when(f.runs.findById("run")).thenReturn(Optional.of(run));
        var c = f.create(true, new ResourceRef(ResourceType.CONNECTION, source.getId()));
        var result = f.diagnostics.read("support_get_connection_sync_status", f.headers(f.service.requireOwnedCase(c.id())));
        var payload = f.json.writeValueAsString(result);
        assertThat(payload).contains("IN_SYNC", "lastSuccessfulSyncAt", "COMPLETED").doesNotContain("DO_NOT_DISCLOSE");
        assertThat(result.resources().getFirst().facts()).containsEntry("liveProviderProbed", false);
        source.setTenantId("foreign-tenant");
        assertThatThrownBy(() -> f.diagnostics.read("support_get_connection_sync_status", f.headers(f.service.requireOwnedCase(c.id()))))
            .hasMessageContaining("SUPPORT_ACCESS_DENIED");
    }
}
