package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Component
public class AuthoringRuntimeClient {
    private final AuthoringProperties properties;
    private final PlatformSecretService secrets;
    private final RuntimePrivateAssertionSigningService signer;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    public AuthoringRuntimeClient(AuthoringProperties properties, PlatformSecretService secrets,
        RuntimePrivateAssertionSigningService signer, ObjectMapper mapper) {
        this.properties = properties; this.secrets = secrets; this.signer = signer; this.mapper = mapper;
    }
    public boolean configured() {
        try { destination(); return present(properties.getApiKeySecretRef()) && present(properties.getSigningKeySecretRef()); }
        catch (RuntimeException failure) { return false; }
    }
    public JsonNode generate(JsonNode input) {
        String subject = "authoring:" + input.path("sessionId").asText() + ":" + input.path("turnId").asText();
        String signingKey = secrets.resolveSecret(properties.getSigningKeySecretRef());
        String key = secrets.resolveSecret(properties.getApiKeySecretRef());
        if (key == null || signingKey == null) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "AUTHORING_RUNTIME_NOT_CONFIGURED");
        var claims = new RuntimePrivateAssertionClaims(subject, "END_USER", "PRIVATE_RUNTIME_BACKEND_MEDIATED", "TRUSTED_BACKEND",
            subject, properties.getDeploymentId(), properties.getCustomerId(), properties.getTenantId(), properties.getIssuer(),
            Instant.now().plusSeconds(130), List.of(properties.getAudience()), List.of("configuration:author"));
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            var request = HttpRequest.newBuilder(destination()).timeout(Duration.ofSeconds(125))
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .header("X-AIFABRIC-RUNTIME-API-KEY", key)
                .header(RuntimePrivateAssertionSigningService.AUTHORIZATION_HEADER,
                    "Bearer " + signer.issueAssertionWithSigningKey(claims, signingKey))
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(input))).build();
            pending = http.sendAsync(request, ignored -> new BoundedBody());
            var response = pending.get(125, TimeUnit.SECONDS);
            JsonNode body = mapper.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(response.body());
            if (response.statusCode() != 200) {
                String code = body.path("error").path("code").asText();
                if (code.matches("(?:AUTHORING|PROVIDER_BUDGET)_[A-Z0-9_]{1,100}"))
                    throw new AuthoringRuntimeFailure(code, body.path("runtime"));
                throw new ResponseStatusException(BAD_GATEWAY, "AUTHORING_RUNTIME_REQUEST_FAILED_" + response.statusCode());
            }
            return body;
        } catch (ResponseStatusException known) { throw known; }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new ResponseStatusException(SERVICE_UNAVAILABLE, "AUTHORING_INTERRUPTED");
        } catch (Exception failure) { throw new ResponseStatusException(BAD_GATEWAY, "AUTHORING_RUNTIME_UNAVAILABLE"); }
        finally { if (pending != null && !pending.isDone()) pending.cancel(true); }
    }
    private boolean present(String ref) { var value = secrets.resolveSecret(ref); return value != null && !value.isBlank(); }
    private URI destination() {
        URI uri = URI.create(properties.getRuntimeUrl());
        if (!List.of("https", "http").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
            || uri.getQuery() != null || uri.getFragment() != null || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())))
            throw new IllegalStateException("AUTHORING_RUNTIME_DESTINATION_INVALID");
        // This is an operator-owned fixed service URL, never an imported/user-selected endpoint.
        return uri.resolve("/api/private/configuration-authoring/generate");
    }
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > 262_144 - bytes.size()) { subscription.cancel(); result.completeExceptionally(new IllegalStateException("AUTHORING_RESPONSE_TOO_LARGE")); return; }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable failure) { result.completeExceptionally(failure); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
    public static final class AuthoringRuntimeFailure extends ResponseStatusException {
        private final JsonNode receipt;
        AuthoringRuntimeFailure(String code, JsonNode receipt) { super(BAD_GATEWAY, code); this.receipt = receipt.deepCopy(); }
        JsonNode receipt() { return receipt; }
    }
}
