package com.ai.fabric.platform.backend.security.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.CustomerOnboardingProperties;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.entity.PlatformCustomerIdentityLinkEntity;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.*;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import static org.springframework.http.HttpStatus.*;

/** Creates only the verified caller's new ownership boundary, never infrastructure or access to another customer. */
@Service
public class CustomerOnboardingService {
    private final CustomerSupabaseIdentityService identities;
    private final CustomerOnboardingProperties properties;
    private final PlatformCustomerIdentityLinkRepository links;
    private final PlatformUserRepository users;
    private final PlatformCustomerRepository customers;
    private final PasswordEncoder passwords;
    private final PlatformAuditService audit;

    public CustomerOnboardingService(CustomerSupabaseIdentityService identities, CustomerOnboardingProperties properties,
        PlatformCustomerIdentityLinkRepository links, PlatformUserRepository users, PlatformCustomerRepository customers,
        PasswordEncoder passwords, PlatformAuditService audit) {
        this.identities = identities; this.properties = properties; this.links = links; this.users = users;
        this.customers = customers; this.passwords = passwords; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Summary status(String token) {
        return inspect(verifiedIdentity(token));
    }

    @Transactional
    public Summary create(String token, String workspaceName, String displayName) {
        Jwt identity = verifiedIdentity(token);
        Summary existing = inspect(identity);
        if (existing.status().equals("READY")) return existing;
        if (!existing.status().equals("CREATE_WORKSPACE")) {
            throw new ResponseStatusException(CONFLICT, "Use the existing account-linking flow or contact your workspace administrator.");
        }
        String name = requiredName(workspaceName);
        String ownerName = requiredName(displayName);
        Instant now = Instant.now();
        var customer = new PlatformCustomerEntity();
        customer.setId("cus-" + UUID.randomUUID());
        customer.setName(name);
        customer.setSlug("workspace-" + UUID.randomUUID());
        customer.setDescription("Customer-created LoomAI workspace.");
        customer.setStatus("ACTIVE"); customer.setPlatformManaged(false);
        customer.setCreatedAt(now); customer.setUpdatedAt(now);
        customers.saveAndFlush(customer);

        var owner = new PlatformUserEntity();
        owner.setId("usr-" + UUID.randomUUID()); owner.setEmail(email(identity)); owner.setDisplayName(ownerName);
        // Supabase is the only usable credential. No local password is issued to the customer.
        owner.setPasswordHash(passwords.encode(UUID.randomUUID().toString()));
        owner.setRole(PlatformRole.CUSTOMER_ADMIN.name()); owner.setCustomerId(customer.getId()); owner.setStatus("ACTIVE");
        owner.setCreatedAt(now); owner.setUpdatedAt(now);
        users.saveAndFlush(owner);
        var link = new PlatformCustomerIdentityLinkEntity();
        link.setId("cil-" + UUID.randomUUID()); link.setIssuer(identity.getIssuer().toString());
        link.setSubject(identity.getSubject()); link.setPlatformUserId(owner.getId()); link.setCreatedAt(now);
        links.saveAndFlush(link);
        audit.record("CUSTOMER_WORKSPACE_REGISTERED", "PLATFORM_CUSTOMER", customer.getId(), Map.of(
            "customerId", customer.getId(), "ownerUserId", owner.getId(), "identityLinkId", link.getId(),
            "authenticationMode", "SUPABASE"));
        return new Summary("READY", customer.getId(), customer.getName());
    }

    private Jwt verifiedIdentity(String token) {
        Jwt identity = identities.verifyIdentity(token);
        // OAuth-delegated Desk identity must not create a Console ownership boundary.
        if (identity.hasClaim("client_id")) throw new ResponseStatusException(FORBIDDEN, "Console sign-in is required.");
        if (identity.getIssuer() == null || identity.getSubject() == null || identity.getSubject().isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "A verified customer identity is required.");
        }
        email(identity);
        return identity;
    }

    private Summary inspect(Jwt identity) {
        var link = links.findByIssuerAndSubject(identity.getIssuer().toString(), identity.getSubject()).orElse(null);
        if (link != null) {
            if (link.getRevokedAt() != null) throw new ResponseStatusException(FORBIDDEN, "Console membership has been revoked.");
            var user = users.findById(link.getPlatformUserId()).filter(value -> "ACTIVE".equals(value.getStatus())
                && PlatformRole.CUSTOMER_ADMIN.name().equals(value.getRole())).orElseThrow(this::disabled);
            if (user.getCustomerId() == null || user.getCustomerId().isBlank()) throw disabled();
            var customer = customers.findById(user.getCustomerId()).filter(value -> "ACTIVE".equals(value.getStatus()))
                .orElseThrow(this::disabled);
            return new Summary("READY", customer.getId(), customer.getName());
        }
        // Never claim or attach an existing account by email, even when the provider verified it.
        if (users.findByEmailIgnoreCase(email(identity)).isPresent()) return new Summary("LINK_REQUIRED", null, null);
        if (!properties.enabled()) return new Summary("REGISTRATION_CLOSED", null, null);
        return new Summary("CREATE_WORKSPACE", null, null);
    }

    private String email(Jwt identity) {
        String email = identity.getClaimAsString("email");
        if (email == null || email.isBlank() || email.length() > 255 || !email.contains("@")) {
            throw new ResponseStatusException(UNAUTHORIZED, "A verified email identity is required.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String requiredName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 120) {
            throw new ResponseStatusException(BAD_REQUEST, "Names must contain between 1 and 120 characters.");
        }
        return name.trim();
    }

    private ResponseStatusException disabled() { return new ResponseStatusException(FORBIDDEN, "Customer membership is not active."); }
    public record Summary(String status, String customerId, String workspaceName) { }
}
