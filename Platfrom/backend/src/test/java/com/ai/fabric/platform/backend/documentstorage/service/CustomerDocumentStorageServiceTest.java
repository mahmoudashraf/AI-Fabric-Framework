package com.ai.fabric.platform.backend.documentstorage.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.documentstorage.entity.*;
import com.ai.fabric.platform.backend.documentstorage.model.DocumentStorageContracts.*;
import com.ai.fabric.platform.backend.documentstorage.provider.CustomerDocumentStorageProvider;
import com.ai.fabric.platform.backend.documentstorage.repository.*;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerDocumentStorageServiceTest {
    final DeploymentRepository deployments=mock(DeploymentRepository.class);
    final DeploymentAccessService access=mock(DeploymentAccessService.class);
    final DocumentStorageConnectionRepository connections=mock(DocumentStorageConnectionRepository.class);
    final DocumentStorageUploadRepository uploads=mock(DocumentStorageUploadRepository.class);
    final CustomerDocumentStorageProvider provider=mock(CustomerDocumentStorageProvider.class);
    final DeploymentDocumentStorageBindingService bindings=mock(DeploymentDocumentStorageBindingService.class);
    final DeploymentTargetProfileRepository targets=mock(DeploymentTargetProfileRepository.class);
    final CustomerSelfServiceOperationBoundary boundary=mock(CustomerSelfServiceOperationBoundary.class);
    final PlatformSecretService secrets=mock(PlatformSecretService.class);
    final PlatformAuditService audit=mock(PlatformAuditService.class);
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final CustomerDocumentStorageService service=new CustomerDocumentStorageService(deployments,access,connections,uploads,provider,bindings,targets,boundary,secrets,audit,mapper);
    DeploymentEntity deployment;
    DocumentStorageConnectionEntity connection;

    @BeforeEach void setup() {
        deployment=new DeploymentEntity();deployment.setId("dep-owned");deployment.setCustomerId("customer");deployment.setTenantId("tenant");
        when(deployments.findById("dep-owned")).thenReturn(Optional.of(deployment));
        when(access.requireDeploymentAdminAccess(deployment)).thenReturn(deployment);
        when(provider.enabled()).thenReturn(true);when(provider.region()).thenReturn("eu-north-1");
        when(provider.endpoint()).thenReturn("https://s3.eu-north-1.amazonaws.com");when(provider.bucketPrefix()).thenReturn("loomai-customer-docs");
        when(connections.saveAndFlush(any())).thenAnswer(call->call.getArgument(0));
        when(uploads.saveAndFlush(any())).thenAnswer(call->call.getArgument(0));
        connection=new DocumentStorageConnectionEntity();connection.setId("storage");connection.setCustomerId("customer");connection.setTenantId("tenant");
        connection.setName("Business documents");connection.setMode("LOOMAI_HOSTED_S3");connection.setBucket("loomai-customer-docs-owned");
        connection.setRootPrefix("knowledge/");connection.setRegion("eu-north-1");connection.setStatus("READY");connection.setCreatedAt(Instant.now());
        when(connections.findById("storage")).thenReturn(Optional.of(connection));
    }

    @Test void creatingConfigurationDoesNotProvisionCloudResources() {
        var result=service.create("dep-owned",new Create("Help guides","create-one"));
        assertThat(result.status()).isEqualTo("NOT_PREPARED");assertThat(result.bucket()).startsWith("loomai-customer-docs-");
        verify(provider,never()).prepareBucket(any(),any(),any());verify(provider,never()).createReader(any(),any(),any());
    }

    @Test void creationReplayKeepsTheSameConnectionAndRejectsChangedIntent() {
        connection.setOperationKey("create-one");
        when(connections.findByCustomerIdAndTenantIdAndOperationKey("customer","tenant","create-one")).thenReturn(Optional.of(connection));
        assertThat(service.create("dep-owned",new Create("Business documents","create-one")).id()).isEqualTo("storage");
        assertThatThrownBy(()->service.create("dep-owned",new Create("Different","create-one"))).hasMessageContaining("different storage request");
        verify(connections,never()).saveAndFlush(any());
    }

    @Test void disabledHostedProviderLeavesByoAndExistingConnectionListingAvailable() {
        when(provider.enabled()).thenReturn(false);
        when(connections.findByCustomerIdAndTenantIdOrderByCreatedAtDesc("customer","tenant")).thenReturn(List.of(connection));
        assertThat(service.list("dep-owned").hostedAvailable()).isFalse();
        assertThat(service.list("dep-owned").connections()).hasSize(1);
        assertThatThrownBy(()->service.prepare("dep-owned","storage",new Command("prepare"))).hasMessageContaining("Customer-provided storage remains available");
        verify(provider,never()).prepareBucket(any(),any(),any());
    }

    @Test void foreignCustomerOrTenantCannotReadOrUploadAnotherConnection() {
        connection.setCustomerId("foreign");
        assertThatThrownBy(()->service.upload("dep-owned","storage","guide.txt","public/","upload",bytes("Guide"))).hasMessageContaining("not found");
        connection.setCustomerId("customer");connection.setTenantId("another-tenant");
        assertThatThrownBy(()->service.uploads("dep-owned","storage")).hasMessageContaining("not found");
        verify(provider,never()).putObject(any(),any(),any(),any(),any());
    }

    @Test void prepareRequiresOwnedVersionedBucketReadback() {
        connection.setStatus("NOT_PREPARED");
        when(provider.prepareBucket("storage","customer",connection.getBucket())).thenReturn(new CustomerDocumentStorageProvider.Bucket(connection.getBucket(),true));
        assertThat(service.prepare("dep-owned","storage",new Command("prepare")).status()).isEqualTo("READY");
        service.prepare("dep-owned","storage",new Command("prepare-replay"));
        verify(provider,times(1)).prepareBucket(any(),any(),any());
    }

    @Test void failedProviderDoesNotProduceReadyStorageOrLeakItsError() {
        connection.setStatus("NOT_PREPARED");
        when(provider.prepareBucket(any(),any(),any())).thenThrow(new IllegalStateException("secret-value raw provider details"));
        assertThatThrownBy(()->service.prepare("dep-owned","storage",new Command("prepare")))
            .hasMessageContaining("Check provider permissions").hasMessageNotContaining("secret-value");
        assertThat(connection.getStatus()).isEqualTo("FAILED");
    }

    @Test void uploadCreatesSourceReceiptButNeverAnIndexingClaim() throws Exception {
        var bytes=bytes("Delivery is discussed with the support team.");
        when(provider.putObject(any(),any(),eq(bytes),any(),any())).thenReturn(new CustomerDocumentStorageProvider.StoredObject("etag","version",bytes.length));
        var result=service.upload("dep-owned","storage","delivery.txt","policies/","upload-one",bytes);
        assertThat(result.status()).isEqualTo("UPLOADED");assertThat(result.objectKey()).startsWith("knowledge/policies/du-").endsWith("/delivery.txt");
        assertThat(mapper.writeValueAsString(result)).doesNotContain("ACTIVE","secretKey","accessKey");
        verifyNoInteractions(bindings);
    }

    @Test void uploadReplayUsesSameObjectAndConflictingPayloadIsRejected() {
        var upload=new DocumentStorageUploadEntity();upload.setId("upload");upload.setConnectionId("storage");upload.setFilename("guide.txt");
        upload.setPrefix("public/");upload.setSha256(CustomerDocumentStorageService.sha256("Guide"));upload.setStatus("UPLOADED");
        when(uploads.findByConnectionIdAndOperationKey("storage","same")).thenReturn(Optional.of(upload));
        assertThat(service.upload("dep-owned","storage","guide.txt","public/","same",bytes("Guide")).id()).isEqualTo("upload");
        assertThatThrownBy(()->service.upload("dep-owned","storage","guide.txt","public/","same",bytes("Changed"))).hasMessageContaining("different document upload");
        verify(provider,never()).putObject(any(),any(),any(),any(),any());
    }

    @Test void filenamesPrefixesTypesSizeAndEncodingAreExecutionBoundaries() {
        for(String filename:List.of("../guide.txt","folder\\guide.txt","guide.pdf","bad\n.txt"))
            assertThatThrownBy(()->service.upload("dep-owned","storage",filename,"public/",UUID.randomUUID().toString(),bytes("guide"))).isInstanceOf(ResponseStatusException.class);
        for(String prefix:List.of("../staff/","/staff/","public//staff/","public/../../"))
            assertThatThrownBy(()->service.upload("dep-owned","storage","guide.txt",prefix,UUID.randomUUID().toString(),bytes("guide"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.upload("dep-owned","storage","guide.txt","public/","large",new byte[CustomerDocumentStorageService.MAX_UPLOAD_BYTES+1])).hasMessageContaining("1 MiB");
        assertThatThrownBy(()->service.upload("dep-owned","storage","guide.txt","public/","encoding",new byte[]{(byte)0xC3,0x28})).hasMessageContaining("UTF-8");
        assertThatThrownBy(()->service.upload("dep-owned","storage","guide.json","public/","json",bytes("{not-json"))).hasMessageContaining("valid JSON");
        assertThatThrownBy(()->service.upload("dep-owned","storage","guide.json","public/","trailing-json",bytes("{} {}"))).hasMessageContaining("valid JSON");
        verify(provider,never()).putObject(any(),any(),any(),any(),any());
    }

    @Test void stageAndProductionUseOneBucketWithSeparateTargetOwnedReaders() {
        for(String id:List.of("stage","prod")) {
            var target=new DeploymentTargetProfileEntity();target.setId(id);target.setActive(true);when(targets.findById(id)).thenReturn(Optional.of(target));
        }
        when(provider.createReader(any(),any(),any())).thenAnswer(call->new CustomerDocumentStorageProvider.Reader("arn:reader", "reader-access-"+call.getArgument(0),"reader-secret"));
        service.bind("dep-owned","storage",new Bind("stage","policies/","bind-stage"));
        service.bind("dep-owned","storage",new Bind("prod","policies/","bind-prod"));
        var requests=ArgumentCaptor.forClass(UpsertDocumentStorageBindingRequest.class);
        verify(bindings,times(2)).upsertHostedConnection(eq("dep-owned"),requests.capture(),eq("storage"),any());
        assertThat(requests.getAllValues()).extracting(UpsertDocumentStorageBindingRequest::bucket).containsOnly(connection.getBucket());
        assertThat(requests.getAllValues()).extracting(UpsertDocumentStorageBindingRequest::accessKey).doesNotHaveDuplicates();
        verify(secrets,times(2)).upsertTargetManagedSecret(any(),any(),eq("dep-owned"),eq("stage"),anyMap());
    }

    @Test void invalidTargetDoesNotCreateReaderCredentials() {
        assertThatThrownBy(()->service.bind("dep-owned","storage",new Bind("foreign-target","public/","bind"))).hasMessageContaining("not found");
        verify(provider,never()).createReader(any(),any(),any());
    }

    @Test void storageOperationLimitsStopNewPaidResourcesWithoutBreakingExistingReads() {
        org.springframework.test.util.ReflectionTestUtils.setField(service,"maxConnections",1);
        when(connections.findByCustomerIdAndTenantIdOrderByCreatedAtDesc("customer","tenant")).thenReturn(List.of(connection));
        assertThat(service.list("dep-owned").connections()).hasSize(1);
        assertThatThrownBy(()->service.create("dep-owned",new Create("Another bucket","create-two"))).hasMessageContaining("connection limit");
        org.springframework.test.util.ReflectionTestUtils.setField(service,"maxUploads",1);
        when(uploads.findByConnectionIdOrderByCreatedAtDesc("storage")).thenReturn(List.of(new DocumentStorageUploadEntity()));
        assertThatThrownBy(()->service.upload("dep-owned","storage","guide.txt","public/","upload-two",bytes("guide"))).hasMessageContaining("upload limit");
        verify(provider,never()).prepareBucket(any(),any(),any());verify(provider,never()).putObject(any(),any(),any(),any(),any());
    }

    @Test void missingReaderBoundaryIsActionableWithoutPublishingAnyCredential() {
        var target=new DeploymentTargetProfileEntity();target.setId("stage");target.setActive(true);when(targets.findById("stage")).thenReturn(Optional.of(target));
        when(provider.createReader(any(),any(),any())).thenThrow(new com.ai.fabric.platform.backend.documentstorage.provider.DocumentStorageProviderException("HOSTED_READER_PERMISSIONS_BOUNDARY_NOT_CONFIGURED"));
        assertThatThrownBy(()->service.bind("dep-owned","storage",new Bind("stage","public/","bind-stage")))
            .hasMessageContaining("HOSTED_READER_PERMISSIONS_BOUNDARY_NOT_CONFIGURED");
        verify(secrets,never()).upsertTargetManagedSecret(any(),any(),any(),any(),anyMap());
        verifyNoInteractions(bindings);
    }

    private byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
