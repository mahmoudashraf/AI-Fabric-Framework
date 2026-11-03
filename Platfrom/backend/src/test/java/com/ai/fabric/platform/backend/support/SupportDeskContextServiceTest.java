package com.ai.fabric.platform.backend.support;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static com.ai.fabric.platform.backend.support.SupportDeskContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContextHolder;

class SupportDeskContextServiceTest {
  @AfterEach
  void logout() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void lookupUsesActualSupportAgentAndProviderTicketWithoutConsoleAgentMembershipOrMutation()
      throws Exception {
    var f = new SupportDeskFixture();
    var saved = f.create(false);
    var input = f.lookup(saved.id());
    SecurityContextHolder.clearContext();
    var result = f.contexts.handle(input, f.deskHeaders());
    assertThat(result.scope()).isEqualTo(input.scope());
    assertThat(result.state()).isEqualTo(State.AVAILABLE);
    assertThat(result.context().reference()).isEqualTo(saved.reference());
    assertThat(result.context().consent().status()).isEqualTo("DECLINED");
    assertThat(result.context().observations()).isEmpty();
    assertThat(result.context().resolveAction()).isNull();
    verify(f.desk).get(eq(f.connection), contains("permission=SUPPORT"));
    assertThat(f.json.writeValueAsString(result.context()))
        .doesNotContain("requester", "initialMessage", "subject-alice", "actual-agent-token");
  }

