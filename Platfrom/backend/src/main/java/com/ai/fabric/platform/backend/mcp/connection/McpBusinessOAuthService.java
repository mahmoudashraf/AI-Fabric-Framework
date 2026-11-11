package com.ai.fabric.platform.backend.mcp.connection;

import static com.ai.fabric.platform.backend.mcp.connection.McpConnectionContracts.*;
import static org.springframework.http.HttpStatus.*;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** One business-owned grant per connection; no app-visitor account or upstream user-grant store. */
@Service
public class McpBusinessOAuthService {
    private final McpConnectionRepository connections;
    private final McpOAuthAttemptRepository attempts;
    private final DeploymentRepository deployments;
    private final DeploymentAccessService access;
    private final PlatformUserRepository users;
    private final McpOwnedSecrets secrets;
    private final McpBusinessOAuthClient client;
    private final McpCredentialCrypto crypto;
    private final McpConnectionProperties properties;
    private final ObjectMapper mapper;
    private final PlatformAuditService audit;

    public McpBusinessOAuthService(McpConnectionRepository connections, McpOAuthAttemptRepository attempts,
        DeploymentRepository deployments, DeploymentAccessService access, PlatformUserRepository users, McpOwnedSecrets secrets,
        McpBusinessOAuthClient client, McpCredentialCrypto crypto, McpConnectionProperties properties, ObjectMapper mapper, PlatformAuditService audit) {
        this.connections = connections; this.attempts = attempts; this.deployments = deployments; this.access = access;
        this.users = users; this.secrets = secrets; this.client = client; this.crypto = crypto; this.properties = properties;
        this.mapper = mapper; this.audit = audit;
    }

    public OAuthSettings validateSettings(OAuthSettings settings, boolean machine) {
        if (settings == null) throw invalid("Provide the business OAuth settings.");
        if (settings.authorizationServerUrl() != null) McpSafeHttpTransport.validate(settings.authorizationServerUrl());
        if (settings.resourceUrl() != null) McpSafeHttpTransport.validate(settings.resourceUrl());
        String registration = settings.registrationMode() == null ? "AUTO" : settings.registrationMode();
        if (!Set.of("AUTO", "PREREGISTERED", "CIMD", "DCR").contains(registration)) throw invalid("Choose a supported OAuth registration method.");
        String method = settings.clientAuthenticationMethod() == null ? (settings.clientSecretRef() == null ? "none" : "client_secret_basic")
            : settings.clientAuthenticationMethod();
        if (!Set.of("none", "client_secret_basic", "client_secret_post").contains(method)) throw invalid("Choose a supported OAuth client authentication method.");
        if (settings.clientId() != null && (settings.clientId().isBlank() || settings.clientId().length() > 2048)) throw invalid("Provide a valid OAuth client ID.");
        if (("PREREGISTERED".equals(registration) || machine) && settings.clientId() == null) throw invalid("A registered OAuth client ID is required.");
        if (machine && (settings.authorizationServerUrl() == null || settings.clientSecretRef() == null || "none".equals(method)))
            throw invalid("Client credentials require an explicit issuer, registered client ID and owned client secret.");
        List<String> scopes = settings.scopes() == null ? List.of() : settings.scopes();
        if (scopes.size() > 32 || new HashSet<>(scopes).size() != scopes.size()) throw invalid("Select at most 32 distinct OAuth scopes.");
        for (String scope : scopes) if (scope == null || scope.isBlank() || scope.length() > 256
            || scope.chars().anyMatch(c -> c < 0x21 || c > 0x7e || c == '"' || c == '\\')) throw invalid("OAuth scopes must use the provider's exact scope tokens.");
        return new OAuthSettings(settings.authorizationServerUrl(), settings.resourceUrl(), registration,
            settings.clientId(), settings.clientSecretRef(), method, List.copyOf(scopes));
    }

