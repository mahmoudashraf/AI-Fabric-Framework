package com.loomai.inbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.inbox.config.BasicInboxProperties;
import com.loomai.inbox.config.ManagedDependencyProperties;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ManagedDependencyLifecycleServiceTest {
    private ManagedDependencyLifecycleService service;
    private JdbcTemplate jdbc;

    @BeforeEach void setUp() {
        JdbcDataSource data = new JdbcDataSource();
        data.setURL("jdbc:h2:mem:inbox-lifecycle-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
        new ResourceDatabasePopulator(
            new ClassPathResource("db/basic-inbox-migration/V1__basic_inbox.sql"),
            new ClassPathResource("db/basic-inbox-migration/V2__callback_fifo_index.sql"),
            new ClassPathResource("db/basic-inbox-migration/V3__handover_scoped_message_identity.sql"),
            new ClassPathResource("db/basic-inbox-migration/V4__managed_dependency_lifecycle.sql"),
            new ClassPathResource("db/basic-inbox-migration/V5__managed_dependency_current_operation.sql")).execute(data);
        BasicInboxProperties inbox = new BasicInboxProperties();
        inbox.setBindingId("binding"); inbox.setTenantId("tenant"); inbox.setDeploymentId("deployment");
        inbox.setConnectorCallbackUrl("https://runtime.example.test/events");
        jdbc = new JdbcTemplate(data);
        service = new ManagedDependencyLifecycleService(properties(), inbox, jdbc,
            new ObjectMapper().findAndRegisterModules());
    }

    @Test void exactInboxBindingInitializesAndReplays() {
        assertThat(service.execute(request("INITIALIZE", "init")).evidenceReference())
            .isEqualTo("inbox:binding:callback-host:runtime.example.test");
        assertThat(service.execute(request("INITIALIZE", "init")).replay()).isTrue();
    }

    @Test void unusedIsDeniedWhileAnOpenConversationRemains() {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO inbox_conversation (handover_id,binding_id,external_conversation_ref,tenant_id,deployment_id,conversation_id,owner_ref,status,expected_control_version,unread_count,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            "handover", "binding", "external", "tenant", "deployment", "conversation", "owner", "OPEN", 1L, 0,
            Timestamp.from(now), Timestamp.from(now), 0L);
        assertThatThrownBy(() -> service.execute(request("VERIFY_UNUSED", "unused")))
            .hasMessageContaining("MANAGED_DEPENDENCY_ACTIVE_WORK_REMAINS");
    }

    @Test void currentReadbackIsFreshReadOnlyAndReflectsStateAfterAReplayedInitialization() {
        var initialized = service.execute(request("INITIALIZE", "init"));
        service.execute(request("DISABLE", "disable"));
        assertThat(service.execute(request("INITIALIZE", "init")).state()).isEqualTo("READY");
        var before = jdbc.queryForMap("SELECT * FROM loomai_managed_dependency_state WHERE singleton_id = 1");
        Long receipts = jdbc.queryForObject("SELECT COUNT(*) FROM loomai_managed_dependency_operation", Long.class);

        var status = service.current("fresh-challenge-012345");

        assertThat(status.challenge()).isEqualTo("fresh-challenge-012345");
        assertThat(status.current().state()).isEqualTo("DISABLED");
        assertThat(status.current().operationKey()).isEqualTo("disable");
        assertThat(status.current().observedAt()).isAfterOrEqualTo(initialized.observedAt());
        assertThat(status.current().replay()).isFalse();
        assertThat(service.available()).isFalse();
        assertThat(jdbc.queryForMap("SELECT * FROM loomai_managed_dependency_state WHERE singleton_id = 1")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loomai_managed_dependency_operation", Long.class)).isEqualTo(receipts);
    }

    @Test void currentReadbackTracksNewInitializationAndRejectsMissingOrForeignState() {
        assertThatThrownBy(() -> service.current("fresh-challenge-012345"))
            .hasMessageContaining("MANAGED_DEPENDENCY_NOT_INITIALIZED");
        service.execute(request("INITIALIZE", "generation-1"));
        service.execute(request("INITIALIZE", "generation-2"));
        service.execute(request("INITIALIZE", "generation-1"));
        assertThat(service.current("fresh-challenge-012345").current().operationKey()).isEqualTo("generation-2");
        jdbc.update("UPDATE loomai_managed_dependency_state SET binding_id='foreign' WHERE singleton_id=1");
        assertThatThrownBy(() -> service.current("fresh-challenge-012345"))
            .hasMessageContaining("MANAGED_DEPENDENCY_CURRENT_STATE_UNPROVEN");
    }

    @Test void currentUnusedStateCountsConversationsCreatedAfterTheReceipt() {
        service.execute(request("VERIFY_UNUSED", "unused"));
        Instant now = Instant.now();
        jdbc.update("INSERT INTO inbox_conversation (handover_id,binding_id,external_conversation_ref,tenant_id,deployment_id,conversation_id,owner_ref,status,expected_control_version,unread_count,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            "handover", "binding", "external", "tenant", "deployment", "conversation", "owner", "OPEN", 1L, 0,
            Timestamp.from(now), Timestamp.from(now), 0L);

        assertThat(service.execute(request("VERIFY_UNUSED", "unused")).activeWork()).isZero();
        var status = service.current("fresh-challenge-012345");
        assertThat(status.current().state()).isEqualTo("UNUSED");
        assertThat(status.current().operationKey()).isEqualTo("unused");
        assertThat(status.current().activeWork()).isEqualTo(1);
    }

    private ManagedDependencyProperties properties() { return new ManagedDependencyProperties("k".repeat(32),
        "customer", "tenant", "deployment", "target", "version", "inbox", "service-ref", "account", "binding"); }
    private ManagedDependencyLifecycleService.Request request(String command, String operation) {
        return new ManagedDependencyLifecycleService.Request(ManagedDependencyLifecycleService.VERSION, command,
            "inbox-callback-canary/v1", "customer", "tenant", "deployment", "target", "version",
            "inbox", "service-ref", "account", "binding", operation);
    }
}
