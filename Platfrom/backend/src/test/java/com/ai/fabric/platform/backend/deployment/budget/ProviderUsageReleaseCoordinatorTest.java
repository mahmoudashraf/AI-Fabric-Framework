package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageRuntimeClient.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProviderUsageReleaseCoordinatorTest {
    private final CustomerOfferDeploymentRepository offers = mock(CustomerOfferDeploymentRepository.class);
    private final ProviderUsageAllowanceService allowances = mock(ProviderUsageAllowanceService.class);
    private final ProviderUsageRuntimeClient runtime = mock(ProviderUsageRuntimeClient.class);
    private final DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
    private final ProviderUsageRuntimeArtifactGate artifacts = mock(ProviderUsageRuntimeArtifactGate.class);
    private final ProviderUsageReleaseCoordinator coordinator = new ProviderUsageReleaseCoordinator(offers, allowances, runtime, authority, artifacts);
    private final DeploymentEntity deployment = new DeploymentEntity();
    private final DeploymentReleaseEntity release = new DeploymentReleaseEntity();
    private final DeploymentVersionEntity version = new DeploymentVersionEntity();
    private final UUID store = UUID.randomUUID();
    private final Instant expires = Instant.parse("2026-10-20T12:00:00Z");
    private static final String RATE_CARD = "openai-gpt4omini-embedding3small-20261018-v1";
    private final Map<String, String> headers = Map.of("X-Private", "test-only");

    ProviderUsageReleaseCoordinatorTest() {
        deployment.setId("dep-1"); deployment.setRuntimeBaseUrl("https://runtime.example");
        release.setId("rel-1"); release.setDeploymentId("dep-1"); release.setDeploymentVersionId("ver-1");
        release.setTargetProfileId("target-1");
        version.setId("ver-1"); version.setDeploymentId("dep-1");
        when(offers.existsById("dep-1")).thenReturn(true);
        when(authority.issueProviderBudgetProvisioningHeaders(deployment, version, release)).thenReturn(headers);
    }

    @Test void privateRuntimeClientHasAnUnambiguousProductionConstructor() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
            .withBean(com.fasterxml.jackson.databind.ObjectMapper.class, com.fasterxml.jackson.databind.ObjectMapper::new)
            .withUserConfiguration(ProviderUsageRuntimeClient.class)
            .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ProviderUsageRuntimeClient.class));
    }

    @Test void commitsTheInitializedStoreBeforeActivationAndChecksFinalAccounting() {
        var initial = allocation(InitializationState.UNINITIALIZED, null);
        var initialized = allocation(InitializationState.INITIALIZED, store);
        when(allowances.findForAuthorizedRelease("rel-1")).thenReturn(Optional.of(initial));
        when(runtime.read(deployment.getRuntimeBaseUrl(), headers)).thenReturn(readback("UNINITIALIZED", null, null),
            readback("ACTIVE", store, false));
        when(runtime.initialize(deployment.getRuntimeBaseUrl(), initial, headers)).thenReturn(receipt("INITIALIZED", store));
        when(allowances.markInitializedForAuthorizedRelease("rel-1", store, "b".repeat(64))).thenReturn(initialized);
        when(runtime.activate(eq(deployment.getRuntimeBaseUrl()), any(), eq(headers))).thenReturn(receipt("ACTIVE", store));

        coordinator.initializeAndActivate(deployment, version, release);

        var order = inOrder(runtime, allowances);
        order.verify(allowances).findForAuthorizedRelease("rel-1");
        order.verify(runtime).read(deployment.getRuntimeBaseUrl(), headers);
        order.verify(runtime).initialize(deployment.getRuntimeBaseUrl(), initial, headers);
        order.verify(allowances).markInitializedForAuthorizedRelease("rel-1", store, "b".repeat(64));
        order.verify(runtime).activate(eq(deployment.getRuntimeBaseUrl()), any(), eq(headers));
        order.verify(allowances).markActiveForAuthorizedRelease("rel-1", store, "b".repeat(64));
        order.verify(runtime).read(deployment.getRuntimeBaseUrl(), headers);
    }

    @Test void aLostActiveStoreCannotBeReinitialized() {
        when(allowances.findForAuthorizedRelease("rel-1")).thenReturn(Optional.of(allocation(InitializationState.ACTIVE, store)));
        when(runtime.read(deployment.getRuntimeBaseUrl(), headers)).thenReturn(readback("UNINITIALIZED", null, null));
        assertThatThrownBy(() -> coordinator.initializeAndActivate(deployment, version, release)).isInstanceOf(IllegalStateException.class);
        verify(runtime, never()).initialize(any(), any(), any());
        verify(runtime, never()).activate(any(), any(), any());
        verify(allowances, never()).markInitializedForAuthorizedRelease(any(), any(), any());
    }

    @Test void aChangedOrDowngradedStoreCannotReceiveAnActivation() {
        when(allowances.findForAuthorizedRelease("rel-1")).thenReturn(Optional.of(allocation(InitializationState.ACTIVE, store)));
        when(runtime.read(deployment.getRuntimeBaseUrl(), headers)).thenReturn(readback("ACTIVE", UUID.randomUUID(), false));
        assertThatThrownBy(() -> coordinator.initializeAndActivate(deployment, version, release)).isInstanceOf(IllegalStateException.class);
        when(runtime.read(deployment.getRuntimeBaseUrl(), headers)).thenReturn(readback("INITIALIZED", store, false));
        assertThatThrownBy(() -> coordinator.initializeAndActivate(deployment, version, release)).isInstanceOf(IllegalStateException.class);
        verify(runtime, never()).activate(any(), any(), any());
    }

    @Test void planningIsReadOnlyAndContainsNoCreditsOrRawSecrets() {
        var initial = allocation(InitializationState.UNINITIALIZED, null);
        when(allowances.findForAuthorizedRelease("rel-1")).thenReturn(Optional.of(initial));
        assertThat(coordinator.environment(deployment, release)).containsEntry("LOOMAI_PROVIDER_BUDGET_ENABLED", "true")
            .containsEntry("LOOMAI_PROVIDER_BUDGET_CREDENTIAL_IDENTITY", "sha256:" + "c".repeat(64))
            .containsEntry("LOOMAI_PROVIDER_BUDGET_OUTPUT_CAP_MODE", "MAX_COMPLETION_TOKENS")
            .hasSize(5);
        verify(allowances, never()).prepareForAuthorizedRelease(any());
        verifyNoInteractions(runtime, authority);
    }

    @Test void metadataWithoutAPreparedAllocationCannotDisableTheGuard() {
        when(allowances.findForAuthorizedRelease("rel-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> coordinator.environment(deployment, release)).hasMessage("PROVIDER_USAGE_PREPARED_ALLOCATION_REQUIRED");
        verifyNoInteractions(runtime, authority);
    }

    @Test void continuationReadsTheSameActiveWalletWithoutAnyFinancialMutation() {
        var old = allocation(InitializationState.ACTIVE, store); var until = Instant.now().plusSeconds(3600);
        var active = new SafeAllowanceProjection(old.approvalId(), old.scope(), old.caps(), until, old.pricingReference(),
            old.binding(), old.credentialSha256(), old.approvalFingerprint(), old.runtimeAllocationFingerprint(), old.state(), old.storeUuid(), old.initializationFingerprint());
        when(allowances.findForAuthorizedRelease("rel-1")).thenReturn(Optional.of(active));
        when(authority.issueReleaseVerificationHeaders(deployment, release, false)).thenReturn(headers);
        when(runtime.read(deployment.getRuntimeBaseUrl(), headers)).thenReturn(new Readback("ACTIVE", store,
            active.approvalId(), active.runtimeAllocationFingerprint(), "customer-1", "dep-1", "target-1", "staging",
            RATE_CARD, active.caps(), until, 7L, 11L, new Usage(12000, 3000, 123456), false));
        coordinator.verifyActive(deployment, version, release);
        verify(runtime, never()).initialize(any(), any(), any()); verify(runtime, never()).activate(any(), any(), any());
        verify(allowances, never()).prepareForAuthorizedRelease(any());
        verify(allowances, never()).markInitializedForAuthorizedRelease(any(), any(), any());
        verify(allowances, never()).markActiveForAuthorizedRelease(any(), any(), any());
        verify(authority, never()).issueProviderBudgetProvisioningHeaders(any(), any(), any());
    }

    @Test void continuationDoesNotResurrectAnExpiredAllowance() {
        when(allowances.findForAuthorizedRelease("rel-1")).thenReturn(Optional.of(allocation(InitializationState.ACTIVE, store)));
        assertThatThrownBy(() -> coordinator.verifyActive(deployment, version, release)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(runtime, authority);
    }

    @Test void continuationUsesAcknowledgedEffectiveValidityButStillRequiresImmutableOriginalExpiry() {
        var original = allocation(InitializationState.ACTIVE, store); var effective = Instant.now().plusSeconds(3600);
        var extended = new SafeAllowanceProjection(original.approvalId(), original.scope(), original.caps(), original.expiresAt(),
            original.pricingReference(), original.binding(), original.credentialSha256(), original.approvalFingerprint(),
            original.runtimeAllocationFingerprint(), original.state(), original.storeUuid(), original.initializationFingerprint(), effective);
        when(allowances.findForAuthorizedRelease("rel-1")).thenReturn(Optional.of(extended));
        when(authority.issueReleaseVerificationHeaders(deployment, release, false)).thenReturn(headers);
        when(runtime.read(deployment.getRuntimeBaseUrl(), headers)).thenReturn(new Readback("ACTIVE", store, extended.approvalId(),
            extended.runtimeAllocationFingerprint(), "customer-1", "dep-1", "target-1", "staging", RATE_CARD, extended.caps(),
            extended.expiresAt(), 7L, 11L, new Usage(12000, 3000, 123456), false, effective));
        coordinator.verifyActive(deployment, version, release);
        when(runtime.read(deployment.getRuntimeBaseUrl(), headers)).thenReturn(new Readback("ACTIVE", store, extended.approvalId(),
            extended.runtimeAllocationFingerprint(), "customer-1", "dep-1", "target-1", "staging", RATE_CARD, extended.caps(),
            effective, 7L, 11L, new Usage(12000, 3000, 123456), false, effective));
        assertThatThrownBy(() -> coordinator.verifyActive(deployment, version, release)).isInstanceOf(IllegalStateException.class);
        verify(runtime, never()).initialize(any(), any(), any()); verify(runtime, never()).activate(any(), any(), any());
    }

    @Test void anExtendedProjectionCannotUseOldImageOriginalOnlyReadbackOrAStoppedWallet() {
        var original = allocation(InitializationState.ACTIVE, store); var effective = Instant.now().plusSeconds(3600);
        var extended = new SafeAllowanceProjection(original.approvalId(), original.scope(), original.caps(), original.expiresAt(),
            original.pricingReference(), original.binding(), original.credentialSha256(), original.approvalFingerprint(),
            original.runtimeAllocationFingerprint(), original.state(), original.storeUuid(), original.initializationFingerprint(), effective);
        when(allowances.findForAuthorizedRelease("rel-1")).thenReturn(Optional.of(extended));
        when(authority.issueReleaseVerificationHeaders(deployment, release, false)).thenReturn(headers);
        when(runtime.read(deployment.getRuntimeBaseUrl(), headers)).thenReturn(readback("ACTIVE", store, false));
        assertThatThrownBy(() -> coordinator.verifyActive(deployment, version, release)).isInstanceOf(IllegalStateException.class);
        when(runtime.read(deployment.getRuntimeBaseUrl(), headers)).thenReturn(new Readback("ACTIVE", store, extended.approvalId(),
            extended.runtimeAllocationFingerprint(), "customer-1", "dep-1", "target-1", "staging", RATE_CARD, extended.caps(),
            extended.expiresAt(), 7L, 11L, new Usage(12000, 3000, 123456), true, effective));
        assertThatThrownBy(() -> coordinator.verifyActive(deployment, version, release)).isInstanceOf(IllegalStateException.class);
        verify(runtime, never()).initialize(any(), any(), any()); verify(runtime, never()).activate(any(), any(), any());
    }

    @Test void anOldArtifactIsRejectedBeforeAnyFinancialAllocationOrRuntimeRequest() {
        doThrow(new IllegalStateException("PROVIDER_USAGE_GUARDED_RUNTIME_ARTIFACT_REQUIRED")).when(artifacts).require(release);
        assertThatThrownBy(() -> coordinator.prepare(deployment, release))
            .hasMessage("PROVIDER_USAGE_GUARDED_RUNTIME_ARTIFACT_REQUIRED");
        verifyNoInteractions(allowances, runtime, authority);
    }

    @Test void aGrantCannotAuthorizePaidModelsOutsideItsReviewedBinding() {
        var valid = allocation(InitializationState.UNINITIALIZED, null);
        var chatOnly = new SafeAllowanceProjection(valid.approvalId(), valid.scope(), valid.caps(), valid.expiresAt(),
            valid.pricingReference(), new ProviderBinding("openai", List.of("gpt-4o-mini"), "OPENAI_API_KEY", null),
            valid.credentialSha256(), valid.approvalFingerprint(), valid.runtimeAllocationFingerprint(), valid.state(), null, null);
        when(allowances.findForAuthorizedRelease("rel-1")).thenReturn(Optional.of(chatOnly));
        assertThatThrownBy(() -> coordinator.environment(deployment, release))
            .hasMessage("PROVIDER_USAGE_RUNTIME_PROFILE_NOT_REVIEWED");
        verifyNoInteractions(runtime, authority);
    }

    @Test void unrelatedDeploymentsAreUnchangedAndForeignReleaseFailsBeforeEffects() {
        when(offers.existsById("dep-1")).thenReturn(false);
        coordinator.prepare(deployment, release);
        coordinator.initializeAndActivate(deployment, version, release);
        assertThat(coordinator.environment(deployment, release)).isEmpty();
        verifyNoInteractions(allowances, runtime, authority);
        release.setDeploymentId("foreign");
        assertThatThrownBy(() -> coordinator.prepare(deployment, release)).isInstanceOf(IllegalStateException.class);
    }

    private SafeAllowanceProjection allocation(InitializationState state, UUID storeUuid) {
        return new SafeAllowanceProjection("approval-1", new AllocationScope("approval-1", "customer-1", "dep-1", "target-1", "staging"),
            new Limits(50, 100_000, 2_000_000_000), expires, RATE_CARD, new ProviderBinding("openai", List.of("gpt-4o-mini", "text-embedding-3-small"),
            "OPENAI_API_KEY", null), "c".repeat(64), "a".repeat(64), "b".repeat(64), state, storeUuid,
            storeUuid == null ? null : "b".repeat(64));
    }

    private StoreReceipt receipt(String state, UUID id) { return new StoreReceipt(state, id, "approval-1", "b".repeat(64)); }

    private Readback readback(String state, UUID id, Boolean stopped) {
        boolean empty = "UNINITIALIZED".equals(state);
        return new Readback(state, id, empty ? null : "approval-1", empty ? null : "b".repeat(64), "customer-1", "dep-1", "target-1", "staging",
            RATE_CARD, empty ? null : allocation(InitializationState.ACTIVE, store).caps(), empty ? null : expires,
            empty ? null : 0L, empty ? null : 0L, empty ? null : new Usage(0, 0, 0), stopped);
    }
}
