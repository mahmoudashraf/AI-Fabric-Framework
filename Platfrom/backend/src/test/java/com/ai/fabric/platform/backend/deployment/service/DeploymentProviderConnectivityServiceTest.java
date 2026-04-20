package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderConnectivitySummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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
        when(secretService.resolveSecret("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_SHARED")).thenReturn("qdrant-secret");

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
                  "qdrantHost": "https://cluster.example",
                  "qdrantRuntimeApiKeySecretName": "MANAGED_QDRANT_DB_API_KEY_DEP_DEP_SHARED"
                }
                """)
        );

        assertThat(summary.probes()).hasSize(1);
        assertThat(summary.probes().get(0).status()).isEqualTo("READY");
        assertThat(summary.probes().get(0).endpoint()).isEqualTo("https://cluster.example/collections");
        assertThat(summary.managedVectorProvisioningEnabled()).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    void probeMarksWeaviateReadyApiFailedWhenClusterReturnsNotFound() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("WEAVIATE_API_KEY")).thenReturn("weaviate-secret");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(404);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && request.uri().toString().equals("https://weaviate.example:443/v1/.well-known/ready")
                && "Bearer weaviate-secret".equals(request.headers().firstValue("Authorization").orElse(null))),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        DeploymentProviderConnectivityService service = new DeploymentProviderConnectivityService(
            secretService,
            objectMapper,
            httpClient
        );

        DeploymentProviderConnectivitySummary summary = service.probe(
            deployment("dep-weaviate", "Weaviate"),
            draft("""
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "weaviate",
                  "weaviateScheme": "https",
                  "weaviateHost": "weaviate.example",
                  "weaviatePort": 443
                }
                """)
        );

        assertThat(summary.probes()).hasSize(1);
        assertThat(summary.probes().get(0).key()).isEqualTo("weaviate_ready_api");
        assertThat(summary.probes().get(0).status()).isEqualTo("FAILED");
        assertThat(summary.probes().get(0).endpoint()).isEqualTo("https://weaviate.example:443/v1/.well-known/ready");
        assertThat(summary.probes().get(0).message()).contains("HTTP 404");
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

    @SuppressWarnings("unchecked")
    @Test
    void probeMarksLlmEndpointReadyOnlyAfterAuthenticatedChatResponse() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("OPENAI_API_KEY")).thenReturn("llm-key");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
            {
              "choices": [
                {
                  "message": {
                    "content": "ok"
                  }
                }
              ]
            }
            """);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "POST".equals(request.method())
                && request.uri().toString().equals("https://shared-llm.example/v1/chat/completions")
                && "Bearer llm-key".equals(request.headers().firstValue("Authorization").orElse(null))
                && requestBody(request).contains("\"model\":\"llama3.1:8b\"")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        DeploymentProviderConnectivityService service = new DeploymentProviderConnectivityService(
            secretService,
            objectMapper,
            httpClient
        );

        DeploymentProviderConnectivitySummary summary = service.probe(
            deployment("dep-llm-ready", "Shared LLM"),
            draft("""
                {
                  "embeddingProvider": "openai",
                  "orchestrationLlmProvider": "openai",
                  "orchestrationBaseUrl": "https://shared-llm.example/v1",
                  "orchestrationModel": "llama3.1:8b",
                  "openaiModel": "gpt-4.1-mini"
                }
                """)
        );

        assertThat(summary.probes())
            .filteredOn(item -> "orchestration_inference_endpoint".equals(item.key()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.status()).isEqualTo("READY");
                assertThat(item.endpoint()).isEqualTo("https://shared-llm.example/v1/chat/completions");
                assertThat(item.message()).contains("accepted an authenticated probe");
            });
    }

    @SuppressWarnings("unchecked")
    @Test
    void probeMarksLlmEndpointFailedWhenAuthenticatedChatProbeReturnsUnauthorized() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("OPENAI_API_KEY")).thenReturn("bad-llm-key");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(response.body()).thenReturn("""
            {
              "error": {
                "message": "invalid api key"
              }
            }
            """);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && request.uri().toString().equals("https://shared-llm.example/v1/chat/completions")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        DeploymentProviderConnectivityService service = new DeploymentProviderConnectivityService(
            secretService,
            objectMapper,
            httpClient
        );

        DeploymentProviderConnectivitySummary summary = service.probe(
            deployment("dep-llm-failed", "Shared LLM"),
            draft("""
                {
                  "embeddingProvider": "openai",
                  "orchestrationLlmProvider": "openai",
                  "orchestrationBaseUrl": "https://shared-llm.example/v1"
                }
                """)
        );

        assertThat(summary.probes())
            .filteredOn(item -> "orchestration_inference_endpoint".equals(item.key()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.status()).isEqualTo("FAILED");
                assertThat(item.endpoint()).isEqualTo("https://shared-llm.example/v1/chat/completions");
                assertThat(item.message()).contains("HTTP 401");
            });
    }

    @Test
    void probeBlocksLlmEndpointWhenResolvedSecretIsMissing() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        HttpClient httpClient = mock(HttpClient.class);

        DeploymentProviderConnectivityService service = new DeploymentProviderConnectivityService(
            secretService,
            objectMapper,
            httpClient
        );

        DeploymentProviderConnectivitySummary summary = service.probe(
            deployment("dep-llm-blocked", "Shared LLM"),
            draft("""
                {
                  "embeddingProvider": "openai",
                  "orchestrationLlmProvider": "openai",
                  "orchestrationBaseUrl": "https://shared-llm.example/v1",
                  "orchestrationApiKeySecretRef": "DEPLOYMENT_OPENAI_LLM"
                }
                """)
        );

        assertThat(summary.probes())
            .filteredOn(item -> "orchestration_inference_endpoint".equals(item.key()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.status()).isEqualTo("BLOCKED");
                assertThat(item.endpoint()).isEqualTo("https://shared-llm.example/v1");
                assertThat(item.message()).contains("No effective secret is available");
            });
        verifyNoInteractions(httpClient);
    }

    @SuppressWarnings("unchecked")
    @Test
    void probeMarksEmbeddingEndpointReadyOnlyAfterAuthenticatedEmbeddingResponse() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("OPENAI_API_KEY")).thenReturn("emb-key");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
            {
              "data": [
                {
                  "embedding": [0.11, 0.22]
                }
              ]
            }
            """);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "POST".equals(request.method())
                && request.uri().toString().equals("https://shared.example/v1/embeddings")
                && "Bearer emb-key".equals(request.headers().firstValue("Authorization").orElse(null))),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        DeploymentProviderConnectivityService service = new DeploymentProviderConnectivityService(
            secretService,
            objectMapper,
            httpClient
        );

        DeploymentProviderConnectivitySummary summary = service.probe(
            deployment("dep-embed-ready", "Shared Embeddings"),
            draft("""
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "embeddingBaseUrl": "https://shared.example/v1"
                }
                """)
        );

        assertThat(summary.probes())
            .filteredOn(item -> "embedding_inference_endpoint".equals(item.key()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.status()).isEqualTo("READY");
                assertThat(item.endpoint()).isEqualTo("https://shared.example/v1/embeddings");
                assertThat(item.message()).contains("accepted an authenticated probe");
            });
    }

    @SuppressWarnings("unchecked")
    @Test
    void probeMarksEmbeddingEndpointFailedWhenAuthenticatedProbeReturnsUnauthorized() throws Exception {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.resolveSecret("OPENAI_API_KEY")).thenReturn("bad-key");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(response.body()).thenReturn("""
            {
              "error": {
                "message": "invalid api key"
              }
            }
            """);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && request.uri().toString().equals("https://shared.example/v1/embeddings")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        DeploymentProviderConnectivityService service = new DeploymentProviderConnectivityService(
            secretService,
            objectMapper,
            httpClient
        );

        DeploymentProviderConnectivitySummary summary = service.probe(
            deployment("dep-embed-failed", "Shared Embeddings"),
            draft("""
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "embeddingBaseUrl": "https://shared.example/v1"
                }
                """)
        );

        assertThat(summary.probes())
            .filteredOn(item -> "embedding_inference_endpoint".equals(item.key()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.status()).isEqualTo("FAILED");
                assertThat(item.endpoint()).isEqualTo("https://shared.example/v1/embeddings");
                assertThat(item.message()).contains("HTTP 401");
            });
    }

    @Test
    void probeBlocksEmbeddingEndpointWhenResolvedSecretIsMissing() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        HttpClient httpClient = mock(HttpClient.class);

        DeploymentProviderConnectivityService service = new DeploymentProviderConnectivityService(
            secretService,
            objectMapper,
            httpClient
        );

        DeploymentProviderConnectivitySummary summary = service.probe(
            deployment("dep-embed-blocked", "Shared Embeddings"),
            draft("""
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "embeddingBaseUrl": "https://shared.example/v1",
                  "embeddingApiKeySecretRef": "DEPLOYMENT_OPENAI_EMBEDDINGS"
                }
                """)
        );

        assertThat(summary.probes())
            .filteredOn(item -> "embedding_inference_endpoint".equals(item.key()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.status()).isEqualTo("BLOCKED");
                assertThat(item.endpoint()).isEqualTo("https://shared.example/v1");
                assertThat(item.message()).contains("No effective secret is available");
            });
        verifyNoInteractions(httpClient);
    }

    private DeploymentEntity deployment(String id, String name) {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId(id);
        deployment.setName(name);
        return deployment;
    }

    private String requestBody(HttpRequest request) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            CountDownLatch finished = new CountDownLatch(1);
            request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    byte[] bytes = new byte[item.remaining()];
                    item.get(bytes);
                    output.write(bytes, 0, bytes.length);
                }

                @Override
                public void onError(Throwable throwable) {
                    finished.countDown();
                }

                @Override
                public void onComplete() {
                    finished.countDown();
                }
            });
            finished.await(1, TimeUnit.SECONDS);
            return output.toString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read request body", ex);
        }
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
