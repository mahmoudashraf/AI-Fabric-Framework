package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZillizCloudControlPlaneClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
}
