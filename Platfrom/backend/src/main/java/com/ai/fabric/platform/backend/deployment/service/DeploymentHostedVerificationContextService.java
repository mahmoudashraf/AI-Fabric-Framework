package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentHostedVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVectorizationVerificationSummary;
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
    private final DeploymentTenantScopedVectorService deploymentTenantScopedVectorService;
    private final DeploymentVectorizationVerificationService deploymentVectorizationVerificationService;
    private final PlatformDeliveryProperties platformDeliveryProperties;
    private final ObjectMapper objectMapper;

    public DeploymentHostedVerificationContextService(DeploymentRepository deploymentRepository,
                                                      DeploymentReleaseRepository deploymentReleaseRepository,
                                                      DeploymentVersionRepository deploymentVersionRepository,
                                                      DeploymentAccessService deploymentAccessService,
                                                      DeploymentVerificationRolloutService deploymentVerificationRolloutService,
                                                      DeploymentTenantScopedVectorService deploymentTenantScopedVectorService,
                                                      DeploymentVectorizationVerificationService deploymentVectorizationVerificationService,
                                                      PlatformDeliveryProperties platformDeliveryProperties,
                                                      ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentReleaseRepository = deploymentReleaseRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.deploymentVerificationRolloutService = deploymentVerificationRolloutService;
        this.deploymentTenantScopedVectorService = deploymentTenantScopedVectorService;
        this.deploymentVectorizationVerificationService = deploymentVectorizationVerificationService;
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
        if (runtimeBaseUrl == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Deployment must have a live runtime URL before hosted verification can run.");
        }

        Map<String, String> env = new LinkedHashMap<>();
        env.put("RUNTIME_BASE_URL", runtimeBaseUrl);
        env.put("PLATFORM_BASE_URL", platformDeliveryProperties.publicBaseUrl());
        env.put("PLATFORM_DEPLOYMENT_ID", deployment.getId());
        env.put("VERIFY_PLATFORM_USER_DIRECTORY_ADMIN", "true");
        env.put("PLATFORM_EXPECT_RELEASE_ID", release.getId());
        env.put("PLATFORM_EXPECT_VERSION_ID", version.getId());
        env.put("PLATFORM_EXPECT_RELEASE_STATUS", normalizeExpectation(release.getStatus(), "APPLIED"));
        env.put("PLATFORM_EXPECT_VERIFICATION_STATUS", normalizeExpectation(release.getVerificationStatus(), "UNKNOWN"));
        env.put("VERIFY_WRITE", Boolean.toString(verifyWrite));
        DeploymentTenantScopedVectorSummary tenantScopedSummary = deploymentTenantScopedVectorService.build(deployment, providerConfig);
        DeploymentVectorizationVerificationSummary vectorizationSummary = deploymentVectorizationVerificationService.build(deployment, entityConfig);
        addRuntimeOperationalUrls(env, runtimeBaseUrl, connectorBaseUrl);
        addTenantScopedVectorExpectations(env, tenantScopedSummary);
        addVectorizationExpectations(env, vectorizationSummary, verifyWrite, tenantScopedSummary);

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

    private void addRuntimeOperationalUrls(Map<String, String> env,
                                           String runtimeBaseUrl,
                                           String connectorBaseUrl) {
        putIfPresent(env, "RUNTIME_AUTH_OVERVIEW_URL", joinRuntimeUrl(runtimeBaseUrl, "/api/admin/auth/overview"));
        if (connectorBaseUrl == null) {
            return;
        }
        putIfPresent(env, "RUNTIME_CONNECTOR_HEALTH_URL", joinRuntimeUrl(runtimeBaseUrl, "/api/admin/connector/health"));
        putIfPresent(env, "RUNTIME_CONNECTOR_OVERVIEW_URL", joinRuntimeUrl(runtimeBaseUrl, "/api/admin/connector/overview"));
        putIfPresent(env, "RUNTIME_CONNECTOR_ACTIONS_OVERVIEW_URL", joinRuntimeUrl(runtimeBaseUrl, "/api/admin/connector/actions/overview"));
        putIfPresent(env, "RUNTIME_CONNECTOR_CONFIG_URL", joinRuntimeUrl(runtimeBaseUrl, "/api/admin/connector/config"));
        putIfPresent(env, "RUNTIME_CONNECTOR_LOGS_URL", joinRuntimeUrl(runtimeBaseUrl, "/api/admin/connector/logs"));
        putIfPresent(env, "RUNTIME_CONNECTOR_READ_PROXY_BASE_URL", joinRuntimeUrl(runtimeBaseUrl, "/api/admin/connector/proxy"));
    }

    private void addTenantScopedVectorExpectations(Map<String, String> env,
                                                   DeploymentTenantScopedVectorSummary summary) {
        env.put("EXPECT_TENANT_SCOPED_SHARED", Boolean.toString(summary.sharedStorage()));
        putIfPresent(env, "EXPECT_TENANT_SCOPED_STATUS", summary.status());
        putIfPresent(env, "EXPECT_TENANT_SCOPED_CUSTOMER_ID", summary.customerId());
        putIfPresent(env, "EXPECT_TENANT_SCOPED_TENANT_ID", summary.tenantId());
        putIfPresent(env, "EXPECT_TENANT_SCOPED_SCOPE_TYPE", summary.scopeType());
        putIfPresent(env, "EXPECT_TENANT_SCOPED_ROOT_RESOURCE_VALUE", summary.rootResourceValue());
        putIfPresent(env, "EXPECT_TENANT_SCOPED_SCOPE_PREFIX", summary.scopePrefix());
        putIfPresent(env, "EXPECT_TENANT_SCOPED_TENANT_HANDLE", summary.tenantHandle());
        putIfPresent(env, "EXPECT_TENANT_SCOPED_SCOPE_PATTERN", summary.scopePattern());
        env.put("EXPECT_TENANT_SCOPED_MIGRATION_LOCKED", Boolean.toString(summary.migrationLocked()));
        if (summary.registry() != null) {
            putIfPresent(env, "EXPECT_TENANT_SCOPED_REGISTRY_STATUS", summary.registry().status());
        }
        putIfPresent(env, "EXPECT_TENANT_SCOPED_READINESS_STATUS", tenantScopedReadinessStatus(summary));
    }

    private void addVectorizationExpectations(Map<String, String> env,
                                              DeploymentVectorizationVerificationSummary summary,
                                              boolean verifyWrite,
                                              DeploymentTenantScopedVectorSummary tenantScopedSummary) {
        env.put("EXPECT_VECTORIZATION_PLAN_PRESENT", Boolean.toString(summary.planPresent()));
        env.put("EXPECT_VECTORIZATION_SOURCE_CONNECTION_PRESENT", Boolean.toString(summary.sourceConnectionPresent()));
        env.put("EXPECT_VECTORIZATION_ACTIVE_REVISION_PRESENT", Boolean.toString(summary.activeRevisionPresent()));
        env.put("EXPECT_VECTORIZATION_CONFIGURED", Boolean.toString(summary.configured()));
        env.put("EXPECT_VECTORIZATION_RUNNER_PRESENT", Boolean.toString(summary.runnerPresent()));
        env.put("EXPECT_VECTORIZATION_RUNNER_REQUIRED", Boolean.toString(summary.runnerRequired()));
        env.put("EXPECT_VECTORIZATION_PLATFORM_MANAGED_RUNNER", Boolean.toString(summary.platformManagedRunnerExpected()));
        if (!summary.availableEntityTypes().isEmpty()) {
            env.put("EXPECT_VECTORIZATION_AVAILABLE_ENTITIES", String.join(",", summary.availableEntityTypes()));
        }
        if (!summary.entityScope().isEmpty()) {
            env.put("EXPECT_VECTORIZATION_ENTITY_SCOPE", String.join(",", summary.entityScope()));
        }
        if (summary.plan() != null) {
            putIfPresent(env, "EXPECT_VECTORIZATION_PLAN_STATUS", summary.plan().status());
            putIfPresent(env, "EXPECT_VECTORIZATION_RUNNER_MODE", summary.plan().runnerMode());
            putIfPresent(env, "EXPECT_VECTORIZATION_SYNC_STATE", summary.plan().syncState());
        }
        if (summary.sourceConnection() != null) {
            putIfPresent(env, "EXPECT_VECTORIZATION_SOURCE_ADAPTER", summary.sourceConnection().adapterType());
            putIfPresent(env, "EXPECT_VECTORIZATION_SOURCE_AUTH_MODE", summary.sourceConnection().authMode());
            putIfPresent(env, "EXPECT_VECTORIZATION_SOURCE_STATUS", summary.sourceConnection().status());
        }
        if (summary.runner() != null) {
            putIfPresent(env, "EXPECT_VECTORIZATION_RUNNER_STATUS", summary.runner().registrationStatus());
            putIfPresent(env, "EXPECT_VECTORIZATION_RUNNER_COMPATIBILITY_STATUS", summary.runner().compatibilityStatus());
        }
        env.put("VERIFY_VECTORIZATION_ADMIN", Boolean.toString(summary.configured()));
        env.put(
            "VERIFY_VECTORIZATION_RUNNER_ACTIVE",
            Boolean.toString(summary.configured() && summary.runnerRequired() && summary.runnerPresent())
        );
        env.put(
            "VERIFY_VECTORIZATION_SAMPLE",
            Boolean.toString(verifyWrite && summary.configured() && summary.runnerRequired() && summary.runnerPresent())
        );
        env.put(
            "VERIFY_TENANT_SHARED_ISOLATION",
            Boolean.toString(
                verifyWrite
                    && summary.configured()
                    && summary.runnerRequired()
                    && summary.runnerPresent()
                    && tenantScopedSummary != null
                    && tenantScopedSummary.sharedStorage()
            )
        );
    }

    private String tenantScopedReadinessStatus(DeploymentTenantScopedVectorSummary summary) {
        String status = normalizeExpectation(summary.status(), "UNKNOWN");
        if (summary.registry() == null) {
            return status;
        }
        String registryStatus = normalizeExpectation(summary.registry().status(), "INFO");
        if ("BLOCKED".equalsIgnoreCase(registryStatus)) {
            return "BLOCKED";
        }
        if ("WARNING".equalsIgnoreCase(registryStatus) && !"BLOCKED".equalsIgnoreCase(status)) {
            return "WARNING";
        }
        return status;
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

    private String joinRuntimeUrl(String baseUrl, String path) {
        String normalizedBase = trimToNull(baseUrl);
        String normalizedPath = trimToNull(path);
        if (normalizedBase == null || normalizedPath == null) {
            return null;
        }
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return normalizedBase + normalizedPath;
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

    private void putIfPresent(Map<String, String> env, String key, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            env.put(key, normalized);
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
