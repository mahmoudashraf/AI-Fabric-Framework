package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
        assertThat(config.has("milvusHost")).isFalse();
        assertThat(service.bind(deployment.getId(), TARGET, request())).isEqualTo(result);
        verify(zilliz, times(3)).getCluster(CLUSTER, "control-plane-only");
        verify(zilliz, times(3)).listServingCollections(ENDPOINT, "db_admin", "serving-password");
        verifyNoMoreInteractions(zilliz);
        verify(handles, times(1)).saveAndFlush(any());
        verify(secrets, times(3)).upsertManagedSecret(anyString(), anyString(), any());
    }
    private static final String URI_HOST = "cluster-free.serverless.aws-eu-central-1.cloud.zilliz.com";

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

    private Request request() { return new Request(version.getId(), CLUSTER, "project", "region", "Free", "default", "db_admin", "serving-password", "owner-approved"); }
    private ZillizCloudControlPlaneClient.ZillizClusterSummary cluster(String option, String endpoint) {
        return new ZillizCloudControlPlaneClient.ZillizClusterSummary(CLUSTER, "Free", "project", "region", "Serverless", "", 0, "RUNNING", endpoint, option);
    }
    private void denied(Runnable call, String code) { assertThatThrownBy(call::run).isInstanceOf(ResponseStatusException.class).hasMessageContaining("EXTERNAL_VECTOR_" + code); }
}
