package com.ai.fabric.platform.backend.support;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ai.fabric.platform.backend.support.runtime.SupportRuntimeAccessService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;

class SupportTicketCommandControllerTest {
  @AfterEach
  void logout() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void chatFirstCreateAdoptsOnlyVerifiedScopeAndReplaysWithoutCreatingAnotherTicket() {
    var f = new SupportFixture();
    var scope = f.conversations.create();
    var runtime = mock(SupportRuntimeAccessService.class);
    var proof =
        Map.of(
            "X-AIFABRIC-RUNTIME-AUTHORIZATION",
            "Bearer rpa1.signed",
            "X-AIFABRIC-RUNTIME-API-KEY",
            "private-key");
    when(runtime.conversationAccess(scope))
        .thenReturn(new SupportRuntimeAccessService.Access(null, null, null, null, proof));
    var controller = controller(f, runtime);
    var input =
        new SupportTicketCommandController.Command(
            "CREATE", null, "User chose to create", "Exact details", "setup");
    SecurityContextHolder.clearContext();
    var ticket =
        controller
            .command(input, headers(f, scope, "support:tickets:create"), "action-command")
            .getBody();
    var replay =
        controller
            .command(input, headers(f, scope, "support:tickets:create"), "action-command")
            .getBody();
    assertThat(ticket).isEqualTo(replay);
    assertThat(ticket.path("runtimePrincipal").asText()).isEqualTo(scope.runtimePrincipal());
    assertThat(ticket.path("conversation").path("conversationId").asText())
        .isEqualTo(scope.conversationId());
    assertThat(ticket.path("conversation").path("delivery").asText()).isEqualTo("NOT_REQUIRED");
    assertThat(
            f.jdbc.queryForObject(
                "select count(*) from platform_support_integration_context", Integer.class))
        .isEqualTo(1);
    verify(f.ticketClient, times(2))
        .request(
            eq(scope.requester()),
            eq("POST"),
            eq("/requests"),
            eq(
                new SupportTicketService.Create(
                    input.subject(), input.details(), input.topic(), scope.conversationId())),
            eq("action-command"),
            eq(proof),
            eq(scope.runtimePrincipal()),
            eq(scope.conversationId()));
  }

  @Test
  void linkedTicketPrincipalUsesCurrentEpisodeInsteadOfOriginalCasualScope() {
    var f = new SupportFixture();
    var original = f.conversations.create();
    var ticket =
        f.service.createForConversation(
            new SupportTicketService.Create(
                "Converted chat", "Original discussion", null, original.conversationId()),
            "convert",
            original,
            Map.of());
    String id = ticket.path("id").asText();
    f.nextEpisode(id);
    var current =
        new SupportConversationScopeService.Scope(
            f.remoteTickets.get(id).path("conversation").path("conversationId").asText(),
            original.runtimePrincipal(),
            original.requester());
    var controller = controller(f, mock(SupportRuntimeAccessService.class));
    SecurityContextHolder.clearContext();
    assertThat(
            controller
                .command(
                    new SupportTicketCommandController.Command("GET", id, null, null, null),
                    headers(f, current, "support:tickets:read"),
                    "current-episode")
                .getBody()
                .path("id")
                .asText())
        .isEqualTo(id);
    assertThatThrownBy(
            () ->
                controller.command(
                    new SupportTicketCommandController.Command("GET", id, null, null, null),
                    headers(f, original, "support:tickets:read"),
                    "old-episode"))
        .hasMessageContaining("SUPPORT_ACCESS_DENIED");
  }

  @Test
  void ticketRuntimeGetAndListRemainScopedToOriginalActiveOwnerWithoutConsoleSession() {
    var f = new SupportFixture();
    var saved = f.create(false);
    var owned = f.service.requireOwnedCase(saved.id());
    var scope =
        new SupportConversationScopeService.Scope(
            owned.conversationId(),
            owned.principalId(),
            f.service.context(saved.id(), false).requester());
    var controller = controller(f, mock(SupportRuntimeAccessService.class));
    SecurityContextHolder.clearContext();
    assertThat(
            controller
                .command(
                    new SupportTicketCommandController.Command("GET", saved.id(), null, null, null),
                    headers(f, scope, "support:tickets:read"),
                    "get")
                .getBody()
                .path("id")
                .asText())
        .isEqualTo(saved.id());
    assertThat(
            controller
                .command(
                    new SupportTicketCommandController.Command("LIST", null, null, null, null),
                    headers(f, scope, "support:tickets:read"),
                    "list")
                .getBody()
                .path("items"))
        .hasSize(1);
    f.directory.get("alice").setStatus("DISABLED");
    assertThatThrownBy(
            () ->
                controller.command(
                    new SupportTicketCommandController.Command("GET", saved.id(), null, null, null),
                    headers(f, scope, "support:tickets:read"),
                    "revoked"))
        .hasMessageContaining("SUPPORT_ACCESS_DENIED");
  }

