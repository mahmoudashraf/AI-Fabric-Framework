package com.ai.fabric.platform.backend.web;

import com.ai.fabric.platform.backend.config.PlatformProperties;
import com.ai.fabric.platform.backend.deployment.model.ExecuteRailwayWorkspaceCleanupRequest;
import com.ai.fabric.platform.backend.deployment.model.RailwayWorkspaceCleanupExecutionSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayWorkspaceCleanupSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightSummary;
import com.ai.fabric.platform.backend.model.PlatformDiagnosticsSummary;
import com.ai.fabric.platform.backend.model.PlatformRailwayLogsResponse;
import com.ai.fabric.platform.backend.deployment.service.RailwayPreflightService;
import com.ai.fabric.platform.backend.deployment.service.RailwayWorkspaceCleanupService;
import com.ai.fabric.platform.backend.service.PlatformDiagnosticsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/platform")
public class PlatformOverviewController {

    private final PlatformProperties properties;
    private final RailwayPreflightService railwayPreflightService;
    private final RailwayWorkspaceCleanupService railwayWorkspaceCleanupService;
    private final PlatformDiagnosticsService platformDiagnosticsService;

    public PlatformOverviewController(PlatformProperties properties,
                                      RailwayPreflightService railwayPreflightService,
                                      RailwayWorkspaceCleanupService railwayWorkspaceCleanupService,
                                      PlatformDiagnosticsService platformDiagnosticsService) {
        this.properties = properties;
        this.railwayPreflightService = railwayPreflightService;
        this.railwayWorkspaceCleanupService = railwayWorkspaceCleanupService;
        this.platformDiagnosticsService = platformDiagnosticsService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public Map<String, Object> overview() {
        return Map.of(
            "name", properties.name(),
            "stage", properties.stage(),
            "currentPhase", properties.currentPhase(),
            "capabilities", List.of(
                "deployment-management",
                "draft-version-release-lifecycle",
                "config-versioning",
                "draft-validation",
                "core-draft-editors",
                "release-evidence",
                "validation",
                "verification",
                "railway-provisioning",
                "platform-diagnostics",
                "live-verification-reruns",
                "artifact-delivery",
                "signed-artifact-delivery",
                "provisioning-provider-abstraction",
                "railway-plan-preview",
                "railway-api-provider",
                "railway-preflight",
                "structured-draft-editors",
                "platform-secrets",
                "async-release-execution",
                "release-progress-tracking",
                "release-state-polling",
                "duplicate-apply-guard",
                "runtime-admin-verification",
                "connector-admin-verification",
                "config-drift-verification",
                "neutral-railway-packaging",
                "platform-served-config-contract",
                "postgres-default-datasource",
                "flyway-schema-migrations",
                "explicit-bootstrap-profiles",
                "platform-api-key-auth",
                "role-based-platform-access-control",
                "audit-events",
                "platform-user-bootstrap",
                "cookie-session-authentication",
                "browser-session-login",
                "api-key-auth-fallback",
                "deployment-overview-summaries",
                "customer-lifecycle-navigation",
                "archived-deployment-state",
                "audited-archive-flow",
                "public-provisioning-api",
                "public-machine-client-auth",
                "public-create-idempotency",
                "public-apply-idempotency",
                "vertical-consumer-contract"
            ),
            "plannedScreens", List.of(
                "deployments",
                "actions",
                "knowledge",
                "providers",
                "security",
                "verification",
                "revisions",
                "diagnostics",
                "platform-diagnostics"
            )
        );
    }

    @GetMapping("/diagnostics")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PlatformDiagnosticsSummary diagnostics() {
        return platformDiagnosticsService.getSummary();
    }

    @GetMapping("/diagnostics/logs")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PlatformRailwayLogsResponse diagnosticsLogs(@RequestParam(defaultValue = "deployment") String source,
                                                       @RequestParam(required = false) Integer limit,
                                                       @RequestParam(required = false) String filter,
                                                       @RequestParam(required = false) String startDate,
                                                       @RequestParam(required = false) String endDate) {
        return platformDiagnosticsService.fetchLogs(source, limit, filter, startDate, endDate);
    }

    @GetMapping("/provisioning/railway/preflight")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public RailwayPreflightSummary railwayPreflight() {
        return railwayPreflightService.run();
    }

    @GetMapping("/provisioning/railway/workspace-cleanup")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public RailwayWorkspaceCleanupSummary railwayWorkspaceCleanup() {
        return railwayWorkspaceCleanupService.getSummary();
    }

    @PostMapping("/provisioning/railway/workspace-cleanup")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public RailwayWorkspaceCleanupExecutionSummary cleanupRailwayWorkspace(
        @Valid @RequestBody ExecuteRailwayWorkspaceCleanupRequest request
    ) {
        return railwayWorkspaceCleanupService.cleanupOrphans(request);
    }
}
