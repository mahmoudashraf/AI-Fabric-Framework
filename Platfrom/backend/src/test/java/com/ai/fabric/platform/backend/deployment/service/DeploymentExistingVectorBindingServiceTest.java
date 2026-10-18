package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.AuthenticationType;
import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.Request;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DeploymentExistingVectorBindingServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    private final DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
    private final PlatformSecretService secrets = mock(PlatformSecretService.class);
    private final ZillizCloudControlPlaneClient zilliz = mock(ZillizCloudControlPlaneClient.class);
    private final TargetRetirementGateService retirement = mock(TargetRetirementGateService.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final TenantScopedVectorHandleResolver scopes = new TenantScopedVectorHandleResolver();
    private final Map<String, String> vault = new HashMap<>();
    private final DeploymentEntity deployment = new DeploymentEntity();
    private final DeploymentVersionEntity version = new DeploymentVersionEntity();
    private DeploymentProviderResourceHandleEntity saved;
    private DeploymentExistingVectorBindingService service;
    private ObjectNode config;
    private static final String TARGET = "target-staging";
    private static final String CLUSTER = "cluster-free";
    private static final String ENDPOINT = "https://cluster-free.serverless.aws-eu-central-1.cloud.zilliz.com";

    @BeforeEach void setup() {
        deployment.setId("dep-test"); deployment.setCustomerId("cus-test"); deployment.setTenantId("tenant-test");
        version.setId("ver-test"); version.setDeploymentId(deployment.getId()); version.setStatus("PUBLISHED"); version.setConfigHash("hash-test");
        version.setEntityConfigJson("{\"ai-entities\":{\"vehicle\":{},\"document\":{}}}");
        config = mapper.createObjectNode().put("vectorStrategy", "milvus").put("vectorProvisioningMode", "EXTERNAL_EXISTING")
            .put("vectorStoragePosture", "SHARED").put("milvusExternalBindingRequired", true).put("milvusSecure", true);
        version.setProviderConfigJson(config.toString());
        var target = new DeploymentTargetProfileEntity(); target.setId(TARGET); target.setActive(true); target.setProviderType(DeploymentProviderType.COOLIFY);
        when(deployments.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        when(versions.findById(version.getId())).thenReturn(Optional.of(version));
        when(targets.findById(TARGET)).thenReturn(Optional.of(target));
        when(handles.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(eq(deployment.getId()), eq(TARGET), anyString()))
            .thenAnswer(invocation -> Optional.ofNullable(saved));
        when(handles.saveAndFlush(any())).thenAnswer(invocation -> { saved = invocation.getArgument(0); return saved; });
        vault.put("ZILLIZ_CLOUD_API_KEY", "control-plane-only");
        when(secrets.resolveSecret(anyString())).thenAnswer(invocation -> vault.get(invocation.getArgument(0)));
        when(secrets.upsertManagedSecret(anyString(), anyString(), any())).thenAnswer(invocation -> {
            vault.put(invocation.getArgument(0), invocation.getArgument(1)); return null;
        });
        when(zilliz.getCluster(CLUSTER, "control-plane-only")).thenReturn(cluster("Free", ENDPOINT));
        when(zilliz.listServingCollections(ENDPOINT, "db_admin", "serving-password")).thenReturn(java.util.List.of());
        service = new DeploymentExistingVectorBindingService(deployments, versions, targets, handles, secrets, zilliz, scopes, retirement, audit, mapper);
    }

    @Test void bindsOnceAndResolvesScopedRetainedConnectionWithoutProviderMutationOrCredentialProjection() {
        var result = service.bind(deployment.getId(), TARGET, request());
        assertThat(result.lifecycle()).isEqualTo("RETAIN_EXTERNAL");
        assertThat(result.deploymentOption()).isEqualTo("Free");
        assertThat(result.authType()).isEqualTo(AuthenticationType.USERNAME_PASSWORD);
        assertThat(result.collectionPrefix()).isEqualTo(scopes.targetScopedProviderConfig(deployment, config, TARGET).path("milvusCollectionPrefix").asText());
        assertThat(result.toString()).doesNotContain("serving-password", "control-plane-only");
        assertThat(saved.getMetadataJson()).doesNotContain("serving-password", "control-plane-only");
        String fingerprintKey = vault.entrySet().stream().filter(entry -> entry.getKey().startsWith("MANAGED_EXTERNAL_VECTOR_FINGERPRINT_"))
            .findFirst().orElseThrow().getValue();
        assertThat(saved.getMetadataJson()).doesNotContain(fingerprintKey);
        JsonNode effective = service.resolve(deployment, version, config, TARGET);
        assertThat(effective.path("milvusHost").asText()).isEqualTo(URI_HOST);
        assertThat(effective.path("milvusDatabaseName").asText()).isEqualTo("default");
        assertThat(effective.path("milvusRuntimeUsernameSecretName").asText()).startsWith("MANAGED_EXTERNAL_VECTOR_USERNAME_");
        assertThat(effective.path("milvusRuntimePasswordSecretName").asText()).startsWith("MANAGED_EXTERNAL_VECTOR_PASSWORD_");
        assertThat(effective.path("milvusAuthenticationType").asText()).isEqualTo("USERNAME_PASSWORD");
        assertThat(effective.has("milvusRuntimeTokenSecretName")).isFalse();
        assertThat(config.has("milvusHost")).isFalse();
        assertThat(service.bind(deployment.getId(), TARGET, request())).isEqualTo(result);
        verify(zilliz, times(3)).getCluster(CLUSTER, "control-plane-only");
        verify(zilliz, times(3)).listServingCollections(ENDPOINT, "db_admin", "serving-password");
        verifyNoMoreInteractions(zilliz);
        verify(handles, times(1)).saveAndFlush(any());
        verify(secrets, times(3)).upsertManagedSecret(anyString(), anyString(), any());
    }
    private static final String URI_HOST = "cluster-free.serverless.aws-eu-central-1.cloud.zilliz.com";

    @Test void freshCandidateRequiresExactCancellationProofForEveryHistoricalClusterHandle() {
        var proof = mock(com.ai.fabric.platform.backend.deployment.admission.AdmissionUnusedReservationEvidence.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "unusedCancellations", proof);
        var first = historicalHandle("old-one");
        var second = historicalHandle("old-two");
        when(handles.findFirstByProviderTypeAndProviderResourceUuid(DeploymentProviderType.COOLIFY, CLUSTER)).thenReturn(Optional.of(first));
        when(handles.findByProviderResourceUuidOrderByIdAsc(CLUSTER)).thenReturn(java.util.List.of(first, second));
        when(proof.permitsFreshBinding(eq(first), eq(deployment.getCustomerId()), eq(deployment.getTenantId()), anyString(), anyString())).thenReturn(true);
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "CLUSTER_ALREADY_BOUND");
        verifyNoInteractions(zilliz);
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
        when(proof.permitsFreshBinding(eq(second), eq(deployment.getCustomerId()), eq(deployment.getTenantId()), anyString(), anyString())).thenReturn(true);
        var bound = service.bind(deployment.getId(), TARGET, request());
        String prefix = scopes.targetScopedProviderConfig(deployment, config, TARGET).path("milvusCollectionPrefix").asText();
        assertThat(bound.collectionPrefix()).isEqualTo(prefix);
        verify(proof, atLeastOnce()).permitsFreshBinding(eq(first), eq(deployment.getCustomerId()), eq(deployment.getTenantId()), anyString(), eq(prefix));
        verify(proof, atLeastOnce()).permitsFreshBinding(eq(second), eq(deployment.getCustomerId()), eq(deployment.getTenantId()), anyString(), eq(prefix));
        verify(zilliz).getCluster(CLUSTER, "control-plane-only");
        verify(zilliz).listServingCollections(ENDPOINT, "db_admin", "serving-password");
    }

    @Test void inactiveHistoricalHandleWithoutReceiptReaderStillBlocksAndSameDeploymentCannotBeRebound() {
        var old = historicalHandle("old-one");
        when(handles.findFirstByProviderTypeAndProviderResourceUuid(DeploymentProviderType.COOLIFY, CLUSTER)).thenReturn(Optional.of(old));
        when(handles.findByProviderResourceUuidOrderByIdAsc(CLUSTER)).thenReturn(java.util.List.of(old));
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "CLUSTER_ALREADY_BOUND");
        verifyNoInteractions(zilliz);
        when(handles.findFirstByProviderTypeAndProviderResourceUuid(DeploymentProviderType.COOLIFY, CLUSTER)).thenReturn(Optional.empty());
        when(handles.findByProviderResourceUuidOrderByIdAsc(CLUSTER)).thenReturn(java.util.List.of());
        service.bind(deployment.getId(), TARGET, request());
        saved.setStatus("RETAINED_UNUSED");
        clearInvocations(zilliz, secrets, handles);
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "BINDING_IMMUTABLE");
        verifyNoInteractions(zilliz);
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
    }

    private DeploymentProviderResourceHandleEntity historicalHandle(String id) {
        var handle = new DeploymentProviderResourceHandleEntity(); handle.setId(id);
        handle.setProviderType(DeploymentProviderType.COOLIFY); handle.setProviderResourceUuid(CLUSTER);
        handle.setTargetProfileId(TARGET);
        handle.setStatus("RETAINED_UNUSED"); handle.setResourceKind(DeploymentExistingVectorBindingService.RESOURCE_KIND);
        return handle;
    }

    @Test void historicalCancellationCannotAuthorizeAnotherTargetOrProviderType() {
        var proof = mock(com.ai.fabric.platform.backend.deployment.admission.AdmissionUnusedReservationEvidence.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "unusedCancellations", proof);
        var previous = historicalHandle("previous"); previous.setTargetProfileId("other-target");
        when(handles.findByProviderResourceUuidOrderByIdAsc(CLUSTER)).thenReturn(java.util.List.of(previous));
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "CLUSTER_ALREADY_BOUND");
        previous.setTargetProfileId(TARGET); previous.setProviderType(DeploymentProviderType.RAILWAY_API);
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "CLUSTER_ALREADY_BOUND");
        verifyNoInteractions(proof, zilliz);
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"TOKEN", "USERNAME_PASSWORD"})
    void firstResponseAndExactReplayKeepPostgresPrecisionWithoutRefreshingTimestamps(String authType) {
        if (authType.equals("TOKEN")) useTokenConfig();
        var request = authType.equals("TOKEN") ? tokenRequest("synthetic-serving-token") : request();
        Instant observedAt = Instant.parse("2026-10-18T21:03:25.799299238Z");
        Instant expected = observedAt.truncatedTo(ChronoUnit.MICROS);
        try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(observedAt);
            time.clearInvocations();
            var first = service.bind(deployment.getId(), TARGET, request);
            assertThat(first.checkedAt()).isEqualTo(expected);
            assertThat(saved.getLastObservedAt()).isEqualTo(expected);
            assertThat(saved.getCreatedAt()).isEqualTo(expected);
            assertThat(saved.getUpdatedAt()).isEqualTo(expected);
            var persisted = new DeploymentProviderResourceHandleEntity();
            BeanUtils.copyProperties(saved, persisted);
            persisted.setLastObservedAt(saved.getLastObservedAt().truncatedTo(ChronoUnit.MICROS));
            persisted.setCreatedAt(saved.getCreatedAt().truncatedTo(ChronoUnit.MICROS));
            persisted.setUpdatedAt(saved.getUpdatedAt().truncatedTo(ChronoUnit.MICROS));
            saved = persisted;
            var unchangedSecrets = Map.copyOf(vault);

            assertThat(service.get(deployment.getId(), TARGET)).isEqualTo(first);
            assertThat(service.bind(deployment.getId(), TARGET, request)).isEqualTo(first);
            assertThat(vault).isEqualTo(unchangedSecrets);
            verify(handles, times(1)).saveAndFlush(any());
            verify(secrets, times(authType.equals("TOKEN") ? 2 : 3)).upsertManagedSecret(anyString(), anyString(), any());
            time.verify(Instant::now, times(1));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"TOKEN", "USERNAME_PASSWORD"})
    void localProjectionValidatesCredentialsWithoutProviderTrafficWhileLiveResolveStillChecksProvider(String authType) {
        bindSynthetic(authType);
        String unchangedConfig = config.toString();
        clearInvocations(zilliz, secrets, handles, audit);

        var local = service.resolveLocal(deployment, version, config, TARGET);
        assertThat(local.path("milvusAuthenticationType").asText()).isEqualTo(authType);
        assertThat(local.toString()).doesNotContain("synthetic-serving-token", "serving-password", "control-plane-only");
        assertThat(config.toString()).isEqualTo(unchangedConfig);
        verifyNoInteractions(zilliz, audit);
        verify(secrets, never()).resolveSecret("ZILLIZ_CLOUD_API_KEY");
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
        verify(handles, never()).saveAndFlush(any());

        assertThat(service.resolve(deployment, version, config, TARGET)).isEqualTo(local);
        verify(zilliz).getCluster(CLUSTER, "control-plane-only");
        if (authType.equals("TOKEN")) verify(zilliz).listServingCollections(ENDPOINT, "synthetic-serving-token");
        else verify(zilliz).listServingCollections(ENDPOINT, "db_admin", "serving-password");
        verifyNoMoreInteractions(zilliz);
    }

    @ParameterizedTest @ValueSource(strings = {"TOKEN", "USERNAME_PASSWORD"})
    void localCredentialEvidenceDoesNotRequireOrClaimLiveProviderReadiness(String authType) {
        bindSynthetic(authType);
        vault.remove("ZILLIZ_CLOUD_API_KEY");
        clearInvocations(zilliz, secrets);
        assertThat(service.resolveLocal(deployment, version, config, TARGET).path("milvusAuthenticationType").asText()).isEqualTo(authType);
        verify(secrets, never()).resolveSecret("ZILLIZ_CLOUD_API_KEY");
        denied(() -> service.resolve(deployment, version, config, TARGET), "READBACK_CREDENTIAL_REQUIRED");
        verifyNoInteractions(zilliz);
    }

    @ParameterizedTest @MethodSource("localBindingFailures")
    void localProjectionRejectsMissingStaleWrongTargetAndTamperedEvidenceWithoutProviderCalls(String authType, String change) throws Exception {
        bindSynthetic(authType);
        var metadata = (ObjectNode) mapper.readTree(saved.getMetadataJson());
        String credentialRef = metadata.path(authType.equals("TOKEN") ? "tokenSecretRef" : "passwordSecretRef").asText();
        String target = change.equals("target") ? "target-other" : TARGET;
        switch (change) {
            case "missing" -> saved = null;
            case "credentialMissing" -> vault.remove(credentialRef);
            case "credentialRotated" -> vault.put(credentialRef, "synthetic-changed-value");
            case "metadata" -> saved.setMetadataJson(metadata.put("databaseName", "tampered").toString());
            case "hash" -> version.setConfigHash("stale-config-hash");
            case "namespace" -> saveRehashedMetadata(metadata.put("collectionPrefix", "foreign_scope__"));
            case "reference" -> saveRehashedMetadata(metadata.put(authType.equals("TOKEN") ? "tokenSecretRef" : "passwordSecretRef", "MANAGED_EXTERNAL_VECTOR_FOREIGN"));
            case "handleTarget" -> saved.setTargetProfileId("target-other");
            case "handleOwner" -> saved.setDeploymentId("dep-other");
            case "fingerprintKey" -> vault.remove(metadata.path("credentialFingerprintSecretRef").asText());
            case "target" -> { }
            default -> throw new IllegalArgumentException(change);
        }
        clearInvocations(zilliz, secrets, handles, audit);
        assertThatThrownBy(() -> service.resolveLocal(deployment, version, config, target))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("EXTERNAL_VECTOR_")
            .hasMessageNotContaining("synthetic-serving-token").hasMessageNotContaining("serving-password");
        verifyNoInteractions(zilliz, audit);
        verify(secrets, never()).resolveSecret("ZILLIZ_CLOUD_API_KEY");
        verify(secrets, never()).resolveSecret("MILVUS_TOKEN");
        verify(secrets, never()).resolveSecret("MILVUS_USERNAME");
        verify(secrets, never()).resolveSecret("MILVUS_PASSWORD");
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
        verify(handles, never()).saveAndFlush(any());
    }

    private static Stream<Arguments> localBindingFailures() {
        return Stream.of("TOKEN", "USERNAME_PASSWORD").flatMap(auth -> Stream.of("missing", "credentialMissing",
            "credentialRotated", "metadata", "hash", "target", "namespace", "reference", "handleTarget", "handleOwner", "fingerprintKey")
            .map(change -> Arguments.of(auth, change)));
    }

    private void bindSynthetic(String authType) {
        if (authType.equals("TOKEN")) {
            useTokenConfig();
            service.bind(deployment.getId(), TARGET, tokenRequest("synthetic-serving-token"));
        } else {
            service.bind(deployment.getId(), TARGET, request());
        }
    }

    @Test void usesDeploymentOptionRatherThanCommonServerlessPlanLabel() {
        when(zilliz.getCluster(CLUSTER, "control-plane-only")).thenReturn(cluster("Serverless", ENDPOINT));
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "PROVIDER_IDENTITY_CHANGED");
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
    }

    @Test void rejectsMissingServingCredentialsBeforeProviderReadback() {
        Request bad = new Request(version.getId(), CLUSTER, "project", "region", "Free", "default", "db_admin", null, "owner-approved");
        denied(() -> service.bind(deployment.getId(), TARGET, bad), "BINDING_FIELD_INVALID");
        verifyNoInteractions(zilliz);
    }

    @Test void rejectsManagementKeyAsServingCredential() {
        Request bad = new Request(version.getId(), CLUSTER, "project", "region", "Free", "default", "db_admin", "control-plane-only", "owner-approved");
        denied(() -> service.bind(deployment.getId(), TARGET, bad), "SERVING_CREDENTIAL_REQUIRED");
        verifyNoInteractions(zilliz);
    }

    @Test void rejectsAlreadyBoundClusterBeforeProviderOrSecretEffects() {
        when(handles.findFirstByProviderTypeAndProviderResourceUuid(DeploymentProviderType.COOLIFY, CLUSTER))
            .thenReturn(Optional.of(new DeploymentProviderResourceHandleEntity()));
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "CLUSTER_ALREADY_BOUND");
        verifyNoInteractions(zilliz);
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
    }

    @Test void rejectsChangedReplayWithoutUpdatingSecrets() {
        service.bind(deployment.getId(), TARGET, request());
        Request changed = new Request(version.getId(), CLUSTER, "project", "region", "Free", "default", "db_admin", "serving-password", "changed-approval");
        denied(() -> service.bind(deployment.getId(), TARGET, changed), "BINDING_IMMUTABLE");
        verify(secrets, times(3)).upsertManagedSecret(anyString(), anyString(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"http://cluster.zilliz.com", "https://other.example", "https://user:password@cluster.zilliz.com", "https://cluster.zilliz.com:444", "https://cluster.zilliz.com/path", "https://cluster.zilliz.com?secret=x"})
    void rejectsNonProviderOrUnsafeEndpoint(String endpoint) {
        when(zilliz.getCluster(CLUSTER, "control-plane-only")).thenReturn(cluster("Free", endpoint));
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "ENDPOINT_INVALID");
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
    }

    @Test void failsClosedForAnotherTargetAndMissingTarget() {
        service.bind(deployment.getId(), TARGET, request());
        denied(() -> service.resolve(deployment, version, config, "target-production"), "BINDING_REQUIRED");
        denied(() -> service.resolve(deployment, version, config, null), "COMPLETE_SCOPE_REQUIRED");
    }

    @ParameterizedTest @ValueSource(strings = {"customer", "tenant", "version", "hash", "deployment"})
    void rejectsChangedScope(String change) {
        service.bind(deployment.getId(), TARGET, request());
        switch (change) {
            case "customer" -> deployment.setCustomerId("foreign-customer");
            case "tenant" -> deployment.setTenantId("foreign-tenant");
            case "version" -> version.setId("foreign-version");
            case "hash" -> version.setConfigHash("changed-config");
            case "deployment" -> version.setDeploymentId("foreign-deployment");
        }
        denied(() -> service.resolve(deployment, version, config, TARGET), "BINDING_SCOPE_CHANGED");
    }

    @Test void rejectsProviderPlanDriftBeforeRuntimeGeneration() {
        service.bind(deployment.getId(), TARGET, request());
        when(zilliz.getCluster(CLUSTER, "control-plane-only")).thenReturn(cluster("Serverless", ENDPOINT));
        denied(() -> service.resolve(deployment, version, config, TARGET), "PROVIDER_IDENTITY_CHANGED");
    }

    @ParameterizedTest @ValueSource(strings = {"USERNAME", "PASSWORD"})
    void missingOrBlankServingSecretNeverFallsBackToGlobal(String kind) {
        service.bind(deployment.getId(), TARGET, request());
        var name = vault.keySet().stream().filter(key -> key.startsWith("MANAGED_EXTERNAL_VECTOR_" + kind)).findFirst().orElseThrow();
        vault.put(name, " "); vault.put("MILVUS_USERNAME", "global-user"); vault.put("MILVUS_PASSWORD", "global-password");
        denied(() -> service.resolve(deployment, version, config, TARGET), "RUNTIME_CREDENTIALS_REQUIRED");
        verify(secrets, never()).resolveSecret("MILVUS_USERNAME");
        verify(secrets, never()).resolveSecret("MILVUS_PASSWORD");
    }

    @Test void detectsChangedSecretAndMetadata() throws Exception {
        service.bind(deployment.getId(), TARGET, request());
        String passwordRef = mapper.readTree(saved.getMetadataJson()).path("passwordSecretRef").asText();
        vault.put(passwordRef, "rotated-outside-binding");
        denied(() -> service.resolve(deployment, version, config, TARGET), "RUNTIME_CREDENTIALS_CHANGED");
        ObjectNode changed = (ObjectNode) mapper.readTree(saved.getMetadataJson()); changed.put("endpointHost", "foreign.example");
        saved.setMetadataJson(changed.toString());
        denied(() -> service.resolve(deployment, version, config, TARGET), "BINDING_CHANGED");
    }

    @Test void exactReplayDoesNotClaimSuccessOrRepairMissingServingCredentials() throws Exception {
        service.bind(deployment.getId(), TARGET, request());
        vault.remove(mapper.readTree(saved.getMetadataJson()).path("passwordSecretRef").asText());
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "RUNTIME_CREDENTIALS_REQUIRED");
        verify(secrets, times(3)).upsertManagedSecret(anyString(), anyString(), any());
        verify(handles, times(1)).saveAndFlush(any());
    }

    @Test void missingFingerprintKeyCannotResetOrRebindExistingCredentials() {
        service.bind(deployment.getId(), TARGET, request());
        vault.keySet().removeIf(name -> name.startsWith("MANAGED_EXTERNAL_VECTOR_FINGERPRINT_"));
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "FINGERPRINT_KEY_REQUIRED");
        denied(() -> service.resolve(deployment, version, config, TARGET), "FINGERPRINT_KEY_REQUIRED");
        verify(secrets, times(3)).upsertManagedSecret(anyString(), anyString(), any());
    }

    @Test void refusesManagedModeRatherThanSilentlyConvertingIt() {
        config.put("vectorProvisioningMode", "PLATFORM_MANAGED"); version.setProviderConfigJson(config.toString());
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "REVIEWED_MODE_REQUIRED");
        verifyNoInteractions(zilliz);
    }

    @Test void rejectsNonDefaultDatabaseRatherThanReportingUnprovenSdkIsolation() {
        Request bad = new Request(version.getId(), CLUSTER, "project", "region", "Free", "another_database", "db_admin", "serving-password", "owner-approved");
        denied(() -> service.bind(deployment.getId(), TARGET, bad), "DATABASE_UNSUPPORTED");
        verifyNoInteractions(zilliz);
    }

    @Test void deniedServingAuthStopsBeforeStoringBindingOrSecrets() {
        when(zilliz.listServingCollections(ENDPOINT, "db_admin", "serving-password")).thenThrow(new RailwayProvisioningException("denied"));
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "SERVING_VERIFICATION_FAILED");
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
        verify(handles, never()).saveAndFlush(any());
    }

    @Test void refusesForeignCollectionsWithoutClearingThem() {
        when(zilliz.listServingCollections(ENDPOINT, "db_admin", "serving-password")).thenReturn(java.util.List.of("foreign_collection"));
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "FOREIGN_COLLECTIONS_PRESENT");
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
    }

    @Test void freeLimitChecksWholeDeclaredScopeBeforeEffects() {
        version.setEntityConfigJson("{\"ai-entities\":{\"a\":{},\"b\":{},\"c\":{},\"d\":{},\"e\":{},\"f\":{}}}");
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "FREE_COLLECTION_LIMIT");
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
    }

    @Test void materializedOwnedCollectionsAreNotChargedAgainOnResolveOrReplay() {
        version.setEntityConfigJson("{\"ai-entities\":{\"a\":{},\"b\":{},\"c\":{}}}");
        var bound = service.bind(deployment.getId(), TARGET, request());
        String prefix = bound.collectionPrefix();
        when(zilliz.listServingCollections(ENDPOINT, "db_admin", "serving-password"))
            .thenReturn(java.util.List.of(prefix + "a", prefix + "b", prefix + "c"));
        assertThat(service.resolve(deployment, version, config, TARGET).path("milvusDatabaseName").asText()).isEqualTo("default");
        assertThat(service.bind(deployment.getId(), TARGET, request())).isEqualTo(bound);
    }

    @Test void countsProviderPhysicalNamesWithoutChargingExistingHashedEntityNamesTwice() {
        version.setEntityConfigJson("{\"ai-entities\":{\"dealer-vehicle\":{},\"document\":{}}}");
        var bound = service.bind(deployment.getId(), TARGET, request());
        String prefix = bound.collectionPrefix();
        when(zilliz.listServingCollections(ENDPOINT, "db_admin", "serving-password"))
            .thenReturn(java.util.List.of(prefix + "dealer_vehicle_haeb1717160f8078e", prefix + "document",
                prefix + "retained_a", prefix + "retained_b", prefix + "retained_c"));
        assertThat(service.resolve(deployment, version, config, TARGET).path("milvusDatabaseName").asText()).isEqualTo("default");
    }

    @Test void ordinaryProfilesAreNotRewrittenAndRequestLoggingIsRedacted() {
        var ordinary = mapper.createObjectNode().put("vectorStrategy", "milvus");
        assertThat(service.resolve(null, null, ordinary, null)).isSameAs(ordinary);
        assertThat(request().toString()).doesNotContain("serving-password", "db_admin");
        verifyNoInteractions(zilliz, handles, secrets);
    }

    @Test void bindsTokenAndReplaysWithoutUsernamePasswordSecretsOrCredentialProjection() throws Exception {
        useTokenConfig();
        var request = tokenRequest("synthetic-serving-token");
        var bound = service.bind(deployment.getId(), TARGET, request);
        var meta = mapper.readTree(saved.getMetadataJson());
        String tokenRef = meta.path("tokenSecretRef").asText();
        String scopeToken = DeploymentEvidenceFingerprint.hash(mapper,
            mapper.valueToTree(Map.of("deploymentId", deployment.getId(), "targetProfileId", TARGET))).toUpperCase(java.util.Locale.ROOT);
        assertThat(tokenRef).isEqualTo("MANAGED_EXTERNAL_VECTOR_TOKEN_" + scopeToken);
        assertThat(vault.get(tokenRef)).isEqualTo("synthetic-serving-token");
        assertThat(meta.path("authType").asText()).isEqualTo("TOKEN");
        assertThat(meta.has("usernameSecretRef")).isFalse();
        assertThat(meta.has("passwordSecretRef")).isFalse();
        assertThat(bound.authType()).isEqualTo(AuthenticationType.TOKEN);
        assertThat(service.get(deployment.getId(), TARGET)).isEqualTo(bound);
        assertThat(mapper.copy().findAndRegisterModules().writeValueAsString(bound))
            .contains("\"authType\":\"TOKEN\"").doesNotContain("synthetic-serving-token", "SecretRef", "credentialDigest");
        assertThat(saved.getMetadataJson()).doesNotContain("synthetic-serving-token", "control-plane-only");
        assertThat(request.toString()).doesNotContain("synthetic-serving-token");
        assertThat(bound.toString()).doesNotContain("synthetic-serving-token");
        verify(audit).record(eq("EXTERNAL_VECTOR_BOUND"), eq("DEPLOYMENT"), eq(deployment.getId()),
            argThat(details -> !details.toString().contains("synthetic-serving-token") && !details.toString().contains(tokenRef)));
        var effective = service.resolve(deployment, version, config, TARGET);
        assertThat(effective.path("milvusAuthenticationType").asText()).isEqualTo("TOKEN");
        assertThat(effective.path("milvusRuntimeTokenSecretName").asText()).isEqualTo(tokenRef);
        assertThat(effective.has("milvusRuntimeUsernameSecretName")).isFalse();
        assertThat(effective.has("milvusRuntimePasswordSecretName")).isFalse();
        assertThat(effective.toString()).doesNotContain("synthetic-serving-token", "control-plane-only");
        assertThat(service.bind(deployment.getId(), TARGET, request)).isEqualTo(bound);
        verify(secrets, times(2)).upsertManagedSecret(anyString(), anyString(), any());
        verify(handles, times(1)).saveAndFlush(any());
        verify(zilliz, times(3)).getCluster(CLUSTER, "control-plane-only");
        verify(zilliz, times(3)).listServingCollections(ENDPOINT, "synthetic-serving-token");
        verifyNoMoreInteractions(zilliz);
    }

    @Test void acceptsOnlyExplicitOwnerSuppliedTokenEvenWhenItsValueMatchesTheBroaderManagementKey() {
        useTokenConfig();
        when(zilliz.listServingCollections(ENDPOINT, "control-plane-only")).thenReturn(java.util.List.of());
        service.bind(deployment.getId(), TARGET, tokenRequest("control-plane-only"));
        service.resolve(deployment, version, config, TARGET);
        verify(zilliz, times(2)).listServingCollections(ENDPOINT, "control-plane-only");
        verify(zilliz, never()).listServingCollections(anyString(), anyString(), anyString());
    }

    @Test void requestModeMustMatchPublishedConfigAndAbsentLegacyConfigMeansUsernamePassword() {
        denied(() -> service.bind(deployment.getId(), TARGET, tokenRequest("synthetic-serving-token")), "AUTH_TYPE_MISMATCH");
        useTokenConfig();
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "AUTH_TYPE_MISMATCH");
        verifyNoInteractions(zilliz, secrets);
    }

    @ParameterizedTest @ValueSource(strings = {"token", "TOKEN ", "UNKNOWN"})
    void invalidPublishedModeNeverDefaults(String mode) {
        config.put("milvusAuthenticationType", mode); version.setProviderConfigJson(config.toString());
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "AUTH_TYPE_INVALID");
        verifyNoInteractions(zilliz, secrets);
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "\t"})
    void explicitlyBlankPublishedModeNeverDefaultsOrInfersRequestMode(String mode) {
        config.put("milvusAuthenticationType", mode); version.setProviderConfigJson(config.toString());
        denied(() -> service.bind(deployment.getId(), TARGET, tokenRequest("synthetic-serving-token")), "AUTH_TYPE_INVALID");
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "AUTH_TYPE_INVALID");
        verifyNoInteractions(zilliz, secrets);
    }

    @Test void nullPublishedModeNeverDefaults() {
        config.putNull("milvusAuthenticationType"); version.setProviderConfigJson(config.toString());
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "AUTH_TYPE_INVALID");
        verifyNoInteractions(zilliz, secrets);
    }

    @ParameterizedTest @ValueSource(strings = {"username", "password", "blankUsername", "blankPassword"})
    void tokenModeRejectsEvenBlankUsernamePasswordFields(String kind) {
        useTokenConfig();
        String username = kind.equals("username") ? "db_admin" : kind.equals("blankUsername") ? "" : null;
        String password = kind.equals("password") ? "serving-password" : kind.equals("blankPassword") ? "" : null;
        var mixed = new Request(version.getId(), CLUSTER, "project", "region", "Free", "default", username, password,
            "owner-approved", AuthenticationType.TOKEN, "synthetic-serving-token");
        denied(() -> service.bind(deployment.getId(), TARGET, mixed), "AUTH_CREDENTIALS_MIXED");
        verifyNoInteractions(zilliz, secrets);
    }

    @ParameterizedTest @ValueSource(strings = {"synthetic-serving-token", ""})
    void usernamePasswordModeRejectsTokenField(String token) {
        var mixed = new Request(version.getId(), CLUSTER, "project", "region", "Free", "default", "db_admin", "serving-password",
            "owner-approved", AuthenticationType.USERNAME_PASSWORD, token);
        denied(() -> service.bind(deployment.getId(), TARGET, mixed), "AUTH_CREDENTIALS_MIXED");
        verifyNoInteractions(zilliz, secrets);
    }

    @Test void missingTokenNeverUsesControlPlaneOrGlobalCredential() {
        useTokenConfig();
        for (String token : new String[] {null, "", " ", " padded", "synthetic\n", "x".repeat(4097)}) {
            denied(() -> service.bind(deployment.getId(), TARGET, tokenRequest(token)), "BINDING_FIELD_INVALID");
        }
        verifyNoInteractions(zilliz, secrets);
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "blank", "rotated"})
    void tokenSecretDriftFailsResolveAndExactReplayWithoutRepairOrFallback(String change) throws Exception {
        useTokenConfig(); service.bind(deployment.getId(), TARGET, tokenRequest("synthetic-serving-token"));
        String ref = mapper.readTree(saved.getMetadataJson()).path("tokenSecretRef").asText();
        if (change.equals("missing")) vault.remove(ref);
        else vault.put(ref, change.equals("blank") ? " " : "synthetic-rotated-token");
        vault.put("MILVUS_TOKEN", "synthetic-global-token");
        String error = change.equals("rotated") ? "RUNTIME_CREDENTIALS_CHANGED" : "RUNTIME_CREDENTIALS_REQUIRED";
        denied(() -> service.resolve(deployment, version, config, TARGET), error);
        denied(() -> service.bind(deployment.getId(), TARGET, tokenRequest("synthetic-serving-token")), error);
        verify(secrets, never()).resolveSecret("MILVUS_TOKEN");
        verify(secrets, times(2)).upsertManagedSecret(anyString(), anyString(), any());
        verify(handles, times(1)).saveAndFlush(any());
        verify(zilliz, times(1)).listServingCollections(ENDPOINT, "synthetic-serving-token");
        verify(zilliz, never()).listServingCollections(anyString(), anyString(), anyString());
    }

    @Test void tokenRotationOrModeChangeCannotRebindImmutableHandle() {
        useTokenConfig(); service.bind(deployment.getId(), TARGET, tokenRequest("synthetic-serving-token"));
        denied(() -> service.bind(deployment.getId(), TARGET, tokenRequest("synthetic-rotated-token")), "BINDING_IMMUTABLE");
        config.put("milvusAuthenticationType", "USERNAME_PASSWORD"); version.setProviderConfigJson(config.toString());
        denied(() -> service.bind(deployment.getId(), TARGET, request()), "BINDING_IMMUTABLE");
        denied(() -> service.resolve(deployment, version, config, TARGET), "AUTH_TYPE_MISMATCH");
        verify(secrets, times(2)).upsertManagedSecret(anyString(), anyString(), any());
        verify(handles, times(1)).saveAndFlush(any());
    }

    @ParameterizedTest @ValueSource(strings = {"tokenSecretRef", "credentialFingerprintSecretRef", "usernameSecretRef", "passwordSecretRef"})
    void tokenReferencesMustBeExactAndOtherModeReferencesAreForbiddenEvenWithRehashedMetadata(String field) throws Exception {
        useTokenConfig(); service.bind(deployment.getId(), TARGET, tokenRequest("synthetic-serving-token"));
        ObjectNode meta = (ObjectNode) mapper.readTree(saved.getMetadataJson());
        meta.put(field, "MANAGED_EXTERNAL_VECTOR_TOKEN_FOREIGN_SCOPE");
        saveRehashedMetadata(meta);
        denied(() -> service.resolve(deployment, version, config, TARGET), "SECRET_SCOPE_CHANGED");
        verify(zilliz, times(1)).listServingCollections(ENDPOINT, "synthetic-serving-token");
    }

    @Test void tokenFingerprintCoversExactModeAndCredentialAndKeyRotationFailsClosed() throws Exception {
        useTokenConfig(); service.bind(deployment.getId(), TARGET, tokenRequest("synthetic-serving-token"));
        var meta = mapper.readTree(saved.getMetadataJson());
        String fingerprintRef = meta.path("credentialFingerprintSecretRef").asText();
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(vault.get(fingerprintRef).getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = java.util.HexFormat.of().formatHex(mac.doFinal(mapper.writeValueAsBytes(
            mapper.createObjectNode().put("authType", "TOKEN").put("token", "synthetic-serving-token"))));
        assertThat(meta.path("credentialDigest").asText()).isEqualTo(expected);
        vault.put(fingerprintRef, "synthetic-rotated-hmac-key");
        denied(() -> service.resolve(deployment, version, config, TARGET), "RUNTIME_CREDENTIALS_CHANGED");
        denied(() -> service.bind(deployment.getId(), TARGET, tokenRequest("synthetic-serving-token")), "BINDING_IMMUTABLE");
        vault.remove(fingerprintRef);
        denied(() -> service.resolve(deployment, version, config, TARGET), "FINGERPRINT_KEY_REQUIRED");
        denied(() -> service.bind(deployment.getId(), TARGET, tokenRequest("synthetic-serving-token")), "FINGERPRINT_KEY_REQUIRED");
        verify(secrets, times(2)).upsertManagedSecret(anyString(), anyString(), any());
    }

    @Test void tokenModeTamperingFailsWithOrWithoutRehashedMetadata() throws Exception {
        useTokenConfig(); service.bind(deployment.getId(), TARGET, tokenRequest("synthetic-serving-token"));
        ObjectNode meta = (ObjectNode) mapper.readTree(saved.getMetadataJson());
        meta.put("authType", "USERNAME_PASSWORD"); saved.setMetadataJson(meta.toString());
        denied(() -> service.resolve(deployment, version, config, TARGET), "BINDING_CHANGED");
        saveRehashedMetadata(meta);
        denied(() -> service.resolve(deployment, version, config, TARGET), "AUTH_TYPE_MISMATCH");
    }

    @Test void resolveRemovesStaleOtherModeReferencesWithoutMutatingPublishedConfig() {
        config.put("milvusAuthenticationType", "USERNAME_PASSWORD").put("milvusRuntimeTokenSecretName", "stale-token-ref");
        version.setProviderConfigJson(config.toString());
        service.bind(deployment.getId(), TARGET, request());
        assertThat(service.resolve(deployment, version, config, TARGET).has("milvusRuntimeTokenSecretName")).isFalse();
        assertThat(config.path("milvusRuntimeTokenSecretName").asText()).isEqualTo("stale-token-ref");
        saved = null;
        useTokenConfig();
        config.put("milvusRuntimeUsernameSecretName", "stale-username-ref").put("milvusRuntimePasswordSecretName", "stale-password-ref");
        version.setProviderConfigJson(config.toString());
        service.bind(deployment.getId(), TARGET, tokenRequest("synthetic-serving-token"));
        var effective = service.resolve(deployment, version, config, TARGET);
        assertThat(effective.has("milvusRuntimeUsernameSecretName")).isFalse();
        assertThat(effective.has("milvusRuntimePasswordSecretName")).isFalse();
    }

    @Test void tokenServingDenialForeignCollectionsAndFreeQuotaStillFailBeforeEffects() {
        useTokenConfig();
        when(zilliz.listServingCollections(ENDPOINT, "synthetic-serving-token")).thenThrow(new RailwayProvisioningException("synthetic-serving-token"));
        denied(() -> service.bind(deployment.getId(), TARGET, tokenRequest("synthetic-serving-token")), "SERVING_VERIFICATION_FAILED");
        doReturn(java.util.List.of("foreign_collection")).when(zilliz).listServingCollections(ENDPOINT, "synthetic-serving-token");
        denied(() -> service.bind(deployment.getId(), TARGET, tokenRequest("synthetic-serving-token")), "FOREIGN_COLLECTIONS_PRESENT");
        when(zilliz.listServingCollections(ENDPOINT, "synthetic-serving-token")).thenReturn(java.util.List.of());
        version.setEntityConfigJson("{\"ai-entities\":{\"a\":{},\"b\":{},\"c\":{},\"d\":{},\"e\":{},\"f\":{}}}");
        denied(() -> service.bind(deployment.getId(), TARGET, tokenRequest("synthetic-serving-token")), "FREE_COLLECTION_LIMIT");
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), any());
        verify(handles, never()).saveAndFlush(any());
    }

    private void useTokenConfig() {
        config.put("milvusAuthenticationType", "TOKEN"); version.setProviderConfigJson(config.toString());
        when(zilliz.listServingCollections(ENDPOINT, "synthetic-serving-token")).thenReturn(java.util.List.of());
    }

    private Request tokenRequest(String token) {
        return new Request(version.getId(), CLUSTER, "project", "region", "Free", "default", null, null,
            "owner-approved", AuthenticationType.TOKEN, token);
    }

    private void saveRehashedMetadata(ObjectNode metadata) {
        metadata.remove("fingerprint"); metadata.put("fingerprint", DeploymentEvidenceFingerprint.hash(mapper, metadata));
        saved.setMetadataJson(metadata.toString());
    }

    private Request request() { return new Request(version.getId(), CLUSTER, "project", "region", "Free", "default", "db_admin", "serving-password", "owner-approved"); }
    private ZillizCloudControlPlaneClient.ZillizClusterSummary cluster(String option, String endpoint) {
        return new ZillizCloudControlPlaneClient.ZillizClusterSummary(CLUSTER, "Free", "project", "region", "Serverless", "", 0, "RUNNING", endpoint, option);
    }
    private void denied(Runnable call, String code) { assertThatThrownBy(call::run).isInstanceOf(ResponseStatusException.class).hasMessageContaining("EXTERNAL_VECTOR_" + code); }
}
