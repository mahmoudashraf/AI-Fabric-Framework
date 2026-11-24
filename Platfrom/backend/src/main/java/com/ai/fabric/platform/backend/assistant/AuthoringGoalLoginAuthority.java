package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.repository.*;
import com.ai.fabric.platform.backend.security.service.CustomerSupabaseIdentityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/** Retains verified login references/expiry, never cookie or bearer credentials. */
@Component
public class AuthoringGoalLoginAuthority {
    private final PlatformAuthProperties properties;
    private final PlatformUserSessionRepository sessions;
    private final PlatformCustomerIdentityLinkRepository links;
    private final CustomerSupabaseIdentityService identities;
    private final ObjectMapper mapper;
    public AuthoringGoalLoginAuthority(PlatformAuthProperties properties, PlatformUserSessionRepository sessions,
        PlatformCustomerIdentityLinkRepository links, CustomerSupabaseIdentityService identities, ObjectMapper mapper) {
        this.properties = properties; this.sessions = sessions; this.links = links; this.identities = identities; this.mapper = mapper;
    }
    public JsonNode capture(PlatformPrincipal principal) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) throw expired();
        var request = attributes.getRequest();
        var result = mapper.createObjectNode();
        if ("SUPABASE".equals(principal.authenticationMode())) {
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) throw expired();
            String token = header.substring(7);
            var verified = identities.authenticate(token);
            var jwt = identities.verifyIdentity(token);
            if (!principal.equals(verified) || jwt.getExpiresAt() == null) throw expired();
            var link = links.findByPlatformUserId(principal.platformUserId()).filter(l -> l.getRevokedAt() == null).orElseThrow(AuthoringGoalLoginAuthority::expired);
            return result.put("identityLinkId", link.getId()).put("validUntil", jwt.getExpiresAt().toString());
        }
        if (!"SESSION".equals(principal.authenticationMode()) || request.getCookies() == null) throw expired();
        for (var cookie : request.getCookies()) if (properties.sessionCookieName().equals(cookie.getName())) {
            var login = sessions.findByTokenHash(hash(cookie.getValue()))
                .filter(s -> s.getRevokedAt() == null && s.getExpiresAt().isAfter(Instant.now()))
                .filter(s -> principal.platformUserId().equals(s.getUserId())).orElseThrow(AuthoringGoalLoginAuthority::expired);
            return result.put("loginSessionId", login.getId()).put("validUntil", login.getExpiresAt().toString());
        }
        throw expired();
    }
    public void requireCurrent(JsonNode login, PlatformPrincipal principal) {
        if (!Instant.parse(login.path("validUntil").asText()).isAfter(Instant.now())) throw expired();
        if (login.hasNonNull("loginSessionId")) {
            sessions.findById(login.path("loginSessionId").asText())
                .filter(s -> s.getRevokedAt() == null && s.getExpiresAt().isAfter(Instant.now()))
                .filter(s -> principal.platformUserId().equals(s.getUserId())).orElseThrow(AuthoringGoalLoginAuthority::expired);
        } else {
            links.findById(login.path("identityLinkId").asText()).filter(l -> l.getRevokedAt() == null)
                .filter(l -> principal.platformUserId().equals(l.getPlatformUserId())).orElseThrow(AuthoringGoalLoginAuthority::expired);
        }
    }
    private String hash(String token) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private static ResponseStatusException expired() { return new ResponseStatusException(UNAUTHORIZED, "AUTHORING_REAUTHENTICATION_REQUIRED"); }
}
