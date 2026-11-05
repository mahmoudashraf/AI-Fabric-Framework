package com.ai.fabric.platform.backend.infrastructure.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

@Component
public class InfrastructureMonitoringClient {
    private final InfrastructureMonitoringProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    public InfrastructureMonitoringClient(InfrastructureMonitoringProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper.copy().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public JsonNode read(InfrastructureMonitoringProperties.Source source, String route) {
        if (!route.matches("servers/[A-Za-z0-9][A-Za-z0-9._-]{0,127}(?:/history\\?range=(?:1h|24h|7d)(?:&resourceId=[a-f0-9]{12,64})?)?")) {
            throw new IllegalArgumentException("Invalid monitoring read route.");
        }
        var request = HttpRequest.newBuilder(URI.create(source.url().replaceAll("/+$", "") + "/internal/v1/telemetry/" + route))
            .timeout(properties.getTimeout()).header("Authorization", "Bearer " + source.readKey())
            .header("Accept", "application/json").GET().build();
        var pending = http.sendAsync(request, ignored -> new LimitedBody());
        try {
            var response = pending.copy().orTimeout(properties.getTimeout().toMillis(), TimeUnit.MILLISECONDS).join();
            if (response.statusCode() != 200) throw new IllegalStateException();
            var body = mapper.readTree(response.body());
            if (body == null || !body.isObject() || !source.id().equals(body.path("serverId").asText())) {
                throw new IllegalStateException();
            }
            return body;
        } catch (Exception exception) {
            throw new MonitoringUnavailableException("MONITORING_SOURCE_UNAVAILABLE");
        } finally {
            if (!pending.isDone()) pending.cancel(true);
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private static final int MAX_BYTES = 524288;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > MAX_BYTES - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new IllegalStateException("MONITORING_RESPONSE_TOO_LARGE")); return;
                }
                var part = new byte[buffer.remaining()]; buffer.get(part); bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable failure) { result.completeExceptionally(failure); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
