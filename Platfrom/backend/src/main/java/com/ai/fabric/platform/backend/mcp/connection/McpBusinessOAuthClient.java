package com.ai.fabric.platform.backend.mcp.connection;

import static org.springframework.http.HttpStatus.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;
import com.nimbusds.oauth2.sdk.auth.*;
import com.nimbusds.oauth2.sdk.client.*;
import com.nimbusds.oauth2.sdk.http.*;
import com.nimbusds.oauth2.sdk.id.*;
import com.nimbusds.oauth2.sdk.pkce.*;
import com.nimbusds.oauth2.sdk.token.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.impl.auth.AuthChallengeParser;
import org.apache.hc.core5.http.message.ParserCursor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Nimbus owns OAuth encoding, PKCE and response parsing; all actual I/O uses bounded pinned HTTPS. */
@Component
public class McpBusinessOAuthClient {
    private final McpSafeHttpTransport http;
    private final McpConnectionProperties properties;
    private final ObjectMapper mapper;
    public McpBusinessOAuthClient(McpSafeHttpTransport http, McpConnectionProperties properties, ObjectMapper mapper) {
        this.http = http; this.properties = properties; this.mapper = mapper;
    }

    public JsonNode discover(String endpoint, McpConnectionContracts.OAuthSettings settings, boolean machine) {
        URI resource = McpSafeHttpTransport.validate(settings.resourceUrl() == null ? endpoint : settings.resourceUrl());
        URI endpointUri = McpSafeHttpTransport.validate(endpoint);
        if (!sameOrigin(resource, endpointUri) || !pathContains(resource.getPath(), endpointUri.getPath()))
            throw invalid("OAuth resource must identify this MCP endpoint or its containing resource path.");
        String selectedIssuer = settings.authorizationServerUrl();
        if (!machine || selectedIssuer == null) {
            var probe = http.request("GET", endpointUri, Map.of(), null, null);
            URI metadataUrl = challengedMetadata(probe.headers().get("www-authenticate"));
            JsonNode protectedMetadata;
            if (metadataUrl != null) protectedMetadata = http.getJson(metadataUrl);
            else {
                List<URI> locations = new ArrayList<>();
                String origin = origin(endpointUri);
                if (!endpointUri.getPath().isBlank() && !"/".equals(endpointUri.getPath()))
                    locations.add(URI.create(origin + "/.well-known/oauth-protected-resource" + endpointUri.getRawPath()));
                locations.add(URI.create(origin + "/.well-known/oauth-protected-resource"));
                protectedMetadata = metadataCandidates(locations);
            }
            URI declared = McpSafeHttpTransport.validate(protectedMetadata.path("resource").asText());
            if (!sameOrigin(declared, endpointUri) || !pathContains(declared.getPath(), endpointUri.getPath())
                || settings.resourceUrl() != null && !declared.equals(resource))
                throw invalid("Protected-resource metadata does not match the configured MCP resource.");
            resource = declared;
            var issuers = protectedMetadata.path("authorization_servers");
            if (!issuers.isArray() || issuers.isEmpty() || issuers.size() > 8) throw invalid("MCP resource did not advertise an authorization server.");
            if (selectedIssuer == null) selectedIssuer = issuers.get(0).asText();
            boolean advertised = false;
            for (JsonNode issuer : issuers) if (selectedIssuer.equals(issuer.asText())) advertised = true;
            if (!advertised) throw invalid("The selected authorization server is not advertised by this MCP resource.");
        }
        URI issuer = McpSafeHttpTransport.validate(selectedIssuer);
        if (issuer.getQuery() != null) throw invalid("OAuth issuer cannot have a query string.");
        String path = issuer.getRawPath();
        if ("/".equals(path)) path = "";
        JsonNode discovered = metadataCandidates(List.of(
            URI.create(origin(issuer) + "/.well-known/oauth-authorization-server" + path),
            URI.create(origin(issuer) + "/.well-known/openid-configuration" + path),
            URI.create(issuer.toString().replaceAll("/+$", "") + "/.well-known/openid-configuration")));
        try {
            var parsed = AuthorizationServerMetadata.parse(discovered.toString());
            if (!issuer.toString().equals(parsed.getIssuer().getValue())) throw invalid("OAuth metadata issuer did not match the selected issuer.");
            McpSafeHttpTransport.validate(parsed.getTokenEndpointURI().toString());
            if (!machine) {
                McpSafeHttpTransport.validate(parsed.getAuthorizationEndpointURI().toString());
                if (parsed.getCodeChallengeMethods() == null || !parsed.getCodeChallengeMethods().contains(CodeChallengeMethod.S256))
                    throw invalid("This authorization server must advertise PKCE S256.");
            }
            if (parsed.getRegistrationEndpointURI() != null) McpSafeHttpTransport.validate(parsed.getRegistrationEndpointURI().toString());
            if (parsed.getRevocationEndpointURI() != null) McpSafeHttpTransport.validate(parsed.getRevocationEndpointURI().toString());
            // Only known endpoint/capability fields are retained, never arbitrary provider token fields.
            var out = mapper.createObjectNode().put("issuer", issuer.toString()).put("resource", resource.toString())
                .put("token_endpoint", parsed.getTokenEndpointURI().toString());
            if (!machine) out.put("authorization_endpoint", parsed.getAuthorizationEndpointURI().toString());
            if (parsed.getRegistrationEndpointURI() != null) out.put("registration_endpoint", parsed.getRegistrationEndpointURI().toString());
            if (parsed.getRevocationEndpointURI() != null) out.put("revocation_endpoint", parsed.getRevocationEndpointURI().toString());
            out.put("client_id_metadata_document_supported", discovered.path("client_id_metadata_document_supported").asBoolean());
            out.set("token_endpoint_auth_methods_supported", discovered.path("token_endpoint_auth_methods_supported"));
            return out;
        } catch (ResponseStatusException failure) { throw failure; }
        catch (Exception malformed) { throw invalid("OAuth authorization-server metadata is incomplete or invalid."); }
    }

