package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentManagedVectorResourceSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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

    @SuppressWarnings("unchecked")
    @Test
    void hardDeleteEncodesQdrantCollectionNamesDuringCleanup() throws Exception {
        DeploymentManagedVectorResourceService managedVectorResourceService = mock(DeploymentManagedVectorResourceService.class);
        PineconeControlPlaneClient pineconeControlPlaneClient = mock(PineconeControlPlaneClient.class);
        QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient = mock(QdrantCloudControlPlaneClient.class);
        ZillizCloudControlPlaneClient zillizCloudControlPlaneClient = mock(ZillizCloudControlPlaneClient.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        HttpClient httpClient = mock(HttpClient.class);

        DeploymentInfrastructureCleanupService service = new DeploymentInfrastructureCleanupService(
            managedVectorResourceService,
            pineconeControlPlaneClient,
            qdrantCloudControlPlaneClient,
            zillizCloudControlPlaneClient,
            railwayGraphqlClient,
            platformSecretService,
            platformAuditService,
            new ObjectMapper(),
            httpClient
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-cleanup");
        deployment.setName("Cleanup");
        deployment.setEnvironmentName("dev");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setProvisioningDetailsJson("{}");

        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode details = objectMapper.createObjectNode();
        details.put("baseUrl", "https://cluster.example.com");

        when(platformSecretService.resolveSecret("QDRANT_API_KEY")).thenReturn("qdrant-runtime-key");
        when(managedVectorResourceService.listResources("dep-cleanup")).thenReturn(List.of(
            new DeploymentManagedVectorResourceSummary(
                "res-1",
                "dep-cleanup",
                "ver-1",
                "rel-1",
                "qdrant",
                "qdrant",
                "EXTERNAL_EXISTING",
                "MANAGED_COLLECTIONS",
                "COLLECTION",
                "product catalog",
                "product catalog",
                "https://cluster.example.com",
                "READY",
                "ACTIVE",
                null,
                null,
                "ACTIVE",
                List.<String>of(),
                details,
                "IN_SYNC",
                "",
                Instant.now(),
                Instant.now()
            )
        ));

        HttpResponse<String> existingCollection = mock(HttpResponse.class);
        when(existingCollection.statusCode()).thenReturn(200);
        HttpResponse<String> deletedCollection = mock(HttpResponse.class);
        when(deletedCollection.statusCode()).thenReturn(200);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().equals("https://cluster.example.com/collections/product%20catalog")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(existingCollection);
        when(httpClient.<String>send(
            argThat(request -> request != null
                && "DELETE".equals(request.method())
                && request.uri().toString().equals("https://cluster.example.com/collections/product%20catalog")),
            any(HttpResponse.BodyHandler.class)
        )).thenReturn(deletedCollection);

        service.cleanupForHardDelete(deployment, release, "cleanup");

        verify(httpClient).send(
            argThat(request -> request != null
                && "GET".equals(request.method())
                && request.uri().toString().equals("https://cluster.example.com/collections/product%20catalog")),
            any(HttpResponse.BodyHandler.class)
        );
        verify(httpClient).send(
            argThat(request -> request != null
                && "DELETE".equals(request.method())
                && request.uri().toString().equals("https://cluster.example.com/collections/product%20catalog")),
            any(HttpResponse.BodyHandler.class)
        );
        verify(managedVectorResourceService).deleteResourceRecords(List.of("res-1"));
    }
}
