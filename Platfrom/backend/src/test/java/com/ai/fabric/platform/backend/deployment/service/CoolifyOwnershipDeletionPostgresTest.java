package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.service.ManagedResourceOwnershipFixture.SCOPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class CoolifyOwnershipDeletionPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withImagePullPolicy(image -> false);

    private final ObjectMapper mapper = new ObjectMapper();
    private final CoolifyConnection connection = new CoolifyConnection("https://coolify.example", "synthetic-token",
        new CoolifyTargetProfileConfig("https://coolify.example", "project", "staging", "environment", "server", "destination",
            null, null, 1, 1, false, true, "8097", "/health", "8097"));
    private JdbcTemplate jdbc;
    private ManagedResourceOwnershipService journal;
    private ManagedResourceOwnershipService.Receipt receipt;
    private TransactionTemplate caller;
    private CoolifyDeploymentProvider provider;
    private DeploymentProviderResourceHandleEntity handle;
    private FakeCoolify client;

    @BeforeEach
    void initialize() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V197__managed_resource_creation_ownership.sql")).execute(source);
        jdbc.execute("CREATE TABLE platform_deployment_managed_vector_resources(vendor TEXT,resource_reference TEXT,endpoint TEXT,deployment_id TEXT,deployment_release_id TEXT)");
        jdbc.execute("CREATE TABLE platform_deployment_releases(id TEXT, deployment_id TEXT,target_profile_id TEXT)");
        jdbc.execute("""
            CREATE TABLE deployment_provider_resource_handles(
              id TEXT PRIMARY KEY,provider_type TEXT,provider_resource_uuid TEXT,status TEXT,deployment_id TEXT,
              target_profile_id TEXT,resource_kind TEXT,provider_project_uuid TEXT,provider_environment_uuid TEXT,
              provider_server_uuid TEXT,metadata_json TEXT,fqdn TEXT)
            """);
        jdbc.execute("CREATE TABLE platform_managed_product_services(details_json TEXT)");
        jdbc.execute("CREATE TABLE deployment_managed_dependency_graphs(state_json TEXT)");
        jdbc.execute("CREATE TABLE platform_deployments(id TEXT PRIMARY KEY,customer_id TEXT,tenant_id TEXT)");
        jdbc.update("INSERT INTO platform_deployments VALUES(?,?,?)", SCOPE.deploymentId(), SCOPE.customerId(), SCOPE.tenantId());
        var transactions = new DataSourceTransactionManager(source);
        journal = new ManagedResourceOwnershipService(jdbc, mapper, transactions);
        caller = new TransactionTemplate(transactions);
        var deployment = new DeploymentEntity();
        deployment.setId(SCOPE.deploymentId()); deployment.setCustomerId(SCOPE.customerId()); deployment.setTenantId(SCOPE.tenantId());
        var deployments = mock(DeploymentRepository.class);
        when(deployments.findById(SCOPE.deploymentId())).thenReturn(Optional.of(deployment));
        var ownership = new CoolifyResourceOwnership(journal, mapper, deployments, jdbc, transactions);
        var location = ownership.location(connection, "project", "environment");
        journal.create(SCOPE, "coolify", "APPLICATION", ownership.parent(location), "runtime", () -> "owned",
            uuid -> new ManagedResourceOwnershipService.Identity(uuid, location.endpoint()));
        receipt = ownership.requireOwned(SCOPE, location, "APPLICATION", "runtime", "owned");
        handle = new DeploymentProviderResourceHandleEntity();
        handle.setId("handle"); handle.setDeploymentId(SCOPE.deploymentId()); handle.setTargetProfileId(SCOPE.targetProfileId());
        handle.setProviderType(DeploymentProviderType.COOLIFY); handle.setResourceKind("APPLICATION");
        handle.setProviderResourceUuid("owned"); handle.setProviderProjectUuid("project");
        handle.setProviderEnvironmentUuid("environment"); handle.setProviderServerUuid("server"); handle.setStatus("ACTIVE");
        handle.setMetadataJson(mapper.createObjectNode().put("applicationName", "runtime").put("ownershipReceiptId", receipt.id()).toString());
        jdbc.update("INSERT INTO deployment_provider_resource_handles VALUES(?,?,?,?,?,?,?,?,?,?,?,NULL)",
            handle.getId(), "COOLIFY", "owned", "ACTIVE", SCOPE.deploymentId(), SCOPE.targetProfileId(), "APPLICATION",
            "project", "environment", "server", handle.getMetadataJson());
        var profile = new DeploymentTargetProfileEntity(); profile.setId(SCOPE.targetProfileId());
        var targets = mock(DeploymentTargetProfileRepository.class);
        when(targets.findById(profile.getId())).thenReturn(Optional.of(profile));
        var resolver = mock(CoolifyTargetProfileResolver.class);
        when(resolver.requireConnection(profile)).thenReturn(connection);
        client = new FakeCoolify();
        provider = new CoolifyDeploymentProvider(targets, mock(DeploymentProviderResourceHandleRepository.class),
            mock(DeploymentSourceArtifactService.class), mock(RailwayProvisioningPlanService.class), resolver, client, mapper);
        ReflectionTestUtils.setField(provider, "resourceOwnership", ownership);
    }

    @Test
    void providerDeletionCommitsJournalDespiteCallerRollback() {
        caller.executeWithoutResult(tx -> {
            assertThat(provider.delete(handle, "synthetic").status()).isEqualTo("COMPLETED");
            tx.setRollbackOnly();
        });
        assertThat(journal.receipt(receipt.id()).state()).isEqualTo("DELETED");
        assertThat(client.deletes).isEqualTo(1);
        assertThat(provider.delete(handle, "retry").status()).isEqualTo("COMPLETED");
        assertThat(client.deletes).isEqualTo(1);
    }

    @Test
    void ambiguousRemoteOutcomeRetainsPendingReceiptAcrossRollbackAndRecovery() {
        client.failAfterDelete = true;
        caller.executeWithoutResult(tx -> {
            assertThatThrownBy(() -> provider.delete(handle, "synthetic")).hasMessageContaining("ambiguous");
            tx.setRollbackOnly();
        });
        assertThat(journal.receipt(receipt.id()).state()).isEqualTo("DELETE_PENDING");
        assertThat(provider.delete(handle, "retry").status()).isEqualTo("COMPLETED");
        assertThat(journal.receipt(receipt.id()).state()).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_managed_resource_ownership", Long.class)).isEqualTo(1);
        assertThat(client.deletes).isEqualTo(1);
    }

    @Test
    void retirementSpiIsDurableAndRequiresActualAbsenceWithoutProviderWrites() {
        caller.executeWithoutResult(tx -> {
            provider.requireRetirementOwnership(handle);
            provider.beginRetirementDeletion(handle);
            tx.setRollbackOnly();
        });
        assertThat(journal.receipt(receipt.id()).state()).isEqualTo("DELETE_PENDING");
        assertThatThrownBy(() -> provider.finishRetirementDeletion(handle)).hasMessageContaining("RECONCILIATION_REQUIRED");
        client.present = false;
        caller.executeWithoutResult(tx -> {
            provider.finishRetirementDeletion(handle);
            tx.setRollbackOnly();
        });
        provider.requireRetirementOwnership(handle);
        provider.beginRetirementDeletion(handle);
        provider.finishRetirementDeletion(handle);
        assertThat(journal.receipt(receipt.id()).state()).isEqualTo("DELETED");
        assertThat(client.deletes).isZero();
        client.present = true;
        assertThatThrownBy(() -> provider.requireRetirementOwnership(handle)).hasMessageContaining("RECONCILIATION_REQUIRED");
    }

    @Test
    void anotherPersistedHandleStillBlocksDeletionEvenWhenRepositoryReadbackOmitsIt() {
        jdbc.update("INSERT INTO deployment_provider_resource_handles(id,provider_resource_uuid,status) VALUES('foreign','owned','ACTIVE')");
        assertThatThrownBy(() -> provider.delete(handle, "synthetic")).hasMessageContaining("REFERENCED");
        assertThat(journal.receipt(receipt.id()).state()).isEqualTo("OWNED");
        assertThat(client.deletes).isZero();
    }

    @Test
    void atomicRetirementPreparationDurablyCommitsOnceWithoutProviderIo() {
        var preparation = provider.preflightRetirementDeletion(handle);
        int reads = client.reads;
        caller.executeWithoutResult(tx -> {
            assertThat(provider.prepareRetirementDeletion(handle, preparation, () -> { })).isTrue();
            tx.setRollbackOnly();
        });
        assertThat(journal.receipt(receipt.id()).state()).isEqualTo("DELETE_PENDING");
        assertThat(provider.prepareRetirementDeletion(handle, preparation, () -> { })).isFalse();
        assertThat(client.reads).isEqualTo(reads);
        assertThat(client.deletes).isZero();
    }

    @Test
    void atomicRetirementPreparationRejectsFencingAndChangedPersistedHandle() {
        var preparation = provider.preflightRetirementDeletion(handle);
        assertThatThrownBy(() -> provider.prepareRetirementDeletion(handle, preparation, () -> {
            throw new IllegalStateException("LIFECYCLE_WORKER_FENCED");
        })).hasMessageContaining("LIFECYCLE_WORKER_FENCED");
        assertThat(journal.receipt(receipt.id()).state()).isEqualTo("OWNED");
        jdbc.update("UPDATE deployment_provider_resource_handles SET provider_resource_uuid='foreign' WHERE id=?", handle.getId());
        assertThatThrownBy(() -> provider.prepareRetirementDeletion(handle, preparation, () -> { })).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(journal.receipt(receipt.id()).state()).isEqualTo("OWNED");
        assertThat(client.deletes).isZero();
    }

    private class FakeCoolify extends CoolifyApiClient {
        boolean present = true;
        boolean failAfterDelete;
        int deletes;
        int reads;

        FakeCoolify() { super(mapper); }

        @Override public Optional<CoolifyApplicationSummary> getApplication(CoolifyConnection ignored, String uuid) {
            reads++;
            assertThat(uuid).isEqualTo("owned");
            var raw = mapper.createObjectNode().put("uuid", uuid).put("name", "runtime");
            raw.putObject("destination").put("uuid", "destination").putObject("server").put("uuid", "server");
            return present ? Optional.of(new CoolifyApplicationSummary(uuid, "runtime", null, "running", null, null, raw)) : Optional.empty();
        }
        @Override public List<CoolifyProjectSummary> listProjects(CoolifyConnection ignored) {
            reads++;
            return List.of(new CoolifyProjectSummary("project", "synthetic", null,
                mapper.createObjectNode().put("uuid", "project").put("id", 11)));
        }
        @Override public Optional<CoolifyEnvironmentSummary> getEnvironment(CoolifyConnection ignored, String project, String environment) {
            reads++;
            assertThat(project).isEqualTo("project"); assertThat(environment).isEqualTo("environment");
            var raw = mapper.createObjectNode().put("uuid", environment).put("id", 22).put("project_id", 11);
            var members = raw.putArray("applications");
            if (present) members.addObject().put("uuid", "owned").put("name", "runtime").put("environment_id", 22);
            return Optional.of(new CoolifyEnvironmentSummary(environment, "staging", project, null, raw));
        }
        @Override public List<CoolifyApplicationStorageSummary> listApplicationStorages(CoolifyConnection ignored, String uuid) {
            reads++;
            assertThat(uuid).isEqualTo("owned");
            return List.of();
        }
        @Override public CoolifyActionResponse delete(CoolifyConnection ignored, String uuid, boolean configurations, boolean volumes,
                boolean cleanup, boolean networks) {
            assertThat(journal.receipt(receipt.id()).state()).isEqualTo("DELETE_PENDING");
            assertThat(uuid).isEqualTo(receipt.resourceId());
            assertThat(configurations).isTrue();
            assertThat(volumes || cleanup || networks).isFalse();
            deletes++;
            present = false;
            if (failAfterDelete) throw new CoolifyApiException("synthetic ambiguous delete", 503, "/applications/owned");
            return new CoolifyActionResponse("synthetic", null, mapper.createObjectNode());
        }
    }
}
