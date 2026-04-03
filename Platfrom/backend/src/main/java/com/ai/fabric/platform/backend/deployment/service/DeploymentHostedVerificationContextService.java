package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentHostedVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationContextSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentHostedVerificationContextService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentReleaseRepository deploymentReleaseRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final DeploymentVerificationRolloutService deploymentVerificationRolloutService;
    private final PlatformDeliveryProperties platformDeliveryProperties;
    private final ObjectMapper objectMapper;

    public DeploymentHostedVerificationContextService(DeploymentRepository deploymentRepository,
                                                      DeploymentReleaseRepository deploymentReleaseRepository,
                                                      DeploymentVersionRepository deploymentVersionRepository,
                                                      DeploymentAccessService deploymentAccessService,
                                                      DeploymentVerificationRolloutService deploymentVerificationRolloutService,
                                                      PlatformDeliveryProperties platformDeliveryProperties,
                                                      ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentReleaseRepository = deploymentReleaseRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.deploymentVerificationRolloutService = deploymentVerificationRolloutService;
        this.platformDeliveryProperties = platformDeliveryProperties;
        this.objectMapper = objectMapper;
    }

    public DeploymentHostedVerificationContextSummary buildContextForOperator(String deploymentId,
                                                                             String requestedProfile,
                                                                             boolean verifyWrite) {
        DeploymentEntity deployment = deploymentAccessService.requireDeploymentOperatorAccess(getDeployment(deploymentId));
        DeploymentReleaseEntity release = resolveActiveRelease(deployment);
        DeploymentVersionEntity version = getVersion(release.getDeploymentVersionId());
        return buildContext(deployment, release, version, normalizeProfile(requestedProfile, deployment, version), verifyWrite);
    }

    public DeploymentHostedVerificationContextSummary buildContextForRun(DeploymentHostedVerificationRunEntity run) {
        DeploymentEntity deployment = getDeployment(run.getDeploymentId());
        DeploymentReleaseEntity release = getRelease(run.getDeploymentId(), run.getReleaseId());
        DeploymentVersionEntity version = getVersion(run.getDeploymentVersionId());
        return buildContext(deployment, release, version, normalizeProfile(run.getVerificationProfile(), deployment, version), run.isVerifyWrite());
    }

    private DeploymentHostedVerificationContextSummary buildContext(DeploymentEntity deployment,
                                                                    DeploymentReleaseEntity release,
                                                                    DeploymentVersionEntity version,
                                                                    String profile,
                                                                    boolean verifyWrite) {
        JsonNode providerConfig = readJson(version.getProviderConfigJson());
        JsonNode routingConfig = readJson(version.getRoutingConfigJson());
        JsonNode entityConfig = readJson(version.getEntityConfigJson());

        String runtimeBaseUrl = trimToNull(deployment.getRuntimeBaseUrl());
        String connectorBaseUrl = trimToNull(deployment.getConnectorBaseUrl());
        if (runtimeBaseUrl == null || connectorBaseUrl == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Deployment must have live runtime and connector URLs before hosted verification can run.");
        }

        Map<String, String> env = new LinkedHashMap<>();
        env.put("REST_CONNECTOR_BASE_URL", connectorBaseUrl);
        env.put("RUNTIME_BASE_URL", runtimeBaseUrl);
        env.put("PLATFORM_BASE_URL", platformDeliveryProperties.publicBaseUrl());
        env.put("PLATFORM_DEPLOYMENT_ID", deployment.getId());
        env.put("PLATFORM_EXPECT_RELEASE_ID", release.getId());
        env.put("PLATFORM_EXPECT_VERSION_ID", version.getId());
        env.put("PLATFORM_EXPECT_RELEASE_STATUS", normalizeExpectation(release.getStatus(), "APPLIED"));
        env.put("PLATFORM_EXPECT_VERIFICATION_STATUS", normalizeExpectation(release.getVerificationStatus(), "UNKNOWN"));
        env.put("VERIFY_WRITE", Boolean.toString(verifyWrite));

        if ("ecommerce".equals(profile)) {
            String storeBaseUrl = trimToNull(routingConfig.path("connector").path("upstream").path("base-url").asText(""));
            if (storeBaseUrl == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Ecommerce verification requires connector.upstream.base-url to resolve the store URL.");
            }
            env.put("STORE_BASE_URL", storeBaseUrl);
        } else {
            List<String> entityTypes = resolveEntityTypes(entityConfig);
            if (entityTypes.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "Vector verification requires at least one configured AI entity type.");
            }
            env.put("EXPECTED_VECTOR_SPACES", String.join(",", entityTypes));
            env.put("EXPECTED_VECTOR_DB", expectedVectorDatabaseService(providerConfig));
        }

        String script = "ecommerce".equals(profile)
            ? "scripts/verify-ecommerce-deployment.sh"
            : "scripts/verify-vector-deployment.sh";
        return new DeploymentHostedVerificationContextSummary(
            profile,
            script,
            deployment.getId(),
            release.getId(),
            version.getId(),
            verifyWrite,
            env
        );
    }

    private DeploymentEntity getDeployment(String deploymentId) {
        return deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
    }

    private DeploymentVersionEntity getVersion(String versionId) {
        return deploymentVersionRepository.findById(versionId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment version not found: " + versionId));
    }

    private DeploymentReleaseEntity getRelease(String deploymentId, String releaseId) {
        DeploymentReleaseEntity release = deploymentReleaseRepository.findById(releaseId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment release not found: " + releaseId));
        if (!deploymentId.equals(release.getDeploymentId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Release does not belong to deployment: " + deploymentId);
        }
        return release;
    }

    private DeploymentReleaseEntity resolveActiveRelease(DeploymentEntity deployment) {
        String activeVersionId = trimToNull(deployment.getActiveVersionId());
        if (activeVersionId != null) {
            DeploymentReleaseEntity matching = deploymentReleaseRepository
                .findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc(deployment.getId(), activeVersionId)
                .orElse(null);
            if (matching != null) {
                return matching;
            }
        }
        return deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "No deployment release exists yet for: " + deployment.getId()));
    }

    private JsonNode readJson(String value) {
        if (!StringUtils.hasText(value)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private List<String> resolveEntityTypes(JsonNode entityConfig) {
        JsonNode aiEntities = entityConfig.path("ai-entities");
        if (!aiEntities.isObject()) {
            return List.of();
        }
        List<String> entityTypes = new ArrayList<>();
        aiEntities.fieldNames().forEachRemaining(field -> {
            String normalized = trimToNull(field);
            if (normalized != null) {
                entityTypes.add(normalized);
            }
        });
        return entityTypes.stream().distinct().toList();
    }

    private String expectedVectorDatabaseService(JsonNode providerConfig) {
        String vectorStrategy = trimToNull(providerConfig.path("vectorStrategy").asText(""));
        return switch ((vectorStrategy == null ? "lucene" : vectorStrategy).toLowerCase(Locale.ROOT)) {
            case "qdrant" -> "QdrantVectorDatabaseService";
            case "pinecone" -> "PineconeVectorDatabaseService";
            case "weaviate" -> "WeaviateVectorDatabaseService";
            case "milvus" -> "MilvusVectorDatabaseService";
            case "memory" -> "InMemoryVectorDatabaseService";
            default -> "LuceneVectorDatabaseService";
        };
    }

    private String normalizeProfile(String requestedProfile, DeploymentEntity deployment, DeploymentVersionEntity version) {
        String canonicalProfile = deploymentVerificationRolloutService.canonicalVerificationProfile(deployment.getId());
        if (canonicalProfile != null) {
            return canonicalProfile;
        }
        String normalized = trimToNull(requestedProfile);
        if (normalized == null) {
            String storeBaseUrl = trimToNull(readJson(version.getRoutingConfigJson()).path("connector").path("upstream").path("base-url").asText(""));
            return storeBaseUrl != null ? "ecommerce" : "vector";
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!"vector".equals(normalized) && !"ecommerce".equals(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "profile must be either 'vector' or 'ecommerce'.");
        }
        return normalized;
    }

    private String normalizeExpectation(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
