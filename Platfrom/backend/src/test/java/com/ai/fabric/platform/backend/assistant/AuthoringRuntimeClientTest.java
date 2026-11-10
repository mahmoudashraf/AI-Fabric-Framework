package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthoringRuntimeClientTest {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper();
    private final AuthoringProperties properties = new AuthoringProperties();
    private final PlatformSecretService secrets = mock(PlatformSecretService.class);
    private final RuntimePrivateAssertionSigningService signer = spy(new RuntimePrivateAssertionSigningService(secrets, mapper));
    private final HttpClient http = mock(HttpClient.class);
    private AuthoringRuntimeClient client;

    @BeforeEach void setup() {
        properties.setRuntimeUrl("https://authoring.internal");
        when(secrets.resolveSecret(properties.getApiKeySecretRef())).thenReturn("exact-api-key");
        when(secrets.resolveSecret(properties.getSigningKeySecretRef())).thenReturn("exact-signing-key");
        client = new AuthoringRuntimeClient(properties, secrets, signer, mapper);
        var transport = (HttpClient) ReflectionTestUtils.getField(client, "http");
        assertThat(transport.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        assertThat(transport.connectTimeout()).contains(Duration.ofSeconds(5));
        ReflectionTestUtils.setField(client, "http", http);
    }

    @Test void noFeedbackRetainsExistingCapsKeysClaimsAndExactPayload() throws Exception {
        var input = input();
        var pending = completed(200, "{\"status\":\"READY\",\"runtime\":{\"modelCalls\":1}}");
        try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(NOW);
            assertThat(client.generate(input).path("runtime").path("modelCalls").asInt()).isEqualTo(1);
        }
        assertThat(claims().expiresAt()).isEqualTo(NOW.plusSeconds(130));
        assertThat(request().timeout()).contains(Duration.ofSeconds(125));
        verify(pending).get(Duration.ofSeconds(125).toNanos(), TimeUnit.NANOSECONDS);
        assertRequest(input);
    }

    @ParameterizedTest
    @ValueSource(longs = {140_000, 127_000, 30_000, 250})
    void feedbackCapsAssertionHttpAndFutureWithoutRoundingToZero(long remainingMillis) throws Exception {
        var input = feedback(NOW.plusMillis(remainingMillis).toString());
        var pending = completed(200, "{}");
        try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(NOW);
            client.generate(input);
        }
        assertThat(claims().expiresAt()).isEqualTo(NOW.plusMillis(Math.min(130_000, remainingMillis)));
        Duration timeout = Duration.ofMillis(Math.min(125_000, remainingMillis));
        assertThat(request().timeout()).contains(timeout);
        verify(pending).get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        assertRequest(input);
    }

    @Test void parsesOffsetDeadlineAsTheSameInstant() {
        completed(200, "{}");
        try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(NOW);
            client.generate(feedback("2026-11-10T13:00:00.125+01:00"));
        }
        assertThat(claims().expiresAt()).isEqualTo(NOW.plusMillis(125));
        assertThat(request().timeout()).contains(Duration.ofMillis(125));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "null", "[]", "1", "{}", "{\"deadlineAt\":null}", "{\"deadlineAt\":123}",
        "{\"deadlineAt\":\"\"}", "{\"deadlineAt\":\"tomorrow\"}",
        "{\"deadlineAt\":\"2026-11-10T12:00:01\"}",
        "{\"deadlineAt\":\"2026-02-30T12:00:01Z\"}",
        "{\"deadlineAt\":\"2026-11-10T24:00:00Z\"}",
        "{\"deadlineAt\":\"2026-11-10T12:00:60Z\"}",
        "{\"deadlineAt\":\" 2026-11-10T12:00:01Z\"}",
        "{\"deadlineAt\":\"2026-11-10T12:00:01Z trailing\"}"
    })
    void malformedServerDeadlineFailsWithoutCredentialsOrHttp(String rawFeedback) throws Exception {
        var input = input();
        ((ObjectNode) input.path("catalogue")).set("validationFeedback", mapper.readTree(rawFeedback));
        assertFailure(() -> client.generate(input), HttpStatus.INTERNAL_SERVER_ERROR, "AUTHORING_VALIDATION_DEADLINE_INVALID");
        verifyNoInteractions(secrets, signer, http);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void expiredDeadlineFailsBeforeCredentialsAssertionOrHttp(long remainingNanos) {
        var input = feedback(NOW.plusNanos(remainingNanos).toString());
        try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(NOW);
            assertFailure(() -> client.generate(input), HttpStatus.GATEWAY_TIMEOUT, "AUTHORING_TIMEOUT");
        }
        verifyNoInteractions(secrets, signer, http);
    }

    @Test void expiryDuringSecretResolutionNeverIssuesAnExpiredAssertion() {
        Instant deadline = NOW.plusSeconds(1);
        var input = feedback(deadline.toString());
        try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(NOW);
            when(secrets.resolveSecret(properties.getApiKeySecretRef())).thenAnswer(ignored -> {
                time.when(Instant::now).thenReturn(deadline);
                return "exact-api-key";
            });
            assertFailure(() -> client.generate(input), HttpStatus.GATEWAY_TIMEOUT, "AUTHORING_TIMEOUT");
        }
        verifyNoInteractions(signer, http);
    }

    @Test void expiryDuringSigningMakesNoHttpCall() {
        Instant deadline = NOW.plusSeconds(1);
        var input = feedback(deadline.toString());
        try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(NOW);
            doAnswer(ignored -> {
                time.when(Instant::now).thenReturn(deadline);
                return "assertion";
            }).when(signer).issueAssertionWithSigningKey(any(), eq("exact-signing-key"));
            assertFailure(() -> client.generate(input), HttpStatus.GATEWAY_TIMEOUT, "AUTHORING_TIMEOUT");
        }
        verifyNoInteractions(http);
    }

    @Test void signingAndSendTimeReduceHttpAndFutureBudgetsSeparately() throws Exception {
        var input = feedback(NOW.plusSeconds(1).toString());
        var pending = spy(CompletableFuture.completedFuture(response(200, "{}")));
        Instant afterSigning = NOW.plusMillis(100);
        Instant afterSend = NOW.plusMillis(300);
        try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(NOW);
            doAnswer(ignored -> {
                time.when(Instant::now).thenReturn(afterSigning);
                return "assertion";
            }).when(signer).issueAssertionWithSigningKey(any(), anyString());
            when(http.<byte[]>sendAsync(any(), any())).thenAnswer(ignored -> {
                time.when(Instant::now).thenReturn(afterSend);
                return pending;
            });
            client.generate(input);
        }
        assertThat(claims().expiresAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(request().timeout()).contains(Duration.ofMillis(900));
        verify(pending).get(Duration.ofMillis(700).toNanos(), TimeUnit.NANOSECONDS);
    }

    @Test void expiryDuringSendCancelsPendingWithoutAZeroBudgetWait() throws Exception {
        Instant deadline = NOW.plusSeconds(1);
        var input = feedback(deadline.toString());
        var pending = spy(new CompletableFuture<HttpResponse<byte[]>>());
        try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(NOW);
            when(http.<byte[]>sendAsync(any(), any())).thenAnswer(ignored -> {
                time.when(Instant::now).thenReturn(deadline);
                return pending;
            });
            assertFailure(() -> client.generate(input), HttpStatus.GATEWAY_TIMEOUT, "AUTHORING_TIMEOUT");
        }
        verify(pending, never()).get(anyLong(), any());
        verify(pending).cancel(true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void futureTimeoutCancelsAndPreservesLegacyFailureWithoutFeedback(boolean feedback) throws Exception {
        var pending = spy(new CompletableFuture<HttpResponse<byte[]>>());
        doThrow(new TimeoutException()).when(pending).get(anyLong(), any());
        when(http.<byte[]>sendAsync(any(), any())).thenReturn(pending);
        try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(NOW);
            var input = feedback ? feedback(NOW.plusSeconds(1).toString()) : input();
            assertFailure(() -> client.generate(input), feedback ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY,
                feedback ? "AUTHORING_TIMEOUT" : "AUTHORING_RUNTIME_UNAVAILABLE");
        }
        verify(pending).cancel(true);
        verify(http).sendAsync(any(), any());
    }

    @Test void httpTimeoutWithFeedbackIsExplicitAndNeverRetried() {
        when(http.<byte[]>sendAsync(any(), any())).thenReturn(CompletableFuture.failedFuture(new HttpTimeoutException("timeout")));
        try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(NOW);
            assertFailure(() -> client.generate(feedback(NOW.plusSeconds(1).toString())), HttpStatus.GATEWAY_TIMEOUT, "AUTHORING_TIMEOUT");
        }
        verify(http).sendAsync(any(), any());
    }

    @Test void interruptionRestoresFlagAndCancelsPending() throws Exception {
        var pending = spy(new CompletableFuture<HttpResponse<byte[]>>());
        doThrow(new InterruptedException()).when(pending).get(anyLong(), any());
        when(http.<byte[]>sendAsync(any(), any())).thenReturn(pending);
        try {
            assertFailure(() -> client.generate(feedback(Instant.now().plusSeconds(30).toString())),
                HttpStatus.SERVICE_UNAVAILABLE, "AUTHORING_INTERRUPTED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(pending).cancel(true);
        } finally { Thread.interrupted(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUTHORING_INVALID_OUTPUT", "PROVIDER_BUDGET_LIMIT_EXCEEDED"})
    void preservesRuntimeFailureCodeAndAccountingReceipt(String code) {
        completed(429, "{\"error\":{\"code\":\"" + code + "\"},\"runtime\":{\"modelCalls\":1,\"tokensUsed\":180}}");
        assertThatThrownBy(() -> client.generate(feedback(Instant.now().plusSeconds(30).toString())))
            .isInstanceOfSatisfying(AuthoringRuntimeClient.AuthoringRuntimeFailure.class, failure -> {
                assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(failure.getReason()).isEqualTo(code);
                assertThat(failure.receipt().path("tokensUsed").asInt()).isEqualTo(180);
            });
        verify(http).sendAsync(any(), any());
    }

    private ObjectNode input() {
        var input = mapper.createObjectNode().put("sessionId", "session-1").put("turnId", "main-turn-id-attempt-2")
            .put("query", "  Keep the exact original intent.\nChange only this action.  ");
        input.putObject("currentConfiguration").putObject("actions").putObject("candidate").put("enabled", true);
        input.putObject("catalogue");
        return input;
    }
    private ObjectNode feedback(String deadline) {
        var input = input();
        var feedback = ((ObjectNode) input.path("catalogue")).putObject("validationFeedback")
            .put("attempt", 2).put("maxAttempts", 3).put("deadlineAt", deadline);
        feedback.putArray("previousFindings").addObject().put("path", "/actions/candidate").put("message", "Missing parameter");
        feedback.putArray("previousChanges").addObject().put("path", "/actions/candidate").put("operation", "SET");
        return input;
    }
    private CompletableFuture<HttpResponse<byte[]>> completed(int status, String body) {
        var pending = spy(CompletableFuture.completedFuture(response(status, body)));
        when(http.<byte[]>sendAsync(any(), any())).thenReturn(pending);
        return pending;
    }
    private HttpResponse<byte[]> response(int status, String body) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        return response;
    }
    private HttpRequest request() {
        var capture = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(capture.capture(), any());
        return capture.getValue();
    }
    private RuntimePrivateAssertionClaims claims() {
        var capture = ArgumentCaptor.forClass(RuntimePrivateAssertionClaims.class);
        verify(signer).issueAssertionWithSigningKey(capture.capture(), eq("exact-signing-key"));
        return capture.getValue();
    }
    private void assertRequest(ObjectNode input) throws Exception {
        var request = request();
        assertThat(request.uri().toString()).isEqualTo("https://authoring.internal/api/private/configuration-authoring/generate");
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers().firstValue("X-AIFABRIC-RUNTIME-API-KEY")).contains("exact-api-key");
        assertThat(request.headers().firstValue(RuntimePrivateAssertionSigningService.AUTHORIZATION_HEADER)).get().asString().startsWith("Bearer rpa1.");
        var subscriber = HttpResponse.BodySubscribers.ofByteArray();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscriber.onSubscribe(subscription); }
            public void onNext(ByteBuffer buffer) { subscriber.onNext(List.of(buffer)); }
            public void onError(Throwable failure) { subscriber.onError(failure); }
            public void onComplete() { subscriber.onComplete(); }
        });
        assertThat(mapper.readTree(subscriber.getBody().toCompletableFuture().get(1, TimeUnit.SECONDS))).isEqualTo(input);
        var claims = claims();
        assertThat(claims.subjectId()).isEqualTo("authoring:session-1:main-turn-id-attempt-2");
        assertThat(claims.sessionId()).isEqualTo(claims.subjectId());
        assertThat(claims.deploymentId()).isEqualTo(properties.getDeploymentId());
        assertThat(claims.customerId()).isEqualTo(properties.getCustomerId());
        assertThat(claims.tenantId()).isEqualTo(properties.getTenantId());
        assertThat(claims.issuer()).isEqualTo(properties.getIssuer());
        assertThat(claims.audiences()).containsExactly(properties.getAudience());
        assertThat(claims.grantedScopes()).containsExactly("configuration:author");
        verify(secrets).resolveSecret(properties.getApiKeySecretRef());
        verify(secrets).resolveSecret(properties.getSigningKeySecretRef());
        verifyNoMoreInteractions(secrets);
    }
    private void assertFailure(Runnable action, HttpStatus status, String reason) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
            assertThat(failure.getStatusCode()).isEqualTo(status);
            assertThat(failure.getReason()).isEqualTo(reason);
        });
    }
}