    public Registration register(JsonNode metadata, McpConnectionContracts.OAuthSettings settings, URI callback) {
        String mode = settings.registrationMode();
        if (settings.clientId() != null) return new Registration(settings.clientId(), null, settings.clientAuthenticationMethod());
        if ("PREREGISTERED".equals(mode)) throw invalid("Register a client with your provider and enter its client ID.");
        boolean cimd = metadata.path("client_id_metadata_document_supported").asBoolean();
        if ("CIMD".equals(mode) || ("AUTO".equals(mode) && cimd)) {
            if (!cimd || properties.getClientMetadataUrl().isBlank()) throw invalid("Client ID metadata is not configured or supported. Use preregistration instead.");
            URI client = McpSafeHttpTransport.validate(properties.getClientMetadataUrl());
            if (!sameOrigin(client, callback)) throw invalid("Client metadata and OAuth callback must use the same configured LoomAI origin.");
            return new Registration(client.toString(), null, "none");
        }
        if (!metadata.hasNonNull("registration_endpoint")) throw invalid("This server requires a preregistered OAuth client. Enter its client ID and registered callback.");
        try {
            var client = new ClientMetadata(); client.setName("LoomAI business MCP integration");
            client.setRedirectionURI(callback); client.setGrantTypes(Set.of(GrantType.AUTHORIZATION_CODE, GrantType.REFRESH_TOKEN));
            client.setResponseTypes(Set.of(ResponseType.CODE)); client.setTokenEndpointAuthMethod(ClientAuthenticationMethod.NONE);
            var request = new ClientRegistrationRequest(URI.create(metadata.path("registration_endpoint").asText()), client, null);
            var response = ClientRegistrationResponse.parse(send(request.toHTTPRequest()));
            if (!response.indicatesSuccess()) throw invalid("Provider rejected dynamic client registration; use its preregistered client settings.");
            var info = response.toSuccessResponse().getClientInformation();
            if (info.getMetadata().getRedirectionURIs() == null || !info.getMetadata().getRedirectionURIs().equals(Set.of(callback)))
                throw invalid("Provider registration did not bind the exact LoomAI callback.");
            String method = info.getMetadata().getTokenEndpointAuthMethod() == null ? "client_secret_basic"
                : info.getMetadata().getTokenEndpointAuthMethod().getValue();
            if (!Set.of("none", "client_secret_basic", "client_secret_post").contains(method)) throw invalid("Provider selected an unsupported OAuth client authentication method.");
            return new Registration(info.getID().getValue(), info.getSecret() == null ? null : info.getSecret().getValue(), method);
        } catch (ResponseStatusException failure) { throw failure; }
        catch (Exception malformed) { throw invalid("Provider returned an invalid dynamic-registration response."); }
    }

