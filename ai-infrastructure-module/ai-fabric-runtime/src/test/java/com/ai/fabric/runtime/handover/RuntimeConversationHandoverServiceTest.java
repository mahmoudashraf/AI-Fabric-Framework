package com.ai.fabric.runtime.handover;

import static com.ai.fabric.runtime.handover.ConversationHandoverContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.fabric.chat.channel.ConversationChannelDispatch;
import ai.fabric.chat.channel.ConversationChannelDispatchType;
import ai.fabric.chat.channel.ConversationChannelEvent;
import ai.fabric.chat.channel.ConversationChannelEventType;
import ai.fabric.chat.control.ConversationControlState;
import ai.fabric.chat.control.ConversationControlViolationException;
import ai.fabric.chat.control.ConversationMessage;
import ai.fabric.chat.control.DefaultConversationControlGateway;
import ai.fabric.chat.control.JdbcConversationControlStore;
import ai.fabric.dto.AIChatMessage;
import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.web.dto.ChatQueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class RuntimeConversationHandoverServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");
    private static final String CONVERSATION_ID = "conversation-1";
    private static final String BINDING_ID = "basic-inbox-1";

    private ObjectMapper objectMapper;
    private ConversationHandoverSecurity security;
    private RuntimeConversationHandoverRepository repository;
    private RuntimeConversationHandoverService service;
    private ConversationHandoverProperties properties;
    private RuntimeResolvedIdentity owner;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
            "jdbc:h2:mem:runtime-handover-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
        );
        new ResourceDatabasePopulator(
            new ClassPathResource("db/runtime-migration/V10__conversation_handover.sql"),
            new ClassPathResource("db/runtime-migration/V16__conversation_orchestration_state.sql")
        ).execute(dataSource);

        objectMapper = new ObjectMapper().findAndRegisterModules();
        clock = new MutableClock(NOW);
        properties = properties();
        security = new ConversationHandoverSecurity(properties, clock);
        repository = new RuntimeConversationHandoverRepository(
            dataSource,
            new DataSourceTransactionManager(dataSource),
            objectMapper,
            security,
            clock
        );
        var controlGateway = new DefaultConversationControlGateway(
            new JdbcConversationControlStore(dataSource, objectMapper, false),
            clock
        );
        service = new RuntimeConversationHandoverService(
            controlGateway,
            repository,
            properties,
            security,
            objectMapper,
            clock
        );
        owner = identity("anonymous-owner-1");
    }

    @Test
    void intakeRequirementDoesNotChangeControlOrCreateDispatch() {
        HandoverAdmission admission = service.request(
            owner,
            CONVERSATION_ID,
            "request-1",
            new HandoverRequest("USER_REQUESTED", false, Map.of(), List.of())
        );

        assertThat(admission.outcome()).isEqualTo("HANDOVER_INTAKE_REQUIRED");
        assertThat(admission.conversation().controlState()).isEqualTo(ConversationControlState.AI_ACTIVE);
        assertThat(admission.intakeSchema()).isNotNull();
        assertThat(repository.handoverStatusCounts()).isEmpty();
        assertThat(repository.dispatchStatusCounts()).isEmpty();
    }

    @Test
    void operationsOverviewSupportsAnEmptyPendingDispatchSet() {
        HandoverOperationsOverview overview = service.operationsOverview();

        assertThat(overview.oldestPendingDispatchAt()).isNull();
        assertThat(overview.dispatchCounts()).isEmpty();
        assertThat(overview.recentDispatches()).isEmpty();
    }

    @Test
    void canonicalAiInteractionIsClaimedOnceAndReplaysItsCompletedResponse() {
        ConversationMessage userMessage = service.appendUserMessage(
            owner,
            CONVERSATION_ID,
            "message-idempotency-1",
            "Please help me.",
            Map.of()
        );
        Map<String, Object> request = Map.of("query", "Please help me.", "mode", "conversational");

        AiInteractionAdmission claimed = service.claimAiInteraction(
            owner,
            CONVERSATION_ID,
            "message-idempotency-1",
            userMessage.messageId(),
            request
        );
        AiInteractionAdmission concurrentReplay = service.claimAiInteraction(
            owner,
            CONVERSATION_ID,
            "message-idempotency-1",
            userMessage.messageId(),
            request
        );

        assertThat(claimed.outcome()).isEqualTo("CLAIMED");
        assertThat(concurrentReplay.outcome()).isEqualTo("IN_PROGRESS");

        ChatQueryResponse response = ChatQueryResponse.builder()
            .success(true)
            .type("GENERATED_RESPONSE")
            .answer("I can help.")
            .conversationId(CONVERSATION_ID)
            .build();
        service.completeAiInteraction(
            owner,
            CONVERSATION_ID,
            "message-idempotency-1",
            200,
            response
        );

        AiInteractionAdmission completedReplay = service.claimAiInteraction(
            owner,
            CONVERSATION_ID,
            "message-idempotency-1",
            userMessage.messageId(),
            request
        );
        assertThat(completedReplay.outcome()).isEqualTo("COMPLETED_REPLAY");
        assertThat(completedReplay.responseHttpStatus()).isEqualTo(200);
        assertThat(completedReplay.response().getAnswer()).isEqualTo("I can help.");

        assertThatThrownBy(() -> service.claimAiInteraction(
            owner,
            CONVERSATION_ID,
            "message-idempotency-1",
            userMessage.messageId(),
            Map.of("query", "Changed request")
        ))
            .isInstanceOf(ConversationHandoverException.class)
            .extracting(exception -> ((ConversationHandoverException) exception).code())
            .isEqualTo("AI_INTERACTION_IDEMPOTENCY_CONFLICT");
    }

    @Test
    void completionAfterHumanTakeoverDoesNotPersistAnAiMessageOrSuccessReceipt() {
        var user = service.appendUserMessage(owner, CONVERSATION_ID, "slow-check", "Check my setup", Map.of());
        service.claimAiInteraction(owner, CONVERSATION_ID, "slow-check", user.messageId(), Map.of("query", "Check my setup"));
        var turn = service.beginAiTurn(owner, CONVERSATION_ID, user.sequence(), "slow-check");
        requestAnonymous("takeover-before-completion");
        var response = ChatQueryResponse.builder().success(true).answer("Stale AI answer").build();

        assertThatThrownBy(() -> service.commitAiInteraction(turn, "slow-check", 200, response))
            .isInstanceOf(ConversationControlViolationException.class);
        assertThat(service.history(owner, CONVERSATION_ID).messages())
            .noneMatch(message -> "Stale AI answer".equals(message.content()));
        assertThat(service.claimAiInteraction(owner, CONVERSATION_ID, "slow-check", user.messageId(),
            Map.of("query", "Check my setup")).outcome()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void acceptedCompletionStoresCanonicalMessageAndReplayTogether() {
        var user = service.appendUserMessage(owner, CONVERSATION_ID, "accepted-check", "Check my setup", Map.of());
        var request = Map.of("query", "Check my setup");
        service.claimAiInteraction(owner, CONVERSATION_ID, "accepted-check", user.messageId(), request);
        var turn = service.beginAiTurn(owner, CONVERSATION_ID, user.sequence(), "accepted-check");
        var response = ChatQueryResponse.builder().success(true).type("INFORMATION_PROVIDED")
            .answer("Accepted AI answer").sources(List.of(Map.of("title", "Setup guide")))
            .metadata(Map.of("debug", "private prompt")).authenticationRequirement(Map.of("token", "not restored")).build();

        service.commitAiInteraction(turn, "accepted-check", 200, response);

        assertThat(service.history(owner, CONVERSATION_ID).messages())
            .anyMatch(message -> "Accepted AI answer".equals(message.content()));
        var replay = service.claimAiInteraction(owner, CONVERSATION_ID, "accepted-check", user.messageId(), request);
        assertThat(replay.outcome()).isEqualTo("COMPLETED_REPLAY");
        assertThat(replay.response().getAnswer()).isEqualTo("Accepted AI answer");
        var presentation = service.completedPresentations(owner, CONVERSATION_ID);
        assertThat(presentation).hasSize(1);
        assertThat(presentation.getFirst().userMessageId()).isEqualTo(user.messageId());
        assertThat(presentation.getFirst().response().getSources()).hasSize(1);
        assertThat(presentation.getFirst().response().getMetadata()).isEmpty();
        assertThat(presentation.getFirst().response().getAuthenticationRequirement()).isEmpty();
        assertThatThrownBy(() -> service.completedPresentations(identity("foreign-owner"), CONVERSATION_ID))
            .isInstanceOf(ConversationControlViolationException.class);
    }

    @Test
    void blankOptionalAiMetadataDoesNotDiscardTheActualResponse() {
        ConversationMessage user = service.appendUserMessage(owner, CONVERSATION_ID,
            "metadata-message", "Show current stock.", Map.of());
        RuntimeConversationTurn turn = service.beginAiTurn(owner, CONVERSATION_ID,
            user.sequence(), "metadata-turn");

        ConversationMessage response = service.appendAiMessage(turn, "metadata-message:ai",
            "The provider returned a structured error.", Map.of(
                "providerRequestId", "", "resultType", "ERROR", "optional", " \t"
            ));

        assertThat(response.content()).isEqualTo("The provider returned a structured error.");
        assertThat(response.metadata()).containsOnly(Map.entry("resultType", "ERROR"));
    }

    @Test
    void lifecycleIsOwnerIsolatedReplaySafeAndResumesWithHumanTranscript() throws Exception {
        var activity = org.mockito.Mockito.mock(com.ai.fabric.runtime.lifecycle.admission.RuntimeAdmissionActivityService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "admissionActivity", activity);
        ConversationMessage firstUser = service.appendUserMessage(
            owner,
            CONVERSATION_ID,
            "message-1",
            "I need help with this order.",
            Map.of("attachmentRefs", List.of("attachment-1"))
        );
        RuntimeConversationTurn initialTurn = service.beginAiTurn(
            owner,
            CONVERSATION_ID,
            firstUser.sequence(),
            "turn-1"
        );
        service.appendAiMessage(
            initialTurn,
            "message-1:ai",
            "I can help or connect you to a person.",
            Map.of()
        );

        HandoverAdmission requested = requestAnonymous("handover-1");
        HandoverAdmission replayed = requestAnonymous("handover-1");
        assertThat(requested.outcome()).isEqualTo("HANDOVER_REQUESTED");
        assertThat(replayed.outcome()).isEqualTo("IDEMPOTENT_ACTIVE_HANDOVER");
        assertThat(repository.dispatchStatusCounts()).containsEntry("PENDING", 1L);

        DispatchClaim openClaim = service.claimDispatch("connector-1", Duration.ofSeconds(30));
        ConversationChannelDispatch open = (ConversationChannelDispatch) openClaim.dispatch();
        assertThat(open.type()).isEqualTo(ConversationChannelDispatchType.OPEN_HANDOVER);
        assertThat(open.transcript())
            .extracting(ai.fabric.chat.channel.ConversationChannelMessage::text)
            .containsExactly(
                "I need help with this order.",
                "I can help or connect you to a person."
            );
        assertThat(open.transcript().getFirst().actorRef()).isEqualTo("conversation-user");
        assertThat(open.transcript().getFirst().attachmentRefs()).containsExactly("attachment-1");
        assertThat(open.transcript())
            .extracting(message -> message.text())
            .containsExactly(
                "I need help with this order.",
                "I can help or connect you to a person."
            );
        assertThat(open.transcript())
            .extracting(message -> message.actorRef())
            .containsExactly("conversation-user", "ai-runtime");
        service.completeDispatch(
            openClaim.dispatchId(),
            new DispatchOutcome(true, "external-1", null, "ACCEPTED", "Accepted", false),
            openClaim.attempt()
        );

        ConversationView pending = service.view(owner, CONVERSATION_ID, 0);
        ConversationChannelEvent claimed = event(
            "event-claim",
            ConversationChannelEventType.HANDOVER_CLAIMED,
            pending,
            null,
            null
        );
        String claimedBody = objectMapper.writeValueAsString(claimed);
        ConversationView active = service.receiveChannelEvent(
            BINDING_ID,
            claimed,
            "nonce-claim",
            claimedBody
        );
        assertThat(active.controlState()).isEqualTo(ConversationControlState.HUMAN_ACTIVE);
        assertThat(active.safeHumanDisplayName()).isEqualTo("Alice");

        ConversationView exactReplay = service.receiveChannelEvent(
            BINDING_ID,
            claimed,
            "nonce-claim",
            claimedBody
        );
        assertThat(exactReplay.controlState()).isEqualTo(ConversationControlState.HUMAN_ACTIVE);
        ConversationView freshNonceReplay = service.receiveChannelEvent(
            BINDING_ID,
            claimed,
            "nonce-claim-retry",
            claimedBody
        );
        assertThat(freshNonceReplay.controlState()).isEqualTo(ConversationControlState.HUMAN_ACTIVE);
        assertThatThrownBy(() -> service.receiveChannelEvent(
            BINDING_ID,
            claimed,
            "nonce-claim",
            claimedBody + " "
        ))
            .isInstanceOf(ConversationHandoverException.class)
            .extracting(exception -> ((ConversationHandoverException) exception).code())
            .isEqualTo("CHANNEL_EVENT_IDEMPOTENCY_CONFLICT");

        ConversationChannelEvent humanMessage = event(
            "event-message",
            ConversationChannelEventType.HUMAN_MESSAGE,
            active,
            "provider-message-1",
            "I have reviewed this with you."
        );
        ConversationView afterHuman = service.receiveChannelEvent(
            BINDING_ID,
            humanMessage,
            "nonce-message",
            objectMapper.writeValueAsString(humanMessage)
        );
        assertThat(afterHuman.messages())
            .extracting(PublicConversationMessage::content)
            .contains("I have reviewed this with you.");
        org.mockito.Mockito.verify(activity).accepted(com.ai.fabric.runtime.lifecycle.admission.RuntimeAdmissionActivityService.Kind.HUMAN_WORK);

        ConversationChannelEvent reusedNonce = event(
            "event-message-2",
            ConversationChannelEventType.HUMAN_MESSAGE,
            afterHuman,
            "provider-message-2",
            "This must not be accepted."
        );
        assertThatThrownBy(() -> service.receiveChannelEvent(
            BINDING_ID,
            reusedNonce,
            "nonce-message",
            objectMapper.writeValueAsString(reusedNonce)
        ))
            .isInstanceOf(ConversationHandoverException.class)
            .extracting(exception -> ((ConversationHandoverException) exception).code())
            .isEqualTo("CHANNEL_CALLBACK_NONCE_REUSED");

        ConversationView resumePending = service.resumeAi(owner, CONVERSATION_ID, "resume-1");
        assertThat(resumePending.controlState()).isEqualTo(ConversationControlState.AI_RESUME_PENDING);
        assertThat(service.resumeAi(owner, CONVERSATION_ID, "resume-1").controlState())
            .isEqualTo(ConversationControlState.AI_RESUME_PENDING);
        DispatchClaim returnClaim = service.claimDispatch("connector-1", Duration.ofSeconds(30));
        assertThat(((ConversationChannelDispatch) returnClaim.dispatch()).type())
            .isEqualTo(ConversationChannelDispatchType.RETURN_TO_AI);
        service.completeDispatch(
            returnClaim.dispatchId(),
            new DispatchOutcome(true, "external-1", null, "ACCEPTED", "Accepted", false),
            returnClaim.attempt()
        );
        assertThat(service.resumeAi(owner, CONVERSATION_ID, "resume-1").controlState())
            .isEqualTo(ConversationControlState.AI_ACTIVE);

        ConversationMessage currentUser = service.appendUserMessage(
            owner,
            CONVERSATION_ID,
            "message-2",
            "Please continue with AI.",
            Map.of()
        );
        RuntimeConversationTurn resumedTurn = service.beginAiTurn(
            owner,
            CONVERSATION_ID,
            currentUser.sequence(),
            "turn-2"
        );
        List<String> history = resumedTurn.conversationSnapshot().historyMessages().stream()
            .map(AIChatMessage::getContent)
            .toList();
        assertThat(history)
            .contains(
                "I need help with this order.",
                "I can help or connect you to a person.",
                "[Human agent: Alice] I have reviewed this with you."
            )
            .doesNotContain("Please continue with AI.");

        assertThatThrownBy(() -> service.view(identity("another-owner"), CONVERSATION_ID, 0))
            .isInstanceOf(ConversationControlViolationException.class)
            .extracting(exception -> ((ConversationControlViolationException) exception).code())
            .isEqualTo("CONVERSATION_OWNER_MISMATCH");
    }

    @Test
    void dispatchesRemainFifoAcrossFailureAndExplicitRecovery() {
        ConversationView pending = requestAnonymous("handover-fifo").conversation();
        service.appendHumanBoundUserMessage(
            owner,
            CONVERSATION_ID,
            "pending-message-1",
            "Please also tell them this.",
            List.of()
        );

        DispatchClaim openClaim = service.claimDispatch("connector-1", Duration.ofSeconds(30));
        assertThat(((ConversationChannelDispatch) openClaim.dispatch()).type())
            .isEqualTo(ConversationChannelDispatchType.OPEN_HANDOVER);
        assertThat(service.claimDispatch("connector-2", Duration.ofSeconds(30))).isNull();

        service.completeDispatch(
            openClaim.dispatchId(),
            new DispatchOutcome(false, null, null, "PROVIDER_DOWN", "Provider unavailable", false),
            openClaim.attempt()
        );
        assertThat(service.claimDispatch("connector-2", Duration.ofSeconds(30))).isNull();

        service.retryDispatch("deployment-1", openClaim.dispatchId());
        DispatchClaim retriedOpen = service.claimDispatch("connector-2", Duration.ofSeconds(30));
        assertThat(retriedOpen.dispatchId()).isEqualTo(openClaim.dispatchId());
        service.completeDispatch(
            retriedOpen.dispatchId(),
            new DispatchOutcome(true, "external-fifo", null, "ACCEPTED", "Accepted", false),
            retriedOpen.attempt()
        );

        DispatchClaim userMessage = service.claimDispatch("connector-2", Duration.ofSeconds(30));
        assertThat(((ConversationChannelDispatch) userMessage.dispatch()).type())
            .isEqualTo(ConversationChannelDispatchType.USER_MESSAGE);
        assertThat(((ConversationChannelDispatch) userMessage.dispatch()).handoverId())
            .isEqualTo(pending.handoverId());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 8})
    void retryableDispatchExhaustsExactlyAtTheConfiguredFiniteLimit(int limit) {
        properties.setDispatchMaxAttempts(limit);
        requestAnonymous("handover-retry-bound");
        var initialEvents = service.view(owner, CONVERSATION_ID, 0).events();
        DispatchClaim first = null;

        for (int attempt = 1; attempt <= limit; attempt++) {
            DispatchClaim claimed = service.claimDispatch("connector-1", Duration.ofSeconds(30));
            assertThat(claimed).isNotNull();
            if (first == null) first = claimed;
            assertThat(claimed.dispatchId()).isEqualTo(first.dispatchId());
            assertThat(claimed.dispatch()).isEqualTo(first.dispatch());
            assertThat(claimed.attempt()).isEqualTo(attempt);

            service.completeDispatch(claimed.dispatchId(), new DispatchOutcome(
                false, null, null, "PROVIDER_HTTP_503", "Provider unavailable", true
            ), claimed.attempt());

            var stored = repository.findDispatch(claimed.dispatchId()).orElseThrow();
            assertThat(stored.status()).isEqualTo(attempt < limit ? "PENDING" : "DEAD");
            assertThat(stored.attemptCount()).isEqualTo(attempt);
            assertThat(service.claimDispatch("connector-2", Duration.ofSeconds(30))).isNull();
            if (attempt < limit) clock.advance(Duration.ofSeconds(1L << attempt));
        }

        assertThat(repository.dispatchStatusCounts()).containsOnlyKeys("DEAD");
        var view = service.view(owner, CONVERSATION_ID, 0);
        assertThat(view.controlState()).isEqualTo(ConversationControlState.HUMAN_PENDING);
        assertThat(view.events()).isEqualTo(initialEvents);
    }

    @Test
    void humanMessageRecoversAfterFifthFailureWithoutChangingIdentityOrDuplicatingDelivery() throws Exception {
        properties.setDispatchMaxAttempts(8);
        requestAnonymous("handover-desk-restart");
        DispatchClaim open = service.claimDispatch("connector-1", Duration.ofSeconds(30));
        service.completeDispatch(open.dispatchId(), new DispatchOutcome(
            true, "external-1", null, "ACCEPTED", "Accepted", false
        ), open.attempt());
        var claimed = event("claim-desk-restart", ConversationChannelEventType.HANDOVER_CLAIMED,
            service.view(owner, CONVERSATION_ID, 0), null, null);
        service.receiveChannelEvent(BINDING_ID, claimed, "nonce-desk-restart", objectMapper.writeValueAsString(claimed));

        String messageKey = "message-desk-restart";
        String text = "Please keep this message for my support agent.";
        var accepted = service.appendHumanBoundUserMessage(owner, CONVERSATION_ID, messageKey, text, List.of());
        service.appendHumanBoundUserMessage(owner, CONVERSATION_ID,
            "later-message-desk-restart", "Please deliver this later message in order.", List.of());
        DispatchClaim first = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            DispatchClaim dispatch = service.claimDispatch("connector-1", Duration.ofSeconds(30));
            assertThat(dispatch).isNotNull();
            if (first == null) first = dispatch;
            assertThat(dispatch.dispatchId()).isEqualTo(first.dispatchId());
            assertThat(dispatch.dispatch()).isEqualTo(first.dispatch());
            assertThat(dispatch.attempt()).isEqualTo(attempt);
            service.completeDispatch(dispatch.dispatchId(), new DispatchOutcome(
                false, null, null, "PROVIDER_HTTP_503", "Provider unavailable", true
            ), dispatch.attempt());
            assertThat(repository.findDispatch(dispatch.dispatchId()).orElseThrow().status()).isEqualTo("PENDING");
            assertThat(service.claimDispatch("connector-2", Duration.ofSeconds(30))).isNull();
            clock.advance(Duration.ofSeconds(1L << attempt));
        }

        DispatchClaim recovery = service.claimDispatch("connector-2", Duration.ofSeconds(30));
        assertThat(recovery.attempt()).isEqualTo(6);
        assertThat(recovery.dispatchId()).isEqualTo(first.dispatchId());
        assertThat(recovery.dispatch()).isEqualTo(first.dispatch());
        assertThat(((ConversationChannelDispatch) recovery.dispatch()).type()).isEqualTo(ConversationChannelDispatchType.USER_MESSAGE);
        assertThat(service.view(owner, CONVERSATION_ID, 0).controlState()).isEqualTo(ConversationControlState.HUMAN_ACTIVE);
        DispatchOutcome delivered = new DispatchOutcome(true, "external-1", "provider-message-1", "ACCEPTED", "Accepted", false);
        assertThatThrownBy(() -> service.completeDispatch(recovery.dispatchId(), delivered, 5))
            .isInstanceOf(ConversationHandoverException.class)
            .extracting(exception -> ((ConversationHandoverException) exception).code())
            .isEqualTo("DISPATCH_ATTEMPT_STALE");
        service.completeDispatch(recovery.dispatchId(), delivered, recovery.attempt());
        assertThatThrownBy(() -> service.completeDispatch(recovery.dispatchId(), delivered, recovery.attempt()))
            .isInstanceOf(ConversationHandoverException.class);

        var replay = service.appendHumanBoundUserMessage(owner, CONVERSATION_ID, messageKey, text, List.of());
        assertThat(replay.messageId()).isEqualTo(accepted.messageId());
        assertThat(replay.conversation().messages()).filteredOn(message -> messageKey.equals(message.clientMessageId())).hasSize(1);
        assertThat(replay.conversation().events()).filteredOn(event -> "USER_MESSAGE_ACCEPTED".equals(event.type())
            && accepted.messageId().equals(event.payload().get("messageId"))).hasSize(1);
        assertThat(replay.conversation().controlState()).isEqualTo(ConversationControlState.HUMAN_ACTIVE);
        assertThat(repository.dispatchStatusCounts()).containsEntry("DELIVERED", 2L).containsEntry("PENDING", 1L);
        var later = service.claimDispatch("connector-1", Duration.ofSeconds(30));
        assertThat(later).isNotNull();
        assertThat(later.dispatchId()).isNotEqualTo(recovery.dispatchId());
        assertThat(later.attempt()).isEqualTo(1);
        assertThat(((ConversationChannelDispatch) later.dispatch()).type()).isEqualTo(ConversationChannelDispatchType.USER_MESSAGE);
        service.completeDispatch(later.dispatchId(), new DispatchOutcome(
            true, "external-1", "provider-message-2", "ACCEPTED", "Accepted", false
        ), later.attempt());
        assertThat(repository.dispatchStatusCounts()).containsOnlyKeys("DELIVERED").containsEntry("DELIVERED", 3L);
        assertThat(service.claimDispatch("connector-1", Duration.ofSeconds(30))).isNull();
    }

    @Test
    void permanentFailureDoesNotUseAdditionalDeskAttempts() {
        properties.setDispatchMaxAttempts(8);
        requestAnonymous("handover-permanent-provider-error");
        var dispatch = service.claimDispatch("connector-1", Duration.ofSeconds(30));

        service.completeDispatch(dispatch.dispatchId(), new DispatchOutcome(
            false, null, null, "PROVIDER_HTTP_403", "Provider denied the request", false
        ), dispatch.attempt());

        var stored = repository.findDispatch(dispatch.dispatchId()).orElseThrow();
        assertThat(stored.status()).isEqualTo("DEAD");
        assertThat(stored.attemptCount()).isEqualTo(1);
        clock.advance(Duration.ofMinutes(1));
        assertThat(service.claimDispatch("connector-1", Duration.ofSeconds(30))).isNull();
        assertThat(service.view(owner, CONVERSATION_ID, 0).controlState()).isEqualTo(ConversationControlState.HUMAN_PENDING);
    }

    @Test
    void duplicateHumanBoundMessageReplaysWithoutCreatingAnotherDispatch() {
        var activity = org.mockito.Mockito.mock(com.ai.fabric.runtime.lifecycle.admission.RuntimeAdmissionActivityService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "admissionActivity", activity);
        requestAnonymous("handover-message-replay");

        HumanMessageAcceptance first = service.appendHumanBoundUserMessage(
            owner,
            CONVERSATION_ID,
            "pending-message-replay",
            "Please also tell them this.",
            List.of("attachment-1")
        );
        HumanMessageAcceptance replay = service.appendHumanBoundUserMessage(
            owner,
            CONVERSATION_ID,
            "pending-message-replay",
            "Please also tell them this.",
            List.of("attachment-1")
        );

        assertThat(replay.messageId()).isEqualTo(first.messageId());
        assertThat(replay.conversation().messages())
            .filteredOn(message -> "pending-message-replay".equals(message.clientMessageId()))
            .hasSize(1);
        assertThat(replay.conversation().events())
            .filteredOn(event -> "USER_MESSAGE_ACCEPTED".equals(event.type()))
            .hasSize(1);
        assertThat(repository.dispatchStatusCounts()).containsEntry("PENDING", 2L);
        org.mockito.Mockito.verify(activity, org.mockito.Mockito.times(1)).accepted(
            com.ai.fabric.runtime.lifecycle.admission.RuntimeAdmissionActivityService.Kind.HUMAN_WORK);
    }

    @Test
    void callbackSignatureAndTimestampFailClosed() {
        String body = "{\"eventId\":\"event-1\"}";
        String timestamp = Long.toString(NOW.getEpochSecond());
        String nonce = "nonce-1";

        service.verifyChannelCallback(
            timestamp,
            nonce,
            body,
            security.sign(timestamp, nonce, body)
        );

        assertThatThrownBy(() -> service.verifyChannelCallback(timestamp, nonce, body, "bad"))
            .isInstanceOf(ConversationHandoverException.class)
            .extracting(exception -> ((ConversationHandoverException) exception).code())
            .isEqualTo("CHANNEL_CALLBACK_SIGNATURE_INVALID");
        assertThatThrownBy(() -> service.verifyChannelCallback(
            Long.toString(NOW.minus(Duration.ofHours(1)).getEpochSecond()),
            nonce,
            body,
            security.sign(
                Long.toString(NOW.minus(Duration.ofHours(1)).getEpochSecond()),
                nonce,
                body
            )
        ))
            .isInstanceOf(ConversationHandoverException.class)
            .extracting(exception -> ((ConversationHandoverException) exception).code())
            .isEqualTo("CHANNEL_CALLBACK_SIGNATURE_INVALID");
    }

    @Test
    void queueTimeoutNeverResumesAiAndSupportsExplicitRetry() {
        ConversationView pending = requestAnonymous("handover-timeout").conversation();
        assertThat(pending.controlState()).isEqualTo(ConversationControlState.HUMAN_PENDING);

        clock.advance(Duration.ofMinutes(16));
        HandoverMaintenanceResult maintenance = service.runMaintenance();
        ConversationView expired = service.view(owner, CONVERSATION_ID, 0);
        assertThat(maintenance.queueExpired()).isEqualTo(1);
        assertThat(expired.controlState()).isEqualTo(ConversationControlState.HUMAN_PENDING);
        assertThat(expired.handoverStatus()).isEqualTo("EXPIRED");
        assertThat(expired.events())
            .extracting(ConversationEventView::type)
            .contains("HANDOVER_EXPIRED");

        ConversationView retried = service.retry(
            owner,
            CONVERSATION_ID,
            expired.handoverId(),
            "retry-timeout-1"
        );
        assertThat(retried.controlState()).isEqualTo(ConversationControlState.HUMAN_PENDING);
        assertThat(retried.handoverId()).isEqualTo(expired.handoverId());
        assertThat(retried.handoverStatus()).isEqualTo("PENDING");
        assertThat(service.retry(owner, CONVERSATION_ID, expired.handoverId(), "retry-timeout-1")
            .handoverStatus()).isEqualTo("PENDING");
    }

    @Test
    void expiredOpenDispatchIsNotDeliveredUntilOwnerRetries() {
        ConversationView pending = requestAnonymous("handover-expired-dispatch").conversation();

        clock.advance(Duration.ofMinutes(16));
        HandoverMaintenanceResult maintenance = service.runMaintenance();

        assertThat(maintenance.queueExpired()).isEqualTo(1);
        assertThat(service.view(owner, CONVERSATION_ID, 0).handoverStatus()).isEqualTo("EXPIRED");
        assertThat(service.claimDispatch("connector-1", Duration.ofSeconds(30))).isNull();
        assertThat(repository.dispatchStatusCounts()).containsEntry("SKIPPED", 1L);

        ConversationView retried = service.retry(
            owner,
            CONVERSATION_ID,
            pending.handoverId(),
            "retry-expired-dispatch"
        );
        DispatchClaim claim = service.claimDispatch("connector-1", Duration.ofSeconds(30));

        assertThat(retried.handoverStatus()).isEqualTo("PENDING");
        assertThat(claim).isNotNull();
        assertThat(((ConversationChannelDispatch) claim.dispatch()).type())
            .isEqualTo(ConversationChannelDispatchType.OPEN_HANDOVER);
        assertThat(repository.recentDispatches(10)).hasSize(2);
        assertThat(service.operationsOverview().recentDispatches())
            .filteredOn(dispatch -> "SKIPPED".equals(dispatch.status()))
            .singleElement()
            .satisfies(dispatch -> assertThat(dispatch.retryEligible()).isFalse());
    }

    @Test
    void obsoleteOpenDispatchIsSkippedWithoutBlockingCancellation() {
        ConversationView pending = requestAnonymous("handover-cancel-before-delivery").conversation();
        service.cancel(owner, CONVERSATION_ID, pending.handoverId(), "cancel-before-delivery");

        DispatchClaim claim = service.claimDispatch("connector-1", Duration.ofSeconds(30));

        assertThat(claim).isNotNull();
        assertThat(((ConversationChannelDispatch) claim.dispatch()).type())
            .isEqualTo(ConversationChannelDispatchType.CANCEL_HANDOVER);
        assertThat(repository.dispatchStatusCounts())
            .containsEntry("SKIPPED", 1L)
            .containsEntry("PROCESSING", 1L);
    }

    @Test
    void maintenanceRepairsPersistedLifecycleDeadLetterWithoutSkippingProviderFailures() {
        ConversationView pending = requestAnonymous("handover-stale-dead-repair").conversation();
        DispatchClaim open = service.claimDispatch("connector-1", Duration.ofSeconds(30));
        service.cancel(owner, CONVERSATION_ID, pending.handoverId(), "cancel-stale-dead-repair");
        service.completeDispatch(
            open.dispatchId(),
            new DispatchOutcome(
                false,
                null,
                null,
                "HANDOVER_LIFECYCLE_STALE",
                "The handover lifecycle no longer accepts this dispatch.",
                false
            ),
            open.attempt()
        );
        service.request(
            owner,
            "conversation-provider-failure",
            "handover-provider-failure",
            new HandoverRequest("USER_REQUESTED", true, Map.of(), List.of())
        );
        DispatchClaim providerFailure = service.claimDispatch("connector-1", Duration.ofSeconds(30));
        service.completeDispatch(
            providerFailure.dispatchId(),
            new DispatchOutcome(
                false,
                null,
                null,
                "PROVIDER_DOWN",
                "Provider unavailable.",
                false
            ),
            providerFailure.attempt()
        );

        assertThat(repository.dispatchStatusCounts())
            .containsEntry("DEAD", 2L)
            .containsEntry("PENDING", 1L);

        service.runMaintenance();
        DispatchClaim cancel = service.claimDispatch("connector-1", Duration.ofSeconds(30));

        assertThat(cancel).isNotNull();
        assertThat(((ConversationChannelDispatch) cancel.dispatch()).type())
            .isEqualTo(ConversationChannelDispatchType.CANCEL_HANDOVER);
        assertThat(repository.dispatchStatusCounts())
            .containsEntry("DEAD", 1L)
            .containsEntry("SKIPPED", 1L)
            .containsEntry("PROCESSING", 1L);
    }

    @Test
    void adminCannotRetryOpenDispatchForExpiredHandover() {
        requestAnonymous("handover-expired-admin-retry");
        DispatchClaim claim = service.claimDispatch("connector-1", Duration.ofSeconds(30));
        service.completeDispatch(
            claim.dispatchId(),
            new DispatchOutcome(false, null, null, "PROVIDER_DOWN", "Provider unavailable", false),
            claim.attempt()
        );

        clock.advance(Duration.ofMinutes(16));
        assertThat(service.runMaintenance().queueExpired()).isEqualTo(1);

        assertThatThrownBy(() -> service.retryDispatch("deployment-1", claim.dispatchId()))
            .isInstanceOf(ConversationHandoverException.class)
            .extracting(exception -> ((ConversationHandoverException) exception).code())
            .isEqualTo("DISPATCH_HANDOVER_STATE_INVALID");
    }

    @Test
    void maintenanceDoesNotExpireHandoverWithProcessingDispatch() {
        requestAnonymous("handover-processing-dispatch");
        DispatchClaim claim = service.claimDispatch("connector-1", Duration.ofSeconds(30));

        clock.advance(Duration.ofMinutes(16));
        HandoverMaintenanceResult maintenance = service.runMaintenance();

        assertThat(claim).isNotNull();
        assertThat(maintenance.queueExpired()).isZero();
        assertThat(service.view(owner, CONVERSATION_ID, 0).handoverStatus()).isEqualTo("PENDING");
        assertThat(repository.dispatchStatusCounts()).containsEntry("PROCESSING", 1L);
    }

    @Test
    void canonicalHistoryIsOwnerScopedSafelyProjectedAndExplicitlyClosed() {
        ConversationMessage userMessage = service.appendUserMessage(
            owner,
            CONVERSATION_ID,
            "history-message-1",
            "Please help with my account.",
            Map.of(
                "attachmentRefs", List.of("attachment-1"),
                "internalTrace", "must-not-be-public"
            )
        );
        RuntimeConversationTurn turn = service.beginAiTurn(
            owner,
            CONVERSATION_ID,
            userMessage.sequence(),
            "history-turn-1"
        );
        service.appendAiMessage(turn, "history-message-1:ai", "How can I help?", Map.of(
            "providerRequestId", "provider-secret-reference"
        ));

        List<CanonicalConversationSummary> summaries = service.listConversations(owner);
        ConversationView history = service.history(owner, CONVERSATION_ID);

        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.conversationId()).isEqualTo(CONVERSATION_ID);
            assertThat(summary.controlState()).isEqualTo(ConversationControlState.AI_ACTIVE);
            assertThat(summary.userMessageCount()).isEqualTo(1);
        });
        assertThat(history.messages())
            .extracting(PublicConversationMessage::role)
            .containsExactly("USER", "AI");
        assertThat(history.messages().getFirst().participantDisplayName()).isEqualTo("Customer");
        assertThat(history.messages().getFirst().metadata())
            .containsEntry("attachmentRefs", List.of("attachment-1"))
            .doesNotContainKey("internalTrace");
        assertThat(history.messages().get(1).metadata()).doesNotContainKey("providerRequestId");
        assertThat(service.listConversations(identity("another-owner"))).isEmpty();
        assertThatThrownBy(() -> service.history(identity("another-owner"), CONVERSATION_ID))
            .isInstanceOf(ConversationHandoverException.class)
            .extracting(exception -> ((ConversationHandoverException) exception).code())
            .isEqualTo("CONVERSATION_NOT_FOUND");

        ConversationView closed = service.closeConversation(
            owner,
            CONVERSATION_ID,
            "close-history-1"
        );
        assertThat(closed.controlState()).isEqualTo(ConversationControlState.CLOSED);
        assertThat(service.closeConversation(owner, CONVERSATION_ID, "close-history-1").controlState())
            .isEqualTo(ConversationControlState.CLOSED);
        assertThat(service.listConversations(owner).getFirst().controlState())
            .isEqualTo(ConversationControlState.CLOSED);
    }

    private HandoverAdmission requestAnonymous(String idempotencyKey) {
        return service.request(
            owner,
            CONVERSATION_ID,
            idempotencyKey,
            new HandoverRequest("USER_REQUESTED", true, Map.of(), List.of())
        );
    }

    private ConversationChannelEvent event(
        String eventId,
        ConversationChannelEventType type,
        ConversationView view,
        String messageId,
        String text
    ) {
        return new ConversationChannelEvent(
            eventId,
            type,
            BINDING_ID,
            view.handoverId(),
            "external-1",
            "operator-1",
            "Alice",
            messageId,
            text,
            view.controlVersion(),
            Map.of("provider", "test"),
            NOW
        );
    }

    private RuntimeResolvedIdentity identity(String ownerId) {
        return RuntimeResolvedIdentity.builder()
            .authContext(RuntimeAuthContext.builder()
                .subjectId(ownerId)
                .subjectType(RuntimeAuthSubjectType.ANONYMOUS_SESSION)
                .authMode(RuntimeAuthMode.PUBLIC_RUNTIME_ANONYMOUS)
                .callerType(RuntimeAuthCallerType.PUBLIC_BROWSER)
                .deploymentId("deployment-1")
                .customerId("customer-1")
                .tenantId("tenant-1")
                .sessionId(ownerId)
                .grantedScopes(List.of("chat:messages", "chat:handover:request", "chat:handover:control"))
                .build())
            .warnings(List.of())
            .build();
    }

    private ConversationHandoverProperties properties() {
        ConversationHandoverProperties properties = new ConversationHandoverProperties();
        properties.setEnabled(true);
        properties.setChannelBindingId(BINDING_ID);
        properties.setEncryptionSecret("runtime-encryption-secret-1234567890-abcd");
        properties.setCallbackSigningSecret("runtime-signing-secret-1234567890-abcdef");
        properties.setAnonymousAllowed(true);
        properties.setContactIntakeEnabled(true);
        properties.setQueueTimeout(Duration.ofMinutes(15));
        properties.setCallbackClockSkew(Duration.ofMinutes(5));
        return properties;
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
