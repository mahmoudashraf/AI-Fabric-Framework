package com.ai.fabric.realapps.chat.web;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.domain.ChatTurn;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.core.LlmPurpose;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.attachment.OrchestrationAttachment;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final String CONVERSATION_PREFIX = "chat-";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_OF_STRINGS = new TypeReference<>() {
    };

    private static final String SUGGESTIONS_SYSTEM_PROMPT_TEMPLATE = """
        You generate short, clickable UI suggestions for a user.
        Output MUST be valid JSON: an array of strings.
        Return exactly %d suggestions (no more, no less).
        Each suggestion should be a short question or task the user can select.
        Do not include explanations.
        """;

    private final ObjectProvider<RAGOrchestrator> orchestratorProvider;
    private final ObjectProvider<ChatSessionService> chatSessionServiceProvider;
    private final ObjectProvider<AICoreService> aiCoreServiceProvider;

    @PostMapping("/query")
    public ResponseEntity<ChatQueryResponse> query(@Valid @RequestBody ChatQueryRequest request) {
        RAGOrchestrator orchestrator = orchestratorProvider.getIfAvailable();
        if (orchestrator == null) {
            return ResponseEntity.ok(ChatQueryResponse.builder()
                .success(false)
                .message("Orchestrator not configured")
                .build());
        }

        String conversationId = StringUtils.hasText(request.getConversationId())
            ? request.getConversationId()
            : CONVERSATION_PREFIX + UUID.randomUUID();

        OrchestrationContext context = buildContext(request, conversationId);
        OrchestrationResult result = orchestrator.orchestrate(request.getQuery(), context);

        return ResponseEntity.ok(ChatQueryResponse.builder()
            .success(true)
            .conversationId(conversationId)
            .userId(context.getUserId())
            .sessionId(context.getSessionId())
            .result(result)
            .build());
    }

    /**
     * Lightweight suggestion endpoint (no orchestration / no retrieval).
     *
     * <p>Input: arbitrary user-provided context. Output: JSON array of suggested next questions/tasks.</p>
     */
    @PostMapping("/suggestions")
    public ResponseEntity<SuggestionsResponse> suggestions(@Valid @RequestBody SuggestionsRequest request) {
        AICoreService aiCoreService = aiCoreServiceProvider.getIfAvailable();
        if (aiCoreService == null) {
            return ResponseEntity.ok(SuggestionsResponse.builder()
                .success(false)
                .message("AI core service not configured")
                .suggestions(List.of())
                .build());
        }

        int n = request.getMaxSuggestions() != null ? request.getMaxSuggestions() : 5;
        String prompt = buildSuggestionsPrompt(request.getContent(), n);

        try {
            AIGenerationResponse response = aiCoreService.generateContent(AIGenerationRequest.builder()
                .entityType("suggestions")
                .entityId("adhoc")
                .generationType("suggestions")
                .systemPrompt(SUGGESTIONS_SYSTEM_PROMPT_TEMPLATE.formatted(n))
                .prompt(prompt)
                .maxTokens(300)
                .temperature(0.4)
                .build(), LlmPurpose.GENERATION);

            String raw = response != null ? response.getContent() : null;
            List<String> suggestions = normalizeSuggestions(parseSuggestions(raw), n);

            return ResponseEntity.ok(SuggestionsResponse.builder()
                .success(true)
                .message(null)
                .suggestions(suggestions)
                .raw(raw)
                .build());
        } catch (Exception ex) {
            return ResponseEntity.ok(SuggestionsResponse.builder()
                .success(false)
                .message("Failed to generate suggestions: " + ex.getMessage())
                .suggestions(List.of())
                .build());
        }
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ConversationResponse> getConversation(@PathVariable String conversationId,
                                                                @RequestParam("ownerId") String ownerId) {
        ChatSessionService service = chatSessionServiceProvider.getIfAvailable();
        if (service == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toConversationResponse(service.getSession(conversationId, ownerId)));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationSummaryResponse>> listConversations(@RequestParam("ownerId") String ownerId) {
        ChatSessionService service = chatSessionServiceProvider.getIfAvailable();
        if (service == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(service.getUserConversations(ownerId).stream().map(this::toConversationSummaryResponse).toList());
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String conversationId,
                                                   @RequestParam("ownerId") String ownerId) {
        ChatSessionService service = chatSessionServiceProvider.getIfAvailable();
        if (service == null) {
            return ResponseEntity.noContent().build();
        }
        service.deleteConversation(conversationId, ownerId);
        return ResponseEntity.noContent().build();
    }

    private OrchestrationContext buildContext(ChatQueryRequest request, String conversationId) {
        String userId = StringUtils.hasText(request.getUserId()) ? request.getUserId() : null;
        String sessionId = StringUtils.hasText(request.getSessionId())
            ? request.getSessionId()
            : "anon-" + UUID.randomUUID();

        OrchestrationContext.OrchestrationContextBuilder builder = OrchestrationContext.builder()
            .conversationId(conversationId);

        if (StringUtils.hasText(request.getPosition())) {
            builder.position(request.getPosition());
        }
        if (StringUtils.hasText(request.getMode())) {
            builder.mode(request.getMode());
        }
        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            builder.attachments(request.getAttachments());
        }
        if (request.getActiveAttachmentIds() != null && !request.getActiveAttachmentIds().isEmpty()) {
            builder.activeAttachmentIds(request.getActiveAttachmentIds());
        }

        if (StringUtils.hasText(userId)) {
            builder.userId(userId);
            builder.sessionId(sessionId);
        } else {
            builder.sessionId(sessionId);
        }

        return builder.build();
    }

    private String buildSuggestionsPrompt(String content, int n) {
        return """
            User attached these items/context:
            %s

            Suggest %d suggestions/questions for the user to select next.

            Output format: JSON array of %d strings.
            """.formatted(content, n, n);
    }

    private List<String> parseSuggestions(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String trimmed = raw.trim();

        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            String candidate = trimmed.substring(start, end + 1);
            try {
                List<String> parsed = OBJECT_MAPPER.readValue(candidate, LIST_OF_STRINGS);
                return parsed != null ? parsed : List.of();
            } catch (Exception ignored) {
            }
        }

        List<String> out = new ArrayList<>();
        for (String line : trimmed.split("\\R")) {
            String normalized = line.replaceFirst("^\\s*[-*\\d.]+\\s*", "").trim();
            if (StringUtils.hasText(normalized)) {
                out.add(normalized);
            }
        }
        return out;
    }

    private List<String> normalizeSuggestions(List<String> suggestions, int n) {
        if (suggestions == null || suggestions.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(Math.min(n, suggestions.size()));
        for (String suggestion : suggestions) {
            if (!StringUtils.hasText(suggestion)) {
                continue;
            }
            String trimmed = suggestion.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            out.add(trimmed);
            if (out.size() >= n) {
                break;
            }
        }
        return out;
    }

    @Data
    public static class ChatQueryRequest {
        @NotBlank
        private String query;
        private String userId;
        private String sessionId;
        private String conversationId;
        private String position;
        private String mode;
        private List<OrchestrationAttachment> attachments;
        private List<String> activeAttachmentIds;
    }

    @Data
    @Builder
    public static class ChatQueryResponse {
        private boolean success;
        private String message;
        private String conversationId;
        private String userId;
        private String sessionId;
        private OrchestrationResult result;
    }

    @Data
    @Builder
    public static class ConversationSummaryResponse {
        private String id;
        private String ownerId;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime lastInteractionAt;
        private int turnsCount;
    }

    @Data
    @Builder
    public static class ConversationResponse {
        private String id;
        private String ownerId;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime lastInteractionAt;
        private List<TurnResponse> turns;
    }

    @Data
    @Builder
    public static class TurnResponse {
        private LocalDateTime timestamp;
        private String userQuery;
        private String aiResponse;
    }

    @Data
    public static class SuggestionsRequest {
        @NotBlank
        private String content;
        @Min(1)
        @Max(10)
        private Integer maxSuggestions = 5;
    }

    @Data
    @Builder
    public static class SuggestionsResponse {
        private boolean success;
        private String message;
        private List<String> suggestions;
        private String raw;
    }

    private ConversationResponse toConversationResponse(ChatSession session) {
        if (session == null) {
            return null;
        }
        List<ChatTurn> turns = session.getTurns() != null ? session.getTurns() : List.of();
        List<TurnResponse> mappedTurns = turns.stream()
            .filter(t -> t != null)
            .map(t -> TurnResponse.builder()
                .timestamp(t.getTimestamp())
                .userQuery(t.getUserQuery())
                .aiResponse(t.getAiResponse())
                .build())
            .toList();
        return ConversationResponse.builder()
            .id(session.getId())
            .ownerId(session.getOwnerId())
            .status(session.getStatus() != null ? session.getStatus().name() : null)
            .createdAt(session.getCreatedAt())
            .lastInteractionAt(session.getLastInteractionAt())
            .turns(mappedTurns)
            .build();
    }

    private ConversationSummaryResponse toConversationSummaryResponse(ChatSession session) {
        if (session == null) {
            return null;
        }
        int turnsCount = session.getTurns() != null ? session.getTurns().size() : 0;
        return ConversationSummaryResponse.builder()
            .id(session.getId())
            .ownerId(session.getOwnerId())
            .status(session.getStatus() != null ? session.getStatus().name() : null)
            .createdAt(session.getCreatedAt())
            .lastInteractionAt(session.getLastInteractionAt())
            .turnsCount(turnsCount)
            .build();
    }
}