  @Test
  void missingAmbiguousOrForgedTrustedCallerNeverSelectsLatestOrAnotherOwnersTicket() {
    var f = new SupportFixture();
    var scope = f.conversations.create();
    var runtime = mock(SupportRuntimeAccessService.class);
    var controller = controller(f, runtime);
    var command = new SupportTicketCommandController.Command("CREATE", null, "Help", null, null);
    for (String name :
        List.of(
            "X-LoomAI-Ticket-Connector-Key",
            "X-AIFABRIC-AUTH-SUBJECT-ID",
            "X-AIFABRIC-CONVERSATION-ID",
            "X-AIFABRIC-AUTH-DEPLOYMENT-ID",
            "X-AIFABRIC-AUTH-TENANT-ID",
            "X-AIFABRIC-AUTH-CUSTOMER-ID",
            "X-AIFABRIC-AUTH-EXPIRES-AT")) {
      var absent = headers(f, scope, "support:tickets:create");
      absent.remove(name);
      assertThatThrownBy(() -> controller.command(command, absent, "missing"))
          .hasMessageContaining("SUPPORT_ACCESS_DENIED");
      var duplicate = headers(f, scope, "support:tickets:create");
      duplicate.add(name, duplicate.getFirst(name));
      assertThatThrownBy(() -> controller.command(command, duplicate, "duplicate"))
          .hasMessageContaining("SUPPORT_ACCESS_DENIED");
      var forged = headers(f, scope, "support:tickets:create");
      forged.set(name, "forged");
      assertThatThrownBy(() -> controller.command(command, forged, "forged"))
          .hasMessageContaining("SUPPORT_ACCESS_DENIED");
    }
    var wrongScope = headers(f, scope, "support:tickets:read");
    assertThatThrownBy(() -> controller.command(command, wrongScope, "read-only"))
        .hasMessageContaining("SUPPORT_ACCESS_DENIED");
    assertThat(f.remoteTickets).isEmpty();
    verifyNoInteractions(runtime);
  }

  @Test
  void chatAuthorityIsUniquePerSessionAndCannotReadAnotherOwnersTicket() {
    var f = new SupportFixture();
    var first = f.conversations.create();
    var second = f.conversations.create();
    assertThat(first.runtimePrincipal()).isNotEqualTo(second.runtimePrincipal());
    assertThat(first.conversationId()).isNotEqualTo(second.conversationId());
    f.login("bob");
    assertThatThrownBy(() -> f.conversations.current(first.conversationId()))
        .hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
    var bob = f.create(false);
    f.login("alice");
    var controller = controller(f, mock(SupportRuntimeAccessService.class));
    assertThatThrownBy(
            () ->
                controller.command(
                    new SupportTicketCommandController.Command("GET", bob.id(), null, null, null),
                    headers(f, first, "support:tickets:read"),
                    "not-bobs"))
        .hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
    f.env.setProperty("platform.support.desk.binding-id", "other-space");
    assertThatThrownBy(() -> f.conversations.verifiedPrincipal(first.runtimePrincipal()))
        .hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
  }

  private SupportTicketCommandController controller(
      SupportFixture f, SupportRuntimeAccessService runtime) {
    var assertions = mock(SupportRequesterAssertionService.class);
    when(assertions.connectorKey()).thenReturn("dedicated-connector-key");
    return new SupportTicketCommandController(
        f.authentication, assertions, f.conversations, f.service, runtime);
  }

  private HttpHeaders headers(
      SupportFixture f, SupportConversationScopeService.Scope scope, String permissions) {
    var binding = f.settings.binding();
    var h = new HttpHeaders();
    h.set("X-LoomAI-Ticket-Connector-Key", "dedicated-connector-key");
    h.set("X-AIFABRIC-AUTH-SUBJECT-TYPE", "END_USER");
    h.set("X-AIFABRIC-AUTH-MODE", "PRIVATE_RUNTIME_BACKEND_MEDIATED");
    h.set("X-AIFABRIC-AUTH-CALLER-TYPE", "TRUSTED_BACKEND");
    h.set("X-AIFABRIC-AUTH-DEPLOYMENT-ID", binding.deploymentId());
    h.set("X-AIFABRIC-AUTH-CUSTOMER-ID", binding.customerId());
    h.set("X-AIFABRIC-AUTH-TENANT-ID", binding.tenantId());
    h.set("X-AIFABRIC-AUTH-ISSUER", binding.issuer());
    h.set("X-AIFABRIC-AUTH-AUDIENCES", binding.audience());
    h.set("X-AIFABRIC-AUTH-SCOPES", permissions);
    h.set("X-AIFABRIC-AUTH-EXPIRES-AT", Instant.now().plusSeconds(120).toString());
    h.set("X-AIFABRIC-AUTH-SUBJECT-ID", scope.runtimePrincipal());
    h.set("X-AIFABRIC-CONVERSATION-ID", scope.conversationId());
    return h;
  }
}
