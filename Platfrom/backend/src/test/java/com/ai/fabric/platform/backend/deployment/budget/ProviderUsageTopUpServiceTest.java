package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.admission.EffectiveRuntimeArtifactResolver;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageTopUpContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProviderUsageTopUpServiceTest {
    // Reuse the real allocation setup without rerunning the unrelated inherited allowance test suite.
    static class Fixture extends ProviderUsageAllowanceContract {
        @Override DataSource freshDataSource() throws Exception {
            return new DriverManagerDataSource("jdbc:h2:mem:top_up_" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=15000", "sa", "");
        }
    }

    final Fixture f = fixture();
    final ProviderUsageRuntimeClient runtime = mock(ProviderUsageRuntimeClient.class);
    final ProviderUsageValidityRuntimeAccess access = mock(ProviderUsageValidityRuntimeAccess.class);
    final AtomicInteger posts = new AtomicInteger();
    SafeAllowanceProjection active;
    ProviderUsageTopUpService topUps;
    ProviderUsageTopUpCapabilityGate capabilities;
    Limits runtimeLimits;
    boolean stopped;
    boolean missingEffectiveLimits;
    RuntimeReceipt runtimeReceipt;
    Instant time = ProviderUsageAllowanceContract.NOW.plusSeconds(10);
    static final String URL = "https://synthetic-runtime.invalid";
    static final Limits ADDITION = new Limits(25, 1_000_000, 5_000_000_000L);

    @BeforeEach void setup() throws Exception {
        f.initialize();
        migrateTopUps();
        f.service.approveGrant(f.runtimeGrant()); f.authorizeRelease("deployment");
        var first = f.service.prepareForAuthorizedRelease("release-deployment"); var store = UUID.randomUUID();
        f.service.markInitialized(first.scope(), store, first.runtimeAllocationFingerprint());
        active = f.service.markActive(first.scope(), store, first.runtimeAllocationFingerprint()); runtimeLimits = active.caps();

        var manifests = new DeploymentSourceCapabilityManifestService(f.mapper);
        var releases = mock(DeploymentReleaseRepository.class); var resolver = mock(EffectiveRuntimeArtifactResolver.class);
        var release = new DeploymentReleaseEntity(); release.setId("release-deployment"); release.setDeploymentId("deployment");
        release.setTargetProfileId("target"); release.setSourceArtifactId("artifact");
        when(releases.findById("release-deployment")).thenReturn(Optional.of(release));
        var artifact = new DeploymentSourceArtifactEntity(); artifact.setArtifactRole("RUNTIME"); artifact.setArtifactType("DOCKER_IMAGE");
        artifact.setImageDigest("sha256:" + "a".repeat(64)); artifact.setGitCommitSha("b".repeat(40));
        artifact.setBuildRunId("synthetic-build"); artifact.setPromotedAt(time);
        artifact.setCapabilityManifestJson(overview().path("runtimeCapabilityManifest").toString());
        artifact.setCapabilityManifestHash(overview().path("runtimeCapabilityManifestHash").asText());
        when(resolver.resolve(release)).thenReturn(artifact);
        capabilities = new ProviderUsageTopUpCapabilityGate(runtime, manifests, releases, new ProviderUsageRuntimeArtifactGate(resolver, manifests));
        topUps = service();
        when(access.resolve(any(), anyString(), anyBoolean())).thenAnswer(call -> {
            noTransaction(); return new ProviderUsageValidityRuntimeAccess.Access(URL, "d".repeat(64), Map.of("X-Synthetic", "not-a-credential"));
        });
        when(runtime.overview(anyString(), anyMap())).thenAnswer(call -> { noTransaction(); return overview(); });
        when(runtime.read(anyString(), anyMap())).thenAnswer(call -> { noTransaction(); return readback(); });
        when(runtime.topUp(anyString(), any(), anyMap())).thenAnswer(call -> {
            noTransaction(); posts.incrementAndGet(); RuntimeCommand command = call.getArgument(1);
            assertThat(command.expectedEffectiveLimits()).isEqualTo(runtimeLimits);
            runtimeReceipt = receipt(command); runtimeLimits = runtimeReceipt.effectiveLimits(); return runtimeReceipt;
        });
        when(runtime.readTopUp(anyString(), anyString(), anyMap())).thenAnswer(call -> {
            noTransaction(); if (runtimeReceipt == null) throw new IllegalStateException("synthetic404"); return runtimeReceipt;
        });
    }

    @AfterEach void cleanup() { f.clearIdentity(); }

    Fixture fixture() { return new Fixture(); }

    void migrateTopUps() throws Exception {
        String migration = new ClassPathResource("db/migration/V209__provider_usage_top_ups.sql").getContentAsString(StandardCharsets.UTF_8);
        new ResourceDatabasePopulator(new ByteArrayResource(migration.substring(0, migration.indexOf("CREATE TRIGGER"))
            .getBytes(StandardCharsets.UTF_8))).execute(f.source);
    }

    ProviderUsageTopUpService service() {
        return new ProviderUsageTopUpService(f.source, new DataSourceTransactionManager(f.source), f.mapper, f.instance(time),
            runtime, access, capabilities, f.secrets, Clock.fixed(time, ZoneOffset.UTC));
    }

    static void noTransaction() { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); }

    ObjectNode overview() {
        var manifest = f.mapper.createObjectNode().put("schemaVersion", "loomai-runtime-capabilities-v1").put("aiFabricVersion", "synthetic-only");
        manifest.putArray("supportedBehaviorTypes").add("CONVERSATIONAL");
        for (String field : List.of("supportedActivationSources", "supportedChannelBindings", "supportedExecutionExtensions", "specialistBundles"))
            manifest.putArray(field);
        manifest.putArray("capabilities").add("deployment-local-provider-budget-v1").add("deployment-local-provider-budget-top-up-v1");
        manifest.putArray("endpointClasses").add("provider-budget-administration");
        manifest.putArray("migrationIds").add("provider-budget-accounting-v1").add("provider-budget-top-ups-v1");
        manifest.putArray("verificationPackIds").add("provider-budget-hard-limit-v1");
        var normalized = new DeploymentSourceCapabilityManifestService(f.mapper).normalize(manifest);
        var overview = f.mapper.createObjectNode().put("success", true).put("runtimeCapabilityManifestHash", normalized.hash())
            .put("productSourceCommit", "b".repeat(40));
        overview.set("runtimeCapabilityManifest", normalized.manifest());
        overview.putObject("runtimeMigrations").put("available", true).putArray("appliedMigrationIds")
            .add("provider-budget-accounting-v1").add("provider-budget-top-ups-v1");
        return overview;
    }

    ReviewRequest request(String id) { return request(id, active.caps(), "new-funding-" + id); }
    ReviewRequest request(String id, Limits expected, String evidence) {
        return new ReviewRequest(id, active.scope(), "release-deployment", active.approvalFingerprint(), active.storeUuid(),
            expected, ADDITION, evidence, "synthetic-new-payer", "Separately approved additional provider funding");
    }

    RuntimeReceipt receipt(RuntimeCommand body) {
        return new RuntimeReceipt(1, body.topUpId(), body.approvalId(), new ProviderUsageValidityExtensionContracts.RuntimeScope(
            "customer", "deployment", "target", "staging", "sha256:" + active.credentialSha256(), active.pricingReference()),
            body.storeUuid(), body.runtimeFingerprint(), body.expectedEffectiveLimits(), body.additionalLimits(),
            add(body.expectedEffectiveLimits(), body.additionalLimits()), body.authorizedBy(), body.approvalEvidenceRef(), body.payerRef(),
            ProviderUsageAllowanceCodec.sha256(body.reason()), time);
    }

    ProviderUsageRuntimeClient.Readback readback() {
        return new ProviderUsageRuntimeClient.Readback("ACTIVE", active.storeUuid(), active.approvalId(), active.runtimeAllocationFingerprint(),
            "customer", "deployment", "target", "staging", active.pricingReference(), active.caps(), active.expiresAt(),
            active.caps().chatAttempts(), 100L, new ProviderUsageRuntimeClient.Usage(active.caps().totalTokens(), 0, active.caps().costNanoUsd()),
            stopped, active.effectiveExpiresAt(), missingEffectiveLimits ? null : runtimeLimits);
    }

    List<Map<String, Object>> originalHistory() {
        return List.of(Map.of("grants", f.jdbc.queryForList("SELECT * FROM provider_usage_grants ORDER BY id")),
            Map.of("allocations", f.jdbc.queryForList("SELECT * FROM provider_usage_allocations ORDER BY approval_id")),
            Map.of("events", f.jdbc.queryForList("SELECT * FROM provider_usage_allowance_events ORDER BY id")),
            Map.of("validity", f.jdbc.queryForList("SELECT * FROM provider_usage_validity_reviews")),
            Map.of("validityEvents", f.jdbc.queryForList("SELECT * FROM provider_usage_validity_events")));
    }

    @Test void separatelyFundedAdditionToExhaustedAllocationRetainsOriginalTermsUsageExpiryAndGrantHistory() {
        var before = originalHistory(); var observed = readback(); var request = request("funded");
        var plan = topUps.plan(request);
        assertThat(plan.financialMutationPerformed()).isFalse(); assertThat(posts).hasValue(0);
        assertThat(topUps.plan(request)).isEqualTo(plan); assertThat(topUps.read("funded").status()).isEqualTo(Status.REVIEWED);
        var result = topUps.execute("funded", plan.command());
        assertThat(result.status()).isEqualTo(Status.ACKNOWLEDGED);
        assertThat(result.originalLimits()).isEqualTo(active.caps()); assertThat(result.effectiveLimits()).isEqualTo(add(active.caps(), ADDITION));
        assertThat(result.originalExpiresAt()).isEqualTo(active.expiresAt()); assertThat(result.effectiveExpiresAt()).isEqualTo(active.expiresAt());
        assertThat(topUps.execute("funded", plan.command())).isEqualTo(result); assertThat(posts).hasValue(1);
        assertThat(result.receipt().authorizedBy()).isEqualTo(plan.command().runtimeCommand().authorizedBy()).isNotBlank();
        assertThat(result.receipt().approvalEvidenceRef()).isEqualTo(request.approvalEvidenceRef());
        assertThat(readback().accountedUsage()).isEqualTo(observed.accountedUsage());
        assertThat(readback().chatAttempts()).isEqualTo(observed.chatAttempts()); assertThat(readback().providerAttempts()).isEqualTo(observed.providerAttempts());
        assertThat(f.service.readQuota("grant").availableForAllocation()).isEqualTo(new Limits(0, 0, 0));
        assertThat(originalHistory()).isEqualTo(before);
    }

    @Test void lostResponseSurvivesRestartAndNeverReplaysPostButExactGetAcknowledges() {
        var plan = topUps.plan(request("lost"));
        doAnswer(call -> {
            noTransaction(); posts.incrementAndGet(); runtimeReceipt = receipt(call.getArgument(1)); runtimeLimits = runtimeReceipt.effectiveLimits();
            throw new IllegalStateException("synthetic response lost after apply");
        }).when(runtime).topUp(anyString(), any(), anyMap());
        assertThat(topUps.execute("lost", plan.command()).status()).isEqualTo(Status.UNKNOWN);
        topUps = service();
        assertThat(topUps.execute("lost", plan.command()).status()).isEqualTo(Status.UNKNOWN);
        assertThat(topUps.read("lost").effectiveLimits()).isEqualTo(active.caps());
        assertThat(topUps.reconcile("lost").status()).isEqualTo(Status.ACKNOWLEDGED);
        assertThat(posts).hasValue(1); assertThat(topUps.read("lost").effectiveLimits()).isEqualTo(runtimeLimits);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM provider_usage_top_up_events WHERE top_up_id='lost'", Integer.class)).isEqualTo(3);
    }

    @Test void missingGetRetainsDurablePendingAndBlocksCompetingReviewedCommand() {
        var first = topUps.plan(request("pending")); var second = topUps.plan(request("competing"));
        doThrow(new IllegalStateException("synthetic timeout")).when(runtime).topUp(anyString(), any(), anyMap());
        assertThat(topUps.execute("pending", first.command()).status()).isEqualTo(Status.UNKNOWN);
        topUps = service(); assertThat(topUps.reconcile("pending").status()).isEqualTo(Status.UNKNOWN);
        assertThat(topUps.execute("pending", first.command()).status()).isEqualTo(Status.UNKNOWN);
        assertThatThrownBy(() -> topUps.execute("competing", second.command())).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThatThrownBy(() -> topUps.plan(request("blocked"))).hasMessageContaining("RECONCILIATION_REQUIRED");
        verify(runtime, times(1)).topUp(anyString(), any(), anyMap());
    }

    @Test void wrongScopeEvidenceActorCapsStoreAndReasonReceiptsRemainUnknown() {
        var plan = topUps.plan(request("exact-receipt")); var good = receipt(plan.command().runtimeCommand());
        doThrow(new IllegalStateException("synthetic lost")).when(runtime).topUp(anyString(), any(), anyMap());
        topUps.execute("exact-receipt", plan.command());
        for (String field : List.of("scope", "store", "fingerprint", "previous", "addition", "effective", "actor", "evidence", "payer", "reason", "time")) {
            var bad = f.mapper.valueToTree(good);
            switch (field) {
                case "scope" -> ((ObjectNode) bad.path("scope")).put("customerId", "other");
                case "store" -> ((ObjectNode) bad).put("storeUuid", UUID.randomUUID().toString());
                case "fingerprint" -> ((ObjectNode) bad).put("runtimeFingerprint", "e".repeat(64));
                case "previous" -> ((ObjectNode) bad.path("previousEffectiveLimits")).put("chatAttempts", 99);
                case "addition" -> ((ObjectNode) bad.path("additionalLimits")).put("chatAttempts", 99);
                case "effective" -> ((ObjectNode) bad.path("effectiveLimits")).put("chatAttempts", 99);
                case "actor" -> ((ObjectNode) bad).put("authorizedBy", "other-admin");
                case "evidence" -> ((ObjectNode) bad).put("approvalEvidenceRef", "other-funding");
                case "payer" -> ((ObjectNode) bad).put("payerRef", "other-payer");
                case "reason" -> ((ObjectNode) bad).put("reasonSha256", "0".repeat(64));
                case "time" -> ((ObjectNode) bad).set("authorizedAt", f.mapper.valueToTree(time.minusSeconds(31)));
                default -> throw new AssertionError(field);
            }
            doReturn(f.mapper.convertValue(bad, RuntimeReceipt.class)).when(runtime).readTopUp(anyString(), anyString(), anyMap());
            assertThat(topUps.reconcile("exact-receipt").status()).as(field).isEqualTo(Status.UNKNOWN);
        }
        doReturn(good).when(runtime).readTopUp(anyString(), anyString(), anyMap());
        assertThat(topUps.reconcile("exact-receipt").status()).isEqualTo(Status.ACKNOWLEDGED);
        verify(runtime, times(1)).topUp(anyString(), any(), anyMap());
    }

    @Test void staleReviewedCapsCannotPostAndSuccessiveAdditionsUseAcknowledgedHead() {
        var first = topUps.plan(request("head")); var stale = topUps.plan(request("stale"));
        topUps.execute("head", first.command());
        assertThatThrownBy(() -> topUps.execute("stale", stale.command())).hasMessageContaining("RUNTIME_CHANGED");
        var acknowledgedHead = runtimeLimits; runtimeLimits = active.caps();
        assertThatThrownBy(() -> topUps.execute("stale", stale.command())).hasMessageContaining("EFFECTIVE_LIMITS_CHANGED");
        runtimeLimits = acknowledgedHead;
        var second = topUps.plan(request("next", runtimeLimits, "funding-next"));
        assertThat(topUps.execute("next", second.command()).effectiveLimits()).isEqualTo(add(add(active.caps(), ADDITION), ADDITION));
        assertThat(topUps.read("head").effectiveLimits()).isEqualTo(runtimeLimits); assertThat(posts).hasValue(2);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM provider_usage_top_up_events WHERE top_up_id='stale'", Integer.class)).isZero();
    }

    @Test void nonExactPostReceiptIsUnknownAndCannotBeUsedToAuthorizeReplay() {
        var plan = topUps.plan(request("wrong-post")); var good = receipt(plan.command().runtimeCommand());
        var bad = new RuntimeReceipt(good.schemaVersion(), good.topUpId(), good.approvalId(), good.scope(), UUID.randomUUID(),
            good.runtimeFingerprint(), good.previousEffectiveLimits(), good.additionalLimits(), good.effectiveLimits(), good.authorizedBy(),
            good.approvalEvidenceRef(), good.payerRef(), good.reasonSha256(), good.authorizedAt());
        doReturn(bad).when(runtime).topUp(anyString(), any(), anyMap());
        assertThat(topUps.execute("wrong-post", plan.command()).status()).isEqualTo(Status.UNKNOWN);
        topUps = service(); assertThat(topUps.execute("wrong-post", plan.command()).status()).isEqualTo(Status.UNKNOWN);
        doReturn(bad).when(runtime).readTopUp(anyString(), anyString(), anyMap());
        assertThat(topUps.reconcile("wrong-post").status()).isEqualTo(Status.UNKNOWN);
        doReturn(good).when(runtime).readTopUp(anyString(), anyString(), anyMap());
        assertThat(topUps.reconcile("wrong-post").status()).isEqualTo(Status.ACKNOWLEDGED);
        verify(runtime, times(1)).topUp(anyString(), any(), anyMap());
    }

    @Test void sameAndDifferentCommandsRacingAcquireOnlyOneIntentAndOnePost() throws Exception {
        var first = topUps.plan(request("race")); var second = topUps.plan(request("race-other"));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> {
            noTransaction(); posts.incrementAndGet(); entered.countDown(); assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            runtimeReceipt = receipt(call.getArgument(1)); runtimeLimits = runtimeReceipt.effectiveLimits(); return runtimeReceipt;
        }).when(runtime).topUp(anyString(), any(), anyMap());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> {
                try { Fixture.authenticate(PlatformRole.PLATFORM_ADMIN); return topUps.execute("race", first.command()); }
                finally { f.clearIdentity(); }
            });
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(topUps.execute("race", first.command()).status()).isEqualTo(Status.UNKNOWN);
                assertThatThrownBy(() -> topUps.execute("race-other", second.command())).hasMessageContaining("RECONCILIATION_REQUIRED");
                assertThat(topUps.reconcile("race").status()).isEqualTo(Status.UNKNOWN);
            } finally { release.countDown(); }
            assertThat(running.get(20, TimeUnit.SECONDS).status()).isEqualTo(Status.ACKNOWLEDGED);
        }
        assertThat(posts).hasValue(1);
    }

    @Test void currentOwnerTargetCredentialAndAllocationFencesAreRequired() {
        var plan = topUps.plan(request("fences"));
        f.secretValue.set("rotated");
        assertThatThrownBy(() -> topUps.execute("fences", plan.command())).hasMessageContaining("CREDENTIAL_CHANGED");
        f.secretValue.set("fictional-provider-credential"); f.customer.setStatus("SUSPENDED");
        assertThatThrownBy(() -> topUps.plan(request("customer"))).hasMessageContaining("CUSTOMER_INACTIVE");
        f.customer.setStatus("ACTIVE"); f.deploymentRows.get("deployment").setCustomerId("other");
        assertThatThrownBy(() -> topUps.plan(request("owner"))).hasMessageContaining("DEPLOYMENT_SCOPE_DENIED");
        f.deploymentRows.get("deployment").setCustomerId("customer"); f.profile.setEnvironmentName("production");
        assertThatThrownBy(() -> topUps.plan(request("target"))).hasMessageContaining("TARGET_SCOPE_DENIED");
        verify(runtime, never()).topUp(anyString(), any(), anyMap());
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM provider_usage_top_up_events", Integer.class)).isZero();
    }

    @Test void stoppedExpiredMissingOrStaleEffectiveLimitsAndChangedAuthorityCannotCreateIntent() {
        var plan = topUps.plan(request("readback-fence"));
        stopped = true;
        assertThatThrownBy(() -> topUps.execute("readback-fence", plan.command())).hasMessageContaining("RUNTIME_CHANGED");
        stopped = false; missingEffectiveLimits = true;
        assertThatThrownBy(() -> topUps.plan(request("old-readback"))).hasMessageContaining("RUNTIME_CHANGED");
        missingEffectiveLimits = false; runtimeLimits = add(active.caps(), ADDITION);
        assertThatThrownBy(() -> topUps.execute("readback-fence", plan.command())).hasMessageContaining("RUNTIME_CHANGED");
        runtimeLimits = active.caps(); time = active.expiresAt(); topUps = service();
        assertThatThrownBy(() -> topUps.execute("readback-fence", plan.command())).hasMessageContaining("VALIDITY_CHANGED");
        time = ProviderUsageAllowanceContract.NOW.plusSeconds(10); topUps = service();
        doReturn(new ProviderUsageValidityRuntimeAccess.Access(URL, "e".repeat(64), Map.of("X-Synthetic", "test")))
            .when(access).resolve(any(), anyString(), anyBoolean());
        assertThatThrownBy(() -> topUps.execute("readback-fence", plan.command())).hasMessageContaining("AUTHORITY_CHANGED");
        verify(runtime, never()).topUp(anyString(), any(), anyMap());
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM provider_usage_top_up_events", Integer.class)).isZero();
    }

    @Test void oldManifestUnappliedMigrationAndSourceDriftCannotAuthorizeTopUp() {
        for (String field : List.of("capabilities", "migrationIds", "applied", "source", "hash", "available")) {
            var observed = overview();
            switch (field) {
                case "capabilities", "migrationIds" -> {
                    ((ObjectNode) observed.path("runtimeCapabilityManifest")).putArray(field);
                    observed.put("runtimeCapabilityManifestHash", new DeploymentSourceCapabilityManifestService(f.mapper)
                        .normalize(observed.path("runtimeCapabilityManifest")).hash());
                }
                case "applied" -> ((ObjectNode) observed.path("runtimeMigrations")).putArray("appliedMigrationIds");
                case "source" -> observed.put("productSourceCommit", "c".repeat(40));
                case "hash" -> observed.put("runtimeCapabilityManifestHash", "0".repeat(64));
                case "available" -> ((ObjectNode) observed.path("runtimeMigrations")).put("available", false);
                default -> throw new AssertionError(field);
            }
            doReturn(observed).when(runtime).overview(anyString(), anyMap());
            assertThatThrownBy(() -> topUps.plan(request("old-" + field))).hasMessageContaining("CAPABILITY_REQUIRED");
        }
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM provider_usage_top_up_reviews", Integer.class)).isZero();
        verify(runtime, never()).topUp(anyString(), any(), anyMap());
    }

    @Test void fundingEvidenceCannotRedrawOriginalGrantOrAuthorizeTwoCommandsAndCommandMustBeExact() {
        assertThatThrownBy(() -> topUps.plan(request("original", active.caps(), f.runtimeGrant().ownerApprovalEvidenceRef())))
            .hasMessageContaining("NEW_FUNDING_REQUIRED");
        var request = request("reviewed"); var plan = topUps.plan(request);
        assertThatThrownBy(() -> topUps.plan(request("double-funding", active.caps(), request.approvalEvidenceRef())))
            .hasMessageContaining("NEW_FUNDING_REQUIRED");
        var changed = new Command(plan.command().review(), plan.command().runtimeCommand(), plan.command().runtimeBaseUrl(),
            plan.command().authorityFingerprint(), plan.command().capabilityManifestHash(), "0".repeat(64));
        assertThatThrownBy(() -> topUps.execute("reviewed", changed)).hasMessageContaining("COMMAND_CHANGED");
        verify(runtime, never()).topUp(anyString(), any(), anyMap());
    }

    @Test void directCallsAlsoRequireAdminForAllOperations() {
        var request = request("admin"); var plan = topUps.plan(request);
        for (var role : List.of(PlatformRole.PLATFORM_OPERATOR, PlatformRole.CUSTOMER_ADMIN, PlatformRole.PUBLIC_API_CLIENT)) {
            Fixture.authenticate(role);
            assertThatThrownBy(() -> topUps.plan(request)).hasMessageContaining("ADMIN_REVIEW_REQUIRED");
            assertThatThrownBy(() -> topUps.execute("admin", plan.command())).hasMessageContaining("ADMIN_REVIEW_REQUIRED");
            assertThatThrownBy(() -> topUps.read("admin")).hasMessageContaining("ADMIN_REVIEW_REQUIRED");
            assertThatThrownBy(() -> topUps.reconcile("admin")).hasMessageContaining("ADMIN_REVIEW_REQUIRED");
        }
        verify(runtime, never()).topUp(anyString(), any(), anyMap());
    }
}
