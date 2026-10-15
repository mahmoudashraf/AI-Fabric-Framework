package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the exact release-bound contract consumed by the runtime retirement boundary. */
@Service
public class RuntimeRetirementTopologyPlanService {
    static final String CONTRACT_VERSION = "LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_V2";

    enum Feature {
        CHAT_SESSIONS("AI_CHAT_ENABLED"),
        CONVERSATION_HANDOVER("LOOMAI_CONVERSATION_HANDOVER_ENABLED"),
        HUMAN_REVIEW("AI_EXECUTION_REVIEWS_ENABLED"),
        SPECIALIST_EXECUTION("AI_EXECUTION_ENABLED"),
        SPECIALIST_CHAINS("AI_EXECUTION_SPECIALIST_CHAINS_ENABLED"),
        SMART_BRAIN("LOOMAI_SMART_BRAIN_ENABLED"),
        ACTION_WEBHOOK(null),
        INDEXING("AI_INDEXING_ENABLED"),
        DOCUMENTS("LOOMAI_DOCUMENTS_ENABLED"),
        SCHEDULER("LOOMAI_SMART_BRAIN_ENABLED");

        private final String environmentKey;

        Feature(String environmentKey) {
            this.environmentKey = environmentKey;
        }
    }

    enum Presence { PRESENT, ABSENT }
    enum Dependency { CONNECTOR, HUMAN_INBOX, VECTORIZATION_RUNNER }
    record VectorScope(String provider, String scopeType, String scopeValue, List<String> entityTypes) { }

    public record Plan(String json, String sha256) { }

    private final ObjectMapper mapper;

