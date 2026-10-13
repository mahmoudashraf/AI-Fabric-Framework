package com.loomai.inbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.fabric.chat.channel.ConversationChannelDispatch;
import ai.fabric.chat.channel.ConversationChannelDispatchType;
import ai.fabric.chat.channel.ConversationChannelMessage;
import ai.fabric.chat.control.ConversationParticipantRole;
import ai.fabric.chat.control.ConversationScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.inbox.config.BasicInboxProperties;
import com.loomai.inbox.model.InboxContracts.CallbackClaim;
import com.loomai.inbox.model.InboxContracts.OperatorIdentity;
import com.loomai.inbox.model.InboxContracts.ReplyRequest;
import com.loomai.inbox.persistence.BasicInboxRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class BasicInboxServiceTest {

    private static final String BINDING_ID = "basic-inbox-1";
    private static final String HANDOVER_ID = "handover-1";
    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    private ObjectMapper objectMapper;
    private BasicInboxRepository repository;
    private BasicInboxService service;
    private OperatorIdentity operator;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
            "jdbc:h2:mem:basic-inbox-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
        );
        new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V1__basic_inbox.sql"),
            new ClassPathResource("db/migration/V2__callback_fifo_index.sql")
        ).execute(dataSource);

        BasicInboxProperties properties = properties();
        InboxSecurityService security = new InboxSecurityService(properties);
        security.validate();
        objectMapper = new ObjectMapper().findAndRegisterModules();
        repository = new BasicInboxRepository(dataSource, objectMapper, security);
        service = new BasicInboxService(properties, repository, security, objectMapper);
        operator = security.requireOperator("operator-key-1234567890-1234567890");
    }

    @Test
    void completeProviderLifecyclePreservesTranscriptAndCallbackOrder() throws Exception {
        ConversationChannelDispatch open = openDispatch("dispatch-open-1", "Please connect me.");
        String rawOpen = objectMapper.writeValueAsString(open);

        assertThat(service.dispatch(open, rawOpen).accepted()).isTrue();
        assertThat(service.dispatch(open, rawOpen).accepted()).isTrue();
        assertThat(service.detail(operator, HANDOVER_ID).messages())
            .extracting(message -> message.content())
            .containsExactly("Please connect me.", "I can connect you to a person.");
        assertThat(service.detail(operator, HANDOVER_ID).consentedIntake())
            .containsEntry("name", "Sam");

        ConversationChannelDispatch conflicting = openDispatch(
            "dispatch-open-1",
            "Different transcript data"
        );
        assertThatThrownBy(() -> service.dispatch(
            conflicting,
            objectMapper.writeValueAsString(conflicting)
        ))
            .isInstanceOf(BasicInboxRepository.InboxConflictException.class)
            .extracting(error -> ((BasicInboxRepository.InboxConflictException) error).code())
            .isEqualTo("DISPATCH_ID_PAYLOAD_CONFLICT");

        assertThat(service.claim(operator, HANDOVER_ID, "claim-1").outcome())
            .isEqualTo("CLAIM_QUEUED");
        assertThat(service.claim(operator, HANDOVER_ID, "claim-2").outcome())
            .isEqualTo("CLAIM_ALREADY_PENDING");
        CallbackClaim claim = repository.claimCallback(Duration.ofSeconds(30)).orElseThrow();
        assertThat(claim.eventType()).isEqualTo("HANDOVER_CLAIMED");
        assertThat(repository.completeCallbackAndUpdateConversation(
            claim.callbackId(), HANDOVER_ID, claim.attempt(), claim.leaseOwner(), "ACTIVE", 2
        )).isTrue();
        assertThat(repository.claimCallback(Duration.ofSeconds(30))).isEmpty();

        assertThat(service.reply(operator, HANDOVER_ID, new ReplyRequest("First reply", "reply-1")).outcome())
            .isEqualTo("MESSAGE_QUEUED");
        assertThat(service.reply(operator, HANDOVER_ID, new ReplyRequest("Second reply", "reply-2")).outcome())
            .isEqualTo("MESSAGE_QUEUED");

        CallbackClaim firstReply = repository.claimCallback(Duration.ofSeconds(30)).orElseThrow();
        assertThat(firstReply.eventType()).isEqualTo("HUMAN_MESSAGE");
        assertThat(repository.failCallback(
            firstReply.callbackId(), firstReply.attempt(), firstReply.leaseOwner(),
            false, 1, Duration.ofSeconds(1), "RUNTIME_REJECTED"
        )).isTrue();
        assertThat(repository.claimCallback(Duration.ofSeconds(30))).isEmpty();

        var retried = service.retryCallback(operator, firstReply.callbackId(), "retry-1");
        assertThat(retried.outcome()).isEqualTo("CALLBACK_RETRY_QUEUED");
        assertThat(service.retryCallback(operator, firstReply.callbackId(), "retry-1").outcome())
            .isEqualTo("CALLBACK_RETRY_REPLAYED");

        CallbackClaim retriedFirst = repository.claimCallback(Duration.ofSeconds(30)).orElseThrow();
        assertThat(retriedFirst.callbackId()).isEqualTo(firstReply.callbackId());
        assertThat(repository.completeCallbackAndUpdateConversation(
            retriedFirst.callbackId(), HANDOVER_ID, retriedFirst.attempt(),
            retriedFirst.leaseOwner(), "ACTIVE", 2
        )).isTrue();
        CallbackClaim secondReply = repository.claimCallback(Duration.ofSeconds(30)).orElseThrow();
        assertThat(secondReply.callbackId()).isNotEqualTo(firstReply.callbackId());

        assertThatThrownBy(() -> service.reply(
            operator,
            HANDOVER_ID,
            new ReplyRequest("Changed reply", "reply-1")
        ))
            .isInstanceOf(BasicInboxRepository.InboxConflictException.class)
            .extracting(error -> ((BasicInboxRepository.InboxConflictException) error).code())
            .isEqualTo("IDEMPOTENCY_KEY_PAYLOAD_CONFLICT");
    }

    @Test
    void rejectsDispatchFromAnotherTenantOrDeploymentEvenWhenBindingMatches() throws Exception {
        ConversationChannelDispatch wrongTenant = openDispatch(
            "dispatch-wrong-tenant",
            "handover-wrong-tenant",
            new ConversationScope("tenant-2", "deployment-1", "conversation-2", "opaque-owner-2"),
            "Please connect me."
        );
        ConversationChannelDispatch wrongDeployment = openDispatch(
            "dispatch-wrong-deployment",
            "handover-wrong-deployment",
            new ConversationScope("tenant-1", "deployment-2", "conversation-3", "opaque-owner-3"),
            "Please connect me."
        );

        assertThatThrownBy(() -> service.dispatch(
            wrongTenant,
            objectMapper.writeValueAsString(wrongTenant)
        ))
            .isInstanceOf(BasicInboxRepository.InboxConflictException.class)
            .extracting(error -> ((BasicInboxRepository.InboxConflictException) error).code())
            .isEqualTo("DEPLOYMENT_SCOPE_MISMATCH");
        assertThatThrownBy(() -> service.dispatch(
            wrongDeployment,
            objectMapper.writeValueAsString(wrongDeployment)
        ))
            .isInstanceOf(BasicInboxRepository.InboxConflictException.class)
            .extracting(error -> ((BasicInboxRepository.InboxConflictException) error).code())
            .isEqualTo("DEPLOYMENT_SCOPE_MISMATCH");
        assertThat(service.queue(operator, null, 100)).isEmpty();
    }

    @Test
    void returnToAiWaitsForEarlierHumanCallbacksBeforeAcknowledging() throws Exception {
        ConversationChannelDispatch open = openDispatch("dispatch-open-return", "Please connect me.");
        assertThat(service.dispatch(open, objectMapper.writeValueAsString(open)).accepted()).isTrue();
        assertThat(service.claim(operator, HANDOVER_ID, "claim-return").outcome())
            .isEqualTo("CLAIM_QUEUED");
        CallbackClaim claim = repository.claimCallback(Duration.ofSeconds(30)).orElseThrow();
        assertThat(repository.completeCallbackAndUpdateConversation(
            claim.callbackId(), HANDOVER_ID, claim.attempt(), claim.leaseOwner(), "ACTIVE", 2
        )).isTrue();

        assertThat(service.reply(
            operator,
            HANDOVER_ID,
            new ReplyRequest("One final message", "reply-before-return")
        ).outcome()).isEqualTo("MESSAGE_QUEUED");

        ConversationChannelDispatch returnDispatch = controlDispatch(
            "dispatch-return-1",
            ConversationChannelDispatchType.RETURN_TO_AI,
            3
        );
        String rawReturn = objectMapper.writeValueAsString(returnDispatch);
        var deferred = service.dispatch(returnDispatch, rawReturn);
        assertThat(deferred.accepted()).isFalse();
        assertThat(deferred.retryable()).isTrue();
        assertThat(deferred.code()).isEqualTo("PROVIDER_CALLBACKS_PENDING");
        assertThat(service.detail(operator, HANDOVER_ID).conversation().status()).isEqualTo("ACTIVE");

        CallbackClaim reply = repository.claimCallback(Duration.ofSeconds(30)).orElseThrow();
        assertThat(reply.eventType()).isEqualTo("HUMAN_MESSAGE");
        assertThat(repository.completeCallbackAndUpdateConversation(
            reply.callbackId(), HANDOVER_ID, reply.attempt(), reply.leaseOwner(), "ACTIVE", 2
        )).isTrue();

        var accepted = service.dispatch(returnDispatch, rawReturn);
        assertThat(accepted.accepted()).isTrue();
        assertThat(accepted.retryable()).isFalse();
        assertThat(service.detail(operator, HANDOVER_ID).conversation().status())
            .isEqualTo("RETURNED_TO_AI");
        assertThat(service.dispatch(returnDispatch, rawReturn)).isEqualTo(accepted);
    }

    private ConversationChannelDispatch openDispatch(String dispatchId, String firstMessage) {
        return openDispatch(
            dispatchId,
            HANDOVER_ID,
            new ConversationScope("tenant-1", "deployment-1", "conversation-1", "opaque-owner-1"),
            firstMessage
        );
    }

    private ConversationChannelDispatch openDispatch(
        String dispatchId,
        String handoverId,
        ConversationScope scope,
        String firstMessage
    ) {
        return new ConversationChannelDispatch(
            dispatchId,
            ConversationChannelDispatchType.OPEN_HANDOVER,
            scope,
            handoverId,
            BINDING_ID,
            1,
            null,
            null,
            List.of(),
            List.of(
                new ConversationChannelMessage(
                    "message-user-1", ConversationParticipantRole.USER,
                    "conversation-user", "Customer", firstMessage,
                    List.of("attachment-1"), NOW
                ),
                new ConversationChannelMessage(
                    "message-ai-1", ConversationParticipantRole.AI,
                    "ai-runtime", "AI assistant", "I can connect you to a person.",
                    List.of(), NOW.plusSeconds(1)
                )
            ),
            Map.of("name", "Sam"),
            NOW.plusSeconds(2)
        );
    }

    private ConversationChannelDispatch controlDispatch(
        String dispatchId,
        ConversationChannelDispatchType type,
        long expectedControlVersion
    ) {
        return new ConversationChannelDispatch(
            dispatchId,
            type,
            new ConversationScope("tenant-1", "deployment-1", "conversation-1", "opaque-owner-1"),
            HANDOVER_ID,
            BINDING_ID,
            expectedControlVersion,
            null,
            null,
            List.of(),
            List.of(),
            Map.of(),
            NOW.plusSeconds(3)
        );
    }

    private BasicInboxProperties properties() {
        BasicInboxProperties properties = new BasicInboxProperties();
        properties.setBindingId(BINDING_ID);
        properties.setTenantId("tenant-1");
        properties.setDeploymentId("deployment-1");
        properties.setProviderApiKey("provider-key-1234567890-1234567890");
        properties.setConnectorCallbackUrl("http://localhost:8080/integrations/conversation-channel/events");
        properties.setCallbackSigningSecret("callback-signing-secret-1234567890-abcd");
        properties.setDataEncryptionSecret("data-encryption-secret-1234567890-abcdef");
        properties.setOperators(
            "operator-1|Alice|operator-key-1234567890-1234567890|" + BINDING_ID
        );
        return properties;
    }
}
