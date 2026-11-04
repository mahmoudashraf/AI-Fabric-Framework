package com.ai.fabric.platform.backend.documentstorage.service;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.documentstorage.entity.*;
import com.ai.fabric.platform.backend.documentstorage.model.DocumentStorageContracts.*;
import com.ai.fabric.platform.backend.documentstorage.repository.DocumentStorageSelectionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentStorageSelectionServiceTest {
    final CustomerDocumentStorageService storage=mock(CustomerDocumentStorageService.class);
    final DocumentStorageSelectionRepository selections=mock(DocumentStorageSelectionRepository.class);
    final DeploymentReleaseRepository releases=mock(DeploymentReleaseRepository.class);
    final DeploymentVersionRepository versions=mock(DeploymentVersionRepository.class);
    final DeploymentDocumentStorageBindingService bindings=mock(DeploymentDocumentStorageBindingService.class);
    final DeploymentDocumentBindingResolver resolver=mock(DeploymentDocumentBindingResolver.class);
    final PlatformSecretService secrets=mock(PlatformSecretService.class);
    final DeploymentDocumentKnowledgeOperationsService operations=mock(DeploymentDocumentKnowledgeOperationsService.class);
    final ObjectMapper mapper=new ObjectMapper();
    final DocumentStorageSelectionService service=new DocumentStorageSelectionService(storage,selections,releases,versions,bindings,resolver,secrets,operations,mapper);
    final PrepareSelection request=new PrepareSelection("stage","release",List.of("upload"),"documents",true,"selection-one");
    final DeploymentTargetReference target=new DeploymentTargetReference("dep","stage","release");
    DeploymentEntity deployment;
    DocumentStorageUploadEntity upload;
    DeploymentReleaseEntity release;

    @BeforeEach void setup() throws Exception {
        deployment=new DeploymentEntity();deployment.setId("dep");when(storage.requireDeployment("dep")).thenReturn(deployment);
        release=new DeploymentReleaseEntity();release.setId("release");release.setDeploymentId("dep");release.setTargetProfileId("stage");release.setDeploymentVersionId("version");
        when(releases.findById("release")).thenReturn(Optional.of(release));
        var version=new DeploymentVersionEntity();version.setId("version");version.setMarketplaceDatasetConfigJson("{\"datasets\":[{\"datasetId\":\"documents\",\"ingestionMode\":\"EXTERNAL_DOCUMENT_STORAGE\"}]}");
        when(versions.findById("version")).thenReturn(Optional.of(version));
        when(bindings.list("dep")).thenReturn(List.of(new DocumentStorageBindingSummary("binding","dep","stage","S3_COMPATIBLE_OBJECT_STORAGE","CONFIGURED",true,
            mapper.readTree("{\"storageConnectionId\":\"storage\"}"),Instant.now())));
        when(secrets.resolveSecretForDeployment(any(),eq("dep"),eq("stage"))).thenReturn("knowledge/public/");
        upload=new DocumentStorageUploadEntity();upload.setId("upload");upload.setConnectionId("storage");upload.setObjectKey("knowledge/public/upload/guide.txt");
        when(storage.requireUpload("dep","storage","upload")).thenReturn(upload);
        when(selections.saveAndFlush(any())).thenAnswer(call->call.getArgument(0));
        when(operations.register(eq(target),any(),any())).thenReturn(response("{\"sourceId\":\"source\"}"));
        when(operations.detail(target,"source")).thenReturn(response("{\"source\":{\"status\":\"REGISTERED\"}}"));
        when(operations.preview(target,"source")).thenReturn(response("{}"));
        when(operations.index(eq(target),eq("source"),any())).thenReturn(response("{\"source\":{\"status\":\"INDEXING\"},\"manifest\":{\"work\":[{\"workId\":\"real-work\"}]}}"));
    }

    @Test void registersOnlyExactSelectedRelativeObjectAndUsesExistingPreviewIndex() {
        var result=service.prepare("dep","storage",request);
        assertThat(result.complete()).isFalse();assertThat(result.items().getFirst().workId()).isEqualTo("real-work");
        verify(operations).register(eq(target),argThat(body->body.path("objectReference").asText().equals("upload/guide.txt")
            &&body.path("metadata").path("publicationStatus").asText().equals("PUBLIC_APPROVED")),any());
        verify(operations).preview(target,"source");verify(operations).index(eq(target),eq("source"),any());
        verify(operations,never()).discover(any(),any());
    }

    @Test void privateSelectionNeverInventsPublicApproval() {
        service.prepare("dep","storage",new PrepareSelection("stage","release",List.of("upload"),"documents",false,"selection-private"));
        verify(operations).register(eq(target),argThat(body->!body.path("metadata").has("publicationStatus")),any());
    }

    @Test void invalidReleaseAndForeignTargetAreRejectedBeforeRuntimeCommands() {
        release.setTargetProfileId("production");
        assertThatThrownBy(()->service.prepare("dep","storage",request)).hasMessageContaining("not found");
        verifyNoInteractions(operations);
    }

    @Test void entireUploadSelectionIsValidatedBeforeAnyWrite() {
        var foreign=new DocumentStorageUploadEntity();foreign.setId("foreign");foreign.setObjectKey("knowledge/staff/guide.txt");
        when(storage.requireUpload("dep","storage","foreign")).thenReturn(foreign);
        assertThatThrownBy(()->service.prepare("dep","storage",new PrepareSelection("stage","release",List.of("upload","foreign"),"documents",true,"mixed")))
            .hasMessageContaining("outside the target");
        verifyNoInteractions(operations);verify(selections,never()).saveAndFlush(any());
    }

    @Test void activeSourceReadbackAvoidsReplayingRegistrationOrIndexing() throws Exception {
        var selection=existing("INDEXING");
        when(selections.findByUploadIdAndDeploymentIdAndTargetProfileIdAndReleaseId("upload","dep","stage","release")).thenReturn(Optional.of(selection));
        when(operations.detail(target,"source")).thenReturn(response("{\"source\":{\"status\":\"ACTIVE\",\"activeVersion\":1}}"));
        assertThat(service.prepare("dep","storage",request).complete()).isTrue();
        verify(operations,never()).register(any(),any(),any());verify(operations,never()).index(any(),any(),any());
        assertThat(selection.getStatus()).isEqualTo("ACTIVE");
    }

    @Test void pendingWorkUsesReconciliationNotAnotherIndexSubmission() throws Exception {
        var selection=existing("INDEXING");
        when(selections.findByUploadIdAndDeploymentIdAndTargetProfileIdAndReleaseId("upload","dep","stage","release")).thenReturn(Optional.of(selection));
        when(operations.detail(target,"source")).thenReturn(response("{\"source\":{\"status\":\"INDEXING\"}}"));
        when(operations.reconcile(eq(target),eq("source"),any())).thenReturn(response("{\"source\":{\"status\":\"ACTIVE\"}}"));
        assertThat(service.prepare("dep","storage",request).complete()).isTrue();
        verify(operations,never()).index(any(),any(),any());verify(operations).reconcile(eq(target),eq("source"),any());
    }

    @Test void authorizationFailureIsNeverHiddenInASuccessResponse() {
        when(operations.register(eq(target),any(),any())).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"Not authorized"));
        assertThatThrownBy(()->service.prepare("dep","storage",request)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        verify(operations,never()).index(any(),any(),any());
    }

    @Test void uncertainRuntimeReadCannotReuseAnOldActiveClaim() {
        var selection=existing("ACTIVE");
        when(selections.findByUploadIdAndDeploymentIdAndTargetProfileIdAndReleaseId("upload","dep","stage","release")).thenReturn(Optional.of(selection));
        when(operations.detail(target,"source")).thenThrow(new IllegalStateException("connection interrupted"));
        var result=service.prepare("dep","storage",request);
        assertThat(result.complete()).isFalse();assertThat(result.items().getFirst().message()).contains("incomplete");
        verify(operations,never()).index(any(),any(),any());
    }

    private DocumentStorageSelectionEntity existing(String status) {
        var value=new DocumentStorageSelectionEntity();value.setId("selection");value.setSourceId("source");value.setDatasetId("documents");value.setPublicApproved(true);value.setStatus(status);return value;
    }
    private DeploymentDocumentKnowledgeOperationsService.RuntimeResponse response(String json) throws Exception {
        return new DeploymentDocumentKnowledgeOperationsService.RuntimeResponse(200,mapper.readTree(json));
    }
}
