package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
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
    static final String CONTRACT_VERSION = "LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_V1";

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

    private void required(String value, String label) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 128) {
            throw new IllegalStateException("Runtime retirement topology requires exact " + label + " identity.");
        }
    }
}
