package com.ai.fabric.platform.backend.security.service;

import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformCustomerAccessServiceTest {
    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final PlatformCustomerRepository customers = mock(PlatformCustomerRepository.class);
    private final CustomerStaffAccessPolicy staffAccess = mock(CustomerStaffAccessPolicy.class);
    private final PlatformCustomerAccessService service = new PlatformCustomerAccessService(users, customers, staffAccess);
    private final PlatformPrincipal owner = new PlatformPrincipal("owner@example.com", PlatformRole.CUSTOMER_ADMIN, "Owner", "SESSION");
    private PlatformUserEntity user;
    private PlatformCustomerEntity customer;

    @BeforeEach
    void setUp() {
        user = new PlatformUserEntity();
        user.setRole("CUSTOMER_ADMIN");
        user.setStatus("ACTIVE");
        user.setCustomerId("cus-1");
        customer = new PlatformCustomerEntity();
        customer.setId("cus-1");
        customer.setStatus("ACTIVE");
        when(users.findByEmailIgnoreCase(owner.actorId())).thenReturn(Optional.of(user));
        when(customers.findById("cus-1")).thenReturn(Optional.of(customer));
    }

    @AfterEach void clearPrincipal() { SecurityContextHolder.clearContext(); }

    @Test void customerGrantedStaffRemainsStaffAndOnlySeesGrantedCustomers() {
        var operator = new PlatformPrincipal("engineer@example.com", PlatformRole.PLATFORM_OPERATOR, "Engineer", "SESSION");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(operator, null, List.of()));
        when(staffAccess.visibleCustomerIds(operator)).thenReturn(Set.of("cus-1"));
        when(staffAccess.decision("cus-1", operator)).thenReturn(Optional.of(true));
        var foreign = new PlatformCustomerEntity(); foreign.setId("cus-2");

        assertThat(service.filterVisibleCustomers(List.of(customer, foreign))).containsExactly(customer);
        service.requireStaffCustomerAccess("cus-1");
        service.requireCustomerManagementAccess("cus-1");
        assertThat(service.currentScope()).isNull();
        assertThat(service.canManageUserDirectory()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(operator);
        assertThatThrownBy(() -> service.requireCustomerManagementAccess("cus-2")).isInstanceOf(ResponseStatusException.class);

        when(staffAccess.decision("cus-1", operator)).thenReturn(Optional.of(false));
        assertThatThrownBy(() -> service.requireCustomerManagementAccess("cus-1")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void activeOwnerResolvesOnlyItsCustomer() {
        assertThat(service.currentScope(owner).customerId()).isEqualTo("cus-1");
    }

    @Test
    void missingUserCannotBecomeGlobalScope() {
        when(users.findByEmailIgnoreCase(owner.actorId())).thenReturn(Optional.empty());
        assertDenied();
    }

    @Test
    void missingCustomerCannotBecomeGlobalScope() {
        when(customers.findById("cus-1")).thenReturn(Optional.empty());
        assertDenied();
    }

    @Test
    void missingOwnerBindingCannotBecomeGlobalScope() {
        user.setCustomerId(null);
        assertDenied();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISABLED", "DELETED", "PENDING", ""})
    void inactiveCustomerCannotBecomeGlobalScope(String status) {
        customer.setStatus(status);
        assertDenied();
    }

    @Test
    void revokedOrChangedOwnerRoleCannotUseStalePrincipal() {
        user.setStatus("DISABLED");
        assertDenied();
        user.setStatus("ACTIVE");
        user.setRole("PLATFORM_OPERATOR");
        assertDenied();
    }

    @Test
    void staffScopeRemainsDistinctFromInvalidCustomerScope() {
        assertThat(service.currentScope(new PlatformPrincipal("admin", PlatformRole.PLATFORM_ADMIN, "Admin", "API_KEY"))).isNull();
    }

    private void assertDenied() {
        assertThatThrownBy(() -> service.currentScope(owner))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
