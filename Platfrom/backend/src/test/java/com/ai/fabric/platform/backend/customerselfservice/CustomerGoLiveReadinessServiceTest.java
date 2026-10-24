package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceReadinessSummary;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceReadinessService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerGoLiveReadinessContracts.Status;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Placement;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.PrerequisiteReadback;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Reservation;
import com.ai.fabric.platform.backend.deployment.admission.DeploymentAdmissionService;
import com.ai.fabric.platform.backend.deployment.admission.ReviewedManagedDependencyEvidenceService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentPromotionCandidateService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerGoLiveReadinessServiceTest {
    private final CustomerOfferEntitlementService entitlements = mock(CustomerOfferEntitlementService.class);
    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    private final DeploymentVerificationRunRepository verifications = mock(DeploymentVerificationRunRepository.class);
    private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    private final AIWorkspaceInstallationRepository installations = mock(AIWorkspaceInstallationRepository.class);
    private final AIWorkspaceReadinessService workspaceReadiness = mock(AIWorkspaceReadinessService.class);
    private final DeploymentPromotionCandidateService candidates = mock(DeploymentPromotionCandidateService.class);
    private final DeploymentAdmissionService admissions = mock(DeploymentAdmissionService.class);
    private final TargetRetirementOperationRepository retirements = mock(TargetRetirementOperationRepository.class);
    private final CustomerReviewedCompositionService composition = mock(CustomerReviewedCompositionService.class);
    private final ReviewedManagedDependencyEvidenceService dependencyEvidence = mock(ReviewedManagedDependencyEvidenceService.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final CustomerGoLiveReadinessService service = new CustomerGoLiveReadinessService(
        entitlements, associations, deployments, releases, versions, targets, verifications, consumers,
        installations, workspaceReadiness, candidates, admissions, retirements, composition, dependencyEvidence, mapper);

    private DeploymentEntity deployment;
    private DeploymentVersionEntity version;
    private DeploymentReleaseEntity release;
    private PlatformConsumerEntity consumer;
    private Definition definition;
    private Reservation reservation;

    @BeforeEach
    void setUp() throws Exception {
        Instant now = Instant.now();
        deployment = new DeploymentEntity();
        deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        deployment.setTemplateId("dealership"); deployment.setBehaviorType("CONVERSATIONAL");

        version = new DeploymentVersionEntity();
        version.setId("version"); version.setDeploymentId("deployment"); version.setStatus("PUBLISHED");

        release = new DeploymentReleaseEntity();
        release.setId("production-release"); release.setDeploymentId("deployment");
        release.setDeploymentVersionId("version"); release.setTargetProfileId("production");
        release.setStatus("APPLIED_VERIFIED"); release.setVerificationStatus("PASSED");
        release.setSourceArtifactId("artifact"); release.setVerificationRunId("verification");
        release.setPromotionCandidateFingerprint("candidate-fp"); release.setUpdatedAt(now);
        release.setPromotionCandidateJson(mapper.createObjectNode()
            .put("deploymentId", "deployment").put("customerId", "customer")
            .put("consumerId", "workspace").put("expectedBindingRevision", 3)
            .put("stagingReleaseId", "staging-release").put("stagingTargetProfileId", "staging")
            .put("versionId", "version").put("productionTargetProfileId", "production")
            .put("promotionEvidenceMaxAgeSeconds", 3600).toString());

        consumer = new PlatformConsumerEntity();
        consumer.setId("consumer-entity"); consumer.setCustomerId("customer"); consumer.setConsumerId("workspace");
        consumer.setStatus("ACTIVE"); consumer.setBindingRevision(3); consumer.setBoundDeploymentId("deployment");
        consumer.setBoundReleaseId("staging-release"); consumer.setBoundTargetProfileId("staging");

        definition = definition();
        var association = new CustomerOfferDeploymentEntity("deployment", "customer", "tenant", "offer",
            "staging", "offer-fp", 7, "bootstrap", now);
        when(associations.findById("deployment")).thenReturn(Optional.of(association));
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        when(releases.findById("production-release")).thenReturn(Optional.of(release));
        when(versions.findById("version")).thenReturn(Optional.of(version));
        var target = new DeploymentTargetProfileEntity();
        target.setId("production"); target.setEnvironmentName("production"); target.setActive(true);
        target.setProviderType(DeploymentProviderType.COOLIFY);
        target.setProviderConfigJson("{\"promotionEvidenceMaxAgeSeconds\":3600}");
        when(targets.findById("production")).thenReturn(Optional.of(target));
        when(candidates.promotionEvidenceMaxAgeSeconds(any())).thenReturn(3600L);
        when(consumers.findByCustomerIdAndConsumerIdIgnoreCase("customer", "workspace"))
            .thenReturn(Optional.of(consumer));

        var actor = new CustomerOfferEntitlementService.CustomerActor("customer", "owner");
        when(entitlements.requireCustomerActor()).thenReturn(actor);
        var entitlement = new CustomerOfferEntitlementEntity(); entitlement.setRevision(7);
        when(entitlements.requireEntitlement("customer", "offer", "owner", false)).thenReturn(entitlement);
        when(entitlements.requireEntitlement("customer", "offer", null, false)).thenReturn(entitlement);
        var offer = new CustomerOfferEntity("offer", "{}", "offer-fp", "review", "reviewer", now);
        when(entitlements.requireOffer("offer")).thenReturn(offer);
        when(entitlements.definition(offer)).thenReturn(definition);

        var run = new DeploymentVerificationRunEntity();
        run.setId("verification"); run.setDeploymentId("deployment"); run.setReleaseId("production-release");
        run.setDeploymentVersionId("version"); run.setVerificationType("POST_APPLY"); run.setStatus("PASSED");
        run.setCompletedAt(now); run.setChecksJson("""
            [
              {"name":"document_source_connector_ready","status":"PASSED"},
              {"name":"document_active_sources_ready","status":"PASSED"},
              {"name":"document_retrieval_evidence_ready","status":"PASSED"},
              {"name":"marketplace_dataset_sync_matches_expected","status":"PASSED"}
            ]
            """);
        when(verifications.findById("verification")).thenReturn(Optional.of(run));
        when(candidates.fingerprint(any())).thenReturn("candidate-fp");

        reservation = mock(Reservation.class);
        when(reservation.customerOperation()).thenReturn(Operation.PROMOTION_PLAN);
        when(reservation.consumesCapacity()).thenReturn(true);
        when(reservation.id()).thenReturn("reservation"); when(reservation.generation()).thenReturn("generation");
        when(reservation.revision()).thenReturn(2L); when(reservation.updatedAt()).thenReturn(now);
        when(reservation.expiresAt()).thenReturn(now.plusSeconds(3600));
        var readback = mock(PrerequisiteReadback.class);
        when(readback.observedAt()).thenReturn(now); when(readback.validUntil()).thenReturn(now.plusSeconds(3600));
        when(readback.evidenceReference()).thenReturn("prerequisites:verified");
        when(readback.placement()).thenReturn(mock(Placement.class));
        when(reservation.prerequisiteReadback()).thenReturn(readback);
        when(admissions.activeCustomerReleaseReservation("deployment", "production-release")).thenReturn(reservation);
        when(admissions.activeCustomerReservations("deployment", "staging")).thenReturn(List.of());
        when(retirements.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc(anyString(), anyString()))
            .thenReturn(List.of());

        var installation = new AIWorkspaceInstallationEntity();
        installation.setId("installation-entity"); installation.setInstallationId("installation");
        installation.setStatus(AIWorkspaceInstallationStatus.ACTIVE); installation.setUpdatedAt(now);
        when(installations.findByConsumerEntityIdAndStatusIn(eq("consumer-entity"), any()))
            .thenReturn(List.of(installation));
        when(workspaceReadiness.evaluateAgainst(installation, consumer, "deployment", "production-release"))
            .thenReturn(new AIWorkspaceReadinessSummary(true, "installation", "PUBLIC_RUNTIME_ANONYMOUS",
                "workspace", "deployment", "production-release", "https://runtime.example", "assignment", List.of()));
    }

    @Test
    void derivesAnActivationReadyProjectionFromExactCandidateEvidence() {
        var summary = service.project("offer", "deployment", "production-release", "workspace");

        assertThat(summary.activationReady()).isTrue();
        assertThat(summary.postActivationComplete()).isFalse();
        assertThat(summary.candidateFingerprint()).isEqualTo("candidate-fp");
        assertThat(summary.items()).filteredOn(item -> item.phase().name().equals("ACTIVATION"))
            .allMatch(item -> item.status() == Status.PASSED || item.status() == Status.NOT_APPLICABLE);
        assertThat(summary.items()).extracting("code").contains(
            "reviewed_composition", "production_connections", "knowledge_evidence", "experience_delivery",
            "security_behavior", "operations_ready", "integration_truth", "staging_disposition");
        var run = verifications.findById("verification").orElseThrow();
        assertThat(summary.items()).filteredOn(item -> item.code().equals("reviewed_composition"))
            .singleElement().extracting("expiresAt").isEqualTo(run.getCompletedAt().plusSeconds(3600));
    }

    @Test
    void policyChangeInvalidatesTheFrozenCandidateAndUsesTheSameExpiryPolicy() {
        when(candidates.promotionEvidenceMaxAgeSeconds(any())).thenReturn(1800L);
        var summary = service.project("offer", "deployment", "production-release", "workspace");
        assertThat(summary.activationReady()).isFalse();
        assertThat(summary.items()).filteredOn(item -> item.code().equals("reviewed_composition"))
            .singleElement().satisfies(item -> {
                assertThat(item.status()).isEqualTo(Status.BLOCKED);
                assertThat(item.blocker()).contains("promotion evidence policy changed");
                assertThat(item.expiresAt()).isEqualTo(verifications.findById("verification").orElseThrow()
                    .getCompletedAt().plusSeconds(1800));
            });
    }

    @Test
    void missingRequiredWorkspaceBlocksProjectionAndActivationGuard() {
        when(installations.findByConsumerEntityIdAndStatusIn(eq("consumer-entity"), any())).thenReturn(List.of());

        var summary = service.project("offer", "deployment", "production-release", "workspace");

        assertThat(summary.activationReady()).isFalse();
        assertThat(summary.items()).filteredOn(item -> item.code().equals("experience_delivery"))
            .singleElement().extracting("status").isEqualTo(Status.BLOCKED);
        assertThatThrownBy(() -> service.requireActivationReady(deployment, release, consumer))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("candidate readiness");
    }

    @Test
    void anUnrelatedCurrentAllocationCannotSatisfyTheExactReleaseReadiness() {
        when(admissions.activeCustomerReleaseReservation("deployment", "production-release"))
            .thenThrow(new IllegalStateException("ACTIVE_RELEASE_ALLOCATION_REQUIRED"));

        var summary = service.project("offer", "deployment", "production-release", "workspace");

        assertThat(summary.activationReady()).isFalse();
        assertThat(summary.items()).filteredOn(item -> item.code().equals("production_connections"))
            .singleElement().extracting("status").isEqualTo(Status.BLOCKED);
    }

    @Test
    void unsupportedManagedPersistenceBlocksProjectionAndActivationDespiteFreshStoredEvidence() {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.PRECONDITION_FAILED,
            "INBOX_PRODUCTION_POSTGRES_REQUIRED")).when(dependencyEvidence).requireProductionPersistence(definition);

        var summary = service.project("offer", "deployment", "production-release", "workspace");

        assertThat(summary.activationReady()).isFalse();
        assertThat(summary.items()).filteredOn(item -> item.code().equals("reviewed_composition"))
            .singleElement().extracting("status").isEqualTo(Status.BLOCKED);
        assertThatThrownBy(() -> service.requireActivationReady(deployment, release, consumer))
            .hasMessageContaining("Managed production dependency persistence");
        verify(dependencyEvidence, times(2)).requireProductionPersistence(definition);
    }

    @Test
    void nonCustomerActivationKeepsTheExistingBoundary() {
        when(associations.findById("deployment")).thenReturn(Optional.empty());

        service.requireActivationReady(deployment, release, consumer);

        verifyNoInteractions(dependencyEvidence);
    }

    @Test
    void coreVerifiedReadinessDoesNotRequireCapacityReservationsOrAutomaticRetirement() {
        var operations = mock(com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsService.class);
        var bindings = mock(com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "deploymentOperations", operations);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "documentBindings", bindings);
        when(operations.status()).thenReturn(new com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsContracts.Status(
            "DISABLED",true,false,"TEAM_AUTHORIZED","EXTERNAL_SERVICE"));

        var summary = service.project("offer", "deployment", "production-release", "workspace");

        assertThat(summary.activationReady()).isTrue();
        assertThat(summary.postActivationComplete()).isTrue();
        assertThat(summary.items()).filteredOn(item -> item.code().equals("operations_ready")
            || item.code().equals("staging_disposition"))
            .allMatch(item -> !item.mandatory() && item.status() == Status.NOT_APPLICABLE);
        verify(operations).require(deployment,version,release);
        verify(bindings).requireUnchanged(deployment,version,release);
        verifyNoInteractions(admissions, retirements);
    }

    @Test
    void optionalPolicyBypassCannotReplaceActualConnectionOrVerificationEvidence() {
        var operations = mock(com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsService.class);
        var bindings = mock(com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "deploymentOperations", operations);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "documentBindings", bindings);
        when(operations.status()).thenReturn(new com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsContracts.Status(
            "ENFORCE",true,true,"TEAM_AUTHORIZED","EXTERNAL_SERVICE"));
        doThrow(new IllegalStateException("DOCUMENT_BINDING_CHANGED")).when(bindings)
            .requireUnchanged(deployment,version,release);

        var summary = service.project("offer", "deployment", "production-release", "workspace");

        assertThat(summary.activationReady()).isFalse();
        assertThat(summary.items()).filteredOn(item -> item.code().equals("production_connections"))
            .singleElement().extracting("status").isEqualTo(Status.BLOCKED);
        verifyNoInteractions(admissions, retirements);
    }

    private Definition definition() {
        Set<Prerequisite> prerequisites = EnumSet.allOf(Prerequisite.class);
        return new Definition("1", "Dealership", "dealership",
            new PluginPin("template", "template-version", "manifest"), List.of(),
            List.of(new ArtifactPin("artifact", "artifact-fp")),
            List.of(new TargetPermission("staging", TargetIntent.CUSTOMER_STAGING, "staging-fp"),
                new TargetPermission("production", TargetIntent.CUSTOMER_PRODUCTION, "production-fp")),
            Set.of(Operation.APP_CONFIGURE, Operation.PROMOTION_APPLY, Operation.ACTIVATE, Operation.READ),
            List.of(), List.of(new BindingSlot("documents", BindingKind.CUSTOMER_S3, Set.of("s3"))),
            Set.of(DocumentAudience.PUBLIC), prerequisites,
            new ReviewedLimits("limits", 1, 1, 500, 1024, 1024, 1, 1, 1, 1, 24, 24, 24),
            new ReviewedTopology(1, 500, 1024, 1024, 1, 1, 1, 1),
            "approved-policy", "operating-policy", "identity-policy", "storage-policy");
    }
}
