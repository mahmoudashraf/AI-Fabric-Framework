package com.ai.fabric.platform.backend.security.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.CustomerSupabaseAuthProperties;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.entity.PlatformCustomerIdentityLinkEntity;
import com.ai.fabric.platform.backend.security.repository.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.springframework.http.HttpStatus.*;

@Service
public class CustomerSupabaseIdentityService {
    private final CustomerSupabaseAuthProperties properties;
    private final ObjectProvider<JwtDecoder> decoder;
    private final CustomerSupabaseUserVerifier verifier;
    private final PlatformCustomerIdentityLinkRepository links;
    private final PlatformUserRepository users;
    private final PlatformCustomerAccessService customerAccess;
    private final PlatformAuditService audit;

    public CustomerSupabaseIdentityService(CustomerSupabaseAuthProperties properties,
        @Qualifier("customerSupabaseJwtDecoder") ObjectProvider<JwtDecoder> decoder,
        CustomerSupabaseUserVerifier verifier, PlatformCustomerIdentityLinkRepository links,
        PlatformUserRepository users, PlatformCustomerAccessService customerAccess, PlatformAuditService audit) {
        this.properties = properties; this.decoder = decoder; this.verifier = verifier;
        this.links = links; this.users = users; this.customerAccess = customerAccess; this.audit = audit;
    }

    public Jwt verifyIdentity(String token) {
        if (!properties.enabled() || decoder.getIfAvailable() == null) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Customer Supabase authentication is not configured.");
        }
        if (token == null || token.isBlank() || token.length() > 16384) throw denied();
        try {
            Jwt jwt = decoder.getObject().decode(token);
            verifier.requireVerifiedUser(token, jwt.getSubject());
            return jwt;
        } catch (JwtException | IllegalArgumentException ex) { throw denied(); }
    }

    @Transactional(readOnly = true)
    public PlatformPrincipal authenticate(String token) {
        Jwt jwt = verifyIdentity(token);
        // Delegated Desk identity scopes never grant Console administration.
        if (jwt.hasClaim("client_id")) throw denied();
        var link = links.findByIssuerAndSubject(jwt.getIssuer().toString(), jwt.getSubject())
            .filter(value -> value.getRevokedAt() == null).orElseThrow(() ->
                new ResponseStatusException(FORBIDDEN, "CUSTOMER_IDENTITY_NOT_LINKED: This identity has no active Console membership."));
        var user = users.findById(link.getPlatformUserId()).filter(value -> "ACTIVE".equals(value.getStatus())
            && PlatformRole.CUSTOMER_ADMIN.name().equals(value.getRole())).orElseThrow(this::denied);
        var principal = new PlatformPrincipal(user.getEmail(), PlatformRole.CUSTOMER_ADMIN, user.getDisplayName(), "SUPABASE", user.getId());
        customerAccess.currentScope(principal);
        return principal;
    }

    @Transactional
    public void linkCurrentCustomer(String token) {
        var principal = PlatformSecurityContext.currentPrincipal();
        if (!CustomerSessionSupport.isCustomerSession(principal) || !"SESSION".equals(principal.authenticationMode())) {
            throw new ResponseStatusException(FORBIDDEN, "An existing local customer session is required to link an identity.");
        }
        customerAccess.currentScope(principal);
        var user = PlatformUserRepository.findForPrincipal(users, principal).orElseThrow(this::denied);
        Jwt jwt = verifyIdentity(token);
        if (jwt.hasClaim("client_id")) throw denied();
        if (links.existsByPlatformUserId(user.getId()) || links.findByIssuerAndSubject(jwt.getIssuer().toString(), jwt.getSubject()).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "Customer identity links are immutable and cannot be reassigned.");
        }
        var link = new PlatformCustomerIdentityLinkEntity();
        link.setId("cil-" + UUID.randomUUID()); link.setIssuer(jwt.getIssuer().toString()); link.setSubject(jwt.getSubject());
        link.setPlatformUserId(user.getId()); link.setCreatedAt(Instant.now());
        links.saveAndFlush(link);
        audit.record("CUSTOMER_IDENTITY_LINKED", "PLATFORM_USER", user.getId(), Map.of("identityLinkId", link.getId()));
    }

    @Transactional
    public void revoke(String userId) {
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() != PlatformRole.PLATFORM_ADMIN) throw new ResponseStatusException(FORBIDDEN);
        var link = links.findByPlatformUserId(userId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        if (link.getRevokedAt() == null) {
            link.setRevokedAt(Instant.now()); links.save(link);
            audit.record("CUSTOMER_IDENTITY_REVOKED", "PLATFORM_USER", userId, Map.of("identityLinkId", link.getId()));
        }
    }

    private ResponseStatusException denied() { return new ResponseStatusException(UNAUTHORIZED, "Invalid customer Supabase identity."); }
}
