package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.*;
import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyFixtures;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.retirement.CoolifyHostVolumeReclamationService;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OwnedManagedDependencyProvisioningTest {
    final ObjectMapper mapper = new ObjectMapper();
    final CoolifyApiClient api = mock(CoolifyApiClient.class);
    final CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
    final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    final PlatformProductProvisioningProperties properties = mock(PlatformProductProvisioningProperties.class);
    final PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint checkpoint = mock(PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint.class);
    final CoolifyHostVolumeReclamationService volumeReclamation = mock(CoolifyHostVolumeReclamationService.class);
    final CoolifyTargetProfileConfig config = new CoolifyTargetProfileConfig("https://coolify.example.test", "project", "staging",
        "environment", "server", "destination", "example.test", null, 1, 10, false, true, "8080", "/actuator/health", "8080");
    final CoolifyConnection connection = new CoolifyConnection(config.baseUrl(), "private", config);
    PlatformManagedProductProvisioningService service;
    OwnedDependencyOwnershipFixture ownership;
    @BeforeEach void setup() {
        when(properties.requestTimeout()).thenReturn(Duration.ofSeconds(1)); when(properties.pollInterval()).thenReturn(Duration.ofMillis(1));
        when(volumeReclamation.reclaimAndVerify(any(), anyString(), anyString(), anyList())).thenAnswer(call -> {
            @SuppressWarnings("unchecked")
            List<CoolifyHostVolumeReclamationService.Volume> volumes = call.getArgument(3, List.class);
            return new CoolifyHostVolumeReclamationService.Receipt("receipt", java.time.Instant.now(),
                volumes.stream().map(CoolifyHostVolumeReclamationService.Volume::uuid)
                    .collect(java.util.stream.Collectors.toSet()));
        });
        when(volumeReclamation.verifyContainersAbsent(any(), anyString(), anyString(), anyString(), anyString()))
            .thenAnswer(call -> new CoolifyHostVolumeReclamationService.ContainerReceipt(
                "container-receipt", java.time.Instant.now()));
        ownership = new OwnedDependencyOwnershipFixture(api, targets, resolver, mapper, connection);
        doAnswer(call -> { ownership.checkpoint.creating(call.getArgument(0)); return null; }).when(checkpoint).creating(anyString());
        doAnswer(call -> { ownership.checkpoint.created(call.getArgument(0), call.getArgument(1)); return null; }).when(checkpoint).created(anyString(), anyString());
        doAnswer(call -> { ownership.checkpoint.volume(call.getArgument(0), call.getArgument(1)); return null; }).when(checkpoint).volume(anyString(), anyString());
        doAnswer(call -> { ownership.checkpoint.volumeReclamationPlanned(call.getArgument(0)); return null; }).when(checkpoint).volumeReclamationPlanned(anyMap());
        doAnswer(call -> { ownership.checkpoint.volumesReclaimed(call.getArgument(0)); return null; }).when(checkpoint).volumesReclaimed(anySet());
        service = service(new CustomerSelfServiceProperties(Map.of(
                TargetIntent.CUSTOMER_STAGING, new CustomerSelfServiceProperties.TrustedServer("server", "46.224.145.148", "host.docker.internal"),
                TargetIntent.CUSTOMER_PRODUCTION, new CustomerSelfServiceProperties.TrustedServer("production-server", "46.225.162.106", "host.docker.internal")), 60));
    }
    private PlatformManagedProductProvisioningService service(CustomerSelfServiceProperties placement) {
        var service = new PlatformManagedProductProvisioningService(mock(PlatformProvisioningProperties.class), properties,
            mock(PlatformDeliveryProperties.class), mock(RailwayGraphqlClient.class), resolver, api, targets,
            mock(PlatformSecretService.class), mock(PlatformManagedProductServiceRepository.class), mock(ShopifyStoreConnectionRepository.class),
            mock(PlatformManagedProductServiceService.class), mock(PlatformAuditService.class), mapper, placement,
            volumeReclamation);
        service.setOwnedDependencyOwnership(ownership.authority);
        return service;
    }
    ObjectNode raw(String name, String marker) {
        return ownership.raw(name.equals("inbox-service") ? "inbox-app" : name.endsWith("-db") ? "db" : "app", name, marker);
    }
    PlatformManagedProductProvisioningService.OwnedDependencyResources empty(boolean intent) {
        return new PlatformManagedProductProvisioningService.OwnedDependencyResources(null, null, intent, false, Map.of());
    }
    @Test void misleadingStagingProfileOnProductionServerIsRejected() {
        var target = new DeploymentTargetProfileEntity(); target.setId("dtp-coolify-prod-staging"); target.setActive(true);
        target.setEnvironmentName("staging"); target.setProviderType(DeploymentProviderType.COOLIFY);
        when(targets.findById(target.getId())).thenReturn(Optional.of(target)); when(resolver.requireConnection(target)).thenReturn(connection);
        when(api.getServer(connection, "server")).thenReturn(mapper.createObjectNode().put("uuid", "server").put("ip", "46.225.162.106"));
        assertThatThrownBy(() -> service.ownedDependencyConnection(target.getId(), "staging"))
            .hasMessageContaining("SERVER_PLACEMENT_CONFLICT");
        verify(api, never()).createDockerImageApplication(any(), any());
    }
    @Test void trustedLocalCoolifyAliasIsAcceptedForExactStagingPlacement() {
        var target = target("customer-staging", "staging");
        when(resolver.requireConnection(target)).thenReturn(connection);
        when(api.getServer(connection, "server")).thenReturn(mapper.createObjectNode().put("uuid", "server").put("ip", "host.docker.internal"));
        when(api.resolveDestinationPlacement(connection, "server", "destination"))
            .thenReturn(Optional.of(new CoolifyDestinationPlacement("destination", "server", "test", mapper.createObjectNode())));

        assertThat(service.ownedDependencyConnection(target.getId(), "staging")).isEqualTo(connection);
    }
    @Test void aliasCannotAuthorizeWrongServerOrProductionPlacement() {
        var staging = target("customer-staging", "staging");
        when(resolver.requireConnection(staging)).thenReturn(connection);
        when(api.getServer(connection, "server")).thenReturn(mapper.createObjectNode().put("uuid", "production-server").put("ip", "host.docker.internal"));
        assertThatThrownBy(() -> service.ownedDependencyConnection(staging.getId(), "staging"))
            .hasMessageContaining("SERVER_PLACEMENT_CONFLICT");
        var production = target("customer-production", "production");
        var wrong = new CoolifyTargetProfileConfig(config.baseUrl(), "project", "production", "environment", "server", "destination",
            "example.test", null, 1, 10, false, true, "8080", "/actuator/health", "8080");
        when(resolver.requireConnection(production)).thenReturn(new CoolifyConnection(wrong.baseUrl(), "private", wrong));
        assertThatThrownBy(() -> service.ownedDependencyConnection(production.getId(), "production"))
            .hasMessageContaining("SERVER_PLACEMENT_CONFLICT");
    }
    @Test void wrongReportedAddressIsDeniedEvenWhenPhysicalAddressIsKnown() {
        var target = target("customer-staging", "staging");
        when(resolver.requireConnection(target)).thenReturn(connection);
        when(api.getServer(connection, "server")).thenReturn(mapper.createObjectNode().put("uuid", "server").put("ip", "46.224.145.148"));
        assertThatThrownBy(() -> service.ownedDependencyConnection(target.getId(), "staging"))
            .hasMessageContaining("SERVER_PLACEMENT_CONFLICT");
    }
    @Test void physicalAddressesMustBePresentAndDifferentBetweenCustomerEnvironments() {
        var target = target("customer-staging", "staging");
        when(resolver.requireConnection(target)).thenReturn(connection);
        for (String address : List.of("", "46.225.162.106")) {
            var invalid = service(new CustomerSelfServiceProperties(Map.of(
                TargetIntent.CUSTOMER_STAGING, new CustomerSelfServiceProperties.TrustedServer("server", address, "host.docker.internal"),
                TargetIntent.CUSTOMER_PRODUCTION, new CustomerSelfServiceProperties.TrustedServer("production-server", "46.225.162.106", "host.docker.internal")), 60));
            assertThatThrownBy(() -> invalid.ownedDependencyConnection(target.getId(), "staging"))
                .hasMessageContaining("SERVER_PLACEMENT_CONFLICT");
        }
        verify(api, never()).getServer(any(), anyString());
    }
    @Test void bothCustomerEnvironmentsRequireProviderReportedAddresses() {
        var target = target("customer-staging", "staging");
        when(resolver.requireConnection(target)).thenReturn(connection);
        var invalid = service(new CustomerSelfServiceProperties(Map.of(
            TargetIntent.CUSTOMER_STAGING,
            new CustomerSelfServiceProperties.TrustedServer("server", "46.224.145.148", "host.docker.internal"),
            TargetIntent.CUSTOMER_PRODUCTION,
            new CustomerSelfServiceProperties.TrustedServer("production-server", "46.225.162.106", "")), 60));

        assertThatThrownBy(() -> invalid.ownedDependencyConnection(target.getId(), "staging"))
            .hasMessageContaining("SERVER_PLACEMENT_CONFLICT");
        verify(api, never()).getServer(any(), anyString());
    }
    private DeploymentTargetProfileEntity target(String id, String environment) {
        var target = new DeploymentTargetProfileEntity(); target.setId(id); target.setActive(true);
        target.setEnvironmentName(environment); target.setProviderType(DeploymentProviderType.COOLIFY);
        when(targets.findById(id)).thenReturn(Optional.of(target)); return target;
    }

    @Test void operatorRemovalUpdatesOnlyExistingCredentialFieldAndNeverCreatesResources() {
        var spec = inbox(); var resources = inboxResources();
        stubExistingInbox(spec);
        String original = "one|One|" + "a".repeat(43) + "|binding;two|Two|" + "b".repeat(43) + "|binding";
        String remaining = "two|Two|" + "b".repeat(43) + "|binding";
        when(api.requireRuntimeEnvironmentValue(connection, "inbox-app", "BASIC_INBOX_OPERATORS"))
            .thenReturn(original, original, remaining);
        when(api.start(connection, "inbox-app", true, true)).thenReturn(new CoolifyActionResponse("ok", "build", mapper.createObjectNode()));
        when(api.getDeployment(connection, "build")).thenReturn(Optional.of(new CoolifyDeploymentSummary("build", "inbox-service", "inbox-app",
            "finished", null, null, null, null, null, mapper.createObjectNode())));

        var removal = service.planOwnedDependencyOperatorRemoval(connection, "graph", "inbox-service", spec, resources, "binding", "one");
        service.applyOwnedDependencyOperatorRemoval(connection, "graph", "inbox-service", spec, resources, removal, checkpoint);

        assertThat(removal.remainingOperators()).isEqualTo(1); assertThat(removal.toString()).doesNotContain(original, remaining);
        var changed = ArgumentCaptor.forClass(List.class);
        verify(api).updateEnvironmentVariables(eq(connection), eq("inbox-app"), changed.capture());
        assertThat(changed.getValue()).hasSize(1);
        assertThat(((CoolifyEnvVar) changed.getValue().getFirst()).key()).isEqualTo("BASIC_INBOX_OPERATORS");
        assertThat(((CoolifyEnvVar) changed.getValue().getFirst()).value()).isEqualTo(remaining);
        verify(api, never()).createDockerImageApplication(any(), any()); verify(api, never()).createPostgresDatabase(any(), any());
        verify(api, never()).reconcilePersistentVolumeStorage(any(), anyString(), anyString(), anyString());
        verify(api, never()).startDatabase(any(), anyString());
        verify(checkpoint, never()).creating(anyString()); verify(checkpoint, never()).created(anyString(), anyString());
    }
    @Test void removalRejectsAReplacementCredentialRatherThanApplyingIt() {
        var spec = inbox(); stubExistingInbox(spec);
        String original = "one|One|" + "a".repeat(43) + "|binding;two|Two|" + "b".repeat(43) + "|binding";
        var replacement = new PlatformManagedProductProvisioningService.OwnedDependencyOperatorRestriction("one", "BASIC_INBOX_OPERATORS",
            original, "three|Three|" + "c".repeat(43) + "|binding", 1);
        assertThatThrownBy(() -> service.applyOwnedDependencyOperatorRemoval(connection, "graph", "inbox-service", spec,
            inboxResources(), replacement, checkpoint)).hasMessageContaining("REMOVAL_ONLY_REQUIRED");
        verify(api, never()).updateEnvironmentVariables(any(), anyString(), anyList());
        verify(api, never()).start(any(), anyString(), anyBoolean(), anyBoolean());
    }
    @Test void lastOperatorRemovalStopsOnlyTheOwnedInboxAndRequiresStoppedReadback() {
        var spec = inbox(); stubExistingInbox(spec);
        var running = api.getApplication(connection, "inbox-app").orElseThrow();
        var stopped = new java.util.concurrent.atomic.AtomicBoolean(false);
        when(api.getApplication(connection, "inbox-app")).thenAnswer(call -> Optional.of(new CoolifyApplicationSummary(
            running.uuid(), running.name(), running.fqdn(), stopped.get() ? "exited" : "running:healthy",
            running.imageRepository(), running.imageTag(), running.raw())));
        when(api.stop(connection, "inbox-app", false)).thenAnswer(call -> {
            stopped.set(true); return new CoolifyActionResponse("stopped", null, mapper.createObjectNode());
        });
        when(api.requireRuntimeEnvironmentValue(connection, "inbox-app", "BASIC_INBOX_OPERATORS"))
            .thenReturn("one|One|" + "a".repeat(43) + "|binding");
        var removal = service.planOwnedDependencyOperatorRemoval(connection, "graph", "inbox-service", spec,
            inboxResources(), "binding", "one");

        service.applyOwnedDependencyOperatorRemoval(connection, "graph", "inbox-service", spec, inboxResources(), removal, checkpoint);

        verify(api).stop(connection, "inbox-app", false);
        verify(api, never()).updateEnvironmentVariables(any(), anyString(), anyList());
        verify(api, never()).start(any(), anyString(), anyBoolean(), anyBoolean());
        verify(api, never()).createDockerImageApplication(any(), any()); verify(api, never()).createPostgresDatabase(any(), any());
    }
    @Test void stopRequestWithoutStoppedReadbackDoesNotProveRevocation() {
        var spec = inbox(); stubExistingInbox(spec);
        when(api.requireRuntimeEnvironmentValue(connection, "inbox-app", "BASIC_INBOX_OPERATORS"))
            .thenReturn("one|One|" + "a".repeat(43) + "|binding");
        var removal = service.planOwnedDependencyOperatorRemoval(connection, "graph", "inbox-service", spec,
            inboxResources(), "binding", "one");
        assertThatThrownBy(() -> service.applyOwnedDependencyOperatorRemoval(connection, "graph", "inbox-service", spec,
            inboxResources(), removal, checkpoint)).hasMessageContaining("DEPENDENCY_DISABLE_UNCONFIRMED");
    }
    @Test void alreadyStoppedOwnedInboxMakesDisableRetryIdempotent() {
        var spec = inbox(); stubExistingInbox(spec);
        var running = api.getApplication(connection, "inbox-app").orElseThrow();
        when(api.getApplication(connection, "inbox-app")).thenReturn(Optional.of(new CoolifyApplicationSummary(
            running.uuid(), running.name(), running.fqdn(), "stopped", running.imageRepository(),
            running.imageTag(), running.raw())));

        service.disableOwnedDependency(connection, "graph", "inbox-service", "inbox", "inbox-app", checkpoint);

        verify(api, never()).stop(any(), anyString(), anyBoolean());
        verify(checkpoint, never()).guard();
    }
    @Test void missingExistingInboxDoesNotFallBackToResourceCreation() {
        stubExistingInbox(inbox());
        when(api.getApplication(connection, "inbox-app")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.planOwnedDependencyOperatorRemoval(connection, "graph", "inbox-service", inbox(),
            inboxResources(), "binding", "one")).hasMessageContaining("OWNERSHIP_CONFLICT");
        verify(api, never()).createDockerImageApplication(any(), any()); verify(api, never()).createPostgresDatabase(any(), any());
    }
    private com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Service inbox() {
        return ManagedDependencyFixtures.topology().services().stream().filter(value -> value.slot().equals("inbox")).findFirst().orElseThrow();
    }
    private PlatformManagedProductProvisioningService.OwnedDependencyResources inboxResources() {
        return new PlatformManagedProductProvisioningService.OwnedDependencyResources("inbox-app", null, true, false, Map.of("/app/data", "volume"));
    }
    private void stubExistingInbox(com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Service spec) {
        ownership.graph(spec, "inbox-service", inboxResources());
        ownership.applications.put("inbox-app", new CoolifyApplicationSummary("inbox-app", "inbox-service",
            "https://inbox-service.example.test", "running:healthy", "registry.example.test/dependency", "sha256-" + "a".repeat(64),
            raw("inbox-service", "ManagedDependency:graph:inbox")));
        ownership.appStorages.put("inbox-app", List.of(ownership.storage("volume",
            "inbox-app-inbox-service-" + Integer.toHexString("/app/data".hashCode()), "/app/data")));
        ownership.receipts();
    }
    @Test void immutableImageAndCreationIntentArePassedThroughExistingProvider() {
        var spec = ManagedDependencyFixtures.http("simulator", false);
        ownership.graph(spec, "dep-service", empty(false));
        var app = new CoolifyApplicationSummary("app", "dep-service", "https://dep-service.example.test", "running:healthy",
            "registry.example.test/dependency", "sha256-" + "a".repeat(64), raw("dep-service", "ManagedDependency:graph:simulator"));
        when(api.createDockerImageApplication(eq(connection), any())).thenReturn("app");
        ownership.applications.put("app", app);
        when(api.start(connection, "app", true, true)).thenReturn(new CoolifyActionResponse("ok", "build", mapper.createObjectNode()));
        when(api.getDeployment(connection, "build")).thenReturn(Optional.of(new CoolifyDeploymentSummary("build", "dep-service", "app", "finished", null, null, null, null, null, mapper.createObjectNode())));
        var result = service.reconcileOwnedDependency(connection, "graph", "dep-service", spec, empty(false), unused -> List.of(), checkpoint);
        assertThat(result.applicationUuid()).isEqualTo("app");
        var request = ArgumentCaptor.forClass(CoolifyCreateDockerImageApplicationRequest.class);
        verify(api).createDockerImageApplication(eq(connection), request.capture());
        assertThat(request.getValue().imageRepository()).isEqualTo("registry.example.test/dependency");
        assertThat(request.getValue().imageTag()).isEqualTo("sha256-" + "a".repeat(64));
        var order = inOrder(checkpoint, api); order.verify(checkpoint).guard(); order.verify(checkpoint).creating("APPLICATION");
        order.verify(api).createDockerImageApplication(eq(connection), any()); order.verify(checkpoint).created("APPLICATION", "app");
        verify(api, never()).createPublicApplication(any(), any());
    }
    @Test void uncertainCreateNeverAllocatesAnotherApplication() {
        ownership.graph(ManagedDependencyFixtures.http("simulator", false), "dep-service", empty(true));
        when(api.listApplications(connection)).thenReturn(List.of());
        assertThatThrownBy(() -> service.reconcileOwnedDependency(connection, "graph", "dep-service",
            ManagedDependencyFixtures.http("simulator", false), empty(true), unused -> List.of(), checkpoint))
            .hasMessageContaining("CREATE_UNCERTAIN");
        verify(api, never()).createDockerImageApplication(any(), any());
    }
    @Test void foreignResourceCannotBeReconfiguredOrDeleted() {
        var spec = ManagedDependencyFixtures.http("simulator", false);
        var app = new CoolifyApplicationSummary("foreign", "dep-service", "https://dep-service.example.test", "running:healthy",
            spec.image(), null, raw("dep-service", "ManagedDependency:OTHER:simulator"));
        when(api.getApplication(connection, "foreign")).thenReturn(Optional.of(app));
        var resource = new PlatformManagedProductProvisioningService.OwnedDependencyResources("foreign", null, true, false, Map.of());
        ownership.graph(spec, "dep-service", resource);
        ownership.applications.put("foreign", app);
        ownership.receipts();
        assertThatThrownBy(() -> service.reconcileOwnedDependency(connection, "graph", "dep-service", spec, resource, unused -> List.of(), checkpoint))
            .hasMessageContaining("OWNERSHIP_CONFLICT");
        assertThatThrownBy(() -> service.retireOwnedDependency(connection, "graph", "dep-service", "simulator", resource, checkpoint))
            .hasMessageContaining("OWNERSHIP_CONFLICT");
        verify(api, never()).updateEnvironmentVariables(any(), anyString(), anyList()); verify(api, never()).delete(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }
    @Test void retirementDeletesAttachedOwnedVolumesWithApplicationAndCheckpointsIntentFirst() {
        var app = new CoolifyApplicationSummary("app", "dep-service", "https://dep-service.example.test", "running:healthy",
            "registry.example.test/dependency", "sha256-" + "a".repeat(64), raw("dep-service", "ManagedDependency:graph:simulator"));
        var storage = ownership.storage("volume", "app-dep-service-" + Integer.toHexString("/app/data".hashCode()), "/app/data");
        var resource = new PlatformManagedProductProvisioningService.OwnedDependencyResources(
            "app", null, true, false, Map.of("/app/data", "volume"));
        ownership.graph(ManagedDependencyFixtures.http("simulator", false), "dep-service", resource);
        ownership.applications.put("app", app);
        ownership.appStorages.put("app", List.of(storage));
        ownership.receipts();
        doAnswer(call -> {
            assertThat(ownership.jdbc.queryForList("SELECT state FROM platform_managed_resource_ownership", String.class))
                .containsOnly("DELETE_PENDING");
            ownership.applications.remove("app");
            return new CoolifyActionResponse("deleted", null, mapper.createObjectNode());
        }).when(api).delete(connection, "app", true, true, false, false);

        service.retireOwnedDependency(connection, "graph", "dep-service", "simulator", resource, checkpoint);

        var order = inOrder(checkpoint, api);
        order.verify(checkpoint).volumeReclamationPlanned(Map.of("volume", storage.name()));
        order.verify(api).delete(connection, "app", true, true, false, false);
        order.verify(checkpoint).volumesReclaimed(Set.of("volume"));
        verify(api, never()).deleteApplicationStorage(any(), anyString(), anyString());
        verify(volumeReclamation).verifyContainersAbsent(TargetIntent.CUSTOMER_STAGING, "server",
            "graph:simulator:application", "application", "app");
    }
    @Test void retirementRetryRequiresDurableVolumeReceiptAfterApplicationDisappears() {
        var missing = new PlatformManagedProductProvisioningService.OwnedDependencyResources(
            "app", null, true, false, Map.of("/app/data", "volume"));
        ownership.graph(ManagedDependencyFixtures.http("simulator", false), "dep-service", missing);
        assertThatThrownBy(() -> service.retireOwnedDependency(connection, "graph", "dep-service", "simulator",
            missing, checkpoint)).hasMessageContaining("OWNERSHIP_CONFLICT");

        var planned = new PlatformManagedProductProvisioningService.OwnedDependencyResources(
            "app", null, true, false, Map.of("/app/data", "volume"),
            Map.of("volume", "app-dep-service-" + Integer.toHexString("/app/data".hashCode())), Set.of());
        ownership.graph(ManagedDependencyFixtures.http("simulator", false), "dep-service", planned);
        ownership.appStorages.put("app", List.of(ownership.storage("volume", planned.volumeReclamationPlan().get("volume"), "/app/data")));
        ownership.receipts();
        ownership.authority.beginVolumeDeletion(ownership.context(), "app", planned.volumes(), null);
        ownership.authority.beginDeletion(ownership.context(), OwnedDependencyOwnership.APPLICATION, "app");
        service.retireOwnedDependency(connection, "graph", "dep-service", "simulator", planned, checkpoint);
        verify(checkpoint).volumesReclaimed(Set.of("volume"));
        verify(api, never()).delete(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }
    @Test void retirementFreezesAndPhysicallyReclaimsApplicationAndDatabaseVolumes() {
        var app = new CoolifyApplicationSummary("app", "dep-service", "https://dep-service.example.test", "running:healthy",
            "registry.example.test/dependency", "sha256-" + "a".repeat(64), raw("dep-service", "ManagedDependency:graph:receiver"));
        var dbRaw = raw("dep-service-db", "ManagedDependency:graph:receiver");
        var database = new CoolifyDatabaseSummary("db", "dep-service-db", "running:healthy", "postgresql",
            "dependency", "dependency", dbRaw);
        var appStorage = ownership.storage("app-volume", "app-dep-service-" + Integer.toHexString("/app/data".hashCode()), "/app/data");
        var databaseStorage = ownership.storage("db-volume", "dep-service-db-data", "/var/lib/postgresql/data");
        var resource = new PlatformManagedProductProvisioningService.OwnedDependencyResources(
            "app", "db", true, true, Map.of("/app/data", "app-volume"));
        ownership.graph(ManagedDependencyFixtures.http("receiver", true), "dep-service", resource);
        ownership.applications.put("app", app);
        ownership.databases.put("db", database);
        ownership.appStorages.put("app", List.of(appStorage));
        ownership.dbStorages.put("db", List.of(databaseStorage));
        ownership.receipts();

        service.retireOwnedDependency(connection, "graph", "dep-service", "receiver", resource, checkpoint);

        verify(checkpoint).volumeReclamationPlanned(Map.of(
            "app-volume", appStorage.name(), "db-volume", databaseStorage.name()));
        verify(checkpoint).volumesReclaimed(Set.of("app-volume"));
        verify(checkpoint).volumesReclaimed(Set.of("app-volume", "db-volume"));
        verify(api).delete(connection, "app", true, true, false, false);
        verify(api).deleteDatabase(connection, "db", true, true, true, false);
        verify(volumeReclamation).reclaimAndVerify(eq(TargetIntent.CUSTOMER_STAGING), eq("server"),
            contains(":application"), anyList());
        verify(volumeReclamation).reclaimAndVerify(eq(TargetIntent.CUSTOMER_STAGING), eq("server"),
            contains(":database"), anyList());
        verify(volumeReclamation).verifyContainersAbsent(TargetIntent.CUSTOMER_STAGING, "server",
            "graph:receiver:application", "application", "app");
        verify(volumeReclamation).verifyContainersAbsent(TargetIntent.CUSTOMER_STAGING, "server",
            "graph:receiver:database", "database", "db");
    }
    @Test void databaseAddressMustComeFromActualProviderEvidence() {
        ownership.graph(ManagedDependencyFixtures.http("receiver", true), "dep-service",
            new PlatformManagedProductProvisioningService.OwnedDependencyResources(null, "db", false, true, Map.of()));
        ownership.databases.put("db", new CoolifyDatabaseSummary("db", "dep-service-db", "running:healthy", "postgresql", "dependency", "dependency",
            raw("dep-service-db", "ManagedDependency:graph:receiver")));
        ownership.dbStorages.put("db", List.of(ownership.storage("db-volume", "db-data", "/var/lib/postgresql/data")));
        ownership.receipts();
        assertThatThrownBy(() -> service.ownedDependencyDatabaseJdbc(connection, "db")).hasMessageContaining("ADDRESS_UNVERIFIED");
    }

    @Test void databaseCreationUsesReviewedCapacityAndRejectsProviderDrift() {
        var spec = ManagedDependencyFixtures.http("receiver", true);
        ownership.graph(spec, "dep-service", empty(false));
        ObjectNode databaseRaw = raw("dep-service-db", "ManagedDependency:graph:receiver")
            .put("image", spec.database().image())
            .put("is_public", false)
            .put("limits_memory", "256M")
            .put("limits_cpus", "0.250");
        var database = new CoolifyDatabaseSummary("db", "dep-service-db", "running:healthy", "postgresql",
            "dependency", "dependency", databaseRaw);
        when(api.createPostgresDatabase(eq(connection), any())).thenReturn("db");
        ownership.databases.put("db", database);
        ownership.dbStorages.put("db", List.of(ownership.storage("db-volume", "db-data", "/var/lib/postgresql/data")));

        assertThatThrownBy(() -> service.reconcileOwnedDependency(connection, "graph", "dep-service", spec,
            empty(false), ignored -> List.of(new CoolifyEnvVar("__DATABASE_PASSWORD", "private", false, true, false, true)),
            checkpoint)).hasMessageContaining("DEPENDENCY_DATABASE_SPEC_DRIFT");

        var request = ArgumentCaptor.forClass(CoolifyCreatePostgresDatabaseRequest.class);
        verify(api).createPostgresDatabase(eq(connection), request.capture());
        assertThat(request.getValue().runtimeSettings().limitsMemory()).isEqualTo("512M");
        assertThat(request.getValue().runtimeSettings().limitsCpus()).isEqualTo("0.250");
        verify(api, never()).startDatabase(any(), anyString());
    }
}
