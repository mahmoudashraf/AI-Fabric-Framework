package com.ai.fabric.runtime.handover;

import static com.ai.fabric.runtime.handover.ConversationHandoverContracts.*;
import static org.assertj.core.api.Assertions.*;

import ai.fabric.chat.control.ConversationControlGateway;
import ai.fabric.chat.control.ConversationControlState;
import ai.fabric.chat.control.DefaultConversationControlGateway;
import ai.fabric.chat.control.JdbcConversationControlStore;
import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Connection;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RuntimeConversationHandoverTransactionPostgresTest.PersistenceConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "loomai.conversation-handover.enabled=true",
    "loomai.conversation-handover.channel-binding-id=transaction-test-inbox",
    "loomai.conversation-handover.encryption-secret=transaction-test-encryption-secret-1234567890",
    "loomai.conversation-handover.callback-signing-secret=transaction-test-signing-secret-1234567890"
})
class RuntimeConversationHandoverTransactionPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final List<String> TABLES = List.of(
        "ai_conversation_control", "ai_conversation_control_command", "ai_conversation_message",
        "ai_conversation_ai_interaction", "ai_conversation_handover", "ai_conversation_handover_operation",
        "ai_conversation_handover_intake", "ai_conversation_handover_dispatch", "ai_conversation_event",
        "ai_conversation_channel_event_receipt", "ai_conversation_channel_callback_nonce",
        "ai_conversation_orchestration_state", "handover_transaction_probe"
    );

    @Autowired HikariDataSource dataSource;
    @Autowired JpaTransactionManager transactionManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired RuntimeConversationHandoverRepository repository;
    @Autowired ConversationControlGateway gateway;
    @Autowired ConversationHandoverProperties properties;
    @Autowired ConversationHandoverSecurity security;
    @Autowired ObjectMapper mapper;

    private JdbcTemplate jdbc;
    private EntityManager entityManager;
    private RuntimeConversationHandoverService service;

    @BeforeEach
    void setUp() {
        new ResourceDatabasePopulator(
            new ClassPathResource("db/runtime-migration/V10__conversation_handover.sql"),
            new ClassPathResource("db/runtime-migration/V16__conversation_orchestration_state.sql")
        ).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER TABLE ai_conversation_handover_dispatch DROP CONSTRAINT IF EXISTS reject_test_dispatch");
        jdbc.execute("TRUNCATE TABLE " + String.join(", ", TABLES) + " CASCADE");
        entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        service = new RuntimeConversationHandoverService(gateway, repository, properties, security, mapper, Clock.systemUTC());
    }

    @Test
    void directHandoverBeforeAnyAiQueryCommitsControlAndDispatch() {
        assertEmpty();

        var result = request("direct");

        assertThat(result.outcome()).isEqualTo("HANDOVER_REQUESTED");
        assertThat(result.conversation().controlState()).isEqualTo(ConversationControlState.HUMAN_PENDING);
        assertCommittedHandover();
        assertThat(request("direct").outcome()).isEqualTo("IDEMPOTENT_ACTIVE_HANDOVER");
        assertThat(count("ai_conversation_handover")).isOne();
        assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
        assertThat(TransactionSynchronizationManager.hasResource(entityManagerFactory)).isFalse();
    }

    @Test
    void handoverJoinsAnOwningJpaTransactionAndCommitsAllWritesTogether() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            persistProbe("commit");
            assertThat(request("commit").outcome()).isEqualTo("HANDOVER_REQUESTED");
            assertCommittedHandover();
            assertThat(count("handover_transaction_probe")).isOne();
            assertInvisibleOutsideTransaction();
        });

        assertCommittedHandover();
        assertThat(count("handover_transaction_probe")).isOne();
        assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
    }

    @Test
    void failureAfterAllWritesRollsBackJpaControlHandoverDispatchAndEvents() {
        assertThatThrownBy(() -> repository.transactional(() -> {
            persistProbe("rollback");
            assertThat(request("rollback").outcome()).isEqualTo("HANDOVER_REQUESTED");
            assertCommittedHandover();
            throw new IllegalStateException("failure after handover writes");
        })).isInstanceOf(IllegalStateException.class).hasMessage("failure after handover writes");

        assertEmpty();
        assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
        assertThat(TransactionSynchronizationManager.hasResource(entityManagerFactory)).isFalse();
        assertThat(request("rollback").outcome()).isEqualTo("HANDOVER_REQUESTED");
        assertCommittedHandover();
    }

    @Test
    void dispatchFailureRollsBackControlTransitionAndPreservesItsOriginalError() {
        jdbc.execute("ALTER TABLE ai_conversation_handover_dispatch ADD CONSTRAINT reject_test_dispatch"
            + " CHECK (dispatch_type <> 'OPEN_HANDOVER')");
        try {
            assertThatThrownBy(() -> request("dispatch-failure"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("reject_test_dispatch");
        } finally {
            jdbc.execute("ALTER TABLE ai_conversation_handover_dispatch DROP CONSTRAINT reject_test_dispatch");
        }
        assertEmpty();
        assertThat(request("dispatch-failure").outcome()).isEqualTo("HANDOVER_REQUESTED");
        assertCommittedHandover();
    }

    private HandoverAdmission request(String key) {
        var owner = RuntimeResolvedIdentity.builder().authContext(RuntimeAuthContext.builder()
            .subjectId("owner-1").subjectType(RuntimeAuthSubjectType.ANONYMOUS_SESSION)
            .authMode(RuntimeAuthMode.PUBLIC_RUNTIME_ANONYMOUS).callerType(RuntimeAuthCallerType.PUBLIC_BROWSER)
            .deploymentId("deployment-1").customerId("customer-1").tenantId("tenant-1").sessionId("owner-1")
            .grantedScopes(List.of("chat:messages", "chat:handover:request", "chat:handover:control")).build())
            .warnings(List.of()).build();
        return service.request(owner, "conversation-1", key,
            new HandoverRequest("USER_REQUESTED", true, Map.of(), List.of()));
    }

    private void persistProbe(String id) {
        entityManager.persist(new TransactionProbe(id));
        entityManager.flush();
    }

    private void assertCommittedHandover() {
        assertThat(jdbc.queryForObject("SELECT control_state FROM ai_conversation_control", String.class))
            .isEqualTo("HUMAN_PENDING");
        assertThat(count("ai_conversation_control_command")).isOne();
        assertThat(count("ai_conversation_handover")).isOne();
        assertThat(count("ai_conversation_handover_dispatch")).isOne();
        assertThat(count("ai_conversation_message")).isOne();
        assertThat(count("ai_conversation_event")).isOne();
    }

    private void assertInvisibleOutsideTransaction() {
        // A fresh pool connection must not observe any part of the pending commit.
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            for (String table : TABLES) {
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong(1)).as(table).isZero();
                }
            }
        } catch (java.sql.SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    private void assertEmpty() {
        for (String table : TABLES) assertThat(count(table)).as(table).isZero();
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    @Entity
    @Table(name = "handover_transaction_probe")
    static class TransactionProbe {
        @Id private String id;
        protected TransactionProbe() { }
        TransactionProbe(String id) { this.id = id; }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ConversationHandoverConfiguration.class)
    static class PersistenceConfiguration {
        @Bean(destroyMethod = "close")
        HikariDataSource dataSource() {
            var source = new HikariDataSource();
            source.setJdbcUrl(POSTGRES.getJdbcUrl());
            source.setUsername(POSTGRES.getUsername());
            source.setPassword(POSTGRES.getPassword());
            source.setMaximumPoolSize(4);
            return source;
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(HikariDataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setPackagesToScan(RuntimeConversationHandoverTransactionPostgresTest.class.getPackageName());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        ConversationControlGateway conversationControlGateway(
            HikariDataSource source, ObjectMapper mapper, JpaTransactionManager transactionManager
        ) {
            var gateway = new DefaultConversationControlGateway(
                new JdbcConversationControlStore(source, mapper, false, transactionManager));
            // Exercise the real gateway through a Spring JPA REQUIRED boundary.
            var attributes = new MatchAlwaysTransactionAttributeSource();
            attributes.setTransactionAttribute(new DefaultTransactionAttribute());
            var proxy = new ProxyFactory();
            proxy.setInterfaces(ConversationControlGateway.class);
            proxy.setTarget(gateway);
            proxy.addAdvice(new TransactionInterceptor(transactionManager, attributes));
            return (ConversationControlGateway) proxy.getProxy();
        }
    }
}
