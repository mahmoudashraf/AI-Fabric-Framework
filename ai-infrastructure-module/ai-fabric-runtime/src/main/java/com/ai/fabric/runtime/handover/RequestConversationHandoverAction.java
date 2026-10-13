package com.ai.fabric.runtime.handover;

import ai.fabric.intent.action.ActionAccessMode;
import ai.fabric.intent.action.ActionContext;
import ai.fabric.intent.action.ActionResult;
import ai.fabric.intent.action.ActionResultContracts;
import ai.fabric.intent.action.annotation.AIAction;
import ai.fabric.intent.action.annotation.ActionAllowed;
import ai.fabric.intent.action.annotation.ActionConfirmation;
import ai.fabric.intent.action.annotation.ActionConfirmationRequired;
import ai.fabric.intent.action.annotation.ActionExecute;
import ai.fabric.intent.action.annotation.Param;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;

@AIAction(
    name = "request_conversation_handover",
    description = "Request temporary transfer of this conversation to a human when the user explicitly asks for a person or accepts an escalation proposal",
    category = "conversation-control",
    accessMode = ActionAccessMode.WRITE_ONLY,
    requiresConfirmation = true,
    anonymousAllowed = true,
    terminal = true
)
@ConditionalOnProperty(prefix = "loomai.conversation-handover", name = "enabled", havingValue = "true")
public class RequestConversationHandoverAction {

    private final RuntimeConversationHandoverService service;

    public RequestConversationHandoverAction(RuntimeConversationHandoverService service) {
        this.service = service;
    }

    @ActionAllowed
    public boolean allowed(ActionContext context) {
        return context != null
            && context.hasConversation()
            && context.authContext() != null
            && StringUtils.hasText(context.authContext().getTenantId())
            && StringUtils.hasText(context.authContext().getDeploymentId())
            && context.authContext().getGrantedScopes() != null
            && context.authContext().getGrantedScopes().contains("chat:handover:request");
    }

    @ActionConfirmation
    public String confirm() {
        return "Would you like me to transfer this conversation to a human?";
    }

    @ActionConfirmationRequired
    public boolean confirmationRequired(
        @Param(
            value = "requestProvenance",
            description = "Classify why this action was selected: EXPLICIT_USER_REQUEST only when the user directly asked to speak to a human; otherwise MODEL_RECOMMENDATION",
            required = false
        ) ConversationHandoverRequestProvenance requestProvenance
    ) {
        return requestProvenance != ConversationHandoverRequestProvenance.EXPLICIT_USER_REQUEST;
    }

    @ActionExecute
    public ActionResult execute(
        @Param(
            value = "reasonCode",
            description = "Short provider-neutral reason for requesting human assistance",
            required = false
        ) String reasonCode,
        @Param(
            value = "requestProvenance",
            description = "Classify why this action was selected: EXPLICIT_USER_REQUEST only when the user directly asked to speak to a human; otherwise MODEL_RECOMMENDATION",
            required = false
        ) ConversationHandoverRequestProvenance requestProvenance,
        ActionContext context
    ) {
        try {
            ConversationHandoverRequestProvenance effectiveProvenance = requestProvenance == null
                ? ConversationHandoverRequestProvenance.MODEL_RECOMMENDATION
                : requestProvenance;
            var admission = service.request(
                context.authContext(),
                context.conversationId(),
                StringUtils.hasText(context.requestId()) ? context.requestId() : "handover-action:" + context.conversationId(),
                reasonCode,
                effectiveProvenance
            );
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("outcome", admission.outcome());
            result.put("conversationId", context.conversationId());
            result.put("controlState", admission.conversation().controlState().name());
            result.put("controlVersion", admission.conversation().controlVersion());
            result.put("handoverId", admission.conversation().handoverId() == null
                ? "" : admission.conversation().handoverId());
            result.put("requestProvenance", effectiveProvenance.name());
            if (admission.intakeSchema() != null) {
                result.put("intakeSchema", admission.intakeSchema());
            }
            return ActionResult.builder()
                .success(true)
                .message(admission.message())
                .data(ActionResultContracts.object(result))
                .build();
        } catch (ConversationHandoverException exception) {
            return ActionResult.builder()
                .success(false)
                .errorCode(exception.code())
                .message(exception.getMessage())
                .build();
        } catch (RuntimeException exception) {
            return ActionResult.builder()
                .success(false)
                .errorCode("CONVERSATION_HANDOVER_FAILED")
                .message("The conversation could not be transferred to a human.")
                .build();
        }
    }
}
