package com.ai.fabric.platform.backend.secret.web;

import com.ai.fabric.platform.backend.secret.model.PlatformSecretSummary;
import com.ai.fabric.platform.backend.secret.model.UpdatePlatformSecretRequest;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/secrets")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class PlatformSecretController {

    private final PlatformSecretService platformSecretService;

    public PlatformSecretController(PlatformSecretService platformSecretService) {
        this.platformSecretService = platformSecretService;
    }

    @GetMapping
    public List<PlatformSecretSummary> listSecrets() {
        return platformSecretService.listSecrets();
    }

    @PutMapping("/{name}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PlatformSecretSummary updateSecret(@PathVariable String name,
                                              @RequestBody UpdatePlatformSecretRequest request) {
        return platformSecretService.updateSecret(name, request.value());
    }

    @DeleteMapping("/{name}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PlatformSecretSummary clearSecret(@PathVariable String name) {
        return platformSecretService.clearSecret(name);
    }
}
