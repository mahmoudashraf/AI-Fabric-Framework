package com.ai.fabric.platform.backend.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static com.ai.fabric.platform.backend.support.SupportDeskContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupportDeskContextServiceTest {
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void lookupUsesActualAgentAndExactScopeAndDoesNotDependOnConsoleMembershipForAgent() throws Exception {
        var f = new SupportDeskFixture(); var c = f.create(false); var input = f.lookup(c.id());
        SecurityContextHolder.clearContext();
        var response = f.contexts.handle(input, f.deskHeaders());
        assertThat(response.schemaVersion()).isEqualTo(1); assertThat(response.scope()).isEqualTo(input.scope());
        assertThat(response.state()).isEqualTo(State.AVAILABLE);
        assertThat(response.context().reference()).isEqualTo(c.reference());
        assertThat(response.context().consent().status()).isEqualTo("DECLINED");
        assertThat(response.context().observations()).isEmpty();
        assertThat(response.context().resolveAction()).isNotNull();
        verify(f.desk).get(eq(f.connection), contains("permission=SUPPORT"));
        var audit = f.jdbc.queryForMap("select actor_issuer,actor_subject,actor_agent_id,operation,response_json from platform_support_desk_operations");
        assertThat(audit).containsEntry("ACTOR_ISSUER", SupportDeskFixture.ISSUER).containsEntry("ACTOR_SUBJECT", SupportDeskFixture.SUBJECT)
            .containsEntry("ACTOR_AGENT_ID", "desk-agent-1").containsEntry("OPERATION", "LOOKUP").containsEntry("RESPONSE_JSON", null);
        assertThat(f.json.writeValueAsString(response.context())).doesNotContain("requester", "initialMessage", "subject-alice", "actual-agent-token");
    }

    @Test void resolvesWithVersionAndStableReceiptWithoutClosingConversationOrImpersonatingRequester() {
        var f = new SupportDeskFixture(); var c = f.create(true); var lookup = f.lookup(c.id());
        var input = f.resolve(lookup, c.revision(), "resolve-once");
        var first = f.contexts.handle(input, f.deskHeaders());
        assertThat(first.scope()).isEqualTo(lookup.scope());
        assertThat(first.context().status()).isEqualTo("RESOLVED");
        assertThat(first.context().version()).isEqualTo(2);
        assertThat(first.context().consent().status()).isEqualTo("REVOKED");
        assertThat(first.context().resolveAction()).isNull();
        assertThat(first.context().observations()).isEmpty();
        assertThat(f.contexts.handle(input, f.deskHeaders())).isEqualTo(first);
        assertThat(f.jdbc.queryForObject("select count(*) from platform_support_desk_operations", Integer.class)).isEqualTo(1);
        assertThat(f.store.episodes(c.id()).getFirst().terminal()).isFalse();
        assertThat(f.jdbc.queryForObject("select actor_user_id from platform_support_case_events where event_type='DESK_RESOLVED'", String.class)).isNull();
        assertThatThrownBy(() -> f.contexts.handle(f.resolve(lookup, 99, "resolve-once"), f.deskHeaders())).hasMessageContaining("IDEMPOTENCY_CONFLICT");
        assertThatThrownBy(() -> f.contexts.handle(f.resolve(lookup, 0, "another-key"), f.deskHeaders())).hasMessageContaining("REVISION_CONFLICT");
    }

    @ParameterizedTest @ValueSource(strings = {"Authorization", "X-LoomAI-Desk-Service-Key", "X-LoomAI-Desk-Binding-Id"})
    void serviceCredentialAgentTokenAndBindingAreAllMandatoryAndMustBeUnambiguous(String name) {
        var f = new SupportDeskFixture(); var input = f.lookup(f.create(false).id());
        var missing = f.deskHeaders(); missing.remove(name);
        assertThatThrownBy(() -> f.contexts.handle(input, missing)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        var duplicate = f.deskHeaders(); duplicate.add(name, duplicate.getFirst(name));
        assertThatThrownBy(() -> f.contexts.handle(input, duplicate)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        var forged = f.deskHeaders(); forged.set(name, "forged");
        assertThatThrownBy(() -> f.contexts.handle(input, forged)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
    }

    @Test void mismatchedActorExpiredWrongAudienceAndWrongIssuerJwtAreRejected() {
        var f = new SupportDeskFixture(); var input = f.lookup(f.create(false).id());
        var mismatch = new Request(1, Operation.LOOKUP, input.scope(), new Actor(input.actor().issuer(), "another-agent", "desk-agent-1"), null);
        assertThatThrownBy(() -> f.contexts.handle(mismatch, f.deskHeaders())).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        for (var jwt : new org.springframework.security.oauth2.jwt.Jwt[] {
            SupportDeskFixture.jwt(SupportDeskFixture.ISSUER, SupportDeskFixture.SUBJECT, "authenticated", Instant.now().minusSeconds(1)),
            SupportDeskFixture.jwt(SupportDeskFixture.ISSUER, SupportDeskFixture.SUBJECT, "wrong-audience", Instant.now().plusSeconds(60)),
            SupportDeskFixture.jwt("https://wrong.example.test/auth/v1", SupportDeskFixture.SUBJECT, "authenticated", Instant.now().plusSeconds(60))}) {
            when(f.verifiedIdentities.verifyIdentity("actual-agent-token")).thenReturn(jwt);
            assertThatThrownBy(() -> f.contexts.handle(input, f.deskHeaders())).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        }
    }

    @Test void ownerReviewerOrRevokedSupportPermissionDoNotGrantContextOrReceiptReplay() {
        var f = new SupportDeskFixture(); var c = f.create(false); var input = f.resolve(f.lookup(c.id()), c.revision(), "receipt");
        f.contexts.handle(input, f.deskHeaders());
        when(f.desk.get(eq(f.connection), anyString())).thenReturn(f.json.createObjectNode().put("authorized", false));
        assertThatThrownBy(() -> f.contexts.handle(input, f.deskHeaders())).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        assertThatThrownBy(() -> f.contexts.intake(f.deskHeaders(), 0, 25)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
    }

    @Test void fixedBindingOwnershipAndPayloadAreNotCustomerOrCaseSelectionAuthority() {
        var f = new SupportDeskFixture(); var input = f.lookup(f.create(false).id()); var s = input.scope();
        var foreign = new Scope(s.bindingId(), "customer-a", s.tenantId(), s.deploymentId(), s.targetProfileId(), s.handoverId(), s.conversationId(), s.ownerRef(), s.externalConversationRef());
        assertThatThrownBy(() -> f.contexts.handle(new Request(1, Operation.LOOKUP, foreign, input.actor(), null), f.deskHeaders())).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        f.binding.setTenantId("wrong-tenant");
        assertThatThrownBy(() -> f.contexts.handle(input, f.deskHeaders())).hasMessageContaining("SUPPORT_NOT_CONFIGURED");
    }

    @Test void unlinkedConversationReturnsNotLinkedWithExactScopeNeverAnotherCase() {
        var f = new SupportDeskFixture(); var input = f.lookup(f.create(false).id()); var other = f.lookup(f.create(false).id()); var s = input.scope();
        var mixed = new Scope(s.bindingId(), s.customerId(), s.tenantId(), s.deploymentId(), s.targetProfileId(), s.handoverId(), other.scope().conversationId(), s.ownerRef(), s.externalConversationRef());
        var response = f.contexts.handle(new Request(1, Operation.LOOKUP, mixed, input.actor(), null), f.deskHeaders());
        assertThat(response.scope()).isEqualTo(mixed); assertThat(response.state()).isEqualTo(State.NOT_LINKED); assertThat(response.context()).isNull();
    }

    @Test void diagnosticSecretCannotBeUsedAsContextSecretAndReadOnlyProviderCannotResolve() {
        var f = new SupportDeskFixture(); var c = f.create(false); var lookup = f.lookup(c.id());
        f.env.setProperty("platform.support.desk.resolve-enabled", "false");
        assertThat(f.contexts.handle(lookup, f.deskHeaders()).context().resolveAction()).isNull();
        assertThatThrownBy(() -> f.contexts.handle(f.resolve(lookup, 0, "read-only"), f.deskHeaders())).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        when(f.secrets.resolveSecret("PLATFORM_MANAGED_SUPPORT_DESK_CONTEXT")).thenReturn("dedicated-diagnostic-test-key");
        assertThatThrownBy(() -> f.contexts.handle(lookup, f.deskHeaders())).hasMessageContaining("SUPPORT_NOT_CONFIGURED");
        f.env.setProperty("platform.support.desk.context-service-key-secret-name", "PLATFORM_MANAGED_SUPPORT_DIAGNOSTICS");
        assertThatThrownBy(() -> f.contexts.handle(lookup, f.deskHeaders())).hasMessageContaining("SUPPORT_NOT_CONFIGURED");
    }

    @Test void requesterMembershipResourcesAndConsentAreRecheckedWithoutTreatingDeclinedConsentAsNoSupport() {
        var f = new SupportDeskFixture(); var d = SupportFixture.deployment("selected-dep", "customer-a", "tenant-a");
        when(f.deployments.findById(d.getId())).thenReturn(Optional.of(d));
        var c = f.create(false, new ResourceRef(ResourceType.DEPLOYMENT, d.getId())); var input = f.lookup(c.id());
        assertThat(f.contexts.handle(input, f.deskHeaders()).context().observations()).isEmpty();
        verifyNoInteractions(f.releases);
        f.service.consent(c.id(), new ConsentChange(true, c.consent().version()));
        assertThat(f.contexts.handle(input, f.deskHeaders()).context().observations()).hasSize(1);
        f.directory.get("alice").setStatus("DISABLED");
        assertThatThrownBy(() -> f.contexts.handle(input, f.deskHeaders())).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        f.directory.get("alice").setStatus("ACTIVE"); d.setCustomerId("customer-b");
        assertThatThrownBy(() -> f.contexts.handle(input, f.deskHeaders())).hasMessageContaining("SUPPORT_ACCESS_DENIED");
    }

    @Test void minimalStaffIntakeShowsUndeliveredRequestsWithoutAServiceSecretOrNewStaffAccount() {
        var f = new SupportDeskFixture(); var pending = f.create(false); var completed = f.create(false); var resolved = f.create(false);
        var first = f.service.initialMessageForDelivery(completed.id());
        f.service.observeInitialDelivery(completed.id(), first.episodeId(), DeliveryStatus.COMPLETED);
        f.service.resolve(resolved.id(), resolved.revision());
        var headers = new HttpHeaders(); headers.setBearerAuth("actual-agent-token"); SecurityContextHolder.clearContext();
        var items = f.contexts.intake(headers, 0, 25);
        assertThat(items).hasSize(1); assertThat(items.getFirst().caseId()).isEqualTo(pending.id());
        assertThat(items.getFirst().details()).isEqualTo("The app is not ready.");
        assertThat(items.getFirst().initialMessageDelivery()).isEqualTo("PENDING");
    }

    @Test void placeholderOrForeignTenantConfigurationNeverBecomesSourceAuthority() {
        var f = new SupportDeskFixture(); f.env.setProperty("platform.support.tenant-id", "${NOT_DEFINED}");
        assertThatThrownBy(() -> f.create(false)).hasMessageContaining("SUPPORT_NOT_CONFIGURED");
        f.env.setProperty("platform.support.tenant-id", "customer-a-tenant");
        assertThatThrownBy(() -> f.create(false)).hasMessageContaining("SUPPORT_NOT_CONFIGURED");
    }

    @Test void failedProjectionRollsBackCaseResolutionConsentAndReceiptRatherThanClaimingSuccess() {
        var f = new SupportDeskFixture(); var c = f.create(true); var input = f.resolve(f.lookup(c.id()), 0, "projection-failure");
        var customer = new com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity();
        customer.setId("customer-a"); customer.setStatus("ACTIVE"); customer.setName("x".repeat(161));
        when(f.customers.findById("customer-a")).thenReturn(Optional.of(customer));
        assertThatThrownBy(() -> f.contexts.handle(input, f.deskHeaders())).hasMessageContaining("CONTEXT_NOT_REPRESENTABLE");
        var unchanged = f.store.find(c.id(), false).orElseThrow();
        assertThat(unchanged.status()).isEqualTo(CaseStatus.OPEN); assertThat(unchanged.revision()).isZero();
        assertThat(unchanged.consent().granted()).isTrue();
        assertThat(f.jdbc.queryForObject("select count(*) from platform_support_desk_operations", Integer.class)).isZero();
        assertThat(f.jdbc.queryForObject("select count(*) from platform_support_case_events where event_type='DESK_RESOLVED'", Integer.class)).isZero();
    }
}
