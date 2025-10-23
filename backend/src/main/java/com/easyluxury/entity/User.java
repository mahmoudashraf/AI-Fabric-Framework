package com.easyluxury.entity;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIEmbedding;
import com.ai.infrastructure.annotation.AIKnowledge;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@AICapable(entityType = "user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(name = "supabase_id", unique = true)
    private String supabaseId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // AI-specific fields
    @Column(name = "ai_preferences")
    private String aiPreferences;

    @Column(name = "ai_behavior_profile")
    private String aiBehaviorProfile;

    @Column(name = "ai_interests")
    private String aiInterests;

    @Column(name = "search_vector")
    private String searchVector;

    @Column(name = "recommendation_score")
    private Double recommendationScore;

    @Column(name = "behavior_score")
    private Double behaviorScore;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @Column(name = "total_interactions")
    private Long totalInteractions = 0L;

    @Column(name = "ai_insights")
    private String aiInsights;

    public enum UserRole {
        ADMIN,
        USER,
        MANAGER
    }

    // Helper methods for AI operations
    
    /**
     * Get the primary searchable text for AI operations
     */
    public String getSearchableText() {
        StringBuilder text = new StringBuilder();
        text.append(firstName != null ? firstName : "").append(" ");
        text.append(lastName != null ? lastName : "").append(" ");
        text.append(email).append(" ");
        if (aiPreferences != null) {
            text.append(aiPreferences).append(" ");
        }
        if (aiInterests != null) {
            text.append(aiInterests).append(" ");
        }
        if (aiBehaviorProfile != null) {
            text.append(aiBehaviorProfile).append(" ");
        }
        if (aiInsights != null) {
            text.append(aiInsights).append(" ");
        }
        return text.toString().trim();
    }
    
    /**
     * Get metadata for AI operations
     */
    public Map<String, Object> getAIMetadata() {
        return Map.of(
            "id", id.toString(),
            "email", email,
            "firstName", firstName != null ? firstName : "",
            "lastName", lastName != null ? lastName : "",
            "role", role.toString(),
            "totalInteractions", totalInteractions != null ? totalInteractions : 0L,
            "behaviorScore", behaviorScore != null ? behaviorScore : 0.0,
            "recommendationScore", recommendationScore != null ? recommendationScore : 0.0,
            "lastActivityAt", lastActivityAt != null ? lastActivityAt.toString() : ""
        );
    }
}
