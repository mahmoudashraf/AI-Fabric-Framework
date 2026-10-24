package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerReviewedCompositionService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.operations.CoreReleaseAuthorizationStore;
import com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsContracts.EffectiveDecision;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedDependencyCoreReleaseAuthorizationTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final CustomerOfferCodec codec = new CustomerOfferCodec(mapper);
    private final ManagedDependencySpecValidator validator = new ManagedDependencySpecValidator(mapper);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    private final ManagedDependencySpecRepository specs = mock(ManagedDependencySpecRepository.class);
    private final ManagedDependencyCustomerOfferGuard guard = mock(ManagedDependencyCustomerOfferGuard.class);
    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final CustomerOfferRepository offers = mock(CustomerOfferRepository.class);
    private final CustomerReviewedCompositionService composition = mock(CustomerReviewedCompositionService.class);
    private final CustomerTargetPlacementService placement = mock(CustomerTargetPlacementService.class);
    private final ManagedDependencyGraphEntity graph = new ManagedDependencyGraphEntity();
    private final DeploymentEntity deployment = new DeploymentEntity();
    private final DeploymentVersionEntity version = new DeploymentVersionEntity();
    private final DeploymentReleaseEntity release = new DeploymentReleaseEntity();
    private final DeploymentTargetProfileEntity target = new DeploymentTargetProfileEntity();
    private final ManagedDependencySpecEntity reviewed = new ManagedDependencySpecEntity();
    private final ManagedDependencySpec spec = new ManagedDependencySpec("managed-dependencies/v1",
        List.of(ManagedDependencyFixtures.productionInbox()));
    private CoreReleaseAuthorizationStore authorizations;
    private ManagedDependencyCoreReleaseAuthorization service;
    private ManagedDependencyPrerequisites.Scope scope;
    private Definition definition;

    @BeforeEach
    void setup() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:managed-dependency-core-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        // Deliberately no admission, reservation, manifest, capacity-policy or expiry tables.
        jdbc.execute("""
            CREATE TABLE platform_release_authorizations (
                release_id VARCHAR(64) PRIMARY KEY,customer_id VARCHAR(64),tenant_id VARCHAR(64),
                deployment_id VARCHAR(64),target_profile_id VARCHAR(64),version_id VARCHAR(64),
                version_config_hash VARCHAR(64),source_artifact_id VARCHAR(64),operation_key VARCHAR(128),
                actor_id VARCHAR(256),provider_usage_mode VARCHAR(32),operations_mode VARCHAR(16),
                decision VARCHAR(32),reason VARCHAR(128),request_fingerprint VARCHAR(64),decision_json TEXT,
                created_at TIMESTAMP WITH TIME ZONE)
            """);
        authorizations = new CoreReleaseAuthorizationStore(jdbc, mapper);
        deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        version.setId("version"); version.setDeploymentId("deployment"); version.setStatus("PUBLISHED");
        version.setConfigHash("a".repeat(64));
        version.setCompositionProvenanceJson("{\"marketplaceInstalls\":[{\"pluginVersionId\":\"template-version\"}]}");
        release.setId("release"); release.setDeploymentId("deployment"); release.setDeploymentVersionId("version");
        release.setTargetProfileId("target"); release.setSourceArtifactId("runtime-artifact");
        target.setId("target"); target.setActive(true); target.setProviderType(DeploymentProviderType.COOLIFY);
        target.setEnvironmentName("staging");
        graph.setId("graph"); graph.setSpecRef("spec"); graph.setReservationRef("core-release:release");
        reviewed.setId("spec"); reviewed.setActive(true); reviewed.setTemplateVersionId("template-version");
        reviewed.setReviewedBy("reviewer"); reviewed.setReviewedAt(Instant.now()); reviewed.setReviewReference("review");
        reviewed.setSpecJson(codec.write(spec)); reviewed.setFingerprint(validator.fingerprint(spec));
        scope = new ManagedDependencyPrerequisites.Scope("graph", "customer", "tenant", "deployment", "target",
            "version", "release", reviewed.getFingerprint(), "b".repeat(64));
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        when(versions.findById("version")).thenReturn(Optional.of(version));
        when(releases.findById("release")).thenReturn(Optional.of(release));
        when(targets.findById("target")).thenReturn(Optional.of(target));
        when(specs.findById("spec")).thenReturn(Optional.of(reviewed));
        service = new ManagedDependencyCoreReleaseAuthorization(authorizations, deployments, versions, releases,
            targets, specs, validator, guard, associations, offers, codec, composition, placement);
    }

    @Test
    void realCoreRecordAuthorizesWithoutAnyAdmissionTables() {
        save("DISABLED", "BYPASSED");

        var proof = service.find(scope, graph, spec).orElseThrow();

        assertThat(proof.releaseId()).isEqualTo("release");
        assertThat(proof.versionConfigHash()).isEqualTo(version.getConfigHash());
        assertThat(proof.production()).isFalse();
        verifyNoInteractions(guard, composition, placement);
    }

    @Test
    void customerProductionIntentComesFromReviewedTargetNotItsDisplayEnvironment() {
        customer(TargetIntent.CUSTOMER_PRODUCTION);
        save("OBSERVE", "OBSERVED");

        assertThat(service.find(scope, graph, spec).orElseThrow().production()).isTrue();

        verify(guard).requireActiveExecution(graph);
        verify(composition).requireVersion(definition, version);
        verify(composition).requireProfile(target, definition.targets().getFirst());
        verify(placement).requireConfiguredPlacement(target, TargetIntent.CUSTOMER_PRODUCTION);
    }

    @Test
    void customerStagingKeepsCorePlacementButDoesNotRequireAPlacementLease() {
        customer(TargetIntent.CUSTOMER_STAGING);
        target.setEnvironmentName("production");
        save("ENFORCE", "ALLOWED");

        assertThat(service.find(scope, graph, spec).orElseThrow().production()).isFalse();
        verify(placement).requireConfiguredPlacement(target, TargetIntent.CUSTOMER_STAGING);
        verifyNoMoreInteractions(placement);
    }

    @Test
    void withdrawnEntitlementOrChangedCompositionStillBlocksRequiredDependencies() {
        customer(TargetIntent.CUSTOMER_STAGING);
        save("DISABLED", "BYPASSED");
        doThrow(new IllegalStateException("DEPENDENCY_ENTITLEMENT_INACTIVE")).when(guard).requireActiveExecution(graph);
        assertThatThrownBy(() -> service.find(scope, graph, spec)).hasMessageContaining("DEPENDENCY_ENTITLEMENT_INACTIVE");
        verifyNoInteractions(composition, placement);

        reset(guard);
        doThrow(new IllegalStateException("REVIEWED_COMPOSITION_CHANGED")).when(composition).requireVersion(definition, version);
        assertThatThrownBy(() -> service.find(scope, graph, spec)).hasMessageContaining("REVIEWED_COMPOSITION_CHANGED");
        verifyNoInteractions(placement);
    }

    @Test
    void rejectedConfiguredPlacementCannotBeReplacedWithAdmissionEvidence() {
        customer(TargetIntent.CUSTOMER_PRODUCTION);
        save("DISABLED", "BYPASSED");
        doThrow(new IllegalStateException("PLACEMENT_DENIED")).when(placement)
            .requireConfiguredPlacement(target, TargetIntent.CUSTOMER_PRODUCTION);
        assertThatThrownBy(() -> service.find(scope, graph, spec)).hasMessageContaining("PLACEMENT_DENIED");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changedIdentity")
    void exactCoreScopeAndFrozenConfigCannotDrift(String name, Consumer<ManagedDependencyCoreReleaseAuthorizationTest> change) {
        save("DISABLED", "BYPASSED");
        change.accept(this);
        assertThatThrownBy(() -> service.find(scope, graph, spec)).isInstanceOf(RuntimeException.class);
    }

    private static Stream<Arguments> changedIdentity() {
        return Stream.of(
            change("customer", value -> value.deployment.setCustomerId("other")),
            change("tenant", value -> value.deployment.setTenantId("other")),
            change("deployment", value -> value.release.setDeploymentId("other")),
            change("target", value -> value.release.setTargetProfileId("other")),
            change("version", value -> value.release.setDeploymentVersionId("other")),
            change("config", value -> value.version.setConfigHash("c".repeat(64))),
            change("runtime source", value -> value.release.setSourceArtifactId("other")),
            change("unpublished version", value -> value.version.setStatus("DRAFT")),
            change("archived owner", value -> value.deployment.setArchivedAt(Instant.now())),
            change("inactive target", value -> value.target.setActive(false)),
            change("revoked spec", value -> value.reviewed.setActive(false)),
            change("unreviewed spec", value -> value.reviewed.setReviewedBy(null)),
            change("spec fingerprint", value -> value.reviewed.setFingerprint("d".repeat(64))),
            change("different spec JSON", value -> value.reviewed.setSpecJson(value.codec.write(
                new ManagedDependencySpec("managed-dependencies/v1", List.of(ManagedDependencyFixtures.http("other", false)))))),
            change("unrelated template", value -> value.reviewed.setTemplateVersionId("other"))
        );
    }

    private static Arguments change(String name, Consumer<ManagedDependencyCoreReleaseAuthorizationTest> change) {
        return Arguments.of(name, change);
    }

    @Test
    void missingCoreRecordCannotStartANewReservationOrDowngradeACoreGraph() {
        assertThatThrownBy(() -> service.find(scope, graph, spec))
            .hasMessageContaining("DEPENDENCY_CORE_RELEASE_AUTHORIZATION_REQUIRED");
        graph.setReservationRef(null);
        assertThatThrownBy(() -> service.find(scope, graph, spec))
            .hasMessageContaining("DEPENDENCY_CORE_RELEASE_AUTHORIZATION_REQUIRED");
        graph.setReservationRef("historical-reservation");
        assertThat(service.find(scope, graph, spec)).isEmpty();
    }

    private void save(String mode, String decision) {
        authorizations.save(deployment, version, release, "operation", "TEAM_AUTHORIZED",
            new EffectiveDecision(mode, decision, "REVIEWED", "e".repeat(64), null, null,
                Instant.parse("2020-01-01T00:00:00Z")));
    }

    private void customer(TargetIntent intent) {
        definition = new Definition("customer-offer/v1", "Offer", "template",
            new PluginPin("template", "template-version", "f".repeat(64)), List.of(), List.of(),
            List.of(new TargetPermission("target", intent, "reviewed-profile")), Set.of(Operation.APPLY), List.of(),
            List.of(), Set.of(DocumentAudience.PUBLIC), Set.of(),
            new ReviewedLimits("unused-policy", 1, 1, 2000, 4096, 8192, 1, 1, 1, 1, 24, 24, 24),
            new ReviewedTopology(1, 2000, 4096, 8192, 1, 1, 1, 1), "approved", "operating", "identity", "storage");
        when(associations.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            "deployment", "customer", "tenant", "offer", "target", "offer-fingerprint", 1, "operation", Instant.now())));
        when(offers.findById("offer")).thenReturn(Optional.of(new CustomerOfferEntity(
            "offer", codec.write(definition), "offer-fingerprint", "review", "reviewer", Instant.now())));
    }
}
