package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.*;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.*;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerOfferEntitlementServiceTest {
    private final CustomerOfferRepository offers = mock(CustomerOfferRepository.class);
    private final CustomerOfferEntitlementRepository entitlements = mock(CustomerOfferEntitlementRepository.class);
    private final CustomerEntitlementCommandRepository commands = mock(CustomerEntitlementCommandRepository.class);
    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final PlatformCustomerRepository customers = mock(PlatformCustomerRepository.class);
    private final CustomerReviewedCompositionService composition = mock(CustomerReviewedCompositionService.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final Map<String, CustomerEntitlementCommandEntity> receipts = new HashMap<>();
    private CustomerOfferEntitlementService service;
    private CustomerOfferEntitlementEntity saved;
    private PlatformUserEntity owner;

    @BeforeEach
    void setup() {
        var codec = CustomerOfferTestFixtures.codec();
        var definition = CustomerOfferTestFixtures.definition();
        var offer = new CustomerOfferEntity("offer", codec.write(definition), codec.hash(definition), "review", "admin", Instant.now());
        when(offers.findByIdForUpdate("offer")).thenReturn(Optional.of(offer));
        when(offers.findById("offer")).thenReturn(Optional.of(offer));
        var customer = new PlatformCustomerEntity(); customer.setId("customer"); customer.setStatus("ACTIVE");
        when(customers.findById("customer")).thenReturn(Optional.of(customer));
        owner = new PlatformUserEntity(); owner.setId("owner"); owner.setEmail("owner@test.invalid");
        owner.setRole("CUSTOMER_ADMIN"); owner.setCustomerId("customer"); owner.setStatus("ACTIVE");
        when(users.findById("owner")).thenReturn(Optional.of(owner));
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
        when(entitlements.findForUpdate("customer", "offer")).thenAnswer(call -> Optional.ofNullable(saved));
        when(entitlements.findByIdForUpdate(anyString())).thenAnswer(call -> saved != null && saved.getId().equals(call.getArgument(0))
            ? Optional.of(saved) : Optional.empty());
        when(entitlements.saveAndFlush(any())).thenAnswer(call -> { saved = call.getArgument(0); return saved; });
        when(commands.findByEntitlementIdAndOperationKey(anyString(), anyString()))
            .thenAnswer(call -> Optional.ofNullable(receipts.get(call.getArgument(0) + ":" + call.getArgument(1))));
        when(commands.save(any())).thenAnswer(call -> {
            CustomerEntitlementCommandEntity command = call.getArgument(0);
            receipts.put(command.getEntitlementId() + ":" + command.getOperationKey(), command); return command;
        });
        service = new CustomerOfferEntitlementService(offers, entitlements, commands, new PlatformCustomerAccessService(users, customers),
            customers, users, codec, composition, mock(CustomerTargetPlacementService.class), mock(DeploymentTargetProfileRepository.class), audit);
        CustomerOfferTestFixtures.authenticate("admin-key", PlatformRole.PLATFORM_ADMIN, "API_KEY");
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void inviteAcceptRevokeAndReplayRetainDurableOriginalResultWithoutReactivation() {
        var invitation = invite();
        assertThat(invitation.status()).isEqualTo(EntitlementStatus.INVITED);
        assertThat(invite()).isEqualTo(invitation);
        CustomerOfferTestFixtures.authenticate(owner.getEmail(), PlatformRole.CUSTOMER_ADMIN, "SESSION");
        var accept = new EntitlementCommand(1, "accept-key", "Owner accepts reviewed invitation");
        var accepted = service.accept(invitation.id(), accept);
        assertThat(accepted.status()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(accepted.revision()).isEqualTo(2);
        assertThat(service.accept(invitation.id(), accept)).isEqualTo(accepted);
        CustomerOfferTestFixtures.authenticate("admin-key", PlatformRole.PLATFORM_ADMIN, "API_KEY");
        var revoke = new EntitlementCommand(2, "revoke-key", "Explicit offboarding");
        var revoked = service.revoke(invitation.id(), revoke);
        assertThat(revoked.revision()).isEqualTo(3);
        assertThat(service.revoke(invitation.id(), revoke)).isEqualTo(revoked);
        CustomerOfferTestFixtures.authenticate(owner.getEmail(), PlatformRole.CUSTOMER_ADMIN, "SESSION");
        assertThat(service.accept(invitation.id(), accept)).isEqualTo(accepted);
        assertThat(saved.getStatus()).isEqualTo(EntitlementStatus.REVOKED);
        assertThat(receipts).hasSize(3);
        verify(audit, times(3)).record(anyString(), anyString(), anyString(), anyMap());
    }

    @Test void keyPayloadActorAndCommandConflictsAreExplicit() {
        var invitation = invite();
        var different = new InviteRequest("customer", "owner", "reviewed-invite", Instant.parse("2099-01-01T00:00:00Z"), 0,
            "invite-key", "Changed payload");
        assertCode(OPERATION_KEY_CONFLICT, () -> service.invite("offer", different));
        assertCode(OPERATION_KEY_CONFLICT, () -> service.revoke(invitation.id(), new EntitlementCommand(1, "invite-key", "revoke")));
        CustomerOfferTestFixtures.authenticate("other-admin-key", PlatformRole.PLATFORM_ADMIN, "API_KEY");
        assertCode(OPERATION_KEY_CONFLICT, this::invite);
    }

    @Test void revokedInviteCannotBeAcceptedWithANewKeyAndStaleRevisionCannotGrant() {
        var invitation = invite();
        service.revoke(invitation.id(), new EntitlementCommand(1, "revoke", "Explicit revoke"));
        assertCode(REVISION_CONFLICT, () -> service.invite("offer", new InviteRequest("customer", "owner", "new-invite",
            Instant.parse("2099-01-01T00:00:00Z"), 1, "new-key", "renew")));
        CustomerOfferTestFixtures.authenticate(owner.getEmail(), PlatformRole.CUSTOMER_ADMIN, "SESSION");
        assertCode(OPERATION_FORBIDDEN, () -> service.accept(invitation.id(), new EntitlementCommand(2, "accept", "accept")));
    }

    @Test void customersOperatorsAndAnonymousCannotPublishGrantOrRevoke() {
        for (PlatformRole role : List.of(PlatformRole.CUSTOMER_ADMIN, PlatformRole.PLATFORM_OPERATOR, PlatformRole.PUBLIC_API_CLIENT)) {
            CustomerOfferTestFixtures.authenticate("actor", role, "SESSION");
            assertCode(STAFF_ADMIN_REQUIRED, this::invite);
        }
        clear(); assertCode(STAFF_ADMIN_REQUIRED, this::invite);
        verify(entitlements, never()).saveAndFlush(any());
    }

    @Test void foreignDisabledOwnerAndMissingInvitationPrerequisitesDeny() {
        owner.setCustomerId("foreign"); assertCode(INVITE_OWNER_MISMATCH, this::invite);
        owner.setCustomerId("customer"); owner.setStatus("DISABLED"); assertCode(INVITE_OWNER_MISMATCH, this::invite);
        owner.setStatus("ACTIVE");
        assertCode(INVALID_CONTRACT, () -> service.invite("offer", new InviteRequest("customer", "owner", "",
            Instant.parse("2099-01-01T00:00:00Z"), 0, "key", "reason")));
    }

    @Test void onlyNamedInvitedOwnerCanAcceptAndBrowserCustomerClaimCannotChangeScope() {
        var invitation = invite();
        owner.setId("another-owner");
        CustomerOfferTestFixtures.authenticate(owner.getEmail(), PlatformRole.CUSTOMER_ADMIN, "SESSION");
        assertCode(INVITE_OWNER_MISMATCH, () -> service.accept(invitation.id(), new EntitlementCommand(1, "accept", "accept")));
        owner.setId("owner");
        assertCode(ENTITLEMENT_REQUIRED, () -> service.accept("guessed-entitlement", new EntitlementCommand(1, "accept", "accept")));
    }

    @Test void anOfferCannotBeReplacedAtTheSameId() {
        when(offers.existsById("offer")).thenReturn(true);
        assertCode(OFFER_IMMUTABLE, () -> service.publish(new PublishOfferRequest("offer", CustomerOfferTestFixtures.definition(), "review")));
    }

    private EntitlementSummary invite() {
        return service.invite("offer", new InviteRequest("customer", "owner", "reviewed-invite",
            Instant.parse("2099-01-01T00:00:00Z"), 0, "invite-key", "Approved early access"));
    }
    private void assertCode(CustomerSelfServiceException.Code code, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(CustomerSelfServiceException.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }
}
