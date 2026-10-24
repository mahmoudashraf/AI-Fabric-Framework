package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.secret.model.DeploymentSecretResolutionSummary;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretResolutionService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

abstract class ProviderUsageAllowanceContract {
    static final Instant NOW = Instant.parse("2026-10-18T12:00:00.123456789Z");
    static final String DECLARED_PROVIDER_CONFIG = """
        {"llmProvider":"openai","embeddingProvider":"openai","openaiModel":"gpt-4o-mini",
         "openaiEmbeddingModel":"text-embedding-3-small","openaiMaxTokens":2048,"enableFallback":false}
        """;
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    final ProviderUsageAllowanceCodec codec = new ProviderUsageAllowanceCodec(mapper);
    final DeploymentRepository deployments = mock(DeploymentRepository.class);
    final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    final PlatformCustomerRepository customers = mock(PlatformCustomerRepository.class);
    final PlatformUserRepository users = mock(PlatformUserRepository.class);
    final DeploymentAccessService deploymentAccess = mock(DeploymentAccessService.class);
    final DeploymentProviderSecretResolutionService secrets = mock(DeploymentProviderSecretResolutionService.class);
    final Map<String, DeploymentEntity> deploymentRows = new ConcurrentHashMap<>();
    final Map<String, DeploymentVersionEntity> versionRows = new ConcurrentHashMap<>();
    final AtomicReference<String> secretValue = new AtomicReference<>("fictional-provider-credential");
    final ProviderBinding binding = new ProviderBinding("openai", List.of("gpt-4o-mini", "text-embedding-3-small"), "OPENAI_API_KEY", null);
    DataSource source; JdbcTemplate jdbc; ProviderUsageAllowanceService service;
    DeploymentTargetProfileEntity profile; PlatformCustomerEntity customer;

    abstract DataSource freshDataSource() throws Exception;

