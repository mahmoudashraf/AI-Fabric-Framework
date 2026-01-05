package com.ai.infrastructure.chat.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single user-AI exchange within a conversation.
 */
@Entity
@Table(
    name = "chat_turns",
    indexes = {
        @Index(name = "idx_session_timestamp", columnList = "session_id,timestamp")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatTurn {

    /**
     * Turn identifier (database primary key).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User's query/message.
     */
    @Column(nullable = false, columnDefinition = "TEXT", name = "user_query")
    private String userQuery;

    /**
     * AI's response.
     */
    @Column(nullable = false, columnDefinition = "TEXT", name = "ai_response")
    private String aiResponse;

    /**
     * When this exchange occurred.
     */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /**
     * Entity IDs used in RAG/relationship queries for this turn.
     */
    @ElementCollection
    @CollectionTable(
        name = "turn_entity_refs",
        joinColumns = @JoinColumn(name = "turn_id")
    )
    @Column(name = "entity_id")
    @Builder.Default
    private List<String> entityIds = new ArrayList<>();

    /**
     * Token count for this turn (for billing/metrics).
     */
    @Column(name = "tokens_used")
    private Integer tokensUsed;

    /**
     * LLM model used (e.g., "gpt-4", "claude-3").
     */
    @Column(length = 50, name = "model_used")
    private String modelUsed;

    /**
     * Turn-specific metadata (JSON stored as TEXT).
     */
    @Convert(converter = MetadataConverter.class)
    @Column(columnDefinition = "TEXT", name = "turn_metadata")
    @Builder.Default
    private Map<String, Object> turnMetadata = new HashMap<>();

    /**
     * Formats turn for inclusion in LLM prompt.
     *
     * @return formatted string "User: ...\nAssistant: ..."
     */
    public String toPromptFormat() {
        return String.format("User: %s%nAssistant: %s", userQuery, aiResponse);
    }
}
