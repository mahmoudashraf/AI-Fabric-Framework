package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.repository.PlatformCustomerIdentityLinkRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;

@Service
public class SupportRequesterService {
    private final PlatformUserRepository users;
    private final PlatformCustomerIdentityLinkRepository links;
    private final PlatformCustomerAccessService customers;

    public SupportRequesterService(PlatformUserRepository users, PlatformCustomerIdentityLinkRepository links,
                                   PlatformCustomerAccessService customers) {
        this.users = users; this.links = links; this.customers = customers;
    }

    public Requester current() {
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() != PlatformRole.CUSTOMER_ADMIN
            || !("SESSION".equals(principal.authenticationMode()) || "SUPABASE".equals(principal.authenticationMode()))) {
            throw SupportPolicy.denied();
        }
        var user = PlatformUserRepository.findForPrincipal(users, principal).orElseThrow(SupportPolicy::denied);
        var scope = customers.currentScope(principal);
        if (scope == null) throw SupportPolicy.denied();
        var link = links.findByPlatformUserId(user.getId()).orElse(null);
        if (link != null && link.getRevokedAt() != null || "SUPABASE".equals(principal.authenticationMode()) && link == null) {
            throw SupportPolicy.denied();
        }
        return new Requester(scope.customerId(), user.getId(), link == null ? null : link.getIssuer(), link == null ? null : link.getSubject());
    }

    public void requireCurrent(Requester owner) {
        var current = current();
        if (!owner.customerId().equals(current.customerId()) || !owner.platformUserId().equals(current.platformUserId())
            || owner.issuer() != null && (!Objects.equals(owner.issuer(), current.issuer()) || !Objects.equals(owner.subject(), current.subject()))) {
            throw SupportPolicy.missing();
        }
        requireActive(owner);
    }

    /** Diagnostic requests have no customer session; recheck the original immutable member explicitly. */
    public void requireActive(Requester owner) {
        var user = users.findById(owner.platformUserId()).orElseThrow(SupportPolicy::denied);
        if (!"ACTIVE".equals(user.getStatus()) || !PlatformRole.CUSTOMER_ADMIN.name().equals(user.getRole())
            || !owner.customerId().equals(user.getCustomerId())) throw SupportPolicy.denied();
        var scope = customers.currentScope(new PlatformPrincipal(user.getEmail(), PlatformRole.CUSTOMER_ADMIN,
            user.getDisplayName(), "SUPABASE", user.getId()));
        if (scope == null || !owner.customerId().equals(scope.customerId())) throw SupportPolicy.denied();
        var currentLink = links.findByPlatformUserId(owner.platformUserId()).orElse(null);
        if (currentLink != null && currentLink.getRevokedAt() != null) throw SupportPolicy.denied();
        if (owner.issuer() != null || owner.subject() != null) {
            if (currentLink == null) throw SupportPolicy.denied();
            var link = currentLink;
            if (link.getRevokedAt() != null || !Objects.equals(link.getIssuer(), owner.issuer())
                || !Objects.equals(link.getSubject(), owner.subject())) throw SupportPolicy.denied();
        }
    }
}
