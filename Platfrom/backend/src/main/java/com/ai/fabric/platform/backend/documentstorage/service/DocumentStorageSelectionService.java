package com.ai.fabric.platform.backend.documentstorage.service;

import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.documentstorage.entity.DocumentStorageSelectionEntity;
import com.ai.fabric.platform.backend.documentstorage.entity.DocumentStorageUploadEntity;
import com.ai.fabric.platform.backend.documentstorage.model.DocumentStorageContracts.*;
import com.ai.fabric.platform.backend.documentstorage.repository.DocumentStorageSelectionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

import static com.ai.fabric.platform.backend.documentstorage.service.CustomerDocumentStorageService.*;

/** Coordinates the existing runtime ingestion API using exact owned upload references. */
@Service
public class DocumentStorageSelectionService {
    private final CustomerDocumentStorageService storage;
    private final DocumentStorageSelectionRepository selections;
    private final DeploymentReleaseRepository releases;
    private final DeploymentVersionRepository versions;
    private final DeploymentDocumentStorageBindingService bindings;
    private final DeploymentDocumentBindingResolver resolver;
    private final PlatformSecretService secrets;
    private final DeploymentDocumentKnowledgeOperationsService operations;
    private final ObjectMapper mapper;

    public DocumentStorageSelectionService(CustomerDocumentStorageService storage,DocumentStorageSelectionRepository selections,
        DeploymentReleaseRepository releases,DeploymentVersionRepository versions,DeploymentDocumentStorageBindingService bindings,
        DeploymentDocumentBindingResolver resolver,PlatformSecretService secrets,
        DeploymentDocumentKnowledgeOperationsService operations,ObjectMapper mapper) {
        this.storage=storage;this.selections=selections;this.releases=releases;this.versions=versions;
        this.bindings=bindings;this.resolver=resolver;this.secrets=secrets;this.operations=operations;this.mapper=mapper;
    }

    public SelectionResult prepare(String deploymentId,String connectionId,PrepareSelection request) {
        var deployment=storage.requireDeployment(deploymentId);
        if(request==null)throw invalid("Document selection is required.");
        storage.ready(deploymentId,connectionId);
        DeploymentOperationIdempotencyKey.require(request.operationKey());
        var release=releases.findById(request.releaseId())
            .filter(value->deploymentId.equals(value.getDeploymentId())&&request.targetProfileId().equals(value.getTargetProfileId()))
            .orElseThrow(()->notFound("The selected deployment release was not found."));
        var version=versions.findById(release.getDeploymentVersionId()).orElseThrow(()->conflict("Release version is unavailable."));
        var datasets=read(version.getMarketplaceDatasetConfigJson()).path("datasets");
        boolean datasetFound=false;
        for(var dataset:datasets) if(request.datasetId().equals(dataset.path("datasetId").asText())
            && "EXTERNAL_DOCUMENT_STORAGE".equals(dataset.path("ingestionMode").asText())) datasetFound=true;
        if(!datasetFound) throw invalid("Choose a document dataset from this release.");
        resolver.requireUnchanged(deployment,version,release);
        var binding=bindings.list(deploymentId).stream()
            .filter(value->request.targetProfileId().equals(value.targetProfileId()))
            .filter(value->connectionId.equals(value.safeMetadata().path("storageConnectionId").asText()))
            .findFirst().orElseThrow(()->conflict("Bind this storage connection to the selected target before indexing."));
        String prefix=secrets.resolveSecretForDeployment(DeploymentDocumentStorageBindingService.secretKey(deploymentId,request.targetProfileId())+"_PREFIX",deploymentId,request.targetProfileId());
        if(prefix==null||prefix.isBlank()||!binding.credentialsPresent()) throw conflict("Target storage scope is unavailable.");
        if(request.uploadIds()==null||request.uploadIds().isEmpty()||request.uploadIds().size()>20
            ||new HashSet<>(request.uploadIds()).size()!=request.uploadIds().size()) throw invalid("Select between one and twenty distinct uploaded files.");

        // Validate the entire selection before sending any registration/indexing command.
        List<DocumentStorageUploadEntity> selected=new ArrayList<>();
        for(String id:request.uploadIds()) {
            var upload=storage.requireUpload(deploymentId,connectionId,id);
            if(!upload.getObjectKey().startsWith(prefix)) throw invalid("A selected document is outside the target's bound purpose folder.");
            var previous=selections.findByUploadIdAndDeploymentIdAndTargetProfileIdAndReleaseId(id,deploymentId,request.targetProfileId(),request.releaseId());
            if(previous.isPresent() && (!request.datasetId().equals(previous.get().getDatasetId())
                || request.publicApproved()!=previous.get().isPublicApproved()))
                throw conflict("An existing selection has a different dataset or publication approval. Review it in document operations.");
            selected.add(upload);
        }
        DeploymentTargetReference target=new DeploymentTargetReference(deploymentId,request.targetProfileId(),request.releaseId());
        List<SelectionItem> results=new ArrayList<>();
        for(var upload:selected) results.add(prepareOne(target,request,upload,prefix));
        return new SelectionResult(results,results.stream().allMatch(value->"ACTIVE".equals(value.status())&&value.message()==null));
    }

