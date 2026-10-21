package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.EntitlementStatus;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Definition;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.PluginPin;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.ReviewedLimits;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferEntitlementRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ManagedDependencyReleaseCoordinatorTest {
    private final CustomerOfferDeploymentRepository offerDeployments = mock(CustomerOfferDeploymentRepository.class);
    private final CustomerOfferRepository offers = mock(CustomerOfferRepository.class);
    private final CustomerOfferEntitlementRepository entitlements = mock(CustomerOfferEntitlementRepository.class);
    private final CustomerOfferCodec codec = mock(CustomerOfferCodec.class);
    private final ManagedDependencySpecRepository specs = mock(ManagedDependencySpecRepository.class);
    private final ManagedDependencyGraphStore store = mock(ManagedDependencyGraphStore.class);
    private final ManagedDependencyLifecycleService lifecycle = mock(ManagedDependencyLifecycleService.class);
    private final ManagedDependencyOperatorService operators = mock(ManagedDependencyOperatorService.class);
    private final ManagedDependencyReleaseCoordinator coordinator = new ManagedDependencyReleaseCoordinator(
        offerDeployments, offers, entitlements, codec, specs, store, lifecycle, operators);
    private DeploymentEntity deployment;
    private DeploymentVersionEntity version;
    private DeploymentReleaseEntity release;
    private ManagedDependencySpecEntity spec;

    @BeforeEach
    void setUp() {
        deployment = new DeploymentEntity();
        deployment.setId("dep-1"); deployment.setCustomerId("customer-1"); deployment.setTenantId("tenant-1");
        version = new DeploymentVersionEntity();
        version.setId("version-1"); version.setDeploymentId("dep-1");
        release = new DeploymentReleaseEntity();
        release.setId("release-1"); release.setDeploymentId("dep-1"); release.setDeploymentVersionId("version-1");
        release.setTargetProfileId("target-1"); release.setRuntimeAuthorityFingerprint("a".repeat(64));

        var association = new CustomerOfferDeploymentEntity("dep-1", "customer-1", "tenant-1", "offer-1",
            "target-1", "offer-fingerprint", 3, "bootstrap", Instant.now());
        var offer = new CustomerOfferEntity("offer-1", "{}", "offer-fingerprint", "review", "reviewer",
            Instant.now());
        var entitlement = new CustomerOfferEntitlementEntity();
        entitlement.setCustomerId("customer-1"); entitlement.setOfferId("offer-1");
        entitlement.setOwnerUserId("owner@example.test"); entitlement.setStatus(EntitlementStatus.ACTIVE);
        entitlement.setRevision(3); entitlement.setExpiresAt(Instant.now().plusSeconds(3600));
        Definition definition = mock(Definition.class);
        PluginPin template = mock(PluginPin.class);
        ReviewedLimits limits = mock(ReviewedLimits.class);
        when(template.versionId()).thenReturn("template-version-1");
        when(definition.template()).thenReturn(template);
        when(definition.limits()).thenReturn(limits);
        when(limits.dependencies()).thenReturn(3);
        spec = new ManagedDependencySpecEntity();
        spec.setId("dependency-spec-1"); spec.setTemplateVersionId("template-version-1");
        spec.setSpecJson("{}"); spec.setActive(true);

        when(offerDeployments.findById("dep-1")).thenReturn(Optional.of(association));
        when(offers.findById("offer-1")).thenReturn(Optional.of(offer));
        when(entitlements.findByCustomerIdAndOfferId("customer-1", "offer-1"))
            .thenReturn(Optional.of(entitlement));
        when(codec.read("{}", Definition.class)).thenReturn(definition);
        when(specs.findByTemplateVersionIdAndActiveTrue("template-version-1")).thenReturn(List.of(spec));
        when(store.parse(any(), eq(ManagedDependencySpec.class))).thenReturn(ManagedDependencyFixtures.topology());
    }

    @Test
    void releaseRunsFoundationThenRuntimeBoundAgainstExactRelease() {
        coordinator.prepareFoundation(deployment, version, release);
        coordinator.completeRuntimeBound(deployment, version, release);

        verify(lifecycle).submitForRelease(eq("dep-1"), eq("target-1"), any());
        verify(operators).enrollInitialOwnerForRelease(eq("dep-1"), eq("target-1"), eq("release-1"), any());
        verify(lifecycle).provisionForRelease("dep-1", "target-1", "release-1",
            "release:release-1:dependency:foundation");
        verify(lifecycle).reconcileForRelease("dep-1", "target-1", "release-1",
            "release:release-1:dependency:runtime-bound");
    }

    @Test
    void ordinaryNonOfferDeploymentHasNoManagedDependencySideEffects() {
        when(offerDeployments.findById("dep-1")).thenReturn(Optional.empty());
        coordinator.prepareFoundation(deployment, version, release);
        coordinator.completeRuntimeBound(deployment, version, release);
        verifyNoInteractions(offers, entitlements, codec, specs, store, lifecycle, operators);
    }

    @Test void explicitContinuationUsesANewBoundedOperationWithoutRepeatingFoundationOrOwnerEnrollment() {
        coordinator.resumeRuntimeBound(deployment, version, release, "b".repeat(64));
        verify(lifecycle).resumeForRelease("dep-1", "target-1", "release-1",
            "release:release-1:dependency:resume:" + "b".repeat(64));
        verify(lifecycle, never()).submitForRelease(any(), any(), any());
        verify(lifecycle, never()).provisionForRelease(any(), any(), any(), any());
        verifyNoInteractions(operators);
        assertThatThrownBy(() -> coordinator.resumeRuntimeBound(deployment, version, release, "unreviewed"))
            .hasMessageContaining("DEPENDENCY_CONTINUATION_PROOF_REQUIRED");
    }

    @Test
    void ambiguousReviewedSpecFailsClosedBeforeProvisioning() {
        when(specs.findByTemplateVersionIdAndActiveTrue("template-version-1")).thenReturn(List.of(spec, spec));
        assertThatThrownBy(() -> coordinator.prepareFoundation(deployment, version, release))
            .hasMessageContaining("REVIEWED_DEPENDENCY_SPEC_MISSING_OR_AMBIGUOUS");
        verifyNoInteractions(lifecycle);
    }

    @Test
    void dependencyFreeOfferDoesNotEnterManagedLifecycle() {
        Definition definition = codec.read("{}", Definition.class);
        when(definition.limits().dependencies()).thenReturn(0);
        when(specs.findByTemplateVersionIdAndActiveTrue("template-version-1")).thenReturn(List.of());

        coordinator.prepareFoundation(deployment, version, release);
        coordinator.completeRuntimeBound(deployment, version, release);

        verifyNoInteractions(lifecycle, operators, store);
    }

    @Test
    void dependencyFreeOfferRejectsUnexpectedReviewedTopology() {
        Definition definition = codec.read("{}", Definition.class);
        when(definition.limits().dependencies()).thenReturn(0);

        assertThatThrownBy(() -> coordinator.prepareFoundation(deployment, version, release))
            .hasMessageContaining("UNEXPECTED_REVIEWED_DEPENDENCY_SPEC");
        verifyNoInteractions(lifecycle, operators, store);
    }
}
