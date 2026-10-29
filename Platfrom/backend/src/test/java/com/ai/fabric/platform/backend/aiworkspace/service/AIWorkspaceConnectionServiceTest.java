package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.model.CreateAIWorkspaceConnectionRequest;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.*;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.lifecycle.DeploymentTargetMutationService;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.*;
import com.ai.fabric.platform.backend.tenant.model.*;
import com.ai.fabric.platform.backend.tenant.repository.*;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real transaction, consumer persistence, binding history, authorization and binding services; no provider calls. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AIWorkspaceConnectionServiceTest {
    private LocalContainerEntityManagerFactoryBean factory;
    private JpaTransactionManager transactions;
    private TransactionTemplate transaction;
    private PlatformCustomerRepository customers;
    private PlatformConsumerRepository consumers;
    private PlatformConsumerBindingHistoryRepository history;
    private DeploymentRepository deployments;
    private AIWorkspaceConnectionService service;
    private PlatformCustomerConsumerService consumerService;
    private CustomerSelfServiceOperationBoundary boundary;
    private DeploymentReleaseRepository releases;
    private DeploymentVersionRepository versions;
    private DeploymentTargetProfileRepository profiles;
    private PlatformUserRepository users;
    private PlatformTenantRepository tenants;
    private CustomerOfferRepository offers;
    private CustomerOfferEntitlementRepository grants;
    private DeploymentTargetMutationService mutations;
    private AIWorkspaceBindingGuard workspaceGuard;
    private CustomerTargetPlacementService placement;
    private PlatformUserEntity owner;
    private PlatformTenantEntity tenant;
    private DeploymentTargetProfileEntity target;
    private DeploymentReleaseEntity release;
    private DeploymentVersionEntity version;

    @BeforeAll
    void persistence() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:engage-connections-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setManagedTypes(PersistenceManagedTypes.of(PlatformCustomerEntity.class.getName(),
            PlatformConsumerEntity.class.getName(), PlatformConsumerBindingHistoryEntity.class.getName(),
            DeploymentEntity.class.getName()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
            "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        transactions = new JpaTransactionManager(factory.getObject());
        transaction = new TransactionTemplate(transactions);
        var repositories = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory.getObject()));
        customers = repositories.getRepository(PlatformCustomerRepository.class);
        consumers = repositories.getRepository(PlatformConsumerRepository.class);
        history = repositories.getRepository(PlatformConsumerBindingHistoryRepository.class);
        deployments = repositories.getRepository(DeploymentRepository.class);
    }

    @AfterAll void closePersistence() { if (factory != null) factory.destroy(); }
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @BeforeEach
    void setup() {
        transaction.executeWithoutResult(ignored -> {
            history.deleteAllInBatch(); consumers.deleteAllInBatch(); deployments.deleteAllInBatch(); customers.deleteAllInBatch();
            for (String id : List.of("customer", "other")) {
                var customer = new PlatformCustomerEntity();
                customer.setId(id); customer.setName(id); customer.setSlug(id); customer.setStatus("ACTIVE");
                customer.setCreatedAt(Instant.now()); customer.setUpdatedAt(Instant.now());
                customers.saveAndFlush(customer);
            }
            deployments.saveAndFlush(deployment("deployment", "customer"));
            deployments.saveAndFlush(deployment("destination", "customer"));
            deployments.saveAndFlush(deployment("foreign", "other"));
        });
        users = mock(PlatformUserRepository.class);
        tenants = mock(PlatformTenantRepository.class);
        releases = mock(DeploymentReleaseRepository.class);
        versions = mock(DeploymentVersionRepository.class);
        profiles = mock(DeploymentTargetProfileRepository.class);
        offers = mock(CustomerOfferRepository.class);
        grants = mock(CustomerOfferEntitlementRepository.class);
        var associations = mock(CustomerOfferDeploymentRepository.class);
        var composition = mock(CustomerReviewedCompositionService.class);
        placement = mock(CustomerTargetPlacementService.class);
        mutations = mock(DeploymentTargetMutationService.class);
        workspaceGuard = mock(AIWorkspaceBindingGuard.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var codec = new CustomerOfferCodec(mapper);
        var access = new PlatformCustomerAccessService(users, customers);
        var audit = mock(PlatformAuditService.class);
        var entitlements = new CustomerOfferEntitlementService(offers, grants, mock(CustomerEntitlementCommandRepository.class),
            access, customers, users, codec, composition, placement, profiles, audit);
        var authorization = new CustomerSelfServiceAuthorizationService(entitlements, composition, placement,
            deployments, releases, versions, profiles, tenants, consumers, mock(DeploymentAccessService.class),
            associations, mock(DeploymentDocumentBindingResolver.class));
        boundary = spy(new CustomerSelfServiceOperationBoundary(associations, deployments, authorization, entitlements, codec));
        consumerService = transactional(new PlatformCustomerConsumerService(consumers, history, customers, deployments,
            releases, access, audit, workspaceGuard, mock(AIWorkspaceInstallationRepository.class),
            mock(AIWorkspaceDeploymentSecurityProjectionService.class), mapper, mutations, profiles,
            mock(CustomerReviewedBindingBoundary.class)));
        service = transactional(new AIWorkspaceConnectionService(deployments, consumers, history, boundary, consumerService,
            SharedEntityManagerCreator.createSharedEntityManager(factory.getObject()), releases, versions, mapper));
        owner = new PlatformUserEntity();
        owner.setId("owner"); owner.setEmail("owner@test.invalid"); owner.setRole("CUSTOMER_ADMIN");
        owner.setStatus("ACTIVE"); owner.setCustomerId("customer");
        when(users.findById("owner")).thenReturn(Optional.of(owner));
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
        tenant = new PlatformTenantEntity(); tenant.setId("tenant"); tenant.setStatus("ACTIVE"); tenant.setCustomerId("customer");
        when(tenants.findById("tenant")).thenReturn(Optional.of(tenant));
        target = target("target", "staging");
        when(profiles.findById("target")).thenReturn(Optional.of(target));
        var production = target("production", "production");
        when(profiles.findById("production")).thenReturn(Optional.of(production));
        when(profiles.findAllById(any())).thenReturn(List.of(target, production));
        release = new DeploymentReleaseEntity(); release.setId("release"); release.setDeploymentId("deployment");
        release.setTargetProfileId("target"); release.setDeploymentVersionId("version");
        release.setStatus("APPLIED_VERIFIED"); release.setVerificationStatus("PASSED");
        when(releases.findById("release")).thenReturn(Optional.of(release));
        when(releases.findAllById(any())).thenAnswer(call -> {
            Iterable<String> ids = call.getArgument(0);
            for (String id : ids) if (id.equals("release")) return List.of(release);
            return List.of();
        });
        version = new DeploymentVersionEntity(); version.setId("version"); version.setDeploymentId("deployment");
        version.setStatus("PUBLISHED"); version.setConfigHash("published-hash");
        version.setBehaviorConfigJson("{}");
        when(versions.findById("version")).thenReturn(Optional.of(version));
        authenticate(PlatformRole.CUSTOMER_ADMIN, owner.getEmail(), "SESSION");
    }

    @Test
    void configurationOnlyConnectionWorksBeforeAnyReleaseAndUsesNormalBindingHistory() {
        var result = service.create("customer", request(null));
        assertThat(result.customerId()).isEqualTo("customer");
        assertThat(result.consumerId()).isEqualTo(alias("customer", "connect-1"));
        assertThat(result.boundDeploymentId()).isEqualTo("deployment");
        assertThat(result.boundTargetProfileId()).isEqualTo("target");
        assertThat(result.boundReleaseId()).isNull();
        assertThat(result.bindingRevision()).isEqualTo(1);
        var entry = history.findByConsumerEntityIdAndOperationKey(current().getId(), "connect-1").orElseThrow();
        assertThat(entry.getFromDeploymentId()).isNull();
        assertThat(entry.getFromBindingRevision()).isZero();
        assertThat(entry.getToBindingRevision()).isEqualTo(1);
        assertThat(entry.getRequestFingerprint()).hasSize(64);
        verify(boundary).require(any(), eq("target"), isNull(), eq(Operation.APP_CONFIGURE), eq("connect-1"),
            eq(Map.of()), isNull(), isNull(), isNull());
        verifyNoInteractions(releases, versions, offers, grants);
    }

    @Test
    void exactVerifiedReleaseUsesNormalBindingServiceAndDoesNotActivateInstallations() {
        var result = service.create("customer", request("release"));
        assertThat(result.boundReleaseId()).isEqualTo("release");
        assertThat(result.boundReleaseStatus()).isEqualTo("APPLIED_VERIFIED");
        verify(workspaceGuard).validateRebind(any(), eq("deployment"), eq("release"));
        verify(boundary).require(any(), eq("target"), eq("release"), eq(Operation.APP_CONFIGURE), eq("connect-1"),
            eq(Map.of()), isNull(), isNull(), isNull());
    }

    @Test
    void productionConfigurationIsNotRestrictedToTheBootstrapStagingTarget() {
        var result = service.create("customer", new CreateAIWorkspaceConnectionRequest(
            "deployment", "production", null, "Engage app", "connect-1"));
        assertThat(result.boundTargetProfileId()).isEqualTo("production");
        verifyNoInteractions(offers, grants);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unverified-status", "failed-verification"})
    void unverifiedReleaseRollsBackConsumerCreation(String failure) {
        if (failure.equals("unverified-status")) release.setStatus("VERIFYING");
        else release.setVerificationStatus("FAILED");
        assert409(() -> service.create("customer", request("release")));
        assertEmpty();
    }

    @Test
    void bindingFailureAfterCreationRollsBackTheConsumerAndHistory() {
        doAnswer(call -> {
            assertThat(consumers.count()).isEqualTo(1);
            assertThat(current().getBoundDeploymentId()).isNull();
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Target mutation fenced");
        }).when(mutations).requireAvailableForMutation("deployment", "target");
        assert409(() -> service.create("customer", request(null)));
        assertEmpty();
    }

    @Test
    void exactReplayReturnsStoredResultWithoutAnotherConsumerOrBindingMutation() {
        var first = service.create("customer", request("release"));
        var replay = service.create("customer", request("release"));
        assertThat(replay).isEqualTo(first);
        assertThat(consumers.count()).isEqualTo(1);
        assertThat(history.count()).isEqualTo(1);
        verify(mutations, times(1)).requireAvailableForMutation("deployment", "target");
        verify(boundary, times(2)).require(any(), eq("target"), eq("release"), eq(Operation.APP_CONFIGURE), eq("connect-1"),
            eq(Map.of()), isNull(), isNull(), isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"displayName", "deployment", "target", "release"})
    void sameKeyDifferentPayloadCannotOverwriteTheOriginal(String field) {
        service.create("customer", request(null));
        var changed = new CreateAIWorkspaceConnectionRequest(field.equals("deployment") ? "destination" : "deployment",
            field.equals("target") ? "production" : "target", field.equals("release") ? "release" : null,
            field.equals("displayName") ? "Different app" : "Engage app", "connect-1");
        assert409(() -> service.create("customer", changed));
        assertThat(current().getBindingRevision()).isEqualTo(1);
        assertThat(current().getBoundDeploymentId()).isEqualTo("deployment");
        assertThat(history.count()).isEqualTo(1);
    }

    @Test
    void authoritativeBindingFingerprintStillRejectsAnAlteredReceiptOrDifferentActor() {
        service.create("customer", request(null));
        authenticate(PlatformRole.PLATFORM_ADMIN, "admin-key", "API_KEY");
        assert409(() -> service.create("customer", request(null)));
        authenticate(PlatformRole.CUSTOMER_ADMIN, owner.getEmail(), "SESSION");
        transaction.executeWithoutResult(ignored -> {
            var entry = history.findAll().getFirst(); entry.setRequestFingerprint("0".repeat(64)); history.saveAndFlush(entry);
        });
        assert409(() -> service.create("customer", request(null)));
        assertThat(history.count()).isEqualTo(1);
    }

    @Test
    void changedBindingCannotBeReplayedOrMovedBackEvenIfItLaterReturnsToTheSameTarget() {
        var first = service.create("customer", request(null));
        consumerService.updateBinding("customer", first.consumerId(), new UpdatePlatformConsumerBindingRequest(
            "destination", null, "production", "Explicit separate move", 1L, "move"));
        assert409(() -> service.create("customer", request(null)));
        assertThat(current().getBoundDeploymentId()).isEqualTo("destination");
        assertThat(current().getBindingRevision()).isEqualTo(2);
        consumerService.updateBinding("customer", first.consumerId(), new UpdatePlatformConsumerBindingRequest(
            "deployment", null, "target", "Explicit return", 2L, "return"));
        assert409(() -> service.create("customer", request(null)));
        assertThat(current().getBindingRevision()).isEqualTo(3);
        assertThat(history.count()).isEqualTo(3);
    }

    @Test
    void existingDeterministicAliasWithoutOriginalCommandIsNotAdopted() {
        consumerService.createConsumer("customer", new CreatePlatformConsumerRequest(
            alias("customer", "connect-1"), "Engage app", null, null, null));
        assert409(() -> service.create("customer", request(null)));
        assertThat(current().getBoundDeploymentId()).isNull();
        assertThat(history.count()).isZero();
    }

    @Test
    void unrelatedSharedAssignmentIsNeverSelectedOrMutated() {
        consumerService.createConsumer("customer", new CreatePlatformConsumerRequest("shared-connection", "Shared app", null, null, null));
        consumerService.updateBinding("customer", "shared-connection", new UpdatePlatformConsumerBindingRequest(
            "destination", null, "production", "Existing shared connection", 0L, "shared"));
        var shared = consumers.findByConsumerIdIgnoreCase("shared-connection").orElseThrow();
        service.create("customer", request(null));
        assertThat(consumers.findByConsumerIdIgnoreCase("shared-connection").orElseThrow())
            .usingRecursiveComparison().isEqualTo(shared);
        assertThat(consumers.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"path-customer", "foreign-deployment", "inactive-target", "unknown-target",
        "foreign-release", "wrong-release-target", "unknown-release", "foreign-tenant"})
    void foreignOrUnavailableResourcesAreDeniedWithoutCreation(String scenario) {
        var command = request("release");
        String customerId = "customer";
        switch (scenario) {
            case "path-customer" -> customerId = "other";
            case "foreign-deployment" -> command = new CreateAIWorkspaceConnectionRequest("foreign", "target", null, "Engage app", "connect-1");
            case "inactive-target" -> target.setActive(false);
            case "unknown-target" -> when(profiles.findById("target")).thenReturn(Optional.empty());
            case "foreign-release" -> release.setDeploymentId("foreign");
            case "wrong-release-target" -> release.setTargetProfileId("production");
            case "unknown-release" -> when(releases.findById("release")).thenReturn(Optional.empty());
            case "foreign-tenant" -> tenant.setCustomerId("other");
        }
        var path = customerId; var payload = command;
        assertThatThrownBy(() -> service.create(path, payload))
            .isInstanceOfAny(CustomerSelfServiceException.class, ResponseStatusException.class);
        assertEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled-owner", "role-changed", "owner-moved", "disabled-customer", "inactive-target", "disabled-consumer"})
    void replayRequiresFreshOwnershipAccountTargetAndConsumerState(String scenario) {
        service.create("customer", request(null));
        switch (scenario) {
            case "disabled-owner" -> owner.setStatus("DISABLED");
            case "role-changed" -> owner.setRole("PLATFORM_ADMIN");
            case "owner-moved" -> owner.setCustomerId("other");
            case "inactive-target" -> target.setActive(false);
            case "disabled-customer" -> transaction.executeWithoutResult(ignored -> {
                var customer = customers.findById("customer").orElseThrow(); customer.setStatus("DISABLED"); customers.saveAndFlush(customer);
            });
            case "disabled-consumer" -> transaction.executeWithoutResult(ignored -> {
                var consumer = current(); consumer.setStatus("DISABLED"); consumers.saveAndFlush(consumer);
            });
        }
        assertThatThrownBy(() -> service.create("customer", request(null)))
            .isInstanceOfAny(CustomerSelfServiceException.class, ResponseStatusException.class);
        assertThat(consumers.count()).isEqualTo(1);
        assertThat(history.count()).isEqualTo(1);
    }

    @Test
    void platformAdminCanCreateButCustomerApiKeysAndOperatorsCannot() {
        authenticate(PlatformRole.CUSTOMER_ADMIN, owner.getEmail(), "API_KEY");
        assertThatThrownBy(() -> service.create("customer", request(null))).isInstanceOf(CustomerSelfServiceException.class);
        authenticate(PlatformRole.PLATFORM_OPERATOR, "operator-key", "API_KEY");
        assertThatThrownBy(() -> service.create("customer", request(null))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertEmpty();
        authenticate(PlatformRole.PLATFORM_ADMIN, "admin-key", "API_KEY");
        assertThat(service.create("customer", request(null)).boundTargetProfileId()).isEqualTo("target");
    }

    @Test
    void previewBeforeReleaseIsExplicitlyUnknownWithDisabledHandoverAndNoReleaseLookup() {
        var created = service.create("customer", request(null));
        clearInvocations(releases, versions);
        var result = service.previewCapabilities("customer", created.consumerId());
        assertThat(result.runtimeConfigured()).isNull();
        assertThat(result.handover()).isEqualTo(new AIWorkspaceConnectionService.PreviewHandover(false, false, false, null));
        verifyNoInteractions(releases, versions);
        assertThat(current().getBindingRevision()).isEqualTo(1);
        assertThat(history.count()).isEqualTo(1);
    }

    @Test
    void previewUsesExactAssignedPublishedBehaviorAndOnlySharedSafeIntakeAndControls() throws Exception {
        version.setBehaviorConfigJson("""
            {"executionExtensions":["CONVERSATION_HANDOVER"], "secret":"root-secret",
             "conversationHandover":{"enabled":true,"userCanCancelPending":false,"userCanResumeAi":true,
               "intake":{"anonymousAllowed":false,"privateSetting":"intake-secret",
                 "fields":[{"id":"email","label":"Email","type":"email","required":true,
                   "maxLength":254,"purpose":"Follow-up","consentRequired":true}]},
               "channel":{"providerBaseUrl":"https://private.invalid","providerApiKeySecretRef":"key-secret"},
               "actionBinding":{"marketplacePluginId":"plugin-secret"}},
             "authentication":{"token":"auth-secret"},"routes":{"queryUrl":"route-secret"}}
            """);
        var created = service.create("customer", request("release"));
        transaction.executeWithoutResult(ignored -> {
            var deployment = deployments.findById("deployment").orElseThrow();
            deployment.setActiveVersionId("newer-unassigned-version"); deployments.saveAndFlush(deployment);
        });
        var result = service.previewCapabilities("customer", created.consumerId());
        assertThat(result.runtimeConfigured()).isTrue();
        assertThat(result.handover().enabled()).isTrue();
        assertThat(result.handover().userCanCancelPending()).isFalse();
        assertThat(result.handover().userCanResumeAi()).isTrue();
        var mapper = new ObjectMapper().findAndRegisterModules();
        var shared = AIWorkspacePublicHandoverProjection.project(mapper, mapper.readTree(version.getBehaviorConfigJson()));
        assertThat(result.handover().intake()).isEqualTo(shared.path("intake"));
        assertThat(result.handover().intake().path("fields").get(0).path("required").asBoolean()).isTrue();
        assertThat(mapper.writeValueAsString(result)).doesNotContain("secret", "private.invalid", "authentication",
            "routes", "channel", "actionBinding", "contractVersion", "runtimeBaseUrl");
        verify(versions, never()).findById("newer-unassigned-version");
        assertThat(history.count()).isEqualTo(1);
        assertThat(current().getBindingRevision()).isEqualTo(1);
    }

    @Test
    void publishedBehaviorWithoutSelectedExtensionCannotEnablePreviewHandover() {
        version.setBehaviorConfigJson("{\"conversationHandover\":{\"enabled\":true,\"userCanResumeAi\":true}}");
        var created = service.create("customer", request("release"));
        var result = service.previewCapabilities("customer", created.consumerId());
        assertThat(result.runtimeConfigured()).isTrue();
        assertThat(result.handover()).isEqualTo(new AIWorkspaceConnectionService.PreviewHandover(false, false, false, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"other-customer", "foreign-owner", "disabled-owner", "disabled-consumer", "foreign-tenant",
        "foreign-release", "wrong-release-target", "foreign-version", "unpublished-version", "missing-release", "missing-version"})
    void previewDeniesForeignStaleAndInconsistentAssignmentState(String failure) {
        var created = service.create("customer", request("release"));
        String customerId = "customer";
        switch (failure) {
            case "other-customer" -> customerId = "other";
            case "foreign-owner" -> owner.setCustomerId("other");
            case "disabled-owner" -> owner.setStatus("DISABLED");
            case "disabled-consumer" -> transaction.executeWithoutResult(ignored -> {
                var consumer = current(); consumer.setStatus("DISABLED"); consumers.saveAndFlush(consumer);
            });
            case "foreign-tenant" -> tenant.setCustomerId("other");
            case "foreign-release" -> release.setDeploymentId("foreign");
            case "wrong-release-target" -> release.setTargetProfileId("production");
            case "foreign-version" -> version.setDeploymentId("foreign");
            case "unpublished-version" -> version.setStatus("DRAFT");
            case "missing-release" -> when(releases.findById("release")).thenReturn(Optional.empty());
            case "missing-version" -> when(versions.findById("version")).thenReturn(Optional.empty());
        }
        var path = customerId;
        assertThatThrownBy(() -> service.previewCapabilities(path, created.consumerId()))
            .isInstanceOfAny(CustomerSelfServiceException.class, ResponseStatusException.class);
        assertThat(history.count()).isEqualTo(1);
        assertThat(current().getBindingRevision()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "[]", "\"not an object\""})
    void malformedPublishedBehaviorIsNotReportedAsDisabledOrUnknown(String raw) {
        var created = service.create("customer", request("release"));
        version.setBehaviorConfigJson(raw);
        assert409(() -> service.previewCapabilities("customer", created.consumerId()));
        assertThat(history.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "bad/key", "key\n", "key with spaces"})
    void malformedOperationKeysAreRejectedBeforeAnyWrite(String key) {
        assertThatThrownBy(() -> service.create("customer", new CreateAIWorkspaceConnectionRequest(
            "deployment", "target", null, "Engage app", key))).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
        assertEmpty();
    }

    private CreateAIWorkspaceConnectionRequest request(String releaseId) {
        return new CreateAIWorkspaceConnectionRequest("deployment", "target", releaseId, "Engage app", "connect-1");
    }
    private PlatformConsumerEntity current() { return consumers.findByConsumerIdIgnoreCase(alias("customer", "connect-1")).orElseThrow(); }
    private void assertEmpty() { assertThat(consumers.count()).isZero(); assertThat(history.count()).isZero(); }
    private void assert409(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
    }
    private String alias(String customerId, String key) {
        return "engage-" + UUID.nameUUIDFromBytes(("ai-workspace-connection-v1\n" + customerId + "\n" + key).getBytes(StandardCharsets.UTF_8));
    }
    private DeploymentEntity deployment(String id, String customerId) {
        var value = new DeploymentEntity(); value.setId(id); value.setName(id); value.setCustomerId(customerId);
        value.setTenantId("tenant"); value.setTemplateId("template"); value.setEnvironmentName("staging");
        value.setBehaviorType("CONVERSATIONAL"); value.setStatus("DRAFT");
        value.setCreatedAt(Instant.now()); value.setUpdatedAt(Instant.now()); return value;
    }
    private DeploymentTargetProfileEntity target(String id, String environment) {
        var value = new DeploymentTargetProfileEntity(); value.setId(id); value.setEnvironmentName(environment); value.setActive(true); return value;
    }
    private void authenticate(PlatformRole role, String actor, String mode) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            new PlatformPrincipal(actor, role, actor, mode), "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    @SuppressWarnings("unchecked")
    private <T> T transactional(T target) {
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }
}
