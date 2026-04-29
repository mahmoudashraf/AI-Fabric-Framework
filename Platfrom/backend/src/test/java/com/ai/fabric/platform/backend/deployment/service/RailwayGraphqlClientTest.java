package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.model.RailwayLogEntrySummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
            .hasMessageContaining("Railway API request failed after 5/5 attempt(s).")
            .hasMessageContaining("ConnectException: Connection refused");

        assertThat(retryCount).hasValue(4);
        verify(httpClient, times(5)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
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

    @Test
    void getProjectUsesLongerRailwayRequestTimeoutFloor() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();
        RailwayGraphqlClient client = new RailwayGraphqlClient(
            objectMapper,
            provisioningProperties(),
            httpClient,
            duration -> {
            }
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
            .thenAnswer(invocation -> {
                capturedRequest.set(invocation.getArgument(0));
                return response;
            });

        client.getProject("proj-1");

        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().timeout()).contains(Duration.ofSeconds(45));
    }

    @Test
    void fetchHttpLogsRequestsOnlySupportedHttpLogFields() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();
        RailwayGraphqlClient client = new RailwayGraphqlClient(
            objectMapper,
            provisioningProperties(),
            httpClient,
            duration -> {
            }
        );

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
            {
              "data": {
                "httpLogs": [
                  {
                    "timestamp": "2026-04-20T21:19:00Z",
                    "message": "GET /api/admin/indexing/overview 200"
                  }
                ]
              }
            }
            """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                capturedRequest.set(invocation.getArgument(0));
                return response;
            });

        List<RailwayLogEntrySummary> logs = client.fetchHttpLogs(
            "dep-123",
            25,
            "indexing",
            "2026-04-20T21:00:00Z",
            "2026-04-20T21:30:00Z"
        );

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).timestamp()).isEqualTo("2026-04-20T21:19:00Z");
        assertThat(logs.get(0).message()).contains("/api/admin/indexing/overview");
        assertThat(logs.get(0).severity()).isNull();
        assertThat(logs.get(0).attributes()).isEmpty();

        String body = requestBody(capturedRequest.get());
        assertThat(body).contains("httpLogs");
        assertThat(body).contains("timestamp");
        assertThat(body).contains("message");
        assertThat(body).doesNotContain("severity");
        assertThat(body).doesNotContain("tags");
        assertThat(body).doesNotContain("attributes");
    }

    @Test
    void fetchHttpLogsFallsBackToMinimalFieldSetAfterGraphQlValidationFailure() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> failure = mock(HttpResponse.class);
        HttpResponse<String> success = mock(HttpResponse.class);
        List<String> requestBodies = new ArrayList<>();
        RailwayGraphqlClient client = new RailwayGraphqlClient(
            objectMapper,
            provisioningProperties(),
            httpClient,
            duration -> {
            }
        );

        when(failure.statusCode()).thenReturn(200);
        when(failure.body()).thenReturn("""
            {
              "errors": [
                {
                  "message": "Cannot query field \\\"message\\\" on type \\\"HttpLog\\\"."
                }
              ]
            }
            """);
        when(success.statusCode()).thenReturn(200);
        when(success.body()).thenReturn("""
            {
              "data": {
                "httpLogs": [
                  {
                    "timestamp": "2026-04-20T21:29:00Z"
                  }
                ]
              }
            }
            """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                requestBodies.add(requestBody(invocation.getArgument(0)));
                return requestBodies.size() == 1 ? failure : success;
            });

        List<RailwayLogEntrySummary> logs = client.fetchHttpLogs(
            "dep-123",
            10,
            null,
            null,
            null
        );

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).timestamp()).isEqualTo("2026-04-20T21:29:00Z");
        assertThat(logs.get(0).message()).isNull();
        assertThat(logs.get(0).severity()).isNull();
        assertThat(requestBodies).hasSize(2);
        assertThat(requestBodies.get(0)).contains("message");
        assertThat(requestBodies.get(1)).contains("timestamp");
        assertThat(requestBodies.get(1)).doesNotContain("message");
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void updateServiceInstanceAlsoAppliesDefaultCpuAndMemoryLimits() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> updateResponse = mock(HttpResponse.class);
        HttpResponse<String> limitsResponse = mock(HttpResponse.class);
        List<String> requestBodies = new ArrayList<>();
        RailwayGraphqlClient client = new RailwayGraphqlClient(
            objectMapper,
            provisioningProperties(),
            httpClient,
            duration -> {
            }
        );

        when(updateResponse.statusCode()).thenReturn(200);
        when(updateResponse.body()).thenReturn("""
            {
              "data": {
                "serviceInstanceUpdate": true
              }
            }
            """);
        when(limitsResponse.statusCode()).thenReturn(200);
        when(limitsResponse.body()).thenReturn("""
            {
              "data": {
                "serviceInstanceLimitsUpdate": true
              }
            }
            """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                requestBodies.add(requestBody(invocation.getArgument(0)));
                return requestBodies.size() == 1 ? updateResponse : limitsResponse;
            });

        client.updateServiceInstance(
            "svc-123",
            "env-123",
            "runtime-root",
            "runtime/Dockerfile",
            "/actuator/health"
        );

        assertThat(requestBodies).hasSize(2);
        assertThat(requestBodies.get(0)).contains("serviceInstanceUpdate");
        assertThat(requestBodies.get(1)).contains("serviceInstanceLimitsUpdate");
        assertThat(requestBodies.get(1)).contains("\"vCPUs\":1.0");
        assertThat(requestBodies.get(1)).contains("\"memoryGB\":1.0");
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

    private String requestBody(HttpRequest request) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            CountDownLatch finished = new CountDownLatch(1);
            request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
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
}
