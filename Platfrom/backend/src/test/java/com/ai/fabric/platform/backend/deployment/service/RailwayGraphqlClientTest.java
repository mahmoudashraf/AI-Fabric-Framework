package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RailwayGraphqlClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void hasMeaningfulStagedChangesReturnsFalseForEmptyPatchPlaceholder() throws Exception {
        assertThat(RailwayGraphqlClient.hasMeaningfulStagedChanges(objectMapper.readTree("""
            {
              "id": "<empty>",
              "status": "STAGED",
              "message": null,
              "lastAppliedError": null
            }
            """))).isFalse();
    }

    @Test
    void hasMeaningfulStagedChangesReturnsTrueForRealPatch() throws Exception {
        assertThat(RailwayGraphqlClient.hasMeaningfulStagedChanges(objectMapper.readTree("""
            {
              "id": "patch-123",
              "status": "STAGED"
            }
            """))).isTrue();
    }

    @Test
    void getProjectRetriesTransientIoFailureThenSucceeds() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        AtomicInteger retryCount = new AtomicInteger();
        RailwayGraphqlClient client = new RailwayGraphqlClient(
            objectMapper,
            provisioningProperties(),
            httpClient,
            duration -> retryCount.incrementAndGet()
        );

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
            {
              "data": {
                "project": {
                  "id": "proj-1",
                  "name": "cleanup-dev",
                  "services": { "edges": [] },
                  "environments": { "edges": [] }
                }
              }
            }
            """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new IOException("Connection reset by peer"))
            .thenReturn(response);

        RailwayGraphqlClient.RailwayProjectSnapshot project = client.getProject("proj-1");

        assertThat(project.id()).isEqualTo("proj-1");
        assertThat(project.name()).isEqualTo("cleanup-dev");
        assertThat(retryCount).hasValue(1);
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void getProjectFailureIncludesFinalIoCauseAfterRetriesExhausted() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        AtomicInteger retryCount = new AtomicInteger();
        RailwayGraphqlClient client = new RailwayGraphqlClient(
            objectMapper,
            provisioningProperties(),
            httpClient,
            duration -> retryCount.incrementAndGet()
        );

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new ConnectException("Connection refused"));

        assertThatThrownBy(() -> client.getProject("proj-1"))
            .isInstanceOf(RailwayProvisioningException.class)
            .hasMessageContaining("Railway API request failed after 3/3 attempt(s).")
            .hasMessageContaining("ConnectException: Connection refused");

        assertThat(retryCount).hasValue(2);
        verify(httpClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void getProjectRetriesRetryableHttpStatusThenSucceeds() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> failure = mock(HttpResponse.class);
        HttpResponse<String> success = mock(HttpResponse.class);
        AtomicInteger retryCount = new AtomicInteger();
        RailwayGraphqlClient client = new RailwayGraphqlClient(
            objectMapper,
            provisioningProperties(),
            httpClient,
            duration -> retryCount.incrementAndGet()
        );

        when(failure.statusCode()).thenReturn(503);
        when(failure.body()).thenReturn("{\"error\":\"upstream unavailable\"}");
        when(success.statusCode()).thenReturn(200);
        when(success.body()).thenReturn("""
            {
              "data": {
                "project": {
                  "id": "proj-1",
                  "name": "cleanup-dev",
                  "services": { "edges": [] },
                  "environments": { "edges": [] }
                }
              }
            }
            """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(failure)
            .thenReturn(success);

        RailwayGraphqlClient.RailwayProjectSnapshot project = client.getProject("proj-1");

        assertThat(project.id()).isEqualTo("proj-1");
        assertThat(retryCount).hasValue(1);
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private PlatformProvisioningProperties provisioningProperties() {
        return new PlatformProvisioningProperties(
            "RAILWAY_API",
            "https://railway.example/graphql/v2",
            "token",
            "repo",
            "main",
            "dev",
            "workspace",
            "runtime-root",
            "runtime/Dockerfile",
            "connector-root",
            "connector/Dockerfile",
            "runtime",
            "rest",
            32,
            "",
            "",
            false,
            false,
            60_000,
            Duration.ofSeconds(1),
            Duration.ofMinutes(1)
        );
    }
}
