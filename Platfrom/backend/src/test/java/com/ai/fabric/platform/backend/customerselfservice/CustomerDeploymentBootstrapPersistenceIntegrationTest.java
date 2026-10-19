package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.audit.repository.PlatformAuditEventRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.entity.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentBootstrapContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentBootstrapContracts.Result;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.*;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.marketplace.entity.*;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.repository.*;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceTemplateBootstrapService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The factory is mocked; grants, locks, authorization, installs, commands and audit writes are real H2 JPA. */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"}, showSql = false)
@Import({CustomerDeploymentBootstrapService.class, CustomerOfferEntitlementService.class,
    CustomerDeploymentStorageSelectionService.class,
    com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyArtifactPolicy.class,
    com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService.class,
    com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationPolicy.class,
    com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationEndpointPolicy.class,
    CustomerSelfServiceAuthorizationService.class, CustomerReviewedCompositionService.class,
    PlatformCustomerAccessService.class, DeploymentAccessService.class, CustomerOfferCodec.class,
    PlatformAuditService.class, CustomerDeploymentBootstrapPersistenceIntegrationTest.Configuration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CustomerDeploymentBootstrapPersistenceIntegrationTest {
    @Autowired CustomerDeploymentBootstrapService service;
    @Autowired CustomerReviewedCompositionService composition;
    @Autowired CustomerOfferCodec codec;
    @Autowired CustomerOfferRepository offers;
    @Autowired CustomerOfferEntitlementRepository grants;
    @Autowired CustomerEntitlementCommandRepository commands;
    @Autowired CustomerOfferDeploymentRepository associations;
    @Autowired CustomerPlacementEvidenceRepository placementEvidence;
    @Autowired PlatformCustomerRepository customers;
    @Autowired PlatformTenantRepository tenants;
    @Autowired PlatformUserRepository users;
    @Autowired DeploymentRepository deployments;
    @Autowired DeploymentReleaseRepository releases;
    @Autowired DeploymentVersionRepository deploymentVersions;
    @Autowired DeploymentTargetProfileRepository profiles;
    @Autowired DeploymentSourceArtifactRepository artifacts;
    @Autowired MarketplacePluginRepository plugins;
    @Autowired MarketplacePluginVersionRepository versions;
    @Autowired DeploymentMarketplacePluginInstallRepository installs;
    @Autowired PlatformAuditEventRepository auditEvents;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean MarketplaceTemplateBootstrapService templates;
    @MockBean com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceDraftCompilerService compiler;
    @MockBean com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationInstallInitializer externalInstalls;
    @MockBean CustomerWorkspaceProfileValidator workspaceProfiles;
    private String customerId;
    private String ownerId;
    private String email;
    private String tenantId;
    private String offerId;
    private String grantId;
    private String stagingId;
    private String productionId;
    private String pluginId;
    private String versionId;
    private Definition definition;
    private String offerFingerprint;
    private final AtomicReference<String> createdId = new AtomicReference<>();

    @BeforeEach void setup() {
        String suffix = UUID.randomUUID().toString();
        customerId = "customer-" + suffix; ownerId = "owner-" + suffix; email = suffix + "@test.invalid";
        tenantId = "tenant-" + suffix; offerId = "offer-" + suffix; grantId = "grant-" + suffix;
        stagingId = "stage-" + suffix; productionId = "prod-" + suffix;
        pluginId = "template-" + suffix; versionId = "version-" + suffix;
        createdId.set(null);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> seedReviewedOffer());
        authenticate();
        when(templates.bootstrapConfiguration(eq(pluginId), any())).thenAnswer(call -> persistConfiguration(call.getArgument(1), versionId));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void reviewedConfigurationCommitsOwnedDraftActualInstallAssociationAndSerializedCommand() {
        Result result = service.bootstrap(offerId, request("bootstrap"));
        assertCommittedResult(result, "bootstrap");
        var deployment = deployments.findById(result.deploymentId()).orElseThrow();
        assertThat(deployment.getName()).isEqualTo("Reviewed workspace");
        assertThat(deployment.getCustomerId()).isEqualTo(customerId);
        assertThat(deployment.getTenantId()).isEqualTo(tenantId);
        assertThat(deployment.getTemplateId()).isEqualTo(definition.templateId());
        assertThat(deployment.getStatus()).isEqualTo("DRAFT");
        assertThat(deployment.getEnvironmentName()).isEqualTo("staging");
        assertThat(deployment.getActiveVersionId()).isNull();
        assertThat(deployment.getRuntimeBaseUrl()).isNull();
        assertThat(deployment.getConnectorBaseUrl()).isNull();
        assertThat(deployment.getSourceRepositoryOverride()).isNull();
        assertThat(deployment.getSourceBranchOverride()).isNull();
        verify(templates).bootstrapConfiguration(pluginId, new CreateMarketplaceTemplateBootstrapRequest(
            "1.0.0", "Reviewed workspace", "staging", definition.templateId(), null, null, null));
        verify(templates, never()).bootstrap(anyString(), any());
        assertNoRuntimeRecords(result.deploymentId());
    }

    @Test void simultaneousSameKeyCommandsSerializeOnTheRealGrantAndReplayExactlyOneCommit() throws Exception {
        CountDownLatch firstInsideFactory = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        when(templates.bootstrapConfiguration(eq(pluginId), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            firstInsideFactory.countDown();
            assertThat(releaseFirst.await(10, TimeUnit.SECONDS)).isTrue();
            return persistConfiguration(call.getArgument(1), versionId);
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Result> first = executor.submit(() -> bootstrapOnWorker(null));
            assertThat(firstInsideFactory.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Result> second = executor.submit(() -> bootstrapOnWorker(secondStarted));
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            verify(templates, times(1)).bootstrapConfiguration(eq(pluginId), any());
            releaseFirst.countDown();
            Result committed = first.get(20, TimeUnit.SECONDS);
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(committed);
            assertCommittedResult(committed, "same-key");
            assertThat(service.bootstrap(offerId, request("same-key"))).isEqualTo(committed);
            verify(templates, times(1)).bootstrapConfiguration(eq(pluginId), any());
            assertThat(ownedDeployments()).hasSize(1);
            assertNoRuntimeRecords(committed.deploymentId());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void laterTransactionReplaysThePersistedOriginalResultDespiteANewerActiveGrantRevision() {
        Request original = request("persisted-replay");
        Result first = service.bootstrap(offerId, original);
        mutateGrant(EntitlementStatus.ACTIVE, Instant.now().plusSeconds(3600), ownerId, 3);
        Result replay = service.bootstrap(offerId, original);
        assertThat(replay).isEqualTo(first);
        assertThat(replay.entitlementRevision()).isEqualTo(2);
        assertThat(grants.findById(grantId).orElseThrow().getRevision()).isEqualTo(3);
        assertThat(grantCommands()).hasSize(1);
        assertThat(codec.read(grantCommands().getFirst().getResultJson(), Result.class)).isEqualTo(first);
        verify(templates, times(1)).bootstrapConfiguration(eq(pluginId), any());
    }

    @ParameterizedTest @ValueSource(strings = {"name", "target", "revision"})
    void changedPayloadCannotReuseAPersistedBootstrapKey(String field) {
        Result original = service.bootstrap(offerId, request("payload-key"));
        Request changed = switch (field) {
            case "name" -> new Request(stagingId, "Changed workspace", 2, "payload-key");
            case "target" -> new Request(productionId, "Reviewed workspace", 2, "payload-key");
            default -> new Request(stagingId, "Reviewed workspace", 3, "payload-key");
        };
        assertCode(OPERATION_KEY_CONFLICT, () -> service.bootstrap(offerId, changed));
        assertCommittedResult(original, "payload-key");
        verify(templates, times(1)).bootstrapConfiguration(eq(pluginId), any());
    }

    @Test void persistedOtherCommandTypeWithAMatchingHashAndActorStillConflicts() {
        Request input = request("accept-key");
        commands.saveAndFlush(new CustomerEntitlementCommandEntity("command-" + UUID.randomUUID(), grantId,
            input.operationKey(), fingerprint(input), "ACCEPT", email, "{}", Instant.now()));
        assertCode(OPERATION_KEY_CONFLICT, () -> service.bootstrap(offerId, input));
        assertThat(grantCommands()).singleElement().extracting(CustomerEntitlementCommandEntity::getCommandType).isEqualTo("ACCEPT");
        assertNoConfigurationCreated();
        verifyNoInteractions(templates);
    }

    @ParameterizedTest @ValueSource(strings = {"revoked", "expired"})
    void revokedOrExpiredGrantDeniesBothNewCommandsAndPreviouslySuccessfulReplay(String state) {
        Result original = service.bootstrap(offerId, request("existing-key"));
        mutateGrant("revoked".equals(state) ? EntitlementStatus.REVOKED : EntitlementStatus.ACTIVE,
            "expired".equals(state) ? Instant.now().minusSeconds(1) : Instant.now().plusSeconds(3600), ownerId, 3);
        var expected = "revoked".equals(state) ? ENTITLEMENT_REVOKED : ENTITLEMENT_EXPIRED;
        assertCode(expected, () -> service.bootstrap(offerId, request("existing-key")));
        assertCode(expected, () -> service.bootstrap(offerId, request("new-key")));
        assertThat(grantCommands()).hasSize(1);
        assertThat(ownedDeployments()).extracting(DeploymentEntity::getId).containsExactly(original.deploymentId());
        verify(templates, times(1)).bootstrapConfiguration(eq(pluginId), any());
    }

    @ParameterizedTest @EnumSource(value = EntitlementStatus.class, names = {"INVITED", "REVOKED"})
    void inactivePersistedGrantCannotCreateConfiguration(EntitlementStatus state) {
        mutateGrant(state, Instant.now().plusSeconds(3600), ownerId, 2);
        assertCode(state == EntitlementStatus.REVOKED ? ENTITLEMENT_REVOKED : ENTITLEMENT_REQUIRED,
            () -> service.bootstrap(offerId, request("inactive-key")));
        assertThat(grantCommands()).isEmpty();
        assertNoConfigurationCreated();
        verifyNoInteractions(templates);
    }

    @Test void expiredPersistedGrantCannotCreateConfiguration() {
        mutateGrant(EntitlementStatus.ACTIVE, Instant.now().minusSeconds(1), ownerId, 2);
        assertCode(ENTITLEMENT_EXPIRED, () -> service.bootstrap(offerId, request("expired-key")));
        assertThat(grantCommands()).isEmpty();
        assertNoConfigurationCreated();
        verifyNoInteractions(templates);
    }

    @Test void anotherActiveOwnerInTheSameCustomerCannotCreateOrReplayTheOwnersCommand() {
        Result original = service.bootstrap(offerId, request("owner-key"));
        String otherEmail = UUID.randomUUID() + "@test.invalid";
        persistOwner("other-" + UUID.randomUUID(), otherEmail, customerId);
        CustomerOfferTestFixtures.authenticate(otherEmail, PlatformRole.CUSTOMER_ADMIN, "SESSION");
        assertCode(INVITE_OWNER_MISMATCH, () -> service.bootstrap(offerId, request("owner-key")));
        assertCode(INVITE_OWNER_MISMATCH, () -> service.bootstrap(offerId, request("other-owner-key")));
        assertThat(grantCommands()).hasSize(1);
        assertThat(ownedDeployments()).extracting(DeploymentEntity::getId).containsExactly(original.deploymentId());
        verify(templates, times(1)).bootstrapConfiguration(eq(pluginId), any());
    }

    @Test void foreignCustomerActorCannotUseTheOwnersOfferEntitlement() {
        String foreignCustomer = "foreign-" + UUID.randomUUID();
        persistCustomer(foreignCustomer);
        String foreignEmail = UUID.randomUUID() + "@test.invalid";
        persistOwner("foreign-owner-" + UUID.randomUUID(), foreignEmail, foreignCustomer);
        CustomerOfferTestFixtures.authenticate(foreignEmail, PlatformRole.CUSTOMER_ADMIN, "SESSION");
        assertCode(ENTITLEMENT_REQUIRED, () -> service.bootstrap(offerId, request("foreign-key")));
        assertThat(grantCommands()).isEmpty();
        assertNoConfigurationCreated();
        verifyNoInteractions(templates);
    }

    @ParameterizedTest @ValueSource(strings = {"template-version", "extra-plugin"})
    void wrongActualInstalledCompositionRollsBackFactoryRowsAndDoesNotPoisonTheOperationKey(String mismatch) {
        when(templates.bootstrapConfiguration(eq(pluginId), any())).thenAnswer(call -> {
            var result = persistConfiguration(call.getArgument(1),
                "template-version".equals(mismatch) ? "unreviewed-version" : versionId);
            if ("extra-plugin".equals(mismatch)) persistInstall(result.id(), "unreviewed-plugin", "unreviewed-version", "INSTALLED");
            assertThat(installs.findByDeploymentIdOrderByUpdatedAtDesc(result.id())).isNotEmpty();
            return result;
        });
        assertCode(REVIEWED_COMPOSITION_CHANGED, () -> service.bootstrap(offerId, request("retryable-key")));
        String rolledBackId = createdId.get();
        assertThat(rolledBackId).isNotBlank();
        assertThat(deployments.findById(rolledBackId)).isEmpty();
        assertThat(installs.findByDeploymentIdOrderByUpdatedAtDesc(rolledBackId)).isEmpty();
        assertThat(associations.findById(rolledBackId)).isEmpty();
        assertThat(grantCommands()).isEmpty();
        assertThat(auditEvents.findAll()).noneMatch(event -> rolledBackId.equals(event.getTargetId()));
        assertThat(grants.findById(grantId).orElseThrow().getRevision()).isEqualTo(2);
        when(templates.bootstrapConfiguration(eq(pluginId), any())).thenAnswer(call -> persistConfiguration(call.getArgument(1), versionId));
        Result retried = service.bootstrap(offerId, request("retryable-key"));
        assertThat(retried.deploymentId()).isNotEqualTo(rolledBackId);
        assertCommittedResult(retried, "retryable-key");
        verify(templates, times(2)).bootstrapConfiguration(eq(pluginId), any());
    }

    @Test void foreignActualTenantRollsBackBeforeAnAssociationOrSuccessCommandIsCommitted() {
        var tenant = tenants.findById(tenantId).orElseThrow();
        String foreignCustomer = "foreign-" + UUID.randomUUID(); persistCustomer(foreignCustomer);
        tenant.setCustomerId(foreignCustomer); tenants.saveAndFlush(tenant);
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.bootstrap(offerId, request("foreign-tenant")));
        assertThat(deployments.findById(createdId.get())).isEmpty();
        assertThat(installs.findByDeploymentIdOrderByUpdatedAtDesc(createdId.get())).isEmpty();
        assertThat(associations.findById(createdId.get())).isEmpty();
        assertThat(grantCommands()).isEmpty();
    }

    private Result bootstrapOnWorker(CountDownLatch started) {
        authenticate();
        try {
            if (started != null) started.countDown();
            return service.bootstrap(offerId, request("same-key"));
        } finally { SecurityContextHolder.clearContext(); }
    }

    private void seedReviewedOffer() {
        Instant now = Instant.now();
        persistCustomer(customerId); persistOwner(ownerId, email, customerId);
        var tenant = new PlatformTenantEntity(); tenant.setId(tenantId); tenant.setCustomerId(customerId);
        tenant.setName("Owned tenant"); tenant.setSlug(tenantId); tenant.setStatus("ACTIVE");
        tenant.setCreatedAt(now); tenant.setUpdatedAt(now); tenants.saveAndFlush(tenant);
        var plugin = new MarketplacePluginEntity(); plugin.setId(pluginId); plugin.setSlug(pluginId);
        plugin.setDisplayName("Reviewed template"); plugin.setPluginType("TEMPLATE"); plugin.setStatus("ACTIVE");
        plugin.setPublisherSlug("test"); plugin.setPublisherDisplayName("Test publisher");
        plugin.setShortDescription("Test template"); plugin.setCreatedAt(now); plugin.setUpdatedAt(now); plugins.saveAndFlush(plugin);
        var version = new MarketplacePluginVersionEntity(); version.setId(versionId); version.setPluginId(pluginId);
        version.setVersion("1.0.0"); version.setReleaseChannel("STABLE"); version.setStatus("PUBLISHED");
        version.setManifestJson("""
            {"schemaVersion":1,"pluginType":"TEMPLATE","contributions":{"template":{"templateId":"reviewed-template","requiredPluginRefs":[]}}}
            """);
        version.setCreatedAt(now); version.setPublishedAt(now); versions.saveAndFlush(version);
        var artifact = new DeploymentSourceArtifactEntity(); artifact.setId("artifact-" + UUID.randomUUID());
        artifact.setServiceName("runtime"); artifact.setArtifactType("DOCKER_IMAGE");
        artifact.setImageRepository("registry.test.invalid/runtime"); artifact.setImageDigest("sha256:" + "a".repeat(64));
        artifact.setCapabilityManifestJson("{}"); artifact.setCapabilityManifestHash(codec.hash(codec.tree("{}")));
        artifact.setCreatedAt(now); artifact.setPromotedAt(now); artifacts.saveAndFlush(artifact);
        var staging = persistProfile(stagingId, "stage-server");
        var production = persistProfile(productionId, "prod-server");
        var base = CustomerOfferTestFixtures.definition();
        definition = new Definition(base.schemaVersion(), base.name(), base.templateId(),
            new PluginPin(pluginId, versionId, codec.hash(codec.tree(version.getManifestJson()))), List.of(),
            List.of(new ArtifactPin(artifact.getId(), composition.artifactFingerprint(artifact))),
            List.of(new TargetPermission(stagingId, TargetIntent.CUSTOMER_STAGING, composition.profileFingerprint(staging)),
                new TargetPermission(productionId, TargetIntent.CUSTOMER_PRODUCTION, composition.profileFingerprint(production))),
            base.operations(), base.settings(), base.bindingSlots(), base.documentAudiences(), base.admissionPrerequisites(),
            base.limits(), base.topology(), base.approvedPolicyRef(), base.operatingPolicyRef(), base.identityPolicyRef(), base.storagePolicyRef());
        composition.validate(definition);
        offerFingerprint = codec.hash(definition);
        offers.saveAndFlush(new CustomerOfferEntity(offerId, codec.write(definition), offerFingerprint, "test-review", "admin", now));
        var grant = new CustomerOfferEntitlementEntity(); grant.setId(grantId); grant.setCustomerId(customerId);
        grant.setOfferId(offerId); grant.setOwnerUserId(ownerId); grant.setInvitationReference("accepted-test-invitation");
        grant.setStatus(EntitlementStatus.ACTIVE); grant.setRevision(2); grant.setExpiresAt(now.plusSeconds(3600));
        grant.setCreatedAt(now); grant.setUpdatedAt(now); grants.saveAndFlush(grant);
        placementEvidence.saveAndFlush(new CustomerPlacementEvidenceEntity("proof-" + UUID.randomUUID(), stagingId,
            TargetIntent.CUSTOMER_STAGING, composition.profileFingerprint(staging), "stage-server", "192.0.2.10",
            "destination", now.minusSeconds(1), now.plusSeconds(300)));
    }

    private DeploymentTargetProfileEntity persistProfile(String id, String server) {
        var profile = CustomerOfferTestFixtures.profile(id, server); profile.setName(id);
        profile.setCreatedAt(Instant.now()); profile.setUpdatedAt(Instant.now());
        return profiles.saveAndFlush(profile);
    }

    private void persistCustomer(String id) {
        var customer = new PlatformCustomerEntity(); customer.setId(id); customer.setName("Test customer");
        customer.setSlug(id); customer.setStatus("ACTIVE"); customer.setCreatedAt(Instant.now()); customer.setUpdatedAt(Instant.now());
        customers.saveAndFlush(customer);
    }

    private void persistOwner(String id, String address, String customer) {
        var owner = new PlatformUserEntity(); owner.setId(id); owner.setEmail(address); owner.setDisplayName("Test owner");
        owner.setPasswordHash("test-not-a-real-password"); owner.setRole("CUSTOMER_ADMIN"); owner.setStatus("ACTIVE");
        owner.setCustomerId(customer); owner.setCreatedAt(Instant.now()); owner.setUpdatedAt(Instant.now()); users.saveAndFlush(owner);
    }

    private DeploymentSummary persistConfiguration(CreateMarketplaceTemplateBootstrapRequest input, String installedVersion) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        var deployment = new DeploymentEntity(); deployment.setId("deployment-" + UUID.randomUUID());
        createdId.set(deployment.getId()); deployment.setName(input.name()); deployment.setEnvironmentName(input.environment());
        deployment.setTemplateId(input.templateId()); deployment.setBehaviorType("CONVERSATIONAL"); deployment.setStatus("DRAFT");
        deployment.setCustomerId(customerId); deployment.setTenantId(tenantId);
        deployment.setCreatedAt(Instant.now()); deployment.setUpdatedAt(Instant.now()); deployments.saveAndFlush(deployment);
        persistInstall(deployment.getId(), pluginId, installedVersion, "BOOTSTRAPPED");
        return new DeploymentSummary(deployment.getId(), deployment.getName(), deployment.getEnvironmentName(),
            deployment.getTemplateId(), deployment.getBehaviorType(), null, null, deployment.getStatus(), null, null,
            false, false, false, deployment.getCreatedAt());
    }

    private void persistInstall(String deploymentId, String installedPlugin, String installedVersion, String status) {
        var install = new DeploymentMarketplacePluginInstallEntity(); install.setId("install-" + UUID.randomUUID());
        install.setDeploymentId(deploymentId); install.setPluginId(installedPlugin); install.setPluginVersionId(installedVersion);
        install.setStatus(status); install.setConfigJson("{}"); install.setSecretRefsJson("{}");
        install.setCreatedAt(Instant.now()); install.setUpdatedAt(Instant.now()); installs.saveAndFlush(install);
    }

    private void mutateGrant(EntitlementStatus state, Instant expiry, String owner, long revision) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var grant = grants.findByIdForUpdate(grantId).orElseThrow(); grant.setStatus(state);
            grant.setExpiresAt(expiry); grant.setOwnerUserId(owner); grant.setRevision(revision);
            grant.setUpdatedAt(Instant.now()); grants.saveAndFlush(grant);
        });
    }

    private void assertCommittedResult(Result result, String key) {
        assertThat(result).isEqualTo(new Result(createdId.get(), tenantId, offerId, offerFingerprint, stagingId, 2, "CONFIGURATION_ONLY"));
        assertThat(grants.findById(grantId).orElseThrow().getRevision()).isEqualTo(2);
        assertThat(ownedDeployments()).extracting(DeploymentEntity::getId).containsExactly(result.deploymentId());
        assertThat(installs.findByDeploymentIdOrderByUpdatedAtDesc(result.deploymentId())).singleElement().satisfies(install -> {
            assertThat(install.getPluginId()).isEqualTo(pluginId);
            assertThat(install.getPluginVersionId()).isEqualTo(versionId);
            assertThat(install.getStatus()).isEqualTo("BOOTSTRAPPED");
        });
        var association = associations.findById(result.deploymentId()).orElseThrow();
        assertThat(association.getCustomerId()).isEqualTo(customerId);
        assertThat(association.getTenantId()).isEqualTo(tenantId);
        assertThat(association.getOfferId()).isEqualTo(offerId);
        assertThat(association.getOfferFingerprint()).isEqualTo(offerFingerprint);
        assertThat(association.getOperationKey()).isEqualTo(key);
        assertThat(association.getAuthorizationEntitlementRevision()).isEqualTo(2);
        assertThat(grantCommands()).singleElement().satisfies(command -> {
            assertThat(command.getCommandType()).isEqualTo("BOOTSTRAP_CONFIG");
            assertThat(command.getActorId()).isEqualTo(email);
            assertThat(command.getOperationKey()).isEqualTo(key);
            assertThat(command.getRequestFingerprint()).isEqualTo(fingerprint(request(key)));
            assertThat(codec.read(command.getResultJson(), Result.class)).isEqualTo(result);
        });
        assertThat(auditEvents.findAll()).filteredOn(event -> result.deploymentId().equals(event.getTargetId()))
            .singleElement().satisfies(event -> {
                assertThat(event.getAction()).isEqualTo("CUSTOMER_OFFER_CONFIGURATION_BOOTSTRAPPED");
                assertThat(event.getActorId()).isEqualTo(email);
            });
    }

    private void assertNoConfigurationCreated() {
        assertThat(ownedDeployments()).isEmpty();
        assertThat(associations.findAll()).noneMatch(association -> customerId.equals(association.getCustomerId()));
    }

    private void assertNoRuntimeRecords(String deploymentId) {
        assertThat(releases.findAll()).noneMatch(release -> deploymentId.equals(release.getDeploymentId()));
        assertThat(deploymentVersions.findAll()).noneMatch(version -> deploymentId.equals(version.getDeploymentId()));
    }

    private List<DeploymentEntity> ownedDeployments() {
        return deployments.findAll().stream().filter(deployment -> customerId.equals(deployment.getCustomerId())).toList();
    }

    private List<CustomerEntitlementCommandEntity> grantCommands() {
        return commands.findAll().stream().filter(command -> grantId.equals(command.getEntitlementId())).toList();
    }

    private Request request(String key) { return new Request(stagingId, "Reviewed workspace", 2, key); }
    private void authenticate() { CustomerOfferTestFixtures.authenticate(email, PlatformRole.CUSTOMER_ADMIN, "SESSION"); }
    private String fingerprint(Request input) {
        return codec.hash(Map.of("command", "BOOTSTRAP_CONFIG", "offerId", offerId, "request", input,
            "customerId", customerId, "ownerId", ownerId, "actor", email));
    }
    private void assertCode(CustomerSelfServiceException.Code code, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(CustomerSelfServiceException.class,
            ex -> assertThat(ex.code()).isEqualTo(code));
    }

    @TestConfiguration
    static class Configuration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean LocalValidatorFactoryBean validator() { return new LocalValidatorFactoryBean(); }
        @Bean MarketplaceManifestService manifests(ObjectMapper mapper) { return new MarketplaceManifestService(mapper); }
        @Bean CustomerSelfServiceProperties properties() {
            return new CustomerSelfServiceProperties(Map.of(
                TargetIntent.CUSTOMER_STAGING, new CustomerSelfServiceProperties.TrustedServer(
                    "stage-server", "192.0.2.10", "stage.internal"),
                TargetIntent.CUSTOMER_PRODUCTION, new CustomerSelfServiceProperties.TrustedServer(
                    "prod-server", "192.0.2.20", "prod.internal")), 300);
        }
        @Bean CustomerTargetPlacementService placement(CustomerSelfServiceProperties properties,
            CustomerReviewedCompositionService composition, CustomerOfferCodec codec, CustomerPlacementEvidenceRepository evidence) {
            // Remote dependencies are deliberately unwired; only the real cached-evidence path is available.
            return new CustomerTargetPlacementService(properties, null, null, composition, codec, evidence);
        }
    }
}
