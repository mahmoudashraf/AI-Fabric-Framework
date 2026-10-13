package com.ai.fabric.runtime.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.fabric.chat.control.ConversationControlViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class RuntimeControllerAdviceTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ConversationControlFailureController())
            .setControllerAdvice(new RuntimeControllerAdvice())
            .build();
    }

    @Test
    void hidesConversationOwnerMismatchBehindNotFoundResponse() throws Exception {
        mockMvc.perform(get("/test/conversation-control/owner-mismatch"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Conversation is not available."));
    }

    @Test
    void makesMissingAndForeignConversationResponsesIndistinguishable() throws Exception {
        String missing = mockMvc.perform(get("/test/conversation-control/not-found"))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String foreign = mockMvc.perform(get("/test/conversation-control/owner-mismatch"))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(foreign).isEqualTo(missing);
    }

    @Test
    void mapsOtherConversationControlViolationsToConflict() throws Exception {
        mockMvc.perform(get("/test/conversation-control/conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("CONTROL_COMMAND_IDEMPOTENCY_CONFLICT"))
            .andExpect(jsonPath("$.message").value("The idempotency key conflicts."));
    }

    @RestController
    private static final class ConversationControlFailureController {

        @GetMapping("/test/conversation-control/owner-mismatch")
        void ownerMismatch() {
            throw new ConversationControlViolationException(
                "CONVERSATION_OWNER_MISMATCH",
                "Conversation is not available."
            );
        }

        @GetMapping("/test/conversation-control/not-found")
        void notFound() {
            throw new ConversationControlViolationException(
                "CONVERSATION_NOT_FOUND",
                "Conversation is not available."
            );
        }

        @GetMapping("/test/conversation-control/conflict")
        void conflict() {
            throw new ConversationControlViolationException(
                "CONTROL_COMMAND_IDEMPOTENCY_CONFLICT",
                "The idempotency key conflicts."
            );
        }
    }
}
