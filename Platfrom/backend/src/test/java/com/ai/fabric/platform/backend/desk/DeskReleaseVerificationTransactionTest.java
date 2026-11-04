package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseVerificationCompleted;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(DeskReleaseVerificationTransactionTest.Config.class)
class DeskReleaseVerificationTransactionTest {
    @Autowired JpaTransactionManager transactions;
    @Autowired ApplicationEventPublisher events;
    @Autowired DeskBindingRepository bindings;
    @Autowired DeploymentRepository deployments;
    @Autowired DeploymentReleaseRepository releases;
    @Autowired DeploymentVerificationRunRepository runs;
    @Autowired DeploymentTargetProfileRepository targets;
    @Autowired DeskBindingReconciliationService reconciliation;

    @BeforeEach void seed() {
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            bindings.deleteAllInBatch(); runs.deleteAllInBatch(); releases.deleteAllInBatch();
            deployments.deleteAllInBatch(); targets.deleteAllInBatch();
            var now = Instant.now();
            var deployment = new DeploymentEntity(); deployment.setId("dep-a"); deployment.setName("Acme");
            deployment.setCustomerId("customer-a"); deployment.setTenantId("tenant-a"); deployment.setEnvironmentName("production");
            deployment.setTemplateId("support"); deployment.setBehaviorType("CONVERSATIONAL"); deployment.setStatus("VERIFYING");
            deployment.setCreatedAt(now); deployment.setUpdatedAt(now); deployments.save(deployment);
            var target = new DeploymentTargetProfileEntity(); target.setId("target-a"); target.setName("Production");
            target.setEnvironmentName("production"); target.setProviderType(DeploymentProviderType.COOLIFY); target.setActive(true);
            target.setSourceStrategy("IMMUTABLE"); target.setProviderConfigJson("{}"); target.setNetworkPolicyJson("{}");
            target.setResourceDefaultsJson("{}"); target.setCreatedAt(now); target.setUpdatedAt(now); targets.save(target);
            var release = new DeploymentReleaseEntity(); release.setId("rel-a"); release.setDeploymentId("dep-a");
            release.setTargetProfileId("target-a"); release.setDeploymentVersionId("ver-a"); release.setStatus("VERIFYING");
            release.setVerificationStatus("RUNNING"); release.setProvisioningStatus("ACTIVE"); release.setProvisioningTarget("COOLIFY");
            release.setProvisioningDetailsJson("{}"); release.setCreatedAt(now); release.setAppliedAt(now); release.setUpdatedAt(now);
            releases.save(release);
            var binding = DeskBindingProvisioner.projection(deployment, "target-a"); binding.setId("desk-a");
            binding.setVersionId("ver-a"); binding.setReleaseId("rel-a"); binding.setStatus("APPLYING");
            binding.setCreatedAt(now); binding.setUpdatedAt(now); bindings.save(binding);
        });
    }

    @Test void committedVerificationEnablesDeskWithoutAnOwnerGetAndDuplicateDeliveryIsIdempotent() {
        complete("run-a", "POST_APPLY", "PASSED", false);
        assertThat(binding().getStatus()).isEqualTo("ENABLED");
        var updated = binding().getUpdatedAt();
        new TransactionTemplate(transactions).executeWithoutResult(ignored ->
            events.publishEvent(new DeploymentReleaseVerificationCompleted("rel-a", "run-a")));
        assertThat(binding().getUpdatedAt()).isEqualTo(updated);
    }

    @Test void rollbackPublishesNoDeskSuccess() {
        complete("run-a", "POST_APPLY", "PASSED", true);
        assertThat(binding().getStatus()).isEqualTo("APPLYING");
        assertThat(releases.findById("rel-a").orElseThrow().getStatus()).isEqualTo("VERIFYING");
        assertThat(runs.findById("run-a")).isEmpty();
    }

    @Test void failedVerificationThenManualRerunReconcilesBothOutcomesAndIgnoresOldEvent() {
        complete("run-a", "POST_APPLY", "FAILED", false);
        assertThat(binding().getStatus()).isEqualTo("FAILED");
        complete("run-b", "MANUAL_RERUN", "PASSED", false);
        assertThat(binding().getStatus()).isEqualTo("ENABLED");
        new TransactionTemplate(transactions).executeWithoutResult(ignored ->
            events.publishEvent(new DeploymentReleaseVerificationCompleted("rel-a", "run-a")));
        assertThat(binding().getStatus()).isEqualTo("ENABLED");
    }

    @Test void deskProjectionFailureDoesNotRewriteDurableReleaseSuccessOrGrantAnotherCustomerAccess() {
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            var binding = binding(); binding.setCustomerId("other-customer"); bindings.save(binding);
        });
        complete("run-a", "POST_APPLY", "PASSED", false);
        assertThat(binding().getStatus()).isEqualTo("APPLYING");
        assertThat(releases.findById("rel-a").orElseThrow().getStatus()).isEqualTo("APPLIED_VERIFIED");
    }

    @Test void eventOutsideDurableTransactionDoesNotPretendVerificationCommitted() {
        events.publishEvent(new DeploymentReleaseVerificationCompleted("rel-a", "uncommitted"));
        assertThat(binding().getStatus()).isEqualTo("APPLYING");
    }

    @Test void completionDoesNotChangeAnotherEnvironmentOrAReplacementBinding() {
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            var other = DeskBindingProvisioner.projection(deployments.findById("dep-a").orElseThrow(), "target-b");
            other.setId("desk-b"); other.setReleaseId("rel-b"); other.setVersionId("ver-b"); other.setStatus("APPLYING");
            bindings.save(other);
            var replacement = binding(); replacement.setReleaseId("rel-newer"); bindings.save(replacement);
        });
        complete("run-a", "POST_APPLY", "PASSED", false);
        assertThat(binding().getStatus()).isEqualTo("APPLYING");
        assertThat(bindings.findById("desk-b").orElseThrow().getStatus()).isEqualTo("APPLYING");
    }

    @Test void ownerRepairReturnsFreshCommittedStateWithoutReusingTheCallersCachedBinding() {
        complete("run-a", "POST_APPLY", "PASSED", false);
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            var binding = binding(); binding.setStatus("APPLYING"); bindings.save(binding);
        });
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            var before = binding();
            var repaired = reconciliation.reconcile("rel-a", null);
            assertThat(repaired.getStatus()).isEqualTo("ENABLED");
            assertThat(before.getStatus()).isEqualTo("APPLYING");
        });
        assertThat(binding().getStatus()).isEqualTo("ENABLED");
    }

    private DeskBindingEntity binding() { return bindings.findById("desk-a").orElseThrow(); }

    private void complete(String runId, String type, String result, boolean rollback) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var run = new DeploymentVerificationRunEntity(); run.setId(runId); run.setReleaseId("rel-a");
            run.setDeploymentId("dep-a"); run.setDeploymentVersionId("ver-a"); run.setVerificationType(type);
            run.setStatus(result); run.setChecksJson("[]"); run.setSummaryMessage("Fixture verification " + result);
            run.setCreatedAt(Instant.now()); run.setCompletedAt(Instant.now()); runs.save(run);
            var release = releases.findById("rel-a").orElseThrow(); release.setVerificationRunId(runId);
            release.setVerificationStatus(result);
            release.setStatus("PASSED".equals(result) ? "APPLIED_VERIFIED" : "APPLIED_VERIFICATION_FAILED"); releases.save(release);
            String before = binding().getStatus();
            events.publishEvent(new DeploymentReleaseVerificationCompleted("rel-a", runId));
            assertThat(binding().getStatus()).isEqualTo(before);
            if (rollback) status.setRollbackOnly();
        });
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:desk-release-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(dataSource);
            factory.setManagedTypes(PersistenceManagedTypes.of(DeskBindingEntity.class.getName(), DeploymentEntity.class.getName(),
                DeploymentReleaseEntity.class.getName(), DeploymentVerificationRunEntity.class.getName(), DeploymentTargetProfileEntity.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop")); return factory;
        }
        @Bean JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean JpaRepositoryFactory repositories(jakarta.persistence.EntityManagerFactory factory) {
            return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory));
        }
        @Bean DeskBindingRepository bindings(JpaRepositoryFactory factory) { return factory.getRepository(DeskBindingRepository.class); }
        @Bean DeploymentRepository deployments(JpaRepositoryFactory factory) { return factory.getRepository(DeploymentRepository.class); }
        @Bean DeploymentReleaseRepository releases(JpaRepositoryFactory factory) { return factory.getRepository(DeploymentReleaseRepository.class); }
        @Bean DeploymentVerificationRunRepository runs(JpaRepositoryFactory factory) { return factory.getRepository(DeploymentVerificationRunRepository.class); }
        @Bean DeploymentTargetProfileRepository targets(JpaRepositoryFactory factory) { return factory.getRepository(DeploymentTargetProfileRepository.class); }
        @Bean DeskBindingReconciliationService reconciliation(DeskBindingRepository bindings, DeploymentRepository deployments,
                DeploymentReleaseRepository releases, DeploymentTargetProfileRepository targets, DeploymentVerificationRunRepository runs) {
            return new DeskBindingReconciliationService(bindings, deployments, releases, targets, runs);
        }
        @Bean DeskReleaseVerificationListener listener(DeskBindingReconciliationService reconciliation) {
            return new DeskReleaseVerificationListener(reconciliation);
        }
    }
}
