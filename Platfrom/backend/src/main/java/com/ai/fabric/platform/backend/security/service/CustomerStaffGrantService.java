package com.ai.fabric.platform.backend.security.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.security.CustomerSessionSupport;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.entity.CustomerStaffAccessEntity;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.CustomerStaffAccessRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

@Service
public class CustomerStaffGrantService {
    private final CustomerStaffAccessRepository repository;
    private final PlatformUserRepository users;
    private final PlatformCustomerAccessService access;
    private final EntityManager entityManager;
    private final PlatformAuditService audit;

    public CustomerStaffGrantService(CustomerStaffAccessRepository repository, PlatformUserRepository users,
        PlatformCustomerAccessService access, EntityManager entityManager, PlatformAuditService audit) {
        this.repository = repository;
        this.users = users;
        this.access = access;
        this.entityManager = entityManager;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Summary> list() {
        String customerId = ownerCustomerId();
        var grants = repository.findByCustomerIdOrderByGrantedAtAscIdAsc(customerId);
        var staff = users.findAllById(grants.stream().map(CustomerStaffAccessEntity::getUserId).distinct().toList())
            .stream().collect(Collectors.toMap(PlatformUserEntity::getId, Function.identity()));
        return grants.stream().map(grant -> summary(grant, staff.get(grant.getUserId()))).toList();
    }

    @Transactional
    public Summary grant(String email) {
        String customerId = lockOwnerCustomer();
        if (!StringUtils.hasText(email) || email.trim().length() > 255) throw ineligibleStaff();
        var staff = users.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT)).orElseThrow(this::ineligibleStaff);
        entityManager.refresh(staff);
        if (!"ACTIVE".equals(staff.getStatus()) || !PlatformRole.PLATFORM_OPERATOR.name().equals(staff.getRole())
            || StringUtils.hasText(staff.getCustomerId())) throw ineligibleStaff();
        var grant = repository.findByCustomerIdAndUserId(customerId, staff.getId()).orElse(null);
        if (grant != null && "ACTIVE".equals(grant.getStatus())) return summary(grant, staff);
        boolean regranted = grant != null;
        if (grant == null) {
            grant = new CustomerStaffAccessEntity();
            grant.setId("csa-" + UUID.randomUUID()); grant.setCustomerId(customerId); grant.setUserId(staff.getId());
        }
        grant.setStatus("ACTIVE"); grant.setGrantedBy(PlatformSecurityContext.actorIdOrSystem()); grant.setGrantedAt(Instant.now());
        grant.setRevokedBy(null); grant.setRevokedAt(null);
        repository.saveAndFlush(grant);
        audit.record("CUSTOMER_STAFF_ACCESS_GRANTED", "CUSTOMER_STAFF_ACCESS", grant.getId(),
            Map.of("customerId", customerId, "userId", staff.getId(), "regranted", regranted));
        return summary(grant, staff);
    }

    @Transactional
    public Summary revoke(String id) {
        String customerId = lockOwnerCustomer();
        var grant = repository.findByCustomerIdAndId(customerId, id).orElseThrow(this::missingGrant);
        var staff = users.findById(grant.getUserId()).orElseThrow(this::missingGrant);
        if ("REVOKED".equals(grant.getStatus())) return summary(grant, staff);
        grant.setStatus("REVOKED"); grant.setRevokedBy(PlatformSecurityContext.actorIdOrSystem()); grant.setRevokedAt(Instant.now());
        repository.saveAndFlush(grant);
        audit.record("CUSTOMER_STAFF_ACCESS_REVOKED", "CUSTOMER_STAFF_ACCESS", grant.getId(),
            Map.of("customerId", customerId, "userId", grant.getUserId()));
        return summary(grant, staff);
    }

    private String ownerCustomerId() {
        if (!CustomerSessionSupport.isCustomerSession(PlatformSecurityContext.currentPrincipal())) {
            throw new ResponseStatusException(FORBIDDEN, "An active customer administrator sign-in is required.");
        }
        var scope = access.currentScope();
        if (scope == null || !StringUtils.hasText(scope.customerId())) {
            throw new ResponseStatusException(FORBIDDEN, "An active customer workspace is required.");
        }
        return scope.customerId();
    }

    private String lockOwnerCustomer() {
        String customerId = ownerCustomerId();
        // Customer-controlled grants and retained revocations share one workspace mutation lock.
        var customer = entityManager.find(PlatformCustomerEntity.class, customerId, LockModeType.PESSIMISTIC_WRITE);
        if (customer == null) throw new ResponseStatusException(FORBIDDEN, "An active customer workspace is required.");
        entityManager.refresh(customer);
        var owner = PlatformUserRepository.findForPrincipal(users, PlatformSecurityContext.currentPrincipal()).orElseThrow(() ->
            new ResponseStatusException(FORBIDDEN, "An active customer administrator sign-in is required."));
        entityManager.refresh(owner);
        if (!"ACTIVE".equals(customer.getStatus()) || !"ACTIVE".equals(owner.getStatus())
            || !PlatformRole.CUSTOMER_ADMIN.name().equals(owner.getRole()) || !customerId.equals(owner.getCustomerId())) {
            throw new ResponseStatusException(FORBIDDEN, "An active customer administrator sign-in is required.");
        }
        return customerId;
    }

    private Summary summary(CustomerStaffAccessEntity grant, PlatformUserEntity staff) {
        if (staff == null) throw missingGrant();
        return new Summary(grant.getId(), grant.getCustomerId(), grant.getUserId(), staff.getEmail(), staff.getDisplayName(),
            grant.getStatus(), grant.getGrantedAt(), grant.getRevokedAt());
    }
    private ResponseStatusException ineligibleStaff() {
        return new ResponseStatusException(CONFLICT, "Use the existing email of an active LoomAI staff operator account. No access was changed.");
    }
    private ResponseStatusException missingGrant() { return new ResponseStatusException(NOT_FOUND, "Staff access entry not found."); }

    public record Summary(String id, String customerId, String userId, String email, String displayName,
                          String status, Instant grantedAt, Instant revokedAt) { }
}
