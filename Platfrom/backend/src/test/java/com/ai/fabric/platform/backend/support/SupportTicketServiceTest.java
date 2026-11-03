package com.ai.fabric.platform.backend.support;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ai.fabric.platform.backend.security.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SupportTicketServiceTest {
  @AfterEach
  void logout() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void createDelegatesIdempotencyAndOpaqueVersionWithoutStoringTicketPayload() {
    var f = new SupportFixture();
    var input = new SupportTicketService.Create("Help", "Original details", "setup", null);
    var first = f.service.create(input, "one-key");
    var second = f.service.create(input, "one-key");
    assertThat(second).isEqualTo(first);
    verify(f.ticketClient, times(2))
        .request(f.requesters.current(), "POST", "/requests", input, "one-key");
    String id = first.path("id").asText();
    assertThat(f.contextStore.find("support-binding", id, false)).isPresent();
    assertThat(
            f.jdbc.queryForObject(
                "select count(*) from platform_support_integration_context", Integer.class))
        .isEqualTo(1);
    assertThat(f.jdbc.queryForMap("select * from platform_support_integration_context"))
        .doesNotContainKeys("SUMMARY", "DETAILS", "STATUS", "INITIAL_MESSAGE", "VERSION");
    assertThat(first.path("version").asText()).isEqualTo("W/\"0\"");
    assertThatThrownBy(
            () ->
                f.service.create(
                    new SupportTicketService.Create("Changed", "details", null, null), "one-key"))
        .hasMessageContaining("IDEMPOTENCY_CONFLICT");
  }

  @Test
  void providerReadsRemainRequesterScopedAcrossSameCompanyAdminsAndEmailChanges() {
    var f = new SupportFixture();
    var saved = f.create(false);
    f.login("bob");
    assertThat(f.service.list(null, null, 20).path("items")).isEmpty();
    assertThatThrownBy(() -> f.service.get(saved.id()))
        .hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
    assertThatThrownBy(() -> f.service.context(saved.id(), false))
        .hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
    f.login("eve");
    assertThatThrownBy(() -> f.service.get(saved.id()))
        .hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
    f.directory.get("alice").setEmail("changed@example.test");
    f.login("alice");
    assertThat(f.service.get(saved.id()).path("id").asText()).isEqualTo(saved.id());
    assertThat(f.service.context(saved.id(), false).requester().subject())
        .isEqualTo("subject-alice");
  }

  @Test
  void inactiveRevokedReboundAndApiKeyIdentitiesCannotAcquireStoredContext() {
    var f = new SupportFixture();
    var saved = f.create(false);
    f.identityLinks.get("alice").setRevokedAt(Instant.now());
    clearInvocations(f.ticketClient);
    assertThatThrownBy(() -> f.service.get(saved.id()))
        .hasMessageContaining("SUPPORT_ACCESS_DENIED");
    verify(f.ticketClient, never()).request(any(), anyString(), anyString(), any(), any());
    f.identityLinks.get("alice").setRevokedAt(null);
    f.identityLinks.get("alice").setSubject("rebound");
    assertThatThrownBy(() -> f.service.context(saved.id(), false))
        .hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
    f.identityLinks.get("alice").setSubject("subject-alice");
    f.directory.get("alice").setStatus("DISABLED");
    assertThatThrownBy(() -> f.service.context(saved.id(), false))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    var api = new PlatformPrincipal("staff", PlatformRole.PLATFORM_ADMIN, "Staff", "API_KEY");
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(api, null, List.of()));
    assertThatThrownBy(() -> f.service.get(saved.id()))
        .hasMessageContaining("SUPPORT_ACCESS_DENIED");
  }

  @Test
  void resourceChangeRevokesConsentAndRequiresCurrentOwnershipAndRevision() {
    var f = new SupportFixture();
    var saved = f.create(true);
    var d = SupportFixture.deployment("selected", "customer-a", "tenant-a");
    when(f.deployments.findById(d.getId())).thenReturn(Optional.of(d));
    var updated =
        f.service.updateContext(
            saved.id(),
            new SupportTicketService.ContextInput(
                List.of(new ResourceRef(ResourceType.DEPLOYMENT, d.getId())), saved.revision()));
    assertThat(updated.consent().granted()).isFalse();
    assertThat(updated.consent().revokedAt()).isNotNull();
    assertThat(f.remoteTickets.get(saved.id()).path("details").asText())
        .isEqualTo("The app is not ready.");
    assertThatThrownBy(
            () -> f.service.consent(saved.id(), new ConsentChange(true, saved.consent().version())))
        .hasMessageContaining("CONSENT_VERSION_CONFLICT");
    assertThatThrownBy(
            () ->
                f.service.updateContext(
                    saved.id(), new SupportTicketService.ContextInput(List.of(), 0)))
        .hasMessageContaining("CONTEXT_REVISION_CONFLICT");
    d.setCustomerId("customer-b");
    assertThatThrownBy(
            () ->
                f.service.consent(saved.id(), new ConsentChange(true, updated.consent().version())))
        .hasMessageContaining("SUPPORT_ACCESS_DENIED");
  }

  @Test
  void foreignAndArchivedResourcesCannotEnterAuthorityMapping() {
    var f = new SupportFixture();
    var saved = f.create(false);
    var d = SupportFixture.deployment("foreign", "customer-b", "tenant-b");
    when(f.deployments.findById(d.getId())).thenReturn(Optional.of(d));
    var input =
        new SupportTicketService.ContextInput(
            List.of(new ResourceRef(ResourceType.DEPLOYMENT, d.getId())), 0);
    assertThatThrownBy(() -> f.service.updateContext(saved.id(), input))
        .hasMessageContaining("SUPPORT_ACCESS_DENIED");
    d.setCustomerId("customer-a");
    d.setArchivedAt(Instant.now());
    assertThatThrownBy(() -> f.service.updateContext(saved.id(), input))
        .hasMessageContaining("SUPPORT_ACCESS_DENIED");
    assertThat(f.service.contextView(saved.id()).resources()).isEmpty();
  }

  @Test
  void lifecycleAndSeenAreDelegatedWithOpaqueExpectedVersionAndNoLocalConversationMutation() {
    var f = new SupportFixture();
    var saved = f.create(true);
    var original = f.remoteTickets.get(saved.id()).path("episodes").deepCopy();
    var version = new SupportTicketService.Version("W/\"vendor-etag\"");
    var owner = f.service.context(saved.id(), false).requester();
    for (String op : List.of("resolve", "reopen", "cancel")) {
      f.service.change(saved.id(), op, version, "key-" + op);
      verify(f.ticketClient)
          .request(owner, "POST", "/requests/" + saved.id() + "/" + op, version, "key-" + op);
    }
    var seen = new SupportTicketService.Seen("opaque", Instant.now());
    f.service.seen(saved.id(), seen, "seen");
    verify(f.ticketClient)
        .request(any(), eq("PUT"), eq("/requests/" + saved.id() + "/seen"), eq(seen), eq("seen"));
    assertThat(f.service.contextView(saved.id()).consent().granted()).isFalse();
    assertThat(f.remoteTickets.get(saved.id()).path("episodes")).isEqualTo(original);
    assertThatThrownBy(() -> f.service.change(saved.id(), "delete", version, "no"))
        .hasMessageContaining("UNKNOWN_OPERATION");
  }

  @Test
  void failedProviderMutationDoesNotRevokeConsentOrCreateFalseSuccess() {
    var f = new SupportFixture();
    var saved = f.create(true);
    when(f.ticketClient.request(
            any(), eq("POST"), eq("/requests/" + saved.id() + "/resolve"), any(), anyString()))
        .thenThrow(SupportPolicy.unavailable());
    assertThatThrownBy(
            () ->
                f.service.change(
                    saved.id(), "resolve", new SupportTicketService.Version("etag"), "resolve"))
        .hasMessageContaining("SUPPORT_NOT_CONFIGURED");
    assertThat(f.service.contextView(saved.id()).consent().granted()).isTrue();
    assertThat(f.remoteTickets.get(saved.id()).path("status").asText()).isEqualTo("OPEN");
  }

  @Test
  void providerOutageLeavesNoLocalTicketReplicaAndRuntimeOutageDoesNotBlockSave() {
    var f = new SupportFixture();
    var runtime = f.deployments.findById("support-runtime").orElseThrow();
    runtime.setStatus("FAILED");
    runtime.setRuntimeBaseUrl(null);
    var saved = f.create(false);
    assertThat(f.service.get(saved.id()).path("status").asText()).isEqualTo("OPEN");
    when(f.ticketClient.request(any(), eq("POST"), eq("/requests"), any(), eq("outage")))
        .thenThrow(SupportPolicy.unavailable());
    assertThatThrownBy(
            () ->
                f.service.create(
                    new SupportTicketService.Create("Help", "details", null, null), "outage"))
        .hasMessageContaining("SUPPORT_NOT_CONFIGURED");
    assertThat(
            f.jdbc.queryForObject(
                "select count(*) from platform_support_integration_context", Integer.class))
        .isEqualTo(1);
    verifyNoInteractions(f.readiness, f.secrets, f.releases, f.plans, f.runs);
  }

  @Test
  void chatConversionUsesServerOwnedScopeAndAdoptsRemotePrincipalWithoutResending() {
    var f = new SupportFixture();
    var scope = f.conversations.create();
    var input =
        new SupportTicketService.Create("From chat", "details", null, scope.conversationId());
    Map<String, String> headers =
        Map.of(
            "X-AIFABRIC-RUNTIME-API-KEY",
            "private",
            "X-AIFABRIC-RUNTIME-AUTHORIZATION",
            "Bearer assertion");
    var ticket = f.service.createForConversation(input, "convert", scope, headers);
    String id = ticket.path("id").asText();
    assertThat(f.service.context(id, false).runtimePrincipal()).isEqualTo(scope.runtimePrincipal());
    verify(f.ticketClient)
        .request(
            scope.requester(),
            "POST",
            "/requests",
            input,
            "convert",
            headers,
            scope.runtimePrincipal(),
            scope.conversationId());
    assertThatThrownBy(() -> f.service.create(input, "unproved"))
        .hasMessageContaining("CONVERSATION_PROOF_REQUIRED");
    f.login("bob");
    assertThatThrownBy(() -> f.conversations.current(scope.conversationId()))
        .hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
  }

  @Test
  void historicalConversationsAreSelectedOnlyFromTheOwnedProviderTicketAndAreReadOnly() {
    var f = new SupportFixture();
    var saved = f.create(false);
    var ticket = f.remoteTickets.get(saved.id());
    var current = f.service.requireOwnedCase(saved.id());
    var old = ((com.fasterxml.jackson.databind.node.ArrayNode) ticket.path("episodes")).addObject();
    old.put("conversationId", "chat-previous").put("episodeId", "episode-previous").put("number", 1);
    var history = f.service.requireOwnedConversation(saved.id(), "chat-previous");
    assertThat(history.conversationId()).isEqualTo("chat-previous");
    assertThat(history.principalId()).isEqualTo(current.principalId());
    assertThat(history.status()).isEqualTo(CaseStatus.RESOLVED);
    assertThatThrownBy(() -> f.service.requireOwnedConversation(saved.id(), "chat-foreign"))
        .hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
    f.login("bob");
    assertThatThrownBy(() -> f.service.requireOwnedConversation(saved.id(), "chat-previous"))
        .hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
  }

  @Test
  void requesterAndContextReadsCannotFollowAnotherSpaceOrRevokedMember() {
    var f = new SupportFixture();
    var saved = f.create(false);
    var c = f.service.context(saved.id(), false);
    when(f.ticketClient.spaceId()).thenReturn("different-space");
    assertThatThrownBy(() -> f.service.getFor(c)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
    when(f.ticketClient.spaceId()).thenReturn("support-binding");
    f.identityLinks.get("alice").setRevokedAt(Instant.now());
    assertThatThrownBy(() -> f.service.contextByPrincipal(c.runtimePrincipal(), false))
        .hasMessageContaining("SUPPORT_ACCESS_DENIED");
  }
}
