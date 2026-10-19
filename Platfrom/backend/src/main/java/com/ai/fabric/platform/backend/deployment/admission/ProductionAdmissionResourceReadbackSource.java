package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementFailure;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementInventory;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementStore;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApiClient;
import com.ai.fabric.platform.backend.deployment.service.CoolifyTargetProfileResolver;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

/** Reads active inventory or a completed, fully evidenced R3 retirement. It performs no provider mutation. */
@Component
final class ProductionAdmissionResourceReadbackSource implements AdmissionResourceReadbackSource {
    @Autowired(required = false)
    private ExternalIndexRetentionService indexRetention;
    private final AdmissionStore admissions;
    private final DeploymentTargetProfileRepository targets;
    private final DeploymentReleaseRepository releases;
    private final DeploymentVersionRepository versions;
    private final DeploymentProviderResourceHandleRepository handles;
    private final PlatformConsumerRepository consumers;
    private final TargetRetirementOperationRepository operations;
    private final TargetRetirementStore retirements;
    private final TargetRetirementInventory retirementInventory;
    private final CoolifyTargetProfileResolver resolver;
    private final CoolifyApiClient client;
    private final CustomerSelfServiceProperties customerSelfServiceProperties;
    private final AdmissionJson json;
    private final Clock clock;

    @Autowired
    ProductionAdmissionResourceReadbackSource(AdmissionStore admissions,
                                              DeploymentTargetProfileRepository targets,
                                              DeploymentReleaseRepository releases,
                                              DeploymentVersionRepository versions,
                                              DeploymentProviderResourceHandleRepository handles,
                                              PlatformConsumerRepository consumers,
                                              TargetRetirementOperationRepository operations,
                                              TargetRetirementStore retirements,
                                              TargetRetirementInventory retirementInventory,
                                              CoolifyTargetProfileResolver resolver,
                                              CoolifyApiClient client,
                                              CustomerSelfServiceProperties customerSelfServiceProperties,
                                              AdmissionJson json) {
        this(admissions, targets, releases, versions, handles, consumers, operations, retirements, retirementInventory,
            resolver, client, customerSelfServiceProperties, json, Clock.systemUTC());
    }

