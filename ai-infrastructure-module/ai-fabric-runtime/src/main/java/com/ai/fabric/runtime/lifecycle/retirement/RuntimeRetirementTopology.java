package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Provider-owned, release-bound enabled topology, not the packaged supported-capabilities catalog. */
@Component
public class RuntimeRetirementTopology {
    public enum Feature {
        CHAT_SESSIONS, CONVERSATION_HANDOVER, HUMAN_REVIEW, SPECIALIST_EXECUTION,
        SPECIALIST_CHAINS, SMART_BRAIN, ACTION_WEBHOOK, INDEXING, DOCUMENTS, SCHEDULER
    }
    public enum Presence { PRESENT, ABSENT }
    public enum Dependency { CONNECTOR, HUMAN_INBOX, VECTORIZATION_RUNNER }
    public record VectorScope(String provider, String scopeType, String scopeValue,
                              List<String> entityTypes) { }
    public record Contract(String contractVersion, Target target, String tenantId,
                           Map<Feature, Presence> features, List<Dependency> remoteDependencies,
                           VectorScope vectorScope) { }
    public static final String VERSION = "LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_V2";
    private final Contract contract;
    private final String fingerprint;
    private final Map<Feature, Boolean> verifiedAbsent = new EnumMap<>(Feature.class);
    private final List<String> blockers;

    public RuntimeRetirementTopology(Environment environment, ObjectMapper mapper, RuntimeTargetRetirementIdentity identity) {
        String raw = environment.getProperty("loomai.runtime.target-retirement.topology-json",
            environment.getProperty("LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_JSON", ""));
        String expected = environment.getProperty("loomai.runtime.target-retirement.topology-sha256",
            environment.getProperty("LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_SHA256", ""));
        if (raw.isBlank()) {
            contract = null;
            fingerprint = "UNATTESTED";
            blockers = List.of("IMMUTABLE_ENABLED_TOPOLOGY_UNATTESTED");
            return;
        }
        try {
            fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
            if (!fingerprint.equals(expected)) throw new IllegalStateException("Retirement topology digest mismatch.");
            contract = mapper.readValue(raw, Contract.class);
            if (!VERSION.equals(contract.contractVersion()) || !identity.target().equals(contract.target())
                || !identity.tenantId().equals(contract.tenantId()) || contract.features() == null
                || contract.features().size() != Feature.values().length || contract.remoteDependencies() == null
                || contract.features().containsValue(null)) throw new IllegalStateException("Retirement topology identity or feature contract is invalid.");
            validateVectorScope(contract.vectorScope(), contract.features().get(Feature.INDEXING), identity);
            List<String> issues = new ArrayList<>();
            for (Feature feature : Feature.values()) {
                boolean absent = contract.features().get(feature) == Presence.ABSENT;
                String enabledProperty = property(feature);
                if (absent && enabledProperty != null && !Boolean.FALSE.equals(environment.getProperty(enabledProperty, Boolean.class))) {
                    issues.add("TOPOLOGY_ABSENCE_NOT_VERIFIED:" + feature);
                    absent = false;
                }
                verifiedAbsent.put(feature, absent);
            }
            blockers = List.copyOf(issues);
        } catch (IllegalStateException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("Retirement topology verification failed.", exception); }
    }

