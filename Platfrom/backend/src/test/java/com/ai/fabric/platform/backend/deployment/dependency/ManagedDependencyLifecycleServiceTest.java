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
        for (String target : List.of("staging", "production")) {
            var profile = new DeploymentTargetProfileEntity(); profile.setId(target); profile.setActive(true); profile.setEnvironmentName(target);
            when(targets.findById(target)).thenReturn(Optional.of(profile));
        }
        var reviewed = new ManagedDependencySpecEntity(); reviewed.setId("spec1"); reviewed.setActive(true);
        reviewed.setReviewedAt(Instant.now()); reviewed.setReviewedBy("reviewer");
        reviewed.setSpecJson(store.json(ManagedDependencyFixtures.topology())); reviewed.setFingerprint(validator.fingerprint(ManagedDependencyFixtures.topology()));
        when(specRepository.findById("spec1")).thenReturn(Optional.of(reviewed));
        when(gates.getIfAvailable()).thenReturn(gate);
        when(gate.reserve(any(), any())).thenAnswer(i -> new ManagedDependencyPrerequisites.Admission(
            "reservation-" + ((ManagedDependencyPrerequisites.Scope)i.getArgument(0)).graphId(), "c".repeat(64),
            Map.of("inbox", "https://connector.example.test/inbox/callback"), List.of("https://app.example.test"), Map.of(), Map.of()));
        when(secrets.isSecretPresent(anyString())).thenAnswer(i -> keys.containsKey(i.getArgument(0)));
        when(secrets.resolveSecret(anyString())).thenAnswer(i -> keys.get(i.getArgument(0)));
        when(secrets.upsertManagedSecret(anyString(), anyString(), anyMap())).thenAnswer(i -> {
            String name = i.getArgument(0); assertThat(name).matches("[A-Z0-9_]+"); assertThat(name.length()).isLessThan(128);
            assertThat(((Map<?, ?>)i.getArgument(2)).containsKey("deploymentId")).isTrue(); keys.put(name, i.getArgument(1)); return null;
        });
        when(secrets.clearManagedSecret(anyString(), anyMap())).thenAnswer(i -> { keys.remove(i.getArgument(0)); return null; });
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
        submit("staging"); when(gates.getIfAvailable()).thenReturn(null);
        var result = lifecycle.reconcile("dep-1", "staging", "apply");
        assertThat(result.failureCode()).isEqualTo("DEPENDENCY_ADMISSION_ADAPTER_REQUIRED"); verifyNoInteractions(provider);
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
        doThrow(new IllegalStateException("runtime not yet provisioned")).when(gate).initializeAndVerify(any(), anyString(), any());
        var pending = operators.enroll("dep-1", "staging", new ManagedDependencyOperatorService.Enroll("inbox", "Staff", "enroll"));
        assertThat(pending.status()).isEqualTo("BLOCKED");
        assertThat(lifecycle.provisionedBindings("dep-1", "staging", pending.specFingerprint(), pending.bindingFingerprint())).hasSize(3);
        assertThatThrownBy(() -> lifecycle.bindings("dep-1", "staging", pending.specFingerprint(), pending.bindingFingerprint()))
            .hasMessageContaining("BINDINGS_NOT_READY");
        var provisioned = lifecycle.provision("dep-1", "staging", "resources-only");
        assertThat(provisioned.status()).isEqualTo("PROVISIONED");
        assertThat(provisioned.effectiveConfigurationRevision()).isEqualTo(-1);
    }
}
