package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetProfileIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
class AdmissionPolicyPlacementPostgresTest extends AdmissionFixture {
    @Container static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override @BeforeEach void database() {
        String schema = "policy_jpa_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl() + (DATABASE.getJdbcUrl().contains("?") ? "&" : "?")
            + "currentSchema=" + schema, DATABASE.getUsername(), DATABASE.getPassword());
        mapper = new ObjectMapper().registerModule(new JavaTimeModule()); json = new AdmissionJson(mapper);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V165__durable_capacity_admission_and_staging_expiry.sql"),
            new ClassPathResource("db/migration/V178__admission_allocation_authorizations.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
    }

    @ParameterizedTest @ValueSource(strings={"deactivated-before-lock","drift-before-lock","writer-after-lock"})
    void publicationLocksBeforeClearingAndReloadingRealJpaProfileAcrossIndependentPostgresConnection(String scenario) throws Exception {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setManagedTypes(PersistenceManagedTypes.of(DeploymentTargetProfileEntity.class.getName()));
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop")); factory.afterPropertiesSet();
        var emf = factory.getObject(); var entities = emf.createEntityManager();
        try (var writers = Executors.newSingleThreadExecutor()) {
            var profile = new DeploymentTargetProfileEntity(); profile.setId(TARGET); profile.setName("Synthetic reviewed target");
            profile.setActive(true); profile.setProviderType(DeploymentProviderType.COOLIFY); profile.setEnvironmentName("customer-staging");
            profile.setSourceStrategy("IMAGE_SOURCE"); profile.setProviderConfigJson(mapper.writeValueAsString(Map.of("serverUuid", STAGING.serverUuid(),
                "destinationUuid", STAGING.destinationUuid()))); profile.setNetworkPolicyJson("{}"); profile.setResourceDefaultsJson("{}");
            profile.setCreatedAt(NOW); profile.setUpdatedAt(NOW);
            entities.getTransaction().begin(); entities.persist(profile); entities.getTransaction().commit();
            TransactionSynchronizationManager.bindResource(emf, new EntityManagerHolder(entities));
            var transactions = new JpaTransactionManager(emf); transactions.setDataSource(dataSource);
            store = new AdmissionStore(jdbc, json, transactions, Clock.fixed(NOW, ZoneOffset.UTC));
            var template = policy(3);
            var initial = new FleetPolicy(template.id(), template.fleet(), Map.of(TARGET, new TargetBudget(TARGET, Intent.CUSTOMER_STAGING, STAGING,
                DeploymentTargetProfileIdentity.fingerprint(mapper, profile), template.fleet())), template.lifecycle(), template.restoreEvidenceReference(),
                template.privacyEvidenceReference(), template.supportReference());
            store.approvePolicy(initial, "staff", NOW); store.approveGrant(grant(3), "staff", NOW);
            store.approveManifest(manifest("manifest1", "deployment1"), "staff", NOW);
            var reservation = reserve("manifest1", "apply");
            var next = AdmissionPolicyContinuityTest.continuation(store, json, "policy-next", List.of(reservation), NOW, NOW.plusSeconds(300));
            var targets = mock(DeploymentTargetProfileRepository.class);
            var placements = mock(CustomerTargetPlacementService.class);
            var writer = new AtomicReference<Future<Integer>>();
            when(targets.findById(TARGET)).thenAnswer(invocation -> {
                var current = entities.find(DeploymentTargetProfileEntity.class, TARGET);
                if (scenario.equals("writer-after-lock")) {
                    var started = new CountDownLatch(1);
                    writer.set(writers.submit(() -> { started.countDown(); return jdbc.update("UPDATE deployment_target_profiles SET active=false WHERE id=?", TARGET); }));
                    assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> writer.get().get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                }
                return Optional.of(current);
            });
            Instant observed = Instant.now().minusSeconds(1);
            when(placements.requireFreshEvidence(any(), any())).thenReturn(new CustomerPlacementEvidenceEntity("current-placement", TARGET,
                TargetIntent.CUSTOMER_STAGING, initial.targets().get(TARGET).profileFingerprint(), STAGING.serverUuid(), STAGING.ip(),
                STAGING.destinationUuid(), observed, observed.plusSeconds(120)));
            var verifier = new AdmissionPolicyPlacementVerifier(targets, placements, mapper, json);
            ReflectionTestUtils.setField(verifier, "entities", entities);
            if (scenario.equals("deactivated-before-lock")) jdbc.update("UPDATE deployment_target_profiles SET active=false WHERE id=?", TARGET);
            if (scenario.equals("drift-before-lock")) jdbc.update("UPDATE deployment_target_profiles SET network_policy_json='{\"changed\":true}' WHERE id=?", TARGET);
            assertThat(entities.find(DeploymentTargetProfileEntity.class, TARGET).isActive()).isTrue();
            assertThat(entities.find(DeploymentTargetProfileEntity.class, TARGET).getNetworkPolicyJson()).isEqualTo("{}");
            if (scenario.equals("writer-after-lock")) {
                store.approvePolicy(next, "staff", NOW, () -> verifier.requireCurrent(next));
                assertThat(writer.get().get(3, TimeUnit.SECONDS)).isEqualTo(1);
                assertThat(store.activePolicy()).isEqualTo(next);
            } else {
                assertThatThrownBy(() -> store.approvePolicy(next, "staff", NOW, () -> verifier.requireCurrent(next)))
                    .hasMessageContaining("POLICY_CONTINUITY_PLACEMENT_REQUIRED");
                assertThat(store.activePolicy()).isEqualTo(initial);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='POLICY_CONT'", Long.class)).isZero();
            }
            assertThat(store.require(reservation.id())).isEqualTo(reservation);
            verify(placements, never()).probeAndRecord(any(), any());
        } finally {
            if (TransactionSynchronizationManager.hasResource(emf)) TransactionSynchronizationManager.unbindResource(emf);
            entities.close(); factory.destroy();
        }
    }
}
