package com.easyluxury.service;

import com.easyluxury.dto.UserDto;
import com.easyluxury.entity.User;
import com.easyluxury.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@Profile("dev")
public class MockUserService {

    // Remove database dependency for mock service
    // private final UserRepository userRepository;
    
    // In-memory storage for mock users and sessions
    private final Map<String, User> mockUsers = new ConcurrentHashMap<>();
    private final Map<String, String> mockSessions = new ConcurrentHashMap<>(); // token -> userId
    private String currentMockUserId = null;

    // Mock user credentials
    private final Map<String, MockCredentials> mockCredentials = new ConcurrentHashMap<>(Map.of(
        "admin@easyluxury.com", new MockCredentials("admin@easyluxury.com", "admin123", User.UserRole.ADMIN)
    ));

    @PostConstruct
    public void initializeMockUsers() {
        log.info("Initializing mock users");
        
        // Create mock users
        createMockUser("mock-admin-001", "admin@easyluxury.com", "Admin", "User", User.UserRole.ADMIN);
        
        log.info("Initialized {} mock users", mockUsers.size());
    }


    private void createMockUser(String id, String email, String firstName, String lastName, User.UserRole role) {
        // Use deterministic UUID based on email to match database users
        UUID userId = UUID.nameUUIDFromBytes(("mock-user-" + email).getBytes());
        
        User user = User.builder()
                .id(userId)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .role(role)
                .supabaseId("mock-supabase-" + id)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        mockUsers.put(user.getId().toString(), user);
    }

    public List<UserDto> getAllMockUsers() {
        return mockUsers.values().stream()
                .map(this::convertToDto)
                .sorted(Comparator.comparing(UserDto::getEmail))
                .toList();
    }

    public List<UserDto> getMockUsersByRole(User.UserRole role) {
        return mockUsers.values().stream()
                .filter(user -> user.getRole() == role)
                .map(this::convertToDto)
                .sorted(Comparator.comparing(UserDto::getEmail))
                .toList();
    }

    public Map<String, Object> mockLogin(String email, String password) {
        MockCredentials credentials = mockCredentials.get(email);
        
        if (credentials == null || !credentials.password.equals(password)) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Find the mock user
        User user = mockUsers.values().stream()
                .filter(u -> u.getEmail().equals(email))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Mock user not found"));

        // Generate mock token
        String token = generateMockToken(user);
        
        // Store session
        mockSessions.put(token, user.getId().toString());
        currentMockUserId = user.getId().toString();

        log.info("Mock login successful for user: {}", email);

        return Map.of(
            "user", convertToDto(user),
            "token", token,
            "message", "Mock login successful"
        );
    }

    public Map<String, Object> quickLogin(User.UserRole role) {
        User user = mockUsers.values().stream()
                .filter(u -> u.getRole() == role)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No mock user found with role: " + role));

        MockCredentials credentials = mockCredentials.get(user.getEmail());
        if (credentials == null) {
            throw new IllegalArgumentException("No credentials found for user: " + user.getEmail());
        }

        return mockLogin(user.getEmail(), credentials.password);
    }

    public void mockLogout() {
        if (currentMockUserId != null) {
            // Remove all sessions for current user
            mockSessions.entrySet().removeIf(entry -> entry.getValue().equals(currentMockUserId));
            currentMockUserId = null;
            log.info("Mock logout successful");
        }
    }

    public UserDto getCurrentMockUser() {
        // This method should not be used directly - use getCurrentMockUser(String token) instead
        throw new UnsupportedOperationException("Use getCurrentMockUser(String token) instead");
    }
    
    public UserDto getCurrentMockUser(String token) {
        String userId = mockSessions.get(token);
        if (userId == null) {
            throw new IllegalStateException("Invalid or expired mock token");
        }

        User user = mockUsers.get(userId);
        if (user == null) {
            throw new IllegalStateException("Mock user not found for token");
        }

        return convertToDto(user);
    }
    
    public User getMockUserById(String userId) {
        User user = mockUsers.get(userId);
        if (user == null) {
            throw new IllegalStateException("Mock user not found for ID: " + userId);
        }
        return user;
    }

    public boolean isMockAuthenticated() {
        return currentMockUserId != null && mockUsers.containsKey(currentMockUserId);
    }

    public UserDto createMockUser(com.easyluxury.controller.MockUserController.CreateMockUserRequest request) {
        // Validate role
        User.UserRole role;
        try {
            role = User.UserRole.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + request.getRole());
        }

        // Check if email already exists
        boolean emailExists = mockUsers.values().stream()
                .anyMatch(user -> user.getEmail().equals(request.getEmail()));
        
        if (emailExists) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        // Create new mock user
        String id = "mock-user-" + System.currentTimeMillis();
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(role)
                .supabaseId("mock-supabase-" + id)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        mockUsers.put(id, user);

        // Add credentials
        mockCredentials.put(request.getEmail(), new MockCredentials(
            request.getEmail(), 
            request.getPassword(), 
            role
        ));

        log.info("Created mock user: {}", request.getEmail());
        return convertToDto(user);
    }

    public void deleteMockUser(String id) {
        User user = mockUsers.remove(id);
        if (user == null) {
            throw new IllegalArgumentException("Mock user not found: " + id);
        }

        // Remove credentials
        mockCredentials.remove(user.getEmail());

        // Remove sessions
        mockSessions.entrySet().removeIf(entry -> entry.getValue().equals(id));

        // Clear current user if it's the deleted user
        if (id.equals(currentMockUserId)) {
            currentMockUserId = null;
        }

        log.info("Deleted mock user: {}", user.getEmail());
    }

    public UserDto convertToDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .supabaseId(user.getSupabaseId())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private String generateMockToken(User user) {
        // Simple mock token generation
        return "mock-token-" + user.getId().toString().replace("-", "") + "-" + System.currentTimeMillis();
    }

    // Helper class for credentials
    private static class MockCredentials {
        final String email;
        final String password;
        final User.UserRole role;

        MockCredentials(String email, String password, User.UserRole role) {
            this.email = email;
            this.password = password;
            this.role = role;
        }
    }

}
