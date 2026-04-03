package com.ai.fabric.platform.backend.security.service;

import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class PlatformCustomerAccessService {

    private final PlatformUserRepository platformUserRepository;
    private final PlatformCustomerRepository platformCustomerRepository;

    public PlatformCustomerAccessService(PlatformUserRepository platformUserRepository,
                                         PlatformCustomerRepository platformCustomerRepository) {
        this.platformUserRepository = platformUserRepository;
        this.platformCustomerRepository = platformCustomerRepository;
    }

    public boolean canManageCustomers() {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        return principal == null
            || principal.role() == PlatformRole.PLATFORM_ADMIN
            || principal.role() == PlatformRole.CUSTOMER_ADMIN;
    }

    public boolean canCreateCustomers() {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        return principal == null || principal.role() == PlatformRole.PLATFORM_ADMIN;
    }

    public boolean canManageUserDirectory() {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        return principal == null
            || principal.role() == PlatformRole.PLATFORM_ADMIN
            || principal.role() == PlatformRole.CUSTOMER_ADMIN;
    }

    public boolean isCustomerAdmin() {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        return principal != null && principal.role() == PlatformRole.CUSTOMER_ADMIN;
    }

    @Transactional(readOnly = true)
    public CustomerScopeSummary currentScope() {
        return currentScope(PlatformSecurityContext.currentPrincipal());
    }

    @Transactional(readOnly = true)
    public CustomerScopeSummary currentScope(PlatformPrincipal principal) {
        if (principal == null || principal.role() != PlatformRole.CUSTOMER_ADMIN) {
            return null;
        }
        PlatformUserEntity user = currentUser(principal);
        if (user == null || !StringUtils.hasText(user.getCustomerId())) {
            return null;
        }
        PlatformCustomerEntity customer = platformCustomerRepository.findById(user.getCustomerId()).orElse(null);
        if (customer == null) {
            return null;
        }
        return new CustomerScopeSummary(customer.getId(), customer.getName(), customer.getSlug(), customer.getStatus());
    }

    public List<PlatformCustomerEntity> filterVisibleCustomers(List<PlatformCustomerEntity> customers) {
        CustomerScopeSummary scope = currentScope();
        if (scope == null) {
            return customers;
        }
        return customers.stream()
            .filter(customer -> scope.customerId().equals(customer.getId()))
            .toList();
    }

    public void requireCustomerManagementAccess(String customerId) {
        requireCustomerScope(customerId, "Customer access is restricted to the authenticated customer boundary.");
    }

    public void requireDeploymentCustomerAccess(String customerId) {
        requireCustomerScope(customerId, "Deployment access is restricted to the authenticated customer boundary.");
    }

    private void requireCustomerScope(String customerId, String message) {
        if (!StringUtils.hasText(customerId)) {
            return;
        }
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() == PlatformRole.PLATFORM_ADMIN) {
            return;
        }
        if (principal.role() != PlatformRole.CUSTOMER_ADMIN) {
            throw new ResponseStatusException(FORBIDDEN, message);
        }
        CustomerScopeSummary scope = currentScope(principal);
        if (scope == null || !customerId.trim().equals(scope.customerId())) {
            throw new ResponseStatusException(FORBIDDEN, message);
        }
    }

    private PlatformUserEntity currentUser(PlatformPrincipal principal) {
        if (principal == null || !StringUtils.hasText(principal.actorId())) {
            return null;
        }
        return platformUserRepository.findByEmailIgnoreCase(principal.actorId()).orElse(null);
    }

    public record CustomerScopeSummary(
        String customerId,
        String customerName,
        String customerSlug,
        String customerStatus
    ) {
    }
}
