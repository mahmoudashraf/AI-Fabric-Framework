package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentManagedVectorResourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentInfrastructureCleanupServiceTest {
    private final ManagedResourceOwnershipFixture fixture=new ManagedResourceOwnershipFixture();
    private final ObjectMapper mapper=new ObjectMapper();
    private final DeploymentManagedVectorResourceService resources=mock(DeploymentManagedVectorResourceService.class);
    private final PineconeControlPlaneClient pinecone=mock(PineconeControlPlaneClient.class);
    private final QdrantCloudControlPlaneClient qdrant=mock(QdrantCloudControlPlaneClient.class);
    private final ZillizCloudControlPlaneClient zilliz=mock(ZillizCloudControlPlaneClient.class);
    private final RailwayGraphqlClient railway=mock(RailwayGraphqlClient.class);
    private final PlatformSecretService secrets=mock(PlatformSecretService.class);
    private final HttpClient http=mock(HttpClient.class);
    private final DeploymentInfrastructureCleanupService service=new DeploymentInfrastructureCleanupService(
        resources,pinecone,qdrant,zilliz,railway,secrets,mock(PlatformAuditService.class),mapper,http);

    DeploymentInfrastructureCleanupServiceTest() { fixture.inject(service); }

    private DeploymentReleaseEntity release(String json) {
        var release=new DeploymentReleaseEntity();
        release.setId("rel-123"); release.setTargetProfileId(ManagedResourceOwnershipFixture.SCOPE.targetProfileId());
        release.setProvisioningDetailsJson(json); return release;
    }

    private void clean(String json) { service.cleanupForHardDelete(fixture.deployment(),release(json),"synthetic cleanup"); }

    private RailwayGraphqlClient.RailwayProjectSnapshot project(List<RailwayGraphqlClient.RailwayServiceSummary> services, boolean extraEnvironment) {
        return new RailwayGraphqlClient.RailwayProjectSnapshot("project","synthetic",
            extraEnvironment?List.of(new RailwayGraphqlClient.RailwayEnvironmentSummary("environment","stage"),
                new RailwayGraphqlClient.RailwayEnvironmentSummary("other-environment","production")):
                List.of(new RailwayGraphqlClient.RailwayEnvironmentSummary("environment","stage")),services);
    }

    private String ownedRailway() {
        fixture.own("railway","PROJECT","workspace","synthetic","project",null);
        fixture.own("railway","ENVIRONMENT","project","stage","environment",null);
        var serviceReceipt=fixture.own("railway","SERVICE","project","runtime","runtime",null);
        return "{\"railway\":{\"projectId\":\"project\",\"environmentId\":\"environment\","
            +"\"services\":{\"runtime\":{\"serviceId\":\"runtime\"}},\"ownership\":{\"runtime\":\""+serviceReceipt.id()+"\"}}}";
    }

    private DeploymentManagedVectorResourceSummary resource(String vendor,String kind,String name,String reference,String endpoint,ObjectNode details) {
        return new DeploymentManagedVectorResourceSummary("resource","dep-123","version","rel-123",vendor,vendor,
            "PLATFORM_MANAGED", "COLLECTION".equals(kind)?"MANAGED_COLLECTIONS":
                "pinecone".equals(vendor)?"MANAGED_SERVERLESS_INDEX":
                "zilliz".equals(vendor)?"MANAGED_ZILLIZ_CLOUD_CLUSTER":"MANAGED_CLOUD_CLUSTER",
            kind,name,reference,endpoint,"READY","ACTIVE",null,null,"ACTIVE",List.of(),details,"IN_SYNC","",Instant.now(),Instant.now());
    }

    private ObjectNode ownedDetails(String vendor,String kind,String parent,String name,String id,String endpoint) {
        return mapper.createObjectNode().put("ownershipReceiptId",fixture.own(vendor,kind,parent,name,id,endpoint).id());
    }

    @Test void historicalRailwayMetadataCannotAuthorizeAnyCleanup() {
        assertThatThrownBy(()->clean("{\"railway\":{\"projectId\":\"project\"}}")).hasMessageContaining("OWNERSHIP_REQUIRED");
        org.mockito.Mockito.verifyNoInteractions(railway,http,pinecone,qdrant,zilliz,secrets,resources);
    }

    @Test void railwayDeletesOnlyOwnedServiceAndNeverProjectOrUntrackedService() {
        String details=ownedRailway();
        var runtime=new RailwayGraphqlClient.RailwayServiceSummary("runtime","runtime");
        var unrelated=new RailwayGraphqlClient.RailwayServiceSummary("unrelated","business-service");
        when(railway.getProject("project")).thenReturn(project(List.of(runtime,unrelated),false),
            project(List.of(runtime,unrelated),false),project(List.of(unrelated),false));
        var result=service.cleanupForHardDelete(fixture.deployment(),release(details),"synthetic cleanup");
        assertThat(result.railway().projectDeleted()).isFalse();
        assertThat(result.railway().deletedServiceIds()).containsExactly("runtime");
        verify(railway).deleteService("runtime");
        verify(railway,never()).deleteService("unrelated");
        verify(railway,never()).deleteProject(any());
    }

    @Test void additionalRailwayEnvironmentBlocksBeforeAnyVectorOrServiceMutation() {
        String details=ownedRailway();
        when(railway.getProject("project")).thenReturn(project(List.of(),true));
        assertThatThrownBy(()->clean(details)).hasMessageContaining("SHARED_ENVIRONMENT");
        verify(railway,never()).deleteService(any());
        org.mockito.Mockito.verifyNoInteractions(resources,secrets);
    }

    @Test void railwayDeleteMustBeObservedAbsentBeforeCompletion() {
        String details=ownedRailway();
        when(railway.getProject("project")).thenReturn(project(List.of(new RailwayGraphqlClient.RailwayServiceSummary("runtime","runtime")),false));
        assertThatThrownBy(()->clean(details)).hasMessageContaining("DELETE_UNSETTLED");
        verify(railway,never()).deleteProject(any());
    }

    @Test void railwayUncertainDeleteIsNotRepeatedAndMissingServiceCanBeReconciled() {
        String details=ownedRailway();
        var runtime=new RailwayGraphqlClient.RailwayServiceSummary("runtime","runtime");
        when(railway.getProject("project")).thenReturn(project(List.of(runtime),false));
        assertThatThrownBy(()->clean(details)).hasMessageContaining("DELETE_UNSETTLED");
        assertThatThrownBy(()->clean(details)).hasMessageContaining("DELETE_RECONCILIATION_REQUIRED");
        verify(railway,times(1)).deleteService("runtime");
        when(railway.getProject("project")).thenReturn(project(List.of(),false));
        clean(details);
        verify(railway,times(1)).deleteService("runtime");
    }

    @Test void foreignRailwayServiceReferenceIsPreservedBeforeDelete() {
        String details=ownedRailway();
        fixture.release("foreign",new ManagedResourceOwnershipService.Scope("c","t","foreign","foreign-target"),
            "{\"railway\":{\"services\":{\"runtime\":{\"serviceId\":\"runtime\"}}}}");
        when(railway.getProject("project")).thenReturn(project(List.of(new RailwayGraphqlClient.RailwayServiceSummary("runtime","runtime")),false));
        assertThatThrownBy(()->clean(details)).hasMessageContaining("REFERENCED");
        verify(railway,never()).deleteService(any());
        verify(railway,never()).deleteProject(any());
    }

    @Test void unownedQdrantCollectionMetadataDoesNotAuthorizeSecretReadOrDelete() {
        when(resources.listResources("dep-123")).thenReturn(List.of(resource("qdrant","COLLECTION","catalog","catalog",
            "https://vector.example",mapper.createObjectNode())));
        assertThatThrownBy(()->clean("{}")).hasMessageContaining("SCOPE_REQUIRED");
        org.mockito.Mockito.verifyNoInteractions(http,secrets);
        verify(resources,never()).deleteResourceRecords(any());
    }

    @SuppressWarnings("unchecked")
    @Test void ownedQdrantCollectionCleanupEncodesNameAndVerifiesAbsence() throws Exception {
        String endpoint="https://vector.example";
        var details=ownedDetails("qdrant","COLLECTION",endpoint,"product catalog","product catalog",endpoint);
        when(resources.listResources("dep-123")).thenReturn(List.of(resource("qdrant","COLLECTION","product catalog","product catalog",endpoint,details)));
        HttpResponse<String> found=mock(HttpResponse.class),missing=mock(HttpResponse.class),deleted=mock(HttpResponse.class);
        when(found.statusCode()).thenReturn(200); when(missing.statusCode()).thenReturn(404); when(deleted.statusCode()).thenReturn(200);
        when(http.<String>send(argThat(request->request!=null&&"GET".equals(request.method())),any(HttpResponse.BodyHandler.class))).thenReturn(found,missing);
        when(http.<String>send(argThat(request->request!=null&&"DELETE".equals(request.method())),any(HttpResponse.BodyHandler.class))).thenReturn(deleted);
        clean("{}");
        verify(http).send(argThat(request->"DELETE".equals(request.method())&&request.uri().toString().equals(endpoint+"/collections/product%20catalog")),any(HttpResponse.BodyHandler.class));
        verify(resources).deleteResourceRecords(List.of("resource"));
        assertThat(fixture.ownership.receipt(details.path("ownershipReceiptId").asText()).state()).isEqualTo("DELETED");
    }

    @SuppressWarnings("unchecked")
    @Test void qdrantDeleteAcknowledgementWithoutAbsenceRetainsLedgerAndRegistry() throws Exception {
        String endpoint="https://vector.example";
        var details=ownedDetails("qdrant","COLLECTION",endpoint,"catalog","catalog",endpoint);
        when(resources.listResources("dep-123")).thenReturn(List.of(resource("qdrant","COLLECTION","catalog","catalog",endpoint,details)));
        HttpResponse<String> found=mock(HttpResponse.class);
        when(found.statusCode()).thenReturn(200);
        when(http.<String>send(any(),any(HttpResponse.BodyHandler.class))).thenReturn(found);
        assertThatThrownBy(()->clean("{}")).hasMessageContaining("DELETE_UNSETTLED");
        verify(resources,never()).deleteResourceRecords(any());
        assertThat(fixture.ownership.receipt(details.path("ownershipReceiptId").asText()).state()).isEqualTo("DELETE_PENDING");
    }

    @SuppressWarnings("unchecked")
    @Test void qdrantUncertainDeleteCannotBeRepeatedAgainstReappearingCollection() throws Exception {
        String endpoint="https://vector.example";
        var details=ownedDetails("qdrant","COLLECTION",endpoint,"catalog","catalog",endpoint);
        when(resources.listResources("dep-123")).thenReturn(List.of(resource("qdrant","COLLECTION","catalog","catalog",endpoint,details)));
        HttpResponse<String> found=mock(HttpResponse.class);
        when(found.statusCode()).thenReturn(200);
        when(http.<String>send(any(),any(HttpResponse.BodyHandler.class))).thenReturn(found);
        assertThatThrownBy(()->clean("{}")).hasMessageContaining("DELETE_UNSETTLED");
        assertThatThrownBy(()->clean("{}")).hasMessageContaining("DELETE_RECONCILIATION_REQUIRED");
        verify(http,times(1)).send(argThat(request->request!=null&&"DELETE".equals(request.method())),any(HttpResponse.BodyHandler.class));
        HttpResponse<String> absent=mock(HttpResponse.class); when(absent.statusCode()).thenReturn(404);
        when(http.<String>send(any(),any(HttpResponse.BodyHandler.class))).thenReturn(absent);
        clean("{}");
        assertThat(fixture.ownership.receipt(details.path("ownershipReceiptId").asText()).state()).isEqualTo("DELETED");
    }

    @SuppressWarnings("unchecked")
    @Test void foreignCollectionReferenceBlocksBeforeSecretReadsOrRemoteDelete() throws Exception {
        String endpoint="https://vector.example";
        var details=ownedDetails("qdrant","COLLECTION",endpoint,"catalog","catalog",endpoint);
        when(resources.listResources("dep-123")).thenReturn(List.of(resource("qdrant","COLLECTION","catalog","catalog",endpoint,details)));
        fixture.release("foreign",new ManagedResourceOwnershipService.Scope("c","t","foreign","foreign-target"),"{}");
        fixture.jdbc.update("INSERT INTO platform_deployment_managed_vector_resources VALUES(?,?,?,?,?)",
            "qdrant",endpoint+"/collections/catalog",endpoint,"foreign","foreign");
        assertThatThrownBy(()->clean("{}")).hasMessageContaining("REFERENCED");
        org.mockito.Mockito.verifyNoInteractions(http,secrets,qdrant);
        verify(resources,never()).deleteResourceRecords(any());
    }

    @Test void qdrantRootRetirementRequiresAuthoritativeCompleteInventoryBeforeSideEffects() {
        String endpoint="https://vector.example";
        var details=ownedDetails("qdrant","CLUSTER","account","cluster","cluster",endpoint);
        when(resources.listResources("dep-123")).thenReturn(List.of(resource("qdrant","CLUSTER","cluster","cluster",endpoint,details)));
        assertThatThrownBy(()->clean("{}")).hasMessageContaining("COMPLETE_CHILD_INVENTORY_REQUIRED");
        org.mockito.Mockito.verifyNoInteractions(qdrant,http,secrets);
    }

    @Test void historicalPineconeIndexDoesNotDisableProtectionOrReadSecrets() {
        when(resources.listResources("dep-123")).thenReturn(List.of(resource("pinecone","INDEX","index","index",
            "https://index.example",mapper.createObjectNode())));
        assertThatThrownBy(()->clean("{}")).hasMessageContaining("SCOPE_REQUIRED");
        org.mockito.Mockito.verifyNoInteractions(pinecone,secrets,http);
    }

    @SuppressWarnings("unchecked")
    @Test void ownedPineconeRootWithUntrackedNamespacesCannotDisableProtectionOrDelete() throws Exception {
        String endpoint="https://index.example";
        var details=ownedDetails("pinecone","INDEX","pinecone-control-plane","index","index",endpoint);
        when(resources.listResources("dep-123")).thenReturn(List.of(resource("pinecone","INDEX","index","index",endpoint,details)));
        when(secrets.resolveSecret("PINECONE_API_KEY")).thenReturn("synthetic-management");
        when(pinecone.findIndexByName("index","synthetic-management")).thenReturn(
            new PineconeControlPlaneClient.PineconeIndexSummary("index",endpoint,1536,"cosine",true,"enabled"));
        HttpResponse<String> stats=mock(HttpResponse.class);
        when(stats.statusCode()).thenReturn(200); when(stats.body()).thenReturn("{\"namespaces\":{\"customer-data\":{\"vectorCount\":1}},\"totalVectorCount\":1}");
        when(http.<String>send(any(),any(HttpResponse.BodyHandler.class))).thenReturn(stats);
        assertThatThrownBy(()->clean("{}")).hasMessageContaining("CHILDREN_RETAINED");
        verify(pinecone,never()).configureDeletionProtection(any(),org.mockito.ArgumentMatchers.anyBoolean(),any());
        verify(pinecone,never()).deleteIndex(any(),any());
    }

    @SuppressWarnings("unchecked")
    @Test void emptyOwnedPineconeRootWithExactEndpointCanBeDeleted() throws Exception {
        String endpoint="https://index.example";
        var details=ownedDetails("pinecone","INDEX","pinecone-control-plane","index","index",endpoint);
        when(resources.listResources("dep-123")).thenReturn(List.of(resource("pinecone","INDEX","index","index",endpoint,details)));
        when(secrets.resolveSecret("PINECONE_API_KEY")).thenReturn("synthetic-management");
        when(pinecone.findIndexByName("index","synthetic-management")).thenReturn(
            new PineconeControlPlaneClient.PineconeIndexSummary("index",endpoint,1536,"cosine",true,"enabled"));
        HttpResponse<String> stats=mock(HttpResponse.class);
        when(stats.statusCode()).thenReturn(200); when(stats.body()).thenReturn("{\"namespaces\":{},\"totalVectorCount\":0}");
        when(http.<String>send(any(),any(HttpResponse.BodyHandler.class))).thenReturn(stats);
        clean("{}");
        var order=org.mockito.Mockito.inOrder(pinecone);
        order.verify(pinecone).findIndexByName("index","synthetic-management");
        order.verify(pinecone).configureDeletionProtection("index",false,"synthetic-management");
        order.verify(pinecone).deleteIndex("index","synthetic-management");
        order.verify(pinecone).awaitIndexDeleted("index","synthetic-management");
        assertThat(fixture.ownership.receipt(details.path("ownershipReceiptId").asText()).state()).isEqualTo("DELETED");
    }

    @Test void pineconeNameReusedAtDifferentHostCannotDisableProtection() {
        var details=ownedDetails("pinecone","INDEX","pinecone-control-plane","index","index","https://original.example");
        when(resources.listResources("dep-123")).thenReturn(List.of(resource("pinecone","INDEX","index","index","https://original.example",details)));
        when(secrets.resolveSecret("PINECONE_API_KEY")).thenReturn("synthetic-management");
        when(pinecone.findIndexByName("index","synthetic-management")).thenReturn(
            new PineconeControlPlaneClient.PineconeIndexSummary("index","https://replacement.example",1536,"cosine",true,"enabled"));
        assertThatThrownBy(()->clean("{}")).hasMessageContaining("IDENTITY_CHANGED");
        verify(pinecone,never()).configureDeletionProtection(any(),org.mockito.ArgumentMatchers.anyBoolean(),any());
        verify(pinecone,never()).deleteIndex(any(),any());
        org.mockito.Mockito.verifyNoInteractions(http);
    }

    private void ownedZilliz(String status) {
        String endpoint="https://cluster.example";
        var details=ownedDetails("zilliz","CLUSTER","project","cluster","cluster",endpoint)
            .put("clusterId","cluster").put("projectId","project")
            .put("runtimeUsernameSecretName","MANAGED_SYNTHETIC_USER").put("runtimePasswordSecretName","MANAGED_SYNTHETIC_PASSWORD");
        when(resources.listResources("dep-123")).thenReturn(List.of(resource("zilliz","CLUSTER","cluster","cluster",endpoint,details)));
        when(secrets.resolveSecret("ZILLIZ_CLOUD_API_KEY")).thenReturn("synthetic-management");
        when(secrets.resolveSecret("MANAGED_SYNTHETIC_USER")).thenReturn("synthetic-user");
        when(secrets.resolveSecret("MANAGED_SYNTHETIC_PASSWORD")).thenReturn("synthetic-password");
        when(zilliz.getCluster("cluster","synthetic-management")).thenReturn(new ZillizCloudControlPlaneClient.ZillizClusterSummary(
            "cluster","cluster","project","region","Serverless","",0,status,endpoint,"Serverless"));
        when(zilliz.listServingCollections(endpoint,"synthetic-user","synthetic-password")).thenReturn(List.of());
    }

    @Test void zillizDeletingTimeoutIsNotAbsence() {
        ownedZilliz("DELETING");
        doThrow(new RailwayProvisioningException("synthetic timeout")).when(zilliz).awaitClusterDeleted("cluster","synthetic-management");
        assertThatThrownBy(()->clean("{}")).hasMessageContaining("synthetic timeout");
        verify(resources,never()).deleteResourceRecords(any());
    }

    @Test void zillizAlreadyDeletedErrorRequiresIndependentAbsenceObservation() {
        ownedZilliz("RUNNING");
        doThrow(new RailwayProvisioningException("instance not support DELETE when instance status is DELETED (code 40064)"))
            .when(zilliz).deleteCluster("cluster","synthetic-management");
        assertThatThrownBy(()->clean("{}")).hasMessageContaining("Managed vector cleanup failed");
        verify(resources,never()).deleteResourceRecords(any());
    }

    @Test void zillizRootWithExistingCollectionsIsRetained() {
        ownedZilliz("RUNNING");
        when(zilliz.listServingCollections("https://cluster.example","synthetic-user","synthetic-password")).thenReturn(List.of("customer-data"));
        assertThatThrownBy(()->clean("{}")).hasMessageContaining("CHILDREN_RETAINED");
        verify(zilliz,never()).deleteCluster(any(),any());
    }

    @Test
    void hardDeleteDeletesTrackedProviderResourcesBeforeRecordRemoval() {
        DeploymentManagedVectorResourceService managedVectorResourceService = mock(DeploymentManagedVectorResourceService.class);
        PineconeControlPlaneClient pineconeControlPlaneClient = mock(PineconeControlPlaneClient.class);
        QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient = mock(QdrantCloudControlPlaneClient.class);
        ZillizCloudControlPlaneClient zillizCloudControlPlaneClient = mock(ZillizCloudControlPlaneClient.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        DeploymentProviderResourceHandleRepository providerResourceHandleRepository =
            mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentProviderRegistry providerRegistry = mock(DeploymentProviderRegistry.class);
        DeploymentProvisioningProvider coolifyProvider = mock(DeploymentProvisioningProvider.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        DeploymentInfrastructureCleanupService service = new DeploymentInfrastructureCleanupService(
            managedVectorResourceService,
            pineconeControlPlaneClient,
            qdrantCloudControlPlaneClient,
            zillizCloudControlPlaneClient,
            railwayGraphqlClient,
            providerResourceHandleRepository,
            providerRegistry,
            platformSecretService,
            platformAuditService,
            new ObjectMapper()
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-cleanup");
        deployment.setName("Cleanup");
        deployment.setEnvironmentName("dev");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setProvisioningDetailsJson("{}");
        DeploymentProviderResourceHandleEntity runtimeHandle = providerHandle("dprh-runtime", "APPLICATION");
        DeploymentProviderResourceHandleEntity connectorHandle = providerHandle("dprh-connector", "CONNECTOR_APPLICATION");
        DeploymentProviderResourceHandleEntity documentBinding = providerHandle(
            "dsh-documents",
            DeploymentDocumentStorageBindingService.RESOURCE_KIND
        );
        DeploymentProviderResourceHandleEntity vectorBinding = providerHandle(
            "dsh-vectors", "EXTERNAL_VECTOR_STORAGE_BINDING"
        );

        when(managedVectorResourceService.listResources("dep-cleanup")).thenReturn(List.of());
        when(providerResourceHandleRepository.findByDeploymentIdOrderByUpdatedAtDesc("dep-cleanup"))
            .thenReturn(List.of(runtimeHandle, connectorHandle, documentBinding, vectorBinding));
        when(providerRegistry.require(DeploymentProviderType.COOLIFY)).thenReturn(coolifyProvider);

        DeploymentInfrastructureCleanupService.DeploymentInfrastructureCleanupResult result =
            service.cleanupForHardDelete(deployment, release, "retire deployment");

        assertThat(result.providerResources().deletedHandleIds())
            .containsExactly("dprh-runtime", "dprh-connector");
        verify(coolifyProvider).delete(runtimeHandle, "retire deployment");
        verify(coolifyProvider).delete(connectorHandle, "retire deployment");
        verify(coolifyProvider, never()).delete(documentBinding, "retire deployment");
        verify(coolifyProvider, never()).delete(vectorBinding, "retire deployment");
        assertThat(documentBinding.getStatus()).isEqualTo("ACTIVE");
        assertThat(vectorBinding.getStatus()).isEqualTo("ACTIVE");
        verify(railwayGraphqlClient, never()).deleteProject(any());

        DeploymentProviderResourceHandleEntity unknownBinding = providerHandle(
            "unknown", "EXTERNAL_VECTOR_STORAGE_BINDING_V2"
        );
        when(providerResourceHandleRepository.findByDeploymentIdOrderByUpdatedAtDesc("dep-cleanup"))
            .thenReturn(List.of(runtimeHandle, unknownBinding));
        assertThatThrownBy(() -> service.cleanupForHardDelete(deployment, release, "retire deployment"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("supported owned resource kind");
        verify(coolifyProvider, times(1)).delete(runtimeHandle, "retire deployment");
        verify(coolifyProvider, never()).delete(unknownBinding, "retire deployment");
    }

    private DeploymentProviderResourceHandleEntity providerHandle(String id, String resourceKind) {
        DeploymentProviderResourceHandleEntity handle = new DeploymentProviderResourceHandleEntity();
        handle.setId(id);
        handle.setDeploymentId("dep-cleanup");
        handle.setTargetProfileId("dtp-coolify-staging");
        handle.setProviderType(DeploymentProviderType.COOLIFY);
        handle.setResourceKind(resourceKind);
        handle.setProviderResourceUuid(id + "-uuid");
        handle.setStatus("ACTIVE");
        handle.setMetadataJson("{}");
        handle.setCreatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        handle.setUpdatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        return handle;
    }
}
