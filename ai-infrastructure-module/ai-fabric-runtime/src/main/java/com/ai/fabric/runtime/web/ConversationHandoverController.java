package com.ai.fabric.runtime.web;

import static com.ai.fabric.runtime.handover.ConversationHandoverContracts.*;

import ai.fabric.chat.control.ConversationControlState;
import ai.fabric.chat.control.ConversationControlViolationException;
import ai.fabric.chat.control.ConversationMessage;
import ai.fabric.intent.orchestration.control.OrchestrationControlException;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.auth.RuntimeScopeCatalog;
import com.ai.fabric.runtime.handover.RuntimeConversationHandoverService;
import com.ai.fabric.runtime.handover.RuntimeConversationTurn;
import com.ai.fabric.runtime.web.dto.ChatQueryRequest;
import com.ai.fabric.runtime.web.dto.ChatQueryResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat/me/conversations")
@ConditionalOnProperty(prefix = "loomai.conversation-handover", name = "enabled", havingValue = "true")
public class ConversationHandoverController {

    private static final long SSE_TIMEOUT_MS = Duration.ofMinutes(10).toMillis();

    private final RuntimeRequestAuthResolver authResolver;
    private final RuntimeConversationHandoverService handoverService;
    private final ChatRuntimeController chatRuntimeController;
    private final TaskExecutor sseExecutor;

    public ConversationHandoverController(
        RuntimeRequestAuthResolver authResolver,
        RuntimeConversationHandoverService handoverService,
        ChatRuntimeController chatRuntimeController,
        @Qualifier("conversationHandoverSseExecutor") TaskExecutor sseExecutor
    ) {
        this.authResolver = authResolver;
        this.handoverService = handoverService;
        this.chatRuntimeController = chatRuntimeController;
        this.sseExecutor = sseExecutor;
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ChatQueryResponse> message(
        @PathVariable String conversationId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody ChatQueryRequest body,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.CHAT_MESSAGES,
            "/api/chat/me/conversations/{conversationId}/messages"
        );
        body.setConversationId(conversationId);
        ConversationView before = handoverService.view(identity, conversationId, 0);
        if (before.controlState() == ConversationControlState.HUMAN_PENDING
            || before.controlState() == ConversationControlState.HUMAN_ACTIVE) {
            HumanMessageAcceptance accepted = handoverService.appendHumanBoundUserMessage(
                identity,
                conversationId,
                idempotencyKey,
                body.getQuery(),
                attachmentRefs(body)
            );
            return ResponseEntity.accepted().body(controlResponse(
                conversationId,
                "HUMAN_MESSAGE_ACCEPTED",
                "Your message was sent to the human conversation.",
                accepted.conversation()
            ));
        }
        if (before.controlState() != ConversationControlState.AI_ACTIVE) {
            return ResponseEntity.status(409).body(controlResponse(
                conversationId,
                "CONVERSATION_CONTROLLED",
                "The conversation is not accepting an AI message in its current state.",
                before
            ));
        }

        ConversationMessage userMessage = handoverService.appendUserMessage(
            identity,
            conversationId,
            idempotencyKey,
            body.getQuery(),
            Map.of(
                "source", "canonical-message-api",
                "attachmentRefs", attachmentRefs(body)
            )
        );
        AiInteractionAdmission interaction = handoverService.claimAiInteraction(
            identity,
            conversationId,
            idempotencyKey,
            userMessage.messageId(),
            body
        );
        if ("COMPLETED_REPLAY".equals(interaction.outcome())) {
            return ResponseEntity
                .status(HttpStatusCode.valueOf(interaction.responseHttpStatus()))
                .body(interaction.response());
        }
        if ("IN_PROGRESS".equals(interaction.outcome())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(interactionResponse(
                conversationId,
                "AI_INTERACTION_IN_PROGRESS",
                "This message is already being processed. Retry with the same key after it completes."
            ));
        }
        if ("FAILED".equals(interaction.outcome())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(interactionResponse(
                conversationId,
                "AI_INTERACTION_FAILED",
                "The prior attempt failed and was not executed again. Send a new message to retry."
            ));
        }
        RuntimeConversationTurn turn;
        try {
            turn = handoverService.beginAiTurn(
                identity,
                conversationId,
                userMessage.sequence(),
                idempotencyKey
            );
        } catch (ConversationControlViolationException exception) {
            ConversationView current = handoverService.view(identity, conversationId, 0);
            return completeInteraction(identity, conversationId, idempotencyKey, ResponseEntity.status(409).body(controlResponse(
                conversationId,
                exception.code(),
                "Conversation control changed before the AI turn started.",
                current
            )));
        }

        ResponseEntity<ChatQueryResponse> response;
        try {
            response = chatRuntimeController.controlledQuery(body, request, turn);
        } catch (OrchestrationControlException exception) {
            ConversationView current = handoverService.view(identity, conversationId, 0);
            if ((current.controlState() == ConversationControlState.HUMAN_PENDING
                || current.controlState() == ConversationControlState.HUMAN_ACTIVE)
                && StringUtils.hasText(current.handoverId())) {
                return completeInteraction(identity, conversationId, idempotencyKey, ResponseEntity.accepted().body(controlResponse(
                    conversationId,
                    "HANDOVER_REQUESTED",
                    "Your conversation is being transferred to a human.",
                    current
                )));
            }
            return completeInteraction(identity, conversationId, idempotencyKey, ResponseEntity.status(409).body(controlResponse(
                conversationId,
                exception.code(),
                "Conversation control changed before the AI result could be committed.",
                current
            )));
        } catch (RuntimeException exception) {
            handoverService.failAiInteraction(
                identity,
                conversationId,
                idempotencyKey,
                exception.getClass().getSimpleName()
            );
            throw exception;
        }
        ChatQueryResponse payload = response.getBody();
        if (payload == null) {
            handoverService.failAiInteraction(identity, conversationId, idempotencyKey, "EMPTY_RESPONSE");
            return response;
        }
        try {
            handoverService.appendAiMessage(
                turn,
                idempotencyKey + ":ai",
                StringUtils.hasText(payload.getAnswer()) ? payload.getAnswer() : "The AI request completed.",
                Map.of(
                    "providerRequestId", payload.getProviderRequestId() == null ? "" : payload.getProviderRequestId(),
                    "resultType", payload.getType() == null ? "" : payload.getType()
                )
            );
            return completeInteraction(identity, conversationId, idempotencyKey, response);
        } catch (ConversationControlViolationException exception) {
            ConversationView current = handoverService.view(identity, conversationId, 0);
            if ((current.controlState() == ConversationControlState.HUMAN_PENDING
                || current.controlState() == ConversationControlState.HUMAN_ACTIVE)
                && StringUtils.hasText(current.handoverId())) {
                String answer = StringUtils.hasText(payload.getAnswer())
                    ? payload.getAnswer()
                    : "Your conversation is being transferred to a human.";
                return completeInteraction(identity, conversationId, idempotencyKey, ResponseEntity.accepted().body(controlResponse(
                    conversationId,
                    "HANDOVER_REQUESTED",
                    answer,
                    current
                )));
            }
            return completeInteraction(identity, conversationId, idempotencyKey, ResponseEntity.status(409).body(controlResponse(
                conversationId,
                exception.code(),
                "Conversation control changed before the AI response could be committed.",
                current
            )));
        }
    }

