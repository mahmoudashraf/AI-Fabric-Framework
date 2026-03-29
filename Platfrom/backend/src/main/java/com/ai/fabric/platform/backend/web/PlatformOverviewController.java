package com.ai.fabric.platform.backend.web;

import com.ai.fabric.platform.backend.config.PlatformProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/platform")
public class PlatformOverviewController {

    private final PlatformProperties properties;

    public PlatformOverviewController(PlatformProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/overview")
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
                "validation",
                "verification",
                "railway-provisioning"
            ),
            "plannedScreens", List.of(
                "deployments",
                "actions",
                "knowledge",
                "providers",
                "security",
                "verification",
                "revisions",
                "diagnostics"
            )
        );
    }
}
