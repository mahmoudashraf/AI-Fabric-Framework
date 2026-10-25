package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.EntitlementStatus;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferEntitlementRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.operations.CoreReleaseAuthorizationStore;
import com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository;
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
    private final CustomerOfferCodec codec = new CustomerOfferCodec(new ObjectMapper());
    private final CustomerOfferEntitlementService eligibility = mock(CustomerOfferEntitlementService.class);
    private final CoreReleaseAuthorizationStore authorizations = mock(CoreReleaseAuthorizationStore.class);
    private final PlatformTenantRepository tenants = mock(PlatformTenantRepository.class);
    private final ManagedDependencySpecRepository specs = mock(ManagedDependencySpecRepository.class);
    private final ManagedDependencyGraphStore store = mock(ManagedDependencyGraphStore.class);
    private final ManagedDependencyLifecycleService lifecycle = mock(ManagedDependencyLifecycleService.class);
    private final ManagedDependencyOperatorService operators = mock(ManagedDependencyOperatorService.class);
    private final ManagedDependencyReleaseCoordinator coordinator = new ManagedDependencyReleaseCoordinator(
        offerDeployments, offers, entitlements, codec, specs, store, lifecycle, operators, eligibility, authorizations, tenants);
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
        version.setCompositionProvenanceJson("{\"marketplaceInstalls\":[{\"pluginType\":\"TEMPLATE\",\"pluginVersionId\":\"template-version-1\"}]}");
        release = new DeploymentReleaseEntity();
        release.setId("release-1"); release.setDeploymentId("dep-1"); release.setDeploymentVersionId("version-1");
        release.setTargetProfileId("target-1"); release.setRuntimeAuthorityFingerprint("a".repeat(64));
        var tenant = new PlatformTenantEntity(); tenant.setId("tenant-1"); tenant.setCustomerId("customer-1"); tenant.setStatus("ACTIVE");
        when(tenants.findById("tenant-1")).thenReturn(Optional.of(tenant));
        var authorization = mock(CoreReleaseAuthorizationStore.Authorization.class);
        when(authorization.actorId()).thenReturn("current-admin@example.test");
        when(authorizations.require(deployment, version, release)).thenReturn(authorization);

        var association = new CustomerOfferDeploymentEntity("dep-1", "customer-1", "tenant-1", "offer-1",
            "target-1", "offer-fingerprint", 3, "bootstrap", Instant.now());
        var entitlement = new CustomerOfferEntitlementEntity();
        entitlement.setCustomerId("customer-1"); entitlement.setOfferId("offer-1");
        entitlement.setOwnerUserId("owner@example.test"); entitlement.setStatus(EntitlementStatus.ACTIVE);
        entitlement.setRevision(3); entitlement.setExpiresAt(Instant.now().plusSeconds(3600));
        spec = new ManagedDependencySpecEntity();
        spec.setId("dependency-spec-1"); spec.setTemplateVersionId("template-version-1");
        spec.setSpecJson("{}"); spec.setActive(true);

        when(offerDeployments.findById("dep-1")).thenReturn(Optional.of(association));
        when(entitlements.findByCustomerIdAndOfferId("customer-1", "offer-1"))
            .thenReturn(Optional.of(entitlement));
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
        verifyNoInteractions(offers, entitlements, specs, store, lifecycle, operators, eligibility, tenants, authorizations);
    }

    @Test
    void ambiguousReviewedSpecFailsClosedBeforeProvisioning() {
        when(specs.findByTemplateVersionIdAndActiveTrue("template-version-1")).thenReturn(List.of(spec, spec));
        assertThatThrownBy(() -> coordinator.prepareFoundation(deployment, version, release))
            .hasMessageContaining("REVIEWED_DEPENDENCY_SPEC_MISSING_OR_AMBIGUOUS");
        verifyNoInteractions(lifecycle);
    }

    @Test
    void dependencyFreePublishedTemplateDoesNotEnterManagedLifecycle() {
        when(specs.findByTemplateVersionIdAndActiveTrue("template-version-1")).thenReturn(List.of());

        coordinator.prepareFoundation(deployment, version, release);
        coordinator.completeRuntimeBound(deployment, version, release);

        verifyNoInteractions(lifecycle, operators, store);
    }

    @Test
    void upgradeUsesPublishedTemplateNotOriginalOfferAndIgnoresInvitationExpiryAndRevision() {
        var entitlement = entitlements.findByCustomerIdAndOfferId("customer-1", "offer-1").orElseThrow();
        entitlement.setExpiresAt(Instant.EPOCH); entitlement.setRevision(100);
        entitlement.setStatus(EntitlementStatus.REVOKED);
        version.setCompositionProvenanceJson("{\"marketplaceInstalls\":[{\"pluginType\":\"TEMPLATE\",\"pluginVersionId\":\"template-version-2\"}]}");
        spec.setTemplateVersionId("template-version-2");
        when(specs.findByTemplateVersionIdAndActiveTrue("template-version-2")).thenReturn(List.of(spec));
        coordinator.prepareFoundation(deployment, version, release);
        verify(eligibility).requireActiveCustomer("customer-1");
        verify(eligibility, never()).requireCurrentOwnerEligibility(anyString(), anyString());
        verify(specs).findByTemplateVersionIdAndActiveTrue("template-version-2");
        verify(specs, never()).findByTemplateVersionIdAndActiveTrue("template-version-1");
        verify(lifecycle).submitForRelease(eq("dep-1"), eq("target-1"), any());
        verifyNoInteractions(offers);
        verify(entitlements, times(1)).findByCustomerIdAndOfferId("customer-1", "offer-1");
    }

    @Test
    void ambiguousTemplateAndForeignReleaseCannotProvision() {
        version.setCompositionProvenanceJson("{\"marketplaceInstalls\":[{\"pluginType\":\"TEMPLATE\",\"pluginVersionId\":\"one\"},{\"pluginType\":\"TEMPLATE\",\"pluginVersionId\":\"two\"}]}");
        assertThatThrownBy(() -> coordinator.prepareFoundation(deployment, version, release))
            .hasMessageContaining("DEPENDENCY_TEMPLATE_EVIDENCE_AMBIGUOUS");
        release.setDeploymentId("other");
        assertThatThrownBy(() -> coordinator.prepareFoundation(deployment, version, release))
            .hasMessageContaining("DEPENDENCY_RELEASE_SCOPE_CHANGED");
        verifyNoInteractions(lifecycle, operators, store);
    }

    @Test
    void missingOriginalInvitationOwnerDoesNotBlockUpgradeAndInitialInboxUsesCurrentReleaseActor() {
        when(entitlements.findByCustomerIdAndOfferId("customer-1", "offer-1")).thenReturn(Optional.empty());
        coordinator.prepareFoundation(deployment, version, release);
        verify(operators).enrollInitialOwnerForRelease(eq("dep-1"), eq("target-1"), eq("release-1"),
            argThat(request -> "current-admin@example.test".equals(request.displayName())));
        verifyNoInteractions(entitlements, offers);
    }
}
