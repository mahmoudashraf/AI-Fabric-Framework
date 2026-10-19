package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.ai.fabric.platform.backend.deployment.service.ManagedResourceOwnershipFixture.SCOPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CoolifyResourceOwnershipReconciliationTest {
    private static final String APP = "APPLICATION";
    private static final String DB = "RUNTIME_POSTGRES_DATABASE";
    private static final String APP_NAME = "runtime-dep-123";
    private static final String DB_NAME = "runtime-db-dep-123";
    private static final String PASSWORD_SECRET = "MANAGED_RUNTIME_POSTGRES_PASSWORD_DEP_DEP_123_PROFILE_TARGET_STAGE";

    private final ObjectMapper mapper = new ObjectMapper();
    private final ManagedResourceOwnershipFixture receipts = new ManagedResourceOwnershipFixture();
    private final DeploymentEntity deployment = receipts.deployment();
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    private final DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
    private final CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
    private final PlatformSecretService secrets = mock(PlatformSecretService.class);
    private final CoolifyResourceOwnership ownership = new CoolifyResourceOwnership(receipts.ownership, mapper, deployments,
        receipts.jdbc, receipts.transactions);
    private final CoolifyConnection connection = new CoolifyConnection("https://coolify.example", "synthetic-token",
        new CoolifyTargetProfileConfig("https://coolify.example", "project", "staging", "environment", "server", "destination",
            "runtime.example", "4.1.1", 1, 1, false, true, "8097", "/health", "8097"));
    private final CoolifyResourceOwnership.Location location = ownership.location(connection, "project", "environment");
    private final DeploymentTargetProfileEntity profile = new DeploymentTargetProfileEntity();
    private final FakeCoolify client = new FakeCoolify();
    private CoolifyDeploymentProvider provider;
    private DeploymentProviderResourceHandleEntity existingHandle;

    @BeforeEach
    void initialize() {
        receipts.jdbc.execute("CREATE TABLE platform_managed_product_services(details_json TEXT)");
        receipts.jdbc.execute("CREATE TABLE deployment_managed_dependency_graphs(state_json TEXT)");
        receipts.jdbc.execute("CREATE TABLE platform_deployments(id VARCHAR(64) PRIMARY KEY,customer_id VARCHAR(64),tenant_id VARCHAR(64))");
        receipts.jdbc.update("INSERT INTO platform_deployments VALUES(?,?,?)", SCOPE.deploymentId(), SCOPE.customerId(), SCOPE.tenantId());
        receipts.jdbc.execute("ALTER TABLE deployment_provider_resource_handles ADD COLUMN id VARCHAR(64) PRIMARY KEY");
        receipts.jdbc.execute("ALTER TABLE deployment_provider_resource_handles ADD COLUMN provider_type VARCHAR(64)");
        receipts.jdbc.execute("ALTER TABLE deployment_provider_resource_handles ADD COLUMN provider_project_uuid VARCHAR(255)");
        receipts.jdbc.execute("ALTER TABLE deployment_provider_resource_handles ADD COLUMN provider_environment_uuid VARCHAR(255)");
        receipts.jdbc.execute("ALTER TABLE deployment_provider_resource_handles ADD COLUMN provider_server_uuid VARCHAR(255)");
        profile.setId(SCOPE.targetProfileId());
        profile.setProviderType(DeploymentProviderType.COOLIFY);
        profile.setActive(true);
        when(targets.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(resolver.requireConnection(profile)).thenReturn(connection);
        when(deployments.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(handles.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(anyString(), anyString(), anyString()))
            .thenAnswer(call -> Optional.ofNullable(existingHandle).filter(value -> call.getArgument(2).equals(value.getResourceKind())));
        when(handles.save(any())).thenAnswer(call -> {
            existingHandle = call.getArgument(0);
            return existingHandle;
        });
        when(secrets.isManagedSecretName(PASSWORD_SECRET)).thenReturn(true);
        provider = new CoolifyDeploymentProvider(targets, handles, mock(DeploymentSourceArtifactService.class),
            mock(RailwayProvisioningPlanService.class), resolver, client, secrets, mapper);
        ReflectionTestUtils.setField(provider, "resourceOwnership", ownership);
    }

    @Test
    void exactNameDatabaseWithoutCreationReceiptCannotBeAdoptedOrHavePasswordChanged() {
        client.databases.put("foreign-db", database("foreign-db", DB_NAME));
        assertThatThrownBy(this::reconcileDatabase).hasMessageContaining("OWNERSHIP_REQUIRED");
        assertThat(client.effects).isEmpty();
        assertThat(receiptCount()).isZero();
    }

    @Test
    void exactNameApplicationWithoutCreationReceiptCannotBeAdopted() {
        client.applications.put("foreign-app", application("foreign-app", APP_NAME));
        assertThatThrownBy(this::reconcileApplication).hasMessageContaining("OWNERSHIP_REQUIRED");
        assertThat(client.effects).isEmpty();
        assertThat(receiptCount()).isZero();
    }

    @Test
    void missingOwnedDatabaseSecretCannotMintReplacementOrMutateDatabase() {
        existingHandle = handle(DB, "owned", own(DB, "owned").id());
        install(DB, "owned");
        assertThatThrownBy(this::runtimeDatabaseRequest).hasMessageContaining("RECONCILIATION_REQUIRED");
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
        assertThat(client.effects).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNED", "CREATE_PENDING"})
    void missingDatabaseSecretWithCreationEvidenceButNoHandleStillCannotMintReplacement(String state) {
        var plannedName = "ai-fabric-runtime-postgres-" + deployment.getId();
        if (state.equals("OWNED")) receipts.own("coolify", DB, ownership.parent(location), plannedName, "owned", location.endpoint());
        else assertThatThrownBy(() -> receipts.ownership.create(SCOPE, "coolify", DB, ownership.parent(location), plannedName,
            () -> { throw new IllegalStateException("uncertain"); }, value -> new ManagedResourceOwnershipService.Identity("owned", location.endpoint())))
            .hasMessageContaining("uncertain");
        assertThatThrownBy(this::runtimeDatabaseRequest).hasMessageContaining("RECONCILIATION_REQUIRED");
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
        assertThat(client.effects).isEmpty();
    }

    @Test
    void missingSecretCannotMintReplacementWhenPriorReceiptLocationHasChanged() {
        var priorLocation = new CoolifyResourceOwnership.Location(location.endpoint(), location.project(), location.environment(),
            "prior-server", location.destination());
        receipts.own("coolify", DB, ownership.parent(priorLocation), "prior-database-name", "owned", location.endpoint());
        assertThatThrownBy(this::runtimeDatabaseRequest).hasMessageContaining("RECONCILIATION_REQUIRED");
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
        assertThat(client.effects).isEmpty();
    }

    @Test
    void genuinelyNewDatabaseCanMintInitialPassword() {
        var request = runtimeDatabaseRequest();
        assertThat(request.postgresPassword()).isNotBlank();
        verify(secrets).upsertManagedSecret(anyString(), anyString(), any());
        assertThat(client.effects).isEmpty();
    }

    @Test
    void historicalLiveDatabaseCannotTriggerInitialPasswordMinting() {
        client.databases.put("historical", database("historical", "ai-fabric-runtime-postgres-" + deployment.getId()));
        assertThatThrownBy(this::runtimeDatabaseRequest).hasMessageContaining("RECONCILIATION_REQUIRED");
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
        assertThat(client.effects).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void duplicateNamesCannotSelectAnArbitraryOwnedCandidate(String kind) {
        own(kind, "owned"); install(kind, "owned"); install(kind, "other");
        assertThatThrownBy(() -> reconcile(kind)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(client.effects).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void historicalHandleDoesNotGetBackfilledEvenWhenAnOwnedReceiptExists(String kind) {
        var receipt = own(kind, "resource");
        existingHandle = handle(kind, "resource", receipt.id());
        existingHandle.setMetadataJson(metadata(kind).toString());
        install(kind, "resource");
        assertThatThrownBy(() -> reconcile(kind)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(client.effects).isEmpty();
        assertThat(client.reads).isZero();
        assertThat(receiptCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void missingOwnedUuidNeverRecoversAnotherUuidByNameOrCreatesReplacement(String kind) {
        existingHandle = handle(kind, "missing", own(kind, "missing").id());
        install(kind, "other-uuid");
        assertThatThrownBy(() -> reconcile(kind)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(client.effects).isEmpty();
        assertThat(client.lists).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void changedHandleScopeNeverDeletesOldResource(String kind) {
        existingHandle = handle(kind, "owned", own(kind, "owned").id());
        existingHandle.setProviderProjectUuid("old-project");
        install(kind, "owned");
        assertThatThrownBy(() -> reconcile(kind)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(client.effects).isEmpty();
        assertThat(client.reads).isZero();
    }

    @Test
    void appConflictDoesNotDeleteForeignApplicationOrRetryCreate() {
        client.createFailure = 409;
        client.resourceAppearsAfterFailedCreate = true;
        assertThatThrownBy(this::reconcileApplication).hasMessageContaining("CREATE_RECONCILIATION_REQUIRED");
        assertThat(client.effects).containsExactly("create-app");
        assertPending();
        assertThatThrownBy(this::reconcileApplication).hasMessageContaining("OWNERSHIP_REQUIRED");
        assertThat(client.effects).containsExactly("create-app");
        assertThat(client.applications).containsKey("created-app");
        assertPending();
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void ambiguousCreateCannotBeRecoveredByMatchingName(String kind) {
        client.createFailure = 503;
        client.resourceAppearsAfterFailedCreate = true;
        assertThatThrownBy(() -> reconcile(kind)).hasMessageContaining("CREATE_RECONCILIATION_REQUIRED");
        assertPending();
        assertThatThrownBy(() -> reconcile(kind)).hasMessageContaining("OWNERSHIP_REQUIRED");
        assertThat(client.effects).hasSize(1);
        assertPending();
    }

    @Test
    void databaseConflictCannotReturnAnUnownedDatabaseAsFallbackSuccess() {
        client.createFailure = 409;
        client.resourceAppearsAfterFailedCreate = true;
        assertThatThrownBy(this::reconcileDatabase).hasMessageContaining("CREATE_RECONCILIATION_REQUIRED");
        assertThat(client.effects).containsExactly("create-db");
        assertPending();
        assertThatThrownBy(this::reconcileDatabase).hasMessageContaining("OWNERSHIP_REQUIRED");
        assertThat(client.effects).containsExactly("create-db");
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void createWithoutLiveReadbackLeavesPendingAndNeverFabricatesSuccess(String kind) {
        client.hideCreated = true;
        assertThatThrownBy(() -> reconcile(kind)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertPending();
        client.hideCreated = false;
        assertThatThrownBy(() -> reconcile(kind)).hasMessageContaining("OWNERSHIP_REQUIRED");
        assertThat(client.effects).hasSize(1);
        assertPending();
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void mismatchedCreationReadbackCannotBecomeOwned(String kind) {
        client.wrongReadbackUuid = true;
        assertThatThrownBy(() -> reconcile(kind)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertPending();
        assertThat(client.effects).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void newResourceHasDurableIntentBeforeCreateAndOwnershipBeforeFollowingMutation(String kind) {
        reconcile(kind);
        assertThat(client.effects).containsExactly(kind.equals(DB) ? "create-db" : "create-app",
            kind.equals(DB) ? "start-db" : "update-app");
        var receipt = ownership.requireOwned(SCOPE, location, kind, name(kind), kind.equals(DB) ? "created-db" : "created-app");
        assertThat(receipt.scope()).isEqualTo(SCOPE);
        assertThat(receipt.parent()).isEqualTo(ownership.parent(location));
        assertThat(receipt.endpoint()).isEqualTo(connection.baseUrl());
        assertThat(receipt.state()).isEqualTo("OWNED");
    }

    @Test
    void publicGitApplicationIsNotUpdatedUntilCreationReceiptIsOwned() {
        var request = new CoolifyCreatePublicApplicationRequest("project", "server", "staging", "environment",
            "example/repository", "main", "dockerfile", "/", "/Dockerfile", "8097", "destination", APP_NAME,
            "synthetic", null, false, "/health", "8097", null, false, false, true, false);
        Supplier<CoolifyApplicationSummary> create = () -> ReflectionTestUtils.invokeMethod(provider, "createPublicApplication", connection, request);
        Consumer<String> update = uuid -> client.updatePublicApplication(connection, uuid, request);
        reconcileApplication(APP, create, update);
        assertThat(client.effects).containsExactly("create-app", "update-app");
        assertThat(receiptCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void exactOwnedHandleCanBeReusedAfterLiveLocationVerification(String kind) {
        existingHandle = handle(kind, "owned", own(kind, "owned").id());
        install(kind, "owned");
        reconcile(kind);
        assertThat(client.effects).containsExactly(kind.equals(DB) ? "update-db" : "update-app");
        assertThat(client.reads).isEqualTo(2);
        assertThat(client.lists).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void ownedCreationReceiptWithoutHandleCanBeReusedButNotInferredFromName(String kind) {
        own(kind, "owned");
        install(kind, "owned");
        reconcile(kind);
        assertThat(client.effects).containsExactly(kind.equals(DB) ? "update-db" : "update-app");
        assertThat(receiptCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void disappearingUpdateReadbackNeverReturnsStaleFallback(String kind) {
        existingHandle = handle(kind, "owned", own(kind, "owned").id());
        install(kind, "owned");
        client.disappearAfterUpdate = true;
        assertThatThrownBy(() -> reconcile(kind)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(client.effects).containsExactly(kind.equals(DB) ? "update-db" : "update-app");
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer", "tenant", "deployment", "target"})
    void everyReceiptOwnerDimensionMustMatchBeforeUpdateOrLifecycle(String dimension) {
        var other = new ManagedResourceOwnershipService.Scope(dimension.equals("customer") ? "other" : SCOPE.customerId(),
            dimension.equals("tenant") ? "other" : SCOPE.tenantId(), dimension.equals("deployment") ? "other" : SCOPE.deploymentId(),
            dimension.equals("target") ? "other" : SCOPE.targetProfileId());
        var receipt = receipts.own(other, "coolify", APP, ownership.parent(location), APP_NAME, "owned", location.endpoint());
        existingHandle = handle(APP, "owned", receipt.id());
        install(APP, "owned");
        assertThatThrownBy(this::reconcileApplication).hasMessageContaining("OWNERSHIP_CHANGED");
        assertThatThrownBy(() -> provider.delete(existingHandle, "test")).hasMessageContaining("OWNERSHIP_CHANGED");
        assertThatThrownBy(() -> provider.requireRetirementOwnership(existingHandle)).hasMessageContaining("OWNERSHIP_CHANGED");
        assertThatThrownBy(() -> provider.beginRetirementDeletion(existingHandle)).hasMessageContaining("OWNERSHIP_CHANGED");
        assertThatThrownBy(() -> provider.finishRetirementDeletion(existingHandle)).hasMessageContaining("OWNERSHIP_CHANGED");
        assertThat(client.effects).isEmpty();
        assertThat(client.reads).isZero();
    }

    @ParameterizedTest
    @CsvSource({"APPLICATION,start", "APPLICATION,stop", "APPLICATION,restart", "APPLICATION,delete",
        "RUNTIME_POSTGRES_DATABASE,start", "RUNTIME_POSTGRES_DATABASE,stop", "RUNTIME_POSTGRES_DATABASE,restart", "RUNTIME_POSTGRES_DATABASE,delete"})
    void lifecycleRejectsHistoricalHandlesBeforeAnyProviderMutation(String kind, String action) {
        var handle = handle(kind, "historical", null);
        install(kind, "historical");
        assertThatThrownBy(() -> action(action, handle)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(client.effects).isEmpty();
        assertThat(client.reads).isZero();
        verifyNoInteractions(secrets);
    }

    @ParameterizedTest
    @CsvSource({"APPLICATION,project_uuid", "APPLICATION,environment_uuid", "APPLICATION,server_uuid", "APPLICATION,destination_uuid",
        "APPLICATION,uuid", "APPLICATION,name", "APPLICATION,missing", "APPLICATION,contradiction",
        "RUNTIME_POSTGRES_DATABASE,project_uuid", "RUNTIME_POSTGRES_DATABASE,environment_uuid", "RUNTIME_POSTGRES_DATABASE,server_uuid",
        "RUNTIME_POSTGRES_DATABASE,destination_uuid", "RUNTIME_POSTGRES_DATABASE,uuid", "RUNTIME_POSTGRES_DATABASE,name",
        "RUNTIME_POSTGRES_DATABASE,missing", "RUNTIME_POSTGRES_DATABASE,contradiction"})
    void ownedReceiptNeverOverridesChangedOrIncompleteLivePlacement(String kind, String field) {
        var handle = handle(kind, "owned", own(kind, "owned").id());
        ObjectNode raw = install(kind, "owned");
        if (field.equals("missing")) client.evidenceTamper = "member_absent";
        else if (field.equals("contradiction")) raw.putObject("destination").put("uuid", "different");
        else raw.put(field, "different");
        assertThatThrownBy(() -> provider.delete(handle, "test")).hasMessageContaining("RECONCILIATION_REQUIRED");
        existingHandle = handle;
        assertThatThrownBy(() -> reconcile(kind)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(client.effects).isEmpty();
        verifyNoInteractions(secrets);
    }

    @ParameterizedTest
    @CsvSource({"APPLICATION,start", "APPLICATION,stop", "APPLICATION,restart", "APPLICATION,delete",
        "RUNTIME_POSTGRES_DATABASE,start", "RUNTIME_POSTGRES_DATABASE,stop", "RUNTIME_POSTGRES_DATABASE,restart", "RUNTIME_POSTGRES_DATABASE,delete",
        "CONNECTOR_APPLICATION,start", "CONNECTOR_APPLICATION,stop", "CONNECTOR_APPLICATION,restart", "CONNECTOR_APPLICATION,delete",
        "VECTORIZATION_RUNNER_APPLICATION,start", "VECTORIZATION_RUNNER_APPLICATION,stop",
        "VECTORIZATION_RUNNER_APPLICATION,restart", "VECTORIZATION_RUNNER_APPLICATION,delete"})
    void exactOwnedReceiptAndLiveLocationAllowLifecycle(String kind, String action) {
        var receipt = own(kind, "owned");
        var handle = handle(kind, "owned", receipt.id());
        existingHandle = handle;
        persist(handle);
        install(kind, "owned");
        action(action, handle);
        assertThat(client.effects).containsExactly(action + (kind.equals(DB) ? "-db" : "-app"));
        if (action.equals("delete")) {
            assertThat((kind.equals(DB) ? client.databases : client.applications).containsKey("owned")).isFalse();
            assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("DELETED");
            if (kind.equals(DB)) verify(secrets).clearManagedSecret(anyString(), any());
        }
    }

    @Test
    void ownedDatabaseCannotDeleteAnUnrelatedSecretFromTamperedMetadata() {
        var handle = handle(DB, "owned", own(DB, "owned").id());
        ObjectNode metadata = (ObjectNode) read(handle.getMetadataJson());
        metadata.put("passwordSecretName", "MANAGED_UNRELATED_SECRET");
        handle.setMetadataJson(metadata.toString());
        install(DB, "owned");
        assertThatThrownBy(() -> provider.delete(handle, "test")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(client.effects).isEmpty();
        verifyNoInteractions(secrets);
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void anotherRetainedHandleForSameUuidBlocksLifecycle(String kind) {
        var owned = handle(kind, "owned", own(kind, "owned").id());
        var other = handle(kind, "owned", null); other.setId("other-handle"); other.setDeploymentId("other-deployment");
        when(handles.findByProviderResourceUuidOrderByIdAsc("owned")).thenReturn(List.of(owned, other));
        install(kind, "owned");
        assertThatThrownBy(() -> provider.delete(owned, "test")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(client.effects).isEmpty();
        assertThat(client.reads).isZero();
    }

    @ParameterizedTest
    @CsvSource({"APPLICATION,product", "RUNTIME_POSTGRES_DATABASE,product", "APPLICATION,dependency", "RUNTIME_POSTGRES_DATABASE,dependency"})
    void sharedServiceOrDependencyRegistrationBlocksDeleteEvenWithExactReceipt(String kind, String registry) {
        var owned = handle(kind, "owned", own(kind, "owned").id());
        install(kind, "owned");
        if (registry.equals("product")) {
            ObjectNode details = mapper.createObjectNode().put(kind.equals(DB) ? "coolifyDatabaseUuid" : "coolifyApplicationUuid", "owned");
            receipts.jdbc.update("INSERT INTO platform_managed_product_services VALUES(?)", details.toString());
        } else {
            ObjectNode state = mapper.createObjectNode();
            state.putArray("nodes").addObject().put(kind.equals(DB) ? "databaseUuid" : "applicationUuid", "owned");
            receipts.jdbc.update("INSERT INTO deployment_managed_dependency_graphs VALUES(?)", state.toString());
        }
        assertThatThrownBy(() -> provider.delete(owned, "test")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(client.effects).isEmpty();
        assertThat(client.reads).isZero();
        verifyNoInteractions(secrets);
    }

    @Test
    void malformedSharedServiceReferencesCannotBeIgnoredDuringDeletion() {
        var owned = handle(APP, "owned", own(APP, "owned").id());
        install(APP, "owned");
        receipts.jdbc.update("INSERT INTO platform_managed_product_services VALUES(?)", "{broken");
        assertThatThrownBy(() -> provider.delete(owned, "test")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(client.effects).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void nestedHostedCoolifyPlacementIsAcceptedOnlyWithExactReceipt(String kind) {
        var owned = handle(kind, "owned", own(kind, "owned").id());
        ObjectNode raw = install(kind, "owned");
        raw.remove(List.of("project_uuid", "environment_uuid", "server_uuid", "destination_uuid", "project", "environment", "server"));
        raw.putObject("destination").put("uuid", "destination").putObject("server").put("uuid", "server");
        assertThat(provider.start(owned, "test").status()).isEqualTo("QUEUED");
        assertThat(client.effects).containsExactly(kind.equals(DB) ? "start-db" : "start-app");
        assertThat(client.evidenceReads).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"project_absent", "project_duplicate", "project_uuid", "project_id", "environment_absent",
        "environment_uuid", "environment_project_id", "environment_project_id_text", "environment_id", "member_absent",
        "member_duplicate", "member_environment_id", "member_environment_id_text", "member_name", "inventory_missing", "inventory_not_array"})
    void authoritativeProjectEnvironmentMembershipCannotBeReplacedByReceiptOrNames(String tamper) {
        client.evidenceTamper = tamper;
        for (String kind : List.of(APP, DB)) {
            var owned = handle(kind, "owned", own(kind, "owned").id());
            install(kind, "owned");
            assertThatThrownBy(() -> provider.delete(owned, "test")).hasMessageContaining("RECONCILIATION_REQUIRED");
            existingHandle = owned;
            assertThatThrownBy(() -> reconcile(kind)).hasMessageContaining("RECONCILIATION_REQUIRED");
        }
        assertThat(client.effects).isEmpty();
        verifyNoInteractions(secrets);
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void createWithoutAuthoritativeMembershipCannotBecomeOwnedOrContinueMutation(String kind) {
        client.evidenceTamper = "member_absent";
        assertThatThrownBy(() -> reconcile(kind)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertPending();
        assertThat(client.effects).containsExactly(kind.equals(DB) ? "create-db" : "create-app");
        client.evidenceTamper = null;
        assertThatThrownBy(() -> reconcile(kind)).hasMessageContaining("OWNERSHIP_REQUIRED");
        assertPending();
        assertThat(client.effects).hasSize(1);
    }

    @Test
    void changedControlPlaneEndpointNeverUsesReceiptFromAnotherCoolifyInstance() {
        var handle = handle(APP, "owned", own(APP, "owned").id());
        var changed = new CoolifyConnection("https://different.example", "synthetic", new CoolifyTargetProfileConfig(
            "https://different.example", "project", "staging", "environment", "server", "destination",
            null, null, 1, 1, false, true, null, null, null));
        when(resolver.requireConnection(profile)).thenReturn(changed);
        assertThatThrownBy(() -> provider.restart(handle, "test")).hasMessageContaining("OWNERSHIP_CHANGED");
        assertThat(client.effects).isEmpty();
        assertThat(client.reads).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void alreadyAbsentResourceNeedsReceiptBeforeDeleteCanReportSuccess(String kind) {
        var receipt = own(kind, "absent");
        var handle = handle(kind, "absent", receipt.id());
        persist(handle);
        assertThat(provider.delete(handle, "test").status()).isEqualTo("COMPLETED");
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("DELETED");
        assertThat(client.effects).isEmpty();
        handle.setMetadataJson(metadata(kind).toString());
        assertThatThrownBy(() -> provider.delete(handle, "test")).hasMessageContaining("RECONCILIATION_REQUIRED");
    }

    @ParameterizedTest
    @CsvSource({"APPLICATION,false", "APPLICATION,true", "RUNTIME_POSTGRES_DATABASE,false", "RUNTIME_POSTGRES_DATABASE,true"})
    void ambiguousDeleteRetainsOriginalPendingReceiptAndRecoversWithoutAdoption(String kind, boolean removed) {
        var receipt = own(kind, "owned");
        var handle = handle(kind, "owned", receipt.id());
        persist(handle);
        install(kind, "owned");
        client.deleteFailure = 503;
        client.removeBeforeDeleteFailure = removed;
        assertThatThrownBy(() -> provider.delete(handle, "test")).hasMessageContaining("ambiguous delete");
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("DELETE_PENDING");
        assertThat(receiptCount()).isEqualTo(1);
        verify(secrets, never()).clearManagedSecret(anyString(), any());
        client.deleteFailure = 0;
        assertThat(provider.delete(handle, "retry").status()).isEqualTo("COMPLETED");
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("DELETED");
        assertThat(receiptCount()).isEqualTo(1);
        assertThat(client.effects).hasSize(removed ? 1 : 2);
        if (kind.equals(DB)) verify(secrets).clearManagedSecret(anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void completedDeleteIsIdempotentButCannotDeleteReappearedUuid(String kind) {
        var receipt = own(kind, "owned");
        var handle = handle(kind, "owned", receipt.id());
        persist(handle);
        install(kind, "owned");
        provider.delete(handle, "test");
        handle.setStatus("DELETED");
        receipts.jdbc.update("UPDATE deployment_provider_resource_handles SET status='DELETED' WHERE id=?", handle.getId());
        assertThat(provider.delete(handle, "retry").status()).isEqualTo("COMPLETED");
        assertThat(client.effects).hasSize(1);
        install(kind, "owned");
        assertThatThrownBy(() -> provider.delete(handle, "reappeared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(client.effects).hasSize(1);
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("DELETED");
    }

    @ParameterizedTest
    @CsvSource({"APPLICATION,persistent", "APPLICATION,file", "RUNTIME_POSTGRES_DATABASE,persistent",
        "RUNTIME_POSTGRES_DATABASE,file", "APPLICATION,unknown", "RUNTIME_POSTGRES_DATABASE,unknown",
        "APPLICATION,unavailable", "RUNTIME_POSTGRES_DATABASE,unavailable"})
    void directDeletionCannotDestroyStorageWithoutItsSeparateRetirementProof(String kind, String storage) {
        var receipt = own(kind, "owned");
        var handle = handle(kind, "owned", receipt.id());
        persist(handle);
        install(kind, "owned");
        if ("unknown".equals(storage)) client.attachedStorages = null;
        else if ("unavailable".equals(storage)) client.storageFailure = true;
        else client.attachedStorages = List.of(new CoolifyApplicationStorageSummary("customer-storage",storage,
            "customer-source","/customer/documents","/customer/source",true,mapper.createObjectNode()));

        assertThatThrownBy(() -> provider.delete(handle, "direct-delete"))
            .hasMessageContaining("COOLIFY_RESOURCE_STORAGE_RETIREMENT_REQUIRED");

        assertThat(client.effects).isEmpty();
        assertThat(client.deleteOptions).isEmpty();
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("OWNED");
        verify(secrets, never()).clearManagedSecret(anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void storageFreeDirectDeletionNeverRequestsVolumeNetworkOrHostCleanup(String kind) {
        var receipt = own(kind, "owned");
        var handle = handle(kind, "owned", receipt.id());
        persist(handle);
        install(kind, "owned");

        assertThat(provider.delete(handle, "direct-delete").status()).isEqualTo("COMPLETED");

        assertThat(client.deleteOptions).containsExactly(List.of(true,false,false,false));
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void deleteJournalSurvivesCallerRollback(String kind) {
        var receipt = own(kind, "owned");
        var handle = handle(kind, "owned", receipt.id());
        persist(handle);
        install(kind, "owned");
        client.deleteFailure = 503;
        receipts.transaction().executeWithoutResult(tx -> {
            assertThatThrownBy(() -> provider.delete(handle, "test")).hasMessageContaining("ambiguous delete");
            tx.setRollbackOnly();
        });
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("DELETE_PENDING");
        client.deleteFailure = 0;
        receipts.transaction().executeWithoutResult(tx -> {
            provider.delete(handle, "retry");
            tx.setRollbackOnly();
        });
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("DELETED");
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void pendingDeletionCannotRestartStopReconcileOrCreateReplacement(String kind) {
        var receipt = own(kind, "owned");
        existingHandle = handle(kind, "owned", receipt.id());
        persist(existingHandle);
        install(kind, "owned");
        provider.beginRetirementDeletion(existingHandle);
        assertThatThrownBy(() -> provider.start(existingHandle, "test")).hasMessageContaining("OWNERSHIP_CHANGED");
        assertThatThrownBy(() -> provider.stop(existingHandle, "test")).hasMessageContaining("OWNERSHIP_CHANGED");
        assertThatThrownBy(() -> provider.restart(existingHandle, "test")).hasMessageContaining("OWNERSHIP_CHANGED");
        assertThatThrownBy(() -> reconcile(kind)).hasMessageContaining("OWNERSHIP_CHANGED");
        assertThat(client.effects).isEmpty();
        assertThat(receiptCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, "CONNECTOR_APPLICATION", "VECTORIZATION_RUNNER_APPLICATION", DB})
    void retirementSpiRequiresOriginalReceiptAndCannotFinishBeforeActualAbsence(String kind) {
        var receipt = own(kind, "owned");
        var handle = handle(kind, "owned", receipt.id());
        persist(handle);
        install(kind, "owned");
        provider.requireRetirementOwnership(handle);
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("OWNED");
        provider.beginRetirementDeletion(handle);
        provider.beginRetirementDeletion(handle);
        provider.requireRetirementOwnership(handle);
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("DELETE_PENDING");
        assertThatThrownBy(() -> provider.finishRetirementDeletion(handle)).hasMessageContaining("RECONCILIATION_REQUIRED");
        client.hideDirectReadback = true;
        assertThatThrownBy(() -> provider.finishRetirementDeletion(handle)).hasMessageContaining("RECONCILIATION_REQUIRED");
        (kind.equals(DB) ? client.databases : client.applications).clear();
        provider.finishRetirementDeletion(handle);
        provider.finishRetirementDeletion(handle);
        provider.requireRetirementOwnership(handle);
        provider.beginRetirementDeletion(handle);
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("DELETED");
        assertThat(client.effects).isEmpty();
        verifyNoInteractions(secrets);
        handle.setMetadataJson(metadata(kind).toString());
        assertThatThrownBy(() -> provider.requireRetirementOwnership(handle)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThatThrownBy(() -> provider.beginRetirementDeletion(handle)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThatThrownBy(() -> provider.finishRetirementDeletion(handle)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(receiptCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void retirementFinishWithoutBeginNeverMarksReceiptDeleted(String kind) {
        var receipt = own(kind, "absent");
        var handle = handle(kind, "absent", receipt.id());
        persist(handle);
        assertThatThrownBy(() -> provider.finishRetirementDeletion(handle)).hasMessageContaining("DELETE_NOT_CLAIMED");
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("OWNED");
        assertThat(client.effects).isEmpty();
        verifyNoInteractions(secrets);
    }

    @ParameterizedTest
    @CsvSource({"APPLICATION,provider_resource_uuid", "APPLICATION,deployment_id", "APPLICATION,target_profile_id",
        "APPLICATION,provider_project_uuid", "APPLICATION,provider_environment_uuid", "APPLICATION,provider_server_uuid",
        "APPLICATION,provider_type", "APPLICATION,resource_kind", "APPLICATION,metadata_json", "APPLICATION,missing",
        "RUNTIME_POSTGRES_DATABASE,provider_resource_uuid", "RUNTIME_POSTGRES_DATABASE,deployment_id",
        "RUNTIME_POSTGRES_DATABASE,target_profile_id", "RUNTIME_POSTGRES_DATABASE,provider_project_uuid",
        "RUNTIME_POSTGRES_DATABASE,provider_environment_uuid", "RUNTIME_POSTGRES_DATABASE,provider_server_uuid",
        "RUNTIME_POSTGRES_DATABASE,provider_type", "RUNTIME_POSTGRES_DATABASE,resource_kind",
        "RUNTIME_POSTGRES_DATABASE,metadata_json", "RUNTIME_POSTGRES_DATABASE,missing"})
    void deletionCannotExcludeAnUnpersistedOrChangedHandleFromReferenceChecks(String kind, String field) {
        var receipt = own(kind, "owned");
        var handle = handle(kind, "owned", receipt.id());
        if (!field.equals("missing")) {
            persist(handle);
            receipts.jdbc.update("UPDATE deployment_provider_resource_handles SET " + field + "=? WHERE id=?",
                field.equals("metadata_json") ? "{}" : "other", handle.getId());
        }
        install(kind, "owned");
        assertThatThrownBy(() -> provider.delete(handle, "test")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("OWNED");
        assertThat(client.effects).isEmpty();
        verifyNoInteractions(secrets);
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void durableReferenceCheckStillVetoesAnotherHandleNotReturnedByRepository(String kind) {
        var receipt = own(kind, "owned");
        var owned = handle(kind, "owned", receipt.id());
        persist(owned);
        var other = handle(kind, "owned", null);
        other.setId("other-handle");
        other.setDeploymentId("other-deployment");
        persist(other);
        install(kind, "owned");
        assertThatThrownBy(() -> provider.delete(owned, "test")).hasMessageContaining("REFERENCED");
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("OWNED");
        assertThat(client.effects).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, "CONNECTOR_APPLICATION", "VECTORIZATION_RUNNER_APPLICATION", DB})
    void globalJournalExcludesOnlyThePersistedValidatedSelfHandle(String kind) {
        var receipt = own(kind, "owned");
        var handle = handle(kind, "owned", receipt.id());
        persist(handle);
        assertThatThrownBy(() -> receipts.ownership.beginDeletion(receipt)).hasMessageContaining("REFERENCED");
        assertThatThrownBy(() -> receipts.ownership.beginDeletion(receipt, null)).hasMessageContaining("REFERENCED");
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("OWNED");
        receipts.ownership.beginDeletion(receipt, handle);
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("DELETE_PENDING");
        assertThat(receiptCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"APPLICATION,customer_id", "APPLICATION,tenant_id", "RUNTIME_POSTGRES_DATABASE,customer_id", "RUNTIME_POSTGRES_DATABASE,tenant_id"})
    void globalJournalReloadsPersistedCustomerAndTenantBeforeAllowingSelfExclusion(String kind, String field) {
        var receipt = own(kind, "owned");
        var handle = handle(kind, "owned", receipt.id());
        persist(handle);
        receipts.jdbc.update("UPDATE platform_deployments SET " + field + "='foreign' WHERE id=?", SCOPE.deploymentId());
        assertThatThrownBy(() -> receipts.ownership.beginDeletion(receipt, handle)).hasMessageContaining("RECONCILIATION_REQUIRED");
        install(kind, "owned");
        assertThatThrownBy(() -> provider.delete(handle, "test")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("OWNED");
        assertThat(client.effects).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void globalJournalCannotUseForeignHandleAsTheExcludedRowEvenWithCopiedReceiptMetadata(String kind) {
        var receipt = own(kind, "owned");
        var owned = handle(kind, "owned", receipt.id());
        persist(owned);
        var foreign = handle(kind, "owned", receipt.id());
        foreign.setId("foreign"); foreign.setDeploymentId("foreign-deployment");
        receipts.jdbc.update("INSERT INTO platform_deployments VALUES('foreign-deployment','foreign-customer','foreign-tenant')");
        persist(foreign);
        assertThatThrownBy(() -> receipts.ownership.beginDeletion(receipt, foreign)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("OWNED");
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void globalJournalRejectsCallerMetadataThatDoesNotMatchThePersistedHandle(String kind) {
        var receipt = own(kind, "owned");
        var owned = handle(kind, "owned", receipt.id());
        persist(owned);
        owned.setMetadataJson(metadata(kind).put("ownershipReceiptId", "unrelated-receipt").toString());
        assertThatThrownBy(() -> receipts.ownership.beginDeletion(receipt, owned)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("OWNED");
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void selfHandleExclusionDoesNotBypassOtherDurableResourceReferences(String kind) {
        var receipt = own(kind, "owned");
        var owned = handle(kind, "owned", receipt.id());
        persist(owned);
        receipts.jdbc.update("INSERT INTO platform_deployment_managed_vector_resources VALUES(?,?,?,?,?)",
            "coolify", "owned", location.endpoint(), "foreign-deployment", "foreign-release");
        assertThatThrownBy(() -> receipts.ownership.beginDeletion(receipt, owned)).hasMessageContaining("REFERENCED");
        assertThat(receipts.ownership.receipt(receipt.id()).state()).isEqualTo("OWNED");
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, DB})
    void directReadbackAbsenceCannotHideRetainedAuthoritativeMembership(String kind) {
        var owned = handle(kind, "owned", own(kind, "owned").id());
        install(kind, "owned");
        client.hideDirectReadback = true;
        assertThatThrownBy(() -> provider.delete(owned, "test")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(client.effects).isEmpty();
        verifyNoInteractions(secrets);
    }

    @ParameterizedTest
    @ValueSource(strings = {APP, "CONNECTOR_APPLICATION", "VECTORIZATION_RUNNER_APPLICATION", DB})
    void upsertRetainsExactCreationReceiptForLaterLifecycleAndRetirementFingerprint(String kind) throws Exception {
        String uuid = "owned";
        var receipt = own(kind, uuid);
        install(kind, uuid);
        var release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        DeploymentProviderResourceHandleEntity saved;
        if (kind.equals(DB)) {
            var binding = new CoolifyRuntimeDatabaseBinding("COOLIFY_POSTGRES", client.databases.get(uuid),
                "jdbc:postgresql://synthetic/runtime", "runtime_user", "synthetic-password", PASSWORD_SECRET);
            saved = ReflectionTestUtils.invokeMethod(provider, "upsertDatabaseHandle", deployment, release, profile,
                resourceScope(), connection.config(), binding);
        } else {
            saved = ReflectionTestUtils.invokeMethod(provider, "upsertHandle", deployment, release, profile, resourceScope(),
                connection.config(), kind, client.applications.get(uuid), provisioningSource(), "runtime", 0, null);
        }
        assertThat(read(saved.getMetadataJson()).path("ownershipReceiptId").asText()).isEqualTo(receipt.id());
        ownership.requireHandle(SCOPE, location, kind, name(kind), saved);
        assertThat(provider.start(saved, "test").status()).isEqualTo("QUEUED");
    }

    @Test
    void creationReceiptSurvivesCallerRollback() {
        receipts.transaction().executeWithoutResult(tx -> {
            reconcileApplication();
            tx.setRollbackOnly();
        });
        assertThat(ownership.requireOwned(SCOPE, location, APP, APP_NAME, "created-app").state()).isEqualTo("OWNED");
    }

    private Object reconcile(String kind) {
        return kind.equals(DB) ? reconcileDatabase() : reconcileApplication();
    }

    private CoolifyApplicationSummary reconcileApplication() {
        var request = new CoolifyCreateDockerImageApplicationRequest("project", "server", "staging", "environment",
            "example/runtime", "immutable", "8097", "destination", APP_NAME, "synthetic", null, false, "/health", "8097",
            null, false, true, false);
        Supplier<CoolifyApplicationSummary> create = () -> ReflectionTestUtils.invokeMethod(provider, "createDockerImageApplication", connection, request);
        Consumer<String> update = uuid -> client.updateDockerImageApplication(connection, uuid, request);
        return reconcileApplication(APP, create, update);
    }

    private CoolifyApplicationSummary reconcileApplication(String kind, Supplier<CoolifyApplicationSummary> create, Consumer<String> update) {
        return ReflectionTestUtils.invokeMethod(provider, "reconcileApplication", connection, resourceScope(), deployment,
            profile, kind, APP_NAME, create, update);
    }

    private CoolifyDatabaseSummary reconcileDatabase() {
        var request = new CoolifyCreatePostgresDatabaseRequest("project", "server", "staging", "environment", "destination",
            DB_NAME, "synthetic", "postgres:16-alpine", "runtime_user", "synthetic-password", "runtime", false, false,
            CoolifyDatabaseCapacity.of(250, 512));
        return ReflectionTestUtils.invokeMethod(provider, "reconcilePostgresDatabase", connection, resourceScope(), deployment, profile, request);
    }

    private CoolifyCreatePostgresDatabaseRequest runtimeDatabaseRequest() {
        return ReflectionTestUtils.invokeMethod(provider, "runtimePostgresDatabaseRequest", connection, resourceScope(), deployment, profile,
            mapper.createObjectNode());
    }

    private Object resourceScope() {
        return privateRecord("CoolifyResourceScope", "project", "Synthetic project", "staging", "environment", true);
    }

    private Object provisioningSource() {
        var artifact = new DeploymentSourceArtifactEntity();
        artifact.setId("artifact"); artifact.setImageRepository("example/runtime"); artifact.setImageTag("immutable");
        return privateRecord("CoolifyProvisioningSource", "DOCKER_IMAGE", artifact, null, null, null, null, null, null, null, null, null);
    }

    private Object privateRecord(String name, Object... values) {
        try {
            var type = Class.forName(CoolifyDeploymentProvider.class.getName() + "$" + name);
            var constructor = type.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return constructor.newInstance(values);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private void action(String action, DeploymentProviderResourceHandleEntity handle) {
        switch (action) {
            case "start" -> provider.start(handle, "test");
            case "stop" -> provider.stop(handle, "test");
            case "restart" -> provider.restart(handle, "test");
            case "delete" -> provider.delete(handle, "test");
            default -> throw new AssertionError(action);
        }
    }

    private ManagedResourceOwnershipService.Receipt own(String kind, String uuid) {
        return receipts.own("coolify", kind, ownership.parent(location), name(kind), uuid, location.endpoint());
    }

    private DeploymentProviderResourceHandleEntity handle(String kind, String uuid, String receiptId) {
        var handle = new DeploymentProviderResourceHandleEntity();
        handle.setId("handle"); handle.setDeploymentId(deployment.getId()); handle.setTargetProfileId(profile.getId());
        handle.setProviderType(DeploymentProviderType.COOLIFY); handle.setResourceKind(kind); handle.setProviderResourceUuid(uuid);
        handle.setProviderProjectUuid("project"); handle.setProviderEnvironmentUuid("environment"); handle.setProviderServerUuid("server");
        handle.setStatus("ACTIVE");
        ObjectNode metadata = metadata(kind);
        if (receiptId != null) metadata.put("ownershipReceiptId", receiptId);
        handle.setMetadataJson(metadata.toString());
        return handle;
    }

    private ObjectNode metadata(String kind) {
        ObjectNode value = mapper.createObjectNode().put(kind.equals(DB) ? "databaseName" : "applicationName", name(kind));
        if (kind.equals(DB)) value.put("passwordSecretName", PASSWORD_SECRET);
        return value;
    }

    private void persist(DeploymentProviderResourceHandleEntity handle) {
        receipts.jdbc.update("""
            INSERT INTO deployment_provider_resource_handles
              (id,provider_type,provider_resource_uuid,status,deployment_id,target_profile_id,resource_kind,
               provider_project_uuid,provider_environment_uuid,provider_server_uuid,metadata_json)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """, handle.getId(), handle.getProviderType().name(), handle.getProviderResourceUuid(), handle.getStatus(),
            handle.getDeploymentId(), handle.getTargetProfileId(), handle.getResourceKind(), handle.getProviderProjectUuid(),
            handle.getProviderEnvironmentUuid(), handle.getProviderServerUuid(), handle.getMetadataJson());
    }

    private ObjectNode install(String kind, String uuid) {
        if (kind.equals(DB)) {
            var database = database(uuid, DB_NAME); client.databases.put(uuid, database); return (ObjectNode) database.raw();
        }
        var application = application(uuid, APP_NAME); client.applications.put(uuid, application); return (ObjectNode) application.raw();
    }

    private static String name(String kind) { return kind.equals(DB) ? DB_NAME : APP_NAME; }

    private ObjectNode raw(String uuid, String name) {
        ObjectNode raw = mapper.createObjectNode().put("uuid", uuid).put("name", name);
        raw.putObject("destination").put("uuid", "destination").putObject("server").put("uuid", "server");
        return raw;
    }

    private CoolifyApplicationSummary application(String uuid, String name) {
        return new CoolifyApplicationSummary(uuid, name, "https://runtime.example", "running:healthy", "example/runtime", "immutable", raw(uuid, name));
    }

    private CoolifyDatabaseSummary database(String uuid, String name) {
        return new CoolifyDatabaseSummary(uuid, name, "running:healthy", "postgresql", "runtime_user", "runtime",
            raw(uuid, name).put("limits_cpus", "0.250").put("limits_memory", "512M"));
    }

    private com.fasterxml.jackson.databind.JsonNode read(String value) {
        try { return mapper.readTree(value); } catch (Exception ex) { throw new AssertionError(ex); }
    }

    private long receiptCount() { return receipts.jdbc.queryForObject("SELECT COUNT(*) FROM platform_managed_resource_ownership", Long.class); }

    private void assertPending() {
        assertThat(receiptCount()).isEqualTo(1);
        assertThat(receipts.jdbc.queryForObject("SELECT state FROM platform_managed_resource_ownership", String.class)).isEqualTo("CREATE_PENDING");
    }

    private class FakeCoolify extends CoolifyApiClient {
        final Map<String, CoolifyApplicationSummary> applications = new LinkedHashMap<>();
        final Map<String, CoolifyDatabaseSummary> databases = new LinkedHashMap<>();
        final List<String> effects = new ArrayList<>();
        final List<List<Boolean>> deleteOptions = new ArrayList<>();
        List<CoolifyApplicationStorageSummary> attachedStorages = List.of();
        boolean storageFailure;
        int reads;
        int lists;
        int evidenceReads;
        int createFailure;
        boolean resourceAppearsAfterFailedCreate;
        boolean hideCreated;
        boolean hideDirectReadback;
        boolean wrongReadbackUuid;
        boolean disappearAfterUpdate;
        int deleteFailure;
        boolean removeBeforeDeleteFailure;
        String evidenceTamper;

        FakeCoolify() { super(mapper); }

        @Override public List<CoolifyApplicationSummary> listApplications(CoolifyConnection ignored) {
            lists++; return List.copyOf(applications.values());
        }
        @Override public List<CoolifyDatabaseSummary> listDatabases(CoolifyConnection ignored) {
            lists++; return List.copyOf(databases.values());
        }
        @Override public List<CoolifyProjectSummary> listProjects(CoolifyConnection ignored) {
            evidenceReads++;
            if ("project_absent".equals(evidenceTamper)) return List.of();
            ObjectNode raw = mapper.createObjectNode().put("uuid", "project").put("id", 41);
            if ("project_uuid".equals(evidenceTamper)) raw.put("uuid", "other-project");
            if ("project_id".equals(evidenceTamper)) raw.put("id", "41");
            var project = new CoolifyProjectSummary("project", "Synthetic project", null, raw);
            return "project_duplicate".equals(evidenceTamper) ? List.of(project, project) : List.of(project);
        }
        @Override public Optional<CoolifyEnvironmentSummary> getEnvironment(CoolifyConnection ignored, String projectUuid, String environmentUuid) {
            evidenceReads++;
            assertThat(projectUuid).isEqualTo("project");
            assertThat(environmentUuid).isEqualTo("environment");
            if ("environment_absent".equals(evidenceTamper)) return Optional.empty();
            ObjectNode raw = mapper.createObjectNode().put("uuid", "environment").put("id", 73).put("project_id", 41);
            var apps = raw.putArray("applications");
            applications.values().forEach(app -> apps.add(((ObjectNode) app.raw()).deepCopy().put("environment_id", 73)));
            var postgres = raw.putArray("postgresqls");
            databases.values().forEach(database -> postgres.add(((ObjectNode) database.raw()).deepCopy().put("environment_id", 73)));
            if ("environment_uuid".equals(evidenceTamper)) raw.put("uuid", "other-environment");
            if ("environment_project_id".equals(evidenceTamper)) raw.put("project_id", 99);
            if ("environment_project_id_text".equals(evidenceTamper)) raw.put("project_id", "41");
            if ("environment_id".equals(evidenceTamper)) raw.put("id", 99);
            if ("member_absent".equals(evidenceTamper)) { apps.removeAll(); postgres.removeAll(); }
            if ("member_duplicate".equals(evidenceTamper)) {
                if (!apps.isEmpty()) apps.add(apps.get(0).deepCopy());
                if (!postgres.isEmpty()) postgres.add(postgres.get(0).deepCopy());
            }
            for (var inventory : List.of(apps, postgres)) {
                for (var item : inventory) {
                    if ("member_environment_id".equals(evidenceTamper)) ((ObjectNode) item).put("environment_id", 99);
                    if ("member_environment_id_text".equals(evidenceTamper)) ((ObjectNode) item).put("environment_id", "73");
                    if ("member_name".equals(evidenceTamper)) ((ObjectNode) item).put("name", "other-resource");
                }
            }
            if ("inventory_missing".equals(evidenceTamper)) raw.remove(List.of("applications", "postgresqls"));
            if ("inventory_not_array".equals(evidenceTamper)) { raw.putObject("applications"); raw.putObject("postgresqls"); }
            return Optional.of(new CoolifyEnvironmentSummary("environment", "staging", "project", null, raw));
        }
        @Override public Optional<CoolifyApplicationSummary> getApplication(CoolifyConnection ignored, String uuid) {
            reads++;
            if (hideDirectReadback) return Optional.empty();
            if (hideCreated && uuid.startsWith("created")) return Optional.empty();
            if (wrongReadbackUuid) return Optional.of(application("other-uuid", APP_NAME));
            return Optional.ofNullable(applications.get(uuid));
        }
        @Override public Optional<CoolifyDatabaseSummary> getDatabase(CoolifyConnection ignored, String uuid) {
            reads++;
            if (hideDirectReadback) return Optional.empty();
            if (hideCreated && uuid.startsWith("created")) return Optional.empty();
            if (wrongReadbackUuid) return Optional.of(database("other-uuid", DB_NAME));
            return Optional.ofNullable(databases.get(uuid));
        }
        @Override public String createDockerImageApplication(CoolifyConnection ignored, CoolifyCreateDockerImageApplicationRequest request) {
            return createApp();
        }
        @Override public String createPublicApplication(CoolifyConnection ignored, CoolifyCreatePublicApplicationRequest request) {
            return createApp();
        }
        private String createApp() {
            assertPending(); effects.add("create-app");
            if (createFailure == 0 || resourceAppearsAfterFailedCreate) {
                var app = application("created-app", APP_NAME);
                if (createFailure == 409) ((ObjectNode) app.raw()).put("project_uuid", "foreign-project");
                applications.put("created-app", app);
            }
            if (createFailure != 0) throw new CoolifyApiException("synthetic ambiguous create", createFailure, "/applications");
            return "created-app";
        }
        @Override public String createPostgresDatabase(CoolifyConnection ignored, CoolifyCreatePostgresDatabaseRequest request) {
            assertPending(); effects.add("create-db");
            if (createFailure == 0 || resourceAppearsAfterFailedCreate) databases.put("created-db", database("created-db", DB_NAME));
            if (createFailure != 0) throw new CoolifyApiException("synthetic ambiguous create", createFailure, "/databases/postgresql");
            return "created-db";
        }
        @Override public void updateDockerImageApplication(CoolifyConnection ignored, String uuid, CoolifyCreateDockerImageApplicationRequest request) { updateApp(uuid); }
        @Override public void updatePublicApplication(CoolifyConnection ignored, String uuid, CoolifyCreatePublicApplicationRequest request) { updateApp(uuid); }
        private void updateApp(String uuid) {
            requireProof(APP, uuid); effects.add("update-app");
            if (disappearAfterUpdate) applications.remove(uuid);
        }
        @Override public void updatePostgresDatabase(CoolifyConnection ignored, String uuid, CoolifyCreatePostgresDatabaseRequest request) {
            requireProof(DB, uuid); effects.add("update-db");
            if (disappearAfterUpdate) databases.remove(uuid);
        }
        @Override public CoolifyActionResponse start(CoolifyConnection ignored, String uuid, boolean force, boolean instant) { return effect("start-app", uuid, false); }
        @Override public CoolifyActionResponse stop(CoolifyConnection ignored, String uuid, boolean dockerCleanup) { return effect("stop-app", uuid, false); }
        @Override public CoolifyActionResponse restart(CoolifyConnection ignored, String uuid) { return effect("restart-app", uuid, false); }
        @Override public List<CoolifyApplicationStorageSummary> listApplicationStorages(CoolifyConnection ignored, String uuid) {
            if (storageFailure) throw new CoolifyApiException("synthetic storage read failure",503,"/storages");
            return attachedStorages;
        }
        @Override public List<CoolifyApplicationStorageSummary> listDatabaseStorages(CoolifyConnection ignored, String uuid) {
            return listApplicationStorages(ignored,uuid);
        }
        @Override public CoolifyActionResponse delete(CoolifyConnection ignored, String uuid, boolean configurations, boolean volumes, boolean cleanup, boolean networks) {
            deleteOptions.add(List.of(configurations,volumes,cleanup,networks));
            var result = deletionEffect("delete-app", uuid, APP);
            if (deleteFailure == 0 || removeBeforeDeleteFailure) applications.remove(uuid);
            if (deleteFailure != 0) throw new CoolifyApiException("synthetic ambiguous delete", deleteFailure, "/applications/" + uuid);
            return result;
        }
        @Override public CoolifyActionResponse startDatabase(CoolifyConnection ignored, String uuid) { return effect("start-db", uuid, true); }
        @Override public CoolifyActionResponse stopDatabase(CoolifyConnection ignored, String uuid) { return effect("stop-db", uuid, true); }
        @Override public CoolifyActionResponse restartDatabase(CoolifyConnection ignored, String uuid) { return effect("restart-db", uuid, true); }
        @Override public CoolifyActionResponse deleteDatabase(CoolifyConnection ignored, String uuid, boolean configurations, boolean volumes, boolean cleanup, boolean networks) {
            deleteOptions.add(List.of(configurations,volumes,cleanup,networks));
            var result = deletionEffect("delete-db", uuid, DB);
            if (deleteFailure == 0 || removeBeforeDeleteFailure) databases.remove(uuid);
            if (deleteFailure != 0) throw new CoolifyApiException("synthetic ambiguous delete", deleteFailure, "/databases/" + uuid);
            return result;
        }
        private CoolifyActionResponse deletionEffect(String action, String uuid, String kind) {
            if (existingHandle != null && !kind.equals(DB)) kind = existingHandle.getResourceKind();
            String receiptId = receipts.jdbc.queryForObject("SELECT id FROM platform_managed_resource_ownership WHERE resource_identity=? AND resource_kind=?",
                String.class, uuid, kind);
            var receipt = receipts.ownership.receipt(receiptId);
            assertThat(receipt.state()).isEqualTo("DELETE_PENDING");
            assertThat(receipt.scope()).isEqualTo(SCOPE);
            assertThat(receipt.provider()).isEqualTo("coolify");
            assertThat(receipt.kind()).isEqualTo(kind);
            assertThat(receipt.resourceId()).isEqualTo(uuid);
            assertThat(receipt.endpoint()).isEqualTo(location.endpoint());
            assertThat(receipt.parent()).isEqualTo(ownership.parent(location));
            assertThat(receipt.name()).isEqualTo(name(kind));
            effects.add(action);
            return new CoolifyActionResponse("synthetic", null, mapper.createObjectNode());
        }
        private CoolifyActionResponse effect(String action, String uuid, boolean database) {
            requireProof(database ? DB : APP, uuid); effects.add(action);
            return new CoolifyActionResponse("synthetic", null, mapper.createObjectNode());
        }
        private void requireProof(String kind, String uuid) {
            if (existingHandle != null && !kind.equals(DB)) kind = existingHandle.getResourceKind();
            ownership.requireOwned(SCOPE, location, kind, name(kind), uuid);
        }
    }
}
