package com.ai.fabric.runtime.workflow;

import ai.fabric.execution.specialist.manifest.SpecialistDirectOutputProjector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class WorkflowRuntimeConfiguration {
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "loomai.configuration-assistant.protect-history", havingValue = "true")
    ai.fabric.chat.control.ConversationMessageProtection authoringMessageProtection(
        org.springframework.beans.factory.ObjectProvider<ai.fabric.execution.state.DurableExecutionSecurity> security) {
        return new ai.fabric.chat.control.ConversationMessageProtection() {
            private String binding(ai.fabric.chat.control.ConversationScope scope, String id) {
                return "canonical-authoring-message:" + security.getObject().canonicalHash(java.util.List.of(
                    scope.tenantId(), scope.deploymentId(), scope.conversationId(), scope.ownerId(), id));
            }
            public Body protect(ai.fabric.chat.control.ConversationScope scope, String id, Body body) {
                return new Body(security.getObject().protect(java.util.Map.of("content", body.content(), "metadata", body.metadata()), binding(scope, id)), java.util.Map.of());
            }
            @SuppressWarnings("unchecked")
            public Body unprotect(ai.fabric.chat.control.ConversationScope scope, String id, Body stored) {
                if (stored.content().isEmpty() && stored.metadata().isEmpty()) return stored;
                var decoded = security.getObject().unprotect(stored.content(), binding(scope, id));
                if (!(decoded.get("content") instanceof String text) || !(decoded.get("metadata") instanceof java.util.Map<?, ?> metadata))
                    throw new IllegalStateException("Authoring message protection is invalid.");
                return new Body(text, (java.util.Map<String, Object>) metadata);
            }
        };
    }
    @Bean
    SpecialistDirectOutputProjector workflowAnswerProjector(ObjectMapper mapper) {
        return SpecialistDirectOutputProjector.named("workflow-answer@1", (result, evidence) -> {
            var output = mapper.createObjectNode().put("answer", result.getMessage() == null ? "" : result.getMessage());
            output.set("work", mapper.valueToTree(result.getData().get("workflow")));
            return output;
        });
    }
}
