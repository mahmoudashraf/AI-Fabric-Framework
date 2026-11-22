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
}
