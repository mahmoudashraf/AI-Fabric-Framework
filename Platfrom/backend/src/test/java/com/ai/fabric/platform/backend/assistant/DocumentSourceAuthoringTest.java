package com.ai.fabric.platform.backend.assistant;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentSourceAuthoringTest {
    private final ObjectMapper mapper=new ObjectMapper();
    @Test void requiresTheFullExactTargetContext() {
        var reference=DocumentSourceAuthoring.reference("dep-a|stage|release-a|source-a");
        assertThat(reference.target()).isEqualTo(new DeploymentTargetReference("dep-a","stage","release-a"));
        for(String invalid:new String[]{"source-a","dep-a|stage|release-a|../source","dep-a|stage|release-a|source|other"})
            assertThatThrownBy(() -> DocumentSourceAuthoring.reference(invalid)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void aiCannotAttestItsOwnReferenceWasHumanReviewed() throws Exception {
        var documents=mock(DeploymentDocumentKnowledgeOperationsService.class);
        var handler=new DocumentSourceAuthoring(documents,mock(DeploymentTargetContextService.class),mapper);
        var snapshot=new AuthoringResourceService.Snapshot("owner","document",mapper.readTree("{\"questions\":[]}"),mapper.createObjectNode(),java.util.List.of("/questions"),"1",true,"");
        assertThatThrownBy(() -> handler.check("dep|stage|release|source",snapshot,mapper.readTree("{\"questions\":[{\"id\":\"q1\",\"question\":\"Q\",\"reviewed\":true}]}")))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("reviewed:false");
        verifyNoInteractions(documents);
    }
    @Test void richCatalogueRevisionRetainsAuthAndDoesNotMutateOriginal() throws Exception {
        var original=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree("""
            {"version":"1.0.0","contributions":{"datasets":[{"ingestionMode":"EXTERNAL_DOCUMENT_STORAGE","documentPolicy":{"allowedExtensions":[".txt"],"initialIndexRequiresConfirmation":true}}],
            "knowledgeSources":[{"authModes":["PLATFORM_PROXY_SESSION"],"filters":{"publicationStatus":"PUBLIC_APPROVED"}}]}}
            """);
        var updated=db.migration.V243__rich_document_catalogue_revisions.richManifest(original);
        assertThat(updated.at("/contributions/datasets/0/documentPolicy/allowedExtensions").toString()).contains(".pdf",".docx");
        assertThat(updated.at("/contributions/knowledgeSources")).isEqualTo(original.at("/contributions/knowledgeSources"));
        assertThat(updated.at("/contributions/datasets/0/documentPolicy/initialIndexRequiresConfirmation").asBoolean()).isTrue();
        assertThat(original.toString()).doesNotContain(".pdf");
    }
    @Test void aiCannotInventOrChangeReviewedEvidenceLabelsButMayPreserveThem() throws Exception {
        var documents = mock(DeploymentDocumentKnowledgeOperationsService.class);
        var handler = new DocumentSourceAuthoring(documents, mock(DeploymentTargetContextService.class), mapper);
        var configuration = mapper.readTree("""
            {"questions":[{"id":"q1","question":"Policy?","reviewed":false,
              "relevanceJudgments":[{"sourceId":"source","sourceVersion":1,"evidenceId":"chunk-a","grade":2,"reviewed":true}]}]}
            """);
        var snapshot = new AuthoringResourceService.Snapshot("owner", "document", configuration, mapper.createObjectNode().put("relevanceJudgmentsSupported", true), java.util.List.of("/questions"), "1", true, "");
        var invented = configuration.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)invented.at("/questions/0/relevanceJudgments/0")).put("evidenceId", "invented");
        assertThatThrownBy(() -> handler.check("dep|stage|release|source", snapshot, invented)).hasMessageContaining("cannot mark expected evidence as reviewed");
        var changed = configuration.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)changed.at("/questions/0")).put("question", "A different question?");
        assertThatThrownBy(() -> handler.check("dep|stage|release|source", snapshot, changed)).hasMessageContaining("cannot mark expected evidence as reviewed");
        verifyNoInteractions(documents);
        when(documents.savePreparation(any(), eq("source"), any(), eq(true)))
            .thenReturn(new DeploymentDocumentKnowledgeOperationsService.RuntimeResponse(200, mapper.createObjectNode().put("valid", true)));
        assertThat(handler.check("dep|stage|release|source", snapshot, configuration).path("valid").asBoolean()).isTrue();
        ((com.fasterxml.jackson.databind.node.ObjectNode)invented.at("/questions/0/relevanceJudgments/0")).put("reviewed", false);
        assertThat(handler.check("dep|stage|release|source", snapshot, invented).path("valid").asBoolean()).isTrue();
        var legacy = new AuthoringResourceService.Snapshot("owner", "document", configuration, mapper.createObjectNode(), java.util.List.of("/questions"), "1", true, "");
        assertThatThrownBy(() -> handler.check("dep|stage|release|source", legacy, invented)).hasMessageContaining("does not support expected evidence");
    }
}
