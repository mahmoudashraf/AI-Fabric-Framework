package com.ai.fabric.platform.backend.security.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.model.CreatePlatformUserRequest;
import com.ai.fabric.platform.backend.security.repository.PlatformCustomerIdentityLinkRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@Service
public class StaffCustomerAccessService {
    private final CustomerOfferEntitlementService access;
    private final PlatformUserAdminService userAdmin;
    private final PlatformUserRepository users;
    private final PlatformCustomerIdentityLinkRepository links;
    private final EntityManager entityManager;
    private final PlatformAuditService audit;
    private final SecureRandom random = new SecureRandom();

    public StaffCustomerAccessService(CustomerOfferEntitlementService access, PlatformUserAdminService userAdmin,
        PlatformUserRepository users, PlatformCustomerIdentityLinkRepository links, EntityManager entityManager,
        PlatformAuditService audit) {
        this.access = access;
        this.userAdmin = userAdmin;
        this.users = users;
        this.links = links;
        this.entityManager = entityManager;
        this.audit = audit;
    }

    @Transactional
    public PreparedAccess prepare(String customerId, Request request) {
        access.requireStaffAdmin();
        if (!StringUtils.hasText(customerId) || request == null || !StringUtils.hasText(request.email())
            || request.email().trim().length() > 255 || !request.email().trim().contains("@")
            || !StringUtils.hasText(request.displayName()) || request.displayName().trim().length() > 255) {
            throw new ResponseStatusException(BAD_REQUEST, "A real email and display name are required.");
        }
        // Keep the customer active during preparation without taking a write lock ahead of recovery's user lock.
        var customer = entityManager.find(PlatformCustomerEntity.class, customerId, LockModeType.PESSIMISTIC_READ);
        if (customer == null) throw new ResponseStatusException(NOT_FOUND, "Customer not found.");
        entityManager.refresh(customer);
        if (!"ACTIVE".equals(customer.getStatus()) || customer.isPlatformManaged()) {
            throw new ResponseStatusException(CONFLICT, "Select an active customer workspace, not the platform internal customer.");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        var user = users.findByEmailForRecovery(email).orElse(null);
        PreparedAccess result;
        if (user == null) {
            byte[] password = new byte[32];
            random.nextBytes(password);
            var created = userAdmin.createUser(new CreatePlatformUserRequest(email, request.displayName().trim(),
                Base64.getUrlEncoder().withoutPadding().encodeToString(password), PlatformRole.CUSTOMER_ADMIN.name(), customerId));
            entityManager.flush();
            result = new PreparedAccess(created.id(), created.email(), created.displayName(), customerId, false, true);
        } else {
            entityManager.refresh(user);
            requireSameActiveMember(user, customerId);
            result = new PreparedAccess(user.getId(), user.getEmail(), user.getDisplayName(), customerId,
                identityLinked(user), false);
        }
        audit.record("STAFF_CUSTOMER_ACCESS_PREPARED", "PLATFORM_USER", result.userId(), Map.of(
            "customerId", customerId, "created", result.created(), "identityLinked", result.identityLinked()));
        return result;
    }

    private void requireSameActiveMember(PlatformUserEntity user, String customerId) {
        if (!"ACTIVE".equals(user.getStatus()) || !PlatformRole.CUSTOMER_ADMIN.name().equals(user.getRole())
            || !customerId.equals(user.getCustomerId())) {
            throw new ResponseStatusException(CONFLICT,
                "This email already belongs to another workspace, a staff account or an inactive account. No access was changed.");
        }
    }

    private boolean identityLinked(PlatformUserEntity user) {
        var link = links.findByPlatformUserId(user.getId()).orElse(null);
        if (link == null) return false;
        entityManager.lock(link, LockModeType.PESSIMISTIC_READ);
        entityManager.refresh(link);
        if (link.getRevokedAt() != null || !user.getId().equals(link.getPlatformUserId())
            || !StringUtils.hasText(link.getIssuer()) || !StringUtils.hasText(link.getSubject())) throw conflictingLink();
        var canonical = links.findByIssuerAndSubject(link.getIssuer(), link.getSubject()).orElse(null);
        if (canonical == null || !link.getId().equals(canonical.getId())
            || !user.getId().equals(canonical.getPlatformUserId())) throw conflictingLink();
        return true;
    }

    private ResponseStatusException conflictingLink() {
        return new ResponseStatusException(CONFLICT,
            "This account has a revoked or conflicting identity link. No access was changed; contact your administrator.");
    }

    public record Request(@NotBlank @Email @Size(max = 255) String email,
                          @NotBlank @Size(min = 2, max = 255) String displayName) { }
    public record PreparedAccess(String userId, String email, String displayName, String customerId,
                                 boolean identityLinked, boolean created) { }
}
