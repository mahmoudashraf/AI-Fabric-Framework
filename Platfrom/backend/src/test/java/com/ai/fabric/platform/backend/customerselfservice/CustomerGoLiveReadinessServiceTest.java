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
import com.ai.fabric.platform.backend.deployment.dependency.*;
import com.ai.fabric.platform.backend.deployment.operations.CoreReleaseAuthorizationStore;
import com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsContracts;
import com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentPromotionCandidateService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    private final CustomerReviewedCompositionService composition = mock(CustomerReviewedCompositionService.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ManagedDependencySpecRepository dependencySpecs = mock(ManagedDependencySpecRepository.class);
    private final ManagedDependencySpecValidator dependencyValidator = spy(new ManagedDependencySpecValidator(mapper));
    private final ManagedDependencyArtifactPolicy dependencyOwnership = mock(ManagedDependencyArtifactPolicy.class);
    private final CoreReleaseAuthorizationStore releaseAuthorizations = mock(CoreReleaseAuthorizationStore.class);
    private final DeploymentOperationsService operations = mock(DeploymentOperationsService.class);
    private final DeploymentDocumentBindingResolver bindings = mock(DeploymentDocumentBindingResolver.class);
    private final CustomerGoLiveReadinessService service = new CustomerGoLiveReadinessService(
        entitlements, associations, deployments, releases, versions, targets, verifications, consumers,
        installations, workspaceReadiness, candidates, composition, dependencySpecs, dependencyValidator,
        dependencyOwnership, releaseAuthorizations, operations, bindings, mapper);

    private DeploymentEntity deployment;
    private DeploymentVersionEntity version;
    private DeploymentReleaseEntity release;
    private PlatformConsumerEntity consumer;
    private Definition definition;
    private ManagedDependencySpecEntity reviewedDependency;

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

        var topology = ManagedDependencyFixtures.topology();
        var production = new ManagedDependencySpec(topology.schemaVersion(), topology.services().stream()
            .map(value -> "inbox".equals(value.slot()) ? ManagedDependencyFixtures.productionInbox() : value).toList(),
            topology.runtimeEnvironment());
        reviewedDependency = new ManagedDependencySpecEntity();
        reviewedDependency.setId("spec"); reviewedDependency.setTemplateVersionId("template-version");
        reviewedDependency.setSpecJson(mapper.writeValueAsString(production));
        reviewedDependency.setFingerprint(dependencyValidator.fingerprint(production));
        reviewedDependency.setActive(true); reviewedDependency.setReviewedBy("reviewer");
        reviewedDependency.setReviewedAt(now); reviewedDependency.setReviewReference("dependency-review");
        when(dependencySpecs.findByTemplateVersionIdAndActiveTrue("template-version")).thenReturn(List.of(reviewedDependency));
        when(operations.status()).thenReturn(new DeploymentOperationsContracts.Status(
            "DISABLED", true, false, "TEAM_AUTHORIZED", "EXTERNAL_SERVICE"));

        var installation = new AIWorkspaceInstallationEntity();
        installation.setId("installation-entity"); installation.setInstallationId("installation");
        installation.setStatus(AIWorkspaceInstallationStatus.ACTIVE); installation.setUpdatedAt(now);
        when(installations.findByConsumerEntityIdAndStatusIn(eq("consumer-entity"), any()))
            .thenReturn(List.of(installation));
        when(workspaceReadiness.evaluateAgainst(installation, consumer, "deployment", "production-release"))
            .thenReturn(new AIWorkspaceReadinessSummary(true, "installation", "PUBLIC_RUNTIME_ANONYMOUS",
                "workspace", "deployment", "production-release", "https://runtime.example", "assignment", List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST_APPLY", "MANUAL_RERUN"})
    void derivesAnActivationReadyProjectionFromExactCandidateEvidence(String verificationType) {
        verifications.findById("verification").orElseThrow().setVerificationType(verificationType);
        var summary = service.project("offer", "deployment", "production-release", "workspace");

        assertThat(summary.activationReady()).isTrue();
        assertThat(summary.postActivationComplete()).isTrue();
        assertThat(summary.candidateFingerprint()).isEqualTo("candidate-fp");
        assertThat(summary.items()).filteredOn(item -> item.phase().name().equals("ACTIVATION"))
            .allMatch(item -> item.status() == Status.PASSED || item.status() == Status.NOT_APPLICABLE);
        assertThat(summary.items()).extracting("code").contains(
            "reviewed_composition", "production_connections", "knowledge_evidence", "experience_delivery",
            "security_behavior", "operations_ready", "integration_truth", "staging_disposition");
        var run = verifications.findById("verification").orElseThrow();
        assertThat(summary.items()).filteredOn(item -> item.code().equals("reviewed_composition"))
            .singleElement().extracting("expiresAt").isEqualTo(run.getCompletedAt().plusSeconds(3600));
        verify(candidates).requireFreshVerification(deployment, version, release,
            targets.findById("production").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRE_APPLY", "CONTROL_PLANE_READINESS", "UNKNOWN"})
    void unrelatedVerificationTypesCannotAuthorizeActivation(String verificationType) {
        verifications.findById("verification").orElseThrow().setVerificationType(verificationType);

        var summary = service.project("offer", "deployment", "production-release", "workspace");

        assertThat(summary.activationReady()).isFalse();
        assertThat(summary.items()).filteredOn(item -> item.code().equals("reviewed_composition"))
            .singleElement().satisfies(item -> {
                assertThat(item.status()).isEqualTo(Status.BLOCKED);
                assertThat(item.blocker()).contains("Exact verification run is unavailable");
            });
    }

    @Test
    void manualRecheckStillRequiresExactFreshPassingEvidence() {
        var run = verifications.findById("verification").orElseThrow();
        run.setVerificationType("MANUAL_RERUN");
        run.setDeploymentId("foreign-deployment");
        assertThat(service.project("offer", "deployment", "production-release", "workspace").activationReady()).isFalse();
        run.setDeploymentId("deployment");
        run.setReleaseId("foreign-release");
        assertThat(service.project("offer", "deployment", "production-release", "workspace").activationReady()).isFalse();
        run.setReleaseId("production-release");
        run.setDeploymentVersionId("foreign-version");
        assertThat(service.project("offer", "deployment", "production-release", "workspace").activationReady()).isFalse();
        run.setDeploymentVersionId("version");
        run.setStatus("FAILED");
        assertThat(service.project("offer", "deployment", "production-release", "workspace").activationReady()).isFalse();
        run.setStatus("PASSED");
        doThrow(new IllegalStateException("Verification is stale"))
            .when(candidates).requireFreshVerification(eq(deployment), eq(version), eq(release), any());
        assertThat(service.project("offer", "deployment", "production-release", "workspace").activationReady()).isFalse();
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
    void missingExactCoreReleaseAuthorityCannotBeReplacedByOptionalPolicyStatus() {
        when(releaseAuthorizations.require(deployment, version, release))
            .thenThrow(new IllegalStateException("CORE_RELEASE_AUTHORIZATION_REQUIRED"));

        var summary = service.project("offer", "deployment", "production-release", "workspace");

        assertThat(summary.activationReady()).isFalse();
        assertThat(summary.items()).filteredOn(item -> item.code().equals("production_connections"))
            .singleElement().extracting("status").isEqualTo(Status.BLOCKED);
    }

    @Test
    void unsupportedManagedPersistenceBlocksProjectionAndActivationDespiteFreshStoredEvidence() {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.PRECONDITION_FAILED,
            "INBOX_PRODUCTION_POSTGRES_REQUIRED")).when(dependencyValidator).requireProductionInboxPersistence(any());

        var summary = service.project("offer", "deployment", "production-release", "workspace");

        assertThat(summary.activationReady()).isFalse();
        assertThat(summary.items()).filteredOn(item -> item.code().equals("reviewed_composition"))
            .singleElement().extracting("status").isEqualTo(Status.BLOCKED);
        assertThatThrownBy(() -> service.requireActivationReady(deployment, release, consumer))
            .hasMessageContaining("Managed production dependency persistence");
        verify(dependencyValidator, times(2)).requireProductionInboxPersistence(any());
    }

    @Test
    void nonCustomerActivationKeepsTheExistingBoundary() {
        when(associations.findById("deployment")).thenReturn(Optional.empty());

        service.requireActivationReady(deployment, release, consumer);

        verifyNoInteractions(dependencySpecs, releaseAuthorizations, bindings);
    }

    @Test
    void coreVerifiedReadinessDoesNotRequireCapacityReservationsOrAutomaticRetirement() {
        when(operations.status()).thenReturn(new com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsContracts.Status(
            "DISABLED",true,false,"TEAM_AUTHORIZED","EXTERNAL_SERVICE"));

        var summary = service.project("offer", "deployment", "production-release", "workspace");

        assertThat(summary.activationReady()).isTrue();
        assertThat(summary.postActivationComplete()).isTrue();
        assertThat(summary.items()).filteredOn(item -> item.code().equals("operations_ready")
            || item.code().equals("staging_disposition"))
            .allMatch(item -> !item.mandatory() && item.status() == Status.NOT_APPLICABLE);
        verify(releaseAuthorizations).require(deployment,version,release);
        verify(bindings).requireUnchanged(deployment,version,release);
    }

    @Test
    void optionalPolicyBypassCannotReplaceActualConnectionOrVerificationEvidence() {
        when(operations.status()).thenReturn(new com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsContracts.Status(
            "ENFORCE",true,true,"TEAM_AUTHORIZED","EXTERNAL_SERVICE"));
        doThrow(new IllegalStateException("DOCUMENT_BINDING_CHANGED")).when(bindings)
            .requireUnchanged(deployment,version,release);

        var summary = service.project("offer", "deployment", "production-release", "workspace");

        assertThat(summary.activationReady()).isFalse();
        assertThat(summary.items()).filteredOn(item -> item.code().equals("production_connections"))
            .singleElement().extracting("status").isEqualTo(Status.BLOCKED);
    }

    @Test
    void unavailableOperationsStatusDoesNotBlockOtherwiseVerifiedProduction() {
        when(operations.status()).thenThrow(new IllegalStateException("OPERATIONS_UNAVAILABLE"));
        assertThat(service.project("offer", "deployment", "production-release", "workspace").activationReady()).isTrue();
        verify(releaseAuthorizations).require(deployment, version, release);
        verify(bindings).requireUnchanged(deployment, version, release);
    }

    @Test
    void missingDriftedOrUnownedRequiredDependenciesBlockReadiness() {
        reviewedDependency.setFingerprint("changed");
        assertThat(service.project("offer", "deployment", "production-release", "workspace").activationReady()).isFalse();
        reviewedDependency.setFingerprint(dependencyValidator.fingerprint(
            new CustomerOfferCodec(mapper).read(reviewedDependency.getSpecJson(), ManagedDependencySpec.class)));
        doThrow(new IllegalStateException("DEPENDENCY_ARTIFACT_OWNERSHIP_REQUIRED"))
            .when(dependencyOwnership).requireAllowed(any(ManagedDependencySpec.class));
        assertThat(service.project("offer", "deployment", "production-release", "workspace").activationReady()).isFalse();
        when(dependencySpecs.findByTemplateVersionIdAndActiveTrue("template-version")).thenReturn(List.of());
        assertThat(service.project("offer", "deployment", "production-release", "workspace").activationReady()).isFalse();
    }

    @Test
    void aPassedVerificationForAnotherReleaseCannotSatisfyReadiness() {
        verifications.findById("verification").orElseThrow().setReleaseId("foreign-release");
        assertThat(service.project("offer", "deployment", "production-release", "workspace").activationReady()).isFalse();
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
