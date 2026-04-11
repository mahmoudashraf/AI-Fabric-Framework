package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class RuntimePublicTokenSigningService {

    public static final String SECRET_NAME = "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String TOKEN_SCHEME = "Bearer";
    private static final String TOKEN_PREFIX = "rpt1";

    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;

    public RuntimePublicTokenSigningService(PlatformSecretService platformSecretService,
                                            ObjectMapper objectMapper) {
        this.platformSecretService = platformSecretService;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(signingKey());
    }

    public String issueToken(RuntimePublicTokenClaims claims) {
        if (!isConfigured()) {
            throw new IllegalStateException("Runtime public token signing is not configured.");
        }
        validateClaims(claims);
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("sub", claims.subjectId().trim());
            payload.put("subjectType", claims.subjectType().trim());
            payload.put("authMode", claims.authMode().trim());
            payload.put("callerType", claims.callerType().trim());
            if (StringUtils.hasText(claims.sessionId())) {
                payload.put("sessionId", claims.sessionId().trim());
            }
            if (StringUtils.hasText(claims.deploymentId())) {
                payload.put("deploymentId", claims.deploymentId().trim());
            }
            if (StringUtils.hasText(claims.customerId())) {
                payload.put("customerId", claims.customerId().trim());
            }
            if (StringUtils.hasText(claims.tenantId())) {
                payload.put("tenantId", claims.tenantId().trim());
            }
            if (StringUtils.hasText(claims.issuer())) {
                payload.put("iss", claims.issuer().trim());
            }
            if (claims.audiences() != null && !claims.audiences().isEmpty()) {
                if (claims.audiences().size() == 1) {
                    payload.put("aud", claims.audiences().get(0).trim());
                } else {
                    ArrayNode audiences = payload.putArray("aud");
                    claims.audiences().stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .distinct()
                        .forEach(audiences::add);
                }
            }
            payload.put("exp", claims.expiresAt().toString());
            ArrayNode scopes = payload.putArray("scopes");
            if (claims.grantedScopes() != null) {
                claims.grantedScopes().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .forEach(scopes::add);
            }

            String payloadSegment = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(payload));
            String signatureSegment = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sign(payloadSegment));
            return TOKEN_PREFIX + "." + payloadSegment + "." + signatureSegment;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to issue runtime public token.", ex);
        }
    }

    public String toAuthorizationHeaderValue(RuntimePublicTokenClaims claims) {
        return TOKEN_SCHEME + " " + issueToken(claims);
    }

    private void validateClaims(RuntimePublicTokenClaims claims) {
        if (claims == null
            || !StringUtils.hasText(claims.subjectId())
            || !StringUtils.hasText(claims.subjectType())
            || !StringUtils.hasText(claims.authMode())
            || !StringUtils.hasText(claims.callerType())
            || claims.expiresAt() == null) {
            throw new IllegalArgumentException("Runtime public token claims are incomplete.");
        }
    }

    private byte[] sign(String payloadSegment) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payloadSegment.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign runtime public token.", ex);
        }
    }

    private String signingKey() {
        String signingKey = platformSecretService.resolveSecret(SECRET_NAME);
        return StringUtils.hasText(signingKey) ? signingKey.trim() : null;
    }

    public record RuntimePublicTokenClaims(
        String subjectId,
        String subjectType,
        String authMode,
        String callerType,
        String sessionId,
        String deploymentId,
        String customerId,
        String tenantId,
        String issuer,
        Instant expiresAt,
        List<String> audiences,
        List<String> grantedScopes
    ) {
    }
}