    public String fingerprint() { return fingerprint; }
    public VectorScope vectorScope() { return contract == null ? null : contract.vectorScope(); }
    public boolean absent(Feature feature) { return Boolean.TRUE.equals(verifiedAbsent.get(feature)); }
    public List<String> blockers() { return blockers; }
    public List<RemoteObligation> remoteObligations(Target target) {
        List<Dependency> dependencies = contract == null ? List.of(Dependency.CONNECTOR, Dependency.HUMAN_INBOX) : contract.remoteDependencies();
        return dependencies.stream().distinct().map(dependency -> new RemoteObligation(
            dependency.name(), target, fingerprint, "PLATFORM_AUTHORITATIVE_R5_READBACK_REQUIRED")).toList();
    }
    public List<String> fencingBlockers(boolean sessionsFenced, RuntimeRetirementFencingCoverage coverage) {
        List<String> result = new ArrayList<>(blockers);
        if (!sessionsFenced && !absent(Feature.CHAT_SESSIONS)) result.add("CHAT_SESSION_STORE_FENCE_UNVERIFIED");
        if (contract == null) return List.copyOf(result);
        for (Feature feature : Feature.values()) {
            if (feature == Feature.CHAT_SESSIONS || absent(feature)) continue;
            if (feature == Feature.CONVERSATION_HANDOVER && coverage.covers("conversation-control", "conversation-orchestration-state",
                "RuntimeConversationHandoverService.scheduledMaintenance")) continue;
            if (feature == Feature.DOCUMENTS && coverage.covers("DocumentKnowledgeService.cleanupRetentionScheduled", "DocumentKnowledgeService.reconcilePending")) continue;
            if (feature == Feature.ACTION_WEBHOOK && coverage.covers("RuntimeActionWebhookDeliveryWorker.dispatchReadyDeliveries")) continue;
            if (feature == Feature.SMART_BRAIN && coverage.covers("SmartBrainOperationWorker.process", "SmartBrainDeliveryWorker.deliver")) continue;
            if ((feature == Feature.SPECIALIST_EXECUTION || feature == Feature.SPECIALIST_CHAINS)
                && coverage.covers("runtime-ingress", "aiFabricExecutionTaskExecutor.attempts")) continue;
            if (feature == Feature.INDEXING && coverage.covers("runtime-ingress", "IndexingQueueService.enqueue",
                "IndexingQueueService.enqueueForSynchronousDispatch", "IndexingQueueService.lease", "IndexingQueueService.claimSynchronous",
                "IndexingQueueService.markCompleted", "IndexingQueueService.markFailure", "IndexingQueueService.markSuperseded",
                "IndexingWorkProcessor.process", "AsyncIndexingWorker.run", "BatchIndexingWorker.run", "SyncIndexingRetryWorker.run",
                "IndexingCleanupScheduler.reclaimStuckEntries", "IndexingCleanupScheduler.purgeOldEntries")) continue;
            result.add(switch (feature) {
                case CONVERSATION_HANDOVER -> "HANDOVER_LOCAL_EFFECT_FENCE_UNVERIFIED";
                case HUMAN_REVIEW -> "FRAMEWORK_REVIEW_RECOVERY_TARGET_EPOCH_FENCE_UNSUPPORTED";
                case SPECIALIST_EXECUTION, SPECIALIST_CHAINS -> "FRAMEWORK_EXECUTION_RECOVERY_TARGET_EPOCH_FENCE_UNSUPPORTED:" + feature;
                case INDEXING -> "FRAMEWORK_INDEXING_PROCESSOR_TARGET_EPOCH_FENCE_UNSUPPORTED";
                case DOCUMENTS -> "DOCUMENT_RECONCILER_EFFECT_FENCE_UNVERIFIED";
                case SMART_BRAIN -> "SMART_BRAIN_LOCAL_WORKER_FENCE_UNVERIFIED";
                case SCHEDULER -> "QUARTZ_TRIGGER_CREATION_TARGET_EPOCH_FENCE_UNSUPPORTED";
                case ACTION_WEBHOOK -> "ACTION_WEBHOOK_LOCAL_WORKER_FENCE_UNVERIFIED";
                default -> throw new IllegalStateException("Unexpected fencing feature.");
            });
        }
        return result.stream().distinct().toList();
    }
    private String property(Feature feature) {
        return switch (feature) {
            case CHAT_SESSIONS -> "ai.chat.enabled";
            case CONVERSATION_HANDOVER -> "loomai.conversation-handover.enabled";
            case HUMAN_REVIEW -> "ai.execution.reviews.enabled";
            case SPECIALIST_EXECUTION -> "ai.execution.enabled";
            case SPECIALIST_CHAINS -> "ai.execution.specialist-chains.enabled";
            case SMART_BRAIN, SCHEDULER -> "loomai.smart-brain.enabled";
            case INDEXING -> "ai.indexing.enabled";
            case DOCUMENTS -> "loomai.documents.enabled";
            case ACTION_WEBHOOK -> null;
        };
    }

    private void validateVectorScope(VectorScope scope, Presence indexing, RuntimeTargetRetirementIdentity identity) {
        if (indexing == Presence.ABSENT) return;
        if (scope == null || scope.provider() == null || scope.provider().isBlank()
            || scope.scopeType() == null || scope.scopeType().isBlank()
            || scope.scopeValue() == null || scope.scopeValue().isBlank()
            || scope.entityTypes() == null || scope.entityTypes().isEmpty()
            || scope.entityTypes().size() > 500
            || scope.entityTypes().stream().anyMatch(value -> value == null || value.isBlank()
                || value.length() > 128 || !value.equals(value.trim()))
            || scope.entityTypes().stream().distinct().count() != scope.entityTypes().size()) {
            throw new IllegalStateException("Retirement topology vector scope is invalid.");
        }
        if ("DEDICATED_RUNTIME_STORAGE".equals(scope.scopeType()) && !identity.dedicatedStore()) {
            throw new IllegalStateException("Retirement topology local vector scope requires dedicated runtime storage.");
        }
        if (!"DEDICATED_RUNTIME_STORAGE".equals(scope.scopeType())
            && !java.util.Set.of("COLLECTION_PREFIX", "NAMESPACE_PREFIX").contains(scope.scopeType())) {
            throw new IllegalStateException("Retirement topology vector scope type is unsupported.");
        }
    }
}
