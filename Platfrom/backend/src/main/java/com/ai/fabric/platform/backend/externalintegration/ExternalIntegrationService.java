package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerEntitlementCommandEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerEntitlementCommandRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerReviewedCompositionService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetProfileIdentity;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.*;
import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationPolicy.conflict;

@Service
public class ExternalIntegrationService {
    static final String BIND = "BIND_EXTERNAL_SERVICE", REVOKE = "REVOKE_EXTERNAL_SERVICE";
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final CustomerReviewedCompositionService composition;
    private final CustomerEntitlementCommandRepository commands;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentTargetProfileRepository targets;
    private final ExternalIntegrationPolicy policy;
    private final ExternalIntegrationIntegrity integrity;
    private final ExternalIntegrationStore store;
    private final PlatformSecretService vault;
    private final PlatformSecretRepository secrets;
    private final CustomerOfferCodec codec;
    private final ObjectMapper mapper;
    private final PlatformAuditService audit;
    private final Validator validator;

    public ExternalIntegrationService(CustomerOfferEntitlementService entitlements, CustomerSelfServiceAuthorizationService authorization,
        CustomerReviewedCompositionService composition, CustomerEntitlementCommandRepository commands, DeploymentRepository deployments,
        DeploymentVersionRepository versions, DeploymentTargetProfileRepository targets, ExternalIntegrationPolicy policy,
        ExternalIntegrationIntegrity integrity, ExternalIntegrationStore store, PlatformSecretService vault,
        PlatformSecretRepository secrets, CustomerOfferCodec codec, ObjectMapper mapper, PlatformAuditService audit, Validator validator) {
        this.entitlements = entitlements; this.authorization = authorization; this.composition = composition; this.commands = commands;
        this.deployments = deployments; this.versions = versions; this.targets = targets; this.policy = policy; this.integrity = integrity;
        this.store = store; this.vault = vault; this.secrets = secrets; this.codec = codec; this.mapper = mapper; this.audit = audit; this.validator = validator;
    }

    @Transactional
    public Summary approve(String offerId, String deploymentId, ApprovalRequest request) {
        entitlements.requireStaffAdmin(); validate(request); entitlements.requireOperationKey(request.operationKey());
        var deployment = lock(deploymentId, null);
        var context = context(offerId, deployment, request.versionId(), request.targetProfileId(), request.bindingSlotId(),
            request.selectedProfileId(), request.operationKey(), Operation.BINDING_REPLACE);
        var config = policy.textualObject(request.config(), 2048);
        var account = policy.textualObject(request.accountScope(), 2048);
        policy.validateConfig(context.profile(), config, account);
        String fingerprint = integrity.seal(Map.of("scope", context.scope(), "request", request,
            "actor", actorId(), "offerFingerprint", context.offerFingerprint(), "versionHash", context.version().getConfigHash(),
            "targetFingerprint", context.targetFingerprint()));
        var previous = store.approvalReplay(deploymentId, request.operationKey());
        if (previous.isPresent()) {
            var row = previous.get(); integrity.require(row.value(), row.integrity());
            if (!fingerprint.equals(row.value().requestFingerprint())) throw conflict("OPERATION_KEY_CONFLICT");
            return approvalSummary(row.value());
        }
        var approval = new Approval("eia-" + UUID.randomUUID(), context.scope(), context.offerFingerprint(), context.version().getConfigHash(),
            context.targetFingerprint(), context.profile(), config, account, request.reviewReference(), actorId(), request.operationKey(), fingerprint);
        store.insertApproval(approval, integrity.seal(approval));
        audit.record("EXTERNAL_INTEGRATION_REVIEWED", "DEPLOYMENT", deploymentId,
            Map.of("approvalId", approval.id(), "targetProfileId", request.targetProfileId(), "versionId", request.versionId(), "bindingSlotId", request.bindingSlotId()));
        return approvalSummary(approval);
    }

