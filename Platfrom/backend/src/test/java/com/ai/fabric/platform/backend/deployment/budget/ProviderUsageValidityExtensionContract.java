package com.ai.fabric.platform.backend.deployment.budget;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageValidityExtensionContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

abstract class ProviderUsageValidityExtensionContract extends ProviderUsageAllowanceContract {
    final ProviderUsageRuntimeClient runtime = mock(ProviderUsageRuntimeClient.class);
    final ProviderUsageValidityRuntimeAccess access = mock(ProviderUsageValidityRuntimeAccess.class);
    final AtomicBoolean stopped = new AtomicBoolean();
    final AtomicInteger posts = new AtomicInteger();
    SafeAllowanceProjection active;
    ProviderUsageValidityExtensionService extensions;
    Instant time;
    Instant runtimeEffective;
    RuntimeReceipt runtimeReceipt;
    static final String URL = "https://synthetic-runtime.invalid";

    @BeforeEach void initializeExtensions(org.junit.jupiter.api.TestInfo info) {
        if (!info.getTestMethod().orElseThrow().getName().startsWith("extension")) return;
        // Actual CP tables and normal allocation transitions; runtime traffic is entirely mocked.
        var grant = runtimeGrant(); service.approveGrant(grant); authorizeRelease("deployment");
        var first = service.prepareForAuthorizedRelease("release-deployment");
        var store = UUID.randomUUID();
        service.markInitialized(first.scope(), store, first.runtimeAllocationFingerprint());
        active = service.markActive(first.scope(), store, first.runtimeAllocationFingerprint());
        time = NOW.plusSeconds(3601); runtimeEffective = active.expiresAt();
        extensions = extensionService(time);
        when(access.resolve(any(), anyString(), anyBoolean())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new ProviderUsageValidityRuntimeAccess.Access(URL, "d".repeat(64), Map.of("X-Synthetic", "not-a-credential"));
        });
        when(runtime.read(anyString(), anyMap())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return readback();
        });
        when(runtime.overview(anyString(), anyMap())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return overview();
        });
        when(runtime.extendValidity(anyString(), any(), anyMap())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            posts.incrementAndGet(); var body = (RuntimeCommand) call.getArgument(1);
            assertThat(body.expectedEffectiveExpiresAt()).isEqualTo(runtimeEffective);
            runtimeReceipt = receipt(body); runtimeEffective = body.expiresAt(); return runtimeReceipt;
        });
        when(runtime.readValidityExtension(anyString(), anyString(), anyMap())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (runtimeReceipt == null) throw new IllegalStateException("synthetic404");
            return runtimeReceipt;
        });
    }

    ProviderUsageValidityExtensionService extensionService(Instant at) {
        var manifests = new com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService(mapper);
        var releases = mock(com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository.class);
        var resolver = mock(com.ai.fabric.platform.backend.deployment.admission.EffectiveRuntimeArtifactResolver.class);
        var release = new com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity();
        release.setId("release-deployment"); release.setDeploymentId("deployment"); release.setTargetProfileId("target");
        release.setSourceArtifactId("artifact");
        when(releases.findById("release-deployment")).thenReturn(java.util.Optional.of(release));
        var artifact = new com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity();
        artifact.setArtifactRole("RUNTIME"); artifact.setArtifactType("DOCKER_IMAGE");
        artifact.setImageDigest("sha256:" + "a".repeat(64)); artifact.setGitCommitSha("b".repeat(40));
        artifact.setBuildRunId("synthetic-reviewed-build"); artifact.setPromotedAt(NOW);
        artifact.setCapabilityManifestJson(overview().path("runtimeCapabilityManifest").toString());
        artifact.setCapabilityManifestHash(overview().path("runtimeCapabilityManifestHash").asText());
        when(resolver.resolve(release)).thenReturn(artifact);
        return new ProviderUsageValidityExtensionService(source, new DataSourceTransactionManager(source), mapper,
            instance(at), runtime, access, new ProviderUsageValidityCapabilityGate(runtime, manifests, releases,
                new ProviderUsageRuntimeArtifactGate(resolver, manifests)), Clock.fixed(at, ZoneOffset.UTC));
    }

    com.fasterxml.jackson.databind.node.ObjectNode overview() {
        var manifest = mapper.createObjectNode().put("schemaVersion", "loomai-runtime-capabilities-v1").put("aiFabricVersion", "synthetic-only");
        manifest.putArray("supportedBehaviorTypes").add("CONVERSATIONAL");
        for (String field : List.of("supportedActivationSources", "supportedChannelBindings", "supportedExecutionExtensions", "specialistBundles"))
            manifest.putArray(field);
        manifest.putArray("capabilities").add("deployment-local-provider-budget-v1").add("deployment-local-provider-budget-validity-extension-v1");
        manifest.putArray("endpointClasses").add("provider-budget-administration");
        manifest.putArray("migrationIds").add("provider-budget-accounting-v1").add("provider-budget-validity-extensions-v1");
        manifest.putArray("verificationPackIds").add("provider-budget-hard-limit-v1").add("provider-budget-validity-extension-v1");
        var normalized = new com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService(mapper).normalize(manifest);
        var overview = mapper.createObjectNode().put("success", true).put("runtimeCapabilityManifestHash", normalized.hash());
        overview.put("productSourceCommit", "b".repeat(40));
        overview.putObject("runtimeMigrations").put("available", true).putArray("appliedMigrationIds")
            .add("provider-budget-accounting-v1").add("provider-budget-validity-extensions-v1");
        overview.set("runtimeCapabilityManifest", normalized.manifest()); return overview;
    }

    ReviewRequest request(String id) {
        return new ReviewRequest(id, active.scope(), "release-deployment", active.approvalFingerprint(), active.storeUuid(),
            active.expiresAt(), time.plusSeconds(3600), "Explicit synthetic operating-validity review");
    }

    RuntimeReceipt receipt(RuntimeCommand command) {
        return new RuntimeReceipt(1, command.extensionId(), active.approvalId(), new RuntimeScope("customer", "deployment", "target", "staging",
            "sha256:" + active.credentialSha256(), active.pricingReference()), active.storeUuid(), active.runtimeAllocationFingerprint(),
            command.expectedEffectiveExpiresAt(), command.expiresAt(), ProviderUsageAllowanceCodec.sha256(command.reason()), time);
    }

    ProviderUsageRuntimeClient.Readback readback() {
        return new ProviderUsageRuntimeClient.Readback("ACTIVE", active.storeUuid(), active.approvalId(), active.runtimeAllocationFingerprint(),
            "customer", "deployment", "target", "staging", active.pricingReference(), active.caps(), active.expiresAt(), 7L, 11L,
            new ProviderUsageRuntimeClient.Usage(12000, 3000, 456789), stopped.get(), runtimeEffective);
    }

    List<Map<String, Object>> finances() {
        return List.of(Map.of("grants", jdbc.queryForList("SELECT * FROM provider_usage_grants ORDER BY id")),
            Map.of("allocations", jdbc.queryForList("SELECT * FROM provider_usage_allocations ORDER BY approval_id")),
            Map.of("events", jdbc.queryForList("SELECT * FROM provider_usage_allowance_events ORDER BY id")),
            Map.of("r7", jdbc.queryForList("SELECT * FROM deployment_capacity_reservations ORDER BY id")),
            Map.of("authorizations", jdbc.queryForList("SELECT * FROM deployment_admission_authorizations ORDER BY operation_key")),
            Map.of("releases", jdbc.queryForList("SELECT * FROM platform_deployment_releases ORDER BY id")));
    }

    @Test void extensionAckPermitsOnlyExistingAllocationAfterOriginalExpiryAndNeverChangesFinancialRows() {
        var before = finances(); var request = request("extension-one");
        assertThatThrownBy(() -> instance(time).prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("ALLOWANCE_EXPIRED");
        var plan = extensions.plan(request);
        assertThat(plan.financialMutationPerformed()).isFalse(); assertThat(posts).hasValue(0);
        assertThat(extensions.read(request.extensionId()).status()).isEqualTo(Status.REVIEWED);
        var ack = extensions.execute(request.extensionId(), plan.command());
        assertThat(ack.status()).isEqualTo(Status.ACKNOWLEDGED);
        assertThat(ack.originalExpiresAt()).isEqualTo(active.expiresAt()); assertThat(ack.effectiveExpiresAt()).isEqualTo(request.expiresAt());
        assertThat(extensions.execute(request.extensionId(), plan.command())).isEqualTo(ack); assertThat(posts).hasValue(1);
        var reused = instance(time).prepareForAuthorizedRelease("release-deployment");
        assertThat(reused.approvalFingerprint()).isEqualTo(active.approvalFingerprint());
        assertThat(reused.runtimeAllocationFingerprint()).isEqualTo(active.runtimeAllocationFingerprint());
        assertThat(reused.expiresAt()).isEqualTo(active.expiresAt()); assertThat(reused.effectiveExpiresAt()).isEqualTo(request.expiresAt());
        assertThat(instance(time).findForAuthorizedRelease("release-deployment")).contains(reused);
        var quota = instance(time).readQuota("grant");
        assertThat(quota.availableForAllocation()).isEqualTo(new Limits(0, 0, 0));
        assertThat(finances()).isEqualTo(before);
    }

    @Test void extensionNeverMakesExpiredGrantCreditAvailableForAnotherDeployment() {
        var request = request("existing-only"); var plan = extensions.plan(request); extensions.execute(request.extensionId(), plan.command());
        addDeployment("new-deployment"); authorizeRelease("new-deployment");
        assertThatThrownBy(() -> instance(time).prepareForAuthorizedRelease("release-new-deployment"))
            .hasMessageContaining("REVIEWED_GRANT_REQUIRED");
        var grant = runtimeGrant();
        var command = new AllocationCommand(grant.id(), "new-deployment", "target", new Limits(0, 0, 0), grant.binding(),
            grant.rateCardReference(), ProviderUsageAllowanceCodec.sha256(secretValue.get()));
        assertThatThrownBy(() -> instance(time).allocate(command, r7("new-deployment"))).hasMessageContaining("GRANT_EXPIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
    }

    @Test void extensionLostResponseIsDurablyUnknownAndOnlyExactGetCanAcknowledge() {
        var request = request("lost-response"); var plan = extensions.plan(request); var before = finances();
        doAnswer(call -> { posts.incrementAndGet(); runtimeReceipt = receipt(call.getArgument(1)); runtimeEffective = runtimeReceipt.expiresAt();
            throw new IllegalStateException("synthetic lost response"); }).when(runtime).extendValidity(anyString(), any(), anyMap());
        assertThat(extensions.execute(request.extensionId(), plan.command()).status()).isEqualTo(Status.UNKNOWN);
        extensions = extensionService(time);
        assertThat(extensions.execute(request.extensionId(), plan.command()).status()).isEqualTo(Status.UNKNOWN);
        assertThatThrownBy(() -> instance(time).prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("ALLOWANCE_EXPIRED");
        assertThat(extensions.reconcile(request.extensionId()).status()).isEqualTo(Status.ACKNOWLEDGED);
        assertThat(posts).hasValue(1); assertThat(finances()).isEqualTo(before);
        verify(runtime, times(1)).readValidityExtension(anyString(), eq(request.extensionId()), anyMap());
    }

    @Test void extensionMissingGetNeverClearsUnknownAndBlocksACompetingIntent() {
        var first = request("unknown"); var other = request("other");
        var firstPlan = extensions.plan(first); var otherPlan = extensions.plan(other);
        doThrow(new IllegalStateException("synthetic transport")).when(runtime).extendValidity(anyString(), any(), anyMap());
        assertThat(extensions.execute(first.extensionId(), firstPlan.command()).status()).isEqualTo(Status.UNKNOWN);
        assertThat(extensions.reconcile(first.extensionId()).status()).isEqualTo(Status.UNKNOWN);
        assertThatThrownBy(() -> extensions.execute(other.extensionId(), otherPlan.command())).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThatThrownBy(() -> extensions.plan(request("third"))).hasMessageContaining("RECONCILIATION_REQUIRED");
        verify(runtime, times(1)).extendValidity(anyString(), any(), anyMap());
    }

    @Test void extensionWrongStoreOrScopeOrReasonOrExpiryReceiptStaysUnknown() {
        var request = request("mismatched"); var plan = extensions.plan(request);
        var good = receipt(plan.command().runtimeCommand());
        var bad = new RuntimeReceipt(1, good.extensionId(), good.approvalId(), good.scope(), UUID.randomUUID(), good.runtimeFingerprint(),
            good.previousEffectiveExpiresAt(), good.expiresAt(), good.reasonSha256(), good.authorizedAt());
        doReturn(bad).when(runtime).extendValidity(anyString(), any(), anyMap());
        assertThat(extensions.execute(request.extensionId(), plan.command()).status()).isEqualTo(Status.UNKNOWN);
        for (var changed : List.of(bad,
            new RuntimeReceipt(1, good.extensionId(), "other-approval", good.scope(), good.storeUuid(), good.runtimeFingerprint(), good.previousEffectiveExpiresAt(), good.expiresAt(), good.reasonSha256(), good.authorizedAt()),
            new RuntimeReceipt(1, good.extensionId(), good.approvalId(), new RuntimeScope("other", "deployment", "target", "staging", good.scope().credentialIdentity(), good.scope().rateCardReference()), good.storeUuid(), good.runtimeFingerprint(), good.previousEffectiveExpiresAt(), good.expiresAt(), good.reasonSha256(), good.authorizedAt()),
            new RuntimeReceipt(1, good.extensionId(), good.approvalId(), good.scope(), good.storeUuid(), good.runtimeFingerprint(), good.previousEffectiveExpiresAt(), good.expiresAt(), "0".repeat(64), good.authorizedAt()),
            new RuntimeReceipt(1, good.extensionId(), good.approvalId(), good.scope(), good.storeUuid(), good.runtimeFingerprint(), good.previousEffectiveExpiresAt(), good.expiresAt().plusSeconds(1), good.reasonSha256(), good.authorizedAt()))) {
            doReturn(changed).when(runtime).readValidityExtension(anyString(), anyString(), anyMap());
            assertThat(extensions.reconcile(request.extensionId()).status()).isEqualTo(Status.UNKNOWN);
        }
        assertThat(instance(time).readAllocation(active.scope()).effectiveExpiresAt()).isEqualTo(active.expiresAt());
    }

    @Test void extensionStoppedRuntimeAndStaleExpiryNeverCreateIntent() {
        stopped.set(true);
        assertThatThrownBy(() -> extensions.plan(request("stopped"))).hasMessageContaining("RUNTIME_CHANGED");
        stopped.set(false); var request = request("stops-after-review"); var plan = extensions.plan(request); stopped.set(true);
        assertThatThrownBy(() -> extensions.execute(request.extensionId(), plan.command())).hasMessageContaining("RUNTIME_CHANGED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_validity_events", Integer.class)).isZero();
        stopped.set(false); runtimeEffective = runtimeEffective.plusSeconds(1);
        assertThatThrownBy(() -> extensions.execute(request.extensionId(), plan.command())).hasMessageContaining("RUNTIME_CHANGED");
        verify(runtime, never()).extendValidity(anyString(), any(), anyMap());
    }

    @Test void extensionWindowDuplicateIdAndChangedCommandAreStrict() {
        var original = request("strict-id"); var plan = extensions.plan(original);
        assertThat(extensions.plan(original)).isEqualTo(plan);
        var changed = new ReviewRequest(original.extensionId(), original.scope(), original.releaseId(), original.expectedApprovalFingerprint(),
            original.storeUuid(), original.expectedEffectiveExpiresAt(), original.expiresAt().plusSeconds(1), original.reason());
        assertThatThrownBy(() -> extensions.plan(changed)).hasMessageContaining("COMMAND_CHANGED");
        var forged = new Command(original, plan.command().runtimeCommand(), plan.command().runtimeBaseUrl(), plan.command().authorityFingerprint(),
            plan.command().capabilityManifestHash(), "0".repeat(64));
        assertThatThrownBy(() -> extensions.execute(original.extensionId(), forged)).hasMessageContaining("COMMAND_CHANGED");
        for (Instant expiry : List.of(time, active.expiresAt(), time.plusSeconds(86_401))) {
            var request = new ReviewRequest("bad-window", original.scope(), original.releaseId(), original.expectedApprovalFingerprint(),
                original.storeUuid(), original.expectedEffectiveExpiresAt(), expiry, original.reason());
            assertThatThrownBy(() -> extensions.plan(request)).hasMessageContaining("WINDOW_INVALID");
        }
        verify(runtime, never()).extendValidity(anyString(), any(), anyMap());
    }

    @Test void extensionSameCommandRacingAndDistinctCompetingCommandsIssueExactlyOnePost() throws Exception {
        var first = request("race-one"); var second = request("race-two");
        var p1 = extensions.plan(first); var p2 = extensions.plan(second);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { posts.incrementAndGet(); entered.countDown(); assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
            runtimeReceipt = receipt(call.getArgument(1)); runtimeEffective = runtimeReceipt.expiresAt(); return runtimeReceipt;
        }).when(runtime).extendValidity(anyString(), any(), anyMap());
        try (var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var pending = pool.submit(() -> { authenticate(com.ai.fabric.platform.backend.security.PlatformRole.PLATFORM_ADMIN);
                try { return extensions.execute(first.extensionId(), p1.command()); }
                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); } });
            try {
                assertThat(entered.await(20, TimeUnit.SECONDS)).isTrue();
                assertThat(extensions.execute(first.extensionId(), p1.command()).status()).isEqualTo(Status.UNKNOWN);
                assertThatThrownBy(() -> extensions.execute(second.extensionId(), p2.command())).hasMessageContaining("RECONCILIATION_REQUIRED");
            } finally { release.countDown(); }
            assertThat(pending.get(20, TimeUnit.SECONDS).status()).isEqualTo(Status.ACKNOWLEDGED);
        }
        assertThat(posts).hasValue(1);
    }

    @Test void extensionSuccessiveReviewedChainRequiresExactAcknowledgedHead() {
        var first = request("chain-one"); extensions.execute(first.extensionId(), extensions.plan(first).command());
        var next = new ReviewRequest("chain-two", active.scope(), "release-deployment", active.approvalFingerprint(), active.storeUuid(),
            first.expiresAt(), first.expiresAt().plusSeconds(3600), "Explicit next reviewed validity");
        var result = extensions.execute(next.extensionId(), extensions.plan(next).command());
        assertThat(result.effectiveExpiresAt()).isEqualTo(next.expiresAt()); assertThat(posts).hasValue(2);
        assertThatThrownBy(() -> extensions.plan(request("stale-head"))).hasMessageContaining("EFFECTIVE_EXPIRY_CHANGED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_validity_events WHERE event_type='ACK'", Integer.class)).isEqualTo(2);
    }

    @Test void extensionUnprivilegedDirectCallsCannotReadOrReviewOrExecuteOrReconcile() {
        var request = request("privileged"); var plan = extensions.plan(request);
        for (var role : List.of(com.ai.fabric.platform.backend.security.PlatformRole.PLATFORM_OPERATOR,
            com.ai.fabric.platform.backend.security.PlatformRole.CUSTOMER_ADMIN, com.ai.fabric.platform.backend.security.PlatformRole.PUBLIC_API_CLIENT)) {
            authenticate(role);
            assertThatThrownBy(() -> extensions.plan(request)).hasMessageContaining("ADMIN_REVIEW_REQUIRED");
            assertThatThrownBy(() -> extensions.execute(request.extensionId(), plan.command())).hasMessageContaining("ADMIN_REVIEW_REQUIRED");
            assertThatThrownBy(() -> extensions.read(request.extensionId())).hasMessageContaining("ADMIN_REVIEW_REQUIRED");
            assertThatThrownBy(() -> extensions.reconcile(request.extensionId())).hasMessageContaining("ADMIN_REVIEW_REQUIRED");
        }
        verify(runtime, never()).extendValidity(anyString(), any(), anyMap());
    }

    @Test void extensionInactiveCustomerDeletedDeploymentAndRelabelledTargetCannotAcquireIntent() {
        var request = request("lifecycle");
        customer.setStatus("SUSPENDED");
        assertThatThrownBy(() -> extensions.plan(request)).hasMessageContaining("CUSTOMER_INACTIVE");
        customer.setStatus("ACTIVE"); deploymentRows.get("deployment").setDeletionRequestedAt(NOW);
        assertThatThrownBy(() -> extensions.plan(request)).hasMessageContaining("DEPLOYMENT_SCOPE_DENIED");
        deploymentRows.get("deployment").setDeletionRequestedAt(null); profile.setActive(false);
        assertThatThrownBy(() -> extensions.plan(request)).hasMessageContaining("TARGET_SCOPE_DENIED");
        profile.setActive(true); profile.setEnvironmentName("production");
        assertThatThrownBy(() -> extensions.plan(request)).hasMessageContaining("TARGET_SCOPE_DENIED");
        verifyNoInteractions(runtime, access);
    }

    @Test void extensionOldOrUnverifiableRuntimeCapabilityCannotPersistPlanOrIntent() {
        for (String field : List.of("capabilities", "endpointClasses", "migrationIds", "verificationPackIds")) {
            var old = overview(); var manifest = (com.fasterxml.jackson.databind.node.ObjectNode) old.path("runtimeCapabilityManifest");
            manifest.putArray(field);
            old.put("runtimeCapabilityManifestHash", new com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService(mapper)
                .normalize(manifest).hash());
            doReturn(old).when(runtime).overview(anyString(), anyMap());
            assertThatThrownBy(() -> extensions.plan(request("old-image"))).hasMessageContaining("CAPABILITY_REQUIRED");
        }
        var badHash = overview(); badHash.put("runtimeCapabilityManifestHash", "0".repeat(64));
        doReturn(badHash).when(runtime).overview(anyString(), anyMap());
        assertThatThrownBy(() -> extensions.plan(request("bad-hash"))).hasMessageContaining("CAPABILITY_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_validity_reviews", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_validity_events", Integer.class)).isZero();
        verify(runtime, never()).extendValidity(anyString(), any(), anyMap());
    }

    @Test void extensionCapabilityLossAfterPreviewCannotAcquireIntentOrPost() {
        var request = request("capability-lost"); var plan = extensions.plan(request);
        doReturn(mapper.createObjectNode().put("success", true)).when(runtime).overview(anyString(), anyMap());
        assertThatThrownBy(() -> extensions.execute(request.extensionId(), plan.command())).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_validity_events", Integer.class)).isZero();
        verify(runtime, never()).extendValidity(anyString(), any(), anyMap());
    }

    @Test void extensionSourceCommitOrActualMigrationMismatchCannotPersistAuthorization() {
        for (String difference : List.of("source", "unapplied", "unavailable", "manifest")) {
            var observed = overview();
            switch (difference) {
                case "source" -> observed.put("productSourceCommit", "c".repeat(40));
                case "unapplied" -> ((com.fasterxml.jackson.databind.node.ObjectNode) observed.path("runtimeMigrations")).putArray("appliedMigrationIds");
                case "unavailable" -> ((com.fasterxml.jackson.databind.node.ObjectNode) observed.path("runtimeMigrations")).put("available", false);
                case "manifest" -> {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) observed.path("runtimeCapabilityManifest")).putArray("supportedBehaviorTypes").add("SMART_BRAIN");
                    observed.put("runtimeCapabilityManifestHash", new com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService(mapper)
                        .normalize(observed.path("runtimeCapabilityManifest")).hash());
                }
                default -> throw new AssertionError(difference);
            }
            doReturn(observed).when(runtime).overview(anyString(), anyMap());
            assertThatThrownBy(() -> extensions.plan(request("drift-" + difference))).hasMessageContaining("CAPABILITY_REQUIRED");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_validity_reviews", Integer.class)).isZero();
        verify(runtime, never()).extendValidity(anyString(), any(), anyMap());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {-30, 30})
    void extensionReceiptAcceptsOnlyExplicitBoundedClockSkewOnAckAndLaterProjection(long offset) {
        var request = request("skew-boundary"); var plan = extensions.plan(request);
        var good = receipt(plan.command().runtimeCommand());
        var skewed = new RuntimeReceipt(good.schemaVersion(), good.extensionId(), good.approvalId(), good.scope(), good.storeUuid(),
            good.runtimeFingerprint(), good.previousEffectiveExpiresAt(), good.expiresAt(), good.reasonSha256(), time.plusSeconds(offset));
        doReturn(skewed).when(runtime).extendValidity(anyString(), any(), anyMap());
        assertThat(extensions.execute(request.extensionId(), plan.command()).status()).isEqualTo(Status.ACKNOWLEDGED);
        assertThat(instance(time).readAllocation(active.scope()).effectiveExpiresAt()).isEqualTo(request.expiresAt());
        assertThat(extensions.read(request.extensionId()).receipt().authorizedAt()).isEqualTo(time.plusSeconds(offset));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {-31, 31})
    void extensionReceiptOutsideBoundedClockSkewRemainsUnknownEvenOnGet(long offset) {
        var request = request("skew-outside"); var plan = extensions.plan(request);
        var good = receipt(plan.command().runtimeCommand());
        var skewed = new RuntimeReceipt(good.schemaVersion(), good.extensionId(), good.approvalId(), good.scope(), good.storeUuid(),
            good.runtimeFingerprint(), good.previousEffectiveExpiresAt(), good.expiresAt(), good.reasonSha256(), time.plusSeconds(offset));
        doReturn(skewed).when(runtime).extendValidity(anyString(), any(), anyMap());
        doReturn(skewed).when(runtime).readValidityExtension(anyString(), anyString(), anyMap());
        assertThat(extensions.execute(request.extensionId(), plan.command()).status()).isEqualTo(Status.UNKNOWN);
        assertThat(extensions.reconcile(request.extensionId()).status()).isEqualTo(Status.UNKNOWN);
        assertThat(instance(time).readAllocation(active.scope()).effectiveExpiresAt()).isEqualTo(active.expiresAt());
    }
}
