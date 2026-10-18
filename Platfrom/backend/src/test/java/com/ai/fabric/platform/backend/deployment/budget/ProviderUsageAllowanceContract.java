package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetProfileIdentity;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    final ProviderBinding binding = new ProviderBinding("fixture-provider", List.of("chat-model", "embedding-model"), "OPENAI_API_KEY", null);
    DataSource source; JdbcTemplate jdbc; ProviderUsageAllowanceService service;
    DeploymentTargetProfileEntity profile; PlatformCustomerEntity customer;

    abstract DataSource freshDataSource() throws Exception;

    @BeforeEach void initialize() throws Exception {
        source = freshDataSource(); jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE deployment_admission_mutex (id INTEGER PRIMARY KEY)"); jdbc.update("INSERT INTO deployment_admission_mutex VALUES (1)");
        jdbc.execute("""
            CREATE TABLE deployment_capacity_reservations
            (id VARCHAR(64) PRIMARY KEY,customer_id VARCHAR(64),deployment_id VARCHAR(64),target_profile_id VARCHAR(64),
             generation VARCHAR(64),state VARCHAR(32),payload_json TEXT)
            """);
        jdbc.execute("CREATE TABLE deployment_admission_reviews (kind VARCHAR(16),record_key VARCHAR(128),payload_json TEXT,PRIMARY KEY(kind,record_key))");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V178__admission_allocation_authorizations.sql")).execute(source);
        jdbc.execute("""
            CREATE TABLE platform_deployment_releases
            (id VARCHAR(64) PRIMARY KEY,deployment_id VARCHAR(64),deployment_version_id VARCHAR(64),target_profile_id VARCHAR(64),
             status VARCHAR(64),provisioning_status VARCHAR(64))
            """);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V190__provider_usage_allowance.sql")).execute(source);
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
        new Limits(50, 200_000, 2_000_000_000L), NOW.plusSeconds(3600), binding, "fixture-rate-card", "fixture-owner-approval", "fixture-payer"); }
    AllocationCommand command(String deployment, Limits caps) { return new AllocationCommand("grant", deployment, "target", caps, binding,
        "fixture-rate-card", ProviderUsageAllowanceCodec.sha256(secretValue.get())); }
    AllocationCommand command() { return command("deployment", grant().credits()); }
    ReviewedR7Context r7(String deployment) { return new ReviewedR7Context("reservation-" + deployment, "generation", "version-" + deployment); }
    SafeAllowanceProjection allocated() { service.approveGrant(grant()); return service.allocate(command(), r7("deployment")); }

    void addDeployment(String id) {
        var deployment = new DeploymentEntity(); deployment.setId(id); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        deployment.setStatus("ACTIVE"); deployment.setEnvironmentName("control-plane-label-not-authority"); deploymentRows.put(id, deployment);
        var version = new DeploymentVersionEntity(); version.setId("version-" + id); version.setDeploymentId(id); version.setStatus("PUBLISHED");
        version.setConfigHash("config-hash"); versionRows.put(version.getId(), version);
        var manifest = new AdmissionContracts.Manifest("manifest-" + id, id, version.getId(), version.getConfigHash(), "target", "binding", "fixture-topology",
            new AdmissionContracts.Vector(Map.of(AdmissionContracts.Dimension.API_REQUESTS_PER_WINDOW, 50L,
                AdmissionContracts.Dimension.PROVIDER_TOKENS_PER_WINDOW, 200_000L,
                AdmissionContracts.Dimension.PROVIDER_SPEND_MICROS_PER_WINDOW, 2_000_000L)), Set.of("fixture-evidence"), Map.of());
        String manifestFingerprint = codec.fingerprint(manifest);
        jdbc.update("INSERT INTO deployment_admission_reviews VALUES ('MANIFEST',?,?)", manifest.id(), codec.write(manifest));
        var evidence = new AdmissionContracts.PrerequisiteReadback(NOW, NOW.plusSeconds(120), "fixture-prerequisite",
            new AdmissionContracts.Placement("fixture-server", "fixture-destination", "fixture-ip"),
            DeploymentTargetProfileIdentity.fingerprint(mapper, profile), manifestFingerprint, "binding", Set.of("fixture-evidence"),
            "fixture-restore", "fixture-privacy", "fixture-owner", "fixture-notices");
        Map<String, Object> value = new HashMap<>();
        value.put("id", "reservation-" + id); value.put("customerId", "customer"); value.put("tenantId", "tenant"); value.put("deploymentId", id);
        value.put("targetProfileId", "target"); value.put("generation", "generation"); value.put("state", "RESERVED"); value.put("manifestId", manifest.id());
        value.put("manifestFingerprint", manifestFingerprint); value.put("bindingFingerprint", "binding"); value.put("expiresAt", NOW.plusSeconds(7200));
        value.put("intent", "CUSTOMER_STAGING"); value.put("prerequisiteReadback", evidence);
        value.put("expiryState", "CURRENT"); value.put("demand", manifest.demand());
        var reservation = mapper.convertValue(value, AdmissionContracts.Reservation.class);
        jdbc.update("INSERT INTO deployment_capacity_reservations VALUES (?,?,?,?,?,?,?)", reservation.id(), reservation.customerId(), id, "target", "generation",
            reservation.state().name(), codec.write(reservation));
    }

    void changeR7(String deployment, String field, Object value) {
        String id = "reservation-" + deployment;
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(codec.read(
            jdbc.queryForObject("SELECT payload_json FROM deployment_capacity_reservations WHERE id=?", String.class, id), AdmissionContracts.Reservation.class));
        tree.set(field, mapper.valueToTree(value));
        jdbc.update("UPDATE deployment_capacity_reservations SET payload_json=? WHERE id=?", codec.write(tree), id);
        if (field.equals("state")) jdbc.update("UPDATE deployment_capacity_reservations SET state=? WHERE id=?", value.toString(), id);
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
        assertThat(instance(NOW).allocate(command(), r7("deployment"))).isEqualTo(first);
        assertThat(service.approveGrant(grant()).lifetimeAllocated()).isEqualTo(grant().credits());
        assertThat(service.readQuota("grant").remaining()).isEqualTo(new Limits(0, 0, 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allowance_events WHERE event_type='ALLOWANCE_ALLOCATED'", Integer.class)).isEqualTo(1);
    }

    @Test void allocationCannotMintCreditsWithoutRealGrantAndR7Proof() {
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("GRANT_NOT_FOUND");
        service.approveGrant(grant());
        assertThatThrownBy(() -> service.allocate(command(), new ReviewedR7Context("missing", "generation", "version-deployment"))).hasMessageContaining("R7_REQUIRED");
        assertThatThrownBy(() -> service.allocate(command(), new ReviewedR7Context("reservation-deployment", "wrong-generation", "version-deployment"))).hasMessageContaining("R7_REQUIRED");
        assertThatThrownBy(() -> service.allocate(command(), new ReviewedR7Context("reservation-deployment", "generation", "other-version"))).hasMessageContaining("VERSION_NOT_FOUND");
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(new Limits(0, 0, 0));
    }

    @Test void rawCustomerCommandsAndUnassignedOperatorsCannotAllocate() {
        service.approveGrant(grant()); authenticate(PlatformRole.CUSTOMER_ADMIN);
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("TRUSTED_SERVER_REQUIRED");
        authenticate(PlatformRole.PLATFORM_OPERATOR);
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "fixture-unassigned"))
            .when(deploymentAccess).requireDeploymentOperatorAccess(deploymentRows.get("deployment"));
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("fixture-unassigned");
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(new Limits(0, 0, 0));
    }

    @Test void profileEnvironmentAndActualDeploymentOwnershipCannotBeSpoofed() {
        service.approveGrant(grant()); profile.setEnvironmentName("production");
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("TARGET_SCOPE_DENIED");
        profile.setEnvironmentName("staging"); deploymentRows.get("deployment").setCustomerId("other");
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("DEPLOYMENT_SCOPE_DENIED");
        deploymentRows.get("deployment").setCustomerId("customer"); customer.setStatus("DISABLED");
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("CUSTOMER_INACTIVE");
    }

    @Test void archivedOrRetiringDeploymentAndReleasedR7CannotReceiveNewCredits() {
        service.approveGrant(grant()); deploymentRows.get("deployment").setArchivedAt(NOW);
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("DEPLOYMENT_SCOPE_DENIED");
        deploymentRows.get("deployment").setArchivedAt(null); deploymentRows.get("deployment").setDeletionRequestedAt(NOW);
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("DEPLOYMENT_SCOPE_DENIED");
        deploymentRows.get("deployment").setDeletionRequestedAt(null); changeR7("deployment", "state", "RELEASED");
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("ACTIVE_R7_REQUIRED");
    }

    @Test void targetConfigurationAndReviewedVersionDriftDenyWithoutDebiting() {
        service.approveGrant(grant()); profile.setNetworkPolicyJson("{\"changed\":true}");
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("R7_PROFILE_CHANGED");
        profile.setNetworkPolicyJson("{}"); versionRows.get("version-deployment").setConfigHash("changed");
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("REVIEWED_VERSION_CHANGED");
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(new Limits(0, 0, 0));
    }

    @Test void modelPricingCapAndActualCredentialChangesCannotAlterAnAllocation() {
        var first = allocated(); var original = command();
        var otherModel = new ProviderBinding(binding.provider(), List.of("other-model"), binding.secretPurpose(), null);
        assertThatThrownBy(() -> service.allocate(new AllocationCommand(original.grantId(), original.deploymentId(), original.targetProfileId(),
            original.caps(), otherModel, original.rateCardReference(), original.expectedCredentialSha256()), r7("deployment"))).hasMessageContaining("BINDING_CHANGED");
        assertThatThrownBy(() -> service.allocate(new AllocationCommand(original.grantId(), original.deploymentId(), original.targetProfileId(),
            original.caps(), binding, "other-price", original.expectedCredentialSha256()), r7("deployment"))).hasMessageContaining("BINDING_CHANGED");
        assertThatThrownBy(() -> service.allocate(command("deployment", new Limits(1, 1, 1)), r7("deployment"))).hasMessageContaining("ALLOCATION_IMMUTABLE");
        secretValue.set("rotated-fictional-credential");
        assertThatThrownBy(() -> service.allocate(original, r7("deployment"))).hasMessageContaining("CREDENTIAL_CHANGED");
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("ALLOCATION_IMMUTABLE");
        assertThat(service.readAllocation(first.scope())).isEqualTo(first);
        assertThat(codec.write(first)).doesNotContain("fictional-provider-credential").doesNotContain("rotated-fictional-credential");
    }

    @Test void missingActualCredentialNeverProducesAFakeHashOrAllocation() {
        service.approveGrant(grant()); var command = command(); secretValue.set(null);
        assertThatThrownBy(() -> service.allocate(command, r7("deployment"))).hasMessageContaining("CREDENTIAL_UNRESOLVED");
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(new Limits(0, 0, 0));
    }

    @Test void retirementAndRemovalNeverRefundOrDeleteLifetimeProviderCredits() {
        var first = allocated(); changeR7("deployment", "state", "RELEASED");
        deploymentRows.remove("deployment"); jdbc.update("DELETE FROM deployment_capacity_reservations WHERE id='reservation-deployment'");
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(grant().credits());
        assertThat(service.readAllocation(first.scope())).isEqualTo(first);
        addDeployment("replacement");
        assertThatThrownBy(() -> service.allocate(command("replacement", grant().credits()), r7("replacement"))).hasMessageContaining("LIFETIME_CREDITS_EXCEEDED");
        assertThat(service.readQuota("grant").remaining()).isEqualTo(new Limits(0, 0, 0));
    }

    @Test void twoPhaseInitializationAndActivationHaveExactIdempotentStoreReceipts() {
        var first = allocated(); UUID store = UUID.randomUUID();
        assertThatThrownBy(() -> service.markActive(first.scope(), store, first.runtimeAllocationFingerprint())).hasMessageContaining("INITIALIZATION_REQUIRED");
        assertThatThrownBy(() -> service.markInitialized(first.scope(), store, "0".repeat(64))).hasMessageContaining("FINGERPRINT_CHANGED");
        var initialized = service.markInitialized(first.scope(), store, first.runtimeAllocationFingerprint());
        assertThat(initialized.state()).isEqualTo(InitializationState.INITIALIZED); assertThat(initialized.storeUuid()).isEqualTo(store);
        assertThat(instance(NOW).markInitialized(first.scope(), store, first.runtimeAllocationFingerprint())).isEqualTo(initialized);
        var active = service.markActive(first.scope(), store, first.runtimeAllocationFingerprint());
        assertThat(active.state()).isEqualTo(InitializationState.ACTIVE);
        assertThat(service.markActive(first.scope(), store, first.runtimeAllocationFingerprint())).isEqualTo(active);
        assertThat(service.markInitialized(first.scope(), store, first.runtimeAllocationFingerprint())).isEqualTo(active);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allowance_events WHERE subject_id=?", Integer.class, first.approvalId())).isEqualTo(3);
    }

    @Test void storeLossCannotBeAutomaticallyReinitializedOrRefunded() {
        var first = allocated(); UUID original = UUID.randomUUID();
        service.markInitialized(first.scope(), original, first.runtimeAllocationFingerprint()); service.markActive(first.scope(), original, first.runtimeAllocationFingerprint());
        var restarted = instance(NOW);
        assertThatThrownBy(() -> restarted.markInitialized(first.scope(), UUID.randomUUID(), first.runtimeAllocationFingerprint())).hasMessageContaining("STORE_REPLACEMENT_FORBIDDEN");
        assertThatThrownBy(() -> restarted.markActive(first.scope(), UUID.randomUUID(), first.runtimeAllocationFingerprint())).hasMessageContaining("STORE_REPLACEMENT_FORBIDDEN");
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
            assertThatThrownBy(() -> service.markInitialized(scope, UUID.randomUUID(), first.runtimeAllocationFingerprint())).hasMessageContaining("SCOPE_MISMATCH");
        }
        assertThat(service.readAllocation(first.scope())).isEqualTo(first);
    }

    @Test void expiryBlocksNewAllocationAndActivationButNeverRestoresLifetimeCredits() {
        var first = allocated(); UUID store = UUID.randomUUID(); service.markInitialized(first.scope(), store, first.runtimeAllocationFingerprint());
        var expired = instance(grant().expiresAt());
        assertThatThrownBy(() -> expired.markActive(first.scope(), store, first.runtimeAllocationFingerprint())).hasMessageContaining("ALLOWANCE_EXPIRED");
        assertThat(expired.approveGrant(grant()).lifetimeAllocated()).isEqualTo(grant().credits());
        assertThat(expired.readQuota("grant").remaining()).isEqualTo(new Limits(0, 0, 0));
    }

    @Test void concurrentDistinctDeploymentAllocationsCannotOverbookLifetimeGrant() throws Exception {
        service.approveGrant(grant());
        for (int index = 0; index < 8; index++) addDeployment("deployment-" + index);
        var results = race(8, index -> () -> {
            try { return instance(NOW).allocate(command("deployment-" + index, new Limits(20, 80_000, 800_000_000)), r7("deployment-" + index)); }
            catch (ProviderUsageAllowanceFailure failure) { assertThat(failure.getReason()).isEqualTo("PROVIDER_USAGE_LIFETIME_CREDITS_EXCEEDED"); return null; }
        });
        assertThat(results.stream().filter(java.util.Objects::nonNull).count()).isEqualTo(2);
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(new Limits(40, 160_000, 1_600_000_000));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(2);
    }

    @Test void concurrentExactAllocationRetriesDebitOnceAndReturnOneApproval() throws Exception {
        service.approveGrant(grant()); var results = race(8, index -> () -> instance(NOW).allocate(command(), r7("deployment")));
        assertThat(results).allMatch(results.getFirst()::equals);
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(grant().credits());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
    }

    @Test void failedMandatoryAuditRollsBackBothLifetimeDebitAndNewApproval() {
        service.approveGrant(grant());
        jdbc.execute("ALTER TABLE provider_usage_allowance_events ADD CONSTRAINT fixture_fail_alloc_audit CHECK (event_type <> 'ALLOWANCE_ALLOCATED')");
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(new Limits(0, 0, 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
    }

    @Test void lifecycleAuditFailureCannotLeaveAnUnreceiptedInitialization() {
        var first = allocated();
        jdbc.execute("ALTER TABLE provider_usage_allowance_events ADD CONSTRAINT fixture_fail_init_audit CHECK (event_type <> 'ALLOWANCE_INITIALIZED')");
        assertThatThrownBy(() -> service.markInitialized(first.scope(), UUID.randomUUID(), first.runtimeAllocationFingerprint()))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(service.readAllocation(first.scope())).isEqualTo(first);
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(grant().credits());
    }

    @Test void checkedArithmeticOverflowCannotWrapOrRestoreLifetimeCredits() {
        var original = grant(); var maximum = new Limits(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
        service.approveGrant(new ProviderUsageGrant(original.id(), original.campaignId(), original.customerId(), original.targetProfileId(),
            original.environment(), maximum, original.expiresAt(), original.binding(), original.rateCardReference(),
            original.ownerApprovalEvidenceRef(), original.payerRef()));
        service.allocate(command("deployment", maximum), r7("deployment")); addDeployment("second");
        assertThatThrownBy(() -> service.allocate(command("second", new Limits(1, 1, 1)), r7("second")))
            .hasMessageContaining("CREDIT_OVERFLOW");
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(maximum);
        assertThat(service.readQuota("grant").remaining()).isEqualTo(new Limits(0, 0, 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
    }

    @Test void committedLifetimeDebitSurvivesCallerRollback() throws Exception {
        service.approveGrant(grant());
        try (var outer = source.getConnection(); var statement = outer.createStatement()) {
            statement.execute("CREATE TABLE fixture_caller_work(id INTEGER PRIMARY KEY)"); outer.setAutoCommit(false);
            statement.executeUpdate("INSERT INTO fixture_caller_work VALUES (1)"); service.allocate(command(), r7("deployment")); outer.rollback();
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
        assertThatThrownBy(() -> service.markInitialized(first.scope(), store, first.approvalFingerprint()))
            .hasMessageContaining("INITIALIZATION_FINGERPRINT_CHANGED");
        assertThat(service.readAllocation(first.scope()).state()).isEqualTo(InitializationState.UNINITIALIZED);
        service.markInitialized(first.scope(), store, first.runtimeAllocationFingerprint());
        assertThatThrownBy(() -> service.markActive(first.scope(), store, first.approvalFingerprint()))
            .hasMessageContaining("INITIALIZATION_FINGERPRINT_CHANGED");
        var active = service.markActive(first.scope(), store, first.runtimeAllocationFingerprint());
        assertThat(active.initializationFingerprint()).isEqualTo(first.runtimeAllocationFingerprint());
        assertThat(jdbc.queryForList("SELECT payload_fingerprint FROM provider_usage_allowance_events WHERE subject_id=?", String.class, first.approvalId()))
            .containsOnly(first.approvalFingerprint());
    }

    @Test void storedRuntimeFingerprintCannotSubstituteForIndependentRecomputation() {
        var first = allocated(); String forged = "0".repeat(64);
        jdbc.update("UPDATE provider_usage_allocations SET runtime_allocation_fingerprint=? WHERE approval_id=?", forged, first.approvalId());
        assertThatThrownBy(() -> service.markInitialized(first.scope(), UUID.randomUUID(), forged))
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
        service.allocate(new AllocationCommand(second.id(), command.deploymentId(), command.targetProfileId(), command.caps(),
            command.binding(), command.rateCardReference(), command.expectedCredentialSha256()), r7("deployment"));
        assertThatThrownBy(() -> service.findForDeployment("deployment", "target")).hasMessageContaining("ALLOCATION_AMBIGUOUS");
    }

    void authorizeRelease(String deployment) {
        var reviewed = r7(deployment);
        var manifest = codec.read(jdbc.queryForObject("SELECT payload_json FROM deployment_admission_reviews WHERE record_key=?",
            String.class, "manifest-" + deployment), AdmissionContracts.Manifest.class);
        var scope = new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext(
            "customer", "tenant", deployment, null, "target",
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent.CUSTOMER_STAGING,
            "offer", "offer-fingerprint", 1, "placement",
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.APPLY,
            "apply-" + deployment, null, null);
        var command = new AdmissionContracts.Reserve(scope.operationKey(), manifest.id(), scope);
        String request = codec.fingerprint(Map.of("customer", "customer", "tenant", "tenant", "deployment", deployment,
            "manifestId", manifest.id(), "manifestFingerprint", codec.fingerprint(manifest), "target", "target", "customerContext", scope));
        var receipt = new AdmissionContracts.AuthorizationReceipt(reviewed.reservationId(), reviewed.generation(),
            codec.fingerprint(manifest), request, codec.fingerprint(scope), command);
        jdbc.update("""
            INSERT INTO deployment_admission_authorizations
            (customer_id,deployment_id,target_profile_id,operation_key,reservation_id,generation,operation,version_id,
             request_fingerprint,context_fingerprint,payload_json,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """, "customer", deployment, "target", scope.operationKey(), reviewed.reservationId(), reviewed.generation(), "APPLY",
            reviewed.versionId(), request, codec.fingerprint(scope), codec.write(receipt), java.sql.Timestamp.from(NOW));
        jdbc.update("""
            INSERT INTO deployment_admission_release_bindings
            (release_id,reservation_id,generation,authorization_key,version_id,version_config_hash,customer_id,tenant_id,
             deployment_id,target_profile_id,state,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, "release-" + deployment, reviewed.reservationId(), reviewed.generation(), scope.operationKey(), reviewed.versionId(),
            manifest.versionConfigHash(), "customer", "tenant", deployment, "target", "PROVISIONING", java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        jdbc.update("INSERT INTO platform_deployment_releases VALUES (?,?,?,?,?,?)", "release-" + deployment, deployment,
            reviewed.versionId(), "target", "PROVISIONING", "RUNNING");
        changeR7(deployment, "state", "PROVISIONING");
    }

    @Test void authorizedWorkerNeedsNoAmbientPrincipalAndUsesServerDerivedManifestCaps() {
        service.approveGrant(grant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        assertThat(service.findForAuthorizedRelease("release-deployment")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
        var allowance = service.prepareForAuthorizedRelease("release-deployment");
        assertThat(allowance.caps()).isEqualTo(new Limits(50, 200_000, 2_000_000_000L));
        assertThat(service.prepareForAuthorizedRelease("release-deployment")).isEqualTo(allowance);
        assertThat(service.findForAuthorizedRelease("release-deployment")).contains(allowance);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(deploymentAccess);
        assertThat(jdbc.queryForList("SELECT actor_id FROM provider_usage_allowance_events WHERE event_type='ALLOWANCE_ALLOCATED'", String.class))
            .containsExactly("system:provider-usage-release");
        assertThat(jdbc.queryForObject("SELECT actor_role FROM provider_usage_allowance_events WHERE event_type='ALLOWANCE_ALLOCATED'", String.class)).isEqualTo("SYSTEM");
    }

    @Test void authorizedWorkerInitializationRequiresAppliedRuntimeAndExactNativeReceipt() {
        service.approveGrant(grant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        var allowance = service.prepareForAuthorizedRelease("release-deployment"); UUID store = UUID.randomUUID();
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
        authenticate(PlatformRole.PLATFORM_ADMIN); service.approveGrant(grant()); SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> instance(grant().expiresAt()).prepareForAuthorizedRelease("release-deployment"))
            .hasMessageContaining("REVIEWED_GRANT_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
    }

    @Test void authorizedWorkerDeniesAmbiguousGrantsRatherThanChoosingAConvenientBalance() {
        var first = grant(); service.approveGrant(first);
        service.approveGrant(new ProviderUsageGrant("other-grant", "other-campaign", first.customerId(), first.targetProfileId(), first.environment(),
            first.credits(), first.expiresAt(), first.binding(), first.rateCardReference(), first.ownerApprovalEvidenceRef(), first.payerRef()));
        authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("REVIEWED_GRANT_AMBIGUOUS");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
    }

    @Test void authorizedWorkerDeniesMissingBindingStaleGenerationAndDifferentReleaseScope() {
        service.approveGrant(grant()); SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("missing")).hasMessageContaining("RELEASE_REQUIRED");
        authorizeRelease("deployment");
        jdbc.update("UPDATE deployment_admission_release_bindings SET generation='other'");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("R7_REQUIRED");
        jdbc.update("UPDATE deployment_admission_release_bindings SET generation='generation'");
        jdbc.update("UPDATE platform_deployment_releases SET deployment_id='other'");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("CURRENT_RELEASE_REQUIRED");
        jdbc.update("DELETE FROM deployment_admission_release_bindings");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("RELEASE_BINDING_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
    }

    @Test void authorizedWorkerRequiresExactApplyReceiptAndCurrentRelease() {
        service.approveGrant(grant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        jdbc.update("UPDATE deployment_admission_authorizations SET request_fingerprint=?", "0".repeat(64));
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("AUTHORIZATION_CHANGED");
        jdbc.update("DELETE FROM deployment_admission_authorizations");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("AUTHORIZATION_REQUIRED");
        jdbc.update("UPDATE deployment_admission_release_bindings SET state='SUPERSEDED'");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("CURRENT_RELEASE_REQUIRED");
    }

    @Test void authorizedWorkerCannotSpendDuringRetirementExpiryWorkerOrFailedRelease() {
        service.approveGrant(grant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        changeR7("deployment", "workerToken", "expiry-worker");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("ACTIVE_R7_REQUIRED");
        changeR7("deployment", "workerToken", null); changeR7("deployment", "retirementOperationId", "retire");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("ACTIVE_R7_REQUIRED");
        changeR7("deployment", "retirementOperationId", null); changeR7("deployment", "expiryState", "CLAIMED");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("CURRENT_RELEASE_REQUIRED");
        changeR7("deployment", "expiryState", "CURRENT"); jdbc.update("UPDATE platform_deployment_releases SET status='FAILED'");
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("RELEASE_PHASE_DENIED");
    }

    @Test void authorizedWorkerManifestMoneyConversionIsCheckedAndNoDefaultLimitsExist() {
        service.approveGrant(grant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        replaceReviewedDemand(Map.of(AdmissionContracts.Dimension.API_REQUESTS_PER_WINDOW, 1L,
            AdmissionContracts.Dimension.PROVIDER_TOKENS_PER_WINDOW, 1L,
            AdmissionContracts.Dimension.PROVIDER_SPEND_MICROS_PER_WINDOW, Long.MAX_VALUE));
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("MANIFEST_LIMIT_OVERFLOW");
        replaceReviewedDemand(Map.of());
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("MANIFEST_LIMITS_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
    }

    @Test void authorizedWorkerUsesReviewedManifestAmountNotTheLargerGrantBalance() {
        service.approveGrant(grant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        replaceReviewedDemand(Map.of(AdmissionContracts.Dimension.API_REQUESTS_PER_WINDOW, 7L,
            AdmissionContracts.Dimension.PROVIDER_TOKENS_PER_WINDOW, 1000L,
            AdmissionContracts.Dimension.PROVIDER_SPEND_MICROS_PER_WINDOW, 1234L));
        var allocation = service.prepareForAuthorizedRelease("release-deployment");
        assertThat(allocation.caps()).isEqualTo(new Limits(7, 1000, 1_234_000));
        assertThat(jdbc.queryForObject("SELECT allocated_cost_nano_usd FROM provider_usage_grants", Long.class)).isEqualTo(1_234_000L);
    }

    @Test void stillProvisioningButNoLongerCurrentReleaseCannotAcquireOrInitializeCredits() {
        service.approveGrant(grant()); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        jdbc.update("""
            INSERT INTO deployment_admission_release_bindings
            (release_id,reservation_id,generation,authorization_key,version_id,version_config_hash,customer_id,tenant_id,
             deployment_id,target_profile_id,state,created_at,updated_at)
            SELECT 'newer-release',reservation_id,generation,'other-command',version_id,version_config_hash,customer_id,tenant_id,
                   deployment_id,target_profile_id,'PROVISIONING',?,? FROM deployment_admission_release_bindings WHERE release_id='release-deployment'
            """, java.sql.Timestamp.from(NOW.plusSeconds(1)), java.sql.Timestamp.from(NOW.plusSeconds(1)));
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("CURRENT_RELEASE_REQUIRED");
        assertThatThrownBy(() -> service.findForAuthorizedRelease("release-deployment")).hasMessageContaining("CURRENT_RELEASE_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
    }

    void replaceReviewedDemand(Map<AdmissionContracts.Dimension, Long> amounts) {
        var manifest = codec.read(jdbc.queryForObject("SELECT payload_json FROM deployment_admission_reviews WHERE record_key='manifest-deployment'",
            String.class), AdmissionContracts.Manifest.class);
        var replacement = new AdmissionContracts.Manifest(manifest.id(), manifest.deploymentId(), manifest.versionId(), manifest.versionConfigHash(),
            manifest.targetProfileId(), manifest.bindingFingerprint(), manifest.topologyReference(), new AdmissionContracts.Vector(amounts),
            manifest.prerequisiteReferences(), manifest.customerPrerequisiteReferences());
        jdbc.update("UPDATE deployment_admission_reviews SET payload_json=? WHERE record_key=?", codec.write(replacement), manifest.id());
        var reservation = codec.read(jdbc.queryForObject("SELECT payload_json FROM deployment_capacity_reservations WHERE id='reservation-deployment'",
            String.class), AdmissionContracts.Reservation.class);
        var evidence = reservation.prerequisiteReadback();
        changeR7("deployment", "manifestFingerprint", codec.fingerprint(replacement));
        changeR7("deployment", "demand", replacement.demand());
        changeR7("deployment", "prerequisiteReadback", new AdmissionContracts.PrerequisiteReadback(evidence.observedAt(), evidence.validUntil(),
            evidence.evidenceReference(), evidence.placement(), evidence.profileFingerprint(), codec.fingerprint(replacement), evidence.bindingFingerprint(),
            evidence.verifiedPrerequisiteReferences(), evidence.restoreEvidenceReference(), evidence.privacyEvidenceReference(),
            evidence.ownerContactEvidenceReference(), evidence.noticeTransportReference()));
        jdbc.update("DELETE FROM deployment_admission_release_bindings"); jdbc.update("DELETE FROM deployment_admission_authorizations");
        jdbc.update("DELETE FROM platform_deployment_releases"); authorizeRelease("deployment");
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
