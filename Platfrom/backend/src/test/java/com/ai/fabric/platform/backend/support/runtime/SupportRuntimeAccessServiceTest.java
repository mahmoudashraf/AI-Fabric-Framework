package com.ai.fabric.platform.backend.support.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceAssetDescriptor;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceAssignmentService;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceAssetCatalogService;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceConfigurationValidator;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.config.PlatformCorsProperties;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityBinding;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import com.ai.fabric.platform.backend.support.SupportCaseService;
import com.ai.fabric.platform.backend.support.SupportSettings;
import com.ai.fabric.platform.backend.support.SupportContracts.CaseStatus;
import com.ai.fabric.platform.backend.support.SupportContracts.DeliveryStatus;
import com.ai.fabric.platform.backend.support.SupportContracts.OwnedCase;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

class SupportRuntimeAccessServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SupportCaseService cases = mock(SupportCaseService.class);
    private final AIWorkspaceInstallationRepository installations = mock(AIWorkspaceInstallationRepository.class);
    private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    private final DeploymentReleaseEndpointService endpoints = mock(DeploymentReleaseEndpointService.class);
    private final AIWorkspaceAssetCatalogService assets = mock(AIWorkspaceAssetCatalogService.class);
    private final DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
    private final MockEnvironment environment = new MockEnvironment().withProperty("platform.support.installation-id", "awi-support");
    private final AIWorkspaceAssignmentService assignments = new AIWorkspaceAssignmentService(
        deployments, releases, versions, mapper, endpoints, targets);
    private final SupportRuntimeAccessService service = new SupportRuntimeAccessService(
        cases, installations, consumers, assignments, assets, authority, environment, mapper,
        new AIWorkspaceConfigurationValidator(mock(PlatformAIWorkspaceProperties.class)));
    private AIWorkspaceInstallationEntity installation;
    private PlatformConsumerEntity consumer;
    private DeploymentEntity deployment;
    private DeploymentReleaseEntity release;
    private DeploymentVersionEntity version;

    @BeforeEach void setup() {
        installation = new AIWorkspaceInstallationEntity();
        installation.setInstallationId("awi-support");
        installation.setConnectionProfileCode(com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceConnectionProfileRegistry.SUPPORT_CASE_HOST);
        installation.setConnectionProfileVersion("1.0.0");
        installation.setCustomerId("support-owner");
        installation.setConsumerEntityId("consumer-row");
        installation.setStatus(AIWorkspaceInstallationStatus.ACTIVE);
        installation.setConnectionMode(AIWorkspaceConnectionMode.BACKEND_MEDIATED_PRIVATE_RUNTIME);
        installation.setExperiencePackCode("generic-workspace");
        installation.setExperiencePackVersion("1.0.0");
        installation.setConfigurationJson("{\"app\":{\"assistantLabel\":\"LoomAI Support\"},\"capabilities\":{\"currentPage\":false}}");
        installation.setConnectionConfigurationJson("{\"serverSecret\":\"connection-private-marker\"}");
        installation.setAuthConfigurationJson("{\"serverSecret\":\"identity-private-marker\"}");
        consumer = new PlatformConsumerEntity();
        consumer.setId("consumer-row");
        consumer.setConsumerId("support-consumer");
        consumer.setCustomerId("support-owner");
        consumer.setStatus("ACTIVE");
        consumer.setBoundDeploymentId("support-deployment");
        consumer.setBoundReleaseId("support-release");
        consumer.setBoundTargetProfileId("support-staging");
        deployment = new DeploymentEntity();
        deployment.setId("support-deployment");
        deployment.setCustomerId("support-owner");
        deployment.setTenantId("support-tenant");
        deployment.setBehaviorType("CONVERSATIONAL");
        release = new DeploymentReleaseEntity();
        release.setId("support-release");
        release.setDeploymentId(deployment.getId());
        release.setDeploymentVersionId("support-version");
        release.setTargetProfileId("support-staging");
        release.setStatus("APPLIED_VERIFIED");
        release.setVerificationStatus("PASSED");
        release.setProviderType(DeploymentProviderType.COOLIFY);
        version = new DeploymentVersionEntity();
        version.setId("support-version");
        version.setDeploymentId(deployment.getId());
        version.setConfigHash("reviewed-config-hash");
        version.setSecurityConfigJson("{\"serverSecret\":\"security-private-marker\"}");
        version.setBehaviorConfigJson("""
            {"serverSecret":"behavior-private-marker","conversationHandover":{
              "enabled":true,"userCanResumeAi":true,"providerSecret":"channel-private-marker",
              "intake":{"anonymousAllowed":true,"fields":[]}},
             "specialistAssistance":{"enabled":true,"compiled":{"invocations":[
               {"requiredScopes":["support:setup:read","support:knowledge:read"]}]}}}
            """);
        var target = new DeploymentTargetProfileEntity();
        target.setId("support-staging");
        target.setActive(true);
        target.setEnvironmentName("staging");
        target.setProviderType(DeploymentProviderType.COOLIFY);
        when(cases.requireOwnedCase("sup-1")).thenReturn(owner("support-deployment", "support-staging", "support-owner", "support-tenant"));
        when(installations.findByInstallationId("awi-support")).thenReturn(Optional.of(installation));
        when(consumers.findById("consumer-row")).thenReturn(Optional.of(consumer));
        when(deployments.findById("support-deployment")).thenReturn(Optional.of(deployment));
        when(releases.findById("support-release")).thenReturn(Optional.of(release));
        when(versions.findById("support-version")).thenReturn(Optional.of(version));
        when(targets.findById("support-staging")).thenReturn(Optional.of(target));
        when(endpoints.runtimeBaseUrl(release)).thenReturn("https://support-runtime.example.test");
        when(endpoints.connectorBaseUrl(release)).thenReturn("https://support-connector.example.test");
        frozenAuthority("support-owner", "support-issuer", "support-audience");
        when(authority.issueReviewedIntegrationSessionHeaders(any(), eq(version), eq(release), eq("private-case-principal"), anyList()))
            .thenReturn(Map.of("X-AI-FABRIC-API-KEY", "runtime-private-marker", "X-AI-FABRIC-USER-TOKEN", "assertion-private-marker"));
        when(assets.workspace()).thenReturn(asset("workspace", "max-mode", "/api/public/ai-workspace/assets/widget.js"));
        when(assets.experiencePack("generic-workspace", "1.0.0"))
            .thenReturn(asset("experience-pack", "generic-workspace", "/api/public/ai-workspace/assets/generic.js"));
    }

    @Test void ordinaryInstallationUsesCurrentVerifiedAssignmentAndExactFrozenOwnerTargetAndPrincipal() {
        var access = service.resolve("sup-1");
        assertThat(access.installation()).isSameAs(installation);
        assertThat(access.assignment().consumer()).isSameAs(consumer);
        assertThat(access.assignment().deployment().getCustomerId()).isEqualTo("support-owner");
        assertThat(access.assignment().deployment().getTenantId()).isEqualTo("support-tenant");
        assertThat(access.assignment().release().getTargetProfileId()).isEqualTo("support-staging");
        assertThat(access.assignment().runtimeBaseUrl()).isEqualTo("https://support-runtime.example.test");
        assertThat(access.assignment().assignmentRevision()).startsWith("sha256:");
        assertThat(access.headers()).containsEntry("X-AI-FABRIC-USER-TOKEN", "assertion-private-marker");
        var order = inOrder(cases, installations, consumers, authority);
        order.verify(cases).requireOwnedCase("sup-1");
        order.verify(installations).findByInstallationId("awi-support");
        order.verify(consumers).findById("consumer-row");
        order.verify(authority).requireReleaseUnchangedForProvisioning(any(), eq(version), eq(release));
        order.verify(authority).issueReviewedIntegrationSessionHeaders(any(), eq(version), eq(release), eq("private-case-principal"),
            org.mockito.ArgumentMatchers.argThat(scopes -> scopes.containsAll(List.of("chat:messages", "chat:handover:read",
                "support:diagnostics:read", "support:setup:read", "support:knowledge:read"))));
    }

    @Test void bootstrapExposesOnlyOrdinaryPublicInstallationAndAssetsNotServerAuthority() throws Exception {
        var bootstrap = service.bootstrap("sup-1");
        assertThat(bootstrap.installationId()).isEqualTo("awi-support");
        assertThat(bootstrap.conversationId()).isEqualTo("chat-1");
        assertThat(bootstrap.principalNamespace()).isEqualTo("support:sup-1:episode-1");
        assertThat(bootstrap.apiBaseUrl()).isEqualTo("/api/support/cases/sup-1/runtime/api");
        assertThat(bootstrap.workspaceAssetUrl()).isEqualTo("/api/public/ai-workspace/assets/widget.js");
        assertThat(bootstrap.experienceModuleUrl()).isEqualTo("/api/public/ai-workspace/assets/generic.js");
        assertThat(bootstrap.configuration().path("app").path("assistantLabel").asText()).isEqualTo("LoomAI Support");
        assertThat(bootstrap.conversationHandover().path("enabled").asBoolean()).isTrue();
        assertThat(bootstrap.conversationHandover().path("providerSecret").isMissingNode()).isTrue();
        assertThat(mapper.writeValueAsString(bootstrap)).doesNotContain("private-case-principal", "runtime-private-marker",
            "assertion-private-marker", "connection-private-marker", "identity-private-marker", "security-private-marker",
            "behavior-private-marker", "channel-private-marker", "support-issuer", "support-audience", "https://support-runtime.example.test");
        verify(assets).experiencePack("generic-workspace", "1.0.0");
    }

    @ParameterizedTest @ValueSource(strings = {"DRAFT", "DISABLED"})
    void inactiveInstallationsCannotIssueAnyRuntimeHeaders(String status) {
        installation.setStatus(AIWorkspaceInstallationStatus.valueOf(status));
        denied();
        verifyNoInteractions(consumers, authority, assets);
    }

    @Test void missingOrForeignInstallationIsNotReplacedWithASyntheticInstallation() {
        when(installations.findByInstallationId("awi-support")).thenReturn(Optional.empty());
        denied();
        when(installations.findByInstallationId("awi-support")).thenReturn(Optional.of(installation));
        installation.setCustomerId("foreign-owner");
        denied();
        verifyNoInteractions(consumers, authority, assets);
    }

    @Test void absentConfiguredInstallationDoesNotSelectAnArbitraryDefault() {
        environment.setProperty("platform.support.installation-id", "");
        denied();
        verify(installations).findByInstallationId("");
        verifyNoInteractions(consumers, authority, assets);
    }

    @Test void publicRuntimeInstallationCannotBeUsedAsThePrivateCaseAdapter() {
        installation.setConnectionMode(AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS);
        denied();
        verifyNoInteractions(consumers, authority, assets);
    }

    @Test void caseAuthorizationPrecedesAllInstallationAndAuthorityLookups() {
        when(cases.requireOwnedCase("sup-1")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not this requester"));
        assertThatThrownBy(() -> service.bootstrap("sup-1")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(installations, consumers, authority, assets);
    }

    @Test void assignmentResolverRejectsConsumerDeploymentOwnerMismatchAndUnverifiedRelease() {
        consumer.setCustomerId("foreign-owner");
        assertThatThrownBy(() -> service.resolve("sup-1")).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("does not belong");
        consumer.setCustomerId("support-owner");
        release.setVerificationStatus("FAILED");
        assertThatThrownBy(() -> service.resolve("sup-1")).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not verified");
        verifyNoInteractions(authority, assets);
    }

    @Test void frozenCaseDeploymentTargetAndTenantCannotFollowAnotherAssignment() {
        for (OwnedCase changed : List.of(
            owner("other-deployment", "support-staging", "support-owner", "support-tenant"),
            owner("support-deployment", "production", "support-owner", "support-tenant"),
            owner("support-deployment", "support-staging", "support-owner", "foreign-tenant"))) {
            when(cases.requireOwnedCase("sup-1")).thenReturn(changed);
            denied();
        }
        verifyNoInteractions(authority, assets);
    }

    @ParameterizedTest @ValueSource(strings = {"issuer", "audience"})
    void changedFrozenAssertionIssuerOrAudienceCannotIssuePrivateHeaders(String field) {
        frozenAuthority("support-owner", field.equals("issuer") ? "foreign-issuer" : "support-issuer",
            field.equals("audience") ? "foreign-audience" : "support-audience");
        denied();
        org.mockito.Mockito.verify(authority, org.mockito.Mockito.never())
            .issueReviewedIntegrationSessionHeaders(any(), any(), any(), any(), anyList());
        verifyNoInteractions(assets);
    }

    @Test void invalidStoredConfigurationCannotSerializeSecretMaterialIntoBootstrap() throws Exception {
        installation.setConfigurationJson("{\"app\":{\"assistantLabel\":\"Support\"},\"serverSecret\":\"stored-secret-marker\"}");
        // Normal installation writes already reject this. This covers emission from an invalid stored row,
        // not a claim that an ordinary customer can persist secret fields through the installation API.
        var validator = new AIWorkspaceConfigurationValidator(mock(PlatformAIWorkspaceProperties.class));
        var unsafe = mapper.readTree(installation.getConfigurationJson());
        assertThatThrownBy(() -> validator.validateGenericConfiguration(unsafe)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.bootstrap("sup-1")).isInstanceOf(ResponseStatusException.class);
    }

    @Test void realSettingsGuardRejectsChangedDeploymentOwnerBeforeAnOwnedCaseCanBeResolved() {
        environment.withProperty("platform.support.enabled", "true")
            .withProperty("platform.support.customer-id", "support-owner")
            .withProperty("platform.support.tenant-id", "support-tenant")
            .withProperty("platform.support.deployment-id", "support-deployment")
            .withProperty("platform.support.target-profile-id", "support-staging")
            .withProperty("platform.support.assertion-issuer", "support-issuer")
            .withProperty("platform.support.assertion-audience", "support-audience");
        when(targets.existsById("support-staging")).thenReturn(true);
        var settings = new SupportSettings(environment, deployments, targets);
        assertThat(settings.binding().customerId()).isEqualTo("support-owner");
        consumer.setCustomerId("foreign-owner");
        deployment.setCustomerId("foreign-owner");
        assertThatThrownBy(settings::binding).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(authority, assets);
    }

    @Test void crossOriginConsoleCanSendTheWidgetReplayCursorOnSseReconnect() {
        // Console and Platform API are separate production origins; reconnect adds this header.
        assertThat(new PlatformCorsProperties().getAllowedHeaders()).contains("Authorization", "Last-Event-ID", "Idempotency-Key");
    }

    private void denied() {
        assertThatThrownBy(() -> service.resolve("sup-1")).isInstanceOfSatisfying(ResponseStatusException.class,
            failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
    private void frozenAuthority(String customerId, String issuer, String audience) {
        var binding = new RuntimeAuthorityBinding(customerId, "support-tenant", "support-deployment", "support-staging",
            "support-version", "reviewed-config-hash", 1, issuer, audience, "public-issuer", "public-audience", false, false,
            List.of("https://console.example.test"), Map.of());
        when(authority.requireReleaseUnchangedForProvisioning(any(), eq(version), eq(release)))
            .thenReturn(new RuntimeAuthorityBinding.Snapshot("snapshot", "support-release", "fingerprint", binding));
    }
    private static OwnedCase owner(String deploymentId, String targetId, String customerId, String tenantId) {
        return new OwnedCase("sup-1", "SUP-1", deploymentId, targetId, customerId, tenantId, "private-case-principal",
            "chat-1", "episode-1", 1, false, "initial-key", "Immutable intake", DeliveryStatus.PENDING, CaseStatus.OPEN,
            "support-issuer", "support-audience");
    }
    private static AIWorkspaceAssetDescriptor asset(String role, String code, String requestPath) {
        return new AIWorkspaceAssetDescriptor(role, code, "1.0.0", requestPath, "fixture.js", "0".repeat(64), "sha384-fixture", 1);
    }
}
