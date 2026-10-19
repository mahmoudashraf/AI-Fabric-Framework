package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProductionAdmissionEvidenceSourceTest extends AdmissionFixture {
    private final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentDocumentBindingResolver documentBindings = mock(DeploymentDocumentBindingResolver.class);
    private final ReviewedManagedDependencyEvidenceService dependencyEvidence =
        mock(ReviewedManagedDependencyEvidenceService.class);
    private final CustomerTargetPlacementService placement = mock(CustomerTargetPlacementService.class);
    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final ObjectProvider<JavaMailSender> mail = mock(ObjectProvider.class);
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationResolver externalIntegrations =
        mock(com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationResolver.class);
    private DeploymentVersionEntity version;
    private CustomerPlacementEvidenceEntity placementEvidence;
    private AdmissionContext context;

    @BeforeEach
    void setUpEvidence() {
        var budget = new Budget(vector(10), vector(0), vector(0), "measurement-proof",
            NOW.minusSeconds(60), NOW.plusSeconds(3_600));
        var lifecycle = new LifecyclePolicy(1_000, 100, 100, 2, 3_000, 200,
            10, 10, 20, 2, 60, 3_600, "published-lifecycle", "approved-mail");
        var policy = new FleetPolicy("policy", budget,
            Map.of(TARGET, new TargetBudget(TARGET, Intent.CUSTOMER_STAGING, STAGING,
                "profile-fingerprint", budget)), lifecycle, "restore-proof", "privacy-proof", "support-owner");
        var grant = new CustomerGrant("customer", vector(10), Map.of(TARGET, vector(10)),
            NOW.plusSeconds(3_600), "platform-user:owner", "payer", "keep-entitlement");
        Set<String> references = Set.of(
            "published-lifecycle", "restore-proof", "privacy-proof", "support-owner", "approved-mail",
            "platform-user:owner", "payer", "reviewed-topology", "deployment-version:version:config-hash",
            "binding:binding-fingerprint", "placement:placement-evidence",
            "managed-dependency-spec:" + "d".repeat(64));
        var manifest = new Manifest("manifest", "deployment1", "version", "config-hash", TARGET,
            "binding-fingerprint", "reviewed-topology", vector(1), references);
        store.approvePolicy(policy, "operator", NOW);
        store.approveGrant(grant, "operator", NOW);
        store.approveManifest(manifest, "operator", NOW);
        context = store.context("customer", "tenant", "deployment1", "manifest");

        var target = new DeploymentTargetProfileEntity();
        target.setId(TARGET);
        when(targets.findById(TARGET)).thenReturn(Optional.of(target));
        placementEvidence = new CustomerPlacementEvidenceEntity("placement-evidence", TARGET,
            TargetIntent.CUSTOMER_STAGING, "profile-fingerprint", STAGING.serverUuid(), STAGING.ip(),
            STAGING.destinationUuid(), NOW.minusSeconds(5), NOW.plusSeconds(300));
        when(placement.requireFreshEvidence(target, TargetIntent.CUSTOMER_STAGING)).thenReturn(placementEvidence);

        version = new DeploymentVersionEntity();
        version.setId("version");
        version.setDeploymentId("deployment1");
        version.setStatus("PUBLISHED");
        version.setConfigHash("config-hash");
        when(versions.findById("version")).thenReturn(Optional.of(version));
        var deployment = new DeploymentEntity();
        deployment.setId("deployment1");
        when(deployments.findById("deployment1")).thenReturn(Optional.of(deployment));
        when(documentBindings.snapshot(deployment, version, TARGET)).thenReturn(mapper.createObjectNode());
        when(dependencyEvidence.resolveForDeployment("customer", "tenant", "deployment1",
            "reviewed-topology", manifest.demand()))
            .thenReturn(Optional.of(new ReviewedManagedDependencyEvidenceService.Evidence(
                "template-version", "d".repeat(64), "managed-dependency-spec:" + "d".repeat(64))));

        var owner = new PlatformUserEntity();
        owner.setId("owner");
        owner.setCustomerId("customer");
        owner.setRole("CUSTOMER_ADMIN");
        owner.setStatus("ACTIVE");
        owner.setEmail(" Owner@Example.Test ");
        when(users.findById("owner")).thenReturn(Optional.of(owner));
        when(mail.getIfAvailable()).thenReturn(sender);
    }

    @Test
    void readsExactPlacementVersionOwnerAndTransportWithoutProjectingContactData() {
        var evidence = source(properties(true, true)).read(context);

        assertThat(evidence.placement()).isEqualTo(STAGING);
        assertThat(evidence.profileFingerprint()).isEqualTo("profile-fingerprint");
        assertThat(evidence.manifestFingerprint()).isEqualTo(context.manifestFingerprint());
        assertThat(evidence.verifiedPrerequisiteReferences()).isEqualTo(context.manifest().prerequisiteReferences());
        assertThat(evidence.ownerContactEvidenceReference()).isEqualTo("platform-user:owner");
        assertThat(evidence.evidenceReference()).doesNotContain("Owner@Example.Test", "owner@example.test");
        verify(placement).requireFreshEvidence(any(), eq(TargetIntent.CUSTOMER_STAGING));
    }

    @Test
    void unavailableTransportAndForeignOwnerFailClosed() {
        when(mail.getIfAvailable()).thenReturn(null);
        assertThatThrownBy(() -> source(properties(true, true)).read(context))
            .hasMessageContaining("NOTICE_TRANSPORT_UNAVAILABLE");

        when(mail.getIfAvailable()).thenReturn(sender);
        var owner = users.findById("owner").orElseThrow();
        owner.setCustomerId("foreign-customer");
        assertThatThrownBy(() -> source(properties(true, true)).read(context))
            .hasMessageContaining("VERIFIED_OWNER_CONTACT_REQUIRED");
    }

    @Test
    void changedPlacementOrImmutableVersionFailsClosed() {
        when(placement.requireFreshEvidence(any(), any())).thenReturn(new CustomerPlacementEvidenceEntity(
            "placement-evidence", TARGET, TargetIntent.CUSTOMER_STAGING, "profile-fingerprint",
            "other-server", STAGING.ip(), STAGING.destinationUuid(), NOW.minusSeconds(5), NOW.plusSeconds(300)));
        assertThatThrownBy(() -> source(properties(true, true)).read(context))
            .hasMessageContaining("TARGET_PLACEMENT_EVIDENCE_CHANGED");

        when(placement.requireFreshEvidence(any(), any())).thenReturn(placementEvidence);
        version.setConfigHash("changed");
        assertThatThrownBy(() -> source(properties(true, true)).read(context))
            .hasMessageContaining("IMMUTABLE_VERSION_EVIDENCE_CHANGED");
    }

    @Test
    void disabledNoticeChannelCannotBeMistakenForProductionReadiness() {
        assertThatThrownBy(() -> source(properties(true, false)).read(context))
            .hasMessageContaining("NOTICE_TRANSPORT_UNAVAILABLE");
    }

    @Test
    void staffManagedAdmissionWithoutOfferAssociationRemainsSupported() {
        Set<String> references = new java.util.HashSet<>(context.manifest().prerequisiteReferences());
        references.remove("managed-dependency-spec:" + "d".repeat(64));
        references.remove("reviewed-topology");
        references.add("staff-reviewed-topology");
        var staffManifest = new Manifest("staff-manifest", "deployment1", "version", "config-hash", TARGET,
            "binding-fingerprint", "staff-reviewed-topology", vector(1), references);
        store.approveManifest(staffManifest, "operator", NOW);
        var staffContext = store.context("customer", "tenant", "deployment1", "staff-manifest");
        when(dependencyEvidence.resolveForDeployment("customer", "tenant", "deployment1",
            "staff-reviewed-topology", staffManifest.demand())).thenReturn(Optional.empty());

        assertThat(source(properties(true, true)).read(staffContext).verifiedPrerequisiteReferences())
            .isEqualTo(references);
    }

    @Test
    void externalAccountOrCredentialRevisionCannotChangeAfterAdmissionReview() {
        version.setCompositionProvenanceJson("{\"externalIntegrationProfiles\":[{\"profileId\":\"reviewed\"}]}");
        var references = new java.util.HashSet<>(context.manifest().prerequisiteReferences());
        references.add("external-integration:reviewed-binding");
        var manifest = new Manifest("external-manifest", "deployment1", "version", "config-hash", TARGET,
            "binding-fingerprint", "reviewed-topology", vector(1), references);
        store.approveManifest(manifest, "operator", NOW);
        var externalContext = store.context("customer", "tenant", "deployment1", "external-manifest");
        when(externalIntegrations.resolveLocal("deployment1", "version", TARGET)).thenReturn(java.util.List.of(
            new com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.Projection("binding", "approval", null,
                "reviewed-binding", Map.of(), Map.of())));
        assertThat(source(properties(true, true)).read(externalContext).verifiedPrerequisiteReferences()).contains("external-integration:reviewed-binding");
        when(externalIntegrations.resolveLocal("deployment1", "version", TARGET)).thenReturn(java.util.List.of(
            new com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.Projection("binding", "approval", null,
                "changed-binding", Map.of(), Map.of())));
        assertThatThrownBy(() -> source(properties(true, true)).read(externalContext))
            .hasMessageContaining("EXTERNAL_INTEGRATION_PREREQUISITE_CHANGED");
    }

    @Test
    void newlyAddedOrRemovedExternalBindingRequiresFreshAdmissionEvidence() {
        version.setCompositionProvenanceJson("{\"externalIntegrationProfiles\":[{}]}");
        when(externalIntegrations.resolveLocal("deployment1", "version", TARGET)).thenReturn(java.util.List.of(
            new com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.Projection("binding", "approval", null,
                "new-binding", Map.of(), Map.of())));
        assertThatThrownBy(() -> source(properties(true, true)).read(context))
            .hasMessageContaining("EXTERNAL_INTEGRATION_PREREQUISITE_CHANGED");
    }

    private ProductionAdmissionEvidenceSource source(AdmissionLifecycleProperties properties) {
        var source = new ProductionAdmissionEvidenceSource(targets, deployments, versions, documentBindings,
            dependencyEvidence, placement, new AdmissionOwnerContactResolver(users), properties, mail, json,
            Clock.fixed(NOW, ZoneOffset.UTC));
        org.springframework.test.util.ReflectionTestUtils.setField(source, "externalIntegrations", externalIntegrations);
        return source;
    }

    private AdmissionLifecycleProperties properties(boolean console, boolean email) {
        return new AdmissionLifecycleProperties(true, Duration.ofSeconds(30), Duration.ofSeconds(30), 32,
            "approved-mail", console, email, "notices@example.test", true);
    }
}
