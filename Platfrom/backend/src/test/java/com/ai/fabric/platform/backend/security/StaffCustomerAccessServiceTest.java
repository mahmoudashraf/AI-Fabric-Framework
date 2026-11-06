package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.audit.entity.PlatformAuditEventEntity;
import com.ai.fabric.platform.backend.audit.repository.PlatformAuditEventRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentAssignmentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.entity.PlatformCustomerIdentityLinkEntity;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformCustomerIdentityLinkRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.*;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class StaffCustomerAccessServiceTest {
    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final PlatformCustomerRepository customers = mock(PlatformCustomerRepository.class);
    private final PlatformCustomerIdentityLinkRepository links = mock(PlatformCustomerIdentityLinkRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final PlatformAuditEventRepository auditEvents = mock(PlatformAuditEventRepository.class);
    private final PasswordEncoder passwords = spy(new BCryptPasswordEncoder(4));
    private final Map<String, PlatformUserEntity> accounts = new HashMap<>();
    private final PlatformCustomerEntity customer = new PlatformCustomerEntity();
    private StaffCustomerAccessService service;
    private PlatformPrincipal staff;

    @BeforeEach void setup() {
        customer.setId("customer-a"); customer.setName("Customer A"); customer.setSlug("customer-a"); customer.setStatus("ACTIVE");
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(em.find(PlatformCustomerEntity.class, customer.getId(), LockModeType.PESSIMISTIC_READ)).thenReturn(customer);
        when(users.findByEmailIgnoreCase(anyString())).thenAnswer(call -> Optional.ofNullable(accounts.get(call.getArgument(0, String.class).toLowerCase(Locale.ROOT))));
        when(users.findByEmailForRecovery(anyString())).thenAnswer(call -> Optional.ofNullable(accounts.get(call.getArgument(0))));
        when(users.save(any())).thenAnswer(call -> { var user = call.getArgument(0, PlatformUserEntity.class); accounts.put(user.getEmail(), user); return user; });
        var customerAccess = new PlatformCustomerAccessService(users, customers, mock(com.ai.fabric.platform.backend.security.service.CustomerStaffAccessPolicy.class));
        var audit = new PlatformAuditService(auditEvents, new ObjectMapper());
        var userAdmin = new PlatformUserAdminService(users, mock(DeploymentAssignmentRepository.class), mock(DeploymentRepository.class),
            passwords, audit, customers, customerAccess);
        // Exercise the real shared authorization methods, not a mocked requireStaffAdmin().
        var guards = new CustomerOfferEntitlementService(null, null, null, customerAccess, customers, users, null, null, null, null, audit);
        service = new StaffCustomerAccessService(guards, userAdmin, users, links, em, audit);
        accounts.put("staff@example.test", account("staff@example.test", PlatformRole.PLATFORM_ADMIN, null, "ACTIVE"));
        authenticate(PlatformRole.PLATFORM_ADMIN, "SESSION");
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void createsOnlyARealCustomerUserThroughExistingAdminServiceWithDiscardedRandomPassword() {
        var response = service.prepare("customer-a", request("  Recipient@Example.test  ", "  Recipient Name  "));
        var user = accounts.get("recipient@example.test");
        assertThat(response).isEqualTo(new StaffCustomerAccessService.PreparedAccess(user.getId(), "recipient@example.test", "Recipient Name", "customer-a", false, true));
        assertThat(user.getRole()).isEqualTo("CUSTOMER_ADMIN"); assertThat(user.getStatus()).isEqualTo("ACTIVE");
        assertThat(user.getCustomerId()).isEqualTo("customer-a");
        var raw = ArgumentCaptor.forClass(CharSequence.class); verify(passwords).encode(raw.capture());
        assertThat(raw.getValue().toString()).matches("[A-Za-z0-9_-]{43}");
        assertThat(passwords.matches(raw.getValue(), user.getPasswordHash())).isTrue();
        assertThat(PlatformSecurityContext.currentPrincipal()).isSameAs(staff);
        var events = ArgumentCaptor.forClass(PlatformAuditEventEntity.class); verify(auditEvents, times(2)).save(events.capture());
        assertThat(events.getAllValues()).allSatisfy(event -> {
            assertThat(event.getActorId()).isEqualTo(staff.actorId());
            assertThat(event.getActorRole()).isEqualTo("PLATFORM_ADMIN");
            assertThat(event.getDetailsJson()).doesNotContain(raw.getValue(), user.getPasswordHash());
        });
        assertThat(events.getAllValues()).extracting(PlatformAuditEventEntity::getAction)
            .containsExactly("PLATFORM_USER_CREATED", "STAFF_CUSTOMER_ACCESS_PREPARED");
        verifyNoInteractions(links);
    }

    @Test void repeatedPreparationReusesExistingUserWithoutRenamingResettingOrMovingIt() {
        var first = service.prepare("customer-a", request("recipient@example.test", "Original Name"));
        var password = accounts.get(first.email()).getPasswordHash();
        var replay = service.prepare("customer-a", request("RECIPIENT@example.test", "Different Name"));
        assertThat(replay.userId()).isEqualTo(first.userId()); assertThat(replay.created()).isFalse();
        assertThat(replay.displayName()).isEqualTo("Original Name"); assertThat(replay.identityLinked()).isFalse();
        assertThat(accounts.get(first.email()).getPasswordHash()).isEqualTo(password);
        verify(users, times(1)).save(any()); verify(passwords, times(1)).encode(any());
    }

    @Test void independentlyGeneratedPasswordsAreNotSharedBetweenRecipients() {
        service.prepare("customer-a", request("first@example.test", "First"));
        service.prepare("customer-a", request("second@example.test", "Second"));
        var raw = ArgumentCaptor.forClass(CharSequence.class); verify(passwords, times(2)).encode(raw.capture());
        assertThat(raw.getAllValues().get(0)).isNotEqualTo(raw.getAllValues().get(1));
    }

    @Test void existingActiveConsistentIdentityIsReportedWithoutRelinkingOrProviderCalls() {
        var user = member(); var link = link(user);
        when(links.findByPlatformUserId(user.getId())).thenReturn(Optional.of(link));
        when(links.findByIssuerAndSubject(link.getIssuer(), link.getSubject())).thenReturn(Optional.of(link));
        var response = service.prepare("customer-a", request(user.getEmail(), "Ignored Rename"));
        assertThat(response.identityLinked()).isTrue(); assertThat(response.created()).isFalse();
        verify(users, never()).save(any()); verify(links, never()).save(any());
        verify(em).lock(link, LockModeType.PESSIMISTIC_READ);
    }

    @ParameterizedTest @ValueSource(strings = {"other-customer", "staff", "operator", "disabled"})
    void existingIneligibleEmailsCannotBeReassignedReactivatedOrRepurposed(String fault) {
        var user = member();
        if (fault.equals("other-customer")) user.setCustomerId("customer-b");
        if (fault.equals("staff")) user.setRole("PLATFORM_ADMIN");
        if (fault.equals("operator")) user.setRole("PLATFORM_OPERATOR");
        if (fault.equals("disabled")) user.setStatus("DISABLED");
        expectStatus(CONFLICT, () -> service.prepare("customer-a", request(user.getEmail(), "Recipient")));
        verify(users, never()).save(any()); verifyNoInteractions(links, auditEvents, passwords);
    }

    @ParameterizedTest @ValueSource(strings = {"revoked", "missing-subject", "missing-issuer", "foreign-user", "canonical-missing", "canonical-other"})
    void revokedOrConflictingLinksAreNeverRepairedOrReactivated(String fault) {
        var user = member(); var link = link(user);
        if (fault.equals("revoked")) link.setRevokedAt(Instant.now());
        if (fault.equals("missing-subject")) link.setSubject("");
        if (fault.equals("missing-issuer")) link.setIssuer("");
        if (fault.equals("foreign-user")) link.setPlatformUserId("foreign-user");
        when(links.findByPlatformUserId(user.getId())).thenReturn(Optional.of(link));
        if (fault.equals("canonical-other")) {
            var other = link(user); other.setId("other-link");
            when(links.findByIssuerAndSubject(link.getIssuer(), link.getSubject())).thenReturn(Optional.of(other));
        }
        expectStatus(CONFLICT, () -> service.prepare("customer-a", request(user.getEmail(), "Recipient")));
        verify(users, never()).save(any()); verify(links, never()).save(any()); verifyNoInteractions(auditEvents, passwords);
    }

    @ParameterizedTest @EnumSource(value = PlatformRole.class, names = "PLATFORM_ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void actualServiceGuardDeniesEveryOtherRoleBeforeReadingCustomer(PlatformRole role) {
        authenticate(role, "SESSION");
        assertThatThrownBy(() -> service.prepare("customer-a", request("recipient@example.test", "Recipient")))
            .isInstanceOf(CustomerSelfServiceException.class);
        verifyNoInteractions(em, auditEvents, links, passwords);
    }

    @ParameterizedTest @ValueSource(strings = {"anonymous", "missing-user", "disabled-admin", "changed-role", "supabase-mode"})
    void actualServiceGuardRejectsMissingOrInactiveStaffEvenWithAnAdminPrincipal(String fault) {
        if (fault.equals("anonymous")) SecurityContextHolder.clearContext();
        if (fault.equals("missing-user")) accounts.clear();
        if (fault.equals("disabled-admin")) accounts.get(staff.actorId()).setStatus("DISABLED");
        if (fault.equals("changed-role")) accounts.get(staff.actorId()).setRole("PLATFORM_OPERATOR");
        if (fault.equals("supabase-mode")) authenticate(PlatformRole.PLATFORM_ADMIN, "SUPABASE");
        assertThatThrownBy(() -> service.prepare("customer-a", request("recipient@example.test", "Recipient")))
            .isInstanceOf(CustomerSelfServiceException.class);
        verifyNoInteractions(em, auditEvents, links, passwords);
    }

    @Test void trustedAdminApiKeysRetainExistingSharedStaffGuardSemantics() {
        authenticate(PlatformRole.PLATFORM_ADMIN, "API_KEY"); accounts.remove(staff.actorId());
        assertThat(service.prepare("customer-a", request("recipient@example.test", "Recipient")).created()).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"DISABLED", "platform-managed", "missing"})
    void targetCustomerMustExistAndBeAnActiveNonInternalWorkspace(String fault) {
        if (fault.equals("missing")) when(em.find(PlatformCustomerEntity.class, "customer-a", LockModeType.PESSIMISTIC_READ)).thenReturn(null);
        else if (fault.equals("platform-managed")) customer.setPlatformManaged(true);
        else customer.setStatus(fault);
        expectStatus(fault.equals("missing") ? NOT_FOUND : CONFLICT, () -> service.prepare("customer-a", request("recipient@example.test", "Recipient")));
        verify(users, never()).findByEmailForRecovery(anyString()); verify(users, never()).save(any());
        verifyNoInteractions(links, auditEvents, passwords);
    }

    private StaffCustomerAccessService.Request request(String email, String name) { return new StaffCustomerAccessService.Request(email, name); }
    private PlatformUserEntity member() {
        var user = account("recipient@example.test", PlatformRole.CUSTOMER_ADMIN, "customer-a", "ACTIVE");
        accounts.put(user.getEmail(), user); return user;
    }
    private PlatformUserEntity account(String email, PlatformRole role, String customerId, String status) {
        var user = new PlatformUserEntity(); user.setId("user-" + UUID.randomUUID()); user.setEmail(email);
        user.setDisplayName("Existing Name"); user.setRole(role.name()); user.setCustomerId(customerId); user.setStatus(status);
        user.setPasswordHash("unchanged-hash"); return user;
    }
    private PlatformCustomerIdentityLinkEntity link(PlatformUserEntity user) {
        var link = new PlatformCustomerIdentityLinkEntity(); link.setId("link"); link.setPlatformUserId(user.getId());
        link.setIssuer("https://auth.example.test/auth/v1"); link.setSubject("recipient-subject"); return link;
    }
    private void authenticate(PlatformRole role, String mode) {
        staff = new PlatformPrincipal("staff@example.test", role, "Staff", mode);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(staff, null,
            List.of(new SimpleGrantedAuthority(role.authority()))));
    }
    private void expectStatus(org.springframework.http.HttpStatus status, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(status));
    }
}
