package com.ai.fabric.runtime.web.admin;

import com.ai.infrastructure.rag.VectorDatabaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin endpoints intended for demo/runtime maintenance.
 *
 * <p>Note: the "commerce demo reset" controller lives in a Real_App. This runtime-only version
 * focuses on clearing the vector index so you can re-index cleanly.</p>
 */
@RestController
@RequestMapping("/api/admin/migration")
@RequiredArgsConstructor
public class MigrationAdminController {

    private static final Logger log = LoggerFactory.getLogger(MigrationAdminController.class);

    private final VectorDatabaseService vectorDatabaseService;

    @Value("${app.admin.api-key:}")
    private String adminApiKey;

    @Value("${app.admin.api-key-header:X-ADMIN-API-KEY}")
    private String adminApiKeyHeader;

    /**
     * Clear vectors from the configured vector DB.
     *
     * <p>To avoid accidental wipes on public deployments, this requires {@code confirm=true}
     * either in JSON body or as a query param.</p>
     */
    @PostMapping("/clear")
    public ResponseEntity<?> clear(@RequestParam(value = "confirm", required = false) Boolean confirmParam,
                                   @RequestParam(value = "clearVectors", required = false) Boolean clearVectorsParam,
                                   @RequestBody(required = false) ClearRequest body,
                                   HttpServletRequest httpRequest) {
        if (!AdminAuth.isAuthorized(adminApiKey, adminApiKeyHeader, httpRequest)) {
            log.warn("Unauthorized admin request: path=/api/admin/migration/clear remoteAddr={}",
                httpRequest != null ? httpRequest.getRemoteAddr() : "unknown");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false,
                "message", "Unauthorized"
            ));
        }

        Boolean confirm = body != null ? body.getConfirm() : null;
        if (confirm == null) {
            confirm = confirmParam;
        }
        if (!Boolean.TRUE.equals(confirm)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "success", false,
                "message", "confirm=true is required"
            ));
        }

        Boolean clearVectors = body != null ? body.getClearVectors() : null;
        if (clearVectors == null) {
            clearVectors = clearVectorsParam;
        }
        if (clearVectors == null) {
            clearVectors = true;
        }

        String reason = body != null ? body.getReason() : null;
        if (StringUtils.hasText(reason)) {
            reason = reason.trim();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clearedVectors", false);
        result.put("removedVectors", 0L);

        if (Boolean.TRUE.equals(clearVectors)) {
            log.info("Clearing vectors requested (reason={}, remoteAddr={})",
                StringUtils.hasText(reason) ? reason : null,
                httpRequest != null ? httpRequest.getRemoteAddr() : "unknown");
            long removed = vectorDatabaseService.clearVectors();
            result.put("clearedVectors", true);
            result.put("removedVectors", removed);
        }

        String message = "Migration clear completed";
        if (StringUtils.hasText(vectorDatabaseService.getClass().getSimpleName())) {
            message = message + " (" + vectorDatabaseService.getClass().getSimpleName() + ")";
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", message,
            "result", result
        ));
    }

    @Data
    public static class ClearRequest {
        @NotNull
        private Boolean confirm;
        private Boolean clearVectors = true;
        private String reason;
    }
}
