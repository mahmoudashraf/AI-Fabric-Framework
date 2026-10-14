package com.ai.fabric.runtime.web;

import com.ai.fabric.runtime.handover.ConversationHandoverException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationHandoverErrorHandlerTest {

    @Test
    void canonicalChatHistoryMapsMissingOrForeignOwnerToNotFound() throws Exception {
        ChatRuntimeController controller = mock(ChatRuntimeController.class);
        when(controller.getConversation(eq("foreign-conversation"), any()))
            .thenThrow(new ConversationHandoverException(
                "CONVERSATION_NOT_FOUND", "The conversation is not available.", HttpStatus.NOT_FOUND));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ConversationHandoverErrorHandler())
            .build();

        mvc.perform(get("/api/chat/me/conversations/foreign-conversation"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("The conversation is not available."));
    }
}