    @BeforeEach void initialize() throws Exception {
        source = freshDataSource(); jdbc = new JdbcTemplate(source);
        jdbc.execute("""
            CREATE TABLE platform_deployment_releases
            (id VARCHAR(64) PRIMARY KEY,deployment_id VARCHAR(64),deployment_version_id VARCHAR(64),target_profile_id VARCHAR(64),
             status VARCHAR(64),provisioning_status VARCHAR(64),source_artifact_id VARCHAR(64))
            """);
        ProviderUsageCoreReleaseTestSchema.create(jdbc);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V190__provider_usage_allowance.sql")).execute(source);
        ProviderUsageGrantLifecycleTestSchema.migrate(source);
        profile = new DeploymentTargetProfileEntity(); profile.setId("target"); profile.setEnvironmentName("staging");
        profile.setActive(true); profile.setProviderType(DeploymentProviderType.COOLIFY); profile.setSourceStrategy("IMAGE_SOURCE");
        profile.setCredentialRefId("profile-reference"); profile.setProviderConfigJson("{}"); profile.setNetworkPolicyJson("{}"); profile.setResourceDefaultsJson("{}");
        when(targets.findById("target")).thenReturn(Optional.of(profile));
        customer = new PlatformCustomerEntity(); customer.setId("customer"); customer.setStatus("ACTIVE");
        when(customers.findById("customer")).thenReturn(Optional.of(customer));
        var owner = new PlatformUserEntity(); owner.setId("owner"); owner.setCustomerId("customer"); owner.setRole("CUSTOMER_ADMIN"); owner.setStatus("ACTIVE");
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(owner));
        when(deployments.findById(anyString())).thenAnswer(call -> Optional.ofNullable(deploymentRows.get(call.getArgument(0))));
        when(versions.findById(anyString())).thenAnswer(call -> Optional.ofNullable(versionRows.get(call.getArgument(0))));
        when(secrets.resolve(anyString(), eq(binding.secretPurpose()), isNull())).thenAnswer(call -> {
            String value = secretValue.get();
            var summary = new DeploymentSecretResolutionSummary(call.getArgument(0), binding.secretPurpose(), "fixture", false,
                value != null, "fixture", "fixture", "fixture", "fixture-reference", null, false, "fixture", "fixture");
            return new DeploymentProviderSecretResolutionService.ResolvedSecretValue(summary, value, null);
        });
        service = instance(NOW); authenticate(PlatformRole.PLATFORM_ADMIN); addDeployment("deployment");
    }

    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    ProviderUsageAllowanceService instance(Instant time) {
        return new ProviderUsageAllowanceService(source, new DataSourceTransactionManager(source), mapper, deployments, targets, versions,
            customers, new PlatformCustomerAccessService(users, customers), deploymentAccess, secrets, Clock.fixed(time, ZoneOffset.UTC));
    }

    ProviderUsageGrant grant() { return new ProviderUsageGrant("grant", "campaign", "customer", "target", "staging",
        new Limits(50, 200_000, 2_000_000_000L), NOW.plusSeconds(3600), binding, "openai-gpt4omini-embedding3small-20261018-v1", "fixture-owner-approval", "fixture-payer"); }
    ProviderUsageGrant runtimeGrant() {
        var original = grant();
        return new ProviderUsageGrant(original.id(), original.campaignId(), original.customerId(), original.targetProfileId(),
            original.environment(), original.credits(), original.expiresAt(),
            new ProviderBinding("openai", List.of("gpt-4o-mini", "text-embedding-3-small"), binding.secretPurpose(), null),
            "openai-gpt4omini-embedding3small-20261018-v1", original.ownerApprovalEvidenceRef(), original.payerRef());
    }
    AllocationCommand command(String deployment, Limits caps) { return new AllocationCommand("grant", deployment, "target", caps, binding,
        "openai-gpt4omini-embedding3small-20261018-v1", ProviderUsageAllowanceCodec.sha256(secretValue.get())); }
    AllocationCommand command() { return command("deployment", grant().credits()); }
    ReviewedR7Context r7(String deployment) { return new ReviewedR7Context("reservation-" + deployment, "generation", "version-" + deployment); }
    SafeAllowanceProjection allocated() { service.approveGrant(grant()); authorizeRelease("deployment"); return service.prepareForAuthorizedRelease("release-deployment"); }

    void addDeployment(String id) {
        var deployment = new DeploymentEntity(); deployment.setId(id); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        deployment.setStatus("ACTIVE"); deployment.setEnvironmentName("control-plane-label-not-authority"); deploymentRows.put(id, deployment);
        var version = new DeploymentVersionEntity(); version.setId("version-" + id); version.setDeploymentId(id); version.setStatus("PUBLISHED");
        version.setConfigHash("config-hash"); versionRows.put(version.getId(), version);
        version.setProviderConfigJson(DECLARED_PROVIDER_CONFIG);
    }

    static void authenticate(PlatformRole role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal("actor@example.test", role, "fixture", "TEST"), null, List.of()));
    }

    @Test void grantsRequireExplicitAdminEvenWithoutMethodSecurityProxyAndAuditIsDurable() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.approveGrant(grant())).hasMessageContaining("AUTHENTICATION_REQUIRED");
        for (PlatformRole role : List.of(PlatformRole.CUSTOMER_ADMIN, PlatformRole.PLATFORM_OPERATOR, PlatformRole.PUBLIC_API_CLIENT)) {
            authenticate(role); assertThatThrownBy(() -> service.approveGrant(grant())).hasMessageContaining("ADMIN_REVIEW_REQUIRED");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_grants", Integer.class)).isZero();
        authenticate(PlatformRole.PLATFORM_ADMIN); service.approveGrant(grant()); service.approveGrant(grant());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allowance_events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT approved_role FROM provider_usage_grants", String.class)).isEqualTo("PLATFORM_ADMIN");
    }

    @Test void grantReplayCannotChangeReviewOrCreateANewIdForTheSameCampaign() {
        var original = grant(); service.approveGrant(original);
        var changed = new ProviderUsageGrant(original.id(), original.campaignId(), original.customerId(), original.targetProfileId(), original.environment(),
            new Limits(51, 200_000, 2_000_000_000L), original.expiresAt(), original.binding(), original.rateCardReference(), original.ownerApprovalEvidenceRef(), original.payerRef());
        assertThatThrownBy(() -> service.approveGrant(changed)).hasMessageContaining("GRANT_IMMUTABLE");
        var alias = new ProviderUsageGrant("new-id", original.campaignId(), original.customerId(), original.targetProfileId(), original.environment(), original.credits(),
            original.expiresAt(), original.binding(), original.rateCardReference(), original.ownerApprovalEvidenceRef(), original.payerRef());
        assertThatThrownBy(() -> service.approveGrant(alias)).hasMessageContaining("CAMPAIGN_ALREADY_REVIEWED");
    }

    @Test void exactAllocationReplayReusesApprovalAndNeverResetsLifetimeDebit() {
        var first = allocated();
        assertThat(first.state()).isEqualTo(InitializationState.UNINITIALIZED); assertThat(first.storeUuid()).isNull();
        assertThat(instance(NOW).prepareForAuthorizedRelease("release-deployment")).isEqualTo(first);
        assertThat(service.approveGrant(grant()).lifetimeAllocated()).isEqualTo(grant().credits());
        assertThat(service.readQuota("grant").remaining()).isEqualTo(new Limits(0, 0, 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allowance_events WHERE event_type='ALLOWANCE_ALLOCATED'", Integer.class)).isEqualTo(1);
    }

    @Test void legacyReservationNeverAuthorizesNewIssuance() {
        service.approveGrant(grant());
        for (var reviewed : List.of(r7("deployment"), new ReviewedR7Context("missing", "wrong", "other"))) {
            assertThatThrownBy(() -> service.allocate(command(), reviewed)).hasMessageContaining("LEGACY_ISSUANCE_UNSUPPORTED");
        }
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(new Limits(0, 0, 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
    }
    @Test void rawCustomerCommandsAndLegacyStaffCommandsCannotAllocate() {
        service.approveGrant(grant()); authenticate(PlatformRole.CUSTOMER_ADMIN);
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("TRUSTED_SERVER_REQUIRED");
        authenticate(PlatformRole.PLATFORM_OPERATOR);
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("LEGACY_ISSUANCE_UNSUPPORTED");
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(new Limits(0, 0, 0));
        verifyNoInteractions(deploymentAccess);
    }
    @Test void profileEnvironmentAndActualDeploymentOwnershipCannotBeSpoofed() {
        service.approveGrant(grant()); authorizeRelease("deployment"); profile.setEnvironmentName("production");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("REVIEWED_GRANT_REQUIRED");
        profile.setEnvironmentName("staging"); deploymentRows.get("deployment").setCustomerId("other");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("DEPLOYMENT_SCOPE_DENIED");
        deploymentRows.get("deployment").setCustomerId("customer"); customer.setStatus("DISABLED");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("CUSTOMER_INACTIVE");
    }
    @Test void archivedOrDeletingDeploymentCannotReceiveNewCredits() {
        service.approveGrant(grant()); authorizeRelease("deployment"); deploymentRows.get("deployment").setArchivedAt(NOW);
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("DEPLOYMENT_SCOPE_DENIED");
        deploymentRows.get("deployment").setArchivedAt(null); deploymentRows.get("deployment").setDeletionRequestedAt(NOW);
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("DEPLOYMENT_SCOPE_DENIED");
    }
    @Test void inactiveTargetAndReviewedVersionDriftDenyWithoutDebiting() {
        service.approveGrant(grant()); authorizeRelease("deployment"); profile.setActive(false);
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("TARGET_SCOPE_DENIED");
        profile.setActive(true); versionRows.get("version-deployment").setConfigHash("changed");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("CORE_AUTHORIZATION_SCOPE_CHANGED");
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(new Limits(0, 0, 0));
    }
    @Test void legacyCommandsAndActualCredentialChangesCannotAlterAnAllocation() {
        var first = allocated();
        assertThatThrownBy(() -> service.allocate(command("deployment", new Limits(1, 1, 1)), r7("deployment")))
            .hasMessageContaining("LEGACY_ISSUANCE_UNSUPPORTED");
        secretValue.set("rotated-fictional-credential");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("ALLOCATION_IMMUTABLE");
        assertThat(service.readAllocation(first.scope())).isEqualTo(first);
        assertThat(codec.write(first)).doesNotContain("fictional-provider-credential").doesNotContain("rotated-fictional-credential");
    }
    @Test void missingActualCredentialNeverProducesAFakeHashOrAllocation() {
        service.approveGrant(grant()); authorizeRelease("deployment"); secretValue.set(null);
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("CREDENTIAL_UNRESOLVED");
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(new Limits(0, 0, 0));
    }
    @Test void removalNeverRefundsOrDeletesLifetimeProviderCredits() {
        var first = allocated(); deploymentRows.remove("deployment");
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(grant().credits());
        assertThat(service.readAllocation(first.scope())).isEqualTo(first);
        addDeployment("replacement"); authorizeRelease("replacement");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-replacement")).hasMessageContaining("LIFETIME_CREDITS_EXCEEDED");
        assertThat(service.readQuota("grant").remaining()).isEqualTo(new Limits(0, 0, 0));
    }
    @Test void twoPhaseInitializationAndActivationHaveExactIdempotentStoreReceipts() {
        var first = allocated(); UUID store = UUID.randomUUID();
        assertThatThrownBy(() -> service.markActiveForAuthorizedRelease("release-" + first.scope().deploymentId(), store, first.runtimeAllocationFingerprint())).hasMessageContaining("INITIALIZATION_REQUIRED");
        assertThatThrownBy(() -> service.markInitializedForAuthorizedRelease("release-" + first.scope().deploymentId(), store, "0".repeat(64))).hasMessageContaining("FINGERPRINT_CHANGED");
        var initialized = service.markInitializedForAuthorizedRelease("release-" + first.scope().deploymentId(), store, first.runtimeAllocationFingerprint());
        assertThat(initialized.state()).isEqualTo(InitializationState.INITIALIZED); assertThat(initialized.storeUuid()).isEqualTo(store);
        assertThat(instance(NOW).markInitializedForAuthorizedRelease("release-deployment", store, first.runtimeAllocationFingerprint())).isEqualTo(initialized);
        var active = service.markActiveForAuthorizedRelease("release-" + first.scope().deploymentId(), store, first.runtimeAllocationFingerprint());
        assertThat(active.state()).isEqualTo(InitializationState.ACTIVE);
        assertThat(service.markActiveForAuthorizedRelease("release-" + first.scope().deploymentId(), store, first.runtimeAllocationFingerprint())).isEqualTo(active);
        assertThat(service.markInitializedForAuthorizedRelease("release-" + first.scope().deploymentId(), store, first.runtimeAllocationFingerprint())).isEqualTo(active);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allowance_events WHERE subject_id=?", Integer.class, first.approvalId())).isEqualTo(3);
    }

    @Test void storeLossCannotBeAutomaticallyReinitializedOrRefunded() {
        var first = allocated(); UUID original = UUID.randomUUID();
        service.markInitializedForAuthorizedRelease("release-" + first.scope().deploymentId(), original, first.runtimeAllocationFingerprint()); service.markActiveForAuthorizedRelease("release-" + first.scope().deploymentId(), original, first.runtimeAllocationFingerprint());
        var restarted = instance(NOW);
        assertThatThrownBy(() -> restarted.markInitializedForAuthorizedRelease("release-" + first.scope().deploymentId(), UUID.randomUUID(), first.runtimeAllocationFingerprint())).hasMessageContaining("STORE_REPLACEMENT_FORBIDDEN");
        assertThatThrownBy(() -> restarted.markActiveForAuthorizedRelease("release-" + first.scope().deploymentId(), UUID.randomUUID(), first.runtimeAllocationFingerprint())).hasMessageContaining("STORE_REPLACEMENT_FORBIDDEN");
        assertThat(restarted.readQuota("grant").remaining()).isEqualTo(new Limits(0, 0, 0));
        assertThat(restarted.readAllocation(first.scope()).storeUuid()).isEqualTo(original);
    }

    @Test void initializationScopeCannotBeReboundToAnotherCustomerDeploymentOrTarget() {
        var first = allocated();
        for (AllocationScope scope : List.of(new AllocationScope(first.approvalId(), "other", "deployment", "target", "staging"),
            new AllocationScope(first.approvalId(), "customer", "other", "target", "staging"),
            new AllocationScope(first.approvalId(), "customer", "deployment", "other", "staging"),
            new AllocationScope(first.approvalId(), "customer", "deployment", "target", "production"))) {
            assertThatThrownBy(() -> service.readAllocation(scope)).hasMessageContaining("SCOPE_MISMATCH");
            assertThatThrownBy(() -> service.markInitialized(scope, UUID.randomUUID(), first.runtimeAllocationFingerprint())).hasMessageContaining("AUTHORIZED_RELEASE_REQUIRED");
        }
        assertThat(service.readAllocation(first.scope())).isEqualTo(first);
    }

    @Test void expiryBlocksNewAllocationAndActivationButNeverRestoresLifetimeCredits() {
        var first = allocated(); UUID store = UUID.randomUUID(); service.markInitializedForAuthorizedRelease("release-" + first.scope().deploymentId(), store, first.runtimeAllocationFingerprint());
        var expired = instance(grant().expiresAt());
        assertThatThrownBy(() -> expired.markActiveForAuthorizedRelease("release-" + first.scope().deploymentId(), store, first.runtimeAllocationFingerprint())).hasMessageContaining("ALLOWANCE_EXPIRED");
        assertThat(expired.approveGrant(grant()).lifetimeAllocated()).isEqualTo(grant().credits());
        assertThat(expired.readQuota("grant").remaining()).isEqualTo(new Limits(0, 0, 0));
    }

    @Test void concurrentDistinctDeploymentAllocationsCannotOverbookLifetimeGrant() throws Exception {
        service.approveGrant(grant());
        for (int index = 0; index < 8; index++) { addDeployment("deployment-" + index); authorizeRelease("deployment-" + index); }
        var results = race(8, index -> () -> {
            try { return instance(NOW).prepareForAuthorizedRelease("release-deployment-" + index); }
            catch (ProviderUsageAllowanceFailure failure) { assertThat(failure.getReason()).isEqualTo("PROVIDER_USAGE_LIFETIME_CREDITS_EXCEEDED"); return null; }
        });
        assertThat(results.stream().filter(java.util.Objects::nonNull).count()).isEqualTo(1);
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(grant().credits());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
    }
    @Test void concurrentExactAllocationRetriesDebitOnceAndReturnOneApproval() throws Exception {
        service.approveGrant(grant()); authorizeRelease("deployment"); var results = race(8, index -> () -> instance(NOW).prepareForAuthorizedRelease("release-deployment"));
        assertThat(results).allMatch(results.getFirst()::equals);
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(grant().credits());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
    }

    @Test void failedMandatoryAuditRollsBackBothLifetimeDebitAndNewApproval() {
        service.approveGrant(grant()); authorizeRelease("deployment");
        jdbc.execute("ALTER TABLE provider_usage_allowance_events ADD CONSTRAINT fixture_fail_alloc_audit CHECK (event_type <> 'ALLOWANCE_ALLOCATED')");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(new Limits(0, 0, 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
    }

    @Test void lifecycleAuditFailureCannotLeaveAnUnreceiptedInitialization() {
        var first = allocated();
        jdbc.execute("ALTER TABLE provider_usage_allowance_events ADD CONSTRAINT fixture_fail_init_audit CHECK (event_type <> 'ALLOWANCE_INITIALIZED')");
        assertThatThrownBy(() -> service.markInitializedForAuthorizedRelease("release-" + first.scope().deploymentId(), UUID.randomUUID(), first.runtimeAllocationFingerprint()))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(service.readAllocation(first.scope())).isEqualTo(first);
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(grant().credits());
    }

    @Test void checkedArithmeticOverflowCannotWrapOrRestoreLifetimeCredits() {
        var original = grant(); var maximum = new Limits(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
        service.approveGrant(new ProviderUsageGrant(original.id(), original.campaignId(), original.customerId(), original.targetProfileId(),
            original.environment(), maximum, original.expiresAt(), original.binding(), original.rateCardReference(),
            original.ownerApprovalEvidenceRef(), original.payerRef()));
        authorizeRelease("deployment"); service.prepareForAuthorizedRelease("release-deployment");
        addDeployment("second"); authorizeRelease("second");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-second")).hasMessageContaining("CREDIT_OVERFLOW");
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(maximum);
        assertThat(service.readQuota("grant").remaining()).isEqualTo(new Limits(0, 0, 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
    }
    @Test void committedLifetimeDebitSurvivesCallerRollback() throws Exception {
        service.approveGrant(grant()); authorizeRelease("deployment");
        try (var outer = source.getConnection(); var statement = outer.createStatement()) {
            statement.execute("CREATE TABLE fixture_caller_work(id INTEGER PRIMARY KEY)"); outer.setAutoCommit(false);
            statement.executeUpdate("INSERT INTO fixture_caller_work VALUES (1)"); service.prepareForAuthorizedRelease("release-deployment"); outer.rollback();
        }
        assertThat(instance(NOW).readQuota("grant").lifetimeAllocated()).isEqualTo(grant().credits());
    }

    @Test void customerQuotaReadUsesActualOwnerScopeAndCannotMutateOrReset() {
        service.approveGrant(grant()); authenticate(PlatformRole.CUSTOMER_ADMIN);
        assertThat(service.readQuota("grant").customerId()).isEqualTo("customer");
        var foreignOwner = new PlatformUserEntity(); foreignOwner.setId("foreign"); foreignOwner.setCustomerId("other");
        foreignOwner.setRole("CUSTOMER_ADMIN"); foreignOwner.setStatus("ACTIVE");
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(foreignOwner));
        var foreignCustomer = new PlatformCustomerEntity(); foreignCustomer.setId("other"); foreignCustomer.setStatus("ACTIVE");
        when(customers.findById("other")).thenReturn(Optional.of(foreignCustomer));
        assertThatThrownBy(() -> service.readQuota("grant")).hasMessageContaining("restricted");
        assertThatThrownBy(() -> service.approveGrant(grant())).hasMessageContaining("ADMIN_REVIEW_REQUIRED");
    }

    @Test void runtimeFingerprintIsSeparateAndInitializationNeverAcceptsCpTermsHash() {
        var first = allocated(); UUID store = UUID.randomUUID();
        assertThat(first.runtimeAllocationFingerprint()).isNotEqualTo(first.approvalFingerprint());
        assertThat(jdbc.queryForObject("SELECT runtime_allocation_fingerprint FROM provider_usage_allocations", String.class))
            .isEqualTo(first.runtimeAllocationFingerprint());
        assertThatThrownBy(() -> service.markInitializedForAuthorizedRelease("release-" + first.scope().deploymentId(), store, first.approvalFingerprint()))
            .hasMessageContaining("INITIALIZATION_FINGERPRINT_CHANGED");
        assertThat(service.readAllocation(first.scope()).state()).isEqualTo(InitializationState.UNINITIALIZED);
        service.markInitializedForAuthorizedRelease("release-" + first.scope().deploymentId(), store, first.runtimeAllocationFingerprint());
        assertThatThrownBy(() -> service.markActiveForAuthorizedRelease("release-" + first.scope().deploymentId(), store, first.approvalFingerprint()))
            .hasMessageContaining("INITIALIZATION_FINGERPRINT_CHANGED");
        var active = service.markActiveForAuthorizedRelease("release-" + first.scope().deploymentId(), store, first.runtimeAllocationFingerprint());
        assertThat(active.initializationFingerprint()).isEqualTo(first.runtimeAllocationFingerprint());
        assertThat(jdbc.queryForList("SELECT payload_fingerprint FROM provider_usage_allowance_events WHERE subject_id=?", String.class, first.approvalId()))
            .containsOnly(first.approvalFingerprint());
    }

    @Test void storedRuntimeFingerprintCannotSubstituteForIndependentRecomputation() {
        var first = allocated(); String forged = "0".repeat(64);
        jdbc.update("UPDATE provider_usage_allocations SET runtime_allocation_fingerprint=? WHERE approval_id=?", forged, first.approvalId());
        assertThatThrownBy(() -> service.markInitializedForAuthorizedRelease("release-" + first.scope().deploymentId(), UUID.randomUUID(), forged))
            .hasMessageContaining("ALLOCATION_RECORD_CHANGED");
        assertThatThrownBy(() -> service.readAllocation(first.scope())).hasMessageContaining("ALLOCATION_RECORD_CHANGED");
        assertThat(jdbc.queryForObject("SELECT initialization_state FROM provider_usage_allocations", String.class)).isEqualTo("UNINITIALIZED");
    }

    @Test void allocationLookupAndRawProjectionAreStaffOnlyAndDeploymentAssigned() {
        var first = allocated();
        assertThat(service.findForDeployment("deployment", "target")).contains(first);
        assertThat(service.findForDeployment("deployment", "other-target")).isEmpty();
        for (PlatformRole role : List.of(PlatformRole.CUSTOMER_ADMIN, PlatformRole.PUBLIC_API_CLIENT)) {
            authenticate(role);
            assertThatThrownBy(() -> service.readAllocation(first.scope())).hasMessageContaining("TRUSTED_SERVER_REQUIRED");
            assertThatThrownBy(() -> service.findForDeployment("deployment", "target")).hasMessageContaining("TRUSTED_SERVER_REQUIRED");
        }
        authenticate(PlatformRole.PLATFORM_OPERATOR);
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "fixture-unassigned"))
            .when(deploymentAccess).requireDeploymentOperatorAccess(deploymentRows.get("deployment"));
        assertThatThrownBy(() -> service.readAllocation(first.scope())).hasMessageContaining("fixture-unassigned");
        assertThatThrownBy(() -> service.findForDeployment("deployment", "target")).hasMessageContaining("fixture-unassigned");
    }

    @Test void lookupNeverSelectsAnArbitraryGrantWhenMultipleReviewedAllocationsExist() {
        allocated(); var original = grant();
        var second = new ProviderUsageGrant("second-grant", "second-campaign", original.customerId(), original.targetProfileId(),
            original.environment(), original.credits(), original.expiresAt(), original.binding(), original.rateCardReference(),
            original.ownerApprovalEvidenceRef(), original.payerRef());
        service.approveGrant(second); var command = command();
        seedHistoricalAllocation(new AllocationCommand(second.id(), command.deploymentId(), command.targetProfileId(), command.caps(),
            command.binding(), command.rateCardReference(), command.expectedCredentialSha256()));
        assertThatThrownBy(() -> service.findForDeployment("deployment", "target")).hasMessageContaining("ALLOCATION_AMBIGUOUS");
    }

    void authorizeRelease(String deployment) {
        if (jdbc.queryForObject("SELECT count(*) FROM platform_deployment_releases WHERE id=?", Integer.class, "release-" + deployment) != 0) return;
        jdbc.update("""
            INSERT INTO platform_deployment_releases
              (id,deployment_id,deployment_version_id,target_profile_id,status,provisioning_status,source_artifact_id)
              VALUES (?,?,?,?,?,?,?)
            """, "release-" + deployment, deployment, "version-" + deployment, "target", "VERIFYING", "ACTIVE", "artifact");
        ProviderUsageCoreReleaseTestSchema.authorize(jdbc, deployment, "FUNDED");
    }

    // Immutable pre-extraction financial records are fixtures, not an alternate issuance API.
    SafeAllowanceProjection seedHistoricalAllocation(AllocationCommand command) {
        var grant = codec.read(jdbc.queryForObject("SELECT payload_json FROM provider_usage_grants WHERE id=?",
            String.class, command.grantId()), ProviderUsageGrant.class);
        String id = "historical-" + UUID.randomUUID();
        var scope = new AllocationScope(id, grant.customerId(), command.deploymentId(), command.targetProfileId(), grant.environment());
        var terms = new AllocationTerms(id, grant.id(), scope, command.caps(), grant.expiresAt(), command.rateCardReference(),
            command.binding(), command.expectedCredentialSha256(), r7(command.deploymentId()));
        jdbc.update("""
            INSERT INTO provider_usage_allocations
              (approval_id,grant_id,customer_id,deployment_id,target_profile_id,environment_name,approval_fingerprint,
               runtime_allocation_fingerprint,payload_json,initialization_state,created_at)
              VALUES (?,?,?,?,?,?,?,?,?,'UNINITIALIZED',?)
            """, id, grant.id(), scope.customerId(), scope.deploymentId(), scope.targetProfileId(), scope.environment(),
            codec.fingerprint(terms), ProviderUsageRuntimeAllocationIdentity.fingerprint(terms), codec.write(terms), java.sql.Timestamp.from(NOW));
        jdbc.update("""
            UPDATE provider_usage_grants SET allocated_chat_attempts=allocated_chat_attempts+?,
              allocated_total_tokens=allocated_total_tokens+?,allocated_cost_nano_usd=allocated_cost_nano_usd+? WHERE id=?
            """, command.caps().chatAttempts(), command.caps().totalTokens(), command.caps().costNanoUsd(), grant.id());
        jdbc.update("""
            INSERT INTO provider_usage_allowance_events
              (id,event_type,subject_id,grant_id,actor_id,actor_role,payload_fingerprint,created_at)
              VALUES (?,'ALLOWANCE_ALLOCATED',?,?,'historical-fixture','TEST',?,?)
            """, UUID.randomUUID().toString(), id, grant.id(), codec.fingerprint(terms), java.sql.Timestamp.from(NOW));
        return service.readAllocation(scope);
    }

    @Test void authorizedWorkerNeedsNoAmbientPrincipalAndDebitsWholeReviewedGrant() {
        var grant = runtimeGrant(); service.approveGrant(grant); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        assertThat(service.findForAuthorizedRelease("release-deployment")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
        var allowance = service.prepareForAuthorizedRelease("release-deployment");
        assertThat(allowance.caps()).isEqualTo(grant.credits());
        assertThat(allowance.binding()).isEqualTo(grant.binding());
        assertThat(allowance.expiresAt()).isEqualTo(grant.expiresAt());
        assertThat(instance(NOW).prepareForAuthorizedRelease("release-deployment")).isEqualTo(allowance);
        assertThat(instance(NOW).findForAuthorizedRelease("release-deployment")).contains(allowance);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allowance_events WHERE event_type='ALLOWANCE_ALLOCATED'", Integer.class)).isEqualTo(1);
        authenticate(PlatformRole.PLATFORM_ADMIN);
        assertThat(service.readQuota(grant.id()).lifetimeAllocated()).isEqualTo(grant.credits());
        assertThat(service.readQuota(grant.id()).remaining()).isEqualTo(new Limits(0, 0, 0));
        SecurityContextHolder.clearContext();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(deploymentAccess);
        assertThat(jdbc.queryForList("SELECT actor_id FROM provider_usage_allowance_events WHERE event_type='ALLOWANCE_ALLOCATED'", String.class))
            .containsExactly("system:provider-usage-release");
        assertThat(jdbc.queryForObject("SELECT actor_role FROM provider_usage_allowance_events WHERE event_type='ALLOWANCE_ALLOCATED'", String.class)).isEqualTo("SYSTEM");
    }

    @Test void authorizedWorkerInitializationRequiresAppliedRuntimeAndExactNativeReceipt() {
        service.approveGrant(runtimeGrant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        var allowance = service.prepareForAuthorizedRelease("release-deployment"); UUID store = UUID.randomUUID();
        jdbc.update("UPDATE platform_deployment_releases SET status='PROVISIONING',provisioning_status='RUNNING'");
        assertThatThrownBy(() -> service.markInitializedForAuthorizedRelease("release-deployment", store, allowance.runtimeAllocationFingerprint()))
            .hasMessageContaining("RELEASE_PHASE_DENIED");
        jdbc.update("UPDATE platform_deployment_releases SET status='VERIFYING',provisioning_status='ACTIVE'");
        assertThatThrownBy(() -> service.markInitializedForAuthorizedRelease("release-deployment", store, allowance.approvalFingerprint()))
            .hasMessageContaining("INITIALIZATION_FINGERPRINT_CHANGED");
        service.markInitializedForAuthorizedRelease("release-deployment", store, allowance.runtimeAllocationFingerprint());
        var active = instance(NOW).markActiveForAuthorizedRelease("release-deployment", store, allowance.runtimeAllocationFingerprint());
        assertThat(active.state()).isEqualTo(InitializationState.ACTIVE); assertThat(active.storeUuid()).isEqualTo(store);
        assertThat(service.markActiveForAuthorizedRelease("release-deployment", store, allowance.runtimeAllocationFingerprint())).isEqualTo(active);
        assertThatThrownBy(() -> service.markInitializedForAuthorizedRelease("release-deployment", UUID.randomUUID(), allowance.runtimeAllocationFingerprint()))
            .hasMessageContaining("STORE_REPLACEMENT_FORBIDDEN");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull(); verifyNoInteractions(deploymentAccess);
    }

    @Test void authorizedWorkerDeniesMissingAndExpiredGrantWithoutMintingAnything() {
        authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("REVIEWED_GRANT_REQUIRED");
        authenticate(PlatformRole.PLATFORM_ADMIN); service.approveGrant(runtimeGrant()); SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> instance(grant().expiresAt()).prepareForAuthorizedRelease("release-deployment"))
            .hasMessageContaining("REVIEWED_GRANT_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
    }

    @Test void authorizedWorkerDeniesAmbiguousGrantsRatherThanChoosingAConvenientBalance() {
        var first = runtimeGrant(); service.approveGrant(first);
        service.approveGrant(new ProviderUsageGrant("other-grant", "other-campaign", first.customerId(), first.targetProfileId(), first.environment(),
            first.credits(), first.expiresAt(), first.binding(), first.rateCardReference(), first.ownerApprovalEvidenceRef(), first.payerRef()));
        authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("REVIEWED_GRANT_AMBIGUOUS");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void freshDeploymentUsesOneWholeUnallocatedGrantWithoutResettingAnOlderAllocation(boolean partial) {
        var original = runtimeGrant(); service.approveGrant(original);
        var caps = partial ? new Limits(1, 1, 1) : original.credits();
        var old = seedHistoricalAllocation(new AllocationCommand(original.id(), "deployment", "target", caps,
            original.binding(), original.rateCardReference(), ProviderUsageAllowanceCodec.sha256(secretValue.get())));
        var before = service.readQuota(original.id());
        var fresh = new ProviderUsageGrant("new-grant", "new-campaign", original.customerId(), original.targetProfileId(),
            original.environment(), original.credits(), original.expiresAt(), original.binding(), original.rateCardReference(),
            original.ownerApprovalEvidenceRef(), original.payerRef());
        service.approveGrant(fresh); addDeployment("new"); authorizeRelease("new"); SecurityContextHolder.clearContext();
        assertThat(service.findForAuthorizedRelease("release-new")).isEmpty();
        var allocated = service.prepareForAuthorizedRelease("release-new");
        assertThat(allocated.caps()).isEqualTo(fresh.credits());
        assertThat(allocated.scope().deploymentId()).isEqualTo("new");
        assertThat(service.prepareForAuthorizedRelease("release-new")).isEqualTo(allocated);
        authenticate(PlatformRole.PLATFORM_ADMIN);
        assertThat(service.readQuota(original.id())).isEqualTo(before);
        assertThat(service.readAllocation(old.scope())).isEqualTo(old);
        assertThat(service.readQuota(fresh.id()).lifetimeAllocated()).isEqualTo(fresh.credits());
        assertThat(jdbc.queryForObject("SELECT grant_id FROM provider_usage_allocations WHERE approval_id=?",
            String.class, allocated.approvalId())).isEqualTo(fresh.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(2);
    }

    @Test void authorizedWorkerRequiresExactCoreReleaseScope() {
        service.approveGrant(runtimeGrant()); SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("missing")).hasMessageContaining("RELEASE_REQUIRED");
        authorizeRelease("deployment");
        jdbc.update("UPDATE platform_deployment_releases SET deployment_id='other'");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("CORE_AUTHORIZATION_SCOPE_CHANGED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
    }
    @Test void authorizedWorkerRequiresUnchangedCoreDecisionAndCannotFallBack() {
        service.approveGrant(runtimeGrant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        jdbc.update("UPDATE platform_release_authorizations SET request_fingerprint=?", "0".repeat(64));
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("CORE_AUTHORIZATION_CHANGED");
        jdbc.update("DELETE FROM platform_release_authorizations");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("CORE_AUTHORIZATION_REQUIRED");
    }
    @Test void authorizedWorkerCannotSpendDuringFailedOrSupersededRelease() {
        service.approveGrant(runtimeGrant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        for (String status : List.of("FAILED", "SUPERSEDED", "INTERRUPTED")) {
            jdbc.update("UPDATE platform_deployment_releases SET status=?", status);
            assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("RELEASE_PHASE_DENIED");
        }
    }
    @Test void authorizedWorkerRejectsPartiallyAllocatedGrantInsteadOfUsingRemainingCredits() {
        var grant = runtimeGrant(); service.approveGrant(grant);
        var split = new Limits(7, 1_000, 1_234_000);
        addDeployment("manual");
        var manual = seedHistoricalAllocation(new AllocationCommand(grant.id(), "manual", "target", split, grant.binding(),
            grant.rateCardReference(), ProviderUsageAllowanceCodec.sha256(secretValue.get())));
        authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment"))
            .hasMessageContaining("LIFETIME_CREDITS_EXCEEDED");
        assertThat(service.findForAuthorizedRelease("release-deployment")).isEmpty();
        authenticate(PlatformRole.PLATFORM_ADMIN);
        assertThat(service.readQuota(grant.id()).lifetimeAllocated()).isEqualTo(split);
        assertThat(service.readAllocation(manual.scope())).isEqualTo(manual);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
    }

    @Test void authorizedWorkerCannotEnlargeAnExistingManualSplitForTheSameDeployment() {
        var grant = runtimeGrant(); service.approveGrant(grant);
        var split = new Limits(7, 1_000, 1_234_000);
        var manual = seedHistoricalAllocation(new AllocationCommand(grant.id(), "deployment", "target", split, grant.binding(),
            grant.rateCardReference(), ProviderUsageAllowanceCodec.sha256(secretValue.get())));
        authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("ALLOCATION_IMMUTABLE");
        assertThatThrownBy(() -> service.findForAuthorizedRelease("release-deployment")).hasMessageContaining("ALLOCATION_IMMUTABLE");
        authenticate(PlatformRole.PLATFORM_ADMIN);
        assertThat(service.readAllocation(manual.scope())).isEqualTo(manual);
        assertThat(service.readQuota(grant.id()).lifetimeAllocated()).isEqualTo(split);
    }

    @Test void twoAuthorizedDeploymentsRaceForOneWholeGrantWithoutAmbientPrincipals() throws Exception {
        var grant = runtimeGrant(); service.approveGrant(grant); addDeployment("second");
        authorizeRelease("deployment"); authorizeRelease("second");
        var releases = List.of("release-deployment", "release-second");
        var results = race(2, index -> () -> {
            SecurityContextHolder.clearContext();
            try { return instance(NOW).prepareForAuthorizedRelease(releases.get(index)); }
            catch (ProviderUsageAllowanceFailure failure) {
                assertThat(failure.getReason()).isEqualTo("PROVIDER_USAGE_LIFETIME_CREDITS_EXCEEDED");
                return null;
            }
        });
        var winners = results.stream().filter(java.util.Objects::nonNull).toList();
        assertThat(winners).hasSize(1);
        var winner = winners.getFirst();
        assertThat(winner.caps()).isEqualTo(grant.credits());
        assertThat(instance(NOW).prepareForAuthorizedRelease("release-" + winner.scope().deploymentId())).isEqualTo(winner);
        assertThat(instance(NOW).findForAuthorizedRelease("release-" + winner.scope().deploymentId())).contains(winner);
        assertThat(service.readQuota(grant.id()).lifetimeAllocated()).isEqualTo(grant.credits());
        assertThat(service.readQuota(grant.id()).remaining()).isEqualTo(new Limits(0, 0, 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allowance_events WHERE event_type='ALLOWANCE_ALLOCATED'", Integer.class)).isEqualTo(1);
    }

    @Test void authorizedWorkerCannotRefundOrReuseWholeGrantAfterRetirementAndRecreation() {
        var grant = runtimeGrant(); service.approveGrant(grant); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        var original = service.prepareForAuthorizedRelease("release-deployment");
        jdbc.update("UPDATE platform_deployment_releases SET status='FAILED' WHERE id='release-deployment'");
        deploymentRows.remove("deployment");
        addDeployment("replacement"); authorizeRelease("replacement");
        assertThatThrownBy(() -> instance(NOW).prepareForAuthorizedRelease("release-replacement"))
            .hasMessageContaining("LIFETIME_CREDITS_EXCEEDED");
        assertThat(instance(NOW).findForAuthorizedRelease("release-replacement")).isEmpty();
        authenticate(PlatformRole.PLATFORM_ADMIN);
        assertThat(service.readAllocation(original.scope())).isEqualTo(original);
        assertThat(service.readQuota(grant.id()).remaining()).isEqualTo(new Limits(0, 0, 0));
        assertThat(service.approveGrant(grant).lifetimeAllocated()).isEqualTo(grant.credits());
    }

    @Test void unsupportedRuntimeGrantAndLegacySplitFailBeforeDebit() {
        var original = grant();
        var unsupported = new ProviderUsageGrant(original.id(), original.campaignId(), original.customerId(), original.targetProfileId(),
            original.environment(), original.credits(), original.expiresAt(), new ProviderBinding("fixture", List.of("unreviewed"), "OPENAI_API_KEY", null),
            "fixture-price", original.ownerApprovalEvidenceRef(), original.payerRef());
        service.approveGrant(unsupported); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("RUNTIME_PROFILE_NOT_REVIEWED");
        authenticate(PlatformRole.PLATFORM_ADMIN);
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("LEGACY_ISSUANCE_UNSUPPORTED");
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(new Limits(0, 0, 0));
    }
    @Test void automaticPreparationUsesExplicitPublishedCapAndPurposeInheritanceWithoutChangingConfiguration() {
        var version = versionRows.get("version-deployment"); String original = version.getProviderConfigJson();
        assertThat(ProviderUsageRuntimeConfiguration.require(original, codec))
            .isEqualTo(new ProviderUsageRuntimeConfiguration.OutputCaps(2048, 2048, 2048));
        service.approveGrant(runtimeGrant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        var allowance = service.prepareForAuthorizedRelease("release-deployment");
        assertThat(allowance.caps()).isEqualTo(runtimeGrant().credits());
        assertThat(version.getProviderConfigJson()).isEqualTo(original);
        assertThat(service.findForAuthorizedRelease("release-deployment")).contains(allowance);
    }

    @Test void automaticPreparationAcceptsExplicitPurposeCapsAndSupportedProviderModels() {
        var config = providerConfiguration();
        config.put("orchestrationLlmProvider", "openai").put("orchestrationModel", "gpt-4o-mini")
            .put("orchestrationMaxTokens", 1024).put("generationLlmProvider", "openai")
            .put("generationModel", "gpt-4o-mini").put("generationMaxTokens", 300);
        String original = codec.write(config); versionRows.get("version-deployment").setProviderConfigJson(original);
        assertThat(ProviderUsageRuntimeConfiguration.require(original, codec))
            .isEqualTo(new ProviderUsageRuntimeConfiguration.OutputCaps(2048, 1024, 300));
        service.approveGrant(runtimeGrant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        assertThat(service.prepareForAuthorizedRelease("release-deployment").caps()).isEqualTo(runtimeGrant().credits());
        assertThat(versionRows.get("version-deployment").getProviderConfigJson()).isEqualTo(original);
    }

    @Test void automaticPreparationRejectsMissingAndNullPrimaryCapBeforeAnyDebit() {
        service.approveGrant(runtimeGrant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        var missing = providerConfiguration(); missing.remove("openaiMaxTokens");
        for (var config : List.of(missing, missing.deepCopy().putNull("openaiMaxTokens"))) {
            versionRows.get("version-deployment").setProviderConfigJson(codec.write(config));
            assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment"))
                .isInstanceOf(ProviderUsageAllowanceFailure.class).hasMessageContaining("RUNTIME_OUTPUT_CAP_REQUIRED");
            assertNoAutomaticDebit();
        }
    }

    @Test void automaticPreparationRejectsInvalidDeclaredCapsIncludingZeroOverflowAndPurposeOverrides() {
        service.approveGrant(runtimeGrant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        for (String field : List.of("openaiMaxTokens", "orchestrationMaxTokens", "generationMaxTokens")) {
            for (Object value : List.of(0, -1, 16_385, Long.MAX_VALUE, 2048.5, "not-a-number", "99999999999999999999", true)) {
                var config = providerConfiguration(); config.put("openaiMaxTokens", 2048);
                config.set(field, mapper.valueToTree(value));
                versionRows.get("version-deployment").setProviderConfigJson(codec.write(config));
                assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment"))
                    .as("%s=%s", field, value).isInstanceOf(ProviderUsageAllowanceFailure.class)
                    .hasMessageContaining("RUNTIME_OUTPUT_CAP_INVALID");
                assertNoAutomaticDebit();
            }
        }
    }

    @Test void automaticPreparationAcceptsTokenCapBoundariesAndNullPurposeInheritance() {
        var config = providerConfiguration();
        config.put("openaiMaxTokens", "2048").put("orchestrationMaxTokens", 1).put("generationMaxTokens", 16_384);
        assertThat(ProviderUsageRuntimeConfiguration.require(codec.write(config), codec))
            .isEqualTo(new ProviderUsageRuntimeConfiguration.OutputCaps(2048, 1, 16_384));
        config.putNull("orchestrationMaxTokens").putNull("generationMaxTokens");
        assertThat(ProviderUsageRuntimeConfiguration.require(codec.write(config), codec))
            .isEqualTo(new ProviderUsageRuntimeConfiguration.OutputCaps(2048, 2048, 2048));
        versionRows.get("version-deployment").setProviderConfigJson(codec.write(config));
        service.approveGrant(runtimeGrant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        assertThat(service.prepareForAuthorizedRelease("release-deployment").caps()).isEqualTo(runtimeGrant().credits());
    }

    @Test void automaticPreparationRejectsUnsupportedVersionProviderOrModelBeforeDebit() {
        service.approveGrant(runtimeGrant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        var cases = List.of(Map.entry("llmProvider", "anthropic"), Map.entry("llmProvider", "unrecognized-provider"),
            Map.entry("orchestrationLlmProvider", "gemini"), Map.entry("generationLlmProvider", "unrecognized-provider"),
            Map.entry("embeddingProvider", "worker"), Map.entry("embeddingProvider", "azure"),
            Map.entry("openaiModel", "gpt-4o"), Map.entry("orchestrationModel", "gpt-5.4-nano"),
            Map.entry("generationModel", "gpt-5.4-mini"), Map.entry("openaiEmbeddingModel", "text-embedding-3-large"));
        for (var entry : cases) {
            var config = providerConfiguration(); config.put(entry.getKey(), entry.getValue());
            versionRows.get("version-deployment").setProviderConfigJson(codec.write(config));
            assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment"))
                .as("%s=%s", entry.getKey(), entry.getValue()).isInstanceOf(ProviderUsageAllowanceFailure.class)
                .hasMessageContaining(entry.getKey().endsWith("Model") ? "RUNTIME_MODEL_NOT_REVIEWED" : "RUNTIME_PROVIDER_NOT_REVIEWED");
            assertNoAutomaticDebit();
        }
    }

    @Test void automaticPreparationRejectsUnreviewedRecoveryAndEndpointsBeforeDebit() {
        service.approveGrant(runtimeGrant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        var fallback = providerConfiguration(); fallback.put("enableFallback", true);
        var recovery = providerConfiguration(); recovery.putArray("generationFallbackProviders").add("openai");
        var embeddingRecovery = providerConfiguration(); embeddingRecovery.putArray("embeddingFallbackProviders").add("onnx");
        for (var config : List.of(fallback, recovery, embeddingRecovery)) {
            versionRows.get("version-deployment").setProviderConfigJson(codec.write(config));
            assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment"))
                .isInstanceOf(ProviderUsageAllowanceFailure.class).hasMessageContaining("RUNTIME_RECOVERY_NOT_REVIEWED");
            assertNoAutomaticDebit();
        }
        for (String field : List.of("openaiBaseUrl", "openaiEmbeddingBaseUrl", "embeddingBaseUrl", "orchestrationBaseUrl", "generationBaseUrl")) {
            var config = providerConfiguration(); config.put(field, "https://not-reviewed.example.test/v1");
            versionRows.get("version-deployment").setProviderConfigJson(codec.write(config));
            assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment"))
                .isInstanceOf(ProviderUsageAllowanceFailure.class).hasMessageContaining("RUNTIME_ENDPOINT_NOT_REVIEWED");
            assertNoAutomaticDebit();
        }
    }

    @Test void automaticPreparationRejectsMalformedProviderConfigurationWithoutInventingDefaults() {
        service.approveGrant(runtimeGrant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        for (String value : List.of("not-json", "null", "[]", "true")) {
            versionRows.get("version-deployment").setProviderConfigJson(value);
            assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment"))
                .isInstanceOf(ProviderUsageAllowanceFailure.class).hasMessageContaining("RUNTIME_CONFIG_INVALID");
            assertNoAutomaticDebit();
        }
    }

    @Test void legacyStaffSplitCannotBypassRuntimeConfigurationReview() {
        versionRows.get("version-deployment").setProviderConfigJson("{}"); service.approveGrant(grant()); authorizeRelease("deployment");
        assertThatThrownBy(() -> service.allocate(command("deployment", new Limits(7, 1000, 1_234_000)), r7("deployment")))
            .hasMessageContaining("LEGACY_ISSUANCE_UNSUPPORTED");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("RUNTIME_OUTPUT_CAP_REQUIRED");
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(new Limits(0, 0, 0));
    }
    private com.fasterxml.jackson.databind.node.ObjectNode providerConfiguration() {
        return (com.fasterxml.jackson.databind.node.ObjectNode) codec.read(
            DECLARED_PROVIDER_CONFIG, com.fasterxml.jackson.databind.JsonNode.class);
    }

    private void assertNoAutomaticDebit() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allowance_events WHERE event_type='ALLOWANCE_ALLOCATED'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT allocated_chat_attempts FROM provider_usage_grants WHERE id='grant'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT allocated_total_tokens FROM provider_usage_grants WHERE id='grant'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT allocated_cost_nano_usd FROM provider_usage_grants WHERE id='grant'", Long.class)).isZero();
    }

    <T> List<T> race(int count, java.util.function.IntFunction<Callable<T>> work) throws Exception {
        var ready = new CountDownLatch(count); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(count)) {
            var pending = new ArrayList<java.util.concurrent.Future<T>>();
            for (int index = 0; index < count; index++) {
                Callable<T> task = work.apply(index);
                pending.add(pool.submit(() -> {
                    authenticate(PlatformRole.PLATFORM_ADMIN); ready.countDown();
                    try { assertThat(start.await(20, TimeUnit.SECONDS)).isTrue(); return task.call(); }
                    finally { SecurityContextHolder.clearContext(); }
                }));
            }
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue(); start.countDown();
            var result = new ArrayList<T>(); for (var future : pending) result.add(future.get(30, TimeUnit.SECONDS)); return result;
        } finally { start.countDown(); }
    }
}
