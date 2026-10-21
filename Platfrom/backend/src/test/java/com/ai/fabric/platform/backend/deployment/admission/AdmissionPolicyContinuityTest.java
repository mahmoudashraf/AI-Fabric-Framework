package com.ai.fabric.platform.backend.deployment.admission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;

class AdmissionPolicyContinuityTest extends AdmissionFixture {
    FleetPolicy originalPolicy;
    Reservation original;
    Instant current;

    @BeforeEach void initialPolicyAndAllocation() {
        current = NOW;
        var template = policy(3);
        var fleet = new Budget(vector(3), vector(0), vector(0), "offer-compatibility-anchor", NOW.minusSeconds(20), NOW.plusSeconds(30));
        var target = new Budget(vector(3), vector(0), vector(0), "actual-target-report-original", NOW.minusSeconds(20), NOW.plusSeconds(30));
        originalPolicy = new FleetPolicy("policy1", fleet, Map.of(TARGET,
            new TargetBudget(TARGET, Intent.CUSTOMER_STAGING, STAGING, "profile-fingerprint", target)), template.lifecycle(),
            template.restoreEvidenceReference(), template.privacyEvidenceReference(), template.supportReference());
        store.approvePolicy(originalPolicy, "staff", NOW); store.approveGrant(grant(3), "staff", NOW);
        var templateManifest = manifest("manifest1", "deployment1");
        var references = new java.util.HashSet<>(templateManifest.prerequisiteReferences()); references.add("policy:policy1");
        store.approveManifest(new Manifest(templateManifest.id(), templateManifest.deploymentId(), templateManifest.versionId(),
            templateManifest.versionConfigHash(), TARGET, templateManifest.bindingFingerprint(), templateManifest.topologyReference(),
            templateManifest.demand(), references, templateManifest.customerPrerequisiteReferences()), "staff", NOW);
        original = reserve("manifest1", "original-key");
    }

