package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentExportRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.ExportMode;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.ExportRecipient;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.ImportMode;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentImportRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentManagedVectorResourceRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.repository.PublicApiDeploymentRepository;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretCleanupPolicy;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretOwnerType;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretScopeType;
import com.ai.fabric.platform.backend.secret.repository.DeploymentProviderSecretBindingRepository;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.RecipientType.OPERATOR_PUBLIC_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentBundleExportImportServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
    private final DeploymentDraftRepository draftRepository = mock(DeploymentDraftRepository.class);
    private final DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
    private final DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
    private final DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);
    private final DeploymentProviderResourceHandleRepository resourceHandleRepository = mock(DeploymentProviderResourceHandleRepository.class);
    private final DeploymentManagedVectorResourceRepository managedVectorResourceRepository = mock(DeploymentManagedVectorResourceRepository.class);
    private final DeploymentMarketplacePluginInstallRepository marketplacePluginInstallRepository = mock(DeploymentMarketplacePluginInstallRepository.class);
    private final DeploymentProviderSecretBindingRepository secretBindingRepository = mock(DeploymentProviderSecretBindingRepository.class);
    private final PlatformSecretRepository platformSecretRepository = mock(PlatformSecretRepository.class);
    private final PublicApiDeploymentRepository publicApiDeploymentRepository = mock(PublicApiDeploymentRepository.class);
    private final DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
    private final DeploymentService deploymentService = mock(DeploymentService.class);
    private final PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
    private final DeploymentBundleSealingService sealingService = new DeploymentBundleSealingService(objectMapper);

    @Test
    void configOnlyExportContainsSecretInventoryButNoSecretValueOrEnvelope() {
        DeploymentBundleExportImportService service = service();
        DeploymentEntity deployment = deployment();
        DeploymentDraftEntity draft = draft(deployment.getId());
        PlatformSecretEntity secret = deploymentSecret("MANAGED_DEPLOYMENT_SECRET", "super-secret-value", deployment.getId());
        stubExportState(deployment, draft, secret);

        var summary = service.exportDeployment(
            deployment.getId(),
            new DeploymentExportRequest(ExportMode.CONFIG_ONLY, "config backup", null, true, true)
        );

        String bundleText = summary.bundle().toString();
        assertThat(bundleText).doesNotContain("super-secret-value");
        assertThat(summary.bundle().has("secretEnvelope")).isFalse();
        assertThat(summary.secretSummary().items()).hasSize(1);
        assertThat(summary.secretSummary().items().get(0).valueIncluded()).isFalse();
        assertThat(summary.secretSummary().items().get(0).secretName()).isEqualTo("MANAGED_DEPLOYMENT_SECRET");
    }

    @Test
    void sealedExportEncryptsDeploymentSecretValues() throws Exception {
        DeploymentBundleExportImportService service = service();
        DeploymentEntity deployment = deployment();
        DeploymentDraftEntity draft = draft(deployment.getId());
        PlatformSecretEntity secret = deploymentSecret("MANAGED_DEPLOYMENT_SECRET", "super-secret-value", deployment.getId());
        stubExportState(deployment, draft, secret);
        KeyPair keyPair = rsaKeyPair();

        var summary = service.exportDeployment(
            deployment.getId(),
            new DeploymentExportRequest(
                ExportMode.SEALED_BACKUP,
                "disaster recovery smoke",
                new ExportRecipient(OPERATOR_PUBLIC_KEY, publicKeyPem(keyPair)),
                true,
                true
            )
        );

        String bundleText = summary.bundle().toString();
        assertThat(summary.bundle().has("secretEnvelope")).isTrue();
        assertThat(bundleText).doesNotContain("super-secret-value");
        assertThat(summary.secretSummary().includedValues()).isEqualTo(1);

        assertThat(sealingService.unseal(summary.bundle().path("secretEnvelope"), privateKeyPem(keyPair))
            .path("secrets")
            .get(0)
            .path("value")
            .asText()).isEqualTo("super-secret-value");
    }

    @Test
    void restoreInPlaceCreatesNewDraftAndRestoresSealedSecretsWithoutChangingDeploymentId() throws Exception {
        DeploymentBundleExportImportService service = service();
        DeploymentEntity deployment = deployment();
        DeploymentDraftEntity draft = draft(deployment.getId());
        PlatformSecretEntity secret = deploymentSecret("MANAGED_DEPLOYMENT_SECRET", "super-secret-value", deployment.getId());
        stubExportState(deployment, draft, secret);
        KeyPair keyPair = rsaKeyPair();
        var export = service.exportDeployment(
            deployment.getId(),
            new DeploymentExportRequest(
                ExportMode.SEALED_BACKUP,
                "restore smoke",
                new ExportRecipient(OPERATOR_PUBLIC_KEY, publicKeyPem(keyPair)),
                true,
                true
            )
        );
        when(draftRepository.findTopByDeploymentIdOrderByRevisionNumberDesc(deployment.getId())).thenReturn(Optional.of(draft));
        when(draftRepository.save(any(DeploymentDraftEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentRepository.save(any(DeploymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(platformSecretRepository.save(any(PlatformSecretEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.importDeployment(new DeploymentImportRequest(
            export.bundle(),
            ImportMode.RESTORE_IN_PLACE,
            deployment.getId(),
            null,
            null,
            null,
            null,
            privateKeyPem(keyPair),
            "restore in place smoke"
        ));

        assertThat(result.deploymentId()).isEqualTo(deployment.getId());
        ArgumentCaptor<DeploymentDraftEntity> draftCaptor = ArgumentCaptor.forClass(DeploymentDraftEntity.class);
        verify(draftRepository).save(draftCaptor.capture());
        assertThat(draftCaptor.getValue().getDeploymentId()).isEqualTo(deployment.getId());
        assertThat(draftCaptor.getValue().getRevisionNumber()).isEqualTo(2);
        assertThat(draftCaptor.getValue().getStatus()).isEqualTo("IMPORTED_RESTORE_DRAFT");

        ArgumentCaptor<PlatformSecretEntity> secretCaptor = ArgumentCaptor.forClass(PlatformSecretEntity.class);
        verify(platformSecretRepository).save(secretCaptor.capture());
        assertThat(secretCaptor.getValue().getDeploymentId()).isEqualTo(deployment.getId());
        assertThat(secretCaptor.getValue().getSecretValue()).isEqualTo("super-secret-value");
    }

    @Test
    void cloneImportCreatesFreshTenantWhenTargetTenantIsNotExplicit() {
        DeploymentBundleExportImportService service = service();
        DeploymentEntity sourceDeployment = deployment();
        DeploymentDraftEntity sourceDraft = draft(sourceDeployment.getId());
        PlatformSecretEntity secret = deploymentSecret("MANAGED_DEPLOYMENT_SECRET", "super-secret-value", sourceDeployment.getId());
        stubExportState(sourceDeployment, sourceDraft, secret);
        var export = service.exportDeployment(
            sourceDeployment.getId(),
            new DeploymentExportRequest(ExportMode.CONFIG_ONLY, "clone smoke", null, true, true)
        );

        DeploymentEntity importedDeployment = deployment();
        importedDeployment.setId("dep-imported");
        importedDeployment.setName("Imported Deployment");
        importedDeployment.setEnvironmentName("staging-import");
        importedDeployment.setTenantId("ten-imported");
        importedDeployment.setActiveDraftId("drf-imported");
        DeploymentDraftEntity importedDraft = draft(importedDeployment.getId());
        importedDraft.setId("drf-imported");
        importedDraft.setRevisionNumber(1);
        when(deploymentService.createDeployment(any(CreateDeploymentRequest.class))).thenReturn(new DeploymentSummary(
            importedDeployment.getId(),
            importedDeployment.getName(),
            importedDeployment.getEnvironmentName(),
            importedDeployment.getTemplateId(),
            null,
            null,
            importedDeployment.getStatus(),
            null,
            importedDeployment.getRuntimeBaseUrl(),
            false,
            false,
            false,
            importedDeployment.getCreatedAt()
        ));
        when(deploymentRepository.findById(importedDeployment.getId())).thenReturn(Optional.of(importedDeployment));
        when(draftRepository.findById(importedDraft.getId())).thenReturn(Optional.of(importedDraft));
        when(draftRepository.save(any(DeploymentDraftEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentRepository.save(any(DeploymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.importDeployment(new DeploymentImportRequest(
            export.bundle(),
            ImportMode.CONFIG_ONLY_CLONE,
            null,
            "Imported Deployment",
            "staging-import",
            null,
            null,
            null,
            "clone smoke"
        ));

        assertThat(result.deploymentId()).isEqualTo(importedDeployment.getId());
        ArgumentCaptor<CreateDeploymentRequest> requestCaptor = ArgumentCaptor.forClass(CreateDeploymentRequest.class);
        verify(deploymentService).createDeployment(requestCaptor.capture());
        assertThat(requestCaptor.getValue().customerId()).isEqualTo(sourceDeployment.getCustomerId());
        assertThat(requestCaptor.getValue().tenantId()).isNull();
    }

    private DeploymentBundleExportImportService service() {
        return new DeploymentBundleExportImportService(
            objectMapper,
            deploymentRepository,
            draftRepository,
            versionRepository,
            releaseRepository,
            verificationRunRepository,
            resourceHandleRepository,
            managedVectorResourceRepository,
            marketplacePluginInstallRepository,
            secretBindingRepository,
            platformSecretRepository,
            publicApiDeploymentRepository,
            deploymentAccessService,
            deploymentService,
            platformAuditService,
            sealingService
        );
    }

    private void stubExportState(DeploymentEntity deployment, DeploymentDraftEntity draft, PlatformSecretEntity secret) {
        when(deploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentAdminAccess(deployment)).thenReturn(deployment);
        when(draftRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(versionRepository.findByDeploymentIdOrderByPublishedAtDesc(deployment.getId())).thenReturn(List.of());
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())).thenReturn(Optional.empty());
        when(verificationRunRepository.findByDeploymentIdOrderByCreatedAtDesc(deployment.getId())).thenReturn(List.of());
        when(marketplacePluginInstallRepository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId())).thenReturn(List.of());
        when(resourceHandleRepository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId())).thenReturn(List.of());
        when(managedVectorResourceRepository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId())).thenReturn(List.of());
        when(secretBindingRepository.findByDeploymentIdOrderBySecretPurposeAsc(deployment.getId())).thenReturn(List.of());
        when(publicApiDeploymentRepository.findByDeploymentId(deployment.getId())).thenReturn(List.of());
        when(platformSecretRepository.findByScopeTypeAndDeploymentIdOrderByUpdatedAtDesc(
            PlatformSecretScopeType.DEPLOYMENT_MANAGED,
            deployment.getId()
        )).thenReturn(List.of(secret));
        when(platformSecretRepository.findById(secret.getName())).thenReturn(Optional.of(secret));
    }

    private static DeploymentEntity deployment() {
        Instant now = Instant.now();
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-test");
        deployment.setName("Test Deployment");
        deployment.setEnvironmentName("staging");
        deployment.setTemplateId("dev-openai-qdrant");
        deployment.setStatus("DRAFT");
        deployment.setCustomerId("cust-test");
        deployment.setTenantId("ten-test");
        deployment.setActiveDraftId("drf-test");
        deployment.setRuntimeBaseUrl("https://runtime.example.test");
        deployment.setConnectorBaseUrl("https://connector.example.test");
        deployment.setCreatedAt(now);
        deployment.setUpdatedAt(now);
        return deployment;
    }

    private static DeploymentDraftEntity draft(String deploymentId) {
        Instant now = Instant.now();
        DeploymentDraftEntity draft = new DeploymentDraftEntity();
        draft.setId("drf-test");
        draft.setDeploymentId(deploymentId);
        draft.setRevisionNumber(1);
        draft.setStatus("DRAFT");
        draft.setActionsConfigJson("{\"actions\":[]}");
        draft.setEntityConfigJson("{\"ai-entities\":{}}");
        draft.setRoutingConfigJson("{\"actions\":{}}");
        draft.setProviderConfigJson("{\"runtimeSecret\":\"${MANAGED_DEPLOYMENT_SECRET}\",\"curatedModuleId\":\"default\"}");
        draft.setSecurityConfigJson("{}");
        draft.setPromptConfigJson("{}");
        draft.setKnowledgeSourceConfigJson(DeploymentDraftEntity.DEFAULT_KNOWLEDGE_SOURCE_CONFIG_JSON);
        draft.setShellConfigJson(DeploymentDraftEntity.DEFAULT_SHELL_CONFIG_JSON);
        draft.setMarketplaceDatasetConfigJson(DeploymentDraftEntity.DEFAULT_MARKETPLACE_DATASET_CONFIG_JSON);
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        return draft;
    }

    private static PlatformSecretEntity deploymentSecret(String name, String value, String deploymentId) {
        PlatformSecretEntity secret = new PlatformSecretEntity();
        secret.setName(name);
        secret.setSecretValue(value);
        secret.setDeploymentId(deploymentId);
        secret.setScopeType(PlatformSecretScopeType.DEPLOYMENT_MANAGED);
        secret.setOwnerType(PlatformSecretOwnerType.PLATFORM_MANAGED);
        secret.setManagedByPlatform(true);
        secret.setCleanupPolicy(PlatformSecretCleanupPolicy.DELETE_ON_HARD_DELETE);
        secret.setSecretPurpose("runtime");
        secret.setUpdatedAt(Instant.now());
        return secret;
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String publicKeyPem(KeyPair keyPair) {
        return pem("PUBLIC KEY", keyPair.getPublic().getEncoded());
    }

    private static String privateKeyPem(KeyPair keyPair) {
        return pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
    }

    private static String pem(String label, byte[] key) {
        return "-----BEGIN " + label + "-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key)
            + "\n-----END " + label + "-----\n";
    }
}
