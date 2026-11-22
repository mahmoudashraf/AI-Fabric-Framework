package com.ai.fabric.runtime.web;

import com.ai.fabric.runtime.auth.*;
import com.ai.fabric.runtime.admin.RuntimeAdminMutationService;
import com.ai.fabric.runtime.documents.DocumentEvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DocumentEvaluationControllerTest {
    @Test void checksBothScopeAndCurrentSourceAccessBeforeDurableReplay() {
        var auth=mock(RuntimeRequestAuthResolver.class);var service=mock(DocumentEvaluationService.class);var mutations=mock(RuntimeAdminMutationService.class);
        var request=mock(HttpServletRequest.class);var identity=mock(RuntimeResolvedIdentity.class);
        when(auth.resolveVerifiedPrivateContext(request,"document evaluation")).thenReturn(identity);
        var controller=new DocumentEvaluationController(service,auth,mutations);
        doThrow(new IllegalStateException("REVOKED_SOURCE")).when(service).requireSourceAccess(identity,"source");
        assertThatThrownBy(() -> controller.start("source",new DocumentEvaluationController.Start(4),"operation",request)).hasMessage("REVOKED_SOURCE");
        verify(auth).requireScope(identity,"documents:evaluate","document evaluation");verifyNoInteractions(mutations);
    }
    @Test void admissionRetainsIdempotencyAndDoesNotBorrowChatPermission() {
        var auth=mock(RuntimeRequestAuthResolver.class);var service=mock(DocumentEvaluationService.class);var mutations=mock(RuntimeAdminMutationService.class);
        var request=mock(HttpServletRequest.class);var identity=mock(RuntimeResolvedIdentity.class);
        when(auth.resolveVerifiedPrivateContext(request,"document evaluation")).thenReturn(identity);
        var result=new ObjectMapper().createObjectNode().put("id","eval-1");
        when(service.start(identity,"source",4)).thenReturn(result);
        when(mutations.execute(eq(identity),eq("DOCUMENT_EVALUATION"),eq("source:4"),eq("operation"),eq(ObjectNode.class),any()))
            .thenAnswer(call -> call.<Supplier<ObjectNode>>getArgument(5).get());
        var controller=new DocumentEvaluationController(service,auth,mutations);
        assertThat(controller.start("source",new DocumentEvaluationController.Start(4),"operation",request)).isEqualTo(result);
        verify(auth,never()).requireScope(eq(identity),eq("chat:query"),anyString());
    }
}
