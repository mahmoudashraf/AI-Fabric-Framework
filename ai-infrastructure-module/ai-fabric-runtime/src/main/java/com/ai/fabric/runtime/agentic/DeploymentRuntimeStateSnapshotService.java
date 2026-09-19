package com.ai.fabric.runtime.agentic;

import ai.fabric.rag.VectorDatabaseService;
import com.ai.fabric.runtime.config.RuntimeCapabilityManifestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DeploymentRuntimeStateSnapshotService {

    private final ObjectMapper objectMapper;
    private final VectorDatabaseService vectorDatabaseService;
    private final RuntimeCapabilityManifestService capabilityManifestService;

    @Value("${LOOMAI_DEPLOYMENT_BEHAVIOR_TYPE:CONVERSATIONAL}")
    private String behaviorType;

    @Value("${AI_FABRIC_FRAMEWORK_VERSION:unknown}")
    private String frameworkVersion;

    @Value("${PLATFORM_DEPLOYMENT_VERSION_ID:unknown}")
    private String deploymentVersionId;

    @Value("${APP_BUILD_COMMIT:${SOURCE_COMMIT:unknown}}")
    private String sourceCommit;

    @Value("${ai.execution.specialist-chains.enabled:false}")
    private boolean specialistChainsEnabled;

    public DeploymentRuntimeStateSnapshotService(
        ObjectMapper objectMapper,
        VectorDatabaseService vectorDatabaseService,
        RuntimeCapabilityManifestService capabilityManifestService
    ) {
        this.objectMapper = objectMapper;
        this.vectorDatabaseService = vectorDatabaseService;
        this.capabilityManifestService = capabilityManifestService;
    }

    public ObjectNode snapshot() {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("behaviorType", safe(behaviorType));
        snapshot.put("aiFabricFrameworkVersion", safe(frameworkVersion));
        snapshot.put("deploymentVersionId", safe(deploymentVersionId));
        snapshot.put("sourceCommit", safe(sourceCommit));
        snapshot.put(
            "runtimeCapabilityManifestHash",
            capabilityManifestService.manifestHash()
        );
        snapshot.put(
            "vectorBackend",
            vectorDatabaseService.getClass().getSimpleName()
        );
        snapshot.put("specialistChainsEnabled", specialistChainsEnabled);
        return snapshot;
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "unknown";
    }
}