    void at(Instant instant) {
        current = instant;
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(instant, ZoneOffset.UTC));
    }

    static FleetPolicy continuation(AdmissionStore store, AdmissionJson json, String id, List<Reservation> reservations,
                                    Instant measuredAt, Instant until) {
        FleetPolicy original = store.reviewed("POLICY", reservations.getFirst().policyId(), FleetPolicy.class);
        var fleet = new Budget(original.fleet().measured(), original.fleet().baseline(), original.fleet().headroom(),
            original.fleet().measurementReference(), measuredAt, until);
        var targets = new java.util.TreeMap<String, TargetBudget>();
        original.targets().forEach((key, target) -> targets.put(key, new TargetBudget(key, target.intent(), target.placement(),
            target.profileFingerprint(), new Budget(target.budget().measured(), target.budget().baseline(), target.budget().headroom(),
                "actual-target-report:" + id + ":" + key, measuredAt, until))));
        return new FleetPolicy(id, fleet, targets, original.lifecycle(), original.restoreEvidenceReference(), original.privacyEvidenceReference(),
            original.supportReference(), List.of(new PolicyContinuation(original.id(), json.fingerprint(original),
                reservations.stream().map(Reservation::fence).toList())));
    }

    static void publishFixture(AdmissionStore store, JdbcTemplate jdbc, FleetPolicy policy, Instant now) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS deployment_target_profiles (id VARCHAR(64) PRIMARY KEY)");
        policy.targets().keySet().forEach(id -> {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM deployment_target_profiles WHERE id=?", Long.class, id) == 0)
                jdbc.update("INSERT INTO deployment_target_profiles(id) VALUES (?)", id);
        });
        // Synthetic local placement receipts isolate the ledger; the production verifier has separate tests.
        store.approvePolicy(policy, "staff", now, () -> policy.targets().keySet().stream()
            .collect(java.util.stream.Collectors.toMap(id -> id, id -> "fixture-placement:" + id)));
    }

    FleetPolicy publish(String id, Reservation reservation, Instant until) {
        var policy = continuation(store, json, id, List.of(reservation), current, until);
        publishFixture(store, jdbc, policy, current); return policy;
    }

    @Test void optionalCoveragePreservesHistoricalPolicyAndContextFingerprints() {
        var node = mapper.valueToTree(originalPolicy);
        assertThat(node.has("continuations")).isFalse();
        assertThat(json.fingerprint(json.read(json.write(originalPolicy), FleetPolicy.class))).isEqualTo(json.fingerprint(originalPolicy));
        assertThat(mapper.valueToTree(context("manifest1")).has("policyAuthority")).isFalse();
        var legacy = Map.of("id", originalPolicy.id(), "fleet", originalPolicy.fleet(), "targets", originalPolicy.targets(),
            "lifecycle", originalPolicy.lifecycle(), "restoreEvidenceReference", originalPolicy.restoreEvidenceReference(),
            "privacyEvidenceReference", originalPolicy.privacyEvidenceReference(), "supportReference", originalPolicy.supportReference());
        assertThat(json.fingerprint(originalPolicy)).isEqualTo(json.fingerprint(legacy));
        var context = context("manifest1");
        assertThat(json.fingerprint(context)).isEqualTo(json.fingerprint(Map.of("customerId", context.customerId(), "tenantId", context.tenantId(),
            "deploymentId", context.deploymentId(), "policy", context.policy(), "grant", context.grant(), "manifest", context.manifest(),
            "manifestFingerprint", context.manifestFingerprint(), "target", context.target())));
    }

    @Test void expiredOriginalPolicyContinuesOnlyThroughFreshExactAppendOnlyRelationWithoutMutation() {
        String originalBytes = jdbc.queryForObject("SELECT payload_json FROM deployment_capacity_reservations WHERE id=?", String.class, original.id());
        String policyBytes = jdbc.queryForObject("SELECT payload_json FROM deployment_admission_reviews WHERE kind='POLICY' AND record_key='policy1'", String.class);
        at(NOW.plusSeconds(31));
        assertThatThrownBy(() -> AdmissionRules.policy(originalPolicy, current)).hasMessageContaining("FRESH_MEASURED_CAPACITY_REQUIRED");
        FleetPolicy fresh = publish("policy2", original, NOW.plusSeconds(300));
        var context = store.context(original);
        assertThat(context.policy()).isEqualTo(fresh);
        assertThat(store.authorizedPolicyReferences(context)).containsExactlyInAnyOrder("policy:policy1", "policy:policy2");
        AdmissionRules.prerequisites(context, evidence(context, current), current);
        assertThat(store.manifestPolicy("deployment1", TARGET, "version")).isEqualTo(originalPolicy);
        assertThat(store.require(original.id())).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT payload_json FROM deployment_capacity_reservations WHERE id=?", String.class, original.id())).isEqualTo(originalBytes);
        assertThat(jdbc.queryForObject("SELECT payload_json FROM deployment_admission_reviews WHERE kind='POLICY' AND record_key='policy1'", String.class)).isEqualTo(policyBytes);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_events", Long.class)).isEqualTo(1);
        assertThat(store.held()).containsExactly(original);
    }

    @ParameterizedTest @EnumSource(value=Dimension.class, names={"ENVIRONMENTS","REPLICAS","DATABASES","INDEXES","DEPENDENCIES","CONCURRENT_APPLIES"})
    void reviewedLogicalCeilingIncreasePreservesTheUnknownHeldAllocationAndItsHistory(Dimension dimension) {
        var held = store.outcome(original.fence(), State.OUTCOME_UNKNOWN, "retain-unknown-effects", "worker", NOW);
        String allocationBytes = jdbc.queryForObject("SELECT payload_json FROM deployment_capacity_reservations WHERE id=?", String.class, held.id());
        String policyBytes = jdbc.queryForObject("SELECT payload_json FROM deployment_admission_reviews WHERE kind='POLICY' AND record_key='policy1'", String.class);
        var events = jdbc.queryForList("SELECT * FROM deployment_admission_events ORDER BY revision");
        var grant = store.customerGrant(held.customerId());
        at(NOW.plusSeconds(31));
        var candidate = continuation(store, json, "policy2", List.of(held), current, NOW.plusSeconds(300));
        for (String scope : List.of("fleet", "target")) candidate = budgetAmount(candidate, scope, "measured", dimension, 4);
        publishFixture(store, jdbc, candidate, current);
        assertThat(store.activePolicy()).isEqualTo(candidate);
        assertThat(store.context(held).policy()).isEqualTo(candidate);
        assertThat(store.manifestPolicy(held.deploymentId(), TARGET, "version")).isEqualTo(originalPolicy);
        assertThat(store.require(held.id())).isEqualTo(held);
        assertThat(store.held()).containsExactly(held);
        assertThat(store.customerGrant(held.customerId())).isEqualTo(grant);
        assertThat(jdbc.queryForObject("SELECT payload_json FROM deployment_capacity_reservations WHERE id=?", String.class, held.id())).isEqualTo(allocationBytes);
        assertThat(jdbc.queryForObject("SELECT payload_json FROM deployment_admission_reviews WHERE kind='POLICY' AND record_key='policy1'", String.class)).isEqualTo(policyBytes);
        assertThat(jdbc.queryForList("SELECT * FROM deployment_admission_events ORDER BY revision")).isEqualTo(events);
    }

    @Test void laterFreshMeasurementRetainsIncreasedLogicalCeilingsAndStillResolvesTheOriginalPolicy() {
        at(NOW.plusSeconds(31));
        var increased = logicalCeilings(continuation(store, json, "policy2", List.of(original), current, NOW.plusSeconds(100)), 4);
        publishFixture(store, jdbc, increased, current);
        String allocationBytes = jdbc.queryForObject("SELECT payload_json FROM deployment_capacity_reservations WHERE id=?", String.class, original.id());
        at(NOW.plusSeconds(40));
        var fresh = logicalCeilings(continuation(store, json, "policy3", List.of(original), current, NOW.plusSeconds(300)), 4);
        publishFixture(store, jdbc, fresh, current);
        assertThat(store.context(original).policy()).isEqualTo(fresh);
        assertThat(store.authorizedPolicyReferences(store.context(original)))
            .containsExactlyInAnyOrder("policy:policy1", "policy:policy2", "policy:policy3");
        assertThat(store.manifestPolicy(original.deploymentId(), TARGET, "version")).isEqualTo(originalPolicy);
        assertThat(store.require(original.id())).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT payload_json FROM deployment_capacity_reservations WHERE id=?", String.class, original.id())).isEqualTo(allocationBytes);
    }

    @ParameterizedTest @EnumSource(value=Dimension.class, names={"ENVIRONMENTS","REPLICAS","DATABASES","INDEXES","DEPENDENCIES","CONCURRENT_APPLIES"})
    void logicalCeilingsCannotFallBackToOriginalValuesAfterAnIncrease(Dimension dimension) {
        at(NOW.plusSeconds(31));
        var increased = logicalCeilings(continuation(store, json, "policy2", List.of(original), current, NOW.plusSeconds(100)), 4);
        publishFixture(store, jdbc, increased, current);
        var relations = jdbc.queryForList("SELECT payload_json FROM deployment_admission_reviews WHERE kind='POLICY_CONT' ORDER BY record_key", String.class);
        at(NOW.plusSeconds(40));
        var fresh = logicalCeilings(continuation(store, json, "policy3", List.of(original), current, NOW.plusSeconds(300)), 4);
        for (String scope : List.of("fleet", "target")) {
            var decreased = budgetAmount(fresh, scope, "measured", dimension, 3);
            assertThatThrownBy(() -> publishFixture(store, jdbc, decreased, current)).hasMessageContaining("POLICY_CONTINUITY_INCOMPATIBLE");
            assertThat(store.activePolicy()).isEqualTo(increased);
            assertThat(store.require(original.id())).isEqualTo(original);
            assertThat(jdbc.queryForList("SELECT payload_json FROM deployment_admission_reviews WHERE kind='POLICY_CONT' ORDER BY record_key", String.class)).isEqualTo(relations);
        }
    }

    @ParameterizedTest @EnumSource(Dimension.class)
    void noMeasuredCeilingMayDecreaseInEitherFleetOrTargetContinuity(Dimension dimension) {
        at(NOW.plusSeconds(31));
        var candidate = continuation(store, json, "policy2", List.of(original), current, NOW.plusSeconds(300));
        for (String scope : List.of("fleet", "target")) {
            var decreased = budgetAmount(candidate, scope, "measured", dimension, 2);
            assertThatThrownBy(() -> publishFixture(store, jdbc, decreased, current)).hasMessageContaining("POLICY_CONTINUITY_INCOMPATIBLE");
            assertThat(store.activePolicy()).isEqualTo(originalPolicy);
            assertThat(store.require(original.id())).isEqualTo(original);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='POLICY_CONT'", Long.class)).isZero();
    }

    @ParameterizedTest @EnumSource(value=Dimension.class, names={"CPU_MILLICORES","MEMORY_BYTES","DISK_BYTES","INDEX_BYTES",
        "CONCURRENT_BUILDS","API_REQUESTS_PER_WINDOW","PROVIDER_TOKENS_PER_WINDOW","PROVIDER_SPEND_MICROS_PER_WINDOW"})
    void physicalFinancialApiAndBuildCeilingsCannotIncreaseWithLogicalSlots(Dimension dimension) {
        at(NOW.plusSeconds(31));
        var candidate = logicalCeilings(continuation(store, json, "policy2", List.of(original), current, NOW.plusSeconds(300)), 4);
        for (String scope : List.of("fleet", "target")) {
            var enlarged = budgetAmount(candidate, scope, "measured", dimension, 4);
            assertThatThrownBy(() -> publishFixture(store, jdbc, enlarged, current)).hasMessageContaining("POLICY_CONTINUITY_INCOMPATIBLE");
            assertThat(store.activePolicy()).isEqualTo(originalPolicy);
            assertThat(store.require(original.id())).isEqualTo(original);
        }
    }

    @Test void zeroBuildCeilingsRemainZeroWhenAllSixLogicalCeilingsIncrease() {
        at(NOW.plusSeconds(31));
        var before = originalPolicy;
        var after = logicalCeilings(continuation(store, json, "policy2", List.of(original), current, NOW.plusSeconds(300)), 4);
        for (String scope : List.of("fleet", "target")) {
            before = budgetAmount(before, scope, "measured", Dimension.CONCURRENT_BUILDS, 0);
            after = budgetAmount(after, scope, "measured", Dimension.CONCURRENT_BUILDS, 0);
        }
        AdmissionRules.policy(before, NOW); AdmissionRules.policy(after, current);
        AdmissionPolicyContinuity.compatible(before, after);
        var zeroBuilds = before;
        for (String scope : List.of("fleet", "target")) {
            var buildsEnabled = budgetAmount(after, scope, "measured", Dimension.CONCURRENT_BUILDS, 1);
            assertThatThrownBy(() -> AdmissionPolicyContinuity.compatible(zeroBuilds, buildsEnabled)).hasMessageContaining("POLICY_CONTINUITY_INCOMPATIBLE");
        }
    }

    @ParameterizedTest @EnumSource(Dimension.class)
    void allHeadroomRemainsExactEvenWhenLogicalCeilingsIncrease(Dimension dimension) {
        at(NOW.plusSeconds(31));
        var candidate = logicalCeilings(continuation(store, json, "policy2", List.of(original), current, NOW.plusSeconds(300)), 4);
        for (String scope : List.of("fleet", "target")) {
            var enlarged = budgetAmount(candidate, scope, "headroom", dimension, 1);
            assertThatThrownBy(() -> publishFixture(store, jdbc, enlarged, current)).hasMessageContaining("POLICY_CONTINUITY_INCOMPATIBLE");
            var originalWithHeadroom = budgetAmount(originalPolicy, scope, "headroom", dimension, 1);
            assertThatThrownBy(() -> AdmissionPolicyContinuity.compatible(originalWithHeadroom, candidate)).hasMessageContaining("POLICY_CONTINUITY_INCOMPATIBLE");
            assertThat(store.activePolicy()).isEqualTo(originalPolicy);
            assertThat(store.require(original.id())).isEqualTo(original);
        }
    }

    @Test void logicalIncreaseCannotReplaceAHeadWithoutExactCoverageOfUnknownHeldWork() {
        var held = store.outcome(original.fence(), State.OUTCOME_UNKNOWN, "retain-unknown-effects", "worker", NOW);
        at(NOW.plusSeconds(31));
        var candidate = logicalCeilings(continuation(store, json, "policy2", List.of(held), current, NOW.plusSeconds(300)), 4);
        var uncovered = new FleetPolicy(candidate.id(), candidate.fleet(), candidate.targets(), candidate.lifecycle(),
            candidate.restoreEvidenceReference(), candidate.privacyEvidenceReference(), candidate.supportReference());
        assertThatThrownBy(() -> store.approvePolicy(uncovered, "staff", current)).hasMessageContaining("POLICY_CONTINUITY_COVERAGE_REQUIRED");
        assertThat(store.activePolicy()).isEqualTo(originalPolicy); assertThat(store.held()).containsExactly(held);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='POLICY_CONT'", Long.class)).isZero();
    }

    @ParameterizedTest @ValueSource(strings={"fleet","target"})
    void logicalIncreaseCannotDiscountUnknownHeldPhysicalDemandFromFreshNonLedgerBaseline(String scope) {
        var held = store.outcome(original.fence(), State.OUTCOME_UNKNOWN, "retain-unknown-effects", "worker", NOW);
        at(NOW.plusSeconds(31));
        var candidate = logicalCeilings(continuation(store, json, "policy2", List.of(held), current, NOW.plusSeconds(300)), 4);
        var overdrawn = budgetAmount(candidate, scope, "baseline", Dimension.CPU_MILLICORES, 3);
        assertThatThrownBy(() -> publishFixture(store, jdbc, overdrawn, current))
            .hasMessageContaining(scope.equals("fleet") ? "FLEET_QUOTA_EXCEEDED:CPU_MILLICORES" : "TARGET_QUOTA_EXCEEDED:CPU_MILLICORES");
        assertThat(store.activePolicy()).isEqualTo(originalPolicy); assertThat(store.held()).containsExactly(held);
    }

    @ParameterizedTest @ValueSource(strings={"fleet-time","target-time","target-report"})
    void logicalIncreaseStillRequiresNewActualMeasurementTimesAndTargetReportIdentity(String changed) {
        at(NOW.plusSeconds(31));
        var candidate = logicalCeilings(continuation(store, json, "policy2", List.of(original), current, NOW.plusSeconds(300)), 4);
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(candidate);
        var budget = (com.fasterxml.jackson.databind.node.ObjectNode) (changed.equals("fleet-time")
            ? node.path("fleet") : node.path("targets").path(TARGET).path("budget"));
        if (changed.equals("target-report")) budget.put("measurementReference", originalPolicy.targets().get(TARGET).budget().measurementReference());
        else budget.put("measuredAt", originalPolicy.fleet().measuredAt().toString());
        var stale = json.read(node.toString(), FleetPolicy.class);
        assertThatThrownBy(() -> publishFixture(store, jdbc, stale, current)).hasMessageContaining("POLICY_CONTINUITY_INCOMPATIBLE");
        assertThat(store.activePolicy()).isEqualTo(originalPolicy); assertThat(store.require(original.id())).isEqualTo(original);
    }

    @Test void logicalIncreaseStillRequiresFreshPlacementEvidenceForTheLockedProfiles() {
        at(NOW.plusSeconds(31));
        var candidate = logicalCeilings(continuation(store, json, "policy2", List.of(original), current, NOW.plusSeconds(300)), 4);
        jdbc.execute("CREATE TABLE deployment_target_profiles (id VARCHAR(64) PRIMARY KEY)");
        jdbc.update("INSERT INTO deployment_target_profiles VALUES (?)", TARGET);
        assertThatThrownBy(() -> store.approvePolicy(candidate, "staff", current, Map::of)).hasMessageContaining("POLICY_CONTINUITY_PLACEMENT_REQUIRED");
        assertThat(store.activePolicy()).isEqualTo(originalPolicy); assertThat(store.require(original.id())).isEqualTo(original);
    }

    @Test void extraLogicalEnvironmentSlotUsesUnchangedPhysicalCapacityWithoutReleasingTheUnknownHold() {
        var held = store.outcome(original.fence(), State.OUTCOME_UNKNOWN, "retain-unknown-effects", "worker", NOW);
        at(NOW.plusSeconds(10));
        var full = continuation(store, json, "policy2", List.of(held), current, NOW.plusSeconds(300));
        for (String scope : List.of("fleet", "target")) full = budgetAmount(full, scope, "baseline", Dimension.ENVIRONMENTS, 2);
        publishFixture(store, jdbc, full, current);
        store.approveManifest(new Manifest("blocked-manifest", "deployment2", "version", "config", TARGET, "binding", "topology",
            vector(1), Set.of("policy:policy2")), "staff", current);
        var blocked = context("blocked-manifest");
        assertThatThrownBy(() -> store.reserve(blocked, new Reserve("blocked-key", "blocked-manifest"), evidence(blocked, current), "owner", current))
            .hasMessageContaining("FLEET_QUOTA_EXCEEDED:ENVIRONMENTS");
        at(NOW.plusSeconds(11));
        var increased = continuation(store, json, "policy3", List.of(held), current, NOW.plusSeconds(300));
        for (String scope : List.of("fleet", "target")) {
            increased = budgetAmount(increased, scope, "baseline", Dimension.ENVIRONMENTS, 2);
            increased = budgetAmount(increased, scope, "measured", Dimension.ENVIRONMENTS, 4);
        }
        publishFixture(store, jdbc, increased, current);
        store.approveManifest(new Manifest("new-manifest", "deployment2", "version", "config", TARGET, "binding", "topology",
            vector(1), Set.of("policy:policy3")), "staff", current);
        var admittedContext = context("new-manifest");
        var admitted = store.reserve(admittedContext, new Reserve("new-key", "new-manifest"), evidence(admittedContext, current), "owner", current);
        assertThat(admitted.policyId()).isEqualTo(increased.id());
        assertThat(store.require(held.id())).isEqualTo(held); assertThat(store.held()).containsExactlyInAnyOrder(held, admitted);
        assertThat(AdmissionRules.sum(store.held().stream().map(Reservation::chargedDemand).toList())).isEqualTo(vector(2));
        assertThat(store.context(held).policy()).isEqualTo(increased);
    }

    private FleetPolicy budgetAmount(FleetPolicy source, String scope, String vectorName, Dimension dimension, long amount) {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(source);
        var budget = scope.equals("fleet") ? node.path("fleet") : node.path("targets").path(TARGET).path("budget");
        ((com.fasterxml.jackson.databind.node.ObjectNode) budget.path(vectorName).path("amounts")).put(dimension.name(), amount);
        return json.read(node.toString(), FleetPolicy.class);
    }

    private FleetPolicy logicalCeilings(FleetPolicy source, long amount) {
        for (String scope : List.of("fleet", "target")) for (Dimension dimension : List.of(Dimension.ENVIRONMENTS, Dimension.REPLICAS,
            Dimension.DATABASES, Dimension.INDEXES, Dimension.DEPENDENCIES, Dimension.CONCURRENT_APPLIES))
            source = budgetAmount(source, scope, "measured", dimension, amount);
        return source;
    }

    @Test void uncoveredReplacementIsRejectedAtomicallyInsteadOfStrandingTheHeldAllocation() {
        at(NOW.plusSeconds(31));
        var candidate = continuation(store, json, "policy2", List.of(original), current, NOW.plusSeconds(300));
        assertThatThrownBy(() -> store.approvePolicy(new FleetPolicy(candidate.id(), candidate.fleet(), candidate.targets(), candidate.lifecycle(),
            candidate.restoreEvidenceReference(), candidate.privacyEvidenceReference(), candidate.supportReference()), "staff", current))
            .hasMessageContaining("POLICY_CONTINUITY_COVERAGE_REQUIRED");
        assertThat(store.activePolicy()).isEqualTo(originalPolicy);
        assertThat(store.require(original.id())).isEqualTo(original);
        publishFixture(store, jdbc, candidate, current);
        assertThat(store.context(original).policy()).isEqualTo(candidate);
    }

    @Test void freshMeasurementsDoNotExtendReservationLifetimeOrRefreshHistoricalPrerequisites() {
        at(NOW.plusSeconds(31)); publish("policy2", original, NOW.plusSeconds(2000));
        var context = store.context(original);
        at(NOW.plusSeconds(61));
        assertThatThrownBy(() -> AdmissionRules.prerequisites(context, original.prerequisiteReadback(), current))
            .hasMessageContaining("FRESH_READBACK_REQUIRED");
        at(original.expiresAt());
        assertThatThrownBy(() -> store.beginProvisioning(original.fence(), store.context(original), evidence(store.context(original), current),
            "worker", current, () -> {})).hasMessageContaining("PROVISIONING_REQUIRES_CURRENT_RESERVATION");
        assertThat(store.require(original.id()).expiresAt()).isEqualTo(original.expiresAt());
    }

    @Test void expiredEffectiveMeasurementCannotUseFreshPlacementOrFallBackToOriginalPolicy() {
        at(NOW.plusSeconds(31)); publish("policy2", original, NOW.plusSeconds(60));
        at(NOW.plusSeconds(60)); var currentContext = store.context(original);
        assertThatThrownBy(() -> store.beginProvisioning(original.fence(), currentContext, evidence(currentContext, current),
            "worker", current, () -> {})).hasMessageContaining("FRESH_MEASURED_CAPACITY_REQUIRED");
        assertThat(store.require(original.id())).isEqualTo(original);
    }

    @ParameterizedTest @ValueSource(strings={"deleted","rekeyed","fingerprint","generation","identity"})
    void missingOrChangedRelationFailsClosed(String changed) {
        at(NOW.plusSeconds(31)); publish("policy2", original, NOW.plusSeconds(300));
        switch (changed) {
            case "deleted" -> jdbc.update("DELETE FROM deployment_admission_reviews WHERE kind='POLICY_CONT'");
            case "rekeyed" -> jdbc.update("UPDATE deployment_admission_reviews SET record_key='wrong' WHERE kind='POLICY_CONT'");
            case "fingerprint" -> jdbc.update("UPDATE deployment_admission_reviews SET fingerprint=? WHERE kind='POLICY_CONT'", "0".repeat(64));
            case "generation", "identity" -> {
                var row = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(original);
                row.put(changed.equals("generation") ? "generation" : "operationKey", "changed");
                original = json.read(row.toString(), Reservation.class);
            }
        }
        assertThatThrownBy(() -> store.context(original)).isInstanceOf(AdmissionFailure.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_capacity_reservations", Long.class)).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings={"lifecycle","placement","profile","ceiling","headroom","restore","privacy","support","anchor","report","time"})
    void incompatiblePublicationCannotAcquireContinuityOrChangeActivePointer(String changed) {
        at(NOW.plusSeconds(31));
        var candidate = continuation(store, json, "policy2", List.of(original), current, NOW.plusSeconds(300));
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(candidate);
        var target = (com.fasterxml.jackson.databind.node.ObjectNode) node.path("targets").path(TARGET);
        switch (changed) {
            case "lifecycle" -> ((com.fasterxml.jackson.databind.node.ObjectNode) node.path("lifecycle")).put("inactivitySeconds", 2000);
            case "placement" -> ((com.fasterxml.jackson.databind.node.ObjectNode) target.path("placement")).put("destinationUuid", "other");
            case "profile" -> target.put("profileFingerprint", "other");
            case "ceiling", "headroom" -> ((com.fasterxml.jackson.databind.node.ObjectNode) target.path("budget")
                .path(changed.equals("ceiling") ? "measured" : "headroom").path("amounts")).put("MEMORY_BYTES", 2);
            case "restore" -> node.put("restoreEvidenceReference", "other");
            case "privacy" -> node.put("privacyEvidenceReference", "other");
            case "support" -> node.put("supportReference", "other");
            case "anchor" -> ((com.fasterxml.jackson.databind.node.ObjectNode) node.path("fleet")).put("measurementReference", "other");
            case "report" -> ((com.fasterxml.jackson.databind.node.ObjectNode) target.path("budget")).put("measurementReference", originalPolicy.targets().get(TARGET).budget().measurementReference());
            case "time" -> ((com.fasterxml.jackson.databind.node.ObjectNode) target.path("budget")).put("measuredAt", originalPolicy.targets().get(TARGET).budget().measuredAt().toString());
        }
        var incompatible = json.read(node.toString(), FleetPolicy.class);
        assertThatThrownBy(() -> publishFixture(store, jdbc, incompatible, current)).isInstanceOf(AdmissionFailure.class);
        assertThat(store.activePolicy()).isEqualTo(originalPolicy);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='POLICY_CONT'", Long.class)).isZero();
        assertThat(store.require(original.id())).isEqualTo(original);
    }

    @Test void staleFenceAndInsufficientChargedFleetRejectAtomically() {
        at(NOW.plusSeconds(31));
        var candidate = continuation(store, json, "policy2", List.of(original), current, NOW.plusSeconds(300));
        var advanced = store.outcome(original.fence(), State.OUTCOME_UNKNOWN, "uncertain-effects", "worker", current);
        assertThatThrownBy(() -> publishFixture(store, jdbc, candidate, current)).hasMessageContaining("STALE_RESERVATION_FENCE");
        var valid = continuation(store, json, "policy2", List.of(advanced), current, NOW.plusSeconds(300));
        var full = new Budget(valid.fleet().measured(), vector(3), valid.fleet().headroom(), valid.fleet().measurementReference(), current, NOW.plusSeconds(300));
        var overdrawn = new FleetPolicy(valid.id(), full, valid.targets(), valid.lifecycle(), valid.restoreEvidenceReference(), valid.privacyEvidenceReference(), valid.supportReference(), valid.continuations());
        assertThatThrownBy(() -> publishFixture(store, jdbc, overdrawn, current)).hasMessageContaining("FLEET_QUOTA_EXCEEDED");
        assertThat(store.activePolicy()).isEqualTo(originalPolicy); assertThat(store.held()).containsExactly(advanced);
    }

    @ParameterizedTest @ValueSource(strings={"inactivitySeconds","noticeLeadSeconds","extensionSeconds","maxExtensions","maxLifetimeSeconds",
        "maxKeepSeconds","drainSeconds","recoverySeconds","retrySeconds","maxAttempts","readbackMaxAgeSeconds","budgetWindowSeconds",
        "policyReference","noticeTransportReference"})
    void everyOriginalLifecyclePromiseIsAnExactCompatibilityContract(String field) {
        at(NOW.plusSeconds(31));
        var candidate = continuation(store, json, "policy2", List.of(original), current, NOW.plusSeconds(300));
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(candidate);
        var lifecycle = (com.fasterxml.jackson.databind.node.ObjectNode) node.path("lifecycle");
        if (lifecycle.path(field).isTextual()) lifecycle.put(field, "changed-promise");
        else lifecycle.put(field, lifecycle.path(field).longValue() + 1);
        var changed = json.read(node.toString(), FleetPolicy.class);
        assertThatThrownBy(() -> publishFixture(store, jdbc, changed, current)).hasMessageContaining("POLICY_CONTINUITY_INCOMPATIBLE");
        assertThat(store.activePolicy()).isEqualTo(originalPolicy); assertThat(store.require(original.id())).isEqualTo(original);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void publicationReplayAndRejectedRolloverPreserveEveryFinancialRowAndHighWaterCounter(boolean logicalIncrease) {
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource(
            "db/migration/V190__provider_usage_allowance.sql")).execute(dataSource);
        jdbc.update("""
            INSERT INTO provider_usage_grants(id,campaign_id,customer_id,target_profile_id,environment_name,fingerprint,payload_json,
              allocated_chat_attempts,allocated_total_tokens,allocated_cost_nano_usd,approved_by,approved_role,approved_at)
            VALUES ('grant','original-campaign','customer',?,'customer-staging',?,'{"immutableFinancialGrant":true}',3,500,100,'owner','PLATFORM_ADMIN',?)
            """, TARGET, "a".repeat(64), java.sql.Timestamp.from(NOW));
        jdbc.update("""
            INSERT INTO provider_usage_allocations(approval_id,grant_id,customer_id,deployment_id,target_profile_id,environment_name,
              approval_fingerprint,runtime_allocation_fingerprint,payload_json,initialization_state,created_at)
            VALUES ('approval','grant','customer','historical-other-deployment',?,'customer-staging',?,?,'{"originalUsageHighWater":500}',
              'UNINITIALIZED',?)
            """, TARGET, "b".repeat(64), "c".repeat(64), java.sql.Timestamp.from(NOW));
        jdbc.update("""
            INSERT INTO provider_usage_allowance_events(id,event_type,subject_id,actor_id,actor_role,payload_fingerprint,created_at)
            VALUES ('event','ALLOCATED','approval','owner','PLATFORM_ADMIN',?,?)
            """, "d".repeat(64), java.sql.Timestamp.from(NOW));
        var before = financialRows();
        at(NOW.plusSeconds(31));
        var next = continuation(store, json, "policy2", List.of(original), current, NOW.plusSeconds(300));
        if (logicalIncrease) next = logicalCeilings(next, 4);
        publishFixture(store, jdbc, next, current);
        publishFixture(store, jdbc, next, current);
        assertThat(financialRows()).isEqualTo(before);
        at(NOW.plusSeconds(32));
        var rejected = continuation(store, json, "policy3", List.of(original), NOW.plusSeconds(31), NOW.plusSeconds(500));
        assertThatThrownBy(() -> publishFixture(store, jdbc, rejected, current)).hasMessageContaining("POLICY_CONTINUITY_INCOMPATIBLE");
        assertThat(financialRows()).isEqualTo(before); assertThat(store.require(original.id())).isEqualTo(original);
    }

    private Map<String, List<Map<String, String>>> financialRows() {
        var result = new java.util.TreeMap<String, List<Map<String, String>>>();
        for (String table : List.of("provider_usage_grant_mutex","provider_usage_grants","provider_usage_allocations","provider_usage_allowance_events"))
            result.put(table, jdbc.query("SELECT * FROM " + table + " ORDER BY 1", (rs, row) -> {
                var values = new java.util.TreeMap<String, String>();
                for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++)
                    values.put(rs.getMetaData().getColumnLabel(column), rs.getString(column));
                return values;
            }));
        return result;
    }

    @Test void identicalPublicationAndRestartResolveTheSameRelationWithoutUpdatingHistoricalReviewTime() {
        at(NOW.plusSeconds(31)); var candidate = publish("policy2", original, NOW.plusSeconds(300));
        String bytes = jdbc.queryForObject("SELECT payload_json FROM deployment_admission_reviews WHERE kind='POLICY_CONT'", String.class);
        at(NOW.plusSeconds(32)); publishFixture(store, jdbc, candidate, current);
        assertThat(jdbc.queryForObject("SELECT payload_json FROM deployment_admission_reviews WHERE kind='POLICY_CONT'", String.class)).isEqualTo(bytes);
        assertThat(store.context(original).policyAuthority().continuityFingerprint()).isEqualTo(json.fingerprint(json.read(bytes, AdmissionPolicyContinuity.class)));
    }

    @Test void supersededBareReplayCannotRestoreAnOlderBaselineEvenWhenNoCapacityIsHeld() {
        store.reconcile(original.fence(), readback(original, NOW, true, 0L, 0L), true, "worker", NOW);
        assertThat(store.held()).isEmpty();
        at(NOW.plusSeconds(1));
        var full = new Budget(vector(3), vector(3), vector(0), originalPolicy.fleet().measurementReference(), current, NOW.plusSeconds(300));
        var target = new Budget(vector(3), vector(3), vector(0), "new-full-capacity-report", current, NOW.plusSeconds(300));
        var head = new FleetPolicy("policy2", full, Map.of(TARGET, new TargetBudget(TARGET, Intent.CUSTOMER_STAGING, STAGING,
            originalPolicy.targets().get(TARGET).profileFingerprint(), target)), originalPolicy.lifecycle(), originalPolicy.restoreEvidenceReference(),
            originalPolicy.privacyEvidenceReference(), originalPolicy.supportReference());
        store.approvePolicy(head, "staff", current);
        var records = jdbc.queryForList("SELECT payload_json FROM deployment_admission_reviews ORDER BY kind,record_key", String.class);
        at(NOW.plusSeconds(2));
        assertThatThrownBy(() -> store.approvePolicy(originalPolicy, "staff", current)).hasMessageContaining("POLICY_CONTINUITY_HEAD_CHANGED");
        assertThat(store.activePolicy()).isEqualTo(head);
        assertThat(jdbc.queryForList("SELECT payload_json FROM deployment_admission_reviews ORDER BY kind,record_key", String.class)).isEqualTo(records);
        store.approveManifest(manifest("new-manifest", "new-deployment"), "staff", current);
        var context = context("new-manifest");
        assertThatThrownBy(() -> store.reserve(context, new Reserve("new-work", "new-manifest"), evidence(context, current), "owner", current))
            .hasMessageContaining("FLEET_QUOTA_EXCEEDED");
        assertThat(store.held()).isEmpty();
    }

    @Test void supersededContinuationAndBareReplayCannotStrandAnAllocationAdmittedUnderTheNewHead() {
        at(NOW.plusSeconds(10)); var previous = publish("policy2", original, NOW.plusSeconds(300));
        store.approveManifest(new Manifest("new-manifest", "new-deployment", "version", "config", TARGET,
            "binding", "topology", vector(1), Set.of("policy:policy2")), "staff", current);
        var context = context("new-manifest");
        var newer = store.reserve(context, new Reserve("new-work", "new-manifest"), evidence(context, current), "owner", current);
        at(NOW.plusSeconds(11));
        var template = continuation(store, json, "policy3", List.of(original), current, NOW.plusSeconds(300));
        var head = new FleetPolicy(template.id(), template.fleet(), template.targets(), template.lifecycle(), template.restoreEvidenceReference(),
            template.privacyEvidenceReference(), template.supportReference(), List.of(template.continuations().getFirst(),
                new PolicyContinuation(previous.id(), json.fingerprint(previous), List.of(newer.fence()))));
        publishFixture(store, jdbc, head, current);
        var records = jdbc.queryForList("SELECT payload_json FROM deployment_admission_reviews ORDER BY kind,record_key", String.class);
        assertThatThrownBy(() -> publishFixture(store, jdbc, previous, current)).hasMessageContaining("POLICY_CONTINUITY_HEAD_CHANGED");
        assertThatThrownBy(() -> store.approvePolicy(originalPolicy, "staff", current)).hasMessageContaining("POLICY_CONTINUITY_HEAD_CHANGED");
        assertThat(store.activePolicy()).isEqualTo(head); assertThat(store.held()).containsExactlyInAnyOrder(original, newer);
        assertThat(store.context(newer).policy().id()).isEqualTo(head.id());
        assertThat(jdbc.queryForList("SELECT payload_json FROM deployment_admission_reviews ORDER BY kind,record_key", String.class)).isEqualTo(records);
    }

    @Test void partialTwoCustomerCoverageIsRejectedAndExactFullCoverageRemainsPublishable() {
        var grant = grant(3);
        store.approveGrant(new CustomerGrant("second-customer", grant.fleetLimit(), grant.targetLimits(), grant.validUntil(),
            "second-owner", "second-payer", null), "staff", NOW);
        store.approveManifest(manifest("second-manifest", "second-deployment"), "staff", NOW);
        var secondContext = store.context("second-customer", "second-tenant", "second-deployment", "second-manifest");
        var second = store.reserve(secondContext, new Reserve("second-op", "second-manifest"), evidence(secondContext, NOW), "second-owner", NOW);
        second = store.outcome(second.fence(), State.OUTCOME_UNKNOWN, "unknown-effects-retain-all-charges", "worker", NOW);
        at(NOW.plusSeconds(31));
        var partial = continuation(store, json, "policy2", List.of(original), current, NOW.plusSeconds(300));
        assertThatThrownBy(() -> publishFixture(store, jdbc, partial, current)).hasMessageContaining("POLICY_CONTINUITY_COVERAGE_REQUIRED");
        assertThat(store.activePolicy()).isEqualTo(originalPolicy); assertThat(store.held()).containsExactlyInAnyOrder(original, second);
        var full = continuation(store, json, "policy2", List.of(original, second), current, NOW.plusSeconds(300));
        publishFixture(store, jdbc, full, current);
        assertThat(store.context(second).policy().id()).isEqualTo(full.id());
        assertThat(store.require(second.id()).state()).isEqualTo(State.OUTCOME_UNKNOWN);
        assertThat(AdmissionRules.sum(store.held().stream().map(Reservation::chargedDemand).toList())).isEqualTo(vector(2));
    }

    @Test void aThirdHeadCannotGiveAnUnprovenForeignPolicyReferenceAuthority() {
        at(NOW.plusSeconds(10)); publish("policy2", original, NOW.plusSeconds(300));
        var source = manifest("foreign-manifest", "deployment1");
        store.approveManifest(new Manifest(source.id(), source.deploymentId(), source.versionId(), source.versionConfigHash(), TARGET,
            source.bindingFingerprint(), source.topologyReference(), source.demand(), Set.of("policy:unrelated-policy")), "staff", current);
        assertThatThrownBy(() -> store.context(original, source.id())).hasMessageContaining("POLICY_CONTINUITY_MANIFEST_CHANGED");
        at(NOW.plusSeconds(11)); var head = publish("policy3", original, NOW.plusSeconds(300));
        assertThat(store.authorizedPolicyReferences(store.context(original))).doesNotContain("policy:unrelated-policy");
        assertThat(store.require(original.id())).isEqualTo(original); assertThat(store.activePolicy()).isEqualTo(head);
    }

    @Test void newerRolloverNeedsItsOwnExactRelationAndNeverFallsBackToExpiredMeasurement() {
        at(NOW.plusSeconds(31)); publish("policy2", original, NOW.plusSeconds(100));
        at(NOW.plusSeconds(101)); publish("policy3", original, NOW.plusSeconds(300));
        assertThat(store.authorizedPolicyReferences(store.context(original))).containsExactlyInAnyOrder("policy:policy1", "policy:policy2", "policy:policy3");
        jdbc.update("DELETE FROM deployment_admission_reviews WHERE kind='POLICY_CONT' AND record_key=?",
            AdmissionPolicyContinuity.key(json, "policy3", original.id()));
        assertThatThrownBy(() -> store.context(original)).hasMessageContaining("POLICY_CONTINUITY_REQUIRED");
    }

    @ParameterizedTest @ValueSource(strings={"same-observation-later-expiry","reused-report","older-than-head","fleet-observation"})
    void secondRolloverMustAdvanceActualCurrentMeasurementHeadNotMerelyOriginalPolicy(String changed) {
        at(NOW.plusSeconds(31)); var head = publish("policy2", original, NOW.plusSeconds(100));
        String relation = jdbc.queryForObject("SELECT payload_json FROM deployment_admission_reviews WHERE kind='POLICY_CONT'", String.class);
        at(NOW.plusSeconds(40));
        var next = continuation(store, json, "policy3", List.of(original), current, NOW.plusSeconds(300));
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(next);
        var target = (com.fasterxml.jackson.databind.node.ObjectNode) node.path("targets").path(TARGET).path("budget");
        switch (changed) {
            case "same-observation-later-expiry" -> {
                target.put("measuredAt", head.targets().get(TARGET).budget().measuredAt().toString());
                target.put("measurementReference", head.targets().get(TARGET).budget().measurementReference());
                ((com.fasterxml.jackson.databind.node.ObjectNode) node.path("fleet")).put("measuredAt", head.fleet().measuredAt().toString());
            }
            case "reused-report" -> target.put("measurementReference", head.targets().get(TARGET).budget().measurementReference());
            case "older-than-head" -> target.put("measuredAt", NOW.plusSeconds(20).toString());
            case "fleet-observation" -> ((com.fasterxml.jackson.databind.node.ObjectNode) node.path("fleet")).put("measuredAt", head.fleet().measuredAt().toString());
        }
        var stale = json.read(node.toString(), FleetPolicy.class);
        assertThatThrownBy(() -> publishFixture(store, jdbc, stale, current)).hasMessageContaining("POLICY_CONTINUITY_INCOMPATIBLE");
        assertThat(store.activePolicy()).isEqualTo(head); assertThat(store.require(original.id())).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT payload_json FROM deployment_admission_reviews WHERE kind='POLICY_CONT'", String.class)).isEqualTo(relation);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='POLICY'", Long.class)).isEqualTo(2);
    }

    @Test void freshAllocationUsesNewPolicyAndStillChargesTheUnchangedHeldAllocation() {
        at(NOW.plusSeconds(31)); var candidate = publish("policy2", original, NOW.plusSeconds(300));
        var template = manifest("manifest2", "deployment2");
        store.approveManifest(new Manifest(template.id(), template.deploymentId(), template.versionId(), template.versionConfigHash(), TARGET,
            template.bindingFingerprint(), template.topologyReference(), template.demand(), Set.of("policy:policy2")), "staff", current);
        var context = context("manifest2");
        var newAllocation = store.reserve(context, new Reserve("new-key", "manifest2"), evidence(context, current), "owner", current);
        assertThat(newAllocation.policyId()).isEqualTo(candidate.id()); assertThat(context.policyAuthority()).isNull();
        assertThat(store.held()).containsExactlyInAnyOrder(original, newAllocation);
        assertThat(AdmissionRules.sum(store.held().stream().map(Reservation::chargedDemand).toList())).isEqualTo(vector(2));
    }

    @Test void noticesDrainRecoveryAndReclamationKeepOriginalLifetimeAfterBothSnapshotsExpire() {
        at(NOW.plusSeconds(31)); publish("policy2", original, NOW.plusSeconds(300));
        at(NOW.plusSeconds(900));
        var notices = store.noticeWork(original.fence(), "worker", current);
        assertThat(notices).hasSize(2);
        store.noticeDelivered(notices.getFirst(), new NoticeDelivery(notices.getFirst().deliveryKey(), "console-receipt", current), "worker", current);
        var email = notices.getLast();
        var notified = store.noticeDelivered(email, new NoticeDelivery(email.deliveryKey(), "email-receipt", current.plusSeconds(1)), "worker", current.plusSeconds(1));
        assertThatThrownBy(() -> store.claimExpiry(notified.fence(), "worker", NOW.plusSeconds(1000))).hasMessageContaining("NOTICE_WINDOW_NOT_ELAPSED");
        var claim = store.claimExpiry(notified.fence(), "worker", NOW.plusSeconds(1001));
        var handed = store.handoff(claim, "retirement", "fresh-retirement-proof", "worker", NOW.plusSeconds(1002));
        assertThat(handed.policyId()).isEqualTo(original.policyId());
        assertThat(handed.recoveryUntil()).isEqualTo(NOW.plusSeconds(1021));
        assertThatThrownBy(() -> store.reconcile(handed.fence(), readback(handed, NOW.plusSeconds(1003), true, 0L, 0L), true, "worker", NOW.plusSeconds(1003)))
            .hasMessageContaining("RECOVERY_WINDOW_NOT_ELAPSED");
        assertThatThrownBy(() -> store.reconcile(handed.fence(), readback(handed, NOW.plusSeconds(1022), true, 1L, 0L), true, "worker", NOW.plusSeconds(1022)))
            .hasMessageContaining("RECLAMATION_UNPROVEN");
        assertThat(store.reconcile(handed.fence(), readback(handed, NOW.plusSeconds(1022), true, 0L, 0L), true, "worker", NOW.plusSeconds(1022)).state())
            .isEqualTo(State.RELEASED);
    }

    @Test void concurrentRolloverAndProvisioningPreserveFencesAndAllCharges() throws Exception {
        at(NOW.plusSeconds(10)); var candidate = continuation(store, json, "policy2", List.of(original), current, NOW.plusSeconds(300));
        // Create only the profile fixture before either contender enters its authoritative transaction.
        jdbc.execute("CREATE TABLE deployment_target_profiles (id VARCHAR(64) PRIMARY KEY)"); jdbc.update("INSERT INTO deployment_target_profiles VALUES (?)", TARGET);
        var oldContext = context("manifest1"); var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var publication = workers.submit(() -> { start.await();
                try { store.approvePolicy(candidate, "staff", current, () -> Map.of(TARGET, "fixture-placement")); return true; }
                catch (AdmissionFailure expected) { assertThat(expected.getReason()).contains("STALE_RESERVATION_FENCE"); return false; } });
            var provisioning = workers.submit(() -> { start.await();
                try { store.beginProvisioning(original.fence(), oldContext, evidence(oldContext, current), "worker", current, () -> {}); return true; }
                catch (AdmissionFailure expected) { return false; } });
            start.countDown(); assertThat(publication.get(5, TimeUnit.SECONDS)).isNotEqualTo(provisioning.get(5, TimeUnit.SECONDS));
        }
        var result = store.require(original.id());
        assertThat(store.held()).containsExactly(result);
        assertThat(result.demand()).isEqualTo(original.demand()); assertThat(result.expiresAt()).isEqualTo(original.expiresAt());
        if (result.state() == State.RESERVED) {
            var fresh = store.context(result);
            assertThat(store.beginProvisioning(result.fence(), fresh, evidence(fresh, current), "worker", current, () -> {}).state()).isEqualTo(State.PROVISIONING);
        }
    }
}