    ProductionAdmissionResourceReadbackSource(AdmissionStore admissions,
                                              DeploymentTargetProfileRepository targets,
                                              DeploymentReleaseRepository releases,
                                              DeploymentVersionRepository versions,
                                              DeploymentProviderResourceHandleRepository handles,
                                              PlatformConsumerRepository consumers,
                                              TargetRetirementOperationRepository operations,
                                              TargetRetirementStore retirements,
                                              TargetRetirementInventory retirementInventory,
                                              CoolifyTargetProfileResolver resolver,
                                              CoolifyApiClient client,
                                              CustomerSelfServiceProperties customerSelfServiceProperties,
                                              AdmissionJson json, Clock clock) {
        this.admissions = admissions;
        this.targets = targets;
        this.releases = releases;
        this.versions = versions;
        this.handles = handles;
        this.consumers = consumers;
        this.operations = operations;
        this.retirements = retirements;
        this.retirementInventory = retirementInventory;
        this.resolver = resolver;
        this.client = client;
        this.customerSelfServiceProperties = customerSelfServiceProperties;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public ResourceReadback read(ReadbackContext context) {
        if (context == null || context.reservation() == null || context.policy() == null) {
            throw failure(PRECONDITION_FAILED, "RESOURCE_READBACK_CONTEXT_REQUIRED");
        }
        Reservation reservation = context.reservation();
        TargetBudget target = context.policy().targets().get(reservation.targetProfileId());
        if (target == null) throw failure(CONFLICT, "STALE_RESOURCE_GENERATION");
        var profile = requirePlacement(reservation, target);
        return reservation.retirementOperationId() == null
            ? active(context, target, profile)
            : retired(context, target);
    }

    private ResourceReadback active(ReadbackContext context, TargetBudget target,
                                    com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity profile) {
        Reservation reservation = context.reservation();
        Manifest manifest = admissions.reviewed("MANIFEST", reservation.manifestId(), Manifest.class);
        if (!reservation.manifestFingerprint().equals(json.fingerprint(manifest))
            || !reservation.bindingFingerprint().equals(manifest.bindingFingerprint())
            || !reservation.deploymentId().equals(manifest.deploymentId())
            || !reservation.targetProfileId().equals(manifest.targetProfileId())) {
            throw failure(CONFLICT, "STALE_RESOURCE_GENERATION");
        }
        var version = versions.findById(manifest.versionId())
            .orElseThrow(() -> failure(PRECONDITION_FAILED, "IMMUTABLE_VERSION_EVIDENCE_REQUIRED"));
        if (!reservation.deploymentId().equals(version.getDeploymentId())
            || !manifest.versionConfigHash().equals(version.getConfigHash())
            || !"PUBLISHED".equalsIgnoreCase(version.getStatus())) {
            throw failure(CONFLICT, "IMMUTABLE_VERSION_EVIDENCE_CHANGED");
        }
        List<DeploymentReleaseEntity> targetReleases = releases
            .findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc(reservation.deploymentId(), reservation.targetProfileId());
        String boundReleaseId = admissions.currentReleaseId(reservation);
        List<DeploymentReleaseEntity> matches = targetReleases.stream().filter(release ->
            manifest.versionId().equals(release.getDeploymentVersionId())
                && (boundReleaseId == null || boundReleaseId.equals(release.getId()))
                && "APPLIED_VERIFIED".equals(release.getStatus())
                && "ACTIVE".equals(release.getProvisioningStatus())
                && release.getProviderType() == DeploymentProviderType.COOLIFY
                && release.getRuntimeAuthorityFingerprint() != null
                && release.getRuntimeAuthorityFingerprint().matches("[a-f0-9]{64}"))
            .toList();
        if (matches.size() != 1) throw failure(PRECONDITION_FAILED, "ACTIVE_RELEASE_UNPROVEN");
        DeploymentReleaseEntity exactRelease = matches.getFirst();

        List<DeploymentProviderResourceHandleEntity> owned = handles
            .findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(reservation.deploymentId(), reservation.targetProfileId())
            .stream().filter(handle -> !PreservedExternalBindingKinds.isPreserved(handle.getResourceKind())).toList();
        if (owned.isEmpty()) throw failure(PRECONDITION_FAILED, "ACTIVE_INVENTORY_UNPROVEN");
        Instant now = clock.instant();
        Set<String> releaseIds = Set.of(exactRelease.getId());
        List<Map<String, Object>> providerEvidence;
        try {
            var connection = resolver.requireConnection(profile);
            if (!target.placement().serverUuid().equals(connection.config().serverUuid())) {
                throw failure(PRECONDITION_FAILED, "TARGET_PLACEMENT_EVIDENCE_CHANGED");
            }
            var server = client.getServer(connection, target.placement().serverUuid());
            var trusted = trusted(target.intent());
            if (server == null || !target.placement().serverUuid().equals(server.path("uuid").asText())
                || !target.placement().serverUuid().equals(trusted.uuid())
                || !target.placement().ip().equals(trusted.address())
                || !trusted.reportedAddress().equals(server.path("ip").asText())) {
                throw failure(PRECONDITION_FAILED, "TARGET_PLACEMENT_EVIDENCE_CHANGED");
            }
            providerEvidence = owned.stream().map(handle -> activeProviderEvidence(
                connection, handle, exactRelease.getId(), target.placement().serverUuid()
            )).toList();
        } catch (AdmissionFailure failure) {
            throw failure;
        } catch (RuntimeException unavailable) {
            throw failure(PRECONDITION_FAILED, "ACTIVE_PROVIDER_READBACK_UNAVAILABLE");
        }
        String reference = "admission-active:" + json.fingerprint(Map.of(
            "releaseId", exactRelease.getId(),
            "versionConfigHash", manifest.versionConfigHash(),
            "bindingFingerprint", manifest.bindingFingerprint(),
            "runtimeAuthorityFingerprint", exactRelease.getRuntimeAuthorityFingerprint(),
            "provider", providerEvidence
        ));
        return readback(context, target, now, reference, true, true, false, false,
            false, false, assignments(reservation, releaseIds), null, null, null, null);
    }

    private CustomerSelfServiceProperties.TrustedServer trusted(Intent intent) {
        TargetIntent targetIntent = intent == Intent.CUSTOMER_STAGING
            ? TargetIntent.CUSTOMER_STAGING
            : TargetIntent.CUSTOMER_PRODUCTION;
        var trusted = customerSelfServiceProperties.trustedServers().get(targetIntent);
        if (trusted == null || trusted.uuid() == null || trusted.uuid().isBlank()
            || trusted.address() == null || trusted.address().isBlank()
            || trusted.reportedAddress() == null || trusted.reportedAddress().isBlank()) {
            throw failure(PRECONDITION_FAILED, "TARGET_PLACEMENT_EVIDENCE_CHANGED");
        }
        return trusted;
    }

    private ResourceReadback retired(ReadbackContext context, TargetBudget target) {
        Reservation reservation = context.reservation();
        var operation = operations.findById(reservation.retirementOperationId())
            .orElseThrow(() -> failure(PRECONDITION_FAILED, "RETIREMENT_READBACK_REQUIRED"));
        var summary = retirements.summary(operation);
        boolean retainedData = summary.state() == com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State.RETIRED_WITH_RETAINED_DATA;
        String expectedKey = "admission-expiry:" + reservation.id() + ":" + reservation.generation();
        if (!expectedKey.equals(summary.operationKey()) || !retainedData && summary.state()
            != com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State.COMPLETE
            || !reservation.deploymentId().equals(summary.deploymentId())
            || !reservation.targetProfileId().equals(summary.targetProfileId())
            || !reservation.customerId().equals(summary.customerId())
            || summary.completedAt() == null || summary.completedAt().isAfter(clock.instant())
            || summary.retention() == null || summary.retention().decision() != RetentionDecision.RECLAIM) {
            throw failure(PRECONDITION_FAILED, "RETIREMENT_OUTCOME_UNSETTLED");
        }

        Snapshot snapshot = summary.snapshot();
        if (!reservation.customerId().equals(snapshot.customerId())
            || !reservation.tenantId().equals(snapshot.tenantId())
            || !reservation.deploymentId().equals(snapshot.deploymentId())
            || !reservation.targetProfileId().equals(snapshot.targetProfileId())) {
            throw failure(CONFLICT, "STALE_RESOURCE_GENERATION");
        }
        Progress progress = summary.result();
        Evidence runtime = completeEvidence(summary, operation.getFenceEpoch(), progress, ParticipantKind.RUNTIME_DRAIN, true);
        for (ParticipantKind kind : ParticipantKind.values()) {
            completeEvidence(summary, operation.getFenceEpoch(), progress, kind, kind == ParticipantKind.RUNTIME_DRAIN);
        }
        completeEvidence(summary, operation.getFenceEpoch(), progress.interlockEvidence(), false);

        try {
            retirementInventory.recheck(snapshot, new PlanRequest(
                summary.operationKey(),
                snapshot.releases().stream().map(ReleaseGeneration::releaseId).toList(),
                snapshot.consumers(),
                summary.drainDeadline(),
                summary.retention(),
                "admission-retirement-readback"
            ));
        } catch (TargetRetirementFailure changed) {
            throw failure(CONFLICT, "STALE_RESOURCE_GENERATION");
        }

        Map<String, DeploymentProviderResourceHandleEntity> current = handles
            .findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(reservation.deploymentId(), reservation.targetProfileId())
            .stream().collect(Collectors.toMap(DeploymentProviderResourceHandleEntity::getId, value -> value));
        Set<String> expectedHandles = snapshot.resources().stream().map(ResourceGeneration::handleId).collect(Collectors.toSet());
        if (!current.keySet().equals(expectedHandles)) throw failure(PRECONDITION_FAILED, "RETIREMENT_INVENTORY_INCOMPLETE");
        if (!retainedData && snapshot.resources().stream().anyMatch(resource -> "EXTERNAL_VECTOR_STORAGE_BINDING".equals(resource.resourceKind())))
            throw failure(PRECONDITION_FAILED, "RETAINED_INDEX_CAPACITY_REQUIRED");
        for (ResourceGeneration resource : snapshot.resources()) {
            var handle = current.get(resource.handleId());
            if (handle == null || handle.getProviderType() != resource.providerType()
                || !resource.providerResourceUuid().equals(handle.getProviderResourceUuid())) {
                throw failure(CONFLICT, "STALE_RESOURCE_GENERATION");
            }
            if (!resource.preserved()) {
                var effect = progress.effects().get(summary.id() + ":" + operation.getFenceEpoch()
                    + ":RESOURCES:" + resource.handleId());
                String expectedObservation = resource.volumes().isEmpty()
                    ? "ABSENT"
                    : retirementInventory.volumeDispositionReceipt(resource);
                if (!"DELETED".equals(handle.getStatus()) || !expectedObservation.equals(handle.getLastObservedStatus())
                    || handle.getLastObservedAt() == null || effect == null || !effect.complete()) {
                    throw failure(PRECONDITION_FAILED, "RETIREMENT_INVENTORY_INCOMPLETE");
                }
            }
        }
        Set<String> releaseIds = snapshot.releases().stream().map(ReleaseGeneration::releaseId).collect(Collectors.toSet());
        long assignmentCount = assignments(reservation, releaseIds);
        String reference = "admission-retirement:" + json.fingerprint(Map.of(
            "operationId", summary.id(), "requestFingerprint", summary.requestFingerprint(),
            "snapshot", snapshot, "progress", progress, "assignmentCount", assignmentCount));
        if (retainedData) {
            if (indexRetention == null) throw failure(PRECONDITION_FAILED, "INDEX_RETENTION_ADAPTER_REQUIRED");
            var receipt = indexRetention.requireReceipt(retirements.context(operation));
            for (Step step : List.of(Step.DRAIN, Step.AUTHORITY, Step.INDEX, Step.DEPENDENCIES)) {
                var effect = progress.effects().get(summary.id() + ":" + operation.getFenceEpoch() + ":" + step + ":target");
                if (effect == null || !effect.complete() || effect.observedAt() == null
                    || effect.observedAt().isAfter(summary.completedAt())
                    || step == Step.INDEX && !("external-index-retention:" + receipt.id()).equals(effect.evidenceReference()))
                    throw failure(PRECONDITION_FAILED, "RETENTION_EFFECT_RECEIPT_REQUIRED");
            }
            return new ResourceReadback(reservation.id(), reservation.generation(), reservation.revision(), reservation.workerToken(),
                reservation.manifestFingerprint(), reservation.bindingFingerprint(), target.placement(), clock.instant(), reference,
                true, true, false, runtime.ingressBlocked(), true, true, assignmentCount, runtime.conversations(),
                runtime.confirmations(), runtime.humanWork(), runtime.backgroundWork(), receipt);
        }
        return readback(context, target, summary.completedAt(), reference, true, true, true,
            runtime.ingressBlocked(), true, true, assignmentCount, runtime.conversations(),
            runtime.confirmations(), runtime.humanWork(), runtime.backgroundWork());
    }

    private Evidence completeEvidence(Summary summary, long fenceEpoch, Progress progress,
                                      ParticipantKind kind, boolean requireWork) {
        if (progress == null || progress.evidence() == null) {
            throw failure(PRECONDITION_FAILED, "RETIREMENT_EVIDENCE_INCOMPLETE");
        }
        Evidence evidence = completeEvidence(summary, fenceEpoch, progress.evidence().get(kind), requireWork);
        if (summary.state() == com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State.RETIRED_WITH_RETAINED_DATA
            && !evidence.durable()) throw failure(PRECONDITION_FAILED, "RETIREMENT_EVIDENCE_INCOMPLETE");
        return evidence;
    }

    private Evidence completeEvidence(Summary summary, long fenceEpoch, Evidence evidence, boolean requireWork) {
        if (evidence == null || !summary.requestFingerprint().equals(evidence.requestFingerprint())
            || evidence.fenceEpoch() != fenceEpoch
            || evidence.reference() == null || evidence.reference().isBlank()
            || !evidence.complete() || !evidence.inventoryComplete() || !evidence.retentionApproved()
            || evidence.blockers() == null || !evidence.blockers().isEmpty()
            || (requireWork && (!evidence.ingressBlocked() || !zero(evidence.conversations())
                || !zero(evidence.confirmations()) || !zero(evidence.humanWork()) || !zero(evidence.backgroundWork())))) {
            throw failure(PRECONDITION_FAILED, "RETIREMENT_EVIDENCE_INCOMPLETE");
        }
        return evidence;
    }

    private long assignments(Reservation reservation, Set<String> releaseIds) {
        return consumers.findAll().stream().filter(consumer -> references(consumer, reservation, releaseIds)).count();
    }

    private boolean references(PlatformConsumerEntity consumer, Reservation reservation, Set<String> releaseIds) {
        boolean target = reservation.deploymentId().equals(consumer.getBoundDeploymentId())
            && (consumer.getBoundTargetProfileId() == null
                || reservation.targetProfileId().equals(consumer.getBoundTargetProfileId()));
        return target || releaseIds.contains(consumer.getBoundReleaseId());
    }

    private com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity requirePlacement(
        Reservation reservation, TargetBudget target) {
        var profile = targets.findById(reservation.targetProfileId())
            .orElseThrow(() -> failure(PRECONDITION_FAILED, "TARGET_PLACEMENT_EVIDENCE_REQUIRED"));
        Map<?, ?> config = json.read(profile.getProviderConfigJson(), Map.class);
        if (!profile.isActive() || profile.getProviderType() != DeploymentProviderType.COOLIFY
            || !target.placement().serverUuid().equals(String.valueOf(config.get("serverUuid")))
            || !target.placement().destinationUuid().equals(String.valueOf(config.get("destinationUuid")))) {
            throw failure(PRECONDITION_FAILED, "TARGET_PLACEMENT_EVIDENCE_CHANGED");
        }
        return profile;
    }

    private Map<String, Object> activeProviderEvidence(
        com.ai.fabric.platform.backend.deployment.service.CoolifyConnection connection,
        DeploymentProviderResourceHandleEntity handle, String releaseId, String serverUuid) {
        if (!releaseId.equals(handle.getReleaseId()) || handle.getProviderType() != DeploymentProviderType.COOLIFY
            || handle.getProviderResourceUuid() == null || handle.getProviderResourceUuid().isBlank()
            || !serverUuid.equals(handle.getProviderServerUuid()) || "DELETED".equals(handle.getStatus())
            || "ABSENT".equals(handle.getLastObservedStatus())) {
            throw failure(PRECONDITION_FAILED, "ACTIVE_INVENTORY_UNPROVEN");
        }
        String status;
        com.fasterxml.jackson.databind.JsonNode raw;
        if (Set.of("APPLICATION", "CONNECTOR_APPLICATION", "VECTORIZATION_RUNNER_APPLICATION")
            .contains(handle.getResourceKind())) {
            var application = client.getApplication(connection, handle.getProviderResourceUuid())
                .orElseThrow(() -> failure(PRECONDITION_FAILED, "ACTIVE_PROVIDER_RESOURCE_ABSENT"));
            status = application.status();
            raw = application.raw();
            if (!applicationReady(status)) throw failure(PRECONDITION_FAILED, "ACTIVE_PROVIDER_RESOURCE_NOT_READY");
        } else if ("RUNTIME_POSTGRES_DATABASE".equals(handle.getResourceKind())) {
            var database = client.getDatabase(connection, handle.getProviderResourceUuid())
                .orElseThrow(() -> failure(PRECONDITION_FAILED, "ACTIVE_PROVIDER_RESOURCE_ABSENT"));
            status = database.status();
            raw = database.raw();
            if (!databaseReady(status)) throw failure(PRECONDITION_FAILED, "ACTIVE_PROVIDER_RESOURCE_NOT_READY");
        } else {
            throw failure(PRECONDITION_FAILED, "ACTIVE_PROVIDER_RESOURCE_UNSUPPORTED");
        }
        if (raw == null || !handle.getProviderResourceUuid().equals(raw.path("uuid").asText())
            || !serverUuid.equals(raw.path("server_uuid").asText())) {
            throw failure(CONFLICT, "ACTIVE_PROVIDER_RESOURCE_CHANGED");
        }
        Map<String, Object> value = new TreeMap<>();
        value.put("id", handle.getId());
        value.put("releaseId", handle.getReleaseId());
        value.put("resourceKind", handle.getResourceKind());
        value.put("providerResourceUuid", handle.getProviderResourceUuid());
        value.put("serverUuid", serverUuid);
        value.put("providerStatus", status);
        return value;
    }

    private boolean applicationReady(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
        return (normalized.equals("RUNNING") || normalized.equals("RUNNING:HEALTHY"))
            && !normalized.contains("UNHEALTHY");
    }

    private boolean databaseReady(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
        return Set.of("RUNNING", "RUNNING:HEALTHY", "HEALTHY", "STARTED", "ACTIVE").contains(normalized);
    }

    private ResourceReadback readback(ReadbackContext context, TargetBudget target, Instant observedAt,
                                      String evidenceReference, boolean inventoryComplete,
                                      boolean outcomeSettled, boolean resourcesAbsent,
                                      boolean ingressBlocked, boolean retentionVerified,
                                      boolean assignmentsVerified, long assignmentCount,
                                      Long sessions, Long confirmations, Long humanWork, Long backgroundWork) {
        Reservation reservation = context.reservation();
        return new ResourceReadback(reservation.id(), reservation.generation(), reservation.revision(),
            context.workerToken(), reservation.manifestFingerprint(), reservation.bindingFingerprint(),
            target.placement(), observedAt, evidenceReference, inventoryComplete, outcomeSettled,
            resourcesAbsent, ingressBlocked, retentionVerified, assignmentsVerified, assignmentCount,
            sessions, confirmations, humanWork, backgroundWork);
    }

    private boolean zero(Long value) { return value != null && value == 0; }

    private AdmissionFailure failure(org.springframework.http.HttpStatus status, String code) {
        return new AdmissionFailure(status, code);
    }
}
