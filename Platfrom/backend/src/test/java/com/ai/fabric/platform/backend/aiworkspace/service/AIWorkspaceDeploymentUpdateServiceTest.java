package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDeliveryMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInteractionPolicy;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

/** Real published source draft, editable draft, locks, projection and assignment resolver; publication itself is a mocked boundary. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AIWorkspaceDeploymentUpdateServiceTest {
    private static final Instant CREATED = Instant.parse("2026-10-30T00:00:00Z");
    private static final String BASE_SECURITY = """
        {"authzMode":"LOCAL","adminApiKeyEnabled":true,
         "corsAllowedOrigins":"https://existing.apps.example",
         "aiWorkspaceManagedCorsOrigins":["https://existing.apps.example"]}
        """;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private LocalContainerEntityManagerFactoryBean factory;
    private JpaTransactionManager transactions;
    private TransactionTemplate transaction;
    private EntityManager entities;
    private AIWorkspaceInstallationRepository installations;
    private PlatformConsumerRepository consumers;
    private DeploymentRepository deployments;
    private DeploymentDraftRepository drafts;
    private PlatformCustomerConsumerService authorization;
    private DeploymentService publisher;
    private AIWorkspaceDeploymentUpdateService service;
    private DeploymentReleaseRepository releases;
    private DeploymentVersionRepository versions;
    private DeploymentTargetProfileRepository targets;
    private DeploymentReleaseEntity currentRelease;
    private DeploymentVersionEntity currentVersion;
    private DeploymentTargetProfileEntity currentTarget;

    @BeforeAll
    void persistence() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:engage-reviewed-update-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setManagedTypes(PersistenceManagedTypes.of(AIWorkspaceInstallationEntity.class.getName(),
            PlatformConsumerEntity.class.getName(), DeploymentEntity.class.getName(), DeploymentDraftEntity.class.getName()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
            "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        transactions = new JpaTransactionManager(factory.getObject());
        transaction = new TransactionTemplate(transactions);
        entities = SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        var repositories = new JpaRepositoryFactory(entities);
        installations = repositories.getRepository(AIWorkspaceInstallationRepository.class);
        consumers = repositories.getRepository(PlatformConsumerRepository.class);
        deployments = repositories.getRepository(DeploymentRepository.class);
        drafts = repositories.getRepository(DeploymentDraftRepository.class);
    }

    @AfterAll
    void closePersistence() { factory.destroy(); }

    @BeforeEach
    void setup() {
        transaction.executeWithoutResult(ignored -> {
            installations.deleteAllInBatch();
            consumers.deleteAllInBatch();
            drafts.deleteAllInBatch();
            deployments.deleteAllInBatch();
            deployments.saveAndFlush(deployment("dep-1", "customer"));
            deployments.saveAndFlush(deployment("dep-foreign", "other"));
            drafts.saveAndFlush(draft("drf-published", "PUBLISHED", 1));
            drafts.saveAndFlush(draft("drf-editable", "DRAFT", 2));
            var foreign = draft("drf-foreign", "PUBLISHED", 1);
            foreign.setDeploymentId("dep-foreign");
            drafts.saveAndFlush(foreign);
            consumers.saveAndFlush(consumer("connection-1"));
            installations.saveAndFlush(installation("app-1", "connection-1"));
            var sibling = installation("app-2", "connection-1");
            sibling.setDisplayName("Second app");
            sibling.setAllowedOriginsJson("[\"https://existing.apps.example\"]");
            installations.saveAndFlush(sibling);
            var foreignApp = installation("app-foreign", "foreign-connection");
            foreignApp.setCustomerId("other");
            installations.saveAndFlush(foreignApp);
        });
        authorization = mock(PlatformCustomerConsumerService.class);
        publisher = mock(DeploymentService.class);
        releases = mock(DeploymentReleaseRepository.class);
        versions = mock(DeploymentVersionRepository.class);
        targets = mock(DeploymentTargetProfileRepository.class);
        var endpoints = mock(DeploymentReleaseEndpointService.class);
        currentRelease = new DeploymentReleaseEntity();
        currentRelease.setId("rel-current");
        currentRelease.setDeploymentId("dep-1");
        currentRelease.setDeploymentVersionId("ver-current");
        currentRelease.setTargetProfileId("target-production");
        currentRelease.setSourceArtifactId("artifact-current-immutable");
        currentRelease.setProviderType(DeploymentProviderType.COOLIFY);
        currentRelease.setStatus("APPLIED_VERIFIED");
        currentRelease.setVerificationStatus("PASSED");
        when(releases.findById("rel-current")).thenReturn(Optional.of(currentRelease));
        currentVersion = new DeploymentVersionEntity();
        currentVersion.setId("ver-current");
        currentVersion.setDeploymentId("dep-1");
        currentVersion.setSourceDraftId("drf-published");
        currentVersion.setSecurityConfigJson(BASE_SECURITY);
        // Compiled prompt artifacts deliberately differ from the editable/source-draft representation.
        currentVersion.setPromptConfigJson("{\"compiledPromptArtifact\":true}");
        currentVersion.setConfigHash("immutable-current-config");
        when(versions.findById("ver-current")).thenReturn(Optional.of(currentVersion));
        currentTarget = new DeploymentTargetProfileEntity();
        currentTarget.setId("target-production");
        currentTarget.setEnvironmentName("production");
        currentTarget.setActive(true);
        currentTarget.setProviderType(DeploymentProviderType.COOLIFY);
        when(targets.findById("target-production")).thenReturn(Optional.of(currentTarget));
        when(endpoints.runtimeBaseUrl(currentRelease)).thenReturn("https://assigned-runtime.example");
        var assignments = new AIWorkspaceAssignmentService(deployments, releases, versions, mapper, endpoints, targets);
        var properties = new PlatformAIWorkspaceProperties(Path.of("."), false, 120, Duration.ofSeconds(60),
            null, null, "apps.loomai.test", "https://engage.loomai.test");
        var projection = new AIWorkspaceDeploymentSecurityProjectionService(installations, consumers, deployments,
            drafts, new AIWorkspaceConfigurationValidator(properties), mock(PlatformAuditService.class), mapper,
            new AIWorkspaceExternalIdentityProjection(mapper, new AIWorkspaceAuthenticationProfileRegistry(mapper)));
        var implementation = new AIWorkspaceDeploymentUpdateService(installations, consumers, deployments, drafts,
            authorization, assignments, projection, publisher, mapper, entities);
        var proxy = new ProxyFactory(implementation);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        service = (AIWorkspaceDeploymentUpdateService) proxy.getProxy();
        when(publisher.publishDraft("drf-editable")).thenReturn(publishedVersion());
    }

    @Test
    void reviewProjectsOnlyWorkspaceSecurityAndUsesTheActualPublishedSourceDraft() throws Exception {
        var review = review();
        assertThat(review.changedSections()).containsExactly("Security");
        assertThat(review.changedSecurityFields()).containsExactly("aiWorkspaceManagedCorsOrigins", "corsAllowedOrigins");
        assertThat(review.addedOrigins()).containsExactly("https://ai.customer.example");
        assertThat(review.removedOrigins()).isEmpty();
        assertThat(review.unrelatedChanges()).isFalse();
        assertThat(review.affectedApps()).containsExactly("Customer workspace", "Second app");
        assertThat(review.fingerprint()).matches("[a-f0-9]{64}");
        assertThat(review.expectedBindingRevision()).isEqualTo(7);
        assertThat(drafts.findById("drf-published").orElseThrow().getStatus()).isEqualTo("PUBLISHED");
        assertThat(mapper.readTree(drafts.findById("drf-published").orElseThrow().getSecurityConfigJson()))
            .isEqualTo(mapper.readTree(BASE_SECURITY));
        assertThat(currentVersion.getPromptConfigJson()).isEqualTo("{\"compiledPromptArtifact\":true}");
        verifyNoInteractions(publisher);
    }

    @Test
    void unchangedReviewHasStableFingerprintAndPublishUsesTheExactReviewedDraft() {
        var first = review();
        var second = review();
        assertThat(second.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(service.publish("customer", "public-app-1", command(first))).isEqualTo(publishedVersion());
        verify(publisher).publishDraft("drf-editable");
        verifyNoMoreInteractions(publisher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Actions", "Entities", "Routing", "Providers", "Prompts", "Knowledge sources",
        "Workspace shell", "Marketplace datasets", "Behaviour"})
    void unrelatedSectionEditsAreIncludedInReviewAndStillRequireExactConfirmation(String section) {
        mutateDraft(draft -> changeSection(draft, section));
        var review = review();
        assertThat(review.changedSections()).contains(section, "Security");
        assertThat(review.unrelatedChanges()).isTrue();
        verifyNoInteractions(publisher);
        service.publish("customer", "public-app-1", command(review));
        verify(publisher).publishDraft("drf-editable");
    }

    @Test
    void unrelatedSecurityChangesAreNotMislabelledAsWorkspaceOnly() {
        mutateDraft(draft -> draft.setSecurityConfigJson(BASE_SECURITY.replace("\"adminApiKeyEnabled\":true",
            "\"adminApiKeyEnabled\":false")));
        var review = review();
        assertThat(review.changedSections()).containsExactly("Security");
        assertThat(review.changedSecurityFields()).contains("adminApiKeyEnabled");
        assertThat(review.unrelatedChanges()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"configuration", "revision", "contract", "app", "sibling", "bindingRevision", "release", "target"})
    void changingReviewedConfigurationAppOrAssignmentInvalidatesApproval(String change) {
        var reviewed = review();
        switch (change) {
            case "configuration" -> mutateDraft(draft -> draft.setPromptConfigJson("{\"managedProfiles\":[\"changed\"]}"));
            case "revision" -> mutateDraft(draft -> draft.setRevisionNumber(3));
            case "contract" -> mutateDraft(draft -> draft.setEntityConfigContractVersion("other-contract"));
            case "app" -> mutateApp("app-1", app -> app.setDisplayName("Changed app"));
            case "sibling" -> mutateApp("app-2", app -> app.setDisplayName("Changed sibling"));
            case "bindingRevision" -> mutateConsumer(consumer -> consumer.setBindingRevision(8));
            case "release" -> {
                var replacement = new DeploymentReleaseEntity();
                BeanUtils.copyProperties(currentRelease, replacement);
                replacement.setId("rel-replacement");
                when(releases.findById("rel-replacement")).thenReturn(Optional.of(replacement));
                mutateConsumer(consumer -> consumer.setBoundReleaseId("rel-replacement"));
            }
            case "target" -> {
                currentRelease.setTargetProfileId("target-replacement");
                var replacement = new DeploymentTargetProfileEntity();
                BeanUtils.copyProperties(currentTarget, replacement);
                replacement.setId("target-replacement");
                when(targets.findById("target-replacement")).thenReturn(Optional.of(replacement));
                mutateConsumer(consumer -> consumer.setBoundTargetProfileId("target-replacement"));
            }
            default -> throw new AssertionError(change);
        }
        assertConflict(() -> service.publish("customer", "public-app-1", command(reviewed)));
        verifyNoInteractions(publisher);
    }

    @Test
    void changedActiveDraftCannotPublishPreviouslyReviewedUnpublishedDraft() {
        var reviewed = review();
        transaction.executeWithoutResult(ignored -> {
            drafts.saveAndFlush(draft("drf-new-active", "DRAFT", 3));
            var deployment = deployments.findById("dep-1").orElseThrow();
            deployment.setActiveDraftId("drf-new-active");
            deployments.saveAndFlush(deployment);
        });
        assertConflict(() -> service.publish("customer", "public-app-1", command(reviewed)));
        verifyNoInteractions(publisher);
    }

    @Test
    void publishedSourceDraftReplayReturnsSameVersionWithoutPublishingTheNextDraft() {
        var reviewed = review();
        when(publisher.publishDraft("drf-editable")).thenAnswer(call -> {
            var source = drafts.findByIdForUpdate("drf-editable").orElseThrow();
            source.setStatus("PUBLISHED");
            source.setUpdatedAt(CREATED.plusSeconds(10));
            drafts.saveAndFlush(source);
            var next = new DeploymentDraftEntity();
            BeanUtils.copyProperties(source, next);
            next.setId("drf-next");
            next.setRevisionNumber(3);
            next.setStatus("DRAFT");
            drafts.saveAndFlush(next);
            var deployment = deployments.findByIdForUpdate("dep-1").orElseThrow();
            deployment.setActiveDraftId("drf-next");
            deployments.saveAndFlush(deployment);
            return publishedVersion();
        });
        when(publisher.listVersions("dep-1")).thenReturn(List.of(
            new DeploymentVersionSummary("unrelated-version", "dep-1", "some-other-draft", "v3", "PUBLISHED", "other", false, CREATED),
            publishedVersion()));
        var first = service.publish("customer", "public-app-1", command(reviewed));
        var replay = service.publish("customer", "public-app-1", command(reviewed));
        assertThat(replay).isEqualTo(first);
        assertThat(deployments.findById("dep-1").orElseThrow().getActiveDraftId()).isEqualTo("drf-next");
        assertThat(drafts.findById("drf-next").orElseThrow().getStatus()).isEqualTo("DRAFT");
        verify(publisher, times(1)).publishDraft("drf-editable");
        verify(publisher, never()).publishDraft("drf-next");
        verify(publisher, times(1)).listVersions("dep-1");
    }

    @Test
    void publishedDraftWithoutMatchingVersionIsAReconciliationConflictNotAnotherPublication() {
        var reviewed = review();
        mutateDraft(draft -> draft.setStatus("PUBLISHED"));
        when(publisher.listVersions("dep-1")).thenReturn(List.of());
        assertConflict(() -> service.publish("customer", "public-app-1", command(reviewed)));
        verify(publisher, never()).publishDraft(any());
    }

    @Test
    void replayStillRequiresTheOriginalAppAndBindingFingerprint() {
        var reviewed = review();
        mutateDraft(draft -> draft.setStatus("PUBLISHED"));
        mutateConsumer(consumer -> consumer.setBindingRevision(8));
        assertConflict(() -> service.publish("customer", "public-app-1", command(reviewed)));
        verifyNoInteractions(publisher);
    }

    @Test
    void reviewUsesCurrentAssignedImmutableArtifactAndTargetNotBootstrapOrLatestDeploymentPointers() {
        transaction.executeWithoutResult(ignored -> {
            var deployment = deployments.findById("dep-1").orElseThrow();
            deployment.setActiveVersionId("newer-unassigned-version");
            deployment.setSourceBranchOverride("some-new-branch");
            deployments.saveAndFlush(deployment);
        });
        var review = review();
        assertThat(review.environment()).isEqualTo("production");
        assertThat(review.targetProfileId()).isEqualTo("target-production");
        assertThat(review.releaseId()).isEqualTo("rel-current");
        assertThat(review.sourceArtifactId()).isEqualTo("artifact-current-immutable");
        verify(versions).findById("ver-current");
        verify(versions, never()).findById("newer-unassigned-version");
        service.publish("customer", "public-app-1", command(review));
        assertThat(consumers.findById("connection-1").orElseThrow().getBoundReleaseId()).isEqualTo("rel-current");
        assertThat(currentRelease.getSourceArtifactId()).isEqualTo("artifact-current-immutable");
        verify(publisher).publishDraft("drf-editable");
        verifyNoMoreInteractions(publisher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "foreign", "disabled"})
    void missingForeignAndDisabledAppsAreDeniedBeforeProjectionOrPublication(String state) {
        String app = state.equals("missing") ? "does-not-exist" : state.equals("foreign") ? "public-app-foreign" : "public-app-1";
        if (state.equals("disabled")) mutateApp("app-1", value -> value.setStatus(AIWorkspaceInstallationStatus.DISABLED));
        assertStatus(NOT_FOUND, () -> service.review("customer", app));
        assertStatus(NOT_FOUND, () -> service.publish("customer", app, plausibleCommand()));
        assertThat(drafts.findById("drf-editable").orElseThrow().getSecurityConfigJson()).isEqualTo(BASE_SECURITY);
        verifyNoInteractions(publisher, releases, versions, targets);
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled-consumer", "missing-consumer", "missing-deployment", "foreign-deployment",
        "unbound", "missing-release", "unverified-release", "missing-target", "disabled-target", "missing-version"})
    void absentDisabledOrInvalidAssignmentsCannotReviewOrPublish(String problem) {
        switch (problem) {
            case "disabled-consumer" -> mutateConsumer(value -> value.setStatus("DISABLED"));
            case "missing-consumer" -> transaction.executeWithoutResult(ignored -> consumers.deleteById("connection-1"));
            case "missing-deployment" -> mutateConsumer(value -> value.setBoundDeploymentId("missing"));
            case "foreign-deployment" -> mutateConsumer(value -> value.setBoundDeploymentId("dep-foreign"));
            case "unbound" -> mutateConsumer(value -> value.setBoundDeploymentId(null));
            case "missing-release" -> mutateConsumer(value -> value.setBoundReleaseId(null));
            case "unverified-release" -> currentRelease.setVerificationStatus("FAILED");
            case "missing-target" -> mutateConsumer(value -> value.setBoundTargetProfileId(null));
            case "disabled-target" -> currentTarget.setActive(false);
            case "missing-version" -> when(versions.findById("ver-current")).thenReturn(Optional.empty());
            default -> throw new AssertionError(problem);
        }
        assertConflict(() -> service.review("customer", "public-app-1"));
        assertConflict(() -> service.publish("customer", "public-app-1", plausibleCommand()));
        verifyNoInteractions(publisher);
    }

    @Test
    void accountAuthorizationRunsBeforeOwnedResourceLookupAndHasNoSideEffectsOnDenial() {
        when(authorization.listConsumers("customer")).thenThrow(new ResponseStatusException(FORBIDDEN, "Account access denied"));
        assertStatus(FORBIDDEN, () -> service.review("customer", "does-not-exist"));
        assertStatus(FORBIDDEN, () -> service.publish("customer", "does-not-exist", plausibleCommand()));
        verifyNoInteractions(publisher, releases, versions, targets);
        assertThat(drafts.findById("drf-editable").orElseThrow().getSecurityConfigJson()).isEqualTo(BASE_SECURITY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "unpublished", "foreign"})
    void missingOrNonPublishedBaselineNeverFallsBackToVersionPromptArtifacts(String state) {
        if (state.equals("missing")) currentVersion.setSourceDraftId("missing-draft");
        if (state.equals("foreign")) currentVersion.setSourceDraftId("drf-foreign");
        if (state.equals("unpublished")) transaction.executeWithoutResult(ignored -> {
            var baseline = drafts.findById("drf-published").orElseThrow();
            baseline.setStatus("DRAFT");
            drafts.saveAndFlush(baseline);
        });
        assertConflict(this::review);
        verifyNoInteractions(publisher);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "not-a-sha256", "BAD-FINGERPRINT"})
    void malformedFingerprintCannotPublish(String fingerprint) {
        assertStatus(BAD_REQUEST, () -> service.publish("customer", "public-app-1",
            new AIWorkspaceDeploymentUpdateService.ReviewedPublish("drf-editable", fingerprint, 7)));
        verifyNoInteractions(authorization, publisher);
    }

    @Test
    void nullRequestMissingDraftOrWrongFingerprintNeverPublishes() {
        assertStatus(BAD_REQUEST, () -> service.publish("customer", "public-app-1", null));
        assertStatus(BAD_REQUEST, () -> service.publish("customer", "public-app-1",
            new AIWorkspaceDeploymentUpdateService.ReviewedPublish(null, "a".repeat(64), 7)));
        assertConflict(() -> service.publish("customer", "public-app-1", plausibleCommand()));
        verifyNoInteractions(publisher);
    }

    @Test
    void foreignReviewedDraftIsRejectedEvenWithTheCurrentApprovalFingerprint() {
        var reviewed = review();
        assertConflict(() -> service.publish("customer", "public-app-1",
            new AIWorkspaceDeploymentUpdateService.ReviewedPublish("drf-foreign", reviewed.fingerprint(), 7)));
        verifyNoInteractions(publisher);
    }

    @Test
    void reviewMustListAppsOnOtherConsumersWhoseRuntimeOriginsAreAlsoBeingPublished() {
        transaction.executeWithoutResult(ignored -> {
            consumers.saveAndFlush(consumer("connection-2"));
            var other = installation("app-3", "connection-2");
            other.setDisplayName("App on another connection");
            other.setAllowedOriginsJson("[\"https://other.apps.example\"]");
            installations.saveAndFlush(other);
        });
        var reviewed = review();
        assertThat(reviewed.addedOrigins()).contains("https://other.apps.example");
        assertThat(reviewed.affectedApps()).contains("Customer workspace", "Second app", "App on another connection");
    }

    private AIWorkspaceDeploymentUpdateService.Review review() { return service.review("customer", "public-app-1"); }

    private AIWorkspaceDeploymentUpdateService.ReviewedPublish command(AIWorkspaceDeploymentUpdateService.Review review) {
        return new AIWorkspaceDeploymentUpdateService.ReviewedPublish(review.draftId(), review.fingerprint(), review.expectedBindingRevision());
    }

    private AIWorkspaceDeploymentUpdateService.ReviewedPublish plausibleCommand() {
        return new AIWorkspaceDeploymentUpdateService.ReviewedPublish("drf-editable", "0".repeat(64), 7);
    }

    private static void assertConflict(Runnable call) { assertStatus(CONFLICT, call); }

    private static void assertStatus(org.springframework.http.HttpStatus expected, Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(expected));
    }

    private void mutateDraft(Consumer<DeploymentDraftEntity> mutation) {
        transaction.executeWithoutResult(ignored -> {
            var draft = drafts.findById("drf-editable").orElseThrow();
            mutation.accept(draft);
            drafts.saveAndFlush(draft);
        });
    }

    private void mutateConsumer(Consumer<PlatformConsumerEntity> mutation) {
        transaction.executeWithoutResult(ignored -> {
            var consumer = consumers.findById("connection-1").orElseThrow();
            mutation.accept(consumer);
            consumers.saveAndFlush(consumer);
        });
    }

    private void mutateApp(String id, Consumer<AIWorkspaceInstallationEntity> mutation) {
        transaction.executeWithoutResult(ignored -> {
            var app = installations.findById(id).orElseThrow();
            mutation.accept(app);
            installations.saveAndFlush(app);
        });
    }

    private void changeSection(DeploymentDraftEntity draft, String section) {
        String changed = "{\"changed\":true}";
        switch (section) {
            case "Actions" -> draft.setActionsConfigJson(changed);
            case "Entities" -> draft.setEntityConfigJson(changed);
            case "Routing" -> draft.setRoutingConfigJson(changed);
            case "Providers" -> draft.setProviderConfigJson(changed);
            case "Prompts" -> draft.setPromptConfigJson(changed);
            case "Knowledge sources" -> draft.setKnowledgeSourceConfigJson(changed);
            case "Workspace shell" -> draft.setShellConfigJson(changed);
            case "Marketplace datasets" -> draft.setMarketplaceDatasetConfigJson(changed);
            case "Behaviour" -> draft.setBehaviorConfigJson(changed);
            default -> throw new AssertionError(section);
        }
    }

    private DeploymentVersionSummary publishedVersion() {
        return new DeploymentVersionSummary("ver-reviewed", "dep-1", "drf-editable", "v2", "PUBLISHED",
            "reviewed-hash", false, CREATED.plusSeconds(10));
    }

    private DeploymentDraftEntity draft(String id, String status, int revision) {
        var draft = new DeploymentDraftEntity();
        draft.setId(id); draft.setDeploymentId("dep-1"); draft.setRevisionNumber(revision); draft.setStatus(status);
        draft.setActionsConfigJson("{\"actions\":[]}");
        draft.setEntityConfigJson("{\"entities\":[]}");
        draft.setRoutingConfigJson("{\"routes\":[]}");
        draft.setProviderConfigJson("{\"provider\":\"openai\"}");
        draft.setSecurityConfigJson(BASE_SECURITY);
        draft.setPromptConfigJson("{\"managedProfiles\":[]}");
        draft.setCreatedAt(CREATED); draft.setUpdatedAt(CREATED);
        return draft;
    }

    private DeploymentEntity deployment(String id, String customer) {
        var deployment = new DeploymentEntity();
        deployment.setId(id); deployment.setName("Customer deployment"); deployment.setCustomerId(customer);
        deployment.setTenantId("tenant"); deployment.setTemplateId("generic");
        deployment.setEnvironmentName("staging"); deployment.setBehaviorType("CONVERSATIONAL");
        deployment.setStatus("DRAFT"); deployment.setActiveDraftId("drf-editable");
        deployment.setActiveVersionId("bootstrap-version");
        deployment.setCreatedAt(CREATED); deployment.setUpdatedAt(CREATED);
        return deployment;
    }

    private PlatformConsumerEntity consumer(String id) {
        var consumer = new PlatformConsumerEntity();
        consumer.setId(id); consumer.setConsumerId("public-" + id); consumer.setCustomerId("customer");
        consumer.setDisplayName("App connection"); consumer.setStatus("ACTIVE");
        consumer.setBoundDeploymentId("dep-1"); consumer.setBoundReleaseId("rel-current");
        consumer.setBoundTargetProfileId("target-production"); consumer.setBindingRevision(7);
        consumer.setCreatedAt(CREATED); consumer.setUpdatedAt(CREATED);
        return consumer;
    }

    private AIWorkspaceInstallationEntity installation(String id, String consumer) {
        var app = new AIWorkspaceInstallationEntity();
        app.setId(id); app.setInstallationId("public-" + id); app.setCustomerId("customer");
        app.setConsumerEntityId(consumer); app.setDisplayName("Customer workspace");
        app.setStatus(AIWorkspaceInstallationStatus.DRAFT); app.setDeliveryMode(AIWorkspaceDeliveryMode.STANDALONE);
        app.setAppSlug(id); app.setExperiencePackCode("generic"); app.setExperiencePackVersion("1.0.0");
        app.setConnectionMode(AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS);
        app.setConnectionProfileCode(AIWorkspaceConnectionProfileRegistry.ANONYMOUS); app.setConnectionProfileVersion("1.0.0");
        app.setInteractionPolicy(AIWorkspaceInteractionPolicy.PUBLIC);
        app.setAuthProfileCode(AIWorkspaceAuthenticationProfileRegistry.NONE);
        app.setAuthProfileVersion(AIWorkspaceAuthenticationProfileRegistry.VERSION);
        app.setAuthConfigurationJson("{}"); app.setConnectionConfigurationJson("{}"); app.setConfigurationJson("{}");
        app.setAllowedOriginsJson("[\"https://existing.apps.example\",\"https://ai.customer.example\"]");
        app.setCreatedAt(CREATED); app.setUpdatedAt(CREATED);
        return app;
    }
}
