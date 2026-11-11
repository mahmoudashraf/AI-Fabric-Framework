package com.ai.fabric.runtime.mcp;

import ai.fabric.http.*;
import ai.fabric.intent.action.*;
import ai.fabric.intent.orchestration.OrchestrationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RuntimeManagedMcpExecutorTest {
    private final OutboundHttpExecutor http = mock(OutboundHttpExecutor.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final RuntimeManagedMcpExecutor executor = new RuntimeManagedMcpExecutor(http, mapper,
        "https://platform.example.test", "target-key", "dep-1", "staging");
    private final Map<String, Object> config = Map.of("execution", Map.of("mcp", Map.of("connectionId", "mcp-1")),
        "secret", "must-not-be-forwarded");

    @Test void dispatchesOnceWithVerifiedContextAndNoSuppliedConfigurationOrSecrets() throws Exception {
        when(http.execute(any())).thenReturn(new OutboundHttpExecutionResponse(200,
            "{\"success\":true,\"data\":{\"receiptId\":\"receipt-1\"}}", Map.of()));
        var result = executor.execute("cancel", ActionAccessMode.WRITE_ONLY, Map.of("subscriptionId", "sub-1"), context(true), config);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().toMap()).containsEntry("receiptId", "receipt-1");
        var call = ArgumentCaptor.forClass(OutboundHttpExecutionRequest.class);
        verify(http, times(1)).execute(call.capture());
        assertThat(call.getValue().url()).isEqualTo("https://platform.example.test/api/mcp/runtime/dep-1/staging/actions/execute");
        assertThat(call.getValue().headers()).containsEntry("X-LoomAI-MCP-Runtime-Key", "target-key");
        assertThat(call.getValue().body()).doesNotContain("must-not-be-forwarded", "actionConfig", "mcpSecretValues");
        assertThat(mapper.readTree(call.getValue().body()).path("trace").path("authContext").path("subjectId").asText()).isEqualTo("user-1");
    }

    @Test void publicReadKeepsAnonymousContextAndNeedsNoProviderCredential() {
        when(http.execute(any())).thenReturn(new OutboundHttpExecutionResponse(200,
            "{\"success\":true,\"data\":{\"_items\":[{\"title\":\"Guide\"}],\"_count\":1}}", Map.of()));
        var result = executor.execute("docs", ActionAccessMode.READ, Map.of(), context(false), config);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isInstanceOf(ActionListPayload.class);
        assertThat(((ActionListPayload) result.getData()).getItems()).hasSize(1);
        verify(http, times(1)).execute(any());
    }

    @Test void uncertainWriteIsNeverRepeated() {
        when(http.execute(any())).thenThrow(new IllegalStateException("receiver response lost"));
        var result = executor.execute("cancel", ActionAccessMode.WRITE_ONLY, Map.of(), context(true), config);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("MCP_EXECUTION_OUTCOME_UNKNOWN");
        verify(http, times(1)).execute(any());
    }

    @Test void serverFailureCannotClaimThatAWriteDefinitelyDidNotExecute() {
        when(http.execute(any())).thenReturn(new OutboundHttpExecutionResponse(502,
            "{\"success\":false,\"errorCode\":\"MCP_CONNECTION_UNAVAILABLE\"}", Map.of()));
        var result = executor.execute("cancel", ActionAccessMode.WRITE_ONLY, Map.of(), context(true), config);
        assertThat(result.getErrorCode()).isEqualTo("MCP_EXECUTION_OUTCOME_UNKNOWN");
        assertThat(result.getMessage()).contains("before retrying");
        verify(http, times(1)).execute(any());
    }

    @Test void foreignRuntimeContextCannotDispatch() {
        var orchestration = OrchestrationContext.builder().userId("user-1").metadata(Map.of("deploymentId", "foreign")).build();
        assertThat(executor.execute("cancel", ActionAccessMode.WRITE_ONLY, Map.of(), new ActionContext(orchestration, null), config)
            .getErrorCode()).isEqualTo("MCP_ACCESS_DENIED");
        verifyNoInteractions(http);
    }

    @Test void legacyMcpStaysOnExistingDispatchWithoutCallingPlatform() {
        assertThat(executor.execute("legacy", ActionAccessMode.READ, Map.of(), context(true), Map.of())
            .getErrorCode()).isEqualTo("MCP_TOOL_NOT_AVAILABLE");
        verifyNoInteractions(http);
    }

    @Test void businessReconnectionIsAVisibleFailureNotAVisitorLoginOrRetry() {
        when(http.execute(any())).thenReturn(new OutboundHttpExecutionResponse(409,
            "{\"success\":false,\"errorCode\":\"MCP_CONNECTION_REAUTH_REQUIRED\",\"message\":\"The business owner must reconnect this service.\"}", Map.of()));
        var result = executor.execute("cancel", ActionAccessMode.WRITE_ONLY, Map.of(), context(true), config);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("MCP_CONNECTION_REAUTH_REQUIRED");
        assertThat(result.getMessage()).contains("business owner");
        verify(http, times(1)).execute(any());
    }

    @Test void upstreamAuthorizationFailureBelongsToTheCompanyNotASecondVisitorLogin() {
        when(http.execute(any())).thenReturn(new OutboundHttpExecutionResponse(200,
            "{\"success\":false,\"errorCode\":\"MCP_UPSTREAM_AUTH_REQUIRED\",\"message\":\"Unauthorized\"}", Map.of()));
        var result = executor.execute("cancel", ActionAccessMode.WRITE_ONLY, Map.of(), context(true), config);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("MCP_UPSTREAM_AUTH_REQUIRED");
        assertThat(result.getMessage()).contains("administrator", "no personal MCP login");
        verify(http, times(1)).execute(any());
    }

    @Test void malformedListCannotBecomeSuccessfulEvidence() {
        when(http.execute(any())).thenReturn(new OutboundHttpExecutionResponse(200,
            "{\"success\":true,\"data\":{\"_items\":\"invalid\"}}", Map.of()));
        assertThat(executor.execute("docs", ActionAccessMode.READ, Map.of(), context(true), config).getErrorCode())
            .isEqualTo("MCP_INVALID_RESPONSE");
    }

    @Test void missingWriteResponseRemainsUnknownWithoutReplay() {
        when(http.execute(any())).thenReturn(new OutboundHttpExecutionResponse(200, null, Map.of()));
        var result = executor.execute("cancel", ActionAccessMode.WRITE_ONLY, Map.of(), context(true), config);
        assertThat(result.getErrorCode()).isEqualTo("MCP_EXECUTION_OUTCOME_UNKNOWN");
        assertThat(result.getMessage()).contains("may have completed", "before retrying");
        verify(http, times(1)).execute(any());
    }

    @Test void malformedWriteResultRemainsUnknownWithoutReplay() {
        when(http.execute(any())).thenReturn(new OutboundHttpExecutionResponse(200,
            "{\"success\":true,\"data\":{\"_items\":\"invalid\"}}", Map.of()));
        var result = executor.execute("cancel", ActionAccessMode.READ_WRITE, Map.of(), context(true), config);
        assertThat(result.getErrorCode()).isEqualTo("MCP_EXECUTION_OUTCOME_UNKNOWN");
        assertThat(result.getMessage()).contains("may have completed");
        verify(http, times(1)).execute(any());
    }

    private ActionContext context(boolean authenticated) {
        var orchestration = OrchestrationContext.builder().userId(authenticated ? "user-1" : null).sessionId("session-1")
            .metadata(Map.of("deploymentId", "dep-1", "customerId", "company-1", "tenantId", "tenant-1",
                "subjectType", authenticated ? "END_USER" : "ANONYMOUS_SESSION")).build();
        return new ActionContext(orchestration, null);
    }
}
