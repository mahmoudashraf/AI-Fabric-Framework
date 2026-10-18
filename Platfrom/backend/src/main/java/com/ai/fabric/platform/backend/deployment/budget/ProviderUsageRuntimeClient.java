package com.ai.fabric.platform.backend.deployment.budget;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;

/** Private provisioning traffic only. No provider calls, redirects or retry loop. */
@Component
public class ProviderUsageRuntimeClient {
    private static final String PATH = "/api/admin/provider-budget";
    private final HttpClient client;
    private final ObjectMapper mapper;

    @org.springframework.beans.factory.annotation.Autowired
    public ProviderUsageRuntimeClient(ObjectMapper mapper) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build(), mapper);
    }

    ProviderUsageRuntimeClient(HttpClient client, ObjectMapper mapper) { this.client = client; this.mapper = mapper; }

    public Readback read(String baseUrl, Map<String, String> headers) {
        return exchange(baseUrl, PATH, null, headers, Readback.class);
    }

    public StoreReceipt initialize(String baseUrl, SafeAllowanceProjection allowance, Map<String, String> headers) {
        var scope = allowance.scope();
        var approval = Map.of("id", allowance.approvalId(), "scope", Map.of(
            "customerId", scope.customerId(), "deploymentId", scope.deploymentId(),
            "targetProfileId", scope.targetProfileId(), "environment", scope.environment(),
            "credentialIdentity", "sha256:" + allowance.credentialSha256(), "rateCardReference", allowance.pricingReference()),
            "limits", allowance.caps(), "expiresAt", allowance.expiresAt());
        return exchange(baseUrl, PATH + "/initialize", Map.of("approval", approval,
            "runtimeFingerprint", allowance.runtimeAllocationFingerprint()), headers, StoreReceipt.class);
    }

    public StoreReceipt activate(String baseUrl, StoreReceipt receipt, Map<String, String> headers) {
        return exchange(baseUrl, PATH + "/activate", Map.of("approvalId", receipt.approvalId(),
            "storeUuid", receipt.storeUuid(), "runtimeFingerprint", receipt.runtimeFingerprint()), headers, StoreReceipt.class);
    }

    private <T> T exchange(String baseUrl, String path, Object body, Map<String, String> headers, Class<T> type) {
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            URI base = URI.create(baseUrl);
            if (!List.of("https", "http").contains(base.getScheme()) || base.getHost() == null
                || base.getUserInfo() != null || base.getRawQuery() != null || base.getRawFragment() != null
                || headers == null || headers.isEmpty()) throw new IllegalArgumentException();
            String normalized = base.toString();
            while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
            var request = HttpRequest.newBuilder(URI.create(normalized + path)).timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json");
            if (body == null) request.GET();
            else request.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            headers.forEach((name, value) -> {
                if (name == null || name.isBlank() || value == null || value.isBlank()) throw new IllegalArgumentException();
                request.header(name, value);
            });
            pending = client.sendAsync(request.build(), ignored -> new BoundedBody());
            var response = pending.get(10, TimeUnit.SECONDS);
            if (response.statusCode() != 200 || response.body().length == 0) throw new IllegalStateException();
            return mapper.readValue(response.body(), type);
        } catch (InterruptedException interrupted) {
            if (pending != null) pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PROVIDER_USAGE_RUNTIME_INTERRUPTED");
        } catch (Exception unavailable) {
            if (pending != null) pending.cancel(true);
            throw new IllegalStateException("PROVIDER_USAGE_RUNTIME_UNVERIFIED");
        }
    }

    public record StoreReceipt(String state, UUID storeUuid, String approvalId, String runtimeFingerprint) { }
    public record Usage(long inputTokens, long outputTokens, long costNanoUsd) { }
    public record Readback(String state, UUID storeUuid, String approvalId, String runtimeFingerprint,
                           String customerId, String deploymentId, String targetProfileId, String environment,
                           String rateCardReference, Limits limits, Instant expiresAt, Long chatAttempts,
                           Long providerAttempts, Usage accountedUsage, Boolean stopped) {
        StoreReceipt receipt() { return new StoreReceipt(state, storeUuid, approvalId, runtimeFingerprint); }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > 16_384 - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new IllegalStateException("RESPONSE_TOO_LARGE")); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
