package com.easyluxury.controller;

import com.easyluxury.dto.UserDto;
import com.easyluxury.entity.User;
import com.easyluxury.facade.UserFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserFacade userFacade;

    @GetMapping("/me")
    @Operation(summary = "Get current user profile", description = "Returns the authenticated user's profile information")
    @ApiResponse(responseCode = "200", description = "User profile retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal User user) {
        UserDto userDto = userFacade.getCurrentUserProfile(user);
        return ResponseEntity.ok(userDto);
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile", description = "Updates the authenticated user's profile information")
    @ApiResponse(responseCode = "200", description = "User profile updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request data")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    public ResponseEntity<UserDto> updateCurrentUser(
            @AuthenticationPrincipal User user,
            @RequestBody UserDto userDto
    ) {
        UserDto updated = userFacade.updateUserProfile(user, userDto);
        return ResponseEntity.ok(updated);
    }
}
