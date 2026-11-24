package com.ai.fabric.platform.backend.assistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import com.ai.fabric.platform.backend.tenant.repository.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

class ManagedAssistantRuntimeTest {
    ObjectMapper mapper = new ObjectMapper(); AuthoringProperties properties = new AuthoringProperties();
    DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
    DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
    CoolifyApiClient coolify = mock(CoolifyApiClient.class);
    ManagedAssistantRuntime service;
    DeploymentEntity brain = new DeploymentEntity(); DeploymentVersionEntity version = new DeploymentVersionEntity();
    DeploymentReleaseEntity release = new DeploymentReleaseEntity(); JsonNode budget;
    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        properties.setEnabled(true); properties.setRuntimeUrl("https://assistant.example.test");
        service = spy(new ManagedAssistantRuntime(properties, handles, mock(DeploymentTargetProfileService.class), mock(DeploymentTargetProfileRepository.class),
            mock(CoolifyTargetProfileResolver.class), coolify, mock(PlatformSecretService.class), mock(RuntimePrivateAssertionSigningService.class),
            authority, mock(RailwayProvisioningPlanService.class), mock(ReleaseArtifactResolver.class), mock(ObjectProvider.class),
            new ConfigurationAssistantTemplate(mapper), mock(PlatformAuditService.class), mapper, mock(PlatformTransactionManager.class),
            mock(PlatformCustomerRepository.class), mock(PlatformTenantRepository.class), "https://platform.example.test"));
        brain.setId(properties.getDeploymentId()); brain.setCustomerId(properties.getCustomerId()); brain.setTenantId(properties.getTenantId());
        version.setId("version"); version.setDeploymentId(brain.getId()); version.setAiFabricFrameworkVersion("0.8.68");
        version.setBehaviorConfigJson("{\"workflowAssistance\":{\"enabled\":true,\"contentHash\":\"sha256:fixture\"}}");
        release.setId("release"); release.setDeploymentId(brain.getId()); release.setDeploymentVersionId(version.getId()); release.setTargetProfileId("configuration-authoring-staging");
        budget = mapper.readTree("""
            {"state":"ACTIVE","customerId":"loomai-internal","deploymentId":"loomai-configuration-assistant",
            "targetProfileId":"configuration-authoring-staging","environment":"staging","storeUuid":"original-store",
            "approvalId":"original-approval","runtimeFingerprint":"original-fingerprint","limits":{"chatAttempts":100,"totalTokens":2000000,"costNanoUsd":5000000000},
            "effectiveLimits":{"chatAttempts":185,"totalTokens":2000000,"costNanoUsd":5000000000},"expiresAt":"2027-01-01T00:00:00Z",
            "effectiveExpiresAt":"2027-01-01T00:00:00Z","chatAttempts":166,"providerAttempts":166,"stopped":false}
            """);
        var handle = new DeploymentProviderResourceHandleEntity(); handle.setStatus("BOUND"); handle.setFqdn(properties.getRuntimeUrl());
        handle.setMetadataJson(mapper.createObjectNode().set("budget", budget).toString());
        when(handles.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(brain.getId(), release.getTargetProfileId(), PreservedExternalBindingKinds.MANAGED_ASSISTANT)).thenReturn(Optional.of(handle));
        when(authority.issueReleaseVerificationHeaders(brain, release, false)).thenReturn(Map.of("private", "fixture"));
        doReturn(mapper.readTree("{\"workflowAssistance\":{\"enabled\":true,\"ready\":true,\"contentHash\":\"sha256:fixture\"},\"deploymentVersionId\":\"version\",\"aiFabricFrameworkVersion\":\"0.8.68\"}"))
            .when(service).read(eq("/api/admin/overview"), anyMap());
        doReturn(budget.deepCopy()).when(service).read(eq("/api/admin/provider-budget"), anyMap());
    }
    @Test void onlyExactInternalOwnerCanUseExistingServiceLifecycle() {
        assertThat(service.matches(brain)).isTrue(); properties.setEnabled(false); assertThat(service.matches(brain)).isTrue();
        brain.setCustomerId("customer-other"); assertThatThrownBy(() -> service.matches(brain)).hasMessageContaining("BINDING_CHANGED");
        assertThatThrownBy(() -> service.requireInstalled(brain, release)).hasMessageContaining("BINDING_CHANGED");
    }
    @Test void installationNeedsAnAdministratorBeforeProviderOrFundingAccess() {
        assertThatThrownBy(() -> service.install(new ManagedAssistantRuntime.Install("target", "application", "store", "fingerprint")))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(coolify); verify(service, never()).read(anyString(), anyMap());
    }
    @Test void existingPrivateServiceDoesNotNeedANewPublicRoute() {
        assertThat(ManagedAssistantRuntime.installedRuntimeAddress("http://loomai-authoring-runtime:8097", null))
            .isEqualTo("http://loomai-authoring-runtime:8097");
        assertThat(ManagedAssistantRuntime.installedRuntimeAddress("https://assistant.example.test", "https://assistant.example.test"))
            .isEqualTo("https://assistant.example.test");
        for (String invalid : List.of("http://public.example:8097", "http://127.0.0.1:8097", "http://localhost:8097",
            "http://loomai-authoring-runtime:8097/other", "http://user@loomai-authoring-runtime:8097", "http://loomai-authoring-runtime:8097?override=1"))
            assertThatThrownBy(() -> ManagedAssistantRuntime.installedRuntimeAddress(invalid, null)).hasMessageContaining("BINDING_CHANGED");
        assertThatThrownBy(() -> ManagedAssistantRuntime.installedRuntimeAddress("https://other.example.test", "https://assistant.example.test"))
            .hasMessageContaining("BINDING_CHANGED");
    }
    @Test void existingWalletMayAccrueUsageWithoutBeingReinitialized() {
        ((com.fasterxml.jackson.databind.node.ObjectNode) budget).put("chatAttempts", 167).put("providerAttempts", 172);
        doReturn(budget).when(service).read(eq("/api/admin/provider-budget"), anyMap());
        service.verifyRuntime(brain, version, release); verifyNoInteractions(coolify);
        verify(authority).issueReleaseVerificationHeaders(brain, release, false);
    }
    @Test void changedStoreOrResetCounterOrHigherCapCannotPassAdoptionVerification() {
        for (String change : List.of("store", "counter", "cap")) {
            var altered = budget.deepCopy();
            if (change.equals("store")) ((com.fasterxml.jackson.databind.node.ObjectNode) altered).put("storeUuid", "new-wallet");
            if (change.equals("counter")) ((com.fasterxml.jackson.databind.node.ObjectNode) altered).put("chatAttempts", 0);
            if (change.equals("cap")) ((com.fasterxml.jackson.databind.node.ObjectNode) altered.path("effectiveLimits")).put("costNanoUsd", 20000000000L);
            doReturn(altered).when(service).read(eq("/api/admin/provider-budget"), anyMap());
            assertThatThrownBy(() -> service.verifyRuntime(brain, version, release)).hasMessageContaining("BINDING_CHANGED");
        }
    }
    @Test void wrongPublishedInstructionsOrUnavailableRegistryNeverProduceVerifiedRelease() {
        doReturn(mapper.createObjectNode().put("deploymentVersionId", "other-version")).when(service).read(eq("/api/admin/overview"), anyMap());
        assertThat(service.verify(brain, version, release, "POST_APPLY").getStatus()).isEqualTo("FAILED");
    }
    @Test void purposeSpecificClientsCannotSilentlySwitchToAnotherPaidProviderAccount() {
        var environment = new LinkedHashMap<String, String>();
        environment.put("AI_PROVIDERS_GENERATION_API_KEY", "secret:unrelated-platform-key");
        ManagedAssistantRuntime.preserveProviderCredential(environment, "original-provider-fixture");
        assertThat(environment).containsEntry("AI_PROVIDERS_OPENAI_API_KEY", "original-provider-fixture")
            .containsEntry("AI_PROVIDERS_ORCHESTRATION_API_KEY", "original-provider-fixture")
            .containsEntry("AI_PROVIDERS_GENERATION_API_KEY", "original-provider-fixture");
        assertThatThrownBy(() -> ManagedAssistantRuntime.preserveProviderCredential(environment, ""))
            .hasMessageContaining("BINDING_CHANGED");
    }
    @Test void intentionallyDisabledWorkflowCanBeVerifiedWithoutReactivatingIt() throws Exception {
        version.setBehaviorConfigJson("{\"workflowAssistance\":{\"enabled\":false}}");
        doReturn(mapper.readTree("{\"workflowAssistance\":{\"enabled\":false,\"ready\":true},\"deploymentVersionId\":\"version\",\"aiFabricFrameworkVersion\":\"0.8.68\"}"))
            .when(service).read(eq("/api/admin/overview"), anyMap());
        service.verifyRuntime(brain, version, release);
    }
}
