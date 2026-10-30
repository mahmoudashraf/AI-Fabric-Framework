package com.ai.fabric.runtime.auth;

import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Supabase's provider-owned user record, never editable user_metadata, proves email confirmation. */
final class RuntimeSupabaseUserVerifier {
    private static final int MAX_BYTES = 65_536;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    RuntimeSupabaseUserVerifier() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build());
    }

    RuntimeSupabaseUserVerifier(HttpClient client) { this.client = client; }

    boolean confirmed(RuntimeAuthProperties.ExternalIdentityBroker config, String token, String subject) {
        String key = config.getSupabasePublicKey();
        if (!publicKey(key)) throw unavailable();
        URI issuer;
        try { issuer = URI.create(config.getIssuer()); }
        catch (RuntimeException invalid) { throw unavailable(); }
        if (!"https".equals(issuer.getScheme()) || issuer.getHost() == null || issuer.getUserInfo() != null
            || issuer.getQuery() != null || issuer.getFragment() != null || !"/auth/v1".equals(issuer.getPath())) throw unavailable();
        try {
            var request = HttpRequest.newBuilder(URI.create(issuer + "/user")).timeout(Duration.ofSeconds(5))
                .header("apikey", key).header("Authorization", "Bearer " + token).GET().build();
            var response = client.send(request, ignored -> new LimitedBody());
            if (response.statusCode() == 401 || response.statusCode() == 403) return false;
            if (response.statusCode() != 200) throw unavailable();
            var user = mapper.readTree(response.body());
            return subject != null && subject.equals(user.path("id").asText())
                && !user.path("is_anonymous").asBoolean(false) && !user.path("email").asText("").isBlank()
                && !user.path("email_confirmed_at").asText("").isBlank();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException invalid) { throw unavailable(); }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Supabase email verification is unavailable.");
    }

    private static boolean publicKey(String value) {
        if (value == null || value.length() > 2048 || value.chars().anyMatch(Character::isWhitespace)) return false;
        if (value.startsWith("sb_publishable_") && value.length() > "sb_publishable_".length()) return true;
        try { return "anon".equals(SignedJWT.parse(value).getJWTClaimsSet().getStringClaim("role")); }
        catch (java.text.ParseException invalid) { return false; }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long received;
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) received += buffer.remaining();
            if (received > MAX_BYTES) {
                subscription.cancel();
                delegate.onError(new IOException("Identity verification response exceeds its bound."));
            } else delegate.onNext(buffers);
        }
        @Override public void onError(Throwable error) { delegate.onError(error); }
        @Override public void onComplete() { delegate.onComplete(); }
    }
}
