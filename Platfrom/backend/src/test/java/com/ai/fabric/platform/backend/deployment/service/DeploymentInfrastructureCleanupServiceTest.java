package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentInfrastructureCleanupServiceTest {

    @Test
    void hardDeleteCleansRailwayServicesThenDeletesEmptyProject() {
        DeploymentManagedVectorResourceService managedVectorResourceService = mock(DeploymentManagedVectorResourceService.class);
        PineconeControlPlaneClient pineconeControlPlaneClient = mock(PineconeControlPlaneClient.class);
        QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient = mock(QdrantCloudControlPlaneClient.class);
        ZillizCloudControlPlaneClient zillizCloudControlPlaneClient = mock(ZillizCloudControlPlaneClient.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        DeploymentInfrastructureCleanupService service = new DeploymentInfrastructureCleanupService(
            managedVectorResourceService,
            pineconeControlPlaneClient,
            qdrantCloudControlPlaneClient,
            zillizCloudControlPlaneClient,
            railwayGraphqlClient,
            platformSecretService,
            platformAuditService,
            new ObjectMapper()
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-cleanup");
        deployment.setName("Cleanup");
        deployment.setEnvironmentName("dev");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setProvisioningDetailsJson("""
            {
              "railway": {
                "projectId": "proj-1",
                "services": {
                  "runtime": { "serviceId": "svc-runtime" },
                  "restConnector": { "serviceId": "svc-rest" }
                }
              }
            }
            """);

        when(managedVectorResourceService.listResources("dep-cleanup")).thenReturn(List.of());
        when(railwayGraphqlClient.getProject("proj-1"))
            .thenReturn(new RailwayGraphqlClient.RailwayProjectSnapshot(
                "proj-1",
                "cleanup-dev",
                List.of(new RailwayGraphqlClient.RailwayEnvironmentSummary("env-1", "dev")),
                List.of(
                    new RailwayGraphqlClient.RailwayServiceSummary("svc-runtime", "runtime-dep-cleanup"),
                    new RailwayGraphqlClient.RailwayServiceSummary("svc-rest", "rest-connector-dep-cleanup")
                )
            ))
            .thenReturn(new RailwayGraphqlClient.RailwayProjectSnapshot(
                "proj-1",
                "cleanup-dev",
                List.of(new RailwayGraphqlClient.RailwayEnvironmentSummary("env-1", "dev")),
                List.of()
            ));

        service.cleanupForHardDelete(deployment, release, "retire deployment");

        verify(railwayGraphqlClient).deleteService("svc-runtime");
        verify(railwayGraphqlClient).deleteService("svc-rest");
        verify(railwayGraphqlClient).deleteProject("proj-1");
        verify(platformAuditService, times(1)).record(
            org.mockito.ArgumentMatchers.eq("DEPLOYMENT_HARD_DELETE_INFRASTRUCTURE_CLEANED"),
            org.mockito.ArgumentMatchers.eq("DEPLOYMENT"),
            org.mockito.ArgumentMatchers.eq("dep-cleanup"),
            org.mockito.ArgumentMatchers.anyMap()
        );
    }

    @Test
    void hardDeleteSurfacesRailwayServiceContextWhenDeleteFails() {
        DeploymentManagedVectorResourceService managedVectorResourceService = mock(DeploymentManagedVectorResourceService.class);
        PineconeControlPlaneClient pineconeControlPlaneClient = mock(PineconeControlPlaneClient.class);
        QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient = mock(QdrantCloudControlPlaneClient.class);
        ZillizCloudControlPlaneClient zillizCloudControlPlaneClient = mock(ZillizCloudControlPlaneClient.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        DeploymentInfrastructureCleanupService service = new DeploymentInfrastructureCleanupService(
            managedVectorResourceService,
            pineconeControlPlaneClient,
            qdrantCloudControlPlaneClient,
            zillizCloudControlPlaneClient,
            railwayGraphqlClient,
            platformSecretService,
            platformAuditService,
            new ObjectMapper()
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-cleanup");
        deployment.setName("Cleanup");
        deployment.setEnvironmentName("dev");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setProvisioningDetailsJson("""
            {
              "railway": {
                "projectId": "proj-1",
                "services": {
                  "runtime": { "serviceId": "svc-runtime" }
                }
              }
            }
            """);

        when(managedVectorResourceService.listResources("dep-cleanup")).thenReturn(List.of());
        when(railwayGraphqlClient.getProject("proj-1"))
            .thenReturn(new RailwayGraphqlClient.RailwayProjectSnapshot(
                "proj-1",
                "cleanup-dev",
                List.of(new RailwayGraphqlClient.RailwayEnvironmentSummary("env-1", "dev")),
                List.of(new RailwayGraphqlClient.RailwayServiceSummary("svc-runtime", "runtime-dep-cleanup"))
            ));
        doThrow(new RailwayProvisioningException(
            "Railway API request failed after 3/3 attempt(s). Last error: ConnectException: Connection refused"
        )).when(railwayGraphqlClient).deleteService("svc-runtime");

        assertThatThrownBy(() -> service.cleanupForHardDelete(deployment, release, "retire deployment"))
            .isInstanceOf(RailwayProvisioningException.class)
            .hasMessageContaining("Failed to delete Railway runtime service 'svc-runtime' during hard delete for deployment 'dep-cleanup'.")
            .hasMessageContaining("ConnectException: Connection refused");
    }
}
