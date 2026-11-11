package com.ai.fabric.platform.backend.mcp.connection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.id.*;
import com.nimbusds.oauth2.sdk.pkce.*;
import java.net.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class McpBusinessOAuthClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final McpSafeHttpTransport http = mock(McpSafeHttpTransport.class);
    private final McpConnectionProperties properties = new McpConnectionProperties();
    private final McpBusinessOAuthClient client = new McpBusinessOAuthClient(http, properties, mapper);
    private static final URI TOKEN = URI.create("https://auth.example/token");

    @Test void standardChallengeResourceAndIssuerDiscoveryKeepExactAudience() throws Exception {
        when(http.request(eq("GET"), eq(URI.create("https://tools.example/mcp")), anyMap(), isNull(), isNull()))
            .thenReturn(new McpSafeHttpTransport.Response(401, Map.of("www-authenticate", "Bearer resource_metadata=\"https://tools.example/resource-metadata\""), ""));
        when(http.getJson(URI.create("https://tools.example/resource-metadata"))).thenReturn(mapper.readTree(
            "{\"resource\":\"https://tools.example/mcp\",\"authorization_servers\":[\"https://auth.example\"]}"));
        when(http.request(eq("GET"), eq(URI.create("https://auth.example/.well-known/oauth-authorization-server")), anyMap(), isNull(), isNull()))
            .thenReturn(json(200, metadata().toString()));
        // Parse the fixture response without performing any network.
        when(http.json(any())).thenAnswer(a -> mapper.readTree(((McpSafeHttpTransport.Response) a.getArgument(0)).body()));
        var result = client.discover("https://tools.example/mcp", settings(null, "AUTO"), false);
        assertThat(result.path("resource").asText()).isEqualTo("https://tools.example/mcp");
        assertThat(result.path("issuer").asText()).isEqualTo("https://auth.example");
        assertThat(result.path("token_endpoint").asText()).isEqualTo(TOKEN.toString());
    }

    @Test void resourceMismatchAndUnadvertisedIssuerNeverRequestTokens() throws Exception {
        when(http.request(eq("GET"), eq(URI.create("https://tools.example/mcp")), anyMap(), isNull(), isNull()))
            .thenReturn(new McpSafeHttpTransport.Response(401, Map.of("www-authenticate", "Bearer resource_metadata=\"https://tools.example/resource-metadata\""), ""));
        when(http.getJson(any())).thenReturn(mapper.readTree("{\"resource\":\"https://other.example/mcp\",\"authorization_servers\":[\"https://auth.example\"]}"));
        assertThatThrownBy(() -> client.discover("https://tools.example/mcp", settings(null, "AUTO"), false)).isInstanceOf(ResponseStatusException.class);
        verify(http, never()).request(eq("POST"), any(), anyMap(), anyString(), anyString());
    }

    @Test void pkceAuthorizationUrlContainsOnlyConfiguredResourceScopeAndCallback() throws Exception {
        CodeVerifier verifier = new CodeVerifier(); State state = new State();
        String url = client.authorizationUrl(metadata(), List.of("subscriptions:read"), "registered-client",
            URI.create("https://api.loomai.example/api/mcp/oauth/callback"), state, verifier);
        var parsed = AuthorizationRequest.parse(URI.create(url));
        assertThat(parsed.getCodeChallenge()).isEqualTo(CodeChallenge.compute(CodeChallengeMethod.S256, verifier));
        assertThat(parsed.getState()).isEqualTo(state);
        assertThat(parsed.getResources()).containsExactly(URI.create("https://tools.example/mcp"));
        assertThat(parsed.getScope().toStringList()).containsExactly("subscriptions:read");
        assertThat(url).doesNotContain(verifier.getValue(), "client_secret");
        verifyNoInteractions(http);
    }

    @Test void preregistrationAndAdvertisedCimdAvoidDynamicRegistration() throws Exception {
        var registered = client.register(metadata(), settings("registered-client", "PREREGISTERED"), URI.create("https://api.loomai.example/api/mcp/oauth/callback"));
        assertThat(registered.clientId()).isEqualTo("registered-client");
        properties.setClientMetadataUrl("https://api.loomai.example/api/mcp/oauth/client-metadata");
        var doc = metadata(); ((com.fasterxml.jackson.databind.node.ObjectNode) doc).put("client_id_metadata_document_supported", true);
        var cimd = client.register(doc, settings(null, "CIMD"), URI.create("https://api.loomai.example/api/mcp/oauth/callback"));
        assertThat(cimd.clientId()).isEqualTo(properties.getClientMetadataUrl()); assertThat(cimd.authenticationMethod()).isEqualTo("none");
        verifyNoInteractions(http);
    }

    @Test void tokenRequestUsesNimbusEncodingPkceAndResourceAndReturnsNoRawSecretsInToString() throws Exception {
        when(http.request(eq("POST"), eq(TOKEN), anyMap(), anyString(), anyString()))
            .thenReturn(json(200, "{\"access_token\":\"private-access\",\"token_type\":\"Bearer\",\"refresh_token\":\"private-refresh\",\"expires_in\":60,\"scope\":\"subscriptions:read\"}"));
        var verifier = new CodeVerifier();
        var result = client.token(metadata(), "registered-client", "private-client-secret", "client_secret_basic",
            new AuthorizationCodeGrant(new AuthorizationCode("private-code"), URI.create("https://api.loomai.example/api/mcp/oauth/callback"), verifier), List.of("subscriptions:read"));
        assertThat(result.accessToken()).isEqualTo("private-access"); assertThat(result.refreshToken()).isEqualTo("private-refresh");
        assertThat(result.expiresAt()).isAfter(java.time.Instant.now());
        assertThat(result.toString()).doesNotContain("private-access", "private-refresh");
        var body = ArgumentCaptor.forClass(String.class);
        verify(http).request(eq("POST"), eq(TOKEN), argThat(h -> h.get("Authorization").startsWith("Basic ")), body.capture(), anyString());
        assertThat(body.getValue()).contains("code_verifier=", "resource=https%3A%2F%2Ftools.example%2Fmcp", "code=private-code");
    }

    @Test void invalidGrantIsVisibleAndDoesNotSelectAnotherCredentialOrRetry() throws Exception {
        when(http.request(eq("POST"), eq(TOKEN), anyMap(), anyString(), anyString())).thenReturn(json(400, "{\"error\":\"invalid_grant\"}"));
        assertThatThrownBy(() -> client.token(metadata(), "registered-client", null, "none",
            new RefreshTokenGrant(new com.nimbusds.oauth2.sdk.token.RefreshToken("fixture-refresh")), List.of("read"))).isInstanceOf(McpBusinessOAuthClient.GrantRejected.class);
        verify(http, times(1)).request(eq("POST"), eq(TOKEN), anyMap(), anyString(), anyString());
    }

    @Test void broaderScopeTokenIsRejectedWithoutLeakingProviderBody() throws Exception {
        when(http.request(eq("POST"), eq(TOKEN), anyMap(), anyString(), anyString()))
            .thenReturn(json(200, "{\"access_token\":\"private-access\",\"token_type\":\"Bearer\",\"scope\":\"read admin\"}"));
        assertThatThrownBy(() -> client.token(metadata(), "registered-client", null, "none", new RefreshTokenGrant(new com.nimbusds.oauth2.sdk.token.RefreshToken("fixture-refresh")), List.of("read")))
            .isInstanceOf(ResponseStatusException.class).hasMessageNotContaining("private-access");
    }

    private static McpSafeHttpTransport.Response json(int status, String body) { return new McpSafeHttpTransport.Response(status, Map.of("content-type", "application/json"), body); }
    private JsonNode metadata() throws Exception { return mapper.readTree("""
        {"issuer":"https://auth.example","resource":"https://tools.example/mcp","authorization_endpoint":"https://auth.example/authorize",
         "token_endpoint":"https://auth.example/token","response_types_supported":["code"],"code_challenge_methods_supported":["S256"],
         "grant_types_supported":["authorization_code","refresh_token","client_credentials"],"revocation_endpoint":"https://auth.example/revoke"}
        """); }
    private static McpConnectionContracts.OAuthSettings settings(String clientId, String mode) {
        return new McpConnectionContracts.OAuthSettings(null, null, mode, clientId, null, "none", List.of("read"));
    }
}
