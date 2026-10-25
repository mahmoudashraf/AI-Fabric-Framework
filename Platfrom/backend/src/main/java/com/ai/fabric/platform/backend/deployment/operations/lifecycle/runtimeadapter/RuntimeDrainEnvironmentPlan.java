package com.ai.fabric.platform.backend.deployment.operations.lifecycle.runtimeadapter;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Optional immutable-version opt-in. It never changes ordinary cold deployment prerequisites. */
public final class RuntimeDrainEnvironmentPlan {
    // Defaults match the pinned Runtime application.yml with AI Fabric 0.8.41.
    // RuntimeRetirementTopology independently verifies every ABSENT claim against its actual environment.
    private static final Map<String, FeatureFlag> FEATURES = Map.ofEntries(
        Map.entry("CHAT_SESSIONS", new FeatureFlag("AI_CHAT_ENABLED", true)),
        Map.entry("CONVERSATION_HANDOVER", new FeatureFlag("LOOMAI_CONVERSATION_HANDOVER_ENABLED", false)),
        Map.entry("HUMAN_REVIEW", new FeatureFlag("AI_EXECUTION_REVIEWS_ENABLED", false)),
        Map.entry("SPECIALIST_EXECUTION", new FeatureFlag("AI_EXECUTION_ENABLED", true)),
        Map.entry("SPECIALIST_CHAINS", new FeatureFlag("AI_EXECUTION_SPECIALIST_CHAINS_ENABLED", false)),
        Map.entry("SMART_BRAIN", new FeatureFlag("LOOMAI_SMART_BRAIN_ENABLED", false)),
        Map.entry("INDEXING", new FeatureFlag("AI_INDEXING_ENABLED", true)),
        Map.entry("DOCUMENTS", new FeatureFlag("LOOMAI_DOCUMENTS_ENABLED", false)),
        Map.entry("SCHEDULER", new FeatureFlag("LOOMAI_SMART_BRAIN_ENABLED", false)));
    private final ObjectMapper mapper;

