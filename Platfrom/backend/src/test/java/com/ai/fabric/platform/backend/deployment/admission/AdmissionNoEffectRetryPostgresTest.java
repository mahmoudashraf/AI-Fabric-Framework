package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Actual PostgreSQL ledger/transactions; repository lookups are adapters over the fixture's real release rows. */
@Testcontainers(disabledWithoutDocker = true)
class AdmissionNoEffectRetryPostgresTest extends AdmissionFixture {
    @Container
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String DEPLOYMENT = "deployment";
    private static final String FIRST_KEY = "original-apply";
    private static final String RETRY_KEY = "explicit-known-no-effect-retry";
    private static final String FIRST_RELEASE = "failed-release";
    private static final String RETRY_RELEASE = "retry-release";
    private DeploymentEntity deployment;
    private DeploymentVersionEntity version;
    private AdmissionContext admitted;
    private Reserve firstCommand;
    private Reservation original;
    private AdmissionReleaseCoordinator coordinator;
    private AdmissionResourceReadbackSource resources;

    @Override
    @BeforeEach
    void database() {
        String schema = "no_effect_retry_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl()
            + (DATABASE.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
            DATABASE.getUsername(), DATABASE.getPassword());
        new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V1__initial_platform_schema.sql"),
            new ClassPathResource("db/migration/V165__durable_capacity_admission_and_staging_expiry.sql"),
            new ClassPathResource("db/migration/V178__admission_allocation_authorizations.sql"),
            new ClassPathResource("db/migration/V190__provider_usage_allowance.sql")
        ).execute(dataSource);
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        json = new AdmissionJson(mapper);
        jdbc = new JdbcTemplate(dataSource);
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW, ZoneOffset.UTC));
        initialQueuedRelease();
    }

    private void initialQueuedRelease() {
        Timestamp now = Timestamp.from(NOW);
        jdbc.update("""
            INSERT INTO platform_deployments
              (id,name,environment_name,template_id,status,created_at,updated_at)
            VALUES (?,'Retry fixture','staging','fixture-template','DRAFT',?,?)
            """, DEPLOYMENT, now, now);
        jdbc.update("""
            INSERT INTO platform_deployment_drafts
              (id,deployment_id,revision_number,status,actions_config_json,entity_config_json,routing_config_json,
               provider_config_json,security_config_json,created_at,updated_at)
            VALUES ('draft',?,1,'PUBLISHED','{}','{}','{}','{}','{}',?,?)
            """, DEPLOYMENT, now, now);
        jdbc.update("""
            INSERT INTO platform_deployment_versions
              (id,deployment_id,source_draft_id,version_label,status,config_hash,reindex_required,
               actions_config_json,entity_config_json,routing_config_json,provider_config_json,security_config_json,
               actions_artifact_yaml,entity_artifact_yaml,routing_artifact_yaml,manifest_json,published_at)
            VALUES ('version',?,'draft','v1','PUBLISHED','config-hash',false,
                    '{}','{}','{}','{}','{}','{}','{}','{}','{}',?)
            """, DEPLOYMENT, now);
        // A synthetic, fixed whole-task financial envelope: the retry must neither spend nor replace it.
        jdbc.update("""
            INSERT INTO provider_usage_grants
              (id,campaign_id,customer_id,target_profile_id,environment_name,fingerprint,payload_json,
               approved_by,approved_role,approved_at)
            VALUES ('usage-grant','fixed-whole-task','customer',?,'staging',?,?,'operator','PLATFORM_ADMIN',?)
            """, TARGET, "f".repeat(64),
            "{\"credits\":{\"costNanoUsd\":12000000000,\"totalTokens\":400000,\"chatAttempts\":50}}", now);
        approve(2);
        store.approveManifest(manifest("manifest", DEPLOYMENT), "operator", NOW);
        admitted = context("manifest");
        firstCommand = new Reserve(FIRST_KEY, admitted.manifest().id(), scope(FIRST_KEY), null);
        original = store.reserve(admitted, firstCommand, evidence(admitted, NOW), "owner", NOW);
        deployment = new DeploymentEntity();
        deployment.setId(DEPLOYMENT); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        version = new DeploymentVersionEntity();
        version.setId("version"); version.setDeploymentId(DEPLOYMENT);
        version.setStatus("PUBLISHED"); version.setConfigHash("config-hash");
        var offers = mock(CustomerOfferDeploymentRepository.class);
        var releases = mock(DeploymentReleaseRepository.class);
        var versions = mock(DeploymentVersionRepository.class);
        resources = mock(AdmissionResourceReadbackSource.class);
        when(offers.existsById(DEPLOYMENT)).thenReturn(true);
        when(releases.findById(anyString())).thenAnswer(call -> releaseEntity(call.getArgument(0)));
        when(versions.findById("version")).thenReturn(Optional.of(version));
        coordinator = new AdmissionReleaseCoordinator(store, offers, releases, versions,
            context -> evidence(context, NOW), resources, Clock.fixed(NOW, ZoneOffset.UTC));
        bind(FIRST_RELEASE);
        assertThat(store.releaseBinding(FIRST_RELEASE).state()).isEqualTo("QUEUED");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void explicitSameManifestRetryAppendsAuthorizationAndReleaseWithoutReplacingTheAllocation(boolean advanceRevision) {
        if (advanceRevision) {
            // Exercise revision 1 through a normal same-clock activity, without extending the original lifetime.
            original = store.activity(original.fence(), Activity.CONFIGURATION_WRITE, "same-clock-configuration", "owner", NOW);
            assertThat(original.revision()).isEqualTo(1);
        }
        failQueuedRelease();
        Reservation held = store.require(original.id());
        String oldAuthorization = authorizationRow(FIRST_KEY);
        String oldRelease = releaseRow(FIRST_RELEASE);
        String oldBinding = bindingRow(FIRST_RELEASE);
        String funds = financialRows();
        var retry = new Reserve(RETRY_KEY, admitted.manifest().id(), scope(RETRY_KEY), held.id());

        Reservation authorized = store.reserve(store.context(held), retry, evidence(admitted, NOW), "owner", NOW);
        store.requireCustomerAuthorization(authorized, retry.customerContext());

        assertThat(authorized.id()).isEqualTo(held.id());
        assertThat(authorized.generation()).isEqualTo(held.generation());
        assertThat(authorized.revision()).isEqualTo(held.revision() + 1);
        assertThat(authorized.state()).isEqualTo(State.RESERVED);
        assertThat(authorized.expiresAt()).isEqualTo(held.expiresAt());
        assertThat(authorized.createdAt()).isEqualTo(held.createdAt());
        assertThat(authorized.lastActivityAt()).isEqualTo(held.lastActivityAt());
        assertThat(authorized.demand()).isEqualTo(held.demand());
        assertThat(authorized.manifestId()).isEqualTo(held.manifestId());
        assertThat(authorized.manifestFingerprint()).isEqualTo(held.manifestFingerprint());
        assertThat(authorized.bindingFingerprint()).isEqualTo(held.bindingFingerprint());
        assertThat(authorized.operationKey()).isEqualTo(FIRST_KEY);
        assertThat(authorized.requestFingerprint()).isEqualTo(held.requestFingerprint());
        assertThat(authorized.customerContextFingerprint()).isEqualTo(held.customerContextFingerprint());
        assertThat(store.reserve(store.context(authorized), retry, evidence(admitted, NOW), "owner", NOW)).isEqualTo(authorized);
        assertThat(store.currentReleaseId(authorized)).isNull();
        bind(RETRY_RELEASE);

        ReleaseBinding next = store.releaseBinding(RETRY_RELEASE);
        assertThat(next.authorizationKey()).isEqualTo(RETRY_KEY);
        assertThat(next.reservationId()).isEqualTo(held.id());
        assertThat(next.generation()).isEqualTo(held.generation());
        assertThat(next.versionId()).isEqualTo("version");
        assertThat(next.versionConfigHash()).isEqualTo("config-hash");
        assertThat(next.state()).isEqualTo("QUEUED");
        assertThat(store.currentReleaseId(authorized)).isEqualTo(RETRY_RELEASE);
        assertThat(store.held()).containsExactly(authorized);
        assertThat(authorizationRow(FIRST_KEY)).isEqualTo(oldAuthorization);
        assertThat(releaseRow(FIRST_RELEASE)).isEqualTo(oldRelease);
        assertThat(bindingRow(FIRST_RELEASE)).isEqualTo(oldBinding);
        assertThat(financialRows()).isEqualTo(funds);
        assertThat(count("deployment_capacity_reservations")).isEqualTo(1);
        assertThat(count("deployment_admission_authorizations")).isEqualTo(2);
        assertThat(count("deployment_admission_release_bindings")).isEqualTo(2);
        assertThat(count("platform_deployment_releases")).isEqualTo(2);
        assertThat(count("provider_usage_allocations")).isZero();
        assertThat(count("provider_usage_allowance_events")).isZero();
        assertThatThrownBy(() -> coordinator.beginProvisioning(FIRST_RELEASE)).hasMessageContaining("RELEASE_ALLOCATION_FENCED");
        assertThatThrownBy(() -> bind("duplicate-retry-release")).hasMessageContaining("EXACT_APPLY_AUTHORIZATION_REQUIRED");
        assertThat(count("platform_deployment_releases")).isEqualTo(2);
        verifyNoInteractions(resources);
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUEUED", "PROVISIONING", "OUTCOME_UNKNOWN"})
    void uncertainOrInProgressReleaseCannotUseAnExplicitRetryKey(String bindingState) {
        if (!"QUEUED".equals(bindingState)) coordinator.beginProvisioning(FIRST_RELEASE);
        if ("OUTCOME_UNKNOWN".equals(bindingState)) {
            markFailed(FIRST_RELEASE);
            coordinator.observe(FIRST_RELEASE);
        }
        assertThat(store.releaseBinding(FIRST_RELEASE).state()).isEqualTo(bindingState);
        Reservation before = store.require(original.id());
        String reservation = reservationRow(before.id());
        String authorization = authorizationRow(FIRST_KEY);
        String release = releaseRow(FIRST_RELEASE);
        String binding = bindingRow(FIRST_RELEASE);
        String funds = financialRows();

        assertThatThrownBy(() -> store.reserve(store.context(before),
            new Reserve(RETRY_KEY, admitted.manifest().id(), scope(RETRY_KEY), before.id()),
            evidence(admitted, NOW), "owner", NOW)).isInstanceOf(AdmissionFailure.class);

        assertThat(reservationRow(before.id())).isEqualTo(reservation);
        assertThat(authorizationRow(FIRST_KEY)).isEqualTo(authorization);
        assertThat(releaseRow(FIRST_RELEASE)).isEqualTo(release);
        assertThat(bindingRow(FIRST_RELEASE)).isEqualTo(binding);
        assertThat(financialRows()).isEqualTo(funds);
        assertThat(count("deployment_admission_authorizations")).isEqualTo(1);
        assertThat(count("deployment_admission_release_bindings")).isEqualTo(1);
        assertThat(count("provider_usage_allocations")).isZero();
        verifyNoInteractions(resources);
    }

    @Test
    void originalKeyOnlyReplaysItsReceiptAndCannotAuthorizeAnotherRelease() {
        failQueuedRelease();
        Reservation before = store.require(original.id());
        String reservation = reservationRow(before.id());
        String authorization = authorizationRow(FIRST_KEY);
        String failed = releaseRow(FIRST_RELEASE);
        String funds = financialRows();

        assertThat(store.reserve(store.context(before), firstCommand, evidence(admitted, NOW), "owner", NOW)).isEqualTo(before);
        assertThatThrownBy(() -> store.reserve(store.context(before),
            new Reserve(FIRST_KEY, admitted.manifest().id(), scope(FIRST_KEY), before.id()),
            evidence(admitted, NOW), "owner", NOW)).hasMessageContaining("OPERATION_KEY_REUSED");
        assertThatThrownBy(() -> bind(RETRY_RELEASE)).hasMessageContaining("EXACT_APPLY_AUTHORIZATION_REQUIRED");

        assertThat(reservationRow(before.id())).isEqualTo(reservation);
        assertThat(authorizationRow(FIRST_KEY)).isEqualTo(authorization);
        assertThat(releaseRow(FIRST_RELEASE)).isEqualTo(failed);
        assertThat(financialRows()).isEqualTo(funds);
        assertThat(count("deployment_admission_authorizations")).isEqualTo(1);
        assertThat(count("deployment_admission_release_bindings")).isEqualTo(1);
        assertThat(count("platform_deployment_releases")).isEqualTo(1);
        verifyNoInteractions(resources);
    }

    private void failQueuedRelease() {
        markFailed(FIRST_RELEASE);
        coordinator.observe(FIRST_RELEASE);
        assertThat(store.releaseBinding(FIRST_RELEASE).state()).isEqualTo("NO_EFFECT");
        assertThat(store.require(original.id()).state()).isEqualTo(State.RESERVED);
        assertThat(store.currentReleaseId(store.require(original.id()))).isNull();
    }

    private void markFailed(String id) {
        jdbc.update("UPDATE platform_deployment_releases SET status='FAILED',provisioning_status='FAILED',error_message='preflight failed' WHERE id=?", id);
    }

    private void bind(String id) {
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(tx -> {
            Timestamp now = Timestamp.from(NOW);
            jdbc.update("""
                INSERT INTO platform_deployment_releases
                  (id,deployment_id,deployment_version_id,status,verification_status,provisioning_status,
                   provisioning_target,provisioning_details_json,created_at,applied_at,updated_at)
                VALUES (?,?,'version','APPLY_REQUESTED','PENDING','QUEUED','COOLIFY','{}',?,?,?)
                """, id, DEPLOYMENT, now, now, now);
            coordinator.bind(deployment, version, releaseEntity(id).orElseThrow());
        });
    }

    private Optional<DeploymentReleaseEntity> releaseEntity(String id) {
        return jdbc.query("SELECT * FROM platform_deployment_releases WHERE id=?", (rs, row) -> {
            var release = new DeploymentReleaseEntity();
            release.setId(rs.getString("id")); release.setDeploymentId(rs.getString("deployment_id"));
            release.setDeploymentVersionId(rs.getString("deployment_version_id")); release.setTargetProfileId(TARGET);
            release.setStatus(rs.getString("status")); release.setProvisioningStatus(rs.getString("provisioning_status"));
            return release;
        }, id).stream().findFirst();
    }

    private CustomerOfferContracts.AdmissionContext scope(String key) {
        return new CustomerOfferContracts.AdmissionContext("customer", "tenant", DEPLOYMENT, null, TARGET,
            CustomerOfferContracts.TargetIntent.CUSTOMER_STAGING, "offer", "offer-fingerprint", 1, "placement-proof",
            CustomerOfferContracts.Operation.APPLY, key,
            new CustomerOfferContracts.ReviewedLimits("measurement-proof", 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1),
            new CustomerOfferContracts.ReviewedTopology(1, 1, 1, 1, 1, 1, 1, 1));
    }

    private String authorizationRow(String key) {
        return jdbc.queryForObject("SELECT row_to_json(a)::text FROM deployment_admission_authorizations a WHERE operation_key=?", String.class, key);
    }
    private String releaseRow(String id) {
        return jdbc.queryForObject("SELECT row_to_json(r)::text FROM platform_deployment_releases r WHERE id=?", String.class, id);
    }
    private String bindingRow(String id) {
        return jdbc.queryForObject("SELECT row_to_json(b)::text FROM deployment_admission_release_bindings b WHERE release_id=?", String.class, id);
    }
    private String reservationRow(String id) {
        return jdbc.queryForObject("SELECT row_to_json(r)::text FROM deployment_capacity_reservations r WHERE id=?", String.class, id);
    }
    private String financialRows() {
        return jdbc.queryForObject("SELECT coalesce(json_agg(row_to_json(g) ORDER BY id),'[]'::json)::text FROM provider_usage_grants g", String.class);
    }
    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