    @Transactional
    public AuthorizationStart authorize(String deploymentId, String connectionId, Authorize request, String browserBinding) {
        requireEnabled(); requireEditor(deploymentId);
        var c = ownedLocked(deploymentId, connectionId);
        McpConnectionService.requireRevision(c, request == null ? null : request.expectedRevision());
        AuthSettings auth = auth(c);
        if (!"BUSINESS".equals(c.getConnectionModel()) || !"OAUTH_AUTHORIZATION_CODE".equals(auth.mode()))
            throw invalid("Browser authorization is only available for a business OAuth connection.");
        if (browserBinding == null || browserBinding.length() < 20) throw invalid("A secure authorization browser binding is required.");
        requireCrypto(); URI callback = callback();
        var user = PlatformUserRepository.findForPrincipal(users, PlatformSecurityContext.currentPrincipal())
            .filter(u -> "ACTIVE".equals(u.getStatus())).orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "Use an active Console account to authorize this connection."));
        String returnPath = returnPath(deploymentId, request.returnPath());
        OAuthSettings settings = auth.oauth();
        JsonNode metadata = client.discover(c.getEndpointUrl(), settings, false);
        var registration = client.register(metadata, settings, callback);
        var registered = (ObjectNode) metadata.deepCopy();
        registered.put("client_id", registration.clientId()).put("client_authentication_method", registration.authenticationMethod());
        State state = new State(); CodeVerifier verifier = new CodeVerifier();
        var material = mapper.createObjectNode().put("verifier", verifier.getValue()); material.set("metadata", registered);
        if (registration.clientSecret() != null) material.put("registrationSecret", registration.clientSecret());
        String hash = McpToolCatalogue.hash(state.getValue());
        var attempt = new McpOAuthAttemptEntity(); attempt.setStateHash(hash); attempt.setConnectionId(connectionId);
        attempt.setConnectionRevision(c.getRevision()); attempt.setActorId(user.getId()); attempt.setActorRole(user.getRole());
        attempt.setBrowserBindingHash(McpToolCatalogue.hash(browserBinding)); attempt.setReturnPath(returnPath);
        attempt.setEncryptedVerifier(crypto.encrypt("attempt:" + hash, material.toString())); attempt.setExpiresAt(Instant.now().plusSeconds(600));
        attempts.clearExpired(Instant.now()); attempts.save(attempt);
        audit.record("MCP_BUSINESS_AUTHORIZATION_STARTED", "DEPLOYMENT", deploymentId, Map.of("connectionId", connectionId, "scopes", settings.scopes()));
        return new AuthorizationStart(client.authorizationUrl(registered, settings.scopes(), registration.clientId(), callback, state, verifier), attempt.getExpiresAt(), connectionId);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public CallbackResult callback(String state, String code, String error, String issuer, String browserBinding) {
        requireEnabled();
        if (state == null || state.isBlank() || state.length() > 512 || browserBinding == null || browserBinding.length() > 512)
            throw invalid("This authorization callback is missing its original browser binding.");
        var attempt = attempts.findLocked(McpToolCatalogue.hash(state)).orElseThrow(() -> invalid("Unknown or expired MCP authorization attempt."));
        if (attempt.getConsumedAt() != null || attempt.getEncryptedVerifier() == null || !attempt.getExpiresAt().isAfter(Instant.now())
            || !constantEquals(attempt.getBrowserBindingHash(), McpToolCatalogue.hash(browserBinding))) throw invalid("This authorization attempt is expired, already used or belongs to another browser.");
        String sealed = attempt.getEncryptedVerifier(); attempt.setConsumedAt(Instant.now()); attempt.setEncryptedVerifier(null); attempts.save(attempt);
        var c = connections.findLocked(attempt.getConnectionId()).orElseThrow(() -> invalid("The MCP connection is no longer available."));
        var original = SecurityContextHolder.getContext().getAuthentication();
        try {
            var user = users.findById(attempt.getActorId()).filter(u -> "ACTIVE".equals(u.getStatus()) && attempt.getActorRole().equals(u.getRole()))
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "The authorizing Console account is no longer active."));
            var existing = PlatformSecurityContext.currentPrincipal();
            if (existing != null && !PlatformUserRepository.findForPrincipal(users, existing).map(u -> u.getId().equals(user.getId())).orElse(false))
                throw new ResponseStatusException(FORBIDDEN, "The callback belongs to a different Console account.");
            PlatformRole role = PlatformRole.valueOf(user.getRole());
            var principal = new PlatformPrincipal(user.getEmail(), role, user.getDisplayName(), "MCP_OAUTH_CALLBACK", user.getId());
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
            requireEditor(c.getDeploymentId()); McpConnectionService.requireRevision(c, attempt.getConnectionRevision());
            if (!sameCustomer(c)) throw new ResponseStatusException(FORBIDDEN, "Connection ownership changed during authorization.");
            JsonNode material = read(crypto.decrypt("attempt:" + attempt.getStateHash(), sealed));
            JsonNode metadata = material.path("metadata");
            if (issuer != null && !metadata.path("issuer").asText().equals(issuer)) throw invalid("The authorization-response issuer did not match this attempt.");
            if (error != null && !error.isBlank()) return new CallbackResult(attempt.getReturnPath(), "cancelled");
            if (code == null || code.isBlank() || code.length() > 8192) throw invalid("The provider did not return a usable authorization code.");
            AuthSettings auth = auth(c);
            if (!"BUSINESS".equals(c.getConnectionModel()) || !"OAUTH_AUTHORIZATION_CODE".equals(auth.mode())) throw invalid("This connection is no longer a business OAuth integration.");
            String secret = clientSecret(c, auth.oauth(), material.path("registrationSecret").asText(null));
            var result = client.token(metadata, metadata.path("client_id").asText(), secret, metadata.path("client_authentication_method").asText(),
                new AuthorizationCodeGrant(new AuthorizationCode(code), callback(), new CodeVerifier(material.path("verifier").asText())), auth.oauth().scopes());
            var tokens = tokenEnvelope(result, null);
            if (material.hasNonNull("registrationSecret")) tokens.set("registrationSecret", material.get("registrationSecret"));
            c.setOauthMetadataJson(metadata.toString()); saveTokens(c, result, tokens); c.setStatus("ACTIVE");
            audit.record("MCP_BUSINESS_AUTHORIZATION_CONNECTED", "DEPLOYMENT", c.getDeploymentId(), Map.of("connectionId", c.getId()));
            return new CallbackResult(attempt.getReturnPath(), "connected");
        } catch (RuntimeException failure) {
            audit.record("MCP_BUSINESS_AUTHORIZATION_FAILED", "DEPLOYMENT", c.getDeploymentId(), Map.of("connectionId", c.getId()));
            return new CallbackResult(attempt.getReturnPath(), "failed");
        } finally { SecurityContextHolder.getContext().setAuthentication(original); }
    }

    /** Caller holds the connection row lock. Token rotation does not change configuration revision. */
    public String accessToken(McpConnectionEntity c) {
        requireCrypto(); AuthSettings auth = auth(c);
        ObjectNode tokens = c.getEncryptedTokens() == null ? mapper.createObjectNode() : (ObjectNode) read(crypto.decrypt("connection:" + c.getId(), c.getEncryptedTokens()));
        if (tokens.hasNonNull("accessToken") && (c.getTokenExpiresAt() == null || c.getTokenExpiresAt().isAfter(Instant.now().plusSeconds(10)))) return tokens.path("accessToken").asText();
        boolean machine = "OAUTH2_CLIENT_CREDENTIALS".equals(auth.mode());
        if (!machine && !tokens.hasNonNull("refreshToken")) {
            c.setAuthStatus("RECONNECT_REQUIRED"); connections.save(c); throw new McpBusinessOAuthClient.GrantRejected();
        }
        JsonNode metadata = c.getOauthMetadataJson() == null ? client.discover(c.getEndpointUrl(), auth.oauth(), machine) : read(c.getOauthMetadataJson());
        if (machine && !metadata.hasNonNull("client_id")) {
            ((ObjectNode) metadata).put("client_id", auth.oauth().clientId()).put("client_authentication_method", auth.oauth().clientAuthenticationMethod());
        }
        try {
            String secret = clientSecret(c, auth.oauth(), tokens.path("registrationSecret").asText(null));
            var grant = machine ? new ClientCredentialsGrant() : new RefreshTokenGrant(new RefreshToken(tokens.path("refreshToken").asText()));
            var result = client.token(metadata, metadata.path("client_id").asText(), secret, metadata.path("client_authentication_method").asText(), grant, auth.oauth().scopes());
            c.setOauthMetadataJson(metadata.toString()); saveTokens(c, result, tokenEnvelope(result, tokens));
            return result.accessToken();
        } catch (McpBusinessOAuthClient.GrantRejected rejected) {
            tokens.remove(List.of("accessToken", "refreshToken")); c.setEncryptedTokens(crypto.encrypt("connection:" + c.getId(), tokens.toString()));
            c.setTokenExpiresAt(null); c.setAuthStatus("RECONNECT_REQUIRED"); connections.save(c); throw rejected;
        }
    }

    public void disconnect(McpConnectionEntity c) {
        String sealed = c.getEncryptedTokens(); c.setEncryptedTokens(null); c.setTokenExpiresAt(null); c.setAuthStatus("DISCONNECTED");
        // Local invalidation is unconditional, even when provider revocation is unavailable or fails.
        if (sealed == null || c.getOauthMetadataJson() == null) return;
        try {
            JsonNode tokens = read(crypto.decrypt("connection:" + c.getId(), sealed)); JsonNode metadata = read(c.getOauthMetadataJson());
            boolean refresh = tokens.hasNonNull("refreshToken"); String value = tokens.path(refresh ? "refreshToken" : "accessToken").asText(null);
            client.revoke(metadata, metadata.path("client_id").asText(), clientSecret(c, auth(c).oauth(), tokens.path("registrationSecret").asText(null)),
                metadata.path("client_authentication_method").asText(), value, refresh);
        } catch (RuntimeException failed) { c.setAuthStatus("DISCONNECTED_UPSTREAM_REVOCATION_FAILED"); }
    }

    private ObjectNode tokenEnvelope(McpBusinessOAuthClient.TokenResult result, ObjectNode previous) {
        ObjectNode out = previous == null ? mapper.createObjectNode() : previous.deepCopy(); out.put("accessToken", result.accessToken());
        if (result.refreshToken() != null) out.put("refreshToken", result.refreshToken()); return out;
    }
    private void saveTokens(McpConnectionEntity c, McpBusinessOAuthClient.TokenResult result, ObjectNode tokens) {
        c.setEncryptedTokens(crypto.encrypt("connection:" + c.getId(), tokens.toString())); c.setTokenExpiresAt(result.expiresAt());
        c.setAuthStatus("CONNECTED"); c.setUpdatedAt(Instant.now()); connections.save(c);
    }
    private String clientSecret(McpConnectionEntity c, OAuthSettings settings, String registered) {
        return settings.clientSecretRef() == null ? registered : secrets.resolve(c.getDeploymentId(), c.getTargetProfileId(), settings.clientSecretRef());
    }
    private AuthSettings auth(McpConnectionEntity c) { try { return mapper.readValue(c.getAuthConfigJson(), AuthSettings.class); }
        catch (Exception invalid) { throw new IllegalStateException("Stored MCP OAuth settings are invalid."); } }
    private JsonNode read(String value) { try { return mapper.readTree(value); } catch (Exception invalid) { throw new IllegalStateException("Stored MCP grant metadata is invalid."); } }
    private McpConnectionEntity ownedLocked(String deployment, String id) { return connections.findLocked(id)
        .filter(c -> deployment.equals(c.getDeploymentId()) && sameCustomer(c)).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "MCP connection not found.")); }
    private boolean sameCustomer(McpConnectionEntity c) { return deployments.findById(c.getDeploymentId())
        .map(d -> Objects.equals(d.getCustomerId(), c.getCustomerId())).orElse(false); }
    private void requireEditor(String id) {
        if (!PlatformSecurityContext.isAuthenticated()) throw new ResponseStatusException(UNAUTHORIZED);
        var deployment = deployments.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        if (PlatformSecurityContext.currentPrincipal().role() == PlatformRole.PLATFORM_OPERATOR) access.requireStaffCustomerAccess(deployment.getCustomerId());
        access.requireDeploymentEditorAccess(deployment);
        if (deployment.getArchivedAt() != null || "ARCHIVED".equals(deployment.getStatus())) throw invalid("Restore this Smart Brain before authorizing its connections.");
    }
    private URI callback() { return McpSafeHttpTransport.validate(properties.getCallbackUrl()); }
    private String returnPath(String deploymentId, String value) {
        String root = "/smart-brains/" + deploymentId + "/capabilities";
        if (value == null) return root;
        try {
            URI uri = URI.create(value);
            if (value.length() > 2048 || uri.isAbsolute() || uri.getRawAuthority() != null || uri.getFragment() != null || !root.equals(uri.getPath())) throw new IllegalArgumentException();
            return value;
        } catch (Exception invalid) { throw invalid("Return to the selected Smart Brain's capability page."); }
    }
    private void requireEnabled() { if (!properties.isEnabled()) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "MCP connections are disabled."); }
    private void requireCrypto() { if (!crypto.configured()) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Business OAuth credential encryption is not configured."); }
    private static boolean constantEquals(String expected, String actual) { return expected != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8)); }
    private static ResponseStatusException invalid(String reason) { return new ResponseStatusException(BAD_REQUEST, reason); }
}
