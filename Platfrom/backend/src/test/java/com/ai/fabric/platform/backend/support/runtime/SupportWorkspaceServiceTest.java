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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

class SupportWorkspaceServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SupportCaseService cases = mock(SupportCaseService.class);
    private final SupportRequesterService requesters = mock(SupportRequesterService.class);
    private final SupportRuntimeAccessService access = mock(SupportRuntimeAccessService.class);
    private final SupportRuntimeClient runtime = mock(SupportRuntimeClient.class);
    private final SupportWorkspaceService service = new SupportWorkspaceService(cases, requesters, access, runtime, mapper);
    private SupportRuntimeAccessService.Access destination;

    static OwnedCase owner(CaseStatus status) {
        return new OwnedCase("sup-1", "SUP-1", "support-deployment", "staging", "internal-owner", "support-tenant",
            "private-case-principal", "chat-1", "episode-1", 1, false, "initial-key", "Original form body",
            DeliveryStatus.PENDING, status, "support-issuer", "support-audience");
    }
    @BeforeEach void setup() {
        destination = new SupportRuntimeAccessService.Access(owner(CaseStatus.OPEN), null, null, mapper.createObjectNode(), Map.of());
        when(access.resolve("sup-1")).thenReturn(destination);
        when(cases.initialMessageForDelivery("sup-1")).thenReturn(new InitialMessage("sup-1", "episode-1", "chat-1",
            "Original form body", "initial-key", DeliveryStatus.PENDING));
    }

    @Test void intakeDeliveryAlwaysUsesThePersistedBodyAndKey() {
        when(runtime.exchange(eq(destination), eq("POST"), eq("/api/chat/me/conversations/chat-1/messages"), any(), eq("initial-key")))
            .thenReturn(new SupportRuntimeClient.Result(200, mapper.createObjectNode().put("type", "INFORMATION_PROVIDED")));
        service.start("sup-1");
        service.start("sup-1");
        var body = ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        verify(runtime, times(2)).exchange(eq(destination), eq("POST"), eq("/api/chat/me/conversations/chat-1/messages"), body.capture(), eq("initial-key"));
        assertThat(body.getAllValues()).allMatch(value -> "Original form body".equals(value.path("query").asText()));
        verify(cases, times(2)).observeInitialDelivery("sup-1", "episode-1", DeliveryStatus.COMPLETED);
    }

    @Test void uncertainDeliveryRetainsTheSavedCaseAndDoesNotRetryWithAnotherKey() {
        when(runtime.exchange(any(), any(), any(), any(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "uncertain"));
        var result = service.start("sup-1");
        assertThat(result.runtimeStatus()).isEqualTo(504);
        assertThat(result.response().path("type").asText()).isEqualTo("DELIVERY_UNCERTAIN");
        verify(runtime, times(1)).exchange(any(), any(), any(), any(), eq("initial-key"));
        verify(cases).observeInitialDelivery("sup-1", "episode-1", DeliveryStatus.UNKNOWN);
    }

    @Test void readonlyResumeNeverPostsOrReplaysTheForm() {
        when(runtime.exchange(destination, "GET", "/api/chat/me/conversations/chat-1", null, null))
            .thenReturn(new SupportRuntimeClient.Result(200, mapper.createObjectNode().put("id", "chat-1")));
        service.exchange("sup-1", "GET", "chat/me/conversations/chat-1", null, null, 0);
        verify(cases, never()).initialMessageForDelivery(any());
        verify(runtime).exchange(destination, "GET", "/api/chat/me/conversations/chat-1", null, null);
    }

    @Test void foreignConversationLegacyQueryArbitraryRoutesAndClosedCaseWritesAreDenied() {
        for (String route : new String[]{"chat/me/conversations", "chat/me/conversations/foreign/messages", "chat/me/query", "http://other/api/chat/me/query", "chat/../admin/overview"}) {
            assertThatThrownBy(() -> service.exchange("sup-1", "POST", route, mapper.createObjectNode().put("query", "help"), "key", 0))
                .isInstanceOf(ResponseStatusException.class);
        }
        when(access.resolve("sup-1")).thenReturn(new SupportRuntimeAccessService.Access(owner(CaseStatus.RESOLVED), null, null,
            mapper.createObjectNode(), Map.of()));
        assertThatThrownBy(() -> service.exchange("sup-1", "POST", "chat/me/conversations/chat-1/messages",
            mapper.createObjectNode().put("query", "help"), "key", 0)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(runtime);
    }

    @Test void browserCannotOverrideConversationIdentityOrPreviewPrompts() {
        var request = mapper.createObjectNode().put("query", "help").put("conversationId", "foreign")
            .put("userId", "admin").put("promptPreview", "override");
        when(runtime.exchange(any(), any(), any(), any(), any())).thenReturn(new SupportRuntimeClient.Result(200, mapper.createObjectNode()));
        service.exchange("sup-1", "POST", "chat/me/conversations/chat-1/messages", request, "key", 0);
        var body = ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        verify(runtime).exchange(eq(destination), eq("POST"), any(), body.capture(), eq("key"));
        assertThat(body.getValue().path("conversationId").asText()).isEqualTo("chat-1");
        assertThat(body.getValue().has("userId")).isFalse();
        assertThat(body.getValue().has("promptPreview")).isFalse();
    }

    @Test void controlCursorIsForwardedWithoutAllowingAQueryDestination() {
        when(runtime.exchange(any(), any(), any(), any(), any())).thenReturn(new SupportRuntimeClient.Result(200, mapper.createObjectNode()));
        service.exchange("sup-1", "GET", "chat/me/conversations/chat-1/control", null, null, 42);
        verify(runtime).exchange(destination, "GET", "/api/chat/me/conversations/chat-1/control?afterMessageSequence=42", null, null);
    }
}
