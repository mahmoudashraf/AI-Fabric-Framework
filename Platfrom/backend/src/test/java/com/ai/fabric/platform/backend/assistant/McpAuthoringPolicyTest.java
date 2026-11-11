package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class McpAuthoringPolicyTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private JsonNode current() throws Exception { return mapper.readTree("""
        {"actionsConfig":{"actions":[{"name":"lookup","adapterType":"mcp-tool","accessMode":"READ",
         "execution":{"mcp":{"connectionId":"one","toolName":"lookup"}},"inputSchema":{"type":"object"},
         "params":[{"name":"actor","resolveFrom":{"source":"RUNTIME_CONTEXT","field":"userId"}}]}],
         "mcpServers":[{"serverRef":"server-one","connectionId":"one"}]}}
        """); }
    @Test void localWordingMayChangeWithoutRewritingExecution() throws Exception {
        var before = current(); var candidate = before.deepCopy();
        ((ObjectNode) candidate.at("/actionsConfig/actions/0")).put("description", "Find your record.");
        assertThatCode(() -> McpAuthoringPolicy.requirePreserved(before, candidate)).doesNotThrowAnyException();
    }
    @Test void catalogueInstructionsCannotSwitchAccountOrGrantAnonymousAccess() throws Exception {
        for (String path : new String[]{"/actionsConfig/actions/0/execution/mcp", "/actionsConfig/actions/0"}) {
            var before = current(); var candidate = before.deepCopy();
            if (path.endsWith("mcp")) ((ObjectNode) candidate.at(path)).put("connectionId", "foreign");
            else ((ObjectNode) candidate.at(path)).put("anonymousAllowed", true);
            assertThatThrownBy(() -> McpAuthoringPolicy.requirePreserved(before, candidate)).hasMessageContaining("owned connection/action controls");
        }
    }
    @Test void trustedActorResolutionCannotBeRemoved() throws Exception {
        var before = current(); var candidate = before.deepCopy();
        ((ObjectNode) candidate.at("/actionsConfig/actions/0/params/0")).remove("resolveFrom");
        assertThatThrownBy(() -> McpAuthoringPolicy.requirePreserved(before, candidate)).hasMessageContaining("owned connection/action controls");
    }
}
