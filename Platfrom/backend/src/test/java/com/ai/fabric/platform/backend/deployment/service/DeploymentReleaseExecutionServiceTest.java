package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.admission.AdmissionReleaseCoordinator;
import com.ai.fabric.platform.backend.deployment.budget.ProviderUsageReleaseCoordinator;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyReleaseCoordinator;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceStatusSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorRegistrySummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceDatasetSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeploymentReleaseExecutionServiceTest {

    @Test void explicitPostProvisionContinuationSkipsProvisioningAndFundingAndFinishesOnlyOnce() {
        var fixture = new BindingApplyFixture(); var nativeProvider = fixture.prepareContinuation();
        fixture.service.runApply("dep-123", "ver-123", "rel-123");
        verify(nativeProvider).postProvisionResumeReadback(eq("dep-123"), eq("target-stage"), eq("rel-123"), any(), any());
        verify(fixture.admission).beginProvisioning("rel-123");
        verify(fixture.budget).verifyActive(fixture.deployment, fixture.version, fixture.release);
        verify(fixture.budget, never()).prepare(any(), any()); verify(fixture.budget, never()).initializeAndActivate(any(), any(), any());
        verify(fixture.dependencies, never()).prepareFoundation(any(), any(), any());
        verify(fixture.dependencies).resumeRuntimeBound(fixture.deployment, fixture.version, fixture.release, "a".repeat(64));
        verifyNoInteractions(fixture.provisioning);
        verify(fixture.datasets).syncReleaseDatasets(fixture.deployment, fixture.version, fixture.release);
        assertThat(fixture.release.getStatus()).isEqualTo("APPLIED_VERIFIED");
        fixture.service.runApply("dep-123", "ver-123", "rel-123");
        verify(fixture.dependencies, org.mockito.Mockito.times(1)).resumeRuntimeBound(any(), any(), any(), any());
    }

    @Test void continuationRechecksNativeStateBeforeAnyDependencyOrFinancialWork() {
        var fixture = new BindingApplyFixture(); var nativeProvider = fixture.prepareContinuation();
        org.mockito.Mockito.doThrow(new IllegalStateException("NATIVE_DRIFT")).when(nativeProvider)
            .postProvisionResumeReadback(any(), any(), any(), any(), any());
        assertThatThrownBy(() -> fixture.service.runApply("dep-123", "ver-123", "rel-123")).hasMessage("NATIVE_DRIFT");
        fixture.assertNoProviderEffects();
    }

    @Test void continuationCannotCreateDependenciesWhenTheExistingWalletIsLost() {
        var fixture = new BindingApplyFixture(); fixture.prepareContinuation();
        org.mockito.Mockito.doThrow(new IllegalStateException("WALLET_LOST")).when(fixture.budget).verifyActive(any(), any(), any());
        assertThatThrownBy(() -> fixture.service.runApply("dep-123", "ver-123", "rel-123")).hasMessage("WALLET_LOST");
        verifyNoInteractions(fixture.provisioning, fixture.dependencies, fixture.datasets);
        verify(fixture.budget, never()).initializeAndActivate(any(), any(), any());
    }

    @Test void malformedContinuationCannotFallBackToFullNativeApply() {
        var fixture = new BindingApplyFixture(); fixture.prepareContinuation(); fixture.release.setProvisioningDetailsJson("{\"postProvisionResume\":{}}");
        assertThatThrownBy(() -> fixture.service.runApply("dep-123", "ver-123", "rel-123")).hasMessage("POST_PROVISION_RESUME_AUTHORITY_REQUIRED");
        fixture.assertNoProviderEffects();
    }

    @Test void externalIntegrationWorkerUsesTypedAuthorityBeforeAdmissionBudgetAndProviderEffects() {
        var fixture = new BindingApplyFixture();
        fixture.version.setCompositionProvenanceJson("{\"externalIntegrationProfiles\":[{\"profileId\":\"reviewed\"}]}");
        var external = mock(com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationResolver.class);
        ReflectionTestUtils.setField(fixture.service, "externalIntegrations", external);
        var denial = new IllegalStateException("EXACT_FROZEN_RELEASE_CHANGED");
        org.mockito.Mockito.doThrow(denial).when(external).releaseEnvironmentForProvisioning(fixture.deployment, fixture.version, fixture.release);
        assertThatThrownBy(() -> fixture.service.runApply("dep-123", "ver-123", "rel-123")).isSameAs(denial);
        verify(external).releaseEnvironmentForProvisioning(fixture.deployment, fixture.version, fixture.release);
        verify(external, never()).releaseEnvironment(any(), any(), any(), any(), any());
        fixture.assertNoProviderEffects();
    }

    @Test
    void customerSqlBindingDriftBlocksBeforeAdmissionBudgetOrProviderEffects() {
        var fixture = new BindingApplyFixture();
        ((com.fasterxml.jackson.databind.node.ObjectNode) fixture.providerConfig).put("aiStateStorageMode", "CUSTOMER_POSTGRES");
        fixture.version.setProviderConfigJson(fixture.providerConfig.toString());
        var sql = mock(DeploymentAiStateStorageBindingService.class);
        ReflectionTestUtils.setField(fixture.service, "aiStateStorageBindings", sql);
        var failure = new IllegalStateException("AI_STATE_STORAGE_RELEASE_BINDING_CHANGED");
        org.mockito.Mockito.doThrow(failure).when(sql).requireUnchanged(fixture.deployment, fixture.version, fixture.release);
        assertThatThrownBy(() -> fixture.service.runApply("dep-123", "ver-123", "rel-123")).isSameAs(failure);
        var order = inOrder(sql);
        order.verify(sql).requireForApply(fixture.deployment, fixture.version, "target-stage");
        order.verify(sql).requireUnchanged(fixture.deployment, fixture.version, fixture.release);
        verifyNoInteractions(fixture.bindings);
        fixture.assertNoProviderEffects();
    }

    @Test
    void explicitSqlStorageCannotProceedWithoutItsValidator() {
        var fixture = new BindingApplyFixture();
        ((com.fasterxml.jackson.databind.node.ObjectNode) fixture.providerConfig).put("aiStateStorageMode", "MANAGED_POSTGRES");
        fixture.version.setProviderConfigJson(fixture.providerConfig.toString());
        assertThatThrownBy(() -> fixture.service.runApply("dep-123", "ver-123", "rel-123"))
            .hasMessage("AI_STATE_STORAGE_BINDING_SERVICE_REQUIRED");
        verifyNoInteractions(fixture.bindings);
        fixture.assertNoProviderEffects();
    }

    @Test
    void tokenArtifactFailurePrecedesBindingReadsAdmissionDebitAndProviderEffects() {
        var fixture = new BindingApplyFixture();
        ((com.fasterxml.jackson.databind.node.ObjectNode) fixture.providerConfig).put("milvusAuthenticationType", "TOKEN");
        fixture.version.setProviderConfigJson(fixture.providerConfig.toString());
        fixture.release.setSourceArtifactId("reviewed-runtime");
        var gate = mock(MilvusTokenRuntimeArtifactGate.class);
        ReflectionTestUtils.setField(fixture.service, "milvusTokenArtifacts", gate);
        var failure = new IllegalStateException("MILVUS_TOKEN_RUNTIME_ARTIFACT_REQUIRED");
        org.mockito.Mockito.doThrow(failure).when(gate).require(fixture.version, fixture.providerConfig, "reviewed-runtime");
        assertThatThrownBy(() -> fixture.service.runApply("dep-123", "ver-123", "rel-123")).isSameAs(failure);
        verifyNoInteractions(fixture.bindings);
        fixture.assertNoProviderEffects();
    }

    @Test
    void tokenReleaseWithoutArtifactGateCannotProceedToProviderEffects() {
        var fixture = new BindingApplyFixture();
        ((com.fasterxml.jackson.databind.node.ObjectNode) fixture.providerConfig).put("milvusAuthenticationType", "TOKEN");
        fixture.version.setProviderConfigJson(fixture.providerConfig.toString());
        assertThatThrownBy(() -> fixture.service.runApply("dep-123", "ver-123", "rel-123"))
            .isInstanceOf(IllegalStateException.class).hasMessage("MILVUS_TOKEN_ARTIFACT_GATE_REQUIRED");
        verifyNoInteractions(fixture.bindings);
        fixture.assertNoProviderEffects();
    }

    @Test
    void resolvedExternalEndpointIsPersistedInReleaseAndSharedScopeInventory() throws Exception {
        var fixture = new BindingApplyFixture();
        var resolved = ((com.fasterxml.jackson.databind.node.ObjectNode) fixture.providerConfig.deepCopy())
            .put("milvusHost", "approved.cluster.zillizcloud.com").put("milvusDatabaseName", "default");
        when(fixture.bindings.resolve(fixture.deployment, fixture.version, fixture.providerConfig, "target-stage")).thenReturn(resolved);
        var scoped = (DeploymentTenantScopedVectorService) ReflectionTestUtils.getField(fixture.service, "deploymentTenantScopedVectorService");
        ReflectionTestUtils.invokeMethod(fixture.service, "applyProvisioningResult", "dep-123", "ver-123", "rel-123",
            new ProvisioningResult("ACTIVE", "COOLIFY", "https://runtime.example", "https://connector.example", "{}"));
        verify(scoped).build(fixture.deployment, resolved, "target-stage");
        var details = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(fixture.progress).mergeProvisioningDetails(eq("rel-123"), details.capture());
        assertThat(fixture.mapper.readTree(details.getValue()).path("effectiveProviderConfig").path("milvusHost").asText())
            .isEqualTo("approved.cluster.zillizcloud.com");
        assertThat(fixture.version.getProviderConfigJson()).isEqualTo(fixture.providerConfig.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXTERNAL_VECTOR_BINDING_REQUIRED", "EXTERNAL_VECTOR_RUNTIME_CREDENTIALS_REQUIRED",
        "EXTERNAL_VECTOR_PROVIDER_IDENTITY_CHANGED"})
    void externalBindingFailurePrecedesAdmissionGrantDebitAndProviderEffects(String code) {
        var fixture = new BindingApplyFixture();
        var denial = new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, code);
        when(fixture.bindings.resolve(fixture.deployment, fixture.version, fixture.providerConfig, "target-stage"))
            .thenThrow(denial);

        assertThatThrownBy(() -> fixture.service.runApply("dep-123", "ver-123", "rel-123")).isSameAs(denial);

        verify(fixture.bindings).resolve(fixture.deployment, fixture.version, fixture.providerConfig, "target-stage");
        fixture.assertNoProviderEffects();
    }

    @Test
    void externalBindingRequiredWithoutServiceFailsBeforeAdmissionOrProviderEffects() {
        var fixture = new BindingApplyFixture();
        ReflectionTestUtils.setField(fixture.service, "existingVectorBindings", null);

        assertThatThrownBy(() -> fixture.service.runApply("dep-123", "ver-123", "rel-123"))
            .isInstanceOf(IllegalStateException.class).hasMessage("EXTERNAL_VECTOR_BINDING_SERVICE_REQUIRED");

        verifyNoInteractions(fixture.bindings);
        fixture.assertNoProviderEffects();
    }

    @ParameterizedTest
    @ValueSource(strings = {"version-deployment", "release-version", "unpublished", "target"})
    void externalBindingRequiresExactPublishedReleaseScopeBeforeEffects(String change) {
        var fixture = new BindingApplyFixture();
        switch (change) {
            case "version-deployment" -> fixture.version.setDeploymentId("foreign-deployment");
            case "release-version" -> fixture.release.setDeploymentVersionId("foreign-version");
            case "unpublished" -> fixture.version.setStatus("DRAFT");
            case "target" -> fixture.release.setTargetProfileId(null);
        }

        assertThatThrownBy(() -> fixture.service.runApply("dep-123", "ver-123", "rel-123"))
            .isInstanceOf(IllegalStateException.class).hasMessage("EXTERNAL_VECTOR_RELEASE_SCOPE_CHANGED");

        verifyNoInteractions(fixture.bindings);
        fixture.assertNoProviderEffects();
    }

    @Test
    void externalBindingResolvesPublishedConfigAndExactTargetBeforeAdmissionAndGrantDebit() {
        var fixture = new BindingApplyFixture();
        var reachedProvisioning = new IllegalStateException("test provisioning boundary");
        when(fixture.provisioning.provision(any(), any(), any(), any())).thenThrow(reachedProvisioning);

        assertThatThrownBy(() -> fixture.service.runApply("dep-123", "ver-123", "rel-123"))
            .isSameAs(reachedProvisioning);

        var order = inOrder(fixture.bindings, fixture.admission, fixture.budget, fixture.dependencies, fixture.provisioning);
        order.verify(fixture.bindings).resolve(fixture.deployment, fixture.version, fixture.providerConfig, "target-stage");
        order.verify(fixture.admission).beginProvisioning("rel-123");
        order.verify(fixture.budget).prepare(fixture.deployment, fixture.release);
        order.verify(fixture.dependencies).prepareFoundation(fixture.deployment, fixture.version, fixture.release);
        order.verify(fixture.provisioning).provision(any(), any(), any(), any());
    }

    @Test
    void ordinaryReleaseDoesNotResolveExternalBinding() {
        var fixture = new BindingApplyFixture();
        fixture.version.setProviderConfigJson("{\"vectorStrategy\":\"lucene\"}");
        var reachedProvisioning = new IllegalStateException("test provisioning boundary");
        when(fixture.provisioning.provision(any(), any(), any(), any())).thenThrow(reachedProvisioning);

        assertThatThrownBy(() -> fixture.service.runApply("dep-123", "ver-123", "rel-123"))
            .isSameAs(reachedProvisioning);

        verifyNoInteractions(fixture.bindings);
        verify(fixture.admission).beginProvisioning("rel-123");
        verify(fixture.budget).prepare(fixture.deployment, fixture.release);
        verify(fixture.dependencies).prepareFoundation(fixture.deployment, fixture.version, fixture.release);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXTERNAL_DOCUMENT_STORAGE_BINDING", "EXTERNAL_VECTOR_STORAGE_BINDING"})
    void refreshRetainedExternalBindingsNeverCallsProviderStatus(String kind) {
        var fixture = new BindingApplyFixture();
        var handle = providerHandle("dep-123", null, "ACTIVE");
        handle.setResourceKind(kind);
        handle.setFqdn("https://retained.example");
        when(fixture.handles.findByDeploymentIdOrderByUpdatedAtDesc("dep-123")).thenReturn(List.of(handle));

        fixture.service.refreshProviderResourceHandlesAfterProvisioning("dep-123", "rel-123");

        verifyNoInteractions(fixture.providers);
        verify(fixture.handles, never()).save(any());
        assertThat(handle.getStatus()).isEqualTo("ACTIVE");
        assertThat(handle.getFqdn()).isEqualTo("https://retained.example");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"EXTERNAL_VECTOR_STORAGE_BINDING_V2", "UNKNOWN_RESOURCE"})
    void refreshDoesNotSilentlyTreatUnknownKindsAsRetained(String kind) {
        var fixture = new BindingApplyFixture();
        var handle = providerHandle("dep-123", null, "PENDING");
        handle.setResourceKind(kind);
        var provider = mock(DeploymentProvisioningProvider.class);
        when(fixture.handles.findByDeploymentIdOrderByUpdatedAtDesc("dep-123")).thenReturn(List.of(handle));
        when(fixture.providers.require(DeploymentProviderType.COOLIFY)).thenReturn(provider);
        when(provider.status(handle)).thenThrow(new IllegalStateException("unsupported resource kind"));

        fixture.service.refreshProviderResourceHandlesAfterProvisioning("dep-123", "rel-123");

        verify(provider).status(handle);
        verify(fixture.handles, never()).save(any());
        assertThat(handle.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void runApplyStopsBeforeProvisioningWhenPreflightVerificationFails() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);
        DeploymentProvisioningService deploymentProvisioningService = mock(DeploymentProvisioningService.class);
        DeploymentReleaseProgressService deploymentReleaseProgressService = mock(DeploymentReleaseProgressService.class);
        DeploymentReleaseVerificationService deploymentReleaseVerificationService = mock(DeploymentReleaseVerificationService.class);
        DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
        DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService = mock(DeploymentTenantScopedVectorRegistryService.class);
        MarketplaceDatasetSyncService marketplaceDatasetSyncService = mock(MarketplaceDatasetSyncService.class);
        when(marketplaceDatasetSyncService.syncReleaseDatasets(any(), any(), any()))
            .thenReturn(new MarketplaceDatasetSyncService.DatasetSyncSummary(0, 0, 0, List.of()));

        DeploymentReleaseExecutionService service = new DeploymentReleaseExecutionService(
            deploymentRepository,
            versionRepository,
            releaseRepository,
            verificationRunRepository,
            deploymentProvisioningService,
            deploymentReleaseProgressService,
            deploymentReleaseVerificationService,
            deploymentTenantScopedVectorService,
            deploymentTenantScopedVectorRegistryService,
            marketplaceDatasetSyncService,
            Runnable::run,
            TransactionOperations.withoutTransaction(),
            new ObjectMapper()
        );
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setName("Sample Commerce Dev");
        deployment.setEnvironmentName("dev");
        deployment.setTemplateId("dev-openai-lucene");
        deployment.setStatus("APPLY_REQUESTED");
        deployment.setActiveVersionId(null);
        deployment.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setDeploymentId("dep-123");
        version.setVersionLabel("v1");
        version.setStatus("PUBLISHED");
        version.setConfigHash("hash-123");
        version.setPublishedAt(Instant.parse("2026-03-31T00:00:00Z"));

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("APPLY_REQUESTED");
        release.setVerificationStatus("PENDING");
        release.setProvisioningStatus("QUEUED");
        release.setProvisioningTarget("RAILWAY_API");
        release.setCurrentStepKey("queue_release");
        release.setCurrentStepDescription("Apply request accepted and queued.");
        release.setProvisioningDetailsJson("{\"progress\":{\"steps\":[]}}");
        release.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        release.setAppliedAt(Instant.parse("2026-03-31T00:00:00Z"));
        release.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));

        DeploymentVerificationRunEntity failedPreflight = new DeploymentVerificationRunEntity();
        failedPreflight.setId("vrf-123");
        failedPreflight.setDeploymentId("dep-123");
        failedPreflight.setReleaseId("rel-123");
        failedPreflight.setDeploymentVersionId("ver-123");
        failedPreflight.setVerificationType("PRE_APPLY");
        failedPreflight.setStatus("FAILED");
        failedPreflight.setSummaryMessage("2 passed, 1 failed, 0 skipped");
        failedPreflight.setChecksJson("[]");
        failedPreflight.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        failedPreflight.setCompletedAt(Instant.parse("2026-03-31T00:00:01Z"));

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(versionRepository.findById("ver-123")).thenReturn(Optional.of(version));
        when(releaseRepository.findById("rel-123")).thenReturn(Optional.of(release));
        when(releaseRepository.findByIdForUpdate("rel-123")).thenReturn(Optional.of(release));
        when(deploymentRepository.save(any(DeploymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(releaseRepository.save(any(DeploymentReleaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationRunRepository.save(any(DeploymentVerificationRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentReleaseVerificationService.verify(deployment, version, release, "PRE_APPLY")).thenReturn(failedPreflight);

        var admission = mock(com.ai.fabric.platform.backend.deployment.admission.AdmissionReleaseCoordinator.class);
        ReflectionTestUtils.setField(service, "admissionReleaseCoordinator", admission);
        service.runApply("dep-123", "ver-123", "rel-123");
        verify(admission, never()).beginProvisioning(any());
        verify(admission).observe("rel-123");

        verify(deploymentProvisioningService, never()).provision(any(), any(), any(), any());
        assertThat(release.getStatus()).isEqualTo("PRE_APPLY_BLOCKED");
        assertThat(release.getProvisioningStatus()).isEqualTo("BLOCKED");
        assertThat(release.getVerificationStatus()).isEqualTo("FAILED");
        assertThat(release.getVerificationRunId()).isEqualTo("vrf-123");
        assertThat(deployment.getStatus()).isEqualTo("VERSION_PUBLISHED");
    }

    @Test
    void markActivationUnconfirmedKeepsReleaseRecoverable() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);
        DeploymentProvisioningService deploymentProvisioningService = mock(DeploymentProvisioningService.class);
        DeploymentReleaseProgressService deploymentReleaseProgressService = new DeploymentReleaseProgressService(
            releaseRepository,
            new com.fasterxml.jackson.databind.ObjectMapper()
        );
        DeploymentReleaseVerificationService deploymentReleaseVerificationService = mock(DeploymentReleaseVerificationService.class);
        DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
        DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService = mock(DeploymentTenantScopedVectorRegistryService.class);
        MarketplaceDatasetSyncService marketplaceDatasetSyncService = mock(MarketplaceDatasetSyncService.class);
        when(marketplaceDatasetSyncService.syncReleaseDatasets(any(), any(), any()))
            .thenReturn(new MarketplaceDatasetSyncService.DatasetSyncSummary(0, 0, 0, List.of()));

        DeploymentReleaseExecutionService service = new DeploymentReleaseExecutionService(
            deploymentRepository,
            versionRepository,
            releaseRepository,
            verificationRunRepository,
            deploymentProvisioningService,
            deploymentReleaseProgressService,
            deploymentReleaseVerificationService,
            deploymentTenantScopedVectorService,
            deploymentTenantScopedVectorRegistryService,
            marketplaceDatasetSyncService,
            Runnable::run,
            TransactionOperations.withoutTransaction(),
            new ObjectMapper()
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setStatus("PROVISIONING");
        deployment.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("PROVISIONING");
        release.setVerificationStatus("PENDING");
        release.setProvisioningStatus("RUNNING");
        release.setCurrentStepKey("wait_for_active");
        release.setCurrentStepDescription("Wait for Railway deployment states to become active.");
        release.setProvisioningDetailsJson("""
            {
              "progress": {
                "currentStepKey": "wait_for_active",
                "currentStepDescription": "Wait for Railway deployment states to become active.",
                "currentStepStatus": "RUNNING",
                "steps": [
                  {
                    "key": "wait_for_active",
                    "description": "Wait for Railway deployment states to become active.",
                    "status": "RUNNING",
                    "startedAt": "2026-03-31T00:00:00Z"
                  }
                ]
              }
            }
            """);
        release.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        release.setAppliedAt(Instant.parse("2026-03-31T00:00:00Z"));
        release.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));

        when(releaseRepository.findById("rel-123")).thenReturn(Optional.of(release));
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(releaseRepository.save(any(DeploymentReleaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentRepository.save(any(DeploymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.markActivationUnconfirmed(
            "rel-123",
            "dep-123",
            new RailwayActivationUnconfirmedException("Timed out waiting for Railway deployment dep-123 to become active.")
        );

        assertThat(release.getStatus()).isEqualTo("PROVISIONING");
        assertThat(release.getProvisioningStatus()).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(release.getVerificationStatus()).isEqualTo("PENDING");
        assertThat(release.getCurrentStepKey()).isEqualTo("wait_for_active");
        assertThat(release.getCurrentStepDescription()).contains("not confirmed yet");
        assertThat(release.getErrorMessage()).contains("Timed out waiting for Railway deployment");
        assertThat(release.getProvisioningDetailsJson()).contains("\"currentStepStatus\" : \"RUNNING\"");
        assertThat(deployment.getStatus()).isEqualTo("PROVISIONING");
    }

    @Test
    void applyProvisioningResultSyncsTenantScopedRegistryAfterSuccessfulProvisioning() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);
        DeploymentProvisioningService deploymentProvisioningService = mock(DeploymentProvisioningService.class);
        DeploymentReleaseProgressService deploymentReleaseProgressService = mock(DeploymentReleaseProgressService.class);
        DeploymentReleaseVerificationService deploymentReleaseVerificationService = mock(DeploymentReleaseVerificationService.class);
        DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
        DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService = mock(DeploymentTenantScopedVectorRegistryService.class);
        MarketplaceDatasetSyncService marketplaceDatasetSyncService = mock(MarketplaceDatasetSyncService.class);
        when(marketplaceDatasetSyncService.syncReleaseDatasets(any(), any(), any()))
            .thenReturn(new MarketplaceDatasetSyncService.DatasetSyncSummary(0, 0, 0, List.of()));

        DeploymentReleaseExecutionService service = new DeploymentReleaseExecutionService(
            deploymentRepository,
            versionRepository,
            releaseRepository,
            verificationRunRepository,
            deploymentProvisioningService,
            deploymentReleaseProgressService,
            deploymentReleaseVerificationService,
            deploymentTenantScopedVectorService,
            deploymentTenantScopedVectorRegistryService,
            marketplaceDatasetSyncService,
            Runnable::run,
            TransactionOperations.withoutTransaction(),
            new ObjectMapper()
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setCustomerId("cust-acme");
        deployment.setTenantId("ten-retail");
        deployment.setStatus("PROVISIONING");
        deployment.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setDeploymentId("dep-123");
        version.setProviderConfigJson("{\"vectorStrategy\":\"pinecone\",\"vectorProvisioningMode\":\"EXTERNAL_EXISTING\",\"vectorStoragePosture\":\"SHARED\"}");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("PROVISIONING");
        release.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        release.setAppliedAt(Instant.parse("2026-03-31T00:00:00Z"));
        release.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));

        DeploymentTenantScopedVectorSummary tenantSummary = new DeploymentTenantScopedVectorSummary(
            "READY",
            "pinecone",
            "EXTERNAL_EXISTING",
            "SHARED",
            true,
            "CUSTOMER_MANAGED_EXTERNAL_RESOURCE",
            "cust-acme",
            "Acme",
            "ten-retail",
            "Retail",
            "NAMESPACE_PREFIX",
            "Index",
            "shared-index",
            "cust-acme--ten-retail",
            null,
            "cust-acme--ten-retail__<entity-type>",
            true,
            "Migration locked",
            "Backup posture",
            new DeploymentTenantScopedVectorRegistrySummary("WARNING", null, 0, 0, null, "INFO", "Pending cleanup.", "Pending apply"),
            "Summary"
        );

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(versionRepository.findById("ver-123")).thenReturn(Optional.of(version));
        when(releaseRepository.findById("rel-123")).thenReturn(Optional.of(release));
        when(releaseRepository.save(any(DeploymentReleaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentRepository.save(any(DeploymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        release.setTargetProfileId("target-stage");
        when(deploymentTenantScopedVectorService.build(any(), any(), eq("target-stage"))).thenReturn(tenantSummary);

        ProvisioningResult provisioningResult = new ProvisioningResult(
            "RUNNING",
            "RAILWAY_API",
            "https://runtime.example",
            "https://connector.example",
            "{}"
        );

        service.applyProvisioningResult("dep-123", "ver-123", "rel-123", provisioningResult);

        verify(deploymentTenantScopedVectorRegistryService).syncResolvedHandle(deployment, version, release, tenantSummary);
        assertThat(deployment.getActiveVersionId()).isEqualTo("ver-123");
        assertThat(deployment.getRuntimeBaseUrl()).isEqualTo("https://runtime.example");
    }

    @Test
    void refreshProviderResourceHandlesAfterProvisioningPersistsFreshProviderStatus() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);
        DeploymentProvisioningService deploymentProvisioningService = mock(DeploymentProvisioningService.class);
        DeploymentReleaseProgressService deploymentReleaseProgressService = mock(DeploymentReleaseProgressService.class);
        DeploymentReleaseVerificationService deploymentReleaseVerificationService = mock(DeploymentReleaseVerificationService.class);
        DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
        DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService = mock(DeploymentTenantScopedVectorRegistryService.class);
        MarketplaceDatasetSyncService marketplaceDatasetSyncService = mock(MarketplaceDatasetSyncService.class);
        DeploymentProviderResourceHandleRepository resourceHandleRepository =
            mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentProviderRegistry providerRegistry = mock(DeploymentProviderRegistry.class);
        DeploymentProvisioningProvider provider = mock(DeploymentProvisioningProvider.class);

        DeploymentReleaseExecutionService service = new DeploymentReleaseExecutionService(
            deploymentRepository,
            versionRepository,
            releaseRepository,
            verificationRunRepository,
            deploymentProvisioningService,
            deploymentReleaseProgressService,
            deploymentReleaseVerificationService,
            deploymentTenantScopedVectorService,
            deploymentTenantScopedVectorRegistryService,
            marketplaceDatasetSyncService,
            resourceHandleRepository,
            providerRegistry,
            Runnable::run,
            TransactionOperations.withoutTransaction(),
            new ObjectMapper()
        );

        DeploymentProviderResourceHandleEntity handle = providerHandle("dep-123", "rel-123", "exited:unhealthy");
        when(resourceHandleRepository.findByDeploymentIdOrderByUpdatedAtDesc("dep-123")).thenReturn(List.of(handle));
        when(providerRegistry.require(DeploymentProviderType.COOLIFY)).thenReturn(provider);
        when(provider.status(handle)).thenReturn(new DeploymentProviderResourceStatusSummary(
            handle.getId(),
            DeploymentProviderType.COOLIFY,
            handle.getProviderResourceUuid(),
            "ACTIVE",
            "running:healthy",
            "runtime.example.test",
            new ObjectMapper().createObjectNode(),
            Instant.parse("2026-03-31T00:00:01Z")
        ));
        when(resourceHandleRepository.save(any(DeploymentProviderResourceHandleEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.refreshProviderResourceHandlesAfterProvisioning("dep-123", "rel-123");

        verify(resourceHandleRepository).save(handle);
        assertThat(handle.getStatus()).isEqualTo("ACTIVE");
        assertThat(handle.getLastObservedStatus()).isEqualTo("running:healthy");
        assertThat(handle.getFqdn()).isEqualTo("runtime.example.test");
    }

    @Test
    void executeApplyFallsBackInlineWhenExecutorRejectsDispatch() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);
        DeploymentProvisioningService deploymentProvisioningService = mock(DeploymentProvisioningService.class);
        DeploymentReleaseProgressService deploymentReleaseProgressService = mock(DeploymentReleaseProgressService.class);
        DeploymentReleaseVerificationService deploymentReleaseVerificationService = mock(DeploymentReleaseVerificationService.class);
        DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
        DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService = mock(DeploymentTenantScopedVectorRegistryService.class);
        MarketplaceDatasetSyncService marketplaceDatasetSyncService = mock(MarketplaceDatasetSyncService.class);
        when(marketplaceDatasetSyncService.syncReleaseDatasets(any(), any(), any()))
            .thenReturn(new MarketplaceDatasetSyncService.DatasetSyncSummary(0, 0, 0, List.of()));

        DeploymentReleaseExecutionService service = new DeploymentReleaseExecutionService(
            deploymentRepository,
            versionRepository,
            releaseRepository,
            verificationRunRepository,
            deploymentProvisioningService,
            deploymentReleaseProgressService,
            deploymentReleaseVerificationService,
            deploymentTenantScopedVectorService,
            deploymentTenantScopedVectorRegistryService,
            marketplaceDatasetSyncService,
            task -> {
                throw new TaskRejectedException("executor saturated");
            },
            TransactionOperations.withoutTransaction(),
            new ObjectMapper()
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setStatus("APPLY_REQUESTED");
        deployment.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setDeploymentId("dep-123");
        version.setProviderConfigJson("{\"provider\":\"pinecone\"}");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("APPLY_REQUESTED");
        release.setVerificationStatus("PENDING");
        release.setProvisioningStatus("QUEUED");
        release.setProvisioningTarget("RAILWAY_API");
        release.setCurrentStepKey("queue_release");
        release.setCurrentStepDescription("Apply request accepted and queued.");
        release.setProvisioningDetailsJson("{}");
        release.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        release.setAppliedAt(Instant.parse("2026-03-31T00:00:00Z"));
        release.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));

        DeploymentVerificationRunEntity preflight = new DeploymentVerificationRunEntity();
        preflight.setId("vrf-pre");
        preflight.setStatus("PASSED");
        preflight.setSummaryMessage("passed");

        DeploymentVerificationRunEntity postApply = new DeploymentVerificationRunEntity();
        postApply.setId("vrf-post");
        postApply.setStatus("PASSED");
        postApply.setSummaryMessage("passed");

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(versionRepository.findById("ver-123")).thenReturn(Optional.of(version));
        when(releaseRepository.findById("rel-123")).thenReturn(Optional.of(release));
        when(releaseRepository.findByIdForUpdate("rel-123")).thenReturn(Optional.of(release));
        when(deploymentRepository.save(any(DeploymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(releaseRepository.save(any(DeploymentReleaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationRunRepository.save(any(DeploymentVerificationRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentReleaseVerificationService.verify(deployment, version, release, "PRE_APPLY")).thenReturn(preflight);
        when(deploymentReleaseVerificationService.verify(deployment, version, release, "POST_APPLY")).thenReturn(postApply);
        when(deploymentProvisioningService.provision(any(), any(), any(), any())).thenReturn(
            new ProvisioningResult("ACTIVE", "RAILWAY_API", "https://runtime.example", "https://connector.example", "{}")
        );
        when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(
            new DeploymentTenantScopedVectorSummary(
                "READY",
                "pinecone",
                "EXTERNAL_EXISTING",
                "SHARED",
                true,
                "CUSTOMER_MANAGED_EXTERNAL_RESOURCE",
                "cust-acme",
                "Acme",
                "ten-retail",
                "Retail",
                "NAMESPACE_PREFIX",
                "Index",
                "shared-index",
                "cust-acme--ten-retail",
                null,
                "cust-acme--ten-retail__<entity-type>",
                true,
                "Migration locked",
                "Backup posture",
                new DeploymentTenantScopedVectorRegistrySummary("READY", null, 0, 0, null, "INFO", "Ready.", "Ready"),
                "Summary"
            )
        );

        ManagedDependencyReleaseCoordinator dependencyCoordinator = mock(ManagedDependencyReleaseCoordinator.class);
        ReflectionTestUtils.setField(service, "managedDependencyReleaseCoordinator", dependencyCoordinator);
        var admission = mock(com.ai.fabric.platform.backend.deployment.admission.AdmissionReleaseCoordinator.class);
        ReflectionTestUtils.setField(service, "admissionReleaseCoordinator", admission);
        service.executeApply("dep-123", "ver-123", "rel-123");

        var order = inOrder(admission, dependencyCoordinator, deploymentProvisioningService, marketplaceDatasetSyncService);
        order.verify(admission).beginProvisioning("rel-123");
        order.verify(dependencyCoordinator).prepareFoundation(deployment, version, release);
        order.verify(deploymentProvisioningService).provision(any(), any(), any(), any());
        order.verify(dependencyCoordinator).completeRuntimeBound(deployment, version, release);
        order.verify(marketplaceDatasetSyncService).syncReleaseDatasets(deployment, version, release);
        verify(marketplaceDatasetSyncService).syncReleaseDatasets(deployment, version, release);
        assertThat(release.getStatus()).isEqualTo("APPLIED_VERIFIED");
        assertThat(deployment.getStatus()).isEqualTo("ACTIVE");
        verify(admission, org.mockito.Mockito.atLeastOnce()).observe("rel-123");
    }

    @Test
    void runApplyNoopsWhenReleaseWasAlreadyClaimedByAnotherWorker() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);
        DeploymentProvisioningService deploymentProvisioningService = mock(DeploymentProvisioningService.class);
        DeploymentReleaseProgressService deploymentReleaseProgressService = mock(DeploymentReleaseProgressService.class);
        DeploymentReleaseVerificationService deploymentReleaseVerificationService = mock(DeploymentReleaseVerificationService.class);
        DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
        DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService = mock(DeploymentTenantScopedVectorRegistryService.class);
        MarketplaceDatasetSyncService marketplaceDatasetSyncService = mock(MarketplaceDatasetSyncService.class);
        when(marketplaceDatasetSyncService.syncReleaseDatasets(any(), any(), any()))
            .thenReturn(new MarketplaceDatasetSyncService.DatasetSyncSummary(0, 0, 0, List.of()));

        DeploymentReleaseExecutionService service = new DeploymentReleaseExecutionService(
            deploymentRepository,
            versionRepository,
            releaseRepository,
            verificationRunRepository,
            deploymentProvisioningService,
            deploymentReleaseProgressService,
            deploymentReleaseVerificationService,
            deploymentTenantScopedVectorService,
            deploymentTenantScopedVectorRegistryService,
            marketplaceDatasetSyncService,
            Runnable::run,
            TransactionOperations.withoutTransaction(),
            new ObjectMapper()
        );

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("PRE_APPLY_VERIFYING");
        release.setVerificationStatus("RUNNING");
        release.setProvisioningStatus("QUEUED");
        release.setCurrentStepKey("preflight_verification");
        release.setCurrentStepDescription("Running pre-apply verification gate.");

        when(releaseRepository.findByIdForUpdate("rel-123")).thenReturn(Optional.of(release));

        service.runApply("dep-123", "ver-123", "rel-123");

        verify(deploymentReleaseVerificationService, never()).verify(any(), any(), any(), any());
        verify(deploymentProvisioningService, never()).provision(any(), any(), any(), any());
        verify(deploymentRepository, never()).save(any(DeploymentEntity.class));
    }

    private static final class BindingApplyFixture {
        final DeploymentRepository deployments = mock(DeploymentRepository.class);
        final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
        final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
        final DeploymentVerificationRunRepository verifications = mock(DeploymentVerificationRunRepository.class);
        final DeploymentProvisioningService provisioning = mock(DeploymentProvisioningService.class);
        final DeploymentReleaseProgressService progress = mock(DeploymentReleaseProgressService.class);
        final DeploymentReleaseVerificationService verification = mock(DeploymentReleaseVerificationService.class);
        final MarketplaceDatasetSyncService datasets = mock(MarketplaceDatasetSyncService.class);
        final DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
        final DeploymentProviderRegistry providers = mock(DeploymentProviderRegistry.class);
        final DeploymentExistingVectorBindingService bindings = mock(DeploymentExistingVectorBindingService.class);
        final AdmissionReleaseCoordinator admission = mock(AdmissionReleaseCoordinator.class);
        final ProviderUsageReleaseCoordinator budget = mock(ProviderUsageReleaseCoordinator.class);
        final ManagedDependencyReleaseCoordinator dependencies = mock(ManagedDependencyReleaseCoordinator.class);
        final DeploymentEntity deployment = new DeploymentEntity();
        final DeploymentVersionEntity version = new DeploymentVersionEntity();
        final DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode providerConfig = mapper.createObjectNode().put("vectorStrategy", "milvus")
            .put("vectorProvisioningMode", "EXTERNAL_EXISTING").put("vectorStoragePosture", "SHARED")
            .put("milvusExternalBindingRequired", true);
        final DeploymentReleaseExecutionService service = new DeploymentReleaseExecutionService(
            deployments, versions, releases, verifications, provisioning, progress, verification,
            mock(DeploymentTenantScopedVectorService.class), mock(DeploymentTenantScopedVectorRegistryService.class),
            datasets, handles, providers, Runnable::run, TransactionOperations.withoutTransaction(), mapper);

        BindingApplyFixture() {
            deployment.setId("dep-123"); deployment.setCustomerId("customer-123"); deployment.setTenantId("tenant-123");
            deployment.setEnvironmentName("deployment-default"); deployment.setStatus("APPLY_REQUESTED");
            version.setId("ver-123"); version.setDeploymentId(deployment.getId()); version.setStatus("PUBLISHED");
            version.setConfigHash("hash-123"); version.setProviderConfigJson(providerConfig.toString());
            release.setId("rel-123"); release.setDeploymentId(deployment.getId()); release.setDeploymentVersionId(version.getId());
            release.setTargetProfileId("target-stage"); release.setStatus("APPLY_REQUESTED"); release.setCurrentStepKey("queue_release");
            var preflight = new DeploymentVerificationRunEntity(); preflight.setId("vrf-pre"); preflight.setStatus("PASSED");
            when(deployments.findById(deployment.getId())).thenReturn(Optional.of(deployment));
            when(versions.findById(version.getId())).thenReturn(Optional.of(version));
            when(releases.findById(release.getId())).thenReturn(Optional.of(release));
            when(releases.findByIdForUpdate(release.getId())).thenReturn(Optional.of(release));
            when(verification.verify(deployment, version, release, "PRE_APPLY")).thenReturn(preflight);
            when(bindings.resolve(deployment, version, providerConfig, "target-stage")).thenReturn(providerConfig);
            ReflectionTestUtils.setField(service, "existingVectorBindings", bindings);
            ReflectionTestUtils.setField(service, "admissionReleaseCoordinator", admission);
            ReflectionTestUtils.setField(service, "providerUsageReleaseCoordinator", budget);
            ReflectionTestUtils.setField(service, "managedDependencyReleaseCoordinator", dependencies);
        }

        CoolifyDeploymentProvider prepareContinuation() {
            deployment.setActiveVersionId(version.getId()); deployment.setRuntimeBaseUrl("https://runtime.example");
            release.setProviderType(DeploymentProviderType.COOLIFY); release.setCreatedAt(Instant.parse("2026-10-20T21:00:00Z"));
            var continuation = mapper.createObjectNode()
                .put("boundary", com.ai.fabric.platform.backend.deployment.admission.PostProvisionResumeContracts.BOUNDARY)
                .put("operationKey", "explicit-resume").put("proofFingerprint", "a".repeat(64)).put("failedAt", "2026-10-20T21:20:00Z");
            release.setProvisioningDetailsJson(mapper.createObjectNode().set("postProvisionResume", continuation).toString());
            var provider = mock(CoolifyDeploymentProvider.class); when(providers.require(DeploymentProviderType.COOLIFY)).thenReturn(provider);
            ReflectionTestUtils.setField(service, "aiStateStorageBindings", mock(DeploymentAiStateStorageBindingService.class));
            ReflectionTestUtils.setField(service, "externalIntegrations", mock(com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationResolver.class));
            ReflectionTestUtils.setField(service, "documentBindingResolver", mock(DeploymentDocumentBindingResolver.class));
            when(datasets.syncReleaseDatasets(deployment, version, release)).thenReturn(new MarketplaceDatasetSyncService.DatasetSyncSummary(0, 0, 0, List.of()));
            var passed = new DeploymentVerificationRunEntity(); passed.setId("post-verify"); passed.setStatus("PASSED");
            when(verification.verify(deployment, version, release, "POST_APPLY")).thenReturn(passed);
            return provider;
        }

        void assertNoProviderEffects() {
            verify(admission, never()).beginProvisioning(any());
            verifyNoInteractions(budget, dependencies, provisioning, datasets);
            assertThat(deployment.getStatus()).isNotEqualTo("PROVISIONING");
        }
    }

    private static DeploymentProviderResourceHandleEntity providerHandle(String deploymentId,
                                                                         String releaseId,
                                                                         String status) {
        Instant now = Instant.parse("2026-03-31T00:00:00Z");
        DeploymentProviderResourceHandleEntity handle = new DeploymentProviderResourceHandleEntity();
        handle.setId("dpr-123");
        handle.setDeploymentId(deploymentId);
        handle.setReleaseId(releaseId);
        handle.setTargetProfileId("dtp-coolify");
        handle.setProviderType(DeploymentProviderType.COOLIFY);
        handle.setResourceKind("APPLICATION");
        handle.setProviderResourceUuid("coolify-app-123");
        handle.setStatus(status);
        handle.setLastObservedStatus(status);
        handle.setLastObservedAt(now);
        handle.setMetadataJson("{}");
        handle.setCreatedAt(now);
        handle.setUpdatedAt(now);
        return handle;
    }
}
