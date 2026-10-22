package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.retirement.*;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Vector;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.retirement.ExternalIndexRetentionContracts.*;
import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual PostgreSQL/Flyway and transactions. Only runtime/provider I/O is synthetic. */
@SpringBootTest(properties = {
    "spring.flyway.locations=classpath:db/migration", "spring.flyway.target=208",
    "platform.bootstrap.sample-enabled=false", "platform.bootstrap.ecommerce-demo.enabled=false",
    "platform.auth.bootstrap-admin-enabled=false", "platform.deployment.admission.release.worker-enabled=false",
    "platform.deployment.admission.lifecycle.worker-enabled=false",
    "platform.deployment.admission.lifecycle.retirement-worker-enabled=false", "platform.target-retirement.worker-enabled=false",
    "platform.target-retirement.approved-retained-volume-mounts=/synthetic/retained"
})
@ActiveProfiles("test")
@Testcontainers
class ExternalIndexRetentionPostgresIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AdmissionJson json;
    @Autowired TargetRetirementJson retirementJson;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PlatformAuditService audit;
    @Autowired DeploymentRepository deployments;
    @Autowired DeploymentDraftRepository drafts;
    @Autowired DeploymentVersionRepository versions;
    @Autowired DeploymentTargetProfileRepository targets;
    @Autowired DeploymentReleaseRepository releases;
    @Autowired DeploymentProviderResourceHandleRepository handles;
    @Autowired TargetRetirementStore retirements;
    @Autowired TargetRetirementOperationRepository operations;
    @Autowired TargetRetirementWorker worker;
    @Autowired ProductionAdmissionResourceReadbackSource resourceReadback;
    @MockBean TargetRetirementRuntimeGateway runtime;
    @MockBean TargetRetirementRuntimeIndexGateway indexes;
    @MockBean TargetRetirementProviderResources provider;
    @MockBean CoolifyApiClient coolify;
    @MockBean CoolifyTargetProfileResolver resolver;

    private final Placement placement = AdmissionFixture.STAGING;
    private MutableClock clock;
    private AdmissionStore store;
    private ExternalIndexRetentionService retention;
    private String suffix, customer, tenant, deploymentId, targetId, releaseId;
    private DeploymentVersionEntity version;
    private DeploymentProviderResourceHandleEntity binding, application;
    private Manifest manifest;
    private Policy policy;
    private Reservation reservation;
    private Summary operation;
    private final AtomicBoolean absent = new AtomicBoolean();

    @BeforeEach void fixture() {
        jdbc.execute("TRUNCATE deployment_capacity_reservations, deployment_admission_reviews, deployment_admission_grants, platform_target_retirement_operations, platform_target_retirement_gates CASCADE");
        jdbc.update("UPDATE deployment_admission_mutex SET active_policy_ref=NULL,lifecycle_cursor=NULL WHERE id=1");
        suffix = UUID.randomUUID().toString().substring(0, 8);
        customer = "retention-customer-" + suffix; tenant = "retention-tenant-" + suffix;
        deploymentId = "retention-deployment-" + suffix; targetId = "retention-target-" + suffix; releaseId = "retention-release-" + suffix;
        clock = new MutableClock(Instant.now().truncatedTo(ChronoUnit.MICROS));
        org.springframework.test.util.ReflectionTestUtils.setField(resourceReadback, "clock", clock);
        store = new AdmissionStore(jdbc, json, transactions, clock);
        retention = new ExternalIndexRetentionService(store, jdbc, json, retirementJson, audit, transactions, runtime);
        authenticate(PlatformRole.PLATFORM_ADMIN);
        jdbc.update("""
            INSERT INTO platform_users(id,email,display_name,password_hash,role,status,created_at,updated_at)
            VALUES (?,'retention-reviewer@example.test','Synthetic reviewer','synthetic-not-a-login','PLATFORM_ADMIN','ACTIVE',?,?)
            ON CONFLICT (email) DO NOTHING
            """, "retention-reviewer-" + suffix, time(), time());
        jdbc.update("INSERT INTO platform_customers(id,name,slug,status,platform_managed,created_at,updated_at) VALUES (?,?,?,'ACTIVE',false,?,?)",
            customer, customer, customer, time(), time());
        jdbc.update("INSERT INTO platform_customer_tenants(id,customer_id,name,slug,status,created_at,updated_at) VALUES (?,?,?,?,'ACTIVE',?,?)",
            tenant, customer, tenant, tenant, time(), time());
        var deployment = new DeploymentEntity();
        deployment.setId(deploymentId); deployment.setCustomerId(customer); deployment.setTenantId(tenant);
        deployment.setName(deploymentId); deployment.setEnvironmentName("staging"); deployment.setTemplateId("synthetic-template");
        deployment.setBehaviorType("CONVERSATIONAL"); deployment.setStatus("CONFIGURED");
        deployment.setCreatedAt(clock.instant()); deployment.setUpdatedAt(clock.instant()); deployments.saveAndFlush(deployment);
        var target = new DeploymentTargetProfileEntity();
        target.setId(targetId); target.setName(targetId); target.setProviderType(DeploymentProviderType.COOLIFY);
        target.setEnvironmentName("staging"); target.setActive(true); target.setSourceStrategy("IMMUTABLE_IMAGE");
        target.setProviderConfigJson(json.write(Map.of("serverUuid", placement.serverUuid(), "destinationUuid", placement.destinationUuid())));
        target.setNetworkPolicyJson("{}"); target.setResourceDefaultsJson("{}");
        target.setCreatedAt(clock.instant()); target.setUpdatedAt(clock.instant()); targets.saveAndFlush(target);
        var draft = new DeploymentDraftEntity();
        draft.setId("retention-draft-" + suffix); draft.setDeploymentId(deploymentId); draft.setRevisionNumber(1); draft.setStatus("PUBLISHED");
        draft.setActionsConfigJson("{}"); draft.setEntityConfigJson("{}"); draft.setRoutingConfigJson("{}");
        draft.setProviderConfigJson("{}"); draft.setSecurityConfigJson("{}"); draft.setPromptConfigJson("{}");
        draft.setCreatedAt(clock.instant()); draft.setUpdatedAt(clock.instant()); drafts.saveAndFlush(draft);
        version = new DeploymentVersionEntity();
        version.setId("retention-version-" + suffix); version.setDeploymentId(deploymentId); version.setSourceDraftId(draft.getId());
        version.setVersionLabel("synthetic-v1"); version.setStatus("PUBLISHED"); version.setConfigHash(json.fingerprint(suffix));
        version.setActionsConfigJson("{}"); version.setEntityConfigJson("{}"); version.setRoutingConfigJson("{}"); version.setProviderConfigJson("{}");
        version.setSecurityConfigJson("{}"); version.setPromptConfigJson("{}"); version.setActionsArtifactYaml("{}");
        version.setEntityArtifactYaml("{}"); version.setRoutingArtifactYaml("{}"); version.setManifestJson("{}");
        version.setPublishedAt(clock.instant()); versions.saveAndFlush(version);
        var release = new DeploymentReleaseEntity();
        release.setId(releaseId); release.setDeploymentId(deploymentId); release.setDeploymentVersionId(version.getId());
        release.setTargetProfileId(targetId); release.setProviderType(DeploymentProviderType.COOLIFY); release.setProvisioningTarget("COOLIFY");
        release.setStatus("APPLIED_VERIFIED"); release.setVerificationStatus("VERIFIED"); release.setProvisioningStatus("ACTIVE");
        release.setProvisioningDetailsJson("{}"); release.setCreatedAt(clock.instant()); release.setAppliedAt(clock.instant()); release.setUpdatedAt(clock.instant());
        releases.saveAndFlush(release);
        var metadata = mapper.createObjectNode().put("customerId", customer).put("tenantId", tenant).put("deploymentId", deploymentId)
            .put("targetProfileId", targetId).put("versionId", version.getId()).put("configHash", version.getConfigHash())
            .put("clusterId", "synthetic-cluster-" + suffix).put("endpointHost", "synthetic-" + suffix + ".zilliz.com")
            .put("databaseName", "default").put("collectionPrefix", "synthetic_" + suffix).put("lifecycle", "RETAIN_EXTERNAL")
            .put("externalResourceOwned", false);
        metadata.put("fingerprint", json.fingerprint(metadata));
        binding = handle("external", "EXTERNAL_VECTOR_STORAGE_BINDING", "synthetic-cluster-" + suffix, null, metadata.toString());
        application = handle("runtime", "APPLICATION", "synthetic-runtime-" + suffix, releaseId, "{\"applicationName\":\"synthetic-runtime\"}");
        var config = new CoolifyTargetProfileConfig("https://synthetic-coolify.invalid", "synthetic-project", "staging", "synthetic-environment",
            placement.serverUuid(), placement.destinationUuid(), null, null, 5, 60, false, false, "8080", "/health", "8080");
        when(resolver.requireConnection(any())).thenReturn(new CoolifyConnection(config.baseUrl(), "synthetic-only-token", config));
        when(coolify.listApplicationStorages(any(), anyString())).thenReturn(List.of());
        absent.set(false);
        when(provider.readback(any(), any())).thenAnswer(invocation -> new EffectResult(absent.get(), "synthetic-owned-provider-readback", Instant.now()));
        when(provider.delete(any(), any())).thenAnswer(invocation -> {
            Context context = invocation.getArgument(0);
            assertThat(retention.requireReceipt(context).indexReadback().totalVectors()).isEqualTo(7);
            assertThat(count("deployment_external_index_retention_receipts")).isEqualTo(1);
            absent.set(true);
            return new EffectResult(true, "synthetic-owned-provider-absent", Instant.now());
        });
        when(runtime.status(any())).thenAnswer(invocation -> runtimeSnapshot(invocation.getArgument(0)).target());
        when(runtime.snapshot(any())).thenAnswer(invocation -> runtimeSnapshot(invocation.getArgument(0)));
        when(indexes.readback(any())).thenAnswer(invocation -> proof(invocation.getArgument(0), 19, "synthetic-runtime-topology"));
        var budget = new Budget(vector(10_000), vector(0), vector(0), "synthetic-measurement", clock.instant().minusSeconds(1), clock.instant().plusSeconds(10_000));
        var fleet = new FleetPolicy("retention-fleet-" + suffix, budget,
            Map.of(targetId, new TargetBudget(targetId, Intent.CUSTOMER_STAGING, placement, "synthetic-profile", budget)),
            new LifecyclePolicy(100, 10, 20, 1, 1000, 20, 10, 10, 10, 3, 60, 3600, "synthetic-lifecycle", "synthetic-notice-transport"),
            "synthetic-restore", "synthetic-privacy", "synthetic-support");
        store.approvePolicy(fleet, "synthetic-reviewer", clock.instant());
        store.approveGrant(new CustomerGrant(customer, vector(10_000), Map.of(targetId, vector(10_000)), clock.instant().plusSeconds(5000),
            "synthetic-contact", "synthetic-payer", null), "synthetic-reviewer", clock.instant());
        manifest = new Manifest("retention-manifest-" + suffix, deploymentId, version.getId(), version.getConfigHash(), targetId,
            json.fingerprint(binding.getId()), "synthetic-full-topology", with(vector(1), Dimension.INDEX_BYTES, 0), Set.of("synthetic-prerequisites"));
        store.approveManifest(manifest, "synthetic-reviewer", clock.instant());
        var context = context(manifest);
        reservation = store.reserve(context, new Reserve("synthetic-apply", manifest.id()), prerequisite(context), "synthetic-owner", clock.instant());
        reservation = store.beginProvisioning(reservation.fence(), context, prerequisite(context), "synthetic-worker", clock.instant(), () -> { });
        reservation = store.reconcile(reservation.fence(), readback(reservation, null, false), false, "synthetic-worker", clock.instant());
        policy = new Policy(reservation.id(), reservation.generation(), reservation.manifestFingerprint(), reservation.bindingFingerprint(),
            new Binding(binding.getId(), metadata.path("fingerprint").asText(), binding.getProviderResourceUuid(), metadata.path("endpointHost").asText(),
                "default", "milvus", "COLLECTION_PREFIX", metadata.path("collectionPrefix").asText()),
            1, 500, "synthetic-customer-preservation-approval", "synthetic-payer", clock.instant().plusSeconds(3600));
    }

    @AfterEach void noVectorMutation() {
        verify(indexes, never()).retire(any());
        verify(provider, never()).delete(any(), argThat(ResourceGeneration::preserved));
        SecurityContextHolder.clearContext();
    }

    @Test void reviewReservesBytesWithoutRewritingTheOriginalManifestOrFinancialHistory() {
        String original = manifestSnapshot();
        String financial = financialSnapshot();
        Authorization reviewed = retention.approve(policy);
        reservation = store.require(reservation.id());
        assertThat(reservation.demand().amounts().get(Dimension.INDEX_BYTES)).isZero();
        assertThat(reservation.chargedDemand().amounts().get(Dimension.INDEX_BYTES)).isEqualTo(500);
        assertThat(manifestSnapshot()).isEqualTo(original);
        long revision = reservation.revision();
        assertThat(retention.approve(policy)).isEqualTo(reviewed);
        assertThat(store.require(reservation.id()).revision()).isEqualTo(revision);
        assertThat(financialSnapshot()).isEqualTo(financial);
    }

    @Test void explicitReviewCanUnblockTheExactUnstartedPlanWithoutRewritingIt() {
        planWithoutIndexReview();
        var entity = operations.findById(operation.id()).orElseThrow();
        var context = retirements.context(entity);
        assertThatThrownBy(() -> retention.requirePolicy(context)).hasMessageContaining("EXTERNAL_VECTOR_INDEX_RETENTION_REQUIRED");
        String planned = row("platform_target_retirement_operations", operation.id());
        String financial = financialSnapshot();
        long memory = store.require(reservation.id()).chargedDemand().amounts().get(Dimension.MEMORY_BYTES);

        Authorization approved = retention.approve(policy);

        assertThat(approved.reservationId()).isEqualTo(reservation.id());
        assertThat(retention.requirePolicy(context)).isEqualTo(policy);
        assertThat(row("platform_target_retirement_operations", operation.id())).isEqualTo(planned);
        assertThat(store.require(reservation.id()).chargedDemand().amounts().get(Dimension.MEMORY_BYTES)).isEqualTo(memory);
        assertThat(store.require(reservation.id()).chargedDemand().amounts().get(Dimension.INDEX_BYTES)).isEqualTo(500);
        assertThat(financialSnapshot()).isEqualTo(financial);
        assertThat(retention.approve(policy)).isEqualTo(approved);
        verifyNoInteractions(indexes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"queued", "started", "attempt", "worker", "effect", "gate", "epoch", "actor"})
    void lateReviewNeverChangesAStartedBusyOrDifferentlyScopedPlan(String mutation) {
        planWithoutIndexReview();
        switch (mutation) {
            case "queued" -> jdbc.update("UPDATE platform_target_retirement_operations SET state='QUEUED' WHERE id=?", operation.id());
            case "started" -> jdbc.update("UPDATE platform_target_retirement_operations SET started_at=? WHERE id=?", time(), operation.id());
            case "attempt" -> jdbc.update("UPDATE platform_target_retirement_operations SET attempt=1 WHERE id=?", operation.id());
            case "worker" -> jdbc.update("UPDATE platform_target_retirement_operations SET worker_token='synthetic-worker' WHERE id=?", operation.id());
            case "effect" -> jdbc.update("UPDATE platform_target_retirement_operations SET in_flight_effect='synthetic-effect' WHERE id=?", operation.id());
            case "gate" -> jdbc.update("UPDATE platform_target_retirement_gates SET state='RETIRING',operation_id=? WHERE deployment_id=? AND target_profile_id=?", operation.id(), deploymentId, targetId);
            case "epoch" -> jdbc.update("UPDATE platform_target_retirement_gates SET epoch=epoch+1 WHERE deployment_id=? AND target_profile_id=?", deploymentId, targetId);
            case "actor" -> jdbc.update("UPDATE platform_target_retirement_operations SET requested_by_actor_id='foreign' WHERE id=?", operation.id());
        }
        String before = ledgerSnapshot();
        assertThatThrownBy(() -> retention.approve(policy)).hasMessageContaining("INDEX_RETENTION_REVIEW_BEFORE_EFFECT_REQUIRED");
        assertThat(ledgerSnapshot()).isEqualTo(before);
        assertThat(store.indexRetentionAuthorization(reservation.id())).isNull();
        verifyNoInteractions(indexes);
    }

    @ParameterizedTest @ValueSource(longs = {0, -1, 10_001})
    void missingOrUnaffordableByteEnvelopeCannotEnableRetention(long bytes) {
        String before = ledgerSnapshot();
        var invalid = new Policy(policy.reservationId(), policy.generation(), policy.manifestFingerprint(), policy.bindingFingerprint(),
            policy.binding(), policy.indexes(), bytes, policy.policyReference(), policy.payerReference(), policy.reviewAcceptBefore());
        assertThatThrownBy(() -> retention.approve(invalid)).isInstanceOf(RuntimeException.class);
        assertThat(ledgerSnapshot()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"CUSTOMER_ADMIN", "PLATFORM_OPERATOR"})
    void reviewIsNotACustomerOrOperatorFlag(String role) {
        authenticate(PlatformRole.valueOf(role));
        assertThatThrownBy(() -> retention.approve(policy)).hasMessageContaining("INDEX_RETENTION_STAFF_REVIEW_REQUIRED");
        assertThat(store.indexRetentionAuthorization(reservation.id())).isNull();
    }

    @Test void anExpiredAcceptanceDeadlineCannotPublishAReviewOrReserveAnEnvelope() {
        policy = policyAcceptBefore(Instant.now().minusSeconds(1));
        String before = ledgerSnapshot();
        assertThatThrownBy(() -> retention.approve(policy)).hasMessageContaining("INDEX_RETENTION_POLICY_INVALID");
        assertThat(ledgerSnapshot()).isEqualTo(before);
        assertThat(store.indexRetentionAuthorization(reservation.id())).isNull();
    }

    @Test void acceptedPreservationRemainsDurableWhenCleanupStartsAfterTheAcceptanceDeadline() throws Exception {
        policy = policyAcceptBefore(Instant.now().plusSeconds(3));
        Authorization accepted = retention.approve(policy);
        String review = table("deployment_admission_reviews");
        while (Instant.now().isBefore(policy.reviewAcceptBefore())) TimeUnit.MILLISECONDS.sleep(25);
        assertThat(retention.approve(policy)).isEqualTo(accepted);
        startRetirement();
        for (int i = 0; i < 10; i++) worker.runNext(operation.id());
        var completed = retirements.summary(operations.findById(operation.id()).orElseThrow());
        assertThat(completed.state()).isEqualTo(TargetRetirementContracts.State.RETIRED_WITH_RETAINED_DATA);
        Reservation settled = store.settleRetained(reservation.fence(), settlementReadback(), "synthetic-late-settlement", clock.instant());
        assertThat(settled.state()).isEqualTo(AdmissionContracts.State.RETAINED_EXTERNAL);
        assertThat(settled.chargedDemand().amounts().get(Dimension.INDEX_BYTES)).isEqualTo(500);
        assertThat(table("deployment_admission_reviews")).isEqualTo(review);
        assertThat(count("deployment_external_index_retention_receipts")).isEqualTo(1);
        verify(provider, times(1)).delete(any(), argThat(r -> r.handleId().equals(application.getId())));
    }

    @Test void nonzeroNativeReadbackCommitsBeforeComputeAndSettlesOnlyResidualQuota() {
        String bindingBefore = row("deployment_provider_resource_handles", binding.getId());
        String manifestBefore = manifestSnapshot();
        String financial = financialSnapshot();
        startRetirement();
        for (int i = 0; i < 10; i++) worker.runNext(operation.id());
        Summary finished = retirements.summary(operations.findById(operation.id()).orElseThrow());
        assertThat(finished.state()).isEqualTo(TargetRetirementContracts.State.RETIRED_WITH_RETAINED_DATA);
        assertThat(finished.consumesCapacity()).isTrue();
        assertThat(count("deployment_external_index_retention_receipts")).isEqualTo(1);
        var receipt = retention.requireReceipt(retirements.context(operations.findById(operation.id()).orElseThrow()));
        assertThat(receipt.indexReadback().complete()).isFalse();
        assertThat(receipt.indexReadback().totalVectors()).isEqualTo(7);
        assertThat(receipt.indexReadback().removedVectors()).isZero();
        var evidence = settlementReadback();
        assertThat(evidence.resourcesAbsent()).isFalse();
        Fence from = reservation.fence();
        Reservation settled = store.settleRetained(from, evidence, "synthetic-settlement", clock.instant());
        assertThat(settled.state()).isEqualTo(AdmissionContracts.State.RETAINED_EXTERNAL);
        assertThat(settled.expiryState()).isEqualTo(ExpiryState.SETTLED_RETAINED);
        assertThat(settled.occupiesActiveSlot()).isFalse();
        assertThat(settled.consumesCapacity()).isTrue();
        assertThat(settled.chargedDemand().amounts().get(Dimension.INDEXES)).isEqualTo(1);
        assertThat(settled.chargedDemand().amounts().get(Dimension.INDEX_BYTES)).isEqualTo(500);
        for (Dimension d : List.of(Dimension.CPU_MILLICORES, Dimension.MEMORY_BYTES, Dimension.DISK_BYTES, Dimension.ENVIRONMENTS))
            assertThat(settled.chargedDemand().amounts().get(d)).as(d.name()).isZero();
        assertThat(settled.chargedDemand().amounts().get(Dimension.PROVIDER_SPEND_MICROS_PER_WINDOW)).isEqualTo(1);
        assertThat(store.settleRetained(from, evidence, "synthetic-settlement", clock.instant())).isEqualTo(settled);
        assertThat(store.lifecycleBatch(32)).noneMatch(r -> r.id().equals(settled.id()));
        assertThat(row("deployment_provider_resource_handles", binding.getId())).isEqualTo(bindingBefore);
        assertThat(manifestSnapshot()).isEqualTo(manifestBefore);
        assertThat(financialSnapshot()).isEqualTo(financial);
        verify(provider, times(1)).delete(any(), argThat(r -> r.handleId().equals(application.getId())));
    }

    @ParameterizedTest @ValueSource(strings = {"fence", "topology", "scope", "removed", "complete"})
    void forgedNativeReadbackCannotCreateAReceiptOrRemoveCompute(String field) {
        startRetirement();
        worker.runNext(operation.id()); worker.runNext(operation.id());
        Context context = retirements.claim(operation.id());
        var valid = proof(context, 19, "synthetic-runtime-topology");
        var forged = new TargetRetirementRuntimeIndexGateway.IndexReadback(valid.target(), valid.tenantId(), valid.operationId(),
            field.equals("fence") ? 20 : valid.fenceEpoch(), field.equals("topology") ? "other-topology" : valid.topologyFingerprint(),
            valid.provider(), valid.scopeType(), field.equals("scope") ? "other-prefix" : valid.scopeValue(), valid.countsByEntityType(),
            valid.totalVectors(), field.equals("removed") ? 1 : 0, valid.observedAt(), valid.evidenceReference(), field.equals("complete"), false);
        assertThatThrownBy(() -> retention.record(context, forged)).hasMessageContaining("INDEX_RETENTION_READBACK_INVALID");
        assertThat(count("deployment_external_index_retention_receipts")).isZero();
        verify(provider, never()).delete(any(), any());
    }

    @Test void receiptAndAuditRollbackTogetherAndRetryHasOneImmutableResult() {
        startRetirement(); worker.runNext(operation.id()); worker.runNext(operation.id());
        Context context = retirements.claim(operation.id());
        var proof = proof(context, 19, "synthetic-runtime-topology");
        jdbc.execute("CREATE FUNCTION synthetic_fail_retention_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='EXTERNAL_INDEX_RETENTION_RECORDED' THEN RAISE EXCEPTION 'synthetic audit failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER synthetic_retention_audit_failure BEFORE INSERT ON platform_audit_events FOR EACH ROW EXECUTE FUNCTION synthetic_fail_retention_audit()");
        try {
            assertThatThrownBy(() -> retention.record(context, proof)).isInstanceOf(RuntimeException.class);
            assertThat(count("deployment_external_index_retention_receipts")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER synthetic_retention_audit_failure ON platform_audit_events");
            jdbc.execute("DROP FUNCTION synthetic_fail_retention_audit()");
        }
        Receipt first = retention.record(context, proof);
        assertThat(retention.record(context, proof)).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_audit_events WHERE action='EXTERNAL_INDEX_RETENTION_RECORDED' AND target_id=?", Long.class, context.operationId())).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("UPDATE deployment_external_index_retention_receipts SET fence_epoch=fence_epoch+1 WHERE id=?", first.id()))
            .hasMessageContaining("immutable");
        assertThat(retention.requireReceipt(context)).isEqualTo(first);
    }

    @ParameterizedTest @ValueSource(strings = {"same", "count", "fence"})
    void concurrentReceiptReplayRequiresTheExactWinningProof(String change) throws Exception {
        startRetirement(); worker.runNext(operation.id()); worker.runNext(operation.id());
        Context context = retirements.claim(operation.id());
        var first = proof(context, 19, "synthetic-runtime-topology");
        var second = new TargetRetirementRuntimeIndexGateway.IndexReadback(first.target(), first.tenantId(), first.operationId(),
            change.equals("fence") ? 20 : first.fenceEpoch(), first.topologyFingerprint(), first.provider(), first.scopeType(),
            first.scopeValue(), Map.of("synthetic_document", change.equals("count") ? 8L : 7L), change.equals("count") ? 8 : 7,
            first.removedVectors(), first.observedAt(), first.evidenceReference(), first.complete(), first.replay());
        CountDownLatch bothReadEmpty = new CountDownLatch(2);
        var submittedProof = new ThreadLocal<TargetRetirementRuntimeIndexGateway.IndexReadback>();
        doAnswer(invocation -> {
            bothReadEmpty.countDown();
            assertThat(bothReadEmpty.await(10, TimeUnit.SECONDS)).isTrue();
            return runtimeSnapshot(invocation.getArgument(0), submittedProof.get().fenceEpoch());
        }).when(runtime).snapshot(any());
        record Attempt(Receipt receipt, TargetRetirementFailure failure) { }
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = List.of(first, second).stream().map(proof -> pool.submit(() -> {
                authenticate(PlatformRole.PLATFORM_ADMIN); submittedProof.set(proof);
                try { return new Attempt(retention.record(context, proof), null); }
                catch (TargetRetirementFailure rejected) { return new Attempt(null, rejected); }
                finally { submittedProof.remove(); SecurityContextHolder.clearContext(); }
            })).toList();
            var attempts = new ArrayList<Attempt>();
            for (var result : results) attempts.add(result.get(30, TimeUnit.SECONDS));
            if (change.equals("same")) {
                assertThat(attempts).allMatch(attempt -> attempt.failure() == null);
                assertThat(attempts.getFirst().receipt()).isEqualTo(attempts.getLast().receipt());
            } else {
                assertThat(attempts.stream().filter(attempt -> attempt.receipt() != null)).hasSize(1);
                assertThat(attempts.stream().filter(attempt -> attempt.failure() != null)).singleElement()
                    .satisfies(attempt -> assertThat(attempt.failure()).hasMessageContaining("INDEX_RETENTION_RECEIPT_REPLAY_CHANGED"));
            }
            var winner = attempts.stream().map(Attempt::receipt).filter(Objects::nonNull).findFirst().orElseThrow();
            assertThat(retention.requireReceipt(context)).isEqualTo(winner);
        }
        assertThat(count("deployment_external_index_retention_receipts")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_audit_events WHERE action='EXTERNAL_INDEX_RETENTION_RECORDED' AND target_id=?", Long.class, context.operationId())).isEqualTo(1);
        verify(provider, never()).delete(any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"worker", "gate", "snapshot", "operationFence"})
    void changedRetirementGenerationCannotCommitRetentionProof(String change) {
        startRetirement(); worker.runNext(operation.id()); worker.runNext(operation.id());
        Context context = retirements.claim(operation.id());
        switch (change) {
            case "worker" -> jdbc.update("UPDATE platform_target_retirement_operations SET worker_token='synthetic-other-worker' WHERE id=?", operation.id());
            case "gate" -> jdbc.update("UPDATE platform_target_retirement_gates SET epoch=epoch+1 WHERE operation_id=?", operation.id());
            case "snapshot" -> jdbc.update("UPDATE platform_target_retirement_operations SET snapshot_json='{}' WHERE id=?", operation.id());
            case "operationFence" -> jdbc.update("UPDATE platform_target_retirement_operations SET fence_epoch=fence_epoch+1 WHERE id=?", operation.id());
            default -> throw new AssertionError(change);
        }
        assertThatThrownBy(() -> retention.record(context, proof(context, 19, "synthetic-runtime-topology")))
            .isInstanceOf(TargetRetirementFailure.class);
        assertThat(count("deployment_external_index_retention_receipts")).isZero();
        verify(provider, never()).delete(any(), any());
    }

    @Test void residualIndexQuotaSerializesConcurrentAdmissionsWithoutRefillingTheBudget() throws Exception {
        startRetirement(); for (int i = 0; i < 10; i++) worker.runNext(operation.id());
        store.settleRetained(reservation.fence(), settlementReadback(), "synthetic-settlement", clock.instant());
        long remainingIndexes = store.activePolicy().targets().get(targetId).budget().measured().amounts().get(Dimension.INDEXES)
            - store.require(reservation.id()).chargedDemand().amounts().get(Dimension.INDEXES);
        var nextDemand = with(manifest.demand(), Dimension.INDEXES, remainingIndexes);
        String financial = financialSnapshot();
        CountDownLatch start = new CountDownLatch(1);
        var manifests = new ArrayList<Manifest>();
        for (int i = 0; i < 8; i++) {
            var next = new Manifest("next-" + i + "-" + suffix, "synthetic-next-" + i + "-" + suffix, version.getId(), version.getConfigHash(),
                targetId, manifest.bindingFingerprint(), manifest.topologyReference(), nextDemand, manifest.prerequisiteReferences());
            store.approveManifest(next, "synthetic-reviewer", clock.instant()); manifests.add(next);
        }
        try (var pool = Executors.newFixedThreadPool(8)) {
            var results = manifests.stream().map(next -> pool.submit(() -> {
                start.await(); var context = context(next);
                try { store.reserve(context, new Reserve("new-apply", next.id()), prerequisite(context), "synthetic-owner", clock.instant()); return true; }
                catch (AdmissionFailure rejected) { assertThat(rejected.getReason()).contains("QUOTA_EXCEEDED"); return false; }
            })).toList();
            start.countDown(); int accepted = 0;
            for (var result : results) if (result.get(30, TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(1);
        }
        assertThat(store.held()).hasSize(2);
        assertThat(AdmissionRules.sum(store.held().stream().map(Reservation::chargedDemand).toList()).amounts().get(Dimension.INDEXES))
            .isEqualTo(store.activePolicy().targets().get(targetId).budget().measured().amounts().get(Dimension.INDEXES));
        assertThat(store.require(reservation.id()).chargedDemand().amounts().get(Dimension.INDEXES)).isEqualTo(policy.indexes());
        assertThat(financialSnapshot()).isEqualTo(financial);
    }

    @Test void retainedLiabilityDoesNotBecomeAnActiveManagedDependencyOrDatabaseAllocation() {
        startRetirement(); for (int i = 0; i < 10; i++) worker.runNext(operation.id());
        Reservation retained = store.settleRetained(reservation.fence(), settlementReadback(), "synthetic-settlement", clock.instant());
        assertThatThrownBy(() -> new CustomerStagingAssignmentAccessGate(store, clock).requireAvailable(customer, deploymentId, targetId))
            .hasMessageContaining("CUSTOMER_STAGING_ASSIGNMENT_EXPIRED");
        String spec = json.fingerprint("synthetic-managed-spec");
        var next = new Manifest("managed-next-" + suffix, deploymentId, version.getId(), version.getConfigHash(), targetId,
            manifest.bindingFingerprint(), manifest.topologyReference(), manifest.demand(), Set.of("managed-dependency-spec:" + spec));
        store.approveManifest(next, "synthetic-reviewer", clock.instant());
        var context = context(next);
        Reservation active = store.reserve(context, new Reserve("synthetic-next-apply", next.id()), prerequisite(context), "synthetic-owner", clock.instant());
        var requirement = new ManagedDependencyAdmissionLedger.Requirement(customer, tenant, deploymentId, targetId, version.getId(),
            manifest.bindingFingerprint(), spec, 1, 1, 1, 1, 1);
        var ledger = new ManagedDependencyAdmissionLedger(store, json, this::prerequisite, clock);
        assertThat(ledger.require(requirement, null).reservationId()).isEqualTo(active.id());
        assertThat(ledger.require(requirement, active.id()).reservationId()).isEqualTo(active.id());
        assertThatThrownBy(() -> ledger.require(requirement, retained.id())).hasMessageContaining("DEPENDENCY_ADMISSION_AMBIGUOUS_OR_MISSING");
        new CustomerAdmissionDatabaseCapacityVerifier(store).requireCovered(deploymentId, targetId, 1, 1);
        new CustomerStagingAssignmentAccessGate(store, clock).requireAvailable(customer, deploymentId, targetId);
        assertThat(store.held()).hasSize(2);
        assertThat(AdmissionRules.sum(store.held().stream().map(Reservation::chargedDemand).toList()).amounts().get(Dimension.INDEXES)).isEqualTo(2);
        assertThat(AdmissionRules.sum(store.held().stream().map(Reservation::chargedDemand).toList()).amounts().get(Dimension.INDEX_BYTES)).isEqualTo(500);
    }

    @Test void capacitySettlementRollsBackOnEventFailureAndChangedReplayIsRejected() {
        startRetirement(); for (int i = 0; i < 10; i++) worker.runNext(operation.id());
        ResourceReadback evidence = settlementReadback(); Fence from = reservation.fence();
        String before = ledgerSnapshot();
        jdbc.execute("CREATE FUNCTION synthetic_fail_retention_settlement() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.event_type='COMPUTE_RECLAIMED_INDEX_RETAINED' THEN RAISE EXCEPTION 'synthetic settlement failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER synthetic_retention_settlement_failure BEFORE INSERT ON deployment_admission_events FOR EACH ROW EXECUTE FUNCTION synthetic_fail_retention_settlement()");
        try {
            assertThatThrownBy(() -> store.settleRetained(from, evidence, "synthetic-settlement", clock.instant())).hasMessageContaining("synthetic settlement failure");
            assertThat(ledgerSnapshot()).isEqualTo(before);
            assertThat(store.require(reservation.id()).occupiesActiveSlot()).isTrue();
            assertThat(store.require(reservation.id()).chargedDemand().amounts().get(Dimension.CPU_MILLICORES)).isEqualTo(1);
        } finally {
            jdbc.execute("DROP TRIGGER synthetic_retention_settlement_failure ON deployment_admission_events");
            jdbc.execute("DROP FUNCTION synthetic_fail_retention_settlement()");
        }
        Reservation settled = store.settleRetained(from, evidence, "synthetic-settlement", clock.instant());
        assertThat(store.settleRetained(from, evidence, "synthetic-settlement", clock.instant())).isEqualTo(settled);
        var changed = new ResourceReadback(evidence.reservationId(), evidence.generation(), evidence.expectedRevision(), evidence.workerToken(),
            evidence.manifestFingerprint(), evidence.bindingFingerprint(), evidence.placement(), evidence.observedAt(), "changed-evidence",
            true, true, false, true, true, true, 0, 0L, 0L, 0L, 0L, evidence.retainedIndex());
        assertThatThrownBy(() -> store.settleRetained(from, changed, "synthetic-settlement", clock.instant()))
            .hasMessageContaining("RETENTION_SETTLEMENT_REPLAY_CHANGED");
        assertThatThrownBy(() -> store.reconcile(settled.fence(), readback(settled, null, true), true, "synthetic-release", clock.instant()))
            .hasMessageContaining("RESERVATION_ALREADY_SETTLED");
    }

    @Test void activeHumanWorkCannotBeBypassedByThePreservationPolicy() {
        startRetirement();
        doAnswer(invocation -> {
            var snapshot = runtimeSnapshot(invocation.getArgument(0));
            return new RuntimeGenerationTransitionContracts.Snapshot(snapshot.snapshotId(), snapshot.evidenceReference(), snapshot.target(),
                snapshot.observedAt(), snapshot.openConversations(), snapshot.pendingConfirmations(),
                new RuntimeGenerationTransitionContracts.Metric(1L, true, List.of()), snapshot.backgroundWork(), 0,
                true, true, false, false, false, snapshot.topologyFingerprint(), List.of(), List.of());
        }).when(runtime).snapshot(any());
        worker.runNext(operation.id());
        assertThat(count("deployment_external_index_retention_receipts")).isZero();
        assertThat(store.require(reservation.id()).chargedDemand().amounts().get(Dimension.CPU_MILLICORES)).isEqualTo(1);
        assertThat(store.require(reservation.id()).occupiesActiveSlot()).isTrue();
        verify(provider, never()).delete(any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"external-state", "retained-volume"})
    void anIndexOnlyReviewCannotReleaseOtherRetainedStorageAsZero(String kind) {
        if (kind.equals("external-state")) {
            handle("external-state", "EXTERNAL_AI_STATE_STORAGE_BINDING", "synthetic-state-" + suffix, null, "{}");
        } else {
            when(coolify.listApplicationStorages(any(), eq(application.getProviderResourceUuid()))).thenReturn(List.of(
                new CoolifyApplicationStorageSummary("synthetic-volume-" + suffix, "persistent", "synthetic-retained-volume",
                    "/synthetic/retained", "/synthetic/customer-data", true, mapper.createObjectNode())));
        }
        startRetirement(); worker.runNext(operation.id());
        var blocked = retirements.summary(operations.findById(operation.id()).orElseThrow());
        assertThat(blocked.state()).isEqualTo(TargetRetirementContracts.State.RETRYABLE_FAILURE);
        assertThat(blocked.inFlightEffect()).endsWith(":DRAIN:target");
        assertThat(blocked.errorCode()).isEqualTo("INDEX_RETENTION_OTHER_STORAGE_UNPROVEN");
        assertThat(count("deployment_external_index_retention_receipts")).isZero();
        assertThat(store.require(reservation.id()).chargedDemand().amounts().get(Dimension.DISK_BYTES)).isEqualTo(1);
        assertThat(store.require(reservation.id()).chargedDemand().amounts().get(Dimension.CPU_MILLICORES)).isEqualTo(1);
        assertThat(store.require(reservation.id()).occupiesActiveSlot()).isTrue();
        verify(provider, never()).delete(any(), any());
    }

    @Test void actual202To203UpgradePreservesPriorLedgerAndHandoffRowsWithoutSeedingPolicy() {
        String schema = "retention_upgrade_" + suffix;
        jdbc.execute("CREATE SCHEMA " + schema);
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("202").load().migrate();
        for (String table : List.of("platform_customers", "platform_customer_tenants", "platform_deployments", "deployment_target_profiles", "deployment_provider_resource_handles")) {
            String id = switch (table) {
                case "platform_customers" -> customer;
                case "platform_customer_tenants" -> tenant;
                case "platform_deployments" -> deploymentId;
                case "deployment_target_profiles" -> targetId;
                default -> binding.getId();
            };
            jdbc.update("INSERT INTO " + schema + "." + table + " SELECT * FROM public." + table + " WHERE id=?", id);
        }
        jdbc.update("INSERT INTO " + schema + ".deployment_capacity_reservations SELECT * FROM public.deployment_capacity_reservations WHERE id=?", reservation.id());
        jdbc.update("INSERT INTO " + schema + ".deployment_admission_events SELECT * FROM public.deployment_admission_events WHERE reservation_id=?", reservation.id());
        jdbc.update("INSERT INTO " + schema + ".deployment_admission_reviews SELECT * FROM public.deployment_admission_reviews WHERE kind='MANIFEST' AND record_key=?", manifest.id());
        jdbc.update("INSERT INTO " + schema + ".deployment_unused_vector_handoffs(id,deployment_id,target_profile_id,operation_key,command_fingerprint,binding_id,actor_id,approval_reference,handed_off_at,fingerprint,payload_json) VALUES (?,?,?,'synthetic-prior-handoff',?,?,'synthetic-reviewer','synthetic-approval',?,?,?)",
            "synthetic-upgrade-handoff", deploymentId, targetId, json.fingerprint("synthetic-command"), binding.getId(), time(), json.fingerprint("synthetic-receipt"), "{\"syntheticMigrationFixture\":true}");
        jdbc.update("INSERT INTO " + schema + ".provider_usage_grants(id,campaign_id,customer_id,target_profile_id,environment_name,fingerprint,payload_json,allocated_chat_attempts,allocated_total_tokens,allocated_cost_nano_usd,approved_by,approved_role,approved_at) VALUES ('synthetic-used-grant','synthetic-task',?,?,'staging',?,'{}',3,1000,1200000000,'synthetic-reviewer','PLATFORM_ADMIN',?)",
            customer, targetId, json.fingerprint("synthetic-financial-history"), time());
        var tables = List.of("deployment_capacity_reservations", "deployment_admission_events", "deployment_admission_reviews", "deployment_unused_vector_handoffs", "provider_usage_grants", "deployment_provider_resource_handles");
        var before = new TreeMap<String, String>();
        tables.forEach(table -> before.put(table, table(schema + "." + table)));
        var migration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("203").load().migrate();
        assertThat(migration.migrationsExecuted).isEqualTo(1);
        tables.forEach(table -> assertThat(table(schema + "." + table)).as(table).isEqualTo(before.get(table)));
        assertThat(count(schema + ".deployment_external_index_retention_receipts")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + schema + ".deployment_admission_reviews WHERE kind='INDEX_RETENTION'", Long.class)).isZero();
    }

    private void planWithoutIndexReview() {
        clock.advance(90);
        for (NoticeWork work : store.noticeWork(reservation.fence(), "synthetic-notices", clock.instant()))
            store.noticeDelivered(work, new NoticeDelivery(work.deliveryKey(), "synthetic-delivery", clock.instant()), "synthetic-notices", clock.instant());
        reservation = store.require(reservation.id()); clock.advance(10);
        ExpiryWork claim = store.claimExpiry(reservation.fence(), "synthetic-expiry", clock.instant());
        reservation = store.require(reservation.id());
        var request = new PlanRequest("admission-expiry:" + reservation.id() + ":" + reservation.generation(), List.of(releaseId), List.of(), claim.drainDeadline(),
            new Retention(RetentionDecision.RECLAIM, null, null, "synthetic-restore"), "synthetic-staging-expiry");
        SecurityContextHolder.clearContext();
        try {
            operation = retirements.planForAdmissionExpiry(deploymentId, targetId, reservation.id(), reservation.generation(), request);
        } finally {
            authenticate(PlatformRole.PLATFORM_ADMIN);
        }
    }

    private void startRetirement() {
        Authorization authorization = retention.approve(policy); reservation = store.require(reservation.id());
        clock.advance(90);
        for (NoticeWork work : store.noticeWork(reservation.fence(), "synthetic-notices", clock.instant()))
            store.noticeDelivered(work, new NoticeDelivery(work.deliveryKey(), "synthetic-delivery", clock.instant()), "synthetic-notices", clock.instant());
        reservation = store.require(reservation.id()); clock.advance(10);
        ExpiryWork claim = store.claimExpiry(reservation.fence(), "synthetic-expiry", clock.instant());
        reservation = store.require(reservation.id());
        var request = new PlanRequest("admission-expiry:" + reservation.id() + ":" + reservation.generation(), List.of(releaseId), List.of(), claim.drainDeadline(),
            new Retention(RetentionDecision.RECLAIM, null, null, "synthetic-restore", authorization), "synthetic-staging-expiry");
        operation = retirements.planForAdmissionExpiry(deploymentId, targetId, reservation.id(), reservation.generation(), request);
        reservation = store.handoff(claim, operation.id(), "synthetic-retirement-handoff", "synthetic-expiry", clock.instant());
        retirements.execute(operation.id(), new Command(operation.requestFingerprint()));
        clock.advance(20);
    }

    private ResourceReadback settlementReadback() {
        reservation = store.require(reservation.id());
        // Source uses real frozen inventory, effects, assignment and owned-handle absence checks.
        return resourceReadback.read(new ReadbackContext(reservation, store.reviewed("POLICY", reservation.policyId(), FleetPolicy.class), reservation.workerToken()));
    }
    private Policy policyAcceptBefore(Instant deadline) {
        return new Policy(policy.reservationId(), policy.generation(), policy.manifestFingerprint(), policy.bindingFingerprint(),
            policy.binding(), policy.indexes(), policy.indexBytes(), policy.policyReference(), policy.payerReference(), deadline);
    }
    private RuntimeGenerationTransitionContracts.Snapshot runtimeSnapshot(Context context) {
        return runtimeSnapshot(context, 19);
    }
    private RuntimeGenerationTransitionContracts.Snapshot runtimeSnapshot(Context context, long nativeFence) {
        var target = new RuntimeGenerationTransitionContracts.Target(deploymentId, targetId, releaseId);
        var current = new RuntimeGenerationTransitionContracts.Readback(target, tenant, RuntimeGenerationTransitionContracts.State.AUTHORITY_REVOKED,
            nativeFence, context.operationId(), context.requestFingerprint(), context.drainDeadline(), true, false, Instant.now());
        var zero = new RuntimeGenerationTransitionContracts.Metric(0L, true, List.of());
        return new RuntimeGenerationTransitionContracts.Snapshot("synthetic-runtime-snapshot", "synthetic-runtime-evidence", current, Instant.now(),
            zero, zero, zero, zero, 0, true, true, true, true, false, "synthetic-runtime-topology", List.of(), List.of());
    }
    private TargetRetirementRuntimeIndexGateway.IndexReadback proof(Context context, long fence, String topology) {
        return new TargetRetirementRuntimeIndexGateway.IndexReadback(new RuntimeGenerationTransitionContracts.Target(deploymentId, targetId, releaseId),
            tenant, context.operationId(), fence, topology, "milvus", "COLLECTION_PREFIX", policy.binding().scopeValue(), Map.of("synthetic_document", 7L),
            7, 0, Instant.now(), "synthetic-index-readback", false, false);
    }
    private ResourceReadback readback(Reservation r, Receipt receipt, boolean absent) {
        return new ResourceReadback(r.id(), r.generation(), r.revision(), r.workerToken(), r.manifestFingerprint(), r.bindingFingerprint(), placement,
            clock.instant(), "synthetic-inventory", true, true, absent, true, true, true, 0, 0L, 0L, 0L, 0L, receipt);
    }
    private AdmissionContext context(Manifest m) { return store.context(customer, tenant, m.deploymentId(), m.id()); }
    private PrerequisiteReadback prerequisite(AdmissionContext c) {
        return new PrerequisiteReadback(clock.instant(), clock.instant().plusSeconds(60), "synthetic-prerequisites", placement, c.target().profileFingerprint(),
            c.manifestFingerprint(), c.manifest().bindingFingerprint(), c.manifest().prerequisiteReferences(), c.policy().restoreEvidenceReference(),
            c.policy().privacyEvidenceReference(), c.grant().ownerContactEvidenceReference(), c.policy().lifecycle().noticeTransportReference());
    }
    private DeploymentProviderResourceHandleEntity handle(String label, String kind, String uuid, String release, String metadata) {
        var h = new DeploymentProviderResourceHandleEntity(); h.setId("retention-" + label + "-" + suffix); h.setDeploymentId(deploymentId);
        h.setTargetProfileId(targetId); h.setReleaseId(release); h.setProviderType(DeploymentProviderType.COOLIFY); h.setResourceKind(kind);
        h.setProviderResourceUuid(uuid); h.setProviderServerUuid(placement.serverUuid()); h.setStatus("ACTIVE"); h.setMetadataJson(metadata);
        h.setCreatedAt(clock.instant()); h.setUpdatedAt(clock.instant()); handles.saveAndFlush(h); return h;
    }
    private void authenticate(PlatformRole role) {
        var principal = new PlatformPrincipal("retention-reviewer@example.test", role, "Synthetic reviewer", "SESSION");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    private Vector vector(long n) { var values = new EnumMap<Dimension, Long>(Dimension.class); for (Dimension d : Dimension.values()) values.put(d, n); return new Vector(values); }
    private Vector with(Vector v, Dimension d, long n) { var values = new EnumMap<Dimension, Long>(v.amounts()); values.put(d, n); return new Vector(values); }
    private Timestamp time() { return Timestamp.from(clock.instant()); }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private String row(String table, String id) { return jdbc.queryForObject("SELECT row_to_json(r)::text FROM " + table + " r WHERE id=?", String.class, id); }
    private String table(String table) { return jdbc.queryForObject("SELECT COALESCE(json_agg(row_to_json(r) ORDER BY row_to_json(r)::text)::text,'[]') FROM " + table + " r", String.class); }
    private String ledgerSnapshot() { return json.fingerprint(List.of(table("deployment_capacity_reservations"), table("deployment_admission_reviews"), table("deployment_admission_events"))); }
    private String financialSnapshot() { return json.fingerprint(List.of(table("provider_usage_grants"), table("provider_usage_allocations"), table("provider_usage_allowance_events"))); }
    private String manifestSnapshot() { return jdbc.queryForObject("SELECT row_to_json(r)::text FROM deployment_admission_reviews r WHERE kind='MANIFEST' AND record_key=?", String.class, manifest.id()); }
    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
