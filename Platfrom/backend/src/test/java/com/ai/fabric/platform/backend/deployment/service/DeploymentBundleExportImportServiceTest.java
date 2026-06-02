package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformProductProvisioningProperties;
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
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretCleanupPolicy;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretOwnerType;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretScopeType;
import com.ai.fabric.platform.backend.secret.repository.DeploymentProviderSecretBindingRepository;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanRevisionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationSourceConnectionEntity;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRevisionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationSourceConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.RecipientType.OPERATOR_PUBLIC_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private final VectorizationPlanRepository vectorizationPlanRepository = mock(VectorizationPlanRepository.class);
    private final VectorizationSourceConnectionRepository vectorizationSourceConnectionRepository = mock(VectorizationSourceConnectionRepository.class);
    private final VectorizationPlanRevisionRepository vectorizationPlanRevisionRepository = mock(VectorizationPlanRevisionRepository.class);
    private final PlatformManagedProductServiceRepository managedProductServiceRepository = mock(PlatformManagedProductServiceRepository.class);
    private final PlatformProductProvisioningProperties productProvisioningProperties = new PlatformProductProvisioningProperties(
        null, null, null, null, null, null, null, null, null, null, null
    );
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
    void sealedExportScopesDraftSecretInventoryToExplicitRefsAndEnvRefs() throws Exception {
        DeploymentBundleExportImportService service = service();
        DeploymentEntity deployment = deployment();
        DeploymentDraftEntity draft = draft(deployment.getId());
        draft.setProviderConfigJson("""
            {
              "runtimeApiKey": "${CONNECTOR_API_KEY}",
              "authorization": {
                "tokenHeaderName": "consentToken",
                "apiKeyHeaderName": "API_KEY_HEADER_SECRET",
                "secretRef": "MCP_SECRET_PRODUS_STAGING_MCP_API_KEY"
              },
              "curatedModuleId": "default"
            }
            """);
        PlatformSecretEntity runtimeSecret = deploymentSecret("CONNECTOR_API_KEY", "connector-runtime-key", deployment.getId());
        PlatformSecretEntity mcpSecret = deploymentSecret("MCP_SECRET_PRODUS_STAGING_MCP_API_KEY", "produs-mcp-key", deployment.getId());
        stubExportState(deployment, draft, runtimeSecret, mcpSecret);
        KeyPair keyPair = rsaKeyPair();

        var summary = service.exportDeployment(
            deployment.getId(),
            new DeploymentExportRequest(
                ExportMode.SEALED_BACKUP,
                "produs lift shift",
                new ExportRecipient(OPERATOR_PUBLIC_KEY, publicKeyPem(keyPair)),
                true,
                true
            )
        );

        assertThat(summary.secretSummary().missingReference()).isZero();
        assertThat(summary.secretSummary().includedValues()).isEqualTo(2);
        assertThat(summary.secretSummary().items().stream().map(item -> item.secretName()).toList())
            .containsExactlyInAnyOrder("CONNECTOR_API_KEY", "MCP_SECRET_PRODUS_STAGING_MCP_API_KEY")
            .doesNotContain("consentToken", "API_KEY_HEADER_SECRET");
        assertThat(sealingService.unseal(summary.bundle().path("secretEnvelope"), privateKeyPem(keyPair))
            .path("secrets")
            .findValuesAsText("secretName"))
            .containsExactlyInAnyOrder("CONNECTOR_API_KEY", "MCP_SECRET_PRODUS_STAGING_MCP_API_KEY");
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
            null,
            "clone smoke"
        ));

        assertThat(result.deploymentId()).isEqualTo(importedDeployment.getId());
        ArgumentCaptor<CreateDeploymentRequest> requestCaptor = ArgumentCaptor.forClass(CreateDeploymentRequest.class);
        verify(deploymentService).createDeployment(requestCaptor.capture());
        assertThat(requestCaptor.getValue().customerId()).isEqualTo(sourceDeployment.getCustomerId());
        assertThat(requestCaptor.getValue().tenantId()).isNull();
    }

    @Test
    void sealedCloneRestoresVectorizationControlPlaneFromBundle() throws Exception {
        DeploymentBundleExportImportService service = service();
        DeploymentEntity sourceDeployment = deployment();
        DeploymentDraftEntity sourceDraft = draft(sourceDeployment.getId());
        PlatformSecretEntity sourceToken = deploymentSecret("MANAGED_PRODUS_EXPORT_TOKEN", "safe-export-token", sourceDeployment.getId());
        stubExportState(sourceDeployment, sourceDraft, sourceToken);
        VectorizationSourceConnectionEntity sourceConnection = vectorizationSourceConnection(sourceDeployment.getId(), sourceToken.getName());
        VectorizationPlanEntity sourcePlan = vectorizationPlan(sourceDeployment.getId(), sourceConnection.getId());
        VectorizationPlanRevisionEntity sourceRevision = vectorizationRevision(sourceDeployment.getId(), sourcePlan.getId(), sourceConnection.getId());
        sourcePlan.setActiveRevisionId(sourceRevision.getId());
        when(vectorizationSourceConnectionRepository.findByDeploymentId(sourceDeployment.getId())).thenReturn(Optional.of(sourceConnection));
        when(vectorizationPlanRepository.findByDeploymentId(sourceDeployment.getId())).thenReturn(Optional.of(sourcePlan));
        when(vectorizationPlanRevisionRepository.findByPlanIdOrderByRevisionNumberDesc(sourcePlan.getId())).thenReturn(List.of(sourceRevision));
        when(platformSecretRepository.findById(sourceToken.getName())).thenReturn(Optional.of(sourceToken));
        KeyPair keyPair = rsaKeyPair();

        var export = service.exportDeployment(
            sourceDeployment.getId(),
            new DeploymentExportRequest(
                ExportMode.SEALED_BACKUP,
                "vectorization lift shift",
                new ExportRecipient(OPERATOR_PUBLIC_KEY, publicKeyPem(keyPair)),
                true,
                true
            )
        );

        assertThat(export.bundle().path("manifest").path("vectorizationControlPlane").path("sourceConnection").path("id").asText())
            .isEqualTo(sourceConnection.getId());
        assertThat(export.secretSummary().items())
            .anySatisfy(item -> {
                assertThat(item.secretName()).isEqualTo(sourceToken.getName());
                assertThat(item.valueIncluded()).isTrue();
                assertThat(item.sources()).anyMatch(source -> source.startsWith("vectorization-source-connection"));
            });

        DeploymentEntity importedDeployment = deployment();
        importedDeployment.setId("dep-imported");
        importedDeployment.setName("Imported Deployment");
        importedDeployment.setEnvironmentName("production-staging");
        importedDeployment.setCustomerId("cust-imported");
        importedDeployment.setTenantId("ten-imported");
        importedDeployment.setActiveDraftId("drf-imported");
        DeploymentDraftEntity importedDraft = draft(importedDeployment.getId());
        importedDraft.setId("drf-imported");
        VectorizationPlanEntity importedBootstrapPlan = vectorizationPlan(importedDeployment.getId(), null);
        importedBootstrapPlan.setId("vpl-imported-bootstrap");
        importedBootstrapPlan.setCustomerId(importedDeployment.getCustomerId());
        importedBootstrapPlan.setTenantId(importedDeployment.getTenantId());
        importedBootstrapPlan.setName(importedDeployment.getName() + " vectorization");
        importedBootstrapPlan.setSyncState("BOOTSTRAP_REQUIRED");
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
        when(vectorizationPlanRepository.findByDeploymentId(importedDeployment.getId())).thenReturn(Optional.of(importedBootstrapPlan));
        when(draftRepository.save(any(DeploymentDraftEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentRepository.save(any(DeploymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(platformSecretRepository.save(any(PlatformSecretEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vectorizationSourceConnectionRepository.save(any(VectorizationSourceConnectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vectorizationPlanRevisionRepository.save(any(VectorizationPlanRevisionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        List<String> savedPlanActiveRevisionIds = new ArrayList<>();
        when(vectorizationPlanRepository.save(any(VectorizationPlanEntity.class))).thenAnswer(invocation -> {
            VectorizationPlanEntity savedPlan = invocation.getArgument(0);
            savedPlanActiveRevisionIds.add(savedPlan.getActiveRevisionId());
            return savedPlan;
        });

        var result = service.importDeployment(new DeploymentImportRequest(
            export.bundle(),
            ImportMode.SEALED_CLONE,
            null,
            "Imported Deployment",
            "production-staging",
            "dtp-coolify-production",
            importedDeployment.getCustomerId(),
            importedDeployment.getTenantId(),
            privateKeyPem(keyPair),
            "sealed clone vectorization"
        ));

        assertThat(result.deploymentId()).isEqualTo(importedDeployment.getId());
        ArgumentCaptor<VectorizationSourceConnectionEntity> sourceCaptor = ArgumentCaptor.forClass(VectorizationSourceConnectionEntity.class);
        verify(vectorizationSourceConnectionRepository).save(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().getDeploymentId()).isEqualTo(importedDeployment.getId());
        assertThat(sourceCaptor.getValue().getCustomerId()).isEqualTo(importedDeployment.getCustomerId());
        assertThat(sourceCaptor.getValue().getSecretReferencesJson()).contains(sourceToken.getName());

        verify(vectorizationPlanRepository, never()).deleteByDeploymentId(importedDeployment.getId());
        ArgumentCaptor<VectorizationPlanRevisionEntity> revisionCaptor = ArgumentCaptor.forClass(VectorizationPlanRevisionEntity.class);
        verify(vectorizationPlanRevisionRepository).save(revisionCaptor.capture());
        assertThat(revisionCaptor.getValue().getDeploymentId()).isEqualTo(importedDeployment.getId());
        assertThat(revisionCaptor.getValue().getSourceConnectionId()).isEqualTo(sourceCaptor.getValue().getId());
        assertThat(revisionCaptor.getValue().getMappingConfigJson()).contains("produs-safe-knowledge");

        ArgumentCaptor<VectorizationPlanEntity> planCaptor = ArgumentCaptor.forClass(VectorizationPlanEntity.class);
        verify(vectorizationPlanRepository, times(2)).save(planCaptor.capture());
        VectorizationPlanEntity finalSavedPlan = planCaptor.getAllValues().get(1);
        assertThat(savedPlanActiveRevisionIds).hasSize(2);
        assertThat(savedPlanActiveRevisionIds.get(0)).isNull();
        assertThat(savedPlanActiveRevisionIds.get(1)).isEqualTo(revisionCaptor.getValue().getId());
        assertThat(finalSavedPlan.getId()).isEqualTo(importedBootstrapPlan.getId());
        assertThat(finalSavedPlan.getDeploymentId()).isEqualTo(importedDeployment.getId());
        assertThat(finalSavedPlan.getSourceConnectionId()).isEqualTo(sourceCaptor.getValue().getId());
        assertThat(finalSavedPlan.getActiveRevisionId()).isEqualTo(revisionCaptor.getValue().getId());
        assertThat(finalSavedPlan.getSyncState()).isEqualTo("BOOTSTRAP_REQUIRED");
    }

    @Test
    void sealedCloneRestoresManagedProductServiceDependenciesWithoutSourceBinding() throws Exception {
        DeploymentBundleExportImportService service = service();
        DeploymentEntity sourceDeployment = deployment();
        DeploymentDraftEntity sourceDraft = draft(sourceDeployment.getId());
        sourceDraft.setActionsConfigJson("""
            {
              "actions": [
                {
                  "id": "produs_catalog_export",
                  "adapterType": "mcp-tool",
                  "execution": {
                    "adapterType": "mcp-tool",
                    "mcp": {"serverRef": "produs-staging", "toolName": "produs.catalog.export"}
                  }
                }
              ]
            }
            """);
        PlatformManagedProductServiceEntity gateway = mcpGatewayService();
        PlatformSecretEntity gatewaySecret = deploymentSecret(
            gateway.getSecretName(),
            "gateway-secret-value",
            null
        );
        stubExportState(sourceDeployment, sourceDraft, gatewaySecret);
        when(managedProductServiceRepository.findByServiceRefIgnoreCase("mcp-execution-gateway"))
            .thenReturn(Optional.of(gateway));
        KeyPair keyPair = rsaKeyPair();

        var export = service.exportDeployment(
            sourceDeployment.getId(),
            new DeploymentExportRequest(
                ExportMode.SEALED_BACKUP,
                "managed service lift shift",
                new ExportRecipient(OPERATOR_PUBLIC_KEY, publicKeyPem(keyPair)),
                true,
                true
            )
        );

        assertThat(export.bundle().path("manifest").path("managedProductServiceDependencies")).hasSize(1);
        assertThat(export.bundle().path("manifest").path("managedProductServiceDependencies").get(0).path("serviceRef").asText())
            .isEqualTo("mcp-execution-gateway");
        assertThat(export.bundle().toString())
            .doesNotContain("https://mcp-execution-gateway.46.224.145.148.sslip.io")
            .doesNotContain("http://mcp-execution-gateway.internal");
        assertThat(export.secretSummary().items())
            .anySatisfy(item -> {
                assertThat(item.secretName()).isEqualTo(gateway.getSecretName());
                assertThat(item.valueIncluded()).isTrue();
                assertThat(item.sources()).anyMatch(source -> source.startsWith("managed-product-service"));
            });

        DeploymentEntity importedDeployment = deployment();
        importedDeployment.setId("dep-imported");
        importedDeployment.setName("Imported Deployment");
        importedDeployment.setEnvironmentName("production-staging");
        importedDeployment.setCustomerId("cust-imported");
        importedDeployment.setTenantId("ten-imported");
        importedDeployment.setActiveDraftId("drf-imported");
        DeploymentDraftEntity importedDraft = draft(importedDeployment.getId());
        importedDraft.setId("drf-imported");
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
        when(platformSecretRepository.save(any(PlatformSecretEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(managedProductServiceRepository.findByServiceRefIgnoreCase("mcp-execution-gateway"))
            .thenReturn(Optional.empty());
        when(managedProductServiceRepository.save(any(PlatformManagedProductServiceEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.importDeployment(new DeploymentImportRequest(
            export.bundle(),
            ImportMode.SEALED_CLONE,
            null,
            "Imported Deployment",
            "production-staging",
            "dtp-coolify-production",
            importedDeployment.getCustomerId(),
            importedDeployment.getTenantId(),
            privateKeyPem(keyPair),
            "sealed clone managed service"
        ));

        ArgumentCaptor<PlatformManagedProductServiceEntity> serviceCaptor =
            ArgumentCaptor.forClass(PlatformManagedProductServiceEntity.class);
        verify(managedProductServiceRepository).save(serviceCaptor.capture());
        PlatformManagedProductServiceEntity importedService = serviceCaptor.getValue();
        assertThat(importedService.getServiceRef()).isEqualTo("mcp-execution-gateway");
        assertThat(importedService.getBaseUrl()).isNull();
        assertThat(importedService.getPrivateNetworkUrl()).isNull();
        assertThat(importedService.getStatus()).isEqualTo("CREATED");
        assertThat(importedService.getEnvironmentScope()).isEqualTo("production-staging");
        assertThat(importedService.getDetailsJson()).contains("dtp-coolify-production");

        ArgumentCaptor<PlatformSecretEntity> secretCaptor = ArgumentCaptor.forClass(PlatformSecretEntity.class);
        verify(platformSecretRepository).save(secretCaptor.capture());
        assertThat(secretCaptor.getValue().getName()).isEqualTo(gateway.getSecretName());
        assertThat(secretCaptor.getValue().getDeploymentId()).isNull();
        assertThat(secretCaptor.getValue().getSecretValue()).isEqualTo("gateway-secret-value");
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
            vectorizationPlanRepository,
            vectorizationSourceConnectionRepository,
            vectorizationPlanRevisionRepository,
            managedProductServiceRepository,
            productProvisioningProperties,
            deploymentAccessService,
            deploymentService,
            platformAuditService,
            sealingService
        );
    }

    private void stubExportState(DeploymentEntity deployment, DeploymentDraftEntity draft, PlatformSecretEntity... secrets) {
        when(deploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentAdminAccess(deployment)).thenReturn(deployment);
        when(draftRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(versionRepository.findByDeploymentIdOrderByPublishedAtDesc(deployment.getId())).thenReturn(List.of());
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())).thenReturn(Optional.empty());
        when(verificationRunRepository.findByDeploymentIdOrderByCreatedAtDesc(deployment.getId())).thenReturn(List.of());
        when(marketplacePluginInstallRepository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId())).thenReturn(List.of());
        when(resourceHandleRepository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId())).thenReturn(List.of());
        when(managedVectorResourceRepository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId())).thenReturn(List.of());
        when(vectorizationPlanRepository.findByDeploymentId(deployment.getId())).thenReturn(Optional.empty());
        when(vectorizationSourceConnectionRepository.findByDeploymentId(deployment.getId())).thenReturn(Optional.empty());
        when(secretBindingRepository.findByDeploymentIdOrderBySecretPurposeAsc(deployment.getId())).thenReturn(List.of());
        when(publicApiDeploymentRepository.findByDeploymentId(deployment.getId())).thenReturn(List.of());
        when(platformSecretRepository.findByScopeTypeAndDeploymentIdOrderByUpdatedAtDesc(
            PlatformSecretScopeType.DEPLOYMENT_MANAGED,
            deployment.getId()
        )).thenReturn(List.of(secrets));
        for (PlatformSecretEntity secret : secrets) {
            when(platformSecretRepository.findById(secret.getName())).thenReturn(Optional.of(secret));
        }
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

    private static PlatformManagedProductServiceEntity mcpGatewayService() {
        Instant now = Instant.now();
        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("psv-mcp-gateway");
        service.setServiceRef("mcp-execution-gateway");
        service.setDisplayName("MCP Execution Gateway");
        service.setProductFamily("MCP");
        service.setServiceKind("MCP_EXECUTION_GATEWAY_SERVICE");
        service.setDeploymentMode("SHARED");
        service.setTenantMode("MULTI_TENANT");
        service.setEnvironmentScope("staging");
        service.setDesiredReplicas(1);
        service.setActualReplicas(1);
        service.setMinReplicas(1);
        service.setMaxReplicas(3);
        service.setBaseUrl("https://mcp-execution-gateway.46.224.145.148.sslip.io");
        service.setPrivateNetworkUrl("http://mcp-execution-gateway.internal");
        service.setHealthPath("/actuator/health");
        service.setServiceRoot("product-services/mcp-execution-gateway-service");
        service.setDockerfilePath("product-services/mcp-execution-gateway-service/deploy/container/Dockerfile");
        service.setSecretName("MANAGED_PRODUCT_MCP_EXECUTION_GATEWAY_API_KEY");
        service.setStatus("ACTIVE");
        service.setDetailsJson("""
            {
              "providerType": "COOLIFY",
              "targetProfileId": "dtp-coolify-staging",
              "coolifyApplicationUuid": "staging-app",
              "lastReconcileStatus": "SUCCESS"
            }
            """);
        service.setCreatedAt(now);
        service.setUpdatedAt(now);
        return service;
    }

    private static VectorizationSourceConnectionEntity vectorizationSourceConnection(String deploymentId, String secretName) {
        Instant now = Instant.now();
        VectorizationSourceConnectionEntity connection = new VectorizationSourceConnectionEntity();
        connection.setId("vcn-source");
        connection.setDeploymentId(deploymentId);
        connection.setCustomerId("cust-test");
        connection.setTenantId("ten-test");
        connection.setName("ProdUS safe knowledge export");
        connection.setAdapterType("REST_API");
        connection.setAuthMode("BEARER");
        connection.setStatus("ACTIVE");
        connection.setConnectionConfigJson("{\"baseUrl\":\"https://produs-api.example.test\",\"path\":\"/api/ai/loomai/knowledge-export\"}");
        connection.setSecretReferencesJson("{\"platformSecretRefs\":{\"token\":\"" + secretName + "\"}}");
        connection.setDiscoverySummaryJson("{\"recordCount\":157}");
        connection.setCreatedAt(now);
        connection.setUpdatedAt(now);
        return connection;
    }

    private static VectorizationPlanEntity vectorizationPlan(String deploymentId, String sourceConnectionId) {
        Instant now = Instant.now();
        VectorizationPlanEntity plan = new VectorizationPlanEntity();
        plan.setId("vpl-source");
        plan.setDeploymentId(deploymentId);
        plan.setCustomerId("cust-test");
        plan.setTenantId("ten-test");
        plan.setName("ProdUS vectorization");
        plan.setStatus("ACTIVE");
        plan.setRunnerMode("PLATFORM_MANAGED_AUTO");
        plan.setSyncState("READY");
        plan.setSyncReasonCodesJson("[]");
        plan.setSyncReasonDetailsJson("{}");
        plan.setSourceConnectionId(sourceConnectionId);
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        return plan;
    }

    private static VectorizationPlanRevisionEntity vectorizationRevision(String deploymentId, String planId, String sourceConnectionId) {
        Instant now = Instant.now();
        VectorizationPlanRevisionEntity revision = new VectorizationPlanRevisionEntity();
        revision.setId("vpr-source");
        revision.setPlanId(planId);
        revision.setDeploymentId(deploymentId);
        revision.setRevisionNumber(3);
        revision.setStatus("ACTIVE");
        revision.setSourceConnectionId(sourceConnectionId);
        revision.setEntityScopeJson("[\"produs-safe-knowledge\"]");
        revision.setMappingConfigJson("{\"entityMappings\":{\"produs-safe-knowledge\":{\"targetEntityTypeField\":\"vectorSpace\"}}}");
        revision.setExecutionConfigJson("{\"pageSize\":100,\"batchSize\":100}");
        revision.setIndexedOutputHash("source-index-hash");
        revision.setCreatedByActorId("operator-test");
        revision.setCreatedAt(now);
        revision.setUpdatedAt(now);
        return revision;
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