  @Test
  void resolveIsRetiredAndCannotMutateProviderOrConsent() {
    var f = new SupportDeskFixture();
    var saved = f.create(true);
    var input = f.resolve(f.lookup(saved.id()), 0, "legacy-resolve");
    clearInvocations(f.ticketClient);
    assertThatThrownBy(() -> f.contexts.handle(input, f.deskHeaders()))
        .hasMessageContaining("READ_ONLY_CONTEXT");
    assertThat(f.service.contextView(saved.id()).consent().granted()).isTrue();
    verify(f.ticketClient, never()).request(any(), eq("POST"), anyString(), any(), any());
    assertThat(f.remoteTickets.get(saved.id()).path("status").asText()).isEqualTo("OPEN");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Authorization", "X-LoomAI-Desk-Service-Key", "X-LoomAI-Desk-Binding-Id"})
  void credentialAgentTokenAndBindingAreMandatoryUnambiguousAndNotForgeable(String name) {
    var f = new SupportDeskFixture();
    var input = f.lookup(f.create(false).id());
    var missing = f.deskHeaders();
    missing.remove(name);
    assertThatThrownBy(() -> f.contexts.handle(input, missing))
        .hasMessageContaining("SUPPORT_ACCESS_DENIED");
    var duplicate = f.deskHeaders();
    duplicate.add(name, duplicate.getFirst(name));
    assertThatThrownBy(() -> f.contexts.handle(input, duplicate))
        .hasMessageContaining("SUPPORT_ACCESS_DENIED");
    var forged = f.deskHeaders();
    forged.set(name, "forged");
    assertThatThrownBy(() -> f.contexts.handle(input, forged))
        .hasMessageContaining("SUPPORT_ACCESS_DENIED");
  }

  @Test
  void actorMismatchExpiryIssuerAudienceAndRevokedSupportDenyBeforeTicketRead() {
    var f = new SupportDeskFixture();
    var input = f.lookup(f.create(false).id());
    var changed =
        new Request(
            1,
            Operation.LOOKUP,
            input.scope(),
            new Actor(input.actor().issuer(), "foreign-agent", "desk-agent-1"),
            null);
    assertThatThrownBy(() -> f.contexts.handle(changed, f.deskHeaders()))
        .hasMessageContaining("SUPPORT_ACCESS_DENIED");
    for (var jwt :
        List.of(
            SupportDeskFixture.jwt(
                SupportDeskFixture.ISSUER,
                SupportDeskFixture.SUBJECT,
                "authenticated",
                Instant.now().minusSeconds(1)),
            SupportDeskFixture.jwt(
                SupportDeskFixture.ISSUER,
                SupportDeskFixture.SUBJECT,
                "wrong",
                Instant.now().plusSeconds(60)),
            SupportDeskFixture.jwt(
                "https://wrong.example",
                SupportDeskFixture.SUBJECT,
                "authenticated",
                Instant.now().plusSeconds(60)))) {
      when(f.verifiedIdentities.verifyIdentity("actual-agent-token")).thenReturn(jwt);
      assertThatThrownBy(() -> f.contexts.handle(input, f.deskHeaders()))
          .hasMessageContaining("SUPPORT_ACCESS_DENIED");
    }
    when(f.verifiedIdentities.verifyIdentity("actual-agent-token"))
        .thenReturn(
            SupportDeskFixture.jwt(
                SupportDeskFixture.ISSUER,
                SupportDeskFixture.SUBJECT,
                "authenticated",
                Instant.now().plusSeconds(60)));
    when(f.desk.get(eq(f.connection), anyString()))
        .thenReturn(f.json.createObjectNode().put("authorized", false));
    clearInvocations(f.ticketClient);
    assertThatThrownBy(() -> f.contexts.handle(input, f.deskHeaders()))
        .hasMessageContaining("SUPPORT_ACCESS_DENIED");
    verify(f.ticketClient, never()).request(any(), anyString(), anyString(), any(), any());
  }

  @Test
  void fixedBindingAndExactConversationAreNotUntrustedSelectionAuthority() {
    var f = new SupportDeskFixture();
    var input = f.lookup(f.create(false).id());
    var other = f.lookup(f.create(false).id());
    var s = input.scope();
    var foreign =
        new Scope(
            s.bindingId(),
            "customer-a",
            s.tenantId(),
            s.deploymentId(),
            s.targetProfileId(),
            s.handoverId(),
            s.conversationId(),
            s.ownerRef(),
            s.externalConversationRef());
    assertThatThrownBy(
            () ->
                f.contexts.handle(
                    new Request(1, Operation.LOOKUP, foreign, input.actor(), null),
                    f.deskHeaders()))
        .hasMessageContaining("SUPPORT_ACCESS_DENIED");
    var mixed =
        new Scope(
            s.bindingId(),
            s.customerId(),
            s.tenantId(),
            s.deploymentId(),
            s.targetProfileId(),
            s.handoverId(),
            other.scope().conversationId(),
            s.ownerRef(),
            s.externalConversationRef());
    var result =
        f.contexts.handle(
            new Request(1, Operation.LOOKUP, mixed, input.actor(), null), f.deskHeaders());
    assertThat(result.state()).isEqualTo(State.NOT_LINKED);
    assertThat(result.scope()).isEqualTo(mixed);
    assertThat(result.context()).isNull();
    f.binding.setTenantId("wrong-tenant");
    assertThatThrownBy(() -> f.contexts.handle(input, f.deskHeaders()))
        .hasMessageContaining("SUPPORT_NOT_CONFIGURED");
  }

  @Test
  void requesterMembershipResourcesAndConsentAreRecheckedForReadOnlyObservations() {
    var f = new SupportDeskFixture();
    var d = SupportFixture.deployment("selected", "customer-a", "tenant-a");
    when(f.deployments.findById(d.getId())).thenReturn(Optional.of(d));
    var saved = f.create(false, new ResourceRef(ResourceType.DEPLOYMENT, d.getId()));
    var input = f.lookup(saved.id());
    assertThat(f.contexts.handle(input, f.deskHeaders()).context().observations()).isEmpty();
    verifyNoInteractions(f.releases);
    f.service.consent(saved.id(), new ConsentChange(true, saved.consent().version()));
    assertThat(f.contexts.handle(input, f.deskHeaders()).context().observations()).hasSize(1);
    f.directory.get("alice").setStatus("DISABLED");
    assertThatThrownBy(() -> f.contexts.handle(input, f.deskHeaders()))
        .hasMessageContaining("SUPPORT_ACCESS_DENIED");
    f.directory.get("alice").setStatus("ACTIVE");
    d.setCustomerId("customer-b");
    assertThatThrownBy(() -> f.contexts.handle(input, f.deskHeaders()))
        .hasMessageContaining("SUPPORT_ACCESS_DENIED");
  }

  @Test
  void diagnosticCredentialCannotReplaceContextCredential() {
    var f = new SupportDeskFixture();
    var input = f.lookup(f.create(false).id());
    when(f.secrets.resolveSecret("PLATFORM_MANAGED_SUPPORT_DESK_CONTEXT"))
        .thenReturn("dedicated-diagnostic-test-key");
    assertThatThrownBy(() -> f.contexts.handle(input, f.deskHeaders()))
        .hasMessageContaining("SUPPORT_NOT_CONFIGURED");
    f.env.setProperty(
        "platform.support.desk.context-service-key-secret-name",
        "PLATFORM_MANAGED_SUPPORT_DIAGNOSTICS");
    assertThatThrownBy(() -> f.contexts.handle(input, f.deskHeaders()))
        .hasMessageContaining("SUPPORT_NOT_CONFIGURED");
  }
}
