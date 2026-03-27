package com.ai.fabric.realapps.chat.migration.web;

import com.ai.fabric.realapps.chat.migration.client.RestConnectorVectorClearClient;
import com.ai.fabric.realapps.chat.migration.service.DemoResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Demo maintenance endpoints for UI workflows:
 * - clear domain (H2) data
 * - optionally clear runtime vectors (via REST connector proxy)
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class DemoResetAdminController {

    private final DemoResetService demoResetService;
    private final RestConnectorVectorClearClient vectorClearClient;

    @Value("${connector.admin.auth.api-key:}")
    private String adminApiKey;

    @Value("${connector.admin.auth.api-key-header:X-AIFABRIC-API-KEY}")
    private String adminApiKeyHeader;

    @Value("${connector.admin.auth.enabled:false}")
    private boolean adminAuthEnabled;

    /**
     * Preferred endpoint name.
     */
    @PostMapping("/demo/reset")
    public ResponseEntity<?> reset(@RequestBody(required = false) ResetRequest request, HttpServletRequest httpRequest) {
        return handleReset(request, httpRequest);
    }

    /**
     * Backwards-compatible path (matches earlier demos).
     */
    @PostMapping("/migration/clear")
    public ResponseEntity<?> clearMigration(@RequestBody(required = false) ResetRequest request, HttpServletRequest httpRequest) {
        return handleReset(request, httpRequest);
    }

    private ResponseEntity<?> handleReset(ResetRequest request, HttpServletRequest httpRequest) {
        if (adminAuthEnabled && !AdminAuth.isAuthorized(adminApiKey, adminApiKeyHeader, httpRequest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false,
                "message", "Unauthorized"
            ));
        }

        if (request == null || !Boolean.TRUE.equals(request.getConfirm())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "success", false,
                "message", "confirm=true is required"
            ));
        }

        boolean clearConnectorData = request.getClearConnectorData() == null || request.getClearConnectorData();
        boolean clearRuntimeVectors = request.getClearRuntimeVectors() == null || request.getClearRuntimeVectors();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);

        if (clearConnectorData) {
            out.put("connector", demoResetService.clearConnectorData());
        } else {
            out.put("connector", Map.of("success", true, "skipped", true));
        }

        if (clearRuntimeVectors) {
            out.put("runtime", vectorClearClient.clearRuntimeVectors("ecommerce-store-reset"));
        } else {
            out.put("runtime", Map.of("success", true, "skipped", true));
        }

        return ResponseEntity.ok(out);
    }

    @Data
    public static class ResetRequest {
        @NotNull
        private Boolean confirm;
        private Boolean clearConnectorData = true;
        private Boolean clearRuntimeVectors = true;
    }
}
