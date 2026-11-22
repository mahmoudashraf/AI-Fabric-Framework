package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.vectorization.entity.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupportResourceServiceTest {
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void integrationProjectionIsBoundedSavedDraftMetadataNotCredentialsOrAConnectivityProbe() throws Exception {
        var f = new SupportFixture(); var d = SupportFixture.deployment("dep-a", "customer-a", "tenant-a");
        when(f.deployments.findById(d.getId())).thenReturn(Optional.of(d));
        var draft = new DeploymentDraftEntity(); draft.setRevisionNumber(7);
        draft.setActionsConfigJson("""
            {"actions":[{"name":"public_lookup","accessMode":"READ","route":{"url":"https://DO_NOT_DISCLOSE","headers":{"Authorization":"DO_NOT_DISCLOSE"}}},
              {"name":"cancel","accessMode":"WRITE_ONLY","requiresConfirmation":true},
              {"name":"update","accessMode":"READ_WRITE"}],"secret":"DO_NOT_DISCLOSE"}
            """);
        draft.setRoutingConfigJson("{\"actions\":{\"cancel\":{\"url\":\"DO_NOT_DISCLOSE\"}}}");
        when(f.drafts.findTopByDeploymentIdOrderByRevisionNumberDesc(d.getId())).thenReturn(Optional.of(draft));
        var c = f.create(true, new ResourceRef(ResourceType.DEPLOYMENT, d.getId()));
        var headers = f.headers(f.service.requireOwnedCase(c.id()));
        var result = f.diagnostics.read("support_get_deployment_status", headers);
        var facts = nested(result.resources().getFirst(), "integrationConfiguration");
        assertThat(facts).containsEntry("draftRevision", 7).containsEntry("capabilityCount", 3)
            .containsEntry("readCapabilityCount", 2).containsEntry("writeCapabilityCount", 2)
            .containsEntry("observationBasis", "SAVED_DRAFT_CONFIGURATION")
            .containsEntry("providerConnectivityProbed", false).containsEntry("actionsExecuted", false);
        assertThat(f.json.writeValueAsString(result)).contains("public_lookup", "confirmationRequired\":true").doesNotContain("DO_NOT_DISCLOSE");
        f.service.consent(c.id(), new ConsentChange(false, c.consent().version()));
        clearInvocations(f.drafts);
        assertThatThrownBy(() -> f.diagnostics.read("support_get_deployment_status", headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        verifyNoInteractions(f.drafts);
    }

    @Test void missingOrMalformedDraftDoesNotInventWorkingIntegration() {
        var f = new SupportFixture(); var d = SupportFixture.deployment("dep-a", "customer-a", "tenant-a");
        when(f.deployments.findById(d.getId())).thenReturn(Optional.of(d));
        var c = f.create(true, new ResourceRef(ResourceType.DEPLOYMENT, d.getId()));
        var headers = f.headers(f.service.requireOwnedCase(c.id()));
        assertThat(nested(f.diagnostics.read("support_get_deployment_status", headers).resources().getFirst(), "integrationConfiguration")).containsEntry("code", "NO_SAVED_DRAFT");
        var draft = new DeploymentDraftEntity(); draft.setActionsConfigJson("not-json");
        when(f.drafts.findTopByDeploymentIdOrderByRevisionNumberDesc(d.getId())).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> f.diagnostics.read("support_get_deployment_status", headers)).hasMessageContaining("SUPPORT_");
    }

    @Test void handoverUsesExactVerifiedAppReleaseNeverTheNewerDraftAndDoesNotClaimAgentPresence() throws Exception {
        var f = new SupportFixture(); var d = SupportFixture.deployment("dep-a", "customer-a", "tenant-a");
        when(f.deployments.findById(d.getId())).thenReturn(Optional.of(d));
        var app = new AIWorkspaceInstallationEntity(); app.setInstallationId("app-a"); app.setCustomerId("customer-a"); app.setConsumerEntityId("consumer-a");
        app.setConfigurationJson("{\"capabilities\":{\"handover\":true,\"tickets\":true},\"support\":{\"teamVisible\":true},\"private\":\"DO_NOT_DISCLOSE\"}");
        var consumer = new PlatformConsumerEntity(); consumer.setId("consumer-a"); consumer.setCustomerId("customer-a");
        consumer.setBoundDeploymentId(d.getId()); consumer.setBoundReleaseId("rel-exact"); consumer.setBoundTargetProfileId("stage");
        when(f.apps.findByCustomerIdAndInstallationId("customer-a", "app-a")).thenReturn(Optional.of(app));
        when(f.consumers.findById("consumer-a")).thenReturn(Optional.of(consumer));
        var release = new DeploymentReleaseEntity(); release.setId("rel-exact"); release.setDeploymentId(d.getId());
        release.setTargetProfileId("stage"); release.setDeploymentVersionId("version-exact"); release.setStatus("APPLIED_VERIFIED"); release.setVerificationStatus("PASSED");
        when(f.releases.findById("rel-exact")).thenReturn(Optional.of(release));
        var version = new DeploymentVersionEntity(); version.setDeploymentId(d.getId());
        version.setBehaviorConfigJson("{\"conversationHandover\":{\"enabled\":true,\"userCanResumeAi\":true,\"channel\":{\"secret\":\"DO_NOT_DISCLOSE\"}}}");
        when(f.versions.findById("version-exact")).thenReturn(Optional.of(version));
        when(f.readiness.evaluateLocal(app)).thenReturn(new AIWorkspaceReadinessSummary(false,"app-a","support","consumer-a",d.getId(),"rel-exact",null,"revision",List.of()));
        var c = f.create(true, new ResourceRef(ResourceType.ENGAGE_APP, "app-a"));
        var headers = f.headers(f.service.requireOwnedCase(c.id()));
        var result = f.diagnostics.read("support_get_engage_readiness", headers);
        assertThat(nested(result.resources().getFirst(), "handoverConfiguration")).containsEntry("handoverEnabledInAssignedRelease", true)
            .containsEntry("handoverVisibleInApp", true).containsEntry("teamVisibleInApp", true)
            .containsEntry("agentPresenceProbed", false).containsEntry("messageDeliveryProbed", false);
        assertThat(f.json.writeValueAsString(result)).doesNotContain("DO_NOT_DISCLOSE");
        verifyNoInteractions(f.drafts);
        release.setVerificationStatus("FAILED"); clearInvocations(f.versions);
        assertThat(nested(f.diagnostics.read("support_get_engage_readiness", headers).resources().getFirst(), "handoverConfiguration")).containsEntry("code", "ASSIGNED_RELEASE_NOT_VERIFIED");
        verifyNoInteractions(f.versions);
        consumer.setBoundDeploymentId("foreign");
        assertThatThrownBy(() -> f.diagnostics.read("support_get_engage_readiness", headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> nested(ResourceDiagnostic resource, String key) {
        return (Map<String,Object>) ((Map<String,Object>) resource.facts().get(key)).get("facts");
    }

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
            "https://private.example", "rev", List.of(new AIWorkspaceReadinessCheck("ASSIGNMENT", "BLOCKED", "DO_NOT_DISCLOSE raw exception"),
                new AIWorkspaceReadinessCheck("SUPPORT_SPACE", "BLOCKED", "DO_NOT_DISCLOSE provider details"))));
        var c = f.create(true, new ResourceRef(ResourceType.ENGAGE_APP, app.getInstallationId()));
        var result = f.diagnostics.read("support_get_engage_readiness", f.headers(f.service.requireOwnedCase(c.id())));
        assertThat(f.json.writeValueAsString(result)).contains("ASSIGNMENT", "SUPPORT_SPACE", "BLOCKED").doesNotContain("DO_NOT_DISCLOSE", "private.example", "UNKNOWN");
        assertThat(result.resources().getFirst().facts()).containsEntry("observationBasis", "LOCAL_CONFIGURATION_ONLY")
            .containsEntry("providerConnectivityProbed", false).containsEntry("deliveryProbed", false)
            .containsEntry("localReadinessPassed", false);
        assertThat(f.json.writeValueAsString(result)).contains("NOT_PROBED");
        assertThat(result.availability()).isEqualTo(Availability.INCOMPLETE);
        verify(f.readiness, never()).evaluate(app);
    }

    @Test void unperformedLocalProviderChecksDoNotInventAnOutageOrRelaxActivationReadiness() throws Exception {
        var f = new SupportFixture();
        var app = new AIWorkspaceInstallationEntity(); app.setInstallationId("app-a"); app.setCustomerId("customer-a"); app.setConsumerEntityId("consumer-a");
        var consumer = new PlatformConsumerEntity(); consumer.setId("consumer-a"); consumer.setCustomerId("customer-a");
        when(f.apps.findByCustomerIdAndInstallationId("customer-a", "app-a")).thenReturn(Optional.of(app));
        when(f.consumers.findById("consumer-a")).thenReturn(Optional.of(consumer));
        when(f.readiness.evaluateLocal(app)).thenReturn(new AIWorkspaceReadinessSummary(false,"app-a","support","consumer-a",null,null,null,null,
            List.of(new AIWorkspaceReadinessCheck("ASSIGNMENT","PASSED","Local assignment exists"),
                new AIWorkspaceReadinessCheck("APP_DELIVERY","BLOCKED","No delivery probe performed"),
                new AIWorkspaceReadinessCheck("SUPPORT_SPACE","BLOCKED","No support provider probe performed"))));
        var c = f.create(true, new ResourceRef(ResourceType.ENGAGE_APP, "app-a"));
        var result = f.diagnostics.read("support_get_engage_readiness", f.headers(f.service.requireOwnedCase(c.id())));
        assertThat(result.resources().getFirst().facts()).containsEntry("localReadinessPassed", true)
            .containsEntry("providerConnectivityProbed", false).containsEntry("deliveryProbed", false);
        assertThat(f.json.writeValueAsString(result)).contains("NOT_PROBED").doesNotContain("\"status\":\"BLOCKED\"");
        assertThat(result.availability()).isEqualTo(Availability.INCOMPLETE);
        verify(f.readiness, never()).evaluate(app);
    }

    @Test void documentStatusUsesOnlyAnOwnedSelectedAppsExactBindingAndRespectsConsent() {
        var f = new SupportFixture(); var d = SupportFixture.deployment("dep-a", "customer-a", "tenant-a");
        when(f.deployments.findById(d.getId())).thenReturn(Optional.of(d));
        var app = new AIWorkspaceInstallationEntity(); app.setInstallationId("app-a"); app.setCustomerId("customer-a"); app.setConsumerEntityId("consumer-a");
        var consumer = new PlatformConsumerEntity(); consumer.setId("consumer-a"); consumer.setCustomerId("customer-a");
        consumer.setBoundDeploymentId(d.getId()); consumer.setBoundReleaseId("rel-exact"); consumer.setBoundTargetProfileId("stage");
        when(f.apps.findByCustomerIdAndInstallationId("customer-a", "app-a")).thenReturn(Optional.of(app));
        when(f.consumers.findById("consumer-a")).thenReturn(Optional.of(consumer));
        var c = f.create(true, new ResourceRef(ResourceType.DEPLOYMENT, d.getId()), new ResourceRef(ResourceType.ENGAGE_APP, "app-a"));
        var owned = f.service.requireOwnedCase(c.id());
        var target = new DeploymentTargetReference(d.getId(), "stage", "rel-exact");
        when(f.documents.read(d, target, owned.principalId())).thenReturn(Map.of("availability", "CURRENT", "registeredSourceCount", 4));
        var result = f.diagnostics.read("support_get_deployment_status", f.headers(owned));
        assertThat(result.resources().getFirst().facts().get("documentIndexing"))
            .isEqualTo(List.of(Map.of("availability", "CURRENT", "registeredSourceCount", 4)));
        verify(f.documents).read(d, target, owned.principalId());
        f.service.consent(c.id(), new ConsentChange(false, c.consent().version()));
        clearInvocations(f.documents);
        assertThatThrownBy(() -> f.diagnostics.read("support_get_deployment_status", f.headers(owned)))
            .hasMessageContaining("SUPPORT_ACCESS_DENIED");
        verifyNoInteractions(f.documents);
    }

    @Test void missingSelectedAppDoesNotGuessDocumentTargetFromLatestRelease() {
        var f = new SupportFixture(); var d = SupportFixture.deployment("dep-a", "customer-a", "tenant-a");
        when(f.deployments.findById(d.getId())).thenReturn(Optional.of(d));
        var c = f.create(true, new ResourceRef(ResourceType.DEPLOYMENT, d.getId()));
        var result = f.diagnostics.read("support_get_deployment_status", f.headers(f.service.requireOwnedCase(c.id())));
        assertThat(result.resources().getFirst().facts().get("documentIndexing"))
            .isEqualTo(List.of(Map.of("availability", "NOT_APPLICABLE", "code", "SELECT_CONNECTED_APP_FOR_EXACT_TARGET")));
        verifyNoInteractions(f.documents);
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
