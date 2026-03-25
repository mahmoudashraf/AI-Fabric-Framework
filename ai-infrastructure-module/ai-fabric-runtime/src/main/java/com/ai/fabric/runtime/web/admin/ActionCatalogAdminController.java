package com.ai.fabric.runtime.web.admin;

import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin endpoints to inspect the loaded action catalog.
 *
 * <p>Useful for debugging "actions not loaded" issues in deployments where {@code ai-actions.yml}
 * might not be mounted/loaded.</p>
 */
@RestController
@RequestMapping("/api/admin/actions")
@RequiredArgsConstructor
public class ActionCatalogAdminController {

    private static final Logger log = LoggerFactory.getLogger(ActionCatalogAdminController.class);

    private final AIActionRegistry actionRegistry;

    @Value("${app.admin.api-key:}")
    private String adminApiKey;

    @Value("${app.admin.api-key-header:X-ADMIN-API-KEY}")
    private String adminApiKeyHeader;

    @GetMapping("/overview")
    public ResponseEntity<?> overview(HttpServletRequest httpRequest) {
        if (!AdminAuth.isAuthorized(adminApiKey, adminApiKeyHeader, httpRequest)) {
            log.warn("Unauthorized admin request: path=/api/admin/actions/overview remoteAddr={}",
                httpRequest != null ? httpRequest.getRemoteAddr() : "unknown");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false,
                "message", "Unauthorized"
            ));
        }

        List<AIActionMetaData> actions = actionRegistry != null ? actionRegistry.getAllMetadata() : List.of();
        actions = actions.stream()
            .filter(a -> a != null && StringUtils.hasText(a.getName()))
            .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("count", actions.size());
        body.put("actions", actions);
        return ResponseEntity.ok(body);
    }
}