    public List<SelectionItem> list(String deploymentId,String connectionId,String targetProfileId,String releaseId) {
        storage.requireConnection(deploymentId,connectionId);
        releases.findById(releaseId)
            .filter(value->deploymentId.equals(value.getDeploymentId())&&targetProfileId.equals(value.getTargetProfileId()))
            .orElseThrow(()->notFound("The selected deployment release was not found."));
        Set<String> allowed=new HashSet<>();
        storage.uploads(deploymentId,connectionId).forEach(value->allowed.add(value.id()));
        return selections.findByDeploymentIdAndTargetProfileIdAndReleaseIdOrderByUploadIdAsc(deploymentId,targetProfileId,releaseId).stream()
            .filter(value->allowed.contains(value.getUploadId()))
            .map(value->new SelectionItem(value.getUploadId(),value.getSourceId(),value.getStatus(),null,value.getLastError())).toList();
    }

    private SelectionItem prepareOne(DeploymentTargetReference target,PrepareSelection request,DocumentStorageUploadEntity upload,String prefix) {
        var current=selections.findByUploadIdAndDeploymentIdAndTargetProfileIdAndReleaseId(upload.getId(),target.deploymentId(),target.targetProfileId(),target.releaseId());
        DocumentStorageSelectionEntity selection;
        if(current.isPresent()) {
            selection=current.get();
            if(!selection.getDatasetId().equals(request.datasetId())||selection.isPublicApproved()!=request.publicApproved())
                throw conflict("The existing selection has a different dataset or publication approval. Review it in document operations.");
        } else {
            selection=new DocumentStorageSelectionEntity();selection.setId("dsel-"+UUID.randomUUID());
            selection.setUploadId(upload.getId());selection.setDeploymentId(target.deploymentId());
            selection.setTargetProfileId(target.targetProfileId());selection.setReleaseId(target.releaseId());
            selection.setDatasetId(request.datasetId());selection.setPublicApproved(request.publicApproved());
            selection.setOperationKey(request.operationKey());selection.setStatus("REGISTERING");selection.setCreatedAt(Instant.now());
            selection.setUpdatedAt(Instant.now());selection=selections.saveAndFlush(selection);
        }
        String workId=null;
        try {
            if(selection.getSourceId()==null) {
                var registration=mapper.createObjectNode();registration.put("datasetId",request.datasetId());
                registration.put("objectReference",upload.getObjectKey().substring(prefix.length()));
                registration.put("visibility","tenant");
                var metadata=registration.putObject("metadata");metadata.put("sourceCategory","customer-document");
                if(request.publicApproved())metadata.put("publicationStatus","PUBLIC_APPROVED");
                JsonNode registered=accepted(operations.register(target,registration,selection.getId()+"-register"));
                String sourceId=registered.path("sourceId").asText("");
                if(sourceId.isBlank())throw conflict("Runtime did not return a document source receipt.");
                selection.setSourceId(sourceId);selection.setStatus("REGISTERED");
                selection.setUpdatedAt(Instant.now());selection=selections.saveAndFlush(selection);
            }
            JsonNode detail=accepted(operations.detail(target,selection.getSourceId()));
            String status=detail.path("source").path("status").asText("");
            JsonNode result;
            if("ACTIVE".equals(status)) {
                selection.setStatus("ACTIVE");
            } else if("REGISTERED".equals(status) && !"INDEXING".equals(selection.getStatus())) {
                accepted(operations.preview(target,selection.getSourceId()));
                // Persist index intent first. A timeout is retried using the same runtime command key.
                selection.setStatus("INDEXING");selection.setUpdatedAt(Instant.now());selection=selections.saveAndFlush(selection);
                result=accepted(operations.index(target,selection.getSourceId(),selection.getId()+"-index"));
                selection.setStatus(result.path("source").path("status").asText("INDEXING"));
                workId=workId(result);
            } else if("REGISTERED".equals(status) && "INDEXING".equals(selection.getStatus())) {
                result=accepted(operations.index(target,selection.getSourceId(),selection.getId()+"-index"));
                selection.setStatus(result.path("source").path("status").asText("INDEXING"));workId=workId(result);
            } else if(Set.of("INDEXING","PREPARING","REPLACING").contains(status)) {
                result=accepted(operations.reconcile(target,selection.getSourceId(),selection.getId()+"-reconcile-"+sha256(request.operationKey()).substring(0,16)));
                selection.setStatus(result.path("source").path("status").asText(status));workId=workId(result);
            } else {
                selection.setStatus(status.isBlank()?"CHECK_REQUIRED":status);
            }
            selection.setLastError(null);
        } catch(RuntimeException error) {
            selection.setLastError("Document preparation is incomplete. Retry to read back the same operation, or inspect document operations.");
            if(error instanceof org.springframework.web.server.ResponseStatusException http
                && http.getStatusCode().is4xxClientError()) {
                selection.setUpdatedAt(Instant.now());selections.saveAndFlush(selection);
                throw error;
            }
            // Leave the durable phase/source ID intact rather than inventing a successful receipt.
        }
        selection.setUpdatedAt(Instant.now());selections.saveAndFlush(selection);
        return new SelectionItem(upload.getId(),selection.getSourceId(),selection.getStatus(),workId,selection.getLastError());
    }

    private JsonNode accepted(DeploymentDocumentKnowledgeOperationsService.RuntimeResponse response) {
        if(response.statusCode()<200||response.statusCode()>=300) throw conflict("Runtime document operation was not accepted.");
        return response.body();
    }
    private String workId(JsonNode result) { var work=result.path("manifest").path("work");return work.isArray()&&!work.isEmpty()?work.get(0).path("workId").asText(null):null; }
    private JsonNode read(String value) {try{return mapper.readTree(value);}catch(Exception error){throw conflict("Published document dataset configuration is unavailable.");}}
}
