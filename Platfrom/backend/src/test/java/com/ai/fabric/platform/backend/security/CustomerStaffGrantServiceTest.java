package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.audit.entity.PlatformAuditEventEntity;
import com.ai.fabric.platform.backend.audit.repository.PlatformAuditEventRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.security.entity.CustomerStaffAccessEntity;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.CustomerStaffAccessRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.CustomerStaffAccessPolicy;
import com.ai.fabric.platform.backend.security.service.CustomerStaffGrantService;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class CustomerStaffGrantServiceTest {
    private final CustomerStaffAccessRepository repository = mock(CustomerStaffAccessRepository.class);
    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final PlatformCustomerRepository customers = mock(PlatformCustomerRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final PlatformAuditEventRepository auditEvents = mock(PlatformAuditEventRepository.class);
    private final Map<String, PlatformUserEntity> accounts = new HashMap<>();
    private final Map<String, CustomerStaffAccessEntity> grants = new LinkedHashMap<>();
    private final PlatformCustomerEntity customer = new PlatformCustomerEntity();
    private PlatformUserEntity owner;
    private PlatformUserEntity operator;
    private CustomerStaffGrantService service;
    private CustomerStaffAccessPolicy policy;

    @BeforeEach void setup() {
        customer.setId("customer-a"); customer.setName("Customer A"); customer.setSlug("customer-a"); customer.setStatus("ACTIVE");
        owner = account("owner", "owner@example.test", "CUSTOMER_ADMIN", "customer-a");
        operator = account("operator", "operator@example.test", "PLATFORM_OPERATOR", null);
        when(users.findById(anyString())).thenAnswer(call -> Optional.ofNullable(accounts.get(call.getArgument(0))));
        when(users.findByEmailIgnoreCase(anyString())).thenAnswer(call -> accounts.values().stream()
            .filter(user -> user.getEmail().equalsIgnoreCase(call.getArgument(0))).findFirst());
        when(users.findAllById(any())).thenAnswer(call -> {
            List<PlatformUserEntity> result = new ArrayList<>();
            call.getArgument(0, Iterable.class).forEach(id -> { if (accounts.containsKey(id)) result.add(accounts.get(id)); });
            return result;
        });
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(em.find(PlatformCustomerEntity.class, customer.getId(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(customer);
        when(repository.findByCustomerIdAndUserId(anyString(), anyString())).thenAnswer(call -> grants.values().stream()
            .filter(grant -> grant.getCustomerId().equals(call.getArgument(0)) && grant.getUserId().equals(call.getArgument(1))).findFirst());
        when(repository.findByCustomerIdAndId(anyString(), anyString())).thenAnswer(call -> Optional.ofNullable(grants.get(call.getArgument(1)))
            .filter(grant -> grant.getCustomerId().equals(call.getArgument(0))));
        when(repository.findByCustomerIdOrderByGrantedAtAscIdAsc(anyString())).thenAnswer(call -> grants.values().stream()
            .filter(grant -> grant.getCustomerId().equals(call.getArgument(0))).toList());
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            var grant = call.getArgument(0, CustomerStaffAccessEntity.class); grants.put(grant.getId(), grant); return grant;
        });
        policy = new CustomerStaffAccessPolicy(repository, users, customers);
        var access = new PlatformCustomerAccessService(users, customers, policy);
        service = new CustomerStaffGrantService(repository, users, access, em, new PlatformAuditService(auditEvents, new ObjectMapper()));
        authenticate(PlatformRole.CUSTOMER_ADMIN, "SESSION", owner);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void grantsOnlyExistingOperatorInActualOwnersWorkspaceWithoutChangingPrincipalOrAccount() {
        var principal = PlatformSecurityContext.currentPrincipal();
        var response = service.grant("  OPERATOR@EXAMPLE.TEST  ");
        assertThat(response.customerId()).isEqualTo(customer.getId()); assertThat(response.userId()).isEqualTo(operator.getId());
        assertThat(response.email()).isEqualTo(operator.getEmail()); assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.grantedAt()).isNotNull(); assertThat(response.revokedAt()).isNull();
        assertThat(PlatformSecurityContext.currentPrincipal()).isSameAs(principal);
        assertThat(operator.getCustomerId()).isNull(); assertThat(operator.getRole()).isEqualTo("PLATFORM_OPERATOR");
        verify(em).find(PlatformCustomerEntity.class, "customer-a", LockModeType.PESSIMISTIC_WRITE);
        verify(users, never()).save(any()); verify(repository, never()).delete(any());
        var event = ArgumentCaptor.forClass(PlatformAuditEventEntity.class); verify(auditEvents).save(event.capture());
        assertThat(event.getValue().getActorId()).isEqualTo(owner.getEmail());
        assertThat(event.getValue().getActorRole()).isEqualTo("CUSTOMER_ADMIN");
        assertThat(event.getValue().getAction()).isEqualTo("CUSTOMER_STAFF_ACCESS_GRANTED");
    }

    @Test void activeGrantAndRepeatedRevokeAreIdempotentAndRegrantKeepsTheTombstoneRowId() {
        var first = service.grant(operator.getEmail());
        assertThat(service.grant(operator.getEmail())).isEqualTo(first);
        var revoked = service.revoke(first.id());
        assertThat(revoked.status()).isEqualTo("REVOKED"); assertThat(revoked.revokedAt()).isNotNull();
        assertThat(service.revoke(first.id())).isEqualTo(revoked);
        assertThat(grants.get(first.id()).getRevokedBy()).isEqualTo(owner.getEmail());
        var again = service.grant(operator.getEmail());
        assertThat(again.id()).isEqualTo(first.id()); assertThat(again.status()).isEqualTo("ACTIVE");
        assertThat(again.revokedAt()).isNull(); assertThat(grants).hasSize(1);
        verify(repository, times(3)).saveAndFlush(any()); verify(repository, never()).delete(any());
        var events = ArgumentCaptor.forClass(PlatformAuditEventEntity.class); verify(auditEvents, times(3)).save(events.capture());
        assertThat(events.getAllValues()).extracting(PlatformAuditEventEntity::getAction)
            .containsExactly("CUSTOMER_STAFF_ACCESS_GRANTED", "CUSTOMER_STAFF_ACCESS_REVOKED", "CUSTOMER_STAFF_ACCESS_GRANTED");
        assertThat(events.getAllValues().get(2).getDetailsJson()).contains("\"regranted\":true");
    }

    @Test void listIncludesRevokedOwnEntriesButNotAnotherCustomersGrants() {
        var entry = service.grant(operator.getEmail()); service.revoke(entry.id());
        var other = new CustomerStaffAccessEntity(); other.setId("foreign"); other.setCustomerId("customer-b");
        other.setUserId(operator.getId()); other.setStatus("ACTIVE"); grants.put(other.getId(), other);
        assertThat(service.list()).extracting(CustomerStaffGrantService.Summary::id).containsExactly(entry.id());
        assertThat(service.list()).extracting(CustomerStaffGrantService.Summary::status).containsExactly("REVOKED");
    }

    @Test void revokeDoesNotRequireRecipientToRemainEligibleSoOwnerCanRemoveStaleAccess() {
        var entry = service.grant(operator.getEmail()); operator.setStatus("DISABLED"); operator.setRole("PLATFORM_ADMIN");
        assertThat(service.revoke(entry.id()).status()).isEqualTo("REVOKED");
    }

    @Test void foreignAndMissingGrantIdsReturnIdenticalScopedNotFoundErrors() {
        var other = new CustomerStaffAccessEntity(); other.setId("foreign"); other.setCustomerId("customer-b");
        other.setUserId(operator.getId()); other.setStatus("ACTIVE"); grants.put(other.getId(), other);
        var missing = catchThrowableOfType(() -> service.revoke("missing"), ResponseStatusException.class);
        var foreign = catchThrowableOfType(() -> service.revoke("foreign"), ResponseStatusException.class);
        assertThat(missing.getStatusCode()).isEqualTo(NOT_FOUND); assertThat(foreign.getStatusCode()).isEqualTo(NOT_FOUND);
        assertThat(foreign.getReason()).isEqualTo(missing.getReason()); assertThat(other.getStatus()).isEqualTo("ACTIVE");
        verify(repository, never()).findById(anyString()); verifyNoInteractions(auditEvents);
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "CUSTOMER_ADMIN", "PLATFORM_ADMIN", "DISABLED", "bound-customer"})
    void unknownInactiveCustomerAndGlobalAdminEmailsShareOneUsefulConflict(String fault) {
        if (fault.equals("missing")) accounts.remove(operator.getId());
        else if (fault.equals("DISABLED")) operator.setStatus(fault);
        else if (fault.equals("bound-customer")) operator.setCustomerId("customer-b");
        else operator.setRole(fault);
        assertThatThrownBy(() -> service.grant(operator.getEmail())).isInstanceOfSatisfying(ResponseStatusException.class, error -> {
            assertThat(error.getStatusCode()).isEqualTo(CONFLICT);
            assertThat(error.getReason()).isEqualTo("Use the existing email of an active LoomAI staff operator account. No access was changed.");
        });
        verify(repository, never()).saveAndFlush(any()); verifyNoInteractions(auditEvents);
    }

    @ParameterizedTest @EnumSource(value = PlatformRole.class, names = "CUSTOMER_ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void realServiceScopeGuardRejectsEveryOtherRoleBeforeReadingGrants(PlatformRole role) {
        authenticate(role, "SESSION", operator);
        assertAllForbidden(); verifyNoInteractions(repository, em, auditEvents);
    }

    @ParameterizedTest @ValueSource(strings = {"API_KEY", "anonymous", "disabled-owner", "changed-role", "missing-customer", "disabled-customer", "missing-user", "supabase-missing-id"})
    void onlyActiveCustomerAdministratorSessionOrSupabaseCanManageStaff(String fault) {
        if (fault.equals("API_KEY")) authenticate(PlatformRole.CUSTOMER_ADMIN, "API_KEY", owner);
        if (fault.equals("anonymous")) SecurityContextHolder.clearContext();
        if (fault.equals("disabled-owner")) owner.setStatus("DISABLED");
        if (fault.equals("changed-role")) owner.setRole("PLATFORM_OPERATOR");
        if (fault.equals("missing-customer")) when(customers.findById("customer-a")).thenReturn(Optional.empty());
        if (fault.equals("disabled-customer")) customer.setStatus("DISABLED");
        if (fault.equals("missing-user")) accounts.remove(owner.getId());
        if (fault.equals("supabase-missing-id")) authenticate(new PlatformPrincipal(owner.getEmail(), PlatformRole.CUSTOMER_ADMIN, "Owner", "SUPABASE"));
        assertAllForbidden(); verifyNoInteractions(repository, em, auditEvents);
    }

    @Test void customerStateIsRecheckedAfterWaitingForTheWorkspaceLock() {
        doAnswer(call -> { customer.setStatus("DISABLED"); return null; }).when(em).refresh(customer);
        assertThatThrownBy(() -> service.grant(operator.getEmail())).isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(FORBIDDEN));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void supabaseOwnerUsesExistingMembershipScopeAndActualActorForAudit() {
        authenticate(PlatformRole.CUSTOMER_ADMIN, "SUPABASE", owner);
        var principal = PlatformSecurityContext.currentPrincipal(); var entry = service.grant(operator.getEmail());
        assertThat(entry.customerId()).isEqualTo("customer-a"); assertThat(PlatformSecurityContext.currentPrincipal()).isSameAs(principal);
        var event = ArgumentCaptor.forClass(PlatformAuditEventEntity.class); verify(auditEvents).save(event.capture());
        assertThat(event.getValue().getActorId()).isEqualTo(owner.getEmail());
    }

    private void assertAllForbidden() {
        for (Runnable action : List.<Runnable>of(service::list, () -> service.grant(operator.getEmail()), () -> service.revoke("grant"))) {
            assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(FORBIDDEN));
        }
    }
    private PlatformUserEntity account(String id, String email, String role, String customerId) {
        var user = new PlatformUserEntity(); user.setId(id); user.setEmail(email); user.setDisplayName(id);
        user.setRole(role); user.setStatus("ACTIVE"); user.setCustomerId(customerId); accounts.put(id, user); return user;
    }
    private void authenticate(PlatformRole role, String mode, PlatformUserEntity user) {
        authenticate(new PlatformPrincipal(user.getEmail(), role, user.getDisplayName(), mode, user.getId()));
    }
    private void authenticate(PlatformPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
            List.of(new SimpleGrantedAuthority(principal.role().authority()))));
    }
}
