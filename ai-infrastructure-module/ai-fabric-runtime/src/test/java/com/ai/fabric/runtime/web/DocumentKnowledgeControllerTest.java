package com.ai.fabric.runtime.web;

import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.auth.RuntimeScopeCatalog;
import com.ai.fabric.runtime.documents.DocumentKnowledgeService;
import com.ai.fabric.runtime.admin.RuntimeAdminMutationService;
import com.ai.fabric.runtime.budget.RuntimeBudgetBindingService;
import com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes;
import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.openai.OpenAiBudgetInvocation;
import com.ai.fabric.budget.openai.OpenAiBudgetException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;

class DocumentKnowledgeControllerTest {

    @Test
    void retrievalProofOpensAndClosesTheEmbeddingOnlyBudgetScope() {
        var auth = mock(RuntimeRequestAuthResolver.class); var service = mock(DocumentKnowledgeService.class);
        var identity = mock(RuntimeResolvedIdentity.class); var request = mock(HttpServletRequest.class);
        var budgets = mock(RuntimeBudgetBindingService.class); var turns = new RuntimeBudgetTurnScopes(budgets);
        when(auth.resolveVerifiedPrivateContext(any(), any())).thenReturn(identity);
        var proof = new DocumentKnowledgeService.RetrievalProof("policy", 0, List.of(), 1);
        when(service.retrievalProof(identity, "policy", 5)).thenAnswer(invocation -> {
            assertThat(turns.capture(OpenAiBudgetInvocation.Operation.EMBEDDING)).isNotNull();
            assertThatThrownBy(() -> turns.capture(OpenAiBudgetInvocation.Operation.CHAT_COMPLETION)).isInstanceOf(OpenAiBudgetException.class);
            return proof;
        });
        var controller = new DocumentKnowledgeController(auth, service, mock(RuntimeAdminMutationService.class));
        controller.setProviderBudgetTurns(turns);

        assertThat(controller.retrievalProof(new DocumentKnowledgeController.RetrievalProofRequest("policy", null), request)).isSameAs(proof);

        verify(budgets).requireActive();
        assertThatThrownBy(() -> turns.capture(OpenAiBudgetInvocation.Operation.EMBEDDING)).isInstanceOf(OpenAiBudgetException.class);
    }

    @Test
    void failedDocumentAuthorizationCannotOpenAPaidScope() {
        var auth = mock(RuntimeRequestAuthResolver.class); var service = mock(DocumentKnowledgeService.class);
        var identity = mock(RuntimeResolvedIdentity.class); var request = mock(HttpServletRequest.class);
        var budgets = mock(RuntimeBudgetBindingService.class);
        when(auth.resolveVerifiedPrivateContext(any(), any())).thenReturn(identity);
        doThrow(new IllegalStateException("DOCUMENT_SCOPE_DENIED")).when(auth).requireScope(identity, RuntimeScopeCatalog.DOCUMENTS_READ, "document retrieval proof");
        var controller = new DocumentKnowledgeController(auth, service, mock(RuntimeAdminMutationService.class));
        controller.setProviderBudgetTurns(new RuntimeBudgetTurnScopes(budgets));

        assertThatThrownBy(() -> controller.retrievalProof(new DocumentKnowledgeController.RetrievalProofRequest("policy", 5), request))
            .hasMessageContaining("DOCUMENT_SCOPE_DENIED");
        verifyNoInteractions(budgets, service);
    }

    @Test
    void stoppedBudgetDeniesRetrievalBeforeTheSearchAndAServiceFailureClosesItsScope() {
        var auth = mock(RuntimeRequestAuthResolver.class); var service = mock(DocumentKnowledgeService.class);
        var identity = mock(RuntimeResolvedIdentity.class); var request = mock(HttpServletRequest.class);
        var budgets = mock(RuntimeBudgetBindingService.class); var turns = new RuntimeBudgetTurnScopes(budgets);
        when(auth.resolveVerifiedPrivateContext(any(), any())).thenReturn(identity);
        when(budgets.requireActive()).thenThrow(new BudgetException(BudgetException.Code.LIMIT_EXCEEDED)).thenReturn(null);
        var controller = new DocumentKnowledgeController(auth, service, mock(RuntimeAdminMutationService.class));
        controller.setProviderBudgetTurns(turns);
        var body = new DocumentKnowledgeController.RetrievalProofRequest("policy", 5);
        assertThatThrownBy(() -> controller.retrievalProof(body, request)).isInstanceOf(BudgetException.class);
        verifyNoInteractions(service);
        when(service.retrievalProof(identity, "policy", 5)).thenThrow(new IllegalStateException("SEARCH_FAILED"));
        assertThatThrownBy(() -> controller.retrievalProof(body, request)).hasMessageContaining("SEARCH_FAILED");
        assertThatThrownBy(() -> turns.capture(OpenAiBudgetInvocation.Operation.EMBEDDING)).isInstanceOf(OpenAiBudgetException.class);
    }

    @Test
    void swallowedProviderDenialCannotProduceASuccessfulRetrievalProof() {
        var auth = mock(RuntimeRequestAuthResolver.class); var service = mock(DocumentKnowledgeService.class);
        var identity = mock(RuntimeResolvedIdentity.class); var request = mock(HttpServletRequest.class);
        var turns = new RuntimeBudgetTurnScopes(mock(RuntimeBudgetBindingService.class));
        when(auth.resolveVerifiedPrivateContext(any(), any())).thenReturn(identity);
        when(service.retrievalProof(identity, "policy", 5)).thenAnswer(invocation -> {
            var operation = turns.capture(OpenAiBudgetInvocation.Operation.EMBEDDING);
            operation.observer().onDenied(new OpenAiBudgetInvocation.Denial(operation.operationId(), UUID.randomUUID(), BudgetException.Code.LIMIT_EXCEEDED, null));
            return new DocumentKnowledgeService.RetrievalProof("policy", 1, List.of(), 1);
        });
        var controller = new DocumentKnowledgeController(auth, service, mock(RuntimeAdminMutationService.class));
        controller.setProviderBudgetTurns(turns);
        assertThatThrownBy(() -> controller.retrievalProof(new DocumentKnowledgeController.RetrievalProofRequest("policy", 5), request))
            .isInstanceOf(ai.fabric.intent.orchestration.control.OrchestrationControlException.class);
        assertThatThrownBy(() -> turns.capture(OpenAiBudgetInvocation.Operation.EMBEDDING)).isInstanceOf(OpenAiBudgetException.class);
    }

    @Test
    void everyDocumentOperationRequiresPrivateContextAndItsExactLeastPrivilegeScope() {
        RuntimeRequestAuthResolver authResolver = mock(RuntimeRequestAuthResolver.class);
        DocumentKnowledgeService service = mock(DocumentKnowledgeService.class);
        RuntimeAdminMutationService mutations = mock(RuntimeAdminMutationService.class);
        RuntimeResolvedIdentity identity = mock(RuntimeResolvedIdentity.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(authResolver.resolveVerifiedPrivateContext(any(), any())).thenReturn(identity);
        DocumentKnowledgeController controller = new DocumentKnowledgeController(authResolver, service, mutations);

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
        controller.cleanupRetention("cleanup-1", request);
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
