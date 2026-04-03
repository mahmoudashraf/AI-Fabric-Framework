package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QdrantCloudControlPlaneClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    @Test
    void findClusterByNameMatchesExistingClusterCaseInsensitively() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> clustersResponse = mock(HttpResponse.class);
        when(clustersResponse.statusCode()).thenReturn(200);
        when(clustersResponse.body()).thenReturn("""
            {
              "items": [
                {
                  "id": "cluster-1",
                  "accountId": "acct-1",
                  "name": "Platform-Deployments",
                  "cloudProviderId": "aws",
                  "cloudProviderRegionId": "eu-west-1",
                  "configuration": {
                    "packageId": "pkg-old"
                  },
                  "state": {
                    "phase": "CLUSTER_PHASE_HEALTHY",
                    "endpoint": {
                      "url": "https://cluster-1.eu-west-1.aws.cloud.qdrant.io",
                      "restPort": 6333,
                      "grpcPort": 6334
                    }
                  },
                  "labels": [
                    {
                      "key": "managed-by",
                      "value": "ai-fabric-platform"
                    }
                  ]
                }
              ]
            }
            """);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().startsWith("https://api.cloud.qdrant.io/api/cluster/v1/accounts/acct-1/clusters")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(clustersResponse);

        QdrantCloudControlPlaneClient client = new QdrantCloudControlPlaneClient(objectMapper, httpClient);

        QdrantCloudControlPlaneClient.QdrantCloudClusterSummary cluster = client.findClusterByName(
            "acct-1",
            "platform-deployments",
            "qdrant-cloud-management"
        );

        assertThat(cluster).isNotNull();
        assertThat(cluster.id()).isEqualTo("cluster-1");
        assertThat(cluster.name()).isEqualTo("Platform-Deployments");
        assertThat(cluster.packageId()).isEqualTo("pkg-old");
        assertThat(cluster.labels()).isEqualTo(Map.of("managed-by", "ai-fabric-platform"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void createCollectionDatabaseApiKeyUsesGlobalManageAccessByOmittingAccessRules() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> createResponse = mock(HttpResponse.class);
        when(createResponse.statusCode()).thenReturn(200);
        when(createResponse.body()).thenReturn("""
            {
              "databaseApiKey": {
                "id": "key-1",
                "clusterId": "cluster-1",
                "name": "ai-fabric-123",
                "postfix": "abcd",
                "key": "runtime-key"
              }
            }
            """);
        when(httpClient.<String>send(
            argThat(request -> {
                if (request == null || !"POST".equals(request.method())
                    || !request.uri().toString().startsWith("https://api.cloud.qdrant.io/api/cluster/auth/v2/accounts/acct-1/database-api-keys")) {
                    return false;
                }
                try {
                    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(4096);
                    request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<>() {
                        private java.util.concurrent.Flow.Subscription subscription;
                        @Override public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                            this.subscription = subscription;
                            subscription.request(Long.MAX_VALUE);
                        }
                        @Override public void onNext(java.nio.ByteBuffer item) { buffer.put(item); }
                        @Override public void onError(Throwable throwable) { throw new RuntimeException(throwable); }
                        @Override public void onComplete() { subscription.cancel(); }
                    });
                    String body = new String(buffer.array(), 0, buffer.position(), java.nio.charset.StandardCharsets.UTF_8);
                    return body.contains("\"name\":\"ai-fabric-123\"")
                        && body.contains("\"clusterId\":\"cluster-1\"")
                        && !body.contains("accessRules");
                } catch (Exception ex) {
                    return false;
                }
            }),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(createResponse);

        QdrantCloudControlPlaneClient client = new QdrantCloudControlPlaneClient(objectMapper, httpClient);

        QdrantCloudControlPlaneClient.QdrantCloudDatabaseApiKeySummary key = client.createCollectionDatabaseApiKey(
            "acct-1",
            "cluster-1",
            "ai-fabric-123",
            java.util.List.of("product"),
            "qdrant-cloud-management"
        );

        assertThat(key.id()).isEqualTo("key-1");
        assertThat(key.name()).isEqualTo("ai-fabric-123");
        assertThat(key.key()).isEqualTo("runtime-key");
    }

    @SuppressWarnings("unchecked")
    @Test
    void failureOmitsRawResponseBody() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(response.body()).thenReturn("{\"token\":\"should-not-leak\"}");
        when(httpClient.<String>send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        QdrantCloudControlPlaneClient client = new QdrantCloudControlPlaneClient(objectMapper, httpClient);

        assertThatThrownBy(() -> client.resolveAccount("", "qdrant-cloud-management"))
            .isInstanceOf(RailwayProvisioningException.class)
            .hasMessageContaining("Qdrant Cloud account lookup failed with HTTP 401")
            .hasMessageContaining("Upstream response body omitted")
            .hasMessageNotContaining("should-not-leak");
    }
}
