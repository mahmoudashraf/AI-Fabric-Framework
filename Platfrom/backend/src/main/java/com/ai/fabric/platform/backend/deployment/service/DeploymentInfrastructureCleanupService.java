package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentManagedVectorResourceSummary;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DeploymentInfrastructureCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentInfrastructureCleanupService.class);
    private static final Set<String> DELETABLE_PROVIDER_RESOURCE_KINDS = Set.of(
        "APPLICATION", "CONNECTOR_APPLICATION", "VECTORIZATION_RUNNER_APPLICATION", "RUNTIME_POSTGRES_DATABASE"
    );

    private final DeploymentManagedVectorResourceService deploymentManagedVectorResourceService;
    private final PineconeControlPlaneClient pineconeControlPlaneClient;
    private final QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient;
    private final ZillizCloudControlPlaneClient zillizCloudControlPlaneClient;
    private final RailwayGraphqlClient railwayGraphqlClient;
    private final DeploymentProviderResourceHandleRepository providerResourceHandleRepository;
    private final DeploymentProviderRegistry providerRegistry;
    private final PlatformSecretService platformSecretService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    @Autowired
    private ManagedResourceOwnershipService ownership;

    @Autowired
    public DeploymentInfrastructureCleanupService(DeploymentManagedVectorResourceService deploymentManagedVectorResourceService,
                                                  PineconeControlPlaneClient pineconeControlPlaneClient,
                                                  QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient,
                                                  ZillizCloudControlPlaneClient zillizCloudControlPlaneClient,
                                                  RailwayGraphqlClient railwayGraphqlClient,
                                                  DeploymentProviderResourceHandleRepository providerResourceHandleRepository,
                                                  DeploymentProviderRegistry providerRegistry,
                                                  PlatformSecretService platformSecretService,
                                                  PlatformAuditService platformAuditService,
                                                  ObjectMapper objectMapper) {
        this(
            deploymentManagedVectorResourceService,
            pineconeControlPlaneClient,
            qdrantCloudControlPlaneClient,
            zillizCloudControlPlaneClient,
            railwayGraphqlClient,
            providerResourceHandleRepository,
            providerRegistry,
            platformSecretService,
            platformAuditService,
            objectMapper,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        );
    }

    DeploymentInfrastructureCleanupService(DeploymentManagedVectorResourceService deploymentManagedVectorResourceService,
                                           PineconeControlPlaneClient pineconeControlPlaneClient,
                                           QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient,
                                           ZillizCloudControlPlaneClient zillizCloudControlPlaneClient,
                                           RailwayGraphqlClient railwayGraphqlClient,
                                           PlatformSecretService platformSecretService,
                                           PlatformAuditService platformAuditService,
                                           ObjectMapper objectMapper) {
        this(
            deploymentManagedVectorResourceService,
            pineconeControlPlaneClient,
            qdrantCloudControlPlaneClient,
            zillizCloudControlPlaneClient,
            railwayGraphqlClient,
            null,
            null,
            platformSecretService,
            platformAuditService,
            objectMapper,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        );
    }

    DeploymentInfrastructureCleanupService(DeploymentManagedVectorResourceService deploymentManagedVectorResourceService,
                                           PineconeControlPlaneClient pineconeControlPlaneClient,
                                           QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient,
                                           ZillizCloudControlPlaneClient zillizCloudControlPlaneClient,
                                           RailwayGraphqlClient railwayGraphqlClient,
                                           PlatformSecretService platformSecretService,
                                           PlatformAuditService platformAuditService,
                                           ObjectMapper objectMapper,
                                           HttpClient httpClient) {
        this(
            deploymentManagedVectorResourceService,
            pineconeControlPlaneClient,
            qdrantCloudControlPlaneClient,
            zillizCloudControlPlaneClient,
            railwayGraphqlClient,
            null,
            null,
            platformSecretService,
            platformAuditService,
            objectMapper,
            httpClient
        );
    }

    DeploymentInfrastructureCleanupService(DeploymentManagedVectorResourceService deploymentManagedVectorResourceService,
                                           PineconeControlPlaneClient pineconeControlPlaneClient,
                                           QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient,
                                           ZillizCloudControlPlaneClient zillizCloudControlPlaneClient,
                                           RailwayGraphqlClient railwayGraphqlClient,
                                           DeploymentProviderResourceHandleRepository providerResourceHandleRepository,
                                           DeploymentProviderRegistry providerRegistry,
                                           PlatformSecretService platformSecretService,
                                           PlatformAuditService platformAuditService,
                                           ObjectMapper objectMapper,
                                           HttpClient httpClient) {
        this.deploymentManagedVectorResourceService = deploymentManagedVectorResourceService;
        this.pineconeControlPlaneClient = pineconeControlPlaneClient;
        this.qdrantCloudControlPlaneClient = qdrantCloudControlPlaneClient;
        this.zillizCloudControlPlaneClient = zillizCloudControlPlaneClient;
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.providerResourceHandleRepository = providerResourceHandleRepository;
        this.providerRegistry = providerRegistry;
        this.platformSecretService = platformSecretService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public DeploymentInfrastructureCleanupResult cleanupForHardDelete(DeploymentEntity deployment,
                                                                      DeploymentReleaseEntity latestRelease,
                                                                      String reason) {
        RailwayCleanupPlan railwayPlan = railwayCleanupPlan(deployment, latestRelease);
        ManagedVectorCleanupResult managedVector = cleanupManagedVectorResources(deployment, reason);
        RailwayCleanupResult railway = cleanupRailwayResources(railwayPlan);
        ProviderResourceCleanupResult providerResources = cleanupProviderResources(deployment, reason);
        List<String> executionSecrets = cleanupDeploymentExecutionSecrets(deployment, reason);
        platformAuditService.record(
            "DEPLOYMENT_HARD_DELETE_INFRASTRUCTURE_CLEANED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "reason", defaultReason(reason),
                "managedVectorResourceCount", managedVector.cleanedResourceIds().size(),
                "deploymentExecutionSecretCount", executionSecrets.size(),
                "railwayDeletedProject", railway.projectDeleted(),
                "railwayDeletedServiceCount", railway.deletedServiceIds().size(),
                "providerResourceDeleteCount", providerResources.deletedHandleIds().size()
            )
        );
        return new DeploymentInfrastructureCleanupResult(managedVector, railway, providerResources, executionSecrets);
    }

    private List<String> cleanupDeploymentExecutionSecrets(DeploymentEntity deployment, String reason) {
        List<String> cleared = new ArrayList<>();
        for (String secretName : DeploymentExecutionSecretService.deploymentManagedSecretNames(deployment.getId())) {
            if (!StringUtils.hasText(platformSecretService.resolveSecret(secretName))) {
                continue;
            }
            platformSecretService.clearManagedSecret(
                secretName,
                Map.of(
                    "deploymentId", deployment.getId(),
                    "reason", defaultReason(reason),
                    "action", "HARD_DELETE_DEPLOYMENT"
                )
            );
            cleared.add(secretName);
        }
        return List.copyOf(cleared);
    }

    private ManagedVectorCleanupResult cleanupManagedVectorResources(DeploymentEntity deployment,
                                                                     String reason) {
        List<DeploymentManagedVectorResourceSummary> resources = deploymentManagedVectorResourceService.listResources(deployment.getId());
        if (resources.isEmpty()) {
            return new ManagedVectorCleanupResult(List.of(), List.of());
        }

        List<DeploymentManagedVectorResourceSummary> orderedResources = resources.stream()
            .sorted((left, right) -> Integer.compare(cleanupOrder(left), cleanupOrder(right)))
            .toList();
        if (ownership==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_OWNERSHIP_REQUIRED");
        Map<String,ManagedResourceOwnershipService.Receipt> receipts=new LinkedHashMap<>();
        for (var resource:orderedResources) {
            var scope=ownership.scope(deployment,ownership.releaseTarget(resource.deploymentReleaseId()));
            receipts.put(resource.id(),ownership.requireReceipt(scope,resource.details().path("ownershipReceiptId").asText(),
                resource.vendor(),resource.resourceType(),"COLLECTION".equals(resource.resourceType())?resource.resourceName():resource.resourceReference(),
                resource.endpoint(),true));
        }
        // The generated Cloud key is collection-scoped; it cannot prove a complete root inventory.
        for (var resource:orderedResources) {
            if ("qdrant".equals(resource.vendor()) && "CLUSTER".equals(resource.resourceType()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_COMPLETE_CHILD_INVENTORY_REQUIRED");
        }
        List<String> cleanedIds = new ArrayList<>();
        Set<String> candidateManagedSecrets = new LinkedHashSet<>();
        Map<String, String> failures = new LinkedHashMap<>();

        for (DeploymentManagedVectorResourceSummary resource : orderedResources) {
            try {
                var receipt=receipts.get(resource.id());
                ownership.beginDeletion(receipt);
                cleanupManagedVectorResource(resource, orderedResources,receipt);
                ownership.deleted(receipt.id());
                cleanedIds.add(resource.id());
                resource.secretReferenceNames().stream()
                    .filter(platformSecretService::isManagedSecretName)
                    .forEach(candidateManagedSecrets::add);
            } catch (RuntimeException ex) {
                failures.put(resource.id(), ex.getMessage());
            }
        }

        if (!cleanedIds.isEmpty()) {
            deploymentManagedVectorResourceService.deleteResourceRecords(cleanedIds);
            for (String secretName : candidateManagedSecrets) {
                if (!deploymentManagedVectorResourceService.managedSecretReferencedByActiveResource(secretName)) {
                    platformSecretService.clearManagedSecret(
                        secretName,
                        Map.of(
                            "deploymentId", deployment.getId(),
                            "reason", defaultReason(reason),
                            "action", "HARD_DELETE_DEPLOYMENT"
                        )
                    );
                }
            }
        }

        if (!failures.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Managed vector cleanup failed before hard delete: " + String.join(" | ", failures.values())
            );
        }

        return new ManagedVectorCleanupResult(List.copyOf(cleanedIds), List.copyOf(candidateManagedSecrets));
    }

    private record RailwayCleanupPlan(ManagedResourceOwnershipService.Scope scope, String projectId,String environmentId,
                                      Map<String,ManagedResourceOwnershipService.Receipt> services) { }

    private RailwayCleanupPlan railwayCleanupPlan(DeploymentEntity deployment, DeploymentReleaseEntity latestRelease) {
        JsonNode details = readJson(latestRelease == null ? null : latestRelease.getProvisioningDetailsJson());
        String projectId = firstNonBlank(text(details, "railway", "projectId"), text(details, "projectId"));
        var requested=new LinkedHashSet<String>();
        for (String role:List.of("runtime","restConnector","vectorizationRunner","embeddingWorker")) {
            String id=text(details,"railway","services",role,"serviceId");
            if (hasText(id)) requested.add(id);
        }
        if (requested.isEmpty() && !hasText(projectId)) return null;
        if (ownership==null || latestRelease==null || !hasText(projectId))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_OWNERSHIP_REQUIRED");
        var scope=ownership.scope(deployment,latestRelease.getTargetProfileId());
        ownership.requireOwned(scope,"railway","PROJECT",projectId,null);
        String environmentId=text(details,"railway","environmentId");
        if (!projectId.equals(ownership.requireOwned(scope,"railway","ENVIRONMENT",environmentId,null).parent()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_LOCATION_CHANGED");
        var receipts=new LinkedHashMap<String,ManagedResourceOwnershipService.Receipt>();
        for (String id:requested) {
            String receiptId=text(details,"railway","ownership",id);
            var receipt=ownership.requireReceipt(scope,receiptId,"railway","SERVICE",id,null,true);
            if (!projectId.equals(receipt.parent()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_LOCATION_CHANGED");
            receipts.put(id,receipt);
        }
        requireSingleRailwayEnvironment(getProjectIfPresent(projectId,"inspect owned Railway project"),environmentId);
        return new RailwayCleanupPlan(scope,projectId,environmentId,receipts);
    }

    private void requireSingleRailwayEnvironment(RailwayGraphqlClient.RailwayProjectSnapshot project,String environmentId) {
        if (project!=null && (project.environments().size()!=1 || !environmentId.equals(project.environments().getFirst().id())))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"RAILWAY_SHARED_ENVIRONMENT_RECONCILIATION_REQUIRED");
    }

    private RailwayCleanupResult cleanupRailwayResources(RailwayCleanupPlan plan) {
        if (plan==null) return new RailwayCleanupResult(false,List.of());
        var deleted=new ArrayList<String>();
        for (var entry:plan.services().entrySet()) {
            var receipt=entry.getValue();
            ownership.beginDeletion(receipt);
            var project=getProjectIfPresent(plan.projectId(),"recheck owned Railway service");
            // Service deletion affects every environment; any additional environment makes it unsafe.
            requireSingleRailwayEnvironment(project,plan.environmentId());
            boolean present=project!=null && project.services().stream().anyMatch(value->entry.getKey().equals(value.id()));
            if (present) {
                requireInitialDeletionAttempt(receipt);
                deleteRailwayServiceIfPresent(entry.getKey(),"owned",plan.scope().deploymentId());
                var observed=getProjectIfPresent(plan.projectId(),"verify owned Railway service absence");
                if (observed!=null && observed.services().stream().anyMatch(value->entry.getKey().equals(value.id())))
                    throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_DELETE_UNSETTLED");
            }
            ownership.deleted(receipt.id());
            deleted.add(entry.getKey());
        }
        // Container ownership never implies permission to delete untracked services or environments.
        return new RailwayCleanupResult(false,List.copyOf(deleted));
    }

    private ProviderResourceCleanupResult cleanupProviderResources(DeploymentEntity deployment, String reason) {
        if (providerResourceHandleRepository == null || providerRegistry == null) {
            return new ProviderResourceCleanupResult(List.of(), Map.of());
        }
        List<DeploymentProviderResourceHandleEntity> handles =
            providerResourceHandleRepository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId());
        if (handles.isEmpty()) {
            return new ProviderResourceCleanupResult(List.of(), Map.of());
        }
        for (DeploymentProviderResourceHandleEntity handle : handles) {
            if (!PreservedExternalBindingKinds.isPreserved(handle.getResourceKind())
                && (handle.getResourceKind() == null || !DELETABLE_PROVIDER_RESOURCE_KINDS.contains(handle.getResourceKind()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provider resource cleanup requires a supported owned resource kind.");
            }
        }

        List<String> deletedHandleIds = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();
        for (DeploymentProviderResourceHandleEntity handle : handles) {
            if (PreservedExternalBindingKinds.isPreserved(handle.getResourceKind())) {
                continue;
            }
            try {
                providerRegistry.require(handle.getProviderType()).delete(handle, defaultReason(reason));
                deletedHandleIds.add(handle.getId());
            } catch (UnsupportedOperationException ex) {
                log.debug(
                    "Provider resource delete is not supported during hard delete: deploymentId={}, handleId={}, providerType={}",
                    deployment.getId(),
                    handle.getId(),
                    handle.getProviderType()
                );
            } catch (RuntimeException ex) {
                failures.put(handle.getId(), ex.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Provider resource cleanup failed before hard delete: " + String.join(" | ", failures.values())
            );
        }
        return new ProviderResourceCleanupResult(List.copyOf(deletedHandleIds), Map.copyOf(failures));
    }

    private void cleanupManagedVectorResource(DeploymentManagedVectorResourceSummary resource,
                                              List<DeploymentManagedVectorResourceSummary> allResources,
                                              ManagedResourceOwnershipService.Receipt receipt) {
        if ("pinecone".equalsIgnoreCase(resource.vendor())
            && "MANAGED_SERVERLESS_INDEX".equalsIgnoreCase(resource.managedMode())
            && "INDEX".equalsIgnoreCase(resource.resourceType())) {
            cleanupPineconeIndex(resource,receipt);
            return;
        }
        if ("qdrant".equalsIgnoreCase(resource.vendor())
            && "COLLECTION".equalsIgnoreCase(resource.resourceType())) {
            cleanupQdrantCollection(resource,receipt);
            return;
        }
        if ("qdrant".equalsIgnoreCase(resource.vendor())
            && "MANAGED_CLOUD_CLUSTER".equalsIgnoreCase(resource.managedMode())
            && "DATABASE_API_KEY".equalsIgnoreCase(resource.resourceType())) {
            cleanupQdrantDatabaseApiKey(resource, allResources,receipt);
            return;
        }
        if ("qdrant".equalsIgnoreCase(resource.vendor())
            && "MANAGED_CLOUD_CLUSTER".equalsIgnoreCase(resource.managedMode())
            && "CLUSTER".equalsIgnoreCase(resource.resourceType())) {
            cleanupQdrantCluster(resource,receipt);
            return;
        }
        if ("zilliz".equalsIgnoreCase(resource.vendor())
            && "MANAGED_ZILLIZ_CLOUD_CLUSTER".equalsIgnoreCase(resource.managedMode())
            && "CLUSTER".equalsIgnoreCase(resource.resourceType())) {
            cleanupZillizCluster(resource,receipt);
            return;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_KIND_UNSUPPORTED");
    }

    private void cleanupPineconeIndex(DeploymentManagedVectorResourceSummary resource,ManagedResourceOwnershipService.Receipt receipt) {
        String apiKey = trimToNull(platformSecretService.resolveSecret("PINECONE_API_KEY"));
        if (!hasText(apiKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PINECONE_API_KEY must be configured before hard delete can remove Pinecone indexes.");
        }
        String indexName = firstNonBlank(resource.resourceName(), resource.resourceReference());
        PineconeControlPlaneClient.PineconeIndexSummary existing = pineconeControlPlaneClient.findIndexByName(indexName, apiKey);
        if (existing == null) {
            return;
        }
        requireLiveIdentity(receipt,existing.name(),existing.host());
        requireEmptyPineconeIndex(existing.host(),apiKey);
        if ("enabled".equalsIgnoreCase(existing.deletionProtection())) {
            pineconeControlPlaneClient.configureDeletionProtection(indexName, false, apiKey);
        }
        pineconeControlPlaneClient.deleteIndex(indexName, apiKey);
        pineconeControlPlaneClient.awaitIndexDeleted(indexName, apiKey);
    }

    private void cleanupQdrantCollection(DeploymentManagedVectorResourceSummary resource,ManagedResourceOwnershipService.Receipt receipt) {
        String collectionName = trimToNull(resource.resourceName());
        String baseUrl = resource.endpoint();
        if (!java.util.Objects.equals(baseUrl,receipt.parent()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_LOCATION_CHANGED");
        if (!hasText(collectionName) || !hasText(baseUrl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Qdrant collection cleanup requires both collection name and base URL.");
        }
        String apiKey = resolveQdrantDataPlaneKey(resource);
        if (!qdrantCollectionExists(baseUrl, collectionName, apiKey)) {
            return;
        }
        requireLiveIdentity(receipt,collectionName,baseUrl);
        deleteQdrantCollection(baseUrl, collectionName, apiKey);
        if (qdrantCollectionExists(baseUrl,collectionName,apiKey)) throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_DELETE_UNSETTLED");
    }

    private void cleanupQdrantDatabaseApiKey(DeploymentManagedVectorResourceSummary resource,
                                             List<DeploymentManagedVectorResourceSummary> allResources,ManagedResourceOwnershipService.Receipt receipt) {
        String managementApiKey = trimToNull(platformSecretService.resolveSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY"));
        if (!hasText(managementApiKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QDRANT_CLOUD_MANAGEMENT_API_KEY must be configured before hard delete can remove Qdrant Cloud resources.");
        }
        JsonNode details = resource.details();
        String accountId = firstNonBlank(text(details, "accountId"), qdrantAccountIdFromClusterResource(allResources));
        String clusterId = firstNonBlank(text(details, "clusterId"), clusterIdFromQdrantClusterResource(allResources));
        String keyName = firstNonBlank(text(details, "databaseApiKeyName"), resource.resourceName());
        if (!hasText(accountId) || !hasText(clusterId) || !hasText(keyName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Qdrant database API key cleanup is missing account, cluster, or key identifiers.");
        }
        QdrantCloudControlPlaneClient.QdrantCloudDatabaseApiKeySummary existing =
            qdrantCloudControlPlaneClient.findDatabaseApiKeyByName(accountId, clusterId, keyName, managementApiKey);
        if (existing == null) {
            return;
        }
        var cluster=qdrantCloudControlPlaneClient.getCluster(accountId,clusterId,managementApiKey);
        if (cluster==null || !clusterId.equals(existing.clusterId()) || !clusterId.equals(cluster.id())
            || !java.util.Objects.equals(receipt.parent(),normalizeEndpoint(cluster.endpointUrl())))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_LOCATION_CHANGED");
        requireLiveIdentity(receipt,existing.id(),resource.endpoint());
        qdrantCloudControlPlaneClient.deleteDatabaseApiKey(accountId, clusterId, existing.id(), managementApiKey);
        if (qdrantCloudControlPlaneClient.findDatabaseApiKeyByName(accountId,clusterId,keyName,managementApiKey)!=null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_DELETE_UNSETTLED");
    }

    private void cleanupQdrantCluster(DeploymentManagedVectorResourceSummary resource,ManagedResourceOwnershipService.Receipt receipt) {
        // Collection-scoped keys cannot establish that no unrelated collections exist on the root.
        throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_COMPLETE_CHILD_INVENTORY_REQUIRED");
    }

    private void cleanupZillizCluster(DeploymentManagedVectorResourceSummary resource,ManagedResourceOwnershipService.Receipt receipt) {
        String apiKey = trimToNull(platformSecretService.resolveSecret("ZILLIZ_CLOUD_API_KEY"));
        if (!hasText(apiKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZILLIZ_CLOUD_API_KEY must be configured before hard delete can remove Zilliz Cloud resources.");
        }
        String clusterId = firstNonBlank(text(resource.details(), "clusterId"), resource.resourceReference());
        if (!hasText(clusterId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zilliz Cloud cluster cleanup is missing the cluster identifier.");
        }
        var live=zillizCloudControlPlaneClient.getCluster(clusterId, apiKey);
        if (live == null) {
            return;
        }
        requireLiveIdentity(receipt,live.clusterId(),normalizeEndpoint(live.connectAddress()));
        if (!receipt.parent().equals(text(resource.details(),"projectId")) || !receipt.parent().equals(live.projectId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_LOCATION_CHANGED");
        String username=platformSecretService.resolveSecret(text(resource.details(),"runtimeUsernameSecretName"));
        String password=platformSecretService.resolveSecret(text(resource.details(),"runtimePasswordSecretName"));
        if (!hasText(username) || !hasText(password))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_COMPLETE_CHILD_INVENTORY_REQUIRED");
        var collections=zillizCloudControlPlaneClient.listServingCollections(receipt.endpoint(),username,password);
        if (collections==null || !collections.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_CHILDREN_RETAINED");
        try {
            zillizCloudControlPlaneClient.deleteCluster(clusterId, apiKey);
        } catch (RailwayProvisioningException ex) {
            if (isZillizDeleteAlreadySatisfied(ex) && zillizCloudControlPlaneClient.getCluster(clusterId,apiKey)==null) {
                return;
            }
            throw ex;
        }
        try {
            zillizCloudControlPlaneClient.awaitClusterDeleted(clusterId, apiKey);
        } catch (RailwayProvisioningException ex) {
            if (isZillizDeleteAlreadySatisfied(ex) && zillizCloudControlPlaneClient.getCluster(clusterId,apiKey)==null) {
                return;
            }
            ZillizCloudControlPlaneClient.ZillizClusterSummary snapshot = zillizCloudControlPlaneClient.getCluster(clusterId, apiKey);
            if (snapshot == null) {
                return;
            }
            throw ex;
        }
    }

    private void requireLiveIdentity(ManagedResourceOwnershipService.Receipt receipt,String id,String endpoint) {
        requireInitialDeletionAttempt(receipt);
        if (!receipt.resourceId().equals(id)
            || !java.util.Objects.equals(receipt.endpoint(),endpoint))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_IDENTITY_CHANGED");
    }

    private void requireInitialDeletionAttempt(ManagedResourceOwnershipService.Receipt receipt) {
        // A retry may confirm absence, but must not delete a resource that reappeared after an uncertain outcome.
        if (!"OWNED".equals(receipt.state()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_DELETE_RECONCILIATION_REQUIRED");
    }

    private String normalizeEndpoint(String value) { return value!=null && value.endsWith("/")?value.substring(0,value.length()-1):value; }

    private void requireEmptyPineconeIndex(String endpoint,String apiKey) {
        HttpRequest request=HttpRequest.newBuilder(URI.create(endpoint+"/describe_index_stats"))
            .timeout(Duration.ofSeconds(30)).header("Api-Key",apiKey).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        var response=send(request);
        JsonNode stats=readJson(response.body());
        if (response.statusCode()!=200 || !stats.path("namespaces").isObject() || !stats.path("namespaces").isEmpty()
            || !stats.path("totalVectorCount").isIntegralNumber() || stats.path("totalVectorCount").longValue()!=0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"MANAGED_RESOURCE_CHILDREN_RETAINED");
    }

    private boolean isZillizDeleteAlreadySatisfied(RuntimeException ex) {
        String message = ex == null ? null : ex.getMessage();
        if (!hasText(message)) {
            return false;
        }
        String normalized = message.toUpperCase();
        if (!normalized.contains("40064")) {
            return false;
        }
        return normalized.contains("STATUS IS DELETED")
            || normalized.contains("INSTANCE STATUS IS DELETED")
            || normalized.contains("ALREADY DELETED");
    }

    private boolean qdrantCollectionExists(String baseUrl,
                                           String collectionName,
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
        return true;
    }

    private void deleteQdrantCollection(String baseUrl,
                                        String collectionName,
                                        String apiKey) {
        HttpRequest request = qdrantRequestBuilder(qdrantCollectionUri(baseUrl, collectionName), apiKey)
            .DELETE()
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            return;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RailwayProvisioningException(
                "Qdrant delete collection failed for '" + collectionName + "' with HTTP " + response.statusCode()
            );
        }
    }

    private String resolveQdrantDataPlaneKey(DeploymentManagedVectorResourceSummary resource) {
        for (String secretName : resource.secretReferenceNames()) {
            String resolved = trimToNull(platformSecretService.resolveSecret(secretName));
            if (hasText(resolved)) {
                return resolved;
            }
        }
        return trimToNull(platformSecretService.resolveSecret("QDRANT_API_KEY"));
    }

    private HttpRequest.Builder qdrantRequestBuilder(String uri,
                                                     String apiKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json");
        if (hasText(apiKey)) {
            builder.header("api-key", apiKey);
        }
        return builder;
    }

    private String qdrantCollectionUri(String baseUrl, String collectionName) {
        String normalizedBaseUrl = baseUrl != null && baseUrl.endsWith("/")
            ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;
        return normalizedBaseUrl + "/collections/" + encodePathSegment(collectionName);
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new RailwayProvisioningException("Qdrant data-plane request failed.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RailwayProvisioningException("Qdrant data-plane request was interrupted.", ex);
        }
    }

    private RailwayGraphqlClient.RailwayProjectSnapshot getProjectIfPresent(String projectId,
                                                                            String operationDescription) {
        try {
            return railwayGraphqlClient.getProject(projectId);
        } catch (RailwayProvisioningException ex) {
            if (isRailwayNotFound(ex)) {
                return null;
            }
            throw contextualRailwayFailure("Failed to " + operationDescription + ".", ex);
        }
    }

    private boolean deleteRailwayServiceIfPresent(String serviceId,
                                                  String serviceRole,
                                                  String deploymentId) {
        try {
            railwayGraphqlClient.deleteService(serviceId);
            return true;
        } catch (RailwayProvisioningException ex) {
            if (isRailwayNotFound(ex)) {
                return false;
            }
            throw contextualRailwayFailure(
                "Failed to delete Railway " + serviceRole + " service '" + serviceId
                    + "' during hard delete for deployment '" + deploymentId + "'.",
                ex
            );
        }
    }

    private RailwayProvisioningException contextualRailwayFailure(String context,
                                                                  RailwayProvisioningException cause) {
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return new RailwayProvisioningException(context, cause);
        }
        return new RailwayProvisioningException(context + " " + message, cause);
    }

    private boolean isRailwayNotFound(RailwayProvisioningException ex) {
        String message = ex.getMessage();
        return message != null && (message.contains("HTTP 404") || message.toLowerCase().contains("not found"));
    }

    private String qdrantAccountIdFromClusterResource(List<DeploymentManagedVectorResourceSummary> resources) {
        return resources.stream()
            .filter(candidate -> "qdrant".equalsIgnoreCase(candidate.vendor()))
            .filter(candidate -> "CLUSTER".equalsIgnoreCase(candidate.resourceType()))
            .map(candidate -> text(candidate.details(), "accountId"))
            .filter(this::hasText)
            .findFirst()
            .orElse(null);
    }

    private String clusterIdFromQdrantClusterResource(List<DeploymentManagedVectorResourceSummary> resources) {
        return resources.stream()
            .filter(candidate -> "qdrant".equalsIgnoreCase(candidate.vendor()))
            .filter(candidate -> "CLUSTER".equalsIgnoreCase(candidate.resourceType()))
            .map(candidate -> firstNonBlank(text(candidate.details(), "clusterId"), candidate.resourceReference()))
            .filter(this::hasText)
            .findFirst()
            .orElse(null);
    }

    private int cleanupOrder(DeploymentManagedVectorResourceSummary resource) {
        if ("qdrant".equalsIgnoreCase(resource.vendor())
            && "COLLECTION".equalsIgnoreCase(resource.resourceType())) {
            return 0;
        }
        if ("qdrant".equalsIgnoreCase(resource.vendor())
            && "DATABASE_API_KEY".equalsIgnoreCase(resource.resourceType())) {
            return 1;
        }
        if ("pinecone".equalsIgnoreCase(resource.vendor())
            && "INDEX".equalsIgnoreCase(resource.resourceType())) {
            return 2;
        }
        if ("qdrant".equalsIgnoreCase(resource.vendor())
            && "CLUSTER".equalsIgnoreCase(resource.resourceType())) {
            return 3;
        }
        if ("zilliz".equalsIgnoreCase(resource.vendor())
            && "CLUSTER".equalsIgnoreCase(resource.resourceType())) {
            return 4;
        }
        return 5;
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse provisioning details JSON.", ex);
        }
    }

    private String text(JsonNode node, String... path) {
        JsonNode current = node;
        for (String field : path) {
            if (current == null) {
                return null;
            }
            current = current.path(field);
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        String value = current.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private String defaultReason(String reason) {
        return hasText(reason) ? reason.trim() : "hard delete";
    }

    record ManagedVectorCleanupResult(
        List<String> cleanedResourceIds,
        List<String> clearedManagedSecrets
    ) {
    }

    record RailwayCleanupResult(
        boolean projectDeleted,
        List<String> deletedServiceIds
    ) {
    }

    record ProviderResourceCleanupResult(
        List<String> deletedHandleIds,
        Map<String, String> failures
    ) {
    }

    public record DeploymentInfrastructureCleanupResult(
        ManagedVectorCleanupResult managedVector,
        RailwayCleanupResult railway,
        ProviderResourceCleanupResult providerResources,
        List<String> clearedDeploymentExecutionSecrets
    ) {
    }
}
