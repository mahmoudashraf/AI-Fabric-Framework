package com.ai.fabric.platform.backend.security.web;

import com.ai.fabric.platform.backend.security.model.CreatePlatformUserRequest;
import com.ai.fabric.platform.backend.security.model.PlatformUserSummary;
import com.ai.fabric.platform.backend.security.model.ResetPlatformUserPasswordRequest;
import com.ai.fabric.platform.backend.security.model.UpdatePlatformUserRequest;
import com.ai.fabric.platform.backend.security.service.PlatformUserAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/users")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformUserAdminController {

    private final PlatformUserAdminService platformUserAdminService;

    public PlatformUserAdminController(PlatformUserAdminService platformUserAdminService) {
        this.platformUserAdminService = platformUserAdminService;
    }

    @GetMapping
    public List<PlatformUserSummary> listUsers() {
        return platformUserAdminService.listUsers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformUserSummary createUser(@Valid @RequestBody CreatePlatformUserRequest request) {
        return platformUserAdminService.createUser(request);
    }

    @PutMapping("/{userId}")
    public PlatformUserSummary updateUser(@PathVariable String userId,
                                          @Valid @RequestBody UpdatePlatformUserRequest request) {
        return platformUserAdminService.updateUser(userId, request);
    }

    @PostMapping("/{userId}/reset-password")
    public PlatformUserSummary resetPassword(@PathVariable String userId,
                                             @Valid @RequestBody ResetPlatformUserPasswordRequest request) {
        return platformUserAdminService.resetPassword(userId, request.password());
    }
}
