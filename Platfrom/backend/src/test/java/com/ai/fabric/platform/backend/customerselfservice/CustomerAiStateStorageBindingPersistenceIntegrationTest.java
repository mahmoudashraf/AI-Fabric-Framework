package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.aiworkspace.service.*;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.entity.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAiStateStorageBindingContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAiStateStorageBindingContracts.Result;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.*;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyArtifactPolicy;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.AiStateStorageMode;
import com.ai.fabric.platform.backend.deployment.model.DeploymentAiStateStorageContracts;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateService;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationEndpointPolicy;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationPolicy;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real production migrations, repositories and service proxies; only remote PostgreSQL probing is mocked. */
@DataJpaTest(properties = {
    "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration", "spring.flyway.target=200",
    "spring.jpa.hibernate.ddl-auto=none", "spring.datasource.hikari.maximum-pool-size=6"
}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import({CustomerAiStateStorageBindingService.class, CustomerOfferEntitlementService.class,
    CustomerSelfServiceAuthorizationService.class, CustomerReviewedCompositionService.class,
    CustomerWorkspaceProfileValidator.class, CustomerOfferCodec.class, DeploymentAiStateStorageBindingService.class,
    PlatformSecretService.class, PlatformAuditService.class, PlatformCustomerAccessService.class,
    DeploymentAccessService.class, TargetRetirementGateService.class, ManagedDependencyArtifactPolicy.class,
    DeploymentSourceCapabilityManifestService.class, ExternalIntegrationPolicy.class, ExternalIntegrationEndpointPolicy.class,
    AIWorkspaceConfigurationValidator.class, AIWorkspaceAssetCatalogService.class, AIWorkspaceExperiencePackRegistry.class,
    AIWorkspaceConnectionProfileRegistry.class, AIWorkspaceAuthenticationProfileRegistry.class,
    CustomerAiStateStorageBindingPersistenceIntegrationTest.Configuration.class})
class CustomerAiStateStorageBindingPersistenceIntegrationTest {
    private static final String SLOT = "ai-state-database";
    private static final String TEMPLATE = "ai-state-persistence-fixture";
    private static final String HOST = "sql.ai-state.test.invalid";
    private static final String PASSWORD = "synthetic-database-password";
    private static final String ROLE = "ai_runtime_role";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ai_state_wrapper_regression");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired CustomerAiStateStorageBindingService service;
    @Autowired CustomerReviewedCompositionService composition;
    @Autowired CustomerOfferCodec codec;
    @Autowired CustomerOfferRepository offers;
    @Autowired CustomerOfferEntitlementRepository grants;
    @Autowired CustomerOfferDeploymentRepository associations;
    @Autowired CustomerEntitlementCommandRepository commands;
    @Autowired CustomerPlacementEvidenceRepository placements;
    @Autowired PlatformCustomerRepository customers;
    @Autowired PlatformTenantRepository tenants;
    @Autowired PlatformUserRepository users;
    @Autowired DeploymentRepository deployments;
    @Autowired DeploymentDraftRepository drafts;
    @Autowired DeploymentVersionRepository versions;
    @Autowired DeploymentReleaseRepository releases;
    @Autowired DeploymentTargetProfileRepository profiles;
    @Autowired DeploymentProviderCredentialRepository providerCredentials;
    @Autowired DeploymentSourceArtifactRepository artifacts;
    @Autowired MarketplacePluginRepository plugins;
    @Autowired MarketplacePluginVersionRepository pluginVersions;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @SpyBean PlatformSecretService secrets;
    @SpyBean DeploymentAiStateStorageBindingService binder;
    @MockBean DeploymentAiStateStorageProbe probe;

    private String customerId, tenantId, ownerId, email, offerId, grantId, deploymentId;
    private String targetId, productionTargetId, credentialId, pluginId, pluginVersionId, versionId, unreviewedVersionId;
    private String signingSecretName, databaseName;
    private Definition definition;

    @BeforeEach
    void seedCommittedScopeWithoutAnAmbientCommandTransaction() {
        assertNoAmbientTransaction();
        String suffix = UUID.randomUUID().toString();
        customerId = "as-customer-" + suffix; tenantId = "as-tenant-" + suffix;
        ownerId = "as-owner-" + suffix; email = suffix + "@state.test.invalid";
        offerId = "as-offer-" + suffix; grantId = "as-grant-" + suffix;
        deploymentId = "as-dep-" + suffix; targetId = "as-stage-" + suffix;
        credentialId = "as-credential-" + suffix;
        productionTargetId = "as-prod-" + suffix; pluginId = "as-template-" + suffix;
        pluginVersionId = "as-plugin-" + suffix; versionId = "as-version-" + suffix;
        unreviewedVersionId = "as-other-" + suffix;
        databaseName = "fixture_ai_state_" + suffix.replace("-", "");
        signingSecretName = "MANAGED_AI_STATE_REQUEST_SIGNING_" + codec.hash(Map.of("deploymentId", deploymentId))
            .toUpperCase(java.util.Locale.ROOT);
        inSetupTransaction(this::seed);
        authenticate();
        clearInvocations(secrets, binder, probe);
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            return null;
        }).when(probe).verify(any());
    }

    @AfterEach
    void clearSecurityAndAssertTheServiceDidNotLeaveATransactionOpen() {
        SecurityContextHolder.clearContext();
        assertNoAmbientTransaction();
    }

    @Test
    void getOpensARealWritableTransactionAndReadsTheCommittedRetainedBinding() {
        assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL");
        assertThat(jdbc.queryForObject("select version from flyway_schema_history where success order by installed_rank desc limit 1", String.class))
            .isEqualTo("200");
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        var result = service.bind(offerId, deploymentId, request("bind-and-get", PASSWORD));
        assertCommittedBinding(result, "bind-and-get");
        AtomicBoolean observed = new AtomicBoolean();
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            assertThat(jdbc.queryForObject("show transaction_read_only", String.class)).isEqualTo("off");
            observed.set(true);
            return call.callRealMethod();
        }).when(binder).get(deploymentId, targetId, versionId);

        assertNoAmbientTransaction();
        var current = service.get(offerId, deploymentId, targetId, SLOT, versionId);
        assertNoAmbientTransaction();

        assertThat(observed).isTrue();
        assertThat(current).isEqualTo(result.binding());
        verify(probe, times(1)).verify(any());
        assertThat(codec.write(current)).doesNotContain(HOST, PASSWORD, ROLE);
    }

    @Test
    void publishedButUnreviewedVersionIsRejectedBeforeVaultBinderAndProbeForPostAndGet() {
        var before = persistedState();
        var request = request("bad-version", PASSWORD);
        var invalid = new Request(2, request.operationKey(), targetId, SLOT, connection(unreviewedVersionId, PASSWORD));

        assertCode(REVIEWED_COMPOSITION_CHANGED, () -> service.bind(offerId, deploymentId, invalid));
        assertCode(REVIEWED_COMPOSITION_CHANGED, () -> service.get(offerId, deploymentId, targetId, SLOT, unreviewedVersionId));

        verifyNoInteractions(secrets, binder, probe);
        assertThat(persistedState()).isEqualTo(before);
    }

    @Test
    void reviewedVersionIsRecheckedBeforeReplayingAnExistingCommand() {
        service.bind(offerId, deploymentId, request("reviewed-replay", PASSWORD));
        inSetupTransaction(() -> jdbc.update("update platform_deployment_versions set composition_provenance_json=? where id=?",
            provenance(false), versionId));
        var before = persistedState();
        clearInvocations(secrets, binder, probe);

        assertCode(REVIEWED_COMPOSITION_CHANGED,
            () -> service.bind(offerId, deploymentId, request("reviewed-replay", PASSWORD)));
        assertCode(REVIEWED_COMPOSITION_CHANGED,
            () -> service.get(offerId, deploymentId, targetId, SLOT, versionId));

        verifyNoInteractions(secrets, binder, probe);
        assertThat(persistedState()).isEqualTo(before);
    }

    @Test
    void freshServiceProxyReplaysThePersistedCommandAndHmacWithoutProbingOrWriting() throws Exception {
        var request = request("persisted-command", PASSWORD);
        Result first = service.bind(offerId, deploymentId, request);
        assertCommittedBinding(first, request.operationKey());
        assertThat(command(request.operationKey()).getRequestFingerprint()).isEqualTo(privateHmac(request));
        inSetupTransaction(() -> {
            var grant = grants.findByIdForUpdate(grantId).orElseThrow();
            grant.setRevision(3); grants.saveAndFlush(grant);
        });
        var before = persistedState();
        var fresh = context.getAutowireCapableBeanFactory().createBean(CustomerAiStateStorageBindingService.class);
        assertThat(fresh).isNotSameAs(service);
        assertThat(AopUtils.isAopProxy(fresh)).isTrue();
        clearInvocations(secrets, binder, probe);

        assertNoAmbientTransaction();
        assertThat(fresh.bind(offerId, deploymentId, request)).isEqualTo(first);
        assertNoAmbientTransaction();
        assertCode(OPERATION_KEY_CONFLICT, () -> fresh.bind(offerId, deploymentId, request("persisted-command", "changed-password")));

        verifyNoInteractions(binder, probe);
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), anyMap());
        assertThat(persistedState()).isEqualTo(before);
    }

    @Test
    void reviewedCandidateGetsANewCredentialGenerationWithoutChangingTheActiveVersionOrItsCommand() throws Exception {
        Result original = service.bind(offerId, deploymentId, request("original-generation", PASSWORD));
        String candidateId = persistSyntheticActiveReleaseAndReviewedCandidate();
        var before = persistedState();
        String candidatePassword = "synthetic-candidate-password";
        Request candidateRequest = request("candidate-generation", candidateId, candidatePassword);

        Result candidate = service.bind(offerId, deploymentId, candidateRequest);

        assertThat(candidate.binding().bindingId()).isEqualTo(original.binding().bindingId());
        assertThat(candidate.binding().versionId()).isEqualTo(candidateId);
        assertThat(candidate.binding().fingerprint()).isNotEqualTo(original.binding().fingerprint());
        assertThat(command(candidateRequest.operationKey()).getRequestFingerprint()).isEqualTo(privateHmac(candidateRequest));
        assertThat(codec.write(candidate)).doesNotContain(HOST, ROLE, PASSWORD, candidatePassword);
        assertThat(persistedState().get("handles")).isEqualTo(before.get("handles"));
        assertThat(persistedState().get("claims")).isEqualTo(before.get("claims"));
        assertThat(persistedState().get("releases")).isEqualTo(before.get("releases"));
        assertThat(approvalRows()).hasSize(2).containsAll(before.get("approvals"));
        assertThat(generationRows()).hasSize(2).containsAll(before.get("generations"));
        assertThat(ownedRows("platform_secrets", "deployment_id", deploymentId))
            .hasSize(5).containsAll(before.get("vault"));
        assertThat(ownedRows("platform_customer_entitlement_commands", "entitlement_id", grantId))
            .hasSize(2).containsAll(before.get("commands"));
        assertThat(generationRows()).allSatisfy(row -> assertThat(row.get("generation_json").toString())
            .doesNotContain(HOST, ROLE, PASSWORD, candidatePassword));
        assertThat(auditRows()).allSatisfy(row -> assertThat(row.get("details_json").toString())
            .doesNotContain(HOST, ROLE, PASSWORD, candidatePassword));
        assertThat(resolveLocal(versionId).password()).isEqualTo(PASSWORD);
        assertThat(resolveLocal(candidateId).password()).isEqualTo(candidatePassword);

        var staged = persistedState();
        assertThat(service.get(offerId, deploymentId, targetId, SLOT, versionId)).isEqualTo(original.binding());
        assertThat(service.get(offerId, deploymentId, targetId, SLOT, candidateId)).isEqualTo(candidate.binding());
        assertThat(service.bind(offerId, deploymentId, request("original-generation", PASSWORD))).isEqualTo(original);
        assertThat(service.bind(offerId, deploymentId, candidateRequest)).isEqualTo(candidate);
        assertThatThrownBy(() -> service.bind(offerId, deploymentId,
            request("changed-version-credentials", candidateId, "synthetic-unapproved-password")))
            .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(failure.getReason()).isEqualTo("AI_STATE_STORAGE_VERSION_CREDENTIALS_IMMUTABLE");
                assertThat(failure.getMessage()).doesNotContain(HOST, candidatePassword, "synthetic-unapproved-password");
            });
        assertThat(persistedState()).isEqualTo(staged);
        verify(probe, times(2)).verify(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"command", "audit"})
    void failedCandidateCommandRollsBackItsGenerationButPreservesActiveCredentialsAndReplay(String failurePoint) throws Exception {
        Result original = service.bind(offerId, deploymentId, request("active-generation", PASSWORD));
        String candidateId = persistSyntheticActiveReleaseAndReviewedCandidate();
        Request candidateRequest = request("rejected-candidate", candidateId, "synthetic-candidate-password");
        var before = persistedState();
        String constraint = "ai_state_candidate_reject_" + UUID.randomUUID().toString().replace("-", "");
        String table = "command".equals(failurePoint) ? "platform_customer_entitlement_commands" : "platform_audit_events";
        String originalAuditId = jdbc.queryForObject("select id from platform_audit_events where target_id=?"
            + " and action='CUSTOMER_AI_STATE_STORAGE_BOUND'", String.class, deploymentId);
        String check = "command".equals(failurePoint)
            ? "entitlement_id <> '" + grantId + "' or operation_key <> 'rejected-candidate'"
            : "target_id <> '" + deploymentId + "' or action <> 'CUSTOMER_AI_STATE_STORAGE_BOUND' or id='" + originalAuditId + "'";
        jdbc.execute("alter table " + table + " add constraint " + constraint + " check (" + check + ")");
        AtomicBoolean generationWritesObserved = new AtomicBoolean();
        doAnswer(call -> {
            var result = call.callRealMethod();
            entityManager.flush();
            assertThat(generationRows()).hasSize(2).containsAll(before.get("generations"));
            assertThat(approvalRows()).hasSize(2).containsAll(before.get("approvals"));
            assertThat(ownedRows("platform_secrets", "deployment_id", deploymentId))
                .hasSize(5).containsAll(before.get("vault"));
            generationWritesObserved.set(true);
            return result;
        }).when(binder).bind(eq(deploymentId), eq(targetId), any());
        try {
            assertThatThrownBy(() -> service.bind(offerId, deploymentId, candidateRequest))
                .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(generationWritesObserved).isTrue();
            assertThat(persistedState()).isEqualTo(before);
            assertThat(service.bind(offerId, deploymentId, request("active-generation", PASSWORD))).isEqualTo(original);
            assertThat(service.get(offerId, deploymentId, targetId, SLOT, versionId)).isEqualTo(original.binding());
            assertThat(resolveLocal(versionId).password()).isEqualTo(PASSWORD);
        } finally {
            jdbc.execute("alter table " + table + " drop constraint " + constraint);
        }

        Result retried = service.bind(offerId, deploymentId, candidateRequest);
        assertThat(retried.binding().bindingId()).isEqualTo(original.binding().bindingId());
        assertThat(retried.binding().fingerprint()).isNotEqualTo(original.binding().fingerprint());
        assertThat(command(candidateRequest.operationKey()).getRequestFingerprint()).isEqualTo(privateHmac(candidateRequest));
        assertThat(service.bind(offerId, deploymentId, candidateRequest)).isEqualTo(retried);
        assertThat(generationRows()).hasSize(2).containsAll(before.get("generations"));
        assertThat(resolveLocal(versionId).password()).isEqualTo(PASSWORD);
        assertThat(resolveLocal(candidateId).password()).isEqualTo(candidateRequest.connection().password());
        assertThat(persistedState().get("releases")).isEqualTo(before.get("releases"));
        verify(probe, times(3)).verify(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"revoked", "expired", "different-owner", "foreign-customer", "api-key"})
    void currentAuthorityIsRequiredForNewBindingReplayAndGet(String denial) {
        service.bind(offerId, deploymentId, request("existing", PASSWORD));
        CustomerSelfServiceException.Code code = revokeAuthority(denial);
        var before = persistedState();
        clearInvocations(secrets, binder, probe);

        assertCode(code, () -> service.bind(offerId, deploymentId, request("existing", PASSWORD)));
        assertCode(code, () -> service.bind(offerId, deploymentId, request("new", PASSWORD)));
        assertCode(code, () -> service.get(offerId, deploymentId, targetId, SLOT, versionId));

        verifyNoInteractions(secrets, binder, probe);
        assertThat(persistedState()).isEqualTo(before);
    }

    @Test
    void probeFailureRollsBackTheCommandSigningSecretAuditAndRetirementGate() {
        AtomicBoolean writesObserved = new AtomicBoolean();
        doAnswer(call -> {
            entityManager.flush();
            assertThat(rowCount("platform_secrets", "deployment_id", deploymentId)).isEqualTo(1);
            assertThat(rowCount("platform_target_retirement_gates", "deployment_id", deploymentId)).isEqualTo(1);
            writesObserved.set(true);
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "SYNTHETIC_SQL_PROBE_REJECTED");
        }).when(probe).verify(any());
        var before = persistedState();

        assertThatThrownBy(() -> service.bind(offerId, deploymentId, request("probe-rejected", PASSWORD)))
            .hasMessageContaining("SYNTHETIC_SQL_PROBE_REJECTED");

        assertThat(writesObserved).isTrue();
        assertThat(persistedState()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"command", "audit"})
    void latePersistenceFailureRollsBackRealBindingClaimsApprovalVaultAndAuditThenTheSameKeyCanRetry(String failurePoint) {
        String constraint = "ai_state_command_reject_" + UUID.randomUUID().toString().replace("-", "");
        String table = "command".equals(failurePoint) ? "platform_customer_entitlement_commands" : "platform_audit_events";
        String check = "command".equals(failurePoint)
            ? "entitlement_id <> '" + grantId + "' or operation_key <> 'late-reject'"
            : "target_id <> '" + deploymentId + "' or action <> 'CUSTOMER_AI_STATE_STORAGE_BOUND'";
        jdbc.execute("alter table " + table + " add constraint " + constraint + " check (" + check + ")");
        AtomicBoolean bindingWritesObserved = new AtomicBoolean();
        doAnswer(call -> {
            var result = call.callRealMethod();
            entityManager.flush();
            assertThat(rowCount("deployment_provider_resource_handles", "deployment_id", deploymentId)).isEqualTo(1);
            assertThat(rowCount("deployment_ai_state_schema_claims", "deployment_id", deploymentId)).isEqualTo(2);
            assertThat(rowCount("platform_secrets", "deployment_id", deploymentId)).isEqualTo(3);
            assertThat(approvalRows()).hasSize(1);
            assertThat(generationRows()).hasSize(1);
            bindingWritesObserved.set(true);
            return result;
        }).when(binder).bind(eq(deploymentId), eq(targetId), any());
        var before = persistedState();
        try {
            assertThatThrownBy(() -> service.bind(offerId, deploymentId, request("late-reject", PASSWORD)))
                .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(bindingWritesObserved).isTrue();
            assertThat(persistedState()).isEqualTo(before);
        } finally {
            jdbc.execute("alter table " + table + " drop constraint " + constraint);
        }

        var retried = service.bind(offerId, deploymentId, request("late-reject", PASSWORD));
        assertCommittedBinding(retried, "late-reject");
        assertThat(service.bind(offerId, deploymentId, request("late-reject", PASSWORD))).isEqualTo(retried);
    }

    @Test
    void concurrentIdenticalCommandsSerializeOnRealPostgresAndCommitOneBindingAndReceipt() throws Exception {
        CountDownLatch firstAtProbe = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        doAnswer(call -> {
            firstAtProbe.countDown();
            assertThat(releaseFirst.await(15, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(probe).verify(any());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> bindOnWorker("concurrent", null));
            try {
                assertThat(firstAtProbe.await(15, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> bindOnWorker("concurrent", secondStarted));
                assertThat(secondStarted.await(15, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                releaseFirst.countDown();
                var committed = first.get(30, TimeUnit.SECONDS);
                assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(committed);
                assertCommittedBinding(committed, "concurrent");
                verify(probe, times(1)).verify(any());
            } finally {
                releaseFirst.countDown();
            }
        }
    }

    private void seed() {
        Instant now = Instant.now();
        persistCustomer(customerId); persistOwner(ownerId, email, customerId);
        var tenant = new PlatformTenantEntity(); tenant.setId(tenantId); tenant.setCustomerId(customerId);
        tenant.setName("AI-state regression tenant"); tenant.setSlug(tenantId); tenant.setStatus("ACTIVE");
        tenant.setCreatedAt(now); tenant.setUpdatedAt(now); tenants.saveAndFlush(tenant);
        var plugin = new MarketplacePluginEntity(); plugin.setId(pluginId); plugin.setSlug(pluginId);
        plugin.setDisplayName("AI-state regression template"); plugin.setPluginType("TEMPLATE"); plugin.setStatus("ACTIVE");
        plugin.setPublisherSlug("fixture"); plugin.setPublisherDisplayName("Fixture");
        plugin.setShortDescription("Isolated persistence regression"); plugin.setCreatedAt(now); plugin.setUpdatedAt(now);
        plugins.saveAndFlush(plugin);
        var pluginVersion = new MarketplacePluginVersionEntity(); pluginVersion.setId(pluginVersionId);
        pluginVersion.setPluginId(pluginId); pluginVersion.setVersion("1.0.0"); pluginVersion.setReleaseChannel("STABLE");
        pluginVersion.setStatus("PUBLISHED"); pluginVersion.setManifestJson("""
            {"schemaVersion":1,"pluginType":"TEMPLATE","contributions":{"template":{"templateId":"ai-state-persistence-fixture","requiredPluginRefs":[]}}}
            """);
        pluginVersion.setCreatedAt(now); pluginVersion.setPublishedAt(now); pluginVersions.saveAndFlush(pluginVersion);
        var artifact = new DeploymentSourceArtifactEntity(); artifact.setId("as-artifact-" + UUID.randomUUID());
        artifact.setServiceName("runtime"); artifact.setArtifactType("DOCKER_IMAGE"); artifact.setArtifactRole("RUNTIME");
        artifact.setImageRepository("registry.test.invalid/ai-state-runtime"); artifact.setImageDigest("sha256:" + "a".repeat(64));
        artifact.setCapabilityManifestJson("{}"); artifact.setCapabilityManifestHash(codec.hash(codec.tree("{}")));
        artifact.setCreatedAt(now); artifact.setPromotedAt(now); artifacts.saveAndFlush(artifact);
        var credential = new DeploymentProviderCredentialEntity(); credential.setId(credentialId);
        credential.setName("Synthetic provider credential metadata");
        credential.setProviderType(com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType.COOLIFY);
        credential.setSecretRef("UNUSED_SYNTHETIC_PROVIDER_SECRET"); credential.setStatus("ACTIVE");
        credential.setCreatedAt(now); credential.setUpdatedAt(now); providerCredentials.saveAndFlush(credential);
        var staging = persistProfile(targetId, "state-stage-server");
        var production = persistProfile(productionTargetId, "state-prod-server");
        var topology = new ReviewedTopology(1, 500, 512, 0, 0, 1, 0, 1);
        definition = new Definition("1", "AI-state persistence offer", TEMPLATE,
            new PluginPin(pluginId, pluginVersionId, codec.hash(codec.tree(pluginVersion.getManifestJson()))), List.of(),
            List.of(new ArtifactPin(artifact.getId(), composition.artifactFingerprint(artifact))),
            List.of(new TargetPermission(targetId, TargetIntent.CUSTOMER_STAGING, composition.profileFingerprint(staging)),
                new TargetPermission(productionTargetId, TargetIntent.CUSTOMER_PRODUCTION, composition.profileFingerprint(production))),
            Set.of(Operation.READ, Operation.BINDING_REPLACE),
            List.of(new SettingRule(Setting.AI_STATE_STORAGE_MODE, ValueType.CHOICE, 64, Set.of("CUSTOMER_POSTGRES"))),
            List.of(new BindingSlot(SLOT, BindingKind.CUSTOMER_AI_STATE, Set.of("CUSTOMER_POSTGRES"))),
            Set.of(), EnumSet.allOf(Prerequisite.class), CustomerOfferTestFixtures.definition().limits(), topology,
            "synthetic-reviewed", "synthetic-operations", "synthetic-identity", "synthetic-storage", List.of(),
            Map.of(AiStateStorageMode.CUSTOMER_POSTGRES, topology));
        composition.validate(definition);
        String offerFingerprint = codec.hash(definition);
        offers.saveAndFlush(new CustomerOfferEntity(offerId, codec.write(definition), offerFingerprint,
            "synthetic-review", "fixture-admin", now));
        var grant = new CustomerOfferEntitlementEntity(); grant.setId(grantId); grant.setCustomerId(customerId);
        grant.setOfferId(offerId); grant.setOwnerUserId(ownerId); grant.setStatus(EntitlementStatus.ACTIVE);
        grant.setRevision(2); grant.setInvitationReference("synthetic-accepted-invitation");
        grant.setExpiresAt(now.plusSeconds(3600)); grant.setCreatedAt(now); grant.setUpdatedAt(now); grants.saveAndFlush(grant);
        placements.saveAndFlush(new CustomerPlacementEvidenceEntity("as-placement-" + UUID.randomUUID(), targetId,
            TargetIntent.CUSTOMER_STAGING, composition.profileFingerprint(staging), "state-stage-server", "192.0.2.10",
            "destination", now.minusSeconds(1), now.plusSeconds(300)));
        var deployment = new DeploymentEntity(); deployment.setId(deploymentId); deployment.setName("AI-state wrapper test");
        deployment.setEnvironmentName("staging"); deployment.setTemplateId(TEMPLATE); deployment.setBehaviorType("CONVERSATIONAL");
        deployment.setStatus("DRAFT"); deployment.setCustomerId(customerId); deployment.setTenantId(tenantId);
        deployment.setCreatedAt(now); deployment.setUpdatedAt(now); deployments.saveAndFlush(deployment);
        var draft = new DeploymentDraftEntity(); draft.setId("as-draft-" + UUID.randomUUID());
        draft.setDeploymentId(deploymentId); draft.setRevisionNumber(1); draft.setStatus("DRAFT");
        draft.setActionsConfigJson("{}"); draft.setEntityConfigJson("{}"); draft.setRoutingConfigJson("{}");
        draft.setProviderConfigJson("{\"aiStateStorageMode\":\"CUSTOMER_POSTGRES\"}");
        draft.setSecurityConfigJson("{}"); draft.setPromptConfigJson("{}"); draft.setCreatedAt(now); draft.setUpdatedAt(now);
        drafts.saveAndFlush(draft);
        persistVersion(versionId, draft.getId(), true); persistVersion(unreviewedVersionId, draft.getId(), false);
        associations.saveAndFlush(new CustomerOfferDeploymentEntity(deploymentId, customerId, tenantId, offerId, targetId,
            offerFingerprint, 2, "synthetic-bootstrap", now));
    }

    private DeploymentTargetProfileEntity persistProfile(String id, String server) {
        var profile = CustomerOfferTestFixtures.profile(id, server); profile.setName(id);
        profile.setCredentialRefId(credentialId);
        profile.setNetworkPolicyJson("{\"aiStateStorageAllowedHosts\":[\"" + HOST + "\"]}");
        profile.setCreatedAt(Instant.now()); profile.setUpdatedAt(Instant.now());
        return profiles.saveAndFlush(profile);
    }

    private void persistVersion(String id, String draftId, boolean reviewed) {
        var version = new DeploymentVersionEntity(); version.setId(id); version.setDeploymentId(deploymentId);
        version.setSourceDraftId(draftId); version.setVersionLabel(id); version.setStatus("PUBLISHED");
        version.setConfigHash(codec.hash(Map.of("fixtureVersion", id))); version.setActionsConfigJson("{}");
        version.setEntityConfigJson("{}"); version.setRoutingConfigJson("{}"); version.setSecurityConfigJson("{}");
        version.setPromptConfigJson("{}"); version.setProviderConfigJson("{\"aiStateStorageMode\":\"CUSTOMER_POSTGRES\"}");
        version.setCompositionProvenanceJson(provenance(reviewed)); version.setActionsArtifactYaml("actions: []");
        version.setEntityArtifactYaml("entities: []"); version.setRoutingArtifactYaml("routes: []");
        version.setManifestJson("{}"); version.setPublishedAt(Instant.now()); versions.saveAndFlush(version);
    }

    private String persistSyntheticActiveReleaseAndReviewedCandidate() {
        String candidateId = "as-candidate-" + UUID.randomUUID();
        inSetupTransaction(() -> {
            var deployment = deployments.findById(deploymentId).orElseThrow();
            var original = versions.findById(versionId).orElseThrow();
            deployment.setActiveVersionId(versionId); deployments.saveAndFlush(deployment);
            var release = new DeploymentReleaseEntity(); release.setId("as-release-" + UUID.randomUUID());
            release.setDeploymentId(deploymentId); release.setDeploymentVersionId(versionId);
            release.setTargetProfileId(targetId); release.setProviderType(profiles.findById(targetId).orElseThrow().getProviderType());
            release.setStatus("ACTIVE"); release.setVerificationStatus("PASSED"); release.setProvisioningStatus("ACTIVE");
            release.setProvisioningTarget("COOLIFY"); release.setProvisioningDetailsJson("{}");
            release.setCreatedAt(Instant.now()); release.setAppliedAt(Instant.now()); release.setUpdatedAt(Instant.now());
            binder.freeze(deployment, original, release);
            assertThat(release.getAiStateStorageSnapshotJson()).contains("credentialGenerationId").doesNotContain(HOST, PASSWORD, ROLE);
            releases.saveAndFlush(release);
            persistVersion(candidateId, original.getSourceDraftId(), true);
        });
        return candidateId;
    }

    private CoolifyRuntimeDatabaseBinding resolveLocal(String id) {
        return binder.resolveLocal(deployments.findById(deploymentId).orElseThrow(), versions.findById(id).orElseThrow(),
            profiles.findById(targetId).orElseThrow());
    }

    private String provenance(boolean reviewed) {
        ObjectNode provenance = new ObjectMapper().createObjectNode().put("templateId", TEMPLATE);
        provenance.putArray("marketplaceInstalls").addObject().put("pluginId", pluginId)
            .put("pluginVersionId", reviewed ? pluginVersionId : "not-the-reviewed-version")
            .put("manifestSha256", "sha256:" + definition.template().manifestSha256());
        return provenance.toString();
    }

    private void persistCustomer(String id) {
        var customer = new PlatformCustomerEntity(); customer.setId(id); customer.setName("AI-state test customer");
        customer.setSlug(id); customer.setStatus("ACTIVE"); customer.setCreatedAt(Instant.now()); customer.setUpdatedAt(Instant.now());
        customers.saveAndFlush(customer);
    }

    private void persistOwner(String id, String address, String customer) {
        var owner = new PlatformUserEntity(); owner.setId(id); owner.setEmail(address); owner.setDisplayName("AI-state test owner");
        owner.setPasswordHash("synthetic-password-hash"); owner.setRole("CUSTOMER_ADMIN"); owner.setStatus("ACTIVE");
        owner.setCustomerId(customer); owner.setCreatedAt(Instant.now()); owner.setUpdatedAt(Instant.now()); users.saveAndFlush(owner);
    }

    private CustomerSelfServiceException.Code revokeAuthority(String reason) {
        if (Set.of("revoked", "expired").contains(reason)) {
            inSetupTransaction(() -> {
                var grant = grants.findByIdForUpdate(grantId).orElseThrow(); grant.setRevision(3);
                if ("revoked".equals(reason)) grant.setStatus(EntitlementStatus.REVOKED);
                else grant.setExpiresAt(Instant.now().minusSeconds(1));
                grants.saveAndFlush(grant);
            });
            return "revoked".equals(reason) ? ENTITLEMENT_REVOKED : ENTITLEMENT_EXPIRED;
        }
        if ("api-key".equals(reason)) {
            CustomerOfferTestFixtures.authenticate(email, PlatformRole.CUSTOMER_ADMIN, "API_KEY");
            return ACTIVE_CUSTOMER_SCOPE_REQUIRED;
        }
        String otherId = "as-other-owner-" + UUID.randomUUID();
        String otherEmail = UUID.randomUUID() + "@state.test.invalid";
        String otherCustomer = "foreign-customer".equals(reason) ? "as-foreign-" + UUID.randomUUID() : customerId;
        inSetupTransaction(() -> {
            if (!otherCustomer.equals(customerId)) persistCustomer(otherCustomer);
            persistOwner(otherId, otherEmail, otherCustomer);
        });
        CustomerOfferTestFixtures.authenticate(otherEmail, PlatformRole.CUSTOMER_ADMIN, "SESSION");
        return "foreign-customer".equals(reason) ? RESOURCE_SCOPE_MISMATCH : INVITE_OWNER_MISMATCH;
    }

    private void assertCommittedBinding(Result result, String operationKey) {
        assertNoAmbientTransaction();
        assertThat(result.offerId()).isEqualTo(offerId); assertThat(result.deploymentId()).isEqualTo(deploymentId);
        assertThat(result.binding().customerId()).isEqualTo(customerId); assertThat(result.binding().tenantId()).isEqualTo(tenantId);
        assertThat(result.binding().targetProfileId()).isEqualTo(targetId); assertThat(result.binding().versionId()).isEqualTo(versionId);
        assertThat(result.binding().lifecycle()).isEqualTo("RETAIN_EXTERNAL");
        assertThat(rowCount("deployment_provider_resource_handles", "deployment_id", deploymentId)).isEqualTo(1);
        assertThat(rowCount("deployment_ai_state_schema_claims", "deployment_id", deploymentId)).isEqualTo(2);
        assertThat(rowCount("platform_secrets", "deployment_id", deploymentId)).isEqualTo(3);
        assertThat(approvalRows()).hasSize(1);
        assertThat(generationRows()).hasSize(1);
        assertThat(generationRows().getFirst().get("generation_json").toString()).doesNotContain(HOST, PASSWORD, ROLE);
        assertThat(rowCount("platform_customer_entitlement_commands", "entitlement_id", grantId)).isEqualTo(1);
        var command = command(operationKey);
        assertThat(command.getCommandType()).isEqualTo("BIND_AI_STATE"); assertThat(command.getActorId()).isEqualTo(email);
        assertThat(command.getRequestFingerprint()).hasSize(64);
        assertThat(codec.read(command.getResultJson(), Result.class)).isEqualTo(result);
        assertThat(command.getResultJson()).doesNotContain(HOST, PASSWORD, ROLE);
        assertThat(jdbc.queryForObject("select metadata_json from deployment_provider_resource_handles where id=?",
            String.class, result.binding().bindingId())).doesNotContain(HOST, PASSWORD, ROLE);
        assertThat(auditRows()).allSatisfy(row -> assertThat(row.get("details_json").toString()).doesNotContain(HOST, PASSWORD, ROLE));
        assertThat(rowCount("platform_deployment_releases", "deployment_id", deploymentId)).isZero();
    }

    private Map<String, List<Map<String, Object>>> persistedState() {
        return Map.of("handles", ownedRows("deployment_provider_resource_handles", "deployment_id", deploymentId),
            "claims", ownedRows("deployment_ai_state_schema_claims", "deployment_id", deploymentId),
            "approvals", approvalRows(), "generations", generationRows(),
            "vault", ownedRows("platform_secrets", "deployment_id", deploymentId),
            "commands", ownedRows("platform_customer_entitlement_commands", "entitlement_id", grantId),
            "gates", ownedRows("platform_target_retirement_gates", "deployment_id", deploymentId), "audit", auditRows(),
            "releases", ownedRows("platform_deployment_releases", "deployment_id", deploymentId));
    }

    private List<Map<String, Object>> ownedRows(String table, String column, String id) {
        return jdbc.queryForList("select * from " + table + " where " + column + "=? order by 1", id);
    }

    private List<Map<String, Object>> approvalRows() {
        return jdbc.queryForList("select a.* from deployment_ai_state_storage_approvals a join deployment_provider_resource_handles h"
            + " on h.id=a.binding_id where h.deployment_id=? order by a.binding_id,a.version_id", deploymentId);
    }

    private List<Map<String, Object>> generationRows() {
        return jdbc.queryForList("select g.* from deployment_ai_state_storage_credential_generations g"
            + " join deployment_provider_resource_handles h on h.id=g.binding_id"
            + " where h.deployment_id=? order by g.binding_id,g.version_id", deploymentId);
    }

    private List<Map<String, Object>> auditRows() {
        return jdbc.queryForList("select * from platform_audit_events where target_id=?"
            + " or cast(details_json as jsonb)->>'deploymentId'=? order by id", deploymentId, deploymentId);
    }

    private long rowCount(String table, String column, String id) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + "=?", Long.class, id);
    }

    private CustomerEntitlementCommandEntity command(String key) {
        return commands.findByEntitlementIdAndOperationKey(grantId, key).orElseThrow();
    }

    private String privateHmac(Request input) throws Exception {
        String key = jdbc.queryForObject("select secret_value from platform_secrets where name=?", String.class, signingSecretName);
        assertThat(Base64.getDecoder().decode(key)).hasSize(32);
        var connection = input.connection();
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("command", "BIND_AI_STATE"); material.put("offerId", offerId); material.put("deploymentId", deploymentId);
        material.put("customerId", customerId); material.put("actorId", email); material.put("targetProfileId", targetId);
        material.put("bindingSlotId", SLOT); material.put("expectedEntitlementRevision", input.expectedEntitlementRevision());
        material.put("versionId", connection.versionId()); material.put("host", connection.host()); material.put("port", connection.port());
        material.put("databaseName", connection.databaseName()); material.put("runtimeSchema", connection.runtimeSchema());
        material.put("connectorSchema", connection.connectorSchema()); material.put("username", connection.username());
        material.put("password", connection.password()); material.put("migrationAuthorized", connection.migrationAuthorized());
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(Base64.getDecoder().decode(key), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(codec.write(material).getBytes(StandardCharsets.UTF_8)));
    }

    private Result bindOnWorker(String key, CountDownLatch started) {
        authenticate();
        try {
            assertNoAmbientTransaction();
            if (started != null) started.countDown();
            return service.bind(offerId, deploymentId, request(key, PASSWORD));
        } finally { SecurityContextHolder.clearContext(); }
    }

    private Request request(String operationKey, String password) {
        return request(operationKey, versionId, password);
    }

    private Request request(String operationKey, String version, String password) {
        return new Request(2, operationKey, targetId, SLOT, connection(version, password));
    }

    private DeploymentAiStateStorageContracts.Request connection(String version, String password) {
        return new DeploymentAiStateStorageContracts.Request(version, HOST, 5432, databaseName, "runtime_state",
            "connector_state", ROLE, password, true, null);
    }

    private void inSetupTransaction(Runnable body) {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> body.run());
    }

    private void authenticate() { CustomerOfferTestFixtures.authenticate(email, PlatformRole.CUSTOMER_ADMIN, "SESSION"); }
    private void assertNoAmbientTransaction() { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); }
    private void assertCode(CustomerSelfServiceException.Code expected, Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomerSelfServiceException.class,
            failure -> assertThat(failure.code()).isEqualTo(expected));
    }

    @TestConfiguration
    static class Configuration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean LocalValidatorFactoryBean validator() { return new LocalValidatorFactoryBean(); }
        @Bean MarketplaceManifestService manifests(ObjectMapper mapper) { return new MarketplaceManifestService(mapper); }
        @Bean PlatformAIWorkspaceProperties workspaceProperties() {
            return new PlatformAIWorkspaceProperties(null, false, 120, null, null, null, null, null);
        }
        @Bean CustomerSelfServiceProperties customerProperties() {
            return new CustomerSelfServiceProperties(Map.of(
                TargetIntent.CUSTOMER_STAGING, new CustomerSelfServiceProperties.TrustedServer("state-stage-server", "192.0.2.10", "stage.invalid"),
                TargetIntent.CUSTOMER_PRODUCTION, new CustomerSelfServiceProperties.TrustedServer("state-prod-server", "192.0.2.20", "prod.invalid")), 300);
        }
        @Bean CustomerTargetPlacementService placement(CustomerSelfServiceProperties properties,
            CustomerReviewedCompositionService composition, CustomerOfferCodec codec, CustomerPlacementEvidenceRepository evidence) {
            // Real cached-evidence validation only; provider HTTP dependencies are deliberately absent.
            return new CustomerTargetPlacementService(properties, null, null, composition, codec, evidence);
        }
    }
}
