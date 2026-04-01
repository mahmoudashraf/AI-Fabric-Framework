package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentManagedVectorProvisioningServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    @Test
    void ensureProvisionedCreatesManagedPineconeIndexAndInjectsResolvedHost() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("PINECONE_API_KEY")).thenReturn("pinecone-secret");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> describeMissing = mock(HttpResponse.class);
        when(describeMissing.statusCode()).thenReturn(404);

        HttpResponse<String> createAccepted = mock(HttpResponse.class);
        when(createAccepted.statusCode()).thenReturn(201);
        when(createAccepted.body()).thenReturn("{\"name\":\"dep-123\"}");

        HttpResponse<String> describeReady = mock(HttpResponse.class);
        when(describeReady.statusCode()).thenReturn(200);
        when(describeReady.body()).thenReturn("""
            {
              "name": "dep-123",
              "host": "dep-123-abc123.svc.eu-west-1-aws.pinecone.io",
              "dimension": 1024,
              "metric": "cosine",
              "status": { "ready": true }
            }
            """);

        when(httpClient.<String>send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().endsWith("/indexes/dep-123")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(describeMissing, describeReady);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "POST".equals(request.method())
                && request.uri().toString().endsWith("/indexes")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(createAccepted);

        DeploymentManagedVectorProvisioningService service = new DeploymentManagedVectorProvisioningService(
            secretService,
            objectMapper,
            httpClient
        );

        JsonNode providerConfig = objectMapper.readTree("""
            {
              "embeddingProvider": "azure",
              "vectorStrategy": "pinecone",
              "vectorProvisioningMode": "PLATFORM_MANAGED",
              "pineconeManagedIndexEnabled": true,
              "pineconeIndexName": "dep-123",
              "pineconeCloud": "aws",
              "pineconeRegion": "eu-west-1",
              "pineconeMetric": "cosine",
              "pineconeDimensions": 1024
            }
            """);
        JsonNode entityConfig = objectMapper.readTree("""
            {
              "ai-config": { "vector-dimensions": 1024 },
              "ai-entities": { "product": {}, "review": {} }
            }
            """);

        ManagedVectorProvisioningResult result = service.ensureProvisioned("dep-123", providerConfig, entityConfig);

        assertThat(result.effectiveProviderConfig().path("pineconeApiHost").asText())
            .isEqualTo("https://dep-123-abc123.svc.eu-west-1-aws.pinecone.io");
        assertThat(result.effectiveProviderConfig().path("pineconeRuntimeApiKeySecretName").asText())
            .isEqualTo("MANAGED_PINECONE_API_KEY_DEP_DEP_123");
        assertThat(result.details().path("mode").asText()).isEqualTo("MANAGED_SERVERLESS_INDEX");
        assertThat(result.details().path("state").asText()).isEqualTo("CREATED");
        assertThat(result.details().path("ready").asBoolean()).isTrue();
        verify(secretService).upsertManagedSecret(
            eq("MANAGED_PINECONE_API_KEY_DEP_DEP_123"),
            eq("pinecone-secret"),
            any(Map.class)
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void ensureProvisionedCreatesManagedQdrantCollectionsForEntityTypes() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("QDRANT_API_KEY")).thenReturn("qdrant-secret");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> missing = mock(HttpResponse.class);
        when(missing.statusCode()).thenReturn(404);
        HttpResponse<String> created = mock(HttpResponse.class);
        when(created.statusCode()).thenReturn(200);
        when(created.body()).thenReturn("{\"status\":\"ok\"}");

        when(httpClient.<String>send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().contains("/collections/")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(missing, missing);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "PUT".equals(request.method())
                && request.uri().toString().contains("/collections/")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(created, created);

        DeploymentManagedVectorProvisioningService service = new DeploymentManagedVectorProvisioningService(
            secretService,
            objectMapper,
            httpClient
        );

        JsonNode providerConfig = objectMapper.readTree("""
            {
              "embeddingProvider": "openai",
              "vectorStrategy": "qdrant",
              "qdrantHost": "https://cluster.example",
              "qdrantManagedCollectionsEnabled": true
            }
            """);
        JsonNode entityConfig = objectMapper.readTree("""
            {
              "ai-config": { "vector-dimensions": 1536 },
              "ai-entities": { "product": {}, "policy": {} }
            }
            """);

        ManagedVectorProvisioningResult result = service.ensureProvisioned("dep-123", providerConfig, entityConfig);

        assertThat(result.details().path("mode").asText()).isEqualTo("MANAGED_COLLECTIONS");
        assertThat(result.details().path("baseUrl").asText()).isEqualTo("https://cluster.example");
        assertThat(result.details().path("collections")).hasSize(2);
        assertThat(result.details().path("collections").get(0).path("state").asText()).isEqualTo("CREATED");
    }

    @SuppressWarnings("unchecked")
    @Test
    void ensureProvisionedCreatesManagedQdrantCloudClusterAndStoresRuntimeKey() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY")).thenReturn("qdrant-cloud-management");
        when(secretService.resolveSecret("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_123")).thenReturn(null);

        QdrantCloudControlPlaneClient qdrantCloudClient = mock(QdrantCloudControlPlaneClient.class);
        when(qdrantCloudClient.resolveAccount("", "qdrant-cloud-management")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudAccountResolution("acct-1", "Primary", true)
        );
        when(qdrantCloudClient.requireRegion("aws", "eu-west-1")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudRegionSummary("eu-west-1", "EU West 1", "aws", true)
        );
        when(qdrantCloudClient.resolvePackage("acct-1", "qdrant-cloud-management", "aws", "eu-west-1", "")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudPackageSummary("pkg-1", "Sandbox", "sandbox", "USD", 1000, "PACKAGE_STATUS_ACTIVE", "2GB", "shared", "10GB")
        );
        when(qdrantCloudClient.findClusterByName("acct-1", "aifabric-123", "qdrant-cloud-management")).thenReturn(null);
        when(qdrantCloudClient.createCluster("acct-1", "dep-123", "aifabric-123", "aws", "eu-west-1", "pkg-1", "qdrant-cloud-management")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudClusterSummary(
                "cluster-1",
                "acct-1",
                "aifabric-123",
                "aws",
                "eu-west-1",
                "pkg-1",
                "CLUSTER_PHASE_CREATING",
                "",
                "",
                6333,
                6334,
                Map.of("managed-by", "ai-fabric-platform")
            )
        );
        when(qdrantCloudClient.awaitClusterReady("acct-1", "cluster-1", "qdrant-cloud-management")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudClusterSummary(
                "cluster-1",
                "acct-1",
                "aifabric-123",
                "aws",
                "eu-west-1",
                "pkg-1",
                "CLUSTER_PHASE_HEALTHY",
                "",
                "https://cluster-1.eu-west-1.aws.cloud.qdrant.io",
                6333,
                6334,
                Map.of("managed-by", "ai-fabric-platform")
            )
        );
        when(qdrantCloudClient.findDatabaseApiKeyByName("acct-1", "cluster-1", "ai-fabric-123", "qdrant-cloud-management")).thenReturn(null);
        when(qdrantCloudClient.createCollectionDatabaseApiKey(
            "acct-1",
            "cluster-1",
            "ai-fabric-123",
            List.of("product", "policy"),
            "qdrant-cloud-management"
        )).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudDatabaseApiKeySummary(
                "key-1",
                "cluster-1",
                "ai-fabric-123",
                "abcd",
                "runtime-qdrant-key"
            )
        );

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> missing = mock(HttpResponse.class);
        when(missing.statusCode()).thenReturn(404);
        HttpResponse<String> created = mock(HttpResponse.class);
        when(created.statusCode()).thenReturn(200);
        when(created.body()).thenReturn("{\"status\":\"ok\"}");
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().contains("/collections/")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(missing, missing);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "PUT".equals(request.method())
                && request.uri().toString().contains("/collections/")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(created, created);

        DeploymentManagedVectorProvisioningService service = new DeploymentManagedVectorProvisioningService(
            secretService,
            objectMapper,
            httpClient,
            qdrantCloudClient
        );

        JsonNode providerConfig = objectMapper.readTree("""
            {
              "embeddingProvider": "openai",
              "vectorStrategy": "qdrant",
              "vectorProvisioningMode": "PLATFORM_MANAGED",
              "qdrantCloudProviderId": "aws",
              "qdrantCloudRegionId": "eu-west-1"
            }
            """);
        JsonNode entityConfig = objectMapper.readTree("""
            {
              "ai-config": { "vector-dimensions": 1536 },
              "ai-entities": { "product": {}, "policy": {} }
            }
            """);

        ManagedVectorProvisioningResult result = service.ensureProvisioned("dep-123", providerConfig, entityConfig);

        assertThat(result.details().path("mode").asText()).isEqualTo("MANAGED_CLOUD_CLUSTER");
        assertThat(result.effectiveProviderConfig().path("qdrantHost").asText())
            .isEqualTo("https://cluster-1.eu-west-1.aws.cloud.qdrant.io");
        assertThat(result.effectiveProviderConfig().path("qdrantRuntimeApiKeySecretName").asText())
            .isEqualTo("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_123");
        verify(secretService).upsertManagedSecret(
            eq("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_123"),
            eq("runtime-qdrant-key"),
            any(Map.class)
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void ensureProvisionedReusesExistingQdrantCloudClusterEvenIfPackageIsNoLongerActive() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY")).thenReturn("qdrant-cloud-management");
        when(secretService.resolveSecret("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_123")).thenReturn("existing-runtime-qdrant-key");

        QdrantCloudControlPlaneClient qdrantCloudClient = mock(QdrantCloudControlPlaneClient.class);
        when(qdrantCloudClient.resolveAccount("", "qdrant-cloud-management")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudAccountResolution("acct-1", "Primary", true)
        );
        when(qdrantCloudClient.requireRegion("aws", "eu-west-1")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudRegionSummary("eu-west-1", "EU West 1", "aws", true)
        );
        when(qdrantCloudClient.resolvePackage("acct-1", "qdrant-cloud-management", "aws", "eu-west-1", "")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudPackageSummary("pkg-new", "Sandbox", "sandbox", "USD", 1000, "PACKAGE_STATUS_ACTIVE", "2GB", "shared", "10GB")
        );
        when(qdrantCloudClient.findClusterByName("acct-1", "platform-deployments", "qdrant-cloud-management")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudClusterSummary(
                "cluster-1",
                "acct-1",
                "Platform-Deployments",
                "aws",
                "eu-west-1",
                "pkg-old",
                "CLUSTER_PHASE_HEALTHY",
                "",
                "https://cluster-1.eu-west-1.aws.cloud.qdrant.io",
                6333,
                6334,
                Map.of("managed-by", "ai-fabric-platform")
            )
        );
        when(qdrantCloudClient.awaitClusterReady("acct-1", "cluster-1", "qdrant-cloud-management")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudClusterSummary(
                "cluster-1",
                "acct-1",
                "Platform-Deployments",
                "aws",
                "eu-west-1",
                "pkg-old",
                "CLUSTER_PHASE_HEALTHY",
                "",
                "https://cluster-1.eu-west-1.aws.cloud.qdrant.io",
                6333,
                6334,
                Map.of("managed-by", "ai-fabric-platform")
            )
        );
        when(qdrantCloudClient.findDatabaseApiKeyByName("acct-1", "cluster-1", "ai-fabric-123", "qdrant-cloud-management")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudDatabaseApiKeySummary(
                "key-1",
                "cluster-1",
                "ai-fabric-123",
                "abcd",
                ""
            )
        );

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> existingCollection = mock(HttpResponse.class);
        when(existingCollection.statusCode()).thenReturn(200);
        when(existingCollection.body()).thenReturn("""
            {
              "result": {
                "config": {
                  "params": {
                    "vectors": {
                      "size": 1536
                    }
                  }
                }
              }
            }
            """);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().contains("/collections/")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(existingCollection);

        DeploymentManagedVectorProvisioningService service = new DeploymentManagedVectorProvisioningService(
            secretService,
            objectMapper,
            httpClient,
            qdrantCloudClient
        );

        JsonNode providerConfig = objectMapper.readTree("""
            {
              "embeddingProvider": "openai",
              "vectorStrategy": "qdrant",
              "vectorProvisioningMode": "PLATFORM_MANAGED",
              "qdrantCloudProviderId": "aws",
              "qdrantCloudRegionId": "eu-west-1",
              "qdrantCloudClusterNameOverride": "Platform-Deployments"
            }
            """);
        JsonNode entityConfig = objectMapper.readTree("""
            {
              "ai-config": { "vector-dimensions": 1536 },
              "ai-entities": { "product": {} }
            }
            """);

        ManagedVectorProvisioningResult result = service.ensureProvisioned("dep-123", providerConfig, entityConfig);

        assertThat(result.details().path("clusterState").asText()).isEqualTo("REUSED");
        assertThat(result.details().path("packageId").asText()).isEqualTo("pkg-old");
        assertThat(result.details().path("requestedActivePackageId").asText()).isEqualTo("pkg-new");
        assertThat(result.details().path("packageResolution").asText()).isEqualTo("REUSED_EXISTING_CLUSTER_PACKAGE");
        assertThat(result.effectiveProviderConfig().path("qdrantCloudPackageId").asText()).isEqualTo("pkg-old");
        assertThat(result.effectiveProviderConfig().path("qdrantRuntimeApiKeySecretName").asText())
            .isEqualTo("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_123");
    }

    @SuppressWarnings("unchecked")
    @Test
    void ensureProvisionedRotatesExistingQdrantDatabaseKeyWhenLegacyPermissionsBlockCollectionProvisioning() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY")).thenReturn("qdrant-cloud-management");
        when(secretService.resolveSecret("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_123")).thenReturn("legacy-runtime-qdrant-key");

        QdrantCloudControlPlaneClient qdrantCloudClient = mock(QdrantCloudControlPlaneClient.class);
        when(qdrantCloudClient.resolveAccount("", "qdrant-cloud-management")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudAccountResolution("acct-1", "Primary", true)
        );
        when(qdrantCloudClient.requireRegion("aws", "eu-west-1")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudRegionSummary("eu-west-1", "EU West 1", "aws", true)
        );
        when(qdrantCloudClient.resolvePackage("acct-1", "qdrant-cloud-management", "aws", "eu-west-1", "")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudPackageSummary("pkg-1", "Sandbox", "sandbox", "USD", 1000, "PACKAGE_STATUS_ACTIVE", "2GB", "shared", "10GB")
        );
        when(qdrantCloudClient.findClusterByName("acct-1", "aifabric-123", "qdrant-cloud-management")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudClusterSummary(
                "cluster-1",
                "acct-1",
                "aifabric-123",
                "aws",
                "eu-west-1",
                "pkg-1",
                "CLUSTER_PHASE_HEALTHY",
                "",
                "https://cluster-1.eu-west-1.aws.cloud.qdrant.io",
                6333,
                6334,
                Map.of("managed-by", "ai-fabric-platform")
            )
        );
        when(qdrantCloudClient.awaitClusterReady("acct-1", "cluster-1", "qdrant-cloud-management")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudClusterSummary(
                "cluster-1",
                "acct-1",
                "aifabric-123",
                "aws",
                "eu-west-1",
                "pkg-1",
                "CLUSTER_PHASE_HEALTHY",
                "",
                "https://cluster-1.eu-west-1.aws.cloud.qdrant.io",
                6333,
                6334,
                Map.of("managed-by", "ai-fabric-platform")
            )
        );
        when(qdrantCloudClient.findDatabaseApiKeyByName("acct-1", "cluster-1", "ai-fabric-123", "qdrant-cloud-management")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudDatabaseApiKeySummary(
                "key-legacy",
                "cluster-1",
                "ai-fabric-123",
                "abcd",
                ""
            )
        );
        when(qdrantCloudClient.createCollectionDatabaseApiKey(
            "acct-1",
            "cluster-1",
            "ai-fabric-123",
            List.of("product"),
            "qdrant-cloud-management"
        )).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudDatabaseApiKeySummary(
                "key-rotated",
                "cluster-1",
                "ai-fabric-123",
                "wxyz",
                "rotated-runtime-qdrant-key"
            )
        );

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> forbidden = mock(HttpResponse.class);
        when(forbidden.statusCode()).thenReturn(403);
        when(forbidden.body()).thenReturn("{\"status\":\"forbidden\"}");
        HttpResponse<String> existingCollection = mock(HttpResponse.class);
        when(existingCollection.statusCode()).thenReturn(200);
        when(existingCollection.body()).thenReturn("""
            {
              "result": {
                "config": {
                  "params": {
                    "vectors": {
                      "size": 1536
                    }
                  }
                }
              }
            }
            """);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().contains("/collections/")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(forbidden, existingCollection);

        DeploymentManagedVectorProvisioningService service = new DeploymentManagedVectorProvisioningService(
            secretService,
            objectMapper,
            httpClient,
            qdrantCloudClient
        );

        JsonNode providerConfig = objectMapper.readTree("""
            {
              "embeddingProvider": "openai",
              "vectorStrategy": "qdrant",
              "vectorProvisioningMode": "PLATFORM_MANAGED",
              "qdrantCloudProviderId": "aws",
              "qdrantCloudRegionId": "eu-west-1"
            }
            """);
        JsonNode entityConfig = objectMapper.readTree("""
            {
              "ai-config": { "vector-dimensions": 1536 },
              "ai-entities": { "product": {} }
            }
            """);

        ManagedVectorProvisioningResult result = service.ensureProvisioned("dep-123", providerConfig, entityConfig);

        verify(qdrantCloudClient).deleteDatabaseApiKey("acct-1", "cluster-1", "key-legacy", "qdrant-cloud-management");
        verify(secretService).upsertManagedSecret(
            eq("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_123"),
            eq("rotated-runtime-qdrant-key"),
            any(Map.class)
        );
        verify(httpClient, times(2)).send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().contains("/collections/")),
            any(HttpResponse.BodyHandler.class)
        );
        assertThat(result.details().path("databaseApiKeyState").asText()).isEqualTo("ROTATED");
        assertThat(result.details().path("collections").get(0).path("state").asText()).isEqualTo("REUSED");
    }

    @Test
    void ensureProvisionedCreatesManagedZillizCloudClusterAndStoresRuntimeCredentials() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("ZILLIZ_CLOUD_API_KEY")).thenReturn("zilliz-key");
        when(secretService.resolveSecret("MANAGED_MILVUS_USERNAME_DEP_DEP_123")).thenReturn(null);
        when(secretService.resolveSecret("MANAGED_MILVUS_PASSWORD_DEP_DEP_123")).thenReturn(null);

        HttpClient httpClient = mock(HttpClient.class);
        ZillizCloudControlPlaneClient zillizClient = mock(ZillizCloudControlPlaneClient.class);
        when(zillizClient.resolveProject("project-1", "gcp-us-west1", "zilliz-key")).thenReturn(
            new ZillizCloudControlPlaneClient.ZillizProjectResolution("project-1", "Shared", false)
        );
        when(zillizClient.findClusterByName("aifabric-123", "zilliz-key")).thenReturn(null);
        when(zillizClient.createCluster(
            "aifabric-123",
            "project-1",
            "gcp-us-west1",
            "Serverless",
            "",
            0,
            "zilliz-key"
        )).thenReturn(new ZillizCloudControlPlaneClient.ZillizClusterCreateResult(
            "cluster-1",
            "db-user",
            "db-password"
        ));
        when(zillizClient.awaitClusterReady("cluster-1", "zilliz-key")).thenReturn(
            new ZillizCloudControlPlaneClient.ZillizClusterSummary(
                "cluster-1",
                "aifabric-123",
                "project-1",
                "gcp-us-west1",
                "Serverless",
                "",
                0,
                "RUNNING",
                "https://cluster-1.gcp-us-west1.zillizcloud.com",
                "Serverless"
            )
        );

        DeploymentManagedVectorProvisioningService service = new DeploymentManagedVectorProvisioningService(
            secretService,
            objectMapper,
            httpClient,
            zillizClient
        );

        JsonNode providerConfig = objectMapper.readTree("""
            {
              "embeddingProvider": "gemini",
              "vectorStrategy": "milvus",
              "vectorProvisioningMode": "PLATFORM_MANAGED",
              "zillizCloudProjectId": "project-1",
              "zillizCloudRegionId": "gcp-us-west1",
              "zillizCloudClusterPlan": "Serverless"
            }
            """);
        JsonNode entityConfig = objectMapper.readTree("""
            {
              "ai-config": { "vector-dimensions": 768 },
              "ai-entities": { "product": {} }
            }
            """);

        ManagedVectorProvisioningResult result = service.ensureProvisioned("dep-123", providerConfig, entityConfig);

        assertThat(result.details().path("mode").asText()).isEqualTo("MANAGED_ZILLIZ_CLOUD_CLUSTER");
        assertThat(result.details().path("clusterId").asText()).isEqualTo("cluster-1");
        assertThat(result.effectiveProviderConfig().path("milvusHost").asText())
            .isEqualTo("cluster-1.gcp-us-west1.zillizcloud.com");
        assertThat(result.effectiveProviderConfig().path("milvusPort").asInt()).isEqualTo(443);
        assertThat(result.effectiveProviderConfig().path("milvusSecure").asBoolean()).isTrue();
        assertThat(result.effectiveProviderConfig().path("milvusRuntimeUsernameSecretName").asText())
            .isEqualTo("MANAGED_MILVUS_USERNAME_DEP_DEP_123");
        assertThat(result.effectiveProviderConfig().path("milvusRuntimePasswordSecretName").asText())
            .isEqualTo("MANAGED_MILVUS_PASSWORD_DEP_DEP_123");
        verify(secretService).upsertManagedSecret(
            eq("MANAGED_MILVUS_USERNAME_DEP_DEP_123"),
            eq("db-user"),
            any(Map.class)
        );
        verify(secretService).upsertManagedSecret(
            eq("MANAGED_MILVUS_PASSWORD_DEP_DEP_123"),
            eq("db-password"),
            any(Map.class)
        );
    }
}
