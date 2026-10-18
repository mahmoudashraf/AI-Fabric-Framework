package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.stream.Stream;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ZillizCloudControlPlaneClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void servingCollectionListUsesExactBearerCredentialAndOnlyDefaultDatabaseMetadata(boolean tokenMode) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"code\":0,\"data\":[\"scoped_vehicle\",\"scoped_document\"]}");
        when(httpClient.<String>send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var client = new ZillizCloudControlPlaneClient(objectMapper, httpClient);
        var collections = tokenMode ? client.listServingCollections("https://synthetic.zilliz.com", "fake-native-token")
            : client.listServingCollections("https://synthetic.zilliz.com", "fake-user", "fake-password");
        assertThat(collections).containsExactly("scoped_vehicle", "scoped_document");
        assertThatThrownBy(() -> collections.add("other")).isInstanceOf(UnsupportedOperationException.class);
        var captured = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captured.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = captured.getValue();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri().toString()).isEqualTo("https://synthetic.zilliz.com/v2/vectordb/collections/list");
        assertThat(request.headers().firstValue("Authorization")).contains(tokenMode ? "Bearer fake-native-token" : "Bearer fake-user:fake-password");
        assertThat(request.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(requestBody(request)).isEqualTo("{\"dbName\":\"default\"}");
        verifyNoMoreInteractions(httpClient);
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest @MethodSource("invalidServingPayloads")
    void servingModesShareStrictCodeZeroAndCollectionArrayParser(String payload) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(payload);
        when(httpClient.<String>send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var client = new ZillizCloudControlPlaneClient(objectMapper, httpClient);
        assertThatThrownBy(() -> client.listServingCollections("https://synthetic.zilliz.com", "fake-native-token"))
            .isInstanceOf(RailwayProvisioningException.class).hasMessageNotContaining("fake-native-token").hasNoCause();
        assertThatThrownBy(() -> client.listServingCollections("https://synthetic.zilliz.com", "fake-user", "fake-password"))
            .isInstanceOf(RailwayProvisioningException.class).hasMessageNotContaining("fake-native-token").hasNoCause();
    }

    static Stream<String> invalidServingPayloads() {
        return Stream.of("", "null", "[]", "{\"data\":[]}", "{\"code\":\"0\",\"data\":[]}",
            "{\"code\":0.0,\"data\":[]}", "{\"code\":false,\"data\":[]}", "{\"code\":null,\"data\":[]}",
            "{\"code\":4294967296,\"data\":[]}", "{\"code\":1,\"data\":[],\"message\":\"fake-native-token\"}",
            "{\"code\":0}", "{\"code\":0,\"data\":{}}", "{\"code\":0,\"data\":[null]}",
            "{\"code\":0,\"data\":[42]}", "{\"code\":0,\"data\":[\"\"]}", "{\"code\":0,\"data\":[\" \"]}",
            "{\"code\":0,\"data\":[{}]}", "{\"code\":0,\"fake-native-token\":",
            "{\"code\":0,\"data\":[" + String.join(",", java.util.Collections.nCopies(101, "\"collection\"")) + "]}");
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest @ValueSource(ints = {302, 401, 403, 500})
    void servingHttpFailuresNeverExposeResponseBody(int status) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn("fake-native-token");
        when(httpClient.<String>send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var client = new ZillizCloudControlPlaneClient(objectMapper, httpClient);
        assertThatThrownBy(() -> client.listServingCollections("https://synthetic.zilliz.com", "fake-native-token"))
            .hasMessageContaining("HTTP " + status).hasMessageNotContaining("fake-native-token").hasNoCause();
    }

    @SuppressWarnings("unchecked")
    @Test void servingTransportFailureOmitsCredentialBearingCause() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.<String>send(any(), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("fake-native-token"));
        var client = new ZillizCloudControlPlaneClient(objectMapper, httpClient);
        assertThatThrownBy(() -> client.listServingCollections("https://synthetic.zilliz.com", "fake-native-token"))
            .hasMessageNotContaining("fake-native-token").hasNoCause();
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", " fake-native-token", "fake-native-token\n", "fake\nnative-token"})
    void invalidTokenNeverLeaksIntoHeaderValidationException(String token) {
        HttpClient httpClient = mock(HttpClient.class);
        var client = new ZillizCloudControlPlaneClient(objectMapper, httpClient);
        assertThatThrownBy(() -> client.listServingCollections("https://synthetic.zilliz.com", token))
            .isInstanceOf(RailwayProvisioningException.class).hasMessageNotContaining("fake").hasNoCause();
        verifyNoInteractions(httpClient);
    }

    private static String requestBody(HttpRequest request) throws Exception {
        var result = new CompletableFuture<String>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer item) {
                byte[] value = new byte[item.remaining()]; item.get(value); bytes.writeBytes(value);
            }
            public void onError(Throwable error) { result.completeExceptionally(error); }
            public void onComplete() { result.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return result.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    @Test
    void resolveProjectUsesV2ProjectsEndpointAndReturnsSingleScopedProject() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> projectResponse = mock(HttpResponse.class);
        when(projectResponse.statusCode()).thenReturn(200);
        when(projectResponse.body()).thenReturn("""
            {
              "code": 0,
              "data": [
                {
                  "projectName": "Default Project",
                  "projectId": "proj-a58a34b87ccfe2c80d6ec2"
                }
              ]
            }
            """);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().startsWith("https://api.cloud.zilliz.com/v2/projects")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(projectResponse);

        ZillizCloudControlPlaneClient client = new ZillizCloudControlPlaneClient(objectMapper, httpClient);

        ZillizCloudControlPlaneClient.ZillizProjectResolution project = client.resolveProject(
            "",
            "aws-eu-central-1",
            "zilliz-key"
        );

        assertThat(project.projectId()).isEqualTo("proj-a58a34b87ccfe2c80d6ec2");
        assertThat(project.projectName()).isEqualTo("Default Project");
        assertThat(project.autoResolved()).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void failureOmitsRawResponseBody() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(403);
        when(response.body()).thenReturn("{\"password\":\"should-not-leak\"}");
        when(httpClient.<String>send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        ZillizCloudControlPlaneClient client = new ZillizCloudControlPlaneClient(objectMapper, httpClient);

        assertThatThrownBy(() -> client.verifyControlPlaneAccess("zilliz-key"))
            .isInstanceOf(RailwayProvisioningException.class)
            .hasMessageContaining("Zilliz Cloud control-plane access check failed with HTTP 403")
            .hasMessageContaining("Upstream response body omitted")
            .hasMessageNotContaining("should-not-leak");
    }

    @SuppressWarnings("unchecked")
    @Test
    void createClusterAcceptsNestedClusterIdResponse() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> createResponse = mock(HttpResponse.class);
        when(createResponse.statusCode()).thenReturn(200);
        when(createResponse.body()).thenReturn("""
            {
              "data": {
                "cluster": {
                  "clusterId": "cluster-1"
                },
                "username": "db-user",
                "password": "db-password"
              }
            }
            """);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "POST".equals(request.method())
                && request.uri().toString().startsWith("https://api.cloud.zilliz.com/v2/clusters/createServerless")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(createResponse);

        ZillizCloudControlPlaneClient client = new ZillizCloudControlPlaneClient(objectMapper, httpClient);

        ZillizCloudControlPlaneClient.ZillizClusterCreateResult result = client.createCluster(
            "aifabric-123",
            "project-1",
            "aws-eu-central-1",
            "Serverless",
            "",
            0,
            "zilliz-key"
        );

        assertThat(result.clusterId()).isEqualTo("cluster-1");
        assertThat(result.username()).isEqualTo("db-user");
        assertThat(result.password()).isEqualTo("db-password");
    }

    @SuppressWarnings("unchecked")
    @Test
    void createClusterFallsBackToLookupByNameWhenCreateResponseOmitsClusterId() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> createResponse = mock(HttpResponse.class);
        when(createResponse.statusCode()).thenReturn(200);
        when(createResponse.body()).thenReturn("""
            {
              "data": {
                "requestId": "req-1",
                "username": "db-user",
                "password": "db-password"
              }
            }
            """);
        HttpResponse<String> listResponse = mock(HttpResponse.class);
        when(listResponse.statusCode()).thenReturn(200);
        when(listResponse.body()).thenReturn("""
            {
              "data": {
                "clusters": [
                  {
                    "clusterId": "cluster-2",
                    "clusterName": "aifabric-123",
                    "projectId": "project-1",
                    "regionId": "aws-eu-central-1",
                    "status": "CREATING",
                    "connectAddress": ""
                  }
                ],
                "count": 1,
                "pageSize": 100
              }
            }
            """);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "POST".equals(request.method())
                && request.uri().toString().startsWith("https://api.cloud.zilliz.com/v2/clusters/createServerless")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(createResponse);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().startsWith("https://api.cloud.zilliz.com/v2/clusters")
                && request.uri().getQuery() != null
                && request.uri().getQuery().contains("pageSize=100")
                && request.uri().getQuery().contains("currentPage=1")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(listResponse);

        ZillizCloudControlPlaneClient client = new ZillizCloudControlPlaneClient(objectMapper, httpClient);

        ZillizCloudControlPlaneClient.ZillizClusterCreateResult result = client.createCluster(
            "aifabric-123",
            "project-1",
            "aws-eu-central-1",
            "Serverless",
            "",
            0,
            "zilliz-key"
        );

        assertThat(result.clusterId()).isEqualTo("cluster-2");
        assertThat(result.username()).isEqualTo("db-user");
        assertThat(result.password()).isEqualTo("db-password");
    }

    @SuppressWarnings("unchecked")
    @Test
    void createClusterSurfacesApplicationCodeMessageWhenHttpStatusIs200() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> createResponse = mock(HttpResponse.class);
        when(createResponse.statusCode()).thenReturn(200);
        when(createResponse.body()).thenReturn("""
            {
              "code": 40060,
              "message": "You can only have one serverless cluster. To create more serverless clusters, please add a payment method."
            }
            """);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "POST".equals(request.method())
                && request.uri().toString().startsWith("https://api.cloud.zilliz.com/v2/clusters/createServerless")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(createResponse);

        ZillizCloudControlPlaneClient client = new ZillizCloudControlPlaneClient(objectMapper, httpClient);

        assertThatThrownBy(() -> client.createCluster(
            "aifabric-123",
            "project-1",
            "aws-eu-central-1",
            "Serverless",
            "",
            0,
            "zilliz-key"
        ))
            .isInstanceOf(RailwayProvisioningException.class)
            .hasMessageContaining("Zilliz Cloud cluster creation failed")
            .hasMessageContaining("one serverless cluster")
            .hasMessageContaining("40060");
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteClusterUsesDropEndpoint() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> deleteResponse = mock(HttpResponse.class);
        when(deleteResponse.statusCode()).thenReturn(200);
        when(deleteResponse.body()).thenReturn("""
            {
              "code": 0,
              "message": "ok"
            }
            """);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "DELETE".equals(request.method())
                && "https://api.cloud.zilliz.com/v2/clusters/cluster-1/drop".equals(request.uri().toString())),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(deleteResponse);

        ZillizCloudControlPlaneClient client = new ZillizCloudControlPlaneClient(objectMapper, httpClient);

        client.deleteCluster("cluster-1", "zilliz-key");
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteClusterTreatsAlreadyDeletedResponseAsSuccess() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> deleteResponse = mock(HttpResponse.class);
        when(deleteResponse.statusCode()).thenReturn(200);
        when(deleteResponse.body()).thenReturn("""
            {
              "code": 40064,
              "message": "instance not support DELETE when instance status is DELETED"
            }
            """);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "DELETE".equals(request.method())
                && "https://api.cloud.zilliz.com/v2/clusters/cluster-1/drop".equals(request.uri().toString())),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(deleteResponse);

        ZillizCloudControlPlaneClient client = new ZillizCloudControlPlaneClient(objectMapper, httpClient);

        client.deleteCluster("cluster-1", "zilliz-key");
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteClusterRetriesAfterCreatingStateBecomesStable() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);

        HttpResponse<String> firstDeleteResponse = mock(HttpResponse.class);
        when(firstDeleteResponse.statusCode()).thenReturn(200);
        when(firstDeleteResponse.body()).thenReturn("""
            {
              "code": 40064,
              "message": "instance not support DELETE when instance status is CREATING"
            }
            """);

        HttpResponse<String> creatingLookupResponse = mock(HttpResponse.class);
        when(creatingLookupResponse.statusCode()).thenReturn(200);
        when(creatingLookupResponse.body()).thenReturn("""
            {
              "code": 0,
              "data": {
                "clusterId": "cluster-1",
                "clusterName": "aifabric-123",
                "projectId": "project-1",
                "regionId": "aws-eu-central-1",
                "status": "CREATING"
              }
            }
            """);

        HttpResponse<String> runningLookupResponse = mock(HttpResponse.class);
        when(runningLookupResponse.statusCode()).thenReturn(200);
        when(runningLookupResponse.body()).thenReturn("""
            {
              "code": 0,
              "data": {
                "clusterId": "cluster-1",
                "clusterName": "aifabric-123",
                "projectId": "project-1",
                "regionId": "aws-eu-central-1",
                "status": "RUNNING"
              }
            }
            """);

        HttpResponse<String> secondDeleteResponse = mock(HttpResponse.class);
        when(secondDeleteResponse.statusCode()).thenReturn(200);
        when(secondDeleteResponse.body()).thenReturn("""
            {
              "code": 0,
              "message": "ok"
            }
            """);

        when(httpClient.<String>send(
            argThat(request -> request != null
                && "DELETE".equals(request.method())
                && "https://api.cloud.zilliz.com/v2/clusters/cluster-1/drop".equals(request.uri().toString())),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(firstDeleteResponse, secondDeleteResponse);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && "https://api.cloud.zilliz.com/v2/clusters/cluster-1".equals(request.uri().toString())),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(creatingLookupResponse, runningLookupResponse);

        ZillizCloudControlPlaneClient client = new ZillizCloudControlPlaneClient(objectMapper, httpClient);

        client.deleteCluster("cluster-1", "zilliz-key");
    }

    @SuppressWarnings("unchecked")
    @Test
    void getClusterReturnsNullWhenVendorReportsDeletedStatus() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> lookupResponse = mock(HttpResponse.class);
        when(lookupResponse.statusCode()).thenReturn(200);
        when(lookupResponse.body()).thenReturn("""
            {
              "code": 0,
              "data": {
                "clusterId": "cluster-1",
                "clusterName": "aifabric-123",
                "projectId": "project-1",
                "regionId": "aws-eu-central-1",
                "status": "DELETED"
              }
            }
            """);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && "https://api.cloud.zilliz.com/v2/clusters/cluster-1".equals(request.uri().toString())),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(lookupResponse);

        ZillizCloudControlPlaneClient client = new ZillizCloudControlPlaneClient(objectMapper, httpClient);

        assertThat(client.getCluster("cluster-1", "zilliz-key")).isNull();
    }
}