    @Transactional
    public Summary bind(String offerId, String deploymentId, BindRequest request) {
        validate(request); entitlements.requireOperationKey(request.operationKey());
        var actor = entitlements.requireCustomerActor();
        var deployment = lock(deploymentId, actor.customerId());
        var grant = entitlements.requireEntitlement(actor.customerId(), offerId, actor.userId(), true);
        var context = context(offerId, deployment, request.versionId(), request.targetProfileId(), request.bindingSlotId(),
            request.selectedProfileId(), request.operationKey(), Operation.BINDING_REPLACE);
        var credentials = policy.textualObject(request.credentials(), 16384);
        if (!credentials.keySet().equals(context.profile().secretFields()) || credentials.values().stream()
            .anyMatch(value -> !value.equals(value.trim()) || value.chars().anyMatch(Character::isISOControl)
                || value.contains("${") || value.contains("#{"))) throw conflict("CREDENTIAL_FIELDS_INVALID");
        String fingerprint = integrity.seal(Map.of("command", BIND, "scope", context.scope(), "approvalId", request.approvalId(),
            "revision", request.expectedEntitlementRevision(), "credentials", credentials, "actor", actorId()));
        Summary replay = replay(grant.getId(), request.operationKey(), BIND, fingerprint);
        if (replay != null) return replay;
        if (grant.getRevision() != request.expectedEntitlementRevision()) throw conflict("ENTITLEMENT_REVISION_CONFLICT");
        var approvalRow = store.approval(request.approvalId()).orElseThrow(() -> conflict("APPROVAL_REQUIRED"));
        integrity.require(approvalRow.value(), approvalRow.integrity());
        var approval = approvalRow.value(); requireApproval(context, approval);
        if (store.binding(deploymentId, request.versionId(), request.targetProfileId(), request.bindingSlotId()).isPresent()) {
            throw conflict("IMMUTABLE_BINDING_EXISTS_NEW_VERSION_REQUIRED");
        }
        String id = "eib-" + UUID.randomUUID();
        Map<String, String> references = new LinkedHashMap<>(), revisions = new LinkedHashMap<>();
        for (String field : context.profile().secretFields()) {
            String name = ExternalIntegrationCompilerResolver.secretName(id, context.scope(), field);
            vault.upsertManagedSecret(name, credentials.get(field), Map.of("deploymentId", deploymentId, "targetProfileId", request.targetProfileId(),
                "versionId", request.versionId(), "externalIntegrationBindingId", id));
            references.put(field, name);
        }
        // Read persisted timestamp precision, not a managed JPA entity's pre-round-trip Instant.
        secrets.flush();
        for (var entry : references.entrySet()) revisions.put(entry.getKey(), requireSecret(store, context.scope(), entry.getValue()).revision());
        Binding binding = new Binding(id, context.scope(), approval.id(), references, revisions);
        store.insertBinding(binding, integrity.seal(bindingEvidence(binding, "CONFIGURED", 0)));
        Summary result = summary(binding, "CONFIGURED_LOCAL_CREDENTIALS");
        persist(grant.getId(), request.operationKey(), BIND, fingerprint, result);
        audit.record("EXTERNAL_INTEGRATION_BOUND", "DEPLOYMENT", deploymentId,
            Map.of("bindingId", id, "approvalId", approval.id(), "versionId", request.versionId(), "targetProfileId", request.targetProfileId()));
        return result;
    }

    @Transactional
    public Summary revoke(String offerId, String deploymentId, RevokeRequest request) {
        validate(request); entitlements.requireOperationKey(request.operationKey());
        var actor = entitlements.requireCustomerActor(); var deployment = lock(deploymentId, actor.customerId());
        var grant = entitlements.requireEntitlement(actor.customerId(), offerId, actor.userId(), true);
        var context = context(offerId, deployment, request.versionId(), request.targetProfileId(), request.bindingSlotId(),
            request.selectedProfileId(), request.operationKey(), Operation.BINDING_REVOKE);
        String fingerprint = integrity.seal(Map.of("command", REVOKE, "scope", context.scope(), "request", request, "actor", actorId()));
        Summary replay = replay(grant.getId(), request.operationKey(), REVOKE, fingerprint);
        if (replay != null) return replay;
        if (grant.getRevision() != request.expectedEntitlementRevision()) throw conflict("ENTITLEMENT_REVISION_CONFLICT");
        var row = store.binding(deploymentId, request.versionId(), request.targetProfileId(), request.bindingSlotId())
            .orElseThrow(() -> conflict("BINDING_REQUIRED"));
        integrity.require(bindingEvidence(row.value(), row.status(), row.revision()), row.integrity());
        if (!context.scope().equals(row.value().scope()) || !request.bindingId().equals(row.value().id())
            || request.expectedBindingRevision() != row.revision()) throw conflict("BINDING_REVISION_CONFLICT");
        store.requireNoInProgressRelease(context.scope());
        store.revokeBinding(row.value().id(), row.revision(), integrity.seal(bindingEvidence(row.value(), "REVOKED", row.revision() + 1)));
        Summary result = summary(row.value(), "REVOKED_FUTURE_RESOLUTION_EXTERNAL_RETAINED", row.revision() + 1);
        persist(grant.getId(), request.operationKey(), REVOKE, fingerprint, result);
        audit.record("EXTERNAL_INTEGRATION_REVOKED", "DEPLOYMENT", deploymentId,
            Map.of("bindingId", row.value().id(), "externalDataRetained", true, "credentialsRetained", true));
        return result;
    }

    @Transactional
    public Summary read(String offerId, String deploymentId, String versionId, String targetId, String slotId, String profileId) {
        var actor = entitlements.requireCustomerActor();
        var deployment = deployments.findById(deploymentId).filter(value -> actor.customerId().equals(value.getCustomerId()))
            .orElseThrow(() -> conflict("RESOURCE_SCOPE_MISMATCH"));
        var context = context(offerId, deployment, versionId, targetId, slotId, profileId, "external-integration-read", Operation.READ);
        var row = store.binding(deploymentId, versionId, targetId, slotId).orElseThrow(() -> conflict("BINDING_REQUIRED"));
        integrity.require(bindingEvidence(row.value(), row.status(), row.revision()), row.integrity());
        if (!context.scope().equals(row.value().scope())) throw conflict("RESOURCE_SCOPE_MISMATCH");
        return summary(row.value(), row.status(), row.revision());
    }