    public String authorizationUrl(JsonNode metadata, List<String> scopes, String clientId, URI callback, State state, CodeVerifier verifier) {
        return new AuthorizationRequest.Builder(ResponseType.CODE, new ClientID(clientId))
            .endpointURI(URI.create(metadata.path("authorization_endpoint").asText())).redirectionURI(callback)
            .scope(new Scope(scopes.toArray(String[]::new))).state(state).codeChallenge(verifier, CodeChallengeMethod.S256)
            .resource(URI.create(metadata.path("resource").asText())).build().toURI().toString();
    }

    public TokenResult token(JsonNode metadata, String clientId, String clientSecret, String method,
        AuthorizationGrant grant, List<String> scopes) {
        try {
            URI endpoint = URI.create(metadata.path("token_endpoint").asText());
            Scope scope = grant instanceof RefreshTokenGrant ? null : new Scope(scopes.toArray(String[]::new));
            var resources = List.of(URI.create(metadata.path("resource").asText()));
            TokenRequest request = "none".equals(method)
                ? new TokenRequest(endpoint, new ClientID(clientId), grant, scope, resources, null, Map.of())
                : new TokenRequest(endpoint, authentication(clientId, clientSecret, method), grant, scope, resources, Map.of());
            var response = TokenResponse.parse(send(request.toHTTPRequest()));
            if (!response.indicatesSuccess()) {
                String code = response.toErrorResponse().getErrorObject().getCode();
                if (Set.of("invalid_grant", "invalid_client", "unauthorized_client", "invalid_scope").contains(code))
                    throw new GrantRejected();
                throw unavailable("OAuth token request was rejected. No other account or provider was tried.");
            }
            var tokens = response.toSuccessResponse().getTokens();
            var access = tokens.getAccessToken();
            if (access == null || !AccessTokenType.BEARER.equals(access.getType()) || access.getValue().isBlank() || access.getValue().length() > 65536)
                throw invalid("Provider returned an unsupported access token.");
            if (access.getScope() != null && !new HashSet<>(scopes).containsAll(access.getScope().toStringList()))
                throw invalid("Provider returned broader scopes than the configured business connection.");
            return new TokenResult(access.getValue(), tokens.getRefreshToken() == null ? null : tokens.getRefreshToken().getValue(),
                access.getLifetime() <= 0 ? null : Instant.now().plusSeconds(access.getLifetime()));
        } catch (ResponseStatusException failure) { throw failure; }
        catch (Exception malformed) { throw unavailable("OAuth token response could not be validated."); }
    }

