package com.ai.fabric.platform.backend.documentstorage.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentKnowledgeOperationsService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentStorageBindingService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.documentstorage.repository.DocumentStorageSelectionRepository;
import com.ai.fabric.platform.backend.documentstorage.repository.DocumentStorageUploadRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Service;

import static com.ai.fabric.platform.backend.documentstorage.service.CustomerDocumentStorageService.conflict;

/** Freezes selected source references, never vectors, as part of the existing promotion candidate. */
@Service
public class DocumentStoragePromotionSelectionService {
    private final DocumentStorageSelectionRepository selections;
    private final DocumentStorageUploadRepository uploads;
    private final DeploymentDocumentBindingResolver bindings;
    private final DeploymentDocumentKnowledgeOperationsService operations;
    private final ObjectMapper mapper;
    private final PlatformSecretService secrets;
    public DocumentStoragePromotionSelectionService(DocumentStorageSelectionRepository selections,
        DocumentStorageUploadRepository uploads,DeploymentDocumentBindingResolver bindings,
        DeploymentDocumentKnowledgeOperationsService operations,ObjectMapper mapper,PlatformSecretService secrets) {
        this.selections=selections;this.uploads=uploads;this.bindings=bindings;this.operations=operations;this.mapper=mapper;this.secrets=secrets;
    }

    public ArrayNode snapshot(DeploymentEntity deployment,DeploymentReleaseEntity staging,String productionTarget) {
        var result=mapper.createArrayNode();
        JsonNode frozen=read(staging.getDocumentBindingSnapshotJson());
        if(!"LOOMAI_MANAGED".equals(frozen.path("safeMetadata").path("storageOwnership").asText()))return result;
        String connectionId=frozen.path("safeMetadata").path("storageConnectionId").asText("");
        var production=bindings.resolve(deployment.getId(),productionTarget,DeploymentDocumentBindingResolver.DOCUMENT_STORAGE_SLOT);
        JsonNode productionMetadata=read(production.getMetadataJson());
        if(!"LOOMAI_MANAGED".equals(productionMetadata.path("storageOwnership").asText())
            ||!connectionId.equals(productionMetadata.path("storageConnectionId").asText()))
            throw conflict("Promotion must bind the same hosted document connection to production. Select its intended purpose folder first.");
        String productionPrefix=secrets.resolveSecretForDeployment(DeploymentDocumentStorageBindingService.secretKey(deployment.getId(),productionTarget)+"_PREFIX",deployment.getId(),productionTarget);
        if(productionPrefix==null||productionPrefix.isBlank())throw conflict("The production document read scope is unavailable.");
        for(var selected:selections.findByDeploymentIdAndTargetProfileIdAndReleaseIdOrderByUploadIdAsc(deployment.getId(),staging.getTargetProfileId(),staging.getId())) {
            if(!"ACTIVE".equals(selected.getStatus())||selected.getLastError()!=null)
                throw conflict("Finish or resolve the selected staging document work before promotion.");
            var upload=uploads.findById(selected.getUploadId()).orElseThrow(()->conflict("Selected source upload receipt is unavailable."));
            if(!connectionId.equals(upload.getConnectionId())||!"UPLOADED".equals(upload.getStatus()))
                throw conflict("Selected source storage differs from the verified staging binding.");
            if(!upload.getObjectKey().startsWith(productionPrefix))
                throw conflict("The production purpose folder cannot read a reviewed source file. Choose a scope containing the selected files.");
            var item=result.addObject();item.put("uploadId",upload.getId());item.put("connectionId",connectionId);
            item.put("filename",upload.getFilename());item.put("sha256",upload.getSha256());
            item.put("datasetId",selected.getDatasetId());item.put("publicApproved",selected.isPublicApproved());
        }
        if(result.isEmpty())throw conflict("Confirm the uploaded document selection on staging before promoting hosted knowledge.");
        return result;
    }

    public void requireProductionEvidence(DeploymentEntity deployment,DeploymentReleaseEntity release) {
        JsonNode expected=read(release.getPromotionCandidateJson()).path("hostedDocumentSelection");
        if(expected.isMissingNode())return; // Non-hosted promotion has no source-file selection.
        if(!expected.isArray())throw conflict("Promotion document selection is malformed.");
        for(var item:expected) {
            var selected=selections.findByUploadIdAndDeploymentIdAndTargetProfileIdAndReleaseId(
                item.path("uploadId").asText(),deployment.getId(),release.getTargetProfileId(),release.getId())
                .orElseThrow(()->conflict("Index the reviewed source files in production before activation."));
            if(!"ACTIVE".equals(selected.getStatus())||selected.getLastError()!=null||selected.getSourceId()==null
                ||!item.path("datasetId").asText().equals(selected.getDatasetId())
                ||item.path("publicApproved").asBoolean()!=selected.isPublicApproved())
                throw conflict("Production document selection is incomplete or differs from the reviewed candidate.");
            var upload=uploads.findById(selected.getUploadId()).orElseThrow(()->conflict("Reviewed source receipt is unavailable."));
            if(!item.path("connectionId").asText().equals(upload.getConnectionId())||!item.path("sha256").asText().equals(upload.getSha256()))
                throw conflict("Reviewed source identity changed.");
            var detail=operations.detail(new DeploymentTargetReference(deployment.getId(),release.getTargetProfileId(),release.getId()),selected.getSourceId());
            if(detail.statusCode()!=200||!"ACTIVE".equals(detail.body().path("source").path("status").asText())
                ||detail.body().path("source").path("activeVersion").asLong(0)<1)
                throw conflict("Production runtime has not confirmed the reviewed document source is active.");
        }
    }

    private JsonNode read(String value) {
        if(value==null||value.isBlank())return mapper.createObjectNode();
        try{return mapper.readTree(value);}catch(Exception error){throw conflict("Stored document selection evidence is unavailable.");}
    }
}
