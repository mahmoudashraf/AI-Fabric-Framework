package com.ai.fabric.runtime.web;

import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.auth.RuntimeScopeCatalog;
import com.ai.fabric.runtime.documents.DocumentKnowledgeService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentKnowledgeControllerTest {

    @Test
    void everyDocumentOperationRequiresPrivateContextAndItsExactLeastPrivilegeScope() {
        RuntimeRequestAuthResolver authResolver = mock(RuntimeRequestAuthResolver.class);
        DocumentKnowledgeService service = mock(DocumentKnowledgeService.class);
        RuntimeResolvedIdentity identity = mock(RuntimeResolvedIdentity.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(authResolver.resolveVerifiedPrivateContext(any(), any())).thenReturn(identity);
        DocumentKnowledgeController controller = new DocumentKnowledgeController(authResolver, service);

        controller.connectorStatus(request);
        controller.discover(new DocumentKnowledgeController.DiscoverRequest("documents", null, 10), request);
        controller.register(
            new DocumentKnowledgeController.RegisterSourceRequest("documents", "manual.txt", "PRIVATE", Map.of()),
            "register-1",
            request
        );
        controller.preview("source-1", request);
        controller.refresh("source-1", "refresh-1", request);
        controller.index("source-1", "index-1", request);
        controller.reconcile("source-1", "reconcile-1", request);
        controller.retrievalProof(new DocumentKnowledgeController.RetrievalProofRequest("service policy", 5), request);
        controller.retentionStatus(request);
        controller.cleanupRetention(request);
        controller.removeIndex("source-1", "delete-1", request);

        var ordered = inOrder(authResolver);
        require(ordered, authResolver, identity, request, RuntimeScopeCatalog.DOCUMENTS_READ, "document connector status");
        require(ordered, authResolver, identity, request, RuntimeScopeCatalog.DOCUMENTS_READ, "document source discovery");
        require(ordered, authResolver, identity, request, RuntimeScopeCatalog.DOCUMENTS_REGISTER, "document source registration");
        require(ordered, authResolver, identity, request, RuntimeScopeCatalog.DOCUMENTS_READ, "document source preview");
        require(ordered, authResolver, identity, request, RuntimeScopeCatalog.DOCUMENTS_INDEX, "document source refresh");
        require(ordered, authResolver, identity, request, RuntimeScopeCatalog.DOCUMENTS_INDEX, "document source indexing");
        require(ordered, authResolver, identity, request, RuntimeScopeCatalog.DOCUMENTS_INDEX, "document source reconciliation");
        require(ordered, authResolver, identity, request, RuntimeScopeCatalog.DOCUMENTS_READ, "document retrieval proof");
        require(ordered, authResolver, identity, request, RuntimeScopeCatalog.DOCUMENTS_READ, "document retention status");
        require(ordered, authResolver, identity, request, RuntimeScopeCatalog.DOCUMENTS_INDEX, "document retention cleanup");
        require(ordered, authResolver, identity, request, RuntimeScopeCatalog.DOCUMENTS_DELETE_INDEX, "document index removal");
    }

    private void require(
        org.mockito.InOrder ordered,
        RuntimeRequestAuthResolver authResolver,
        RuntimeResolvedIdentity identity,
        HttpServletRequest request,
        String scope,
        String surface
    ) {
        ordered.verify(authResolver).resolveVerifiedPrivateContext(request, surface);
        ordered.verify(authResolver).requireScope(identity, scope, surface);
    }
}
