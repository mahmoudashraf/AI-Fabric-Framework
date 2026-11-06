package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.security.entity.CustomerStaffAccessEntity;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.CustomerStaffAccessRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.CustomerStaffAccessPolicy;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerStaffAccessPolicyTest {
    private final CustomerStaffAccessRepository repository = mock(CustomerStaffAccessRepository.class);
    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final PlatformCustomerRepository customers = mock(PlatformCustomerRepository.class);
    private final CustomerStaffAccessPolicy policy = new CustomerStaffAccessPolicy(repository, users, customers);
    private final PlatformUserEntity operator = new PlatformUserEntity();
    private final PlatformCustomerEntity customer = new PlatformCustomerEntity();
    private final PlatformPrincipal principal = new PlatformPrincipal("operator@example.test", PlatformRole.PLATFORM_OPERATOR,
        "Operator", "SESSION", "operator");

    @BeforeEach void setup() {
        operator.setId("operator"); operator.setEmail(principal.actorId()); operator.setRole("PLATFORM_OPERATOR"); operator.setStatus("ACTIVE");
        customer.setId("customer-a"); customer.setStatus("ACTIVE");
        when(users.findById(operator.getId())).thenReturn(Optional.of(operator));
        when(users.findByEmailIgnoreCase(operator.getEmail())).thenReturn(Optional.of(operator));
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));
    }

    @Test void noRecordForValidSessionOperatorLeavesLegacyAssignmentsToTheCaller() {
        assertThat(policy.decision("customer-a", principal)).isEmpty();
        verify(repository).findByCustomerIdAndUserId("customer-a", "operator");
    }

    @Test void activeGrantAndRetainedRevocationAreReReadForTheSamePrincipalWithoutCaching() {
        var active = grant("customer-a", "ACTIVE"); var revoked = grant("customer-a", "REVOKED");
        when(repository.findByCustomerIdAndUserId("customer-a", "operator")).thenReturn(Optional.of(active), Optional.of(revoked));
        assertThat(policy.decision("customer-a", principal)).contains(true);
        assertThat(policy.decision("customer-a", principal)).contains(false);
        verify(repository, times(2)).findByCustomerIdAndUserId("customer-a", "operator");
        assertThat(policy.decision("customer-a", principal).orElse(true)).isFalse();
    }

    @ParameterizedTest @EnumSource(value = PlatformRole.class, names = "PLATFORM_OPERATOR", mode = EnumSource.Mode.EXCLUDE)
    void otherRolesAreOutsideThisPolicyIncludingGlobalAdmins(PlatformRole role) {
        var other = new PlatformPrincipal(principal.actorId(), role, "Other", "SESSION", "operator");
        assertThat(policy.decision("customer-a", other)).isEmpty(); assertThat(policy.visibleCustomerIds(other)).isEmpty();
        verifyNoInteractions(repository, users, customers);
    }

    @ParameterizedTest @ValueSource(strings = {"API_KEY", "SUPABASE", "PUBLIC_API", ""})
    void nonSessionOperatorCredentialsKeepExistingExplicitAccessPaths(String mode) {
        var other = new PlatformPrincipal(principal.actorId(), PlatformRole.PLATFORM_OPERATOR, "Operator", mode, "operator");
        assertThat(policy.decision("customer-a", other)).isEmpty(); assertThat(policy.visibleCustomerIds(other)).isEmpty();
        verifyNoInteractions(repository, users, customers);
    }

    @Test void nullPrincipalDoesNotApplyThisPolicy() {
        assertThat(policy.decision("customer-a", null)).isEmpty(); assertThat(policy.visibleCustomerIds(null)).isEmpty();
        verifyNoInteractions(repository, users, customers);
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "disabled", "changed-role", "bound-customer", "mismatched-email", "blank-actor"})
    void staleSessionOperatorIdentityIsAnExplicitDenialNotLegacyFallback(String fault) {
        var current = principal;
        if (fault.equals("missing")) when(users.findById("operator")).thenReturn(Optional.empty());
        if (fault.equals("disabled")) operator.setStatus("DISABLED");
        if (fault.equals("changed-role")) operator.setRole("PLATFORM_ADMIN");
        if (fault.equals("bound-customer")) operator.setCustomerId("another-customer");
        if (fault.equals("mismatched-email")) operator.setEmail("another@example.test");
        if (fault.equals("blank-actor")) current = new PlatformPrincipal("", PlatformRole.PLATFORM_OPERATOR, "Operator", "SESSION", "operator");
        assertThat(policy.decision("customer-a", current)).contains(false);
        assertThat(policy.visibleCustomerIds(current)).isEmpty(); verifyNoInteractions(repository);
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "disabled", "blank"})
    void invalidCustomerIsAnExplicitDenialEvenWithoutAGrantRecord(String fault) {
        String id = "customer-a";
        if (fault.equals("missing")) when(customers.findById(id)).thenReturn(Optional.empty());
        if (fault.equals("disabled")) customer.setStatus("DISABLED");
        if (fault.equals("blank")) id = "";
        assertThat(policy.decision(id, principal)).contains(false); verifyNoInteractions(repository);
    }

    @Test void sessionWithoutEmbeddedUserIdResolvesTheExistingStaffLoginEmail() {
        var session = new PlatformPrincipal(principal.actorId(), PlatformRole.PLATFORM_OPERATOR, "Operator", "SESSION");
        assertThat(policy.decision("customer-a", session)).isEmpty();
        verify(users).findByEmailIgnoreCase(principal.actorId());
    }

    @Test void visibleCustomersContainOnlyActiveGrantsAndActiveCustomersAndAreReRead() {
        var disabled = new PlatformCustomerEntity(); disabled.setId("disabled"); disabled.setStatus("DISABLED");
        when(repository.findByUserIdAndStatus("operator", "ACTIVE"))
            .thenReturn(List.of(grant("customer-a", "ACTIVE"), grant("disabled", "ACTIVE"), grant("missing", "ACTIVE")), List.of());
        when(customers.findAllById(any())).thenReturn(List.of(customer, disabled));
        assertThat(policy.visibleCustomerIds(principal)).isEqualTo(Set.of("customer-a"));
        assertThat(policy.visibleCustomerIds(principal)).isEmpty();
        verify(repository, times(2)).findByUserIdAndStatus("operator", "ACTIVE");
    }

    private CustomerStaffAccessEntity grant(String customerId, String status) {
        var grant = new CustomerStaffAccessEntity(); grant.setId("grant-" + customerId); grant.setUserId("operator");
        grant.setCustomerId(customerId); grant.setStatus(status); return grant;
    }
}
