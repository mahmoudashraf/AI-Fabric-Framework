package com.loomai.operations.lifecycle;

import static com.loomai.operations.lifecycle.LifecycleContracts.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class HttpLifecycleGateway implements LifecycleGateway, AutoCloseable {
    private final LifecycleProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public HttpLifecycleGateway(LifecycleProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        if (properties.httpTimeout().compareTo(Duration.ofMillis(50)) < 0
            || properties.httpTimeout().compareTo(Duration.ofSeconds(10)) > 0
            || properties.workerLease().compareTo(Duration.ofSeconds(5)) < 0
            || properties.workerLease().compareTo(properties.httpTimeout().multipliedBy(2)) < 0
            || properties.workerLease().compareTo(Duration.ofMinutes(5)) > 0
            || properties.noticeLead().isNegative() || properties.noticeLead().compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalStateException("Lifecycle time bounds are invalid.");
        }
        if (!properties.gatewayBaseUrl().isBlank() || !properties.gatewayKey().isBlank()) endpoint("commands");
        if (!properties.webhookUrl().isBlank() || !properties.webhookKey().isBlank()) {
            trustedUri(properties.webhookUrl(), properties.webhookKey());
        }
        if (properties.schedulerEnabled() && !configured()) {
            throw new IllegalStateException("Lifecycle scheduling requires a configured core gateway.");
        }
        http = HttpClient.newBuilder().connectTimeout(properties.httpTimeout())
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public boolean configured() {
        return !properties.gatewayBaseUrl().isBlank() && !properties.gatewayKey().isBlank();
    }

    @Override public Receipt submit(Command command) {
        return receipt(endpoint("commands"), command);
    }

    @Override public Receipt observe(Command command) {
        String id = URLEncoder.encode(command.commandId(), StandardCharsets.UTF_8).replace("+", "%20");
        return receipt(endpoint("commands/" + id), null);
    }

    private Receipt receipt(URI uri, Command command) {
        try {
            return mapper.readValue(exchange(uri, properties.gatewayKey(), command, null), Receipt.class);
        } catch (Exception exception) {
            throw new Unavailable();
        }
    }

    void deliver(Object event, String eventId) {
        exchange(trustedUri(properties.webhookUrl(), properties.webhookKey()), properties.webhookKey(), event, eventId);
    }

    @Override public void close() { http.close(); }

    private URI endpoint(String suffix) {
        URI base = trustedUri(properties.gatewayBaseUrl(), properties.gatewayKey());
        return URI.create(base.toString().replaceAll("/+$", "") + "/internal/v1/deployment-lifecycle/" + suffix);
    }

    private URI trustedUri(String url, String key) {
        try {
            if (key == null || key.length() < 32 || key.length() > 512
                || key.chars().anyMatch(value -> value < 33 || value > 126)) throw new IllegalArgumentException();
            URI uri = URI.create(url);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("https".equals(uri.getScheme()) || properties.allowInsecureHttp() && "http".equals(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (Exception exception) {
            throw new IllegalStateException("Lifecycle trusted endpoint or credential is invalid.");
        }
    }

    private byte[] exchange(URI uri, String key, Object payload, String eventId) {
        try {
            long timeout = properties.httpTimeout().toMillis();
            var builder = HttpRequest.newBuilder(uri).timeout(properties.httpTimeout())
                .header("Authorization", "Bearer " + key).header("Accept", "application/json");
            if (eventId != null) builder.header("Idempotency-Key", eventId);
            if (payload == null) builder.GET();
            else builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(payload)));
            var future = http.sendAsync(builder.build(), ignored -> new LimitedBody());
            HttpResponse<byte[]> response;
            try { response = future.copy().orTimeout(timeout, TimeUnit.MILLISECONDS).join(); }
            finally { if (!future.isDone()) future.cancel(true); }
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new Unavailable();
            return response.body();
        } catch (Exception exception) {
            throw new Unavailable();
        }
    }

    static final class Unavailable extends RuntimeException {
        Unavailable() { super("LIFECYCLE_GATEWAY_UNAVAILABLE"); }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > 65_536 - bytes.size()) {
                    subscription.cancel(); body.completeExceptionally(new Unavailable()); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { body.completeExceptionally(error); }
        @Override public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
