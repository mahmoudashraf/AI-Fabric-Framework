package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyFixtures;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class OwnedDependencyOwnershipPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    final ObjectMapper mapper = new ObjectMapper();
    final CoolifyApiClient api = mock(CoolifyApiClient.class);
    final CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
    final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    final CoolifyTargetProfileConfig config = new CoolifyTargetProfileConfig("https://coolify.example.test", "project", "staging",
        "environment", "server", "destination", "example.test", null, 1, 10, false, true, "8080", "/health", "8080");
    final CoolifyConnection connection = new CoolifyConnection(config.baseUrl(), "private", config);
    OwnedDependencyOwnershipFixture fixture;
    JdbcTemplate jdbc;

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V197__managed_resource_creation_ownership.sql")).execute(source);
        jdbc.execute("CREATE TABLE platform_deployment_releases(id VARCHAR PRIMARY KEY,deployment_id VARCHAR,target_profile_id VARCHAR,provisioning_details_json TEXT)");
        jdbc.execute("CREATE TABLE platform_deployment_managed_vector_resources(vendor VARCHAR,resource_reference VARCHAR,endpoint VARCHAR,deployment_id VARCHAR,deployment_release_id VARCHAR)");
        jdbc.execute("CREATE TABLE deployment_provider_resource_handles(provider_resource_uuid VARCHAR,status VARCHAR,deployment_id VARCHAR,target_profile_id VARCHAR,resource_kind VARCHAR,fqdn VARCHAR,metadata_json TEXT)");
        var transactions = new DataSourceTransactionManager(source);
        fixture = new OwnedDependencyOwnershipFixture(api, targets, resolver, mapper, connection, jdbc, transactions,
            new ManagedResourceOwnershipService(jdbc, mapper, transactions));
    }

    private PlatformManagedProductProvisioningService.OwnedDependencyResources empty() {
        return new PlatformManagedProductProvisioningService.OwnedDependencyResources(null, null, false, false, Map.of());
    }

    private CoolifyApplicationSummary application() {
        return new CoolifyApplicationSummary("app", "ai-state", "https://ai-state.example.test", "running:healthy",
            "registry.example.test/dependency", "sha256-" + "a".repeat(64), fixture.raw("app", "ai-state", "ManagedDependency:graph:state"));
    }

    @Test void applicationAndStorageReceiptsCommitBeforeEffectsAndDeletionRequiresTheJournal() {
        fixture.graph(ManagedDependencyFixtures.http("state", false), "ai-state", empty());
        var owner = fixture.context();
        fixture.authority.createApplication(owner, () -> {
            assertThat(jdbc.queryForObject("SELECT state FROM platform_managed_resource_ownership", String.class)).isEqualTo("CREATE_PENDING");
            fixture.applications.put("app", application());
            return "app";
        });
        fixture.checkpoint.created("APPLICATION", "app");
        var storage = fixture.authority.volume(owner, "app", "/app/data", null);
        fixture.checkpoint.volume("/app/data", storage.uuid());
        assertThat(jdbc.queryForList("SELECT state FROM platform_managed_resource_ownership", String.class)).hasSize(2).containsOnly("OWNED");
        assertThat(fixture.authority.application(owner, "app").uuid()).isEqualTo("app");

        fixture.applications.clear();
        assertThatThrownBy(() -> fixture.authority.application(owner, "app", true)).hasMessageContaining("OWNERSHIP_CONFLICT");
        fixture.authority.beginVolumeDeletion(owner, "app", fixture.resources().volumes(), null);
        fixture.authority.beginDeletion(owner, OwnedDependencyOwnership.APPLICATION, "app");
        assertThat(jdbc.queryForList("SELECT state FROM platform_managed_resource_ownership", String.class)).containsOnly("DELETE_PENDING");
        assertThat(fixture.authority.application(owner, "app", true)).isEmpty();
        fixture.authority.deleted(owner, OwnedDependencyOwnership.APPLICATION, "app");
        assertThat(jdbc.queryForObject("SELECT state FROM platform_managed_resource_ownership WHERE resource_kind=?", String.class,
            OwnedDependencyOwnership.APPLICATION)).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("SELECT state FROM platform_managed_resource_ownership WHERE resource_kind=?", String.class,
            OwnedDependencyOwnership.APP_VOLUME)).isEqualTo("DELETE_PENDING");
    }

    @Test void ambiguousApplicationCreateCannotBeRecoveredFromAMatchingLiveResource() {
        fixture.graph(ManagedDependencyFixtures.http("state", false), "ai-state", empty());
        var effects = new AtomicInteger();
        assertThatThrownBy(() -> fixture.authority.createApplication(fixture.context(), () -> {
            effects.incrementAndGet(); fixture.applications.put("app", application());
            throw new IllegalStateException("synthetic lost create response");
        })).hasMessageContaining("lost create response");
        assertThat(jdbc.queryForObject("SELECT state FROM platform_managed_resource_ownership", String.class)).isEqualTo("CREATE_PENDING");
        assertThatThrownBy(() -> fixture.authority.createApplication(fixture.context(), () -> {
            effects.incrementAndGet(); return "app";
        })).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(effects).hasValue(1);
        assertThat(fixture.resources().applicationUuid()).isNull();
    }

    @Test void automaticDatabaseStorageIsJournaledBeforeCreationAndUnknownReadbackRemainsPending() {
        fixture.graph(ManagedDependencyFixtures.http("state", true), "ai-state", empty());
        var effects = new AtomicInteger();
        assertThatThrownBy(() -> fixture.authority.createDatabase(fixture.context(), () -> {
            effects.incrementAndGet();
            assertThat(jdbc.queryForList("SELECT state FROM platform_managed_resource_ownership", String.class)).hasSize(2).containsOnly("CREATE_PENDING");
            fixture.databases.put("db", new CoolifyDatabaseSummary("db", "ai-state-db", "stopped", "postgresql", "dependency", "dependency",
                fixture.raw("db", "ai-state-db", "ManagedDependency:graph:state")));
            return "db";
        })).hasMessageContaining("VOLUME_OWNERSHIP_CONFLICT");
        assertThat(jdbc.queryForList("SELECT state FROM platform_managed_resource_ownership", String.class)).hasSize(2).containsOnly("CREATE_PENDING");
        fixture.dbStorages.put("db", List.of(fixture.storage("db-volume", "db-state", "/var/lib/postgresql/data")));
        assertThatThrownBy(() -> fixture.authority.createDatabase(fixture.context(), () -> {
            effects.incrementAndGet(); return "db";
        })).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(effects).hasValue(1);
    }

    @Test void successfulReceiptWithALostGraphCheckpointNeverBackfillsOwnership() {
        fixture.graph(ManagedDependencyFixtures.http("state", false), "ai-state", empty());
        fixture.applications.put("app", application());
        fixture.authority.createApplication(fixture.context(), () -> "app");
        assertThat(jdbc.queryForObject("SELECT state FROM platform_managed_resource_ownership", String.class)).isEqualTo("OWNED");
        assertThatThrownBy(() -> fixture.authority.requireResources(fixture.context(), empty())).hasMessageContaining("CREATE_UNCERTAIN");
        assertThat(fixture.resources().applicationUuid()).isNull();
    }
}
