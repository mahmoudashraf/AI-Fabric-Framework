package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderConnectivityProbeSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderConnectivitySummary;
import com.ai.fabric.platform.backend.secret.model.DeploymentSecretResolutionSummary;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretResolutionService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DeploymentProviderConnectivityService {

    private static final String EMBEDDING_SMOKE_TEXT = "connectivity smoke probe";
    private static final Duration DEFAULT_JSON_PROBE_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration MIN_LLM_PROBE_TIMEOUT = Duration.ofSeconds(60);

    private final PlatformSecretService platformSecretService;
    private final DeploymentProviderSecretResolutionService deploymentProviderSecretResolutionService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient;
    private final PineconeControlPlaneClient pineconeControlPlaneClient;
    private final ZillizCloudControlPlaneClient zillizCloudControlPlaneClient;

    @Autowired
    public DeploymentProviderConnectivityService(PlatformSecretService platformSecretService,
                                                 DeploymentProviderSecretResolutionService deploymentProviderSecretResolutionService,
                                                 ObjectMapper objectMapper,
                                                 QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient,
                                                 PineconeControlPlaneClient pineconeControlPlaneClient,
                                                 ZillizCloudControlPlaneClient zillizCloudControlPlaneClient) {
        this(
            platformSecretService,
            deploymentProviderSecretResolutionService,
            objectMapper,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
            qdrantCloudControlPlaneClient,
            pineconeControlPlaneClient,
            zillizCloudControlPlaneClient
        );
    }

    DeploymentProviderConnectivityService(PlatformSecretService platformSecretService,
                                          ObjectMapper objectMapper,
                                          HttpClient httpClient) {
        this(
            platformSecretService,
            new DeploymentProviderSecretResolutionService(platformSecretService),
            objectMapper,
            httpClient,
            new QdrantCloudControlPlaneClient(objectMapper, httpClient),
            new PineconeControlPlaneClient(objectMapper, httpClient),
            new ZillizCloudControlPlaneClient(objectMapper, httpClient)
        );
    }

    DeploymentProviderConnectivityService(PlatformSecretService platformSecretService,
                                          ObjectMapper objectMapper,
                                          HttpClient httpClient,
                                          QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient) {
        this(
            platformSecretService,
            new DeploymentProviderSecretResolutionService(platformSecretService),
            objectMapper,
            httpClient,
            qdrantCloudControlPlaneClient,
            new PineconeControlPlaneClient(objectMapper, httpClient),
            new ZillizCloudControlPlaneClient(objectMapper, httpClient)
        );
    }

    DeploymentProviderConnectivityService(PlatformSecretService platformSecretService,
                                          ObjectMapper objectMapper,
                                          HttpClient httpClient,
                                          PineconeControlPlaneClient pineconeControlPlaneClient) {
        this(
            platformSecretService,
            new DeploymentProviderSecretResolutionService(platformSecretService),
            objectMapper,
            httpClient,
            new QdrantCloudControlPlaneClient(objectMapper, httpClient),
            pineconeControlPlaneClient,
            new ZillizCloudControlPlaneClient(objectMapper, httpClient)
        );
    }

    DeploymentProviderConnectivityService(PlatformSecretService platformSecretService,
                                          ObjectMapper objectMapper,
                                          HttpClient httpClient,
                                          ZillizCloudControlPlaneClient zillizCloudControlPlaneClient) {
        this(
            platformSecretService,
            new DeploymentProviderSecretResolutionService(platformSecretService),
            objectMapper,
            httpClient,
            new QdrantCloudControlPlaneClient(objectMapper, httpClient),
            new PineconeControlPlaneClient(objectMapper, httpClient),
            zillizCloudControlPlaneClient
        );
    }

    DeploymentProviderConnectivityService(PlatformSecretService platformSecretService,
                                          DeploymentProviderSecretResolutionService deploymentProviderSecretResolutionService,
                                          ObjectMapper objectMapper,
                                          HttpClient httpClient,
                                          QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient,
                                          PineconeControlPlaneClient pineconeControlPlaneClient,
                                          ZillizCloudControlPlaneClient zillizCloudControlPlaneClient) {
        this.platformSecretService = platformSecretService;
        this.deploymentProviderSecretResolutionService = deploymentProviderSecretResolutionService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.qdrantCloudControlPlaneClient = qdrantCloudControlPlaneClient;
        this.pineconeControlPlaneClient = pineconeControlPlaneClient;
        this.zillizCloudControlPlaneClient = zillizCloudControlPlaneClient;
    }

    public DeploymentProviderConnectivitySummary probe(DeploymentEntity deployment, DeploymentDraftEntity draft) {
        return probe(
            deployment.getId(),
            deployment.getName(),
            readJson(draft.getProviderConfigJson()),
            readJson(draft.getEntityConfigJson())
        );
    }

    DeploymentProviderConnectivitySummary probe(String deploymentId,
                                                String deploymentName,
                                                JsonNode providerConfig,
                                                JsonNode entityConfig) {
        List<DeploymentProviderConnectivityProbeSummary> probes = new ArrayList<>();
        List<DeploymentSecretResolutionSummary> effectiveSecretResolutions =
            effectiveSecretResolutions(deploymentId, providerConfig);
        String vectorStrategy = ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig);
        switch (vectorStrategy) {
            case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_PINECONE -> probes.add(probePinecone(deploymentId, providerConfig));
            case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT -> probes.add(
                ManagedDeploymentProfileCatalog.qdrantPlatformManaged(providerConfig)
                    ? probeQdrantCloud(providerConfig)
                    : probeQdrant(deploymentId, providerConfig)
            );
            case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_WEAVIATE -> probes.add(probeWeaviate(deploymentId, providerConfig));
            case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_MILVUS -> probes.add(
                ManagedDeploymentProfileCatalog.milvusPlatformManaged(providerConfig)
                    ? probeZillizCloud(providerConfig)
                    : new DeploymentProviderConnectivityProbeSummary(
                        "milvus_connectivity",
                        "Milvus connectivity",
                        "SKIPPED",
                        normalizeMilvusEndpoint(providerConfig),
                        "Milvus does not expose a platform-safe HTTP readiness probe in this slice. Verify host and credentials during apply."
                    )
            );
            default -> probes.add(new DeploymentProviderConnectivityProbeSummary(
                "local_vector_backend",
                "Local vector backend",
                "SKIPPED",
                vectorStrategy,
                "Selected vector backend is local to the runtime and does not require an external vendor connectivity probe."
            ));
        }

        addIfPresent(
            probes,
            probeInferenceLlmEndpoint(
                deploymentId,
                providerConfig,
                "orchestration",
                ManagedDeploymentProfileCatalog.orchestrationLlmProvider(providerConfig),
                ManagedDeploymentProfileCatalog.orchestrationEndpointProfile(providerConfig),
                ManagedDeploymentProfileCatalog.orchestrationBaseUrl(providerConfig),
                ManagedDeploymentProfileCatalog.orchestrationApiKeySecretRef(providerConfig),
                ManagedDeploymentProfileCatalog.orchestrationDeploymentName(providerConfig),
                ManagedDeploymentProfileCatalog.orchestrationApiVersion(providerConfig)
            )
        );
        addIfPresent(
            probes,
            probeInferenceLlmEndpoint(
                deploymentId,
                providerConfig,
                "generation",
                ManagedDeploymentProfileCatalog.generationLlmProvider(providerConfig),
                ManagedDeploymentProfileCatalog.generationEndpointProfile(providerConfig),
                ManagedDeploymentProfileCatalog.generationBaseUrl(providerConfig),
                ManagedDeploymentProfileCatalog.generationApiKeySecretRef(providerConfig),
                ManagedDeploymentProfileCatalog.generationDeploymentName(providerConfig),
                ManagedDeploymentProfileCatalog.generationApiVersion(providerConfig)
            )
        );
        addIfPresent(probes, probeInferenceEmbeddingEndpoint(deploymentId, providerConfig));

        ManagedVectorSummary managedVectorSummary = summarizeManagedVectorProvisioning(providerConfig, entityConfig);
        String vectorProvisioningMode = ManagedDeploymentProfileCatalog.resolveVectorProvisioningMode(providerConfig);

        return new DeploymentProviderConnectivitySummary(
            deploymentId,
            deploymentName,
            ManagedDeploymentProfileCatalog.resolveLlmProvider(providerConfig),
            ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig),
            vectorStrategy,
            vectorProvisioningMode,
            managedVectorSummary.enabled(),
            managedVectorSummary.mode(),
            List.copyOf(managedVectorSummary.targets()),
            managedVectorSummary.message(),
            List.copyOf(probes),
            summarize(probes),
            List.copyOf(effectiveSecretResolutions)
        );
    }

    private DeploymentProviderConnectivityProbeSummary probePinecone(String deploymentId, JsonNode providerConfig) {
        DeploymentProviderSecretResolutionService.ResolvedSecretValue resolution =
            deploymentProviderSecretResolutionService.resolve(deploymentId, "PINECONE_API_KEY", null);
        String apiKey = resolution.value();
        if (!StringUtils.hasText(apiKey)) {
            return new DeploymentProviderConnectivityProbeSummary(
                "pinecone_control_plane",
                "Pinecone control plane",
                "BLOCKED",
                "https://api.pinecone.io/indexes",
                resolution.summary().diagnosticMessage()
            );
        }
        try {
            pineconeControlPlaneClient.verifyControlPlaneAccess(apiKey);
            return new DeploymentProviderConnectivityProbeSummary(
                "pinecone_control_plane",
                "Pinecone control plane",
                "READY",
                PineconeControlPlaneClient.API_BASE_URL + "/indexes",
                ManagedDeploymentProfileCatalog.pineconePlatformManaged(providerConfig)
                    ? "Pinecone control-plane access is ready for platform-managed serverless index provisioning."
                    : "Pinecone control-plane access is reachable for the configured project/API key."
            );
        } catch (RuntimeException ex) {
            return new DeploymentProviderConnectivityProbeSummary(
                "pinecone_control_plane",
                "Pinecone control plane",
                "FAILED",
                PineconeControlPlaneClient.API_BASE_URL + "/indexes",
                "Pinecone control-plane probe failed: " + ex.getMessage()
            );
        }
    }

    private DeploymentProviderConnectivityProbeSummary probeQdrantCloud(JsonNode providerConfig) {
        String managementApiKey = platformSecretService.resolveSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY");
        if (!StringUtils.hasText(managementApiKey)) {
            return new DeploymentProviderConnectivityProbeSummary(
                "qdrant_cloud_control_plane",
                "Qdrant Cloud control plane",
                "BLOCKED",
                "https://api.cloud.qdrant.io",
                "QDRANT_CLOUD_MANAGEMENT_API_KEY is missing, so the platform cannot verify Qdrant Cloud provisioning."
            );
        }
        String regionId = ManagedDeploymentProfileCatalog.qdrantCloudRegionId(providerConfig);
        if (!StringUtils.hasText(regionId)) {
            return new DeploymentProviderConnectivityProbeSummary(
                "qdrant_cloud_control_plane",
                "Qdrant Cloud control plane",
                "BLOCKED",
                "https://api.cloud.qdrant.io",
                "qdrantCloudRegionId is required before the platform can verify Qdrant Cloud provisioning."
            );
        }
        String providerId = ManagedDeploymentProfileCatalog.qdrantCloudProviderId(providerConfig);
        try {
            QdrantCloudControlPlaneClient.QdrantCloudAccountResolution account = qdrantCloudControlPlaneClient.resolveAccount(
                ManagedDeploymentProfileCatalog.qdrantCloudAccountId(providerConfig),
                managementApiKey
            );
            QdrantCloudControlPlaneClient.QdrantCloudRegionSummary region =
                qdrantCloudControlPlaneClient.requireRegion(providerId, regionId);
            QdrantCloudControlPlaneClient.QdrantCloudPackageSummary pkg = qdrantCloudControlPlaneClient.resolvePackage(
                account.accountId(),
                managementApiKey,
                providerId,
                region.id(),
                ManagedDeploymentProfileCatalog.qdrantCloudPackageId(providerConfig)
            );
            return new DeploymentProviderConnectivityProbeSummary(
                "qdrant_cloud_control_plane",
                "Qdrant Cloud control plane",
                "READY",
                "https://api.cloud.qdrant.io",
                "Qdrant Cloud management access resolved account " + account.accountName()
                    + " and package " + pkg.name() + " for " + providerId + "/" + region.id() + "."
            );
        } catch (RailwayProvisioningConfigurationException ex) {
            return new DeploymentProviderConnectivityProbeSummary(
                "qdrant_cloud_control_plane",
                "Qdrant Cloud control plane",
                "BLOCKED",
                "https://api.cloud.qdrant.io",
                ex.getMessage()
            );
        } catch (RuntimeException ex) {
            return new DeploymentProviderConnectivityProbeSummary(
                "qdrant_cloud_control_plane",
                "Qdrant Cloud control plane",
                "FAILED",
                "https://api.cloud.qdrant.io",
                "Qdrant Cloud control-plane probe failed: " + ex.getMessage()
            );
        }
    }

    private DeploymentProviderConnectivityProbeSummary probeQdrant(String deploymentId, JsonNode providerConfig) {
        String host = ManagedDeploymentProfileCatalog.qdrantHost(providerConfig);
        if (!StringUtils.hasText(host)) {
            return new DeploymentProviderConnectivityProbeSummary(
                "qdrant_collections_api",
                "Qdrant collections API",
                "BLOCKED",
                "",
                "qdrantHost is missing, so the platform cannot verify Qdrant connectivity."
            );
        }
        String runtimeSecretName = ManagedDeploymentProfileCatalog.qdrantRuntimeApiKeySecretName(providerConfig);
        DeploymentProviderSecretResolutionService.ResolvedSecretValue resolution =
            deploymentProviderSecretResolutionService.resolve(deploymentId, "QDRANT_API_KEY", runtimeSecretName);
        if (overrideRequiredButMissing(resolution.summary())) {
            return new DeploymentProviderConnectivityProbeSummary(
                "qdrant_collections_api",
                "Qdrant collections API",
                "BLOCKED",
                buildQdrantBaseUrl(providerConfig) + "/collections",
                resolution.summary().diagnosticMessage()
            );
        }
        String apiKey = resolution.value();
        String endpoint = buildQdrantBaseUrl(providerConfig) + "/collections";
        return sendProbe(
            "qdrant_collections_api",
            "Qdrant collections API",
            endpoint,
            request -> {
                if (StringUtils.hasText(apiKey)) {
                    request.header("api-key", apiKey);
                }
            },
            false
        );
    }

    private DeploymentProviderConnectivityProbeSummary probeWeaviate(String deploymentId, JsonNode providerConfig) {
        String host = ManagedDeploymentProfileCatalog.weaviateHost(providerConfig);
        if (!StringUtils.hasText(host)) {
            return new DeploymentProviderConnectivityProbeSummary(
                "weaviate_ready_api",
                "Weaviate readiness API",
                "BLOCKED",
                "",
                "weaviateHost is missing, so the platform cannot verify Weaviate connectivity."
            );
        }
        String endpoint = buildWeaviateBaseUrl(providerConfig) + "/v1/.well-known/ready";
        DeploymentProviderSecretResolutionService.ResolvedSecretValue resolution =
            deploymentProviderSecretResolutionService.resolve(deploymentId, "WEAVIATE_API_KEY", null);
        if (overrideRequiredButMissing(resolution.summary())) {
            return new DeploymentProviderConnectivityProbeSummary(
                "weaviate_ready_api",
                "Weaviate readiness API",
                "BLOCKED",
                endpoint,
                resolution.summary().diagnosticMessage()
            );
        }
        String apiKey = resolution.value();
        return sendProbe(
            "weaviate_ready_api",
            "Weaviate readiness API",
            endpoint,
            request -> {
                if (StringUtils.hasText(apiKey)) {
                    request.header("Authorization", "Bearer " + apiKey);
                }
            },
            false
        );
    }

    private DeploymentProviderConnectivityProbeSummary probeZillizCloud(JsonNode providerConfig) {
        String apiKey = platformSecretService.resolveSecret("ZILLIZ_CLOUD_API_KEY");
        if (!StringUtils.hasText(apiKey)) {
            return new DeploymentProviderConnectivityProbeSummary(
                "zilliz_cloud_control_plane",
                "Zilliz Cloud control plane",
                "BLOCKED",
                ZillizCloudControlPlaneClient.API_BASE_URL,
                "ZILLIZ_CLOUD_API_KEY is missing, so the platform cannot verify Zilliz Cloud provisioning."
            );
        }
        String regionId = ManagedDeploymentProfileCatalog.zillizCloudRegionId(providerConfig);
        if (!StringUtils.hasText(regionId)) {
            return new DeploymentProviderConnectivityProbeSummary(
                "zilliz_cloud_control_plane",
                "Zilliz Cloud control plane",
                "BLOCKED",
                ZillizCloudControlPlaneClient.API_BASE_URL,
                "zillizCloudRegionId is required before the platform can verify Zilliz Cloud provisioning."
            );
        }
        try {
            zillizCloudControlPlaneClient.verifyControlPlaneAccess(apiKey);
            ZillizCloudControlPlaneClient.ZillizProjectResolution project = zillizCloudControlPlaneClient.resolveProject(
                ManagedDeploymentProfileCatalog.zillizCloudProjectId(providerConfig),
                regionId,
                apiKey
            );
            return new DeploymentProviderConnectivityProbeSummary(
                "zilliz_cloud_control_plane",
                "Zilliz Cloud control plane",
                "READY",
                ZillizCloudControlPlaneClient.API_BASE_URL + "/v2/clusters",
                "Zilliz Cloud management access resolved project " + project.projectName()
                    + " in region " + regionId + "."
            );
        } catch (RailwayProvisioningConfigurationException ex) {
            return new DeploymentProviderConnectivityProbeSummary(
                "zilliz_cloud_control_plane",
                "Zilliz Cloud control plane",
                "BLOCKED",
                ZillizCloudControlPlaneClient.API_BASE_URL + "/v2/clusters",
                ex.getMessage()
            );
        } catch (RuntimeException ex) {
            return new DeploymentProviderConnectivityProbeSummary(
                "zilliz_cloud_control_plane",
                "Zilliz Cloud control plane",
                "FAILED",
                ZillizCloudControlPlaneClient.API_BASE_URL + "/v2/clusters",
                "Zilliz Cloud control-plane probe failed: " + ex.getMessage()
            );
        }
    }

    private DeploymentProviderConnectivityProbeSummary probeInferenceLlmEndpoint(String deploymentId,
                                                                                 JsonNode providerConfig,
                                                                                 String purpose,
                                                                                 String explicitProvider,
                                                                                 String endpointProfile,
                                                                                 String explicitBaseUrl,
                                                                                 String apiKeySecretRef,
                                                                                 String deploymentName,
                                                                                 String apiVersion) {
        String provider = StringUtils.hasText(explicitProvider)
            ? explicitProvider
            : ManagedDeploymentProfileCatalog.resolveLlmProvider(providerConfig);
        if (!StringUtils.hasText(provider)) {
            return null;
        }
        String normalizedProvider = provider.trim().toLowerCase();
        if ("onnx".equals(normalizedProvider)) {
            return hasExplicitInferenceLlmEndpointConfiguration(
                endpointProfile,
                explicitBaseUrl,
                apiKeySecretRef,
                deploymentName,
                apiVersion
            )
                ? skippedInferenceProbe(purpose, "ONNX is local to the runtime and does not require an external " + purpose + " endpoint probe.")
                : null;
        }
        String endpoint = switch (normalizedProvider) {
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_OPENAI -> fallback(explicitBaseUrl, ManagedDeploymentProfileCatalog.openAiBaseUrl(providerConfig));
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_AZURE -> fallback(explicitBaseUrl, ManagedDeploymentProfileCatalog.azureEndpoint(providerConfig));
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_ANTHROPIC -> fallback(explicitBaseUrl, ManagedDeploymentProfileCatalog.anthropicBaseUrl(providerConfig));
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_COHERE -> fallback(explicitBaseUrl, ManagedDeploymentProfileCatalog.cohereBaseUrl(providerConfig));
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_GEMINI -> fallback(explicitBaseUrl, ManagedDeploymentProfileCatalog.geminiBaseUrl(providerConfig));
            default -> "";
        };
        if (!StringUtils.hasText(endpoint)) {
            if (!hasExplicitInferenceLlmEndpointConfiguration(
                endpointProfile,
                explicitBaseUrl,
                apiKeySecretRef,
                deploymentName,
                apiVersion
            )) {
                return null;
            }
            return new DeploymentProviderConnectivityProbeSummary(
                purpose + "_inference_endpoint",
                titleCase(purpose) + " inference endpoint",
                "BLOCKED",
                "",
                titleCase(purpose) + " inference provider '" + normalizedProvider + "' is selected, but no reachable base URL is configured."
            );
        }
        String secretPurpose = ManagedDeploymentProfileCatalog.secretNameForLlmProvider(normalizedProvider);
        String apiKey = null;
        if (StringUtils.hasText(secretPurpose)) {
            DeploymentProviderSecretResolutionService.ResolvedSecretValue resolution =
                deploymentProviderSecretResolutionService.resolve(deploymentId, secretPurpose, trimToNull(apiKeySecretRef));
            if (missingResolvedSecret(resolution.summary())) {
                return new DeploymentProviderConnectivityProbeSummary(
                    purpose + "_inference_endpoint",
                    titleCase(purpose) + " inference endpoint",
                    "BLOCKED",
                    endpoint,
                    resolution.summary().diagnosticMessage()
                );
            }
            apiKey = resolution.value();
        }
        return probeLlmEndpointWithAuth(
            purpose,
            normalizedProvider,
            endpoint,
            providerConfig,
            apiKey,
            deploymentName,
            apiVersion
        );
    }

    private DeploymentProviderConnectivityProbeSummary probeLlmEndpointWithAuth(String purpose,
                                                                                String provider,
                                                                                String endpoint,
                                                                                JsonNode providerConfig,
                                                                                String apiKey,
                                                                                String deploymentName,
                                                                                String apiVersion) {
        String key = purpose + "_inference_endpoint";
        String label = titleCase(purpose) + " inference endpoint";
        return switch (provider) {
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_OPENAI -> sendJsonPostProbe(
                key,
                label,
                buildOpenAiChatProbeEndpoint(endpoint),
                buildOpenAiChatProbeEndpoint(endpoint),
                objectNode("""
                    {
                      "model": "%s",
                      "messages": [
                        {
                          "role": "user",
                          "content": "%s"
                        }
                      ],
                      "max_completion_tokens": 8,
                      "temperature": 0
                    }
                    """.formatted(
                    resolveLlmProbeModel(purpose, provider, providerConfig),
                    EMBEDDING_SMOKE_TEXT
                )),
                request -> request.header("Authorization", "Bearer " + apiKey),
                this::isOpenAiChatResponse,
                llmProbeTimeout(purpose, provider, providerConfig)
            );
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_AZURE -> {
                String requestEndpoint = buildAzureChatProbeEndpoint(
                    endpoint,
                    deploymentName,
                    apiVersion
                );
                JsonNode requestBody = endpoint.contains("/openai/v1")
                    ? objectNode("""
                        {
                          "model": "%s",
                          "messages": [
                            {
                              "role": "user",
                              "content": "%s"
                            }
                          ],
                          "max_tokens": 8,
                          "temperature": 0
                        }
                        """.formatted(
                        firstNonBlank(resolveLlmProbeModel(purpose, provider, providerConfig), deploymentName),
                        EMBEDDING_SMOKE_TEXT
                    ))
                    : objectNode("""
                        {
                          "messages": [
                            {
                              "role": "user",
                              "content": "%s"
                            }
                          ],
                          "max_tokens": 8,
                          "temperature": 0
                        }
                        """.formatted(EMBEDDING_SMOKE_TEXT));
                yield sendJsonPostProbe(
                    key,
                    label,
                    requestEndpoint,
                    requestEndpoint,
                    requestBody,
                    request -> request.header("api-key", apiKey),
                    this::isOpenAiChatResponse,
                    llmProbeTimeout(purpose, provider, providerConfig)
                );
            }
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_ANTHROPIC -> sendJsonPostProbe(
                key,
                label,
                buildAnthropicChatProbeEndpoint(endpoint),
                buildAnthropicChatProbeEndpoint(endpoint),
                objectNode("""
                    {
                      "model": "%s",
                      "max_tokens": 8,
                      "temperature": 0,
                      "messages": [
                        {
                          "role": "user",
                          "content": "%s"
                        }
                      ]
                    }
                    """.formatted(
                    resolveLlmProbeModel(purpose, provider, providerConfig),
                    EMBEDDING_SMOKE_TEXT
                )),
                request -> {
                    request.header("x-api-key", apiKey);
                    request.header("anthropic-version", "2023-06-01");
                },
                this::isAnthropicChatResponse,
                llmProbeTimeout(purpose, provider, providerConfig)
            );
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_COHERE -> sendJsonPostProbe(
                key,
                label,
                buildCohereChatProbeEndpoint(endpoint),
                buildCohereChatProbeEndpoint(endpoint),
                objectNode("""
                    {
                      "model": "%s",
                      "message": "%s",
                      "max_tokens": 8,
                      "temperature": 0
                    }
                    """.formatted(
                    resolveLlmProbeModel(purpose, provider, providerConfig),
                    EMBEDDING_SMOKE_TEXT
                )),
                request -> request.header("Authorization", "Bearer " + apiKey),
                this::isCohereChatResponse,
                llmProbeTimeout(purpose, provider, providerConfig)
            );
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_GEMINI -> {
                String displayEndpoint = buildGeminiChatProbeEndpoint(
                    endpoint,
                    resolveLlmProbeModel(purpose, provider, providerConfig),
                    null
                );
                String requestEndpoint = buildGeminiChatProbeEndpoint(
                    endpoint,
                    resolveLlmProbeModel(purpose, provider, providerConfig),
                    apiKey
                );
                yield sendJsonPostProbe(
                    key,
                    label,
                    displayEndpoint,
                    requestEndpoint,
                    objectNode("""
                        {
                          "contents": [
                            {
                              "role": "user",
                              "parts": [
                                {
                                  "text": "%s"
                                }
                              ]
                            }
                          ],
                          "generationConfig": {
                            "maxOutputTokens": 8,
                            "temperature": 0
                          }
                        }
                        """.formatted(EMBEDDING_SMOKE_TEXT)),
                    request -> { },
                    this::isGeminiChatResponse,
                    llmProbeTimeout(purpose, provider, providerConfig)
                );
            }
            default -> new DeploymentProviderConnectivityProbeSummary(
                key,
                label,
                "SKIPPED",
                endpoint,
                label + " provider '" + provider + "' does not expose a platform probe in this slice."
            );
        };
    }

    private DeploymentProviderConnectivityProbeSummary probeInferenceEmbeddingEndpoint(String deploymentId,
                                                                                       JsonNode providerConfig) {
        String provider = ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig);
        String endpointProfile = ManagedDeploymentProfileCatalog.embeddingEndpointProfile(providerConfig);
        String serviceMode = ManagedDeploymentProfileCatalog.embeddingServiceMode(providerConfig);
        if (!StringUtils.hasText(provider)) {
            return null;
        }
        String normalizedProvider = provider.trim().toLowerCase();
        if (ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_ONNX.equals(normalizedProvider)) {
            return StringUtils.hasText(endpointProfile)
                ? skippedInferenceProbe("embedding", "ONNX embeddings are bundled in the runtime and do not require an external endpoint probe.")
                : null;
        }
        if (ManagedDeploymentProfileCatalog.INFERENCE_SERVICE_MODE_DEPLOYMENT_DEDICATED_SERVICE.equals(serviceMode)) {
            return skippedInferenceProbe(
                "embedding",
                "Dedicated embedding worker will be provisioned during apply and does not require a pre-apply external endpoint probe."
            );
        }
        String endpoint = switch (normalizedProvider) {
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_OPENAI -> firstNonBlank(
                ManagedDeploymentProfileCatalog.openAiEmbeddingBaseUrl(providerConfig),
                ManagedDeploymentProfileCatalog.embeddingBaseUrl(providerConfig),
                ManagedDeploymentProfileCatalog.openAiBaseUrl(providerConfig)
            );
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_AZURE -> firstNonBlank(
                ManagedDeploymentProfileCatalog.azureEmbeddingEndpoint(providerConfig),
                ManagedDeploymentProfileCatalog.embeddingBaseUrl(providerConfig),
                ManagedDeploymentProfileCatalog.azureEndpoint(providerConfig)
            );
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_COHERE -> ManagedDeploymentProfileCatalog.cohereBaseUrl(providerConfig);
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_GEMINI -> ManagedDeploymentProfileCatalog.geminiBaseUrl(providerConfig);
            default -> "";
        };
        if (!StringUtils.hasText(endpoint)) {
            if (!hasExplicitInferenceEmbeddingEndpointConfiguration(
                endpointProfile,
                ManagedDeploymentProfileCatalog.embeddingBaseUrl(providerConfig),
                ManagedDeploymentProfileCatalog.embeddingApiKeySecretRef(providerConfig)
            )) {
                return null;
            }
            return new DeploymentProviderConnectivityProbeSummary(
                "embedding_inference_endpoint",
                "Embedding inference endpoint",
                "BLOCKED",
                "",
                "Embedding provider '" + normalizedProvider + "' is selected, but no reachable base URL is configured."
            );
        }
        String apiKey = null;
        String secretPurpose = ManagedDeploymentProfileCatalog.secretNameForEmbeddingProvider(normalizedProvider);
        if (StringUtils.hasText(secretPurpose)) {
            DeploymentProviderSecretResolutionService.ResolvedSecretValue resolution =
                deploymentProviderSecretResolutionService.resolve(
                    deploymentId,
                    secretPurpose,
                    trimToNull(ManagedDeploymentProfileCatalog.embeddingApiKeySecretRef(providerConfig))
                );
            if (missingResolvedSecret(resolution.summary())) {
                return new DeploymentProviderConnectivityProbeSummary(
                    "embedding_inference_endpoint",
                    "Embedding inference endpoint",
                    "BLOCKED",
                    endpoint,
                    resolution.summary().diagnosticMessage()
                );
            }
            apiKey = resolution.value();
        }
        return probeEmbeddingEndpointWithAuth(normalizedProvider, endpoint, providerConfig, apiKey);
    }

    private DeploymentProviderConnectivityProbeSummary probeEmbeddingEndpointWithAuth(String provider,
                                                                                      String endpoint,
                                                                                      JsonNode providerConfig,
                                                                                      String apiKey) {
        return switch (provider) {
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_OPENAI -> sendJsonPostProbe(
                "embedding_inference_endpoint",
                "Embedding inference endpoint",
                buildOpenAiEmbeddingProbeEndpoint(endpoint),
                buildOpenAiEmbeddingProbeEndpoint(endpoint),
                objectNode("""
                    {
                      "model": "%s",
                      "input": "%s"
                    }
                    """.formatted(
                    ManagedDeploymentProfileCatalog.openAiEmbeddingModel(providerConfig),
                    EMBEDDING_SMOKE_TEXT
                )),
                request -> request.header("Authorization", "Bearer " + apiKey),
                this::isOpenAiEmbeddingResponse
            );
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_AZURE -> {
                String requestEndpoint = buildAzureEmbeddingProbeEndpoint(
                    endpoint,
                    firstNonBlank(
                        ManagedDeploymentProfileCatalog.azureEmbeddingDeploymentName(providerConfig),
                        ManagedDeploymentProfileCatalog.embeddingDeploymentName(providerConfig),
                        ManagedDeploymentProfileCatalog.azureDeploymentName(providerConfig)
                    ),
                    firstNonBlank(
                        ManagedDeploymentProfileCatalog.azureEmbeddingApiVersion(providerConfig),
                        ManagedDeploymentProfileCatalog.embeddingApiVersion(providerConfig),
                        ManagedDeploymentProfileCatalog.azureApiVersion(providerConfig)
                    )
                );
                yield sendJsonPostProbe(
                    "embedding_inference_endpoint",
                    "Embedding inference endpoint",
                    requestEndpoint,
                    requestEndpoint,
                    objectNode("""
                        {
                          "input": ["%s"]
                        }
                        """.formatted(EMBEDDING_SMOKE_TEXT)),
                    request -> request.header("api-key", apiKey),
                    this::isOpenAiEmbeddingResponse
                );
            }
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_COHERE -> sendJsonPostProbe(
                "embedding_inference_endpoint",
                "Embedding inference endpoint",
                buildCohereEmbeddingProbeEndpoint(endpoint),
                buildCohereEmbeddingProbeEndpoint(endpoint),
                objectNode("""
                    {
                      "model": "%s",
                      "texts": ["%s"],
                      "input_type": "search_document"
                    }
                    """.formatted(
                    ManagedDeploymentProfileCatalog.cohereEmbeddingModel(providerConfig),
                    EMBEDDING_SMOKE_TEXT
                )),
                request -> request.header("Authorization", "Bearer " + apiKey),
                this::isCohereEmbeddingResponse
            );
            case ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_GEMINI -> {
                String displayEndpoint = buildGeminiEmbeddingProbeEndpoint(
                    endpoint,
                    ManagedDeploymentProfileCatalog.geminiEmbeddingModel(providerConfig),
                    null
                );
                String requestEndpoint = buildGeminiEmbeddingProbeEndpoint(
                    endpoint,
                    ManagedDeploymentProfileCatalog.geminiEmbeddingModel(providerConfig),
                    apiKey
                );
                yield sendJsonPostProbe(
                    "embedding_inference_endpoint",
                    "Embedding inference endpoint",
                    displayEndpoint,
                    requestEndpoint,
                    objectNode("""
                        {
                          "content": {
                            "parts": [
                              {
                                "text": "%s"
                              }
                            ]
                          }
                        }
                        """.formatted(EMBEDDING_SMOKE_TEXT)),
                    request -> { },
                    this::isGeminiEmbeddingResponse
                );
            }
            default -> new DeploymentProviderConnectivityProbeSummary(
                "embedding_inference_endpoint",
                "Embedding inference endpoint",
                "SKIPPED",
                endpoint,
                "Embedding provider '" + provider + "' does not expose a platform probe in this slice."
            );
        };
    }

    private DeploymentProviderConnectivityProbeSummary sendJsonPostProbe(String key,
                                                                         String label,
                                                                         String displayEndpoint,
                                                                         String requestEndpoint,
                                                                         JsonNode requestBody,
                                                                         RequestCustomizer customizer,
                                                                         ResponseBodyValidator validator) {
        return sendJsonPostProbe(
            key,
            label,
            displayEndpoint,
            requestEndpoint,
            requestBody,
            customizer,
            validator,
            DEFAULT_JSON_PROBE_TIMEOUT
        );
    }

    private DeploymentProviderConnectivityProbeSummary sendJsonPostProbe(String key,
                                                                         String label,
                                                                         String displayEndpoint,
                                                                         String requestEndpoint,
                                                                         JsonNode requestBody,
                                                                         RequestCustomizer customizer,
                                                                         ResponseBodyValidator validator,
                                                                         Duration timeout) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(requestEndpoint))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)));
            customizer.customize(builder);

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                return new DeploymentProviderConnectivityProbeSummary(
                    key,
                    label,
                    "FAILED",
                    displayEndpoint,
                    label + " responded with HTTP " + statusCode + "."
                );
            }
            JsonNode responseBody = objectMapper.readTree(blankToEmpty(response.body()));
            if (!validator.isValid(responseBody)) {
                return new DeploymentProviderConnectivityProbeSummary(
                    key,
                    label,
                    "FAILED",
                    displayEndpoint,
                    label + " returned an invalid embedding response payload."
                );
            }
            return new DeploymentProviderConnectivityProbeSummary(
                key,
                label,
                "READY",
                displayEndpoint,
                label + " accepted an authenticated probe."
            );
        } catch (Exception ex) {
            return new DeploymentProviderConnectivityProbeSummary(
                key,
                label,
                "FAILED",
                displayEndpoint,
                label + " probe failed: " + ex.getMessage()
            );
        }
    }

    private DeploymentProviderConnectivityProbeSummary sendProbe(String key,
                                                                 String label,
                                                                 String endpoint,
                                                                 RequestCustomizer customizer,
                                                                 boolean strictSuccessOnly) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET();
            customizer.customize(builder);

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            boolean reachable = strictSuccessOnly
                ? statusCode >= 200 && statusCode < 300
                : statusCode >= 200 && statusCode < 500;
            if (reachable) {
                return new DeploymentProviderConnectivityProbeSummary(
                    key,
                    label,
                    "READY",
                    endpoint,
                    label + " responded with HTTP " + statusCode + "."
                );
            }
            return new DeploymentProviderConnectivityProbeSummary(
                key,
                label,
                "FAILED",
                endpoint,
                label + " responded with HTTP " + statusCode + "."
            );
        } catch (Exception ex) {
            return new DeploymentProviderConnectivityProbeSummary(
                key,
                label,
                "FAILED",
                endpoint,
                label + " probe failed: " + ex.getMessage()
            );
        }
    }

    private String summarize(List<DeploymentProviderConnectivityProbeSummary> probes) {
        long ready = probes.stream().filter(item -> "READY".equals(item.status())).count();
        long blocked = probes.stream().filter(item -> "BLOCKED".equals(item.status())).count();
        long failed = probes.stream().filter(item -> "FAILED".equals(item.status())).count();
        long skipped = probes.stream().filter(item -> "SKIPPED".equals(item.status())).count();
        return ready + " ready, " + blocked + " blocked, " + failed + " failed, " + skipped + " skipped.";
    }

    private boolean overrideRequiredButMissing(DeploymentSecretResolutionSummary summary) {
        return summary != null
            && !summary.resolved()
            && "REQUIRE_OVERRIDE".equals(summary.bindingMode());
    }

    private boolean missingResolvedSecret(DeploymentSecretResolutionSummary summary) {
        return summary != null && !summary.resolved();
    }

    private void addIfPresent(List<DeploymentProviderConnectivityProbeSummary> probes,
                              DeploymentProviderConnectivityProbeSummary probe) {
        if (probe != null) {
            probes.add(probe);
        }
    }

    private boolean hasExplicitInferenceLlmEndpointConfiguration(String endpointProfile,
                                                                 String explicitBaseUrl,
                                                                 String apiKeySecretRef,
                                                                 String deploymentName,
                                                                 String apiVersion) {
        return StringUtils.hasText(endpointProfile)
            || StringUtils.hasText(explicitBaseUrl)
            || StringUtils.hasText(apiKeySecretRef)
            || StringUtils.hasText(deploymentName)
            || StringUtils.hasText(apiVersion);
    }

    private boolean hasExplicitInferenceEmbeddingEndpointConfiguration(String endpointProfile,
                                                                       String explicitBaseUrl,
                                                                       String apiKeySecretRef) {
        return StringUtils.hasText(endpointProfile)
            || StringUtils.hasText(explicitBaseUrl)
            || StringUtils.hasText(apiKeySecretRef);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private List<DeploymentSecretResolutionSummary> effectiveSecretResolutions(String deploymentId,
                                                                               JsonNode providerConfig) {
        List<DeploymentSecretResolutionSummary> summaries = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();
        addResolvedSecretSummary(
            summaries,
            dedupe,
            deploymentId,
            ManagedDeploymentProfileCatalog.secretNameForLlmProvider(ManagedDeploymentProfileCatalog.resolveLlmProvider(providerConfig)),
            null
        );
        addResolvedSecretSummary(
            summaries,
            dedupe,
            deploymentId,
            ManagedDeploymentProfileCatalog.secretNameForLlmProvider(ManagedDeploymentProfileCatalog.orchestrationLlmProvider(providerConfig)),
            ManagedDeploymentProfileCatalog.orchestrationApiKeySecretRef(providerConfig)
        );
        addResolvedSecretSummary(
            summaries,
            dedupe,
            deploymentId,
            ManagedDeploymentProfileCatalog.secretNameForLlmProvider(ManagedDeploymentProfileCatalog.generationLlmProvider(providerConfig)),
            ManagedDeploymentProfileCatalog.generationApiKeySecretRef(providerConfig)
        );
        addResolvedSecretSummary(
            summaries,
            dedupe,
            deploymentId,
            ManagedDeploymentProfileCatalog.secretNameForEmbeddingProvider(
                ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig)
            ),
            ManagedDeploymentProfileCatalog.embeddingApiKeySecretRef(providerConfig)
        );
        String vectorStrategy = ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig);
        switch (vectorStrategy) {
            case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_PINECONE -> addResolvedSecretSummary(summaries, dedupe, deploymentId, "PINECONE_API_KEY", null);
            case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT -> addResolvedSecretSummary(
                summaries,
                dedupe,
                deploymentId,
                "QDRANT_API_KEY",
                ManagedDeploymentProfileCatalog.qdrantRuntimeApiKeySecretName(providerConfig)
            );
            case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_WEAVIATE -> addResolvedSecretSummary(summaries, dedupe, deploymentId, "WEAVIATE_API_KEY", null);
            case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_MILVUS -> addResolvedSecretSummary(summaries, dedupe, deploymentId, "MILVUS_RUNTIME_CREDENTIALS", null);
            default -> {
            }
        }
        return List.copyOf(summaries);
    }

    private void addResolvedSecretSummary(List<DeploymentSecretResolutionSummary> summaries,
                                          Set<String> dedupe,
                                          String deploymentId,
                                          String secretPurpose,
                                          String managedSecretName) {
        String normalizedPurpose = trimToNull(secretPurpose);
        if (normalizedPurpose == null) {
            return;
        }
        String key = normalizedPurpose + "|" + blankToEmpty(trimToNull(managedSecretName));
        if (!dedupe.add(key)) {
            return;
        }
        summaries.add(deploymentProviderSecretResolutionService.summarize(deploymentId, normalizedPurpose, trimToNull(managedSecretName)));
    }

    private String buildQdrantBaseUrl(JsonNode providerConfig) {
        String rawHost = ManagedDeploymentProfileCatalog.qdrantHost(providerConfig);
        if (rawHost.startsWith("https://") || rawHost.startsWith("http://")) {
            return trimTrailingSlash(rawHost);
        }
        int port = ManagedDeploymentProfileCatalog.qdrantPort(providerConfig);
        return trimTrailingSlash((port > 0 ? "http://" + rawHost + ":" + port : "http://" + rawHost));
    }

    private String buildWeaviateBaseUrl(JsonNode providerConfig) {
        String scheme = ManagedDeploymentProfileCatalog.weaviateScheme(providerConfig);
        String host = ManagedDeploymentProfileCatalog.weaviateHost(providerConfig);
        int port = ManagedDeploymentProfileCatalog.weaviatePort(providerConfig);
        return trimTrailingSlash(scheme + "://" + host + ":" + port);
    }

    private JsonNode objectNode(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build connectivity probe request JSON.", ex);
        }
    }

    private String buildOpenAiEmbeddingProbeEndpoint(String baseUrl) {
        String normalized = trimTrailingSlash(baseUrl.trim());
        if (normalized.endsWith("/embeddings")) {
            return normalized;
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/embeddings";
        }
        return normalized + "/v1/embeddings";
    }

    private String buildOpenAiChatProbeEndpoint(String baseUrl) {
        String normalized = trimTrailingSlash(baseUrl.trim());
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/chat/completions";
        }
        return normalized + "/v1/chat/completions";
    }

    private String buildAzureEmbeddingProbeEndpoint(String endpoint, String deploymentName, String apiVersion) {
        String normalized = trimTrailingSlash(endpoint.trim());
        String resolvedApiVersion = StringUtils.hasText(apiVersion) ? apiVersion.trim() : "2024-02-15-preview";
        if (normalized.contains("/models/embeddings")) {
            return normalized.contains("?")
                ? normalized
                : normalized + "?api-version=" + resolvedApiVersion;
        }
        if (normalized.contains("/models")) {
            return normalized + "/embeddings?api-version=" + resolvedApiVersion;
        }
        if (!StringUtils.hasText(deploymentName)) {
            throw new IllegalStateException("Azure embedding provider requires azureEmbeddingDeploymentName.");
        }
        return normalized + "/openai/deployments/" + deploymentName.trim() + "/embeddings?api-version=" + resolvedApiVersion;
    }

    private String buildAzureChatProbeEndpoint(String endpoint, String deploymentName, String apiVersion) {
        String normalized = trimTrailingSlash(endpoint.trim());
        String resolvedApiVersion = StringUtils.hasText(apiVersion) ? apiVersion.trim() : "2024-02-15-preview";
        if (normalized.contains("/models/chat/completions")) {
            return normalized.contains("?")
                ? normalized
                : normalized + "?api-version=" + resolvedApiVersion;
        }
        if (normalized.contains("/openai/v1")) {
            return normalized + "/chat/completions";
        }
        if (normalized.contains("/models")) {
            return normalized + "/chat/completions?api-version=" + resolvedApiVersion;
        }
        if (!StringUtils.hasText(deploymentName)) {
            throw new IllegalStateException("Azure inference provider requires azureDeploymentName.");
        }
        return normalized + "/openai/deployments/" + deploymentName.trim() + "/chat/completions?api-version=" + resolvedApiVersion;
    }

    private String buildCohereEmbeddingProbeEndpoint(String baseUrl) {
        String normalized = trimTrailingSlash(baseUrl.trim());
        if (normalized.endsWith("/embed")) {
            return normalized;
        }
        return normalized + "/embed";
    }

    private String buildAnthropicChatProbeEndpoint(String baseUrl) {
        String normalized = trimTrailingSlash(baseUrl.trim());
        if (normalized.endsWith("/messages")) {
            return normalized;
        }
        return normalized + "/messages";
    }

    private String buildCohereChatProbeEndpoint(String baseUrl) {
        String normalized = trimTrailingSlash(baseUrl.trim());
        if (normalized.endsWith("/chat")) {
            return normalized;
        }
        return normalized + "/chat";
    }

    private String buildGeminiEmbeddingProbeEndpoint(String baseUrl, String model, String apiKey) {
        String normalized = trimTrailingSlash(baseUrl.trim());
        String suffix = "/models/" + model.trim() + ":embedContent";
        if (!normalized.endsWith(suffix)) {
            normalized = normalized + suffix;
        }
        if (!StringUtils.hasText(apiKey)) {
            return normalized;
        }
        return normalized + "?key=" + apiKey.trim();
    }

    private String buildGeminiChatProbeEndpoint(String baseUrl, String model, String apiKey) {
        String normalized = trimTrailingSlash(baseUrl.trim());
        String suffix = "/models/" + model.trim() + ":generateContent";
        if (!normalized.endsWith(suffix)) {
            normalized = normalized + suffix;
        }
        if (!StringUtils.hasText(apiKey)) {
            return normalized;
        }
        return normalized + "?key=" + apiKey.trim();
    }

    private boolean isOpenAiChatResponse(JsonNode responseBody) {
        return responseBody.path("choices").isArray()
            && responseBody.path("choices").size() > 0
            && responseBody.path("choices").get(0).path("message").isObject();
    }

    private boolean isAnthropicChatResponse(JsonNode responseBody) {
        return responseBody.path("content").isArray()
            && responseBody.path("content").size() > 0
            && responseBody.path("content").get(0).path("text").isTextual();
    }

    private boolean isCohereChatResponse(JsonNode responseBody) {
        return responseBody.path("text").isTextual();
    }

    private boolean isGeminiChatResponse(JsonNode responseBody) {
        return responseBody.path("candidates").isArray()
            && responseBody.path("candidates").size() > 0
            && responseBody.path("candidates").get(0).path("content").path("parts").isArray()
            && responseBody.path("candidates").get(0).path("content").path("parts").size() > 0
            && responseBody.path("candidates").get(0).path("content").path("parts").get(0).path("text").isTextual();
    }

    private Duration llmProbeTimeout(String purpose, String provider, JsonNode providerConfig) {
        int configuredSeconds = switch (purpose) {
            case "orchestration" -> ManagedDeploymentProfileCatalog.orchestrationTimeout(providerConfig);
            case "generation" -> ManagedDeploymentProfileCatalog.generationTimeout(providerConfig);
            default -> 0;
        };
        if (configuredSeconds <= 0) {
            configuredSeconds = switch (provider) {
                case ManagedDeploymentProfileCatalog.LLM_PROVIDER_OPENAI,
                    ManagedDeploymentProfileCatalog.LLM_PROVIDER_AZURE -> ManagedDeploymentProfileCatalog.openAiTimeout(providerConfig);
                case ManagedDeploymentProfileCatalog.LLM_PROVIDER_ANTHROPIC -> ManagedDeploymentProfileCatalog.anthropicTimeout(providerConfig);
                case ManagedDeploymentProfileCatalog.LLM_PROVIDER_COHERE -> ManagedDeploymentProfileCatalog.cohereTimeout(providerConfig);
                case ManagedDeploymentProfileCatalog.LLM_PROVIDER_GEMINI -> ManagedDeploymentProfileCatalog.geminiTimeout(providerConfig);
                default -> 0;
            };
        }
        Duration configured = configuredSeconds > 0
            ? Duration.ofSeconds(configuredSeconds)
            : Duration.ZERO;
        return configured.compareTo(MIN_LLM_PROBE_TIMEOUT) >= 0
            ? configured
            : MIN_LLM_PROBE_TIMEOUT;
    }

    private String resolveLlmProbeModel(String purpose, String provider, JsonNode providerConfig) {
        String purposeOverride = switch (purpose) {
            case "orchestration" -> ManagedDeploymentProfileCatalog.orchestrationModel(providerConfig);
            case "generation" -> ManagedDeploymentProfileCatalog.generationModel(providerConfig);
            default -> "";
        };
        if (StringUtils.hasText(purposeOverride)) {
            return purposeOverride.trim();
        }
        return switch (provider) {
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_OPENAI,
                ManagedDeploymentProfileCatalog.LLM_PROVIDER_AZURE -> ManagedDeploymentProfileCatalog.openAiModel(providerConfig);
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_ANTHROPIC -> ManagedDeploymentProfileCatalog.anthropicModel(providerConfig);
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_COHERE -> ManagedDeploymentProfileCatalog.cohereModel(providerConfig);
            case ManagedDeploymentProfileCatalog.LLM_PROVIDER_GEMINI -> ManagedDeploymentProfileCatalog.geminiModel(providerConfig);
            default -> "";
        };
    }

    private boolean isOpenAiEmbeddingResponse(JsonNode responseBody) {
        return responseBody.path("data").isArray()
            && responseBody.path("data").size() > 0
            && responseBody.path("data").get(0).path("embedding").isArray()
            && responseBody.path("data").get(0).path("embedding").size() > 0;
    }

    private boolean isCohereEmbeddingResponse(JsonNode responseBody) {
        return responseBody.path("embeddings").isArray()
            && responseBody.path("embeddings").size() > 0
            && responseBody.path("embeddings").get(0).isArray()
            && responseBody.path("embeddings").get(0).size() > 0;
    }

    private boolean isGeminiEmbeddingResponse(JsonNode responseBody) {
        return responseBody.path("embedding").path("values").isArray()
            && responseBody.path("embedding").path("values").size() > 0;
    }

    private String normalizeMilvusEndpoint(JsonNode providerConfig) {
        String host = ManagedDeploymentProfileCatalog.milvusHost(providerConfig);
        if (!StringUtils.hasText(host)) {
            return "";
        }
        return (ManagedDeploymentProfileCatalog.milvusSecure(providerConfig) ? "tls://" : "tcp://")
            + host + ":" + ManagedDeploymentProfileCatalog.milvusPort(providerConfig);
    }

    private ManagedVectorSummary summarizeManagedVectorProvisioning(JsonNode providerConfig,
                                                                   JsonNode entityConfig) {
        String vectorStrategy = ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig);
        if (ManagedDeploymentProfileCatalog.pineconePlatformManaged(providerConfig)) {
            String indexName = ManagedDeploymentProfileCatalog.pineconeIndexName(providerConfig);
            String region = ManagedDeploymentProfileCatalog.pineconeRegion(providerConfig);
            String cloud = ManagedDeploymentProfileCatalog.pineconeCloud(providerConfig);
            List<String> targets = List.of(
                (StringUtils.hasText(indexName) ? indexName : "index name not configured")
                    + " (" + cloud + "/" + (StringUtils.hasText(region) ? region : "region not configured") + ")"
            );
            return new ManagedVectorSummary(
                true,
                "MANAGED_SERVERLESS_INDEX",
                targets,
                StringUtils.hasText(indexName)
                    ? "Apply will create or reconcile the Pinecone serverless index, resolve its host, and bind runtime to the managed resource."
                    : "Platform-managed Pinecone serverless provisioning is enabled, but pineconeIndexName still needs review."
            );
        }
        if (ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT.equals(vectorStrategy)
            && ManagedDeploymentProfileCatalog.qdrantPlatformManaged(providerConfig)) {
            String providerId = ManagedDeploymentProfileCatalog.qdrantCloudProviderId(providerConfig);
            String regionId = ManagedDeploymentProfileCatalog.qdrantCloudRegionId(providerConfig);
            String clusterNameOverride = ManagedDeploymentProfileCatalog.qdrantCloudClusterNameOverride(providerConfig);
            List<String> targets = new ArrayList<>();
            targets.add((StringUtils.hasText(clusterNameOverride) ? clusterNameOverride : "deployment-managed cluster")
                + " (" + providerId + "/" + (StringUtils.hasText(regionId) ? regionId : "region not configured") + ")");
            List<String> entityTypes = resolveEntityTypes(entityConfig);
            if (!entityTypes.isEmpty()) {
                targets.addAll(entityTypes);
            }
            return new ManagedVectorSummary(
                true,
                "MANAGED_CLOUD_CLUSTER",
                targets,
                StringUtils.hasText(regionId)
                    ? "Apply will create or reconcile a Qdrant Cloud cluster, issue a deployment-scoped database key, and provision one collection per configured entity type."
                    : "Platform-managed Qdrant Cloud provisioning is enabled, but qdrantCloudRegionId still needs review."
            );
        }
        if (ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT.equals(vectorStrategy)
            && ManagedDeploymentProfileCatalog.qdrantManagedCollectionsEnabled(providerConfig)) {
            List<String> entityTypes = resolveEntityTypes(entityConfig);
            List<String> targets = entityTypes.isEmpty() ? List.of("No entity types configured") : entityTypes;
            String host = ManagedDeploymentProfileCatalog.qdrantHost(providerConfig);
            String suffix = StringUtils.hasText(host) ? " on " + host.trim() : "";
            return new ManagedVectorSummary(
                true,
                "MANAGED_COLLECTIONS",
                targets,
                entityTypes.isEmpty()
                    ? "Platform-managed Qdrant collections are enabled, but entity types still need review."
                    : "Apply will create or reconcile Qdrant collections for the configured entity types" + suffix + "."
            );
        }
        if (ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_MILVUS.equals(vectorStrategy)
            && ManagedDeploymentProfileCatalog.milvusPlatformManaged(providerConfig)) {
            String regionId = ManagedDeploymentProfileCatalog.zillizCloudRegionId(providerConfig);
            String projectId = ManagedDeploymentProfileCatalog.zillizCloudProjectId(providerConfig);
            String clusterPlan = ManagedDeploymentProfileCatalog.zillizCloudClusterPlan(providerConfig);
            String clusterName = ManagedDeploymentProfileCatalog.zillizCloudClusterNameOverride(providerConfig);
            List<String> targets = List.of(
                (StringUtils.hasText(clusterName) ? clusterName : "deployment-managed cluster")
                    + " (" + (StringUtils.hasText(projectId) ? projectId : "project not configured")
                    + " / " + (StringUtils.hasText(regionId) ? regionId : "region not configured")
                    + " / " + clusterPlan + ")"
            );
            return new ManagedVectorSummary(
                true,
                "MANAGED_ZILLIZ_CLOUD_CLUSTER",
                targets,
                StringUtils.hasText(projectId) && StringUtils.hasText(regionId)
                    ? "Apply will create or reconcile a Zilliz Cloud managed Milvus cluster and bind deployment-scoped runtime credentials automatically."
                    : "Platform-managed Zilliz Cloud provisioning is enabled, but zillizCloudProjectId and zillizCloudRegionId still need review."
            );
        }
        return new ManagedVectorSummary(
            false,
            "NONE",
            List.of(),
            "Platform-managed external vector provisioning is not enabled for this draft."
        );
    }

    private List<String> resolveEntityTypes(JsonNode entityConfig) {
        JsonNode aiEntities = entityConfig.path("ai-entities");
        if (!aiEntities.isObject()) {
            return List.of();
        }
        List<String> entityTypes = new ArrayList<>();
        aiEntities.fieldNames().forEachRemaining(field -> {
            if (StringUtils.hasText(field)) {
                entityTypes.add(field.trim());
            }
        });
        return entityTypes.stream().distinct().collect(Collectors.toList());
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse provider config JSON.", ex);
        }
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String fallback(String primary, String secondary) {
        return StringUtils.hasText(primary) ? primary.trim() : blankToEmpty(secondary).trim();
    }

    private DeploymentProviderConnectivityProbeSummary skippedInferenceProbe(String purpose, String message) {
        return new DeploymentProviderConnectivityProbeSummary(
            purpose + "_inference_endpoint",
            titleCase(purpose) + " inference endpoint",
            "SKIPPED",
            "",
            message
        );
    }

    private String titleCase(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase();
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    @FunctionalInterface
    private interface RequestCustomizer {
        void customize(HttpRequest.Builder request);
    }

    @FunctionalInterface
    private interface ResponseBodyValidator {
        boolean isValid(JsonNode responseBody);
    }

    private record ManagedVectorSummary(
        boolean enabled,
        String mode,
        List<String> targets,
        String message
    ) {
    }
}
