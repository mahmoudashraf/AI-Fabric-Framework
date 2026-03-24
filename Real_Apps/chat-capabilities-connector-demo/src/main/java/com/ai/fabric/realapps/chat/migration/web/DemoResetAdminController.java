package com.ai.fabric.realapps.chat.migration.web;

import com.ai.fabric.realapps.chat.migration.client.RuntimeVectorClearClient;
import com.ai.fabric.realapps.chat.migration.service.DemoResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single "reset demo" endpoint for the connector:
 * - clears connector demo data (H2)
 * - optionally clears runtime vectors by calling runtime admin endpoint
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class DemoResetAdminController {

    private final DemoResetService demoResetService;
    private final RuntimeVectorClearClient runtimeVectorClearClient;

    @Value("${connector.auth.api-key:}")
    private String connectorAdminApiKey;

    @Value("${connector.auth.api-key-header:X-AIFABRIC-API-KEY}")
    private String connectorAdminApiKeyHeader;

    /**
     * Some deployments want to keep {@code /actions/execute} protected while leaving demo reset endpoints open.
     *
     * <p>If set to {@code false}, this controller will skip the API-key check even if
     * {@code connector.auth.api-key} is configured.</p>
     */
    @Value("${connector.admin.auth.enabled:true}")
    private boolean connectorAdminAuthEnabled;

    /**
     * Preferred endpoint name.
     */
    @PostMapping("/demo/reset")
    public ResponseEntity<?> reset(@RequestBody ResetRequest request, HttpServletRequest httpRequest) {
        return handleReset(request, httpRequest);
    }

    /**
     * Backwards-compatible path (matches the combined demo app).
     */
    @PostMapping("/migration/clear")
    public ResponseEntity<?> clearMigration(@RequestBody ResetRequest request, HttpServletRequest httpRequest) {
        return handleReset(request, httpRequest);
    }

    private ResponseEntity<?> handleReset(ResetRequest request, HttpServletRequest httpRequest) {
        if (connectorAdminAuthEnabled
            && !AdminAuth.isAuthorized(connectorAdminApiKey, connectorAdminApiKeyHeader, httpRequest)) {
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
            out.put("runtime", runtimeVectorClearClient.clearRuntimeVectors("connector-demo-reset"));
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
