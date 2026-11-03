package com.ai.fabric.platform.backend.support.runtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ai.fabric.platform.backend.support.*;
import com.ai.fabric.platform.backend.support.SupportContracts.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SupportWorkspaceServiceTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final SupportTicketService cases = mock(SupportTicketService.class);
  private final SupportRequesterService requesters = mock(SupportRequesterService.class);
  private final SupportRuntimeAccessService access = mock(SupportRuntimeAccessService.class);
  private final SupportRuntimeClient runtime = mock(SupportRuntimeClient.class);
  private final SupportWorkspaceService service =
      new SupportWorkspaceService(cases, requesters, access, runtime, mapper);
  private SupportRuntimeAccessService.Access destination;

  static OwnedCase owner(CaseStatus status) {
    return new OwnedCase(
        "sup-1",
        "SUP-1",
        "support-deployment",
        "staging",
        "internal-owner",
        "support-tenant",
        "private-case-principal",
        "chat-1",
        "episode-1",
        1,
        false,
        "initial-key",
        "Original form body",
        DeliveryStatus.PENDING,
        status,
        "support-issuer",
        "support-audience");
  }

  @BeforeEach
  void setup() {
    destination =
        new SupportRuntimeAccessService.Access(
            owner(CaseStatus.OPEN), null, null, mapper.createObjectNode(), Map.of());
    when(access.resolve("sup-1")).thenReturn(destination);
  }

  @Test
  void startDelegatesDeliveryToTicketProviderWithExactVersionAndCommandKey() {
    var version = new SupportTicketService.Version("opaque-etag");
    var result = mapper.createObjectNode().put("openingMessageRequired", false);
    when(cases.start("sup-1", version, "start-key", destination.headers())).thenReturn(result);
    assertThat(service.start("sup-1", version, "start-key")).isSameAs(result);
    assertThat(service.start("sup-1", version, "start-key")).isSameAs(result);
    verify(cases, times(2)).start("sup-1", version, "start-key", destination.headers());
    verifyNoInteractions(runtime);
  }

  @Test
  void uncertainDeliveryRetainsTheSavedCaseAndDoesNotRetryWithAnotherKey() {
    when(cases.start(any(), any(), any(), anyMap()))
        .thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "uncertain"));
    var version = new SupportTicketService.Version("opaque-etag");
    assertThatThrownBy(() -> service.start("sup-1", version, "same-key"))
        .isInstanceOf(ResponseStatusException.class);
    verify(cases).start("sup-1", version, "same-key", destination.headers());
    verifyNoInteractions(runtime);
  }

  @Test
  void casualAndContinuationReadsInitializeOnlyOwnedEmptyControlBeforeCanonicalHistory() {
    for (OwnedCase owned : java.util.List.of(emptyOwner(null, 1), emptyOwner("sup-1", 2))) {
      reset(runtime);
      var empty =
          new SupportRuntimeAccessService.Access(
              owned, null, null, mapper.createObjectNode(), Map.of());
      var canonical = mapper.createObjectNode().put("id", "chat-1");
      canonical.putArray("participantMessages");
      var history = new SupportRuntimeClient.Result(200, canonical);
      when(runtime.exchange(empty, "GET", "/api/chat/me/conversations/chat-1/control", null, null))
          .thenReturn(
              new SupportRuntimeClient.Result(
                  200, mapper.createObjectNode().put("controlState", "AI_ACTIVE")));
      when(runtime.exchange(empty, "GET", "/api/chat/me/conversations/chat-1", null, null))
          .thenReturn(history);
      assertThat(service.exchange(empty, "GET", "chat/me/conversations/chat-1", null, null, 0))
          .isSameAs(history);
      var order = inOrder(runtime);
      order
          .verify(runtime)
          .exchange(empty, "GET", "/api/chat/me/conversations/chat-1/control", null, null);
      order.verify(runtime).exchange(empty, "GET", "/api/chat/me/conversations/chat-1", null, null);
      verifyNoMoreInteractions(runtime);
    }
    verifyNoInteractions(cases);
  }

  @Test
  void failedEmptyControlReadCannotTriggerItemReadOrInventUserMessage() {
    var empty =
        new SupportRuntimeAccessService.Access(
            emptyOwner(null, 1), null, null, mapper.createObjectNode(), Map.of());
    var failure =
        new SupportRuntimeClient.Result(
            503, mapper.createObjectNode().put("code", "RUNTIME_UNAVAILABLE"));
    when(runtime.exchange(empty, "GET", "/api/chat/me/conversations/chat-1/control", null, null))
        .thenReturn(failure);
    assertThat(service.exchange(empty, "GET", "chat/me/conversations/chat-1", null, null, 0))
        .isSameAs(failure);
    verify(runtime).exchange(empty, "GET", "/api/chat/me/conversations/chat-1/control", null, null);
    verifyNoMoreInteractions(runtime);
    verifyNoInteractions(cases);
  }

  @Test
  void emptyInitializationCannotFollowForeignConversationOrArbitraryReadDestination() {
    var empty =
        new SupportRuntimeAccessService.Access(
            emptyOwner(null, 1), null, null, mapper.createObjectNode(), Map.of());
    for (String route :
        java.util.List.of(
            "chat/me/conversations/foreign",
            "chat/me/conversations/foreign/control",
            "https://foreign.test/api/chat/me/conversations/chat-1"))
      assertThatThrownBy(() -> service.exchange(empty, "GET", route, null, null, 0))
          .isInstanceOf(ResponseStatusException.class);
    verifyNoInteractions(runtime, cases);
  }

  @Test
  void continuationAndActivityCommandsDelegateOpaqueVersionAndNeverResendIntake() {
    var version = new SupportTicketService.Version("opaque-provider-version");
    for (String operation : java.util.List.of("episodes", "activity")) {
      var response = mapper.createObjectNode().put("id", "sup-1");
      when(cases.conversationCommand(
              "sup-1", operation, version, "command-" + operation, destination.headers()))
          .thenReturn(response);
      assertThat(service.conversationCommand("sup-1", operation, version, "command-" + operation))
          .isSameAs(response);
      verify(cases)
          .conversationCommand(
              "sup-1", operation, version, "command-" + operation, destination.headers());
    }
    verifyNoInteractions(runtime);
  }

  private OwnedCase emptyOwner(String caseId, int episode) {
    var base = owner(CaseStatus.OPEN);
    return new OwnedCase(
        caseId,
        base.reference(),
        base.deploymentId(),
        base.targetProfileId(),
        base.customerId(),
        base.tenantId(),
        base.principalId(),
        base.conversationId(),
        "episode-" + episode,
        episode,
        false,
        null,
        null,
        DeliveryStatus.COMPLETED,
        CaseStatus.OPEN,
        base.assertionIssuer(),
        base.assertionAudience());
  }

  @Test
  void readonlyResumeNeverPostsOrReplaysTheForm() {
    when(runtime.exchange(destination, "GET", "/api/chat/me/conversations/chat-1", null, null))
        .thenReturn(
            new SupportRuntimeClient.Result(200, mapper.createObjectNode().put("id", "chat-1")));
    service.exchange("sup-1", "GET", "chat/me/conversations/chat-1", null, null, 0);
    verifyNoInteractions(cases);
    verify(runtime).exchange(destination, "GET", "/api/chat/me/conversations/chat-1", null, null);
  }

  @Test
  void foreignConversationLegacyQueryArbitraryRoutesAndClosedCaseWritesAreDenied() {
    for (String route :
        new String[] {
          "chat/me/conversations",
          "chat/me/conversations/foreign/messages",
          "chat/me/query",
          "http://other/api/chat/me/query",
          "chat/../admin/overview"
        }) {
      assertThatThrownBy(
              () ->
                  service.exchange(
                      "sup-1",
                      "POST",
                      route,
                      mapper.createObjectNode().put("query", "help"),
                      "key",
                      0))
          .isInstanceOf(ResponseStatusException.class);
    }
    when(access.resolve("sup-1"))
        .thenReturn(
            new SupportRuntimeAccessService.Access(
                owner(CaseStatus.RESOLVED), null, null, mapper.createObjectNode(), Map.of()));
    assertThatThrownBy(
            () ->
                service.exchange(
                    "sup-1",
                    "POST",
                    "chat/me/conversations/chat-1/messages",
                    mapper.createObjectNode().put("query", "help"),
                    "key",
                    0))
        .isInstanceOf(ResponseStatusException.class);
    verifyNoInteractions(runtime);
  }

  @Test
  void browserCannotOverrideConversationIdentityOrPreviewPrompts() {
    var request =
        mapper
            .createObjectNode()
            .put("query", "help")
            .put("conversationId", "foreign")
            .put("userId", "admin")
            .put("promptPreview", "override");
    when(runtime.exchange(any(), any(), any(), any(), any()))
        .thenReturn(new SupportRuntimeClient.Result(200, mapper.createObjectNode()));
    service.exchange("sup-1", "POST", "chat/me/conversations/chat-1/messages", request, "key", 0);
    var body = ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
    verify(runtime).exchange(eq(destination), eq("POST"), any(), body.capture(), eq("key"));
    assertThat(body.getValue().path("conversationId").asText()).isEqualTo("chat-1");
    assertThat(body.getValue().has("userId")).isFalse();
    assertThat(body.getValue().has("promptPreview")).isFalse();
  }

  @Test
  void historicalReadsUseTheExplicitOwnedEpisodeWhilePostsCannotSwitchEpisodes() {
    var old = emptyOwner("sup-1", 1);
    var archive = new OwnedCase(old.caseId(), old.reference(), old.deploymentId(), old.targetProfileId(),
        old.customerId(), old.tenantId(), old.principalId(), "chat-previous", "episode-previous", 1,
        false, null, null, DeliveryStatus.COMPLETED, CaseStatus.RESOLVED, old.assertionIssuer(), old.assertionAudience());
    var historical = new SupportRuntimeAccessService.Access(archive, null, null, mapper.createObjectNode(), Map.of());
    when(access.resolveEpisode("sup-1", "chat-previous")).thenReturn(historical);
    when(runtime.exchange(historical, "GET", "/api/chat/me/conversations/chat-previous", null, null))
        .thenReturn(new SupportRuntimeClient.Result(200, mapper.createObjectNode().put("id", "chat-previous")));
    assertThat(service.exchange("sup-1", "GET", "chat/me/conversations/chat-previous", null, null, 0).status()).isEqualTo(200);
    assertThatThrownBy(() -> service.exchange("sup-1", "POST", "chat/me/conversations/chat-previous/messages",
        mapper.createObjectNode().put("query", "mutate archive"), "archive-key", 0)).isInstanceOf(ResponseStatusException.class);
    verify(runtime).exchange(historical, "GET", "/api/chat/me/conversations/chat-previous", null, null);
    verifyNoMoreInteractions(runtime);
    verify(access, times(1)).resolveEpisode("sup-1", "chat-previous");
  }

  @Test
  void suggestionsUseTheScopedIdentityWithoutRequiringAMessageKeyOrForwardingIdentityFields() {
    var request = mapper.createObjectNode().put("content", "Attached setup guide")
        .put("maxSuggestions", 3).put("userId", "foreign").put("promptPreview", "override");
    when(runtime.exchange(any(), any(), any(), any(), any()))
        .thenReturn(new SupportRuntimeClient.Result(200, mapper.createObjectNode()));
    service.exchange("sup-1", "POST", "chat/me/suggestions", request, null, 0);
    var body = ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
    verify(runtime).exchange(eq(destination), eq("POST"), eq("/api/chat/me/suggestions"), body.capture(), isNull());
    assertThat(body.getValue().path("content").asText()).isEqualTo("Attached setup guide");
    assertThat(body.getValue().path("maxSuggestions").asInt()).isEqualTo(3);
    assertThat(body.getValue().has("userId")).isFalse();
    assertThat(body.getValue().has("promptPreview")).isFalse();
  }

  @Test
  void controlCursorIsForwardedWithoutAllowingAQueryDestination() {
    when(runtime.exchange(any(), any(), any(), any(), any()))
        .thenReturn(new SupportRuntimeClient.Result(200, mapper.createObjectNode()));
    service.exchange("sup-1", "GET", "chat/me/conversations/chat-1/control", null, null, 42);
    verify(runtime)
        .exchange(
            destination,
            "GET",
            "/api/chat/me/conversations/chat-1/control?afterMessageSequence=42",
            null,
            null);
  }
}
