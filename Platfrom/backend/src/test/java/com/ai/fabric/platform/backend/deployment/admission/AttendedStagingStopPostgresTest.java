package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.ReleaseGeneration;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.ResourceGeneration;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.Snapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionColdTerminationReview.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.AttendedStagingStopContracts.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class AttendedStagingStopPostgresTest extends AdmissionFixture {
    @Container static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final String STAFF = "staff";
    private static final long TARGET_EPOCH = 8;
    private static final EnumSet<Dimension> COMPUTE = EnumSet.of(Dimension.CPU_MILLICORES,
        Dimension.MEMORY_BYTES, Dimension.ENVIRONMENTS, Dimension.REPLICAS,
        Dimension.CONCURRENT_APPLIES, Dimension.CONCURRENT_BUILDS);
    private static final List<String> LEDGER_TABLES = List.of("deployment_admission_mutex",
        "deployment_capacity_reservations", "deployment_admission_reviews", "deployment_admission_grants",
        "deployment_admission_authorizations", "deployment_admission_release_bindings",
        "deployment_admission_events", "deployment_admission_activity_watermarks",
        "deployment_admission_extension_commands", "deployment_admission_notices");

    @Override @BeforeEach
    void database() {
        String schema = "attended_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl()
            + (DATABASE.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
            DATABASE.getUsername(), DATABASE.getPassword());
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        json = new AdmissionJson(mapper);
        new ResourceDatabasePopulator(
            new ClassPathResource("db/test-migration/V120_13__durable_capacity_admission_and_staging_expiry_test.sql"),
            new ClassPathResource("db/test-migration/V120_22_2__admission_allocation_authorizations_test.sql"))
            .execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        // Only the retained-state and review-kind schema deltas used by these store methods.
        jdbc.execute("ALTER TABLE deployment_admission_reviews ALTER COLUMN kind TYPE VARCHAR(32)");
        jdbc.execute("ALTER TABLE deployment_capacity_reservations DROP CONSTRAINT deployment_capacity_reservations_state_check");
        jdbc.execute("ALTER TABLE deployment_capacity_reservations ADD CONSTRAINT ck_admission_capacity_state "
            + "CHECK (state IN ('RESERVED','PROVISIONING','ACTIVE','OUTCOME_UNKNOWN','CLEANUP_FAILED','RELEASED','RETAINED_EXTERNAL'))");
        jdbc.execute("ALTER TABLE deployment_capacity_reservations DROP CONSTRAINT ck_admission_slot");
        jdbc.execute("ALTER TABLE deployment_capacity_reservations ADD CONSTRAINT ck_admission_slot "
            + "CHECK ((state IN ('RELEASED','RETAINED_EXTERNAL') AND current_slot IS NULL) "
            + "OR (state NOT IN ('RELEASED','RETAINED_EXTERNAL') AND current_slot IS NOT NULL))");
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void claimAdvancesFenceWithoutReleasingHoldsAndRejectsTheOldRevision() {
        var original = candidate();
        var plan = plan(original);
        var fenceClaims = new AtomicInteger();

        var claim = store.claimAttendedStop(plan, STAFF, () -> {
            fenceClaims.incrementAndGet();
            return TARGET_EPOCH;
        });

        var claimed = store.require(original.id());
        assertThat(claim.fence()).isEqualTo(claimed.fence());
        assertThat(claim.fence().revision()).isEqualTo(original.revision() + 1);
        assertThat(claim.fence().generation()).isEqualTo(original.generation());
        assertThat(claim.targetEpoch()).isEqualTo(TARGET_EPOCH);
        assertThat(claim.planFingerprint()).isEqualTo(json.fingerprint(plan));
        assertThat(claimed.state()).isEqualTo(State.OUTCOME_UNKNOWN);
        assertThat(claimed.expiryState()).isEqualTo(ExpiryState.CLAIMED);
        assertThat(claimed.chargedDemand()).isEqualTo(original.chargedDemand());
        assertThat(claimed.failureCode()).isEqualTo("ATTENDED_STOP_NATIVE_READBACK_PENDING");
        assertThat(claimed.evidenceReference()).isEqualTo("attended-staging-stop:" + plan.id());
        assertThatThrownBy(() -> store.outcome(original.fence(), State.CLEANUP_FAILED, "late-writer", STAFF, NOW))
            .hasMessageContaining("STALE_RESERVATION_FENCE");
        assertThat(store.claimAttendedStop(plan, STAFF, () -> {
            throw new AssertionError("duplicate claim must not acquire another target fence");
        })).isEqualTo(claim);
        assertThat(fenceClaims).hasValue(1);
        assertThat(store.require(original.id())).isEqualTo(claimed);
        assertThat(store.reviewed("ATTENDED_STOP_PLAN", plan.id(), Plan.class)).isEqualTo(plan);
        assertThat(store.reviewed("ATTENDED_STOP_CLAIM", plan.id(), Claim.class)).isEqualTo(claim);
    }

    @Test void claimRevalidatesFreezeEligibilityInsteadOfTrustingAPayload() {
        var original = candidate();
        var valid = plan(original);
        var capture = valid.review().capture();
        var confirmations = EnumSet.allOf(Confirmation.class);
        confirmations.remove(Confirmation.NEVER_ACTIVATED);
        var invalid = new ServerCapture(capture.reference(), capture.capturedAt(), capture.validUntil(),
            capture.reservation(), capture.snapshot(), capture.releaseBindings(), capture.nativePlacement(),
            capture.projectUuid(), capture.environmentUuid(), confirmations);
        var payload = new Payload(valid.review().expectation(), invalid, valid.review().acknowledgement(), NOW,
            valid.review().semantics(), valid.review().accountingDisposition());
        var plan = new Plan(valid.id(), STAFF, payload, valid.dependencyGraphId(), valid.dependencyRevision(),
            valid.dependencyFingerprint(), valid.resources(), valid.expiresAt());
        var before = ledger();

        assertThatThrownBy(() -> store.claimAttendedStop(plan, STAFF, () -> {
            throw new AssertionError("ineligible review must not claim the target fence");
        })).hasMessageContaining("COLD_REVIEW_SERVER_CONFIRMATIONS_REQUIRED");

        assertThat(ledger()).isEqualTo(before);
        assertThat(store.require(original.id()).chargedDemand()).isEqualTo(original.chargedDemand());
    }

    @Test void settlementReleasesOnlyComputeAndPreservesOriginalDemandAndImmutableHistory() {
        var original = candidate();
        var historicalReviews = rows("deployment_admission_reviews");
        var historicalEvents = rows("deployment_admission_events");
        var authorizations = rows("deployment_admission_authorizations");
        var activity = rows("deployment_admission_activity_watermarks");
        var extensions = rows("deployment_admission_extension_commands");
        var grants = rows("deployment_admission_grants");
        var bindings = rows("deployment_admission_release_bindings");
        var plan = plan(original);
        var claim = store.claimAttendedStop(plan, STAFF, () -> TARGET_EPOCH);
        var localChecks = new AtomicInteger();
        var proof = proof(plan, claim);

        var receipt = store.settleAttendedStop(claim, proof, STAFF, localChecks::incrementAndGet);

        var settled = store.require(original.id());
        assertThat(receipt.reservation()).isEqualTo(settled);
        assertThat(receipt.readback()).isEqualTo(proof);
        assertThat(receipt.claim()).isEqualTo(claim);
        assertThat(localChecks).hasValue(1);
        assertThat(settled.state()).isEqualTo(State.RETAINED_EXTERNAL);
        assertThat(settled.expiryState()).isEqualTo(ExpiryState.SETTLED_RETAINED);
        assertThat(settled.consumesCapacity()).isTrue();
        assertThat(settled.occupiesActiveSlot()).isFalse();
        assertThat(settled.workerToken()).isNull();
        assertThat(settled.nextRetryAt()).isNull();
        assertThat(settled.failureCode()).isNull();
        assertThat(settled.retirementOperationId()).isNull();
        assertThat(settled.revision()).isEqualTo(claim.fence().revision() + 1);
        assertThat(settled.demand()).isEqualTo(original.demand());
        assertThat(settled.retentionEnvelope()).isEqualTo(original.retentionEnvelope());
        assertThat(settled.retentionSettlement().fromRevision()).isEqualTo(claim.fence().revision());
        for (Dimension dimension : Dimension.values()) {
            long charged = original.chargedDemand().amounts().get(dimension);
            assertThat(charged).as("nonzero fixture: %s", dimension).isPositive();
            assertThat(settled.chargedDemand().amounts().get(dimension)).as("retained: %s", dimension)
                .isEqualTo(COMPUTE.contains(dimension) ? 0L : charged);
            assertThat(settled.retentionSettlement().releasedDemand().amounts().get(dimension))
                .as("released: %s", dimension).isEqualTo(COMPUTE.contains(dimension) ? charged : 0L);
        }
        assertThat(store.held()).containsExactly(settled);
        assertThat(jdbc.queryForObject("SELECT current_slot FROM deployment_capacity_reservations WHERE id=?",
            String.class, original.id())).isNull();
        assertThat(rows("deployment_admission_reviews")).containsAll(historicalReviews);
        assertThat(rows("deployment_admission_events")).containsAll(historicalEvents).hasSize(historicalEvents.size() + 2);
        assertThat(rows("deployment_admission_authorizations")).isEqualTo(authorizations);
        assertThat(rows("deployment_admission_activity_watermarks")).isEqualTo(activity);
        assertThat(rows("deployment_admission_extension_commands")).isEqualTo(extensions);
        assertThat(rows("deployment_admission_grants")).isEqualTo(grants);
        for (var binding : bindings) {
            var after = jdbc.queryForMap("SELECT * FROM deployment_admission_release_bindings WHERE release_id=?",
                binding.get("release_id"));
            var expected = new LinkedHashMap<>(binding);
            if ("OUTCOME_UNKNOWN".equals(binding.get("state"))) {
                expected.put("state", "COLD_STOPPED");
                expected.put("updated_at", after.get("updated_at"));
            }
            assertThat(after).isEqualTo(expected);
        }
        assertThat(store.reviewed("ATTENDED_STOP_PLAN", plan.id(), Plan.class).review().capture().reservation())
            .isEqualTo(original);
        assertThat(store.reviewed("ATTENDED_STOP_RECEIPT", plan.id(), Receipt.class)).isEqualTo(receipt);
        assertThat(jdbc.queryForList("SELECT event_type FROM deployment_admission_events ORDER BY revision", String.class))
            .containsExactly("FAILED_PILOT_CAPTURED", "ATTENDED_STOP_CLAIMED_NOT_DRAINED",
                "COMPUTE_STOPPED_STORAGE_RETAINED_NOT_DRAINED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "stale", "future", "no-observation", "plan", "fingerprint", "epoch",
        "no-resources", "resource-uuid", "resource-marker", "resource-server", "resource-destination",
        "resource-environment", "no-host-evidence"})
    void absentStaleOrMismatchedPhysicalProofLeavesEveryHoldAndLedgerRowUnchanged(String fault) {
        var original = candidate();
        var plan = plan(original);
        var claim = store.claimAttendedStop(plan, STAFF, () -> TARGET_EPOCH);
        var invalid = invalidProof(plan, claim, fault);
        var before = ledger();

        assertThatThrownBy(() -> store.settleAttendedStop(claim, invalid, STAFF, () -> {
            throw new AssertionError("invalid host proof must not reach the local recheck");
        })).hasMessageContaining("ATTENDED_STOP_HOST_PROOF_REQUIRED");

        assertThat(ledger()).isEqualTo(before);
        assertThat(store.require(original.id()).chargedDemand()).isEqualTo(original.chargedDemand());
        assertThat(store.held()).containsExactly(store.require(original.id()));
    }

    @Test void failedLocalRecheckRollsBackItsWritesAndTheEntireSettlement() {
        var original = candidate();
        var plan = plan(original);
        var claim = store.claimAttendedStop(plan, STAFF, () -> TARGET_EPOCH);
        var before = ledger();

        assertThatThrownBy(() -> store.settleAttendedStop(claim, proof(plan, claim), STAFF, () -> {
            jdbc.update("UPDATE deployment_admission_release_bindings SET state='RECHECK_IN_PROGRESS' WHERE release_id='used-release'");
            jdbc.update("INSERT INTO deployment_admission_activity_watermarks VALUES (?, 'used-release', 'HUMAN_WORK', 2, ?)",
                original.id(), Timestamp.from(NOW));
            throw new IllegalStateException("local dependency ownership changed");
        })).isInstanceOf(IllegalStateException.class).hasMessage("local dependency ownership changed");

        assertThat(ledger()).isEqualTo(before);
        assertThat(store.require(original.id()).chargedDemand()).isEqualTo(original.chargedDemand());
        assertThat(store.settleAttendedStop(claim, proof(plan, claim), STAFF, () -> { }).reservation().state())
            .isEqualTo(State.RETAINED_EXTERNAL);
    }

    @Test void exactDuplicateSettlementReturnsTheStoredReceiptWithoutRecheckingOrWriting() {
        var original = candidate();
        var plan = plan(original);
        var claim = store.claimAttendedStop(plan, STAFF, () -> TARGET_EPOCH);
        var proof = proof(plan, claim);
        var receipt = store.settleAttendedStop(claim, proof, STAFF, () -> { });
        var before = ledger();

        var replay = store.settleAttendedStop(json.read(json.write(claim), Claim.class),
            json.read(json.write(proof), StoppedReadback.class), STAFF, () -> {
                throw new AssertionError("exact duplicate settlement must not repeat the local recheck");
            });

        assertThat(replay).isEqualTo(receipt);
        assertThat(ledger()).isEqualTo(before);
    }

    private Reservation candidate() {
        approve(1000);
        store.approveManifest(manifest("manifest", "deployment"), STAFF, NOW);
        var amounts = new EnumMap<Dimension, Long>(Dimension.class);
        for (Dimension dimension : Dimension.values()) amounts.put(dimension, (long) dimension.ordinal() + 1);
        var demand = new Vector(amounts);
        var reservation = new Reservation("reservation", "customer", "tenant", "deployment", TARGET,
            Intent.CUSTOMER_STAGING, "authorization", "request", "generation", 32, State.OUTCOME_UNKNOWN, demand,
            "policy1", "manifest", "manifest-fingerprint", "binding-fingerprint", NOW.minusSeconds(172800),
            NOW.minusSeconds(80000), NOW.minusSeconds(172800), NOW.minusSeconds(86400), 0, null, null,
            ExpiryState.CLAIMED, "notice-cycle", NOW.minusSeconds(100), 1, "expiry-worker", NOW.minusSeconds(79000),
            NOW.plusSeconds(28800), null, "failure-evidence", "FAILED", "customer-context", null, null,
            with(demand, Dimension.INDEX_BYTES, 99), null);
        jdbc.update("""
            INSERT INTO deployment_capacity_reservations
              (id,customer_id,deployment_id,target_profile_id,operation_key,request_fingerprint,generation,revision,
               state,current_slot,expires_at,next_retry_at,expiry_state,payload_json,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, reservation.id(), reservation.customerId(), reservation.deploymentId(), TARGET,
            reservation.operationKey(), reservation.requestFingerprint(), reservation.generation(), reservation.revision(),
            reservation.state().name(), "customer:deployment:" + TARGET, Timestamp.from(reservation.expiresAt()),
            Timestamp.from(reservation.nextRetryAt()), reservation.expiryState().name(), json.write(reservation),
            Timestamp.from(reservation.updatedAt()));
        jdbc.update("""
            INSERT INTO deployment_admission_authorizations
              (customer_id,deployment_id,target_profile_id,operation_key,reservation_id,generation,operation,version_id,
               request_fingerprint,context_fingerprint,payload_json,created_at)
            VALUES ('customer','deployment',?,'authorization','reservation','generation','APPLY','version',
              'request','customer-context',?,?)
            """, TARGET, json.write(Map.of("originalFinancialAndAdmissionAuthority", "immutable")), Timestamp.from(NOW.minusSeconds(90000)));
        for (var binding : bindings(reservation)) {
            jdbc.update("INSERT INTO deployment_admission_release_bindings VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                binding.releaseId(), binding.reservationId(), binding.generation(), binding.authorizationKey(),
                binding.versionId(), binding.versionConfigHash(), binding.customerId(), binding.tenantId(),
                binding.deploymentId(), binding.targetProfileId(), binding.state(), Timestamp.from(NOW.minusSeconds(90000)),
                Timestamp.from(NOW.minusSeconds(80000)));
        }
        jdbc.update("INSERT INTO deployment_admission_events VALUES ('old-event','reservation',32,'FAILED_PILOT_CAPTURED',?,?,?)",
            STAFF, "failure-evidence", Timestamp.from(NOW.minusSeconds(80000)));
        jdbc.update("INSERT INTO deployment_admission_activity_watermarks VALUES ('reservation','used-release','ACCEPTED_SESSION',1,?)",
            Timestamp.from(NOW.minusSeconds(90000)));
        jdbc.update("INSERT INTO deployment_admission_extension_commands VALUES ('reservation','historical-extension','immutable',?)",
            json.write(Map.of("originalExpiry", reservation.expiresAt())));
        return reservation;
    }

    private List<ReleaseBinding> bindings(Reservation reservation) {
        return List.of(binding(reservation, "used-release", "OUTCOME_UNKNOWN"),
            binding(reservation, "no-effect-release", "NO_EFFECT"));
    }

    private ReleaseBinding binding(Reservation reservation, String release, String state) {
        return new ReleaseBinding(release, reservation.id(), reservation.generation(), release + "-authorization",
            "version", "config-hash", reservation.customerId(), reservation.tenantId(), reservation.deploymentId(), TARGET, state);
    }

    private Plan plan(Reservation reservation) {
        var resource = new ResourceGeneration("runtime", "used-release", DeploymentProviderType.COOLIFY, "APPLICATION",
            "runtime-uuid", STAGING.serverUuid(), "project", "environment", STAGING.destinationUuid(), "runtime",
            "Managed by AI Fabric deployment " + reservation.deploymentId(), "runtime-generation", "native-identity", List.of(), false);
        var snapshot = new Snapshot(reservation.customerId(), reservation.tenantId(), reservation.deploymentId(), TARGET,
            "target-generation", TARGET_EPOCH - 1,
            List.of(new ReleaseGeneration("used-release", "version", "used-generation"),
                new ReleaseGeneration("no-effect-release", "version", "no-effect-generation")), List.of(resource), List.of());
        var capture = new ServerCapture("capture", NOW, NOW.plusSeconds(300), reservation, snapshot,
            bindings(reservation), STAGING, "project", "environment", EnumSet.allOf(Confirmation.class));
        var expected = new Expectation(reservation.fence(), reservation.customerId(), reservation.tenantId(),
            reservation.deploymentId(), TARGET, reservation.workerToken());
        var ack = new Acknowledgement("review", capture.reference(), STAFF, "Stop failed pilot with acknowledged loss",
            NOW, NOW.plusSeconds(180), EnumSet.allOf(LossAcknowledgement.class));
        // A journal payload is not authority: claimAttendedStop must execute freeze itself.
        var review = new Payload(expected, capture, ack, NOW, Semantics.PHYSICAL_TERMINATION_WITH_LOSS_NOT_DRAIN,
            AccountingDisposition.PRESERVE_HISTORY_AND_ACTUAL_USAGE_NO_REFUND_OR_BUDGET_RESET);
        var nativeResource = new NativeResource(resource.resourceKind(), resource.providerResourceUuid(), resource.providerName(),
            resource.deploymentMarker(), resource.serverUuid(), resource.destinationUuid(), resource.environmentUuid());
        return new Plan("attended-stop", STAFF, review, "dependency-graph", 4, "dependency-fingerprint",
            List.of(nativeResource), NOW.plusSeconds(180));
    }

    private StoppedReadback proof(Plan plan, Claim claim) {
        return new StoppedReadback(plan.id(), claim.planFingerprint(), claim.targetEpoch(), plan.resources(), NOW, "host-stopped-proof");
    }

    private StoppedReadback invalidProof(Plan plan, Claim claim, String fault) {
        if (fault.equals("absent")) return null;
        var resource = plan.resources().getFirst();
        var resources = new ArrayList<>(plan.resources());
        if (fault.equals("no-resources")) resources.clear();
        else if (fault.startsWith("resource-")) resources.set(0, new NativeResource(resource.kind(),
            fault.equals("resource-uuid") ? "foreign" : resource.uuid(), resource.name(),
            fault.equals("resource-marker") ? "foreign" : resource.marker(),
            fault.equals("resource-server") ? "foreign" : resource.serverUuid(),
            fault.equals("resource-destination") ? "foreign" : resource.destinationUuid(),
            fault.equals("resource-environment") ? "foreign" : resource.environmentId()));
        Instant observedAt = switch (fault) {
            case "stale" -> NOW.minusSeconds(61);
            case "future" -> NOW.plusSeconds(1);
            case "no-observation" -> null;
            default -> NOW;
        };
        return new StoppedReadback(fault.equals("plan") ? "foreign" : plan.id(),
            fault.equals("fingerprint") ? "foreign" : claim.planFingerprint(),
            fault.equals("epoch") ? claim.targetEpoch() + 1 : claim.targetEpoch(), resources, observedAt,
            fault.equals("no-host-evidence") ? " " : "host-stopped-proof");
    }

    private List<Map<String, Object>> rows(String table) {
        return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1,2");
    }

    private Map<String, List<Map<String, Object>>> ledger() {
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : LEDGER_TABLES) result.put(table, rows(table));
        return result;
    }
}
