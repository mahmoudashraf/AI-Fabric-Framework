package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretResolutionService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DeploymentManagedVectorProvisioningService {

    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient;
    private final PineconeControlPlaneClient pineconeControlPlaneClient;
    private final ZillizCloudControlPlaneClient zillizCloudControlPlaneClient;
    private final DeploymentProviderSecretResolutionService deploymentProviderSecretResolutionService;
    @Autowired
    private ManagedResourceOwnershipService ownership;

    @Autowired
    public DeploymentManagedVectorProvisioningService(PlatformSecretService platformSecretService,
                                                      ObjectMapper objectMapper,
                                                      QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient,
                                                      PineconeControlPlaneClient pineconeControlPlaneClient,
                                                      ZillizCloudControlPlaneClient zillizCloudControlPlaneClient) {
        this(
            platformSecretService,
            objectMapper,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
            new DeploymentProviderSecretResolutionService(platformSecretService),
            qdrantCloudControlPlaneClient,
            pineconeControlPlaneClient,
            zillizCloudControlPlaneClient
        );
    }

    DeploymentManagedVectorProvisioningService(PlatformSecretService platformSecretService,
                                               ObjectMapper objectMapper,
                                               HttpClient httpClient) {
        this(
            platformSecretService,
            objectMapper,
            httpClient,
            new DeploymentProviderSecretResolutionService(platformSecretService),
            new QdrantCloudControlPlaneClient(objectMapper, httpClient),
            new PineconeControlPlaneClient(objectMapper, httpClient),
            new ZillizCloudControlPlaneClient(objectMapper, httpClient)
        );
    }

    DeploymentManagedVectorProvisioningService(PlatformSecretService platformSecretService,
                                               ObjectMapper objectMapper,
                                               HttpClient httpClient,
                                               QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient) {
        this(
            platformSecretService,
            objectMapper,
            httpClient,
            new DeploymentProviderSecretResolutionService(platformSecretService),
            qdrantCloudControlPlaneClient,
            new PineconeControlPlaneClient(objectMapper, httpClient),
            new ZillizCloudControlPlaneClient(objectMapper, httpClient)
        );
    }

    DeploymentManagedVectorProvisioningService(PlatformSecretService platformSecretService,
                                               ObjectMapper objectMapper,
                                               HttpClient httpClient,
                                               PineconeControlPlaneClient pineconeControlPlaneClient) {
        this(
            platformSecretService,
            objectMapper,
            httpClient,
            new DeploymentProviderSecretResolutionService(platformSecretService),
            new QdrantCloudControlPlaneClient(objectMapper, httpClient),
            pineconeControlPlaneClient,
            new ZillizCloudControlPlaneClient(objectMapper, httpClient)
        );
    }

    DeploymentManagedVectorProvisioningService(PlatformSecretService platformSecretService,
                                               ObjectMapper objectMapper,
                                               HttpClient httpClient,
                                               QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient,
                                               PineconeControlPlaneClient pineconeControlPlaneClient) {
        this(
            platformSecretService,
            objectMapper,
            httpClient,
            new DeploymentProviderSecretResolutionService(platformSecretService),
            qdrantCloudControlPlaneClient,
            pineconeControlPlaneClient,
            new ZillizCloudControlPlaneClient(objectMapper, httpClient)
        );
    }

    DeploymentManagedVectorProvisioningService(PlatformSecretService platformSecretService,
                                               ObjectMapper objectMapper,
                                               HttpClient httpClient,
                                               ZillizCloudControlPlaneClient zillizCloudControlPlaneClient) {
        this(
            platformSecretService,
            objectMapper,
            httpClient,
            new DeploymentProviderSecretResolutionService(platformSecretService),
            new QdrantCloudControlPlaneClient(objectMapper, httpClient),
            new PineconeControlPlaneClient(objectMapper, httpClient),
            zillizCloudControlPlaneClient
        );
    }

    DeploymentManagedVectorProvisioningService(PlatformSecretService platformSecretService,
                                               ObjectMapper objectMapper,
                                               HttpClient httpClient,
                                               QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient,
                                               PineconeControlPlaneClient pineconeControlPlaneClient,
                                               ZillizCloudControlPlaneClient zillizCloudControlPlaneClient) {
        this(
            platformSecretService,
            objectMapper,
            httpClient,
            new DeploymentProviderSecretResolutionService(platformSecretService),
            qdrantCloudControlPlaneClient,
            pineconeControlPlaneClient,
            zillizCloudControlPlaneClient
        );
    }

    DeploymentManagedVectorProvisioningService(PlatformSecretService platformSecretService,
                                               ObjectMapper objectMapper,
                                               HttpClient httpClient,
                                               DeploymentProviderSecretResolutionService deploymentProviderSecretResolutionService,
                                               QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient,
                                               PineconeControlPlaneClient pineconeControlPlaneClient,
                                               ZillizCloudControlPlaneClient zillizCloudControlPlaneClient) {
        this.platformSecretService = platformSecretService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.deploymentProviderSecretResolutionService = deploymentProviderSecretResolutionService;
        this.qdrantCloudControlPlaneClient = qdrantCloudControlPlaneClient;
        this.pineconeControlPlaneClient = pineconeControlPlaneClient;
        this.zillizCloudControlPlaneClient = zillizCloudControlPlaneClient;
    }

    public boolean requiresProvisioning(DeploymentVersionEntity version) {
        JsonNode providerConfig = readJson(version.getProviderConfigJson());
        return ManagedDeploymentProfileCatalog.managedVectorProvisioningRequested(providerConfig);
    }

    public ManagedVectorProvisioningResult ensureProvisioned(DeploymentEntity deployment,
                                                             DeploymentVersionEntity version) {
        if (requiresProvisioning(version)) throw new RailwayProvisioningConfigurationException("MANAGED_VECTOR_TARGET_REQUIRED");
        return ensureProvisioned(deployment, version, null);
    }

    public ManagedVectorProvisioningResult ensureProvisioned(DeploymentEntity deployment,
                                                             DeploymentVersionEntity version, String targetProfileId) {
        JsonNode providerConfig = readJson(version.getProviderConfigJson());
        JsonNode entityConfig = readJson(version.getEntityConfigJson());
        if (!ManagedDeploymentProfileCatalog.managedVectorProvisioningRequested(providerConfig)) {
            return new ManagedVectorProvisioningResult(providerConfig, objectMapper.createObjectNode().put("enabled",false).put("mode","NONE"));
        }
        if (ownership==null) throw new RailwayProvisioningConfigurationException("MANAGED_RESOURCE_OWNERSHIP_REQUIRED");
        return ensureProvisioned(ownership.scope(deployment,targetProfileId),providerConfig,entityConfig);
    }

    ManagedVectorProvisioningResult ensureProvisioned(String deploymentId,
                                                      JsonNode providerConfig,
                                                      JsonNode entityConfig) {
        throw new RailwayProvisioningConfigurationException("MANAGED_VECTOR_TARGET_REQUIRED");
    }

    ManagedVectorProvisioningResult ensureProvisioned(ManagedResourceOwnershipService.Scope scope,
                                                      JsonNode providerConfig, JsonNode entityConfig) {
        if (ownership==null) throw new RailwayProvisioningConfigurationException("MANAGED_RESOURCE_OWNERSHIP_REQUIRED");
        String deploymentId=scope.deploymentId();
        ObjectNode effectiveProviderConfig = providerConfig != null && providerConfig.isObject()
            ? ((ObjectNode) providerConfig).deepCopy()
            : objectMapper.createObjectNode();
        ObjectNode details = objectMapper.createObjectNode();

        if (ManagedDeploymentProfileCatalog.pineconePlatformManaged(providerConfig)) {
            details.put("enabled", true);
            details.put("vectorStrategy", ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_PINECONE);
            ensureManagedPineconeIndex(scope, effectiveProviderConfig, entityConfig, details);
            return new ManagedVectorProvisioningResult(effectiveProviderConfig, details);
        }

        if (ManagedDeploymentProfileCatalog.qdrantPlatformManaged(providerConfig)) {
            details.put("enabled", true);
            details.put("vectorStrategy", ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT);
            if (reuseManagedQdrantSharedRoot(effectiveProviderConfig)) {
                ensureManagedQdrantCollections(scope, effectiveProviderConfig, entityConfig, details);
                details.put("sharedRootReused", true);
            } else {
                ensureManagedQdrantCloudCluster(scope, effectiveProviderConfig, entityConfig, details);
            }
            return new ManagedVectorProvisioningResult(effectiveProviderConfig, details);
        }

        if (ManagedDeploymentProfileCatalog.milvusPlatformManaged(providerConfig)) {
            details.put("enabled", true);
            details.put("vectorStrategy", ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_MILVUS);
            ensureManagedZillizCloudCluster(scope, effectiveProviderConfig, details);
            return new ManagedVectorProvisioningResult(effectiveProviderConfig, details);
        }

        if (ManagedDeploymentProfileCatalog.usesQdrant(providerConfig)
            && ManagedDeploymentProfileCatalog.qdrantManagedCollectionsEnabled(providerConfig)) {
            details.put("enabled", true);
            details.put("vectorStrategy", ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT);
            ensureManagedQdrantCollections(scope, effectiveProviderConfig, entityConfig, details);
            return new ManagedVectorProvisioningResult(effectiveProviderConfig, details);
        }

        details.put("enabled", false);
        details.put("vectorStrategy", ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig));
        details.put("mode", "NONE");
        details.put("message", "No managed external vector provisioning is configured for this deployment.");
        return new ManagedVectorProvisioningResult(effectiveProviderConfig, details);
    }

    private void ensureManagedPineconeIndex(ManagedResourceOwnershipService.Scope scope,
                                            ObjectNode effectiveProviderConfig,
                                            JsonNode entityConfig,
                                            ObjectNode details) {
        String deploymentId=scope.deploymentId();
        String apiKey = requireResolvedProviderSecret(deploymentId, "PINECONE_API_KEY");
        String indexName = requiredText(effectiveProviderConfig, "pineconeIndexName", "pinecone managed index");
        int dimensions = resolveVectorDimensions(entityConfig, effectiveProviderConfig);
        String metric = ManagedDeploymentProfileCatalog.pineconeMetric(effectiveProviderConfig);
        String cloud = ManagedDeploymentProfileCatalog.pineconeCloud(effectiveProviderConfig);
        String region = requiredText(effectiveProviderConfig, "pineconeRegion", "pinecone managed index");
        boolean deletionProtectionEnabled = ManagedDeploymentProfileCatalog.pineconeDeletionProtectionEnabled(effectiveProviderConfig);

        PineconeControlPlaneClient.PineconeIndexSummary existing = pineconeControlPlaneClient.findIndexByName(indexName, apiKey);
        boolean created = false;
        if (existing == null) {
            ownership.create(scope,"pinecone","INDEX","pinecone-control-plane",indexName,()->{
                pineconeControlPlaneClient.createServerlessIndex(indexName,dimensions,metric,cloud,region,deletionProtectionEnabled,apiKey);
                return pineconeControlPlaneClient.awaitIndexReady(indexName,apiKey);
            },value->{
                validatePineconeIndex(value,indexName,dimensions,metric);
                return new ManagedResourceOwnershipService.Identity(indexName,value.host());
            });
            created = true;
        } else {
            ownership.requireOwnedAt(scope,"pinecone","INDEX","pinecone-control-plane",indexName,indexName,existing.host());
            validatePineconeIndex(existing, indexName, dimensions, metric);
        }

        PineconeControlPlaneClient.PineconeIndexSummary readyIndex = pineconeControlPlaneClient.awaitIndexReady(indexName, apiKey);
        validatePineconeIndex(readyIndex, indexName, dimensions, metric);
        var ownershipReceipt=ownership.requireOwned(scope,"pinecone","INDEX",indexName,readyIndex.host());
        details.put("ownershipReceiptId",ownershipReceipt.id());
        if (!StringUtils.hasText(readyIndex.host())) {
            throw new RailwayProvisioningException("Pinecone index '" + indexName + "' did not expose an API host.");
        }
        String runtimeSecretName = managedPineconeRuntimeSecretName(scope);
        platformSecretService.upsertManagedSecret(
            runtimeSecretName,
            apiKey,
            Map.of(
                "deploymentId", deploymentId,
                "vendor", "pinecone",
                "resourceType", "INDEX",
                "resourceName", indexName
            )
        );

        effectiveProviderConfig.put("pineconeIndexName", indexName);
        effectiveProviderConfig.put("pineconeApiHost", readyIndex.host());
        effectiveProviderConfig.put("pineconeDimensions", dimensions);
        effectiveProviderConfig.put("pineconeManagedIndexEnabled", true);
        effectiveProviderConfig.put("vectorProvisioningMode", ManagedDeploymentProfileCatalog.VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED);
        effectiveProviderConfig.put("pineconeRuntimeApiKeySecretName", runtimeSecretName);
        effectiveProviderConfig.put("pineconeEnvironment", "");
        effectiveProviderConfig.put("pineconeProjectId", "");

        details.put("mode", "MANAGED_SERVERLESS_INDEX");
        details.put("deploymentId", deploymentId);
        details.put("indexName", indexName);
        details.put("apiHost", readyIndex.host());
        details.put("metric", readyIndex.metric());
        details.put("dimensions", readyIndex.dimension());
        details.put("cloud", cloud);
        details.put("region", region);
        details.put("deletionProtection", deletionProtectionEnabled ? "enabled" : "disabled");
        details.put("state", created ? "CREATED" : "REUSED");
        details.put("ready", readyIndex.ready());
        details.put("runtimeApiKeySecretName", runtimeSecretName);
    }

    private void ensureManagedQdrantCollections(ManagedResourceOwnershipService.Scope scope,
                                                ObjectNode effectiveProviderConfig,
                                                JsonNode entityConfig,
                                                ObjectNode details) {
        String deploymentId=scope.deploymentId();
        String baseUrl = buildQdrantBaseUrl(effectiveProviderConfig);
        int vectorDimensions = resolveVectorDimensions(entityConfig, effectiveProviderConfig);
        String apiKey = resolveOptionalProviderSecret(
            deploymentId,
            "QDRANT_API_KEY",
            ManagedDeploymentProfileCatalog.qdrantRuntimeApiKeySecretName(effectiveProviderConfig)
        );
        ArrayNode collections = reconcileQdrantCollections(scope,baseUrl, resolveEntityTypes(entityConfig), vectorDimensions, apiKey);

        details.put("mode", "MANAGED_COLLECTIONS");
        details.put("baseUrl", baseUrl);
        details.put("vectorDimensions", vectorDimensions);
        details.set("collections", collections);
    }

    private boolean reuseManagedQdrantSharedRoot(JsonNode providerConfig) {
        return ManagedDeploymentProfileCatalog.qdrantManagedCollectionsEnabled(providerConfig)
            && StringUtils.hasText(ManagedDeploymentProfileCatalog.qdrantHost(providerConfig));
    }

    private void ensureManagedQdrantCloudCluster(ManagedResourceOwnershipService.Scope scope,
                                                 ObjectNode effectiveProviderConfig,
                                                 JsonNode entityConfig,
                                                 ObjectNode details) {
        String deploymentId=scope.deploymentId();
        List<String> entityTypes = resolveEntityTypes(entityConfig);
        if (entityTypes.isEmpty()) {
            throw new RailwayProvisioningException(
                "Platform-managed Qdrant Cloud provisioning requires at least one configured entity type."
            );
        }

        String managementApiKey = requireSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY");
        String configuredAccountId = ManagedDeploymentProfileCatalog.qdrantCloudAccountId(effectiveProviderConfig);
        QdrantCloudControlPlaneClient.QdrantCloudAccountResolution account = qdrantCloudControlPlaneClient.resolveAccount(
            configuredAccountId,
            managementApiKey
        );
        String providerId = ManagedDeploymentProfileCatalog.qdrantCloudProviderId(effectiveProviderConfig);
        String regionId = requiredText(effectiveProviderConfig, "qdrantCloudRegionId", "platform-managed Qdrant Cloud");
        QdrantCloudControlPlaneClient.QdrantCloudRegionSummary region = qdrantCloudControlPlaneClient.requireRegion(providerId, regionId);
        QdrantCloudControlPlaneClient.QdrantCloudPackageSummary pkg = qdrantCloudControlPlaneClient.resolvePackage(
            account.accountId(),
            managementApiKey,
            providerId,
            region.id(),
            ManagedDeploymentProfileCatalog.qdrantCloudPackageId(effectiveProviderConfig)
        );

        String clusterName = resolveManagedQdrantClusterName(deploymentId, effectiveProviderConfig);
        QdrantCloudControlPlaneClient.QdrantCloudClusterSummary existingCluster =
            qdrantCloudControlPlaneClient.findClusterByName(account.accountId(), clusterName, managementApiKey);
        boolean clusterCreated = false;
        QdrantCloudControlPlaneClient.QdrantCloudClusterSummary cluster;
        if (existingCluster == null) {
            cluster = ownership.create(scope,"qdrant","CLUSTER",account.accountId(),clusterName,()->{
                var result=qdrantCloudControlPlaneClient.createCluster(account.accountId(),deploymentId,clusterName,providerId,region.id(),pkg.id(),managementApiKey);
                var ready=qdrantCloudControlPlaneClient.awaitClusterReady(account.accountId(),result.id(),managementApiKey);
                if (!result.id().equals(ready.id()) || !account.accountId().equals(ready.accountId()) || !clusterName.equals(ready.name()))
                    throw new RailwayProvisioningException("MANAGED_RESOURCE_CREATE_IDENTITY_CHANGED");
                validateExistingQdrantCluster(ready,providerId,region.id());
                return ready;
            },value->new ManagedResourceOwnershipService.Identity(value.id(),normalizeQdrantCloudBaseUrl(value.endpointUrl())));
            clusterCreated = true;
        } else {
            ownership.requireOwnedAt(scope,"qdrant","CLUSTER",account.accountId(),clusterName,existingCluster.id(),normalizeQdrantCloudBaseUrl(existingCluster.endpointUrl()));
            validateExistingQdrantCluster(existingCluster, providerId, region.id());
            cluster = existingCluster;
        }

        QdrantCloudControlPlaneClient.QdrantCloudClusterSummary readyCluster = qdrantCloudControlPlaneClient.awaitClusterReady(
            account.accountId(),
            cluster.id(),
            managementApiKey
        );
        if (!cluster.id().equals(readyCluster.id()) || !account.accountId().equals(readyCluster.accountId()) || !clusterName.equals(readyCluster.name()))
            throw new RailwayProvisioningException("MANAGED_RESOURCE_CREATE_IDENTITY_CHANGED");
        validateExistingQdrantCluster(readyCluster,providerId,region.id());
        String baseUrl = normalizeQdrantCloudBaseUrl(readyCluster.endpointUrl());
        details.put("ownershipReceiptId",ownership.requireOwned(scope,"qdrant","CLUSTER",readyCluster.id(),baseUrl).id());
        String runtimeSecretName = managedQdrantRuntimeSecretName(scope);
        String databaseApiKeyName = managedQdrantDatabaseApiKeyName(scope);
        QdrantCloudControlPlaneClient.QdrantCloudDatabaseApiKeySummary existingDatabaseApiKey =
            qdrantCloudControlPlaneClient.findDatabaseApiKeyByName(
                account.accountId(),
                readyCluster.id(),
                databaseApiKeyName,
                managementApiKey
            );
        String runtimeApiKey = platformSecretService.resolveSecret(runtimeSecretName);
        boolean databaseApiKeyCreated = false;
        boolean databaseApiKeyRotated = false;
        if (existingDatabaseApiKey == null) {
            QdrantCloudControlPlaneClient.QdrantCloudDatabaseApiKeySummary createdDatabaseApiKey =
                ownership.create(scope,"qdrant","DATABASE_API_KEY",baseUrl,databaseApiKeyName,
                    ()->qdrantCloudControlPlaneClient.createCollectionDatabaseApiKey(account.accountId(),readyCluster.id(),databaseApiKeyName,entityTypes,managementApiKey),
                    value->{
                        if (!readyCluster.id().equals(value.clusterId()) || !databaseApiKeyName.equals(value.name()))
                            throw new RailwayProvisioningException("MANAGED_RESOURCE_CREATE_IDENTITY_CHANGED");
                        return new ManagedResourceOwnershipService.Identity(value.id(),baseUrl);
                    });
            runtimeApiKey = createdDatabaseApiKey.key();
            if (!StringUtils.hasText(runtimeApiKey)) {
                throw new RailwayProvisioningException(
                    "Qdrant Cloud created database API key '" + databaseApiKeyName + "' but did not return the secret value."
                );
            }
            platformSecretService.upsertManagedSecret(
                runtimeSecretName,
                runtimeApiKey,
                Map.of(
                    "deploymentId", deploymentId,
                    "vendor", "qdrant",
                    "resourceType", "DATABASE_API_KEY",
                    "resourceName", databaseApiKeyName
                )
            );
            existingDatabaseApiKey = createdDatabaseApiKey;
            databaseApiKeyCreated = true;
        } else {
            ownership.requireOwnedAt(scope,"qdrant","DATABASE_API_KEY",baseUrl,databaseApiKeyName,existingDatabaseApiKey.id(),baseUrl);
            if (!StringUtils.hasText(runtimeApiKey)) {
            throw new RailwayProvisioningConfigurationException(
                "The platform-managed Qdrant Cloud database key '" + databaseApiKeyName + "' already exists, but the managed platform secret '"
                    + runtimeSecretName + "' is missing. Restore the secret or rotate the key before re-applying."
            );
            }
        }
        details.put("databaseApiKeyOwnershipReceiptId",ownership.requireOwned(scope,"qdrant","DATABASE_API_KEY",existingDatabaseApiKey.id(),baseUrl).id());

        int vectorDimensions = resolveVectorDimensions(entityConfig, effectiveProviderConfig);
        ArrayNode collections;
        collections = databaseApiKeyCreated
            ? reconcileQdrantCollectionsWithPropagationGrace(scope,baseUrl,entityTypes,vectorDimensions,runtimeApiKey)
            : reconcileQdrantCollections(scope,baseUrl,entityTypes,vectorDimensions,runtimeApiKey);
        String effectivePackageId = StringUtils.hasText(readyCluster.packageId()) ? readyCluster.packageId() : pkg.id();
        boolean packageResolvedFromExistingCluster = !clusterCreated
            && StringUtils.hasText(readyCluster.packageId())
            && !readyCluster.packageId().equals(pkg.id());

        effectiveProviderConfig.put("qdrantHost", baseUrl);
        if (readyCluster.restPort() > 0) {
            effectiveProviderConfig.put("qdrantPort", readyCluster.restPort());
        }
        if (readyCluster.grpcPort() > 0) {
            effectiveProviderConfig.put("qdrantGrpcPort", readyCluster.grpcPort());
        }
        effectiveProviderConfig.put("qdrantCloudAccountId", account.accountId());
        effectiveProviderConfig.put("qdrantCloudProviderId", providerId);
        effectiveProviderConfig.put("qdrantCloudRegionId", region.id());
        effectiveProviderConfig.put("qdrantCloudPackageId", effectivePackageId);
        effectiveProviderConfig.put("qdrantRuntimeApiKeySecretName", runtimeSecretName);

        details.put("mode", "MANAGED_CLOUD_CLUSTER");
        details.put("deploymentId", deploymentId);
        details.put("accountId", account.accountId());
        details.put("accountName", account.accountName());
        details.put("accountAutoResolved", account.autoResolved());
        details.put("cloudProviderId", providerId);
        details.put("regionId", region.id());
        details.put("regionName", region.name());
        details.put("packageId", effectivePackageId);
        if (packageResolvedFromExistingCluster) {
            details.put("requestedActivePackageId", pkg.id());
            details.put("packageName", "Existing cluster package");
            details.put("packageResolution", "REUSED_EXISTING_CLUSTER_PACKAGE");
        } else {
            details.put("packageName", pkg.name());
            details.put("packageResolution", clusterCreated ? "CREATED_WITH_ACTIVE_PACKAGE" : "REUSED_ACTIVE_PACKAGE");
        }
        details.put("packageType", pkg.type());
        details.put("packageHourlyPriceMillicents", pkg.unitIntPricePerHour());
        details.put("clusterId", readyCluster.id());
        details.put("clusterName", readyCluster.name());
        details.put("clusterState", clusterCreated ? "CREATED" : "REUSED");
        details.put("baseUrl", baseUrl);
        details.put("restPort", readyCluster.restPort());
        details.put("grpcPort", readyCluster.grpcPort());
        details.put("clusterPhase", readyCluster.phase());
        details.put("databaseApiKeyId", existingDatabaseApiKey.id());
        details.put("databaseApiKeyName", existingDatabaseApiKey.name());
        details.put("databaseApiKeySecretName", runtimeSecretName);
        details.put("databaseApiKeyState", databaseApiKeyRotated ? "ROTATED" : (databaseApiKeyCreated ? "CREATED" : "REUSED"));
        details.put("vectorDimensions", vectorDimensions);
        details.set("collections", collections);
    }

    private void ensureManagedZillizCloudCluster(ManagedResourceOwnershipService.Scope scope,
                                                 ObjectNode effectiveProviderConfig,
                                                 ObjectNode details) {
        String deploymentId=scope.deploymentId();
        String apiKey = requireSecret("ZILLIZ_CLOUD_API_KEY");
        String regionId = requiredText(effectiveProviderConfig, "zillizCloudRegionId", "platform-managed Zilliz Cloud");
        ZillizCloudControlPlaneClient.ZillizProjectResolution project = zillizCloudControlPlaneClient.resolveProject(
            ManagedDeploymentProfileCatalog.zillizCloudProjectId(effectiveProviderConfig),
            regionId,
            apiKey
        );
        String clusterName = resolveManagedZillizClusterName(deploymentId, effectiveProviderConfig);
        String clusterPlan = ManagedDeploymentProfileCatalog.zillizCloudClusterPlan(effectiveProviderConfig);
        String cuType = ManagedDeploymentProfileCatalog.zillizCloudCuType(effectiveProviderConfig);
        int cuSize = ManagedDeploymentProfileCatalog.zillizCloudCuSize(effectiveProviderConfig);

        ZillizCloudControlPlaneClient.ZillizClusterSummary existingCluster =
            zillizCloudControlPlaneClient.findClusterByName(clusterName, apiKey);
        String clusterState;
        ZillizCloudControlPlaneClient.ZillizClusterSummary cluster;
        if (existingCluster == null) {
            var created=new java.util.concurrent.atomic.AtomicReference<ZillizCloudControlPlaneClient.ZillizClusterCreateResult>();
            cluster=ownership.create(scope,"zilliz","CLUSTER",project.projectId(),clusterName,()->{
                created.set(zillizCloudControlPlaneClient.createCluster(clusterName,project.projectId(),regionId,clusterPlan,cuType,cuSize,apiKey));
                var ready=zillizCloudControlPlaneClient.awaitClusterReady(created.get().clusterId(),apiKey);
                if (!created.get().clusterId().equals(ready.clusterId()) || !clusterName.equals(ready.clusterName()))
                    throw new RailwayProvisioningException("MANAGED_RESOURCE_CREATE_IDENTITY_CHANGED");
                validateExistingZillizCluster(ready,project.projectId(),regionId,clusterPlan,cuType,cuSize);
                return ready;
            },value->new ManagedResourceOwnershipService.Identity(value.clusterId(),resolveManagedMilvusEndpoint(value.connectAddress()).baseUrl()));
            storeManagedZillizRuntimeCredentials(scope, clusterName, created.get());
            clusterState = "CREATED";
        } else {
            ownership.requireOwnedAt(scope,"zilliz","CLUSTER",project.projectId(),clusterName,existingCluster.clusterId(),resolveManagedMilvusEndpoint(existingCluster.connectAddress()).baseUrl());
            validateExistingZillizCluster(existingCluster, project.projectId(), regionId, clusterPlan, cuType, cuSize);
            if (managedZillizRuntimeSecretsPresent(scope)) {
                cluster = zillizCloudControlPlaneClient.awaitClusterReady(existingCluster.clusterId(), apiKey);
                clusterState = "REUSED";
            } else {
                throw new RailwayProvisioningConfigurationException(
                    "MANAGED_ZILLIZ_RUNTIME_CREDENTIALS_REQUIRED: existing cluster runtime credentials are missing. "
                        + "Operator reconciliation is required; the cluster and remaining secrets were not changed."
                );
            }
        }

        ManagedMilvusEndpoint endpoint = resolveManagedMilvusEndpoint(cluster.connectAddress());
        if (!clusterName.equals(cluster.clusterName())) throw new RailwayProvisioningException("MANAGED_RESOURCE_CREATE_IDENTITY_CHANGED");
        validateExistingZillizCluster(cluster,project.projectId(),regionId,clusterPlan,cuType,cuSize);
        details.put("ownershipReceiptId",ownership.requireOwned(scope,"zilliz","CLUSTER",cluster.clusterId(),endpoint.baseUrl()).id());
        String usernameSecretName = managedMilvusUsernameSecretName(scope);
        String passwordSecretName = managedMilvusPasswordSecretName(scope);

        effectiveProviderConfig.put("milvusHost", endpoint.host());
        effectiveProviderConfig.put("milvusPort", endpoint.port());
        effectiveProviderConfig.put("milvusSecure", endpoint.secure());
        effectiveProviderConfig.put("milvusRuntimeUsernameSecretName", usernameSecretName);
        effectiveProviderConfig.put("milvusRuntimePasswordSecretName", passwordSecretName);
        effectiveProviderConfig.put("zillizCloudProjectId", project.projectId());
        effectiveProviderConfig.put("zillizCloudRegionId", regionId);
        effectiveProviderConfig.put("zillizCloudClusterPlan", clusterPlan);

        details.put("mode", "MANAGED_ZILLIZ_CLOUD_CLUSTER");
        details.put("deploymentId", deploymentId);
        details.put("projectId", project.projectId());
        details.put("projectName", project.projectName());
        details.put("projectAutoResolved", project.autoResolved());
        details.put("regionId", regionId);
        details.put("clusterId", cluster.clusterId());
        details.put("clusterName", cluster.clusterName());
        details.put("clusterState", clusterState);
        details.put("clusterPlan", cluster.plan());
        details.put("deploymentOption", cluster.deploymentOption());
        details.put("clusterStatus", cluster.status());
        details.put("baseUrl", endpoint.baseUrl());
        details.put("host", endpoint.host());
        details.put("port", endpoint.port());
        details.put("secure", endpoint.secure());
        details.put("runtimeUsernameSecretName", usernameSecretName);
        details.put("runtimePasswordSecretName", passwordSecretName);
        if (StringUtils.hasText(cluster.cuType())) {
            details.put("cuType", cluster.cuType());
        }
        if (cluster.cuSize() > 0) {
            details.put("cuSize", cluster.cuSize());
        }
    }

    private int resolveVectorDimensions(JsonNode entityConfig, JsonNode providerConfig) {
        int configured = entityConfig.path("ai-config").path("vector-dimensions").asInt(0);
        if (configured > 0) {
            return configured;
        }
        return ManagedDeploymentProfileCatalog.defaultVectorDimensions(
            ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig),
            ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig)
        );
    }

    private List<String> resolveEntityTypes(JsonNode entityConfig) {
        JsonNode aiEntities = entityConfig.path("ai-entities");
        if (!aiEntities.isObject()) {
            return List.of();
        }
        List<String> entityTypes = new ArrayList<>();
        Iterator<String> fieldNames = aiEntities.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (StringUtils.hasText(fieldName)) {
                entityTypes.add(fieldName.trim());
            }
        }
        return entityTypes;
    }

    private ArrayNode reconcileQdrantCollections(ManagedResourceOwnershipService.Scope scope,String baseUrl,
                                                 List<String> entityTypes,
                                                 int vectorDimensions,
                                                 String apiKey) {
        if (entityTypes.isEmpty()) {
            throw new RailwayProvisioningException(
                "Qdrant managed collection provisioning requires at least one configured entity type."
            );
        }
        var existing=new java.util.LinkedHashMap<String,Boolean>();
        for (String entityType:entityTypes) {
            boolean found=qdrantCollectionExists(baseUrl,entityType,vectorDimensions,apiKey);
            if (found) ownership.requireOwnedAt(scope,"qdrant","COLLECTION",baseUrl,entityType,entityType,baseUrl);
            existing.put(entityType,found);
        }
        ArrayNode collections = objectMapper.createArrayNode();
        for (String entityType : entityTypes) {
            boolean existed = existing.get(entityType);
            if (!existed) {
                ownership.create(scope,"qdrant","COLLECTION",baseUrl,entityType,()->{
                    createQdrantCollection(baseUrl,entityType,vectorDimensions,apiKey);
                    return entityType;
                },value->new ManagedResourceOwnershipService.Identity(value,baseUrl));
            }
            collections.add(objectMapper.createObjectNode()
                .put("name", entityType)
                .put("ownershipReceiptId",ownership.requireOwned(scope,"qdrant","COLLECTION",entityType,baseUrl).id())
                .put("state", existed ? "REUSED" : "CREATED"));
        }
        return collections;
    }

    private ArrayNode reconcileQdrantCollectionsWithPropagationGrace(ManagedResourceOwnershipService.Scope scope,String baseUrl,
                                                                     List<String> entityTypes,
                                                                     int vectorDimensions,
                                                                     String apiKey) {
        RailwayProvisioningException lastFailure = null;
        for (int attempt = 1; attempt <= 15; attempt += 1) {
            try {
                return reconcileQdrantCollections(scope,baseUrl, entityTypes, vectorDimensions, apiKey);
            } catch (RailwayProvisioningException ex) {
                if (!isQdrantStartupPropagationFailure(ex) || attempt == 15) {
                    throw ex;
                }
                lastFailure = ex;
                sleep(2000L, "Interrupted while waiting for Qdrant collection provisioning prerequisites.");
            }
        }
        throw lastFailure == null
            ? new RailwayProvisioningException("Qdrant collection reconciliation did not complete.")
            : lastFailure;
    }

    private void validateExistingQdrantCluster(QdrantCloudControlPlaneClient.QdrantCloudClusterSummary cluster,
                                               String providerId,
                                               String regionId) {
        if (!providerId.equals(cluster.cloudProviderId())) {
            throw new RailwayProvisioningException(
                "Qdrant Cloud cluster '" + cluster.name() + "' already exists on provider '" + cluster.cloudProviderId()
                    + "' but deployment requires '" + providerId + "'."
            );
        }
        if (!regionId.equals(cluster.cloudProviderRegionId())) {
            throw new RailwayProvisioningException(
                "Qdrant Cloud cluster '" + cluster.name() + "' already exists in region '" + cluster.cloudProviderRegionId()
                    + "' but deployment requires '" + regionId + "'."
            );
        }
    }

    private String resolveManagedQdrantClusterName(String deploymentId, JsonNode providerConfig) {
        String override = ManagedDeploymentProfileCatalog.qdrantCloudClusterNameOverride(providerConfig);
        String base = StringUtils.hasText(override) ? override : "aifabric-" + deploymentId.replace("dep-", "");
        String normalized = base.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9-_]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");
        if (normalized.length() < 2) {
            normalized = "af-" + deploymentId.replace("dep-", "");
        }
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private String managedQdrantRuntimeSecretName(ManagedResourceOwnershipService.Scope scope) {
        return PlatformSecretService.MANAGED_SECRET_PREFIX
            + "QDRANT_DB_API_KEY_DEP_"
            + secretScope(scope);
    }

    private String managedQdrantDatabaseApiKeyName(ManagedResourceOwnershipService.Scope scope) {
        String suffix = secretScope(scope).toLowerCase(Locale.ROOT);
        return ("ai-fabric-" + suffix).substring(0, Math.min(("ai-fabric-" + suffix).length(), 64));
    }

    private String managedPineconeRuntimeSecretName(ManagedResourceOwnershipService.Scope scope) {
        return PlatformSecretService.MANAGED_SECRET_PREFIX
            + "PINECONE_API_KEY_DEP_"
            + secretScope(scope);
    }

    private String managedMilvusUsernameSecretName(ManagedResourceOwnershipService.Scope scope) {
        return PlatformSecretService.MANAGED_SECRET_PREFIX
            + "MILVUS_USERNAME_DEP_"
            + secretScope(scope);
    }

    private String managedMilvusPasswordSecretName(ManagedResourceOwnershipService.Scope scope) {
        return PlatformSecretService.MANAGED_SECRET_PREFIX
            + "MILVUS_PASSWORD_DEP_"
            + secretScope(scope);
    }

    private String secretScope(ManagedResourceOwnershipService.Scope scope) {
        return DeploymentEvidenceFingerprint.hash(objectMapper,objectMapper.valueToTree(scope)).toUpperCase(Locale.ROOT);
    }

    private void storeManagedZillizRuntimeCredentials(ManagedResourceOwnershipService.Scope scope,
                                                      String clusterName,
                                                      ZillizCloudControlPlaneClient.ZillizClusterCreateResult createResult) {
        if (!StringUtils.hasText(createResult.username()) || !StringUtils.hasText(createResult.password())) {
            throw new RailwayProvisioningException(
                "Zilliz Cloud created cluster '" + clusterName + "' but did not return runtime credentials."
            );
        }
        platformSecretService.upsertManagedSecret(
            managedMilvusUsernameSecretName(scope),
            createResult.username(),
            Map.of(
                "deploymentId", scope.deploymentId(),
                "vendor", "zilliz",
                "resourceType", "CLUSTER_CREDENTIAL",
                "resourceName", clusterName,
                "credentialKind", "USERNAME"
            )
        );
        platformSecretService.upsertManagedSecret(
            managedMilvusPasswordSecretName(scope),
            createResult.password(),
            Map.of(
                "deploymentId", scope.deploymentId(),
                "vendor", "zilliz",
                "resourceType", "CLUSTER_CREDENTIAL",
                "resourceName", clusterName,
                "credentialKind", "PASSWORD"
            )
        );
    }

    private boolean managedZillizRuntimeSecretsPresent(ManagedResourceOwnershipService.Scope scope) {
        String usernameSecretName = managedMilvusUsernameSecretName(scope);
        String passwordSecretName = managedMilvusPasswordSecretName(scope);
        return StringUtils.hasText(platformSecretService.resolveSecret(usernameSecretName))
            && StringUtils.hasText(platformSecretService.resolveSecret(passwordSecretName));
    }

    private void validateExistingZillizCluster(ZillizCloudControlPlaneClient.ZillizClusterSummary cluster,
                                               String projectId,
                                               String regionId,
                                               String clusterPlan,
                                               String cuType,
                                               int cuSize) {
        if (StringUtils.hasText(projectId) && StringUtils.hasText(cluster.projectId()) && !projectId.equals(cluster.projectId())) {
            throw new RailwayProvisioningException(
                "Zilliz Cloud cluster '" + cluster.clusterName() + "' already exists in project '" + cluster.projectId()
                    + "' but deployment requires '" + projectId + "'."
            );
        }
        if (StringUtils.hasText(regionId) && StringUtils.hasText(cluster.regionId()) && !regionId.equals(cluster.regionId())) {
            throw new RailwayProvisioningException(
                "Zilliz Cloud cluster '" + cluster.clusterName() + "' already exists in region '" + cluster.regionId()
                    + "' but deployment requires '" + regionId + "'."
            );
        }
        if (StringUtils.hasText(clusterPlan) && StringUtils.hasText(cluster.plan()) && !clusterPlan.equalsIgnoreCase(cluster.plan())) {
            throw new RailwayProvisioningException(
                "Zilliz Cloud cluster '" + cluster.clusterName() + "' already exists with plan '" + cluster.plan()
                    + "' but deployment requires '" + clusterPlan + "'."
            );
        }
        boolean dedicatedPlan = "Standard".equals(clusterPlan) || "Enterprise".equals(clusterPlan);
        if (dedicatedPlan && StringUtils.hasText(cuType) && StringUtils.hasText(cluster.cuType())
            && !cuType.equalsIgnoreCase(cluster.cuType())) {
            throw new RailwayProvisioningException(
                "Zilliz Cloud cluster '" + cluster.clusterName() + "' already exists with cuType '" + cluster.cuType()
                    + "' but deployment requires '" + cuType + "'."
            );
        }
        if (dedicatedPlan && cuSize > 0 && cluster.cuSize() > 0 && cuSize != cluster.cuSize()) {
            throw new RailwayProvisioningException(
                "Zilliz Cloud cluster '" + cluster.clusterName() + "' already exists with cuSize " + cluster.cuSize()
                    + " but deployment requires " + cuSize + "."
            );
        }
    }

    private String resolveManagedZillizClusterName(String deploymentId, JsonNode providerConfig) {
        String override = ManagedDeploymentProfileCatalog.zillizCloudClusterNameOverride(providerConfig);
        String base = StringUtils.hasText(override) ? override : "aifabric-" + deploymentId.replace("dep-", "");
        String normalized = base.trim()
            .replaceAll("[^A-Za-z0-9-]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");
        if (!StringUtils.hasText(normalized)) {
            normalized = "aifabric-" + deploymentId.replace("dep-", "");
        }
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private ManagedMilvusEndpoint resolveManagedMilvusEndpoint(String connectAddress) {
        if (!StringUtils.hasText(connectAddress)) {
            throw new RailwayProvisioningException("Zilliz Cloud cluster did not expose a usable connectAddress.");
        }
        URI uri = URI.create(connectAddress.trim());
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new RailwayProvisioningException("Zilliz Cloud cluster connectAddress did not expose a valid host.");
        }
        boolean secure = "https".equalsIgnoreCase(uri.getScheme());
        int port = uri.getPort() > 0 ? uri.getPort() : (secure ? 443 : ManagedDeploymentProfileCatalog.DEFAULT_MILVUS_PORT);
        String baseUrl = uri.getScheme() + "://" + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        return new ManagedMilvusEndpoint(baseUrl, host, port, secure);
    }

    private String normalizeQdrantCloudBaseUrl(String endpointUrl) {
        if (!StringUtils.hasText(endpointUrl)) {
            throw new RailwayProvisioningException("Qdrant Cloud cluster did not expose a usable endpoint URL.");
        }
        return trimTrailingSlash(endpointUrl.trim());
    }

    private void validatePineconeIndex(PineconeControlPlaneClient.PineconeIndexSummary snapshot,
                                       String indexName,
                                       int dimensions,
                                       String metric) {
        if (snapshot == null || !indexName.equals(snapshot.name()))
            throw new RailwayProvisioningException("MANAGED_RESOURCE_CREATE_IDENTITY_CHANGED");
        if (snapshot.dimension() > 0 && snapshot.dimension() != dimensions) {
            throw new RailwayProvisioningException(
                "Pinecone index '" + indexName + "' exists with dimension " + snapshot.dimension()
                    + " but deployment requires " + dimensions + "."
            );
        }
        if (StringUtils.hasText(snapshot.metric()) && !metric.equalsIgnoreCase(snapshot.metric())) {
            throw new RailwayProvisioningException(
                "Pinecone index '" + indexName + "' exists with metric '" + snapshot.metric()
                    + "' but deployment requires '" + metric + "'."
            );
        }
    }

    private boolean qdrantCollectionExists(String baseUrl,
                                           String collectionName,
                                           int vectorDimensions,
                                           String apiKey) {
        HttpRequest request = qdrantRequestBuilder(qdrantCollectionUri(baseUrl, collectionName), apiKey)
            .GET()
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            return false;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RailwayProvisioningException(
                "Qdrant collection lookup failed for '" + collectionName + "' with HTTP " + response.statusCode()
            );
        }
        JsonNode root = readJson(response.body());
        int existingSize = root.path("result").path("config").path("params").path("vectors").path("size").asInt(0);
        if (existingSize > 0 && existingSize != vectorDimensions) {
            throw new RailwayProvisioningException(
                "Qdrant collection '" + collectionName + "' exists with vector size " + existingSize
                    + " but deployment requires " + vectorDimensions + "."
            );
        }
        return true;
    }

    private void createQdrantCollection(String baseUrl,
                                        String collectionName,
                                        int vectorDimensions,
                                        String apiKey) {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode vectors = payload.putObject("vectors");
        vectors.put("size", vectorDimensions);
        vectors.put("distance", "Cosine");

        HttpRequest request = qdrantRequestBuilder(qdrantCollectionUri(baseUrl, collectionName), apiKey)
            .PUT(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 409) {
            throw new RailwayProvisioningConfigurationException("MANAGED_RESOURCE_CREATE_RECONCILIATION_REQUIRED");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RailwayProvisioningException(
                "Qdrant create collection failed for '" + collectionName + "' with HTTP " + response.statusCode()
            );
        }
        JsonNode acknowledgement=readJson(response.body()).path("result");
        if (!acknowledgement.isBoolean() || !acknowledgement.booleanValue()) {
            throw new RailwayProvisioningConfigurationException("MANAGED_RESOURCE_CREATE_RECONCILIATION_REQUIRED");
        }
    }

    private String buildQdrantBaseUrl(JsonNode providerConfig) {
        String rawHost = requiredText(providerConfig, "qdrantHost", "qdrant managed collections");
        if (rawHost.startsWith("https://") || rawHost.startsWith("http://")) {
            return trimTrailingSlash(rawHost);
        }
        int port = ManagedDeploymentProfileCatalog.qdrantPort(providerConfig);
        String defaulted = port > 0 ? "http://" + rawHost + ":" + port : "http://" + rawHost;
        return trimTrailingSlash(defaulted);
    }

    private HttpRequest.Builder qdrantRequestBuilder(String uri, String apiKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json");
        if (StringUtils.hasText(apiKey)) {
            builder.header("api-key", apiKey);
        }
        return builder;
    }

    private String qdrantCollectionUri(String baseUrl, String collectionName) {
        return trimTrailingSlash(baseUrl) + "/collections/" + encodePathSegment(collectionName);
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new RailwayProvisioningException("External vector provisioning request failed.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RailwayProvisioningException("External vector provisioning request was interrupted.", ex);
        }
    }

    private boolean isQdrantStartupPropagationFailure(RailwayProvisioningException ex) {
        String message = ex.getMessage();
        return StringUtils.hasText(message)
            && message.contains("Qdrant")
            && (message.contains("HTTP 403")
            || message.contains("HTTP 429")
            || message.contains("HTTP 502")
            || message.contains("HTTP 503")
            || message.contains("HTTP 504"));
    }

    private void sleep(long millis, String message) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RailwayProvisioningException(message, ex);
        }
    }

    private String requireSecret(String name) {
        String value = platformSecretService.resolveSecret(name);
        if (!StringUtils.hasText(value)) {
            throw new RailwayProvisioningConfigurationException(
                "Missing platform secret '" + name + "' required for managed vector provisioning."
            );
        }
        return value;
    }

    private String requireResolvedProviderSecret(String deploymentId, String secretPurpose) {
        DeploymentProviderSecretResolutionService.ResolvedSecretValue resolved =
            deploymentProviderSecretResolutionService.resolve(deploymentId, secretPurpose, null);
        if (!resolved.resolved() || !StringUtils.hasText(resolved.value())) {
            throw new RailwayProvisioningConfigurationException(
                "Missing effective provider secret '" + secretPurpose + "' required for managed vector provisioning."
            );
        }
        return resolved.value();
    }

    private String resolveOptionalProviderSecret(String deploymentId, String secretPurpose) {
        return resolveOptionalProviderSecret(deploymentId, secretPurpose, null);
    }

    private String resolveOptionalProviderSecret(String deploymentId,
                                                 String secretPurpose,
                                                 String managedSecretName) {
        DeploymentProviderSecretResolutionService.ResolvedSecretValue resolved =
            deploymentProviderSecretResolutionService.resolve(deploymentId, secretPurpose, managedSecretName);
        return resolved.resolved() ? resolved.value() : null;
    }

    private String requiredText(JsonNode providerConfig, String field, String target) {
        String value = providerConfig.path(field).asText("").trim();
        if (!StringUtils.hasText(value)) {
            throw new RailwayProvisioningConfigurationException(
                "Missing provider config field '" + field + "' required for " + target + "."
            );
        }
        return value;
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse deployment vector provider config JSON.", ex);
        }
    }

    private String blankOrFallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record ManagedMilvusEndpoint(
        String baseUrl,
        String host,
        int port,
        boolean secure
    ) {
    }
}