    public RuntimeRetirementTopologyPlanService(ObjectMapper mapper) {
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public Plan plan(DeploymentEntity deployment,
                     DeploymentReleaseEntity release,
                     Map<String, String> runtimeEnvironment,
                     boolean connectorConfigured) {
        return plan(deployment, release, runtimeEnvironment, connectorConfigured, false);
    }

    public Plan plan(DeploymentEntity deployment,
                     DeploymentReleaseEntity release,
                     Map<String, String> runtimeEnvironment,
                     boolean connectorConfigured,
                     boolean vectorizationRunnerConfigured) {
        return plan(deployment, null, release, runtimeEnvironment, connectorConfigured,
            vectorizationRunnerConfigured);
    }

    public Plan plan(DeploymentEntity deployment,
                     DeploymentVersionEntity version,
                     DeploymentReleaseEntity release,
                     Map<String, String> runtimeEnvironment,
                     boolean connectorConfigured,
                     boolean vectorizationRunnerConfigured) {
        required(deployment.getId(), "deployment");
        required(deployment.getTenantId(), "tenant");
        required(release.getTargetProfileId(), "target profile");
        required(release.getId(), "release");
        if (!deployment.getId().equals(release.getDeploymentId())) {
            throw new IllegalStateException("Runtime retirement topology release ownership is inconsistent.");
        }

        var features = new EnumMap<Feature, Presence>(Feature.class);
        for (Feature feature : Feature.values()) {
            String configured = feature.environmentKey == null || runtimeEnvironment == null
                ? null
                : runtimeEnvironment.get(feature.environmentKey);
            features.put(feature, explicitlyFalse(configured) ? Presence.ABSENT : Presence.PRESENT);
        }

        var dependencies = new ArrayList<Dependency>();
        if (connectorConfigured) dependencies.add(Dependency.CONNECTOR);
        if (vectorizationRunnerConfigured) dependencies.add(Dependency.VECTORIZATION_RUNNER);
        if (features.get(Feature.CONVERSATION_HANDOVER) == Presence.PRESENT) {
            dependencies.add(Dependency.HUMAN_INBOX);
        }

        var target = new LinkedHashMap<String, Object>();
        target.put("deploymentId", deployment.getId());
        target.put("targetProfileId", release.getTargetProfileId());
        target.put("releaseId", release.getId());

        var contract = new LinkedHashMap<String, Object>();
        contract.put("contractVersion", CONTRACT_VERSION);
        contract.put("target", target);
        contract.put("tenantId", deployment.getTenantId());
        contract.put("features", features);
        contract.put("remoteDependencies", List.copyOf(dependencies));
        contract.put("vectorScope", vectorScope(version, runtimeEnvironment, features.get(Feature.INDEXING)));
        try {
            String json = mapper.writeValueAsString(contract);
            String digest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))
            );
            return new Plan(json, digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Runtime retirement topology cannot be encoded.", exception);
        }
    }

    private boolean explicitlyFalse(String value) {
        return value != null && "false".equalsIgnoreCase(value.trim());
    }

    private VectorScope vectorScope(DeploymentVersionEntity version, Map<String, String> environment,
                                    Presence indexing) {
        if (indexing == Presence.ABSENT) return null;
        // Compatibility overloads are test/planning helpers only. Hosted runtime plans always
        // provide the immutable version so an index scope cannot be invented from mutable env.
        if (version == null) return null;
        String provider = value(environment, "AI_VECTOR_DB_TYPE", "lucene").toLowerCase(java.util.Locale.ROOT);
        String scopeType;
        String scopeValue;
        switch (provider) {
            case "qdrant" -> {
                scopeType = "COLLECTION_PREFIX";
                scopeValue = requiredScope(environment, "AI_PROVIDERS_QDRANT_COLLECTION_PREFIX", provider);
            }
            case "pinecone" -> {
                scopeType = "NAMESPACE_PREFIX";
                scopeValue = requiredScope(environment, "AI_PROVIDERS_PINECONE_NAMESPACE_PREFIX", provider);
            }
            case "milvus" -> {
                scopeType = "COLLECTION_PREFIX";
                scopeValue = requiredScope(environment, "AI_PROVIDERS_MILVUS_COLLECTION_PREFIX", provider);
            }
            case "lucene", "memory" -> {
                scopeType = "DEDICATED_RUNTIME_STORAGE";
                scopeValue = releaseScope(version);
            }
            default -> throw new IllegalStateException("Target retirement does not support vector provider '" + provider + "'.");
        }
        List<String> entityTypes = indexedEntityTypes(version);
        if (entityTypes.isEmpty()) {
            throw new IllegalStateException("Target retirement requires an immutable indexed entity inventory.");
        }
        return new VectorScope(provider, scopeType, scopeValue, entityTypes);
    }

    private List<String> indexedEntityTypes(DeploymentVersionEntity version) {
        if (version == null) return List.of();
        try {
            JsonNode config = mapper.readTree(version.getEntityConfigJson());
            java.util.TreeSet<String> values = new java.util.TreeSet<>();
            JsonNode current = config.path("ai-entities");
            if (current.isObject()) {
                current.fields().forEachRemaining(entry -> {
                    if (entry.getValue().path("indexing").path("enabled").asBoolean(true)) values.add(entry.getKey());
                });
            }
            JsonNode legacy = config.path("entities");
            if (legacy.isArray()) {
                for (JsonNode entity : legacy) {
                    if (!entity.path("indexing").path("enabled").asBoolean(true)) continue;
                    for (String field : List.of("entityType", "id", "name")) {
                        String candidate = entity.path(field).asText("").trim();
                        if (!candidate.isEmpty()) { values.add(candidate); break; }
                    }
                }
            }
            if (values.size() > 500 || values.stream().anyMatch(value -> value.length() > 128)) {
                throw new IllegalStateException("Target retirement indexed entity inventory is invalid.");
            }
            return List.copyOf(values);
        } catch (IllegalStateException error) { throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Target retirement cannot read the indexed entity inventory.", error);
        }
    }

    private String requiredScope(Map<String, String> environment, String key, String provider) {
        String value = value(environment, key, "");
        if (value.isBlank() || value.length() > 160) {
            throw new IllegalStateException("Target retirement requires a target-scoped " + provider + " prefix.");
        }
        return value;
    }

    private String releaseScope(DeploymentVersionEntity version) {
        if (version == null || version.getId() == null || version.getId().isBlank()) {
            throw new IllegalStateException("Target retirement requires an immutable local index owner.");
        }
        return "deployment-version:" + version.getId();
    }

    private String value(Map<String, String> environment, String key, String fallback) {
        if (environment == null) return fallback;
        String value = environment.get(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void required(String value, String label) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 128) {
            throw new IllegalStateException("Runtime retirement topology requires exact " + label + " identity.");
        }
    }
}
