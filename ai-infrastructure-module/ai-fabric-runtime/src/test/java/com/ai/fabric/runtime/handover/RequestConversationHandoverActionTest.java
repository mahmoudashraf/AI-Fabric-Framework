package com.ai.fabric.runtime.handover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.fabric.intent.action.ActionContext;
import ai.fabric.intent.orchestration.OrchestrationContext;
import ai.fabric.intent.orchestration.OrchestrationContextMetadataKeys;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RequestConversationHandoverActionTest {

    private final RuntimeConversationHandoverService service =
        mock(RuntimeConversationHandoverService.class);
    private final RequestConversationHandoverAction action =
        new RequestConversationHandoverAction(service);

    @Test
    void explicitUserRequestDoesNotAskTheSameQuestionTwice() {
        assertThat(action.confirmationRequired(
            ConversationHandoverRequestProvenance.EXPLICIT_USER_REQUEST
        )).isFalse();
    }

    @Test
    void recommendationOrMissingProvenanceRequiresConfirmation() {
        assertThat(action.confirmationRequired(
            ConversationHandoverRequestProvenance.MODEL_RECOMMENDATION
        )).isTrue();
        assertThat(action.confirmationRequired(null)).isTrue();
    }

    @Test
    void preservesTypedHandoverFailureWithoutCollapsingItsContract() {
        when(service.request(any(), any(), any(), any(), any())).thenThrow(
            new ConversationHandoverException(
                "CONVERSATION_HANDOVER_UNAVAILABLE",
                "No conversation channel is currently available.",
                HttpStatus.SERVICE_UNAVAILABLE
            )
        );

        var result = action.execute(
            "user-request",
            ConversationHandoverRequestProvenance.EXPLICIT_USER_REQUEST,
            actionContext()
        );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("CONVERSATION_HANDOVER_UNAVAILABLE");
        assertThat(result.getMessage()).isEqualTo("No conversation channel is currently available.");
    }

    @Test
    void doesNotExposeUnexpectedRuntimeFailureDetails() {
        when(service.request(any(), any(), any(), any(), any())).thenThrow(
            new IllegalStateException("jdbc:postgresql://private-host/internal-secret")
        );

        var result = action.execute(
            "user-request",
            ConversationHandoverRequestProvenance.EXPLICIT_USER_REQUEST,
            actionContext()
        );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("CONVERSATION_HANDOVER_FAILED");
        assertThat(result.getMessage())
            .isEqualTo("The conversation could not be transferred to a human.");
    }

    private ActionContext actionContext() {
        return new ActionContext(
            OrchestrationContext.builder()
                .requestId("request-1")
                .conversationId("conversation-1")
                .sessionId("session-1")
                .metadata(Map.of(
                    OrchestrationContextMetadataKeys.SUBJECT_ID, "owner-1",
                    OrchestrationContextMetadataKeys.SUBJECT_TYPE, "ANONYMOUS_SESSION",
                    OrchestrationContextMetadataKeys.TENANT_ID, "tenant-1",
                    OrchestrationContextMetadataKeys.DEPLOYMENT_ID, "deployment-1",
                    OrchestrationContextMetadataKeys.GRANTED_SCOPES,
                    List.of("chat:handover:request")
                ))
                .build(),
            null
        );
    }
}
