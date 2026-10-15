package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ManagedDependencyLifecycleServiceTest {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final ManagedDependencyGraphRepository repository = mock(ManagedDependencyGraphRepository.class);
    final ManagedDependencySpecRepository specRepository = mock(ManagedDependencySpecRepository.class);
    final DeploymentRepository deployments = mock(DeploymentRepository.class);
    final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    final DeploymentAccessService access = mock(DeploymentAccessService.class);
    final PlatformManagedProductProvisioningService provider = mock(PlatformManagedProductProvisioningService.class);
    final PlatformManagedProductServiceService products = mock(PlatformManagedProductServiceService.class);
    final PlatformManagedProductServiceRepository productRepository = mock(PlatformManagedProductServiceRepository.class);
    final PlatformSecretService secrets = mock(PlatformSecretService.class);
    final ManagedDependencyPrerequisites gate = mock(ManagedDependencyPrerequisites.class);
    @SuppressWarnings("unchecked") final ObjectProvider<ManagedDependencyPrerequisites> gates = mock(ObjectProvider.class);
    final Map<String, ManagedDependencyGraphEntity> graphs = new HashMap<>();
    final Map<String, String> keys = new HashMap<>();
    ManagedDependencyGraphStore store;
    ManagedDependencyLifecycleService lifecycle;
    ManagedDependencyOperatorService operators;
    ManagedDependencySpecValidator validator;

    @BeforeEach void setup() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        store = new ManagedDependencyGraphStore(repository, mapper, manager); validator = new ManagedDependencySpecValidator(mapper);
        when(repository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(graphs.get(i.getArgument(0))));
        when(repository.lockById(anyString())).thenAnswer(i -> Optional.ofNullable(graphs.get(i.getArgument(0))));
        when(repository.findByDeploymentIdAndTargetProfileId(anyString(), anyString())).thenAnswer(i -> graphs.values().stream()
            .filter(g -> g.getDeploymentId().equals(i.getArgument(0)) && g.getTargetProfileId().equals(i.getArgument(1))).findFirst());
        when(repository.saveAndFlush(any())).thenAnswer(i -> { var g = (ManagedDependencyGraphEntity)i.getArgument(0); graphs.put(g.getId(), g); return g; });
        var deployment = new DeploymentEntity(); deployment.setId("dep-1"); deployment.setCustomerId("customer-1"); deployment.setTenantId("tenant-1");
        when(deployments.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(deployments.findByIdForUpdate("dep-1")).thenReturn(Optional.of(deployment));
        var version = new DeploymentVersionEntity(); version.setId("v1"); version.setDeploymentId("dep-1");
        when(versions.findById("v1")).thenReturn(Optional.of(version));
        var nextVersion = new DeploymentVersionEntity(); nextVersion.setId("v2"); nextVersion.setDeploymentId("dep-1");
        when(versions.findById("v2")).thenReturn(Optional.of(nextVersion));
        for (String target : List.of("staging", "production")) {
            var profile = new DeploymentTargetProfileEntity(); profile.setId(target); profile.setActive(true); profile.setEnvironmentName(target);
            when(targets.findById(target)).thenReturn(Optional.of(profile));
        }
        var reviewed = new ManagedDependencySpecEntity(); reviewed.setId("spec1"); reviewed.setActive(true);
        reviewed.setReviewedAt(Instant.now()); reviewed.setReviewedBy("reviewer");
        reviewed.setSpecJson(store.json(ManagedDependencyFixtures.topology())); reviewed.setFingerprint(validator.fingerprint(ManagedDependencyFixtures.topology()));
        when(specRepository.findById("spec1")).thenReturn(Optional.of(reviewed));
        when(gates.orderedStream()).thenAnswer(ignored -> java.util.stream.Stream.of(gate));
        when(gate.reserve(any(), any(), any())).thenAnswer(i -> new ManagedDependencyPrerequisites.Admission(
            "reservation-" + ((ManagedDependencyPrerequisites.Scope)i.getArgument(0)).graphId(), "c".repeat(64),
            i.getArgument(2) == ManagedDependencyPrerequisites.ProvisioningPhase.RUNTIME_BOUND
                ? Map.of("inbox", "https://connector.example.test/inbox/callback") : Map.of(),
            List.of("https://app.example.test"), Map.of(),
            Map.of("conversation-handover-callback-signing", "TARGET_CALLBACK_SECRET")));
        when(secrets.isSecretPresent(anyString())).thenAnswer(i -> keys.containsKey(i.getArgument(0)));
        when(secrets.resolveSecret(anyString())).thenAnswer(i -> keys.get(i.getArgument(0)));
        when(secrets.upsertManagedSecret(anyString(), anyString(), anyMap())).thenAnswer(i -> {
            String name = i.getArgument(0); assertThat(name).matches("[A-Z0-9_]+"); assertThat(name.length()).isLessThan(128);
            assertThat(((Map<?, ?>)i.getArgument(2)).containsKey("deploymentId")).isTrue(); keys.put(name, i.getArgument(1)); return null;
        });
        when(secrets.clearManagedSecret(anyString(), anyMap())).thenAnswer(i -> { keys.remove(i.getArgument(0)); return null; });
        keys.put("TARGET_CALLBACK_SECRET", "target-callback-secret-that-is-at-least-32-chars");
        when(products.requireService(anyString())).thenReturn(new PlatformManagedProductServiceEntity());
        var config = new CoolifyTargetProfileConfig("https://coolify.example.test", "project", "staging", "environment", "server", "destination",
            "example.test", null, 1, 10, false, true, "8080", "/actuator/health", "8080");
        var connection = new CoolifyConnection(config.baseUrl(), "token", config);
        when(provider.ownedDependencyConnection(anyString(), anyString())).thenReturn(connection);
        when(provider.reconcileOwnedDependency(any(), anyString(), anyString(), any(), any(), any(), any())).thenAnswer(i -> {
            String graphId = i.getArgument(1); String serviceRef = i.getArgument(2);
            var spec = (ManagedDependencySpec.Service)i.getArgument(3);
            var owned = (PlatformManagedProductProvisioningService.OwnedDependencyResources)i.getArgument(4);
            var checkpoint = (PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint)i.getArgument(6);
            checkpoint.guard();
            if (owned.applicationUuid() == null) { checkpoint.creating("APPLICATION"); checkpoint.created("APPLICATION", "app-" + serviceRef); }
            if (spec.database() != null && owned.databaseUuid() == null) { checkpoint.creating("DATABASE"); checkpoint.created("DATABASE", "db-" + serviceRef); }
            @SuppressWarnings("unchecked") var environment = (Function<String, List<CoolifyEnvVar>>)i.getArgument(5);
            if ("inbox".equals(spec.slot())) {
                var env = environment.apply(null);
                assertThat(env).anySatisfy(v -> { assertThat(v.key()).isEqualTo("BASIC_INBOX_OPERATORS"); assertThat(v.value()).contains("|binding-"); });
            }
            assertThat(graphs.get(graphId).getStateJson()).contains("applicationCreateIntent");
            return new PlatformManagedProductProvisioningService.OwnedDependencyResult("app-" + serviceRef,
                spec.database() == null ? null : "db-" + serviceRef, "https://" + serviceRef + ".example.test");
        });
        lifecycle = new ManagedDependencyLifecycleService(store, specRepository, validator, deployments, versions, targets,
            access, provider, products, productRepository, secrets, mock(PlatformAuditService.class), gates, Runnable::run);
        operators = new ManagedDependencyOperatorService(lifecycle, store, validator, secrets);
        login(PlatformRole.CUSTOMER_ADMIN);
    }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }
    void login(PlatformRole role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal("owner@example.test", role, "Owner", "JWT"), null, List.of()));
    }
    ManagedDependencyLifecycleService.Summary submit(String target) {
        return lifecycle.submit("dep-1", target, new ManagedDependencyLifecycleService.Request("spec1", "v1", "c".repeat(64), "create"));
    }
    ManagedDependencyLifecycleService.Summary ready(String target) {
        submit(target); return operators.enroll("dep-1", target, new ManagedDependencyOperatorService.Enroll("inbox", "Staff", "enroll"));
    }
    @Test void missingAdmissionBlocksBeforeAnyProviderResource() {
        submit("staging"); when(gates.orderedStream()).thenReturn(java.util.stream.Stream.empty());
        var result = lifecycle.reconcile("dep-1", "staging", "apply");
        assertThat(result.failureCode()).isEqualTo("DEPENDENCY_ADMISSION_ADAPTER_REQUIRED"); verifyNoInteractions(provider);
    }
    @Test void ambiguousAdmissionAdaptersBlockBeforeAnyProviderResource() {
        submit("staging");
        when(gates.orderedStream()).thenReturn(java.util.stream.Stream.of(gate, mock(ManagedDependencyPrerequisites.class)));
        var result = lifecycle.reconcile("dep-1", "staging", "apply");
        assertThat(result.failureCode()).isEqualTo("DEPENDENCY_ADMISSION_ADAPTER_REQUIRED");
        verifyNoInteractions(provider);
    }
    @Test void pluginOrGraphRowsAreNotReadinessAndEnrollmentIsRequired() {
        submit("staging"); var result = lifecycle.reconcile("dep-1", "staging", "apply");
        assertThat(result.status()).isEqualTo("BLOCKED"); assertThat(result.failureCode()).isEqualTo("INBOX_OPERATOR_ENROLLMENT_REQUIRED");
        verifyNoInteractions(provider);
    }
    @Test void fullFlowUsesOwnedResourcesAndSameOperationDoesNotRedeploy() {
        var ready = ready("staging"); assertThat(ready.status()).isEqualTo("READY");
        var owned = store.state(graphs.get(ready.graphId()));
        var refs = owned.nodes.stream().map(n -> n.serviceRef).toList();
        clearInvocations(provider);
        operators.enroll("dep-1", "staging", new ManagedDependencyOperatorService.Enroll("inbox", "Staff", "enroll"));
        verifyNoInteractions(provider);
        lifecycle.reconcile("dep-1", "staging", "retry-new-key");
        assertThat(store.state(graphs.get(ready.graphId())).nodes.stream().map(n -> n.serviceRef)).containsExactlyElementsOf(refs);
        verify(gate, times(6)).initializeAndVerify(any(), anyString(), any());
        assertThat(store.json(ready)).doesNotContain(keys.values().iterator().next(), "secretName");
    }
    @Test void targetsGetDifferentAccountsBindingsAndCredentials() {
        var staging = ready("staging"); var production = ready("production");
        var a = store.state(graphs.get(staging.graphId())); var b = store.state(graphs.get(production.graphId()));
        assertThat(a.nodes.stream().map(n -> n.accountId)).doesNotContainAnyElementsOf(b.nodes.stream().map(n -> n.accountId).toList());
        assertThat(a.nodes.stream().map(n -> n.bindingId)).doesNotContainAnyElementsOf(b.nodes.stream().map(n -> n.bindingId).toList());
        assertThat(a.operators.get(0).secretName).isNotEqualTo(b.operators.get(0).secretName);
    }
    @Test void oneTimeTlsDeliveryRotationAndLastOperatorRevokeAreManaged() {
        var ready = ready("staging"); String operatorId = ready.operators().get(0).id();
        var request = new ManagedDependencyOperatorService.Mutation(1, "deliver");
        assertThatThrownBy(() -> operators.deliver("dep-1", "staging", operatorId, request, false)).hasMessageContaining("TLS_REQUIRED");
        var delivered = operators.deliver("dep-1", "staging", operatorId, request, true);
        assertThat(delivered.apiKey()).hasSize(43); assertThat(delivered.toString()).doesNotContain(delivered.apiKey());
        assertThatThrownBy(() -> operators.deliver("dep-1", "staging", operatorId, request, true)).hasMessageContaining("ALREADY_DELIVERED");
        var rotated = operators.rotate("dep-1", "staging", operatorId, new ManagedDependencyOperatorService.Mutation(1, "rotate"));
        assertThat(rotated.operators().get(0).credentialRevision()).isEqualTo(2);
        assertThat(operators.deliver("dep-1", "staging", operatorId, new ManagedDependencyOperatorService.Mutation(2, "deliver2"), true).apiKey()).isNotEqualTo(delivered.apiKey());
        var revoked = operators.revoke("dep-1", "staging", operatorId, new ManagedDependencyOperatorService.Mutation(2, "revoke"));
        assertThat(revoked.failureCode()).isEqualTo("INBOX_DISABLED"); assertThat(revoked.operators().get(0).effectiveAt()).isNotNull();
        verify(provider).disableOwnedDependency(any(), eq(ready.graphId()), anyString(), eq("inbox"), anyString(), any());
        verify(gate).disableAndVerifyBinding(any(), any());
    }
    @Test void staffNeverReceivePlatformRoleOrOwnerManagementAuthority() {
        submit("staging"); login(PlatformRole.PLATFORM_OPERATOR);
        assertThatThrownBy(() -> operators.enroll("dep-1", "staging", new ManagedDependencyOperatorService.Enroll("inbox", "Staff", "enroll")))
            .hasMessageContaining("Customer owner enrollment"); verifyNoInteractions(provider);
    }
    @Test void canaryFailurePreservesOwnedPartialGraphForRetry() {
        doThrow(new IllegalStateException("private provider failure")).doNothing().when(gate).initializeAndVerify(any(), anyString(), any());
        var failed = ready("staging"); assertThat(failed.failureCode()).isEqualTo("DEPENDENCY_RECONCILE_FAILED");
        var before = store.state(graphs.get(failed.graphId())).nodes.get(0).applicationUuid;
        var retry = lifecycle.reconcile("dep-1", "staging", "retry"); assertThat(retry.status()).isEqualTo("READY");
        assertThat(store.state(graphs.get(failed.graphId())).nodes.get(0).applicationUuid).isEqualTo(before);
    }
    @Test void retirementChecksUnusedReclaimedAndRetainsHistory() {
        var ready = ready("staging"); var adapter = new ManagedDependencyRetirementAdapter(lifecycle, store, provider, secrets, validator);
        var plan = adapter.plan("dep-1", "staging");
        var result = adapter.retire("dep-1", "staging", plan.expectedRevision(), plan.fingerprint(), "retire");
        assertThat(result.status()).isEqualTo("RETIRED"); assertThat(graphs).containsKey(ready.graphId());
        verify(gate).verifyReclaimed(any(), anyList()); verify(gate).releaseReservation(any(), anyString());
        verify(deployments, never()).delete(any());
        clearInvocations(provider);
        adapter.retire("dep-1", "staging", plan.expectedRevision(), plan.fingerprint(), "retire"); verifyNoInteractions(provider);
    }
    @Test void uncertainReclamationKeepsCapacityAndRetryIsVisible() {
        ready("staging"); doThrow(new IllegalStateException()).when(gate).verifyReclaimed(any(), anyList());
        var adapter = new ManagedDependencyRetirementAdapter(lifecycle, store, provider, secrets, validator);
        var plan = adapter.plan("dep-1", "staging");
        var result = adapter.retire("dep-1", "staging", plan.expectedRevision(), plan.fingerprint(), "retire");
        assertThat(result.status()).isEqualTo("RETIREMENT_BLOCKED"); verify(gate, never()).releaseReservation(any(), anyString());
    }
    @Test void explicitProvisionedBindingsCanBootstrapRuntimeWithoutClaimingReady() {
        submit("staging");
        var pending = lifecycle.provision("dep-1", "staging", "resources-only");
        assertThat(pending.status()).isEqualTo("PROVISIONED");
        assertThat(lifecycle.provisionedBindings("dep-1", "staging", pending.specFingerprint(), pending.bindingFingerprint()))
            .extracting(ManagedDependencyPrerequisites.Binding::slot)
            .containsExactly("simulator", "receiver");
        var runtimeEnvironment = lifecycle.runtimeEnvironment("dep-1", "staging", "v1", pending.bindingFingerprint());
        assertThat(runtimeEnvironment.runtime()).isEmpty();
        assertThat(runtimeEnvironment.connector())
            .containsEntry("MANAGED_SIMULATOR_BASE_URL", "https://dep-" + pending.graphId().substring(4) + "-simulator.example.test")
            .containsEntry("MANAGED_SIMULATOR_HOST", "dep-" + pending.graphId().substring(4) + "-simulator.example.test")
            .containsEntry("MANAGED_RECEIVER_ACTION_URL", "https://dep-" + pending.graphId().substring(4)
                + "-receiver.example.test/api/internal/actions/execute")
            .containsEntry("MANAGED_INBOX_BASE_URL", "https://dep-" + pending.graphId().substring(4) + "-inbox.example.test")
            .containsEntry("MANAGED_INBOX_CALLBACK_SIGNING_SECRET", "${secret:TARGET_CALLBACK_SECRET}")
            .containsEntry("MANAGED_SIMULATOR_ACCOUNT_ID", store.state(graphs.get(pending.graphId())).nodes.getFirst().accountId);
        assertThat(runtimeEnvironment.connector().get("MANAGED_SIMULATOR_API_KEY"))
            .startsWith("${secret:MANAGED_DEP_").endsWith("}");
        assertThatThrownBy(() -> lifecycle.bindings("dep-1", "staging", pending.specFingerprint(), pending.bindingFingerprint()))
            .hasMessageContaining("BINDINGS_NOT_READY");
        verify(provider, never()).reconcileOwnedDependency(any(), anyString(), contains("inbox"), any(), any(), any(), any());

        var enrolled = operators.enroll("dep-1", "staging",
            new ManagedDependencyOperatorService.Enroll("inbox", "Staff", "enroll"));
        assertThat(enrolled.status()).isEqualTo("READY");
        assertThat(lifecycle.bindings("dep-1", "staging", enrolled.specFingerprint(), enrolled.bindingFingerprint()))
            .hasSize(3);
        assertThat(enrolled.effectiveConfigurationRevision()).isEqualTo(enrolled.configurationRevision());
    }

    @Test void callbackHostConsumersWaitForRuntimeBoundReconciliation() {
        var original = ManagedDependencyFixtures.topology();
        var simulator = original.services().getFirst();
        var environment = new TreeMap<>(simulator.environment());
        environment.put("SIMULATOR_ALLOWED_WEBHOOK_HOSTS",
            new ManagedDependencySpec.Value(ManagedDependencySpec.ValueKind.CALLBACK_HOSTS, null));
        var runtimeBoundSimulator = new ManagedDependencySpec.Service(
            simulator.slot(), simulator.contract(), simulator.image(), simulator.sourceArtifactId(),
            simulator.sourceCommit(), simulator.port(), simulator.healthPath(), simulator.capacity(),
            simulator.secretPurposes(), environment, simulator.database(), simulator.volumes(),
            simulator.dependsOn(), simulator.initializationContract());
        var topology = new ManagedDependencySpec(original.schemaVersion(),
            List.of(runtimeBoundSimulator, original.services().get(1), original.services().get(2)),
            original.runtimeEnvironment());
        var reviewed = new ManagedDependencySpecEntity();
        reviewed.setId("spec1");
        reviewed.setActive(true);
        reviewed.setReviewedAt(Instant.now());
        reviewed.setReviewedBy("reviewer");
        reviewed.setSpecJson(store.json(topology));
        reviewed.setFingerprint(validator.fingerprint(topology));
        when(specRepository.findById("spec1")).thenReturn(Optional.of(reviewed));

        submit("staging");
        lifecycle.provision("dep-1", "staging", "resources-only");

        verify(provider, never()).reconcileOwnedDependency(any(), anyString(), contains("simulator"),
            any(), any(), any(), any());
    }

    @Test void runtimeEnvironmentFailsClosedWhenTargetOwnedSecretIsLost() {
        submit("staging");
        var pending = lifecycle.provision("dep-1", "staging", "resources-only");
        var graph = graphs.get(pending.graphId());
        var state = store.state(graph);
        state.targetSecretReferences.remove("conversation-handover-callback-signing");
        graph.setStateJson(store.json(state));

        assertThatThrownBy(() -> lifecycle.runtimeEnvironment(
            "dep-1", "staging", "v1", pending.bindingFingerprint()))
            .hasMessageContaining("DEPENDENCY_TARGET_SECRET_MISSING");
    }

    @Test void runtimeGenerationQuiescesAndResumesOnlyTheManagedInbox() {
        var ready = ready("staging");
        when(provider.quiesceOwnedDependencyForRuntimeGeneration(any(), eq(ready.graphId()), anyString(),
            eq("inbox"), anyString(), any())).thenReturn("managed-dependency-generation:stopped");
        when(provider.resumeOwnedDependencyAfterRuntimeGeneration(any(), eq(ready.graphId()), anyString(),
            eq("inbox"), anyString(), any())).thenReturn("managed-dependency-generation:running");

        var evidence = lifecycle.quiesceForRuntimeGeneration(
            "dep-1", "staging", "release-old", "release-new", "v1"
        );
        assertThat(evidence.readbacks()).containsEntry("HUMAN_INBOX", "managed-dependency-generation:stopped");
        var quiesced = store.state(graphs.get(ready.graphId()));
        assertThat(quiesced.runtimeGenerationState).isEqualTo("QUIESCED");
        assertThat(quiesced.runtimeGenerationStoppedSlots).containsExactly("inbox");

        lifecycle.resumeAfterRuntimeGeneration("dep-1", "staging", "release-old", "release-new", "v1");
        var resumed = store.state(graphs.get(ready.graphId()));
        assertThat(resumed.runtimeGenerationState).isEqualTo("RESUMED");
        assertThat(resumed.runtimeGenerationStoppedSlots).isEmpty();
        verify(provider).quiesceOwnedDependencyForRuntimeGeneration(any(), eq(ready.graphId()), anyString(),
            eq("inbox"), anyString(), any());
        verify(provider).resumeOwnedDependencyAfterRuntimeGeneration(any(), eq(ready.graphId()), anyString(),
            eq("inbox"), anyString(), any());
    }

    @Test void releaseRebindAdvancesGenerationAndInitialOwnerEnrollmentIsIdempotent() {
        var first = lifecycle.submitForRelease("dep-1", "staging",
            new ManagedDependencyLifecycleService.Request("spec1", "v1", "release-1", "c".repeat(64), "submit-1"));
        operators.enrollInitialOwnerForRelease("dep-1", "staging", "release-1",
            new ManagedDependencyOperatorService.Enroll("inbox", "owner@example.test", "owner-1"));
        long firstRevision = lifecycle.status("dep-1", "staging").configurationRevision();

        var graph = graphs.get(first.graphId());
        graph.setReservationRef("reservation-old");
        var state = store.state(graph);
        state.callbacks.put("inbox", "https://old.example.test/callback");
        state.origins = List.of("https://old.example.test");
        state.targetValues.put("runtime-base-url", "https://old-runtime.example.test");
        graph.setStateJson(store.json(state));

        var rebound = lifecycle.submitForRelease("dep-1", "staging",
            new ManagedDependencyLifecycleService.Request("spec1", "v2", "release-2", "d".repeat(64), "submit-2"));
        operators.enrollInitialOwnerForRelease("dep-1", "staging", "release-2",
            new ManagedDependencyOperatorService.Enroll("inbox", "owner@example.test", "owner-2"));

        assertThat(rebound.configurationRevision()).isGreaterThan(firstRevision);
        assertThat(rebound.versionId()).isEqualTo("v2");
        assertThat(rebound.releaseId()).isEqualTo("release-2");
        assertThat(rebound.bindingFingerprint()).isEqualTo("d".repeat(64));
        assertThat(graphs.get(first.graphId()).getReservationRef()).isNull();
        assertThat(store.state(graphs.get(first.graphId())).callbacks).isEmpty();
        assertThat(store.state(graphs.get(first.graphId())).origins).isEmpty();
        assertThat(store.state(graphs.get(first.graphId())).targetValues).isEmpty();
        assertThat(store.state(graphs.get(first.graphId())).operators)
            .filteredOn(operator -> "ENROLLED".equals(operator.status))
            .hasSize(1);
    }
}
