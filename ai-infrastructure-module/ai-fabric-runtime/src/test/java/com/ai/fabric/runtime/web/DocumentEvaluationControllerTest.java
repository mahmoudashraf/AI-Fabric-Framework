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
    @Test void variantIsPartOfTheIdempotentOperationAndCannotAddAuthority() {
        var auth=mock(RuntimeRequestAuthResolver.class);var service=mock(DocumentEvaluationService.class);var mutations=mock(RuntimeAdminMutationService.class);
        var request=mock(HttpServletRequest.class);var identity=mock(RuntimeResolvedIdentity.class);
        when(auth.resolveVerifiedPrivateContext(request,"document evaluation")).thenReturn(identity);
        var result=new ObjectMapper().createObjectNode().put("id","baseline");
        var variant=com.ai.fabric.runtime.documents.EvaluationRetrievalVariant.DENSE_BASELINE;
        when(service.start(identity,"source",4,variant)).thenReturn(result);
        when(mutations.execute(eq(identity),eq("DOCUMENT_EVALUATION"),eq("source:4:DENSE_BASELINE"),eq("operation"),eq(ObjectNode.class),any()))
            .thenAnswer(call -> call.<Supplier<ObjectNode>>getArgument(5).get());
        var controller=new DocumentEvaluationController(service,auth,mutations,new ObjectMapper());
        assertThat(controller.start("source",new DocumentEvaluationController.Start(4,variant),"operation",request)).containsEntry("id","baseline");
        verify(service).requireSourceAccess(identity,"source");
        verify(auth).requireScope(identity,"documents:evaluate","document evaluation");
        verify(auth,never()).requireScope(eq(identity),eq("chat:query"),anyString());
    }

    @Test void checksBothScopeAndCurrentSourceAccessBeforeDurableReplay() {
        var auth=mock(RuntimeRequestAuthResolver.class);var service=mock(DocumentEvaluationService.class);var mutations=mock(RuntimeAdminMutationService.class);
        var request=mock(HttpServletRequest.class);var identity=mock(RuntimeResolvedIdentity.class);
        when(auth.resolveVerifiedPrivateContext(request,"document evaluation")).thenReturn(identity);
        var controller=new DocumentEvaluationController(service,auth,mutations,new ObjectMapper());
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
        var controller=new DocumentEvaluationController(service,auth,mutations,new ObjectMapper());
        assertThat(controller.start("source",new DocumentEvaluationController.Start(4),"operation",request)).containsEntry("id","eval-1");
        verify(auth,never()).requireScope(eq(identity),eq("chat:query"),anyString());
    }
    @Test void httpResponsesUseEvaluationJsonInsteadOfLegacyTreeBeanProperties() throws Exception {
        var auth=mock(RuntimeRequestAuthResolver.class);var service=mock(DocumentEvaluationService.class);var mutations=mock(RuntimeAdminMutationService.class);
        var identity=mock(RuntimeResolvedIdentity.class);var mapper=new ObjectMapper();
        when(auth.resolveVerifiedPrivateContext(any(),eq("document evaluation"))).thenReturn(identity);
        var result=mapper.createObjectNode().put("id","eval-1").put("status","COMPLETED").put("modelCalls",2);
        result.putArray("cases").addObject().put("answer","Supported answer").put("referenceReviewed",false);
        when(service.start(identity,"source",4)).thenReturn(result);when(service.get(identity,"source","eval-1")).thenReturn(result);
        when(service.cancel(identity,"source","eval-1")).thenReturn(result);when(service.list(identity,"source")).thenReturn(java.util.List.of(result));
        when(mutations.execute(eq(identity),eq("DOCUMENT_EVALUATION"),eq("source:4"),eq("operation"),eq(ObjectNode.class),any()))
            .thenAnswer(call -> call.<Supplier<ObjectNode>>getArgument(5).get());
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new DocumentEvaluationController(service,auth,mutations,mapper))
            .setMessageConverters(new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter()).build();
        var base="/api/documents/sources/source/evaluations";
        for(var request:java.util.List.of(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base).header("Idempotency-Key","operation").contentType("application/json").content("{\"expectedRevision\":4}"),
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base+"/eval-1"),
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base+"/eval-1/cancel"))) {
            mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id").value("eval-1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.modelCalls").value(2))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.cases[0].answer").value("Supported answer"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.nodeType").doesNotExist());
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].id").value("eval-1"));
    }
}