    @PostMapping("/{conversationId}/handover-requests")
    public ResponseEntity<HandoverAdmission> requestHandover(
        @PathVariable String conversationId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody(required = false) HandoverRequest body,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.CHAT_HANDOVER_REQUEST,
            "/api/chat/me/conversations/{conversationId}/handover-requests"
        );
        HandoverAdmission admission = handoverService.request(identity, conversationId, idempotencyKey, body);
        return "HANDOVER_INTAKE_REQUIRED".equals(admission.outcome())
            ? ResponseEntity.unprocessableEntity().body(admission)
            : ResponseEntity.accepted().body(admission);
    }

    @PostMapping("/{conversationId}/handover-requests/{handoverId}/cancel")
    public ConversationView cancel(
        @PathVariable String conversationId,
        @PathVariable String handoverId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.CHAT_HANDOVER_CONTROL,
            "/api/chat/me/conversations/{conversationId}/handover-requests/{handoverId}/cancel"
        );
        return handoverService.cancel(identity, conversationId, handoverId, idempotencyKey);
    }

    @PostMapping("/{conversationId}/handover-requests/{handoverId}/retry")
    public ConversationView retry(
        @PathVariable String conversationId,
        @PathVariable String handoverId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.CHAT_HANDOVER_CONTROL,
            "/api/chat/me/conversations/{conversationId}/handover-requests/{handoverId}/retry"
        );
        return handoverService.retry(identity, conversationId, handoverId, idempotencyKey);
    }

    @PostMapping("/{conversationId}/ai-resume-requests")
    public ConversationView resumeAi(
        @PathVariable String conversationId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.CHAT_HANDOVER_CONTROL,
            "/api/chat/me/conversations/{conversationId}/ai-resume-requests"
        );
        return handoverService.resumeAi(identity, conversationId, idempotencyKey);
    }

    @GetMapping("/{conversationId}/control")
    public ConversationView status(
        @PathVariable String conversationId,
        @RequestParam(defaultValue = "0") long afterMessageSequence,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.CHAT_HANDOVER_READ,
            "/api/chat/me/conversations/{conversationId}/control"
        );
        return handoverService.view(identity, conversationId, afterMessageSequence);
    }

    @GetMapping(path = "/{conversationId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
        @PathVariable String conversationId,
        @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.CHAT_HANDOVER_READ,
            "/api/chat/me/conversations/{conversationId}/events"
        );
        handoverService.requireConversationAccess(identity, conversationId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        try {
            sseExecutor.execute(() -> streamEvents(emitter, identity, conversationId, lastEventId));
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Conversation event-stream capacity is temporarily exhausted.",
                exception
            );
        }
        return emitter;
    }

    private void streamEvents(
        SseEmitter emitter,
        RuntimeResolvedIdentity identity,
        String conversationId,
        String initialCursor
    ) {
        String cursor = initialCursor;
        long deadline = System.nanoTime() + Duration.ofMillis(SSE_TIMEOUT_MS).toNanos();
        try {
            while (System.nanoTime() < deadline) {
                List<ConversationEventView> events = handoverService.events(identity, conversationId, cursor, 100);
                for (ConversationEventView event : events) {
                    emitter.send(SseEmitter.event()
                        .id(event.eventId())
                        .name(event.type())
                        .data(event));
                    cursor = event.eventId();
                }
                if (events.isEmpty()) {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                }
                Thread.sleep(1_000L);
            }
            emitter.complete();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (IOException | RuntimeException exception) {
            emitter.completeWithError(exception);
        }
    }

    private RuntimeResolvedIdentity authorize(HttpServletRequest request, String scope, String surface) {
        RuntimeResolvedIdentity identity = authResolver.resolveVerifiedForChat(request);
        authResolver.requireScope(identity, scope, surface);
        return identity;
    }

    private List<String> attachmentRefs(ChatQueryRequest request) {
        if (request.getAttachments() == null) {
            return List.of();
        }
        return request.getAttachments().stream()
            .map(attachment -> attachment == null ? null : attachment.getId())
            .filter(StringUtils::hasText)
            .limit(12)
            .toList();
    }

    private ChatQueryResponse controlResponse(
        String conversationId,
        String type,
        String answer,
        ConversationView view
    ) {
        return ChatQueryResponse.builder()
            .success(!"CONVERSATION_CONTROLLED".equals(type))
            .type(type)
            .answer(answer)
            .safeSummary(answer)
            .conversationId(conversationId)
            .fallbackReason("CONVERSATION_CONTROLLED".equals(type) ? type : null)
            .sources(List.of())
            .actions(List.of())
            .suggestions(List.of())
            .ragResponse(Map.of())
            .metadata(Map.of("conversationControl", view))
            .build();
    }

    private ResponseEntity<ChatQueryResponse> completeInteraction(
        RuntimeResolvedIdentity identity,
        String conversationId,
        String idempotencyKey,
        ResponseEntity<ChatQueryResponse> response
    ) {
        if (response.getBody() == null) {
            handoverService.failAiInteraction(identity, conversationId, idempotencyKey, "EMPTY_RESPONSE");
            return response;
        }
        handoverService.completeAiInteraction(
            identity,
            conversationId,
            idempotencyKey,
            response.getStatusCode().value(),
            response.getBody()
        );
        return response;
    }

    private ChatQueryResponse interactionResponse(String conversationId, String type, String answer) {
        return ChatQueryResponse.builder()
            .success(false)
            .type(type)
            .answer(answer)
            .safeSummary(answer)
            .conversationId(conversationId)
            .fallbackReason(type)
            .sources(List.of())
            .actions(List.of())
            .suggestions(List.of())
            .ragResponse(Map.of())
            .metadata(Map.of())
            .build();
    }
}