    private Context context(String offerId, DeploymentEntity deployment, String versionId, String targetId,
                            String slotId, String profileId, String operationKey, Operation operation) {
        var approved = authorization.authorize(deployment.getCustomerId(), new AuthorizationRequest(offerId, targetId, deployment.getId(),
            deployment.getTenantId(), null, operation, operationKey, Set.of(), slotId, profileId, null));
        var profile = policy.requireProfile(approved.definition(), slotId, profileId, targetId);
        var version = versions.findById(versionId).filter(value -> deployment.getId().equals(value.getDeploymentId())
            && "PUBLISHED".equals(value.getStatus()) && value.getConfigHash() != null && !value.getConfigHash().isBlank())
            .orElseThrow(() -> conflict("VERSION_SCOPE_MISMATCH"));
        composition.requireVersion(approved.definition(), version);
        var target = targets.findById(targetId).filter(value -> value.isActive()).orElseThrow(() -> conflict("TARGET_REQUIRED"));
        var offer = entitlements.requireOffer(offerId);
        return new Context(new Scope(offerId, deployment.getCustomerId(), deployment.getTenantId(), deployment.getId(), versionId,
            targetId, slotId, profileId), profile, version, offer.getFingerprint(), DeploymentTargetProfileIdentity.fingerprint(mapper, target));
    }
    private void requireApproval(Context context, Approval approval) {
        if (!context.scope().equals(approval.scope()) || !context.profile().equals(approval.profile())
            || !context.offerFingerprint().equals(approval.offerFingerprint()) || !context.version().getConfigHash().equals(approval.versionConfigHash())
            || !context.targetFingerprint().equals(approval.targetFingerprint())) throw conflict("APPROVAL_STALE_OR_FOREIGN");
        policy.validateConfig(approval.profile(), approval.config(), approval.accountScope());
    }
    private DeploymentEntity lock(String deploymentId, String customerId) {
        return deployments.findByIdForUpdate(deploymentId).filter(value -> value.getCustomerId() != null && value.getTenantId() != null
            && (customerId == null || customerId.equals(value.getCustomerId()))).orElseThrow(() -> conflict("RESOURCE_SCOPE_MISMATCH"));
    }
    private Summary replay(String entitlementId, String key, String command, String fingerprint) {
        var previous = commands.findByEntitlementIdAndOperationKey(entitlementId, key).orElse(null);
        if (previous == null) return null;
        if (!command.equals(previous.getCommandType()) || !fingerprint.equals(previous.getRequestFingerprint())
            || !actorId().equals(previous.getActorId())) throw conflict("OPERATION_KEY_CONFLICT");
        return codec.read(previous.getResultJson(), Summary.class);
    }
    private void persist(String entitlementId, String key, String command, String fingerprint, Summary result) {
        commands.saveAndFlush(new CustomerEntitlementCommandEntity("cec-" + UUID.randomUUID(), entitlementId, key, fingerprint,
            command, actorId(), codec.write(result), Instant.now()));
    }
    static Map<String, Object> bindingEvidence(Binding binding, String status, long revision) {
        return Map.of("binding", binding, "status", status, "revision", revision);
    }
    static ExternalIntegrationStore.SecretEvidence requireSecret(ExternalIntegrationStore store, Scope scope, String name) {
        var evidence = store.secretEvidence(name).orElseThrow(() -> conflict("CREDENTIAL_REQUIRED"));
        if (!scope.deploymentId().equals(evidence.deploymentId()) || !"DEPLOYMENT_MANAGED".equals(evidence.scopeType())
            || !"PLATFORM_MANAGED".equals(evidence.ownerType()) || !evidence.managed() || !evidence.present() || evidence.revision() == null) {
            throw conflict("CREDENTIAL_OWNERSHIP_INVALID");
        }
        return evidence;
    }
    private static Summary approvalSummary(Approval value) { return new Summary(value.id(), value.id(), value.scope().deploymentId(),
        value.scope().versionId(), value.scope().targetProfileId(), value.scope().bindingSlotId(), value.scope().profileId(), "APPROVED_CONFIG_ONLY"); }
    static Summary summary(Binding value, String status) { return new Summary(value.id(), value.approvalId(), value.scope().deploymentId(),
        value.scope().versionId(), value.scope().targetProfileId(), value.scope().bindingSlotId(), value.scope().profileId(), status); }
    static Summary summary(Binding value, String status, long revision) { return new Summary(value.id(), value.approvalId(), value.scope().deploymentId(),
        value.scope().versionId(), value.scope().targetProfileId(), value.scope().bindingSlotId(), value.scope().profileId(), status, revision); }
    private String actorId() { return PlatformSecurityContext.currentPrincipal().actorId(); }
    private void validate(Object request) { if (request == null || !validator.validate(request).isEmpty()) throw conflict("REQUEST_INVALID"); }
    private record Context(Scope scope, Profile profile, DeploymentVersionEntity version, String offerFingerprint, String targetFingerprint) { }
}
