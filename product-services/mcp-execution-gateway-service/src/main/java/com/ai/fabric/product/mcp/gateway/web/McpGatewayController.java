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
    private final Instant serverStartedAt;

    public McpGatewayController(McpGatewayExecutionService executionService,
                                McpGatewayProperties properties) {
        this.executionService = executionService;
        this.properties = properties;
        this.serverStartedAt = Instant.now();
    }

    @GetMapping("/api/admin/overview")
    public Map<String, Object> overview() {
        return Map.ofEntries(
            Map.entry("status", properties.internalApiKeyConfigured() ? "READY" : "BLOCKED"),
            Map.entry("message", properties.internalApiKeyConfigured()
                ? "MCP execution gateway is configured."
                : "MCP gateway internal API key is not configured."),
            Map.entry("appName", "MCP Execution Gateway Service"),
            Map.entry("serviceRef", properties.serviceRef()),
            Map.entry("productFamily", "MCP"),
            Map.entry("serviceKind", "MCP_EXECUTION_GATEWAY_SERVICE"),
            Map.entry("environmentScope", properties.environmentScope()),
            Map.entry("adminApiKeyConfigured", properties.internalApiKeyConfigured()),
            Map.entry("serverStartedAt", serverStartedAt.toString()),
            Map.entry("capabilities", java.util.List.of(
                "mcp.initialize",
                "mcp.tools.list",
                "mcp.tools.call",
                "mcp.servers.verify",
                "marketplace.mcp.discovery",
                "actions.adapterType.mcp-tool"
            )),
            Map.entry("notYetImplemented", java.util.List.of())
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
