package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderConnectivitySummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentProviderConnectivityServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    @Test
    void probeMarksPineconeControlPlaneReadyWhenCredentialWorks() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("PINECONE_API_KEY")).thenReturn("pinecone-secret");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && request.uri().toString().equals("https://api.pinecone.io/indexes")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        DeploymentProviderConnectivityService service = new DeploymentProviderConnectivityService(
            secretService,
            objectMapper,
            httpClient
        );

        DeploymentProviderConnectivitySummary summary = service.probe(
            deployment("dep-123", "Commerce"),
            draft("""
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "pinecone",
                  "vectorProvisioningMode": "PLATFORM_MANAGED",
                  "pineconeManagedIndexEnabled": true,
                  "pineconeIndexName": "dep-123",
                  "pineconeRegion": "eu-west-1"
                }
                """)
        );

        assertThat(summary.probes()).hasSize(1);
        assertThat(summary.probes().get(0).status()).isEqualTo("READY");
        assertThat(summary.managedVectorProvisioningEnabled()).isTrue();
        assertThat(summary.managedVectorProvisioningMode()).isEqualTo("MANAGED_SERVERLESS_INDEX");
        assertThat(summary.managedVectorTargets()).containsExactly("dep-123 (aws/eu-west-1)");
        assertThat(summary.effectiveSecretResolutions())
            .extracting(item -> item.secretPurpose())
            .contains("OPENAI_API_KEY", "PINECONE_API_KEY");
    }

    @SuppressWarnings("unchecked")
    @Test
    void probeMarksQdrantCollectionsApiReadyWhenClusterResponds() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("QDRANT_API_KEY")).thenReturn("qdrant-secret");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && request.uri().toString().equals("https://cluster.example/collections")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        DeploymentProviderConnectivityService service = new DeploymentProviderConnectivityService(
            secretService,
            objectMapper,
            httpClient
        );

        DeploymentProviderConnectivitySummary summary = service.probe(
            deployment("dep-456", "Customer Search"),
            draft("""
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "qdrant",
                  "qdrantHost": "https://cluster.example"
                }
                """)
        );

        assertThat(summary.probes()).hasSize(1);
        assertThat(summary.probes().get(0).status()).isEqualTo("READY");
        assertThat(summary.probes().get(0).endpoint()).isEqualTo("https://cluster.example/collections");
        assertThat(summary.managedVectorProvisioningEnabled()).isFalse();
    }

    @Test
    void probeSummarizesManagedQdrantCollectionsFromEntityConfig() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        HttpClient httpClient = mock(HttpClient.class);
        DeploymentProviderConnectivityService service = new DeploymentProviderConnectivityService(
            secretService,
            objectMapper,
            httpClient
        );

        DeploymentProviderConnectivitySummary summary = service.probe(
            deployment("dep-900", "Managed Qdrant"),
            draft("""
                {
                  "vectorStrategy": "qdrant",
                  "qdrantHost": "https://cluster.example",
                  "qdrantManagedCollectionsEnabled": true
                }
                """, """
                {
                  "ai-entities": {
                    "product": {},
                    "policy": {}
                  }
                }
                """)
        );

        assertThat(summary.managedVectorProvisioningEnabled()).isTrue();
        assertThat(summary.managedVectorProvisioningMode()).isEqualTo("MANAGED_COLLECTIONS");
        assertThat(summary.managedVectorTargets()).containsExactly("product", "policy");
    }

    @Test
    void probeMarksQdrantCloudControlPlaneReadyWhenManagementAccessWorks() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY")).thenReturn("mgmt-key");

        HttpClient httpClient = mock(HttpClient.class);
        QdrantCloudControlPlaneClient qdrantCloudClient = mock(QdrantCloudControlPlaneClient.class);
        when(qdrantCloudClient.resolveAccount("", "mgmt-key")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudAccountResolution("acct-1", "Primary", true)
        );
        when(qdrantCloudClient.requireRegion("aws", "eu-west-1")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudRegionSummary("eu-west-1", "EU West 1", "aws", true)
        );
        when(qdrantCloudClient.resolvePackage("acct-1", "mgmt-key", "aws", "eu-west-1", "")).thenReturn(
            new QdrantCloudControlPlaneClient.QdrantCloudPackageSummary("pkg-1", "Sandbox", "sandbox", "USD", 1000, "PACKAGE_STATUS_ACTIVE", "2GB", "shared", "10GB")
        );

        DeploymentProviderConnectivityService service = new DeploymentProviderConnectivityService(
            secretService,
            objectMapper,
            httpClient,
            qdrantCloudClient
        );

        DeploymentProviderConnectivitySummary summary = service.probe(
            deployment("dep-321", "Managed Qdrant"),
            draft("""
                {
                  "embeddingProvider": "openai",
                  "vectorStrategy": "qdrant",
                  "vectorProvisioningMode": "PLATFORM_MANAGED",
                  "qdrantCloudProviderId": "aws",
                  "qdrantCloudRegionId": "eu-west-1"
                }
                """, """
                {
                  "ai-entities": {
                    "product": {},
                    "policy": {}
                  }
                }
                """)
        );

        assertThat(summary.probes()).hasSize(1);
        assertThat(summary.probes().get(0).key()).isEqualTo("qdrant_cloud_control_plane");
        assertThat(summary.probes().get(0).status()).isEqualTo("READY");
        assertThat(summary.managedVectorProvisioningEnabled()).isTrue();
        assertThat(summary.managedVectorProvisioningMode()).isEqualTo("MANAGED_CLOUD_CLUSTER");
        assertThat(summary.managedVectorTargets().get(0)).contains("aws/eu-west-1");
    }

    @Test
    void probeMarksZillizCloudControlPlaneReadyWhenManagementAccessWorks() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("ZILLIZ_CLOUD_API_KEY")).thenReturn("zilliz-key");

        HttpClient httpClient = mock(HttpClient.class);
        ZillizCloudControlPlaneClient zillizClient = mock(ZillizCloudControlPlaneClient.class);
        when(zillizClient.resolveProject("project-1", "gcp-us-west1", "zilliz-key")).thenReturn(
            new ZillizCloudControlPlaneClient.ZillizProjectResolution("project-1", "Shared", false)
        );

        DeploymentProviderConnectivityService service = new DeploymentProviderConnectivityService(
            secretService,
            objectMapper,
            httpClient,
            zillizClient
        );

        DeploymentProviderConnectivitySummary summary = service.probe(
            deployment("dep-654", "Managed Zilliz"),
            draft("""
                {
                  "embeddingProvider": "gemini",
                  "vectorStrategy": "milvus",
                  "vectorProvisioningMode": "PLATFORM_MANAGED",
                  "zillizCloudProjectId": "project-1",
                  "zillizCloudRegionId": "gcp-us-west1",
                  "zillizCloudClusterPlan": "Serverless"
                }
                """)
        );

        assertThat(summary.probes()).hasSize(1);
        assertThat(summary.probes().get(0).key()).isEqualTo("zilliz_cloud_control_plane");
        assertThat(summary.probes().get(0).status()).isEqualTo("READY");
        assertThat(summary.managedVectorProvisioningEnabled()).isTrue();
        assertThat(summary.managedVectorProvisioningMode()).isEqualTo("MANAGED_ZILLIZ_CLOUD_CLUSTER");
        assertThat(summary.managedVectorTargets().get(0)).contains("project-1");
        assertThat(summary.managedVectorTargets().get(0)).contains("gcp-us-west1");
    }

    private DeploymentEntity deployment(String id, String name) {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId(id);
        deployment.setName(name);
        return deployment;
    }

    private DeploymentDraftEntity draft(String providerConfigJson) {
        return draft(providerConfigJson, """
            {
              "ai-entities": {}
            }
            """);
    }

    private DeploymentDraftEntity draft(String providerConfigJson, String entityConfigJson) {
        DeploymentDraftEntity draft = new DeploymentDraftEntity();
        draft.setProviderConfigJson(providerConfigJson);
        draft.setEntityConfigJson(entityConfigJson);
        return draft;
    }
}
