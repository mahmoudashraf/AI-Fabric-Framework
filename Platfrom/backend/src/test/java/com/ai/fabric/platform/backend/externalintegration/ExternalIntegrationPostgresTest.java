package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerEntitlementCommandRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferEntitlementRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import jakarta.persistence.EntityManagerFactory;
import jakarta.validation.Validation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Testcontainers
class ExternalIntegrationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void approvalOptionsQueryUsesExactScopeAndReadStillVerifiesStoredIntegrity() throws Exception {
        try (var fixture = new DatabaseFixture()) {
            var f = fixture.f;
            var scope = new Scope("offer-generic", f.deployment.getCustomerId(), f.deployment.getTenantId(), f.deployment.getId(),
                f.version.getId(), f.target.getId(), f.profile.bindingSlotId(), f.profile.id());
            assertThat(fixture.store.approvals(scope)).isEmpty();
            var approved = fixture.approve();
            var original = fixture.store.approval(approved.id()).orElseThrow().value();
            int index = 0;
            for (UnaryOperator<Scope> change : List.<UnaryOperator<Scope>>of(
                s -> new Scope("other-offer", s.customerId(), s.tenantId(), s.deploymentId(), s.versionId(), s.targetProfileId(), s.bindingSlotId(), s.profileId()),
                s -> new Scope(s.offerId(), "other-customer", s.tenantId(), s.deploymentId(), s.versionId(), s.targetProfileId(), s.bindingSlotId(), s.profileId()),
                s -> new Scope(s.offerId(), s.customerId(), "other-tenant", s.deploymentId(), s.versionId(), s.targetProfileId(), s.bindingSlotId(), s.profileId()),
                s -> new Scope(s.offerId(), s.customerId(), s.tenantId(), "other-deployment", s.versionId(), s.targetProfileId(), s.bindingSlotId(), s.profileId()),
                s -> new Scope(s.offerId(), s.customerId(), s.tenantId(), s.deploymentId(), "other-version", s.targetProfileId(), s.bindingSlotId(), s.profileId()),
                s -> new Scope(s.offerId(), s.customerId(), s.tenantId(), s.deploymentId(), s.versionId(), "other-target", s.bindingSlotId(), s.profileId()),
                s -> new Scope(s.offerId(), s.customerId(), s.tenantId(), s.deploymentId(), s.versionId(), s.targetProfileId(), "other-slot", s.profileId()),
                s -> new Scope(s.offerId(), s.customerId(), s.tenantId(), s.deploymentId(), s.versionId(), s.targetProfileId(), s.bindingSlotId(), "other-profile"))) {
                var foreignScope = change.apply(scope);
                fixture.jdbc.update("insert into platform_deployments values (?) on conflict do nothing", foreignScope.deploymentId());
                fixture.jdbc.update("insert into platform_deployment_versions values (?) on conflict do nothing", foreignScope.versionId());
                fixture.jdbc.update("insert into deployment_target_profiles values (?) on conflict do nothing", foreignScope.targetProfileId());
                var foreign = new Approval("foreign-" + index, foreignScope, original.offerFingerprint(), original.versionConfigHash(),
                    original.targetFingerprint(), original.profile(), original.config(), original.accountScope(), original.reviewReference(),
                    original.reviewedBy(), "foreign-operation-" + index++, original.requestFingerprint());
                fixture.store.insertApproval(foreign, f.integrity.seal(foreign));
            }
            assertThat(fixture.store.approvals(scope)).extracting(row -> row.value().id()).containsExactly(approved.id());
            assertThat(fixture.service.approvals("offer-generic", scope.deploymentId(), scope.versionId(), scope.targetProfileId(),
                scope.bindingSlotId(), scope.profileId())).containsExactly(new ApprovalOption(original.id(), scope.versionId(),
                scope.targetProfileId(), scope.bindingSlotId(), scope.profileId(), original.config(), original.accountScope(), original.reviewReference()));
            assertThat(fixture.count("deployment_external_integration_bindings")).isZero();
            assertThat(fixture.count("platform_secrets")).isZero();
            assertThat(fixture.count("platform_customer_entitlement_commands")).isZero();
            fixture.jdbc.update("update deployment_external_integration_approvals set integrity_mac=? where id=?", "0".repeat(64), approved.id());
            assertThatThrownBy(() -> fixture.service.approvals("offer-generic", scope.deploymentId(), scope.versionId(), scope.targetProfileId(),
                scope.bindingSlotId(), scope.profileId())).hasMessageContaining("INTEGRITY");
        }
    }

    @Test void fullRealFlywayChainIncludes199AndPreservesParentCommandTypesWithoutSeedingBindings() throws Exception {
        String schema = "chain_" + UUID.randomUUID().toString().replace("-", "");
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("create schema " + schema);
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("199").load().migrate();
        assertThat(jdbc.queryForObject("select count(*) from " + schema + ".deployment_external_integration_bindings", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from " + schema + ".deployment_external_integration_approvals", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select pg_get_constraintdef(oid) from pg_constraint where conname='ck_customer_entitlement_command_type' and connamespace=(select oid from pg_namespace where nspname=?)",
            String.class, schema)).contains("BIND_AI_STATE", "BIND_EXTERNAL_SERVICE", "REVOKE_EXTERNAL_SERVICE", "BIND_S3", "CREATE_STAGING_CONSUMER");
        // Exercise the actual published DATA manifest, not a protocol invented by this test.
        var f = new ExternalIntegrationFixture();
        var plugin = new com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity();
        plugin.setId("mkp-data-dealership-external-stock-v1"); plugin.setPluginType("DATA"); plugin.setStatus("ACTIVE");
        var version = new com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity();
        version.setId("mkv-data-dealership-external-stock-v1-0"); version.setPluginId(plugin.getId()); version.setVersion("1.0.0"); version.setStatus("PUBLISHED");
        version.setManifestJson(jdbc.queryForObject("select manifest_json from " + schema + ".platform_marketplace_plugin_versions where id=?", String.class, version.getId()));
        when(f.plugins.findById(plugin.getId())).thenReturn(java.util.Optional.of(plugin));
        when(f.pluginVersions.findById(version.getId())).thenReturn(java.util.Optional.of(version));
        var profile = new Profile(PROFILE_SCHEMA, "external-stock-profile", "vehicle-stock", plugin.getId(), version.getId(),
            f.codec.hash(f.codec.tree(version.getManifestJson())), f.profile.targetProfileIds(), java.util.Set.of("providerBaseUrl", "providerHost", "advertiserId"),
            java.util.Set.of("providerKey", "providerSecret", "notificationSecret"), java.util.Set.of("providerBaseUrl"), java.util.Set.of("advertiserId"), java.util.Set.of("provider.example.com"));
        var definition = (com.fasterxml.jackson.databind.node.ObjectNode) f.mapper.valueToTree(f.definition);
        definition.set("plugins", f.mapper.valueToTree(java.util.List.of(new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.PluginPin(
            profile.pluginId(), profile.pluginVersionId(), profile.manifestSha256()))));
        definition.set("bindingSlots", f.mapper.valueToTree(java.util.List.of(new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.BindingSlot(
            profile.bindingSlotId(), com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.BindingKind.valueOf("EXTERNAL_SERVICE"), java.util.Set.of(profile.id()), profile.pluginId(), null))));
        definition.set("externalIntegrationProfiles", f.mapper.valueToTree(java.util.List.of(profile)));
        f.policy.validateDefinition(f.mapper.treeToValue(definition, com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Definition.class));
        var config = Map.of("providerBaseUrl", "https://provider.example.com", "providerHost", "provider.example.com", "advertiserId", "reviewed-account");
        f.policy.validateConfig(profile, config, Map.of("advertiserId", "reviewed-account"));
        assertThatThrownBy(() -> f.policy.validateConfig(profile, config, Map.of("advertiserId", "foreign-account"))).hasMessageContaining("ACCOUNT_SCOPE_MISMATCH");
    }

    @Test void actualJpaVaultJdbcBindingAndJpaReceiptCommitTogetherAndReplayPersistedPgPrecision() throws Exception {
        try (var fixture = new DatabaseFixture()) {
            var approval = fixture.approve(); var request = fixture.f.bindRequest(approval.id());
            var first = fixture.service.bind("offer-generic", fixture.f.deployment.getId(), request);
            assertThat(fixture.service.bind("offer-generic", fixture.f.deployment.getId(), request)).isEqualTo(first);
            assertThat(fixture.count("platform_secrets")).isEqualTo(1);
            assertThat(fixture.count("deployment_external_integration_bindings")).isEqualTo(1);
            assertThat(fixture.count("platform_customer_entitlement_commands")).isEqualTo(1);
            var binding = fixture.store.binding(fixture.f.deployment.getId(), fixture.f.version.getId(), fixture.f.target.getId(), fixture.f.profile.bindingSlotId()).orElseThrow().value();
            assertThat(binding.secretRevisions().values()).allSatisfy(value -> assertThat(Instant.parse(value).getNano() % 1000).isZero());
            assertThat(fixture.f.codec.write(binding)).doesNotContain("synthetic-customer-key");
            assertThat(fixture.jdbc.queryForObject("select result_json from platform_customer_entitlement_commands", String.class)).doesNotContain("synthetic-customer-key");
        }
    }

    @Test void failureAfterAllThreeWritesRollsBackJpaVaultJdbcBindingAndJpaReceiptTogether() throws Exception {
        try (var fixture = new DatabaseFixture()) {
            var approval = fixture.approve();
            doThrow(new IllegalStateException("synthetic late failure")).when(fixture.f.audit)
                .record(eq("EXTERNAL_INTEGRATION_BOUND"), anyString(), anyString(), anyMap());
            assertThatThrownBy(() -> fixture.service.bind("offer-generic", fixture.f.deployment.getId(), fixture.f.bindRequest(approval.id())))
                .hasMessageContaining("synthetic late failure");
            assertThat(fixture.count("platform_secrets")).isZero();
            assertThat(fixture.count("deployment_external_integration_bindings")).isZero();
            assertThat(fixture.count("platform_customer_entitlement_commands")).isZero();
            assertThat(fixture.count("deployment_external_integration_approvals")).isEqualTo(1);
        }
    }

    @Test void concurrentExactCustomerCommandsSerializeOnDeploymentAndReturnOneDurableReceipt() throws Exception {
        try (var fixture = new DatabaseFixture(); var workers = Executors.newFixedThreadPool(2)) {
            var approval = fixture.approve(); var request = fixture.f.bindRequest(approval.id());
            var start = new CountDownLatch(1);
            Callable<Summary> command = () -> { fixture.f.customer(); start.await(10, TimeUnit.SECONDS);
                return fixture.service.bind("offer-generic", fixture.f.deployment.getId(), request); };
            var first = workers.submit(command); var second = workers.submit(command); start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
            assertThat(fixture.count("platform_secrets")).isEqualTo(1);
            assertThat(fixture.count("deployment_external_integration_bindings")).isEqualTo(1);
            assertThat(fixture.count("platform_customer_entitlement_commands")).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
        "APPLY_REQUESTED,QUEUED",
        "PRE_APPLY_VERIFYING,QUEUED",
        "PROVISIONING,RUNNING",
        "VERIFYING,ACTIVE",
        "PROVISIONING,AWAITING_CONFIRMATION",
        "UNKNOWN_FUTURE_PHASE,RUNNING",
        "NULL,RUNNING"
    }, nullValues = "NULL")
    void persistedNonterminalOrUnknownReleaseBlocksRevocationWithoutChangingBindingOrReceipt(String status,
                                                                                           String provisioningStatus) throws Exception {
        try (var fixture = new DatabaseFixture()) {
            var binding = fixture.bind(); var before = fixture.binding();
            fixture.persistRelease("blocking-release", status, provisioningStatus);
            fixture.persistRelease("later-terminal-release", "APPLIED_VERIFIED", "ACTIVE");

            assertReleaseInProgress(catchThrowable(() -> fixture.service.revoke("offer-generic", fixture.f.deployment.getId(),
                fixture.f.revokeRequest(binding.id()))));

            assertThat(fixture.binding()).isEqualTo(before);
            assertThat(fixture.count("platform_customer_entitlement_commands")).isEqualTo(1);
            assertThat(fixture.count("platform_secrets")).isEqualTo(1);
            verify(fixture.f.audit, never()).record(eq("EXTERNAL_INTEGRATION_REVOKED"), anyString(), anyString(), anyMap());
        }
    }

    @Test void persistedReleaseWithUnknownTargetBlocksRevocationOfTheSameVersion() throws Exception {
        try (var fixture = new DatabaseFixture()) {
            var binding = fixture.bind(); var before = fixture.binding();
            fixture.persistRelease("unknown-target-release", fixture.f.deployment.getId(), fixture.f.version.getId(),
                null, "PROVISIONING", "AWAITING_CONFIRMATION");

            assertReleaseInProgress(catchThrowable(() -> fixture.service.revoke("offer-generic", fixture.f.deployment.getId(),
                fixture.f.revokeRequest(binding.id()))));

            assertThat(fixture.binding()).isEqualTo(before);
            assertThat(fixture.count("platform_customer_entitlement_commands")).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"deployment", "version", "target", "version-with-unknown-target"})
    void persistedInProgressReleaseOutsideExactBindingScopeDoesNotBlockRevocation(String differentScope) throws Exception {
        try (var fixture = new DatabaseFixture()) {
            var binding = fixture.bind();
            String deploymentId = fixture.f.deployment.getId(), versionId = fixture.f.version.getId(), targetId = fixture.f.target.getId();
            switch (differentScope) {
                case "deployment" -> deploymentId = "unrelated-deployment";
                case "version" -> versionId = "unrelated-version";
                case "target" -> targetId = "unrelated-target";
                case "version-with-unknown-target" -> { versionId = "unrelated-version"; targetId = null; }
                default -> throw new AssertionError("Unexpected scope");
            }
            fixture.persistRelease("unrelated-release", deploymentId, versionId, targetId, "PROVISIONING", "RUNNING");

            var result = fixture.service.revoke("offer-generic", fixture.f.deployment.getId(), fixture.f.revokeRequest(binding.id()));

            assertThat(result.status()).isEqualTo("REVOKED_FUTURE_RESOLUTION_EXTERNAL_RETAINED");
            assertThat(fixture.binding().status()).isEqualTo("REVOKED");
            assertThat(fixture.binding().revision()).isEqualTo(1);
            assertThat(fixture.count("platform_customer_entitlement_commands")).isEqualTo(2);
            assertThat(fixture.jdbc.queryForObject("select status from platform_deployment_releases where id='unrelated-release'", String.class))
                .isEqualTo("PROVISIONING");
        }
    }

    @ParameterizedTest
    @CsvSource({
        "APPLIED_VERIFIED,ACTIVE",
        "APPLIED_VERIFICATION_FAILED,ACTIVE",
        "PRE_APPLY_BLOCKED,BLOCKED",
        "FAILED,FAILED"
    })
    void terminalReleaseAllowsRevocationButCannotResolveItsFrozenBindingAgain(String status, String provisioningStatus) throws Exception {
        try (var fixture = new DatabaseFixture()) {
            var binding = fixture.bind(); fixture.f.release();
            fixture.persistRelease("release-generic", status, provisioningStatus);
            new TransactionTemplate(fixture.transactions).execute(tx -> fixture.resolver.freezeRelease(fixture.f.deployment.getId(),
                fixture.f.version.getId(), fixture.f.target.getId(), "release-generic", "authority-fingerprint"));
            var request = fixture.f.revokeRequest(binding.id());

            var result = fixture.service.revoke("offer-generic", fixture.f.deployment.getId(), request);

            assertThat(fixture.service.revoke("offer-generic", fixture.f.deployment.getId(), request)).isEqualTo(result);
            assertThat(fixture.binding().status()).isEqualTo("REVOKED");
            assertThat(fixture.binding().revision()).isEqualTo(1);
            assertThat(fixture.count("platform_customer_entitlement_commands")).isEqualTo(2);
            assertThat(fixture.count("platform_secrets")).isEqualTo(1);
            assertThat(fixture.count("deployment_external_integration_snapshots")).isEqualTo(1);
            assertThatThrownBy(() -> fixture.resolver.releaseEnvironment(fixture.f.deployment.getId(), fixture.f.version.getId(),
                fixture.f.target.getId(), "release-generic", "authority-fingerprint")).hasMessageContaining("BINDING_STALE_OR_FOREIGN");
        }
    }

    @ParameterizedTest
    @CsvSource({
        "REVOKE,APPLY_REQUESTED", "INVITE,APPLY_REQUESTED",
        "REVOKE,PROVISIONING", "INVITE,PROVISIONING",
        "REVOKE,VERIFYING", "INVITE,VERIFYING"
    })
    void actualEntitlementMutationConflictsWhileItsPersistedExternalSnapshotIsInProgress(String command, String status) throws Exception {
        try (var fixture = new EntitlementFixture()) {
            fixture.externalRelease(status);
            assertThat(fixture.grants.countUnfinishedExternalReleases(fixture.caller.getCustomerId(), fixture.caller.getOfferId())).isEqualTo(1);

            assertThatThrownBy(() -> fixture.mutate(command, fixture.caller)).isInstanceOfSatisfying(CustomerSelfServiceException.class, denied -> {
                assertThat(denied.code()).isEqualTo(CustomerSelfServiceException.Code.RELEASE_IN_PROGRESS);
                assertThat(denied.status()).isEqualTo(HttpStatus.CONFLICT);
            });

            var persisted = fixture.grants.findById(fixture.caller.getId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
            assertThat(persisted.getRevision()).isEqualTo(3);
            assertThat(persisted.getRowVersion()).isEqualTo(fixture.caller.getRowVersion());
            assertThat(persisted.getOwnerUserId()).isEqualTo(fixture.caller.getOwnerUserId());
            assertThat(fixture.count("platform_customer_entitlement_commands")).isEqualTo(1);
            assertThat(fixture.binding().status()).isEqualTo("CONFIGURED");
            assertThat(fixture.count("deployment_external_integration_snapshots")).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "REVOKE,customer", "INVITE,customer",
        "REVOKE,offer", "INVITE,offer",
        "REVOKE,no-snapshot", "INVITE,no-snapshot"
    })
    void actualEntitlementGuardUsesCallerCustomerOfferAndRequiresAnExternalSnapshot(String command, String unrelatedScope) throws Exception {
        try (var fixture = new EntitlementFixture()) {
            CustomerOfferEntitlementEntity caller = fixture.caller;
            if ("no-snapshot".equals(unrelatedScope)) fixture.persistRelease("release-generic", "PROVISIONING", "RUNNING");
            else {
                fixture.externalRelease("PROVISIONING");
                caller = fixture.seedGrant("other-entitlement", "customer".equals(unrelatedScope) ? "other-customer" : caller.getCustomerId(),
                    "offer".equals(unrelatedScope) ? "other-offer" : caller.getOfferId(), "other-owner");
                assertThat(fixture.grants.countUnfinishedExternalReleases(fixture.caller.getCustomerId(), fixture.caller.getOfferId())).isEqualTo(1);
            }
            assertThat(fixture.grants.countUnfinishedExternalReleases(caller.getCustomerId(), caller.getOfferId())).isZero();

            var result = fixture.mutate(command, caller);

            assertThat(result.revision()).isEqualTo(4);
            assertThat(result.status()).isEqualTo("REVOKE".equals(command) ? EntitlementStatus.REVOKED : EntitlementStatus.INVITED);
            assertThat(fixture.grants.findById(caller.getId()).orElseThrow().getRevision()).isEqualTo(4);
            assertThat(fixture.count("platform_customer_entitlement_commands")).isEqualTo(2);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"REVOKE", "INVITE"})
    void actualEntitlementMutationIsAllowedAfterTheExternalReleaseBecomesTerminal(String command) throws Exception {
        try (var fixture = new EntitlementFixture()) {
            fixture.externalRelease("APPLY_REQUESTED");
            assertThat(fixture.grants.countUnfinishedExternalReleases(fixture.caller.getCustomerId(), fixture.caller.getOfferId())).isEqualTo(1);
            fixture.jdbc.update("update platform_deployment_releases set status='FAILED',provisioning_status='FAILED' where id='release-generic'");
            assertThat(fixture.grants.countUnfinishedExternalReleases(fixture.caller.getCustomerId(), fixture.caller.getOfferId())).isZero();

            var result = fixture.mutate(command, fixture.caller);

            assertThat(result.revision()).isEqualTo(4);
            assertThat(result.status()).isEqualTo("REVOKE".equals(command) ? EntitlementStatus.REVOKED : EntitlementStatus.INVITED);
            assertThat(fixture.count("platform_customer_entitlement_commands")).isEqualTo(2);
            assertThat(fixture.count("deployment_external_integration_snapshots")).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"REVOKE", "INVITE"})
    void actualEntitlementMutationWaitsForApplyFreezeThenSeesItsCommittedSnapshot(String command) throws Exception {
        try (var fixture = new EntitlementFixture(); var workers = Executors.newFixedThreadPool(2)) {
            fixture.f.release();
            var applyLocked = new CountDownLatch(1); var finishApply = new CountDownLatch(1); var mutationAttempted = new CountDownLatch(1);
            var applyPid = new AtomicInteger(); var mutationPid = new AtomicInteger();
            var apply = workers.submit(() -> new TransactionTemplate(fixture.transactions).execute(tx -> {
                fixture.lockDeployment(); applyPid.set(fixture.jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                fixture.persistRelease("release-generic", "APPLY_REQUESTED", "QUEUED");
                var snapshot = fixture.resolver.freezeRelease(fixture.f.deployment.getId(), fixture.f.version.getId(),
                    fixture.f.target.getId(), "release-generic", "authority-fingerprint");
                applyLocked.countDown(); awaitSignal(finishApply); return snapshot;
            }));
            try {
                awaitSignal(applyLocked);
                assertThat(fixture.count("deployment_external_integration_snapshots")).isZero();
                var mutation = workers.submit(() -> {
                    try {
                        return new TransactionTemplate(fixture.transactions).execute(tx -> {
                            mutationPid.set(fixture.jdbc.queryForObject("select pg_backend_pid()", Integer.class)); mutationAttempted.countDown();
                            return fixture.mutate(command, fixture.caller);
                        });
                    } finally { SecurityContextHolder.clearContext(); }
                });
                awaitSignal(mutationAttempted); awaitBlocked(fixture.jdbc, mutationPid.get(), applyPid.get());
                finishApply.countDown();

                assertThat(apply.get(15, TimeUnit.SECONDS).projections()).hasSize(1);
                var failure = catchThrowableOfType(() -> mutation.get(15, TimeUnit.SECONDS), ExecutionException.class);
                assertThat(failure).isNotNull();
                assertThat(failure.getCause()).isInstanceOfSatisfying(CustomerSelfServiceException.class, denied -> {
                    assertThat(denied.code()).isEqualTo(CustomerSelfServiceException.Code.RELEASE_IN_PROGRESS);
                    assertThat(denied.status()).isEqualTo(HttpStatus.CONFLICT);
                });
                var persisted = fixture.grants.findById(fixture.caller.getId()).orElseThrow();
                assertThat(persisted.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
                assertThat(persisted.getRevision()).isEqualTo(3);
                assertThat(fixture.count("platform_customer_entitlement_commands")).isEqualTo(1);
                assertThat(fixture.count("deployment_external_integration_snapshots")).isEqualTo(1);
            } finally { finishApply.countDown(); }
        }
    }

    @Test void applyFreezeCommitMakesConcurrentRevocationWaitThenConflict() throws Exception {
        try (var fixture = new DatabaseFixture(); var workers = Executors.newFixedThreadPool(2)) {
            var binding = fixture.bind(); fixture.f.release();
            var applyLocked = new CountDownLatch(1); var finishApply = new CountDownLatch(1); var revokeAttempted = new CountDownLatch(1);
            var applyPid = new AtomicInteger(); var revokePid = new AtomicInteger();
            when(fixture.f.deployments.findByIdForUpdate(fixture.f.deployment.getId())).thenAnswer(call -> {
                revokePid.set(fixture.jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                revokeAttempted.countDown(); fixture.lockDeployment(); return Optional.of(fixture.f.deployment);
            });
            var apply = workers.submit(() -> new TransactionTemplate(fixture.transactions).execute(tx -> {
                fixture.lockDeployment(); applyPid.set(fixture.jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                fixture.persistRelease("release-generic", "APPLY_REQUESTED", "QUEUED");
                var snapshot = fixture.resolver.freezeRelease(fixture.f.deployment.getId(), fixture.f.version.getId(),
                    fixture.f.target.getId(), "release-generic", "authority-fingerprint");
                applyLocked.countDown(); awaitSignal(finishApply); return snapshot;
            }));
            try {
                awaitSignal(applyLocked);
                assertThat(fixture.count("platform_deployment_releases")).isZero();
                assertThat(fixture.count("deployment_external_integration_snapshots")).isZero();
                var revoke = workers.submit(() -> {
                    fixture.f.customer();
                    try { return fixture.service.revoke("offer-generic", fixture.f.deployment.getId(), fixture.f.revokeRequest(binding.id())); }
                    finally { SecurityContextHolder.clearContext(); }
                });
                awaitSignal(revokeAttempted); awaitBlocked(fixture.jdbc, revokePid.get(), applyPid.get());
                finishApply.countDown();

                assertThat(apply.get(15, TimeUnit.SECONDS).projections()).hasSize(1);
                var failure = catchThrowableOfType(() -> revoke.get(15, TimeUnit.SECONDS), ExecutionException.class);
                assertThat(failure).isNotNull(); assertReleaseInProgress(failure.getCause());
                assertThat(fixture.binding().status()).isEqualTo("CONFIGURED");
                assertThat(fixture.binding().revision()).isZero();
                assertThat(fixture.count("platform_customer_entitlement_commands")).isEqualTo(1);
                assertThat(fixture.count("platform_deployment_releases")).isEqualTo(1);
                assertThat(fixture.count("deployment_external_integration_snapshots")).isEqualTo(1);
            } finally { finishApply.countDown(); }
        }
    }

    @Test void revokeCommitMakesConcurrentApplyFreezeWaitThenRollBackItsQueuedRelease() throws Exception {
        try (var fixture = new DatabaseFixture(); var workers = Executors.newFixedThreadPool(2)) {
            var binding = fixture.bind(); fixture.f.release();
            var revokeLocked = new CountDownLatch(1); var finishRevoke = new CountDownLatch(1); var applyAttempted = new CountDownLatch(1);
            var applyPid = new AtomicInteger(); var revokePid = new AtomicInteger();
            doAnswer(call -> {
                revokePid.set(fixture.jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                revokeLocked.countDown(); awaitSignal(finishRevoke); return null;
            }).when(fixture.f.audit).record(eq("EXTERNAL_INTEGRATION_REVOKED"), anyString(), anyString(), anyMap());
            var revoke = workers.submit(() -> {
                fixture.f.customer();
                try { return fixture.service.revoke("offer-generic", fixture.f.deployment.getId(), fixture.f.revokeRequest(binding.id())); }
                finally { SecurityContextHolder.clearContext(); }
            });
            try {
                awaitSignal(revokeLocked);
                assertThat(fixture.binding().status()).isEqualTo("CONFIGURED");
                var apply = workers.submit(() -> new TransactionTemplate(fixture.transactions).execute(tx -> {
                    applyPid.set(fixture.jdbc.queryForObject("select pg_backend_pid()", Integer.class)); applyAttempted.countDown();
                    fixture.lockDeployment(); fixture.persistRelease("release-generic", "APPLY_REQUESTED", "QUEUED");
                    return fixture.resolver.freezeRelease(fixture.f.deployment.getId(), fixture.f.version.getId(),
                        fixture.f.target.getId(), "release-generic", "authority-fingerprint");
                }));
                awaitSignal(applyAttempted); awaitBlocked(fixture.jdbc, applyPid.get(), revokePid.get());
                finishRevoke.countDown();

                assertThat(revoke.get(15, TimeUnit.SECONDS).status()).isEqualTo("REVOKED_FUTURE_RESOLUTION_EXTERNAL_RETAINED");
                var failure = catchThrowableOfType(() -> apply.get(15, TimeUnit.SECONDS), ExecutionException.class);
                assertThat(failure).isNotNull();
                assertThat(failure.getCause()).isInstanceOfSatisfying(ResponseStatusException.class,
                    denied -> assertThat(denied.getReason()).isEqualTo("EXTERNAL_INTEGRATION_BINDING_STALE_OR_FOREIGN"));
                assertThat(fixture.binding().status()).isEqualTo("REVOKED");
                assertThat(fixture.binding().revision()).isEqualTo(1);
                assertThat(fixture.count("platform_customer_entitlement_commands")).isEqualTo(2);
                assertThat(fixture.count("platform_deployment_releases")).isZero();
                assertThat(fixture.count("deployment_external_integration_snapshots")).isZero();
                assertThat(fixture.count("platform_secrets")).isEqualTo(1);
            } finally { finishRevoke.countDown(); }
        }
    }

    private static void assertReleaseInProgress(Throwable failure) {
        assertThat(failure).isInstanceOfSatisfying(ResponseStatusException.class, denied -> {
            assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(denied.getReason()).isEqualTo("EXTERNAL_INTEGRATION_RELEASE_IN_PROGRESS");
        });
    }

    private static void awaitBlocked(JdbcTemplate jdbc, int waitingPid, int blockingPid) {
        await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.TRUE.equals(jdbc.queryForObject(
            "select ? = any(pg_blocking_pids(?))", Boolean.class, blockingPid, waitingPid)));
    }

    private static void awaitSignal(CountDownLatch signal) {
        try { if (!signal.await(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrent release test timed out"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }

    @Test void preEffectFenceIsNonblockingWhenAnotherTransactionOwnsDeployment() throws Exception {
        try (var fixture = new DatabaseFixture(); var workers = Executors.newSingleThreadExecutor()) {
            var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
            var holder = workers.submit(() -> new TransactionTemplate(fixture.transactions).execute(tx -> {
                fixture.jdbc.queryForList("select id from platform_deployments where id=? for update", fixture.f.deployment.getId());
                locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("lock test timeout"); }
                catch (InterruptedException error) { throw new IllegalStateException(error); }
                return true;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                assertThatThrownBy(() -> new TransactionTemplate(fixture.transactions).execute(tx -> {
                    fixture.store.fenceDeployment(fixture.f.deployment.getId()); return true;
                })).hasMessageContaining("DEPLOYMENT_BUSY");
            } finally { release.countDown(); }
            assertThat(holder.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.count("deployment_external_integration_bindings")).isZero();
        }
    }

    @Test void provisionEnvironmentOpensItsOwnRequiredJpaTransactionAndKeepsExactFrozenEvidence() throws Exception {
        try (var fixture = new DatabaseFixture()) {
            var approval = fixture.approve();
            fixture.service.bind("offer-generic", fixture.f.deployment.getId(), fixture.f.bindRequest(approval.id()));
            fixture.f.release(); fixture.persistRelease("release-generic", "APPLY_REQUESTED", "QUEUED");
            assertThatThrownBy(() -> fixture.resolver.requireForEffects(fixture.f.deployment.getId(), fixture.f.version.getId(), fixture.f.target.getId()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
            new TransactionTemplate(fixture.transactions).execute(tx -> fixture.resolver.freezeRelease(fixture.f.deployment.getId(),
                fixture.f.version.getId(), fixture.f.target.getId(), "release-generic", "authority-fingerprint"));
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var environment = fixture.resolver.releaseEnvironment(fixture.f.deployment.getId(), fixture.f.version.getId(),
                fixture.f.target.getId(), "release-generic", "authority-fingerprint");
            assertThat(environment.configEnvironment()).hasSize(1); assertThat(environment.secretEnvironment()).hasSize(1);
            assertThat(fixture.f.codec.write(environment)).doesNotContain("synthetic-customer-key");
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(fixture.count("deployment_external_integration_snapshots")).isEqualTo(1);
        }
    }

    /** Minimal predecessor schema for transaction tests, separate from the full-chain migration test above. */
    static class DatabaseFixture implements AutoCloseable {
        final ExternalIntegrationFixture f = new ExternalIntegrationFixture();
        final JdbcTemplate jdbc;
        final EntityManagerFactory emf;
        final JpaTransactionManager transactions;
        final ExternalIntegrationStore store;
        final ExternalIntegrationService service;
        final ExternalIntegrationResolver resolver;
        DatabaseFixture() throws Exception {
            String schema = "atomic_" + UUID.randomUUID().toString().replace("-", "");
            var admin = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
            admin.execute("create schema " + schema);
            var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl() + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
                POSTGRES.getUsername(), POSTGRES.getPassword());
            jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("create table platform_deployments(id varchar(64) primary key)");
            jdbc.execute("create table platform_deployment_versions(id varchar(64) primary key)");
            jdbc.execute("create table deployment_target_profiles(id varchar(64) primary key)");
            jdbc.execute("create table platform_deployment_releases(id varchar(64) primary key, deployment_id varchar(64) not null references platform_deployments(id), deployment_version_id varchar(64) not null references platform_deployment_versions(id), target_profile_id varchar(64) references deployment_target_profiles(id), status varchar(64), provisioning_status varchar(64) not null)");
            jdbc.execute("create table platform_customer_offers(id varchar(64) primary key, definition_json text not null, fingerprint varchar(64) not null, review_reference varchar(255) not null, reviewed_by varchar(255) not null, reviewed_at timestamptz not null)");
            jdbc.execute("create table platform_customer_offer_entitlements(id varchar(64) primary key, customer_id varchar(64) not null, offer_id varchar(64) not null references platform_customer_offers(id), owner_user_id varchar(64) not null, invitation_reference varchar(255) not null, status varchar(16) not null, revision bigint not null, row_version bigint not null, expires_at timestamptz not null, created_at timestamptz not null, updated_at timestamptz not null, unique(customer_id,offer_id))");
            jdbc.execute("create table platform_customer_offer_deployments(deployment_id varchar(64) primary key references platform_deployments(id), customer_id varchar(64) not null, offer_id varchar(64) not null references platform_customer_offers(id))");
            jdbc.execute("create table platform_secrets(name varchar(128) primary key, secret_value text not null, updated_at timestamptz not null, scope_type varchar(64), deployment_id varchar(64), secret_purpose varchar(128), owner_type varchar(64), managed_by_platform boolean not null, cleanup_policy varchar(64))");
            jdbc.execute("create table platform_customer_entitlement_commands(id varchar(64) primary key, entitlement_id varchar(64) not null, operation_key varchar(128) not null, request_fingerprint varchar(64) not null, command_type varchar(32) not null, actor_id varchar(64) not null, result_json text not null, created_at timestamptz not null, unique(entitlement_id,operation_key), constraint ck_customer_entitlement_command_type check(command_type in ('BIND_S3')))");
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V199__external_integration_bindings.sql")).execute(dataSource);
            jdbc.update("insert into platform_deployments values (?)", f.deployment.getId());
            jdbc.update("insert into platform_deployment_versions values (?)", f.version.getId());
            jdbc.update("insert into deployment_target_profiles values (?)", f.target.getId());
            var factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(dataSource);
            factory.setManagedTypes(PersistenceManagedTypes.of("com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity",
                "com.ai.fabric.platform.backend.customerselfservice.entity.CustomerEntitlementCommandEntity",
                "com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity",
                "com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity"));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter()); factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none",
                "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            factory.afterPropertiesSet(); emf = factory.getObject();
            transactions = new JpaTransactionManager(emf); transactions.setDataSource(dataSource);
            var repositories = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf));
            var secretRepository = repositories.getRepository(PlatformSecretRepository.class);
            var commandRepository = repositories.getRepository(CustomerEntitlementCommandRepository.class);
            var vault = new PlatformSecretService(secretRepository, f.audit, new MockEnvironment()
                .withProperty("PLATFORM_ARTIFACT_SIGNING_KEY", "synthetic-integration-signing-key"));
            store = new ExternalIntegrationStore(jdbc, f.codec);
            var integrity = new ExternalIntegrationIntegrity(f.codec, vault);
            // Exact owning-repository lock ordering, with authorization/owner lookup stubbed locally.
            when(f.deployments.findByIdForUpdate(f.deployment.getId())).thenAnswer(call -> {
                jdbc.queryForList("select id from platform_deployments where id=? for update", f.deployment.getId()); return java.util.Optional.of(f.deployment);
            });
            var target = new ExternalIntegrationService(f.entitlements, f.authorization, f.composition, commandRepository, f.deployments,
                f.versions, f.targets, f.policy, integrity, store, vault, secretRepository, f.codec, f.mapper, f.audit,
                Validation.buildDefaultValidatorFactory().getValidator());
            var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
            proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
            service = (ExternalIntegrationService) proxy.getProxy();
            var resolution = new ExternalIntegrationResolver(f.deployments, f.versions, f.targets, f.releases, f.associations,
                f.entitlements, f.composition, f.authority, store, f.policy, integrity, f.endpoints, f.compiler, f.codec, f.mapper);
            var resolutionProxy = new ProxyFactory(resolution); resolutionProxy.setProxyTargetClass(true);
            resolutionProxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
            resolver = (ExternalIntegrationResolver) resolutionProxy.getProxy();
        }
        Summary approve() { f.staff(); Summary result = service.approve("offer-generic", f.deployment.getId(), f.approvalRequest()); f.customer(); return result; }
        Summary bind() { return service.bind("offer-generic", f.deployment.getId(), f.bindRequest(approve().id())); }
        ExternalIntegrationStore.Row<Binding> binding() {
            return store.binding(f.deployment.getId(), f.version.getId(), f.target.getId(), f.profile.bindingSlotId()).orElseThrow();
        }
        void lockDeployment() { jdbc.queryForList("select id from platform_deployments where id=? for update", f.deployment.getId()); }
        void persistRelease(String id, String status, String provisioningStatus) {
            persistRelease(id, f.deployment.getId(), f.version.getId(), f.target.getId(), status, provisioningStatus);
        }
        void persistRelease(String id, String deploymentId, String versionId, String targetId, String status, String provisioningStatus) {
            jdbc.update("insert into platform_deployments values (?) on conflict do nothing", deploymentId);
            jdbc.update("insert into platform_deployment_versions values (?) on conflict do nothing", versionId);
            if (targetId != null) jdbc.update("insert into deployment_target_profiles values (?) on conflict do nothing", targetId);
            jdbc.update("insert into platform_deployment_releases(id,deployment_id,deployment_version_id,target_profile_id,status,provisioning_status) values (?,?,?,?,?,?)",
                id, deploymentId, versionId, targetId, status, provisioningStatus);
        }
        long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
        @Override public void close() { emf.close(); SecurityContextHolder.clearContext(); }
    }

    /** Real entitlement service/repository and snapshot join; identity eligibility remains a local boundary stub. */
    static class EntitlementFixture extends DatabaseFixture {
        final CustomerOfferEntitlementRepository grants;
        final CustomerOfferRepository offers;
        final CustomerOfferEntitlementService entitlementService;
        final PlatformCustomerRepository customers = mock(PlatformCustomerRepository.class);
        final PlatformUserRepository users = mock(PlatformUserRepository.class);
        final CustomerOfferEntitlementEntity caller;

        EntitlementFixture() throws Exception {
            var repositories = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf));
            grants = repositories.getRepository(CustomerOfferEntitlementRepository.class);
            offers = repositories.getRepository(CustomerOfferRepository.class);
            var target = new CustomerOfferEntitlementService(offers, grants,
                repositories.getRepository(CustomerEntitlementCommandRepository.class), mock(PlatformCustomerAccessService.class),
                customers, users, f.codec, f.composition, mock(CustomerTargetPlacementService.class), f.targets, f.audit);
            var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
            proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
            entitlementService = (CustomerOfferEntitlementService) proxy.getProxy();
            caller = seedGrant("entitlement-generic", f.deployment.getCustomerId(), "offer-generic", "owner");
            bind();
            jdbc.update("insert into platform_customer_offer_deployments(deployment_id,customer_id,offer_id) values (?,?,?)",
                f.deployment.getId(), caller.getCustomerId(), caller.getOfferId());
            when(f.entitlements.requireEntitlement(f.deployment.getCustomerId(), "offer-generic", null, true)).thenAnswer(call ->
                entitlementService.requireEntitlement(f.deployment.getCustomerId(), "offer-generic", null, true));
        }

        CustomerOfferEntitlementEntity seedGrant(String id, String customerId, String offerId, String ownerId) {
            var customer = new PlatformCustomerEntity(); customer.setId(customerId); customer.setStatus("ACTIVE");
            when(customers.findById(customerId)).thenReturn(Optional.of(customer));
            for (String userId : List.of(ownerId, ownerId + "-replacement")) {
                var user = new PlatformUserEntity(); user.setId(userId); user.setCustomerId(customerId);
                user.setRole("CUSTOMER_ADMIN"); user.setStatus("ACTIVE");
                when(users.findById(userId)).thenReturn(Optional.of(user));
            }
            return new TransactionTemplate(transactions).execute(tx -> {
                if (!offers.existsById(offerId)) offers.saveAndFlush(new CustomerOfferEntity(offerId, f.codec.write(f.definition),
                    f.codec.hash(f.definition), "review", "staff", Instant.now()));
                var grant = new CustomerOfferEntitlementEntity(); grant.setId(id); grant.setCustomerId(customerId); grant.setOfferId(offerId);
                grant.setOwnerUserId(ownerId); grant.setInvitationReference("synthetic-invitation"); grant.setStatus(EntitlementStatus.ACTIVE);
                grant.setRevision(3); grant.setExpiresAt(Instant.now().plusSeconds(3600));
                grant.setCreatedAt(Instant.now()); grant.setUpdatedAt(Instant.now()); return grants.saveAndFlush(grant);
            });
        }

        void externalRelease(String status) {
            f.release(); persistRelease("release-generic", status, "PROVISIONING".equals(status) ? "RUNNING" : "QUEUED");
            new TransactionTemplate(transactions).execute(tx -> resolver.freezeRelease(f.deployment.getId(), f.version.getId(),
                f.target.getId(), "release-generic", "authority-fingerprint"));
        }

        EntitlementSummary mutate(String command, CustomerOfferEntitlementEntity grant) {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new PlatformPrincipal("staff", PlatformRole.PLATFORM_ADMIN, "staff", "API_KEY"), "",
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));
            if ("REVOKE".equals(command)) return entitlementService.revoke(grant.getId(),
                new EntitlementCommand(grant.getRevision(), "staff-revoke", "synthetic revoke"));
            return entitlementService.invite(grant.getOfferId(), new InviteRequest(grant.getCustomerId(),
                grant.getOwnerUserId() + "-replacement", "replacement-invite", Instant.now().plusSeconds(7200),
                grant.getRevision(), "staff-reinvite", "synthetic reinvite"));
        }
    }
}