    public void revoke(JsonNode metadata, String clientId, String clientSecret, String method, String token, boolean refreshToken) {
        if (!metadata.hasNonNull("revocation_endpoint") || token == null) return;
        Token value = refreshToken ? new RefreshToken(token) : new BearerAccessToken(token);
        URI endpoint = URI.create(metadata.path("revocation_endpoint").asText());
        var request = "none".equals(method) ? new TokenRevocationRequest(endpoint, new ClientID(clientId), value)
            : new TokenRevocationRequest(endpoint, authentication(clientId, clientSecret, method), value);
        var response = send(request.toHTTPRequest());
        if (!response.indicatesSuccess()) throw unavailable("Upstream revocation did not complete.");
    }

    private HTTPResponse send(HTTPRequest request) {
        var headers = new LinkedHashMap<String, String>();
        if (request.getAuthorization() != null) headers.put("Authorization", request.getAuthorization());
        String content = request.getEntityContentType() == null ? "application/x-www-form-urlencoded" : request.getEntityContentType().toString();
        var result = http.request(request.getMethod().name(), request.getURI(), headers, request.getBody(), content);
        var response = new HTTPResponse(result.status());
        try { response.setContentType("application/json"); } catch (Exception invalid) { throw new IllegalStateException(invalid); }
        response.setContent(result.body()); return response;
    }

    private JsonNode metadataCandidates(List<URI> candidates) {
        for (URI uri : new LinkedHashSet<>(candidates)) {
            var response = http.request("GET", uri, Map.of(), null, null);
            // Standards-specified well-known discovery only; credential/network failures never select a different identity.
            if (response.status() == 404) continue;
            return http.json(response);
        }
        throw invalid("No standard OAuth metadata was found. Ask the integrator to provide the advertised issuer.");
    }
    private static URI challengedMetadata(String header) {
        if (header == null || header.isBlank()) return null;
        try {
            for (var challenge : AuthChallengeParser.INSTANCE.parse(ChallengeType.TARGET, header, new ParserCursor(0, header.length())))
                if ("Bearer".equalsIgnoreCase(challenge.getSchemeName()))
                    for (var param : challenge.getParams()) if ("resource_metadata".equals(param.getName()))
                        return McpSafeHttpTransport.validate(param.getValue());
            return null;
        } catch (Exception malformed) { throw invalid("MCP authorization challenge is invalid."); }
    }
    private static ClientAuthentication authentication(String clientId, String secret, String method) {
        if (secret == null || secret.isBlank()) throw invalid("An owned OAuth client secret is required for this registration.");
        return switch (method) {
            case "client_secret_basic" -> new ClientSecretBasic(new ClientID(clientId), new Secret(secret));
            case "client_secret_post" -> new ClientSecretPost(new ClientID(clientId), new Secret(secret));
            default -> throw invalid("Unsupported OAuth client authentication method.");
        };
    }
    private static boolean sameOrigin(URI a, URI b) { return origin(a).equals(origin(b)); }
    private static String origin(URI uri) { return "https://" + uri.getHost().toLowerCase(Locale.ROOT) + (uri.getPort() == 443 || uri.getPort() == -1 ? "" : ":" + uri.getPort()); }
    private static boolean pathContains(String parent, String child) { return parent == null || parent.isBlank() || "/".equals(parent)
        || child.equals(parent) || child.startsWith(parent.replaceAll("/+$", "") + "/"); }
    public record Registration(String clientId, String clientSecret, String authenticationMethod) {
        @Override public String toString() { return "McpOAuthRegistration[credentials=redacted]"; }
    }
    public record TokenResult(String accessToken, String refreshToken, Instant expiresAt) {
        @Override public String toString() { return "McpOAuthTokenResult[credentials=redacted]"; }
    }
    public static class GrantRejected extends ResponseStatusException {
        public GrantRejected() { super(CONFLICT, "Business authorization is no longer valid. Reconnect this MCP connection."); }
    }
    private static ResponseStatusException invalid(String reason) { return new ResponseStatusException(BAD_REQUEST, reason); }
    private static ResponseStatusException unavailable(String reason) { return new ResponseStatusException(BAD_GATEWAY, reason); }
}
