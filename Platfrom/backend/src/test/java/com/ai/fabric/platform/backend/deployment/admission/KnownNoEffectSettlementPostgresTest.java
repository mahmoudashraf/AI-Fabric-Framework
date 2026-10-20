package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.*;
import com.ai.fabric.platform.backend.audit.entity.PlatformAuditEventEntity;
import com.ai.fabric.platform.backend.audit.repository.PlatformAuditEventRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerReviewedBindingBoundary;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateService;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateEntity;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationEntity;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationRepository;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationIntegrity;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.*;
import com.ai.fabric.platform.backend.tenant.repository.*;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.KnownNoEffectSettlementContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual PostgreSQL migrations, ledger transactions and JPA consumer/history/audit mutations. No provider calls. */
@Testcontainers
class KnownNoEffectSettlementPostgresTest extends AdmissionFixture {
    @Container static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");
    private Reservation original;
    private Command command;
    private ExternalIntegrationIntegrity integrity;
    private AdmissionUnusedConsumerDetachment detachment;
    private EntityManagerFactory emf;
    private JpaTransactionManager jpaTransactions;
    private PlatformCustomerConsumerService consumerOwner;
    private TargetRetirementGateService targetGateOwner;
    private String databaseName;

    @BeforeAll static void migratedDatabaseTemplate() {
        var admin = new JdbcTemplate(new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()));
        admin.execute("CREATE DATABASE settlement_template");
        Flyway.configure().dataSource(databaseUrl("settlement_template"), DATABASE.getUsername(), DATABASE.getPassword())
            .locations("classpath:db/migration").target("206").load().migrate();
    }

    private static String databaseUrl(String name) {
        return "jdbc:postgresql://" + DATABASE.getHost() + ":" + DATABASE.getMappedPort(5432) + "/" + name;
    }

    @Override @BeforeEach void database() {
        databaseName = "settlement_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        new JdbcTemplate(admin).execute("CREATE DATABASE " + databaseName + " TEMPLATE settlement_template");
        dataSource = new DriverManagerDataSource(databaseUrl(databaseName),
            DATABASE.getUsername(), DATABASE.getPassword());
        mapper = new ObjectMapper().registerModule(new JavaTimeModule()); json = new AdmissionJson(mapper);
        jdbc = new JdbcTemplate(dataSource);
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW, ZoneOffset.UTC));
        var secrets = mock(PlatformSecretService.class);
        when(secrets.resolveSecret("PLATFORM_ARTIFACT_SIGNING_KEY")).thenReturn("synthetic-offline-fixture-signing-key");
        integrity = new ExternalIntegrationIntegrity(new CustomerOfferCodec(mapper), secrets);
        seed();
    }

    @AfterEach void close() {
        SecurityContextHolder.clearContext(); if (emf != null) emf.close();
        if (databaseName != null) new JdbcTemplate(new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()))
            .execute("DROP DATABASE " + databaseName + " WITH (FORCE)");
    }

    private void seed() {
        Timestamp now = Timestamp.from(NOW);
        jdbc.update("INSERT INTO platform_customers(id,name,slug,status,created_at,updated_at) VALUES ('customer','Fixture','settlement-fixture','ACTIVE',?,?)", now, now);
        jdbc.update("INSERT INTO platform_customer_tenants(id,customer_id,name,slug,status,created_at,updated_at) VALUES ('tenant','customer','Fixture','fixture','ACTIVE',?,?)", now, now);
        jdbc.update("INSERT INTO deployment_target_profiles(id,name,provider_type,environment_name,active,source_strategy,created_at,updated_at) VALUES (?,'Fixture','COOLIFY','staging',true,'IMAGE',?,?)", TARGET, now, now);
        jdbc.update("INSERT INTO platform_deployments(id,name,environment_name,template_id,status,customer_id,tenant_id,created_at,updated_at) VALUES ('deployment','Fixture','staging','fixture','APPLY_FAILED','customer','tenant',?,?)", now, now);
        jdbc.update("INSERT INTO platform_deployment_drafts(id,deployment_id,revision_number,status,actions_config_json,entity_config_json,routing_config_json,provider_config_json,security_config_json,created_at,updated_at) VALUES ('draft','deployment',1,'PUBLISHED','{}','{}','{}','{}','{}',?,?)", now, now);
        jdbc.update("INSERT INTO platform_deployment_versions(id,deployment_id,source_draft_id,version_label,status,config_hash,reindex_required,actions_config_json,entity_config_json,routing_config_json,provider_config_json,security_config_json,actions_artifact_yaml,entity_artifact_yaml,routing_artifact_yaml,manifest_json,published_at) VALUES ('version','deployment','draft','v1','PUBLISHED','config-hash',false,'{}','{}','{}','{}','{}','{}','{}','{}','{}',?)", now);
        approve(3); store.approveManifest(manifest("manifest", "deployment"), "staff", NOW);
        var c = context("manifest");
        original = store.reserve(c, new Reserve("original-apply", "manifest", scope("original-apply")), evidence(c, NOW), "owner", NOW);
        bind("failed-release");
        jdbc.update("UPDATE platform_deployment_releases SET status='FAILED',provisioning_status='FAILED',current_step_key='apply_failed',provisioning_details_json=? WHERE id='failed-release'", progress());
        coordinator().observe("failed-release");
        assertThat(store.releaseBinding("failed-release").state()).isEqualTo("NO_EFFECT");
        String requestHash = jdbc.queryForObject("SELECT request_fingerprint FROM deployment_admission_authorizations WHERE operation_key='original-apply'", String.class);
        command = new Command(original.fence(), "explicit-settlement", original.manifestFingerprint(), "version", "config-hash",
            original.bindingFingerprint(), List.of(new FailedRelease("failed-release", "original-apply", requestHash)), "Abandon known pre-provider failure", List.of());
        for (String kind : List.of("EXTERNAL_DOCUMENT_STORAGE_BINDING", "EXTERNAL_VECTOR_STORAGE_BINDING")) {
            jdbc.update("INSERT INTO deployment_provider_resource_handles(id,deployment_id,target_profile_id,provider_type,resource_kind,provider_resource_uuid,status,metadata_json,created_at,updated_at) VALUES (?,'deployment',?,'COOLIFY',?,?,'ACTIVE','{\"retained\":true}',?,?)", kind, TARGET, kind, "synthetic-" + kind, now, now);
        }
        jdbc.update("INSERT INTO provider_usage_grants(id,campaign_id,customer_id,target_profile_id,environment_name,fingerprint,payload_json,approved_by,approved_role,approved_at) VALUES ('grant','fixed-whole-task','customer',?,'staging',?,'{\"wholeTaskUsd\":12,\"tokens\":400000,\"queries\":50}','staff','PLATFORM_ADMIN',?)", TARGET, "f".repeat(64), now);
        jdbc.update("INSERT INTO provider_usage_allowance_events(id,event_type,subject_id,grant_id,actor_id,actor_role,payload_fingerprint,created_at) VALUES ('grant-event','GRANT_APPROVED','grant','grant','staff','PLATFORM_ADMIN',?,?)", "f".repeat(64), now);
    }

    @Test void normalKnownNoEffectSettlementPreservesAllHistoryFundingAndExternalRowsAndReplaysExactly() {
        var before = preserved();
        var receipt = settle();
        assertThat(receipt.outcome()).isEqualTo(Outcome.SETTLED_KNOWN_NO_EFFECT);
        assertThat(receipt.reclamationPerformed()).isFalse();
        assertThat(receipt.reservation().id()).isEqualTo(original.id());
        assertThat(receipt.reservation().generation()).isEqualTo(original.generation());
        assertThat(receipt.reservation().revision()).isEqualTo(original.revision() + 1);
        assertThat(receipt.reservation().state()).isEqualTo(State.RELEASED);
        assertThat(receipt.reservation().expiresAt()).isEqualTo(original.expiresAt());
        assertThat(receipt.reservation().demand()).isEqualTo(original.demand());
        assertThat(store.held()).isEmpty(); assertThat(preserved()).isEqualTo(before);
        assertThat(settle()).isEqualTo(receipt);
        assertThat(count("deployment_admission_events") - original.revision()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='NO_EFFECT_SETTLE'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='NO_EFFECT_OP'", Long.class)).isEqualTo(1);
        assertThatThrownBy(() -> coordinator().beginProvisioning("failed-release")).hasMessageContaining("FENCED");
    }

    @Test void ownerCanConstructAndSubmitTheEntireCommandThroughSupportedPlanAndSettlementServices() {
        configuration(); var api = customerApi("customer"); var before = allRelevant();
        Plan plan = api.plan("offer", "deployment", original.id(), new PlanRequest("owner-planned-settlement", "Move to a reviewed PG deployment"));
        assertThat(allRelevant()).isEqualTo(before);
        assertThat(plan.command().consumerDetachments()).hasSize(1);
        assertThat(plan.command().failedReleases()).hasSize(1);
        assertThat(json.write(plan)).doesNotContain("customerContext", "secretEnvironment", "metadata_json", "payload_json");
        Summary result = api.settle("offer", "deployment", original.id(), plan.command());
        assertThat(result.outcome()).isEqualTo(Outcome.SETTLED_KNOWN_NO_EFFECT);
        assertThat(api.settle("offer", "deployment", original.id(), plan.command())).isEqualTo(result);
    }

    @Test void completedSettlementReplaysAfterNormalPolicyRolloverWithoutReleasedReservationContinuity() {
        configuration(); var api = customerApi("customer");
        var receipt = api.settle("offer", "deployment", original.id(), command);
        var p = policy(3);
        store.approvePolicy(new FleetPolicy("policy-after-settlement", p.fleet(), p.targets(), p.lifecycle(),
            p.restoreEvidenceReference(), p.privacyEvidenceReference(), p.supportReference()), "staff", NOW);
        assertThat(store.activePolicy().id()).isEqualTo("policy-after-settlement");
        assertThat(store.activePolicy().continuations()).isEmpty();
        assertThatThrownBy(() -> store.context(store.require(original.id()))).hasMessageContaining("POLICY_CONTINUITY_REQUIRED");
        var before = allRelevant();
        assertThat(api.settle("offer", "deployment", original.id(), command)).isEqualTo(receipt);
        assertThat(store.held()).isEmpty();
        assertThat(allRelevant()).isEqualTo(before);
        assertThatThrownBy(() -> api.settle("offer", "deployment", original.id(),
            changed("different-settlement-key", command.fence(), command.consumerDetachments())))
            .hasMessageContaining("REPLAY_CHANGED");
        assertThatThrownBy(() -> store.settleKnownNoEffect("customer", "deployment", original.id(), command,
            "different-owner", r -> { }, detachment, integrity)).hasMessageContaining("REPLAY_CHANGED");
        assertThatThrownBy(() -> store.settleKnownNoEffect("customer", "deployment", original.id(), command,
            "owner", r -> { throw new AdmissionFailure(org.springframework.http.HttpStatus.FORBIDDEN, "ENTITLEMENT_REVOKED"); },
            detachment, integrity)).hasMessageContaining("ENTITLEMENT_REVOKED");
        assertThat(allRelevant()).isEqualTo(before);
    }

    @Test void foreignCustomerCannotObtainPlanOrSettleAnotherOwnersReservation() {
        var api = customerApi("foreign-customer"); var before = allRelevant();
        assertThatThrownBy(() -> api.plan("offer", "deployment", original.id(), new PlanRequest("attempt", "Foreign"))).hasMessageContaining("NOT_FOUND");
        assertThatThrownBy(() -> api.settle("offer", "deployment", original.id(), command)).hasMessageContaining("NOT_FOUND");
        assertThat(allRelevant()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"revision", "effect"})
    void anOwnerPlanIsNotAuthorityAfterRevisionOrEffectChanges(String change) {
        var api = customerApi("customer");
        Plan plan = api.plan("offer", "deployment", original.id(), new PlanRequest("planned", "Explicit cleanup"));
        if (change.equals("revision")) store.activity(original.fence(), Activity.CONFIGURATION_WRITE, "new-config", "owner", NOW);
        else store.releaseBindingState("failed-release", "OUTCOME_UNKNOWN");
        var before = allRelevant();
        assertThatThrownBy(() -> api.settle("offer", "deployment", original.id(), plan.command())).isInstanceOf(AdmissionFailure.class);
        assertThat(allRelevant()).isEqualTo(before); assertHeld();
    }

    @Test void allExplicitFailedNoEffectRetriesArePreservedInOneCompleteSettlement() {
        var c = context("manifest");
        store.reserve(c, new Reserve("attended-retry", "manifest", scope("attended-retry"), original.id()), evidence(c, NOW), "owner", NOW);
        bind("second-failed");
        jdbc.update("UPDATE platform_deployment_releases SET status='FAILED',provisioning_status='FAILED',current_step_key='apply_failed',provisioning_details_json=? WHERE id='second-failed'", progress());
        coordinator().observe("second-failed");
        Plan plan = customerApi("customer").plan("offer", "deployment", original.id(), new PlanRequest("settle-two", "Explicit cleanup"));
        assertThat(plan.command().failedReleases()).hasSize(2); var before = preserved();
        assertThat(customerApi("customer").settle("offer", "deployment", original.id(), plan.command()).failedReleaseIds()).hasSize(2);
        assertThat(preserved()).isEqualTo(before);
    }

    @Test void agedMeasurementsReservationAndGrantDoNotRequireNewCapacityToCleanUp() {
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW.plusSeconds(20000), ZoneOffset.UTC));
        assertThat(settle().reservation().state()).isEqualTo(State.RELEASED);
    }

    @Test void vendorNeutralNoEffectLedgerComparesTheExactTargetProvider() {
        jdbc.update("UPDATE deployment_target_profiles SET provider_type='RAILWAY' WHERE id=?", TARGET);
        jdbc.update("UPDATE platform_deployment_releases SET provider_type='RAILWAY' WHERE id='failed-release'");
        assertThat(settle().reservation().state()).isEqualTo(State.RELEASED);
    }

    @Test void legitimateFrozenAuthorityIntegrationAndAiStateMetadataIsVerifiedAndPreserved() throws Exception {
        frozenMetadata(); var before = preserved();
        assertThat(settle().reservation().state()).isEqualTo(State.RELEASED);
        assertThat(preserved()).isEqualTo(before);
    }

    @Test void normalConfigurationMutationsReachEpochSixAndSettlementPreservesTheEntireAvailableGate() {
        configuration();
        for (long epoch = 1; epoch <= 6; epoch++) assertThat(advanceTargetGate()).isEqualTo(epoch);
        var gate = rows("platform_target_retirement_gates"); var before = preserved();
        assertThat(count("platform_target_retirement_operations")).isZero();
        var receipt = settle();
        assertThat(receipt.reservation().state()).isEqualTo(State.RELEASED);
        assertThat(receipt.proof().inventoryFingerprints().get("targetGate")).isEqualTo(json.fingerprint(
            jdbc.queryForList("SELECT * FROM platform_target_retirement_gates WHERE deployment_id=? AND target_profile_id=?", "deployment", TARGET)));
        assertThat(rows("platform_target_retirement_gates")).isEqualTo(gate);
        assertThat(preserved()).isEqualTo(before);
        assertThat(settle()).isEqualTo(receipt);
        assertThat(rows("platform_target_retirement_gates")).isEqualTo(gate);
    }

    @ParameterizedTest @ValueSource(strings = {"RETIRING", "RETIRED", "operation", "negative-epoch", "provider-allocation", "activity"})
    void positiveConfigurationEpochDoesNotWaiveRetirementOrEffectChecks(String variant) {
        configuration(); assertThat(advanceTargetGate()).isEqualTo(1);
        switch (variant) {
            case "RETIRING", "RETIRED" -> jdbc.update("UPDATE platform_target_retirement_gates SET state=?", variant);
            case "operation" -> jdbc.update("UPDATE platform_target_retirement_gates SET operation_id='retirement-in-flight'");
            case "negative-epoch" -> jdbc.update("UPDATE platform_target_retirement_gates SET epoch=-1");
            case "provider-allocation" -> allocation("effect-after-config", "deployment", original.id());
            case "activity" -> jdbc.update("INSERT INTO deployment_admission_activity_watermarks VALUES (?,'failed-release','ACCEPTED_SESSION',1,?)", original.id(), Timestamp.from(NOW));
            default -> throw new AssertionError(variant);
        }
        var before = allRelevant();
        assertThatThrownBy(this::settle).isInstanceOf(AdmissionFailure.class);
        assertThat(allRelevant()).isEqualTo(before); assertHeld();
    }

    @Test void anotherRealConfigurationEpochBetweenEvidenceReadsRollsBackSettlementAndDetachment() {
        configuration(); assertThat(advanceTargetGate()).isEqualTo(1);
        var before = allRelevant(); var gateReads = new java.util.concurrent.atomic.AtomicInteger();
        var driftingJdbc = new JdbcTemplate(dataSource) {
            @Override public List<Map<String, Object>> queryForList(String sql, Object... args) {
                if (sql.equals("SELECT * FROM platform_target_retirement_gates WHERE deployment_id=? AND target_profile_id=? FOR UPDATE NOWAIT")
                    && gateReads.incrementAndGet() == 2) {
                    assertThat(targetGateOwner.requireAvailableForMutation("deployment", TARGET)).isEqualTo(2);
                    SharedEntityManagerCreator.createSharedEntityManager(emf).flush();
                }
                return super.queryForList(sql, args);
            }
        };
        store = new AdmissionStore(driftingJdbc, json, jpaTransactions, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(this::settle).hasMessageContaining("NO_EFFECT_SETTLEMENT_EFFECT_EVIDENCE_CHANGED");
        assertThat(gateReads.get()).isEqualTo(2);
        assertThat(allRelevant()).isEqualTo(before); assertHeld();
    }

    @Test void configurationOnlyConsumerDetachesThroughActualJpaOwnerAndKeepsDraftAppIdentityUnchanged() {
        configuration(); var app = rows("ai_workspace_installations"); var before = preserved();
        var receipt = settle();
        assertThat(receipt.consumerDetachments()).singleElement().satisfies(value -> {
            assertThat(value.fromBindingRevision()).isEqualTo(1); assertThat(value.toBindingRevision()).isEqualTo(2);
            assertThat(value.preservedDraftWorkspaces()).containsExactly(new DraftWorkspaceExpectation("draft-app", 0));
        });
        assertThat(jdbc.queryForObject("SELECT bound_deployment_id FROM platform_consumers WHERE id='consumer'", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT binding_revision FROM platform_consumers WHERE id='consumer'", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_consumer_binding_history WHERE consumer_entity_id='consumer'", Long.class)).isEqualTo(2);
        assertThat(count("platform_audit_events")).isEqualTo(1);
        assertThat(rows("ai_workspace_installations")).isEqualTo(app); assertThat(preserved()).isEqualTo(before);
        assertThat(settle()).isEqualTo(receipt);
        assertThat(count("platform_audit_events")).isEqualTo(1);
    }

    @Test void failureAfterActualJpaDetachmentAndFinalAdmissionSaveRollsBackEveryRow() {
        configuration(); var before = allRelevant();
        var reachedAfterWrites = new java.util.concurrent.atomic.AtomicBoolean();
        var failingJdbc = new JdbcTemplate(dataSource) {
            @Override public int update(String sql, Object... args) {
                if (sql.startsWith("INSERT INTO deployment_admission_reviews") && "NO_EFFECT_SETTLE".equals(args[0])) {
                    SharedEntityManagerCreator.createSharedEntityManager(emf).flush();
                    assertThat(jdbc.queryForObject("SELECT binding_revision FROM platform_consumers WHERE id='consumer'", Long.class)).isEqualTo(2);
                    assertThat(jdbc.queryForObject("SELECT bound_deployment_id FROM platform_consumers WHERE id='consumer'", String.class)).isNull();
                    assertThat(count("platform_consumer_binding_history")).isEqualTo(2);
                    assertThat(count("platform_audit_events")).isEqualTo(1);
                    assertThat(jdbc.queryForObject("SELECT state FROM deployment_capacity_reservations WHERE id=?", String.class,
                        original.id())).isEqualTo("RELEASED");
                    reachedAfterWrites.set(true);
                    throw new IllegalStateException("INJECTED_FINAL_RECEIPT_INSERT_FAILURE");
                }
                return super.update(sql, args);
            }
        };
        store = new AdmissionStore(failingJdbc, json, jpaTransactions, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(this::settle).hasMessageContaining("INJECTED_FINAL_RECEIPT_INSERT_FAILURE");
        assertThat(reachedAfterWrites).isTrue();
        assertThat(allRelevant()).isEqualTo(before);
        assertHeld();
    }

    @ParameterizedTest @ValueSource(strings = {"omitted", "stale-consumer", "stale-app", "active-app", "formerly-active", "release-assignment", "ambiguous-history"})
    void explicitConfigurationProofRejectsActivationHistoryOrRevisionDrift(String variant) {
        configuration();
        switch (variant) {
            case "omitted" -> command = changed(command.operationKey(), command.fence(), List.of());
            case "stale-consumer" -> command = changed(command.operationKey(), command.fence(), List.of(new UnusedConsumerDetachment("consumer", 0, command.consumerDetachments().getFirst().draftWorkspaces())));
            case "stale-app" -> command = changed(command.operationKey(), command.fence(), List.of(new UnusedConsumerDetachment("consumer", 1, List.of(new DraftWorkspaceExpectation("draft-app", 99)))));
            case "active-app" -> jdbc.update("UPDATE ai_workspace_installations SET status='ACTIVE' WHERE id='draft-app'");
            case "formerly-active" -> jdbc.update("UPDATE ai_workspace_installations SET activated_at=? WHERE id='draft-app'", Timestamp.from(NOW));
            case "release-assignment" -> jdbc.update("UPDATE platform_consumers SET bound_release_id='failed-release' WHERE id='consumer'");
            case "ambiguous-history" -> jdbc.update("UPDATE platform_consumer_binding_history SET from_release_id='failed-release' WHERE id='initial'");
            default -> throw new AssertionError(variant);
        }
        var before = allRelevant(); assertThatThrownBy(this::settle).isInstanceOf(AdmissionFailure.class);
        assertThat(allRelevant()).isEqualTo(before); assertHeld();
    }

    @ParameterizedTest @ValueSource(strings = {"QUEUED", "PROVISIONING", "OUTCOME_UNKNOWN", "ACTIVE", "SUPERSEDED"})
    void onlyExactNoEffectBindingCanSettle(String binding) {
        store.releaseBindingState("failed-release", binding); var before = allRelevant();
        assertThatThrownBy(this::settle).isInstanceOf(AdmissionFailure.class);
        assertThat(allRelevant()).isEqualTo(before); assertHeld();
    }

    @ParameterizedTest @ValueSource(strings = {"provider-mismatch", "native-handle", "provisioning-step", "provisioning-event", "activity", "unconsumed-authorization", "unknown-release", "activation", "authority-drift", "integration-mac", "ai-state-drift", "current-funding", "orphan-funding"})
    void relevantEffectsUncertaintyOrFrozenRecordDriftKeepCapacityFullyCharged(String variant) throws Exception {
        switch (variant) {
            case "provider-mismatch" -> jdbc.update("UPDATE platform_deployment_releases SET provider_type='RAILWAY'");
            case "native-handle" -> jdbc.update("UPDATE deployment_provider_resource_handles SET resource_kind='APPLICATION' WHERE resource_kind='EXTERNAL_DOCUMENT_STORAGE_BINDING'");
            case "provisioning-step" -> jdbc.update("UPDATE platform_deployment_releases SET provisioning_details_json=replace(provisioning_details_json,'queue_release','prepare_apply')");
            case "provisioning-event" -> { var advanced = store.activity(original.fence(), Activity.ACCEPTED_SESSION, "runtime-activity", "worker", NOW); command = changed(command.operationKey(), advanced.fence(), List.of()); }
            case "activity" -> jdbc.update("INSERT INTO deployment_admission_activity_watermarks VALUES (?,'failed-release','ACCEPTED_SESSION',1,?)", original.id(), Timestamp.from(NOW));
            case "unconsumed-authorization" -> { var c = context("manifest"); store.reserve(c, new Reserve("new-explicit-retry", "manifest", scope("new-explicit-retry"), original.id()), evidence(c, NOW), "owner", NOW); command = changed(command.operationKey(), store.require(original.id()).fence(), List.of()); }
            case "unknown-release" -> jdbc.update("INSERT INTO platform_deployment_releases(id,deployment_id,deployment_version_id,status,verification_status,provisioning_status,provisioning_target,provisioning_details_json,created_at,applied_at,updated_at) VALUES ('unbound-history','deployment','version','FAILED','SKIPPED','FAILED','COOLIFY','{}',?,?,?)", Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW));
            case "activation" -> jdbc.update("UPDATE platform_deployments SET active_version_id='version'");
            case "authority-drift" -> { frozenMetadata(); jdbc.update("UPDATE platform_runtime_authority_snapshots SET fingerprint=?", "f".repeat(64)); }
            case "integration-mac" -> { frozenMetadata(); jdbc.update("UPDATE deployment_external_integration_snapshots SET integrity_mac=?", "f".repeat(64)); }
            case "ai-state-drift" -> { frozenMetadata(); jdbc.update("UPDATE platform_deployment_releases SET ai_state_storage_fingerprint=?", "f".repeat(64)); }
            case "current-funding" -> allocation("current-allocation", "deployment", original.id());
            case "orphan-funding" -> fundingEvent("unknown", "missing-allocation", "grant", "ALLOWANCE_ALLOCATED");
            default -> throw new AssertionError(variant);
        }
        var before = allRelevant(); assertThatThrownBy(this::settle).isInstanceOf(RuntimeException.class);
        assertThat(allRelevant()).isEqualTo(before); assertHeld();
    }

    @Test void unrelatedGrantVoidAndOtherDeploymentSpendDoNotBlockOrChangeFinancialHistory() {
        allocation("other-allocation", "other-deployment", "other-reservation");
        fundingEvent("other-spend", "other-allocation", "grant", "ALLOWANCE_ALLOCATED");
        jdbc.update("UPDATE provider_usage_grants SET allocated_chat_attempts=1,allocated_total_tokens=100,allocated_cost_nano_usd=100 WHERE id='grant'");
        jdbc.update("INSERT INTO provider_usage_grants SELECT 'old-void','old-campaign',customer_id,target_profile_id,environment_name,fingerprint,payload_json,0,0,0,approved_by,approved_role,approved_at,'VOID_UNUSED' FROM provider_usage_grants WHERE id='grant'");
        fundingEvent("void-event", "old-void", "old-void", "GRANT_VOID_UNUSED");
        fundingEvent("unrelated-legacy", "clearly-unrelated", null, "LEGACY_EVENT");
        var before = preserved(); assertThat(settle().reservation().state()).isEqualTo(State.RELEASED);
        assertThat(preserved()).isEqualTo(before);
    }

    @Test void changedCommandOrStaleFenceCannotProduceASecondReceipt() {
        settle(); var before = allRelevant();
        command = changed("changed-key", command.fence(), List.of());
        assertThatThrownBy(this::settle).hasMessageContaining("REPLAY_CHANGED");
        assertThat(allRelevant()).isEqualTo(before);
    }

    @Test void concurrentIdenticalCommandsReturnTheSameSingleCommittedReceipt() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = pool.submit(() -> { await(start); return settle(); });
            var second = pool.submit(() -> { await(start); return settle(); });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='NO_EFFECT_SETTLE'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_events WHERE event_type='SETTLED_KNOWN_NO_EFFECT'", Long.class)).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"authorization", "release", "grant"})
    void reverseOrderContendedRowsFailBoundedlyAndLeaveEveryRecordUnchanged(String row) throws Exception {
        String sql = switch (row) {
            case "authorization" -> "SELECT reservation_id FROM deployment_admission_authorizations FOR UPDATE";
            case "release" -> "SELECT id FROM platform_deployment_releases FOR UPDATE";
            case "grant" -> "SELECT id FROM provider_usage_grants FOR UPDATE";
            default -> throw new AssertionError(row);
        };
        var holding = new CountDownLatch(1); var release = new CountDownLatch(1); var before = allRelevant();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var owner = pool.submit(() -> new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(tx -> {
                jdbc.queryForList(sql, String.class); holding.countDown(); await(release); return true;
            }));
            assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();
            try { var attempt = pool.submit(this::settle); assertThatThrownBy(() -> attempt.get(3, TimeUnit.SECONDS)).hasRootCauseMessage("409 CONFLICT \"NO_EFFECT_SETTLEMENT_BUSY\""); }
            finally { release.countDown(); }
            assertThat(owner.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(allRelevant()).isEqualTo(before); assertHeld();
    }

    @Test void authorizationFailureRollsBackWithoutASettlement() {
        var before = allRelevant();
        assertThatThrownBy(() -> store.settleKnownNoEffect("customer", "deployment", original.id(), command, "owner",
            r -> { throw new AdmissionFailure(org.springframework.http.HttpStatus.FORBIDDEN, "ENTITLEMENT_REVOKED"); }, null, integrity))
            .hasMessageContaining("ENTITLEMENT_REVOKED");
        assertThat(allRelevant()).isEqualTo(before); assertHeld();
    }

    @Test void concurrentApplyHoldingDeploymentGetsABoundedConflictThenPreventsSettlement() throws Exception {
        var held = new CountDownLatch(1); var commit = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var writer = pool.submit(() -> new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(tx -> {
                jdbc.queryForObject("SELECT id FROM platform_deployments WHERE id='deployment' FOR UPDATE", String.class);
                held.countDown(); await(commit);
                jdbc.update("UPDATE platform_deployment_releases SET status='PROVISIONING',provisioning_status='RUNNING' WHERE id='failed-release'");
                return true;
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            try { var attempt = pool.submit(this::settle); assertThatThrownBy(() -> attempt.get(3, TimeUnit.SECONDS)).hasRootCauseMessage("409 CONFLICT \"NO_EFFECT_SETTLEMENT_BUSY\""); }
            finally { commit.countDown(); }
            assertThat(writer.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThatThrownBy(this::settle).hasMessageContaining("TERMINAL_PRE_PROVIDER"); assertHeld();
    }

    @Test void settlementCommitWinsAndLateNormalRetryCannotBindReleasedAllocation() throws Exception {
        var holding = new CountDownLatch(1); var commit = new CountDownLatch(1);
        var blockingJdbc = new JdbcTemplate(dataSource) {
            @Override public int update(String sql, Object... args) {
                int changed = super.update(sql, args);
                if (sql.startsWith("UPDATE deployment_capacity_reservations SET")) { holding.countDown(); await(commit); }
                return changed;
            }
        };
        var settling = new AdmissionStore(blockingJdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW, ZoneOffset.UTC));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> settling.settleKnownNoEffect("customer", "deployment", original.id(), command, "owner", r -> { }, null, integrity));
            assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();
            var retry = pool.submit(() -> { var c = context("manifest"); return store.reserve(c,
                new Reserve("attended-retry", "manifest", scope("attended-retry"), original.id()), evidence(c, NOW), "owner", NOW); });
            commit.countDown(); assertThat(first.get(10, TimeUnit.SECONDS).reservation().state()).isEqualTo(State.RELEASED);
            assertThatThrownBy(() -> retry.get(10, TimeUnit.SECONDS)).hasRootCauseInstanceOf(AdmissionFailure.class);
        }
        assertThat(count("deployment_admission_authorizations")).isEqualTo(1);
    }

    private Receipt settle() { return store.settleKnownNoEffect("customer", "deployment", original.id(), command, "owner", r -> { }, detachment, integrity); }
    private KnownNoEffectSettlementController customerApi(String customerId) {
        var entitlements = mock(com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService.class);
        when(entitlements.requireCustomerActor()).thenReturn(new com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService.CustomerActor(customerId, "owner"));
        var boundary = mock(com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary.class);
        when(boundary.require(any(), eq(TARGET), anyString(), eq(CustomerOfferContracts.Operation.TARGET_RETIRE), anyString(), anyMap(), isNull(), isNull(), isNull()))
            .thenAnswer(call -> new CustomerOfferContracts.Authorization(scope("cleanup"), null));
        var deployments = mock(DeploymentRepository.class); when(deployments.findById("deployment")).thenReturn(Optional.of(deployment()));
        var versions = mock(DeploymentVersionRepository.class); when(versions.findById("version")).thenReturn(Optional.of(version()));
        return new KnownNoEffectSettlementController(new KnownNoEffectSettlementService(store, entitlements, boundary, deployments, versions, detachment, integrity));
    }
    private Command changed(String key, Fence fence, List<UnusedConsumerDetachment> consumers) {
        return new Command(fence, key, command.manifestFingerprint(), command.versionId(), command.versionConfigHash(),
            command.bindingFingerprint(), command.failedReleases(), command.reason(), consumers);
    }
    private void assertHeld() { assertThat(store.require(original.id()).occupiesActiveSlot()).isTrue(); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='NO_EFFECT_SETTLE'", Long.class)).isZero(); }
    private static void await(CountDownLatch latch) { try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("timeout"); } catch (InterruptedException e) { throw new IllegalStateException(e); } }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private String rows(String table) { return jdbc.queryForObject("SELECT coalesce(json_agg(row_to_json(t) ORDER BY row_to_json(t)::text),'[]'::json)::text FROM " + table + " t", String.class); }
    private Map<String, String> preserved() {
        var result = new TreeMap<String, String>();
        for (String table : List.of("platform_deployment_releases", "deployment_admission_authorizations", "deployment_admission_release_bindings",
            "provider_usage_grants", "provider_usage_allocations", "provider_usage_allowance_events", "deployment_provider_resource_handles",
            "platform_runtime_authority_snapshots", "deployment_external_integration_snapshots", "platform_customer_entitlement_commands")) result.put(table, rows(table));
        return result;
    }
    private Map<String, String> allRelevant() { var result = new TreeMap<>(preserved()); for (String table : List.of("deployment_capacity_reservations", "deployment_admission_reviews", "deployment_admission_events", "platform_consumers", "platform_consumer_binding_history", "ai_workspace_installations", "platform_audit_events", "platform_target_retirement_gates", "platform_target_retirement_operations")) result.put(table, rows(table)); return result; }
    private long advanceTargetGate() { return new TransactionTemplate(jpaTransactions).execute(tx -> targetGateOwner.requireAvailableForMutation("deployment", TARGET)); }

    private CustomerOfferContracts.AdmissionContext scope(String key) {
        return new CustomerOfferContracts.AdmissionContext("customer", "tenant", "deployment", null, TARGET,
            CustomerOfferContracts.TargetIntent.CUSTOMER_STAGING, "offer", "offer-fingerprint", 1, "placement-proof",
            CustomerOfferContracts.Operation.APPLY, key, new CustomerOfferContracts.ReviewedLimits("measurement-proof", 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1),
            new CustomerOfferContracts.ReviewedTopology(1, 1, 1, 1, 1, 1, 1, 1));
    }
    private String progress() { return "{\"progress\":{\"currentStepKey\":\"apply_failed\",\"steps\":[{\"key\":\"queue_release\",\"status\":\"COMPLETED\"},{\"key\":\"preflight_verification\",\"status\":\"COMPLETED\"},{\"key\":\"apply_failed\",\"status\":\"FAILED\"}]}}"; }
    private DeploymentEntity deployment() { var d = new DeploymentEntity(); d.setId("deployment"); d.setCustomerId("customer"); d.setTenantId("tenant"); return d; }
    private DeploymentVersionEntity version() { var v = new DeploymentVersionEntity(); v.setId("version"); v.setDeploymentId("deployment"); v.setStatus("PUBLISHED"); v.setConfigHash("config-hash"); return v; }
    private DeploymentReleaseEntity release(String id) {
        return jdbc.query("SELECT * FROM platform_deployment_releases WHERE id=?", (rs, row) -> {
            var r = new DeploymentReleaseEntity(); r.setId(id); r.setDeploymentId("deployment"); r.setDeploymentVersionId("version");
            r.setTargetProfileId(TARGET); r.setStatus(rs.getString("status")); r.setProvisioningStatus(rs.getString("provisioning_status")); return r;
        }, id).getFirst();
    }
    private AdmissionReleaseCoordinator coordinator() {
        var associations = mock(com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository.class);
        var releases = mock(DeploymentReleaseRepository.class); var versions = mock(DeploymentVersionRepository.class);
        when(associations.existsById("deployment")).thenReturn(true); when(releases.findById(anyString())).thenAnswer(call -> Optional.of(release(call.getArgument(0))));
        when(versions.findById("version")).thenReturn(Optional.of(version()));
        return new AdmissionReleaseCoordinator(store, associations, releases, versions, c -> evidence(c, NOW),
            mock(AdmissionResourceReadbackSource.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }
    private void bind(String id) {
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(tx -> {
            jdbc.queryForObject("SELECT id FROM platform_deployments WHERE id='deployment' FOR UPDATE", String.class);
            jdbc.update("INSERT INTO platform_deployment_releases(id,deployment_id,deployment_version_id,target_profile_id,provider_type,status,verification_status,provisioning_status,provisioning_target,provisioning_details_json,created_at,applied_at,updated_at) VALUES (?,'deployment','version',?,'COOLIFY','APPLY_REQUESTED','PENDING','QUEUED','COOLIFY','{}',?,?,?)", id, TARGET, Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW));
            store.bindRelease(deployment(), version(), release(id));
        });
    }
    private void fundingEvent(String id, String subject, String grant, String event) { jdbc.update("INSERT INTO provider_usage_allowance_events(id,event_type,subject_id,grant_id,actor_id,actor_role,payload_fingerprint,created_at) VALUES (?,?,?,?,'fixture','PLATFORM_ADMIN',?,?)", id, event, subject, grant, "f".repeat(64), Timestamp.from(NOW)); }
    private void allocation(String id, String deployment, String reservation) {
        String payload = json.write(Map.of("approvalId", id, "grantId", "grant", "scope", Map.of("deploymentId", deployment), "initialAdmission", Map.of("reservationId", reservation)));
        jdbc.update("INSERT INTO provider_usage_allocations(approval_id,grant_id,customer_id,deployment_id,target_profile_id,environment_name,approval_fingerprint,runtime_allocation_fingerprint,payload_json,initialization_state,created_at) VALUES (?,'grant','customer',? ,?,'staging',?,?,?,'UNINITIALIZED',?)", id, deployment, TARGET, "a".repeat(64), "b".repeat(64), payload, Timestamp.from(NOW));
    }

    private void frozenMetadata() throws Exception {
        String authority = json.write(Map.of("customerId", "customer", "tenantId", "tenant", "deploymentId", "deployment", "targetProfileId", TARGET, "versionId", "version", "versionConfigHash", "config-hash", "generation", 1));
        String fingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(authority.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbc.update("INSERT INTO platform_runtime_target_authorities(id,deployment_id,target_profile_id,customer_id,tenant_id,generation,state,secret_revisions_json,secret_integrity_json,created_at,updated_at) VALUES ('authority','deployment',?,'customer','tenant',1,'ACTIVE','{}','{}',?,?)", TARGET, Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("INSERT INTO platform_runtime_authority_snapshots(id,deployment_id,target_profile_id,version_id,release_id,fingerprint,binding_json,created_at) VALUES ('frozen','deployment',?,'version','failed-release',?,?,?)", TARGET, fingerprint, authority, Timestamp.from(NOW));
        jdbc.update("UPDATE platform_deployment_releases SET runtime_authority_snapshot_id='frozen',runtime_authority_fingerprint=? WHERE id='failed-release'", fingerprint);
        var snapshot = new com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.ReleaseSnapshot("failed-release", fingerprint, List.of());
        jdbc.update("INSERT INTO deployment_external_integration_snapshots VALUES ('failed-release','deployment','version',?,?,?,?)", TARGET, json.write(snapshot), integrity.seal(snapshot), Timestamp.from(NOW));
        // Use the real owning subsystem's freeze/serializer, not a self-consistent admission-only fixture hash.
        var targets = mock(DeploymentTargetProfileRepository.class);
        var target = new com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity();
        target.setId(TARGET); target.setProviderType(com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType.COOLIFY);
        target.setEnvironmentName("staging"); target.setActive(true); target.setProviderConfigJson("{}");
        target.setResourceDefaultsJson("{}"); target.setNetworkPolicyJson("{}");
        when(targets.findById(TARGET)).thenReturn(Optional.of(target));
        var stateOwner = new com.ai.fabric.platform.backend.deployment.service.DeploymentAiStateStorageBindingService(
            mock(DeploymentRepository.class), mock(DeploymentVersionRepository.class), targets,
            mock(DeploymentProviderResourceHandleRepository.class), mock(PlatformSecretService.class),
            mock(TargetRetirementGateService.class), mock(PlatformAuditService.class),
            mock(com.ai.fabric.platform.backend.deployment.service.DeploymentAiStateStorageProbe.class), jdbc, mapper);
        var version = version(); version.setProviderConfigJson("{\"aiStateStorageMode\":\"MANAGED_POSTGRES\"}");
        var frozenRelease = release("failed-release"); stateOwner.freeze(deployment(), version, frozenRelease);
        jdbc.update("UPDATE platform_deployment_releases SET ai_state_storage_snapshot_json=?,ai_state_storage_fingerprint=? WHERE id='failed-release'",
            frozenRelease.getAiStateStorageSnapshotJson(), frozenRelease.getAiStateStorageFingerprint());
    }

    private void configuration() {
        Timestamp now = Timestamp.from(NOW);
        jdbc.update("INSERT INTO platform_users(id,email,display_name,password_hash,role,status,customer_id,created_at,updated_at) VALUES ('owner','owner@example.invalid','Fixture','unused','CUSTOMER_ADMIN','ACTIVE','customer',?,?)", now, now);
        jdbc.update("INSERT INTO platform_customer_offers VALUES ('offer','{}','offer-fingerprint','review','staff',?)", now);
        jdbc.update("INSERT INTO platform_customer_offer_entitlements(id,customer_id,offer_id,owner_user_id,invitation_reference,status,revision,expires_at,created_at,updated_at) VALUES ('entitlement','customer','offer','owner','invite','ACTIVE',1,?,?,?)", Timestamp.from(NOW.plusSeconds(100000)), now, now);
        jdbc.update("INSERT INTO platform_customer_offer_deployments(deployment_id,customer_id,tenant_id,offer_id,offer_fingerprint,authorization_entitlement_revision,operation_key,created_at,bootstrap_target_profile_id) VALUES ('deployment','customer','tenant','offer','offer-fingerprint',1,'bootstrap',?,?)", now, TARGET);
        var factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(dataSource); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setManagedTypes(PersistenceManagedTypes.of(PlatformConsumerEntity.class.getName(), PlatformConsumerBindingHistoryEntity.class.getName(), AIWorkspaceInstallationEntity.class.getName(), PlatformAuditEventEntity.class.getName(), DeploymentEntity.class.getName(), TargetRetirementGateEntity.class.getName(), TargetRetirementOperationEntity.class.getName()));
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet(); emf = factory.getObject(); var entities = SharedEntityManagerCreator.createSharedEntityManager(emf);
        jpaTransactions = new JpaTransactionManager(emf); var repositories = new JpaRepositoryFactory(entities);
        targetGateOwner = new TargetRetirementGateService(repositories.getRepository(DeploymentRepository.class),
            repositories.getRepository(TargetRetirementGateRepository.class), repositories.getRepository(TargetRetirementOperationRepository.class));
        var consumers = repositories.getRepository(PlatformConsumerRepository.class); var histories = repositories.getRepository(PlatformConsumerBindingHistoryRepository.class);
        var apps = repositories.getRepository(AIWorkspaceInstallationRepository.class); var customers = mock(PlatformCustomerRepository.class);
        var customer = new PlatformCustomerEntity(); customer.setId("customer"); customer.setStatus("ACTIVE"); when(customers.findById("customer")).thenReturn(Optional.of(customer));
        var access = mock(PlatformCustomerAccessService.class);
        consumerOwner = new PlatformCustomerConsumerService(consumers, histories, customers, mock(DeploymentRepository.class), mock(DeploymentReleaseRepository.class), access,
            new PlatformAuditService(repositories.getRepository(PlatformAuditEventRepository.class), mapper), new AIWorkspaceBindingGuard(apps, mock(AIWorkspaceReadinessService.class)), apps,
            mock(AIWorkspaceDeploymentSecurityProjectionService.class), mapper, mock(TargetRetirementGateService.class), mock(DeploymentTargetProfileRepository.class), mock(CustomerStagingAssignmentAccessGate.class), mock(CustomerReviewedBindingBoundary.class));
        ReflectionTestUtils.setField(consumerOwner, "unusedCancellationEntityManager", entities);
        detachment = new AdmissionUnusedConsumerDetachment(jdbc, json, consumerOwner);
        store = new AdmissionStore(jdbc, json, jpaTransactions, Clock.fixed(NOW, ZoneOffset.UTC));
        new TransactionTemplate(jpaTransactions).executeWithoutResult(tx -> {
            var consumer = new PlatformConsumerEntity(); consumer.setId("consumer"); consumer.setConsumerId("synthetic-config"); consumer.setCustomerId("customer"); consumer.setDisplayName("Fixture"); consumer.setStatus("ACTIVE"); consumer.setBoundDeploymentId("deployment"); consumer.setBoundTargetProfileId(TARGET); consumer.setBindingRevision(1); consumer.setCreatedAt(NOW); consumer.setUpdatedAt(NOW); consumers.saveAndFlush(consumer);
            var history = new PlatformConsumerBindingHistoryEntity(); history.setId("initial"); history.setConsumerEntityId("consumer"); history.setConsumerId("synthetic-config"); history.setCustomerId("customer"); history.setToDeploymentId("deployment"); history.setToTargetProfileId(TARGET); history.setFromBindingRevision(0L); history.setToBindingRevision(1L); history.setOperationKey("create-consumer"); history.setActorId("owner"); history.setActorRole("CUSTOMER_ADMIN"); history.setCreatedAt(NOW); histories.saveAndFlush(history);
            apps.saveAndFlush(AdmissionUnusedConsumerDetachmentTest.draftApp("draft-app"));
        });
        jdbc.update("INSERT INTO platform_customer_entitlement_commands VALUES ('creation','entitlement','create-consumer','request','CREATE_STAGING_CONSUMER','owner',?,?)", "{\"consumerId\":\"synthetic-config\",\"customerId\":\"customer\",\"boundDeploymentId\":\"deployment\",\"boundTargetProfileId\":\"" + TARGET + "\",\"boundReleaseId\":null,\"bindingRevision\":1}", now);
        command = changed(command.operationKey(), command.fence(), List.of(new UnusedConsumerDetachment("consumer", 1, List.of(new DraftWorkspaceExpectation("draft-app", 0)))));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new PlatformPrincipal("owner@example.invalid", PlatformRole.CUSTOMER_ADMIN, "Fixture", "SESSION"), "fixture", List.of()));
    }
}
