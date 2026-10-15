package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
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
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final CustomerTargetPlacementService placement = mock(CustomerTargetPlacementService.class);
    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final ObjectProvider<JavaMailSender> mail = mock(ObjectProvider.class);
    private final JavaMailSender sender = mock(JavaMailSender.class);
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
            "binding:binding-fingerprint", "placement:placement-evidence");
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

    private ProductionAdmissionEvidenceSource source(AdmissionLifecycleProperties properties) {
        return new ProductionAdmissionEvidenceSource(targets, versions, placement,
            new AdmissionOwnerContactResolver(users), properties, mail, json, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AdmissionLifecycleProperties properties(boolean console, boolean email) {
        return new AdmissionLifecycleProperties(true, Duration.ofSeconds(30), Duration.ofSeconds(30), 32,
            "approved-mail", console, email, "notices@example.test", true);
    }
}
