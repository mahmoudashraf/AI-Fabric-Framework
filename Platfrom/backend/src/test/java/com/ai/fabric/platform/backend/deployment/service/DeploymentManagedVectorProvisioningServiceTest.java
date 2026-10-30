package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeploymentManagedVectorProvisioningServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ManagedResourceOwnershipFixture ownershipFixture=new ManagedResourceOwnershipFixture();

    private ManagedVectorProvisioningResult provision(DeploymentManagedVectorProvisioningService service,JsonNode provider,JsonNode entity) {
        ownershipFixture.inject(service);
        return service.ensureProvisioned(ManagedResourceOwnershipFixture.SCOPE,provider,entity);
    }

    private String managedSecret(String kind) {
        return "MANAGED_"+kind+"_DEP_"+DeploymentEvidenceFingerprint.hash(objectMapper,
            objectMapper.valueToTree(ManagedResourceOwnershipFixture.SCOPE)).toUpperCase(java.util.Locale.ROOT);
    }

    private String databaseKeyName() {
        return ("ai-fabric-"+DeploymentEvidenceFingerprint.hash(objectMapper,
            objectMapper.valueToTree(ManagedResourceOwnershipFixture.SCOPE))).substring(0,64);
    }

    @SuppressWarnings("unchecked")
    @Test
    void existingUnownedQdrantCollectionBlocksEveryCreateEvenAfterAnotherCollectionWasAbsent() throws Exception {
        var secret=mock(PlatformSecretService.class);
        var http=mock(HttpClient.class);
        HttpResponse<String> missing=mock(HttpResponse.class),existing=mock(HttpResponse.class);
        when(missing.statusCode()).thenReturn(404);
        when(existing.statusCode()).thenReturn(200);
        when(existing.body()).thenReturn("{\"result\":{\"config\":{\"params\":{\"vectors\":{\"size\":1536}}}}}");
        when(http.<String>send(any(),any(HttpResponse.BodyHandler.class))).thenReturn(missing,existing);
        var service=new DeploymentManagedVectorProvisioningService(secret,objectMapper,http);
        var provider=objectMapper.readTree("{\"vectorStrategy\":\"qdrant\",\"qdrantHost\":\"https://vector.example\",\"qdrantManagedCollectionsEnabled\":true}");
        var entities=objectMapper.readTree("{\"ai-config\":{\"vector-dimensions\":1536},\"ai-entities\":{\"absent\":{},\"customer-data\":{}}}");
        assertThatThrownBy(()->provision(service,provider,entities)).hasMessageContaining("OWNERSHIP_REQUIRED");
        verify(http,never()).send(argThat(request->request!=null&&"PUT".equals(request.method())),any(HttpResponse.BodyHandler.class));
        assertThat(ownershipFixture.jdbc.queryForObject("SELECT COUNT(*) FROM platform_managed_resource_ownership",Long.class)).isZero();
        verify(secret,never()).upsertManagedSecret(any(),any(),any(Map.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void qdrantCreateConflictLeavesDurablePendingIntentAndCannotBecomeOwnedOnReplay() throws Exception {
        var secret=mock(PlatformSecretService.class);
        var http=mock(HttpClient.class);
        HttpResponse<String> missing=mock(HttpResponse.class),conflict=mock(HttpResponse.class),existing=mock(HttpResponse.class);
        when(missing.statusCode()).thenReturn(404); when(conflict.statusCode()).thenReturn(409);
        when(existing.statusCode()).thenReturn(200); when(existing.body()).thenReturn("{}");
        when(http.<String>send(any(),any(HttpResponse.BodyHandler.class))).thenReturn(missing,conflict,existing);
        var service=new DeploymentManagedVectorProvisioningService(secret,objectMapper,http);
        var provider=objectMapper.readTree("{\"vectorStrategy\":\"qdrant\",\"qdrantHost\":\"https://vector.example\",\"qdrantManagedCollectionsEnabled\":true}");
        var entities=objectMapper.readTree("{\"ai-entities\":{\"catalog\":{}}}");
        assertThatThrownBy(()->provision(service,provider,entities)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(ownershipFixture.jdbc.queryForObject("SELECT state FROM platform_managed_resource_ownership",String.class)).isEqualTo("CREATE_PENDING");
        assertThatThrownBy(()->provision(service,provider,entities)).hasMessageContaining("OWNERSHIP_REQUIRED");
        verify(http,times(1)).send(argThat(request->request!=null&&"PUT".equals(request.method())),any(HttpResponse.BodyHandler.class));
    }

    @Test
    void sameNamePineconeWithManagedSecretStillCannotBeAdopted() throws Exception {
        var secret=mock(PlatformSecretService.class);
        when(secret.resolveSecret("PINECONE_API_KEY")).thenReturn("synthetic-management");
        when(secret.resolveSecretForDeployment("PINECONE_API_KEY", ManagedResourceOwnershipFixture.SCOPE.deploymentId(), null))
            .thenReturn("synthetic-management");
        when(secret.resolveSecret(managedSecret("PINECONE_API_KEY"))).thenReturn("synthetic-retained-secret");
        var http=mock(HttpClient.class);
        var pinecone=mock(PineconeControlPlaneClient.class);
        when(pinecone.findIndexByName("index","synthetic-management")).thenReturn(
            new PineconeControlPlaneClient.PineconeIndexSummary("index","https://index.example",1536,"cosine",true,"enabled"));
        var service=new DeploymentManagedVectorProvisioningService(secret,objectMapper,http,mock(QdrantCloudControlPlaneClient.class),pinecone,mock(ZillizCloudControlPlaneClient.class));
        var provider=objectMapper.readTree("{\"vectorStrategy\":\"pinecone\",\"vectorProvisioningMode\":\"PLATFORM_MANAGED\",\"pineconeManagedIndexEnabled\":true,\"pineconeIndexName\":\"index\",\"pineconeRegion\":\"region\"}");
        assertThatThrownBy(()->provision(service,provider,objectMapper.createObjectNode())).hasMessageContaining("OWNERSHIP_REQUIRED");
        verify(pinecone,never()).awaitIndexReady(any(),any());
        verify(secret,never()).upsertManagedSecret(any(),any(),any(Map.class));
        verifyNoInteractions(http);
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @ValueSource(strings={"{}","{\"result\":false}","{\"result\":\"true\"}"})
    void qdrantSuccessStatusWithoutExplicitCreationAcknowledgementCannotGrantOwnership(String body) throws Exception {
        var secret=mock(PlatformSecretService.class);
        var http=mock(HttpClient.class);
        HttpResponse<String> missing=mock(HttpResponse.class),response=mock(HttpResponse.class);
        when(missing.statusCode()).thenReturn(404);
        when(response.statusCode()).thenReturn(200); when(response.body()).thenReturn(body);
        when(http.<String>send(any(),any(HttpResponse.BodyHandler.class))).thenReturn(missing,response);
        var service=new DeploymentManagedVectorProvisioningService(secret,objectMapper,http);
        var provider=objectMapper.readTree("{\"vectorStrategy\":\"qdrant\",\"qdrantHost\":\"https://vector.example\",\"qdrantManagedCollectionsEnabled\":true}");
        assertThatThrownBy(()->provision(service,provider,objectMapper.readTree("{\"ai-entities\":{\"catalog\":{}}}")))
            .hasMessageContaining("CREATE_RECONCILIATION_REQUIRED");
        assertThat(ownershipFixture.jdbc.queryForObject("SELECT state FROM platform_managed_resource_ownership",String.class)).isEqualTo("CREATE_PENDING");
    }

    @Test
    void externalNativeTokenPathRemainsInertAndUnscopedManagedPathFailsBeforeProviderAccess() {
        var secret=mock(PlatformSecretService.class);
        var http=mock(HttpClient.class);
        var service=new DeploymentManagedVectorProvisioningService(secret,objectMapper,http);
        var version=new com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity();
        version.setProviderConfigJson("{\"vectorStrategy\":\"milvus\",\"vectorProvisioningMode\":\"EXTERNAL_EXISTING\",\"milvusAuthenticationType\":\"TOKEN\",\"milvusExternalBindingRequired\":true,\"vectorStoragePosture\":\"SHARED\"}");
        version.setEntityConfigJson("{}");
        assertThat(service.ensureProvisioned(ownershipFixture.deployment(),version).details().path("enabled").asBoolean()).isFalse();
        version.setProviderConfigJson("{\"vectorStrategy\":\"pinecone\",\"vectorProvisioningMode\":\"PLATFORM_MANAGED\",\"pineconeManagedIndexEnabled\":true}");
        assertThatThrownBy(()->service.ensureProvisioned(ownershipFixture.deployment(),version)).hasMessageContaining("TARGET_REQUIRED");
        verifyNoInteractions(secret,http);
    }

    @Test
    void matchingQdrantCloudClusterWithoutCreationReceiptCannotGainKeysOrCollections() throws Exception {
        var secret=mock(PlatformSecretService.class);
        when(secret.resolveSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY")).thenReturn("synthetic-management");
        var qdrant=mock(QdrantCloudControlPlaneClient.class);
        when(qdrant.resolveAccount("","synthetic-management")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudAccountResolution("account","Synthetic",true));
        when(qdrant.requireRegion("aws","region")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudRegionSummary("region","Synthetic","aws",true));
        when(qdrant.resolvePackage("account","synthetic-management","aws","region","")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudPackageSummary("package","Synthetic","sandbox","USD",0,"ACTIVE","","",""));
        when(qdrant.findClusterByName("account","aifabric-123","synthetic-management")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudClusterSummary("cluster","account","aifabric-123","aws","region","package","HEALTHY",
                "","https://vector.example",6333,6334,Map.of("managed-by","ai-fabric-platform")));
        var http=mock(HttpClient.class);
        var service=new DeploymentManagedVectorProvisioningService(secret,objectMapper,http,qdrant);
        var config=objectMapper.readTree("{\"vectorStrategy\":\"qdrant\",\"vectorProvisioningMode\":\"PLATFORM_MANAGED\",\"qdrantCloudRegionId\":\"region\"}");
        assertThatThrownBy(()->provision(service,config,objectMapper.readTree("{\"ai-entities\":{\"catalog\":{}}}")))
            .hasMessageContaining("OWNERSHIP_REQUIRED");
        verify(qdrant,never()).createCluster(any(),any(),any(),any(),any(),any(),any());
        verify(qdrant,never()).createCollectionDatabaseApiKey(any(),any(),any(),any(),any());
        verify(secret,never()).upsertManagedSecret(any(),any(),any(Map.class));
        verifyNoInteractions(http);
    }

    @Test
    void matchingZillizClusterAndExistingCredentialsDoNotGrantOwnership() throws Exception {
        var secret=mock(PlatformSecretService.class);
        when(secret.resolveSecret("ZILLIZ_CLOUD_API_KEY")).thenReturn("synthetic-management");
        when(secret.resolveSecret(managedSecret("MILVUS_USERNAME"))).thenReturn("synthetic-user");
        when(secret.resolveSecret(managedSecret("MILVUS_PASSWORD"))).thenReturn("synthetic-password");
        var zilliz=mock(ZillizCloudControlPlaneClient.class);
        when(zilliz.resolveProject("project","region","synthetic-management")).thenReturn(
            new ZillizCloudControlPlaneClient.ZillizProjectResolution("project","Synthetic",false));
        when(zilliz.findClusterByName("aifabric-123","synthetic-management")).thenReturn(
            new ZillizCloudControlPlaneClient.ZillizClusterSummary("cluster","aifabric-123","project","region","Serverless","",0,"RUNNING",
                "https://vector.example","Serverless"));
        var http=mock(HttpClient.class);
        var service=new DeploymentManagedVectorProvisioningService(secret,objectMapper,http,zilliz);
        var config=objectMapper.readTree("{\"vectorStrategy\":\"milvus\",\"vectorProvisioningMode\":\"PLATFORM_MANAGED\",\"zillizCloudProjectId\":\"project\",\"zillizCloudRegionId\":\"region\",\"zillizCloudClusterPlan\":\"Serverless\"}");
        assertThatThrownBy(()->provision(service,config,objectMapper.createObjectNode())).hasMessageContaining("OWNERSHIP_REQUIRED");
        verify(zilliz,never()).createCluster(any(),any(),any(),any(),any(),anyInt(),any());
        verify(zilliz,never()).deleteCluster(any(),any());
        verify(secret,never()).clearManagedSecret(any(),any(Map.class));
        verify(secret,never()).upsertManagedSecret(any(),any(),any(Map.class));
        verifyNoInteractions(http);
    }

    @SuppressWarnings("unchecked")
    @Test
    void ensureProvisionedCreatesManagedPineconeIndexAndInjectsResolvedHost() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("PINECONE_API_KEY")).thenReturn("pinecone-secret");
        when(secretService.resolveSecretForDeployment("PINECONE_API_KEY", ManagedResourceOwnershipFixture.SCOPE.deploymentId(), null))
            .thenReturn("pinecone-secret");

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

        ManagedVectorProvisioningResult result = provision(service, providerConfig, entityConfig);

        assertThat(result.effectiveProviderConfig().path("pineconeApiHost").asText())
            .isEqualTo("https://dep-123-abc123.svc.eu-west-1-aws.pinecone.io");
        assertThat(result.effectiveProviderConfig().path("pineconeRuntimeApiKeySecretName").asText())
            .isEqualTo(managedSecret("PINECONE_API_KEY"));
        assertThat(result.details().path("mode").asText()).isEqualTo("MANAGED_SERVERLESS_INDEX");
        assertThat(result.details().path("state").asText()).isEqualTo("CREATED");
        assertThat(result.details().path("ready").asBoolean()).isTrue();
        verify(secretService).upsertManagedSecret(
            eq(managedSecret("PINECONE_API_KEY")),
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
        when(created.body()).thenReturn("{\"result\":true,\"status\":\"ok\"}");

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

        ManagedVectorProvisioningResult result = provision(service, providerConfig, entityConfig);

        assertThat(result.details().path("mode").asText()).isEqualTo("MANAGED_COLLECTIONS");
        assertThat(result.details().path("baseUrl").asText()).isEqualTo("https://cluster.example");
        assertThat(result.details().path("collections")).hasSize(2);
        assertThat(result.details().path("collections").get(0).path("state").asText()).isEqualTo("CREATED");
    }

    @SuppressWarnings("unchecked")
    @Test
    void ensureProvisionedUsesConfiguredSharedQdrantRuntimeSecretForManagedCollections() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_SHARED")).thenReturn("shared-qdrant-secret");
        when(secretService.resolveSecretForDeployment("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_SHARED", ManagedResourceOwnershipFixture.SCOPE.deploymentId(), null))
            .thenReturn("shared-qdrant-secret");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> missing = mock(HttpResponse.class);
        when(missing.statusCode()).thenReturn(404);
        HttpResponse<String> created = mock(HttpResponse.class);
        when(created.statusCode()).thenReturn(200);
        when(created.body()).thenReturn("{\"result\":true,\"status\":\"ok\"}");

        when(httpClient.<String>send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().contains("/collections/")
                && "shared-qdrant-secret".equals(request.headers().firstValue("api-key").orElse(null))),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(missing);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "PUT".equals(request.method())
                && request.uri().toString().contains("/collections/")
                && "shared-qdrant-secret".equals(request.headers().firstValue("api-key").orElse(null))),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(created);

        DeploymentManagedVectorProvisioningService service = new DeploymentManagedVectorProvisioningService(
            secretService,
            objectMapper,
            httpClient
        );

        JsonNode providerConfig = objectMapper.readTree("""
            {
              "embeddingProvider": "openai",
              "vectorStrategy": "qdrant",
              "vectorProvisioningMode": "EXTERNAL_EXISTING",
              "vectorStoragePosture": "SHARED",
              "qdrantHost": "https://cluster.example",
              "qdrantManagedCollectionsEnabled": true,
              "qdrantRuntimeApiKeySecretName": "MANAGED_QDRANT_DB_API_KEY_DEP_DEP_SHARED"
            }
            """);
        JsonNode entityConfig = objectMapper.readTree("""
            {
              "ai-config": { "vector-dimensions": 1536 },
              "ai-entities": { "product": {} }
            }
            """);

        ManagedVectorProvisioningResult result = provision(service, providerConfig, entityConfig);

        assertThat(result.details().path("collections")).hasSize(1);
        assertThat(result.details().path("collections").get(0).path("state").asText()).isEqualTo("CREATED");
        verify(httpClient).send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && "shared-qdrant-secret".equals(request.headers().firstValue("api-key").orElse(null))),
            any(HttpResponse.BodyHandler.class)
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void ensureProvisionedReusesPlatformManagedSharedQdrantRootWhenHostIsPreconfigured() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_SHARED")).thenReturn("shared-qdrant-secret");
        when(secretService.resolveSecretForDeployment("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_SHARED", ManagedResourceOwnershipFixture.SCOPE.deploymentId(), null))
            .thenReturn("shared-qdrant-secret");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> missing = mock(HttpResponse.class);
        when(missing.statusCode()).thenReturn(404);
        HttpResponse<String> created = mock(HttpResponse.class);
        when(created.statusCode()).thenReturn(200);
        when(created.body()).thenReturn("{\"result\":true,\"status\":\"ok\"}");

        when(httpClient.<String>send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().contains("/collections/")
                && "shared-qdrant-secret".equals(request.headers().firstValue("api-key").orElse(null))),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(missing);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "PUT".equals(request.method())
                && request.uri().toString().contains("/collections/")
                && "shared-qdrant-secret".equals(request.headers().firstValue("api-key").orElse(null))),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(created);

        QdrantCloudControlPlaneClient qdrantCloudClient = mock(QdrantCloudControlPlaneClient.class);
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
              "vectorStoragePosture": "SHARED",
              "qdrantHost": "https://cluster.example",
              "qdrantManagedCollectionsEnabled": true,
              "qdrantRuntimeApiKeySecretName": "MANAGED_QDRANT_DB_API_KEY_DEP_DEP_SHARED",
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

        ManagedVectorProvisioningResult result = provision(service, providerConfig, entityConfig);

        assertThat(result.details().path("mode").asText()).isEqualTo("MANAGED_COLLECTIONS");
        assertThat(result.details().path("sharedRootReused").asBoolean()).isTrue();
        assertThat(result.details().path("baseUrl").asText()).isEqualTo("https://cluster.example");
        assertThat(result.details().path("collections")).hasSize(1);
        verifyNoInteractions(qdrantCloudClient);
    }

    @SuppressWarnings("unchecked")
    @Test
    void ensureProvisionedCreatesManagedQdrantCloudClusterAndStoresRuntimeKey() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY")).thenReturn("qdrant-cloud-management");
        when(secretService.resolveSecret(managedSecret("QDRANT_DB_API_KEY"))).thenReturn(null);

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
        when(qdrantCloudClient.findDatabaseApiKeyByName("acct-1", "cluster-1", databaseKeyName(), "qdrant-cloud-management")).thenReturn(null);
        when(qdrantCloudClient.createCollectionDatabaseApiKey(
            "acct-1",
            "cluster-1",
            databaseKeyName(),
            List.of("product", "policy"),
            "qdrant-cloud-management"
        )).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudDatabaseApiKeySummary(
                "key-1",
                "cluster-1",
                databaseKeyName(),
                "abcd",
                "runtime-qdrant-key"
            )
        );

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> forbidden = mock(HttpResponse.class);
        when(forbidden.statusCode()).thenReturn(403);
        when(forbidden.body()).thenReturn("{\"status\":\"forbidden\"}");
        HttpResponse<String> missing = mock(HttpResponse.class);
        when(missing.statusCode()).thenReturn(404);
        HttpResponse<String> created = mock(HttpResponse.class);
        when(created.statusCode()).thenReturn(200);
        when(created.body()).thenReturn("{\"result\":true,\"status\":\"ok\"}");
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().contains("/collections/")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(forbidden, missing, missing);
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

        ManagedVectorProvisioningResult result = provision(service, providerConfig, entityConfig);

        assertThat(result.details().path("mode").asText()).isEqualTo("MANAGED_CLOUD_CLUSTER");
        assertThat(result.effectiveProviderConfig().path("qdrantHost").asText())
            .isEqualTo("https://cluster-1.eu-west-1.aws.cloud.qdrant.io");
        assertThat(result.effectiveProviderConfig().path("qdrantRuntimeApiKeySecretName").asText())
            .isEqualTo(managedSecret("QDRANT_DB_API_KEY"));
        verify(secretService).upsertManagedSecret(
            eq(managedSecret("QDRANT_DB_API_KEY")),
            eq("runtime-qdrant-key"),
            any(Map.class)
        );
        verify(httpClient, times(3)).send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().contains("/collections/")),
            any(HttpResponse.BodyHandler.class)
        );
        verify(httpClient, times(2)).send(
            argThat(request -> request != null
                && "PUT".equals(request.method())
                && request.uri().toString().contains("/collections/")),
            any(HttpResponse.BodyHandler.class)
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void ensureProvisionedRetriesManagedQdrantCollectionLookupWhileDataPlaneWarmsUp() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY")).thenReturn("qdrant-cloud-management");
        when(secretService.resolveSecret(managedSecret("QDRANT_DB_API_KEY"))).thenReturn(null);

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
        when(qdrantCloudClient.findDatabaseApiKeyByName("acct-1", "cluster-1", databaseKeyName(), "qdrant-cloud-management")).thenReturn(null);
        when(qdrantCloudClient.createCollectionDatabaseApiKey(
            "acct-1",
            "cluster-1",
            databaseKeyName(),
            List.of("product", "policy"),
            "qdrant-cloud-management"
        )).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudDatabaseApiKeySummary(
                "key-1",
                "cluster-1",
                databaseKeyName(),
                "abcd",
                "runtime-qdrant-key"
            )
        );

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> unavailable = mock(HttpResponse.class);
        when(unavailable.statusCode()).thenReturn(503);
        when(unavailable.body()).thenReturn("{\"status\":\"error\"}");
        HttpResponse<String> missing = mock(HttpResponse.class);
        when(missing.statusCode()).thenReturn(404);
        HttpResponse<String> created = mock(HttpResponse.class);
        when(created.statusCode()).thenReturn(200);
        when(created.body()).thenReturn("{\"result\":true,\"status\":\"ok\"}");
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().contains("/collections/")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(unavailable, missing, missing);
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

        ManagedVectorProvisioningResult result = provision(service, providerConfig, entityConfig);

        assertThat(result.details().path("mode").asText()).isEqualTo("MANAGED_CLOUD_CLUSTER");
        assertThat(result.details().path("collections")).hasSize(2);
        verify(secretService).upsertManagedSecret(
            eq(managedSecret("QDRANT_DB_API_KEY")),
            eq("runtime-qdrant-key"),
            any(Map.class)
        );
        verify(httpClient, times(3)).send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().contains("/collections/")),
            any(HttpResponse.BodyHandler.class)
        );
        verify(httpClient, times(2)).send(
            argThat(request -> request != null
                && "PUT".equals(request.method())
                && request.uri().toString().contains("/collections/")),
            any(HttpResponse.BodyHandler.class)
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void ensureProvisionedReusesExistingQdrantCloudClusterEvenIfPackageIsNoLongerActive() throws Exception {
        String endpoint="https://cluster-1.eu-west-1.aws.cloud.qdrant.io";
        ownershipFixture.own("qdrant","CLUSTER","acct-1","platform-deployments","cluster-1",endpoint);
        ownershipFixture.own("qdrant","DATABASE_API_KEY",endpoint,databaseKeyName(),"key-1",endpoint);
        ownershipFixture.own("qdrant","COLLECTION",endpoint,"product","product",endpoint);
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY")).thenReturn("qdrant-cloud-management");
        when(secretService.resolveSecret(managedSecret("QDRANT_DB_API_KEY"))).thenReturn("existing-runtime-qdrant-key");

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
                "platform-deployments",
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
                "platform-deployments",
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
        when(qdrantCloudClient.findDatabaseApiKeyByName("acct-1", "cluster-1", databaseKeyName(), "qdrant-cloud-management")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudDatabaseApiKeySummary(
                "key-1",
                "cluster-1",
                databaseKeyName(),
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

        ManagedVectorProvisioningResult result = provision(service, providerConfig, entityConfig);

        assertThat(result.details().path("clusterState").asText()).isEqualTo("REUSED");
        assertThat(result.details().path("packageId").asText()).isEqualTo("pkg-old");
        assertThat(result.details().path("requestedActivePackageId").asText()).isEqualTo("pkg-new");
        assertThat(result.details().path("packageResolution").asText()).isEqualTo("REUSED_EXISTING_CLUSTER_PACKAGE");
        assertThat(result.effectiveProviderConfig().path("qdrantCloudPackageId").asText()).isEqualTo("pkg-old");
        assertThat(result.effectiveProviderConfig().path("qdrantRuntimeApiKeySecretName").asText())
            .isEqualTo(managedSecret("QDRANT_DB_API_KEY"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void ensureProvisionedDoesNotRotateOwnedQdrantDatabaseKeyOnForbiddenResponse() throws Exception {
        String endpoint="https://cluster-1.eu-west-1.aws.cloud.qdrant.io";
        ownershipFixture.own("qdrant","CLUSTER","acct-1","aifabric-123","cluster-1",endpoint);
        ownershipFixture.own("qdrant","DATABASE_API_KEY",endpoint,databaseKeyName(),"key-legacy",endpoint);
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY")).thenReturn("qdrant-cloud-management");
        when(secretService.resolveSecret(managedSecret("QDRANT_DB_API_KEY"))).thenReturn("legacy-runtime-qdrant-key");

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
        when(qdrantCloudClient.findDatabaseApiKeyByName("acct-1", "cluster-1", databaseKeyName(), "qdrant-cloud-management")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudDatabaseApiKeySummary(
                "key-legacy",
                "cluster-1",
                databaseKeyName(),
                "abcd",
                ""
            )
        );
        when(qdrantCloudClient.createCollectionDatabaseApiKey(
            "acct-1",
            "cluster-1",
            databaseKeyName(),
            List.of("product"),
            "qdrant-cloud-management"
        )).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudDatabaseApiKeySummary(
                "key-rotated",
                "cluster-1",
                databaseKeyName(),
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
        )).thenReturn(forbidden, forbidden, existingCollection);

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

        assertThatThrownBy(()->provision(service,providerConfig,entityConfig)).hasMessageContaining("HTTP 403");

        verify(qdrantCloudClient,never()).deleteDatabaseApiKey(any(),any(),any(),any());
        verify(secretService,never()).upsertManagedSecret(any(),any(),any(Map.class));
    }

    @Test
    void ensureProvisionedCreatesManagedZillizCloudClusterAndStoresRuntimeCredentials() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("ZILLIZ_CLOUD_API_KEY")).thenReturn("zilliz-key");
        when(secretService.resolveSecret(managedSecret("MILVUS_USERNAME"))).thenReturn(null);
        when(secretService.resolveSecret(managedSecret("MILVUS_PASSWORD"))).thenReturn(null);

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

        ManagedVectorProvisioningResult result = provision(service, providerConfig, entityConfig);

        assertThat(result.details().path("mode").asText()).isEqualTo("MANAGED_ZILLIZ_CLOUD_CLUSTER");
        assertThat(result.details().path("clusterId").asText()).isEqualTo("cluster-1");
        assertThat(result.effectiveProviderConfig().path("milvusHost").asText())
            .isEqualTo("cluster-1.gcp-us-west1.zillizcloud.com");
        assertThat(result.effectiveProviderConfig().path("milvusPort").asInt()).isEqualTo(443);
        assertThat(result.effectiveProviderConfig().path("milvusSecure").asBoolean()).isTrue();
        assertThat(result.effectiveProviderConfig().path("milvusRuntimeUsernameSecretName").asText())
            .isEqualTo(managedSecret("MILVUS_USERNAME"));
        assertThat(result.effectiveProviderConfig().path("milvusRuntimePasswordSecretName").asText())
            .isEqualTo(managedSecret("MILVUS_PASSWORD"));
        verify(secretService).upsertManagedSecret(
            eq(managedSecret("MILVUS_USERNAME")),
            eq("db-user"),
            any(Map.class)
        );
        verify(secretService).upsertManagedSecret(
            eq(managedSecret("MILVUS_PASSWORD")),
            eq("db-password"),
            any(Map.class)
        );
    }

    @Test
    void ensureProvisionedPreservesExistingZillizClusterAndSecretsWhenRuntimeCredentialsAreMissing() throws Exception {
        ownershipFixture.own("zilliz","CLUSTER","project-1","aifabric-123","cluster-old","https://cluster-old.gcp-us-west1.zillizcloud.com");
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("ZILLIZ_CLOUD_API_KEY")).thenReturn("zilliz-key");
        when(secretService.resolveSecret(managedSecret("MILVUS_USERNAME"))).thenReturn("stale-user");
        when(secretService.resolveSecret(managedSecret("MILVUS_PASSWORD"))).thenReturn(null);

        HttpClient httpClient = mock(HttpClient.class);
        ZillizCloudControlPlaneClient zillizClient = mock(ZillizCloudControlPlaneClient.class);
        when(zillizClient.resolveProject("project-1", "gcp-us-west1", "zilliz-key")).thenReturn(
            new ZillizCloudControlPlaneClient.ZillizProjectResolution("project-1", "Shared", false)
        );
        when(zillizClient.findClusterByName("aifabric-123", "zilliz-key")).thenReturn(
            new ZillizCloudControlPlaneClient.ZillizClusterSummary(
                "cluster-old",
                "aifabric-123",
                "project-1",
                "gcp-us-west1",
                "Serverless",
                "",
                0,
                "RUNNING",
                "https://cluster-old.gcp-us-west1.zillizcloud.com",
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

        for (String[] credentials : List.of(
            new String[] {"stale-user", null}, new String[] {null, "retained-password"}, new String[] {"", ""}
        )) {
            when(secretService.resolveSecret(managedSecret("MILVUS_USERNAME"))).thenReturn(credentials[0]);
            when(secretService.resolveSecret(managedSecret("MILVUS_PASSWORD"))).thenReturn(credentials[1]);
            assertThatThrownBy(() -> provision(service, providerConfig, entityConfig))
                .isInstanceOf(RailwayProvisioningConfigurationException.class)
                .hasMessageContaining("MANAGED_ZILLIZ_RUNTIME_CREDENTIALS_REQUIRED");
        }
        verify(zillizClient, never()).deleteCluster(any(), any());
        verify(zillizClient, never()).awaitClusterDeleted(any(), any());
        verify(zillizClient, never()).createCluster(any(), any(), any(), any(), any(), anyInt(), any());
        verify(zillizClient, never()).awaitClusterReady(any(), any());
        verify(secretService, never()).clearManagedSecret(any(), any(Map.class));
        verify(secretService, never()).upsertManagedSecret(any(), any(), any(Map.class));

        when(secretService.resolveSecret(managedSecret("MILVUS_USERNAME"))).thenReturn("restored-user");
        when(secretService.resolveSecret(managedSecret("MILVUS_PASSWORD"))).thenReturn("restored-password");
        var retainedCluster = zillizClient.findClusterByName("aifabric-123", "zilliz-key");
        when(zillizClient.awaitClusterReady("cluster-old", "zilliz-key"))
            .thenReturn(retainedCluster);
        ManagedVectorProvisioningResult reused = provision(service, providerConfig, entityConfig);
        assertThat(reused.details().path("clusterId").asText()).isEqualTo("cluster-old");
        assertThat(reused.details().path("clusterState").asText()).isEqualTo("REUSED");
        verify(zillizClient, never()).deleteCluster(any(), any());
        verify(zillizClient, never()).createCluster(any(), any(), any(), any(), any(), anyInt(), any());
        verify(secretService, never()).clearManagedSecret(any(), any(Map.class));
        verify(secretService, never()).upsertManagedSecret(any(), any(), any(Map.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void ensureProvisionedEncodesQdrantCollectionNamesForManagedCollections() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("QDRANT_API_KEY")).thenReturn("qdrant-runtime-key");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> missingCollection = mock(HttpResponse.class);
        when(missingCollection.statusCode()).thenReturn(404);
        HttpResponse<String> createdCollection = mock(HttpResponse.class);
        when(createdCollection.statusCode()).thenReturn(200);
        when(createdCollection.body()).thenReturn("{\"result\":true,\"status\":\"ok\"}");
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().equals("https://cluster.example.com/collections/product%20catalog")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(missingCollection);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "PUT".equals(request.method())
                && request.uri().toString().equals("https://cluster.example.com/collections/product%20catalog")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(createdCollection);

        DeploymentManagedVectorProvisioningService service = new DeploymentManagedVectorProvisioningService(
            secretService,
            objectMapper,
            httpClient
        );

        JsonNode providerConfig = objectMapper.readTree("""
            {
              "vectorStrategy": "qdrant",
              "qdrantHost": "https://cluster.example.com",
              "qdrantManagedCollectionsEnabled": true
            }
            """);
        JsonNode entityConfig = objectMapper.readTree("""
            {
              "ai-config": { "vector-dimensions": 1536 },
              "ai-entities": { "product catalog": {} }
            }
            """);

        ManagedVectorProvisioningResult result = provision(service, providerConfig, entityConfig);

        assertThat(result.details().path("collections").get(0).path("state").asText()).isEqualTo("CREATED");
        verify(httpClient).send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().equals("https://cluster.example.com/collections/product%20catalog")),
            any(HttpResponse.BodyHandler.class)
        );
        verify(httpClient).send(
            argThat(request -> request != null
                && "PUT".equals(request.method())
                && request.uri().toString().equals("https://cluster.example.com/collections/product%20catalog")),
            any(HttpResponse.BodyHandler.class)
        );
    }
}
