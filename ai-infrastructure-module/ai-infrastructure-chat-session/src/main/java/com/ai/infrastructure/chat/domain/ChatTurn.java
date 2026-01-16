package com.ai.infrastructure.chat.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Column(nullable = false, columnDefinition = "TEXT", name = "user_query")
    private String userQuery;

    @Column(nullable = false, columnDefinition = "TEXT", name = "ai_response")
    private String aiResponse;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @ElementCollection
    @CollectionTable(name = "turn_entity_refs", joinColumns = @JoinColumn(name = "turn_id"))
    @Column(name = "entity_id")
    @Builder.Default
    private List<String> entityIds = new ArrayList<>();

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(length = 120, name = "model_used")
    private String modelUsed;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "TEXT", name = "turn_metadata")
    @Builder.Default
    private Map<String, Object> turnMetadata = new HashMap<>();

    public String toPromptFormat() {
        return "User: " + userQuery + "\nAssistant: " + aiResponse;
    }
}

