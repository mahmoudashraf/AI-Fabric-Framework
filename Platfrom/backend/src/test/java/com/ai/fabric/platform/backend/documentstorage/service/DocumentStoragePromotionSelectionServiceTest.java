package com.ai.fabric.platform.backend.documentstorage.service;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.documentstorage.entity.*;
import com.ai.fabric.platform.backend.documentstorage.repository.*;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentStoragePromotionSelectionServiceTest {
    final DocumentStorageSelectionRepository selections=mock(DocumentStorageSelectionRepository.class);
    final DocumentStorageUploadRepository uploads=mock(DocumentStorageUploadRepository.class);
    final DeploymentDocumentBindingResolver bindings=mock(DeploymentDocumentBindingResolver.class);
    final DeploymentDocumentKnowledgeOperationsService operations=mock(DeploymentDocumentKnowledgeOperationsService.class);
    final PlatformSecretService secrets=mock(PlatformSecretService.class);
    final ObjectMapper mapper=new ObjectMapper();
    final DocumentStoragePromotionSelectionService service=new DocumentStoragePromotionSelectionService(selections,uploads,bindings,operations,mapper,secrets);
    DeploymentEntity deployment;
    DeploymentReleaseEntity staging;
    DeploymentProviderResourceHandleEntity productionBinding;
    DocumentStorageUploadEntity upload;
    DocumentStorageSelectionEntity selected;

    @BeforeEach void setup() {
        deployment=new DeploymentEntity();deployment.setId("dep");
        staging=new DeploymentReleaseEntity();staging.setId("stage-release");staging.setTargetProfileId("stage");
        staging.setDocumentBindingSnapshotJson("{\"safeMetadata\":{\"storageOwnership\":\"LOOMAI_MANAGED\",\"storageConnectionId\":\"connection\"}}");
        productionBinding=new DeploymentProviderResourceHandleEntity();productionBinding.setMetadataJson("{\"storageOwnership\":\"LOOMAI_MANAGED\",\"storageConnectionId\":\"connection\"}");
        when(bindings.resolve("dep","prod",DeploymentDocumentBindingResolver.DOCUMENT_STORAGE_SLOT)).thenReturn(productionBinding);
        when(secrets.resolveSecretForDeployment(any(),eq("dep"),eq("prod"))).thenReturn("knowledge/public/");
        upload=new DocumentStorageUploadEntity();upload.setId("upload");upload.setConnectionId("connection");upload.setFilename("guide.txt");
        upload.setObjectKey("knowledge/public/upload/guide.txt");upload.setStatus("UPLOADED");upload.setSha256("a".repeat(64));
        when(uploads.findById("upload")).thenReturn(Optional.of(upload));
        selected=new DocumentStorageSelectionEntity();selected.setUploadId("upload");selected.setSourceId("stage-source");selected.setDatasetId("documents");selected.setPublicApproved(true);selected.setStatus("ACTIVE");
        when(selections.findByDeploymentIdAndTargetProfileIdAndReleaseIdOrderByUploadIdAsc("dep","stage","stage-release")).thenReturn(List.of(selected));
    }

    @Test void freezesExactSourceIdentityWithoutVectorsOrStagingSourceIds() {
        var snapshot=service.snapshot(deployment,staging,"prod");
        assertThat(snapshot).hasSize(1);assertThat(snapshot.get(0).path("uploadId").asText()).isEqualTo("upload");
        assertThat(snapshot.get(0).path("sha256").asText()).isEqualTo("a".repeat(64));
        assertThat(snapshot.toString()).doesNotContain("stage-source","vector","secret","accessKey");
        verifyNoInteractions(operations);
    }

    @Test void differentConnectionOrUnreadablePurposeFailsBeforePromotion() {
        productionBinding.setMetadataJson("{\"storageOwnership\":\"LOOMAI_MANAGED\",\"storageConnectionId\":\"other\"}");
        assertThatThrownBy(()->service.snapshot(deployment,staging,"prod")).hasMessageContaining("same hosted document connection");
        productionBinding.setMetadataJson("{\"storageOwnership\":\"LOOMAI_MANAGED\",\"storageConnectionId\":\"connection\"}");
        when(secrets.resolveSecretForDeployment(any(),eq("dep"),eq("prod"))).thenReturn("knowledge/staff/");
        assertThatThrownBy(()->service.snapshot(deployment,staging,"prod")).hasMessageContaining("cannot read");
    }

    @Test void incompleteStagingSourceNeverBecomesReviewedProductionKnowledge() {
        selected.setStatus("INDEXING");
        assertThatThrownBy(()->service.snapshot(deployment,staging,"prod")).hasMessageContaining("Finish or resolve");
    }

    @Test void nativeProductionActiveReadbackIsRequiredRatherThanStagingOrDbClaim() throws Exception {
        var production=new DeploymentReleaseEntity();production.setId("prod-release");production.setTargetProfileId("prod");
        production.setPromotionCandidateJson(mapper.createObjectNode().set("hostedDocumentSelection",service.snapshot(deployment,staging,"prod")).toString());
        var target=new DeploymentTargetReference("dep","prod","prod-release");
        var own=new DocumentStorageSelectionEntity();own.setUploadId("upload");own.setDatasetId("documents");own.setPublicApproved(true);own.setStatus("ACTIVE");own.setSourceId("prod-source");
        when(selections.findByUploadIdAndDeploymentIdAndTargetProfileIdAndReleaseId("upload","dep","prod","prod-release")).thenReturn(Optional.of(own));
        when(operations.detail(target,"prod-source")).thenReturn(new DeploymentDocumentKnowledgeOperationsService.RuntimeResponse(200,mapper.readTree("{\"source\":{\"status\":\"INDEXING\"}}")));
        assertThatThrownBy(()->service.requireProductionEvidence(deployment,production)).hasMessageContaining("not confirmed");
        when(operations.detail(target,"prod-source")).thenReturn(new DeploymentDocumentKnowledgeOperationsService.RuntimeResponse(200,mapper.readTree("{\"source\":{\"status\":\"ACTIVE\",\"activeVersion\":1}}")));
        service.requireProductionEvidence(deployment,production);
        verify(operations,times(2)).detail(target,"prod-source");verify(operations,never()).index(any(),any(),any());
    }

    @Test void existingByoPromotionsDoNotGainHostedSelectionRequirements() {
        staging.setDocumentBindingSnapshotJson("{\"safeMetadata\":{\"storageOwnership\":\"CUSTOMER_MANAGED\"}}");
        assertThat(service.snapshot(deployment,staging,"prod")).isEmpty();
        verifyNoInteractions(bindings,selections,uploads,operations);
    }
}
