package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformGitHubActionsProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentGitHubVerificationDispatchRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentGitHubVerificationDispatchSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentGitHubVerificationRunSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class DeploymentGitHubVerificationService {

    private static final String ARTIFACT_SIGNING_SECRET_NAME = "PLATFORM_ARTIFACT_SIGNING_KEY";
    private static final String GITHUB_ACTIONS_TOKEN_SECRET_NAME = "GITHUB_ACTIONS_TOKEN";
    private static final String PLATFORM_OPERATOR_API_KEY_SECRET_NAME = "PLATFORM_OPERATOR_API_KEY";
    private static final String PLATFORM_ADMIN_API_KEY_SECRET_NAME = "PLATFORM_ADMIN_API_KEY";
    private static final String CONTEXT_RESOURCE_NAME = "github-actions-context.json";
    private static final String SIGNATURE_ALGORITHM = "HmacSHA256";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentReleaseRepository deploymentReleaseRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final DeploymentSourceResolver deploymentSourceResolver;
    private final PlatformSecretService platformSecretService;
    private final PlatformAuthProperties platformAuthProperties;
    private final PlatformDeliveryProperties platformDeliveryProperties;
    private final PlatformGitHubActionsProperties platformGitHubActionsProperties;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public DeploymentGitHubVerificationService(DeploymentRepository deploymentRepository,
                                               DeploymentReleaseRepository deploymentReleaseRepository,
                                               DeploymentVersionRepository deploymentVersionRepository,
                                               DeploymentAccessService deploymentAccessService,
                                               DeploymentSourceResolver deploymentSourceResolver,
                                               PlatformSecretService platformSecretService,
                                               PlatformAuthProperties platformAuthProperties,
                                               PlatformDeliveryProperties platformDeliveryProperties,
                                               PlatformGitHubActionsProperties platformGitHubActionsProperties,
                                               PlatformAuditService platformAuditService,
                                               ObjectMapper objectMapper) {
        this(
            deploymentRepository,
            deploymentReleaseRepository,
            deploymentVersionRepository,
            deploymentAccessService,
            deploymentSourceResolver,
            platformSecretService,
            platformAuthProperties,
            platformDeliveryProperties,
            platformGitHubActionsProperties,
            platformAuditService,
            objectMapper,
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()
        );
    }

    DeploymentGitHubVerificationService(DeploymentRepository deploymentRepository,
                                        DeploymentReleaseRepository deploymentReleaseRepository,
                                        DeploymentVersionRepository deploymentVersionRepository,
                                        DeploymentAccessService deploymentAccessService,
                                        DeploymentSourceResolver deploymentSourceResolver,
                                        PlatformSecretService platformSecretService,
                                        PlatformAuthProperties platformAuthProperties,
                                        PlatformDeliveryProperties platformDeliveryProperties,
                                        PlatformGitHubActionsProperties platformGitHubActionsProperties,
                                        PlatformAuditService platformAuditService,
                                        ObjectMapper objectMapper,
                                        HttpClient httpClient) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentReleaseRepository = deploymentReleaseRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.deploymentSourceResolver = deploymentSourceResolver;
        this.platformSecretService = platformSecretService;
        this.platformAuthProperties = platformAuthProperties;
        this.platformDeliveryProperties = platformDeliveryProperties;
        this.platformGitHubActionsProperties = platformGitHubActionsProperties;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public List<DeploymentGitHubVerificationRunSummary> listRuns(String deploymentId) {
        DeploymentEntity deployment = requireDeploymentView(deploymentId);
        String repository = deploymentSourceResolver.resolveRepository(deployment);
        String branch = deploymentSourceResolver.resolveBranch(deployment);
        deploymentSourceResolver.validateEffectiveSource(repository, branch);
        String token = requireGitHubActionsToken();
        return fetchWorkflowRuns(repository, branch, token).stream()
            .filter(run -> belongsToDeployment(run, deploymentId))
            .toList();
    }

    public DeploymentGitHubVerificationDispatchSummary dispatch(String deploymentId,
                                                                DeploymentGitHubVerificationDispatchRequest request) {
        DeploymentEntity deployment = requireDeploymentOperate(deploymentId);
        DeploymentReleaseEntity release = resolveActiveRelease(deployment);
        DeploymentVersionEntity version = deploymentVersionRepository.findById(release.getDeploymentVersionId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment version not found: " + release.getDeploymentVersionId()));
        String profile = normalizeProfile(request == null ? null : request.profile(), version);
        boolean verifyWrite = request == null || request.verifyWrite() == null || request.verifyWrite();
        VerificationContext context = buildContext(deployment, release, version, profile, verifyWrite);

        String repository = deploymentSourceResolver.resolveRepository(deployment);
        String branch = deploymentSourceResolver.resolveBranch(deployment);
        deploymentSourceResolver.validateEffectiveSource(repository, branch);
        String token = requireGitHubActionsToken();
        String workflowFile = platformGitHubActionsProperties.verificationWorkflowFile();
        Instant dispatchedAt = Instant.now();

        dispatchWorkflow(repository, branch, workflowFile, token, context);
        DeploymentGitHubVerificationRunSummary run = awaitDispatchedRun(
            repository,
            branch,
            workflowFile,
            token,
            deploymentId,
            release.getId(),
            dispatchedAt
        );

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("repository", repository);
        details.put("branch", branch);
        details.put("workflowFile", workflowFile);
        details.put("profile", profile);
        details.put("verifyWrite", verifyWrite);
        details.put("releaseId", release.getId());
        details.put("deploymentVersionId", version.getId());
        if (run != null) {
            details.put("runId", run.runId());
            details.put("runUrl", run.htmlUrl());
        }
        platformAuditService.record("GITHUB_ACTIONS_VERIFICATION_DISPATCHED", "DEPLOYMENT", deploymentId, details);

        String summaryMessage = run == null
            ? "GitHub Actions verification dispatched. The workflow run has not been indexed by GitHub yet."
            : "GitHub Actions verification dispatched.";

        return new DeploymentGitHubVerificationDispatchSummary(
            deploymentId,
            release.getId(),
            version.getId(),
            repository,
            branch,
            workflowFile,
            profile,
            verifyWrite,
            summaryMessage,
            run
        );
    }

    public String readSignedContext(String deploymentId,
                                    String releaseId,
                                    String profile,
                                    Boolean verifyWrite,
                                    Long expires,
                                    String signature) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        DeploymentReleaseEntity release = getRelease(deploymentId, releaseId);
        DeploymentVersionEntity version = deploymentVersionRepository.findById(release.getDeploymentVersionId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment version not found: " + release.getDeploymentVersionId()));

        String normalizedProfile = normalizeProfile(profile, version);
        boolean normalizedVerifyWrite = verifyWrite == null || verifyWrite;

        if (!PlatformSecurityContext.isAuthenticated()) {
            authorizeSignedContextAccess(deploymentId, releaseId, normalizedProfile, normalizedVerifyWrite, expires, signature);
        } else {
            deploymentAccessService.requireDeploymentAccess(deployment);
        }

        try {
            return objectMapper.writeValueAsString(buildContext(deployment, release, version, normalizedProfile, normalizedVerifyWrite).payload());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize GitHub Actions verification context.", ex);
        }
    }

    private DeploymentEntity requireDeploymentView(String deploymentId) {
        return deploymentAccessService.requireDeploymentAccess(getDeployment(deploymentId));
    }

    private DeploymentEntity requireDeploymentOperate(String deploymentId) {
        return deploymentAccessService.requireDeploymentOperatorAccess(getDeployment(deploymentId));
    }

    private DeploymentEntity getDeployment(String deploymentId) {
        return deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
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

    private VerificationContext buildContext(DeploymentEntity deployment,
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
            throw new ResponseStatusException(BAD_REQUEST, "Deployment must have live runtime and connector URLs before GitHub verification can run.");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("profile", profile);
        payload.put("script", "ecommerce".equals(profile) ? "scripts/verify-ecommerce-deployment.sh" : "scripts/verify-vector-deployment.sh");
        payload.put("deploymentId", deployment.getId());
        payload.put("deploymentName", deployment.getName());
        payload.put("releaseId", release.getId());
        payload.put("deploymentVersionId", version.getId());
        payload.put("verifyWrite", verifyWrite);
        payload.put("repository", deploymentSourceResolver.resolveRepository(deployment));
        payload.put("branch", deploymentSourceResolver.resolveBranch(deployment));

        ObjectNode env = payload.putObject("env");
        env.put("REST_CONNECTOR_BASE_URL", connectorBaseUrl);
        env.put("RUNTIME_BASE_URL", runtimeBaseUrl);
        env.put("API_KEY", firstPresent("CONNECTOR_API_KEY"));
        putIfText(env, "RUNTIME_ADMIN_API_KEY", firstPresent("APP_ADMIN_API_KEY"));
        putIfText(env, "CONNECTOR_ADMIN_API_KEY", firstPresent("APP_ADMIN_API_KEY"));
        putIfText(env, "PLATFORM_BASE_URL", platformDeliveryProperties.publicBaseUrl());
        putIfText(env, "PLATFORM_DEPLOYMENT_ID", deployment.getId());
        putIfText(env, "PLATFORM_API_KEY", resolveRequiredPlatformAutomationApiKey());
        putIfText(env, "PLATFORM_EXPECT_RELEASE_ID", release.getId());
        putIfText(env, "PLATFORM_EXPECT_VERSION_ID", version.getId());
        putIfText(env, "PLATFORM_EXPECT_RELEASE_STATUS", "APPLIED_VERIFIED");
        putIfText(env, "PLATFORM_EXPECT_VERIFICATION_STATUS", "PASSED");
        env.put("VERIFY_WRITE", verifyWrite);

        if ("ecommerce".equals(profile)) {
            String storeBaseUrl = trimToNull(routingConfig.path("connector").path("upstream").path("base-url").asText(""));
            if (storeBaseUrl == null) {
                throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Ecommerce GitHub verification requires connector.upstream.base-url to resolve the store URL."
                );
            }
            env.put("STORE_BASE_URL", storeBaseUrl);
        } else {
            List<String> entityTypes = resolveEntityTypes(entityConfig);
            if (entityTypes.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "Vector GitHub verification requires at least one configured AI entity type.");
            }
            env.put("EXPECTED_VECTOR_SPACES", String.join(",", entityTypes));
            env.put("EXPECTED_VECTOR_DB", expectedVectorDatabaseService(providerConfig));
        }

        String contextUrl = signedContextUrl(deployment.getId(), release.getId(), profile, verifyWrite);
        payload.put("contextUrl", contextUrl);
        return new VerificationContext(contextUrl, payload);
    }

    private String signedContextUrl(String deploymentId,
                                    String releaseId,
                                    String profile,
                                    boolean verifyWrite) {
        long expires = Instant.now().plus(platformGitHubActionsProperties.verificationContextTtl()).getEpochSecond();
        String signingKey = requireArtifactSigningKey();
        String signature = signContext(deploymentId, releaseId, profile, verifyWrite, expires, signingKey);
        return UriComponentsBuilder.fromUriString(platformDeliveryProperties.publicBaseUrl())
            .path("/api/deployments/{deploymentId}/releases/{releaseId}/github-actions/context.json")
            .queryParam("profile", profile)
            .queryParam("verifyWrite", verifyWrite)
            .queryParam("expires", expires)
            .queryParam("sig", signature)
            .buildAndExpand(deploymentId, releaseId)
            .toUriString();
    }

    private void authorizeSignedContextAccess(String deploymentId,
                                              String releaseId,
                                              String profile,
                                              boolean verifyWrite,
                                              Long expires,
                                              String signature) {
        if (expires == null || !StringUtils.hasText(signature)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Missing signed GitHub verification context access parameters.");
        }
        if (Instant.ofEpochSecond(expires).isBefore(Instant.now())) {
            throw new ResponseStatusException(UNAUTHORIZED, "GitHub verification context URL has expired.");
        }
        String expected = signContext(deploymentId, releaseId, profile, verifyWrite, expires, requireArtifactSigningKey());
        if (!MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            signature.trim().getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(UNAUTHORIZED, "GitHub verification context signature is invalid.");
        }
    }

    private String signContext(String deploymentId,
                               String releaseId,
                               String profile,
                               boolean verifyWrite,
                               long expires,
                               String signingKey) {
        try {
            Mac mac = Mac.getInstance(SIGNATURE_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), SIGNATURE_ALGORITHM));
            String payload = deploymentId + ":" + releaseId + ":" + CONTEXT_RESOURCE_NAME + ":" + profile + ":" + verifyWrite + ":" + expires;
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to sign GitHub verification context URL.", ex);
        }
    }

    private String requireArtifactSigningKey() {
        String signingKey = platformSecretService.resolveSecret(ARTIFACT_SIGNING_SECRET_NAME);
        if (!StringUtils.hasText(signingKey)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PLATFORM_ARTIFACT_SIGNING_KEY is required for GitHub verification context delivery.");
        }
        return signingKey.trim();
    }

    private String requireGitHubActionsToken() {
        String token = platformSecretService.resolveSecret(GITHUB_ACTIONS_TOKEN_SECRET_NAME);
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(BAD_REQUEST, "GITHUB_ACTIONS_TOKEN is required before GitHub Actions verification can run.");
        }
        return token.trim();
    }

    private void dispatchWorkflow(String repository,
                                  String branch,
                                  String workflowFile,
                                  String token,
                                  VerificationContext context) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("ref", branch);
        ObjectNode inputs = body.putObject("inputs");
        inputs.put("deployment_id", context.payload().path("deploymentId").asText());
        inputs.put("release_id", context.payload().path("releaseId").asText());
        inputs.put("verification_profile", context.payload().path("profile").asText());
        inputs.put("context_url", context.contextUrl());

        HttpRequest request = HttpRequest.newBuilder(workflowDispatchUri(repository, workflowFile))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer " + token)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 204) {
                throw githubFailure("Failed to dispatch GitHub verification workflow.", response);
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to dispatch GitHub verification workflow: " + ex.getMessage(), ex);
        }
    }

    private DeploymentGitHubVerificationRunSummary awaitDispatchedRun(String repository,
                                                                      String branch,
                                                                      String workflowFile,
                                                                      String token,
                                                                      String deploymentId,
                                                                      String releaseId,
                                                                      Instant dispatchedAt) {
        Instant deadline = Instant.now().plus(platformGitHubActionsProperties.workflowRunLookupTimeout());
        while (Instant.now().isBefore(deadline)) {
            List<DeploymentGitHubVerificationRunSummary> runs = fetchWorkflowRuns(repository, branch, token);
            for (DeploymentGitHubVerificationRunSummary run : runs) {
                if (!belongsToDeployment(run, deploymentId)) {
                    continue;
                }
                if (!belongsToRelease(run, releaseId)) {
                    continue;
                }
                if (run.createdAt() != null && run.createdAt().isBefore(dispatchedAt.minusSeconds(15))) {
                    continue;
                }
                if (workflowFile.equals(run.workflowFile())) {
                    return run;
                }
            }
            sleepQuietly(2_000L);
        }
        return null;
    }

    private List<DeploymentGitHubVerificationRunSummary> fetchWorkflowRuns(String repository,
                                                                           String branch,
                                                                           String token) {
        HttpRequest request = HttpRequest.newBuilder(workflowRunsUri(repository, platformGitHubActionsProperties.verificationWorkflowFile(), branch))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer " + token)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .GET()
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw githubFailure("Failed to list GitHub verification workflow runs.", response);
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<DeploymentGitHubVerificationRunSummary> runs = new ArrayList<>();
            for (JsonNode run : root.path("workflow_runs")) {
                String workflowPath = trimToNull(run.path("path").asText(""));
                String workflowFile = workflowPath == null ? platformGitHubActionsProperties.verificationWorkflowFile() : workflowPath.substring(workflowPath.lastIndexOf('/') + 1);
                runs.add(new DeploymentGitHubVerificationRunSummary(
                    run.path("id").asLong(),
                    workflowFile,
                    trimToNull(run.path("display_title").asText("")),
                    trimToNull(run.path("status").asText("")),
                    trimToNull(run.path("conclusion").asText("")),
                    trimToNull(run.path("html_url").asText("")),
                    trimToNull(run.path("head_branch").asText("")),
                    trimToNull(run.path("event").asText("")),
                    parseInstant(run.path("created_at").asText("")),
                    parseInstant(run.path("updated_at").asText(""))
                ));
            }
            return runs;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to list GitHub verification workflow runs: " + ex.getMessage(), ex);
        }
    }

    private URI workflowDispatchUri(String repository, String workflowFile) {
        String encodedWorkflow = URLEncoder.encode(workflowFile, StandardCharsets.UTF_8);
        return URI.create(platformGitHubActionsProperties.apiBaseUrl()
            + "/repos/" + repository + "/actions/workflows/" + encodedWorkflow + "/dispatches");
    }

    private URI workflowRunsUri(String repository, String workflowFile, String branch) {
        String encodedWorkflow = URLEncoder.encode(workflowFile, StandardCharsets.UTF_8);
        return UriComponentsBuilder.fromUriString(
                platformGitHubActionsProperties.apiBaseUrl()
                    + "/repos/" + repository + "/actions/workflows/" + encodedWorkflow + "/runs"
            )
            .queryParam("event", "workflow_dispatch")
            .queryParam("branch", branch)
            .queryParam("per_page", 25)
            .build(true)
            .toUri();
    }

    private ResponseStatusException githubFailure(String message, HttpResponse<String> response) {
        String suffix = trimToNull(response.body());
        String details = suffix == null ? "HTTP " + response.statusCode() : "HTTP " + response.statusCode() + " " + suffix;
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message + " " + details);
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

    private String normalizeProfile(String requestedProfile, DeploymentVersionEntity version) {
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

    private String resolveRequiredPlatformAutomationApiKey() {
        if (!platformAuthProperties.enabled()) {
            return null;
        }
        if (!platformAuthProperties.apiKeyEnabled()) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "GitHub Actions verification requires platform API-key auth to be enabled when platform auth is on."
            );
        }
        String operator = firstNonBlank(
            platformAuthProperties.operatorApiKey(),
            platformSecretService.resolveSecret(PLATFORM_OPERATOR_API_KEY_SECRET_NAME)
        );
        if (operator != null) {
            return operator;
        }
        String admin = firstNonBlank(
            platformAuthProperties.adminApiKey(),
            platformSecretService.resolveSecret(PLATFORM_ADMIN_API_KEY_SECRET_NAME)
        );
        if (admin != null) {
            return admin;
        }
        throw new ResponseStatusException(
            BAD_REQUEST,
            "GitHub Actions verification requires PLATFORM_OPERATOR_API_KEY or PLATFORM_ADMIN_API_KEY in platform config or the Secrets workspace."
        );
    }

    private String firstPresent(String secretName) {
        return trimToNull(platformSecretService.resolveSecret(secretName));
    }

    private void putIfText(ObjectNode node, String key, String value) {
        if (StringUtils.hasText(value)) {
            node.put(key, value.trim());
        }
    }

    private boolean belongsToDeployment(DeploymentGitHubVerificationRunSummary run, String deploymentId) {
        return run != null && run.displayTitle() != null && run.displayTitle().contains(deploymentId);
    }

    private boolean belongsToRelease(DeploymentGitHubVerificationRunSummary run, String releaseId) {
        return run != null && run.displayTitle() != null && run.displayTitle().contains(releaseId);
    }

    private Instant parseInstant(String value) {
        try {
            return StringUtils.hasText(value) ? Instant.parse(value.trim()) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String primary, String secondary) {
        String normalizedPrimary = trimToNull(primary);
        if (normalizedPrimary != null) {
            return normalizedPrimary;
        }
        return trimToNull(secondary);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private record VerificationContext(String contextUrl, ObjectNode payload) {
    }
}
