package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceArtifactService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService;
import com.ai.fabric.platform.backend.deployment.service.MilvusTokenRuntimeArtifactGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CustomerAdmissionManifestServiceTest extends AdmissionFixture {
    private final DeploymentService deployments = mock(DeploymentService.class);
    private final DeploymentDocumentBindingResolver documentBindings = mock(DeploymentDocumentBindingResolver.class);
    private final ReviewedManagedDependencyEvidenceService dependencyEvidence =
        mock(ReviewedManagedDependencyEvidenceService.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private CustomerAdmissionManifestService service;
    private DeploymentEntity deployment;
    private DeploymentVersionEntity version;
    private Authorization authorization;

    @Test
    void missingTokenArtifactGateBlocksManifestBeforeAuthorityAndSourceEffects() {
        version.setProviderConfigJson("""
            {"vectorStrategy":"milvus","vectorProvisioningMode":"EXTERNAL_EXISTING","vectorStoragePosture":"SHARED",
             "milvusExternalBindingRequired":true,"milvusAuthenticationType":"TOKEN"}
            """);
        assertThatThrownBy(() -> service.prepare(deployment, version, authorization))
            .hasMessageContaining("MILVUS_TOKEN_ARTIFACT_GATE_REQUIRED");
        verifyNoInteractions(deployments, documentBindings, dependencyEvidence, audit);
    }

    @BeforeEach
    void setup() {
        Budget budget = new Budget(capacity(8), vector(0), vector(0), "reviewed-measurement",
            NOW.minusSeconds(1), NOW.plusSeconds(10_000));
        FleetPolicy policy = new FleetPolicy("policy1", budget,
            Map.of(TARGET, new TargetBudget(TARGET, Intent.CUSTOMER_STAGING, STAGING, "profile-fingerprint", budget)),
            policy(8).lifecycle(), "restore-proof", "privacy-proof", "support-proof");
        store.approvePolicy(policy, "operator", NOW);
        store.approveGrant(new CustomerGrant("customer", capacity(8), Map.of(TARGET, capacity(8)),
            NOW.plusSeconds(5_000), "verified-owner", "payer", "keep-entitlement"), "operator", NOW);

        deployment = new DeploymentEntity();
        deployment.setId("deployment1");
        deployment.setCustomerId("customer");
        deployment.setTenantId("tenant");
        version = new DeploymentVersionEntity();
        version.setId("version");
        version.setDeploymentId("deployment1");
        version.setStatus("PUBLISHED");
        version.setConfigHash("config-hash");

        ReviewedTopology topology = new ReviewedTopology(1, 3_750, 5_888, 16_384, 2, 2, 4, 1);
        Definition definition = definition(topology);
        var context = new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext(
            "customer", "tenant", "deployment1", null, TARGET, TargetIntent.CUSTOMER_STAGING,
            "offer", "offer-fingerprint", 2, "placement", Operation.APPLY, "operation",
            definition.limits(), topology);
        authorization = new Authorization(context, definition);

        var documentSnapshot = mapper.createObjectNode();
        documentSnapshot.put("schema", "DOCUMENT_STORAGE_BINDING_V1");
        documentSnapshot.put("resourceHandleId", "binding");
        when(documentBindings.snapshot(deployment, version, TARGET)).thenReturn(documentSnapshot);
        when(deployments.prepareAuthorizedRuntimeAuthorityCandidate(deployment, version, TARGET))
            .thenReturn(new RuntimeAuthorityCandidateEvidence("deployment1", TARGET, "version", "snapshot",
                "a".repeat(64), 1, List.of("https://app.example.test")));
        when(dependencyEvidence.resolve(any(), any())).thenReturn(java.util.Optional.of(
            new ReviewedManagedDependencyEvidenceService.Evidence(
                "template-version", "d".repeat(64), "managed-dependency-spec:" + "d".repeat(64))));
        service = new CustomerAdmissionManifestService(store, json, deployments, documentBindings,
            dependencyEvidence, audit);
    }

    @Test
    void customerProvidedStateDoesNotReserveTheManagedPostgresFootprint() throws Exception {
        storageChoice();
        version.setProviderConfigJson("{\"aiStateStorageMode\":\"CUSTOMER_POSTGRES\"}");
        var manifest = service.prepare(deployment, version, authorization);
        assertThat(manifest.demand().amounts()).containsEntry(Dimension.DATABASES, 1L)
            .containsEntry(Dimension.CPU_MILLICORES, 3_250L)
            .containsEntry(Dimension.MEMORY_BYTES, 4_864L * 1024L * 1024L);
    }

    @Test void sameVersionManifestRemainsExactAfterExplicitPolicyContinuity() {
        var manifest = service.prepare(deployment, version, authorization);
        var context = store.context("customer", "tenant", deployment.getId(), manifest.id());
        var reservation = store.reserve(context, new Reserve("operation", manifest.id(), authorization.context()), evidence(context, NOW), "owner", NOW);
        var next = AdmissionPolicyContinuityTest.continuation(store, json, "policy-next", List.of(reservation), NOW, NOW.plusSeconds(300));
        AdmissionPolicyContinuityTest.publishFixture(store, jdbc, next, NOW);
        assertThat(service.prepare(deployment, version, authorization)).isEqualTo(manifest);
        assertThat(store.require(reservation.id())).isEqualTo(reservation);
        jdbc.update("DELETE FROM deployment_admission_reviews WHERE kind='POLICY_CONT'");
        assertThatThrownBy(() -> service.prepare(deployment, version, authorization)).hasMessageContaining("POLICY_CONTINUITY_REQUIRED");
    }

    @Test
    void managedStateReservesItsExplicitReviewedFootprint() throws Exception {
        storageChoice();
        version.setProviderConfigJson("{\"aiStateStorageMode\":\"MANAGED_POSTGRES\"}");
        var manifest = service.prepare(deployment, version, authorization);
        assertThat(manifest.demand().amounts()).containsEntry(Dimension.DATABASES, 2L)
            .containsEntry(Dimension.CPU_MILLICORES, 3_750L);
    }

    @ParameterizedTest
    @CsvSource({"{},CUSTOMER_AI_STATE_STORAGE_CHOICE_REQUIRED", "{\"aiStateStorageMode\":true},CUSTOMER_AI_STATE_STORAGE_CHOICE_REQUIRED",
        "{\"aiStateStorageMode\":\"H2\"},CUSTOMER_AI_STATE_STORAGE_CHOICE_INVALID"})
    void missingOrUnreviewedStorageCannotSilentlyPickADatabase(String provider, String code) throws Exception {
        storageChoice();
        version.setProviderConfigJson(provider);
        assertThatThrownBy(() -> service.prepare(deployment, version, authorization)).hasMessageContaining(code);
        verifyNoInteractions(deployments, dependencyEvidence, audit);
    }

    private void storageChoice() throws Exception {
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(authorization.definition());
        tree.withArray("settings").addObject().put("setting", "AI_STATE_STORAGE_MODE")
            .put("type", "CHOICE").put("maxLength", 32).putArray("allowedValues")
            .add("MANAGED_POSTGRES").add("CUSTOMER_POSTGRES");
        var topologies = tree.putObject("aiStateStorageTopologies");
        topologies.set("MANAGED_POSTGRES", mapper.valueToTree(authorization.context().topology()));
        topologies.set("CUSTOMER_POSTGRES", mapper.valueToTree(
            new ReviewedTopology(1, 3_250, 4_864, 12_288, 1, 2, 4, 1)));
        authorization = new Authorization(authorization.context(), mapper.treeToValue(tree, Definition.class));
    }

    @Test
    void derivesAndPersistsExactManifestWithoutCallerSelectedCapacityOrReferences() {
        Manifest first = service.prepare(deployment, version, authorization);
        Manifest replay = service.prepare(deployment, version, authorization);

        assertThat(replay).isEqualTo(first);
        assertThat(first.id()).startsWith("amf-").hasSize(64);
        assertThat(first.bindingFingerprint()).isEqualTo("a".repeat(64));
        assertThat(first.demand().amounts())
            .containsEntry(Dimension.CPU_MILLICORES, 3_750L)
            .containsEntry(Dimension.MEMORY_BYTES, 5_888L * 1024L * 1024L)
            .containsEntry(Dimension.DISK_BYTES, 16_384L * 1024L * 1024L)
            .containsEntry(Dimension.DATABASES, 2L)
            .containsEntry(Dimension.INDEXES, 2L)
            .containsEntry(Dimension.DEPENDENCIES, 4L);
        assertThat(first.customerPrerequisiteReferences()).containsKeys(
            Prerequisite.APPROVED_POLICY, Prerequisite.IMMUTABLE_ARTIFACTS, Prerequisite.STORAGE,
            Prerequisite.IDENTITY, Prerequisite.NOTIFICATIONS, Prerequisite.OPERATING_POLICY);
        assertThat(first.customerPrerequisiteReferences()).doesNotContainKey(Prerequisite.CAPACITY);
        assertThat(first.customerPrerequisiteReferences().get(Prerequisite.STORAGE)).startsWith("document-binding:");
        assertThat(first.prerequisiteReferences()).contains("managed-dependency-spec:" + "d".repeat(64));
        assertThat(store.reviewed("MANIFEST", first.id(), Manifest.class)).isEqualTo(first);
    }

    @Test
    void generatedManifestIsConsumableByManagedDependencyLedger() {
        Manifest manifest = service.prepare(deployment, version, authorization);
        var context = store.context("customer", "tenant", deployment.getId(), manifest.id());
        var reservation = store.reserve(context, new Reserve("apply", manifest.id()),
            evidence(context, NOW), "owner", NOW);
        var ledger = new ManagedDependencyAdmissionLedger(store, json, current -> evidence(current, NOW),
            java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        var result = ledger.require(new ManagedDependencyAdmissionLedger.Requirement(
            "customer", "tenant", deployment.getId(), TARGET, version.getId(), "a".repeat(64),
            "d".repeat(64), 1_000, 2L * 1024L * 1024L * 1024L,
            6L * 1024L * 1024L * 1024L, 1, 4), reservation.id());

        assertThat(result.reservationId()).isEqualTo(reservation.id());
    }

    @Test
    void missingRequiredStorageFailsBeforeAuthorityMaterialIsGenerated() {
        when(documentBindings.snapshot(deployment, version, TARGET)).thenReturn(mapper.createObjectNode());

        assertThatThrownBy(() -> service.prepare(deployment, version, authorization))
            .hasMessageContaining("CUSTOMER_ADMISSION_STORAGE_BINDING_REQUIRED");
        verifyNoInteractions(deployments);
    }

    @Test
    void realTokenGateAcceptsReviewedRuntimeBeforeManifestAndAuthorityEffects() throws Exception {
        var fixture = tokenFixture(List.of(new ArtifactPin("runtime", "artifact-hash"),
            new ArtifactPin("dependency", "dependency-hash")));
        when(fixture.artifacts().require("runtime")).thenReturn(tokenArtifact("0.8.36", true));
        var dependency = new DeploymentSourceArtifactEntity();
        dependency.setArtifactRole("MANAGED_DEPENDENCY");
        when(fixture.artifacts().require("dependency")).thenReturn(dependency);

        Manifest manifest = service.prepare(deployment, version, authorization);

        var ordered = inOrder(fixture.artifacts(), fixture.observedStore(), documentBindings,
            dependencyEvidence, deployments, audit);
        ordered.verify(fixture.artifacts(), times(2)).require("runtime");
        ordered.verify(fixture.artifacts()).require("dependency");
        ordered.verify(fixture.observedStore()).activePolicy();
        ordered.verify(fixture.observedStore()).customerGrant("customer");
        ordered.verify(documentBindings).snapshot(deployment, version, TARGET);
        ordered.verify(dependencyEvidence).resolve(eq(authorization.definition()), any());
        ordered.verify(deployments).prepareAuthorizedRuntimeAuthorityCandidate(deployment, version, TARGET);
        ordered.verify(fixture.observedStore()).approveManifest(eq(manifest), anyString(), any());
        ordered.verify(audit).record(eq("CUSTOMER_ADMISSION_MANIFEST_MATERIALIZED"), eq("DEPLOYMENT"),
            eq(deployment.getId()), anyMap());
        assertThat(store.reviewed("MANIFEST", manifest.id(), Manifest.class)).isEqualTo(manifest);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='MANIFEST'", Integer.class))
            .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_capacity_reservations", Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"manifest-hash", "framework", "missing-capability", "promotion"})
    void realTokenGateRejectsAnyStaleRuntimeBeforeAdmissionOrAuthorityEffects(String change) throws Exception {
        var fixture = tokenFixture(List.of(new ArtifactPin("runtime", "artifact-hash"),
            new ArtifactPin("stale-runtime", "stale-artifact-hash")));
        when(fixture.artifacts().require("runtime")).thenReturn(tokenArtifact("0.8.36", true));
        var stale = tokenArtifact("framework".equals(change) ? "0.8.35" : "0.8.36",
            !"missing-capability".equals(change));
        if ("manifest-hash".equals(change)) stale.setCapabilityManifestHash("changed");
        if ("promotion".equals(change)) stale.setPromotedAt(null);
        when(fixture.artifacts().require("stale-runtime")).thenReturn(stale);

        assertThatThrownBy(() -> service.prepare(deployment, version, authorization))
            .hasMessageContaining("MILVUS_TOKEN_RUNTIME_ARTIFACT_REQUIRED");

        verify(fixture.artifacts(), times(2)).require("runtime");
        verify(fixture.artifacts(), times(2)).require("stale-runtime");
        verifyNoInteractions(fixture.observedStore(), deployments, documentBindings, dependencyEvidence, audit);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='MANIFEST'", Integer.class))
            .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_capacity_reservations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_events", Integer.class)).isZero();
    }

    @Test
    void realTokenGateRejectsCandidatesWithoutRuntimeBeforeAdmissionEffects() {
        var fixture = tokenFixture(List.of(new ArtifactPin("dependency", "dependency-hash")));
        var dependency = new DeploymentSourceArtifactEntity();
        dependency.setArtifactRole("MANAGED_DEPENDENCY");
        when(fixture.artifacts().require("dependency")).thenReturn(dependency);

        assertThatThrownBy(() -> service.prepare(deployment, version, authorization))
            .hasMessageContaining("MILVUS_TOKEN_RUNTIME_ARTIFACT_REQUIRED");

        verifyNoInteractions(fixture.observedStore(), deployments, documentBindings, dependencyEvidence, audit);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='MANIFEST'", Integer.class))
            .isZero();
    }

    private TokenFixture tokenFixture(List<ArtifactPin> pins) {
        version.setAiFabricFrameworkVersion("0.8.36");
        version.setProviderConfigJson("""
            {"vectorStrategy":"milvus","vectorProvisioningMode":"EXTERNAL_EXISTING","vectorStoragePosture":"SHARED",
             "milvusExternalBindingRequired":true,"milvusAuthenticationType":"TOKEN"}
            """);
        authorization = new Authorization(authorization.context(), definition(authorization.context().topology(), pins));
        var artifacts = mock(DeploymentSourceArtifactService.class);
        var observedStore = spy(store);
        service = new CustomerAdmissionManifestService(observedStore, json, deployments, documentBindings, dependencyEvidence, audit);
        ReflectionTestUtils.setField(service, "milvusTokenArtifacts", new MilvusTokenRuntimeArtifactGate(artifacts,
            new DeploymentSourceCapabilityManifestService(mapper)));
        return new TokenFixture(artifacts, observedStore);
    }

    private DeploymentSourceArtifactEntity tokenArtifact(String frameworkVersion, boolean tokenSupport) throws Exception {
        var manifest = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree("""
            {"schemaVersion":"loomai-runtime-capabilities-v1","aiFabricVersion":"0.8.36",
             "supportedBehaviorTypes":["CONVERSATIONAL"],"supportedActivationSources":["AUTHENTICATED_INTERACTIVE"],
             "supportedChannelBindings":["BACKEND_API"],"supportedExecutionExtensions":[],
             "capabilities":[],"endpointClasses":[],"migrationIds":[],"verificationPackIds":[]}
            """);
        manifest.put("aiFabricVersion", frameworkVersion);
        if (tokenSupport) manifest.withArray("capabilities").add(MilvusTokenRuntimeArtifactGate.CAPABILITY);
        var normalized = new DeploymentSourceCapabilityManifestService(mapper).normalize(manifest);
        var artifact = new DeploymentSourceArtifactEntity();
        artifact.setArtifactRole("RUNTIME"); artifact.setArtifactType("DOCKER_IMAGE");
        artifact.setImageDigest("sha256:" + "a".repeat(64)); artifact.setGitCommitSha("b".repeat(40));
        artifact.setBuildRunId("reviewed-build"); artifact.setPromotionChannel("staging"); artifact.setPromotedAt(NOW);
        artifact.setCapabilityManifestJson(normalized.manifest().toString());
        artifact.setCapabilityManifestHash(normalized.hash());
        return artifact;
    }

    private record TokenFixture(DeploymentSourceArtifactService artifacts, AdmissionStore observedStore) { }

    private Definition definition(ReviewedTopology topology) {
        return definition(topology, List.of(new ArtifactPin("runtime", "artifact-hash")));
    }

    private Definition definition(ReviewedTopology topology, List<ArtifactPin> artifacts) {
        return new Definition("1", "Reviewed offer", "template",
            new PluginPin("template-plugin", "template-version", "manifest-hash"), List.of(),
            artifacts,
            List.of(new TargetPermission(TARGET, TargetIntent.CUSTOMER_STAGING, "profile-fingerprint")),
            EnumSet.of(Operation.APPLY), List.of(),
            List.of(new BindingSlot("document-storage", BindingKind.CUSTOMER_S3, Set.of("reviewed-s3"))),
            Set.of(DocumentAudience.PUBLIC), EnumSet.allOf(Prerequisite.class),
            new ReviewedLimits("reviewed-measurement", 1, 1, 4_000, 6_144, 16_384, 2, 2, 4,
                1, 24, 24, 24), topology, "approved-policy", "operating-policy", "identity-policy", "storage-policy");
    }

    private Vector capacity(long value) {
        return with(with(with(vector(value), Dimension.CPU_MILLICORES, 20_000),
            Dimension.MEMORY_BYTES, 64L * 1024L * 1024L * 1024L),
            Dimension.DISK_BYTES, 256L * 1024L * 1024L * 1024L);
    }
}
