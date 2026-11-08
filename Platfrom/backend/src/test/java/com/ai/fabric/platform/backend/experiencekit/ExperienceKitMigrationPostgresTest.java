package com.ai.fabric.platform.backend.experiencekit;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.CreateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentBootstrapContracts;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformConsumerRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ai.fabric.platform.backend.experiencekit.ExperienceKitTestFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real migrations/FKs and kit JPA transactions; canonical resource collaborators use participating JDBC test writes. */
@Testcontainers(disabledWithoutDocker = true)
class ExperienceKitMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @BeforeAll static void migrateRealSchemaThroughV225() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target("225").load().migrate();
    }

    @BeforeEach void connect() {
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test void seedsKitsWithExactCanonicalTemplateIdsAndOptionalNotAutomaticHumanSupport() throws Exception {
        var rows = jdbc.queryForList("""
            SELECT code, definition_json, template_plugin_ids_json
            FROM platform_experience_kit_definitions ORDER BY sort_order
            """);
        assertThat(rows).extracting(row -> row.get("code")).containsExactly(GENERIC, DEALERSHIP, "ai-support-experience");
        assertThat(mapper.readTree((String) rows.get(2).get("template_plugin_ids_json")))
            .isEqualTo(mapper.valueToTree(List.of("mkp-template-ai-support-workspace")));
        assertThat(mapper.readTree((String) rows.get(2).get("definition_json")).path("experiencePackCode").asText())
            .isEqualTo("support");
        assertThat(mapper.readTree((String) rows.get(0).get("template_plugin_ids_json")))
            .isEqualTo(mapper.valueToTree(List.of("mkp-template-document-knowledge-assistant")));
        assertThat(mapper.readTree((String) rows.get(1).get("template_plugin_ids_json")))
            .isEqualTo(mapper.valueToTree(List.of(TEMPLATE, "mkp-template-dealership-external-systems-v1",
                "mkp-template-dealership-external-systems-managed-inbox-v1")));
        assertThat(mapper.readTree((String) rows.get(0).get("definition_json")).path("experiencePackCode").asText())
            .isEqualTo("generic-workspace");
        assertThat(mapper.readTree((String) rows.get(1).get("definition_json")).path("experiencePackCode").asText())
            .isEqualTo("dealership");
        var generic = mapper.readTree((String) rows.get(0).get("definition_json"));
        var dealership = mapper.readTree((String) rows.get(1).get("definition_json"));
        assertThat(generic.path("summary").asText()).isEqualTo("Give customers clear answers and useful next steps.");
        assertThat(dealership.path("summary").asText()).isEqualTo("Turn your website into a helpful buying experience.");
        assertThat(generic.path("name").asText()).isEqualTo("AI Customer Experience");
        assertThat(dealership.path("name").asText()).isEqualTo("Dealership AI Experience");
        assertThat(generic.path("version").asText()).isEqualTo("1.0.0");
        assertThat(dealership.path("defaultWorkspaceName").asText()).isEqualTo("Your dealership AI");
        assertThat(dealership.path("features").get(0).asText()).isEqualTo("Find and compare cars");
        assertThat(jdbc.queryForObject("""
            SELECT column_default FROM information_schema.columns
            WHERE table_schema=current_schema() AND table_name='platform_customer_experience_kits'
              AND column_name='human_support_enabled'
            """, String.class)).isEqualTo("false");
    }

    @Test void ticketingDefaultsOffAndIsIndependentOfHumanSupportForSupportKitsOnly() {
        var p = parents(null);
        String kit = unique("kit");
        insertKit(kit, p, "ai-support-experience", "both", "support-ticket-preference");
        assertThat(jdbc.queryForMap("SELECT ticketing_enabled,ticket_provider,human_support_enabled FROM platform_customer_experience_kits WHERE id=?", kit))
            .containsEntry("ticketing_enabled", false).containsEntry("ticket_provider", null).containsEntry("human_support_enabled", false);
        assertSqlState("23514", () -> jdbc.update("UPDATE platform_customer_experience_kits SET ticketing_enabled=true WHERE id=?", kit));
        jdbc.update("UPDATE platform_customer_experience_kits SET ticketing_enabled=true,ticket_provider='desk' WHERE id=?", kit);
        assertThat(jdbc.queryForMap("SELECT ticketing_enabled,human_support_enabled FROM platform_customer_experience_kits WHERE id=?", kit))
            .containsEntry("ticketing_enabled", true).containsEntry("human_support_enabled", false);
        var generic = parents(null);
        String ordinary = unique("kit");
        insertKit(ordinary, generic, GENERIC, "standalone", "ordinary-kit");
        assertSqlState("23514", () -> jdbc.update("UPDATE platform_customer_experience_kits SET ticketing_enabled=true,ticket_provider='desk' WHERE id=?", ordinary));
    }

    @Test void enforcesPerCustomerOperationKeysResourceUniquenessAndAllDeclaredForeignKeys() {
        var original = parents(null);
        insertKit(unique("kit"), original, DEALERSHIP, "both", "same-key");
        var sameOwner = parents(original.customer());
        assertSqlState("23505", () -> insertKit(unique("kit"), sameOwner, DEALERSHIP, "both", "same-key"));
        var differentOwner = parents(null);
        insertKit(unique("kit"), differentOwner, DEALERSHIP, "both", "same-key");

        var duplicateDeployment = new Parents(sameOwner.customer(), original.deployment(), sameOwner.offer(),
            sameOwner.consumerEntity(), sameOwner.app(), sameOwner.embedded());
        assertSqlState("23505", () -> insertKit(unique("kit"), duplicateDeployment, DEALERSHIP, "both", "another-key"));
        var duplicateApp = new Parents(sameOwner.customer(), sameOwner.deployment(), sameOwner.offer(),
            sameOwner.consumerEntity(), original.app(), sameOwner.embedded());
        assertSqlState("23505", () -> insertKit(unique("kit"), duplicateApp, DEALERSHIP, "both", "another-key"));

        var p = parents(null);
        for (String missing : List.of("customer", "deployment", "offer", "app", "embedded")) {
            var broken = new Parents(missing.equals("customer") ? "missing-customer" : p.customer(),
                missing.equals("deployment") ? "missing-deployment" : p.deployment(),
                missing.equals("offer") ? "missing-offer" : p.offer(), p.consumerEntity(),
                missing.equals("app") ? "missing-app" : p.app(),
                missing.equals("embedded") ? "missing-embedded" : p.embedded());
            assertSqlState("23503", () -> insertKit(unique("kit"), broken, DEALERSHIP, "both", "fk-key"));
        }
        assertSqlState("23503", () -> insertKit(unique("kit"), p, "missing-kit-definition", "both", "fk-key"));
        assertSqlState("23514", () -> insertKit(unique("kit"), p, DEALERSHIP, "invalid", "check-key"));
    }

    @Test void deletingEitherCanonicalAppSetsOnlyItsKitReferenceToNull() {
        var p = parents(null);
        String kit = unique("kit");
        insertKit(kit, p, DEALERSHIP, "both", "delete-app-key");

        jdbc.update("DELETE FROM ai_workspace_installations WHERE id=?", p.app());
        var row = jdbc.queryForMap("SELECT * FROM platform_customer_experience_kits WHERE id=?", kit);
        assertThat(row.get("installation_entity_id")).isNull();
        assertThat(row.get("embedded_installation_entity_id")).isEqualTo(p.embedded());
        assertThat(row.get("deployment_id")).isEqualTo(p.deployment());
        assertThat(row.get("customer_id")).isEqualTo(p.customer());

        jdbc.update("DELETE FROM ai_workspace_installations WHERE id=?", p.embedded());
        var retained = jdbc.queryForMap("SELECT * FROM platform_customer_experience_kits WHERE id=?", kit);
        assertThat(retained.get("installation_entity_id")).isNull();
        assertThat(retained.get("embedded_installation_entity_id")).isNull();
        assertThat(retained.get("operation_key")).isEqualTo("delete-app-key");
    }

    @Test void deletingDeploymentRemovesOnlyItsKitMetadataAndPreservesSeparateCanonicalResources() {
        var removed = parents(null);
        String removedKit = unique("kit");
        insertKit(removedKit, removed, DEALERSHIP, "both", "delete-deployment-key");
        var retained = parents(removed.customer());
        String retainedKit = unique("kit");
        insertKit(retainedKit, retained, DEALERSHIP, "both", "retain-deployment-key");
        var primaryBefore = jdbc.queryForMap("SELECT * FROM ai_workspace_installations WHERE id=?", removed.app());
        var embeddedBefore = jdbc.queryForMap("SELECT * FROM ai_workspace_installations WHERE id=?", removed.embedded());
        var consumerBefore = jdbc.queryForMap("SELECT * FROM platform_consumers WHERE id=?", removed.consumerEntity());
        var offerBefore = jdbc.queryForMap("SELECT * FROM platform_customer_offers WHERE id=?", removed.offer());
        var retainedKitBefore = jdbc.queryForMap("SELECT * FROM platform_customer_experience_kits WHERE id=?", retainedKit);

        assertThat(jdbc.update("DELETE FROM platform_deployments WHERE id=?", removed.deployment())).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_customer_experience_kits WHERE id=?",
            Long.class, removedKit)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_deployments WHERE id=?",
            Long.class, removed.deployment())).isZero();
        assertThat(jdbc.queryForMap("SELECT * FROM ai_workspace_installations WHERE id=?", removed.app()))
            .isEqualTo(primaryBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM ai_workspace_installations WHERE id=?", removed.embedded()))
            .isEqualTo(embeddedBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM platform_consumers WHERE id=?", removed.consumerEntity()))
            .isEqualTo(consumerBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM platform_customer_offers WHERE id=?", removed.offer()))
            .isEqualTo(offerBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM platform_customer_experience_kits WHERE id=?", retainedKit))
            .isEqualTo(retainedKitBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_deployments WHERE id=?",
            Long.class, retained.deployment())).isEqualTo(1L);
    }

    @Test void secondAppFailureRollsBackTheRealOuterTransactionAfterACommittedPositiveControl() {
        insertCustomer(CUSTOMER);
        insertOffer(OFFER);
        var f = new ExperienceKitTestFixture();
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setManagedTypes(PersistenceManagedTypes.of(PlatformCustomerEntity.class.getName(),
            ExperienceKitDefinitionEntity.class.getName(), CustomerExperienceKitEntity.class.getName(),
            AIWorkspaceInstallationEntity.class.getName()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.physical_naming_strategy",
            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        try {
            var entityManager = SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
            var repositories = new JpaRepositoryFactory(entityManager);
            var kits = repositories.getRepository(CustomerExperienceKitRepository.class);
            var definitions = repositories.getRepository(ExperienceKitDefinitionRepository.class);
            var installations = repositories.getRepository(AIWorkspaceInstallationRepository.class);
            when(f.deployments.findById(anyString())).thenReturn(java.util.Optional.of(f.deployment));
            var transactions = new JpaTransactionManager(factory.getObject()); transactions.setDataSource(dataSource);
            var failSecond = new AtomicBoolean();
            var appCalls = new AtomicInteger();
            var consumerIds = new java.util.HashMap<String, String>();

            when(f.bootstrap.bootstrap(eq(OFFER), any())).thenAnswer(call -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                var request = (CustomerDeploymentBootstrapContracts.Request) call.getArgument(1);
                String deployment = unique("dep");
                String tenant = insertDeployment(CUSTOMER, deployment);
                return new CustomerDeploymentBootstrapContracts.Result(deployment, tenant, OFFER, "fingerprint",
                    request.targetProfileId(), 7, "CONFIGURATION_ONLY", request.aiStateStorageMode());
            });
            when(f.consumers.createConsumer(eq(CUSTOMER), any())).thenAnswer(call -> {
                var request = (CreatePlatformConsumerRequest) call.getArgument(1);
                String entity = unique("consumer");
                insertConsumer(CUSTOMER, entity, request.consumerId());
                consumerIds.put(request.consumerId(), entity);
                return f.consumer(request.consumerId());
            });
            doAnswer(call -> {
                if (failSecond.get() && appCalls.incrementAndGet() == 2) {
                    assertThat(count("ai_workspace_installations", CUSTOMER)).isEqualTo(3);
                    assertThat(count("platform_deployments", CUSTOMER)).isEqualTo(2);
                    assertThat(count("platform_consumers", CUSTOMER)).isEqualTo(2);
                    throw new IllegalStateException("Second canonical app failed");
                }
                var request = (CreateAIWorkspaceInstallationRequest) call.getArgument(1);
                String entity = unique("app");
                String publicId = unique("awi");
                insertApp(CUSTOMER, consumerIds.get(request.consumerId()), entity, publicId,
                    request.deliveryMode(), request.appSlug(), request.configuration());
                var summary = f.workspace(entity, publicId, "DRAFT", request);
                f.remember(summary);
                return summary;
            }).when(f.workspaces).create(eq(CUSTOMER), any(), anyString());
            var implementation = new ExperienceKitService(definitions, kits, f.access, f.catalogue, f.bootstrap,
                f.staging, f.consumers, f.workspaces, installations, f.versions, f.mapper, f.codec, entityManager, f.audit, f.deployments);
            var proxy = new ProxyFactory(implementation); proxy.setProxyTargetClass(true);
            proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
            var service = (ExperienceKitService) proxy.getProxy();

            var committed = service.create(f.request("both", true));
            assertThat(count("platform_customer_experience_kits", CUSTOMER)).isEqualTo(1);
            assertThat(count("platform_deployments", CUSTOMER)).isEqualTo(1);
            assertThat(count("platform_consumers", CUSTOMER)).isEqualTo(1);
            assertThat(count("ai_workspace_installations", CUSTOMER)).isEqualTo(2);
            assertThat(jdbc.queryForMap("SELECT installation_entity_id, embedded_installation_entity_id "
                + "FROM platform_customer_experience_kits WHERE id=?", committed.id()))
                .containsEntry("installation_entity_id", committed.workspace().id())
                .containsEntry("embedded_installation_entity_id", committed.embeddedWorkspace().id());
            insertCustomer("kit-other-customer");
            when(f.access.requireCustomerActor()).thenReturn(
                new com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService.CustomerActor(
                    "kit-other-customer", "other-owner"));
            assertThatThrownBy(() -> service.get(committed.id()))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                    error -> assertThat(error.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));
            when(f.access.requireCustomerActor()).thenReturn(
                new com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService.CustomerActor(CUSTOMER, OWNER));

            failSecond.set(true);
            var request = f.request("both", true);
            var failing = new ExperienceKitContracts.CreateRequest(request.kitCode(), request.name(), request.offerId(),
                request.targetProfileId(), request.workspaceProfileId(), request.aiStateStorageMode(), request.workspaceName(),
                request.brandColor(), request.deliveryMode(), "failing-kit-app", request.websiteOrigin(), true, "failed-kit-create", false, null, null);
            assertThatThrownBy(() -> service.create(failing)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Second canonical app failed");

            assertThat(appCalls.get()).isEqualTo(2);
            assertThat(count("platform_customer_experience_kits", CUSTOMER)).isEqualTo(1);
            assertThat(count("platform_deployments", CUSTOMER)).isEqualTo(1);
            assertThat(count("platform_customer_tenants", CUSTOMER)).isEqualTo(1);
            assertThat(count("platform_consumers", CUSTOMER)).isEqualTo(1);
            assertThat(count("ai_workspace_installations", CUSTOMER)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_customer_experience_kits "
                + "WHERE customer_id=? AND operation_key='failed-kit-create'", Long.class, CUSTOMER)).isZero();
        } finally {
            factory.destroy();
        }
    }

    private Parents parents(String existingCustomer) {
        String customer = existingCustomer == null ? unique("customer") : existingCustomer;
        if (existingCustomer == null) insertCustomer(customer);
        String deployment = unique("dep"); insertDeployment(customer, deployment);
        String offer = unique("offer"); insertOffer(offer);
        String consumer = unique("consumer"); insertConsumer(customer, consumer, unique("consumer-public"));
        String app = unique("app");
        String embedded = unique("app");
        insertApp(customer, consumer, app, unique("awi"), "standalone", null, mapper.createObjectNode());
        insertApp(customer, consumer, embedded, unique("awi"), "embedded", null, mapper.createObjectNode());
        return new Parents(customer, deployment, offer, consumer, app, embedded);
    }

    private void insertCustomer(String customer) {
        jdbc.update("INSERT INTO platform_customers(id,name,slug,status,created_at,updated_at) "
            + "VALUES (?, 'Kit customer', ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", customer, customer);
    }

    private String insertDeployment(String customer, String deployment) {
        String tenant = unique("tenant");
        jdbc.update("INSERT INTO platform_customer_tenants(id,customer_id,name,slug,status,created_at,updated_at) "
            + "VALUES (?,?,'Kit tenant',?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", tenant, customer, tenant);
        jdbc.update("""
            INSERT INTO platform_deployments(id,customer_id,tenant_id,name,environment_name,template_id,status,created_at,updated_at)
            VALUES (?,?,?,'Kit deployment','staging',?,'DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """, deployment, customer, tenant, TEMPLATE);
        return tenant;
    }

    private void insertOffer(String offer) {
        jdbc.update("INSERT INTO platform_customer_offers(id,definition_json,fingerprint,review_reference,reviewed_by,reviewed_at) "
            + "VALUES (?,'{}',?,'test-review','test-staff',CURRENT_TIMESTAMP)", offer, "a".repeat(64));
    }

    private void insertConsumer(String customer, String entity, String consumerId) {
        jdbc.update("INSERT INTO platform_consumers(id,customer_id,consumer_id,display_name,status,created_at,updated_at) "
            + "VALUES (?,?,?,'Kit consumer','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", entity, customer, consumerId);
    }

    private void insertApp(String customer, String consumer, String entity, String publicId,
                           String delivery, String slug, JsonNode configuration) {
        jdbc.update("""
            INSERT INTO ai_workspace_installations(id,installation_id,customer_id,consumer_entity_id,display_name,status,
                experience_pack_code,experience_pack_version,connection_mode,connection_profile_code,connection_profile_version,
                connection_configuration_json,allowed_origins_json,configuration_json,delivery_mode,app_slug,created_at,updated_at)
            VALUES (?,?,?,?,'Kit workspace','DRAFT','dealership','1.1.0','PUBLIC_RUNTIME_ANONYMOUS',
                'runtime-anonymous-direct','1.0.0','{}','[]',?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """, entity, publicId, customer, consumer, configuration.toString(), delivery.toUpperCase(java.util.Locale.ROOT), slug);
    }

    private void insertKit(String id, Parents parents, String code, String delivery, String operation) {
        jdbc.update("""
            INSERT INTO platform_customer_experience_kits(id,customer_id,kit_code,kit_version,name,deployment_id,consumer_id,
                installation_entity_id,embedded_installation_entity_id,delivery_mode,offer_id,target_profile_id,
                operation_key,request_fingerprint,created_at,updated_at)
            VALUES (?,?,?,'1.0.0','Kit',?,'kit-consumer',?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """, id, parents.customer(), code, parents.deployment(), parents.app(), parents.embedded(), delivery,
            parents.offer(), TARGET, operation, "a".repeat(64));
    }

    private long count(String table, String customer) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE customer_id=?", Long.class, customer);
    }

    private void assertSqlState(String state, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(DataIntegrityViolationException.class, error -> {
            assertThat(error.getMostSpecificCause()).isInstanceOf(SQLException.class);
            assertThat(((SQLException) error.getMostSpecificCause()).getSQLState()).isEqualTo(state);
        });
    }

    private String unique(String prefix) { return prefix + "-" + UUID.randomUUID(); }

    private record Parents(String customer, String deployment, String offer, String consumerEntity,
                           String app, String embedded) { }
}
