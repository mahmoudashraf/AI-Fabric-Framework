package com.ai.fabric.realapps.chat.web;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.domain.ChatTurn;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final String CONVERSATION_PREFIX = "chat-";

    private final ObjectProvider<RAGOrchestrator> orchestratorProvider;
    private final ObjectProvider<ChatSessionService> chatSessionServiceProvider;

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

        if (StringUtils.hasText(userId)) {
            builder.userId(userId);
            builder.sessionId(sessionId);
        } else {
            builder.sessionId(sessionId);
        }

        return builder.build();
    }

    @Data
    public static class ChatQueryRequest {
        @NotBlank
        private String query;
        private String userId;
        private String sessionId;
        private String conversationId;
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
