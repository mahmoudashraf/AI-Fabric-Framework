package com.ai.fabric.platform.backend.security.service;

import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.CustomerStaffAccessRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomerStaffAccessPolicy {
    private final CustomerStaffAccessRepository repository;
    private final PlatformUserRepository users;
    private final PlatformCustomerRepository customers;

    public CustomerStaffAccessPolicy(CustomerStaffAccessRepository repository, PlatformUserRepository users,
                                     PlatformCustomerRepository customers) {
        this.repository = repository;
        this.users = users;
        this.customers = customers;
    }

    /** Global platform-admin access is owned by the caller, not this customer-controlled operator policy. */
    @Transactional(readOnly = true)
    public Optional<Boolean> decision(String customerId, PlatformPrincipal principal) {
        if (!sessionOperator(principal)) return Optional.empty();
        var operator = operator(principal);
        if (operator.isEmpty() || !StringUtils.hasText(customerId)
            || customers.findById(customerId).filter(customer -> "ACTIVE".equals(customer.getStatus())).isEmpty()) {
            return Optional.of(false);
        }
        return repository.findByCustomerIdAndUserId(customerId, operator.get().getId())
            .map(grant -> "ACTIVE".equals(grant.getStatus()));
    }

    @Transactional(readOnly = true)
    public Set<String> visibleCustomerIds(PlatformPrincipal principal) {
        var operator = operator(principal);
        if (operator.isEmpty()) return Set.of();
        var ids = repository.findByUserIdAndStatus(operator.get().getId(), "ACTIVE").stream()
            .map(grant -> grant.getCustomerId()).collect(Collectors.toSet());
        if (ids.isEmpty()) return Set.of();
        return customers.findAllById(ids).stream().filter(customer -> "ACTIVE".equals(customer.getStatus()))
            .map(customer -> customer.getId()).collect(Collectors.toUnmodifiableSet());
    }

    private Optional<PlatformUserEntity> operator(PlatformPrincipal principal) {
        if (!sessionOperator(principal) || !StringUtils.hasText(principal.actorId())) {
            return Optional.empty();
        }
        return PlatformUserRepository.findForPrincipal(users, principal).filter(user ->
            "ACTIVE".equals(user.getStatus()) && PlatformRole.PLATFORM_OPERATOR.name().equals(user.getRole())
                && !StringUtils.hasText(user.getCustomerId()) && principal.actorId().equalsIgnoreCase(user.getEmail()));
    }

    private boolean sessionOperator(PlatformPrincipal principal) {
        return principal != null && principal.role() == PlatformRole.PLATFORM_OPERATOR
            && "SESSION".equals(principal.authenticationMode());
    }
}
