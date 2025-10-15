package com.easyluxury.controller;

import com.easyluxury.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminController {

    @GetMapping("/dashboard")
    @Operation(summary = "Get admin dashboard", description = "Returns admin dashboard information")
    @ApiResponse(responseCode = "200", description = "Dashboard data retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
    public ResponseEntity<Map<String, Object>> getDashboard(
            @AuthenticationPrincipal User admin
    ) {
        return ResponseEntity.ok(Map.of(
            "message", "Admin dashboard",
            "admin", Map.of(
                "id", admin.getId(),
                "email", admin.getEmail(),
                "role", admin.getRole()
            )
        ));
    }
}
