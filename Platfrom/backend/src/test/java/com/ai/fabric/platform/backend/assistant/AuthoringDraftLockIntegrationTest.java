package com.ai.fabric.platform.backend.assistant;

import static org.assertj.core.api.Assertions.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(showSql = false, properties = {"spring.flyway.target=120.36",
    "spring.datasource.url=jdbc:h2:mem:authoring-lock;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000"})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AuthoringDraftLock.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthoringDraftLockIntegrationTest {
    @Autowired EntityManager entities;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthoringDraftLock lock;
    final ObjectMapper mapper = new ObjectMapper();
    String brainId, draftId;
    TransactionTemplate tx;
    @BeforeEach void fixture() {
        String suffix = UUID.randomUUID().toString(); brainId = "dep-" + suffix; draftId = "draft-" + suffix;
        String customer = "customer-" + suffix, tenant = "tenant-" + suffix;
        jdbc.update("INSERT INTO platform_customers (id,name,slug,status,platform_managed,created_at,updated_at) VALUES (?,?,?,'ACTIVE',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", customer, customer, customer);
        jdbc.update("INSERT INTO platform_customer_tenants (id,customer_id,name,slug,status,platform_managed,created_at,updated_at) VALUES (?,?,?,?,'ACTIVE',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", tenant, customer, tenant, tenant);
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            var brain = new DeploymentEntity(); brain.setId(brainId); brain.setName("Concurrent draft");
            brain.setCustomerId(customer); brain.setTenantId(tenant); brain.setEnvironmentName("staging");
            brain.setTemplateId("fixture"); brain.setBehaviorType("CONVERSATIONAL"); brain.setStatus("DRAFT");
            brain.setCreatedAt(Instant.now()); brain.setUpdatedAt(Instant.now()); entities.persist(brain);
            var draft = new DeploymentDraftEntity(); draft.setId(draftId); draft.setDeploymentId(brainId);
            draft.setRevisionNumber(1); draft.setStatus("DRAFT"); draft.setActionsConfigJson("{}"); draft.setEntityConfigJson("{}");
            draft.setRoutingConfigJson("{}"); draft.setProviderConfigJson("{}"); draft.setSecurityConfigJson("{}");
            draft.setPromptConfigJson("{\"systemPrompt\":\"Initial\",\"unrelated\":\"initial\"}");
            draft.setCreatedAt(Instant.now()); draft.setUpdatedAt(Instant.now()); entities.persist(draft);
            brain.setActiveDraftId(draftId);
        });
    }
    @Test void waitingAssistantRefreshesTheLockedDraftAndPreservesCommittedManualEdits() throws Exception {
        var staleRead = new CountDownLatch(1); var manualLocked = new CountDownLatch(1);
        var assistantWaiting = new CountDownLatch(1); var releaseManual = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var assistant = pool.submit(() -> tx.executeWithoutResult(status -> {
                var stale = entities.find(DeploymentDraftEntity.class, draftId);
                assertThat(stale.getPromptConfigJson()).contains("initial"); staleRead.countDown(); await(manualLocked);
                assistantWaiting.countDown(); lock.lock("DEPLOYMENT_DRAFT", brainId, draftId);
                var fresh = entities.find(DeploymentDraftEntity.class, draftId);
                try {
                    var prompt = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(fresh.getPromptConfigJson());
                    assertThat(prompt.path("unrelated").asText()).isEqualTo("manual committed");
                    prompt.put("systemPrompt", "AI requested change"); fresh.setPromptConfigJson(prompt.toString());
                } catch (java.io.IOException invalid) { throw new IllegalStateException(invalid); }
            }));
            await(staleRead);
            var manual = pool.submit(() -> tx.executeWithoutResult(status -> {
                entities.find(DeploymentEntity.class, brainId, LockModeType.PESSIMISTIC_WRITE);
                var draft = entities.find(DeploymentDraftEntity.class, draftId, LockModeType.PESSIMISTIC_WRITE);
                draft.setPromptConfigJson("{\"systemPrompt\":\"Initial\",\"unrelated\":\"manual committed\"}");
                manualLocked.countDown(); await(releaseManual);
            }));
            try {
                await(assistantWaiting);
                assertThatThrownBy(() -> assistant.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally { releaseManual.countDown(); }
            manual.get(10, TimeUnit.SECONDS); assistant.get(10, TimeUnit.SECONDS);
        }
        tx.executeWithoutResult(status -> assertThat(entities.find(DeploymentDraftEntity.class, draftId).getPromptConfigJson())
            .contains("AI requested change", "manual committed"));
    }
    @Test void refreshedDeploymentRejectsAnOldDraftAfterConcurrentPublication() throws Exception {
        try (var pool = Executors.newSingleThreadExecutor()) {
            tx.executeWithoutResult(status -> {
                entities.find(DeploymentEntity.class, brainId);
                try {
                    pool.submit(() -> tx.executeWithoutResult(other -> entities.find(DeploymentEntity.class, brainId)
                        .setActiveDraftId("replacement-draft"))).get(10, TimeUnit.SECONDS);
                } catch (Exception failed) { throw new IllegalStateException(failed); }
                assertThatThrownBy(() -> lock.lock("DEPLOYMENT_DRAFT", brainId, draftId)).hasMessageContaining("editable draft changed");
            });
        }
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrency fixture timed out"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
    }
}
