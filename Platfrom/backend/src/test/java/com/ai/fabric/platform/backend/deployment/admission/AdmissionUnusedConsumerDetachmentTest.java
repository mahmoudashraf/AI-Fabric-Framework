package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.*;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.*;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerReviewedBindingBoundary;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateService;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.*;
import com.ai.fabric.platform.backend.tenant.repository.*;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Synthetic source tables plus real JPA owning mutations and the actual admission ledger migrations. */
class AdmissionUnusedConsumerDetachmentTest {
    AdmissionUnusedReservationCancellationTest fixture;
    EntityManagerFactory emf;
    EntityManager entities;
    JpaTransactionManager transactions;
    PlatformConsumerRepository consumers;
    PlatformConsumerBindingHistoryRepository histories;
    AIWorkspaceInstallationRepository workspaces;
    PlatformCustomerConsumerService owner;
    AdmissionUnusedConsumerDetachment adapter;
    AIWorkspaceDeploymentSecurityProjectionService projection;
    PlatformAuditService audit;
    CancelUnusedReservation command;

    AdmissionUnusedReservationCancellationTest fixture() {
        var result = new AdmissionUnusedReservationCancellationTest();
        result.database(); result.candidate(); return result;
    }

    @BeforeEach void configuredNeverActivatedConsumer() {
        fixture = fixture();
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(fixture.dataSource);
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setManagedTypes(PersistenceManagedTypes.of(PlatformConsumerEntity.class.getName(),
            PlatformConsumerBindingHistoryEntity.class.getName(), AIWorkspaceInstallationEntity.class.getName()));
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "update", "hibernate.physical_naming_strategy",
            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet(); emf = factory.getObject();
        entities = SharedEntityManagerCreator.createSharedEntityManager(emf);
        transactions = new JpaTransactionManager(emf);
        var repositories = new JpaRepositoryFactory(entities);
        consumers = repositories.getRepository(PlatformConsumerRepository.class);
        histories = repositories.getRepository(PlatformConsumerBindingHistoryRepository.class);
        workspaces = repositories.getRepository(AIWorkspaceInstallationRepository.class);
        var customers = mock(PlatformCustomerRepository.class);
        var customer = new PlatformCustomerEntity(); customer.setId("customer"); customer.setStatus("ACTIVE");
        when(customers.findById("customer")).thenReturn(Optional.of(customer));
        projection = mock(AIWorkspaceDeploymentSecurityProjectionService.class);
        audit = mock(PlatformAuditService.class);
        owner = new PlatformCustomerConsumerService(consumers, histories, customers, mock(DeploymentRepository.class),
            mock(DeploymentReleaseRepository.class), mock(PlatformCustomerAccessService.class), audit,
            new AIWorkspaceBindingGuard(workspaces, mock(AIWorkspaceReadinessService.class)), workspaces, projection,
            fixture.mapper, mock(TargetRetirementGateService.class), mock(DeploymentTargetProfileRepository.class),
            mock(CustomerStagingAssignmentAccessGate.class), mock(CustomerReviewedBindingBoundary.class));
        ReflectionTestUtils.setField(owner, "unusedCancellationEntityManager", entities);
        adapter = new AdmissionUnusedConsumerDetachment(fixture.jdbc, fixture.json, owner);
        fixture.store = store(fixture.jdbc);
        for (String ddl : List.of(
            "CREATE TABLE platform_customer_offer_entitlements(id VARCHAR(64),customer_id VARCHAR(64),offer_id VARCHAR(64))",
            "CREATE TABLE platform_customer_offer_deployments(deployment_id VARCHAR(64),customer_id VARCHAR(64),tenant_id VARCHAR(64),offer_id VARCHAR(64))",
            "CREATE TABLE platform_customer_entitlement_commands(id VARCHAR(64),entitlement_id VARCHAR(64),operation_key VARCHAR(128),command_type VARCHAR(64),result_json TEXT)",
            "CREATE TABLE platform_deployment_drafts(id VARCHAR(64),deployment_id VARCHAR(64),status VARCHAR(32),config_json TEXT,row_version BIGINT)")) fixture.jdbc.execute(ddl);
        fixture.jdbc.update("INSERT INTO platform_customer_offer_entitlements VALUES ('entitlement','customer','offer')");
        fixture.jdbc.update("INSERT INTO platform_customer_offer_deployments VALUES ('deployment','customer','tenant','offer')");
        fixture.jdbc.update("INSERT INTO platform_deployment_drafts VALUES ('draft','deployment','PUBLISHED','{\"preserve\":true}',7)");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var consumer = new PlatformConsumerEntity();
            consumer.setId("consumer"); consumer.setConsumerId("synthetic-config"); consumer.setCustomerId("customer");
            consumer.setDisplayName("Synthetic configuration"); consumer.setStatus("ACTIVE");
            consumer.setBoundDeploymentId("deployment"); consumer.setBoundTargetProfileId(TARGET);
            consumer.setBindingRevision(1); consumer.setCreatedAt(NOW); consumer.setUpdatedAt(NOW);
            consumers.saveAndFlush(consumer);
            var history = new PlatformConsumerBindingHistoryEntity();
            history.setId("initial"); history.setConsumerEntityId("consumer"); history.setConsumerId("synthetic-config");
            history.setCustomerId("customer"); history.setToDeploymentId("deployment"); history.setToTargetProfileId(TARGET);
            history.setFromBindingRevision(0L); history.setToBindingRevision(1L); history.setOperationKey("create-config");
            history.setActorId("synthetic-owner"); history.setActorRole("CUSTOMER_ADMIN"); history.setCreatedAt(NOW);
            histories.saveAndFlush(history);
            workspaces.saveAndFlush(draftApp("draft-app"));
        });
        fixture.jdbc.update("INSERT INTO platform_customer_entitlement_commands VALUES ('creation','entitlement','create-config','CREATE_STAGING_CONSUMER',?)",
            "{\"consumerId\":\"synthetic-config\",\"customerId\":\"customer\",\"boundDeploymentId\":\"deployment\",\"boundTargetProfileId\":\""
                + TARGET + "\",\"boundReleaseId\":null,\"bindingRevision\":1}");
        fixture.externalBinding();
        command = withDetachments(List.of(new UnusedConsumerDetachment("consumer", 1, List.of(new DraftWorkspaceExpectation("draft-app", 0)))));
        authenticate(PlatformRole.PLATFORM_ADMIN);
    }

    @AfterEach void close() {
        SecurityContextHolder.clearContext();
        if (emf != null) emf.close();
    }

    @Test void cancellationDetachesThroughOwnerHistoryAndPreservesDraftsAtomicallyWithStableReplay() {
        var originalApp = fixture.jdbc.queryForMap("SELECT * FROM ai_workspace_installations WHERE id='draft-app'");
        var originalDraft = fixture.jdbc.queryForMap("SELECT * FROM platform_deployment_drafts");
        var originalVersion = fixture.jdbc.queryForMap("SELECT * FROM platform_deployment_versions");
        var originalMetadata = fixture.jdbc.queryForObject("SELECT metadata_json FROM deployment_provider_resource_handles", String.class);
        var receipt = cancel();
        assertThat(receipt.reservation().state()).isEqualTo(State.RELEASED);
        assertThat(receipt.consumerDetachments()).singleElement().satisfies(detached -> {
            assertThat(detached.consumerEntityId()).isEqualTo("consumer");
            assertThat(detached.fromBindingRevision()).isEqualTo(1);
            assertThat(detached.toBindingRevision()).isEqualTo(2);
            assertThat(detached.preservedDraftWorkspaces()).containsExactly(new DraftWorkspaceExpectation("draft-app", 0));
            assertThat(fixture.jdbc.queryForObject("SELECT operation_key FROM platform_consumer_binding_history WHERE id=?", String.class,
                detached.historyId())).isEqualTo(detached.operationKey());
        });
        var assignment = fixture.jdbc.queryForMap("SELECT bound_deployment_id,bound_release_id,bound_target_profile_id,binding_revision,status FROM platform_consumers");
        assertThat(assignment.get("bound_deployment_id")).isNull();
        assertThat(assignment.get("bound_release_id")).isNull();
        assertThat(assignment.get("bound_target_profile_id")).isNull();
        assertThat(((Number) assignment.get("binding_revision")).longValue()).isEqualTo(2);
        assertThat(assignment.get("status")).isEqualTo("ACTIVE");
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM platform_consumer_binding_history", Long.class)).isEqualTo(2);
        assertThat(fixture.jdbc.queryForMap("SELECT * FROM ai_workspace_installations WHERE id='draft-app'")).isEqualTo(originalApp);
        assertThat(fixture.jdbc.queryForMap("SELECT * FROM platform_deployment_drafts")).isEqualTo(originalDraft);
        assertThat(fixture.jdbc.queryForMap("SELECT * FROM platform_deployment_versions")).isEqualTo(originalVersion);
        assertThat(fixture.jdbc.queryForObject("SELECT status FROM deployment_provider_resource_handles", String.class)).isEqualTo("RETAINED_UNUSED");
        assertThat(fixture.jdbc.queryForObject("SELECT metadata_json FROM deployment_provider_resource_handles", String.class)).isEqualTo(originalMetadata);
        assertThat(cancel()).isEqualTo(receipt);
        verifyNoInteractions(projection);
        verify(audit, times(1)).record(anyString(), anyString(), anyString(), anyMap());
        assertThat(fixture.json.write(receipt)).doesNotContain("synthetic-secret-ref", "old-prefix", "\"preserve\":");
        assertThat(fixture.store.held()).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"missing-list", "wrong-consumer", "stale-consumer", "missing-app", "extra-app", "stale-app"})
    void explicitExpectationsMustMatchTheEntireLockedSet(String mismatch) {
        command = switch (mismatch) {
            case "missing-list" -> withDetachments(List.of());
            case "wrong-consumer" -> withDetachments(List.of(new UnusedConsumerDetachment("other", 1, List.of(new DraftWorkspaceExpectation("draft-app", 0)))));
            case "stale-consumer" -> withDetachments(List.of(new UnusedConsumerDetachment("consumer", 0, List.of(new DraftWorkspaceExpectation("draft-app", 0)))));
            case "missing-app" -> withDetachments(List.of(new UnusedConsumerDetachment("consumer", 1, List.of())));
            case "extra-app" -> withDetachments(List.of(new UnusedConsumerDetachment("consumer", 1, List.of(new DraftWorkspaceExpectation("draft-app", 0), new DraftWorkspaceExpectation("unknown", 0)))));
            case "stale-app" -> withDetachments(List.of(new UnusedConsumerDetachment("consumer", 1, List.of(new DraftWorkspaceExpectation("draft-app", 99)))));
            default -> throw new AssertionError(mismatch);
        };
        assertThatThrownBy(this::cancel).isInstanceOf(AdmissionFailure.class);
        assertUnchanged();
    }

    @ParameterizedTest @ValueSource(strings = {"released-consumer", "old-release-history", "extra-history", "active-app", "formerly-active-app", "disabled-app", "unknown-app", "foreign-app", "legacy-creation", "wrong-tenant-creation", "wrong-creation-result", "null-target"})
    void historyRuntimeOrUnprovenCreatorNeverQualifies(String history) {
        switch (history) {
            case "released-consumer" -> fixture.jdbc.update("UPDATE platform_consumers SET bound_release_id='old-release'");
            case "old-release-history" -> fixture.jdbc.update("UPDATE platform_consumer_binding_history SET from_release_id='old-release'");
            case "extra-history" -> fixture.jdbc.update("UPDATE platform_consumer_binding_history SET from_binding_revision=2");
            case "active-app" -> fixture.jdbc.update("UPDATE ai_workspace_installations SET status='ACTIVE'");
            case "formerly-active-app" -> fixture.jdbc.update("UPDATE ai_workspace_installations SET activated_at=CURRENT_TIMESTAMP");
            case "disabled-app" -> fixture.jdbc.update("UPDATE ai_workspace_installations SET disabled_at=CURRENT_TIMESTAMP");
            case "unknown-app" -> fixture.jdbc.update("UPDATE ai_workspace_installations SET status='DISABLED'");
            case "foreign-app" -> fixture.jdbc.update("UPDATE ai_workspace_installations SET customer_id='foreign'");
            case "legacy-creation" -> fixture.jdbc.update("DELETE FROM platform_customer_entitlement_commands");
            case "wrong-tenant-creation" -> fixture.jdbc.update("UPDATE platform_customer_offer_deployments SET tenant_id='other'");
            case "wrong-creation-result" -> fixture.jdbc.update("UPDATE platform_customer_entitlement_commands SET result_json='{}'");
            case "null-target" -> fixture.jdbc.update("UPDATE platform_consumers SET bound_target_profile_id=NULL");
            default -> throw new AssertionError(history);
        }
        assertThatThrownBy(this::cancel).isInstanceOf(AdmissionFailure.class);
        assertUnchanged();
    }

    @ParameterizedTest @ValueSource(strings = {"audit", "recheck", "receipt"})
    void anyFailureRollsBackOwningMutationHistoryHandleAndCapacity(String phase) {
        if (phase.equals("audit")) doThrow(new IllegalStateException("synthetic-audit-failure"))
            .when(audit).record(anyString(), anyString(), anyString(), anyMap());
        if (phase.equals("recheck")) doAnswer(invocation -> {
            fixture.jdbc.update("INSERT INTO provider_usage_allocations VALUES ('unexpected','deployment',?)", TARGET); return null;
        }).when(audit).record(anyString(), anyString(), anyString(), anyMap());
        if (phase.equals("receipt")) fixture.store = store(new JdbcTemplate(fixture.dataSource) {
            @Override public int update(String sql, Object... args) {
                if (sql.contains("INSERT INTO deployment_admission_unused_cancellations")) throw new IllegalStateException("synthetic-receipt-failure");
                return super.update(sql, args);
            }
        });
        assertThatThrownBy(this::cancel).isInstanceOf(RuntimeException.class);
        assertUnchanged();
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM provider_usage_allocations", Long.class)).isZero();
    }

    @Test void owningEntryRequiresStaffAndExistingCancellationTransaction() {
        assertThatThrownBy(() -> owner.detachNeverActivatedForUnusedCancellation("customer", "consumer", "deployment", TARGET,
            1, "cancel", Map.of("draft-app", 0L))).hasMessageContaining("AUTHORITY_REQUIRED");
        authenticate(PlatformRole.CUSTOMER_ADMIN);
        assertThatThrownBy(this::cancel).hasMessageContaining("AUTHORITY_REQUIRED");
        assertUnchanged();
    }

    @Test void changingDetachmentExpectationsAfterSuccessIsNotAnIdempotentReplay() {
        var receipt = cancel(); command = withDetachments(List.of());
        assertThatThrownBy(this::cancel).hasMessageContaining("CANCELLATION_OPERATION_REUSED");
        assertThat(receipt.consumerDetachments()).hasSize(1);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM platform_consumer_binding_history", Long.class)).isEqualTo(2);
    }

    @Test void omittedOrNullRevisionFieldsCannotDefaultToAnAuthorizedZeroVersion() {
        for (String value : List.of("{\"installationEntityId\":\"draft-app\"}",
            "{\"installationEntityId\":\"draft-app\",\"expectedRowVersion\":null}"))
            assertThatThrownBy(() -> fixture.mapper.readValue(value, DraftWorkspaceExpectation.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
        for (String value : List.of("{\"consumerEntityId\":\"consumer\",\"draftWorkspaces\":[]}",
            "{\"consumerEntityId\":\"consumer\",\"expectedBindingRevision\":null,\"draftWorkspaces\":[]}"))
            assertThatThrownBy(() -> fixture.mapper.readValue(value, UnusedConsumerDetachment.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
        assertUnchanged();
    }

    AdmissionStore store(JdbcTemplate jdbc) {
        return new AdmissionStore(jdbc, fixture.json, transactions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    UnusedReservationCancellationReceipt cancel() { return fixture.store.cancelUnused("deployment", TARGET, command, "staff", adapter); }

    void assertUnchanged() {
        fixture.assertHeld();
        assertThat(fixture.jdbc.queryForObject("SELECT bound_deployment_id FROM platform_consumers WHERE id='consumer'", String.class)).isEqualTo("deployment");
        assertThat(fixture.jdbc.queryForObject("SELECT binding_revision FROM platform_consumers WHERE id='consumer'", Long.class)).isEqualTo(1);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM platform_consumer_binding_history", Long.class)).isEqualTo(1);
        assertThat(fixture.jdbc.queryForObject("SELECT status FROM deployment_provider_resource_handles", String.class)).isEqualTo("ACTIVE");
        assertThat(fixture.jdbc.queryForObject("SELECT row_version FROM ai_workspace_installations", Long.class)).isZero();
        verifyNoInteractions(projection);
    }

    CancelUnusedReservation withDetachments(List<UnusedConsumerDetachment> expected) {
        var c = fixture.command;
        return new CancelUnusedReservation(c.fence(), c.operationKey(), c.authorizationOperationKey(), c.authorizationRequestFingerprint(),
            c.manifestFingerprint(), c.versionId(), c.versionConfigHash(), c.bindingFingerprint(), expected);
    }

    static void authenticate(PlatformRole role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal("staff", role, "Synthetic staff", "TEST"), "not-a-secret", List.of()));
    }

    static AIWorkspaceInstallationEntity draftApp(String id) {
        var app = new AIWorkspaceInstallationEntity();
        app.setId(id); app.setInstallationId("synthetic-" + id); app.setCustomerId("customer"); app.setConsumerEntityId("consumer");
        app.setDisplayName("Synthetic draft"); app.setStatus(AIWorkspaceInstallationStatus.DRAFT);
        app.setExperiencePackCode("synthetic"); app.setExperiencePackVersion("1");
        app.setConnectionMode(AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS);
        app.setConnectionProfileCode("synthetic"); app.setConnectionProfileVersion("1");
        app.setDeliveryMode(AIWorkspaceDeliveryMode.EMBEDDED); app.setInteractionPolicy(AIWorkspaceInteractionPolicy.PUBLIC);
        app.setAuthProfileCode("synthetic"); app.setAuthProfileVersion("1"); app.setAuthConfigurationJson("{}");
        app.setConnectionConfigurationJson("{}"); app.setAllowedOriginsJson("[]"); app.setConfigurationJson("{\"preserve\":true}");
        app.setCreatedAt(NOW); app.setUpdatedAt(NOW); return app;
    }
}
