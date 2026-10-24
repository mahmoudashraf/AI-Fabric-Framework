package com.ai.fabric.platform.backend.deployment.operations;

import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.deployment.budget.ProviderUsageModeResolver;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.AiStateStorageMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsContracts.*;
import static com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsProperties.Mode;

@Service
public class DeploymentOperationsService {
    private static final Logger log = LoggerFactory.getLogger(DeploymentOperationsService.class);
    private final DeploymentOperationsProperties properties;
    private final DeploymentOperationsClient client;
    private final CoreReleaseAuthorizationStore authorizations;
    private final ProviderUsageModeResolver providerModes;
    private final CustomerOfferDeploymentRepository offers;
    private final CustomerOfferEntitlementService entitlements;
    private final ObjectMapper mapper;
    private final CustomerOfferCodec codec;

    public DeploymentOperationsService(DeploymentOperationsProperties properties, DeploymentOperationsClient client,
        CoreReleaseAuthorizationStore authorizations, ProviderUsageModeResolver providerModes,
        CustomerOfferDeploymentRepository offers, CustomerOfferEntitlementService entitlements, ObjectMapper mapper,
        CustomerOfferCodec codec) {
        this.properties = properties; this.client = client; this.authorizations = authorizations;
        this.providerModes = providerModes; this.offers = offers; this.entitlements = entitlements; this.mapper = mapper;
        this.codec = codec;
    }

    public Status status() {
        return new Status(mode().name(), true, client.configured(), properties.getProviderUsageMode(), "EXTERNAL_SERVICE");
    }

    public EffectiveDecision authorize(DeploymentEntity deployment, DeploymentVersionEntity version,
                                       DeploymentReleaseEntity release, String operationKey) {
        String providerMode = providerModes.resolve(deployment, release.getTargetProfileId());
        Instant now = Instant.now();
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("commandId", release.getId()); identity.put("customerId", deployment.getCustomerId());
        identity.put("tenantId", deployment.getTenantId()); identity.put("deploymentId", deployment.getId());
        identity.put("targetProfileId", release.getTargetProfileId()); identity.put("versionId", version.getId());
        identity.put("versionConfigHash", version.getConfigHash()); identity.put("sourceArtifactId", release.getSourceArtifactId());
        identity.put("operationKey", operationKey == null ? release.getId() : operationKey);
        identity.put("providerUsageMode", providerMode); identity.put("mode", mode().name());
        ResourceDemand demand = mode() == Mode.DISABLED ? null : demand(deployment, version);
        identity.put("resourceDemand", demand);
        String fingerprint = codec.hash(identity);
        EffectiveDecision effective;
        if (mode() == Mode.DISABLED) {
            effective = decision("BYPASSED", "DISABLED_MANUAL_TEAM", fingerprint, null, now);
        } else if (demand == null) {
            effective = decision("BYPASSED", "OPERATIONS_DEMAND_UNAVAILABLE", fingerprint, null, now);
        } else {
            var request = new AssessmentRequest("1", release.getId(), deployment.getCustomerId(), deployment.getTenantId(),
                deployment.getId(), release.getTargetProfileId(), release.getId(), version.getId(), version.getConfigHash(),
                "APPLY", mode().name(), fingerprint, demand, now);
            AssessmentResponse assessment = null;
            try {
                var response = client.assess(request);
                validate(request, response);
                assessment = response;
            } catch (RuntimeException exception) {
                log.warn("Optional deployment operations unavailable for release {}; core authorization remains required", release.getId());
            }
            if (assessment == null || assessment.decision() == Decision.UNAVAILABLE) {
                effective = decision("BYPASSED", "OPERATIONS_UNAVAILABLE", fingerprint, null, now);
            } else if (mode() == Mode.ENFORCE && assessment.decision() == Decision.DENY) {
                throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                    "DEPLOYMENT_OPERATIONS_DENIED: " + assessment.reason());
            } else {
                effective = decision(mode() == Mode.OBSERVE ? "OBSERVED" : "ALLOWED", assessment.reason(), fingerprint, assessment, now);
            }
        }
        authorizations.save(deployment, version, release, operationKey, providerMode, effective);
        return effective;
    }

    public CoreReleaseAuthorizationStore.Authorization require(DeploymentEntity deployment, DeploymentVersionEntity version,
                                                                DeploymentReleaseEntity release) {
        return authorizations.require(deployment, version, release);
    }

    public boolean hasCoreAuthorization(String releaseId) { return authorizations.find(releaseId).isPresent(); }

    public void report(String releaseId, String status) {
        var authorization = authorizations.find(releaseId);
        if (authorization.isEmpty() || "DISABLED".equals(authorization.get().operations().mode())) return;
        try { client.report(new Outcome("1", releaseId, authorization.get().operations().requestFingerprint(), status, Instant.now())); }
        catch (RuntimeException exception) { log.warn("Optional operations outcome unavailable for release {}", releaseId); }
    }

    private Mode mode() { return properties.getMode() == null ? Mode.DISABLED : properties.getMode(); }

    private ResourceDemand demand(DeploymentEntity deployment, DeploymentVersionEntity version) {
        var association = offers.findById(deployment.getId());
        if (association.isEmpty()) return null;
        if (!Objects.equals(association.get().getCustomerId(), deployment.getCustomerId())
            || !Objects.equals(association.get().getTenantId(), deployment.getTenantId()))
            throw new IllegalStateException("CORE_RELEASE_AUTHORIZATION_SCOPE_CHANGED");
        var definition = entitlements.definition(entitlements.requireOffer(association.get().getOfferId()));
        var topology = definition.topology();
        if (!definition.aiStateStorageTopologies().isEmpty()) {
            try {
                var selected = AiStateStorageMode.valueOf(mapper.readTree(version.getProviderConfigJson()).path("aiStateStorageMode").asText());
                topology = definition.aiStateStorageTopologies().get(selected);
            } catch (Exception exception) { return null; }
        }
        if (topology == null) return null;
        return new ResourceDemand(topology.cpuMillis(), topology.memoryMiB(), topology.diskMiB(), topology.databases(),
            topology.indexes(), topology.dependencies(), topology.replicas(), topology.concurrentApplies());
    }

    private EffectiveDecision decision(String status, String reason, String fingerprint, AssessmentResponse response, Instant now) {
        return new EffectiveDecision(mode().name(), status, reason, fingerprint,
            response == null ? null : response.assessmentId(), response == null ? null : response.policyRevision(), now);
    }

    private void validate(AssessmentRequest request, AssessmentResponse response) {
        if (!valid(request, response)) throw new DeploymentOperationsClient.OperationsUnavailableException();
    }

    private boolean valid(AssessmentRequest request, AssessmentResponse response) {
        Instant now = Instant.now();
        return response != null && "1".equals(response.schemaVersion()) && request.commandId().equals(response.commandId())
            && request.requestFingerprint().equals(response.requestFingerprint()) && response.decision() != null
            && response.assessmentId() != null && !response.assessmentId().isBlank() && response.assessmentId().length() <= 128
            && response.reason() != null && response.reason().matches("[A-Z0-9_]{1,128}")
            && response.observedAt() != null && !response.observedAt().isAfter(now.plusSeconds(2))
            && !response.observedAt().isBefore(request.requestedAt().minusSeconds(2))
            && response.expiresAt() != null && response.expiresAt().isAfter(now);
    }
}
