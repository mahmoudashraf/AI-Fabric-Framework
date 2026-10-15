package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionRules.*;
import static org.springframework.http.HttpStatus.*;

/** Independent sidecar: no provider writes, scheduler, bootstrap or lifecycle wiring. */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class DeploymentAdmissionService {
    private final AdmissionStore store;
    private final AdmissionJson json;
    private final ObjectMapper mapper;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentTargetProfileRepository targets;
    private final DeploymentAccessService access;
    private final PlatformCustomerAccessService customersAccess;
    private final PlatformCustomerRepository customers;
    private final PlatformTenantRepository tenants;
    private final ObjectProvider<AdmissionEvidenceSource> evidenceSources;
    private final ObjectProvider<AdmissionResourceReadbackSource> resourceSources;
    private final ObjectProvider<AdmissionNoticeDeliverySource> noticeSources;

    public DeploymentAdmissionService(AdmissionStore store, AdmissionJson json, ObjectMapper mapper,
                                      DeploymentRepository deployments, DeploymentVersionRepository versions,
                                      DeploymentTargetProfileRepository targets, DeploymentAccessService access,
                                      PlatformCustomerAccessService customersAccess, PlatformCustomerRepository customers,
                                      PlatformTenantRepository tenants, ObjectProvider<AdmissionEvidenceSource> evidenceSources,
                                      ObjectProvider<AdmissionResourceReadbackSource> resourceSources,
                                      ObjectProvider<AdmissionNoticeDeliverySource> noticeSources) {
        this.store = store; this.json = json; this.mapper = mapper; this.deployments = deployments; this.versions = versions;
        this.targets = targets; this.access = access; this.customersAccess = customersAccess; this.customers = customers;
        this.tenants = tenants; this.evidenceSources = evidenceSources; this.resourceSources = resourceSources; this.noticeSources = noticeSources;
    }

    /** Staff reviews measured policy; there are no defaults and registration does not publish the offer. */
    public void approvePolicy(FleetPolicy policy) {
        staff(); policy(policy, Instant.now());
        policy.targets().values().forEach(this::validateTarget);
        store.approvePolicy(policy, actor(), Instant.now());
    }
    public void approveCustomerGrant(CustomerGrant grant) {
        staff(); grant(grant, Instant.now()); activeCustomer(grant.customerId());
        store.approveGrant(grant, actor(), Instant.now());
    }
    public void approveManifest(Manifest manifest) {
        staff(); manifest(manifest);
        DeploymentEntity deployment = authorize(manifest.deploymentId(), true);
        validateVersion(deployment, manifest);
        store.approveManifest(manifest, actor(), Instant.now());
    }
    public Reservation reserve(String deploymentId, Reserve command) {
        return reserve(deploymentId, null, command, false);
    }
    /** Reserve against the exact customer authorization context prepared before admission evidence exists. */
    public Reservation reserveCustomer(String deploymentId, String expectedVersionId, Reserve command) {
        required(expectedVersionId, 64);
        if (command == null || command.customerContext() == null) {
            throw new AdmissionFailure(BAD_REQUEST, "CUSTOMER_ADMISSION_CONTEXT_REQUIRED");
        }
        return reserve(deploymentId, expectedVersionId, command, true);
    }
    private Reservation reserve(String deploymentId, String expectedVersionId, Reserve command, boolean customerScoped) {
        DeploymentEntity deployment = authorize(deploymentId, true);
        if (command == null) throw new AdmissionFailure(BAD_REQUEST, "ADMISSION_COMMAND_REQUIRED");
        required(command.operationKey(), 128); required(command.manifestId(), 64);
        Manifest manifest = store.reviewed("MANIFEST", command.manifestId(), Manifest.class);
        if (!deployment.getId().equals(manifest.deploymentId())) throw new AdmissionFailure(NOT_FOUND, "MANIFEST_NOT_FOUND");
        if (expectedVersionId != null && !expectedVersionId.equals(manifest.versionId())) {
            throw new AdmissionFailure(CONFLICT, "REVIEWED_VERSION_CHANGED");
        }
        if (customerScoped) validateCustomerScope(deployment, manifest, command);
        String fingerprint = store.commandFingerprint(deployment.getCustomerId(), deployment.getTenantId(), deploymentId, manifest, command);
        Reservation replay = store.replay(deployment.getCustomerId(), deploymentId, manifest.targetProfileId(), command, fingerprint);
        if (replay != null) return replay;
        AdmissionContext context = store.context(deployment.getCustomerId(), deployment.getTenantId(), deploymentId, manifest.id());
        PrerequisiteReadback evidence = validateAdmission(deployment, context);
        // Slow provider/prerequisite reads are above, never inside the ledger transaction.
        return store.reserve(context, command, evidence, actor(), Instant.now(), () -> localGuard(deploymentId, context));
    }
    public Reservation status(String deploymentId, String reservationId) {
        return owned(deploymentId, reservationId, false);
    }
    /** Call and commit before initiating ANY provisioning; persist generation markers in every owned handle. */
    public Reservation beginProvisioning(String deploymentId, Fence fence) {
        Reservation reservation = owned(deploymentId, id(fence), true); staff();
        DeploymentEntity deployment = authorize(deploymentId, true);
        AdmissionContext context = store.context(reservation.customerId(), reservation.tenantId(), deploymentId, reservation.manifestId());
        PrerequisiteReadback evidence = validateAdmission(deployment, context);
        if (!context.manifestFingerprint().equals(reservation.manifestFingerprint()) || !context.policy().id().equals(reservation.policyId()))
            throw new AdmissionFailure(CONFLICT, "RESERVATION_POLICY_CHANGED");
        return store.beginProvisioning(fence, context, evidence, actor(), Instant.now(), () -> localGuard(deploymentId, context));
    }
    public Reservation markOutcomeUnknown(String deploymentId, Fence fence, String evidenceReference) {
        owned(deploymentId, id(fence), true); staff();
        return store.outcome(fence, State.OUTCOME_UNKNOWN, evidenceReference, actor(), Instant.now());
    }
    public Reservation markCleanupFailed(String deploymentId, Fence fence, String evidenceReference) {
        owned(deploymentId, id(fence), true); staff();
        return store.outcome(fence, State.CLEANUP_FAILED, evidenceReference, actor(), Instant.now());
    }
    /** Completion/release is authoritative readback, not an 'apply succeeded' or 'delete requested' callback. */
    public Reservation reconcile(String deploymentId, Fence fence, boolean releaseCapacity) {
        Reservation r = owned(deploymentId, id(fence), true); staff();
        FleetPolicy policy = store.reviewed("POLICY", r.policyId(), FleetPolicy.class);
        ResourceReadback evidence = one(resourceSources, "RESOURCE_READBACK_ADAPTER_REQUIRED").read(new ReadbackContext(r, policy, r.workerToken()));
        return store.reconcile(fence, evidence, releaseCapacity, actor(), Instant.now());
    }
    /** Only trusted accepted-work hooks may record activity; health probes/UI polling have no enum value. */
    public Reservation recordActivity(String deploymentId, Fence fence, Activity activity, String evidenceReference) {
        owned(deploymentId, id(fence), true); staff();
        return store.activity(fence, activity, evidenceReference, actor(), Instant.now());
    }
    public Reservation extendStaging(String deploymentId, Fence fence, boolean keepAfterPromotion, String evidenceReference) {
        Reservation r = owned(deploymentId, id(fence), true);
        return store.extend(fence, keepAfterPromotion, store.customerGrant(r.customerId()), evidenceReference, actor(), Instant.now());
    }
    /** Integration polls the bounded list, then requests notices/claims per exact fence under staff identity. */
    public List<Reservation> dueStaging(int limit) {
        staff();
        if (limit < 1 || limit > 256) throw new AdmissionFailure(BAD_REQUEST, "WORK_LIMIT_INVALID");
        Instant now = Instant.now();
        return store.held().stream().filter(r -> r.intent() == Intent.CUSTOMER_STAGING)
            .filter(r -> {
                LifecyclePolicy p = store.reviewed("POLICY", r.policyId(), FleetPolicy.class).lifecycle();
                return !now.isBefore(r.expiresAt().minusSeconds(p.noticeLeadSeconds()));
            }).filter(r -> canAccess(r.deploymentId())).limit(limit).toList();
    }
    public List<NoticeWork> requestNotices(String deploymentId, Fence fence) {
        owned(deploymentId, id(fence), true); staff();
        return store.noticeWork(fence, actor(), Instant.now());
    }
    public Reservation confirmNoticeDelivery(String deploymentId, NoticeWork work) {
        if (work == null || work.channel() == null) throw new AdmissionFailure(BAD_REQUEST, "NOTICE_WORK_REQUIRED");
        owned(deploymentId, id(work.fence()), true); staff();
        NoticeDelivery evidence = one(noticeSources, "NOTICE_DELIVERY_ADAPTER_REQUIRED").read(work);
        return store.noticeDelivered(work, evidence, actor(), Instant.now());
    }
    public Reservation noticeDeliveryFailed(String deploymentId, Fence fence, String failureCode) {
        owned(deploymentId, id(fence), true); staff();
        return store.noticeFailed(fence, failureCode, actor(), Instant.now());
    }
    public ExpiryWork claimExpiry(String deploymentId, Fence fence) {
        owned(deploymentId, id(fence), true); staff();
        return store.claimExpiry(fence, actor(), Instant.now());
    }
    /** Restart readback returns persisted claim/deadlines, without assigning a second worker or deleting anything. */
    public ExpiryWork expiryWork(String deploymentId, String reservationId) {
        Reservation r = owned(deploymentId, reservationId, true); staff();
        return store.expiryWork(r);
    }
    public Reservation acknowledgeRetirementHandoff(String deploymentId, ExpiryWork work,
                                                      String retirementOperationId, String evidenceReference) {
        if (work == null) throw new AdmissionFailure(BAD_REQUEST, "EXPIRY_WORK_REQUIRED");
        owned(deploymentId, id(work.fence()), true); staff();
        return store.handoff(work, retirementOperationId, evidenceReference, actor(), Instant.now());
    }
    private PrerequisiteReadback validateAdmission(DeploymentEntity deployment, AdmissionContext context) {
        policy(context.policy(), Instant.now()); grant(context.grant(), Instant.now()); manifest(context.manifest());
        validateVersion(deployment, context.manifest()); validateTarget(context.target());
        PrerequisiteReadback evidence = one(evidenceSources, "ADMISSION_EVIDENCE_ADAPTER_REQUIRED").read(context);
        prerequisites(context, evidence, Instant.now());
        return evidence;
    }
    private void localGuard(String deploymentId, AdmissionContext context) {
        store.lockAuthoritativeRows(context);
        DeploymentEntity current = authorize(deploymentId, true);
        if (!context.customerId().equals(current.getCustomerId()) || !context.tenantId().equals(current.getTenantId()))
            throw new AdmissionFailure(CONFLICT, "ADMISSION_OWNERSHIP_CHANGED");
        validateVersion(current, context.manifest()); validateTarget(context.target());
    }
    private void validateVersion(DeploymentEntity deployment, Manifest manifest) {
        var version = versions.findById(manifest.versionId()).orElseThrow(() -> new AdmissionFailure(NOT_FOUND, "VERSION_NOT_FOUND"));
        if (!deployment.getId().equals(version.getDeploymentId()) || !manifest.versionConfigHash().equals(version.getConfigHash())
            || !"PUBLISHED".equalsIgnoreCase(version.getStatus())) throw new AdmissionFailure(CONFLICT, "REVIEWED_VERSION_CHANGED");
    }
    private void validateCustomerScope(DeploymentEntity deployment, Manifest manifest, Reserve command) {
        var scope = command.customerContext();
        Intent intent;
        try { intent = Intent.valueOf(scope.intent().name()); }
        catch (RuntimeException ex) { throw new AdmissionFailure(BAD_REQUEST, "CUSTOMER_ADMISSION_SCOPE_INVALID"); }
        TargetBudget target = store.context(deployment.getCustomerId(), deployment.getTenantId(), deployment.getId(), manifest.id()).target();
        if (scope.operation() == null || scope.limits() == null || scope.topology() == null || scope.entitlementRevision() <= 0
            || !deployment.getCustomerId().equals(scope.customerId()) || !deployment.getTenantId().equals(scope.tenantId())
            || !deployment.getId().equals(scope.deploymentId()) || !manifest.targetProfileId().equals(scope.targetProfileId())
            || target.intent() != intent || !command.operationKey().equals(scope.operationKey())
            || scope.offerId() == null || scope.offerId().isBlank()
            || scope.offerFingerprint() == null || scope.offerFingerprint().isBlank()
            || scope.placementEvidenceId() == null || scope.placementEvidenceId().isBlank()) {
            throw new AdmissionFailure(BAD_REQUEST, "CUSTOMER_ADMISSION_SCOPE_INVALID");
        }
    }
    private void validateTarget(TargetBudget target) {
        var profile = targets.findById(target.targetProfileId()).orElseThrow(() -> new AdmissionFailure(NOT_FOUND, "TARGET_NOT_FOUND"));
        if (!profile.isActive() || profile.getProviderType() != DeploymentProviderType.COOLIFY
            || "dtp-coolify-prod-staging".equals(profile.getId())) throw new AdmissionFailure(PRECONDITION_FAILED, "TARGET_NOT_ADMISSIBLE");
        try {
            var config = mapper.readTree(profile.getProviderConfigJson());
            if (!target.placement().serverUuid().equals(config.path("serverUuid").asText())
                || !target.placement().destinationUuid().equals(config.path("destinationUuid").asText()))
                throw new AdmissionFailure(PRECONDITION_FAILED, "PHYSICAL_PLACEMENT_FORBIDDEN");
            String fingerprint = json.fingerprint(Map.of("id", profile.getId(), "provider", profile.getProviderType(),
                "environment", profile.getEnvironmentName(), "sourceStrategy", profile.getSourceStrategy(),
                "credentialReference", profile.getCredentialRefId() == null ? "" : profile.getCredentialRefId(),
                "providerConfig", config, "networkPolicy", mapper.readTree(profile.getNetworkPolicyJson()),
                "resourceDefaults", mapper.readTree(profile.getResourceDefaultsJson())));
            if (!fingerprint.equals(target.profileFingerprint())) throw new AdmissionFailure(CONFLICT, "TARGET_PROFILE_CHANGED");
            placement(target.intent(), target.placement());
        } catch (AdmissionFailure ex) { throw ex; }
        catch (Exception ex) { throw new AdmissionFailure(PRECONDITION_FAILED, "TARGET_CONFIGURATION_INVALID"); }
    }
    private Reservation owned(String deploymentId, String reservationId, boolean mutate) {
        DeploymentEntity d = authorize(deploymentId, mutate); Reservation r = store.require(reservationId);
        if (!r.deploymentId().equals(d.getId()) || !r.customerId().equals(d.getCustomerId()) || !r.tenantId().equals(d.getTenantId()))
            throw new AdmissionFailure(NOT_FOUND, "RESERVATION_NOT_FOUND");
        return r;
    }
    private DeploymentEntity authorize(String deploymentId, boolean mutate) {
        if (!PlatformSecurityContext.isAuthenticated()) throw new AdmissionFailure(FORBIDDEN, "ACTIVE_ACTOR_REQUIRED");
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal.role() != PlatformRole.PLATFORM_ADMIN && principal.role() != PlatformRole.PLATFORM_OPERATOR
            && principal.role() != PlatformRole.CUSTOMER_ADMIN) throw new AdmissionFailure(FORBIDDEN, "ADMISSION_ACTOR_FORBIDDEN");
        DeploymentEntity d = deployments.findById(required(deploymentId, 64)).orElseThrow(() -> new AdmissionFailure(NOT_FOUND, "DEPLOYMENT_NOT_FOUND"));
        if (mutate) access.requireDeploymentOperatorAccess(d); else access.requireDeploymentAccess(d);
        if (principal.role() == PlatformRole.CUSTOMER_ADMIN) customersAccess.requireDeploymentCustomerAccess(d.getCustomerId());
        activeCustomer(d.getCustomerId());
        var tenant = tenants.findById(d.getTenantId()).orElseThrow(() -> new AdmissionFailure(FORBIDDEN, "ACTIVE_TENANT_REQUIRED"));
        if (!"ACTIVE".equalsIgnoreCase(tenant.getStatus()) || !d.getCustomerId().equals(tenant.getCustomerId())
            || d.getArchivedAt() != null || "DELETED".equalsIgnoreCase(d.getStatus()))
            throw new AdmissionFailure(FORBIDDEN, "ACTIVE_OWNERSHIP_REQUIRED");
        return d;
    }
    private void activeCustomer(String id) {
        var customer = customers.findById(required(id, 64)).orElseThrow(() -> new AdmissionFailure(FORBIDDEN, "ACTIVE_CUSTOMER_REQUIRED"));
        if (!"ACTIVE".equalsIgnoreCase(customer.getStatus())) throw new AdmissionFailure(FORBIDDEN, "ACTIVE_CUSTOMER_REQUIRED");
    }
    private boolean canAccess(String deploymentId) {
        try { authorize(deploymentId, true); return true; } catch (org.springframework.web.server.ResponseStatusException ex) { return false; }
    }
    private void staff() {
        if (!PlatformSecurityContext.isAuthenticated()) throw new AdmissionFailure(FORBIDDEN, "ACTIVE_STAFF_REQUIRED");
        var role = PlatformSecurityContext.currentPrincipal().role();
        if (role != PlatformRole.PLATFORM_ADMIN && role != PlatformRole.PLATFORM_OPERATOR)
            throw new AdmissionFailure(FORBIDDEN, "STAFF_REVIEW_REQUIRED");
    }
    private String actor() { return required(PlatformSecurityContext.actorIdOrSystem(), 255); }
    private String id(Fence fence) {
        if (fence == null) throw new AdmissionFailure(BAD_REQUEST, "FENCE_REQUIRED");
        return required(fence.reservationId(), 64);
    }
    private <T> T one(ObjectProvider<T> provider, String code) {
        List<T> beans = provider.orderedStream().toList();
        if (beans.size() != 1) throw new AdmissionFailure(PRECONDITION_FAILED, code);
        return beans.get(0);
    }
}
