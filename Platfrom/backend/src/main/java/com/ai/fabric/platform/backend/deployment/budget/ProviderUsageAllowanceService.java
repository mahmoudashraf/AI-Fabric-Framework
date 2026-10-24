package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetProfileIdentity;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretResolutionService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceFailure.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageGrantLifecycleContracts.*;

/**
 * Lifetime financial allowance issuer. New releases are authorized by core, independently of capacity policy.
 * No model-call traffic, runtime DB bootstrap, provider calls, refunds, expiry jobs or retirement hooks.
 */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ProviderUsageAllowanceService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;
    private final ProviderUsageAllowanceCodec codec;
    private final DeploymentRepository deployments;
    private final DeploymentTargetProfileRepository targets;
    private final DeploymentVersionRepository versions;
    private final PlatformCustomerRepository customers;
    private final PlatformCustomerAccessService customerAccess;
    private final DeploymentAccessService deploymentAccess;
    private final DeploymentProviderSecretResolutionService secrets;
    private final Clock clock;

    @Autowired
    public ProviderUsageAllowanceService(DataSource source, PlatformTransactionManager manager, ObjectMapper mapper,
        DeploymentRepository deployments, DeploymentTargetProfileRepository targets, DeploymentVersionRepository versions,
        PlatformCustomerRepository customers, PlatformCustomerAccessService customerAccess, DeploymentAccessService deploymentAccess,
        DeploymentProviderSecretResolutionService secrets) {
        this(source, manager, mapper, deployments, targets, versions, customers, customerAccess, deploymentAccess, secrets, Clock.systemUTC());
    }

    ProviderUsageAllowanceService(DataSource source, PlatformTransactionManager manager, ObjectMapper mapper,
        DeploymentRepository deployments, DeploymentTargetProfileRepository targets, DeploymentVersionRepository versions,
        PlatformCustomerRepository customers, PlatformCustomerAccessService customerAccess, DeploymentAccessService deploymentAccess,
        DeploymentProviderSecretResolutionService secrets, Clock clock) {
        this.jdbc = new JdbcTemplate(source); this.mapper = mapper; this.codec = new ProviderUsageAllowanceCodec(mapper);
        this.deployments = deployments; this.targets = targets; this.versions = versions; this.customers = customers;
        this.customerAccess = customerAccess; this.deploymentAccess = deploymentAccess; this.secrets = secrets; this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public GrantQuotaView approveGrant(ProviderUsageGrant grant) {
        staff(true);
        if (grant == null) throw invalid();
        return transaction(() -> {
            jdbc.queryForObject("SELECT id FROM provider_usage_grant_mutex WHERE id=1 FOR UPDATE", Integer.class);
            var existing = grantRows(grant.id(), true);
            if (!existing.isEmpty()) {
                GrantRow row = existing.getFirst();
                if (!row.grant().equals(grant) || !row.fingerprint().equals(codec.fingerprint(grant)))
                    throw conflict("PROVIDER_USAGE_GRANT_IMMUTABLE");
                return quota(row);
            }
            activeCustomer(grant.customerId()); actualTarget(grant.targetProfileId(), grant.environment());
            if (!clock.instant().isBefore(grant.expiresAt())) throw conflict("PROVIDER_USAGE_GRANT_EXPIRED");
            if (!jdbc.query("""
                SELECT id FROM provider_usage_grants WHERE campaign_id=? AND customer_id=? AND target_profile_id=? AND environment_name=?
                    AND lifecycle_status='ACTIVE'
                """, (rs, row) -> rs.getString(1), grant.campaignId(), grant.customerId(), grant.targetProfileId(), grant.environment()).isEmpty())
                throw conflict("PROVIDER_USAGE_CAMPAIGN_ALREADY_REVIEWED");
            String fingerprint = codec.fingerprint(grant);
            jdbc.update("""
                INSERT INTO provider_usage_grants
                (id,campaign_id,customer_id,target_profile_id,environment_name,fingerprint,payload_json,approved_by,approved_role,approved_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, grant.id(), grant.campaignId(), grant.customerId(), grant.targetProfileId(), grant.environment(), fingerprint,
                codec.write(grant), actor().actorId(), actor().role().name(), time(clock.instant()));
            audit("GRANT_APPROVED", grant.id(), grant.id(), fingerprint, staffActor());
            return quota(requireGrant(grant.id()));
        });
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public VoidUnusedReceipt voidUnused(String grantId, VoidUnusedCommand command) {
        staff(true); text(grantId, 96);
        if (command == null) throw invalid();
        return transaction(() -> {
            // Same review mutex as approval; the source-row lock also serializes every allocation/initialization.
            jdbc.queryForObject("SELECT id FROM provider_usage_grant_mutex WHERE id=1 FOR UPDATE", Integer.class);
            GrantRow row = requireGrant(grantId);
            String commandFingerprint = codec.fingerprint(new VoidRequest(grantId, command));
            var previous = voidReceipts(grantId, command.operationKey());
            if (!previous.isEmpty()) {
                if (previous.size() != 1) throw conflict("PROVIDER_USAGE_VOID_COMMAND_CHANGED");
                var receipt = previous.getFirst();
                if (!receipt.grantId().equals(grantId) || !receipt.command().equals(command)
                    || !receipt.commandFingerprint().equals(commandFingerprint))
                    throw conflict("PROVIDER_USAGE_VOID_COMMAND_CHANGED");
                if (row.status() != GrantStatus.VOID_UNUSED || !row.fingerprint().equals(receipt.grantFingerprint())
                    || !row.allocated().equals(ZERO)) throw conflict("PROVIDER_USAGE_VOID_RECORD_CHANGED");
                return receipt;
            }
            if (!row.fingerprint().equals(command.expectedGrantFingerprint()))
                throw conflict("PROVIDER_USAGE_GRANT_FINGERPRINT_CHANGED");
            if (row.status() != GrantStatus.ACTIVE) throw conflict("PROVIDER_USAGE_GRANT_VOID_UNUSED");
            requireUnused(row);
            var receipt = new VoidUnusedReceipt("pugv-" + UUID.randomUUID(), grantId, GrantStatus.VOID_UNUSED,
                row.fingerprint(), commandFingerprint, command, actor().actorId(), actor().role().name(),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
            jdbc.update("""
                INSERT INTO provider_usage_grant_void_receipts
                (grant_id,receipt_id,operation_key,grant_fingerprint,command_fingerprint,receipt_fingerprint,payload_json,actor_id,actor_role,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, grantId, receipt.receiptId(), command.operationKey(), row.fingerprint(), commandFingerprint,
                codec.fingerprint(receipt), codec.write(receipt), receipt.actorId(), receipt.actorRole(), time(receipt.voidedAt()));
            if (jdbc.update("UPDATE provider_usage_grants SET lifecycle_status='VOID_UNUSED' WHERE id=? AND lifecycle_status='ACTIVE'", grantId) != 1)
                throw conflict("PROVIDER_USAGE_VOID_RECORD_CHANGED");
            audit("GRANT_VOID_UNUSED", grantId, grantId, codec.fingerprint(receipt), staffActor());
            return receipt;
        });
    }

    private void requireUnused(GrantRow row) {
        String id = row.grant().id();
        if (!row.allocated().equals(ZERO)
            || jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations WHERE grant_id=?", Long.class, id) != 0)
            throw conflict("PROVIDER_USAGE_GRANT_NOT_UNUSED");
        // Unattributed legacy usage cannot prove absence of historical allocations, so it fails closed.
        if (jdbc.queryForObject("""
            SELECT count(*) FROM provider_usage_allowance_events
            WHERE (grant_id=? OR subject_id=? OR grant_id IS NULL) AND event_type<>'GRANT_APPROVED'
            """, Long.class, id, id) != 0) throw conflict("PROVIDER_USAGE_GRANT_HAS_USAGE_HISTORY");
        if (jdbc.queryForObject("""
            SELECT count(*) FROM provider_usage_allowance_events
            WHERE grant_id=? AND subject_id=? AND event_type='GRANT_APPROVED' AND payload_fingerprint=? AND actor_role='PLATFORM_ADMIN'
            """, Long.class, id, id, row.fingerprint()) != 1) throw conflict("PROVIDER_USAGE_GRANT_APPROVAL_HISTORY_CHANGED");
    }

    private List<VoidUnusedReceipt> voidReceipts(String grantId, String operationKey) {
        return jdbc.query("SELECT * FROM provider_usage_grant_void_receipts WHERE grant_id=? OR operation_key=?", (rs, index) -> {
            var receipt = codec.read(rs.getString("payload_json"), VoidUnusedReceipt.class);
            if (receipt.status() != GrantStatus.VOID_UNUSED || !receipt.grantId().equals(rs.getString("grant_id"))
                || !receipt.receiptId().equals(rs.getString("receipt_id")) || !receipt.command().operationKey().equals(rs.getString("operation_key"))
                || !receipt.grantFingerprint().equals(receipt.command().expectedGrantFingerprint())
                || !receipt.grantFingerprint().equals(rs.getString("grant_fingerprint"))
                || !receipt.commandFingerprint().equals(codec.fingerprint(new VoidRequest(receipt.grantId(), receipt.command())))
                || !receipt.commandFingerprint().equals(rs.getString("command_fingerprint"))
                || !codec.fingerprint(receipt).equals(rs.getString("receipt_fingerprint"))
                || !receipt.actorId().equals(rs.getString("actor_id")) || !"PLATFORM_ADMIN".equals(receipt.actorRole())
                || !receipt.actorRole().equals(rs.getString("actor_role")) || !receipt.voidedAt().equals(rs.getTimestamp("created_at").toInstant()))
                throw conflict("PROVIDER_USAGE_VOID_RECORD_CHANGED");
            return receipt;
        }, grantId, operationKey);
    }

    /** Trusted server/staff API, never expose raw financial limits as a customer command. */
    public SafeAllowanceProjection allocate(AllocationCommand command, ReviewedR7Context reviewed) {
        staff(false);
        if (command == null || reviewed == null) throw invalid();
        return transaction(() -> {
            lockR7();
            operatorAccess(command.deploymentId());
            return allocateLocked(command, reviewed, staffActor());
        });
    }

    private SafeAllowanceProjection allocateLocked(AllocationCommand command, ReviewedR7Context reviewed, AuditActor auditActor) {
            AdmissionContracts.Reservation reservation = reservation(reviewed);
            GrantRow row = requireActiveGrant(command.grantId());
            ProviderUsageGrant grant = row.grant();
            DeploymentEntity deployment = ownedDeployment(command.deploymentId(), grant.customerId());
            DeploymentTargetProfileEntity profile = actualTarget(command.targetProfileId(), grant.environment());
            validateR7(reservation, reviewed, deployment, profile);
            return allocateFinanciallyLocked(command, reviewed, null, row, deployment, profile, auditActor);
    }

    private SafeAllowanceProjection allocateFinanciallyLocked(AllocationCommand command, ReviewedR7Context reviewed,
        CoreReleaseContext coreRelease, GrantRow row, DeploymentEntity deployment, DeploymentTargetProfileEntity profile,
        AuditActor auditActor) {
            ProviderUsageGrant grant = row.grant();
            if (!grant.targetProfileId().equals(command.targetProfileId()) || !grant.binding().equals(command.binding())
                || !grant.rateCardReference().equals(command.rateCardReference())) throw conflict("PROVIDER_USAGE_BINDING_CHANGED");
            String credentialSha = actualCredentialSha(command.deploymentId(), grant.binding());
            if (!credentialSha.equals(command.expectedCredentialSha256())) throw conflict("PROVIDER_USAGE_CREDENTIAL_CHANGED");
            var existing = jdbc.query("SELECT approval_id FROM provider_usage_allocations WHERE grant_id=? AND deployment_id=? AND target_profile_id=?",
                (rs, index) -> rs.getString(1), grant.id(), deployment.getId(), profile.getId());
            if (!existing.isEmpty()) {
                AllocationRow previous = requireAllocation(existing.getFirst());
                AllocationTerms terms = previous.terms();
                if (!terms.caps().equals(command.caps()) || !terms.binding().equals(command.binding())
                    || !terms.pricingReference().equals(command.rateCardReference()) || !terms.credentialSha256().equals(credentialSha))
                    throw conflict("PROVIDER_USAGE_ALLOCATION_IMMUTABLE");
                if (!clock.instant().isBefore(projection(previous).effectiveExpiresAt()))
                    throw conflict("PROVIDER_USAGE_ALLOWANCE_EXPIRED");
                return projection(previous);
            }
            if (!clock.instant().isBefore(grant.expiresAt())) throw conflict("PROVIDER_USAGE_GRANT_EXPIRED");
            Limits next = add(row.allocated(), command.caps());
            if (!within(next, grant.credits())) throw conflict("PROVIDER_USAGE_LIFETIME_CREDITS_EXCEEDED");
            String id = "pua-" + UUID.randomUUID();
            AllocationScope scope = new AllocationScope(id, grant.customerId(), deployment.getId(), profile.getId(), profile.getEnvironmentName());
            AllocationTerms terms = new AllocationTerms(id, grant.id(), scope, command.caps(), grant.expiresAt(), grant.rateCardReference(),
                grant.binding(), credentialSha, reviewed, coreRelease);
            String fingerprint = codec.fingerprint(terms);
            jdbc.update("""
                INSERT INTO provider_usage_allocations
                (approval_id,grant_id,customer_id,deployment_id,target_profile_id,environment_name,approval_fingerprint,runtime_allocation_fingerprint,payload_json,initialization_state,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,'UNINITIALIZED',?)
                """, id, grant.id(), scope.customerId(), scope.deploymentId(), scope.targetProfileId(), scope.environment(),
                fingerprint, ProviderUsageRuntimeAllocationIdentity.fingerprint(terms), codec.write(terms), time(clock.instant()));
            jdbc.update("""
                UPDATE provider_usage_grants SET allocated_chat_attempts=?,allocated_total_tokens=?,allocated_cost_nano_usd=? WHERE id=?
                """, next.chatAttempts(), next.totalTokens(), next.costNanoUsd(), grant.id());
            audit("ALLOWANCE_ALLOCATED", id, grant.id(), fingerprint, auditActor);
            return projection(requireAllocation(id));
    }

    public SafeAllowanceProjection markInitialized(AllocationScope scope, UUID storeUuid, String runtimeAllocationFingerprint) {
        return transition(scope, storeUuid, runtimeAllocationFingerprint, false);
    }

    public SafeAllowanceProjection markActive(AllocationScope scope, UUID storeUuid, String runtimeAllocationFingerprint) {
        return transition(scope, storeUuid, runtimeAllocationFingerprint, true);
    }

    private SafeAllowanceProjection transition(AllocationScope scope, UUID storeUuid, String fingerprint, boolean activate) {
        staff(false);
        if (scope == null || storeUuid == null) throw invalid();
        sha(fingerprint);
        return transaction(() -> {
            var locator = allocation(scope.approvalId(), false);
            if (!locator.terms().scope().equals(scope)) throw denied("PROVIDER_USAGE_SCOPE_MISMATCH");
            if (locator.terms().initialCoreRelease() != null) throw conflict("PROVIDER_USAGE_AUTHORIZED_RELEASE_REQUIRED");
            lockR7();
            operatorAccess(scope.deploymentId());
            return transitionLocked(scope, storeUuid, fingerprint, activate, null, staffActor());
        });
    }

    private SafeAllowanceProjection transitionLocked(AllocationScope scope, UUID storeUuid, String fingerprint, boolean activate,
                                                     AuthorizedRelease authorized, AuditActor auditActor) {
            // Financial lock order remains grant -> allowance; legacy callers acquire their admission lock first.
            AllocationRow locator = allocation(scope.approvalId(), false);
            if (!locator.terms().scope().equals(scope)) throw denied("PROVIDER_USAGE_SCOPE_MISMATCH");
            ReviewedR7Context reviewed = null;
            AdmissionContracts.Reservation reservation = null;
            if (authorized == null || authorized.coreRelease() == null) {
                reviewed = authorized == null ? locator.terms().initialAdmission() : authorized.reviewed();
                if (reviewed == null) throw conflict("PROVIDER_USAGE_AUTHORIZED_RELEASE_REQUIRED");
                reservation = reservation(reviewed);
            }
            requireActiveGrant(locator.terms().grantId());
            AllocationRow row = requireAllocation(scope.approvalId());
            ownedDeployment(scope.deploymentId(), scope.customerId()); actualTarget(scope.targetProfileId(), scope.environment());
            if (!ProviderUsageRuntimeAllocationIdentity.fingerprint(row.terms()).equals(fingerprint))
                throw conflict("PROVIDER_USAGE_INITIALIZATION_FINGERPRINT_CHANGED");
            if (row.storeUuid() != null) {
                if (!row.storeUuid().equals(storeUuid) || !fingerprint.equals(row.initializationFingerprint()))
                    throw conflict("PROVIDER_USAGE_STORE_REPLACEMENT_FORBIDDEN");
                if (!activate || row.state() == InitializationState.ACTIVE) return projection(row);
            }
            if (activate && row.state() != InitializationState.INITIALIZED) throw conflict("PROVIDER_USAGE_INITIALIZATION_REQUIRED");
            if (!clock.instant().isBefore(row.terms().expiresAt())) throw conflict("PROVIDER_USAGE_ALLOWANCE_EXPIRED");
            if (reservation != null) validateR7(reservation, reviewed, ownedDeployment(scope.deploymentId(), scope.customerId()),
                actualTarget(scope.targetProfileId(), scope.environment()));
            if (!actualCredentialSha(scope.deploymentId(), row.terms().binding()).equals(row.terms().credentialSha256()))
                throw conflict("PROVIDER_USAGE_CREDENTIAL_CHANGED");
            if (activate) {
                jdbc.update("UPDATE provider_usage_allocations SET initialization_state='ACTIVE',activated_at=? WHERE approval_id=?",
                    time(clock.instant()), scope.approvalId());
            } else {
                jdbc.update("""
                    UPDATE provider_usage_allocations SET initialization_state='INITIALIZED',store_uuid=?,initialization_fingerprint=?,initialized_at=?
                    WHERE approval_id=?
                    """, storeUuid.toString(), fingerprint, time(clock.instant()), scope.approvalId());
            }
            audit(activate ? "ALLOWANCE_ACTIVE" : "ALLOWANCE_INITIALIZED", scope.approvalId(), row.terms().grantId(), row.fingerprint(), auditActor);
            return projection(requireAllocation(scope.approvalId()));
    }

    /** Worker-only boundary; no caller limits, identity, credential, grant ID or ambient principal. */
    SafeAllowanceProjection prepareForAuthorizedRelease(String releaseId) {
        text(releaseId, 64);
        return transaction(() -> {
            var authorized = authorizedRelease(releaseId, ProviderUsageAuthorizedReleaseReader.Phase.PREPARE);
            var version = versions.findById(authorized.versionId())
                .orElseThrow(() -> missing("PROVIDER_USAGE_VERSION_NOT_FOUND"));
            var grant = grantForRelease(authorized);
            ProviderUsageRuntimeProfile.require(grant.grant().binding(), grant.grant().rateCardReference());
            ProviderUsageRuntimeConfiguration.require(version.getProviderConfigJson(), codec, grant.grant().rateCardReference());
            var command = commandForRelease(authorized, grant);
            if (authorized.coreRelease() != null)
                return allocateFinanciallyLocked(command, null, authorized.coreRelease(), grant,
                    authorized.deployment(), authorized.profile(), SYSTEM_ACTOR);
            return allocateLocked(command, authorized.reviewed(), SYSTEM_ACTOR);
        });
    }

    /** Planning/readback only. Missing allowance stays missing; this never debits or initializes. */
    Optional<SafeAllowanceProjection> findForAuthorizedRelease(String releaseId) {
        text(releaseId, 64);
        return transaction(() -> {
            var authorized = authorizedRelease(releaseId, ProviderUsageAuthorizedReleaseReader.Phase.READ);
            return allocationForRelease(authorized).map(this::projection);
        });
    }

    SafeAllowanceProjection markInitializedForAuthorizedRelease(String releaseId, UUID storeUuid, String runtimeFingerprint) {
        return transitionForRelease(releaseId, storeUuid, runtimeFingerprint, false);
    }

    SafeAllowanceProjection markActiveForAuthorizedRelease(String releaseId, UUID storeUuid, String runtimeFingerprint) {
        return transitionForRelease(releaseId, storeUuid, runtimeFingerprint, true);
    }

    private SafeAllowanceProjection transitionForRelease(String releaseId, UUID storeUuid, String runtimeFingerprint, boolean activate) {
        text(releaseId, 64); sha(runtimeFingerprint); if (storeUuid == null) throw invalid();
        return transaction(() -> {
            var authorized = authorizedRelease(releaseId, ProviderUsageAuthorizedReleaseReader.Phase.INITIALIZE);
            var allocation = allocationForRelease(authorized).orElseThrow(() -> missing("PROVIDER_USAGE_ALLOCATION_NOT_FOUND"));
            return transitionLocked(allocation.terms().scope(), storeUuid, runtimeFingerprint, activate, authorized, SYSTEM_ACTOR);
        });
    }

    private AuthorizedRelease authorizedRelease(String releaseId, ProviderUsageAuthorizedReleaseReader.Phase phase) {
        var reader = new ProviderUsageAuthorizedReleaseReader(jdbc, codec);
        var release = reader.release(releaseId, phase);
        var core = ProviderUsageAuthorizedReleaseReader.coreAuthorization(jdbc, releaseId);
        if (core.isPresent()) {
            var authorization = core.get();
            if (ProviderUsageModeResolver.mode(authorization.providerUsageMode()) != ProviderUsageModeResolver.Mode.FUNDED)
                throw conflict("PROVIDER_USAGE_FUNDED_MODE_REQUIRED");
            var deployment = ownedDeployment(authorization.deploymentId(), authorization.customerId());
            var profile = targets.findById(authorization.targetProfileId())
                .orElseThrow(() -> missing("PROVIDER_USAGE_TARGET_NOT_FOUND"));
            profile = actualTarget(profile.getId(), profile.getEnvironmentName());
            var version = versions.findById(authorization.versionId())
                .orElseThrow(() -> missing("PROVIDER_USAGE_VERSION_NOT_FOUND"));
            if (!Objects.equals(authorization.releaseId(), release.id())
                || !Objects.equals(authorization.deploymentId(), release.deploymentId())
                || !Objects.equals(authorization.tenantId(), deployment.getTenantId())
                || !Objects.equals(authorization.versionId(), release.versionId())
                || !Objects.equals(authorization.targetProfileId(), release.targetProfileId())
                || !Objects.equals(authorization.sourceArtifactId(), release.sourceArtifactId())
                || !Objects.equals(version.getDeploymentId(), deployment.getId())
                || !Objects.equals(version.getConfigHash(), authorization.versionConfigHash())
                || !"PUBLISHED".equals(version.getStatus()))
                throw conflict("PROVIDER_USAGE_CORE_AUTHORIZATION_SCOPE_CHANGED");
            return new AuthorizedRelease(null, authorization.context(), authorization.versionId(), deployment, profile);
        }
        lockR7();
        var locator = reader.binding(releaseId, false);
        var reviewed = new ReviewedR7Context(locator.reservationId(), locator.generation(), locator.versionId());
        var reservation = reservation(reviewed);
        var binding = reader.binding(releaseId, true);
        if (!binding.equals(locator)) throw conflict("PROVIDER_USAGE_RELEASE_BINDING_CHANGED");
        var deployment = ownedDeployment(binding.deploymentId(), binding.customerId());
        var profile = targets.findById(binding.targetProfileId()).orElseThrow(() -> missing("PROVIDER_USAGE_TARGET_NOT_FOUND"));
        profile = actualTarget(profile.getId(), profile.getEnvironmentName());
        var manifest = validateR7(reservation, reviewed, deployment, profile);
        if (!Objects.equals(manifest.demand(), reservation.demand())) throw conflict("PROVIDER_USAGE_R7_MANIFEST_DEMAND_CHANGED");
        reader.validate(release, binding, reservation, manifest, phase);
        return new AuthorizedRelease(reviewed, null, binding.versionId(), deployment, profile);
    }

    private GrantRow grantForRelease(AuthorizedRelease authorized) {
        jdbc.queryForObject("SELECT id FROM provider_usage_grant_mutex WHERE id=1 FOR UPDATE", Integer.class);
        // An ACK extends only this already charged allocation, never the grant's unused credit.
        var existing = jdbc.query("SELECT approval_id FROM provider_usage_allocations WHERE deployment_id=? AND target_profile_id=? ORDER BY approval_id",
            (rs, index) -> rs.getString(1), authorized.deployment().getId(), authorized.profile().getId());
        if (existing.size() > 1) throw conflict("PROVIDER_USAGE_ALLOCATION_AMBIGUOUS");
        if (existing.size() == 1) {
            var allocation = allocation(existing.getFirst(), false);
            var source = requireActiveGrant(allocation.terms().grantId());
            if (!source.grant().customerId().equals(authorized.deployment().getCustomerId())
                || !source.grant().targetProfileId().equals(authorized.profile().getId())
                || !source.grant().environment().equals(authorized.profile().getEnvironmentName()))
                throw conflict("PROVIDER_USAGE_ALLOCATION_IMMUTABLE");
            if (!clock.instant().isBefore(projection(allocation).effectiveExpiresAt()))
                throw conflict("PROVIDER_USAGE_ALLOWANCE_EXPIRED");
            return source;
        }
        var grants = jdbc.query("SELECT id FROM provider_usage_grants WHERE customer_id=? AND target_profile_id=? AND environment_name=? AND lifecycle_status='ACTIVE' ORDER BY id",
            (rs, index) -> rs.getString(1), authorized.deployment().getCustomerId(), authorized.profile().getId(), authorized.profile().getEnvironmentName())
            .stream().map(this::requireActiveGrant).filter(row -> clock.instant().isBefore(row.grant().expiresAt())).toList();
        if (grants.isEmpty()) throw conflict("PROVIDER_USAGE_REVIEWED_GRANT_REQUIRED");
        if (grants.size() != 1) {
            // Automatic allocation consumes a whole grant, not the residual balance of an earlier deployment.
            var unallocated = grants.stream().filter(row -> row.allocated().equals(new Limits(0, 0, 0))).toList();
            if (unallocated.size() != 1) throw conflict("PROVIDER_USAGE_REVIEWED_GRANT_AMBIGUOUS");
            return unallocated.getFirst();
        }
        return grants.getFirst();
    }

    private AllocationCommand commandForRelease(AuthorizedRelease authorized, GrantRow row) {
        var grant = row.grant();
        // Release authority never supplies credits; only the immutable staff grant authorizes them.
        return new AllocationCommand(grant.id(), authorized.deployment().getId(), authorized.profile().getId(), grant.credits(),
            grant.binding(), grant.rateCardReference(), actualCredentialSha(authorized.deployment().getId(), grant.binding()));
    }

    private Optional<AllocationRow> allocationForRelease(AuthorizedRelease authorized) {
        var grant = grantForRelease(authorized); var expected = commandForRelease(authorized, grant);
        var ids = jdbc.query("SELECT approval_id FROM provider_usage_allocations WHERE grant_id=? AND deployment_id=? AND target_profile_id=?",
            (rs, index) -> rs.getString(1), expected.grantId(), expected.deploymentId(), expected.targetProfileId());
        if (ids.isEmpty()) return Optional.empty();
        if (ids.size() != 1) throw conflict("PROVIDER_USAGE_ALLOCATION_AMBIGUOUS");
        var row = allocation(ids.getFirst(), false); var terms = row.terms();
        if (!terms.scope().customerId().equals(authorized.deployment().getCustomerId())
            || !terms.scope().environment().equals(authorized.profile().getEnvironmentName()) || !terms.caps().equals(expected.caps())
            || !terms.binding().equals(expected.binding()) || !terms.pricingReference().equals(expected.rateCardReference())
            || !terms.credentialSha256().equals(expected.expectedCredentialSha256())) throw conflict("PROVIDER_USAGE_ALLOCATION_IMMUTABLE");
        return Optional.of(row);
    }

    public SafeAllowanceProjection readAllocation(AllocationScope scope) {
        staff(false);
        if (scope == null) throw invalid();
        return transaction(() -> {
            // Locator only: do not lock an allowance before projection locks its source grant.
            AllocationRow row = allocation(scope.approvalId(), false);
            if (!row.terms().scope().equals(scope)) throw denied("PROVIDER_USAGE_SCOPE_MISMATCH");
            allocationReadAccess(scope);
            return projection(row);
        });
    }

    /** Admin validity review is not a bypass for inactive customers, deleted deployments or relabelled targets. */
    SafeAllowanceProjection readForValidityExtension(AllocationScope scope) {
        staff(true);
        if (scope == null) throw invalid();
        return transaction(() -> {
            var row = allocation(scope.approvalId(), false);
            if (!row.terms().scope().equals(scope)) throw denied("PROVIDER_USAGE_SCOPE_MISMATCH");
            ownedDeployment(scope.deploymentId(), scope.customerId());
            actualTarget(scope.targetProfileId(), scope.environment());
            return projection(row);
        });
    }

    /** Staff/internal lookup only. Never guess the latest grant if more than one allocation exists. */
    public Optional<SafeAllowanceProjection> findForDeployment(String deploymentId, String targetProfileId) {
        staff(false); text(deploymentId, 64); text(targetProfileId, 64);
        return transaction(() -> {
            var deployment = deployments.findById(deploymentId).orElseThrow(() -> missing("PROVIDER_USAGE_DEPLOYMENT_NOT_FOUND"));
            deploymentAccess.requireDeploymentOperatorAccess(deployment);
            var ids = jdbc.query("SELECT approval_id FROM provider_usage_allocations WHERE deployment_id=? AND target_profile_id=? ORDER BY approval_id",
                (rs, index) -> rs.getString(1), deploymentId, targetProfileId);
            if (ids.size() > 1) throw conflict("PROVIDER_USAGE_ALLOCATION_AMBIGUOUS");
            if (ids.isEmpty()) return Optional.empty();
            var row = allocation(ids.getFirst(), false);
            allocationReadAccess(row.terms().scope());
            return Optional.of(projection(row));
        });
    }

    public GrantQuotaView readQuota(String grantId) {
        authenticated(); text(grantId, 96);
        return transaction(() -> {
            GrantRow row = requireGrant(grantId); readAccess(row.grant().customerId()); return quota(row);
        });
    }

    private void lockR7() {
        // Same global mutex as existing R7 mutations; no inverse deployment/allocation locking.
        jdbc.queryForObject("SELECT id FROM deployment_admission_mutex WHERE id=1 FOR UPDATE", Integer.class);
    }

    private AdmissionContracts.Reservation reservation(ReviewedR7Context context) {
        var rows = jdbc.query("SELECT payload_json,state,generation,customer_id,deployment_id,target_profile_id FROM deployment_capacity_reservations WHERE id=? FOR UPDATE",
            (rs, index) -> {
                var result = codec.read(rs.getString(1), AdmissionContracts.Reservation.class);
                if (!result.state().name().equals(rs.getString(2)) || !result.generation().equals(rs.getString(3))
                    || !result.customerId().equals(rs.getString(4)) || !result.deploymentId().equals(rs.getString(5))
                    || !result.targetProfileId().equals(rs.getString(6))) throw conflict("PROVIDER_USAGE_R7_RECORD_CHANGED");
                return result;
            }, context.reservationId());
        if (rows.size() != 1 || !rows.getFirst().generation().equals(context.generation())) throw conflict("PROVIDER_USAGE_R7_REQUIRED");
        return rows.getFirst();
    }

    private AdmissionContracts.Manifest validateR7(AdmissionContracts.Reservation r, ReviewedR7Context reviewed, DeploymentEntity deployment,
                            DeploymentTargetProfileEntity profile) {
        if (!List.of(AdmissionContracts.State.RESERVED, AdmissionContracts.State.PROVISIONING, AdmissionContracts.State.ACTIVE).contains(r.state())
            || !r.id().equals(reviewed.reservationId()) || !r.generation().equals(reviewed.generation())
            || !r.customerId().equals(deployment.getCustomerId()) || !r.tenantId().equals(deployment.getTenantId())
            || !r.deploymentId().equals(deployment.getId()) || !r.targetProfileId().equals(profile.getId())
            || r.workerToken() != null || r.retirementOperationId() != null || r.failureCode() != null
            || r.expiresAt() == null || !clock.instant().isBefore(r.expiresAt()) || r.prerequisiteReadback() == null)
            throw conflict("PROVIDER_USAGE_ACTIVE_R7_REQUIRED");
        String profileFingerprint = DeploymentTargetProfileIdentity.fingerprint(mapper, profile);
        if (!profileFingerprint.equals(r.prerequisiteReadback().profileFingerprint())
            || !r.manifestFingerprint().equals(r.prerequisiteReadback().manifestFingerprint()))
            throw conflict("PROVIDER_USAGE_R7_PROFILE_CHANGED");
        var manifests = jdbc.query("SELECT payload_json FROM deployment_admission_reviews WHERE kind='MANIFEST' AND record_key=?",
            (rs, index) -> codec.read(rs.getString(1), AdmissionContracts.Manifest.class), r.manifestId());
        if (manifests.size() != 1) throw conflict("PROVIDER_USAGE_REVIEWED_VERSION_REQUIRED");
        var manifest = manifests.getFirst();
        var version = versions.findById(reviewed.versionId()).orElseThrow(() -> missing("PROVIDER_USAGE_VERSION_NOT_FOUND"));
        if (!codec.fingerprint(manifest).equals(r.manifestFingerprint()) || !manifest.versionId().equals(reviewed.versionId()) || !manifest.deploymentId().equals(deployment.getId())
            || !manifest.targetProfileId().equals(profile.getId()) || !version.getDeploymentId().equals(deployment.getId())
            || !manifest.versionConfigHash().equals(version.getConfigHash()) || !"PUBLISHED".equals(version.getStatus()))
            throw conflict("PROVIDER_USAGE_REVIEWED_VERSION_CHANGED");
        return manifest;
    }

    private void operatorAccess(String deploymentId) {
        var deployment = deployments.findById(deploymentId).orElseThrow(() -> missing("PROVIDER_USAGE_DEPLOYMENT_NOT_FOUND"));
        deploymentAccess.requireDeploymentOperatorAccess(deployment);
    }

    private DeploymentEntity ownedDeployment(String id, String customerId) {
        var deployment = deployments.findById(id).orElseThrow(() -> missing("PROVIDER_USAGE_DEPLOYMENT_NOT_FOUND"));
        activeCustomer(customerId);
        if (!customerId.equals(deployment.getCustomerId()) || deployment.getArchivedAt() != null || deployment.getDeletionRequestedAt() != null
            || "DELETED".equals(deployment.getStatus())) throw denied("PROVIDER_USAGE_DEPLOYMENT_SCOPE_DENIED");
        return deployment;
    }

    private DeploymentTargetProfileEntity actualTarget(String id, String environment) {
        var profile = targets.findById(id).orElseThrow(() -> missing("PROVIDER_USAGE_TARGET_NOT_FOUND"));
        if (!profile.isActive() || !environment.equals(profile.getEnvironmentName())) throw denied("PROVIDER_USAGE_TARGET_SCOPE_DENIED");
        return profile;
    }

    private void activeCustomer(String id) {
        var customer = customers.findById(id).orElseThrow(() -> missing("PROVIDER_USAGE_CUSTOMER_NOT_FOUND"));
        if (!"ACTIVE".equals(customer.getStatus())) throw denied("PROVIDER_USAGE_CUSTOMER_INACTIVE");
    }

    private String actualCredentialSha(String deploymentId, ProviderBinding binding) {
        var secret = secrets.resolve(deploymentId, binding.secretPurpose(), binding.managedSecretRef());
        if (secret == null || !secret.resolved() || secret.value() == null || secret.value().isBlank())
            throw conflict("PROVIDER_USAGE_CREDENTIAL_UNRESOLVED");
        return ProviderUsageAllowanceCodec.sha256(secret.value());
    }

    private List<GrantRow> grantRows(String id, boolean lock) {
        return jdbc.query("SELECT * FROM provider_usage_grants WHERE id=?" + (lock ? " FOR UPDATE" : ""), (rs, index) -> {
            var grant = codec.read(rs.getString("payload_json"), ProviderUsageGrant.class);
            if (!grant.id().equals(id) || !codec.fingerprint(grant).equals(rs.getString("fingerprint"))
                || !grant.customerId().equals(rs.getString("customer_id")) || !grant.targetProfileId().equals(rs.getString("target_profile_id"))
                || !grant.environment().equals(rs.getString("environment_name")) || !grant.campaignId().equals(rs.getString("campaign_id")))
                throw conflict("PROVIDER_USAGE_GRANT_RECORD_CHANGED");
            GrantStatus status;
            try { status = GrantStatus.valueOf(rs.getString("lifecycle_status")); }
            catch (IllegalArgumentException | NullPointerException error) { throw conflict("PROVIDER_USAGE_GRANT_RECORD_CHANGED"); }
            return new GrantRow(grant, rs.getString("fingerprint"), new Limits(rs.getLong("allocated_chat_attempts"),
                rs.getLong("allocated_total_tokens"), rs.getLong("allocated_cost_nano_usd")), status);
        }, id);
    }

    private GrantRow requireGrant(String id) {
        var rows = grantRows(id, true);
        if (rows.size() != 1) throw missing("PROVIDER_USAGE_GRANT_NOT_FOUND");
        return rows.getFirst();
    }

    private GrantRow requireActiveGrant(String id) {
        GrantRow row = requireGrant(id);
        if (row.status() != GrantStatus.ACTIVE
            || jdbc.queryForObject("SELECT count(*) FROM provider_usage_grant_void_receipts WHERE grant_id=?", Long.class, id) != 0)
            throw conflict("PROVIDER_USAGE_GRANT_VOID_UNUSED");
        return row;
    }

    private AllocationRow requireAllocation(String id) { return allocation(id, true); }

    private AllocationRow allocation(String id, boolean lock) {
        var rows = jdbc.query("SELECT * FROM provider_usage_allocations WHERE approval_id=?" + (lock ? " FOR UPDATE" : ""), (rs, index) -> {
            var terms = codec.read(rs.getString("payload_json"), AllocationTerms.class);
            if (!id.equals(terms.approvalId()) || !id.equals(terms.scope().approvalId()) || !terms.grantId().equals(rs.getString("grant_id"))
                || !terms.scope().customerId().equals(rs.getString("customer_id")) || !terms.scope().deploymentId().equals(rs.getString("deployment_id"))
                || !terms.scope().targetProfileId().equals(rs.getString("target_profile_id")) || !terms.scope().environment().equals(rs.getString("environment_name"))
                || !codec.fingerprint(terms).equals(rs.getString("approval_fingerprint"))
                || !ProviderUsageRuntimeAllocationIdentity.fingerprint(terms).equals(rs.getString("runtime_allocation_fingerprint")))
                throw conflict("PROVIDER_USAGE_ALLOCATION_RECORD_CHANGED");
            String store = rs.getString("store_uuid");
            return new AllocationRow(terms, rs.getString("approval_fingerprint"), InitializationState.valueOf(rs.getString("initialization_state")),
                store == null ? null : UUID.fromString(store), rs.getString("initialization_fingerprint"));
        }, id);
        if (rows.size() != 1) throw missing("PROVIDER_USAGE_ALLOCATION_NOT_FOUND");
        return rows.getFirst();
    }

    private SafeAllowanceProjection projection(AllocationRow row) {
        var terms = row.terms();
        requireActiveGrant(terms.grantId());
        var original = new SafeAllowanceProjection(terms.approvalId(), terms.scope(), terms.caps(), terms.expiresAt(), terms.pricingReference(),
            terms.binding(), terms.credentialSha256(), row.fingerprint(), ProviderUsageRuntimeAllocationIdentity.fingerprint(terms),
            row.state(), row.storeUuid(), row.initializationFingerprint());
        return new SafeAllowanceProjection(original.approvalId(), original.scope(), original.caps(), original.expiresAt(), original.pricingReference(),
            original.binding(), original.credentialSha256(), original.approvalFingerprint(), original.runtimeAllocationFingerprint(), original.state(),
            original.storeUuid(), original.initializationFingerprint(), ProviderUsageValidityExtensionStore.effectiveExpiry(jdbc, codec, original));
    }

    private GrantQuotaView quota(GrantRow row) {
        var grant = row.grant();
        if (!within(row.allocated(), grant.credits())) throw conflict("PROVIDER_USAGE_CREDIT_INTEGRITY_FAILURE");
        var receipts = voidReceipts(grant.id(), "");
        if ((row.status() == GrantStatus.VOID_UNUSED) != (receipts.size() == 1)
            || (!receipts.isEmpty() && (!receipts.getFirst().grantFingerprint().equals(row.fingerprint()) || !row.allocated().equals(ZERO))))
            throw conflict("PROVIDER_USAGE_VOID_RECORD_CHANGED");
        Limits remaining = new Limits(grant.credits().chatAttempts() - row.allocated().chatAttempts(),
            grant.credits().totalTokens() - row.allocated().totalTokens(), grant.credits().costNanoUsd() - row.allocated().costNanoUsd());
        return new GrantQuotaView(grant.id(), grant.campaignId(), grant.customerId(), grant.targetProfileId(), grant.environment(), grant.credits(),
            row.allocated(), remaining, grant.expiresAt(), grant.rateCardReference(), row.status(), row.fingerprint(),
            row.status() == GrantStatus.ACTIVE && clock.instant().isBefore(grant.expiresAt()) ? remaining : ZERO);
    }

    private static Limits add(Limits left, Limits right) {
        try { return new Limits(Math.addExact(left.chatAttempts(), right.chatAttempts()), Math.addExact(left.totalTokens(), right.totalTokens()),
            Math.addExact(left.costNanoUsd(), right.costNanoUsd())); }
        catch (ArithmeticException error) { throw conflict("PROVIDER_USAGE_CREDIT_OVERFLOW"); }
    }
    private static boolean within(Limits value, Limits cap) {
        return value.chatAttempts() <= cap.chatAttempts() && value.totalTokens() <= cap.totalTokens() && value.costNanoUsd() <= cap.costNanoUsd();
    }
    private void audit(String event, String id, String grantId, String fingerprint, AuditActor auditActor) {
        jdbc.update("INSERT INTO provider_usage_allowance_events(id,event_type,subject_id,grant_id,actor_id,actor_role,payload_fingerprint,created_at) VALUES (?,?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(), event, id, grantId, auditActor.id(), auditActor.role(), fingerprint, time(clock.instant()));
    }
    private void readAccess(String customerId) {
        var principal = actor();
        if (principal.role() == PlatformRole.PLATFORM_ADMIN || principal.role() == PlatformRole.PLATFORM_OPERATOR) return;
        if (principal.role() != PlatformRole.CUSTOMER_ADMIN) throw denied("PROVIDER_USAGE_ACTOR_FORBIDDEN");
        customerAccess.requireDeploymentCustomerAccess(customerId);
    }
    private void allocationReadAccess(AllocationScope scope) {
        if (actor().role() == PlatformRole.PLATFORM_ADMIN) return;
        var deployment = deployments.findById(scope.deploymentId()).orElseThrow(() -> missing("PROVIDER_USAGE_DEPLOYMENT_NOT_FOUND"));
        deploymentAccess.requireDeploymentOperatorAccess(deployment);
        if (!scope.customerId().equals(deployment.getCustomerId())) throw denied("PROVIDER_USAGE_DEPLOYMENT_SCOPE_DENIED");
    }
    private static void staff(boolean adminOnly) {
        authenticated(); var role = actor().role();
        if (role != PlatformRole.PLATFORM_ADMIN && (adminOnly || role != PlatformRole.PLATFORM_OPERATOR))
            throw denied(adminOnly ? "PROVIDER_USAGE_ADMIN_REVIEW_REQUIRED" : "PROVIDER_USAGE_TRUSTED_SERVER_REQUIRED");
    }
    private static void authenticated() {
        if (!PlatformSecurityContext.isAuthenticated()) throw denied("PROVIDER_USAGE_AUTHENTICATION_REQUIRED");
    }
    private static PlatformPrincipal actor() {
        authenticated(); var principal = PlatformSecurityContext.currentPrincipal(); text(principal.actorId(), 255); return principal;
    }
    private static AuditActor staffActor() { return new AuditActor(actor().actorId(), actor().role().name()); }
    private static final AuditActor SYSTEM_ACTOR = new AuditActor("system:provider-usage-release", "SYSTEM");
    private <T> T transaction(Supplier<T> operation) { return Objects.requireNonNull(transactions.execute(status -> operation.get())); }
    private static Timestamp time(Instant value) { return Timestamp.from(value); }
    private static final Limits ZERO = new Limits(0, 0, 0);
    private record GrantRow(ProviderUsageGrant grant, String fingerprint, Limits allocated, GrantStatus status) { }
    private record VoidRequest(String grantId, VoidUnusedCommand command) { }
    private record AllocationRow(AllocationTerms terms, String fingerprint, InitializationState state, UUID storeUuid, String initializationFingerprint) { }
    private record AuditActor(String id, String role) { }
    private record AuthorizedRelease(ReviewedR7Context reviewed, CoreReleaseContext coreRelease, String versionId,
                                     DeploymentEntity deployment, DeploymentTargetProfileEntity profile) { }
}
