package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.action.service.ShopifyBridgeActionExecutionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/admin/stores")
public class ShopifyBridgeActionsController {

    private final ShopifyBridgeActionExecutionService actionExecutionService;

    public ShopifyBridgeActionsController(ShopifyBridgeActionExecutionService actionExecutionService) {
        this.actionExecutionService = actionExecutionService;
    }

    @PostMapping("/{shopDomain}/actions/execute")
    public ResponseEntity<ShopifyBridgeActionResult> execute(@PathVariable String shopDomain,
                                                             @RequestBody(required = false) ShopifyBridgeActionExecuteRequest request,
                                                             HttpServletRequest servletRequest) {
        ShopifyBridgeActionResult result = actionExecutionService.execute(
            shopDomain,
            withRequestContext(request, servletRequest)
        );
        return ResponseEntity.status(statusFor(result)).body(result);
    }

    @GetMapping("/{shopDomain}/mcp/readiness")
    public ResponseEntity<Map<String, Object>> mcpReadiness(@PathVariable String shopDomain) {
        Map<String, Object> result = actionExecutionService.mcpReadiness(shopDomain);
        return ResponseEntity.status(Boolean.TRUE.equals(result.get("ready")) ? HttpStatus.OK : HttpStatus.CONFLICT).body(result);
    }

    private HttpStatus statusFor(ShopifyBridgeActionResult result) {
        if (result == null || result.success()) {
            return HttpStatus.OK;
        }
        return switch (result.errorCode() == null ? "" : result.errorCode()) {
            case "INVALID_REQUEST" -> HttpStatus.BAD_REQUEST;
            case "NOT_FOUND", "ACTION_NOT_SUPPORTED", "OWNED_RESOURCE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "NOT_CONNECTED",
                 "PRODUCT_SELECTION_REQUIRED",
                 "VARIANT_SELECTION_REQUIRED",
                 "SHOPPER_SESSION_REQUIRED",
                 "CUSTOMER_SESSION_REQUIRED",
                 "CUSTOMER_ACCOUNT_AUTH_REQUIRED",
                 "CUSTOMER_ACCOUNT_MCP_NOT_CONFIGURED",
                 "CHECKOUT_MCP_NOT_CONFIGURED",
                 "CHECKOUT_TERMINAL_OPERATION_DISABLED",
                 "OWNED_RESOURCE_ACTION_FAILED",
                 "MCP_TOOL_REPORTED_ERROR",
                 "GOVERNED_ACTION_REJECTED" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }

    private ShopifyBridgeActionExecuteRequest withRequestContext(ShopifyBridgeActionExecuteRequest request,
                                                                 HttpServletRequest servletRequest) {
        if (request == null) {
            request = new ShopifyBridgeActionExecuteRequest(null, Map.of(), null, Map.of());
        }
        Map<String, Object> trace = new LinkedHashMap<>(request.trace() == null ? Map.of() : request.trace());
        trace.putIfAbsent("buyerIp", clientIp(servletRequest));
        String userAgent = servletRequest == null ? null : servletRequest.getHeader("User-Agent");
        if (userAgent != null && !userAgent.isBlank()) {
            trace.putIfAbsent("buyerUserAgent", userAgent.trim());
        }
        return new ShopifyBridgeActionExecuteRequest(
            request.actionId(),
            request.params(),
            request.idempotencyKey(),
            trace
        );
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        for (String header : new String[] {"X-Forwarded-For", "X-Real-IP"}) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return value.split(",", 2)[0].trim();
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote.trim();
    }
}
