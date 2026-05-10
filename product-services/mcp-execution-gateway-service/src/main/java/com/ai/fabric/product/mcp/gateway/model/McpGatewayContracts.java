package com.ai.fabric.product.mcp.gateway.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public final class McpGatewayContracts {

    private McpGatewayContracts() {
    }

    public record ActionExecuteRequest(
        String actionId,
        Map<String, Object> params,
        String idempotencyKey,
        Map<String, Object> trace,
        Map<String, Object> actionConfig
    ) {
    }

    public record ActionExecuteResponse(
        boolean success,
        String message,
        Map<String, Object> data,
        String errorCode,
        List<Map<String, Object>> pinnedTargets
    ) {
    }

    public record ServerRequest(
        String serverRef,
        Map<String, Object> server,
        Map<String, Object> trace
    ) {
    }

    public record ToolsCallRequest(
        String serverRef,
        String toolName,
        Map<String, Object> arguments,
        Map<String, Object> server,
        Map<String, Object> trace
    ) {
    }

    public record ToolsResultResponse(
        boolean success,
        String message,
        String serverRef,
        String toolName,
        JsonNode result,
        String errorCode
    ) {
    }

    public record DiscoveryRequest(
        String serverRef,
        Map<String, Object> server,
        Map<String, Object> trace,
        List<String> allowedTools
    ) {
    }

    public record DiscoveryResponse(
        boolean ready,
        String message,
        String serverRef,
        String endpointUrl,
        String protocolVersion,
        List<McpToolSummary> tools,
        String errorCode
    ) {
    }

    public record McpToolSummary(
        String name,
        String title,
        String description,
        JsonNode inputSchema,
        JsonNode outputSchema,
        String schemaHash
    ) {
    }

    public record ServerVerificationRequest(
        String serverRef,
        Map<String, Object> server,
        Map<String, Object> trace,
        List<ExpectedTool> expectedTools
    ) {
    }

    public record ExpectedTool(
        String name,
        String schemaHash,
        String schemaDriftPolicy
    ) {
    }

    public record ServerVerificationResponse(
        boolean ready,
        String message,
        String serverRef,
        String protocolVersion,
        List<ToolVerificationResult> tools,
        String errorCode
    ) {
    }

    public record ToolVerificationResult(
        String name,
        boolean present,
        boolean schemaMatches,
        String expectedSchemaHash,
        String actualSchemaHash,
        String schemaDriftPolicy,
        String status
    ) {
    }
}
