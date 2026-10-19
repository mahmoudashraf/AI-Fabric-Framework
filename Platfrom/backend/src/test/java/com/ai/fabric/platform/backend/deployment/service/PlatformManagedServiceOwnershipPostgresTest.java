package com.ai.fabric.platform.backend.deployment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Testcontainers
class PlatformManagedServiceOwnershipPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    PlatformManagedServiceOwnershipFixture f;

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP SCHEMA public CASCADE"); jdbc.execute("CREATE SCHEMA public");
        f = new PlatformManagedServiceOwnershipFixture(source);
    }

    @Test void realProvisioningProxySuspendsOuterTransactionAndCommitsTargetAndIntentBeforeLostPost() {
        when(f.api.createPublicApplication(eq(f.connection),any())).thenAnswer(call -> {
            var request = call.getArgument(1,CoolifyCreatePublicApplicationRequest.class);
            f.create(request.name(),request.domains(),null);
            throw new IllegalStateException("synthetic lost POST response");
        });
        new TransactionTemplate(f.transactions).executeWithoutResult(tx -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThatThrownBy(() -> f.provisioning.reconcile("shared","target")).hasMessageContaining("lost POST response");
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            tx.setRollbackOnly();
        });
        assertThat(f.journalState()).isEqualTo("CREATE_PENDING");
        assertThat(f.row().getDetailsJson()).contains("targetProfileId").contains("target").doesNotContain("coolifyApplicationUuid");
        assertThatThrownBy(() -> f.provisioning.reconcile("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(f.effects).isEqualTo(1);
    }

    @Test void uncommittedCallerRetargetCannotOverrideCommittedRegistryScope() {
        f.details("{\"providerType\":\"COOLIFY\",\"targetProfileId\":\"target\"}");
        new TransactionTemplate(f.transactions).executeWithoutResult(tx -> {
            f.jdbc.update("UPDATE platform_managed_product_services SET details_json='{\"targetProfileId\":\"caller-only\"}' WHERE id='registry'");
            assertThatThrownBy(() -> f.provisioning.reconcile("shared","caller-only")).hasMessageContaining("RECONCILIATION_REQUIRED");
            tx.setRollbackOnly();
        });
        assertThat(f.row().getDetailsJson()).contains("target").doesNotContain("caller-only");
        assertThat(f.jdbc.queryForList("SELECT * FROM platform_managed_service_ownership")).isEmpty();
        verifyNoInteractions(f.api);
    }

    @Test void realProvisioningLifecycleUsesCommittedReceiptsOutsideAllDatabaseTransactions() {
        new TransactionTemplate(f.transactions).executeWithoutResult(tx -> {
            f.provisioning.reconcile("shared","target");
            f.provisioning.restart("shared");
            f.provisioning.decommission("shared");
            tx.setRollbackOnly();
        });
        assertThat(f.row().getStatus()).isEqualTo("DECOMMISSIONED");
        assertThat(f.journalState()).isEqualTo("DELETED");
        assertThat(f.applications).isEmpty();
    }

    @Test void uncertainMutationJournalSurvivesCallerRollbackAndPreventsReplay() {
        f.provisioning.reconcile("shared");
        when(f.api.restart(eq(f.connection),anyString())).thenThrow(new IllegalStateException("lost restart response"));
        new TransactionTemplate(f.transactions).executeWithoutResult(tx -> {
            assertThatThrownBy(() -> f.provisioning.restart("shared")).hasMessageContaining("lost restart response");
            tx.setRollbackOnly();
        });
        assertThat(f.journalState()).isEqualTo("MUTATION_PENDING");
        assertThatThrownBy(() -> f.provisioning.restart("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        verify(f.api,times(1)).restart(any(),anyString());
    }

    @Test void callerLocalForeignCredentialCannotPassCommittedAuthorityAndReachTheVault() {
        f.jdbc.update("UPDATE platform_managed_product_services SET secret_name=NULL WHERE id='registry'");
        when(f.secrets.isSecretPresent("MANAGED_PRODUCT_SHARED_API_KEY")).thenReturn(false,true);
        f.provisioning.reconcile("shared");
        when(f.secrets.isManagedSecretName(anyString())).thenReturn(true);
        clearInvocations(f.secrets,f.api);
        var audit = mock(com.ai.fabric.platform.backend.audit.service.PlatformAuditService.class);
        var admin = new com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductAdminService(
            f.services,f.repository,f.stores,null,null,null,null,null,f.secrets,f.provisioning,audit,
            f.railway,f.resolver,f.api,f.targets,null,null,null,f.mapper);

        new TransactionTemplate(f.transactions).executeWithoutResult(tx -> {
            f.jdbc.update("UPDATE platform_managed_product_services SET secret_name='MANAGED_FOREIGN_API_KEY' WHERE id='registry'");
            assertThat(f.row().getSecretName()).isEqualTo("MANAGED_FOREIGN_API_KEY");
            assertThatThrownBy(() -> admin.rotateSecret("shared","example-replacement"))
                .hasMessageContaining("RECONCILIATION_REQUIRED");
            tx.setRollbackOnly();
        });

        verifyNoInteractions(f.secrets,audit,f.api);
        assertThat(f.row().getSecretName()).isEqualTo("MANAGED_PRODUCT_SHARED_API_KEY");
        assertThat(f.jdbc.queryForObject("SELECT credential_secret_name FROM platform_managed_service_ownership",String.class))
            .isEqualTo("MANAGED_PRODUCT_SHARED_API_KEY");
    }
}
