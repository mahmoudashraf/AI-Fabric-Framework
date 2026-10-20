package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Definition;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerReviewedCompositionService;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetProfileIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityBinding.Snapshot;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.*;
import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationPolicy.conflict;
import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationService.*;

/** Local evidence resolution is deliberately separate from DNS and frozen release authority. */
@Service
public class ExternalIntegrationResolver {
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentTargetProfileRepository targets;
    private final DeploymentReleaseRepository releases;
    private final CustomerOfferDeploymentRepository associations;
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerReviewedCompositionService composition;
    private final DeploymentRuntimeAuthorityService authority;
    private final ExternalIntegrationStore store;
    private final ExternalIntegrationPolicy policy;
    private final ExternalIntegrationIntegrity integrity;
    private final ExternalIntegrationEndpointPolicy endpoints;
    private final ExternalIntegrationCompilerResolver compiler;
    private final CustomerOfferCodec codec;
    private final ObjectMapper mapper;

    public ExternalIntegrationResolver(DeploymentRepository deployments, DeploymentVersionRepository versions,
        DeploymentTargetProfileRepository targets, DeploymentReleaseRepository releases, CustomerOfferDeploymentRepository associations,
        CustomerOfferEntitlementService entitlements, CustomerReviewedCompositionService composition, DeploymentRuntimeAuthorityService authority,
        ExternalIntegrationStore store, ExternalIntegrationPolicy policy, ExternalIntegrationIntegrity integrity,
        ExternalIntegrationEndpointPolicy endpoints, ExternalIntegrationCompilerResolver compiler, CustomerOfferCodec codec, ObjectMapper mapper) {
        this.deployments = deployments; this.versions = versions; this.targets = targets; this.releases = releases; this.associations = associations;
        this.entitlements = entitlements; this.composition = composition; this.authority = authority; this.store = store; this.policy = policy;
        this.integrity = integrity; this.endpoints = endpoints; this.compiler = compiler; this.codec = codec; this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<Projection> resolveLocal(String deploymentId, String versionId, String targetId) {
        return resolve(context(deploymentId, versionId, targetId), false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Projection> requireForEffects(String deploymentId, String versionId, String targetId) {
        if (!required(context(deploymentId, versionId, targetId))) return List.of();
        store.fenceDeployment(deploymentId);
        Context context = context(deploymentId, versionId, targetId);
        fenceEntitlement(context);
        return resolve(context, true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ReleaseSnapshot freezeRelease(String deploymentId, String versionId, String targetId, String releaseId,
                                         String expectedAuthorityFingerprint) {
        Context context = context(deploymentId, versionId, targetId);
        if (!required(context)) return new ReleaseSnapshot(releaseId, expectedAuthorityFingerprint, List.of());
        store.fenceDeployment(deploymentId);
        context = context(deploymentId, versionId, targetId);
        fenceEntitlement(context);
        requireRelease(context, releaseId, expectedAuthorityFingerprint);
        List<Projection> projections = resolve(context, true);
        ReleaseSnapshot snapshot = new ReleaseSnapshot(releaseId, expectedAuthorityFingerprint, projections);
        var previous = store.snapshot(releaseId);
        if (previous.isPresent()) {
            integrity.require(previous.get().value(), previous.get().integrity());
            if (!snapshot.equals(previous.get().value())) throw conflict("FROZEN_RELEASE_CHANGED");
            return previous.get().value();
        }
        if (!projections.isEmpty()) store.insertSnapshot(snapshot, projections.getFirst().scope(), integrity.seal(snapshot));
        return snapshot;
    }

    @Transactional(propagation = Propagation.REQUIRED, timeout = 15)
    public ReleaseEnvironment releaseEnvironment(String deploymentId, String versionId, String targetId,
                                                  String releaseId, String expectedAuthorityFingerprint) {
        return releaseEnvironment(deploymentId, versionId, targetId, releaseId, expectedAuthorityFingerprint,
            () -> authority.requireReleaseUnchanged(deploymentId, targetId, releaseId, expectedAuthorityFingerprint));
    }

    /** Internal provisioning boundary; does not turn a missing request principal into API authority. */
    @Transactional(propagation = Propagation.REQUIRED, timeout = 15)
    public ReleaseEnvironment releaseEnvironmentForProvisioning(DeploymentEntity deployment,
        DeploymentVersionEntity version, DeploymentReleaseEntity release) {
        if (deployment == null || version == null || release == null) throw conflict("RELEASE_SCOPE_MISMATCH");
        return releaseEnvironment(deployment.getId(), version.getId(), release.getTargetProfileId(), release.getId(),
            release.getRuntimeAuthorityFingerprint(), () -> authority.requireReleaseUnchangedForProvisioning(deployment, version, release));
    }

    private ReleaseEnvironment releaseEnvironment(String deploymentId, String versionId, String targetId,
        String releaseId, String expectedAuthorityFingerprint, Supplier<Snapshot> checkedAuthority) {
        Context context = context(deploymentId, versionId, targetId);
        if (!required(context)) {
            if (store.snapshot(releaseId).isPresent()) throw conflict("FROZEN_RELEASE_CHANGED");
            return new ReleaseEnvironment(Map.of(), Map.of());
        }
        store.fenceDeployment(deploymentId);
        context = context(deploymentId, versionId, targetId);
        fenceEntitlement(context);
        requireRelease(context, releaseId, expectedAuthorityFingerprint, checkedAuthority);
        List<Projection> current = resolve(context, true);
        if (current.isEmpty()) {
            if (store.snapshot(releaseId).isPresent()) throw conflict("FROZEN_RELEASE_CHANGED");
            return new ReleaseEnvironment(Map.of(), Map.of());
        }
        var row = store.snapshot(releaseId).orElseThrow(() -> conflict("FROZEN_RELEASE_REQUIRED"));
        integrity.require(row.value(), row.integrity());
        if (!row.value().equals(new ReleaseSnapshot(releaseId, expectedAuthorityFingerprint, current))) throw conflict("FROZEN_RELEASE_CHANGED");
        Map<String, String> environment = new LinkedHashMap<>(), secrets = new LinkedHashMap<>();
        for (Projection projection : row.value().projections()) {
            projection.configEnvironment().forEach((name, value) -> unique(environment, name, value));
            projection.secretEnvironment().forEach((name, reference) -> unique(secrets, name, reference));
        }
        if (environment.keySet().stream().anyMatch(secrets::containsKey)) throw conflict("ENVIRONMENT_ALIAS_COLLISION");
        return new ReleaseEnvironment(environment, secrets);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireForRecovery(DeploymentEntity deployment, DeploymentReleaseEntity release) {
        if (!deployment.getId().equals(release.getDeploymentId())) throw conflict("RELEASE_SCOPE_MISMATCH");
        var version = versions.findById(release.getDeploymentVersionId())
            .filter(value -> deployment.getId().equals(value.getDeploymentId()))
            .orElseThrow(() -> conflict("VERSION_SCOPE_MISMATCH"));
        boolean required = ExternalIntegrationRequirement.required(codec.tree(version.getCompositionProvenanceJson()));
        boolean frozen = store.snapshot(release.getId()).isPresent();
        if (!required && !frozen) return;
        if (!required) throw conflict("FROZEN_RELEASE_CHANGED");
        releaseEnvironmentForProvisioning(deployment, version, release);
    }

    private Context context(String deploymentId, String versionId, String targetId) {
        var deployment = deployments.findById(deploymentId).filter(value -> value.getArchivedAt() == null
            && !"ARCHIVED".equals(value.getStatus())).orElseThrow(() -> conflict("DEPLOYMENT_REQUIRED"));
        var version = versions.findById(versionId).filter(value -> deploymentId.equals(value.getDeploymentId()) && "PUBLISHED".equals(value.getStatus())
            && value.getConfigHash() != null && !value.getConfigHash().isBlank()).orElseThrow(() -> conflict("VERSION_SCOPE_MISMATCH"));
        var associationRow = associations.findById(deploymentId);
        if (associationRow.isEmpty()) {
            JsonNode compilerEvidence = codec.tree(version.getCompositionProvenanceJson()).path("externalIntegrationProfiles");
            if (!compilerEvidence.isMissingNode() && (!compilerEvidence.isArray() || !compilerEvidence.isEmpty())) throw conflict("OFFER_SCOPE_REQUIRED");
            return new Context(deployment, version, targetId, null, null, null, null);
        }
        var association = associationRow.filter(value -> value.getCustomerId().equals(deployment.getCustomerId())
            && value.getTenantId().equals(deployment.getTenantId())).orElseThrow(() -> conflict("OFFER_SCOPE_REQUIRED"));
        var offer = entitlements.requireOffer(association.getOfferId());
        if (!offer.getFingerprint().equals(association.getOfferFingerprint())) throw conflict("OFFER_CHANGED");
        entitlements.requireEntitlement(deployment.getCustomerId(), offer.getId(), null, false);
        Definition definition = entitlements.definition(offer);
        policy.validateDefinition(definition); composition.requireVersion(definition, version);
        policy.requireCompilerEvidence(definition, codec.tree(version.getCompositionProvenanceJson()));
        var target = targets.findById(targetId).filter(value -> value.isActive()).orElseThrow(() -> conflict("TARGET_REQUIRED"));
        var permission = definition.targets().stream().filter(value -> targetId.equals(value.targetProfileId())).findFirst()
            .orElseThrow(() -> conflict("TARGET_NOT_REVIEWED"));
        composition.requireProfile(target, permission);
        return new Context(deployment, version, targetId, definition, offer.getId(), offer.getFingerprint(),
            DeploymentTargetProfileIdentity.fingerprint(mapper, target));
    }

    private List<Projection> resolve(Context context, boolean forEffects) {
        if (!required(context)) return List.of();
        var profiles = context.definition().externalIntegrationProfiles();
        List<Projection> result = new ArrayList<>();
        for (Profile profile : profiles) {
            if (!profile.targetProfileIds().contains(context.targetId())) throw conflict("COMPILER_PROFILE_MISMATCH");
            var row = store.binding(context.deployment().getId(), context.version().getId(), context.targetId(), profile.bindingSlotId())
                .orElseThrow(() -> conflict("BINDING_REQUIRED"));
            Binding binding = row.value();
            integrity.require(bindingEvidence(binding, row.status(), row.revision()), row.integrity());
            Scope expected = new Scope(context.offerId(), context.deployment().getCustomerId(), context.deployment().getTenantId(),
                context.deployment().getId(), context.version().getId(), context.targetId(), profile.bindingSlotId(), profile.id());
            if (!"CONFIGURED".equals(row.status()) || !expected.equals(binding.scope())) throw conflict("BINDING_STALE_OR_FOREIGN");
            var approvalRow = store.approval(binding.approvalId()).orElseThrow(() -> conflict("APPROVAL_REQUIRED"));
            Approval approval = approvalRow.value(); integrity.require(approval, approvalRow.integrity());
            if (!expected.equals(approval.scope()) || !profile.equals(approval.profile()) || !context.offerFingerprint().equals(approval.offerFingerprint())
                || !context.version().getConfigHash().equals(approval.versionConfigHash()) || !context.targetFingerprint().equals(approval.targetFingerprint())) {
                throw conflict("APPROVAL_STALE_OR_FOREIGN");
            }
            policy.validateConfig(profile, approval.config(), approval.accountScope());
            if (!binding.secretReferences().keySet().equals(profile.secretFields()) || !binding.secretRevisions().keySet().equals(profile.secretFields())) {
                throw conflict("CREDENTIAL_FIELDS_INVALID");
            }
            Map<String, String> config = new LinkedHashMap<>(), secrets = new LinkedHashMap<>();
            approval.config().forEach((field, value) -> config.put(ExternalIntegrationCompilerResolver.alias(profile, field), value));
            for (String field : profile.secretFields()) {
                String expectedName = ExternalIntegrationCompilerResolver.secretName(binding.id(), expected, field);
                if (!expectedName.equals(binding.secretReferences().get(field)) || !requireSecret(store, expected, expectedName).revision()
                    .equals(binding.secretRevisions().get(field))) throw conflict("CREDENTIAL_CHANGED_OR_FOREIGN");
                secrets.put(ExternalIntegrationCompilerResolver.alias(profile, field), expectedName);
            }
            if (forEffects) endpoints.requirePublicDns(profile.allowedHosts());
            result.add(new Projection(binding.id(), approval.id(), expected,
                integrity.seal(Map.of("approval", approval, "binding", bindingEvidence(binding, row.status(), row.revision()))), config, secrets));
        }
        return List.copyOf(result);
    }

    private boolean required(Context context) {
        return context.definition() != null && !context.definition().externalIntegrationProfiles().isEmpty();
    }

    private void fenceEntitlement(Context context) {
        entitlements.requireEntitlement(context.deployment().getCustomerId(), context.offerId(), null, true);
    }

    private void requireRelease(Context context, String releaseId, String expectedFingerprint) {
        requireRelease(context, releaseId, expectedFingerprint,
            () -> authority.requireReleaseUnchanged(context.deployment().getId(), context.targetId(), releaseId, expectedFingerprint));
    }

    private void requireRelease(Context context, String releaseId, String expectedFingerprint, Supplier<Snapshot> checkedAuthority) {
        var release = releases.findById(releaseId).filter(value -> context.deployment().getId().equals(value.getDeploymentId())
            && context.version().getId().equals(value.getDeploymentVersionId()) && context.targetId().equals(value.getTargetProfileId()))
            .orElseThrow(() -> conflict("RELEASE_SCOPE_MISMATCH"));
        var snapshot = checkedAuthority.get();
        var binding = snapshot.binding();
        if (!release.getId().equals(snapshot.releaseId()) || !context.deployment().getId().equals(binding.deploymentId())
            || !context.targetId().equals(binding.targetProfileId())
            || !context.deployment().getCustomerId().equals(binding.customerId()) || !context.deployment().getTenantId().equals(binding.tenantId())
            || !context.version().getId().equals(binding.versionId()) || !context.version().getConfigHash().equals(binding.versionConfigHash())
            || !expectedFingerprint.equals(snapshot.fingerprint())) throw conflict("RUNTIME_AUTHORITY_SCOPE_MISMATCH");
    }
    private void unique(Map<String, String> values, String key, String value) {
        if (values.putIfAbsent(key, value) != null) throw conflict("ENVIRONMENT_ALIAS_COLLISION");
    }
    private record Context(DeploymentEntity deployment, DeploymentVersionEntity version, String targetId,
                           Definition definition, String offerId, String offerFingerprint, String targetFingerprint) { }
}
