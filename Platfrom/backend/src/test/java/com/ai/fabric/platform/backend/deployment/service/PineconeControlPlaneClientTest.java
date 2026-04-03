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

class PineconeControlPlaneClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    @Test
    void findIndexByNameEncodesIndexPathSegment() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
            {
              "name": "index with spaces",
              "host": "example.svc.pinecone.io",
              "dimension": 1536,
              "metric": "cosine",
              "status": { "ready": true }
            }
            """);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && "https://api.pinecone.io/indexes/index%20with%20spaces".equals(request.uri().toString())),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        PineconeControlPlaneClient client = new PineconeControlPlaneClient(objectMapper, httpClient);

        PineconeControlPlaneClient.PineconeIndexSummary summary = client.findIndexByName("index with spaces", "pinecone-key");

        assertThat(summary).isNotNull();
        assertThat(summary.name()).isEqualTo("index with spaces");
    }

    @SuppressWarnings("unchecked")
    @Test
    void failureOmitsRawResponseBody() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(403);
        when(response.body()).thenReturn("{\"secret\":\"should-not-leak\"}");
        when(httpClient.<String>send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        PineconeControlPlaneClient client = new PineconeControlPlaneClient(objectMapper, httpClient);

        assertThatThrownBy(() -> client.verifyControlPlaneAccess("pinecone-key"))
            .isInstanceOf(RailwayProvisioningException.class)
            .hasMessageContaining("Pinecone control-plane access check failed with HTTP 403")
            .hasMessageContaining("Upstream response body omitted")
            .hasMessageNotContaining("should-not-leak");
    }
}
