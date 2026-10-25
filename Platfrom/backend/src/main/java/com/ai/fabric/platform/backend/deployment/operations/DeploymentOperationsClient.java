package com.ai.fabric.platform.backend.deployment.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

import static com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsContracts.*;

/** This client may fail policy assessment, never deployment authorization or provider execution. */
@Component
public class DeploymentOperationsClient {
    private static final int MAX_RESPONSE_BYTES = 65_536;
    private final DeploymentOperationsProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    public DeploymentOperationsClient(DeploymentOperationsProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public boolean configured() {
        try { endpoint("assess"); return true; }
        catch (RuntimeException exception) { return false; }
    }

    public AssessmentResponse assess(AssessmentRequest request) {
        try { return mapper.readValue(post("assess", request), AssessmentResponse.class); }
        catch (Exception exception) { throw new OperationsUnavailableException(); }
    }

    public void report(Outcome outcome) {
        try { post("outcomes", outcome); }
        catch (Exception exception) { throw new OperationsUnavailableException(); }
    }

    /** Explicit operational commands are not optional-policy bypasses: unavailability is reported to their caller. */
    public com.fasterxml.jackson.databind.JsonNode lifecycle(String route, Object payload, boolean read) {
        if (!route.matches("(?:jobs|jobs/[A-Za-z0-9][A-Za-z0-9._-]{0,127}(?:/(?:advance|cancel|retry|events))?)"))
            throw new IllegalArgumentException("Invalid lifecycle route.");
        try {
            if (!read) return mapper.readTree(post("lifecycle/" + route, payload));
            long millis = timeoutMillis();
            var request = HttpRequest.newBuilder(endpoint("lifecycle/" + route)).timeout(Duration.ofMillis(millis))
                .header("Authorization", "Bearer " + properties.getApiKey()).GET().build();
            var response = http.send(request, ignored -> new BoundedBodySubscriber());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new OperationsUnavailableException();
            return mapper.readTree(response.body());
        } catch (Exception exception) { throw new OperationsUnavailableException(); }
    }

    private byte[] post(String route, Object payload) throws Exception {
        long millis = timeoutMillis();
        var request = HttpRequest.newBuilder(endpoint(route)).timeout(Duration.ofMillis(millis))
            .header("Authorization", "Bearer " + properties.getApiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(payload))).build();
        var future = http.sendAsync(request, response -> new BoundedBodySubscriber());
        HttpResponse<byte[]> response;
        try { response = future.copy().orTimeout(millis, TimeUnit.MILLISECONDS).join(); }
        finally { if (!future.isDone()) future.cancel(true); }
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new OperationsUnavailableException();
        return response.body();
    }

    private URI endpoint(String route) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()
            || properties.getApiKey().contains("\r") || properties.getApiKey().contains("\n"))
            throw new OperationsUnavailableException();
        String base = properties.getServiceUrl();
        if (base == null || base.isBlank()) throw new OperationsUnavailableException();
        URI uri = URI.create(base);
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
            || !("https".equals(uri.getScheme()) || properties.isAllowInsecureHttp() && "http".equals(uri.getScheme())))
            throw new OperationsUnavailableException();
        timeoutMillis();
        return URI.create(base.replaceAll("/+$", "") + "/internal/v1/deployment-operations/" + route);
    }

    private long timeoutMillis() {
        Duration timeout = properties.getTimeout();
        if (timeout == null || timeout.toMillis() < 50 || timeout.toMillis() > 1_000)
            throw new OperationsUnavailableException();
        return timeout.toMillis();
    }

    static final class OperationsUnavailableException extends RuntimeException {
        OperationsUnavailableException() { super("OPERATIONS_UNAVAILABLE"); }
    }

    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
                    subscription.cancel(); body.completeExceptionally(new OperationsUnavailableException()); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { body.completeExceptionally(error); }
        @Override public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
