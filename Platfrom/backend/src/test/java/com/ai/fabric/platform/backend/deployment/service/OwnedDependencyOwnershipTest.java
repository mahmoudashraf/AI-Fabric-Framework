package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProductProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyFixtures;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyResource;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.provider.CoolifyHostVolumeReclamationService;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OwnedDependencyOwnershipTest {
    final ObjectMapper mapper = new ObjectMapper();
    final CoolifyApiClient api = mock(CoolifyApiClient.class);
    final CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
    final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    final CoolifyHostVolumeReclamationService host = mock(CoolifyHostVolumeReclamationService.class);
    final CoolifyTargetProfileConfig config = new CoolifyTargetProfileConfig("https://coolify.example.test", "project", "staging",
        "environment", "server", "destination", "example.test", null, 1, 10, false, true, "8080", "/health", "8080");
    final CoolifyConnection connection = new CoolifyConnection(config.baseUrl(), "private", config);
    OwnedDependencyOwnershipFixture fixture;
    PlatformManagedProductProvisioningService service;
    PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint checkpoint;

    @BeforeEach void setup() {
        fixture = new OwnedDependencyOwnershipFixture(api, targets, resolver, mapper, connection);
        checkpoint = spy(fixture.checkpoint);
        var properties = mock(PlatformProductProvisioningProperties.class);
        when(properties.requestTimeout()).thenReturn(Duration.ofMillis(5));
        when(properties.pollInterval()).thenReturn(Duration.ZERO);
        service = new PlatformManagedProductProvisioningService(mock(PlatformProvisioningProperties.class), properties,
            mock(PlatformDeliveryProperties.class), mock(RailwayGraphqlClient.class), resolver, api, targets,
            mock(PlatformSecretService.class), mock(PlatformManagedProductServiceRepository.class), mock(ShopifyStoreConnectionRepository.class),
            mock(PlatformManagedProductServiceService.class), mock(PlatformAuditService.class), mapper,
            new CustomerSelfServiceProperties(Map.of(TargetIntent.CUSTOMER_STAGING,
                new CustomerSelfServiceProperties.TrustedServer("server", "synthetic-address", "synthetic-reported-address")), 60), host);
        service.setOwnedDependencyOwnership(fixture.authority);
        when(api.start(eq(connection), anyString(), eq(true), eq(true))).thenAnswer(call ->
            new CoolifyActionResponse("ok", "build-" + call.getArgument(1), mapper.createObjectNode()));
        when(api.getDeployment(eq(connection), anyString())).thenAnswer(call -> {
            String build = call.getArgument(1); String uuid = build.substring("build-".length());
            return Optional.of(new CoolifyDeploymentSummary(build, "synthetic", uuid, "finished", null, null, null, null, null, mapper.createObjectNode()));
        });
        when(host.verifyContainersAbsent(any(), anyString(), anyString(), anyString(), anyString())).thenReturn(
            new CoolifyHostVolumeReclamationService.ContainerReceipt("container-receipt", Instant.now()));
        when(host.reclaimAndVerify(any(), anyString(), anyString(), anyList())).thenAnswer(call -> {
            List<CoolifyHostVolumeReclamationService.Volume> volumes = call.getArgument(3);
            return new CoolifyHostVolumeReclamationService.Receipt("host-receipt", Instant.now(), volumes.stream()
                .map(CoolifyHostVolumeReclamationService.Volume::uuid).collect(java.util.stream.Collectors.toSet()));
        });
    }

    private PlatformManagedProductProvisioningService.OwnedDependencyResources empty(boolean application, boolean database) {
        return new PlatformManagedProductProvisioningService.OwnedDependencyResources(null, null, application, database, Map.of());
    }

    private ManagedDependencySpec.Service inbox() {
        var service = ManagedDependencyFixtures.http("inbox", false);
        return new ManagedDependencySpec.Service(service.slot(), service.contract(), service.image(),
            service.sourceArtifactId(), service.sourceCommit(), 8085, service.healthPath(), service.capacity(),
            service.secretPurposes(), service.environment(), service.database(), service.volumes(),
            service.dependsOn(), service.initializationContract());
    }

    private CoolifyApplicationSummary app(String uuid, String ref, String slot) {
        return new CoolifyApplicationSummary(uuid, ref, "https://" + ref + ".example.test", "running:healthy",
            "registry.example.test/dependency", "sha256-" + "a".repeat(64), fixture.raw(uuid, ref, "ManagedDependency:graph:" + slot));
    }

    private void existing(boolean receipts) {
        fixture.graph(inbox(), "inbox-service", new PlatformManagedProductProvisioningService.OwnedDependencyResources(
            "app", null, true, false, Map.of("/app/data", "volume")));
        fixture.applications.put("app", app("app", "inbox-service", "inbox"));
        fixture.appStorages.put("app", List.of(fixture.storage("volume", "app-inbox-state", "/app/data")));
        if (receipts) fixture.receipts();
    }

    private void reconcile(ManagedDependencySpec.Service spec, String ref) {
        service.reconcileOwnedDependency(connection, "graph", ref, spec, fixture.resources(),
            ignored -> List.of(new CoolifyEnvVar("__DATABASE_PASSWORD", "private", false, true, false, true)), checkpoint);
    }

    private void checkpointReady() {
        existing(true);
        var before = fixture.applications.get("app");
        var raw = (com.fasterxml.jackson.databind.node.ObjectNode) before.raw();
        raw.put("build_pack", "dockerimage").put("ports_exposes", "8085")
            .put("health_check_enabled", true).put("health_check_path", "/actuator/health")
            .put("health_check_port", "8085").put("health_check_interval", 10)
            .put("health_check_timeout", 5).put("health_check_retries", 18).put("health_check_start_period", 180)
            .put("limits_memory", "512M").put("limits_cpus", "0.250");
        when(api.requireRuntimeEnvironmentValue(connection, "app", "API_KEY")).thenReturn("private-exact-value");
        var job = new CoolifyDeploymentSummary("finished-job", "inbox-service", "app", "finished", null, null,
            "2026-01-01T00:00:00Z", "2026-01-01T00:01:00Z", "2026-01-01T00:01:00Z", mapper.createObjectNode());
        when(api.applicationDeploymentHistory(connection, "app")).thenReturn(List.of(job));
        when(api.getDeployment(connection, "finished-job")).thenReturn(Optional.of(job));
    }

    private PlatformManagedProductProvisioningService.OwnedDependencyResult verifyCheckpoint() {
        return service.verifyOwnedDependencyCheckpoint(connection, "graph", "inbox-service", inbox(), fixture.resources(),
            ignored -> List.of(new CoolifyEnvVar("API_KEY", "private-exact-value", false, true, false, true)),
            "https://inbox-service.example.test", checkpoint);
    }

    @Test void completedCheckpointIsVerifiedWithoutProviderMutation() {
        checkpointReady();
        assertThat(verifyCheckpoint()).isEqualTo(new PlatformManagedProductProvisioningService.OwnedDependencyResult(
            "app", null, "https://inbox-service.example.test"));
        verify(checkpoint, times(2)).guard();
        verify(checkpoint, never()).creating(anyString());
        noMutations();
    }

    @Test void nativeHistoryApplicationIdIsVerifiedAgainstTheOwnedAppAndDetailedJob() {
        checkpointReady();
        var exact = api.applicationDeploymentHistory(connection, "app").getFirst();
        assertThat(fixture.applications.get("app").raw().has("id")).isFalse();
        ((com.fasterxml.jackson.databind.node.ObjectNode) exact.raw()).putObject("application").put("id", 277).put("uuid", "app");
        var listed = new CoolifyDeploymentSummary(exact.deploymentUuid(), exact.applicationName(), null, exact.status(),
            null, null, exact.createdAt(), exact.updatedAt(), exact.finishedAt(), mapper.createObjectNode().put("application_id", "277"));
        when(api.applicationDeploymentHistory(connection, "app")).thenReturn(List.of(listed));
        assertThatCode(this::verifyCheckpoint).doesNotThrowAnyException();
        ((com.fasterxml.jackson.databind.node.ObjectNode) listed.raw()).put("application_id", "278");
        assertThatThrownBy(this::verifyCheckpoint).hasMessageContaining("DEPENDENCY_DEPLOYMENT_READBACK_REQUIRED");
        ((com.fasterxml.jackson.databind.node.ObjectNode) listed.raw()).put("application_id", "277");
        ((com.fasterxml.jackson.databind.node.ObjectNode) exact.raw().path("application")).remove("id");
        assertThatThrownBy(this::verifyCheckpoint).hasMessageContaining("DEPENDENCY_DEPLOYMENT_READBACK_REQUIRED");
        noMutations();
    }

    @Test void detailedNativeIdCannotAuthorizeADeploymentForAnotherApplication() {
        checkpointReady();
        var job = api.applicationDeploymentHistory(connection, "app").getFirst();
        var listed = new CoolifyDeploymentSummary(job.deploymentUuid(), job.applicationName(), null, job.status(),
            null, null, job.createdAt(), job.updatedAt(), job.finishedAt(), mapper.createObjectNode().put("application_id", "277"));
        var raw = mapper.createObjectNode(); raw.putObject("application").put("id", 277).put("uuid", "foreign");
        var foreign = new CoolifyDeploymentSummary(job.deploymentUuid(), job.applicationName(), "foreign", job.status(),
            null, null, job.createdAt(), job.updatedAt(), job.finishedAt(), raw);
        when(api.applicationDeploymentHistory(connection, "app")).thenReturn(List.of(listed));
        when(api.getDeployment(connection, job.deploymentUuid())).thenReturn(Optional.of(foreign));
        assertThatThrownBy(this::verifyCheckpoint).hasMessageContaining("DEPENDENCY_DEPLOYMENT_READBACK_REQUIRED");
        noMutations();
    }

    @ParameterizedTest @ValueSource(strings = {"healthy", "capacity-drift", "unhealthy"})
    void checkpointReuseSupportsGenericAiServicesWithOwnedPostgres(String scenario) {
        var spec = ManagedDependencyFixtures.http("state", true);
        existingDatabase("postgres://dependency:synthetic-password@" + DATABASE_UUID + ":5432/dependency", false);
        fixture.graph(spec, "state-service", new PlatformManagedProductProvisioningService.OwnedDependencyResources(
            "app", DATABASE_UUID, true, true, Map.of()));
        var app = app("app", "state-service", "state");
        ((com.fasterxml.jackson.databind.node.ObjectNode) app.raw())
            .put("build_pack", "dockerimage").put("ports_exposes", "8080")
            .put("health_check_enabled", true).put("health_check_path", "/actuator/health")
            .put("health_check_port", "8080").put("health_check_interval", 10)
            .put("health_check_timeout", 5).put("health_check_retries", 18).put("health_check_start_period", 180)
            .put("limits_memory", "512M").put("limits_cpus", "0.250");
        fixture.applications.put("app", app); fixture.receipts();
        var database = fixture.databases.get(DATABASE_UUID);
        if ("capacity-drift".equals(scenario)) ((com.fasterxml.jackson.databind.node.ObjectNode) database.raw()).put("limits_memory", "128M");
        if ("unhealthy".equals(scenario)) fixture.databases.put(DATABASE_UUID,
            new CoolifyDatabaseSummary(database.uuid(), database.name(), "running:unhealthy", database.databaseType(),
                database.postgresUser(), database.postgresDatabase(), database.raw()));
        var job = new CoolifyDeploymentSummary("finished-job", "state-service", "app", "finished", null, null,
            "2026-01-01T00:00:00Z", "2026-01-01T00:01:00Z", "2026-01-01T00:01:00Z", mapper.createObjectNode());
        when(api.applicationDeploymentHistory(connection, "app")).thenReturn(List.of(job));
        when(api.getDeployment(connection, "finished-job")).thenReturn(Optional.of(job));
        Runnable verify = () -> service.verifyOwnedDependencyCheckpoint(connection, "graph", "state-service", spec,
            fixture.resources(), ignored -> List.of(), "https://state-service.example.test", checkpoint);
        if ("healthy".equals(scenario)) assertThatCode(verify::run).doesNotThrowAnyException();
        else assertThatThrownBy(verify::run).hasMessageContaining("DEPENDENCY_DATABASE_");
        noMutations();
    }

    @ParameterizedTest
    @ValueSource(strings = {"image", "port", "health", "interval", "memory", "environment", "missing-job",
        "pending-job", "foreign-job", "changed-job", "duplicate-job", "unhealthy", "ownership"})
    void checkpointDriftIsVisibleAndNeverRepairedByRedeploy(String change) {
        checkpointReady();
        var before = fixture.applications.get("app");
        var raw = (com.fasterxml.jackson.databind.node.ObjectNode) before.raw();
        var job = api.applicationDeploymentHistory(connection, "app").getFirst();
        switch (change) {
            case "image" -> fixture.applications.put("app", new CoolifyApplicationSummary(before.uuid(), before.name(),
                before.fqdn(), before.status(), before.imageRepository(), "sha256-" + "f".repeat(64), raw));
            case "port" -> raw.put("ports_exposes", "9999");
            case "health" -> raw.put("health_check_enabled", false);
            case "interval" -> raw.put("health_check_interval", 5);
            case "memory" -> raw.put("limits_memory", "128M");
            case "environment" -> when(api.requireRuntimeEnvironmentValue(connection, "app", "API_KEY")).thenReturn("different-value");
            case "missing-job" -> when(api.applicationDeploymentHistory(connection, "app")).thenReturn(List.of());
            case "pending-job", "foreign-job" -> when(api.applicationDeploymentHistory(connection, "app")).thenReturn(List.of(
                new CoolifyDeploymentSummary(job.deploymentUuid(), job.applicationName(), "foreign-job".equals(change) ? "foreign" : "app",
                    "pending-job".equals(change) ? "in_progress" : "finished", null, null, job.createdAt(), job.updatedAt(), job.finishedAt(), job.raw())));
            case "changed-job" -> when(api.getDeployment(connection, "finished-job")).thenReturn(Optional.empty());
            case "duplicate-job" -> when(api.applicationDeploymentHistory(connection, "app")).thenReturn(List.of(job, job));
            case "unhealthy" -> fixture.applications.put("app", new CoolifyApplicationSummary(before.uuid(), before.name(),
                before.fqdn(), "running:unhealthy", before.imageRepository(), before.imageTag(), raw));
            case "ownership" -> fixture.jdbc.update("DELETE FROM platform_managed_resource_ownership WHERE resource_kind=?", OwnedDependencyOwnership.APPLICATION);
            default -> throw new AssertionError(change);
        }
        assertThatThrownBy(this::verifyCheckpoint).isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageNotContaining("private-exact-value").hasMessageNotContaining("different-value");
        noMutations();
    }

    private void noMutations() {
        verify(api, never()).createDockerImageApplication(any(), any());
        verify(api, never()).createPostgresDatabase(any(), any());
        verify(api, never()).updateDockerImageApplication(any(), anyString(), any());
        verify(api, never()).updateEnvironmentVariables(any(), anyString(), anyList());
        verify(api, never()).start(any(), anyString(), anyBoolean(), anyBoolean());
        verify(api, never()).stop(any(), anyString(), anyBoolean());
        verify(api, never()).startDatabase(any(), anyString());
        verify(api, never()).delete(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
        verify(api, never()).deleteDatabase(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
        verify(fixture.authority, never()).postVolume(any(), anyString(), anyString(), anyString());
        verifyNoInteractions(host);
    }

    @Test void matchingNameAndMarkerNeverRecoverAnUnknownApplicationCreate() {
        fixture.graph(inbox(), "inbox-service", empty(true, false));
        when(api.listApplications(connection)).thenReturn(List.of(app("lookalike", "inbox-service", "inbox")));
        assertThatThrownBy(() -> reconcile(inbox(), "inbox-service")).hasMessageContaining("CREATE_UNCERTAIN");
        verify(api, never()).listApplications(any());
        verify(checkpoint, never()).created(anyString(), anyString());
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM platform_managed_resource_ownership", Long.class)).isZero();
        noMutations();
    }

    @Test void matchingDatabaseNeverRecoversAnUnknownDatabaseCreate() {
        var spec = ManagedDependencyFixtures.http("state", true);
        fixture.graph(spec, "state-service", empty(false, true));
        assertThatThrownBy(() -> reconcile(spec, "state-service")).hasMessageContaining("CREATE_UNCERTAIN");
        verify(api, never()).listDatabases(any());
        noMutations();
    }

    @Test void historicalGraphMetadataCannotAuthorizeAnyLifecycleOrCredentialPath() {
        existing(false);
        var resources = fixture.resources();
        List<Runnable> operations = List.of(
            () -> reconcile(inbox(), "inbox-service"),
            () -> service.disableOwnedDependency(connection, "graph", "inbox-service", "inbox", "app", checkpoint),
            () -> service.verifyOwnedDependencyReady(connection, "graph", "inbox-service", "inbox", "app", null, resources.volumes()),
            () -> service.retireOwnedDependency(connection, "graph", "inbox-service", "inbox", resources, checkpoint),
            () -> service.verifyOwnedDependenciesReclaimed(connection, "graph", List.of(new ManagedDependencyResource(
                "inbox", "inbox-service", "app", null, resources.volumes()))));
        for (var operation : operations) assertThatThrownBy(operation::run).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        noMutations();
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer_id", "tenant_id", "deployment_id", "target_profile_id", "parent_identity", "endpoint"})
    void everyReceiptScopeAndParentCoordinateMustMatch(String column) {
        existing(true);
        fixture.jdbc.update("UPDATE platform_managed_resource_ownership SET " + column + "=? WHERE resource_kind=?",
            "endpoint".equals(column) ? "https://other.example.test" : "other", OwnedDependencyOwnership.APPLICATION);
        assertThatThrownBy(() -> service.disableOwnedDependency(connection, "graph", "inbox-service", "inbox", "app", checkpoint))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        noMutations();
    }

    @Test void graphScopeCannotBorrowAnotherCustomerDeployment() {
        existing(true);
        fixture.graphRows.get("graph").setCustomerId("other-customer");
        assertThatThrownBy(() -> service.disableOwnedDependency(connection, "graph", "inbox-service", "inbox", "app", checkpoint))
            .hasMessageContaining("OWNERSHIP_CONFLICT");
        noMutations();
    }

    @ParameterizedTest
    @ValueSource(strings = {"app", "volume"})
    void referencesFromAnotherGraphDenyLifecycleAndRetirement(String uuid) {
        existing(true);
        fixture.jdbc.update("INSERT INTO deployment_managed_dependency_graphs VALUES(?,?)", "other-graph",
            mapper.createObjectNode().set("nodes", mapper.createArrayNode().add(mapper.createObjectNode()
                .put("slot", "other").put("serviceRef", "other-service").put("applicationUuid", uuid))).toString());
        assertThatThrownBy(() -> service.disableOwnedDependency(connection, "graph", "inbox-service", "inbox", "app", checkpoint))
            .hasMessageContaining("OWNERSHIP_CONFLICT");
        assertThatThrownBy(() -> service.retireOwnedDependency(connection, "graph", "inbox-service", "inbox", fixture.resources(), checkpoint))
            .hasMessageContaining("OWNERSHIP_CONFLICT");
        noMutations();
    }

    @Test void sharedServiceAndProviderHandleReferencesDenyMutation() {
        existing(true);
        fixture.jdbc.update("INSERT INTO platform_managed_product_services VALUES(?)", "{\"coolifyApplicationUuid\":\"app\"}");
        assertThatThrownBy(() -> reconcile(inbox(), "inbox-service")).hasMessageContaining("OWNERSHIP_CONFLICT");
        fixture.jdbc.update("DELETE FROM platform_managed_product_services");
        fixture.jdbc.update("INSERT INTO deployment_provider_resource_handles(provider_resource_uuid,status,metadata_json) VALUES('app','ACTIVE','{}')");
        assertThatThrownBy(() -> service.disableOwnedDependency(connection, "graph", "inbox-service", "inbox", "app", checkpoint))
            .hasMessageContaining("OWNERSHIP_CONFLICT");
        noMutations();
    }

    @Test void graphIdIsPartOfReceiptIdentityEvenForTheSameDeploymentAndTarget() {
        existing(true);
        var original = fixture.graphRows.get("graph");
        original.setId("replacement-graph"); fixture.save(original);
        fixture.graphRows.remove("graph");
        fixture.jdbc.update("DELETE FROM deployment_managed_dependency_graphs WHERE id='graph'");
        assertThatThrownBy(() -> service.disableOwnedDependency(connection, "replacement-graph", "inbox-service", "inbox", "app", checkpoint))
            .hasMessageContaining("OWNERSHIP_CONFLICT");
        noMutations();
    }

    @ParameterizedTest
    @ValueSource(strings = {"project_id", "environment_id", "uuid", "destination_uuid"})
    void livePlacementAndUuidReadbackAreMandatory(String drift) {
        existing(true);
        if (drift.equals("project_id")) {
            var raw = mapper.createObjectNode().put("uuid", "environment").put("id", 20).put("project_id", 11);
            raw.putArray("applications").addObject().put("uuid", "app").put("name", "inbox-service").put("environment_id", 20);
            when(api.getEnvironment(connection, "project", "environment")).thenReturn(Optional.of(
                new CoolifyEnvironmentSummary("environment", "staging", "project", null, raw)));
        } else {
            var raw = (com.fasterxml.jackson.databind.node.ObjectNode) fixture.applications.get("app").raw();
            if (drift.equals("environment_id")) raw.put(drift, 21); else raw.put(drift, "other");
        }
        assertThatThrownBy(() -> service.disableOwnedDependency(connection, "graph", "inbox-service", "inbox", "app", checkpoint))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        noMutations();
    }

    @Test void directGetWithoutParentFieldsWorksOnlyWithExactScopedInventory() {
        existing(true);
        var raw = fixture.applications.get("app").raw();
        assertThat(raw.has("project_uuid")).isFalse(); assertThat(raw.has("environment_uuid")).isFalse();
        service.verifyOwnedDependencyReady(connection, "graph", "inbox-service", "inbox", "app", null, fixture.resources().volumes());
        noMutations();
    }

    @Test void createJournalsBeforeEffectAndRequiresExactUuidReadback() {
        fixture.graph(inbox(), "inbox-service", empty(false, false));
        when(api.createDockerImageApplication(eq(connection), any())).thenAnswer(call -> {
            assertThat(fixture.jdbc.queryForObject("SELECT state FROM platform_managed_resource_ownership", String.class)).isEqualTo("CREATE_PENDING");
            fixture.applications.put("app", app("different", "inbox-service", "inbox"));
            return "app";
        });
        assertThatThrownBy(() -> reconcile(inbox(), "inbox-service")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(fixture.jdbc.queryForObject("SELECT state FROM platform_managed_resource_ownership", String.class)).isEqualTo("CREATE_PENDING");
        verify(checkpoint, never()).created(anyString(), anyString());
        assertThatThrownBy(() -> reconcile(inbox(), "inbox-service")).hasMessageContaining("CREATE_UNCERTAIN");
        verify(api, times(1)).createDockerImageApplication(any(), any());
        verify(api, never()).updateEnvironmentVariables(any(), anyString(), anyList());
    }

    @Test void unknownMountCannotBecomeANewlyOwnedVolume() {
        existing(true);
        fixture.mutate(node -> node.volumes.clear());
        fixture.jdbc.update("DELETE FROM platform_managed_resource_ownership WHERE resource_kind=?", OwnedDependencyOwnership.APP_VOLUME);
        assertThatThrownBy(() -> reconcile(inbox(), "inbox-service")).hasMessageContaining("VOLUME_OWNERSHIP_CONFLICT");
        assertThatThrownBy(() -> service.retireOwnedDependency(connection, "graph", "inbox-service", "inbox", fixture.resources(), checkpoint))
            .hasMessageContaining("VOLUME_OWNERSHIP_CONFLICT");
        noMutations();
    }

    @Test void recordedVolumeUuidStillNeedsItsCreationStorageIdentity() {
        existing(true);
        fixture.appStorages.put("app", List.of(fixture.storage("volume", "renamed-unknown-volume", "/app/data")));
        assertThatThrownBy(() -> reconcile(inbox(), "inbox-service")).hasMessageContaining("VOLUME_OWNERSHIP_CONFLICT");
        noMutations();
    }

    @Test void ambiguousVolumeCreateRemainsBlockedEvenWhenItsMountAppears() {
        fixture.graph(inbox(), "inbox-service", empty(false, false));
        when(api.createDockerImageApplication(eq(connection), any())).thenAnswer(call -> {
            fixture.applications.put("app", app("app", "inbox-service", "inbox")); return "app";
        });
        doAnswer(call -> {
            assertThat(fixture.jdbc.queryForObject("SELECT state FROM platform_managed_resource_ownership WHERE resource_kind=?", String.class,
                OwnedDependencyOwnership.APP_VOLUME)).isEqualTo("CREATE_PENDING");
            fixture.appStorages.put("app", List.of(fixture.storage("volume", "app-inbox-state", "/app/data")));
            throw new IllegalStateException("synthetic unknown storage outcome");
        }).when(fixture.authority).postVolume(any(), anyString(), anyString(), anyString());
        assertThatThrownBy(() -> reconcile(inbox(), "inbox-service")).hasMessageContaining("unknown storage outcome");
        assertThatThrownBy(() -> reconcile(inbox(), "inbox-service")).hasMessageContaining("VOLUME_OWNERSHIP_CONFLICT");
        assertThatThrownBy(() -> service.disableOwnedDependency(connection, "graph", "inbox-service", "inbox", "app", checkpoint))
            .hasMessageContaining("VOLUME_OWNERSHIP_CONFLICT");
        verify(fixture.authority, times(1)).postVolume(any(), anyString(), anyString(), anyString());
        verify(checkpoint, never()).volume(anyString(), anyString());
        verify(api, never()).updateEnvironmentVariables(any(), anyString(), anyList());
        verify(api, never()).stop(any(), anyString(), anyBoolean());
    }

    @Test void lostVolumeCheckpointDoesNotBackfillTheGraphFromLiveStorage() {
        fixture.graph(inbox(), "inbox-service", empty(false, false));
        when(api.createDockerImageApplication(eq(connection), any())).thenAnswer(call -> {
            fixture.applications.put("app", app("app", "inbox-service", "inbox")); return "app";
        });
        doThrow(new IllegalStateException("synthetic checkpoint loss")).when(checkpoint).volume(anyString(), anyString());
        assertThatThrownBy(() -> reconcile(inbox(), "inbox-service")).hasMessageContaining("checkpoint loss");
        assertThat(fixture.resources().volumes()).isEmpty();
        assertThat(fixture.jdbc.queryForObject("SELECT state FROM platform_managed_resource_ownership WHERE resource_kind=?", String.class,
            OwnedDependencyOwnership.APP_VOLUME)).isEqualTo("OWNED");
        assertThatThrownBy(() -> reconcile(inbox(), "inbox-service")).hasMessageContaining("VOLUME_OWNERSHIP_CONFLICT");
        verify(fixture.authority, times(1)).postVolume(any(), anyString(), anyString(), anyString());
    }

    @Test void optionalAiOperationalServiceCreatesOnlyAiStateWithoutABusinessDatabase() {
        fixture.graph(inbox(), "inbox-service", empty(false, false));
        when(api.createDockerImageApplication(eq(connection), any())).thenAnswer(call -> {
            fixture.applications.put("app", app("app", "inbox-service", "inbox")); return "app";
        });
        reconcile(inbox(), "inbox-service");
        assertThat(fixture.resources().volumes()).containsEntry("/app/data", "volume");
        assertThat(fixture.resources().databaseUuid()).isNull();
        assertThat(fixture.jdbc.queryForList("SELECT state FROM platform_managed_resource_ownership", String.class)).containsOnly("OWNED").hasSize(2);
        verify(api, never()).createPostgresDatabase(any(), any()); verify(api, never()).startDatabase(any(), anyString());
        verify(api, never()).reconcilePersistentVolumeStorage(any(), anyString(), anyString(), anyString());
        var request = org.mockito.ArgumentCaptor.forClass(CoolifyCreateDockerImageApplicationRequest.class);
        verify(api).createDockerImageApplication(eq(connection), request.capture());
        assertStartupBudget(request.getValue());
    }

    @Test void resumedOwnedApplicationReceivesStartupBudgetWithoutCreatingAnotherApplication() {
        existing(true);
        reconcile(inbox(), "inbox-service");
        var request = org.mockito.ArgumentCaptor.forClass(CoolifyCreateDockerImageApplicationRequest.class);
        verify(api).updateDockerImageApplication(eq(connection), eq("app"), request.capture());
        assertStartupBudget(request.getValue());
        assertThat(request.getValue().imageRepository()).isEqualTo("registry.example.test/dependency");
        assertThat(request.getValue().imageTag()).isEqualTo("sha256-" + "a".repeat(64));
        verify(api, never()).createDockerImageApplication(any(), any());
        verify(api, never()).createPostgresDatabase(any(), any());
        verify(api).start(connection, "app", true, true);
    }

    private void assertStartupBudget(CoolifyCreateDockerImageApplicationRequest request) {
        assertThat(request.runtimeSettings().healthCheckIntervalSeconds()).isEqualTo(10);
        assertThat(request.runtimeSettings().healthCheckTimeoutSeconds()).isEqualTo(5);
        assertThat(request.runtimeSettings().healthCheckRetries()).isEqualTo(18);
        assertThat(request.runtimeSettings().healthCheckStartPeriodSeconds()).isEqualTo(180);
    }

    @Test void supportedGenericDatabaseCreatesWithPreEffectStorageJournalAndExactReadback() {
        var spec = ManagedDependencyFixtures.http("state", true);
        fixture.graph(spec, "state-service", empty(false, false));
        when(api.createPostgresDatabase(eq(connection), any())).thenAnswer(call -> {
            assertThat(fixture.jdbc.queryForList("SELECT state FROM platform_managed_resource_ownership", String.class)).hasSize(2).containsOnly("CREATE_PENDING");
            var raw = fixture.raw("db", "state-service-db", "ManagedDependency:graph:state").put("image", spec.database().image())
                .put("is_public", false).put("limits_memory", "512M").put("limits_cpus", "0.250")
                .put("internal_db_url", "postgresql://dependency:private@db:5432/dependency");
            fixture.databases.put("db", new CoolifyDatabaseSummary("db", "state-service-db", "running:healthy", "postgresql", "dependency", "dependency", raw));
            fixture.dbStorages.put("db", List.of(fixture.storage("db-volume", "db-ai-state", "/var/lib/postgresql/data")));
            return "db";
        });
        when(api.createDockerImageApplication(eq(connection), any())).thenAnswer(call -> {
            fixture.applications.put("app", app("app", "state-service", "state")); return "app";
        });
        reconcile(spec, "state-service");
        assertThat(service.ownedDependencyDatabaseJdbc(connection, "db")).isEqualTo("jdbc:postgresql://db:5432/dependency");
        verify(api, never()).startDatabase(any(), anyString());
        assertThat(fixture.jdbc.queryForList("SELECT state FROM platform_managed_resource_ownership", String.class)).hasSize(3).containsOnly("OWNED");
    }

    private static final String DATABASE_UUID = "mhjvz7fg41apfvw34ta5nifk";

    private void existingDatabase(String url, boolean receipts) {
        var spec = ManagedDependencyFixtures.http("state", true);
        fixture.graph(spec, "state-service", new PlatformManagedProductProvisioningService.OwnedDependencyResources(
            null, DATABASE_UUID, false, true, Map.of()));
        var raw = fixture.raw(DATABASE_UUID, "state-service-db", "ManagedDependency:graph:state")
            .put("image", spec.database().image()).put("is_public", false).put("internal_db_url", url)
            .put("limits_memory", "512M").put("limits_cpus", "0.250");
        fixture.databases.put(DATABASE_UUID, new CoolifyDatabaseSummary(DATABASE_UUID, "state-service-db", "running:healthy",
            "postgresql", "dependency", "dependency", raw));
        fixture.dbStorages.put(DATABASE_UUID, List.of(fixture.storage("db-volume", "db-ai-state", "/var/lib/postgresql/data")));
        if (receipts) fixture.receipts();
    }

    @ParameterizedTest
    @ValueSource(strings = {"postgres", "postgresql"})
    void ownedDatabaseJdbcAcceptsBothObservedPostgresSchemesWithoutProjectingCredentials(String scheme) {
        existingDatabase(scheme + "://dependency:synthetic-password@" + DATABASE_UUID + ":5432/dependency", true);
        var before = fixture.jdbc.queryForList("SELECT * FROM platform_managed_resource_ownership ORDER BY id");

        assertThat(service.ownedDependencyDatabaseJdbc(connection, DATABASE_UUID))
            .isEqualTo("jdbc:postgresql://" + DATABASE_UUID + ":5432/dependency")
            .doesNotContain("synthetic-password", "dependency@");

        assertThat(fixture.jdbc.queryForList("SELECT * FROM platform_managed_resource_ownership ORDER BY id")).isEqualTo(before);
        verify(api).getDatabase(connection, DATABASE_UUID);
        noMutations();
    }

    @ParameterizedTest @ValueSource(strings = {"exited", "exited:unhealthy", "stopped"})
    void stoppedOwnedDatabaseStartsExactlyOnceBeforeApplicationSetup(String status) {
        databaseReconciliation(status);
        verify(api).startDatabase(connection, DATABASE_UUID);
    }

    @Test void runningHealthyOwnedDatabaseIsReusedWithoutARejectedStartRequest() {
        databaseReconciliation("running:healthy");
        verify(api, never()).startDatabase(any(), anyString());
    }

    @Test void unknownOwnedDatabaseStateCannotTriggerAnyStartOrApplicationCreation() {
        existingDatabase("postgres://dependency:synthetic-password@" + DATABASE_UUID + ":5432/dependency", true);
        var db = fixture.databases.get(DATABASE_UUID);
        fixture.databases.put(DATABASE_UUID, new CoolifyDatabaseSummary(db.uuid(), db.name(), "unknown", db.databaseType(), db.postgresUser(), db.postgresDatabase(), db.raw()));
        assertThatThrownBy(() -> reconcile(ManagedDependencyFixtures.http("state", true), "state-service"))
            .hasMessageContaining("DEPENDENCY_DATABASE_STATE_UNVERIFIED");
        noMutations();
    }

    private void databaseReconciliation(String status) {
        existingDatabase("postgres://dependency:synthetic-password@" + DATABASE_UUID + ":5432/dependency", true);
        var db = fixture.databases.get(DATABASE_UUID);
        fixture.databases.put(DATABASE_UUID, new CoolifyDatabaseSummary(db.uuid(), db.name(), status, db.databaseType(), db.postgresUser(), db.postgresDatabase(), db.raw()));
        when(api.startDatabase(connection, DATABASE_UUID)).thenAnswer(call -> {
            fixture.databases.put(DATABASE_UUID, db); return new CoolifyActionResponse("started", null, mapper.createObjectNode());
        });
        when(api.createDockerImageApplication(eq(connection), any())).thenAnswer(call -> {
            fixture.applications.put("app", app("app", "state-service", "state")); return "app";
        });
        reconcile(ManagedDependencyFixtures.http("state", true), "state-service");
        verify(api, never()).createPostgresDatabase(any(), any());
        verify(api).updateEnvironmentVariables(eq(connection), eq("app"), anyList());
        verify(api).start(connection, "app", true, true);
    }

    @ParameterizedTest
    @MethodSource("unverifiedDatabaseUrls")
    void ownedDatabaseJdbcRejectsUnverifiedAddressRatherThanGuessingOrStrippingUnsafeParts(String url) {
        existingDatabase(url, true);
        assertThatThrownBy(() -> service.ownedDependencyDatabaseJdbc(connection, DATABASE_UUID))
            .hasMessageContaining("DEPENDENCY_DATABASE_ADDRESS_UNVERIFIED");
        noMutations();
    }

    private static java.util.stream.Stream<String> unverifiedDatabaseUrls() {
        String address = "dependency:synthetic-password@" + DATABASE_UUID;
        String url = "postgres://" + address + ":5432/dependency";
        return java.util.stream.Stream.of(
            "mysql://" + address + ":5432/dependency", "jdbc:postgresql://" + address + ":5432/dependency",
            "postgres:dependency", "postgres://", "postgres://" + address + ":invalid/dependency",
            "postgres://dependency:synthetic-password@foreign-db:5432/dependency",
            "postgres://dependency:synthetic-password@127.0.0.1:5432/dependency",
            "postgres://dependency:synthetic-password@[::1]:5432/dependency",
            "postgres://" + address + "/dependency", "postgres://" + address + ":5433/dependency",
            "postgres://" + address + ":65536/dependency", "postgres://" + address + ":5432/other",
            "postgres://" + address + ":5432/%64ependency", url + "/", url + "?sslmode=disable", url + "#fragment");
    }

    @Test void validPostgresUrlCannotBypassMissingOrForeignOwnershipReceipts() {
        existingDatabase("postgres://dependency:synthetic-password@" + DATABASE_UUID + ":5432/dependency", false);
        assertThatThrownBy(() -> service.ownedDependencyDatabaseJdbc(connection, DATABASE_UUID))
            .hasMessageContaining("MANAGED_RESOURCE_OWNERSHIP_REQUIRED");
        fixture.receipts();
        fixture.jdbc.update("UPDATE platform_managed_resource_ownership SET customer_id='foreign-customer'");
        assertThatThrownBy(() -> service.ownedDependencyDatabaseJdbc(connection, DATABASE_UUID))
            .hasMessageContaining("OWNERSHIP_CONFLICT");
        verify(api, never()).getDatabase(any(), anyString());
        noMutations();
    }

    @Test void authorityIsRecheckedAfterTheLifecycleGuardBeforeMutation() {
        existing(true);
        doAnswer(call -> {
            fixture.jdbc.update("INSERT INTO platform_managed_product_services VALUES(?)", "{\"coolifyApplicationUuid\":\"app\"}");
            return null;
        }).when(checkpoint).guard();
        assertThatThrownBy(() -> service.disableOwnedDependency(connection, "graph", "inbox-service", "inbox", "app", checkpoint))
            .hasMessageContaining("OWNERSHIP_CONFLICT");
        noMutations();
    }

    @Test void ambiguousDeletePersistsPendingAndCanOnlyResumeThroughExactAbsenceReadback() {
        existing(true);
        doAnswer(call -> {
            assertThat(fixture.jdbc.queryForList("SELECT state FROM platform_managed_resource_ownership", String.class)).containsOnly("DELETE_PENDING");
            fixture.applications.remove("app");
            throw new IllegalStateException("synthetic unknown delete outcome");
        }).when(api).delete(connection, "app", true, true, false, false);
        assertThatThrownBy(() -> service.retireOwnedDependency(connection, "graph", "inbox-service", "inbox", fixture.resources(), checkpoint))
            .hasMessageContaining("unknown delete outcome");
        verifyNoInteractions(host);
        service.retireOwnedDependency(connection, "graph", "inbox-service", "inbox", fixture.resources(), checkpoint);
        verify(api, times(1)).delete(connection, "app", true, true, false, false);
        assertThat(fixture.jdbc.queryForList("SELECT state FROM platform_managed_resource_ownership", String.class)).containsOnly("DELETED");
        var resources = fixture.resources();
        service.verifyOwnedDependenciesReclaimed(connection, "graph", List.of(new ManagedDependencyResource(
            "inbox", "inbox-service", "app", null, resources.volumes(), resources.volumeReclamationPlan(), resources.reclaimedVolumeUuids())));
    }

    @Test void missingResourceWithOnlyOwnedReceiptIsNotADeletionReceipt() {
        existing(true);
        fixture.applications.clear();
        assertThatThrownBy(() -> service.retireOwnedDependency(connection, "graph", "inbox-service", "inbox", fixture.resources(), checkpoint))
            .hasMessageContaining("OWNERSHIP_CONFLICT");
        assertThat(fixture.jdbc.queryForList("SELECT state FROM platform_managed_resource_ownership", String.class)).containsOnly("OWNED");
        noMutations();
    }

    @Test void graphReclamationPlanCannotAuthorizeUnknownHostVolumes() {
        existing(true);
        fixture.mutate(node -> node.volumeReclamationPlan.put("volume", "unknown-host-volume"));
        assertThatThrownBy(() -> service.retireOwnedDependency(connection, "graph", "inbox-service", "inbox", fixture.resources(), checkpoint))
            .hasMessageContaining("RECLAMATION_EVIDENCE_INVALID");
        noMutations();
    }

    @Test void aReceiptFromAnotherCreationPathCannotClaimThisGraphResource() {
        existing(true);
        var foreign = new ManagedResourceOwnershipService.Scope("other-customer", "other-tenant", "other-deployment", "other-target");
        fixture.ledger.create(foreign, "coolify", "APPLICATION", "other-parent", "other-runtime", () -> "app",
            value -> new ManagedResourceOwnershipService.Identity(value, config.baseUrl()));
        assertThatThrownBy(() -> service.disableOwnedDependency(connection, "graph", "inbox-service", "inbox", "app", checkpoint))
            .hasMessageContaining("OWNERSHIP_CONFLICT");
        noMutations();
    }

    @Test void aDifferentStorageUuidCannotShareTheSameOwnedHostVolumeName() {
        existing(true);
        var foreign = new ManagedResourceOwnershipService.Scope("other-customer", "other-tenant", "other-deployment", "other-target");
        fixture.ledger.create(foreign, "coolify", OwnedDependencyOwnership.DB_STORAGE, "other-parent", "other-state", () ->
            fixture.graphs.json(new OwnedDependencyOwnership.Storage("other-db", List.of(
                new OwnedDependencyOwnership.Volume("other-volume", "app-inbox-state", "/data")))),
            value -> new ManagedResourceOwnershipService.Identity(value, config.baseUrl()));
        assertThatThrownBy(() -> service.retireOwnedDependency(connection, "graph", "inbox-service", "inbox", fixture.resources(), checkpoint))
            .hasMessageContaining("OWNERSHIP_CONFLICT");
        noMutations();
    }

    @Test void callerSuppliedResourceMetadataCannotReplaceTheDurableGraph() {
        existing(true);
        assertThatThrownBy(() -> service.reconcileOwnedDependency(connection, "graph", "inbox-service", inbox(), empty(false, false),
            ignored -> List.of(), checkpoint)).hasMessageContaining("OWNERSHIP_CONFLICT");
        noMutations();
    }

    @ParameterizedTest
    @ValueSource(ints = {503, 429})
    @SuppressWarnings("unchecked")
    void storagePostNeverRetriesUnknownProviderOutcomes(int status) throws Exception {
        var http = mock(java.net.http.HttpClient.class);
        var response = mock(java.net.http.HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn("{}");
        when(http.send(any(java.net.http.HttpRequest.class), any(java.net.http.HttpResponse.BodyHandler.class))).thenReturn(response);
        org.springframework.test.util.ReflectionTestUtils.setField(fixture.authority, "http", http);
        doCallRealMethod().when(fixture.authority).postVolume(any(), anyString(), anyString(), anyString());
        assertThatThrownBy(() -> fixture.authority.postVolume(connection, "app", "new-state", "/data"))
            .hasMessageContaining("VOLUME_OWNERSHIP_CONFLICT");
        var request = org.mockito.ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
        verify(http, times(1)).send(request.capture(), any(java.net.http.HttpResponse.BodyHandler.class));
        assertThat(request.getValue().method()).isEqualTo("POST");
        assertThat(request.getValue().uri().toString()).isEqualTo(config.baseUrl() + "/api/v1/applications/app/storages");
    }

    @Test void reclamationVerificationCannotOmitThePersistedGraphInventory() {
        existing(true);
        assertThatThrownBy(() -> service.verifyOwnedDependenciesReclaimed(connection, "graph", List.of()))
            .hasMessageContaining("OWNERSHIP_CONFLICT");
        noMutations();
    }

    @Test void graphReclaimedFlagsCannotSkipReceiptAuthorizedPhysicalReclamation() {
        existing(true);
        fixture.mutate(node -> {
            node.volumeReclamationPlan.put("volume", "app-inbox-state");
            node.reclaimedVolumeUuids.add("volume");
        });
        service.retireOwnedDependency(connection, "graph", "inbox-service", "inbox", fixture.resources(), checkpoint);
        verify(host).reclaimAndVerify(any(), eq("server"), eq("graph:inbox:application"), anyList());
        assertThat(fixture.jdbc.queryForList("SELECT state FROM platform_managed_resource_ownership", String.class)).containsOnly("DELETED");
    }
}
