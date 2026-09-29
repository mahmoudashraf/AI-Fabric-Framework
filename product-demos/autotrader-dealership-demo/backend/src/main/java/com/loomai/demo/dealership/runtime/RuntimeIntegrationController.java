package com.loomai.demo.dealership.runtime;

import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.loomai.demo.dealership.inventory.VehicleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class RuntimeIntegrationController {

    private final DealershipDemoProperties properties;
    private final VehicleRepository vehicles;
    private final RuntimeSyncService syncService;
    private final String appVersion;
    private final String buildCommit;
    private final String buildTime;

    public RuntimeIntegrationController(DealershipDemoProperties properties,
                                        VehicleRepository vehicles,
                                        RuntimeSyncService syncService,
                                        @Value("${info.app.version:unknown}") String appVersion,
                                        @Value("${info.app.commit:unknown}") String buildCommit,
                                        @Value("${info.app.build-time:unknown}") String buildTime) {
        this.properties = properties;
        this.vehicles = vehicles;
        this.syncService = syncService;
        this.appVersion = appVersion;
        this.buildCommit = buildCommit;
        this.buildTime = buildTime;
    }

    @GetMapping("/api/public/runtime-descriptor")
    public ResponseEntity<?> descriptor() {
        var runtime = properties.getRuntime();
        if (!runtime.isEnabled() || !StringUtils.hasText(runtime.getBaseUrl())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "success", false,
                "ready", false,
                "errorCode", "RUNTIME_NOT_CONFIGURED",
                "message", "The dealership AI runtime is not configured."
            ));
        }
        String baseUrl = runtime.getBaseUrl().trim().replaceAll("/+$", "");
        return ResponseEntity.ok(Map.of(
            "success", true,
            "ready", true,
            "integrationMode", "public-runtime-anonymous",
            "chatBaseUrl", baseUrl,
            "runtimeRoutes", Map.of(
                "bootstrapUrl", runtime.getPublicBootstrapPath(),
                "queryUrl", runtime.getQueryPath(),
                "suggestionsUrl", runtime.getSuggestionsPath(),
                "authContextUrl", runtime.getAuthContextPath(),
                "shellConfigUrl", runtime.getShellConfigPath(),
                "conversationsUrl", runtime.getConversationsPath(),
                "conversationItemUrlTemplate", runtime.getConversationItemPathTemplate()
            ),
            "vectorSpace", runtime.getVectorSpace(),
            "dataNotice", "The browser receives only public runtime routes. Backend credentials remain server-side."
        ));
    }

    @GetMapping("/api/public/status")
    public Map<String, Object> publicStatus() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("status", "UP");
        response.put("service", "loomai-dealership-demo-backend");
        response.put("version", appVersion);
        response.put("commit", buildCommit);
        response.put("buildTime", buildTime);
        response.put("dealershipId", properties.getId());
        response.put("inventoryCount", vehicles.count());
        response.put("inventoryRefreshedAt", vehicles.latestSourceUpdate().orElse(Instant.EPOCH));
        response.put("runtimeConfigured", properties.getRuntime().isEnabled());
        response.put("sourceMode", "DEMONSTRATION_INVENTORY");
        return response;
    }

    @GetMapping("/api/staff/integration/status")
    public Map<String, Object> staffStatus() {
        return Map.of("success", true, "integration", syncService.status());
    }

    @PostMapping("/api/staff/integration/sync")
    public Map<String, Object> synchronize() {
        return Map.of("success", true, "run", syncService.synchronize());
    }
}
