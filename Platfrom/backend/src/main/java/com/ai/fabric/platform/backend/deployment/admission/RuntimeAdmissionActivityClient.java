package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;

@Component
public class RuntimeAdmissionActivityClient {
    private final HttpClient client;
    private final ObjectMapper mapper;

    public RuntimeAdmissionActivityClient(ObjectMapper mapper) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
        this.mapper = mapper;
    }

    public ActivityReadback read(String baseUrl, ReleaseBinding binding, Map<String, String> headers) {
        java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            URI base = URI.create(baseUrl);
            if (!java.util.Set.of("https", "http").contains(base.getScheme()) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || headers == null || headers.isEmpty()) throw new IllegalArgumentException();
            String normalized = base.toString().replaceAll("/+$", "");
            var request = HttpRequest.newBuilder(URI.create(normalized + "/api/private/runtime-admission-activity/readback"))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                    "deploymentId", binding.deploymentId(), "targetProfileId", binding.targetProfileId(), "releaseId", binding.releaseId()))));
            headers.forEach(request::header);
            pending = client.sendAsync(request.build(), ignored -> new BoundedBody());
            var response = pending.get(5, java.util.concurrent.TimeUnit.SECONDS);
            byte[] payload = response.body();
            if (response.statusCode() != 200 || payload.length == 0) {
                throw new IllegalStateException("RUNTIME_ACTIVITY_READBACK_REJECTED");
            }
            return mapper.readValue(payload, ActivityReadback.class);
        } catch (InterruptedException error) {
            if (pending != null) pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RUNTIME_ACTIVITY_READBACK_INTERRUPTED", error);
        } catch (java.util.concurrent.TimeoutException error) {
            if (pending != null) pending.cancel(true);
            throw new IllegalStateException("RUNTIME_ACTIVITY_READBACK_TIMEOUT", error);
        } catch (Exception error) {
            throw new IllegalStateException("RUNTIME_ACTIVITY_READBACK_UNAVAILABLE", error);
        }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final java.util.concurrent.CompletableFuture<byte[]> result = new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription subscription;

        public java.util.concurrent.CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(java.util.concurrent.Flow.Subscription value) {
            subscription = value; value.request(1);
        }
        public void onNext(java.util.List<java.nio.ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > 8_192 - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IllegalStateException("RUNTIME_ACTIVITY_RESPONSE_TOO_LARGE"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
