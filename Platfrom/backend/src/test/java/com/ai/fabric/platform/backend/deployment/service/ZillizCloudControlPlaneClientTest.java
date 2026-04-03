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
}
