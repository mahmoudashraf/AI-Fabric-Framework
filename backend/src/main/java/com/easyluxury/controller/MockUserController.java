package com.easyluxury.controller;

import com.easyluxury.dto.UserDto;
import com.easyluxury.entity.User;
import com.easyluxury.service.MockUserService;
import com.easyluxury.service.UsersFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@RestController
@RequestMapping("/api/mock")
@RequiredArgsConstructor
@Tag(name = "Mock Users", description = "Mock user endpoints for testing")
@Profile("dev")
public class MockUserController {

    private final MockUserService mockUserService;
    private final UsersFeedService usersFeedService;

    @GetMapping("/users")
    @Operation(summary = "Get all mock users", description = "Returns a list of all available mock users for testing")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mock users retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<UserDto>> getAllMockUsers() {
        log.info("Fetching all mock users");
        List<UserDto> users = mockUserService.getAllMockUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/role/{role}")
    @Operation(summary = "Get mock users by role", description = "Returns mock users filtered by role")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mock users retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid role"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<UserDto>> getMockUsersByRole(@PathVariable String role) {
        log.info("Fetching mock users with role: {}", role);
        try {
            User.UserRole userRole = User.UserRole.valueOf(role.toUpperCase());
            List<UserDto> users = mockUserService.getMockUsersByRole(userRole);
            return ResponseEntity.ok(users);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role: {}", role);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Mock login", description = "Authenticate as a mock user for testing")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "400", description = "Invalid credentials"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> mockLogin(@RequestBody MockLoginRequest request) {
        log.info("Mock login attempt for email: {}", request.getEmail());
        try {
            Map<String, Object> response = mockUserService.mockLogin(request.getEmail(), request.getPassword());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Mock login failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/auth/login-as/{role}")
    @Operation(summary = "Quick login by role", description = "Quickly login as a mock user with specified role")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "400", description = "Invalid role"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> quickLogin(@PathVariable String role) {
        log.info("Quick login as role: {}", role);
        try {
            User.UserRole userRole = User.UserRole.valueOf(role.toUpperCase());
            Map<String, Object> response = mockUserService.quickLogin(userRole);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role for quick login: {}", role);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role: " + role));
        }
    }

    @PostMapping("/auth/logout")
    @Operation(summary = "Mock logout", description = "Logout from mock authentication")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Logout successful"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, String>> mockLogout() {
        log.info("Mock logout");
        mockUserService.mockLogout();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/auth/current")
    @Operation(summary = "Get current mock user", description = "Get the currently authenticated mock user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current user retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<UserDto> getCurrentMockUser(HttpServletRequest request) {
        log.info("Getting current mock user");
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).build();
            }
            
            String token = authHeader.substring(7);
            UserDto user = mockUserService.getCurrentMockUser(token);
            return ResponseEntity.ok(user);
        } catch (IllegalStateException e) {
            log.warn("No current mock user: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }

    @GetMapping("/auth/status")
    @Operation(summary = "Get authentication status", description = "Check if currently authenticated with mock user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully")
    })
    public ResponseEntity<Map<String, Object>> getAuthStatus() {
        boolean isAuthenticated = mockUserService.isMockAuthenticated();
        
        return ResponseEntity.ok(Map.of(
            "authenticated", isAuthenticated,
            "message", isAuthenticated ? "Mock authentication is active" : "Mock authentication is not active"
        ));
    }

    @PostMapping("/users/create")
    @Operation(summary = "Create mock user", description = "Create a new mock user for testing")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mock user created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid user data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<UserDto> createMockUser(@RequestBody CreateMockUserRequest request) {
        log.info("Creating mock user: {}", request.getEmail());
        try {
            UserDto user = mockUserService.createMockUser(request);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to create mock user: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/users/{id}")
    @Operation(summary = "Delete mock user", description = "Delete a mock user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mock user deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Mock user not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, String>> deleteMockUser(@PathVariable String id) {
        log.info("Deleting mock user: {}", id);
        try {
            mockUserService.deleteMockUser(id);
            return ResponseEntity.ok(Map.of("message", "Mock user deleted successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Mock user not found: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    // Users Feed Service endpoints
    @PostMapping("/feed/users")
    @Operation(summary = "Feed users to database", description = "Loads all mock users into the database")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Users fed to database successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> feedUsersToDatabase() {
        log.info("Feeding users to database");
        try {
            usersFeedService.feedUsersToDatabase();
            long userCount = usersFeedService.getUserCount();
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Users fed to database successfully");
            response.put("totalUsers", userCount);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to feed users to database", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @PostMapping("/feed/users/if-empty")
    @Operation(summary = "Feed users if database is empty", description = "Loads mock users into database only if no users exist")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operation completed successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> feedUsersIfEmpty() {
        log.info("Checking if users should be fed to database");
        try {
            usersFeedService.feedUsersIfEmpty();
            long userCount = usersFeedService.getUserCount();
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Feed operation completed");
            response.put("totalUsers", userCount);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to feed users to database", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @DeleteMapping("/feed/users")
    @Operation(summary = "Clear all users from database", description = "Removes all users from the database (use with caution)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Users cleared successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> clearAllUsers() {
        log.warn("Clearing all users from database");
        try {
            long countBefore = usersFeedService.getUserCount();
            usersFeedService.clearAllUsers();
            Map<String, Object> response = new HashMap<>();
            response.put("message", "All users cleared from database");
            response.put("usersRemoved", countBefore);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to clear users from database", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping("/feed/status")
    @Operation(summary = "Get database user status", description = "Returns information about users in the database")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully")
    })
    public ResponseEntity<Map<String, Object>> getDatabaseUserStatus() {
        log.info("Getting database user status");
        try {
            long totalUsers = usersFeedService.getUserCount();
            boolean hasUsers = usersFeedService.hasUsersInDatabase();
            
            Map<String, Object> status = new HashMap<>();
            status.put("hasUsers", hasUsers);
            status.put("totalUsers", totalUsers);
            status.put("adminUsers", usersFeedService.getUserCountByRole(User.UserRole.ADMIN));
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to get database user status", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    // Request DTOs
    public static class MockLoginRequest {
        private String email;
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class CreateMockUserRequest {
        private String email;
        private String firstName;
        private String lastName;
        private String role;
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