    public RuntimeDrainEnvironmentPlan(ObjectMapper mapper) {
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public Map<String, String> plan(DeploymentEntity deployment, DeploymentVersionEntity version,
        DeploymentReleaseEntity release, Map<String, String> environment, boolean dedicatedStore,
        boolean connector, boolean vectorizationRunner, String artifactManifestJson) {
        JsonNode configured = json(version.getProviderConfigJson()).path("operationalDrainEnabled");
        if (!configured.isBoolean() || !configured.booleanValue()) return Map.of(
            "LOOMAI_RUNTIME_TARGET_RETIREMENT_ENABLED", "false",
            "LOOMAI_RUNTIME_TARGET_RETIREMENT_DEDICATED_STORE", "false");
        JsonNode manifest = json(artifactManifestJson);
        if (!contains(manifest.path("capabilities"), "runtime-release-scoped-drain-v1")
            || !contains(manifest.path("migrationIds"), "runtime-release-scoped-retirement-v24")) {
            throw new IllegalStateException("Operational drain requires a pinned release-scoped Runtime artifact with V24.");
        }
        if (!dedicatedStore || release.getRuntimeAuthoritySnapshotId() == null
            || !sha(release.getRuntimeAuthorityFingerprint())
            || !deployment.getId().equals(release.getDeploymentId())
            || !version.getId().equals(release.getDeploymentVersionId())) {
            throw new IllegalStateException("Operational drain requires exact frozen authority and dedicated target storage.");
        }
        var features = new java.util.TreeMap<String, String>();
        FEATURES.forEach((feature, flag) -> features.put(feature, flag.enabled(environment) ? "PRESENT" : "ABSENT"));
        features.put("ACTION_WEBHOOK", "PRESENT");
        var output = new LinkedHashMap<String, String>();
        if ("PRESENT".equals(features.get("SPECIALIST_EXECUTION"))) {
            for (String key : List.of("AI_EXECUTION_ASYNC_ENCRYPTION_SECRET", "AI_EXECUTION_ASYNC_FINGERPRINT_SECRET")) {
                if (environment.get(key) == null || environment.get(key).isBlank()) {
                    throw new IllegalStateException("Operational drain requires frozen JDBC specialist secrets.");
                }
            }
            output.put("AI_EXECUTION_ASYNC_REPOSITORY", "JDBC");
            output.put("AI_EXECUTION_ASYNC_INITIALIZE_SCHEMA", "false");
        }
        var dependencies = new ArrayList<String>();
        if (connector) dependencies.add("CONNECTOR");
        if (vectorizationRunner) dependencies.add("VECTORIZATION_RUNNER");
        if ("PRESENT".equals(features.get("CONVERSATION_HANDOVER"))) dependencies.add("HUMAN_INBOX");
        var topology = new LinkedHashMap<String, Object>();
        topology.put("contractVersion", "LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_V2");
        topology.put("target", Map.of("deploymentId", deployment.getId(), "targetProfileId", release.getTargetProfileId(), "releaseId", release.getId()));
        topology.put("tenantId", deployment.getTenantId());
        topology.put("features", features);
        topology.put("remoteDependencies", List.copyOf(dependencies));
        topology.put("vectorScope", "ABSENT".equals(features.get("INDEXING")) ? null : vectorScope(version, environment));
        try {
            String raw = mapper.writeValueAsString(topology);
            output.put("LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_JSON", raw);
            output.put("LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_SHA256", HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception failure) {
            throw new IllegalStateException("Operational drain topology could not be encoded.", failure);
        }
        output.put("LOOMAI_RUNTIME_TARGET_RETIREMENT_ENABLED", "true");
        output.put("LOOMAI_RUNTIME_TARGET_RETIREMENT_DEDICATED_STORE", "true");
        return Map.copyOf(output);
    }

    private record FeatureFlag(String environmentKey, boolean defaultEnabled) {
        boolean enabled(Map<String, String> environment) {
            String configured = environment.get(environmentKey);
            // Unknown configured values must not become proof of absence.
            return configured == null ? defaultEnabled : !"false".equalsIgnoreCase(configured);
        }
    }

    private Map<String, Object> vectorScope(DeploymentVersionEntity version, Map<String, String> environment) {
        String provider = environment.getOrDefault("AI_VECTOR_DB_TYPE", "lucene").toLowerCase(java.util.Locale.ROOT);
        String key = switch (provider) {
            case "qdrant" -> "AI_PROVIDERS_QDRANT_COLLECTION_PREFIX";
            case "pinecone" -> "AI_PROVIDERS_PINECONE_NAMESPACE_PREFIX";
            case "milvus" -> "AI_PROVIDERS_MILVUS_COLLECTION_PREFIX";
            case "lucene", "memory" -> null;
            default -> throw new IllegalStateException("Operational drain vector provider is unsupported.");
        };
        String scope = key == null ? "deployment-version:" + version.getId() : environment.get(key);
        if (scope == null || scope.isBlank() || scope.length() > 160) {
            throw new IllegalStateException("Operational drain requires an exact target vector scope.");
        }
        var entities = new TreeSet<String>();
        JsonNode configured = json(version.getEntityConfigJson()).path("ai-entities");
        if (!configured.isObject()) throw new IllegalStateException("Operational drain requires frozen indexed entities.");
        configured.fields().forEachRemaining(entry -> {
            if (entry.getValue().path("indexing").path("enabled").asBoolean(true)) entities.add(entry.getKey());
        });
        if (entities.isEmpty() || entities.size() > 500 || entities.stream().anyMatch(value -> value.isBlank()
            || !value.equals(value.trim()) || value.length() > 128)) {
            throw new IllegalStateException("Operational drain indexed entity inventory is invalid.");
        }
        return Map.of("provider", provider, "scopeType", key == null ? "DEDICATED_RUNTIME_STORAGE"
            : provider.equals("pinecone") ? "NAMESPACE_PREFIX" : "COLLECTION_PREFIX", "scopeValue", scope, "entityTypes", List.copyOf(entities));
    }
    private JsonNode json(String raw) {
        try { return raw == null || raw.isBlank() ? mapper.createObjectNode() : mapper.readTree(raw); }
        catch (Exception failure) { throw new IllegalStateException("Operational drain configuration is invalid.", failure); }
    }
    private boolean sha(String value) { return value != null && value.matches("[a-f0-9]{64}"); }
    private boolean contains(JsonNode values, String expected) {
        return values.isArray() && java.util.stream.StreamSupport.stream(values.spliterator(), false)
            .anyMatch(value -> value.isTextual() && expected.equals(value.textValue()));
    }
}
