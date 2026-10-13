package com.ai.fabric.runtime.handover;

import static com.ai.fabric.runtime.handover.ConversationHandoverContracts.*;

import ai.fabric.chat.channel.ConversationChannelDeliveryResult;
import ai.fabric.chat.channel.ConversationChannelDispatch;
import ai.fabric.chat.channel.ConversationChannelDispatchType;
import ai.fabric.chat.channel.ConversationChannelEvent;
import ai.fabric.chat.channel.ConversationChannelEventType;
import ai.fabric.chat.channel.ConversationChannelMessage;
import ai.fabric.chat.control.ConversationControlCommand;
import ai.fabric.chat.control.ConversationControlCommandType;
import ai.fabric.chat.control.ConversationControlGateway;
import ai.fabric.chat.control.ConversationControlOutcome;
import ai.fabric.chat.control.ConversationControlResult;
import ai.fabric.chat.control.ConversationControlSnapshot;
import ai.fabric.chat.control.ConversationControlState;
import ai.fabric.chat.control.ConversationControlViolationException;
import ai.fabric.chat.control.ConversationMessage;
import ai.fabric.chat.control.ConversationMessageCommand;
import ai.fabric.chat.control.ConversationParticipantRole;
import ai.fabric.chat.control.ConversationScope;
import ai.fabric.chat.control.ConversationTurnLeaseGuard;
import ai.fabric.chat.control.HandoverIntakeField;
import ai.fabric.chat.control.HandoverIntakeFieldType;
import ai.fabric.chat.control.HandoverIntakeSchema;
import ai.fabric.dto.AIAccessSubjectContext;
import ai.fabric.dto.AIChatMessage;
import ai.fabric.intent.orchestration.OrchestrationContext;
import ai.fabric.intent.orchestration.conversation.ApprovedConversationSnapshot;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.web.dto.ChatQueryResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@ConditionalOnProperty(prefix = "loomai.conversation-handover", name = "enabled", havingValue = "true")
public class RuntimeConversationHandoverService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeConversationHandoverService.class);

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9][0-9 ()-]{5,30}$");
    private static final int EVENT_PAGE_SIZE = 100;
    private static final int SNAPSHOT_MESSAGE_LIMIT = 80;
    private static final int SNAPSHOT_SOURCE_WINDOW = 500;
    private static final int SNAPSHOT_CHARACTER_LIMIT = 50_000;

    private final ConversationControlGateway controlGateway;
    private final RuntimeConversationHandoverRepository repository;
    private final ConversationHandoverProperties properties;
    private final ConversationHandoverSecurity security;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RuntimeConversationHandoverService(
        ConversationControlGateway controlGateway,
        RuntimeConversationHandoverRepository repository,
        ConversationHandoverProperties properties,
        ConversationHandoverSecurity security,
        ObjectMapper objectMapper
    ) {
        this(controlGateway, repository, properties, security, objectMapper, Clock.systemUTC());
    }

    RuntimeConversationHandoverService(
        ConversationControlGateway controlGateway,
        RuntimeConversationHandoverRepository repository,
        ConversationHandoverProperties properties,
        ConversationHandoverSecurity security,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.controlGateway = controlGateway;
        this.repository = repository;
        this.properties = properties;
        this.security = security;
        this.objectMapper = objectMapper;
        this.clock = clock;
        if (!StringUtils.hasText(properties.getChannelBindingId())) {
            throw new IllegalStateException("LOOMAI_CONVERSATION_HANDOVER_CHANNEL_BINDING_ID is required when handover is enabled.");
        }
        requirePositive(properties.getQueueTimeout(), "Conversation handover queue timeout");
        requirePositive(properties.getRetention(), "Conversation handover retention");
        requirePositive(properties.getIntakeRetention(), "Conversation handover intake retention");
    }

    public RuntimeConversationTurn beginAiTurn(
        RuntimeResolvedIdentity identity,
        String conversationId,
        long currentUserMessageSequence,
        String interactionTurnId
    ) {
        ConversationScope scope = scope(identity, conversationId);
        var lease = controlGateway.acquireAiTurnLease(scope, properties.getAiTurnLease());
        return new RuntimeConversationTurn(
            scope,
            lease,
            new ConversationTurnLeaseGuard(controlGateway, lease),
            approvedSnapshot(scope, currentUserMessageSequence, interactionTurnId)
        );
    }

    public void installGuard(OrchestrationContext context, RuntimeConversationTurn turn) {
        context.setOrchestrationTurnGuard(turn.guard() != null
            ? turn.guard()
            : new ConversationTurnLeaseGuard(controlGateway, turn.lease()));
        context.setApprovedConversationSnapshot(turn.conversationSnapshot());
    }

    private ApprovedConversationSnapshot approvedSnapshot(
        ConversationScope scope,
        long currentUserMessageSequence,
        String interactionTurnId
    ) {
        long afterSequence = Math.max(0, currentUserMessageSequence - SNAPSHOT_SOURCE_WINDOW - 1);
        List<ConversationMessage> sourceMessages = controlGateway
            .messages(scope, afterSequence, SNAPSHOT_SOURCE_WINDOW)
            .stream()
            .filter(message -> message.sequence() < currentUserMessageSequence)
            .filter(message -> message.role() == ConversationParticipantRole.USER
                || message.role() == ConversationParticipantRole.AI
                || message.role() == ConversationParticipantRole.HUMAN)
            .toList();

        List<ConversationMessage> boundedMessages = boundedSnapshotMessages(sourceMessages);
        List<AIChatMessage> history = boundedMessages.stream()
            .map(this::toAiHistoryMessage)
            .toList();
        return new ApprovedConversationSnapshot(
            required(interactionTurnId, "interactionTurnId is required."),
            scope.ownerId(),
            scope.conversationId(),
            "runtime-conversation",
            snapshotRevision(boundedMessages),
            sourceMessages.size(),
            history,
            clock.instant()
        );
    }

    private List<ConversationMessage> boundedSnapshotMessages(List<ConversationMessage> sourceMessages) {
        if (sourceMessages == null || sourceMessages.isEmpty()) {
            return List.of();
        }
        List<ConversationMessage> selected = new ArrayList<>();
        int characters = 0;
        for (int index = sourceMessages.size() - 1; index >= 0; index--) {
            ConversationMessage message = sourceMessages.get(index);
            String rendered = snapshotContent(message);
            if (selected.size() >= SNAPSHOT_MESSAGE_LIMIT
                || characters + rendered.length() > SNAPSHOT_CHARACTER_LIMIT) {
                break;
            }
            selected.add(message);
            characters += rendered.length();
        }
        java.util.Collections.reverse(selected);
        return List.copyOf(selected);
    }

    private AIChatMessage toAiHistoryMessage(ConversationMessage message) {
        return message.role() == ConversationParticipantRole.USER
            ? AIChatMessage.user(message.content())
            : AIChatMessage.assistant(snapshotContent(message));
    }

    private String snapshotContent(ConversationMessage message) {
        if (message.role() != ConversationParticipantRole.HUMAN) {
            return message.content();
        }
        Object displayValue = message.metadata().get("safeActorDisplayName");
        String display = displayValue == null ? null : displayValue.toString().trim();
        return "[Human agent" + (StringUtils.hasText(display) ? ": " + display : "") + "] " + message.content();
    }

    private String snapshotRevision(List<ConversationMessage> messages) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ConversationMessage message : messages) {
                digest.update((message.sequence() + "|" + message.role().name() + "|"
                    + message.actorRef() + "|" + snapshotContent(message) + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Conversation snapshot revision generation failed.", exception);
        }
    }

    public ConversationMessage appendUserMessage(
        RuntimeResolvedIdentity identity,
        String conversationId,
        String clientMessageId,
        String content,
        Map<String, Object> metadata
    ) {
        ConversationScope scope = scope(identity, conversationId);
        return controlGateway.appendMessage(
            new ConversationMessageCommand(
                scope,
                deterministicId("user-message", scope, clientMessageId),
                clientMessageId,
                ConversationParticipantRole.USER,
                scope.ownerId(),
                content,
                safeMetadata(metadata)
            ),
            null
        );
    }

    public ConversationMessage appendAiMessage(
        RuntimeConversationTurn turn,
        String clientMessageId,
        String content,
        Map<String, Object> metadata
    ) {
        return controlGateway.appendMessage(
            new ConversationMessageCommand(
                turn.scope(),
                deterministicId("ai-message", turn.scope(), clientMessageId),
                clientMessageId,
                ConversationParticipantRole.AI,
                "ai-runtime",
                content,
                safeMetadata(metadata)
            ),
            turn.lease()
        );
    }

    public AiInteractionAdmission claimAiInteraction(
        RuntimeResolvedIdentity identity,
        String conversationId,
        String idempotencyKey,
        String userMessageId,
        Object request
    ) {
        ConversationScope scope = scope(identity, conversationId);
        String key = requireIdempotency(idempotencyKey);
        String requestFingerprint = security.fingerprint(writeJson(request));
        RuntimeConversationHandoverRepository.InteractionClaim claim = repository.claimInteraction(
            scope,
            key,
            required(userMessageId, "userMessageId is required."),
            requestFingerprint
        );
        RuntimeConversationHandoverRepository.InteractionRecord record = claim.record();
        if (claim.claimed()) {
            return new AiInteractionAdmission("CLAIMED", null, null, null);
        }
        if ("COMPLETED".equals(record.status())) {
            return new AiInteractionAdmission(
                "COMPLETED_REPLAY",
                record.responseHttpStatus(),
                readJson(record.responseJson(), ChatQueryResponse.class),
                null
            );
        }
        if ("FAILED".equals(record.status())) {
            return new AiInteractionAdmission("FAILED", null, null, record.failureCode());
        }
        return new AiInteractionAdmission("IN_PROGRESS", null, null, null);
    }

    public void completeAiInteraction(
        RuntimeResolvedIdentity identity,
        String conversationId,
        String idempotencyKey,
        int responseHttpStatus,
        ChatQueryResponse response
    ) {
        repository.completeInteraction(
            scope(identity, conversationId),
            requireIdempotency(idempotencyKey),
            responseHttpStatus,
            writeJson(response)
        );
    }

    public void failAiInteraction(
        RuntimeResolvedIdentity identity,
        String conversationId,
        String idempotencyKey,
        String failureCode
    ) {
        repository.failInteraction(
            scope(identity, conversationId),
            requireIdempotency(idempotencyKey),
            failureCode
        );
    }

    public HumanMessageAcceptance appendHumanBoundUserMessage(
        RuntimeResolvedIdentity identity,
        String conversationId,
        String clientMessageId,
        String content,
        List<String> attachmentRefs
    ) {
        ConversationScope scope = scope(identity, conversationId);
        return repository.transactional(() -> {
            ConversationControlSnapshot snapshot = controlGateway.status(scope);
            if (snapshot.state() != ConversationControlState.HUMAN_PENDING
                && snapshot.state() != ConversationControlState.HUMAN_ACTIVE) {
                throw conflict("HUMAN_CHANNEL_NOT_ACTIVE", "The conversation is not currently routed to a human channel.");
            }
            ConversationMessage message = controlGateway.appendMessage(
                new ConversationMessageCommand(
                    scope,
                    deterministicId("user-message", scope, clientMessageId),
                    clientMessageId,
                    ConversationParticipantRole.USER,
                    scope.ownerId(),
                    content,
                    Map.of("deliveryState", "PENDING")
                ),
                null
            );
            String handoverId = required(snapshot.activeHandoverId(), "Active handover is missing.");
            ConversationChannelDispatch dispatch = new ConversationChannelDispatch(
                deterministicId("dispatch-user", scope, message.messageId()),
                ConversationChannelDispatchType.USER_MESSAGE,
                scope,
                handoverId,
                required(snapshot.channelBindingId(), "Channel binding is missing."),
                snapshot.version(),
                message.messageId(),
                message.content(),
                boundedStrings(attachmentRefs, 12, 500),
                List.of(),
                Map.of(),
                clock.instant()
            );
            repository.addDispatch(dispatch, "user-message:" + message.messageId());
            appendEvent(scope, "USER_MESSAGE_ACCEPTED", Map.of(
                "messageId", message.messageId(),
                "messageSequence", message.sequence(),
                "controlState", snapshot.state().name()
            ));
            return new HumanMessageAcceptance("ACCEPTED_FOR_HUMAN", message.messageId(), view(scope, 0));
        });
    }

    public HandoverAdmission request(
        RuntimeResolvedIdentity identity,
        String conversationId,
        String idempotencyKey,
        HandoverRequest request
    ) {
        return request(scope(identity, conversationId), idempotencyKey, request, "user");
    }

    public HandoverAdmission request(
        AIAccessSubjectContext auth,
        String conversationId,
        String idempotencyKey,
        String reasonCode,
        ConversationHandoverRequestProvenance requestProvenance
    ) {
        ConversationScope scope = scope(auth, conversationId);
        ConversationHandoverRequestProvenance effectiveProvenance = requestProvenance == null
            ? ConversationHandoverRequestProvenance.MODEL_RECOMMENDATION
            : requestProvenance;
        return request(
            scope,
            idempotencyKey,
            new HandoverRequest(reasonCode, properties.isAnonymousAllowed(), Map.of(), List.of()),
            "ai-action:" + effectiveProvenance.name()
        );
    }

    private HandoverAdmission request(
        ConversationScope scope,
        String idempotencyKey,
        HandoverRequest rawRequest,
        String actor
    ) {
        String key = requireIdempotency(idempotencyKey);
        HandoverRequest request = rawRequest == null
            ? new HandoverRequest(null, false, Map.of(), List.of())
            : rawRequest;
        IntakeAdmission intake = validateIntake(request);
        if (!intake.accepted()) {
            return new HandoverAdmission(
                "HANDOVER_INTAKE_REQUIRED",
                view(scope, 0),
                intakeSchema(),
                "HANDOVER_INTAKE_REQUIRED",
                "Choose anonymous continuation or provide the configured optional contact details."
            );
        }

        return repository.transactional(() -> {
            ConversationControlSnapshot current = controlGateway.status(scope);
            if (current.state() != ConversationControlState.AI_ACTIVE) {
                if (StringUtils.hasText(current.activeHandoverId())) {
                    return new HandoverAdmission(
                        "IDEMPOTENT_ACTIVE_HANDOVER",
                        view(scope, 0),
                        null,
                        "HANDOVER_ALREADY_ACTIVE",
                        "This conversation already has an active handover."
                    );
                }
                throw conflict("HANDOVER_NOT_ALLOWED", "A handover cannot start from " + current.state() + ".");
            }

            String handoverId = deterministicId("handover", scope, key);
            Instant now = clock.instant();
            RuntimeConversationHandoverRepository.HandoverRecord handover = repository.createHandoverIfAbsent(
                handoverId,
                scope,
                properties.getChannelBindingId().trim(),
                key,
                bounded(request.reasonCode(), 100),
                now.plus(properties.getRetention())
            );
            if (!intake.values().isEmpty()) {
                repository.saveIntake(
                    handoverId,
                    intake.values(),
                    now.plus(properties.getIntakeRetention())
                );
            }
            ConversationControlResult transition = controlGateway.transition(new ConversationControlCommand(
                scope,
                ConversationControlCommandType.REQUEST_HUMAN,
                current.version(),
                key,
                actor,
                bounded(request.reasonCode(), 100),
                handoverId,
                handover.channelBindingId()
            ));
            requireApplied(transition);
            Map<String, Object> consented = repository.consentedIntake(handoverId);
            ConversationChannelDispatch dispatch = new ConversationChannelDispatch(
                deterministicId("dispatch-open", scope, handoverId),
                ConversationChannelDispatchType.OPEN_HANDOVER,
                scope,
                handoverId,
                handover.channelBindingId(),
                transition.snapshot().version(),
                null,
                null,
                List.of(),
                channelTranscript(scope),
                consented,
                now
            );
            repository.addDispatch(dispatch, "open:" + handoverId);
            appendSystemMessage(scope, "handover-requested:" + handoverId, "A human handover was requested.", Map.of(
                "handoverId", handoverId,
                "controlState", transition.snapshot().state().name(),
                "requestSource", actor
            ));
            appendEvent(scope, "HANDOVER_REQUESTED", Map.of(
                "handoverId", handoverId,
                "controlState", transition.snapshot().state().name(),
                "anonymous", intake.values().isEmpty(),
                "requestSource", actor
            ));
            return new HandoverAdmission(
                "HANDOVER_REQUESTED",
                view(scope, 0),
                null,
                "HANDOVER_REQUESTED",
                "Your conversation is waiting for a human."
            );
        });
    }

    public ConversationView cancel(
        RuntimeResolvedIdentity identity,
        String conversationId,
        String handoverId,
        String idempotencyKey
    ) {
        ConversationScope scope = scope(identity, conversationId);
        return repository.transactional(() -> {
            RuntimeConversationHandoverRepository.HandoverRecord handover = requireHandover(scope, handoverId);
            ConversationControlSnapshot current = controlGateway.status(scope);
            ConversationControlResult result = controlGateway.transition(new ConversationControlCommand(
                scope,
                ConversationControlCommandType.CANCEL_HUMAN_REQUEST,
                current.version(),
                requireIdempotency(idempotencyKey),
                scope.ownerId(),
                "OWNER_CANCELLED",
                handoverId,
                handover.channelBindingId()
            ));
            requireApplied(result);
            repository.updateHandover(handoverId, "CANCELLED", null, null, null, true);
            addControlDispatch(scope, handoverId, handover.channelBindingId(), ConversationChannelDispatchType.CANCEL_HANDOVER);
            appendEvent(scope, "HANDOVER_CANCELLED", Map.of("handoverId", handoverId));
            return view(scope, 0);
        });
    }

    public ConversationView retry(
        RuntimeResolvedIdentity identity,
        String conversationId,
        String handoverId,
        String idempotencyKey
    ) {
        ConversationScope scope = scope(identity, conversationId);
        String key = requireIdempotency(idempotencyKey);
        return repository.transactional(() -> {
            RuntimeConversationHandoverRepository.HandoverRecord handover = requireHandover(scope, handoverId);
            ConversationControlSnapshot current = controlGateway.status(scope);
            if (current.state() != ConversationControlState.HUMAN_PENDING
                || !handoverId.equals(current.activeHandoverId())) {
                throw conflict("HANDOVER_RETRY_NOT_ALLOWED", "Only the active pending handover can be retried.");
            }
            if (repository.findOperationHandover(scope, "RETRY_HANDOVER", key).isPresent()) {
                return view(scope, 0);
            }
            if (!repository.reactivateExpiredHandover(handoverId)) {
                throw conflict("HANDOVER_RETRY_NOT_ALLOWED", "Only an expired handover can be retried.");
            }
            repository.recordOperation(scope, "RETRY_HANDOVER", key, handoverId);
            ConversationChannelDispatch dispatch = new ConversationChannelDispatch(
                deterministicId("dispatch-retry-open", scope, handoverId + ":" + key),
                ConversationChannelDispatchType.OPEN_HANDOVER,
                scope,
                handoverId,
                handover.channelBindingId(),
                current.version(),
                null,
                null,
                List.of(),
                channelTranscript(scope),
                repository.consentedIntake(handoverId),
                clock.instant()
            );
            repository.addDispatch(dispatch, "retry-open:" + handoverId + ":" + key);
            appendEvent(scope, "HANDOVER_RETRY_REQUESTED", Map.of("handoverId", handoverId));
            return view(scope, 0);
        });
    }

    public ConversationView resumeAi(
        RuntimeResolvedIdentity identity,
        String conversationId,
        String idempotencyKey
    ) {
        ConversationScope scope = scope(identity, conversationId);
        String key = requireIdempotency(idempotencyKey);
        return repository.transactional(() -> {
            ConversationControlSnapshot current = controlGateway.status(scope);
            if (current.state() == ConversationControlState.AI_ACTIVE) {
                if (repository.findOperationHandover(scope, "RESUME_AI", key).isPresent()) {
                    return view(scope, 0);
                }
                throw conflict("AI_RESUME_NOT_ALLOWED", "No active human handover is available.");
            }
            if (current.state() == ConversationControlState.AI_RESUME_PENDING) {
                if (repository.findOperationHandover(scope, "RESUME_AI", key).isPresent()) {
                    return view(scope, 0);
                }
                throw conflict("AI_RESUME_ALREADY_PENDING", "A return to AI is already pending channel acknowledgement.");
            }
            String handoverId = required(current.activeHandoverId(), "No active handover is available.");
            RuntimeConversationHandoverRepository.HandoverRecord handover = requireHandover(scope, handoverId);
            if (current.state() != ConversationControlState.HUMAN_ACTIVE) {
                throw conflict("AI_RESUME_NOT_ALLOWED", "AI resume is not allowed from " + current.state() + ".");
            }
            ConversationControlResult requested = controlGateway.transition(new ConversationControlCommand(
                scope,
                ConversationControlCommandType.REQUEST_AI_RESUME,
                current.version(),
                key + ":request",
                scope.ownerId(),
                "OWNER_REQUESTED_AI_RESUME",
                handoverId,
                handover.channelBindingId()
            ));
            requireApplied(requested);
            repository.recordOperation(scope, "RESUME_AI", key, handoverId);
            addControlDispatch(scope, handoverId, handover.channelBindingId(), ConversationChannelDispatchType.RETURN_TO_AI);
            appendEvent(scope, "AI_RESUME_REQUESTED", Map.of(
                "handoverId", handoverId,
                "controlState", requested.snapshot().state().name()
            ));
            return view(scope, 0);
        });
    }

    public ConversationView receiveChannelEvent(
        String bindingId,
        ConversationChannelEvent event,
        String nonce,
        String rawBody
    ) {
        return repository.transactional(() -> {
            RuntimeConversationHandoverRepository.HandoverRecord handover = repository
                .findForBinding(bindingId, event.handoverId())
                .orElseThrow(() -> notFound("HANDOVER_NOT_FOUND", "The handover binding is not available."));
            if (!event.channelBindingId().equals(bindingId)) {
                throw forbidden("CHANNEL_BINDING_MISMATCH", "The normalized event binding does not match the route.");
            }
            if (StringUtils.hasText(handover.externalConversationRef())
                && !handover.externalConversationRef().equals(event.externalConversationRef())) {
                throw conflict("EXTERNAL_CONVERSATION_MISMATCH", "The external conversation reference changed.");
            }
            boolean fresh = repository.recordChannelEventReceipt(
                event.eventId(), bindingId, event.handoverId(), nonce,
                security.fingerprint(rawBody), "ACCEPTED"
            );
            if (!fresh) {
                return view(handover.scope(), 0);
            }
            ConversationControlSnapshot current = controlGateway.status(handover.scope());
            if (event.expectedControlVersion() != current.version()) {
                throw conflict("CONVERSATION_VERSION_CONFLICT", "The channel event targets a stale control version.");
            }
            applyChannelEvent(handover, current, event);
            appendEvent(handover.scope(), event.type().name(), channelEventProjection(event));
            return view(handover.scope(), 0);
        });
    }

    private void applyChannelEvent(
        RuntimeConversationHandoverRepository.HandoverRecord handover,
        ConversationControlSnapshot current,
        ConversationChannelEvent event
    ) {
        switch (event.type()) {
            case HANDOVER_ACCEPTED, HANDOVER_CLAIMED -> {
                ConversationControlResult result = controlGateway.transition(new ConversationControlCommand(
                    handover.scope(),
                    ConversationControlCommandType.ACTIVATE_HUMAN,
                    current.version(),
                    "channel-event:" + event.eventId(),
                    required(event.externalActorRef(), "A verified external actor reference is required."),
                    event.type().name(),
                    handover.handoverId(),
                    handover.channelBindingId()
                ));
                requireApplied(result);
                repository.updateHandover(
                    handover.handoverId(), "ACTIVE", event.externalConversationRef(),
                    event.externalActorRef(), event.safeActorDisplayName(), false
                );
            }
            case HUMAN_MESSAGE -> {
                if (current.state() != ConversationControlState.HUMAN_ACTIVE) {
                    throw conflict("HUMAN_MESSAGE_NOT_ALLOWED", "A human message requires HUMAN_ACTIVE state.");
                }
                controlGateway.appendMessage(new ConversationMessageCommand(
                    handover.scope(),
                    required(event.messageId(), "A provider message ID is required."),
                    "provider:" + event.messageId(),
                    ConversationParticipantRole.HUMAN,
                    required(event.externalActorRef(), "A verified external actor reference is required."),
                    required(event.text(), "Human message text is required."),
                    Map.of(
                        "safeActorDisplayName", StringUtils.hasText(event.safeActorDisplayName())
                            ? event.safeActorDisplayName() : "Human agent",
                        "externalConversationRef", event.externalConversationRef()
                    )
                ), null);
            }
            case HANDOVER_RELEASED -> {
                ConversationControlResult requested = controlGateway.transition(new ConversationControlCommand(
                    handover.scope(),
                    ConversationControlCommandType.REQUEST_AI_RESUME,
                    current.version(),
                    "channel-event:" + event.eventId(),
                    required(event.externalActorRef(), "A verified external actor reference is required."),
                    "PROVIDER_RELEASED",
                    handover.handoverId(),
                    handover.channelBindingId()
                ));
                requireApplied(requested);
                ConversationControlResult completed = controlGateway.transition(new ConversationControlCommand(
                    handover.scope(),
                    ConversationControlCommandType.COMPLETE_AI_RESUME,
                    requested.snapshot().version(),
                    "channel-event:" + event.eventId() + ":complete",
                    required(event.externalActorRef(), "A verified external actor reference is required."),
                    "PROVIDER_AUTHORIZED_AI_RESUME",
                    handover.handoverId(),
                    handover.channelBindingId()
                ));
                requireApplied(completed);
                repository.updateHandover(handover.handoverId(), "RETURNED_TO_AI", null, null, null, true);
                appendSystemMessage(
                    handover.scope(),
                    "provider-ai-resumed:" + handover.handoverId(),
                    "The human operator returned the conversation to AI.",
                    Map.of("handoverId", handover.handoverId(), "controlState", completed.snapshot().state().name())
                );
            }
            case HANDOVER_CLOSED -> {
                ConversationControlResult result = controlGateway.transition(new ConversationControlCommand(
                    handover.scope(),
                    ConversationControlCommandType.CLOSE,
                    current.version(),
                    "channel-event:" + event.eventId(),
                    required(event.externalActorRef(), "A verified external actor reference is required."),
                    "PROVIDER_CLOSED",
                    handover.handoverId(),
                    handover.channelBindingId()
                ));
                requireApplied(result);
                repository.updateHandover(handover.handoverId(), "CLOSED", null, null, null, true);
            }
            case DELIVERY_FAILED -> repository.updateHandover(
                handover.handoverId(), "DELIVERY_FAILED", null, null, null, false
            );
        }
    }

    public ConversationView view(RuntimeResolvedIdentity identity, String conversationId, long afterMessageSequence) {
        return view(scope(identity, conversationId), afterMessageSequence);
    }

    public ConversationView history(RuntimeResolvedIdentity identity, String conversationId) {
        ConversationScope scope = scope(identity, conversationId);
        requireCanonicalConversation(scope);
        return view(scope, 0);
    }

    public CanonicalConversationSummary conversationSummary(
        RuntimeResolvedIdentity identity,
        String conversationId
    ) {
        return toCanonicalSummary(requireCanonicalConversation(scope(identity, conversationId)));
    }

    public List<CanonicalConversationSummary> listConversations(RuntimeResolvedIdentity identity) {
        OwnerScope owner = ownerScope(identity);
        return repository.listCanonicalConversations(owner.tenantId(), owner.deploymentId(), owner.ownerId(), 100)
            .stream()
            .map(this::toCanonicalSummary)
            .toList();
    }

    public ConversationView closeConversation(
        RuntimeResolvedIdentity identity,
        String conversationId,
        String idempotencyKey
    ) {
        ConversationScope scope = scope(identity, conversationId);
        String key = requireIdempotency(idempotencyKey);
        return repository.transactional(() -> {
            requireCanonicalConversation(scope);
            ConversationControlSnapshot current = controlGateway.status(scope);
            if (current.state() == ConversationControlState.CLOSED) {
                return view(scope, 0);
            }
            String activeHandoverId = current.activeHandoverId();
            String channelBindingId = current.channelBindingId();
            ConversationControlResult closed = controlGateway.transition(new ConversationControlCommand(
                scope,
                ConversationControlCommandType.CLOSE,
                current.version(),
                key,
                scope.ownerId(),
                "OWNER_CLOSED",
                activeHandoverId,
                channelBindingId
            ));
            requireApplied(closed);
            if (StringUtils.hasText(activeHandoverId) && StringUtils.hasText(channelBindingId)) {
                repository.updateHandover(activeHandoverId, "CLOSED", null, null, null, true);
                addControlDispatch(
                    scope,
                    activeHandoverId,
                    channelBindingId,
                    ConversationChannelDispatchType.CLOSE
                );
            }
            appendSystemMessage(
                scope,
                "conversation-closed:" + key,
                "The conversation was closed.",
                Map.of("controlState", ConversationControlState.CLOSED.name())
            );
            appendEvent(scope, "CONVERSATION_CLOSED", Map.of(
                "controlState", ConversationControlState.CLOSED.name()
            ));
            return view(scope, 0);
        });
    }

    public List<ConversationEventView> events(
        RuntimeResolvedIdentity identity,
        String conversationId,
        String afterEventId,
        int limit
    ) {
        ConversationScope scope = scope(identity, conversationId);
        controlGateway.status(scope);
        return repository.events(scope, afterEventId, limit).stream()
            .map(this::toEventView)
            .toList();
    }

    public DispatchClaim claimDispatch(String workerId, Duration lease) {
        String normalizedWorker = required(workerId, "workerId is required.");
        return repository.claimNextDispatch(normalizedWorker, boundedLease(lease))
            .map(claim -> new DispatchClaim(
                claim.dispatch().dispatchId(), claim.attempt(), claim.dispatch()
            ))
            .orElse(null);
    }

    public void completeDispatch(String dispatchId, DispatchOutcome outcome, int attempt) {
        DispatchOutcome safe = outcome == null
            ? new DispatchOutcome(false, null, null, "EMPTY_OUTCOME", "Connector returned no outcome.", true)
            : outcome;
        String normalizedDispatchId = required(dispatchId, "dispatchId is required.");
        repository.transactional(() -> {
            ConversationChannelDispatch dispatch = repository.findDispatchPayload(normalizedDispatchId)
                .orElseThrow(() -> notFound("DISPATCH_NOT_FOUND", "The handover dispatch is not available."));
            boolean accepted = repository.completeDispatch(
                normalizedDispatchId,
                new ConversationChannelDeliveryResult(
                    safe.accepted(), safe.externalConversationRef(), safe.providerMessageRef(),
                    safe.code(), safe.message(), safe.retryable()
                ),
                Math.max(1, attempt),
                5
            );
            if (!accepted) {
                throw conflict(
                    "DISPATCH_ATTEMPT_STALE",
                    "The dispatch outcome does not match the active delivery attempt."
                );
            }
            if (safe.accepted() && dispatch.type() == ConversationChannelDispatchType.RETURN_TO_AI) {
                completeOwnerAiResume(dispatch);
            }
            return Boolean.TRUE;
        });
    }

    private void completeOwnerAiResume(ConversationChannelDispatch dispatch) {
        ConversationControlSnapshot current = controlGateway.status(dispatch.scope());
        if (current.state() != ConversationControlState.AI_RESUME_PENDING
            || !dispatch.handoverId().equals(current.activeHandoverId())) {
            throw conflict(
                "AI_RESUME_ACKNOWLEDGEMENT_STALE",
                "The channel acknowledgement no longer matches the pending AI resume."
            );
        }
        ConversationControlResult completed = controlGateway.transition(new ConversationControlCommand(
            dispatch.scope(),
            ConversationControlCommandType.COMPLETE_AI_RESUME,
            current.version(),
            "dispatch-accepted:" + dispatch.dispatchId(),
            "conversation-channel",
            "CHANNEL_ACKNOWLEDGED_AI_RESUME",
            dispatch.handoverId(),
            dispatch.channelBindingId()
        ));
        requireApplied(completed);
        repository.updateHandover(dispatch.handoverId(), "RETURNED_TO_AI", null, null, null, true);
        appendSystemMessage(
            dispatch.scope(),
            "ai-resumed:" + dispatch.handoverId(),
            "The conversation returned to AI.",
            Map.of(
                "handoverId", dispatch.handoverId(),
                "controlState", completed.snapshot().state().name()
            )
        );
        appendEvent(dispatch.scope(), "AI_RESUMED", Map.of("handoverId", dispatch.handoverId()));
    }

    public HandoverOperationsOverview operationsOverview() {
        List<DispatchOperationView> recent = repository.recentDispatches(50).stream()
            .map(dispatch -> new DispatchOperationView(
                dispatch.dispatchId(),
                dispatch.handoverId(),
                dispatch.dispatchType(),
                dispatch.status(),
                dispatch.attemptCount(),
                dispatch.failureCode(),
                dispatch.createdAt(),
                dispatch.updatedAt(),
                "DEAD".equals(dispatch.status())
            ))
            .toList();
        return new HandoverOperationsOverview(
            true,
            properties.getChannelBindingId(),
            repository.handoverStatusCounts(),
            repository.dispatchStatusCounts(),
            repository.oldestPendingDispatchAt(),
            recent
        );
    }

    public HandoverOperationsOverview retryDispatch(String deploymentId, String dispatchId) {
        String normalizedDeployment = required(deploymentId, "Verified deployment is required.");
        String normalizedDispatch = required(dispatchId, "dispatchId is required.");
        return repository.transactional(() -> {
            RuntimeConversationHandoverRepository.DispatchAdminRecord dispatch = repository
                .findDispatch(normalizedDispatch)
                .orElseThrow(() -> notFound("DISPATCH_NOT_FOUND", "The handover dispatch is not available."));
            RuntimeConversationHandoverRepository.HandoverRecord handover = repository
                .findById(dispatch.handoverId())
                .orElseThrow(() -> notFound("HANDOVER_NOT_FOUND", "The handover is not available."));
            if (!normalizedDeployment.equals(handover.scope().deploymentId())) {
                throw notFound("DISPATCH_NOT_FOUND", "The handover dispatch is not available.");
            }
            if (!repository.retryDeadDispatch(normalizedDispatch)) {
                throw conflict("DISPATCH_RETRY_NOT_ALLOWED", "Only a dead dispatch can be retried.");
            }
            appendEvent(handover.scope(), "DISPATCH_RETRY_REQUESTED", Map.of(
                "dispatchId", normalizedDispatch,
                "handoverId", handover.handoverId()
            ));
            return operationsOverview();
        });
    }

    public HandoverOperationsOverview expirePendingHandover(String deploymentId, String handoverId) {
        String normalizedDeployment = required(deploymentId, "Verified deployment is required.");
        String normalizedHandover = required(handoverId, "handoverId is required.");
        return repository.transactional(() -> {
            RuntimeConversationHandoverRepository.HandoverRecord handover = repository
                .findById(normalizedHandover)
                .filter(candidate -> normalizedDeployment.equals(candidate.scope().deploymentId()))
                .orElseThrow(() -> notFound("HANDOVER_NOT_FOUND", "The handover is not available."));
            ConversationControlSnapshot current = controlGateway.status(handover.scope());
            if (current.state() != ConversationControlState.HUMAN_PENDING
                || !"PENDING".equals(handover.status())) {
                throw conflict("HANDOVER_EXPIRY_NOT_ALLOWED", "Only a pending handover can be expired.");
            }
            if (!repository.expirePendingHandover(handover.handoverId(), clock.instant())) {
                throw conflict("HANDOVER_EXPIRY_STALE", "The pending handover changed before it could be expired.");
            }
            appendEvent(handover.scope(), "HANDOVER_EXPIRED", Map.of("handoverId", handover.handoverId()));
            return operationsOverview();
        });
    }

    @Scheduled(fixedDelayString = "${loomai.conversation-handover.maintenance-interval:PT30S}")
    public void scheduledMaintenance() {
        try {
            runMaintenance();
        } catch (RuntimeException exception) {
            log.error("Conversation handover maintenance failed", exception);
        }
    }

    public HandoverMaintenanceResult runMaintenance() {
        Instant now = clock.instant();
        Instant queueCutoff = now.minus(properties.getQueueTimeout());
        int queueExpired = 0;
        for (RuntimeConversationHandoverRepository.HandoverRecord handover
            : repository.pendingHandoversBefore(queueCutoff, 200)) {
            boolean expired = repository.transactional(() -> {
                ConversationControlSnapshot control = controlGateway.status(handover.scope());
                if (control.state() != ConversationControlState.HUMAN_PENDING
                    || !handover.handoverId().equals(control.activeHandoverId())) {
                    return false;
                }
                if (!repository.expirePendingHandover(handover.handoverId(), queueCutoff)) {
                    return false;
                }
                appendSystemMessage(
                    handover.scope(),
                    "handover-expired:" + handover.handoverId(),
                    "No human joined before the queue timeout. Retry or continue with AI.",
                    Map.of("handoverId", handover.handoverId(), "deliveryState", "EXPIRED")
                );
                appendEvent(handover.scope(), "HANDOVER_EXPIRED", Map.of(
                    "handoverId", handover.handoverId(),
                    "controlState", control.state().name(),
                    "automatic", true
                ));
                return true;
            });
            if (expired) {
                queueExpired++;
            }
        }

        int retentionClosed = 0;
        for (RuntimeConversationHandoverRepository.HandoverRecord handover
            : repository.retentionDueHandovers(now, 200)) {
            boolean closed = repository.transactional(() -> closeForRetention(handover));
            if (closed) {
                retentionClosed++;
            }
        }
        int intakePurged = repository.purgeExpiredIntake(now);
        int replayPurged = repository.purgeReplayRecordsBefore(now.minus(properties.getRetention()));
        return new HandoverMaintenanceResult(queueExpired, retentionClosed, intakePurged, replayPurged);
    }

    private boolean closeForRetention(RuntimeConversationHandoverRepository.HandoverRecord candidate) {
        RuntimeConversationHandoverRepository.HandoverRecord handover = repository.findById(candidate.handoverId())
            .orElse(null);
        if (handover == null
            || Set.of("CANCELLED", "CLOSED", "RETURNED_TO_AI").contains(handover.status())
            || handover.expiresAt().isAfter(clock.instant())) {
            return false;
        }
        ConversationControlSnapshot current = controlGateway.status(handover.scope());
        ConversationControlResult result = controlGateway.transition(new ConversationControlCommand(
            handover.scope(),
            ConversationControlCommandType.CLOSE,
            current.version(),
            "retention-close:" + handover.handoverId(),
            "retention-policy",
            "RETENTION_EXPIRED",
            handover.handoverId(),
            handover.channelBindingId()
        ));
        requireApplied(result);
        repository.updateHandover(handover.handoverId(), "CLOSED", null, null, null, true);
        addControlDispatch(
            handover.scope(),
            handover.handoverId(),
            handover.channelBindingId(),
            ConversationChannelDispatchType.CLOSE
        );
        appendEvent(handover.scope(), "CONVERSATION_CLOSED_BY_RETENTION", Map.of(
            "handoverId", handover.handoverId()
        ));
        return true;
    }

    public void verifyChannelCallback(String timestamp, String nonce, String body, String signature) {
        if (!StringUtils.hasText(nonce) || nonce.length() > 160) {
            throw forbidden("CHANNEL_CALLBACK_NONCE_INVALID", "The channel callback nonce is invalid.");
        }
        try {
            security.verify(timestamp, nonce, body, signature);
        } catch (SecurityException exception) {
            throw forbidden("CHANNEL_CALLBACK_SIGNATURE_INVALID", exception.getMessage());
        }
    }

    public HandoverIntakeSchema intakeSchema() {
        if (!properties.isContactIntakeEnabled()) {
            return new HandoverIntakeSchema("HANDOVER_INTAKE_V1", properties.isAnonymousAllowed(), List.of());
        }
        if (StringUtils.hasText(properties.getIntakeSchemaJson())) {
            return configuredIntakeSchema(properties.getIntakeSchemaJson());
        }
        Set<String> required = normalizedRequiredFields();
        return new HandoverIntakeSchema(
            "HANDOVER_INTAKE_V1",
            properties.isAnonymousAllowed(),
            List.of(
                new HandoverIntakeField(
                    "name", "Name", HandoverIntakeFieldType.TEXT,
                    required.contains("name"), 120, "Human-agent conversation continuity", false, List.of()
                ),
                new HandoverIntakeField(
                    "email", "Email", HandoverIntakeFieldType.EMAIL,
                    required.contains("email"), 254, "Optional follow-up after the live conversation", true, List.of()
                ),
                new HandoverIntakeField(
                    "phone", "Phone", HandoverIntakeFieldType.PHONE,
                    required.contains("phone"), 32, "Optional phone follow-up after the live conversation", true, List.of()
                )
            )
        );
    }

    private HandoverIntakeSchema configuredIntakeSchema(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode fields = root.path("fields");
            if (!root.isObject() || !fields.isArray() || fields.size() > 6) {
                throw new IllegalArgumentException("configured handover intake schema is invalid");
            }
            List<HandoverIntakeField> configured = new ArrayList<>();
            Set<String> keys = new LinkedHashSet<>();
            for (JsonNode field : fields) {
                String key = firstConfiguredText(field, "key", "id");
                String label = firstConfiguredText(field, "label");
                if (!StringUtils.hasText(key)
                    || !key.matches("^[A-Za-z][A-Za-z0-9_-]{0,63}$")
                    || !keys.add(key.toLowerCase(Locale.ROOT))
                    || !StringUtils.hasText(label)) {
                    throw new IllegalArgumentException("configured handover intake field identity is invalid");
                }
                configured.add(new HandoverIntakeField(
                    key,
                    bounded(label, 120),
                    configuredFieldType(field.path("type").asText("TEXT")),
                    field.path("required").asBoolean(false),
                    Math.max(1, Math.min(4_096, field.path("maxLength").asInt(500))),
                    bounded(field.path("purpose").asText(null), 240),
                    field.path("consentRequired").asBoolean(false),
                    configuredOptions(field.path("options"))
                ));
            }
            return new HandoverIntakeSchema(
                root.path("schemaVersion").asText("HANDOVER_INTAKE_V1"),
                root.path("anonymousAllowed").asBoolean(properties.isAnonymousAllowed()),
                configured
            );
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("LOOMAI_CONVERSATION_HANDOVER_INTAKE_SCHEMA_JSON is invalid.", exception);
        }
    }

    private HandoverIntakeFieldType configuredFieldType(String value) {
        String normalized = value == null ? "TEXT" : value.trim().toUpperCase(Locale.ROOT);
        if ("TEL".equals(normalized)) {
            normalized = "PHONE";
        }
        try {
            return HandoverIntakeFieldType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("configured handover intake field type is unsupported", exception);
        }
    }

    private List<String> configuredOptions(JsonNode values) {
        if (!values.isArray()) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        values.forEach(value -> {
            String normalized = bounded(value.asText(null), 120);
            if (StringUtils.hasText(normalized) && !options.contains(normalized) && options.size() < 20) {
                options.add(normalized);
            }
        });
        return List.copyOf(options);
    }

    private String firstConfiguredText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("").trim();
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private ConversationView view(ConversationScope scope, long afterMessageSequence) {
        ConversationControlSnapshot snapshot = controlGateway.status(scope);
        RuntimeConversationHandoverRepository.HandoverRecord handover = StringUtils.hasText(snapshot.activeHandoverId())
            ? repository.findForOwner(scope, snapshot.activeHandoverId()).orElse(null)
            : null;
        List<PublicConversationMessage> messages = controlGateway
            .messages(scope, Math.max(0, afterMessageSequence), 200)
            .stream()
            .map(this::toPublicMessage)
            .toList();
        List<ConversationEventView> events = repository.events(scope, null, EVENT_PAGE_SIZE).stream()
            .map(this::toEventView)
            .toList();
        return new ConversationView(
            scope.conversationId(), snapshot.state(), snapshot.version(), snapshot.activeHandoverId(),
            snapshot.channelBindingId(), handover == null ? null : handover.status(),
            handover == null ? null : handover.safeActorDisplayName(),
            messages, events, snapshot.updatedAt()
        );
    }

    private RuntimeConversationHandoverRepository.CanonicalConversationRecord requireCanonicalConversation(
        ConversationScope scope
    ) {
        return repository.findCanonicalConversation(scope).orElseThrow(() ->
            notFound("CONVERSATION_NOT_FOUND", "The conversation is not available."));
    }

    private CanonicalConversationSummary toCanonicalSummary(
        RuntimeConversationHandoverRepository.CanonicalConversationRecord record
    ) {
        return new CanonicalConversationSummary(
            record.conversationId(),
            ConversationControlState.valueOf(record.controlState()),
            record.createdAt(),
            record.lastInteractionAt(),
            record.userMessageCount()
        );
    }

    private PublicConversationMessage toPublicMessage(ConversationMessage message) {
        return new PublicConversationMessage(
            message.messageId(),
            message.sequence(),
            message.role().name(),
            safeChannelDisplayName(message),
            message.content(),
            message.clientMessageId(),
            publicMessageMetadata(message.metadata()),
            message.createdAt()
        );
    }

    private Map<String, Object> publicMessageMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> projection = new LinkedHashMap<>();
        copyPublicMetadata(metadata, projection, "attachmentRefs");
        copyPublicMetadata(metadata, projection, "deliveryState");
        return projection.isEmpty() ? Map.of() : Map.copyOf(projection);
    }

    private void copyPublicMetadata(
        Map<String, Object> source,
        Map<String, Object> target,
        String key
    ) {
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private IntakeAdmission validateIntake(HandoverRequest request) {
        Map<String, String> supplied = request.intake() == null ? Map.of() : request.intake();
        Set<String> consented = request.consentedFields() == null
            ? Set.of()
            : request.consentedFields().stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!properties.isContactIntakeEnabled()) {
            return new IntakeAdmission(true, List.of());
        }
        Set<String> allowed = intakeSchema().fields().stream()
            .map(field -> field.key().toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (supplied.keySet().stream().anyMatch(key -> key == null || !allowed.contains(key.trim().toLowerCase(Locale.ROOT)))) {
            throw badRequest("HANDOVER_INTAKE_FIELD_UNKNOWN", "Handover intake contains an unsupported field.");
        }
        List<RuntimeConversationHandoverRepository.IntakeValue> values = new ArrayList<>();
        for (HandoverIntakeField field : intakeSchema().fields()) {
            String value = supplied.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(field.key()))
                .map(Map.Entry::getValue)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .findFirst()
                .orElse(null);
            if (value == null) {
                if (field.required() && !request.continueAnonymously()) {
                    return new IntakeAdmission(false, List.of());
                }
                continue;
            }
            if (value.length() > field.maxLength()) {
                throw badRequest("HANDOVER_INTAKE_VALUE_TOO_LONG", field.label() + " is too long.");
            }
            validateField(field, value);
            boolean hasConsent = !field.consentRequired() || consented.contains(field.key());
            if (!hasConsent) {
                throw badRequest("HANDOVER_INTAKE_CONSENT_REQUIRED", "Consent is required to share " + field.label() + ".");
            }
            values.add(new RuntimeConversationHandoverRepository.IntakeValue(
                field.key(), value, field.purpose(), hasConsent, false
            ));
        }
        if (values.isEmpty() && !request.continueAnonymously()) {
            return new IntakeAdmission(false, List.of());
        }
        if (values.isEmpty() && !properties.isAnonymousAllowed()) {
            return new IntakeAdmission(false, List.of());
        }
        return new IntakeAdmission(true, List.copyOf(values));
    }

    private void validateField(HandoverIntakeField field, String value) {
        if (field.type() == HandoverIntakeFieldType.EMAIL && !EMAIL.matcher(value).matches()) {
            throw badRequest("HANDOVER_EMAIL_INVALID", "Email is invalid.");
        }
        if (field.type() == HandoverIntakeFieldType.PHONE && !PHONE.matcher(value).matches()) {
            throw badRequest("HANDOVER_PHONE_INVALID", "Phone is invalid.");
        }
    }

    private void addControlDispatch(
        ConversationScope scope,
        String handoverId,
        String channelBindingId,
        ConversationChannelDispatchType type
    ) {
        ConversationChannelDispatch dispatch = new ConversationChannelDispatch(
            deterministicId("dispatch-" + type.name().toLowerCase(Locale.ROOT), scope, handoverId),
            type,
            scope,
            handoverId,
            channelBindingId,
            controlGateway.status(scope).version(),
            null,
            null,
            List.of(),
            List.of(),
            Map.of(),
            clock.instant()
        );
        repository.addDispatch(dispatch, type.name().toLowerCase(Locale.ROOT) + ":" + handoverId);
    }

    private void appendSystemMessage(
        ConversationScope scope,
        String clientMessageId,
        String content,
        Map<String, Object> metadata
    ) {
        controlGateway.appendMessage(new ConversationMessageCommand(
            scope,
            deterministicId("system-message", scope, clientMessageId),
            clientMessageId,
            ConversationParticipantRole.SYSTEM,
            "conversation-control",
            content,
            metadata
        ), null);
    }

    private List<ConversationChannelMessage> channelTranscript(ConversationScope scope) {
        List<ConversationMessage> messages = controlGateway.messages(scope, 0, SNAPSHOT_SOURCE_WINDOW)
            .stream()
            .filter(message -> message.role() == ConversationParticipantRole.USER
                || message.role() == ConversationParticipantRole.AI
                || message.role() == ConversationParticipantRole.HUMAN)
            .toList();
        return boundedSnapshotMessages(messages).stream()
            .map(message -> new ConversationChannelMessage(
                message.messageId(),
                message.role(),
                safeChannelActorRef(message.role()),
                safeChannelDisplayName(message),
                message.content(),
                channelAttachmentRefs(message),
                message.createdAt()
            ))
            .toList();
    }

    private String safeChannelActorRef(ConversationParticipantRole role) {
        return switch (role) {
            case USER -> "conversation-user";
            case AI -> "ai-runtime";
            case HUMAN -> "human-participant";
            case SYSTEM -> "conversation-system";
        };
    }

    private String safeChannelDisplayName(ConversationMessage message) {
        return switch (message.role()) {
            case USER -> "Customer";
            case AI -> "AI assistant";
            case HUMAN -> {
                Object display = message.metadata().get("safeActorDisplayName");
                yield display == null || !StringUtils.hasText(display.toString())
                    ? "Human agent"
                    : bounded(display.toString(), 160);
            }
            case SYSTEM -> "System";
        };
    }

    private void appendEvent(ConversationScope scope, String type, Map<String, Object> payload) {
        repository.appendEvent(scope, "event-" + UUID.randomUUID(), type, payload);
    }

    private Map<String, Object> channelEventProjection(ConversationChannelEvent event) {
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("handoverId", event.handoverId());
        projection.put("externalConversationRef", event.externalConversationRef());
        if (StringUtils.hasText(event.messageId())) {
            projection.put("messageId", event.messageId());
        }
        if (StringUtils.hasText(event.safeActorDisplayName())) {
            projection.put("safeActorDisplayName", event.safeActorDisplayName());
        }
        return Map.copyOf(projection);
    }

    private ConversationEventView toEventView(RuntimeConversationHandoverRepository.ConversationEventRecord record) {
        return new ConversationEventView(
            record.eventId(),
            record.type(),
            publicEventPayload(record.payload()),
            record.createdAt()
        );
    }

    private Map<String, Object> publicEventPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Set<String> allowed = Set.of(
            "handoverId",
            "messageId",
            "messageSequence",
            "controlState",
            "safeActorDisplayName",
            "anonymous",
            "automatic",
            "deliveryState"
        );
        Map<String, Object> projection = new LinkedHashMap<>();
        payload.forEach((key, value) -> {
            if (allowed.contains(key) && value != null) {
                projection.put(key, value);
            }
        });
        return projection.isEmpty() ? Map.of() : Map.copyOf(projection);
    }

    private RuntimeConversationHandoverRepository.HandoverRecord requireHandover(
        ConversationScope scope,
        String handoverId
    ) {
        return repository.findForOwner(scope, handoverId).orElseThrow(() ->
            notFound("HANDOVER_NOT_FOUND", "The handover is not available."));
    }

    private ConversationScope scope(RuntimeResolvedIdentity identity, String conversationId) {
        OwnerScope owner = ownerScope(identity);
        return new ConversationScope(
            owner.tenantId(),
            owner.deploymentId(),
            required(conversationId, "conversationId is required."),
            owner.ownerId()
        );
    }

    private OwnerScope ownerScope(RuntimeResolvedIdentity identity) {
        if (identity == null || identity.getAuthContext() == null) {
            throw forbidden("VERIFIED_IDENTITY_REQUIRED", "Verified conversation identity is required.");
        }
        RuntimeAuthContext auth = identity.getAuthContext();
        return new OwnerScope(
            required(auth.getTenantId(), "Verified tenant is required."),
            required(auth.getDeploymentId(), "Verified deployment is required."),
            required(auth.ownerId(), "Verified conversation owner is required.")
        );
    }

    private ConversationScope scope(AIAccessSubjectContext auth, String conversationId) {
        if (auth == null) {
            throw forbidden("VERIFIED_IDENTITY_REQUIRED", "Verified conversation identity is required.");
        }
        return new ConversationScope(
            required(auth.getTenantId(), "Verified tenant is required."),
            required(auth.getDeploymentId(), "Verified deployment is required."),
            required(conversationId, "conversationId is required."),
            required(auth.getSubjectId(), "Verified conversation owner is required.")
        );
    }

    private void requireApplied(ConversationControlResult result) {
        if (result.outcome() != ConversationControlOutcome.APPLIED
            && result.outcome() != ConversationControlOutcome.IDEMPOTENT_REPLAY) {
            throw conflict(result.code(), result.message());
        }
    }

    private Set<String> normalizedRequiredFields() {
        Set<String> allowed = Set.of("name", "email", "phone");
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : properties.getRequiredIntakeFields()) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String key = value.trim().toLowerCase(Locale.ROOT);
            if (!allowed.contains(key)) {
                throw new IllegalStateException("Unsupported required handover intake field: " + key);
            }
            normalized.add(key);
        }
        return Set.copyOf(normalized);
    }

    private Map<String, Object> safeMetadata(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (StringUtils.hasText(key) && value != null && result.size() < 16) {
                Object safeValue = value instanceof Iterable<?> values
                    ? boundedStrings(
                        java.util.stream.StreamSupport.stream(values.spliterator(), false)
                            .filter(java.util.Objects::nonNull)
                            .map(String::valueOf)
                            .toList(),
                        12,
                        500
                    )
                    : bounded(String.valueOf(value), 500);
                result.put(key.trim(), safeValue);
            }
        });
        return Map.copyOf(result);
    }

    private List<String> channelAttachmentRefs(ConversationMessage message) {
        Object value = message.metadata().get("attachmentRefs");
        if (!(value instanceof Iterable<?> values)) {
            return List.of();
        }
        return boundedStrings(
            java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .toList(),
            12,
            500
        );
    }

    private List<String> boundedStrings(List<String> values, int maxCount, int maxLength) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .map(value -> bounded(value, maxLength))
            .distinct()
            .limit(maxCount)
            .toList();
    }

    private Duration boundedLease(Duration requested) {
        if (requested == null || requested.isNegative() || requested.isZero()) {
            return Duration.ofSeconds(30);
        }
        return requested.compareTo(Duration.ofMinutes(5)) > 0 ? Duration.ofMinutes(5) : requested;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw badRequest("AI_INTERACTION_REQUEST_INVALID", "AI interaction data is not JSON serializable.");
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored AI interaction response is invalid.", exception);
        }
    }

    private String requireIdempotency(String value) {
        String key = required(value, "Idempotency-Key is required.");
        if (key.length() > 200) {
            throw badRequest("IDEMPOTENCY_KEY_INVALID", "Idempotency-Key is too long.");
        }
        return key;
    }

    private static String deterministicId(String prefix, ConversationScope scope, String key) {
        try {
            String input = String.join("|", prefix, scope.tenantId(), scope.deploymentId(),
                scope.conversationId(), scope.ownerId(), required(key, "Deterministic ID key is required."));
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8)));
            return prefix + "-" + digest.substring(0, 32);
        } catch (ConversationHandoverException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to build a deterministic conversation identifier.", exception);
        }
    }

    private static String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw badRequest("REQUIRED_VALUE_MISSING", message);
        }
        return value.trim();
    }

    private static String bounded(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private static void requirePositive(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(label + " must be positive.");
        }
    }

    private static ConversationHandoverException badRequest(String code, String message) {
        return new ConversationHandoverException(code, message, HttpStatus.BAD_REQUEST);
    }

    private static ConversationHandoverException forbidden(String code, String message) {
        return new ConversationHandoverException(code, message, HttpStatus.FORBIDDEN);
    }

    private static ConversationHandoverException notFound(String code, String message) {
        return new ConversationHandoverException(code, message, HttpStatus.NOT_FOUND);
    }

    private static ConversationHandoverException conflict(String code, String message) {
        return new ConversationHandoverException(code, message, HttpStatus.CONFLICT);
    }

    private record IntakeAdmission(
        boolean accepted,
        List<RuntimeConversationHandoverRepository.IntakeValue> values
    ) {}

    private record OwnerScope(String tenantId, String deploymentId, String ownerId) {}
}
