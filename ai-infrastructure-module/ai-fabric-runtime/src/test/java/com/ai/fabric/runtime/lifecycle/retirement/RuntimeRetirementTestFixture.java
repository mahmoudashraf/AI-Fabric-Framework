package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;

import ai.fabric.chat.spi.ChatSessionStorageProvider;
import ai.fabric.chat.storage.ChatSessionPendingActionStore;
import ai.fabric.chat.storage.DefaultDatabaseChatSessionStorage;
import ai.fabric.intent.action.PendingActionStore;
import com.ai.fabric.runtime.auth.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

final class RuntimeRetirementTestFixture {
    static final Target TARGET = new Target("dep-123", "staging", "release-1");
    static final String FINGERPRINT = "a".repeat(64);
    final DriverManagerDataSource dataSource;
    final JdbcTemplate jdbc;
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final RuntimeTargetRetirementIdentity identity;
    final RuntimeRetirementSchema schema;
    final RuntimeTargetDrainInventory inventory;
    final RuntimeTargetRetirementService service;
    final RuntimeRetirementTopology topology;
    final RuntimeRetirementFencingCoverage coverage = new RuntimeRetirementFencingCoverage();
    final RuntimeRetirementStoreVerification stores;

    RuntimeRetirementTestFixture() { this(true); }
    RuntimeRetirementTestFixture(boolean dedicated) { this(dedicated, false, List.of()); }
    RuntimeRetirementTestFixture(boolean dedicated, boolean certified, List<RuntimeRetirementTopology.Dependency> dependencies) {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/runtime-migration/V12__runtime_target_retirement.sql")).execute(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("db/runtime-migration/V13__runtime_generation_transition.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE chat_sessions (id VARCHAR(160) PRIMARY KEY, owner_id VARCHAR(256), status VARCHAR(32), session_metadata TEXT)");
        jdbc.execute("CREATE TABLE ai_conversation_control (tenant_id VARCHAR(128), deployment_id VARCHAR(128), conversation_id VARCHAR(160), owner_id VARCHAR(256), control_state VARCHAR(32), active_handover_id VARCHAR(160))");
        jdbc.execute("CREATE TABLE ai_conversation_handover (handover_id VARCHAR(160), status VARCHAR(40))");
        jdbc.execute("CREATE TABLE ai_review_task (status VARCHAR(40))");
        jdbc.execute("CREATE TABLE loomai_document_work (terminal BOOLEAN)");
        jdbc.execute("CREATE TABLE loomai_document_source (lifecycle_status VARCHAR(40))");
        jdbc.execute("CREATE TABLE loomai_document_manifest (lifecycle_state VARCHAR(40))");
        identity = new RuntimeTargetRetirementIdentity(true, TARGET.deploymentId(), TARGET.targetProfileId(), TARGET.releaseId(), "tenant-1", dedicated);
        schema = new RuntimeRetirementSchema(dataSource);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        DefaultDatabaseChatSessionStorage store = new DefaultDatabaseChatSessionStorage(null);
        beans.registerSingleton("sessions", new RuntimeRetirementStoreFencing.FencedSessions(store, beans.getBeanProvider(RuntimeTargetRetirementService.class)));
        beans.registerSingleton("pending", new ChatSessionPendingActionStore(store));
        var environment = new org.springframework.mock.env.MockEnvironment();
        if (certified) {
            var features = new java.util.EnumMap<RuntimeRetirementTopology.Feature, RuntimeRetirementTopology.Presence>(RuntimeRetirementTopology.Feature.class);
            for (var feature : RuntimeRetirementTopology.Feature.values()) features.put(feature, RuntimeRetirementTopology.Presence.ABSENT);
            features.put(RuntimeRetirementTopology.Feature.CHAT_SESSIONS, RuntimeRetirementTopology.Presence.PRESENT);
            try {
                String raw = mapper.writeValueAsString(new RuntimeRetirementTopology.Contract(RuntimeRetirementTopology.VERSION, TARGET, "tenant-1", features, dependencies));
                environment.withProperty("loomai.runtime.target-retirement.topology-json", raw)
                    .withProperty("loomai.runtime.target-retirement.topology-sha256", java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            } catch (Exception exception) { throw new IllegalStateException(exception); }
            for (String property : List.of("loomai.conversation-handover.enabled", "ai.execution.reviews.enabled", "ai.execution.enabled",
                "ai.execution.specialist-chains.enabled", "loomai.smart-brain.enabled", "ai.indexing.enabled", "loomai.documents.enabled")) {
                environment.withProperty(property, "false");
            }
        }
        topology = new RuntimeRetirementTopology(environment, mapper, identity);
        stores = new RuntimeRetirementStoreVerification(beans);
        inventory = new RuntimeTargetDrainInventory(jdbc, schema, identity, mapper,
            beans.getBeanProvider(ChatSessionStorageProvider.class), beans.getBeanProvider(PendingActionStore.class), topology, coverage, stores);
        service = restart();
        beans.registerSingleton("retirement", service);
    }
    RuntimeTargetRetirementService restart() {
        return new RuntimeTargetRetirementService(identity, jdbc, schema, inventory, mapper, new DataSourceTransactionManager(dataSource));
    }
    Replacement replacement(String releaseId) {
        Target nextTarget = new Target(TARGET.deploymentId(), TARGET.targetProfileId(), releaseId);
        RuntimeTargetRetirementIdentity nextIdentity = new RuntimeTargetRetirementIdentity(true,
            nextTarget.deploymentId(), nextTarget.targetProfileId(), nextTarget.releaseId(), "tenant-1", true);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        DefaultDatabaseChatSessionStorage store = new DefaultDatabaseChatSessionStorage(null);
        beans.registerSingleton("sessions", new RuntimeRetirementStoreFencing.FencedSessions(
            store, beans.getBeanProvider(RuntimeTargetRetirementService.class)));
        beans.registerSingleton("pending", new ChatSessionPendingActionStore(store));
        RuntimeRetirementTopology nextTopology = certifiedTopology(nextIdentity, nextTarget, List.of());
        RuntimeRetirementFencingCoverage nextCoverage = new RuntimeRetirementFencingCoverage();
        RuntimeRetirementStoreVerification nextStores = new RuntimeRetirementStoreVerification(beans);
        RuntimeTargetDrainInventory nextInventory = new RuntimeTargetDrainInventory(jdbc, schema, nextIdentity, mapper,
            beans.getBeanProvider(ChatSessionStorageProvider.class), beans.getBeanProvider(PendingActionStore.class),
            nextTopology, nextCoverage, nextStores);
        RuntimeTargetRetirementService nextService = new RuntimeTargetRetirementService(nextIdentity, jdbc, schema,
            nextInventory, mapper, new DataSourceTransactionManager(dataSource));
        beans.registerSingleton("retirement", nextService);
        return new Replacement(nextTarget, nextIdentity, nextInventory, nextService);
    }
    private RuntimeRetirementTopology certifiedTopology(RuntimeTargetRetirementIdentity targetIdentity,
        Target target, List<RuntimeRetirementTopology.Dependency> dependencies) {
        MockEnvironment environment = new MockEnvironment();
        var features = new java.util.EnumMap<RuntimeRetirementTopology.Feature, RuntimeRetirementTopology.Presence>(
            RuntimeRetirementTopology.Feature.class);
        for (var feature : RuntimeRetirementTopology.Feature.values()) {
            features.put(feature, RuntimeRetirementTopology.Presence.ABSENT);
        }
        features.put(RuntimeRetirementTopology.Feature.CHAT_SESSIONS, RuntimeRetirementTopology.Presence.PRESENT);
        try {
            String raw = mapper.writeValueAsString(new RuntimeRetirementTopology.Contract(
                RuntimeRetirementTopology.VERSION, target, "tenant-1", features, dependencies));
            environment.withProperty("loomai.runtime.target-retirement.topology-json", raw)
                .withProperty("loomai.runtime.target-retirement.topology-sha256", java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
        for (String property : List.of("loomai.conversation-handover.enabled", "ai.execution.reviews.enabled",
            "ai.execution.enabled", "ai.execution.specialist-chains.enabled", "loomai.smart-brain.enabled",
            "ai.indexing.enabled", "loomai.documents.enabled")) {
            environment.withProperty(property, "false");
        }
        return new RuntimeRetirementTopology(environment, mapper, targetIdentity);
    }
    record Replacement(Target target, RuntimeTargetRetirementIdentity identity,
                       RuntimeTargetDrainInventory inventory, RuntimeTargetRetirementService service) { }
    Binding binding(long epoch) { return new Binding(TARGET, epoch, "retirement-1", FINGERPRINT); }
    Command command(long epoch, long expected, CommandType type) {
        return new Command(binding(epoch), expected, type, Instant.parse("2030-01-01T00:00:00Z"),
            type == CommandType.RECORD_AUTHORITY_REVOCATION || type == CommandType.RECORD_RECLAIM_STARTED ? "platform-audit:1" : null);
    }
    void session(String id, String owner, String status, String metadata) {
        jdbc.update("INSERT INTO chat_sessions VALUES (?, ?, ?, ?)", id, owner, status, metadata);
    }
    RuntimeResolvedIdentity caller(String owner) {
        return RuntimeResolvedIdentity.builder().authContext(RuntimeAuthContext.builder()
            .authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED).callerType(RuntimeAuthCallerType.TRUSTED_BACKEND)
            .subjectType(RuntimeAuthSubjectType.END_USER).subjectId(owner).deploymentId(TARGET.deploymentId())
            .tenantId("tenant-1").grantedScopes(List.of("chat:query")).build()).build();
    }
}
