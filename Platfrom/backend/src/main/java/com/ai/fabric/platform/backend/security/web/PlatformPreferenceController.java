package com.ai.fabric.platform.backend.security.web;

import com.ai.fabric.platform.backend.security.model.PlatformUserPreferences;
import com.ai.fabric.platform.backend.security.model.UpdatePlatformUserPreferencesRequest;
import com.ai.fabric.platform.backend.security.service.PlatformUserPreferenceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/preferences")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class PlatformPreferenceController {

    private final PlatformUserPreferenceService preferenceService;

    public PlatformPreferenceController(PlatformUserPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public PlatformUserPreferences getPreferences() {
        return preferenceService.getPreferences();
    }

    @PutMapping
    public PlatformUserPreferences updatePreferences(@RequestBody UpdatePlatformUserPreferencesRequest request) {
        return preferenceService.updatePreferences(request);
    }
}
