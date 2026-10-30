package com.ai.fabric.runtime.auth;

import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RuntimeSupabaseUserVerifierTest {
    @Test
    void acceptsOnlyTheProviderRecordForTheVerifiedJwtSubject() throws Exception {
        var client = client(200, "{\"id\":\"user-1\",\"email\":\"fixture@example.invalid\",\"email_confirmed_at\":\"2026-10-30T12:00:00Z\",\"is_anonymous\":false}");
        assertThat(new RuntimeSupabaseUserVerifier(client).confirmed(config(), "user-token", "user-1")).isTrue();
        assertThat(new RuntimeSupabaseUserVerifier(client).confirmed(config(), "user-token", "other-user")).isFalse();
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(2)).send(request.capture(), any());
        assertThat(request.getValue().uri().toString()).isEqualTo("https://fixture.supabase.co/auth/v1/user");
        assertThat(request.getValue().headers().firstValue("apikey")).contains("sb_publishable_fixture");
        assertThat(request.getValue().headers().firstValue("Authorization")).contains("Bearer user-token");
    }

    @Test
    void editableMetadataAndAnonymousUsersCannotProveConfirmation() throws Exception {
        var metadata = client(200, "{\"id\":\"user-1\",\"email\":\"fixture@example.invalid\",\"user_metadata\":{\"email_verified\":true}}");
        assertThat(new RuntimeSupabaseUserVerifier(metadata).confirmed(config(), "token", "user-1")).isFalse();
        var anonymous = client(200, "{\"id\":\"user-1\",\"email\":\"fixture@example.invalid\",\"email_confirmed_at\":\"2026-10-30T12:00:00Z\",\"is_anonymous\":true}");
        assertThat(new RuntimeSupabaseUserVerifier(anonymous).confirmed(config(), "token", "user-1")).isFalse();
    }

    @Test
    void treatsRevocationAsDeniedAndOutagesOrRedirectsAsUnavailable() throws Exception {
        assertThat(new RuntimeSupabaseUserVerifier(client(401, "{}")).confirmed(config(), "token", "user-1")).isFalse();
        for (int status : List.of(302, 500)) {
            var verifier = new RuntimeSupabaseUserVerifier(client(status, "{}"));
            assertThatThrownBy(() -> verifier.confirmed(config(), "token", "user-1"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                    error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        }
    }

    @Test
    void refusesMissingPrivilegedKeysOrForeignEndpointShapesBeforeNetwork() {
        var client = mock(HttpClient.class);
        var verifier = new RuntimeSupabaseUserVerifier(client);
        for (String key : List.of("", "sb_secret_not-for-runtime-user-lookup", "sb_publishable_bad\nheader")) {
            var config = config(); config.setSupabasePublicKey(key);
            assertThatThrownBy(() -> verifier.confirmed(config, "token", "user-1")).isInstanceOf(ResponseStatusException.class);
        }
        var config = config(); config.setIssuer("https://fixture.supabase.co/auth/v1?elsewhere=true");
        assertThatThrownBy(() -> verifier.confirmed(config, "token", "user-1")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(client);
    }

    @Test
    void cancelsAnOversizedProviderResponse() throws Exception {
        var client = mock(HttpClient.class);
        var subscription = mock(Flow.Subscription.class);
        doAnswer(invocation -> {
            HttpResponse.BodyHandler<byte[]> handler = invocation.getArgument(1);
            var subscriber = handler.apply(mock(HttpResponse.ResponseInfo.class));
            subscriber.onSubscribe(subscription);
            subscriber.onNext(List.of(ByteBuffer.wrap(new byte[65_537])));
            assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().join()).hasCauseInstanceOf(IOException.class);
            throw new IOException("bounded response");
        }).when(client).send(any(), any());
        assertThatThrownBy(() -> new RuntimeSupabaseUserVerifier(client).confirmed(config(), "token", "user-1"))
            .isInstanceOf(ResponseStatusException.class);
        verify(subscription).cancel();
    }

    private RuntimeAuthProperties.ExternalIdentityBroker config() {
        var config = new RuntimeAuthProperties.ExternalIdentityBroker();
        config.setIssuer("https://fixture.supabase.co/auth/v1");
        config.setSupabasePublicKey("sb_publishable_fixture");
        return config;
    }

    @SuppressWarnings("unchecked")
    private HttpClient client(int status, String body) throws Exception {
        var client = mock(HttpClient.class);
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        doReturn(response).when(client).send(any(), any());
        return client;
    }
}
