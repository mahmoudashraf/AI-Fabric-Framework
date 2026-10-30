package com.ai.fabric.platform.backend.support.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Component
public class SupportRuntimeClient {
    private final HttpClient http;
    private final ObjectMapper mapper;
    @Autowired
    public SupportRuntimeClient(ObjectMapper mapper) {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build(), mapper);
    }
    SupportRuntimeClient(HttpClient http, ObjectMapper mapper) { this.http = http; this.mapper = mapper; }

    public Result exchange(SupportRuntimeAccessService.Access access, String method, String path, JsonNode body, String key) {
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            var request = request(access, path, "application/json").timeout(Duration.ofSeconds(120));
            if (key != null) request.header("Idempotency-Key", messageKey(key));
            if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
            else request.header("Content-Type", "application/json").method(method,
                HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)));
            pending = http.sendAsync(request.build(), ignored -> new BoundedBody());
            var response = pending.get(120, TimeUnit.SECONDS);
            if (response.statusCode() < 200 || response.statusCode() >= 500)
                throw new ResponseStatusException(BAD_GATEWAY, "SUPPORT_RUNTIME_UNAVAILABLE");
            if (response.statusCode() >= 300 && response.statusCode() < 400)
                throw new ResponseStatusException(BAD_GATEWAY, "SUPPORT_RUNTIME_REDIRECT_REFUSED");
            JsonNode result = response.body().length == 0 ? mapper.createObjectNode() : mapper.readTree(response.body());
            return new Result(response.statusCode(), result);
        } catch (ResponseStatusException known) { throw known; }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "SUPPORT_RUNTIME_INTERRUPTED");
        } catch (Exception unavailable) {
            throw new ResponseStatusException(GATEWAY_TIMEOUT, "SUPPORT_RUNTIME_DELIVERY_UNCERTAIN");
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }

    public HttpResponse<java.io.InputStream> events(SupportRuntimeAccessService.Access access, String path, String lastEventId) {
        try {
            var request = request(access, path, "text/event-stream").timeout(Duration.ofSeconds(60));
            if (lastEventId != null) request.header("Last-Event-ID", messageKey(lastEventId));
            var response = http.send(request.GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200 || !response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream")) {
                response.body().close();
                throw new ResponseStatusException(BAD_GATEWAY, "SUPPORT_EVENT_STREAM_UNAVAILABLE");
            }
            return response;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "SUPPORT_EVENT_STREAM_INTERRUPTED");
        } catch (java.io.IOException failure) {
            throw new ResponseStatusException(BAD_GATEWAY, "SUPPORT_EVENT_STREAM_UNAVAILABLE");
        }
    }

    private HttpRequest.Builder request(SupportRuntimeAccessService.Access access, String path, String accept) {
        URI base = URI.create(access.assignment().runtimeBaseUrl());
        if (!List.of("https", "http").contains(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null
            || base.getQuery() != null || base.getFragment() != null || !path.startsWith("/api/chat/"))
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "SUPPORT_RUNTIME_DESTINATION_INVALID");
        String root = base.toString().replaceAll("/+$", "");
        var request = HttpRequest.newBuilder(URI.create(root + path)).header("Accept", accept);
        access.headers().forEach(request::header);
        return request;
    }

    static String messageKey(String value) {
        if (value == null || value.isBlank() || value.length() > 128 || value.chars().anyMatch(c -> c < 33 || c > 126))
            throw new ResponseStatusException(BAD_REQUEST, "SUPPORT_MESSAGE_KEY_REQUIRED");
        return value;
    }

    public record Result(int status, JsonNode body) { }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > 1_048_576 - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new IllegalStateException("SUPPORT_RESPONSE_TOO_LARGE")); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
