package com.ai.fabric.runtime.web.admin;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import com.ai.infrastructure.intent.action.connector.AIActionCatalogProperties;
import com.ai.infrastructure.rag.VectorDatabaseService;
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
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class RuntimeAdminOverviewController {

    private static final Logger log = LoggerFactory.getLogger(RuntimeAdminOverviewController.class);

    private final AIActionRegistry actionRegistry;
    private final AIActionCatalogProperties actionCatalogProperties;
    private final AIEntityConfigurationLoader entityConfigurationLoader;
    private final VectorDatabaseService vectorDatabaseService;

    @Value("${ai.config.default-file:ai-entity-config.yml}")
    private String entityConfigLocation;

    @Value("${ai.prompts.deployment.config-file:}")
    private String promptConfigLocation;

    @Value("${app.admin.api-key:}")
    private String adminApiKey;

    @Value("${app.admin.api-key-header:X-ADMIN-API-KEY}")
    private String adminApiKeyHeader;

    @GetMapping("/overview")
    public ResponseEntity<?> overview(HttpServletRequest httpRequest) {
        if (!AdminAuth.isAuthorized(adminApiKey, adminApiKeyHeader, httpRequest)) {
            log.warn("Unauthorized admin request: path=/api/admin/overview remoteAddr={}",
                httpRequest != null ? httpRequest.getRemoteAddr() : "unknown");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false,
                "message", "Unauthorized"
            ));
        }

        List<AIActionMetaData> actions = actionRegistry != null ? actionRegistry.getAllMetadata() : List.of();
        long actionCount = actions.stream()
            .filter(action -> action != null && StringUtils.hasText(action.getName()))
            .count();

        Set<String> entityTypes = entityConfigurationLoader != null
            ? entityConfigurationLoader.getSupportedEntityTypes()
            : Set.of();

        List<Map<String, Object>> sources = actionCatalogProperties != null
            ? actionCatalogProperties.getSources().stream()
                .map(source -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("type", source.getType() != null ? source.getType().name() : null);
                    item.put("path", source.getPath());
                    item.put("optional", source.isOptional());
                    return item;
                })
                .toList()
            : List.of();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("entityConfigLocation", entityConfigLocation);
        body.put("promptConfigLocation", promptConfigLocation);
        body.put("actionCatalogSources", sources);
        body.put("actionsCount", actionCount);
        body.put("supportedEntityTypes", entityTypes);
        body.put("vectorDb", vectorDatabaseService.getClass().getSimpleName());
        body.put("supportsVectorScan", vectorDatabaseService.supportsVectorScan());
        body.put("vectorScope", vectorDatabaseService.adminDiagnostics());
        return ResponseEntity.ok(body);
    }
}
