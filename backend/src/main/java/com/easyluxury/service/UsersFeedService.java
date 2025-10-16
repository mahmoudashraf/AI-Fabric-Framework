package com.easyluxury.service;

import com.easyluxury.entity.User;
import com.easyluxury.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Optional;

@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class UsersFeedService {

    private final UserRepository userRepository;

    // Mock user data - same as MockUserService for consistency
    private final Map<String, MockUserData> mockUserData = initializeMockUserData();

    @PostConstruct
    public void initializeUsersFeed() {
        log.info("UsersFeedService initialized for dev mode");
        
        // Always ensure mock users are synchronized with database
        // This ensures consistency between MockUserService and database
        log.info("Ensuring mock users are synchronized with database");
        feedUsersToDatabase();
    }

    private Map<String, MockUserData> initializeMockUserData() {
        Map<String, MockUserData> data = new HashMap<>();
        
        // Admin user
        data.put("admin@easyluxury.com", new MockUserData("admin@easyluxury.com", "Admin", "User", User.UserRole.ADMIN));
        
        // Regular users
        data.put("john.doe@easyluxury.com", new MockUserData("john.doe@easyluxury.com", "John", "Doe", User.UserRole.USER));
        data.put("jane.smith@easyluxury.com", new MockUserData("jane.smith@easyluxury.com", "Jane", "Smith", User.UserRole.USER));
        data.put("bob.wilson@easyluxury.com", new MockUserData("bob.wilson@easyluxury.com", "Bob", "Wilson", User.UserRole.USER));
        
        // Manager user
        data.put("manager@easyluxury.com", new MockUserData("manager@easyluxury.com", "Manager", "User", User.UserRole.MANAGER));
        
        return data;
    }

    /**
     * Feeds all mock users to the database
     */
    @Transactional
    public void feedUsersToDatabase() {
        log.info("Starting to feed {} mock users to database", mockUserData.size());
        
        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        
        for (Map.Entry<String, MockUserData> entry : mockUserData.entrySet()) {
            String email = entry.getKey();
            MockUserData userData = entry.getValue();
            
            // Generate deterministic UUID
            UUID deterministicId = UUID.nameUUIDFromBytes(("mock-user-" + email).getBytes());
            
            // Check if user already exists
            Optional<User> existingUser = userRepository.findByEmail(email);
            if (existingUser.isPresent()) {
                User user = existingUser.get();
                // If UUID is different, delete and recreate (JPA doesn't allow changing primary key)
                if (!user.getId().equals(deterministicId)) {
                    log.debug("Recreating user {} with correct UUID (old: {}, new: {})", email, user.getId(), deterministicId);
                    userRepository.delete(user);
                    User newUser = createUserFromMockData(userData);
                    userRepository.save(newUser);
                    updatedCount++;
                } else {
                    log.debug("User with email {} already exists with correct UUID, skipping", email);
                    skippedCount++;
                }
                continue;
            }
            
            // Create and save user
            User user = createUserFromMockData(userData);
            userRepository.save(user);
            createdCount++;
            
            log.debug("Created user: {} ({})", email, userData.role);
        }
        
        log.info("Users feed completed: {} created, {} updated, {} skipped", createdCount, updatedCount, skippedCount);
    }

    /**
     * Clears all users from the database (use with caution)
     */
    @Transactional
    public void clearAllUsers() {
        log.warn("Clearing all users from database");
        long count = userRepository.count();
        userRepository.deleteAll();
        log.info("Cleared {} users from database", count);
    }

    /**
     * Feeds users to database only if no users exist
     */
    @Transactional
    public void feedUsersIfEmpty() {
        if (!hasUsersInDatabase()) {
            log.info("No users found in database, feeding mock users");
            feedUsersToDatabase();
        } else {
            log.info("Users already exist in database, skipping feed");
        }
    }

    /**
     * Checks if there are any users in the database
     */
    public boolean hasUsersInDatabase() {
        return userRepository.count() > 0;
    }

    /**
     * Gets count of users in database
     */
    public long getUserCount() {
        return userRepository.count();
    }

    /**
     * Gets count of users by role
     */
    public long getUserCountByRole(User.UserRole role) {
        return userRepository.findAll().stream()
                .filter(user -> user.getRole() == role)
                .count();
    }

    /**
     * Creates a User entity from mock data
     */
    private User createUserFromMockData(MockUserData mockData) {
        // Use deterministic UUID based on email to match MockUserService
        UUID userId = UUID.nameUUIDFromBytes(("mock-user-" + mockData.email).getBytes());
        
        return User.builder()
                .id(userId)
                .email(mockData.email)
                .firstName(mockData.firstName)
                .lastName(mockData.lastName)
                .role(mockData.role)
                .supabaseId("mock-supabase-" + mockData.email.replace("@", "-").replace(".", "-"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Helper class for mock user data
     */
    private static class MockUserData {
        final String email;
        final String firstName;
        final String lastName;
        final User.UserRole role;

        MockUserData(String email, String firstName, String lastName, User.UserRole role) {
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.role = role;
        }
    }
}
