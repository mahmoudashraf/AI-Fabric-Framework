package com.ai.fabric.platform.backend.deployment.authority;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentExecutionSecretService;
import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretOwnerType;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretScopeType;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityBinding.*;
import static com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityPurpose.*;
import static org.springframework.http.HttpStatus.*;

/** Server-side authority only. Parent callers still validate target entitlement, placement and endpoints. */
@Service
public class DeploymentRuntimeAuthorityService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> ADMINISTRABLE_RELEASES = Set.of("VERIFYING", "APPLIED_VERIFIED", "APPLIED_VERIFICATION_FAILED");
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentReleaseRepository releases;
    private final DeploymentTargetProfileRepository targets;
    private final DeploymentAccessService access;
    private final RuntimeAuthorityRepository authorities;
    private final RuntimeAuthoritySnapshotRepository snapshots;
    private final PlatformSecretRepository secrets;
    private final PlatformSecretService secretService;
    private final DeploymentExecutionSecretService executionSecrets;
    private final RuntimePrivateAssertionSigningService signer;
    private final PlatformAuditService audit;
    private final ObjectMapper mapper;

    public DeploymentRuntimeAuthorityService(DeploymentRepository deployments, DeploymentVersionRepository versions,
        DeploymentReleaseRepository releases, DeploymentTargetProfileRepository targets, DeploymentAccessService access,
        RuntimeAuthorityRepository authorities, RuntimeAuthoritySnapshotRepository snapshots, PlatformSecretRepository secrets,
        PlatformSecretService secretService, DeploymentExecutionSecretService executionSecrets,
        RuntimePrivateAssertionSigningService signer, PlatformAuditService audit, ObjectMapper mapper) {
        this.deployments = deployments;
        this.versions = versions;
        this.releases = releases;
        this.targets = targets;
        this.access = access;
        this.authorities = authorities;
        this.snapshots = snapshots;
        this.secrets = secrets;
        this.secretService = secretService;
        this.executionSecrets = executionSecrets;
        this.signer = signer;
        this.audit = audit;
        this.mapper = mapper;
    }

    /** Origins are a reviewed target slot. No arbitrary environment map or issuer/key overrides are accepted. */
    @Transactional
    public Snapshot freezeCandidate(String deploymentId, String targetProfileId, String versionId, List<String> reviewedOrigins) {
        return freezeCandidate(deploymentId, targetProfileId, versionId, reviewedOrigins, true);
    }

    /**
     * Release-orchestration boundary used only after DeploymentService or its trusted caller has
     * authorized the apply. This accepts the same server-owned identities and reviewed origins as
     * the operator path, but deliberately does not depend on an ambient browser principal.
     */
    @Transactional
    public Snapshot freezeCandidateForAuthorizedApply(String deploymentId, String targetProfileId,
                                                       String versionId, List<String> reviewedOrigins) {
        return freezeCandidate(deploymentId, targetProfileId, versionId, reviewedOrigins, false);
    }

    private Snapshot freezeCandidate(String deploymentId, String targetProfileId, String versionId,
                                     List<String> reviewedOrigins, boolean requireOperatorAccess) {
        DeploymentEntity deployment = lock(deploymentId, targetProfileId, true, requireOperatorAccess);
        DeploymentVersionEntity version = version(deployment, versionId);
        List<String> origins = origins(reviewedOrigins);
        RuntimeAuthorityEntity authority = authorities.findByDeploymentIdAndTargetProfileId(deploymentId, targetProfileId)
            .orElseGet(() -> newAuthority(deployment, targetProfileId));
        requireOwner(deployment, authority);
        requireActive(authority);
        var revisions = revisions(authority);
        var integrity = integrity(authority);
        for (var entry : revisions.entrySet()) checkedSecret(authority, entry.getKey(), entry.getValue(), integrity);
        boolean changed = false;
        for (RuntimeAuthorityPurpose purpose : requiredPurposes(deployment, version)) {
            if (revisions.containsKey(purpose)) continue;
            String reference = reference(authority, purpose);
            if (secrets.existsById(reference)) throw conflict("Target secret reference already exists without authority ownership evidence.");
            generate(authority, purpose, revisions, integrity);
            changed = true;
        }
        if (changed) saveSecrets(authority, revisions, integrity);
        RuntimeAuthorityBinding binding = binding(deployment, version, authority, origins);
        String fingerprint = hash(write(binding));
        String snapshotId = hash(write(List.of("CANDIDATE", fingerprint)));
        var existing = snapshots.findById(snapshotId);
        if (existing.isPresent()) return checkedSnapshot(deployment, authority, existing.get(), fingerprint);
        RuntimeAuthoritySnapshotEntity snapshot = snapshot(snapshotId, binding, null, fingerprint);
        snapshots.save(snapshot);
        audit.record("RUNTIME_AUTHORITY_CANDIDATE_FROZEN", "DEPLOYMENT_RUNTIME_AUTHORITY", authority.getId(),
            details(authority, Map.of("versionId", version.getId(), "snapshotId", snapshotId, "bindingFingerprint", fingerprint)));
        return projection(snapshot, binding);
    }

    @Transactional
    public Snapshot freezeRelease(String deploymentId, String targetProfileId, String releaseId,
                                  String candidateSnapshotId, String expectedFingerprint) {
        return freezeRelease(deploymentId, targetProfileId, releaseId, candidateSnapshotId, expectedFingerprint, true);
    }

    /** Completes an already-authorized apply without consulting browser identity. */
    @Transactional
    public Snapshot freezeReleaseForAuthorizedApply(String deploymentId, String targetProfileId, String releaseId,
                                                     String candidateSnapshotId, String expectedFingerprint) {
        return freezeRelease(deploymentId, targetProfileId, releaseId, candidateSnapshotId, expectedFingerprint, false);
    }

    private Snapshot freezeRelease(String deploymentId, String targetProfileId, String releaseId,
                                   String candidateSnapshotId, String expectedFingerprint,
                                   boolean requireOperatorAccess) {
        DeploymentEntity deployment = lock(deploymentId, targetProfileId, true, requireOperatorAccess);
        var release = release(deployment, targetProfileId, releaseId);
        var authority = authority(deployment, targetProfileId);
        var candidate = requireSnapshot(candidateSnapshotId);
        if (candidate.getReleaseId() != null || !release.getDeploymentVersionId().equals(candidate.getVersionId())) {
            throw conflict("Release does not match the frozen authority candidate.");
        }
        var checked = checkedSnapshot(deployment, authority, candidate, expectedFingerprint);
        var existing = snapshots.findByReleaseId(releaseId);
        if (existing.isPresent()) return checkedSnapshot(deployment, authority, existing.get(), expectedFingerprint);
        var frozen = snapshot(hash(write(List.of("RELEASE", releaseId))), checked.binding(), releaseId, expectedFingerprint);
        snapshots.save(frozen);
        audit.record("RUNTIME_AUTHORITY_RELEASE_FROZEN", "DEPLOYMENT_RUNTIME_AUTHORITY", authority.getId(),
            details(authority, Map.of("releaseId", releaseId, "snapshotId", frozen.getId(), "bindingFingerprint", expectedFingerprint)));
        return projection(frozen, checked.binding());
    }

    @Transactional
    public Snapshot requireCandidateUnchanged(String deploymentId, String targetProfileId, String versionId,
                                               String snapshotId, String expectedFingerprint) {
        DeploymentEntity deployment = lock(deploymentId, targetProfileId, true);
        var candidate = requireSnapshot(snapshotId);
        if (candidate.getReleaseId() != null || !required(versionId).equals(candidate.getVersionId())) {
            throw conflict("Authority snapshot does not belong to the requested candidate.");
        }
        return checkedSnapshot(deployment, authority(deployment, targetProfileId), candidate, expectedFingerprint);
    }

    @Transactional
    public Snapshot requireReleaseUnchanged(String deploymentId, String targetProfileId, String releaseId, String expectedFingerprint) {
        DeploymentEntity deployment = lock(deploymentId, targetProfileId, true);
        var release = release(deployment, targetProfileId, releaseId);
        var snapshot = snapshots.findByReleaseId(releaseId).orElseThrow(() -> conflict("Release lacks frozen target authority evidence."));
        if (!release.getDeploymentVersionId().equals(snapshot.getVersionId())) throw conflict("Release authority version drifted.");
        return checkedSnapshot(deployment, authority(deployment, targetProfileId), snapshot, expectedFingerprint);
    }

    @Transactional
    public EnvironmentPlan candidateEnvironmentPlan(String deploymentId, String targetProfileId, String versionId,
                                                     String snapshotId, String expectedFingerprint) {
        return environment(requireCandidateUnchanged(deploymentId, targetProfileId, versionId, snapshotId, expectedFingerprint).binding());
    }

    @Transactional
    public EnvironmentPlan releaseEnvironmentPlan(String deploymentId, String targetProfileId, String releaseId, String expectedFingerprint) {
        return environment(requireReleaseUnchanged(deploymentId, targetProfileId, releaseId, expectedFingerprint).binding());
    }

    /**
     * Provisioning-worker readback for an authority snapshot that was synchronously frozen on the release.
     * This deliberately accepts no browser identity, arbitrary snapshot id, origin, or secret override.
     */
    @Transactional
    public EnvironmentPlan releaseEnvironmentPlanForProvisioning(DeploymentEntity deployment,
                                                                  DeploymentVersionEntity version,
                                                                  DeploymentReleaseEntity release) {
        if (deployment == null || version == null || release == null
            || !deployment.getId().equals(release.getDeploymentId())
            || !version.getId().equals(release.getDeploymentVersionId())
            || !deployment.getId().equals(version.getDeploymentId())
            || release.getTargetProfileId() == null
            || release.getRuntimeAuthoritySnapshotId() == null
            || release.getRuntimeAuthorityFingerprint() == null) {
            throw conflict("Release lacks complete frozen target authority evidence.");
        }
        RuntimeAuthorityEntity authority = authority(deployment, release.getTargetProfileId());
        RuntimeAuthoritySnapshotEntity snapshot = snapshots.findById(release.getRuntimeAuthoritySnapshotId())
            .orElseThrow(() -> conflict("Frozen release authority snapshot is unavailable."));
        if (!release.getId().equals(snapshot.getReleaseId())
            || !version.getId().equals(snapshot.getVersionId())
            || !release.getRuntimeAuthorityFingerprint().equals(snapshot.getFingerprint())) {
            throw conflict("Frozen release authority evidence does not match the exact release and version.");
        }
        return environment(checkedSnapshot(
            deployment,
            authority,
            snapshot,
            release.getRuntimeAuthorityFingerprint()
        ).binding());
    }

    @Transactional
    public Map<String, String> issueSystemHeaders(String deploymentId, String targetProfileId, String releaseId,
        String expectedFingerprint, String subjectId, String sessionId, List<String> grantedScopes, Duration ttl) {
        var snapshot = requireReleaseUnchanged(deploymentId, targetProfileId, releaseId, expectedFingerprint);
        requireProvisioned(releases.findById(releaseId).orElseThrow());
        return headers(snapshot.binding(), requiredIdentity(subjectId), "SYSTEM_PROCESS", requiredIdentity(sessionId),
            false, grantedScopes, ttl);
    }

    /** Fixed-scope worker boundary, only after actual provisioning of the exact frozen release. */
    @Transactional
    public Map<String, String> issueProviderBudgetProvisioningHeaders(DeploymentEntity deployment,
        DeploymentVersionEntity version, DeploymentReleaseEntity release) {
        releaseEnvironmentPlanForProvisioning(deployment, version, release);
        requireProvisioned(release);
        var snapshot = snapshots.findById(release.getRuntimeAuthoritySnapshotId()).orElseThrow();
        var checked = checkedSnapshot(deployment, authority(deployment, release.getTargetProfileId()),
            snapshot, release.getRuntimeAuthorityFingerprint());
        return headers(checked.binding(), "platform-provider-budget", "SYSTEM_PROCESS", release.getId(), false,
            List.of("runtime:admin:overview", "runtime:admin:provider-budget:write"), Duration.ofMinutes(2));
    }

    /**
     * Scheduler-only authority for an exact frozen release. Unlike the browser path this does not
     * consult a request principal; the caller must already hold the durable target-retirement fence.
     */
    @Transactional
    public Map<String, String> issueTargetRetirementHeaders(String deploymentId, String targetProfileId,
        String releaseId, String expectedFingerprint, String operationId, List<String> grantedScopes) {
        var release = releases.findById(required(releaseId))
            .filter(value -> deploymentId.equals(value.getDeploymentId())
                && targetProfileId.equals(value.getTargetProfileId()))
            .orElseThrow(() -> conflict("Retirement release does not belong to the exact target."));
        var deployment = deployments.findById(required(deploymentId))
            .orElseThrow(() -> conflict("Retirement deployment is unavailable."));
        var snapshot = snapshots.findByReleaseId(releaseId)
            .orElseThrow(() -> conflict("Retirement release lacks frozen target authority evidence."));
        if (!release.getDeploymentVersionId().equals(snapshot.getVersionId())
            || !expectedFingerprint.equals(snapshot.getFingerprint())) {
            throw conflict("Retirement release authority evidence changed.");
        }
        var checked = checkedSnapshot(deployment, authority(deployment, targetProfileId), snapshot, expectedFingerprint);
        requireProvisioned(release);
        String subject = "target-retirement:" + requiredIdentity(operationId);
        return headers(checked.binding(), subject, "SYSTEM_PROCESS", subject, false,
            grantedScopes, Duration.ofMinutes(2));
    }

    @Transactional
    public Map<String, String> issuePlatformProxyHeaders(String deploymentId, String targetProfileId, String releaseId,
        String expectedFingerprint, List<String> grantedScopes, Duration ttl) {
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null) throw new ResponseStatusException(FORBIDDEN, "An authenticated Platform operator is required.");
        var snapshot = requireReleaseUnchanged(deploymentId, targetProfileId, releaseId, expectedFingerprint);
        requireProvisioned(releases.findById(releaseId).orElseThrow());
        return headers(snapshot.binding(), requiredIdentity(principal.actorId()), "INTERNAL_PLATFORM_USER",
            "target-proxy-" + snapshot.snapshotId(), true, grantedScopes, ttl);
    }

    /** Identity and scopes must come from the caller's verified session, never browser claims. */
    @Transactional
    public Map<String, String> issueBackendSessionHeaders(String deploymentId, String targetProfileId, String releaseId,
        String expectedFingerprint, String subjectId, String sessionId, boolean anonymous, List<String> grantedScopes, Duration ttl) {
        var snapshot = requireReleaseUnchanged(deploymentId, targetProfileId, releaseId, expectedFingerprint);
        requireProvisioned(releases.findById(releaseId).orElseThrow());
        if (anonymous && !Objects.equals(subjectId, sessionId)) throw new ResponseStatusException(BAD_REQUEST, "Anonymous session identity is inconsistent.");
        return headers(snapshot.binding(), requiredIdentity(subjectId), anonymous ? "ANONYMOUS_SESSION" : "END_USER",
            requiredIdentity(sessionId), false, grantedScopes, ttl);
    }

    @Transactional
    public long rotate(String deploymentId, String targetProfileId, long expectedGeneration) {
        var deployment = lock(deploymentId, targetProfileId, true);
        var authority = authority(deployment, targetProfileId);
        requireActive(authority);
        requireGeneration(authority, expectedGeneration);
        var revisions = revisions(authority);
        var integrity = integrity(authority);
        for (var entry : revisions.entrySet()) ownedSecret(authority, entry.getKey(), entry.getValue());
        authority.setGeneration(Math.addExact(authority.getGeneration(), 1));
        for (var purpose : List.copyOf(revisions.keySet())) generate(authority, purpose, revisions, integrity);
        saveSecrets(authority, revisions, integrity);
        audit.record("RUNTIME_TARGET_AUTHORITY_ROTATED", "DEPLOYMENT_RUNTIME_AUTHORITY", authority.getId(), details(authority, Map.of()));
        return authority.getGeneration();
    }

    /** Caller completes drain/provider revocation first; this never claims a live runtime was disabled. */
    @Transactional
    public void revoke(String deploymentId, String targetProfileId, long expectedGeneration) {
        var deployment = lock(deploymentId, targetProfileId, false);
        var authority = authority(deployment, targetProfileId);
        requireGeneration(authority, expectedGeneration);
        if ("REVOKED".equals(authority.getState())) return;
        var revisions = revisions(authority);
        var integrity = integrity(authority);
        for (var entry : revisions.entrySet()) {
            if (secrets.existsById(entry.getValue().reference())) checkedSecret(authority, entry.getKey(), entry.getValue(), integrity);
        }
        for (var entry : revisions.entrySet()) secretService.clearManagedSecret(entry.getValue().reference(),
            details(authority, Map.of("purpose", entry.getKey().name())));
        authority.setState("REVOKED");
        authority.setUpdatedAt(Instant.now());
        authorities.save(authority);
        audit.record("RUNTIME_TARGET_AUTHORITY_REVOKED", "DEPLOYMENT_RUNTIME_AUTHORITY", authority.getId(), details(authority, Map.of()));
    }

    private DeploymentEntity lock(String deploymentId, String targetProfileId, boolean requireActiveTarget) {
        return lock(deploymentId, targetProfileId, requireActiveTarget, true);
    }

    private DeploymentEntity lock(String deploymentId, String targetProfileId, boolean requireActiveTarget,
                                  boolean requireOperatorAccess) {
        required(deploymentId);
        required(targetProfileId);
        var deployment = deployments.findByIdForUpdate(deploymentId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found."));
        if (requireOperatorAccess) access.requireDeploymentOperatorAccess(deployment);
        required(deployment.getCustomerId());
        required(deployment.getTenantId());
        var target = targets.findById(targetProfileId).orElseThrow(() -> conflict("Explicit target profile is unavailable."));
        if (requireActiveTarget && (!target.isActive() || deployment.getArchivedAt() != null || "ARCHIVED".equals(deployment.getStatus()))) {
            throw conflict("Target authority is unavailable for an inactive target or archived deployment.");
        }
        return deployment;
    }

    private DeploymentVersionEntity version(DeploymentEntity deployment, String versionId) {
        var version = versions.findById(required(versionId)).filter(v -> deployment.getId().equals(v.getDeploymentId()))
            .orElseThrow(() -> conflict("Explicit candidate version does not belong to the deployment."));
        if (!"PUBLISHED".equals(version.getStatus()) || version.getConfigHash() == null || version.getConfigHash().isBlank()) {
            throw conflict("Target authority requires an immutable published candidate.");
        }
        return version;
    }

    private DeploymentReleaseEntity release(DeploymentEntity deployment, String targetProfileId, String releaseId) {
        return releases.findById(required(releaseId))
            .filter(r -> deployment.getId().equals(r.getDeploymentId()) && targetProfileId.equals(r.getTargetProfileId()))
            .orElseThrow(() -> conflict("Release does not belong to the explicit deployment and target."));
    }

    private RuntimeAuthorityEntity newAuthority(DeploymentEntity deployment, String target) {
        var authority = new RuntimeAuthorityEntity();
        authority.setId(hash(write(List.of(deployment.getCustomerId(), deployment.getTenantId(), deployment.getId(), target))));
        authority.setCustomerId(deployment.getCustomerId());
        authority.setTenantId(deployment.getTenantId());
        authority.setDeploymentId(deployment.getId());
        authority.setTargetProfileId(target);
        authority.setGeneration(1);
        authority.setState("ACTIVE");
        authority.setSecretRevisionsJson("{}");
        authority.setSecretIntegrityJson("{}");
        authority.setCreatedAt(Instant.now());
        return authority;
    }

    private RuntimeAuthorityEntity authority(DeploymentEntity deployment, String target) {
        var authority = authorities.findByDeploymentIdAndTargetProfileId(deployment.getId(), target)
            .orElseThrow(() -> conflict("Explicit target authority has not been generated."));
        requireOwner(deployment, authority);
        return authority;
    }

    private Set<RuntimeAuthorityPurpose> requiredPurposes(DeploymentEntity deployment, DeploymentVersionEntity version) {
        var result = EnumSet.of(TRUSTED_BACKEND_API_KEY, PRIVATE_ASSERTION_SIGNING_KEY, PUBLIC_TOKEN_SIGNING_KEY);
        var legacyNames = executionSecrets.requiredSecretNames(deployment, version);
        for (var purpose : RuntimeAuthorityPurpose.values()) {
            String legacyName = purpose.legacyName(deployment.getId());
            if (legacyName != null && legacyNames.contains(legacyName)) result.add(purpose);
        }
        if (result.contains(CONVERSATION_HANDOVER_ENCRYPTION)) result.add(CONVERSATION_HANDOVER_CALLBACK_SIGNING);
        return result;
    }

    private RuntimeAuthorityBinding binding(DeploymentEntity deployment, DeploymentVersionEntity version,
                                             RuntimeAuthorityEntity authority, List<String> origins) {
        var refs = new EnumMap<RuntimeAuthorityPurpose, SecretRevision>(RuntimeAuthorityPurpose.class);
        var all = revisions(authority);
        for (var purpose : requiredPurposes(deployment, version)) {
            var revision = all.get(purpose);
            if (revision == null) throw conflict("Required target authority secret slot is missing.");
            checkedSecret(authority, purpose, revision, integrity(authority));
            refs.put(purpose, revision);
        }
        try {
            var security = mapper.readTree(version.getSecurityConfigJson());
            boolean bootstrap = ManagedDeploymentProfileCatalog.publicRuntimeBootstrapEnabled(security);
            boolean allowAnonymous = bootstrap && ManagedDeploymentProfileCatalog.AUTHZ_MODE_ALLOW_VERIFIED.equals(
                ManagedDeploymentProfileCatalog.resolveAuthzMode(security));
            if (bootstrap && origins.isEmpty()) throw conflict("Anonymous bootstrap requires reviewed exact target origins.");
            String scope = authority.getId() + ":" + authority.getGeneration();
            return new RuntimeAuthorityBinding(deployment.getCustomerId(), deployment.getTenantId(), deployment.getId(),
                authority.getTargetProfileId(), version.getId(), version.getConfigHash(), authority.getGeneration(),
                "platform-target:" + scope, "runtime-private:" + scope,
                "runtime-public:" + scope, "runtime-public:" + scope, bootstrap, allowAnonymous, origins, refs);
        } catch (ResponseStatusException ex) { throw ex;
        } catch (Exception ex) { throw conflict("Published runtime security configuration is invalid."); }
    }

    private Snapshot checkedSnapshot(DeploymentEntity deployment, RuntimeAuthorityEntity authority,
                                      RuntimeAuthoritySnapshotEntity snapshot, String expectedFingerprint) {
        requireActive(authority);
        if (expectedFingerprint == null || !expectedFingerprint.equals(snapshot.getFingerprint())
            || !deployment.getId().equals(snapshot.getDeploymentId()) || !authority.getTargetProfileId().equals(snapshot.getTargetProfileId())) {
            throw conflict("Frozen target authority fingerprint or ownership does not match.");
        }
        var frozen = read(snapshot.getBindingJson(), RuntimeAuthorityBinding.class);
        if (!expectedFingerprint.equals(hash(write(frozen))) || !snapshot.getVersionId().equals(frozen.versionId())) {
            throw conflict("Frozen target authority metadata is inconsistent.");
        }
        var current = binding(deployment, version(deployment, snapshot.getVersionId()), authority, frozen.allowedOrigins());
        if (!expectedFingerprint.equals(hash(write(current)))) throw conflict("Target authority drifted; a new apply and verification is required.");
        return projection(snapshot, frozen);
    }

    private PlatformSecretEntity checkedSecret(RuntimeAuthorityEntity authority, RuntimeAuthorityPurpose purpose,
                                                SecretRevision revision, Map<RuntimeAuthorityPurpose, String> integrity) {
        var secret = ownedSecret(authority, purpose, revision);
        if (secret.getUpdatedAt() == null || !secret.getUpdatedAt().toString().equals(revision.revision())
            || secret.getSecretValue() == null || secret.getSecretValue().isBlank()
            || !Objects.equals(integrity.get(purpose), hash(secret.getSecretValue()))) {
            throw conflict("Target authority secret changed; rotate and reverify.");
        }
        return secret;
    }

    private PlatformSecretEntity ownedSecret(RuntimeAuthorityEntity authority, RuntimeAuthorityPurpose purpose, SecretRevision revision) {
        if (!reference(authority, purpose).equals(revision.reference())) throw conflict("Target secret reference ownership is inconsistent.");
        var secret = secrets.findById(revision.reference()).orElseThrow(() -> conflict("Target authority secret is missing or revoked."));
        if (!authority.getDeploymentId().equals(secret.getDeploymentId()) || !secret.isManagedByPlatform()
            || secret.getScopeType() != PlatformSecretScopeType.DEPLOYMENT_MANAGED
            || secret.getOwnerType() != PlatformSecretOwnerType.PLATFORM_MANAGED) {
            throw conflict("Target authority secret has inconsistent managed ownership.");
        }
        return secret;
    }

    private void generate(RuntimeAuthorityEntity authority, RuntimeAuthorityPurpose purpose,
                            Map<RuntimeAuthorityPurpose, SecretRevision> revisions, Map<RuntimeAuthorityPurpose, String> integrity) {
        byte[] random = new byte[48];
        RANDOM.nextBytes(random);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String ref = reference(authority, purpose);
        secretService.upsertManagedSecret(ref, value, details(authority, Map.of("purpose", purpose.name(), "owner", "TARGET_RUNTIME")));
        var stored = secrets.findById(ref).orElseThrow(() -> conflict("Target secret persistence did not provide revision evidence."));
        if (stored.getUpdatedAt() == null || !value.equals(stored.getSecretValue())) throw conflict("Target secret persistence is inconsistent.");
        stored.setUpdatedAt(stored.getUpdatedAt().truncatedTo(ChronoUnit.MICROS));
        secrets.saveAndFlush(stored);
        revisions.put(purpose, new SecretRevision(ref, stored.getUpdatedAt().toString()));
        integrity.put(purpose, hash(value));
    }

    private void saveSecrets(RuntimeAuthorityEntity authority, Map<RuntimeAuthorityPurpose, SecretRevision> refs,
                               Map<RuntimeAuthorityPurpose, String> integrity) {
        authority.setSecretRevisionsJson(write(refs));
        authority.setSecretIntegrityJson(write(integrity));
        authority.setUpdatedAt(Instant.now());
        authorities.save(authority);
    }

    private Map<String, String> headers(RuntimeAuthorityBinding binding, String subject, String subjectType, String session,
                                         boolean proxy, List<String> scopes, Duration ttl) {
        Duration lifetime = ttl == null ? Duration.ofMinutes(5) : ttl;
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Target assertion lifetime must be positive and at most ten minutes.");
        }
        if (scopes == null || scopes.isEmpty() || scopes.stream().anyMatch(s -> s == null || !s.matches("[a-z0-9][a-z0-9:_-]{0,127}"))) {
            throw new ResponseStatusException(BAD_REQUEST, "Explicit verified identity scopes are required.");
        }
        var authority = authorities.findByDeploymentIdAndTargetProfileId(binding.deploymentId(), binding.targetProfileId()).orElseThrow();
        var integrity = integrity(authority);
        String trustedKey = checkedSecret(authority, TRUSTED_BACKEND_API_KEY, binding.secrets().get(TRUSTED_BACKEND_API_KEY), integrity).getSecretValue();
        String signingKey = checkedSecret(authority, PRIVATE_ASSERTION_SIGNING_KEY, binding.secrets().get(PRIVATE_ASSERTION_SIGNING_KEY), integrity).getSecretValue();
        var claims = new RuntimePrivateAssertionClaims(subject, subjectType,
            proxy ? "PLATFORM_PROXY_SESSION" : "PRIVATE_RUNTIME_BACKEND_MEDIATED", proxy ? "PLATFORM_PROXY" : "TRUSTED_BACKEND",
            session, binding.deploymentId(), binding.customerId(), binding.tenantId(), binding.privateIssuer(), Instant.now().plus(lifetime),
            List.of(binding.privateAudience()), scopes.stream().distinct().sorted().toList());
        return Map.of(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER, trustedKey,
            RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER,
            RuntimePrivateAssertionSigningService.TOKEN_SCHEME + " " + signer.issueAssertionWithSigningKey(claims, signingKey));
    }

    private EnvironmentPlan environment(RuntimeAuthorityBinding binding) {
        var runtime = new LinkedHashMap<String, String>();
        var connector = new LinkedHashMap<String, String>();
        binding.secrets().forEach((purpose, revision) -> {
            String placeholder = "${secret:" + revision.reference() + "}";
            purpose.runtimeEnvironmentNames().forEach(name -> runtime.put(name, placeholder));
            purpose.connectorEnvironmentNames().forEach(name -> connector.put(name, placeholder));
        });
        runtime.put("AI_FABRIC_RUNTIME_AUTH_INGRESS_MODE", "VERIFIED_CONTEXT_REQUIRED");
        runtime.put("AI_FABRIC_RUNTIME_REJECT_CONFLICTING_REQUEST_IDENTITY", "true");
        runtime.put("AI_FABRIC_RUNTIME_REJECT_REQUEST_IDENTITY_WHEN_VERIFIED_CONTEXT_PRESENT", "true");
        runtime.put("AI_FABRIC_RUNTIME_AUTH_ACCEPTED_ISSUERS", binding.privateIssuer());
        runtime.put("AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES", binding.privateAudience());
        runtime.put("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_ISSUER", binding.publicIssuer());
        runtime.put("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_ACCEPTED_ISSUERS", binding.publicIssuer());
        runtime.put("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_DEFAULT_AUDIENCE", binding.publicAudience());
        runtime.put("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_ACCEPTED_AUDIENCES", binding.publicAudience());
        runtime.put("AI_FABRIC_RUNTIME_DEPLOYMENT_ID", binding.deploymentId());
        runtime.put("AI_FABRIC_RUNTIME_CUSTOMER_ID", binding.customerId());
        runtime.put("AI_FABRIC_RUNTIME_TENANT_ID", binding.tenantId());
        runtime.put("AI_FABRIC_RUNTIME_PUBLIC_BOOTSTRAP_ENABLED", Boolean.toString(binding.anonymousBootstrapEnabled()));
        runtime.put("AI_FABRIC_RUNTIME_AUTHZ_ALLOW_ANONYMOUS_SESSIONS", Boolean.toString(binding.allowAnonymousSessions()));
        runtime.put("AI_FABRIC_RUNTIME_PUBLIC_BOOTSTRAP_ALLOW_MISSING_ORIGIN", "false");
        runtime.put("AI_FABRIC_RUNTIME_PUBLIC_BOOTSTRAP_ALLOWED_ORIGINS", String.join(",", binding.allowedOrigins()));
        runtime.put("CORS_ALLOWED_ORIGINS", String.join(",", binding.allowedOrigins()));
        if (binding.secrets().containsKey(INTEGRATION_SERVICE_API_KEY)) {
            runtime.put("AI_FABRIC_RUNTIME_AUTH_INGRESS_INTEGRATION_SERVICE_ENABLED", "true");
            runtime.put("AI_FABRIC_RUNTIME_AUTH_INGRESS_INTEGRATION_SERVICE_DEPLOYMENT_ID", binding.deploymentId());
            runtime.put("AI_FABRIC_RUNTIME_AUTH_INGRESS_INTEGRATION_SERVICE_TENANT_ID", binding.tenantId());
        } else runtime.put("AI_FABRIC_RUNTIME_AUTH_INGRESS_INTEGRATION_SERVICE_ENABLED", "false");
        return new EnvironmentPlan(runtime, connector);
    }

    private RuntimeAuthoritySnapshotEntity snapshot(String id, RuntimeAuthorityBinding binding, String releaseId, String fingerprint) {
        var snapshot = new RuntimeAuthoritySnapshotEntity();
        snapshot.setId(id);
        snapshot.setDeploymentId(binding.deploymentId());
        snapshot.setTargetProfileId(binding.targetProfileId());
        snapshot.setVersionId(binding.versionId());
        snapshot.setReleaseId(releaseId);
        snapshot.setBindingJson(write(binding));
        snapshot.setFingerprint(fingerprint);
        snapshot.setCreatedAt(Instant.now());
        return snapshot;
    }

    private Snapshot projection(RuntimeAuthoritySnapshotEntity entity, RuntimeAuthorityBinding binding) {
        return new Snapshot(entity.getId(), entity.getReleaseId(), entity.getFingerprint(), binding);
    }

    private RuntimeAuthoritySnapshotEntity requireSnapshot(String id) {
        return snapshots.findById(required(id)).orElseThrow(() -> conflict("Frozen authority candidate is unavailable."));
    }

    private String reference(RuntimeAuthorityEntity authority, RuntimeAuthorityPurpose purpose) {
        return "MANAGED_RTA_" + purpose.name() + "_" + authority.getId().toUpperCase(java.util.Locale.ROOT);
    }

    private Map<RuntimeAuthorityPurpose, SecretRevision> revisions(RuntimeAuthorityEntity authority) {
        return read(authority.getSecretRevisionsJson(), new TypeReference<EnumMap<RuntimeAuthorityPurpose, SecretRevision>>() { });
    }

    private Map<RuntimeAuthorityPurpose, String> integrity(RuntimeAuthorityEntity authority) {
        return read(authority.getSecretIntegrityJson(), new TypeReference<EnumMap<RuntimeAuthorityPurpose, String>>() { });
    }

    private Map<String, Object> details(RuntimeAuthorityEntity authority, Map<String, ?> extra) {
        var details = new LinkedHashMap<String, Object>();
        details.put("deploymentId", authority.getDeploymentId());
        details.put("targetProfileId", authority.getTargetProfileId());
        details.put("customerId", authority.getCustomerId());
        details.put("tenantId", authority.getTenantId());
        details.put("generation", authority.getGeneration());
        details.putAll(extra);
        return details;
    }

    private void requireOwner(DeploymentEntity deployment, RuntimeAuthorityEntity authority) {
        if (!deployment.getId().equals(authority.getDeploymentId()) || !deployment.getCustomerId().equals(authority.getCustomerId())
            || !deployment.getTenantId().equals(authority.getTenantId())
            || !authority.getId().equals(hash(write(List.of(deployment.getCustomerId(), deployment.getTenantId(),
                deployment.getId(), authority.getTargetProfileId()))))) throw conflict("Target authority owner changed.");
    }

    private void requireActive(RuntimeAuthorityEntity authority) {
        if (!"ACTIVE".equals(authority.getState())) throw conflict("Target authority is revoked; it cannot be silently recreated.");
    }

    private void requireGeneration(RuntimeAuthorityEntity authority, long generation) {
        if (generation != authority.getGeneration()) throw conflict("Target authority generation changed.");
    }

    private void requireProvisioned(DeploymentReleaseEntity release) {
        if (!"ACTIVE".equals(release.getProvisioningStatus()) || !ADMINISTRABLE_RELEASES.contains(release.getStatus())) {
            throw conflict("Explicit release is not provisioned for runtime access.");
        }
    }

    private List<String> origins(List<String> values) {
        if (values == null) throw new ResponseStatusException(BAD_REQUEST, "Reviewed target origins must be explicit, even when empty.");
        return values.stream().map(value -> {
            try {
                URI uri = URI.create(value);
                if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || !uri.getRawPath().isEmpty()) throw new IllegalArgumentException();
                return uri.toString();
            } catch (Exception ex) { throw new ResponseStatusException(BAD_REQUEST, "Target origins must be exact HTTPS origins without paths or wildcards."); }
        }).distinct().sorted().toList();
    }

    private String required(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 64) {
            throw new ResponseStatusException(BAD_REQUEST, "Explicit authority identity is required and must not exceed 64 characters.");
        }
        return value;
    }

    private String requiredIdentity(String value) {
        if (value == null || value.isBlank() || value.length() > 256 || value.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(BAD_REQUEST, "Explicit verified subject and session identity are required.");
        }
        return value;
    }

    private String write(Object value) {
        try { return mapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(value);
        } catch (Exception ex) { throw conflict("Target authority metadata cannot be encoded."); }
    }

    private <T> T read(String json, Class<T> type) {
        try { return mapper.readValue(json, type);
        } catch (Exception ex) { throw conflict("Stored target authority metadata is invalid."); }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try { return mapper.readValue(json, type);
        } catch (Exception ex) { throw conflict("Stored target authority revision metadata is invalid."); }
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("SHA-256 is unavailable."); }
    }

    private ResponseStatusException conflict(String message) { return new ResponseStatusException(CONFLICT, message); }
}
