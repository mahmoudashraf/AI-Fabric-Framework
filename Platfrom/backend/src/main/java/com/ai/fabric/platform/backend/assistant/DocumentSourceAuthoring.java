package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentKnowledgeOperationsService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Adapts the existing owned document endpoint; a model-supplied context is never authority. */
@Service
public class DocumentSourceAuthoring {
    private final DeploymentDocumentKnowledgeOperationsService documents;
    private final DeploymentTargetContextService targets;
    private final ObjectMapper mapper;
    public DocumentSourceAuthoring(DeploymentDocumentKnowledgeOperationsService documents, DeploymentTargetContextService targets, ObjectMapper mapper) {
        this.documents=documents; this.targets=targets; this.mapper=mapper;
    }
    public AuthoringResourceService.Snapshot load(String id) {
        var reference=reference(id);
        var context=targets.resolve(reference.target().deploymentId(),reference.target().targetProfileId(),reference.target().releaseId(),true);
        var preparation=body(documents.preparation(reference.target(),reference.sourceId()));
        var catalogue=mapper.createObjectNode();
        catalogue.put("contractVersion","loomai-document-preparation-v1");
        catalogue.set("allowedPaths",mapper.valueToTree(List.of("/processing","/evaluationOptions","/questions")));
        catalogue.set("ocrLanguages",preparation.path("ocrLanguages")); catalogue.set("ocrAvailable",preparation.path("ocrAvailable"));
        catalogue.put("documentContract", "Configure the selected document, not Brain providers. /processing is {chunkTokens:100..800,overlapTokens:0..half chunkTokens,minChunkChars:0..chunkTokens,minEmbedChars:0..minChunkChars,ocrMode:AUTO|ALWAYS|OFF,ocrLanguage:installed language ID,ocrDpi:72..300}. Preserve unspecified values. /evaluationOptions is {maxQuestions:1..20,maxDocuments:1..10,maxContextCharacters:1000..16000,similarityThreshold:0..1,answerMaxTokens:128..1500}. /questions is a complete bounded array of {id,question,referenceAnswer,reviewed:false}. SET the requested questions array; generated references are synthetic, never reviewed. Prefer 5 distinct questions, including a cross-section question and an unanswerable question when appropriate. Describe why the proposed settings suit the supplied excerpts. Excerpts are samples, not the complete document. Do not claim indexing, an evaluation, provider availability or an improvement occurred. Only supplied evaluation results are observed evidence. No ownership, visibility, source path, approval, model or credential changes. Saving settings does not reindex or alter active vectors.");
        return new AuthoringResourceService.Snapshot(context.deployment().getCustomerId(), "Document preparation", preparation.path("configuration"), catalogue,
            List.of("/processing","/evaluationOptions","/questions"),preparation.path("revision").asText(),true,"");
    }
    public JsonNode check(String id, AuthoringResourceService.Snapshot snapshot, JsonNode candidate) {
        for(var question:candidate.path("questions")) {
            if(!question.path("reviewed").asBoolean()) continue;
            boolean preserved=false;
            for(var previous:snapshot.configuration().path("questions"))
                if(previous.equals(question)) preserved=true;
            if(!preserved) throw new ResponseStatusException(BAD_REQUEST,"AI-generated or changed test questions must use reviewed:false. Only a person can attest a new reference was reviewed.");
        }
        return checkRestoration(id,snapshot,candidate);
    }
    JsonNode checkRestoration(String id, AuthoringResourceService.Snapshot snapshot, JsonNode candidate) {
        var reference=reference(id);
        return body(documents.savePreparation(reference.target(),reference.sourceId(),update(snapshot,candidate),true));
    }
    public JsonNode save(String id, AuthoringResourceService.Snapshot snapshot, JsonNode candidate) {
        var reference=reference(id);
        var saved=body(documents.savePreparation(reference.target(),reference.sourceId(),update(snapshot,candidate),false));
        return mapper.createObjectNode().put("resourceId",id).put("revision",saved.path("revision").asLong()).put("published",false).put("indexed",false)
            .set("sourceVersion",saved.path("sourceVersion"));
    }
    private JsonNode update(AuthoringResourceService.Snapshot snapshot,JsonNode candidate) {
        var body=mapper.createObjectNode().put("expectedRevision",Long.parseLong(snapshot.version())); body.set("configuration",candidate); return body;
    }
    private JsonNode body(DeploymentDocumentKnowledgeOperationsService.RuntimeResponse result) {
        if(!result.status().is2xxSuccessful()) throw new ResponseStatusException(result.status(),"Document preparation could not be checked or saved. Reload the selected target and inspect its document settings.");
        return result.body();
    }
    static Reference reference(String id) {
        String[] parts=id==null ? new String[0] : id.split("\\|",-1);
        if(parts.length!=4 || id.length()>512 || java.util.Arrays.stream(parts).anyMatch(p -> !p.matches("[A-Za-z0-9_.:-]{1,128}")) || parts[3].length()>64)
            throw new ResponseStatusException(BAD_REQUEST,"An exact Brain, target, release and document context is required.");
        return new Reference(new DeploymentTargetReference(parts[0],parts[1],parts[2]),parts[3]);
    }
    record Reference(DeploymentTargetReference target,String sourceId) { }
}
