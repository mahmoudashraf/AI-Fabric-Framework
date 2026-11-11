package com.ai.fabric.platform.backend.mcp.connection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.fasterxml.jackson.databind.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;

class McpConnectionReadinessAdapterTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final McpConnectionRepository connections = mock(McpConnectionRepository.class);
    private final McpDiscoverySnapshotRepository snapshots = mock(McpDiscoverySnapshotRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final McpConnectionExecutionResolver resolver = mock(McpConnectionExecutionResolver.class);
    private final McpConnectionGatewayClient gateway = mock(McpConnectionGatewayClient.class);
    private final McpConnectionReadinessAdapter service = new McpConnectionReadinessAdapter(connections, snapshots, deployments, resolver, gateway, mapper);
    private final McpConnectionEntity connection = new McpConnectionEntity();

    @BeforeEach void setup() throws Exception {
        connection.setId("c"); connection.setDeploymentId("dep"); connection.setCustomerId("company"); connection.setName("Business tools");
        connection.setStatus("ACTIVE"); connection.setAuthStatus("CONFIGURED"); connection.setRevision(1);
        var deployment = new DeploymentEntity(); deployment.setId("dep"); deployment.setCustomerId("company");
        when(deployments.findById("dep")).thenReturn(Optional.of(deployment));
        when(connections.findById("c")).thenReturn(Optional.of(connection)); when(connections.findLocked("c")).thenReturn(Optional.of(connection));
        when(resolver.resolveExecution(eq("c"), eq("dep"), eq("prod"), anyString(), anyString())).thenAnswer(a -> new McpConnectionContracts.ExecutionResolution(
            mapper.readTree("{\"serverRef\":\"s\",\"connectionRevision\":1,\"auth\":{\"mode\":\"NONE\"}}"), Map.of()));
        when(gateway.verify(anyString(), any(), anyMap(), any())).thenReturn(mapper.readTree("{\"ready\":true}"));
    }

    @Test void pageInspectionIsLocalAndDoesNotRefreshGrantOrContactGateway() throws Exception {
        var result = service.inspect("dep", actions());
        assertThat(result).hasSize(1); assertThat(result.getFirst().status()).isEqualTo("NOT_TESTED");
        verifyNoInteractions(resolver, gateway);
    }
    @Test void applyGroupsToolsByConnectionAndVerifiesOnlyMetadataWithExactHashes() throws Exception {
        service.verify("dep", "prod", actions());
        verify(resolver, times(1)).resolveExecution(eq("c"), eq("dep"), eq("prod"), anyString(), anyString());
        verify(gateway, times(1)).verify(eq("s"), argThat(server -> server.path("allowedTools").size() == 2), eq(Map.of()),
            argThat(tools -> tools.size() == 2 && "hash-search".equals(tools.get(0).path("schemaHash").asText()) && "hash-book".equals(tools.get(1).path("schemaHash").asText())));
        verify(gateway, never()).execute(anyMap()); verify(gateway, never()).discover(anyString(), any(), anyMap());
        assertThat(connection.getLastVerificationJson()).contains("\"ready\":true", "prod", "hash-book");
        assertThat(service.inspect("dep", actions()).getFirst().status()).isEqualTo("VERIFIED");
    }
    @Test void changedAuthorityAndMetadataFailureStopBeforeBusinessCallsAndRetainFailureEvidence() throws Exception {
        connection.setRevision(2);
        assertThat(service.inspect("dep", actions()).getFirst().status()).isEqualTo("FAILED");
        connection.setRevision(1); when(gateway.verify(anyString(), any(), anyMap(), any())).thenReturn(mapper.readTree("{\"ready\":false}"));
        assertThatThrownBy(() -> service.verify("dep", "prod", actions())).isInstanceOf(ResponseStatusException.class);
        assertThat(connection.getLastVerificationJson()).contains("\"ready\":false");
        verify(gateway, never()).execute(anyMap());
    }
    @Test void foreignConnectionAndDisabledActionsDoNotExposeOrVerifyAnotherCustomer() throws Exception {
        connection.setCustomerId("other-company");
        assertThat(service.inspect("dep", actions()).getFirst().status()).isEqualTo("FAILED");
        JsonNode actions = mapper.readTree("{\"actions\":[{\"name\":\"disabled\",\"enabled\":false,\"adapterType\":\"mcp-tool\",\"execution\":{\"mcp\":{\"connectionId\":\"c\"}}}]}");
        service.verify("dep", "prod", actions); verifyNoInteractions(resolver, gateway);
    }
    private JsonNode actions() throws Exception { return mapper.readTree("""
        {"actions":[{"name":"search","adapterType":"mcp-tool","execution":{"mcp":{"connectionId":"c","connectionRevision":1,"serverRef":"s","toolName":"search","toolSchemaHash":"hash-search"}}},
          {"name":"book","adapterType":"mcp-tool","execution":{"mcp":{"connectionId":"c","connectionRevision":1,"serverRef":"s","toolName":"book","toolSchemaHash":"hash-book"}}}]}
        """); }
}
