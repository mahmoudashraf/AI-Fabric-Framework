package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentManagedVectorResourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRailwayLiveFieldDriftSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRailwayLiveReadbackSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRailwayLiveServiceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRemediationExecutionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRemediationSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentWorkspaceAccessSummary;
import com.ai.fabric.platform.backend.deployment.model.ExecuteDeploymentRemediationRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentRemediationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getSummaryBlocksProviderSideRestartWhenRailwayDriftExists() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentPocWorkspaceService deploymentPocWorkspaceService = mock(DeploymentPocWorkspaceService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        DeploymentRailwayLiveReadbackService deploymentRailwayLiveReadbackService = mock(DeploymentRailwayLiveReadbackService.class);
        DeploymentManagedVectorResourceService deploymentManagedVectorResourceService = mock(DeploymentManagedVectorResourceService.class);
        PineconeControlPlaneClient pineconeControlPlaneClient = mock(PineconeControlPlaneClient.class);
        QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient = mock(QdrantCloudControlPlaneClient.class);
        ZillizCloudControlPlaneClient zillizCloudControlPlaneClient = mock(ZillizCloudControlPlaneClient.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        DeploymentRemediationService service = new DeploymentRemediationService(
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            deploymentAccessService,
            deploymentService,
            deploymentPocWorkspaceService,
            railwayProvisioningPlanService,
            deploymentRailwayLiveReadbackService,
            deploymentManagedVectorResourceService,
            pineconeControlPlaneClient,
            qdrantCloudControlPlaneClient,
            zillizCloudControlPlaneClient,
            railwayGraphqlClient,
            provisioningProperties(),
            platformSecretService,
            platformAuditService,
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentVersionEntity activeVersion = activeVersion();
        DeploymentReleaseEntity latestRelease = latestRelease();

        when(deploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(deploymentVersionRepository.findById(activeVersion.getId())).thenReturn(Optional.of(activeVersion));
        when(deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId()))
            .thenReturn(Optional.of(latestRelease));
        when(deploymentAccessService.summarizeAccess(deployment))
            .thenReturn(new DeploymentWorkspaceAccessSummary("DEPLOYMENT_ADMIN", true, true, true));
        when(platformSecretService.isSecretPresent("APP_ADMIN_API_KEY")).thenReturn(true);
        when(railwayProvisioningPlanService.buildPlan(deployment, activeVersion)).thenReturn(null);
        when(deploymentRailwayLiveReadbackService.build(
            any(DeploymentEntity.class),
            any(DeploymentReleaseEntity.class),
            nullable(com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary.class)
        )).thenReturn(runtimeDriftReadback());
        when(deploymentManagedVectorResourceService.buildStateSummary(anyString(), any(), any(), anyString()))
            .thenReturn(managedVectorState(false));
        when(deploymentManagedVectorResourceService.listResources(anyString())).thenReturn(List.of());

        DeploymentRemediationSummary summary = service.getSummary(deployment.getId());

        assertThat(summary.providerDriftDetected()).isTrue();
        assertThat(summary.providerDriftStatus()).isEqualTo("WARNING");
        assertThat(summary.providerDriftMessage()).contains("drift");
        assertThat(summary.summaryMessage()).contains("Redeploy the active version");
        assertThat(summary.actions())
            .filteredOn(action -> action.key().equals(DeploymentRemediationService.REDEPLOY_ACTIVE_VERSION))
            .singleElement()
            .satisfies(action -> {
                assertThat(action.available()).isTrue();
                assertThat(action.operatorGuidance()).contains("Recommended first");
            });
        assertThat(summary.actions())
            .filteredOn(action -> action.key().equals(DeploymentRemediationService.RESTART_RUNTIME_SERVICE))
            .singleElement()
            .satisfies(action -> {
                assertThat(action.available()).isFalse();
                assertThat(action.blockedReason()).contains("drifted");
            });
        assertThat(summary.actions())
            .filteredOn(action -> action.key().equals(DeploymentRemediationService.RESTART_REST_CONNECTOR_SERVICE))
            .singleElement()
            .satisfies(action -> {
                assertThat(action.available()).isFalse();
                assertThat(action.blockedReason()).contains("drifted");
            });
        assertThat(summary.actions())
            .filteredOn(action -> action.key().equals(DeploymentRemediationService.RESET_RUNTIME_VECTORS))
            .singleElement()
            .satisfies(action -> {
                assertThat(action.available()).isFalse();
                assertThat(action.blockedReason()).contains("drifted");
            });
    }

    @Test
    void executeBlocksRuntimeRestartWhenRailwayDriftExists() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentPocWorkspaceService deploymentPocWorkspaceService = mock(DeploymentPocWorkspaceService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        DeploymentRailwayLiveReadbackService deploymentRailwayLiveReadbackService = mock(DeploymentRailwayLiveReadbackService.class);
        DeploymentManagedVectorResourceService deploymentManagedVectorResourceService = mock(DeploymentManagedVectorResourceService.class);
        PineconeControlPlaneClient pineconeControlPlaneClient = mock(PineconeControlPlaneClient.class);
        QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient = mock(QdrantCloudControlPlaneClient.class);
        ZillizCloudControlPlaneClient zillizCloudControlPlaneClient = mock(ZillizCloudControlPlaneClient.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        DeploymentRemediationService service = new DeploymentRemediationService(
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            deploymentAccessService,
            deploymentService,
            deploymentPocWorkspaceService,
            railwayProvisioningPlanService,
            deploymentRailwayLiveReadbackService,
            deploymentManagedVectorResourceService,
            pineconeControlPlaneClient,
            qdrantCloudControlPlaneClient,
            zillizCloudControlPlaneClient,
            railwayGraphqlClient,
            provisioningProperties(),
            platformSecretService,
            platformAuditService,
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentVersionEntity activeVersion = activeVersion();
        DeploymentReleaseEntity latestRelease = latestRelease();

        when(deploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(deploymentVersionRepository.findById(activeVersion.getId())).thenReturn(Optional.of(activeVersion));
        when(deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId()))
            .thenReturn(Optional.of(latestRelease));
        when(railwayProvisioningPlanService.buildPlan(deployment, activeVersion)).thenReturn(null);
        when(deploymentRailwayLiveReadbackService.build(
            any(DeploymentEntity.class),
            any(DeploymentReleaseEntity.class),
            nullable(com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary.class)
        )).thenReturn(runtimeDriftReadback());
        when(deploymentManagedVectorResourceService.buildStateSummary(anyString(), any(), any(), anyString()))
            .thenReturn(managedVectorState(false));
        when(deploymentManagedVectorResourceService.listResources(anyString())).thenReturn(List.of());

        assertThatThrownBy(() -> service.execute(
            deployment.getId(),
            DeploymentRemediationService.RESTART_RUNTIME_SERVICE,
            new ExecuteDeploymentRemediationRequest(true, "recover runtime", null)
        ))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getReason())
            .asString()
            .contains("blocked because Railway live managed services have drifted");

        verify(deploymentAccessService).requireDeploymentAdminAccess(deployment);
    }

    @Test
    void getSummaryBlocksRuntimeVectorResetWhenManagedVectorDriftExists() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentPocWorkspaceService deploymentPocWorkspaceService = mock(DeploymentPocWorkspaceService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        DeploymentRailwayLiveReadbackService deploymentRailwayLiveReadbackService = mock(DeploymentRailwayLiveReadbackService.class);
        DeploymentManagedVectorResourceService deploymentManagedVectorResourceService = mock(DeploymentManagedVectorResourceService.class);
        PineconeControlPlaneClient pineconeControlPlaneClient = mock(PineconeControlPlaneClient.class);
        QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient = mock(QdrantCloudControlPlaneClient.class);
        ZillizCloudControlPlaneClient zillizCloudControlPlaneClient = mock(ZillizCloudControlPlaneClient.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        DeploymentRemediationService service = new DeploymentRemediationService(
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            deploymentAccessService,
            deploymentService,
            deploymentPocWorkspaceService,
            railwayProvisioningPlanService,
            deploymentRailwayLiveReadbackService,
            deploymentManagedVectorResourceService,
            pineconeControlPlaneClient,
            qdrantCloudControlPlaneClient,
            zillizCloudControlPlaneClient,
            railwayGraphqlClient,
            provisioningProperties(),
            platformSecretService,
            platformAuditService,
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentVersionEntity activeVersion = activeVersion();
        DeploymentReleaseEntity latestRelease = latestRelease();

        when(deploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(deploymentVersionRepository.findById(activeVersion.getId())).thenReturn(Optional.of(activeVersion));
        when(deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId()))
            .thenReturn(Optional.of(latestRelease));
        when(deploymentAccessService.summarizeAccess(deployment))
            .thenReturn(new DeploymentWorkspaceAccessSummary("DEPLOYMENT_ADMIN", true, true, true));
        when(platformSecretService.isSecretPresent("APP_ADMIN_API_KEY")).thenReturn(true);
        when(railwayProvisioningPlanService.buildPlan(deployment, activeVersion)).thenReturn(null);
        when(deploymentRailwayLiveReadbackService.build(
            any(DeploymentEntity.class),
            any(DeploymentReleaseEntity.class),
            nullable(com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary.class)
        )).thenReturn(alignedReadback());
        when(deploymentManagedVectorResourceService.buildStateSummary(anyString(), any(), any(), anyString()))
            .thenReturn(managedVectorState(true));
        when(deploymentManagedVectorResourceService.listResources(anyString())).thenReturn(List.of());

        DeploymentRemediationSummary summary = service.getSummary(deployment.getId());

        assertThat(summary.managedVectorDriftDetected()).isTrue();
        assertThat(summary.managedVectorDriftStatus()).isEqualTo("WARNING");
        assertThat(summary.managedVectorDriftMessage()).contains("Managed vector drift");
        assertThat(summary.actions())
            .filteredOn(action -> action.key().equals(DeploymentRemediationService.RESET_RUNTIME_VECTORS))
            .singleElement()
            .satisfies(action -> {
                assertThat(action.available()).isFalse();
                assertThat(action.blockedReason()).contains("Managed vector resource records");
            });
    }

    @Test
    void executeRecreateManagedVectorTargetDeletesIndexAndTriggersRedeploy() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentPocWorkspaceService deploymentPocWorkspaceService = mock(DeploymentPocWorkspaceService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        DeploymentRailwayLiveReadbackService deploymentRailwayLiveReadbackService = mock(DeploymentRailwayLiveReadbackService.class);
        DeploymentManagedVectorResourceService deploymentManagedVectorResourceService = mock(DeploymentManagedVectorResourceService.class);
        PineconeControlPlaneClient pineconeControlPlaneClient = mock(PineconeControlPlaneClient.class);
        QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient = mock(QdrantCloudControlPlaneClient.class);
        ZillizCloudControlPlaneClient zillizCloudControlPlaneClient = mock(ZillizCloudControlPlaneClient.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        DeploymentRemediationService service = new DeploymentRemediationService(
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            deploymentAccessService,
            deploymentService,
            deploymentPocWorkspaceService,
            railwayProvisioningPlanService,
            deploymentRailwayLiveReadbackService,
            deploymentManagedVectorResourceService,
            pineconeControlPlaneClient,
            qdrantCloudControlPlaneClient,
            zillizCloudControlPlaneClient,
            railwayGraphqlClient,
            provisioningProperties(),
            platformSecretService,
            platformAuditService,
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentVersionEntity activeVersion = activeVersion();
        activeVersion.setProviderConfigJson(objectMapper.createObjectNode()
            .put("vectorStrategy", "pinecone")
            .put("vectorProvisioningMode", "PLATFORM_MANAGED")
            .put("pineconeManagedIndexEnabled", true)
            .put("pineconeIndexName", "dep-123-index")
            .toString());
        DeploymentReleaseEntity latestRelease = latestRelease();
        DeploymentReleaseSummary release = new DeploymentReleaseSummary(
            "rel-456",
            "dep-123",
            "ver-123",
            "APPLY_REQUESTED",
            "PENDING",
            "QUEUED",
            "RAILWAY_API",
            null,
            null,
            null,
            null,
            objectMapper.createObjectNode(),
            Instant.now(),
            null,
            Instant.now()
        );

        when(deploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(deploymentVersionRepository.findById(activeVersion.getId())).thenReturn(Optional.of(activeVersion));
        when(deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId()))
            .thenReturn(Optional.of(latestRelease));
        when(railwayProvisioningPlanService.buildPlan(deployment, activeVersion)).thenReturn(null);
        when(deploymentRailwayLiveReadbackService.build(
            any(DeploymentEntity.class),
            any(DeploymentReleaseEntity.class),
            nullable(com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary.class)
        )).thenReturn(alignedReadback());
        when(deploymentManagedVectorResourceService.buildStateSummary(anyString(), any(), any(), anyString()))
            .thenReturn(managedVectorState(false));
        when(deploymentManagedVectorResourceService.listResources(anyString()))
            .thenReturn(List.of(managedVectorResource("ACTIVE", "pinecone", "MANAGED_SERVERLESS_INDEX", "INDEX", "dep-123-index", "idx-123")));
        when(platformSecretService.resolveSecret("PINECONE_API_KEY")).thenReturn("pc-test");
        when(pineconeControlPlaneClient.findIndexByName("dep-123-index", "pc-test"))
            .thenReturn(new PineconeControlPlaneClient.PineconeIndexSummary(
                "dep-123-index",
                "https://dep-123-index.example.pinecone.io",
                1536,
                "cosine",
                true,
                "enabled"
            ));
        when(deploymentService.applyVersion(eq("dep-123"), eq("ver-123"), eq("apr-1"))).thenReturn(release);

        DeploymentRemediationExecutionSummary summary = service.execute(
            "dep-123",
            DeploymentRemediationService.RECREATE_MANAGED_VECTOR_TARGET,
            new ExecuteDeploymentRemediationRequest(true, "rebuild index", "apr-1")
        );

        assertThat(summary.status()).isEqualTo("TRIGGERED");
        verify(pineconeControlPlaneClient).configureDeletionProtection("dep-123-index", false, "pc-test");
        verify(pineconeControlPlaneClient).deleteIndex("dep-123-index", "pc-test");
        verify(pineconeControlPlaneClient).awaitIndexDeleted("dep-123-index", "pc-test");
        verify(deploymentService).applyVersion("dep-123", "ver-123", "apr-1");
    }

    @Test
    void executeCleanupManagedVectorResourcesDeletesDetachedVendorResourcesAndClearsManagedSecrets() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentPocWorkspaceService deploymentPocWorkspaceService = mock(DeploymentPocWorkspaceService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        DeploymentRailwayLiveReadbackService deploymentRailwayLiveReadbackService = mock(DeploymentRailwayLiveReadbackService.class);
        DeploymentManagedVectorResourceService deploymentManagedVectorResourceService = mock(DeploymentManagedVectorResourceService.class);
        PineconeControlPlaneClient pineconeControlPlaneClient = mock(PineconeControlPlaneClient.class);
        QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient = mock(QdrantCloudControlPlaneClient.class);
        ZillizCloudControlPlaneClient zillizCloudControlPlaneClient = mock(ZillizCloudControlPlaneClient.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        DeploymentRemediationService service = new DeploymentRemediationService(
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            deploymentAccessService,
            deploymentService,
            deploymentPocWorkspaceService,
            railwayProvisioningPlanService,
            deploymentRailwayLiveReadbackService,
            deploymentManagedVectorResourceService,
            pineconeControlPlaneClient,
            qdrantCloudControlPlaneClient,
            zillizCloudControlPlaneClient,
            railwayGraphqlClient,
            provisioningProperties(),
            platformSecretService,
            platformAuditService,
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        deployment.setArchivedAt(Instant.now());
        DeploymentVersionEntity activeVersion = activeVersion();
        DeploymentReleaseEntity latestRelease = latestRelease();
        List<DeploymentManagedVectorResourceSummary> resources = List.of(
            managedVectorResource("DETACHED", "pinecone", "MANAGED_SERVERLESS_INDEX", "INDEX", "dep-123-index", "idx-123")
        );

        when(deploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(deploymentVersionRepository.findById(activeVersion.getId())).thenReturn(Optional.of(activeVersion));
        when(deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId()))
            .thenReturn(Optional.of(latestRelease));
        when(railwayProvisioningPlanService.buildPlan(deployment, activeVersion)).thenReturn(null);
        when(deploymentRailwayLiveReadbackService.build(
            any(DeploymentEntity.class),
            any(DeploymentReleaseEntity.class),
            nullable(com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary.class)
        )).thenReturn(alignedReadback());
        when(deploymentManagedVectorResourceService.buildStateSummary(anyString(), any(), any(), anyString()))
            .thenReturn(managedVectorState(false));
        when(deploymentManagedVectorResourceService.listResources(anyString())).thenReturn(resources);
        when(platformSecretService.resolveSecret("PINECONE_API_KEY")).thenReturn("pc-test");
        when(platformSecretService.isManagedSecretName("MANAGED_PINECONE_API_KEY_DEP_DEP_123")).thenReturn(true);
        when(deploymentManagedVectorResourceService.managedSecretReferencedByActiveResource("MANAGED_PINECONE_API_KEY_DEP_DEP_123")).thenReturn(false);
        when(pineconeControlPlaneClient.findIndexByName("dep-123-index", "pc-test"))
            .thenReturn(new PineconeControlPlaneClient.PineconeIndexSummary(
                "dep-123-index",
                "https://dep-123-index.example.pinecone.io",
                1536,
                "cosine",
                true,
                "enabled"
            ));

        DeploymentRemediationExecutionSummary summary = service.execute(
            "dep-123",
            DeploymentRemediationService.CLEANUP_MANAGED_VECTOR_RESOURCES,
            new ExecuteDeploymentRemediationRequest(true, "retire resources", null)
        );

        assertThat(summary.status()).isEqualTo("COMPLETED");
        verify(pineconeControlPlaneClient).configureDeletionProtection("dep-123-index", false, "pc-test");
        verify(pineconeControlPlaneClient).deleteIndex("dep-123-index", "pc-test");
        verify(pineconeControlPlaneClient).awaitIndexDeleted("dep-123-index", "pc-test");
        verify(deploymentManagedVectorResourceService).deleteResourceRecords(List.of("mvr-1"));
        verify(platformSecretService).clearManagedSecret(eq("MANAGED_PINECONE_API_KEY_DEP_DEP_123"), any());
        verify(deploymentService, never()).applyVersion(anyString(), anyString(), any());
    }

    @Test
    void executeRecreateManagedVectorTargetDeletesManagedZillizClusterAndReappliesVersion() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentPocWorkspaceService deploymentPocWorkspaceService = mock(DeploymentPocWorkspaceService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        DeploymentRailwayLiveReadbackService deploymentRailwayLiveReadbackService = mock(DeploymentRailwayLiveReadbackService.class);
        DeploymentManagedVectorResourceService deploymentManagedVectorResourceService = mock(DeploymentManagedVectorResourceService.class);
        PineconeControlPlaneClient pineconeControlPlaneClient = mock(PineconeControlPlaneClient.class);
        QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient = mock(QdrantCloudControlPlaneClient.class);
        ZillizCloudControlPlaneClient zillizCloudControlPlaneClient = mock(ZillizCloudControlPlaneClient.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        DeploymentRemediationService service = new DeploymentRemediationService(
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            deploymentAccessService,
            deploymentService,
            deploymentPocWorkspaceService,
            railwayProvisioningPlanService,
            deploymentRailwayLiveReadbackService,
            deploymentManagedVectorResourceService,
            pineconeControlPlaneClient,
            qdrantCloudControlPlaneClient,
            zillizCloudControlPlaneClient,
            railwayGraphqlClient,
            provisioningProperties(),
            platformSecretService,
            platformAuditService,
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentVersionEntity activeVersion = activeVersion();
        activeVersion.setProviderConfigJson(objectMapper.createObjectNode()
            .put("vectorStrategy", "milvus")
            .put("vectorProvisioningMode", "PLATFORM_MANAGED")
            .put("zillizCloudProjectId", "project-1")
            .put("zillizCloudRegionId", "gcp-us-west1")
            .put("zillizCloudClusterPlan", "Serverless")
            .toString());
        DeploymentReleaseEntity latestRelease = latestRelease();
        DeploymentReleaseSummary release = new DeploymentReleaseSummary(
            "rel-789",
            "dep-123",
            "ver-123",
            "APPLY_REQUESTED",
            "PENDING",
            "QUEUED",
            "RAILWAY_API",
            null,
            null,
            null,
            null,
            objectMapper.createObjectNode(),
            Instant.now(),
            null,
            Instant.now()
        );
        List<DeploymentManagedVectorResourceSummary> resources = List.of(
            new DeploymentManagedVectorResourceSummary(
                "mvr-zilliz",
                "dep-123",
                "ver-123",
                "rel-123",
                "zilliz",
                "milvus",
                "PLATFORM_MANAGED",
                "MANAGED_ZILLIZ_CLOUD_CLUSTER",
                "CLUSTER",
                "aifabric-123",
                "cluster-1",
                "https://cluster-1.gcp-us-west1.zillizcloud.com",
                "ACTIVE",
                "REUSED",
                List.of("ZILLIZ_CLOUD_API_KEY", "MANAGED_MILVUS_USERNAME_DEP_DEP_123", "MANAGED_MILVUS_PASSWORD_DEP_DEP_123"),
                objectMapper.createObjectNode()
                    .put("clusterId", "cluster-1")
                    .put("clusterName", "aifabric-123"),
                "ALIGNED",
                null,
                Instant.parse("2026-03-31T10:00:00Z"),
                Instant.parse("2026-03-31T10:00:00Z")
            )
        );

        when(deploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(deploymentVersionRepository.findById(activeVersion.getId())).thenReturn(Optional.of(activeVersion));
        when(deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId()))
            .thenReturn(Optional.of(latestRelease));
        when(railwayProvisioningPlanService.buildPlan(deployment, activeVersion)).thenReturn(null);
        when(deploymentRailwayLiveReadbackService.build(
            any(DeploymentEntity.class),
            any(DeploymentReleaseEntity.class),
            nullable(com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary.class)
        )).thenReturn(alignedReadback());
        when(deploymentManagedVectorResourceService.buildStateSummary(anyString(), any(), any(), anyString()))
            .thenReturn(managedVectorState(false));
        when(deploymentManagedVectorResourceService.listResources(anyString())).thenReturn(resources);
        when(platformSecretService.resolveSecret("ZILLIZ_CLOUD_API_KEY")).thenReturn("zc-test");
        when(deploymentService.applyVersion(eq("dep-123"), eq("ver-123"), eq("apr-2"))).thenReturn(release);

        DeploymentRemediationExecutionSummary summary = service.execute(
            "dep-123",
            DeploymentRemediationService.RECREATE_MANAGED_VECTOR_TARGET,
            new ExecuteDeploymentRemediationRequest(true, "rebuild cluster", "apr-2")
        );

        assertThat(summary.status()).isEqualTo("TRIGGERED");
        verify(zillizCloudControlPlaneClient).deleteCluster("cluster-1", "zc-test");
        verify(zillizCloudControlPlaneClient).awaitClusterDeleted("cluster-1", "zc-test");
        verify(deploymentService).applyVersion("dep-123", "ver-123", "apr-2");
    }

    @Test
    void executeCleanupManagedVectorResourcesDeletesDetachedZillizClusterAndClearsManagedSecrets() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentPocWorkspaceService deploymentPocWorkspaceService = mock(DeploymentPocWorkspaceService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        DeploymentRailwayLiveReadbackService deploymentRailwayLiveReadbackService = mock(DeploymentRailwayLiveReadbackService.class);
        DeploymentManagedVectorResourceService deploymentManagedVectorResourceService = mock(DeploymentManagedVectorResourceService.class);
        PineconeControlPlaneClient pineconeControlPlaneClient = mock(PineconeControlPlaneClient.class);
        QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient = mock(QdrantCloudControlPlaneClient.class);
        ZillizCloudControlPlaneClient zillizCloudControlPlaneClient = mock(ZillizCloudControlPlaneClient.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        DeploymentRemediationService service = new DeploymentRemediationService(
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            deploymentAccessService,
            deploymentService,
            deploymentPocWorkspaceService,
            railwayProvisioningPlanService,
            deploymentRailwayLiveReadbackService,
            deploymentManagedVectorResourceService,
            pineconeControlPlaneClient,
            qdrantCloudControlPlaneClient,
            zillizCloudControlPlaneClient,
            railwayGraphqlClient,
            provisioningProperties(),
            platformSecretService,
            platformAuditService,
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        deployment.setArchivedAt(Instant.now());
        DeploymentVersionEntity activeVersion = activeVersion();
        activeVersion.setProviderConfigJson(objectMapper.createObjectNode()
            .put("vectorStrategy", "milvus")
            .put("vectorProvisioningMode", "PLATFORM_MANAGED")
            .toString());
        DeploymentReleaseEntity latestRelease = latestRelease();
        List<DeploymentManagedVectorResourceSummary> resources = List.of(
            new DeploymentManagedVectorResourceSummary(
                "mvr-zilliz",
                "dep-123",
                "ver-123",
                "rel-123",
                "zilliz",
                "milvus",
                "PLATFORM_MANAGED",
                "MANAGED_ZILLIZ_CLOUD_CLUSTER",
                "CLUSTER",
                "aifabric-123",
                "cluster-1",
                "https://cluster-1.gcp-us-west1.zillizcloud.com",
                "DETACHED",
                "REUSED",
                List.of("ZILLIZ_CLOUD_API_KEY", "MANAGED_MILVUS_USERNAME_DEP_DEP_123", "MANAGED_MILVUS_PASSWORD_DEP_DEP_123"),
                objectMapper.createObjectNode().put("clusterId", "cluster-1"),
                "ALIGNED",
                null,
                Instant.parse("2026-03-31T10:00:00Z"),
                Instant.parse("2026-03-31T10:00:00Z")
            )
        );

        when(deploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(deploymentVersionRepository.findById(activeVersion.getId())).thenReturn(Optional.of(activeVersion));
        when(deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId()))
            .thenReturn(Optional.of(latestRelease));
        when(railwayProvisioningPlanService.buildPlan(deployment, activeVersion)).thenReturn(null);
        when(deploymentRailwayLiveReadbackService.build(
            any(DeploymentEntity.class),
            any(DeploymentReleaseEntity.class),
            nullable(com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary.class)
        )).thenReturn(alignedReadback());
        when(deploymentManagedVectorResourceService.buildStateSummary(anyString(), any(), any(), anyString()))
            .thenReturn(managedVectorState(false));
        when(deploymentManagedVectorResourceService.listResources(anyString())).thenReturn(resources);
        when(platformSecretService.resolveSecret("ZILLIZ_CLOUD_API_KEY")).thenReturn("zc-test");
        when(platformSecretService.isManagedSecretName("MANAGED_MILVUS_USERNAME_DEP_DEP_123")).thenReturn(true);
        when(platformSecretService.isManagedSecretName("MANAGED_MILVUS_PASSWORD_DEP_DEP_123")).thenReturn(true);
        when(deploymentManagedVectorResourceService.managedSecretReferencedByActiveResource("MANAGED_MILVUS_USERNAME_DEP_DEP_123")).thenReturn(false);
        when(deploymentManagedVectorResourceService.managedSecretReferencedByActiveResource("MANAGED_MILVUS_PASSWORD_DEP_DEP_123")).thenReturn(false);

        DeploymentRemediationExecutionSummary summary = service.execute(
            "dep-123",
            DeploymentRemediationService.CLEANUP_MANAGED_VECTOR_RESOURCES,
            new ExecuteDeploymentRemediationRequest(true, "retire zilliz resources", null)
        );

        assertThat(summary.status()).isEqualTo("COMPLETED");
        verify(zillizCloudControlPlaneClient).deleteCluster("cluster-1", "zc-test");
        verify(zillizCloudControlPlaneClient).awaitClusterDeleted("cluster-1", "zc-test");
        verify(deploymentManagedVectorResourceService).deleteResourceRecords(List.of("mvr-zilliz"));
        verify(platformSecretService).clearManagedSecret(eq("MANAGED_MILVUS_USERNAME_DEP_DEP_123"), any());
        verify(platformSecretService).clearManagedSecret(eq("MANAGED_MILVUS_PASSWORD_DEP_DEP_123"), any());
    }

    private PlatformProvisioningProperties provisioningProperties() {
        return new PlatformProvisioningProperties(
            "RAILWAY_API",
            "https://backboard.railway.com/graphql/v2",
            "token",
            "mahmoudashraf/AI-Fabric-Framework",
            "Platformv-V2",
            "dev",
            "workspace-123",
            "ai-infrastructure-module/ai-fabric-runtime",
            "ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile",
            "ai-infrastructure-module/ai-fabric-generic-rest-connector",
            "ai-infrastructure-module/ai-fabric-generic-rest-connector/deploy/railway/Dockerfile",
            "runtime",
            "rest-connector",
            32,
            "",
            "",
            true,
            false,
            60_000,
            Duration.ofSeconds(5),
            Duration.ofMinutes(10)
        );
    }

    private com.ai.fabric.platform.backend.deployment.model.DeploymentManagedVectorStateSummary managedVectorState(boolean driftDetected) {
        return new com.ai.fabric.platform.backend.deployment.model.DeploymentManagedVectorStateSummary(
            driftDetected ? "WARNING" : "READY",
            true,
            "pinecone",
            "PLATFORM_MANAGED",
            driftDetected,
            driftDetected ? "WARNING" : "READY",
            1,
            0,
            driftDetected ? 1 : 0,
            List.of(),
            driftDetected ? "Managed vector drift detected." : null,
            driftDetected ? "Managed vector drift detected." : "Managed vector resources are aligned."
        );
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setName("Drifted deployment");
        deployment.setEnvironmentName("dev");
        deployment.setTemplateId("dev-openai-lucene");
        deployment.setStatus("ACTIVE");
        deployment.setActiveVersionId("ver-123");
        deployment.setRuntimeBaseUrl("https://runtime.example");
        deployment.setConnectorBaseUrl("https://connector.example");
        deployment.setApprovalRequiredForApply(false);
        deployment.setApprovalRequiredForDelete(false);
        deployment.setCreatedAt(Instant.parse("2026-03-31T10:00:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-03-31T10:00:00Z"));
        return deployment;
    }

    private DeploymentVersionEntity activeVersion() {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setDeploymentId("dep-123");
        version.setVersionLabel("v3");
        version.setStatus("PUBLISHED");
        version.setConfigHash("cfg-123");
        version.setProviderConfigJson(objectMapper.createObjectNode()
            .put("vectorStrategy", "pinecone")
            .put("vectorProvisioningMode", "PLATFORM_MANAGED")
            .put("pineconeManagedIndexEnabled", true)
            .put("pineconeIndexName", "dep-123-index")
            .toString());
        version.setPublishedAt(Instant.parse("2026-03-31T10:05:00Z"));
        return version;
    }

    private DeploymentReleaseEntity latestRelease() {
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("APPLIED_VERIFIED");
        release.setProvisioningStatus("SUCCEEDED");
        release.setVerificationStatus("PASSED");
        release.setProvisioningTarget("RAILWAY_API");
        release.setProvisioningDetailsJson("""
            {
              "railway": {
                "environmentId": "env-123",
                "services": {
                  "runtime": {
                    "serviceId": "svc-runtime"
                  },
                  "restConnector": {
                    "serviceId": "svc-rest"
                  }
                }
              }
            }
            """);
        release.setCreatedAt(Instant.parse("2026-03-31T10:06:00Z"));
        release.setAppliedAt(Instant.parse("2026-03-31T10:06:00Z"));
        release.setUpdatedAt(Instant.parse("2026-03-31T10:07:00Z"));
        return release;
    }

    private DeploymentRailwayLiveReadbackSummary runtimeDriftReadback() {
        return new DeploymentRailwayLiveReadbackSummary(
            true,
            "WARNING",
            "Railway live read-back found drift between the platform plan and provider state.",
            "proj-123",
            "ai-fabric-platform",
            "env-123",
            "dev",
            new DeploymentRailwayLiveServiceSummary(
                "runtime",
                "Runtime service",
                "svc-runtime",
                "WARNING",
                "Runtime service has live drift against the platform-managed Railway plan.",
                matchedField("rootDirectory"),
                matchedField("dockerfilePath"),
                matchedField("repository"),
                mismatchedField("branch"),
                matchedField("publicBaseUrl"),
                2,
                1,
                0,
                1,
                List.of()
            ),
            new DeploymentRailwayLiveServiceSummary(
                "restConnector",
                "REST connector",
                "svc-rest",
                "READY",
                "REST connector matches Railway live settings and variables.",
                matchedField("rootDirectory"),
                matchedField("dockerfilePath"),
                matchedField("repository"),
                matchedField("branch"),
                matchedField("publicBaseUrl"),
                2,
                2,
                0,
                0,
                List.of()
            ),
            null
        );
    }

    private DeploymentRailwayLiveReadbackSummary alignedReadback() {
        return new DeploymentRailwayLiveReadbackSummary(
            true,
            "READY",
            "Railway live provider state matches the platform-managed deployment plan.",
            "proj-123",
            "ai-fabric-platform",
            "env-123",
            "dev",
            new DeploymentRailwayLiveServiceSummary(
                "runtime",
                "Runtime service",
                "svc-runtime",
                "READY",
                "Runtime service matches Railway live settings and variables.",
                matchedField("rootDirectory"),
                matchedField("dockerfilePath"),
                matchedField("repository"),
                matchedField("branch"),
                matchedField("publicBaseUrl"),
                2,
                2,
                0,
                0,
                List.of()
            ),
            new DeploymentRailwayLiveServiceSummary(
                "restConnector",
                "REST connector",
                "svc-rest",
                "READY",
                "REST connector matches Railway live settings and variables.",
                matchedField("rootDirectory"),
                matchedField("dockerfilePath"),
                matchedField("repository"),
                matchedField("branch"),
                matchedField("publicBaseUrl"),
                2,
                2,
                0,
                0,
                List.of()
            ),
            null
        );
    }

    private DeploymentRailwayLiveFieldDriftSummary matchedField(String key) {
        return new DeploymentRailwayLiveFieldDriftSummary(
            key,
            key,
            "expected",
            "expected",
            "MATCHED",
            "Matched"
        );
    }

    private DeploymentRailwayLiveFieldDriftSummary mismatchedField(String key) {
        return new DeploymentRailwayLiveFieldDriftSummary(
            key,
            key,
            "expected",
            "actual",
            "MISMATCHED",
            "Mismatched"
        );
    }

    private DeploymentManagedVectorResourceSummary managedVectorResource(String resourceStatus,
                                                                        String vendor,
                                                                        String managedMode,
                                                                        String resourceType,
                                                                        String resourceName,
                                                                        String resourceReference) {
        return new DeploymentManagedVectorResourceSummary(
            "mvr-1",
            "dep-123",
            "ver-123",
            "rel-123",
            vendor,
            vendor,
            "PLATFORM_MANAGED",
            managedMode,
            resourceType,
            resourceName,
            resourceReference,
            "https://vector.example",
            resourceStatus,
            "REUSED",
            List.of("PINECONE_API_KEY", "MANAGED_PINECONE_API_KEY_DEP_DEP_123"),
            objectMapper.createObjectNode()
                .put("accountId", "acct-1")
                .put("clusterId", "cluster-1")
                .put("databaseApiKeyId", "dbkey-1"),
            "ALIGNED",
            null,
            Instant.parse("2026-03-31T10:00:00Z"),
            Instant.parse("2026-03-31T10:00:00Z")
        );
    }
}
