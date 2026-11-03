package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ai.fabric.platform.backend.support.SupportContracts.Requester;

/** Console-to-provider identity. The assertion and its separate server key never reach a browser. */
@Service
public class SupportRequesterAssertionService {
    private final SupportSettings settings;
    private final PlatformSecretService secrets;
    private final Environment environment;

    public SupportRequesterAssertionService(SupportSettings settings, PlatformSecretService secrets, Environment environment) {
        this.settings = settings;
        this.secrets = secrets;
        this.environment = environment;
    }

    public void prepare() {
        var binding = settings.binding();
        var metadata = Map.of("deploymentId", binding.deploymentId(), "targetProfileId", binding.targetProfileId(),
            "bindingId", settings.deskBindingId(), "purpose", "CONSOLE_SUPPORT_REQUESTER");
        try {
            if (secrets.resolveSecret(name("SIGNING")) == null) {
                RSAKey key = new RSAKeyGenerator(2048).keyID(settings.deskBindingId()).generate();
                secrets.upsertManagedSecret(name("SIGNING"), key.toJSONString(), metadata);
            }
            for (String purpose : List.of("SERVER", "CONNECTOR")) {
                if (secrets.resolveSecret(name(purpose)) == null) {
                    byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
                    secrets.upsertManagedSecret(name(purpose), Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), metadata);
                }
            }
        } catch (com.nimbusds.jose.JOSEException error) { throw new IllegalStateException("Cannot prepare support requester identity", error); }
    }

    public String issue(Requester requester, String spaceId) {
        return issue(requester, spaceId, null, null);
    }
    public String issue(Requester requester, String spaceId, String runtimePrincipal, String runtimeConversationId) {
        try {
            Instant now = Instant.now();
            var builder = new JWTClaimsSet.Builder().issuer(issuer()).audience(audience(spaceId))
                .subject(subject(requester)).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(120)))
                .jwtID(UUID.randomUUID().toString()).claim("spaceId", spaceId)
                .claim("operations", List.of("SESSION", "TEAM", "CREATE", "LIST", "GET", "START", "RESOLVE",
                    "CANCEL", "REOPEN", "SEEN", "LINK_CONVERSATION", "ACTIVITY", "TICKET_COMMAND"))
                ;
            if (runtimePrincipal != null) builder.claim("runtimePrincipal", runtimePrincipal)
                .claim("runtimeConversationId", runtimeConversationId);
            var claims = builder.build();
            var key = key();
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception error) { throw SupportPolicy.unavailable(); }
    }

    public Map<String, Object> publicKeys() { return new JWKSet(key().toPublicJWK()).toJSONObject(); }
    public String serverKey() { return secret("SERVER"); }
    public String connectorKey() { return secret("CONNECTOR"); }
    public String issuer() { return origin() + "/api/support/requester"; }
    public String jwksUri() { return origin() + "/api/public/support/requester-jwks"; }
    public String audience(String spaceId) { return "loomai-support-requester:" + spaceId; }

    public static String subject(Requester requester) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                (requester.customerId() + "\n" + requester.platformUserId()).getBytes(StandardCharsets.UTF_8));
            return "console-" + java.util.HexFormat.of().formatHex(digest);
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private RSAKey key() {
        try { return RSAKey.parse(secret("SIGNING")); }
        catch (Exception error) { throw SupportPolicy.unavailable(); }
    }
    private String secret(String purpose) {
        String value = secrets.resolveSecret(name(purpose));
        if (value == null || value.isBlank()) throw SupportPolicy.unavailable();
        return value;
    }
    private String name(String purpose) {
        return "MANAGED_SUPPORT_REQUESTER_" + purpose + "_" + settings.deskBindingId().replace('-', '_').toUpperCase(java.util.Locale.ROOT);
    }
    private String origin() {
        String value = environment.getProperty("LOOMAI_DESK_REVIEW_BRIDGE_BASE_URL", "");
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || !uri.getPath().matches("/?")) throw SupportPolicy.unavailable();
            return value.replaceAll("/$", "");
        } catch (IllegalArgumentException error) { throw SupportPolicy.unavailable(); }
    }
}
