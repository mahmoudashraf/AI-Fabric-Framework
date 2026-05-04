package com.ai.fabric.product.mcp.gateway.web;

import com.ai.fabric.product.mcp.gateway.config.McpGatewayProperties;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ActionExecuteRequest;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ActionExecuteResponse;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.DiscoveryRequest;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.DiscoveryResponse;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ServerRequest;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ServerVerificationRequest;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ServerVerificationResponse;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ToolsCallRequest;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ToolsResultResponse;
import com.ai.fabric.product.mcp.gateway.service.McpGatewayExecutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping
public class McpGatewayController {

    private final McpGatewayExecutionService executionService;
    private final McpGatewayProperties properties;

    public McpGatewayController(McpGatewayExecutionService executionService,
                                McpGatewayProperties properties) {
        this.executionService = executionService;
        this.properties = properties;
    }

    @GetMapping("/api/admin/overview")
    public Map<String, Object> overview() {
        return Map.of(
            "status", properties.internalApiKeyConfigured() ? "READY" : "BLOCKED",
            "message", properties.internalApiKeyConfigured()
                ? "MCP execution gateway is configured."
                : "MCP gateway internal API key is not configured.",
            "appName", "MCP Execution Gateway Service",
            "productFamily", "MCP",
            "serviceKind", "MCP_EXECUTION_GATEWAY_SERVICE",
            "environmentScope", properties.environmentScope(),
            "adminApiKeyConfigured", properties.internalApiKeyConfigured(),
            "serverStartedAt", Instant.now().toString(),
            "capabilities", java.util.List.of(
                "mcp.initialize",
                "mcp.tools.list",
                "mcp.tools.call",
                "mcp.servers.verify",
                "marketplace.mcp.discovery",
                "actions.adapterType.mcp-tool"
            ),
            "notYetImplemented", java.util.List.of()
        );
    }

    @PostMapping("/api/internal/mcp/actions/execute")
    public ActionExecuteResponse execute(@RequestBody ActionExecuteRequest request) {
        return executionService.executeAction(request);
    }

    @PostMapping("/api/internal/mcp/servers/tools/list")
    public ToolsResultResponse toolsList(@RequestBody ServerRequest request) {
        return executionService.initializeAndList(request);
    }

    @PostMapping("/api/internal/mcp/servers/verify")
    public ServerVerificationResponse verify(@RequestBody ServerVerificationRequest request) {
        return executionService.verify(request);
    }

    @PostMapping("/api/internal/mcp/tools/call")
    public ToolsResultResponse toolsCall(@RequestBody ToolsCallRequest request) {
        return executionService.toolsCall(request);
    }

    @PostMapping("/api/internal/mcp/import/discover")
    public DiscoveryResponse discover(@RequestBody DiscoveryRequest request) {
        return executionService.discover(request);
    }
}
