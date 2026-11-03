package com.ai.fabric.runtime.handover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ai.fabric.chat.control.ConversationControlGateway;
import ai.fabric.chat.control.DefaultConversationControlGateway;
import ai.fabric.chat.control.JdbcConversationControlStore;
import com.ai.fabric.runtime.auth.*;
import com.ai.fabric.runtime.chat.RuntimeConversationGateway;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.ai.fabric.runtime.lifecycle.admission.RuntimeAdmissionActivityService;
import com.ai.fabric.runtime.web.ChatRuntimeController;
import com.ai.fabric.runtime.web.ConversationChannelPrivateController;
import com.ai.fabric.runtime.web.ConversationHandoverController;
import com.ai.fabric.runtime.web.ConversationHandoverErrorHandler;
import com.ai.fabric.runtime.web.RuntimeControllerAdvice;
import com.ai.fabric.runtime.web.admin.ConversationHandoverAdminController;
import com.ai.fabric.runtime.web.dto.ChatQueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CanonicalConversationControlPostgresTest.FoundationConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "loomai.conversation-control.enabled=true",
    "loomai.conversation-handover.enabled=false",
    "loomai.conversation-handover.encryption-secret=control-test-encryption-secret-1234567890",
    "loomai.conversation-handover.callback-signing-secret=control-test-fingerprint-secret-1234567890"
})
class CanonicalConversationControlPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String BASE = "/api/chat/me/conversations/case-1";
    private static final List<String> SCOPES = List.of("chat:messages", "chat:conversations", "chat:handover:read");
    @Autowired HikariDataSource dataSource;
    @Autowired ApplicationContext context;
    @Autowired RuntimeConversationHandoverService service;
    @Autowired RuntimePrivateAssertionService assertions;
    @Autowired ChatRuntimeController chat;
    @Autowired RuntimeConversationGateway legacy;
    private MockMvc mvc;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        new ResourceDatabasePopulator(
            new ClassPathResource("db/runtime-migration/V10__conversation_handover.sql"),
            new ClassPathResource("db/runtime-migration/V16__conversation_orchestration_state.sql")
        ).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE ai_conversation_control, ai_conversation_control_command, ai_conversation_message, "
            + "ai_conversation_ai_interaction, ai_conversation_handover, ai_conversation_handover_operation, "
            + "ai_conversation_handover_intake, ai_conversation_handover_dispatch, ai_conversation_event, "
            + "ai_conversation_channel_event_receipt, ai_conversation_channel_callback_nonce, "
            + "ai_conversation_orchestration_state CASCADE");
        clearInvocations(chat, legacy);
        // Only the generated AI result is stubbed; admission, leases, persistence and replay are real.
        doReturn(ResponseEntity.ok(ChatQueryResponse.builder().success(true)
            .type("INFORMATION_PROVIDED").conversationId("case-1").answer("Canonical answer")
            .sources(List.of()).actions(List.of()).metadata(Map.of()).build()))
            .when(chat).controlledQuery(any(), any(), any());
        mvc = MockMvcBuilders.standaloneSetup(chat, context.getBean(ConversationHandoverController.class))
            .setControllerAdvice(context.getBean(ConversationHandoverErrorHandler.class), new RuntimeControllerAdvice())
            .build();
    }

    @Test
    void foundationOnlyStartsOwnedConversationAndReplaysOneDurableInteraction() throws Exception {
        assertThat(context.getBeansOfType(RequestConversationHandoverAction.class)).isEmpty();
        assertThat(context.getBeansOfType(ConversationChannelPrivateController.class)).isEmpty();
        assertThat(context.getBeansOfType(ConversationHandoverAdminController.class)).isEmpty();
        mvc.perform(owner(get(BASE + "/control"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.controlState").value("AI_ACTIVE"))
            .andExpect(jsonPath("$.messages").isEmpty());
        mvc.perform(owner(get(BASE))).andExpect(status().isOk())
            .andExpect(jsonPath("$.participantMessages").isEmpty());
        String first = mvc.perform(owner(message("message-1"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("Canonical answer"))
            .andReturn().getResponse().getContentAsString();
        String replay = mvc.perform(owner(message("message-1"))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(replay).isEqualTo(first);
        verify(chat, times(1)).controlledQuery(any(), any(), any());
        mvc.perform(owner(get(BASE))).andExpect(status().isOk())
            .andExpect(jsonPath("$.controlState").value("AI_ACTIVE"))
            .andExpect(jsonPath("$.participantMessages.length()").value(2))
            .andExpect(jsonPath("$.participantMessages[0].clientMessageId").value("message-1"));
        mvc.perform(owner(get("/api/chat/me/conversations"))).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("case-1"));
        mvc.perform(owner(get(BASE + "/events"))).andExpect(status().isOk())
            .andExpect(request().asyncStarted());
        assertThat(count("ai_conversation_ai_interaction")).isOne();
        assertNoHumanWork();
        verifyNoInteractions(legacy);
    }

    @Test
    void canonicalCloseIsIdempotentTerminalAndCannotRestartOrUseLegacyQuery() throws Exception {
        mvc.perform(owner(message("message-1"))).andExpect(status().isOk());
        mvc.perform(owner(delete(BASE).header("Idempotency-Key", "close-1"))).andExpect(status().isNoContent());
        mvc.perform(owner(delete(BASE).header("Idempotency-Key", "close-1"))).andExpect(status().isNoContent());
        mvc.perform(owner(get(BASE + "/control"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.controlState").value("CLOSED"));
        mvc.perform(owner(message("message-2"))).andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("CONVERSATION_CONTROLLED"));
        for (String route : List.of("/api/chat/me/query", "/api/chat/me/query-once")) {
            mvc.perform(owner(post(route).contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"Bypass\"}")))
                .andExpect(status().isGone());
        }
        assertThat(count("ai_conversation_ai_interaction")).isOne();
        assertNoHumanWork();
        verifyNoInteractions(legacy);
    }

    @Test
    void foreignOwnerCannotReadReplayCloseOrObserveEvents() throws Exception {
        mvc.perform(owner(message("message-1"))).andExpect(status().isOk());
        for (var route : List.of(get(BASE), get(BASE + "/control"), get(BASE + "/events"),
            message("message-1"), delete(BASE).header("Idempotency-Key", "foreign-close"))) {
            mvc.perform(auth(route, "foreign-owner", SCOPES)).andExpect(status().isNotFound());
        }
        assertThat(count("ai_conversation_message")).isEqualTo(2);
        assertThat(count("ai_conversation_control_command")).isZero();
    }

    @Test
    void humanTransitionsDeniedEvenWhenPrivateCallerHasEveryHumanScope() throws Exception {
        mvc.perform(owner(get(BASE + "/control"))).andExpect(status().isOk());
        var scopes = new java.util.ArrayList<>(SCOPES);
        scopes.addAll(List.of("chat:handover:request", "chat:handover:control"));
        for (String route : List.of("/handover-requests", "/handover-requests/unused/cancel",
            "/handover-requests/unused/retry", "/ai-resume-requests")) {
            mvc.perform(auth(post(BASE + route).header("Idempotency-Key", "human-1"), "owner-1", scopes))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONVERSATION_HANDOVER_DISABLED"));
        }
        var result = new RequestConversationHandoverAction(service).execute(null,
            ConversationHandoverRequestProvenance.EXPLICIT_USER_REQUEST,
            new ai.fabric.intent.action.ActionContext(ai.fabric.intent.orchestration.OrchestrationContext.builder()
                .conversationId("case-1").requestId("action-1").metadata(Map.of(
                    "subjectId", "owner-1", "tenantId", "tenant-1", "deploymentId", "deployment-1")).build(), null));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("CONVERSATION_HANDOVER_DISABLED");
        assertThat(count("ai_conversation_control_command")).isZero();
        assertNoHumanWork();
    }

    @Test
    void controlStillRequiresVerifiedIdentityScopeAndPrivateIngressKey() throws Exception {
        mvc.perform(get(BASE + "/control")).andExpect(status().isUnauthorized());
        mvc.perform(auth(get(BASE + "/control"), "owner-1", List.of("chat:messages")))
            .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/control").header("X-AIFABRIC-RUNTIME-AUTHORIZATION", token("owner-1", SCOPES)))
            .andExpect(status().isUnauthorized());
        assertThat(count("ai_conversation_control")).isZero();
    }

    private MockHttpServletRequestBuilder message(String key) {
        return post(BASE + "/messages").header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"Opening message\"}");
    }

    private MockHttpServletRequestBuilder owner(MockHttpServletRequestBuilder request) {
        return auth(request, "owner-1", SCOPES);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String owner, List<String> scopes) {
        return request.header("X-AIFABRIC-RUNTIME-AUTHORIZATION", token(owner, scopes))
            .header("X-AIFABRIC-RUNTIME-API-KEY", "private-test-ingress-key");
    }

    private String token(String owner, List<String> scopes) {
        return "Bearer " + assertions.issueAssertion(RuntimeAuthContext.builder().subjectId(owner)
            .subjectType(RuntimeAuthSubjectType.END_USER).authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED)
            .callerType(RuntimeAuthCallerType.TRUSTED_BACKEND).issuer("test-host").audiences(List.of("runtime"))
            .tenantId("tenant-1").deploymentId("deployment-1").customerId("customer-1")
            .expiresAt(Instant.now().plusSeconds(120)).grantedScopes(scopes).build());
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private void assertNoHumanWork() {
        assertThat(count("ai_conversation_handover")).isZero();
        assertThat(count("ai_conversation_handover_dispatch")).isZero();
        assertThat(count("ai_conversation_handover_intake")).isZero();
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ConversationHandoverConfiguration.class, RuntimeConversationHandoverService.class,
        ConversationHandoverController.class, ConversationHandoverErrorHandler.class,
        RequestConversationHandoverAction.class, ConversationChannelPrivateController.class,
        ConversationHandoverAdminController.class})
    static class FoundationConfiguration {
        @Bean(destroyMethod = "close") HikariDataSource dataSource() {
            var source = new HikariDataSource();
            source.setJdbcUrl(POSTGRES.getJdbcUrl());
            source.setUsername(POSTGRES.getUsername());
            source.setPassword(POSTGRES.getPassword());
            return source;
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean DataSourceTransactionManager transactionManager(HikariDataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean ConversationControlGateway conversationControlGateway(HikariDataSource source,
            ObjectMapper mapper, DataSourceTransactionManager transactions) {
            return new DefaultConversationControlGateway(new JdbcConversationControlStore(source, mapper, false, transactions));
        }
        @Bean RuntimeAdmissionActivityService admissionActivity() { return mock(RuntimeAdmissionActivityService.class); }
        @Bean RuntimeAuthProperties authProperties() {
            var properties = new RuntimeAuthProperties();
            properties.getIngress().getPrivateAssertions().setSigningKey("private-test-signing-secret-1234567890");
            properties.getIngress().getTrustedBackend().setApiKeyValue("private-test-ingress-key");
            return properties;
        }
        @Bean RuntimePrivateAssertionService assertions(RuntimeAuthProperties properties) {
            return new RuntimePrivateAssertionService(properties);
        }
        @Bean RuntimeRequestAuthResolver resolver(RuntimeAuthProperties properties, RuntimePrivateAssertionService assertions) {
            return new RuntimeRequestAuthResolver(properties, assertions, null);
        }
        @Bean RuntimeConversationGateway legacy() { return mock(RuntimeConversationGateway.class); }
        @Bean ChatRuntimeController chat(ObjectProvider<RuntimeConversationHandoverService> service,
            RuntimeRequestAuthResolver resolver, RuntimeConversationGateway legacy) {
            return spy(new ChatRuntimeController(emptyProvider(), legacy, emptyProvider(), emptyProvider(),
                emptyProvider(), emptyProvider(), service, resolver));
        }
        @SuppressWarnings("unchecked")
        private <T> ObjectProvider<T> emptyProvider() {
            return mock(ObjectProvider.class);
        }
    }
}
