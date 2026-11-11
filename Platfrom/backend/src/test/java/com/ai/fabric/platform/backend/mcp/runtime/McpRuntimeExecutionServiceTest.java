package com.ai.fabric.platform.backend.mcp.runtime;

import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class McpRuntimeExecutionServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentTargetContextService targets = mock(DeploymentTargetContextService.class);
    private final DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
    private final McpRuntimeConnectionResolver connections = mock(McpRuntimeConnectionResolver.class);
    private final McpRuntimeExecutionService service = new McpRuntimeExecutionService(deployments, releases, versions, targets, authority, connections, mapper);
    private DeploymentEntity deployment;
    private DeploymentVersionEntity version;
    private DeploymentReleaseEntity release;

    @BeforeEach void setup() throws Exception {
        deployment = new DeploymentEntity(); deployment.setId("dep-1"); deployment.setCustomerId("company-1"); deployment.setTenantId("tenant-1");
        release = new DeploymentReleaseEntity(); release.setId("rel-1"); release.setDeploymentId("dep-1");
        release.setTargetProfileId("staging"); release.setDeploymentVersionId("ver-1"); release.setStatus("APPLIED_VERIFIED"); release.setProvisioningStatus("ACTIVE");
        version = new DeploymentVersionEntity(); version.setId("ver-1"); version.setDeploymentId("dep-1");
        version.setActionsConfigJson("""
            {"actions":[{"name":"cancel_subscription","adapterType":"mcp-tool","accessMode":"WRITE_ONLY",
             "anonymousAllowed":false,"requiresConfirmation":true,
             "params":[{"name":"actorId","askUser":false,"visibility":"INTERNAL","resolveFrom":{"source":"RUNTIME_CONTEXT","field":"userId"}}],
             "inputSchema":{"type":"object"},
             "execution":{"mcp":{"connectionId":"mcp-1","connectionRevision":1,"serverRef":"server-1","toolName":"cancel","toolSchemaHash":"hash-1"}}}]}
            """);
        version.setManifestJson(mapper.createObjectNode().set("actionsConfig", mapper.readTree(version.getActionsConfigJson())).toString());
        when(deployments.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(releases.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("dep-1", "staging")).thenReturn(List.of(release));
        when(versions.findById("ver-1")).thenReturn(Optional.of(version));
        when(connections.resolve("mcp-1", "dep-1", "staging", "cancel", "hash-1"))
            .thenReturn(new McpRuntimeConnectionResolver.Resolved(mapper.readTree("{\"connectionRevision\":1,\"endpointUrl\":\"https://business.example/mcp\",\"auth\":{\"mode\":\"BEARER_TOKEN_SECRET_REF\",\"secretRef\":\"MCP_SECRET_CONNECTION\"}}"),
                Map.of("MCP_SECRET_CONNECTION", "owned-business-token")));
        when(connections.execute(any())).thenReturn(mapper.readTree("{\"success\":true,\"data\":{\"receiptId\":\"receipt-1\"}}"));
    }

    @Test void reconstructsAppliedActionAndOverridesSpoofedTrustedArgument() throws Exception {
        ObjectNode request = request(true);
        request.withObject("/params").put("actorId", "victim");
        request.withObject("/trace").putObject("actionConfig").put("toolName", "dangerous-tool");
        request.withObject("/trace").putObject("mcpSecretValues").put("FOREIGN", "attacker-token");
        var result = service.execute("dep-1", "staging", "exact-target-key", request);
        assertThat(result.path("success").asBoolean()).isTrue();
        var submitted = ArgumentCaptor.forClass(JsonNode.class);
        verify(connections).execute(submitted.capture());
        var body = submitted.getValue();
        assertThat(body.at("/params/actorId").asText()).isEqualTo("user-1");
        assertThat(body.at("/trace/actionConfig/execution/mcp/toolName").asText()).isEqualTo("cancel");
        assertThat(body.at("/trace/actionConfig/mcpServers/server-1/endpointUrl").asText()).isEqualTo("https://business.example/mcp");
        assertThat(body.toString()).doesNotContain("dangerous-tool", "attacker-token", "victim");
        assertThat(body.at("/trace/mcpSecretValues/MCP_SECRET_CONNECTION").asText()).isEqualTo("owned-business-token");
        var ordered = inOrder(authority, connections);
        ordered.verify(authority).authenticateMcpRuntime(deployment, version, release, "exact-target-key");
        ordered.verify(connections).resolve("mcp-1", "dep-1", "staging", "cancel", "hash-1");
    }

    @Test void missingRuntimeKeyCannotReadBindingsOrDispatch() throws Exception {
        assertThatThrownBy(() -> service.execute("dep-1", "staging", "", request(true))).hasMessageContaining("not authorized");
        verifyNoInteractions(deployments, releases, versions, connections);
    }

    @Test void foreignTenantCannotUseAnOtherwiseAuthenticatedRuntime() throws Exception {
        var request = request(true); request.withObject("/trace/authContext").put("tenantId", "foreign");
        assertThatThrownBy(() -> service.execute("dep-1", "staging", "key", request)).hasMessageContaining("not authorized");
        verifyNoInteractions(connections);
    }

    @Test void expiredUserSessionCannotDispatchPrivateMutation() throws Exception {
        var request = request(true); request.withObject("/trace/authContext").put("expiresAt", "2000-01-01T00:00:00Z");
        assertThatThrownBy(() -> service.execute("dep-1", "staging", "key", request)).hasMessageContaining("not authorized");
        verifyNoInteractions(connections);
    }

    @Test void anonymousCallerCannotInvokePrivateTool() throws Exception {
        assertThatThrownBy(() -> service.execute("dep-1", "staging", "key", request(false))).hasMessageContaining("not authorized");
        verifyNoInteractions(connections);
    }

    @Test void publicReadUsesNoUpstreamCredentialButRetainsTargetAuthorization() throws Exception {
        ObjectNode config = (ObjectNode) mapper.readTree(version.getActionsConfigJson());
        ((ObjectNode) config.path("actions").get(0)).put("accessMode", "READ").put("anonymousAllowed", true);
        version.setActionsConfigJson(config.toString());
        version.setManifestJson(mapper.createObjectNode().set("actionsConfig", config).toString());
        when(connections.resolve(anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(new McpRuntimeConnectionResolver.Resolved(mapper.readTree("{\"connectionRevision\":1,\"auth\":{\"mode\":\"NONE\"}}"), Map.of()));
        assertThat(service.execute("dep-1", "staging", "key", request(false)).path("success").asBoolean()).isTrue();
        var submitted = ArgumentCaptor.forClass(JsonNode.class); verify(connections).execute(submitted.capture());
        assertThat(submitted.getValue().path("trace").has("mcpSecretValues")).isFalse();
        verify(authority).authenticateMcpRuntime(deployment, version, release, "key");
    }

    @Test void callerCannotSelectAToolOutsideAppliedVersion() throws Exception {
        var request = request(true); request.put("actionId", "delete_everything");
        assertThatThrownBy(() -> service.execute("dep-1", "staging", "key", request)).hasMessageContaining("not authorized");
        verifyNoInteractions(connections);
    }

    @Test void receiverOwnershipFailureRemainsFailureNotInventedCancellation() throws Exception {
        when(connections.execute(any())).thenReturn(mapper.readTree("{\"success\":false,\"errorCode\":\"SUBSCRIPTION_ACCESS_DENIED\",\"message\":\"No authorized subscription matched.\"}"));
        var request = request(true); request.withObject("/params").put("subscriptionId", "another-users-subscription");
        var response = service.execute("dep-1", "staging", "key", request);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("errorCode").asText()).isEqualTo("SUBSCRIPTION_ACCESS_DENIED");
        verify(connections, times(1)).execute(any());
    }

    @Test void changedEndpointAuthorityCannotSilentlyReplacePublishedConnection() throws Exception {
        when(connections.resolve(anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(new McpRuntimeConnectionResolver.Resolved(mapper.readTree("{\"connectionRevision\":2}"), Map.of()));
        assertThatThrownBy(() -> service.execute("dep-1", "staging", "key", request(true))).hasMessageContaining("connection changed");
        verify(connections, never()).execute(any());
    }

    private ObjectNode request(boolean authenticated) throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree("{\"actionId\":\"cancel_subscription\",\"params\":{\"subscriptionId\":\"sub-1\"},\"trace\":{\"sessionId\":\"session-1\"}}");
        request.withObject("/trace").put("authenticated", authenticated).putObject("authContext")
            .put("subjectId", authenticated ? "user-1" : "session-1").put("deploymentId", "dep-1")
            .put("customerId", "company-1").put("tenantId", "tenant-1")
            .put("subjectType", authenticated ? "END_USER" : "ANONYMOUS_SESSION");
        return request;
    }
}
