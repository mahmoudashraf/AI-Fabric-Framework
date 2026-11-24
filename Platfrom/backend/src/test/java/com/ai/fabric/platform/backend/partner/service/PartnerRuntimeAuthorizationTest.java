package com.ai.fabric.platform.backend.partner.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.partner.entity.*;
import com.ai.fabric.platform.backend.partner.repository.*;
import com.ai.fabric.platform.backend.partner.security.PartnerPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartnerRuntimeAuthorizationTest {
    PartnerMemberRepository members = mock(PartnerMemberRepository.class);
    PartnerAccountRepository accounts = mock(PartnerAccountRepository.class);
    PartnerStoreAssignmentRepository assignments = mock(PartnerStoreAssignmentRepository.class);
    ShopifyStoreConnectionRepository stores = mock(ShopifyStoreConnectionRepository.class);
    PartnerRuntimeAuthorization service = new PartnerRuntimeAuthorization(members, accounts, assignments, stores, new ObjectMapper());
    DeploymentEntity deployment = new DeploymentEntity();
    PartnerMemberEntity member = new PartnerMemberEntity();
    PartnerAccountEntity account = new PartnerAccountEntity();
    PartnerStoreAssignmentEntity assignment = new PartnerStoreAssignmentEntity();
    ShopifyStoreConnectionEntity store = new ShopifyStoreConnectionEntity();

    @BeforeEach void fixture() {
        deployment.setId("brain-a"); deployment.setCustomerId("customer-a"); deployment.setTenantId("tenant-a");
        member.setId("member-a"); member.setPartnerAccountId("account-a"); member.setSupabaseUserId("verified-user"); member.setStatus("ACTIVE");
        account.setId("account-a"); account.setStatus("ACTIVE");
        store.setId("store-a"); store.setDeploymentId("brain-a"); store.setCustomerId("customer-a");
        store.setShopDomain("shop.example"); store.setInstallStatus("INSTALLED");
        assignment.setId("assignment-a"); assignment.setPartnerAccountId("account-a"); assignment.setStoreConnectionId("store-a");
        assignment.setShopDomain("shop.example"); assignment.setStatus("ACTIVE"); assignment.setPermissionsJson("[\"PRODUCT_CONFIG_READ\"]");
        when(members.findById("member-a")).thenReturn(Optional.of(member));
        when(accounts.findById("account-a")).thenReturn(Optional.of(account));
        when(stores.findByDeploymentId("brain-a")).thenReturn(Optional.of(store));
        when(assignments.findByPartnerAccountIdAndStoreConnectionId("account-a", "store-a")).thenReturn(Optional.of(assignment));
        authenticate("member-a", "verified-user", "account-a", true);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void approvedStoreHasStableMemberSpecificSessionWithoutConsolePermissions() {
        var first = service.authorize(deployment);
        assertThat(service.authorize(deployment)).isEqualTo(first);
        assertThat(first.subjectId()).isEqualTo("partner-member:member-a");
        member.setId("member-b"); when(members.findById("member-b")).thenReturn(Optional.of(member));
        authenticate("member-b", "verified-user", "account-a", true);
        assertThat(service.authorize(deployment).sessionId()).isNotEqualTo(first.sessionId());
    }

    @ParameterizedTest @ValueSource(strings = {"NO_IDENTITY", "UNVERIFIED", "FOREIGN_SUBJECT", "FOREIGN_ACCOUNT",
        "INACTIVE_MEMBER", "INACTIVE_ACCOUNT", "NO_STORE", "UNINSTALLED", "FOREIGN_CUSTOMER", "NO_ASSIGNMENT",
        "REVOKED", "SUSPENDED", "FOREIGN_SHOP", "NO_PERMISSION", "INVALID_PERMISSIONS"})
    void cannotCrossTheExistingStoreDelegationBoundary(String boundary) {
        switch (boundary) {
            case "NO_IDENTITY" -> SecurityContextHolder.clearContext();
            case "UNVERIFIED" -> authenticate("member-a", "verified-user", "account-a", false);
            case "FOREIGN_SUBJECT" -> authenticate("member-a", "other-user", "account-a", true);
            case "FOREIGN_ACCOUNT" -> authenticate("member-a", "verified-user", "other-account", true);
            case "INACTIVE_MEMBER" -> member.setStatus("SUSPENDED");
            case "INACTIVE_ACCOUNT" -> account.setStatus("SUSPENDED");
            case "NO_STORE" -> when(stores.findByDeploymentId("brain-a")).thenReturn(Optional.empty());
            case "UNINSTALLED" -> store.setInstallStatus("UNINSTALLED");
            case "FOREIGN_CUSTOMER" -> store.setCustomerId("customer-b");
            case "NO_ASSIGNMENT" -> when(assignments.findByPartnerAccountIdAndStoreConnectionId("account-a", "store-a")).thenReturn(Optional.empty());
            case "REVOKED" -> assignment.setRevokedAt(Instant.now());
            case "SUSPENDED" -> assignment.setStatus("SUSPENDED");
            case "FOREIGN_SHOP" -> assignment.setShopDomain("other.example");
            case "NO_PERMISSION" -> assignment.setPermissionsJson("[\"SUPPORT_MANAGE\"]");
            case "INVALID_PERMISSIONS" -> assignment.setPermissionsJson("not json");
            default -> throw new AssertionError(boundary);
        }
        assertThatThrownBy(() -> service.authorize(deployment)).isInstanceOf("NO_IDENTITY".equals(boundary)
            ? com.ai.fabric.platform.backend.partner.security.PartnerUnauthorizedException.class
            : com.ai.fabric.platform.backend.partner.security.PartnerForbiddenException.class);
    }

    private void authenticate(String memberId, String subject, String accountId, boolean verified) {
        var principal = new PartnerPrincipal(subject, "partner@example.test", verified, "Partner", null,
            "email", subject, accountId, memberId, PlatformRole.PARTNER_ADMIN, "ACTIVE");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, "unused", List.of()));
    }
}
