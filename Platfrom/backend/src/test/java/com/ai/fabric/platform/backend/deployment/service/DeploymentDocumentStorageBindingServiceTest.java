package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.UpsertDocumentStorageBindingRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentDocumentStorageBindingServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void s3BindingStoresCredentialsOnlyInManagedSecretsAndProjectsSafeMetadata() throws Exception {
        Fixture fixture = fixture("staging", "staging");
        when(fixture.secretService().isSecretPresent(any())).thenReturn(true);

        var result = fixture.service().upsert("dep-docs", new UpsertDocumentStorageBindingRequest(
            "profile-staging",
            "S3_COMPATIBLE_OBJECT_STORAGE",
            "https://objects.example.test",
            "eu-west-2",
            "customer-private-bucket",
            "approved/manuals",
            "access-value",
            "secret-value",
            "session-value",
            true,
            true,
            false,
            null
        ));

        assertThat(result.bindingRef()).startsWith("dsh-");
        assertThat(result.connectorType()).isEqualTo("S3_COMPATIBLE_OBJECT_STORAGE");
        assertThat(result.credentialsPresent()).isTrue();
        assertThat(result.safeMetadata().toString())
            .contains("objects.example.test", "allowedPrefixDigest", "CUSTOMER_MANAGED")
            .doesNotContain("approved/manuals", "access-value", "secret-value", "session-value");

        String key = DeploymentDocumentStorageBindingService.secretKey("dep-docs", "profile-staging");
        verify(fixture.secretService()).upsertManagedSecret(eq(key + "_ENDPOINT"), eq("https://objects.example.test"), anyMap());
        verify(fixture.secretService()).upsertManagedSecret(eq(key + "_BUCKET"), eq("customer-private-bucket"), anyMap());
        verify(fixture.secretService()).upsertManagedSecret(eq(key + "_PREFIX"), eq("approved/manuals/"), anyMap());
        verify(fixture.secretService()).upsertManagedSecret(eq(key + "_ACCESS_KEY"), eq("access-value"), anyMap());
        verify(fixture.secretService()).upsertManagedSecret(eq(key + "_SECRET_KEY"), eq("secret-value"), anyMap());
        verify(fixture.secretService()).upsertManagedSecret(eq(key + "_SESSION_TOKEN"), eq("session-value"), anyMap());

        ArgumentCaptor<DeploymentProviderResourceHandleEntity> handle =
            ArgumentCaptor.forClass(DeploymentProviderResourceHandleEntity.class);
        verify(fixture.handleRepository()).save(handle.capture());
        JsonNode metadata = objectMapper.readTree(handle.getValue().getMetadataJson());
        assertThat(metadata.has("accessKey")).isFalse();
        assertThat(metadata.has("secretKey")).isFalse();
        assertThat(metadata.has("prefix")).isFalse();
    }

    @Test
    void bindingMustMatchTheDeploymentTargetEnvironment() {
        Fixture fixture = fixture("staging", "production");

        assertThatThrownBy(() -> fixture.service().upsert("dep-docs", s3Request("profile-staging")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("target environment must match");
        verify(fixture.handleRepository(), never()).save(any());
    }

    @Test
    void mountedFolderIsRejectedForProduction() {
        Fixture fixture = fixture("production", "production");

        assertThatThrownBy(() -> fixture.service().upsert(
            "dep-docs",
            new UpsertDocumentStorageBindingRequest(
                "profile-staging",
                "MOUNTED_FOLDER",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "/app/document-sources"
            )
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("limited to non-production demos");
        verify(fixture.handleRepository(), never()).save(any());
    }

    @Test
    void mountedFolderIsRejectedWhenTheTargetCannotProvisionItsMount() {
        Fixture fixture = fixture("staging", "staging", DeploymentProviderType.RAILWAY_API);

        assertThatThrownBy(() -> fixture.service().upsert(
            "dep-docs",
            new UpsertDocumentStorageBindingRequest(
                "profile-staging",
                "MOUNTED_FOLDER",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "/app/document-sources"
            )
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("only by Coolify demo target profiles");
        verify(fixture.handleRepository(), never()).save(any());
    }

    @Test
    void privateEndpointRequiresExactTargetProfileNetworkApproval() {
        Fixture blocked = fixture("staging", "staging");
        assertThatThrownBy(() -> blocked.service().upsert(
            "dep-docs",
            s3Request("profile-staging", "https://127.0.0.1", false)
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("network-policy approval");

        Fixture approved = fixture(
            "staging",
            "staging",
            DeploymentProviderType.COOLIFY,
            "{\"documentStoragePrivateEndpointHosts\":[\"minio.internal\"]}"
        );
        when(approved.secretService().isSecretPresent(any())).thenReturn(true);
        var result = approved.service().upsert(
            "dep-docs",
            s3Request("profile-staging", "http://minio.internal:9000", true)
        );
        assertThat(result.safeMetadata().path("privateEndpointApproved").asBoolean()).isTrue();
        assertThat(result.safeMetadata().path("endpointHost").asText()).isEqualTo("minio.internal");
    }

    @Test
    void deploymentCleanupRemovesOnlyLoomAiBindingStateAndNeverCallsCustomerStorage() {
        Fixture fixture = fixture("staging", "staging");
        DeploymentProviderResourceHandleEntity binding = new DeploymentProviderResourceHandleEntity();
        binding.setId("dsh-documents");
        binding.setDeploymentId("dep-docs");
        binding.setTargetProfileId("profile-staging");
        binding.setResourceKind(DeploymentDocumentStorageBindingService.RESOURCE_KIND);
        when(fixture.handleRepository().findByDeploymentIdOrderByUpdatedAtDesc("dep-docs"))
            .thenReturn(java.util.List.of(binding));

        int removed = fixture.service().cleanupForDeletedDeployment(fixture.deployment());

        assertThat(removed).isEqualTo(1);
        verify(fixture.secretService(), times(8)).clearManagedSecret(any(), anyMap());
        verify(fixture.handleRepository()).deleteAll(java.util.List.of(binding));
        verify(fixture.auditService()).record(
            eq("DOCUMENT_STORAGE_BINDINGS_CLEANED"),
            eq("DEPLOYMENT"),
            eq("dep-docs"),
            anyMap()
        );
    }

    private Fixture fixture(String deploymentEnvironment, String profileEnvironment) {
        return fixture(deploymentEnvironment, profileEnvironment, DeploymentProviderType.COOLIFY);
    }

    private Fixture fixture(
        String deploymentEnvironment,
        String profileEnvironment,
        DeploymentProviderType providerType
    ) {
        return fixture(deploymentEnvironment, profileEnvironment, providerType, "{}");
    }

    private Fixture fixture(
        String deploymentEnvironment,
        String profileEnvironment,
        DeploymentProviderType providerType,
        String networkPolicy
    ) {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentAccessService accessService = mock(DeploymentAccessService.class);
        DeploymentTargetProfileRepository profileRepository = mock(DeploymentTargetProfileRepository.class);
        DeploymentProviderResourceHandleRepository handleRepository = mock(DeploymentProviderResourceHandleRepository.class);
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-docs");
        deployment.setEnvironmentName(deploymentEnvironment);
        DeploymentTargetProfileEntity profile = new DeploymentTargetProfileEntity();
        profile.setId("profile-staging");
        profile.setEnvironmentName(profileEnvironment);
        profile.setProviderType(providerType);
        profile.setActive(true);
        profile.setNetworkPolicyJson(networkPolicy);

        when(deploymentRepository.findById("dep-docs")).thenReturn(Optional.of(deployment));
        when(accessService.requireDeploymentAdminAccess(deployment)).thenReturn(deployment);
        when(profileRepository.findById("profile-staging")).thenReturn(Optional.of(profile));
        when(handleRepository.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            "dep-docs", "profile-staging", DeploymentDocumentStorageBindingService.RESOURCE_KIND
        )).thenReturn(Optional.empty());
        when(handleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        return new Fixture(
            new DeploymentDocumentStorageBindingService(
                deploymentRepository,
                accessService,
                profileRepository,
                handleRepository,
                secretService,
                auditService,
                objectMapper
            ),
            deployment,
            handleRepository,
            secretService,
            auditService
        );
    }

    private UpsertDocumentStorageBindingRequest s3Request(String profileId) {
        return s3Request(profileId, "https://objects.example.test", false);
    }

    private UpsertDocumentStorageBindingRequest s3Request(
        String profileId,
        String endpoint,
        boolean allowInsecure
    ) {
        return new UpsertDocumentStorageBindingRequest(
            profileId,
            "S3_COMPATIBLE_OBJECT_STORAGE",
            endpoint,
            "eu-west-2",
            "customer-private-bucket",
            "approved/",
            "access",
            "secret",
            null,
            true,
            true,
            allowInsecure,
            null
        );
    }

    private record Fixture(
        DeploymentDocumentStorageBindingService service,
        DeploymentEntity deployment,
        DeploymentProviderResourceHandleRepository handleRepository,
        PlatformSecretService secretService,
        PlatformAuditService auditService
    ) {
    }
}
