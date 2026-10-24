package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentAiStateStorageContracts.Request;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.lifecycle.DeploymentTargetMutationService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeploymentAiStateStorageBindingServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    private final DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
    private final PlatformSecretService secrets = mock(PlatformSecretService.class);
    private final DeploymentTargetMutationService retirement = mock(DeploymentTargetMutationService.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final DeploymentAiStateStorageProbe probe = mock(DeploymentAiStateStorageProbe.class);
    private final Map<String, String> vault = new HashMap<>();
    private final DeploymentEntity deployment = new DeploymentEntity();
    private final DeploymentVersionEntity version = new DeploymentVersionEntity();
    private final DeploymentTargetProfileEntity target = new DeploymentTargetProfileEntity();
    private DeploymentProviderResourceHandleEntity saved;
    private DeploymentAiStateStorageBindingService service;
    private JdbcTemplate jdbc;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"));
        jdbc.execute("create table deployment_ai_state_schema_claims(schema_identity varchar primary key,binding_id varchar not null,"
            + "customer_id varchar not null,tenant_id varchar not null,deployment_id varchar not null,target_profile_id varchar not null)");
        jdbc.execute("create table deployment_ai_state_storage_approvals(binding_id varchar,version_id varchar,config_hash varchar,"
            + "binding_fingerprint varchar,approval_reference varchar,approved_at timestamp with time zone,primary key(binding_id,version_id))");
        jdbc.execute("create table deployment_ai_state_storage_credential_generations(binding_id varchar,version_id varchar,"
            + "generation_id varchar unique,generation_json varchar,fingerprint varchar,created_at timestamp with time zone,"
            + "primary key(binding_id,version_id),foreign key(binding_id,version_id) references deployment_ai_state_storage_approvals)");
        jdbc.execute("create table platform_deployment_releases(id varchar primary key,deployment_id varchar,"
            + "deployment_version_id varchar,target_profile_id varchar,status varchar)");
        deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        version.setId("version"); version.setDeploymentId(deployment.getId()); version.setStatus("PUBLISHED");
        version.setConfigHash("version-content-hash"); version.setProviderConfigJson("{\"aiStateStorageMode\":\"CUSTOMER_POSTGRES\"}");
        target.setId("staging"); target.setActive(true); target.setProviderType(DeploymentProviderType.COOLIFY);
        target.setResourceDefaultsJson("{}"); target.setNetworkPolicyJson("{\"aiStateStorageAllowedHosts\":[\"sql.customer.example\"]}");
        when(deployments.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        when(deployments.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(versions.findById(version.getId())).thenReturn(Optional.of(version));
        when(targets.findById(target.getId())).thenReturn(Optional.of(target));
        when(handles.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            deployment.getId(), target.getId(), DeploymentAiStateStorageBindingService.RESOURCE_KIND))
            .thenAnswer(invocation -> Optional.ofNullable(saved));
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(deployment.getId(), target.getId()))
            .thenAnswer(invocation -> saved == null ? List.of() : List.of(saved));
        when(handles.saveAndFlush(any())).thenAnswer(invocation -> { saved = invocation.getArgument(0); return saved; });
        when(secrets.resolveSecret(anyString())).thenAnswer(invocation -> vault.get(invocation.getArgument(0)));
        when(secrets.upsertManagedSecret(anyString(), anyString(), any())).thenAnswer(invocation -> {
            vault.put(invocation.getArgument(0), invocation.getArgument(1)); return null;
        });
        service = createService();
    }

    private DeploymentAiStateStorageBindingService createService() {
        return new DeploymentAiStateStorageBindingService(deployments, versions, targets, handles, secrets,
            retirement, audit, probe, jdbc, mapper);
    }

    @Test void bindsRetainedScopedStoreAndReplaysWithoutRefreshingApprovalOrProjectingSecrets() throws Exception {
        var result = service.bind(deployment.getId(), target.getId(), request());
        assertThat(result.mode()).isEqualTo("CUSTOMER_POSTGRES");
        assertThat(result.lifecycle()).isEqualTo("RETAIN_EXTERNAL");
        assertThat(result.fingerprint()).hasSize(64);
        assertThat(mapper.writeValueAsString(result)).doesNotContain("sql.customer", "private-pass", "ai_runtime", "ai_connector", "ai_user");
        assertThat(mapper.writeValueAsString(request())).doesNotContain("private-pass", "sql.customer", "ai_user", "ai_runtime");
        assertThat(saved.getMetadataJson()).doesNotContain("private-pass", "sql.customer", "ai_user", "ai_runtime", "ai_connector");
        assertThat(saved.getReleaseId()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from deployment_ai_state_schema_claims", Integer.class)).isEqualTo(2);
        var originalVault = Map.copyOf(vault);
        assertThat(service.bind(deployment.getId(), target.getId(), request())).isEqualTo(result);
        assertThat(service.get(deployment.getId(), target.getId(), version.getId())).isEqualTo(result);
        assertThat(vault).isEqualTo(originalVault);
        verify(handles, times(1)).saveAndFlush(any());
        verify(secrets, times(2)).upsertManagedSecret(anyString(), anyString(), any());
        verify(probe, times(2)).verify(any());
    }

    @Test void restartReconstructsTheExactSameReleaseIdentityAndSeparateSchemaConnections() {
        service.bind(deployment.getId(), target.getId(), request());
        var release = release(); service.freeze(deployment, version, release);
        var fingerprint = release.getAiStateStorageFingerprint();
        assertThat(release.getAiStateStorageSnapshotJson()).doesNotContain("private-pass", "sql.customer", "ai_runtime", "ai_user");
        service = createService();
        service.requireForRecovery(deployment, release);
        service.requireForApply(deployment, version, target.getId());
        service.freeze(deployment, version, release);
        assertThat(release.getAiStateStorageFingerprint()).isEqualTo(fingerprint);
        var connection = service.resolveLocal(deployment, version, target);
        assertThat(connection.managed()).isFalse();
        assertThat(connection.database()).isNull();
        assertThat(connection.jdbcUrl()).contains("sslmode=verify-full", "DefaultJavaSSLFactory", "currentSchema=ai_runtime");
        assertThat(connection.connectorJdbcUrl()).contains("currentSchema=ai_connector");
        assertThat(connection.toString()).doesNotContain("private-pass", "sql.customer", "ai_user");
    }

    @ParameterizedTest @ValueSource(strings = {"customer", "tenant", "target", "version", "configHash", "status",
        "credential", "hmac", "metadata", "store", "claim", "claimScope", "network", "profileDefaults", "profileCredential"})
    void immutableScopeAndCredentialTamperingFailsClosed(String change) throws Exception {
        service.bind(deployment.getId(), target.getId(), request());
        var release = release(); service.freeze(deployment, version, release);
        switch (change) {
            case "customer" -> deployment.setCustomerId("foreign");
            case "tenant" -> deployment.setTenantId("foreign");
            case "target" -> saved.setTargetProfileId("production");
            case "version" -> version.setDeploymentId("foreign");
            case "configHash" -> version.setConfigHash("changed");
            case "status" -> saved.setStatus("RETAINED_UNUSED");
            case "credential" -> vault.replaceAll((key, value) -> key.contains("CONNECTION") ? value.replace("private-pass", "changed") : value);
            case "hmac" -> vault.replaceAll((key, value) -> key.contains("FINGERPRINT") ? "changed" : value);
            case "metadata" -> { var value = (ObjectNode) mapper.readTree(saved.getMetadataJson());
                value.put("externalResourceOwned", true); saved.setMetadataJson(value.toString()); }
            case "store" -> saved.setProviderResourceUuid("another-database");
            case "claim" -> jdbc.update("update deployment_ai_state_schema_claims set binding_id='foreign'");
            case "claimScope" -> jdbc.update("update deployment_ai_state_schema_claims set customer_id='foreign'");
            case "network" -> target.setNetworkPolicyJson("{\"aiStateStorageAllowedHosts\":[]}");
            case "profileDefaults" -> target.setResourceDefaultsJson("{\"runtimeDatabaseCpuMillicores\":500}");
            case "profileCredential" -> target.setCredentialRefId("different-provider-credential");
        }
        clearInvocations(probe);
        assertThatThrownBy(() -> service.requireUnchanged(deployment, version, release))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(probe);
    }

    @ParameterizedTest @ValueSource(strings = {"host", "password", "runtimeSchema", "connectorSchema", "username", "database"})
    void connectionCannotBeReplacedBehindSameBinding(String field) {
        target.setNetworkPolicyJson("{\"aiStateStorageAllowedHosts\":[\"sql.customer.example\",\"other.customer.example\"]}");
        service.bind(deployment.getId(), target.getId(), request());
        var request = request();
        var changed = new Request(version.getId(), field.equals("host") ? "other.customer.example" : request.host(), 5432,
            field.equals("database") ? "another_database" : request.databaseName(),
            field.equals("runtimeSchema") ? "another_runtime" : request.runtimeSchema(),
            field.equals("connectorSchema") ? "another_connector" : request.connectorSchema(),
            field.equals("username") ? "another_role" : request.username(),
            field.equals("password") ? "another-pass" : request.password(), true, "approval");
        clearInvocations(probe, secrets, handles);
        assertThatThrownBy(() -> service.bind(deployment.getId(), target.getId(), changed))
            .hasMessageContaining(field.equals("username") || field.equals("password")
                ? "VERSION_CREDENTIALS_IMMUTABLE" : "BINDING_IMMUTABLE");
        verifyNoInteractions(probe);
        verify(handles, never()).saveAndFlush(any());
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
    }

    @Test void eachPublishedVersionRequiresItsOwnApprovalAndConfigHash() {
        service.bind(deployment.getId(), target.getId(), request());
        version.setId("version-2"); version.setConfigHash("hash-2");
        when(versions.findById(version.getId())).thenReturn(Optional.of(version));
        assertThatThrownBy(() -> service.resolveLocal(deployment, version, target)).hasMessageContaining("VERSION_APPROVAL_REQUIRED");
        var result = service.bind(deployment.getId(), target.getId(), request());
        assertThat(result.versionId()).isEqualTo("version-2");
        assertThat(jdbc.queryForObject("select count(*) from deployment_ai_state_storage_approvals", Integer.class)).isEqualTo(2);
        verify(handles, times(1)).saveAndFlush(any());
    }

    @ParameterizedTest @ValueSource(strings = {"public", "information_schema", "pg_catalog", "ai_connector", "unsafe;schema"})
    void rejectsSharedSystemOrInvalidSchemasBeforeConnecting(String schema) {
        var request = request();
        assertThatThrownBy(() -> service.bind(deployment.getId(), target.getId(), new Request(version.getId(),
            request.host(), request.port(), request.databaseName(), schema, request.connectorSchema(), request.username(),
            request.password(), true, "approval"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(probe);
    }

    @Test void consentHostApprovalAndPublishedScopeAreMandatory() {
        var request = request();
        assertThatThrownBy(() -> service.bind(deployment.getId(), target.getId(), new Request(version.getId(),
            request.host(), request.port(), request.databaseName(), request.runtimeSchema(), request.connectorSchema(),
            request.username(), request.password(), false, "approval"))).hasMessageContaining("MIGRATION_AUTHORIZATION_REQUIRED");
        target.setNetworkPolicyJson("{\"aiStateStorageAllowedHosts\":[\"*.customer.example\"]}");
        assertThatThrownBy(() -> service.bind(deployment.getId(), target.getId(), request())).hasMessageContaining("ENDPOINT_NOT_APPROVED");
        target.setNetworkPolicyJson("{\"aiStateStorageAllowedHosts\":[\"sql.customer.example\"]}");
        version.setStatus("DRAFT");
        assertThatThrownBy(() -> service.bind(deployment.getId(), target.getId(), request())).hasMessageContaining("PUBLISHED_VERSION_REQUIRED");
        verifyNoInteractions(probe);
    }

    @Test void noManagedCreationOrRawJdbcOverlayIsUsedAsExternalStorage() {
        target.setResourceDefaultsJson("{\"runtimeDatabaseJdbcUrl\":\"jdbc:postgresql://unsafe/db\"}");
        assertThatThrownBy(() -> service.bind(deployment.getId(), target.getId(), request())).hasMessageContaining("RAW_JDBC_OVERRIDE_FORBIDDEN");
        target.setResourceDefaultsJson("{}");
        var managed = new DeploymentProviderResourceHandleEntity(); managed.setResourceKind("RUNTIME_POSTGRES_DATABASE"); managed.setStatus("ACTIVE");
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(deployment.getId(), target.getId())).thenReturn(List.of(managed));
        assertThatThrownBy(() -> service.bind(deployment.getId(), target.getId(), request())).hasMessageContaining("EXISTING_MANAGED_STORE_REQUIRES_MIGRATION");
        verifyNoInteractions(probe);
    }

    @Test void modeSwitchCannotSilentlyAbandonExistingCustomerState() {
        service.bind(deployment.getId(), target.getId(), request());
        version.setProviderConfigJson("{\"aiStateStorageMode\":\"MANAGED_POSTGRES\"}");
        assertThatThrownBy(() -> service.requireForApply(deployment, version, target.getId()))
            .hasMessageContaining("EXISTING_CUSTOMER_STORE_REQUIRES_MIGRATION");
    }

    @ParameterizedTest @ValueSource(strings = {"username", "password"})
    void attemptedRotationCannotRewriteVaultVersionApprovalOrFrozenRelease(String field) {
        var original = service.bind(deployment.getId(), target.getId(), request());
        var release = release(); service.freeze(deployment, version, release);
        String snapshot = release.getAiStateStorageSnapshotJson();
        String fingerprint = release.getAiStateStorageFingerprint();
        var originalVault = Map.copyOf(vault);
        String originalMetadata = saved.getMetadataJson();
        var approval = jdbc.queryForMap("select * from deployment_ai_state_storage_approvals");
        var request = request();
        var rotation = new Request(version.getId(), request.host(), request.port(), request.databaseName(),
            request.runtimeSchema(), request.connectorSchema(), field.equals("username") ? "new_role" : request.username(),
            field.equals("password") ? "new-secret-credential" : request.password(), true, "new-approval-must-not-rewrite-old");
        clearInvocations(probe, secrets, handles, audit);

        assertThatThrownBy(() -> service.bind(deployment.getId(), target.getId(), rotation))
            .hasMessageContaining("AI_STATE_STORAGE_APPROVAL_IMMUTABLE")
            .hasMessageNotContaining("new-secret-credential").hasMessageNotContaining("new_role");
        assertThat(vault).isEqualTo(originalVault);
        assertThat(saved.getMetadataJson()).isEqualTo(originalMetadata);
        assertThat(jdbc.queryForMap("select * from deployment_ai_state_storage_approvals")).isEqualTo(approval);
        assertThat(release.getAiStateStorageSnapshotJson()).isEqualTo(snapshot);
        assertThat(release.getAiStateStorageFingerprint()).isEqualTo(fingerprint);
        service.requireUnchanged(deployment, version, release);
        assertThat(service.get(deployment.getId(), target.getId(), version.getId())).isEqualTo(original);
        verifyNoInteractions(probe, audit);
        verify(handles, never()).saveAndFlush(any());
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
    }

    @Test void newPublishedVersionStagesANewGenerationWithoutChangingTheLiveRelease() {
        var original = service.bind(deployment.getId(), target.getId(), request());
        var oldRelease = release(); service.freeze(deployment, version, oldRelease);
        String oldSnapshot = oldRelease.getAiStateStorageSnapshotJson();
        String oldFingerprint = oldRelease.getAiStateStorageFingerprint();
        var originalVault = Map.copyOf(vault);
        String originalMetadata = saved.getMetadataJson();
        var originalApproval = jdbc.queryForMap("select * from deployment_ai_state_storage_approvals");
        var originalGeneration = jdbc.queryForMap("select * from deployment_ai_state_storage_credential_generations");
        var originalClaims = jdbc.queryForList("select * from deployment_ai_state_schema_claims order by schema_identity");
        deployment.setActiveVersionId(version.getId());
        jdbc.update("insert into platform_deployment_releases values (?,?,?,?,?)", "old-release", deployment.getId(),
            version.getId(), target.getId(), "ACTIVE");
        var candidate = published("candidate");
        var input = credentials(candidate, "new_ai_role", "new-private-password");
        clearInvocations(handles, secrets);

        var staged = service.bind(deployment.getId(), target.getId(), input);
        assertThat(staged.bindingId()).isEqualTo(original.bindingId());
        assertThat(staged.fingerprint()).isNotEqualTo(original.fingerprint());
        assertThat(saved.getMetadataJson()).isEqualTo(originalMetadata);
        assertThat(vault).containsAllEntriesOf(originalVault).hasSize(4);
        assertThat(jdbc.queryForMap("select * from deployment_ai_state_storage_approvals where version_id=?", version.getId()))
            .isEqualTo(originalApproval);
        assertThat(jdbc.queryForMap("select * from deployment_ai_state_storage_credential_generations where version_id=?", version.getId()))
            .isEqualTo(originalGeneration);
        assertThat(jdbc.queryForList("select * from deployment_ai_state_schema_claims order by schema_identity")).isEqualTo(originalClaims);
        assertThat(service.resolveLocal(deployment, version, target).password()).isEqualTo("private-pass");
        assertThat(service.resolveLocal(deployment, candidate, target).password()).isEqualTo("new-private-password");
        assertThat(service.resolveLocal(deployment, candidate, target).username()).isEqualTo("new_ai_role");
        verify(handles, never()).saveAndFlush(any());
        verify(secrets, times(2)).upsertManagedSecret(anyString(), anyString(), any());

        var candidateRelease = release(); candidateRelease.setId("candidate-release");
        candidateRelease.setDeploymentVersionId(candidate.getId());
        service.freeze(deployment, candidate, candidateRelease);
        assertThat(candidateRelease.getAiStateStorageSnapshotJson()).contains("credentialGenerationId");
        service = createService();
        service.requireForRecovery(deployment, oldRelease);
        service.requireForRecovery(deployment, candidateRelease);
        assertThat(oldRelease.getAiStateStorageSnapshotJson()).isEqualTo(oldSnapshot);
        assertThat(oldRelease.getAiStateStorageFingerprint()).isEqualTo(oldFingerprint);
        assertThat(service.bind(deployment.getId(), target.getId(), input)).isEqualTo(staged);
        assertThat(service.get(deployment.getId(), target.getId(), version.getId())).isEqualTo(original);
        assertThat(vault).hasSize(4);
    }

    @ParameterizedTest @ValueSource(strings = {"APPLY_REQUESTED", "PROVISIONING", "ACTIVE", "FAILED", "ACTIVATION_UNCONFIRMED", "UNKNOWN"})
    void noNewGenerationForAnyPreviouslyQueuedOrAppliedTargetVersion(String status) {
        service.bind(deployment.getId(), target.getId(), request());
        var candidate = published("candidate");
        jdbc.update("insert into platform_deployment_releases values (?,?,?,?,?)", "queued-release", deployment.getId(),
            candidate.getId(), target.getId(), status);
        var originalVault = Map.copyOf(vault);
        clearInvocations(probe, secrets, handles);

        assertThatThrownBy(() -> service.bind(deployment.getId(), target.getId(), credentials(candidate, "new_ai_role", "new-private-password")))
            .hasMessageContaining("NEVER_APPLIED_VERSION_REQUIRED");
        assertThat(vault).isEqualTo(originalVault);
        verifyNoInteractions(probe, secrets);
        verify(handles, never()).saveAndFlush(any());
        assertThat(jdbc.queryForObject("select count(*) from deployment_ai_state_storage_credential_generations", Integer.class)).isEqualTo(1);
    }

    @Test void unknownLegacyTargetHistoryAndActiveVersionCannotBeTreatedAsNeverApplied() {
        var candidate = published("candidate");
        jdbc.update("insert into platform_deployment_releases values (?,?,?,?,?)", "legacy-release", deployment.getId(),
            candidate.getId(), null, "UNKNOWN");
        assertThatThrownBy(() -> service.bind(deployment.getId(), target.getId(), credentials(candidate, "new_ai_role", "new-private-password")))
            .hasMessageContaining("NEVER_APPLIED_VERSION_REQUIRED");
        jdbc.update("delete from platform_deployment_releases");
        deployment.setActiveVersionId(candidate.getId());
        assertThatThrownBy(() -> service.bind(deployment.getId(), target.getId(), credentials(candidate, "new_ai_role", "new-private-password")))
            .hasMessageContaining("NEVER_APPLIED_VERSION_REQUIRED");
        verifyNoInteractions(probe, secrets);
    }

    @Test void candidateProbeFailureDoesNotChangeAnyLiveCredentialOrApproval() {
        service.bind(deployment.getId(), target.getId(), request());
        var oldRelease = release(); service.freeze(deployment, version, oldRelease);
        var candidate = published("candidate");
        var originalVault = Map.copyOf(vault);
        var originalApprovals = jdbc.queryForList("select * from deployment_ai_state_storage_approvals");
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.PRECONDITION_FAILED,
            "AI_STATE_STORAGE_CONNECTION_VERIFICATION_FAILED")).when(probe).verify(any());
        clearInvocations(secrets, handles, audit);

        assertThatThrownBy(() -> service.bind(deployment.getId(), target.getId(), credentials(candidate, "new_ai_role", "new-private-password")))
            .hasMessageContaining("CONNECTION_VERIFICATION_FAILED").hasMessageNotContaining("new-private-password");
        assertThat(vault).isEqualTo(originalVault);
        assertThat(jdbc.queryForList("select * from deployment_ai_state_storage_approvals")).isEqualTo(originalApprovals);
        service.requireForRecovery(deployment, oldRelease);
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
        verify(handles, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test void exactReplayAfterQueuingIsReadOnlyAndChangedCredentialsStillFail() {
        var original = service.bind(deployment.getId(), target.getId(), request());
        jdbc.update("insert into platform_deployment_releases values (?,?,?,?,?)", "queued-release", deployment.getId(),
            version.getId(), target.getId(), "APPLY_REQUESTED");
        clearInvocations(secrets, handles, audit);
        assertThat(service.bind(deployment.getId(), target.getId(), request())).isEqualTo(original);
        assertThatThrownBy(() -> service.bind(deployment.getId(), target.getId(), credentials(version, "new_ai_role", "new-private-password")))
            .hasMessageContaining("VERSION_CREDENTIALS_IMMUTABLE");
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
        verify(handles, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test void approvalFreezesTheTargetProfileEvenBeforeAReleaseIsCreated() {
        service.bind(deployment.getId(), target.getId(), request());
        target.setResourceDefaultsJson("{\"runtimeDatabaseCpuMillicores\":500}");
        clearInvocations(probe, secrets);
        assertThatThrownBy(() -> service.requireForApply(deployment, version, target.getId()))
            .hasMessageContaining("GENERATION_SCOPE_CHANGED");
        verifyNoInteractions(probe, secrets);
    }

    @Test void candidateCannotSwapTheRuntimeAndConnectorSchemaEvenThoughItIsTheSamePair() {
        service.bind(deployment.getId(), target.getId(), request());
        var candidate = published("candidate"); var request = request();
        clearInvocations(probe, secrets);
        assertThatThrownBy(() -> service.bind(deployment.getId(), target.getId(), new Request(candidate.getId(), request.host(),
            request.port(), request.databaseName(), request.connectorSchema(), request.runtimeSchema(), request.username(),
            request.password(), true, "approval"))).hasMessageContaining("BINDING_IMMUTABLE");
        verifyNoInteractions(probe, secrets);
    }

    @Test void anotherTargetsReleaseDoesNotPreventANewTargetsVersionApprovalDuringPromotion() {
        deployment.setActiveVersionId(version.getId());
        jdbc.update("insert into platform_deployment_releases values (?,?,?,?,?)", "other-target-release", deployment.getId(),
            version.getId(), "other-staging-target", "ACTIVE");
        assertThat(service.bind(deployment.getId(), target.getId(), request()).versionId()).isEqualTo(version.getId());
    }

    @Test void newGenerationDoesNotDependOnTheOldGenerationsVaultEntries() throws Exception {
        service.bind(deployment.getId(), target.getId(), request());
        var oldVault = Map.copyOf(vault);
        var candidate = published("candidate");
        service.bind(deployment.getId(), target.getId(), credentials(candidate, "new_ai_role", "new-private-password"));
        oldVault.keySet().forEach(vault::remove);
        assertThat(service.resolveLocal(deployment, candidate, target).password()).isEqualTo("new-private-password");
        assertThatThrownBy(() -> service.resolveLocal(deployment, version, target)).hasMessageContaining("CREDENTIALS_REQUIRED");
        // Losing old secrets is a recovery fault, not permission to substitute the candidate's credentials.
        vault.putAll(oldVault);
        assertThat(service.resolveLocal(deployment, version, target).password()).isEqualTo("private-pass");
    }

    @Test void invalidCandidateCannotFallBackToTheLiveGenerationsWorkingCredentials() {
        var original = service.bind(deployment.getId(), target.getId(), request());
        var oldVault = Map.copyOf(vault);
        var candidate = published("candidate");
        service.bind(deployment.getId(), target.getId(), credentials(candidate, "new_ai_role", "new-private-password"));
        var candidateRelease = release(); candidateRelease.setId("candidate-release");
        candidateRelease.setDeploymentVersionId(candidate.getId()); service.freeze(deployment, candidate, candidateRelease);
        vault.replaceAll((name, value) -> oldVault.containsKey(name) ? value : value.replace("new-private-password", "tampered"));
        clearInvocations(probe);
        assertThatThrownBy(() -> service.requireForApply(deployment, candidate, target.getId())).hasMessageContaining("CREDENTIALS_CHANGED");
        assertThatThrownBy(() -> service.requireForRecovery(deployment, candidateRelease)).hasMessageContaining("CREDENTIALS_CHANGED");
        assertThat(service.get(deployment.getId(), target.getId(), version.getId())).isEqualTo(original);
        verifyNoInteractions(probe);
    }

    @ParameterizedTest @ValueSource(strings = {"row-version", "generation-id", "scope-rehashed", "secret-ref-rehashed"})
    void tamperedCredentialGenerationCannotChangeItsVersionOrScopedSecretSelection(String change) throws Exception {
        service.bind(deployment.getId(), target.getId(), request());
        var oldRelease = release(); service.freeze(deployment, version, oldRelease);
        var row = jdbc.queryForMap("select * from deployment_ai_state_storage_credential_generations");
        var generation = (ObjectNode) mapper.readTree(row.get("GENERATION_JSON").toString());
        if (change.equals("row-version")) {
            var candidate = published("candidate");
            jdbc.update("insert into deployment_ai_state_storage_approvals values (?,?,?,?,?,?)", saved.getId(), candidate.getId(),
                candidate.getConfigHash(), row.get("FINGERPRINT"), "approval", java.sql.Timestamp.from(java.time.Instant.now()));
            jdbc.update("update deployment_ai_state_storage_credential_generations set version_id=?", candidate.getId());
        } else if (change.equals("generation-id")) {
            jdbc.update("update deployment_ai_state_storage_credential_generations set generation_id=?", "asg-" + UUID.randomUUID());
        } else {
            if (change.equals("scope-rehashed")) generation.put("approvalReference", "re-signed-public-metadata");
            else generation.put("connectionSecretRef", "MANAGED_FOREIGN_SECRET");
            String fingerprint = DeploymentEvidenceFingerprint.hash(mapper, generation);
            jdbc.update("update deployment_ai_state_storage_credential_generations set generation_json=?,fingerprint=?", generation.toString(), fingerprint);
            jdbc.update("update deployment_ai_state_storage_approvals set binding_fingerprint=?", fingerprint);
        }
        clearInvocations(probe);
        assertThatThrownBy(() -> service.requireForRecovery(deployment, oldRelease))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(probe);
    }

    @Test void equalPasswordsAcrossVersionsStillUseDistinctPrivateKeysSecretsAndPublicFingerprints() {
        var original = service.bind(deployment.getId(), target.getId(), request());
        var candidate = published("candidate");
        var replacement = service.bind(deployment.getId(), target.getId(), credentials(candidate, "ai_user", "private-pass"));
        assertThat(replacement.fingerprint()).isNotEqualTo(original.fingerprint());
        assertThat(vault).hasSize(4);
        assertThat(vault.entrySet().stream().filter(entry -> entry.getKey().contains("FINGERPRINT")).map(Map.Entry::getValue).distinct()).hasSize(2);
        assertThat(jdbc.queryForList("select generation_json from deployment_ai_state_storage_credential_generations", String.class))
            .allSatisfy(json -> assertThat(json).doesNotContain("private-pass", "sql.customer.example", "ai_runtime", "ai_user"));
    }

    @Test void activeApplyDoesNotBecomeAnImplicitCredentialRotationPermit() {
        service.bind(deployment.getId(), target.getId(), request());
        var originalVault = Map.copyOf(vault);
        String originalMetadata = saved.getMetadataJson();
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
            "RELEASE_APPLY_IN_PROGRESS")).when(retirement).requireAvailableForMutation(deployment.getId(), target.getId());
        var request = request();
        clearInvocations(probe, secrets, handles, audit);

        assertThatThrownBy(() -> service.bind(deployment.getId(), target.getId(), new Request(version.getId(),
            request.host(), request.port(), request.databaseName(), request.runtimeSchema(), request.connectorSchema(),
            request.username(), "new-secret-credential", true, "approval"))).hasMessageContaining("RELEASE_APPLY_IN_PROGRESS");
        assertThat(vault).isEqualTo(originalVault);
        assertThat(saved.getMetadataJson()).isEqualTo(originalMetadata);
        verifyNoInteractions(probe, secrets, audit);
        verify(handles, never()).saveAndFlush(any());
    }

    @Test void aSecondBindingCannotClaimEitherExistingSchemaEvenWithDifferentPair() {
        service.bind(deployment.getId(), target.getId(), request());
        saved = null;
        var request = request();
        assertThatThrownBy(() -> service.bind(deployment.getId(), target.getId(), new Request(version.getId(),
            request.host(), request.port(), request.databaseName(), request.runtimeSchema(), "another_connector",
            request.username(), request.password(), true, "approval"))).hasMessageContaining("SCHEMA_ALREADY_BOUND");
    }

    @Test void frozenReleaseCannotBeReboundToAnotherTargetOrRewrittenSnapshot() {
        service.bind(deployment.getId(), target.getId(), request());
        var release = release(); service.freeze(deployment, version, release);
        release.setAiStateStorageSnapshotJson("{}");
        assertThatThrownBy(() -> service.requireForRecovery(deployment, release)).hasMessageContaining("RELEASE_BINDING_CHANGED");
        release.setTargetProfileId("production");
        assertThatThrownBy(() -> service.requireForRecovery(deployment, release)).hasMessageContaining("TARGET_REQUIRED");
    }

    private Request request() {
        return new Request(version.getId(), "sql.customer.example", 5432, "customer_database", "ai_runtime", "ai_connector",
            "ai_user", "private-pass", true, "approval");
    }
    private Request credentials(DeploymentVersionEntity selected, String username, String password) {
        var original = request();
        return new Request(selected.getId(), original.host(), original.port(), original.databaseName(), original.runtimeSchema(),
            original.connectorSchema(), username, password, true, "approval");
    }
    private DeploymentVersionEntity published(String id) {
        var candidate = new DeploymentVersionEntity(); candidate.setId(id); candidate.setDeploymentId(deployment.getId());
        candidate.setStatus("PUBLISHED"); candidate.setConfigHash("content-" + id);
        candidate.setProviderConfigJson(version.getProviderConfigJson());
        when(versions.findById(id)).thenReturn(Optional.of(candidate));
        return candidate;
    }
    private DeploymentReleaseEntity release() {
        var release = new DeploymentReleaseEntity(); release.setId("release"); release.setDeploymentId(deployment.getId());
        release.setDeploymentVersionId(version.getId()); release.setTargetProfileId(target.getId()); return release;
    }
}
