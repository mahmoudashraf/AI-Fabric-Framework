package com.ai.fabric.runtime.specialist.assistance;

import ai.fabric.execution.chain.SpecialistChainExecutionRequest;
import ai.fabric.execution.chain.SpecialistChainGateway;
import ai.fabric.execution.chain.SpecialistChainId;
import ai.fabric.execution.context.ExecutionCallerIdentity;
import ai.fabric.execution.context.ExecutionPrincipal;
import ai.fabric.execution.context.ExecutionPrincipalType;
import ai.fabric.execution.context.ExecutionSource;
import ai.fabric.execution.context.ExecutionSubjectRef;
import ai.fabric.execution.context.TrustedExecutionContext;
import ai.fabric.execution.specialist.SpecialistId;
import ai.fabric.execution.specialist.client.SpecialistClientFactory;
import ai.fabric.execution.specialist.client.SpecialistInvocation;
import ai.fabric.intent.action.*;
import ai.fabric.intent.orchestration.control.OrchestrationControlException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public final class SpecialistAssistanceActionContributor implements AIActionRegistryContributor {
    private final SpecialistAssistanceBundle bundle;
    private final ObjectProvider<SpecialistClientFactory> clients;
    private final ObjectProvider<SpecialistChainGateway> chains;
    private final ObjectMapper mapper;

    public SpecialistAssistanceActionContributor(SpecialistAssistanceBundle bundle,
        ObjectProvider<SpecialistClientFactory> clients, ObjectProvider<SpecialistChainGateway> chains,
        ObjectMapper mapper) {
        this.bundle = bundle;
        this.clients = clients;
        this.chains = chains;
        this.mapper = mapper;
    }

    @Override public List<AIActionHandler> getHandlers() {
        return bundle.enabled() ? List.of(new Handler()) : List.of();
    }

    private final class Handler implements AIActionHandler {
        private final Map<String, JsonNode> catalogue = catalogue();
        private final AIActionMetaData metadata = metadata();

        private Map<String, JsonNode> catalogue() {
            var result = new LinkedHashMap<String, JsonNode>();
            bundle.content().path("invocations").forEach(item -> result.put(item.path("id").asText(), item));
            return Map.copyOf(result);
        }

        private AIActionMetaData metadata() {
            var descriptions = new ArrayList<String>();
            catalogue.forEach((id, item) -> descriptions.add(id + ": " + item.path("name").asText()
                + ". " + item.path("description").asText()));
            return AIActionMetaData.builder().name("consult_specialist").displayName("Resolution checks")
                .description("Consult one configured read-only specialist capability when its expertise or coordinated checks help. "
                    + "Answer simple questions directly when sufficient. Available capabilities: " + String.join("; ", descriptions))
                .category("specialist-assistance").accessMode(ActionAccessMode.READ)
                .anonymousAllowed(true).confirmationRequired(false).groundingEligible(true)
                .readActionResolutionEligible(true).sideEffectLevel(ActionSideEffectLevel.NONE)
                .parameters(Map.of("capabilityId", "Exact configured capability ID", "question", "Bounded diagnostic objective"))
                .parameterSchemas(Map.of(
                    "capabilityId", AIActionParamSchema.builder().name("capabilityId").type(AIActionParamType.STRING)
                        .required(true).allowedValues(List.copyOf(catalogue.keySet())).build(),
                    "question", AIActionParamSchema.builder().name("question").type(AIActionParamType.STRING)
                        .required(true).build()))
                .requiredParameters(Set.of("capabilityId", "question")).build();
        }

        @Override public AIActionMetaData getActionMetadata() { return metadata; }
        @Override public boolean requiresConfirmation() { return false; }
        @Override public String getConfirmationMessage(Map<String, Object> params, ActionContext context) { return null; }
        @Override public boolean validateActionAllowed(ActionContext context) {
            return context != null && context.authContext() != null
                && context.authContext().getSubjectId() != null
                && context.authContext().getTenantId() != null
                && context.authContext().getDeploymentId() != null;
        }

        @Override public ActionResult executeAction(Map<String, Object> params, ActionContext context) {
            if (!validateActionAllowed(context)) return failure("SPECIALIST_AUTHORITY_REQUIRED", "Verified deployment identity is required.");
            Object selected = params == null ? null : params.get("capabilityId");
            JsonNode invocation = selected instanceof String id ? catalogue.get(id) : null;
            Object questionValue = params == null ? null : params.get("question");
            if (invocation == null || !(questionValue instanceof String question) || question.isBlank() || question.length() > 8_000)
                return failure("SPECIALIST_REQUEST_INVALID", "Select a configured capability and provide a bounded question.");
            TrustedExecutionContext trusted = trustedContext(context);
            for (var scope : invocation.path("requiredScopes")) {
                if (!trusted.grantedScopes().contains(scope.asText()))
                    return failure("SPECIALIST_SCOPE_DENIED", "This capability is not authorized for the current requester.");
            }
            verifyParent(context);
            var input = mapper.createObjectNode().put("question", question);
            Instant deadline = Instant.now().plusSeconds(Math.max(5, Math.min(120, invocation.path("deadlineSeconds").asInt(75))));
            String target = invocation.path("targetRef").asText();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("capabilityId", selected);
            data.put("name", invocation.path("name").asText());
            if ("CHAIN".equals(invocation.path("targetType").asText())) {
                SpecialistChainGateway gateway = chains.getIfAvailable();
                if (gateway == null) return failure("SPECIALIST_CHAIN_UNAVAILABLE", "The configured resolution chain is unavailable.");
                // Each action invocation owns one chain request; polling reuses its execution handle.
                var handle = gateway.submit(new SpecialistChainExecutionRequest<>(SpecialistChainId.parse(target), input,
                    trusted, null, deadline, "specialist-assistance-" + UUID.randomUUID()));
                var result = awaitResult(() -> gateway.findResult(handle.executionId(), trusted),
                    () -> gateway.cancel(handle.executionId(), trusted), context, deadline);
                verifyParent(context);
                data.put("executionRef", result.executionId());
                data.put("status", result.status().name());
                data.put("findings", result.projectedResults());
                data.put("observedAt", result.completedAt() == null ? Instant.now() : result.completedAt());
                if (!result.succeeded()) return ActionResult.builder().success(false)
                    .errorCode(result.failure() == null ? "SPECIALIST_CHAIN_INCOMPLETE" : result.failure().reason())
                    .message("Resolution checks did not complete. Do not describe the failed checks as successful.")
                    .data(ActionResultContracts.object(data)).build();
            } else {
                var factory = clients.getIfAvailable();
                if (factory == null) return failure("SPECIALIST_UNAVAILABLE", "The configured specialist is unavailable.");
                var client = factory.bind(SpecialistId.parse(target), JsonNode.class, JsonNode.class);
                var handle = client.submit(new SpecialistInvocation<>(input, trusted, null, deadline, null));
                var result = awaitResult(() -> client.find(handle.invocationId(), trusted)
                    .map(ai.fabric.execution.specialist.client.SpecialistExecutionSnapshot::result),
                    () -> client.cancel(handle.invocationId(), trusted), context, deadline);
                verifyParent(context);
                data.put("executionRef", result.invocationId());
                data.put("status", result.status().name());
                data.put("findings", result.output() == null ? Map.of() : result.output());
                data.put("evidence", result.evidence());
                data.put("observedAt", result.completedAt() == null ? Instant.now() : result.completedAt());
                if (!result.succeeded()) return ActionResult.builder().success(false)
                    .errorCode(result.failure() == null ? "SPECIALIST_INCOMPLETE" : result.failure().reason())
                    .message("The specialist check did not complete. Explain the limitation without inventing findings.")
                    .data(ActionResultContracts.object(data)).build();
            }
            return ActionResult.builder().success(true).message("Configured resolution checks completed.")
                .data(ActionResultContracts.object(data)).build();
        }

        @Override public ActionResult handleError(Exception failure, ActionContext context) {
            if (failure instanceof OrchestrationControlException control) throw control;
            return failure("SPECIALIST_INVOCATION_FAILED", "The configured diagnostic capability could not complete.");
        }
    }

    private static void verifyParent(ActionContext context) {
        if (context.orchestrationContext() != null && context.orchestrationContext().getOrchestrationTurnGuard() != null)
            context.orchestrationContext().getOrchestrationTurnGuard().verifyBeforeOrchestration();
    }

    private static <T> T awaitResult(java.util.function.Supplier<java.util.Optional<T>> read,
        Runnable cancel, ActionContext context, Instant deadline) {
        try {
            while (Instant.now().isBefore(deadline)) {
                verifyParent(context);
                var completed = read.get();
                if (completed.isPresent()) return completed.get();
                Thread.sleep(150);
            }
            throw new IllegalStateException("SPECIALIST_DEADLINE_EXCEEDED");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            try { cancel.run(); } catch (RuntimeException ignored) { }
            throw new OrchestrationControlException("SPECIALIST_INTERRUPTED", "The diagnostic invocation was interrupted.");
        } catch (RuntimeException failure) {
            try { cancel.run(); } catch (RuntimeException ignored) { }
            throw failure;
        }
    }

    private static TrustedExecutionContext trustedContext(ActionContext context) {
        var request = context.pipelineContext() == null ? null : context.pipelineContext().getOrchestrationRequest();
        if (request != null && request.trustedExecutionContext() != null) return request.trustedExecutionContext();
        var auth = context.authContext();
        boolean browser = "PUBLIC_BROWSER".equals(auth.getCallerType());
        return new TrustedExecutionContext(new ExecutionPrincipal(auth.getSubjectId(),
            browser ? context.isAnonymous() ? ExecutionPrincipalType.ANONYMOUS_SESSION : ExecutionPrincipalType.END_USER
                : ExecutionPrincipalType.SERVICE),
            new ExecutionSubjectRef("conversation", context.conversationId() == null ? auth.getSubjectId() : context.conversationId()),
            browser ? ExecutionSource.INTERACTIVE : ExecutionSource.APPLICATION,
            auth.getTenantId(), auth.getDeploymentId(), auth.getGrantedScopes() == null ? Set.of() : Set.copyOf(auth.getGrantedScopes()),
            context.requestId(), Instant.now()).withCallerIdentity(new ExecutionCallerIdentity(auth.getSubjectType(),
                auth.getAuthMode(), auth.getCallerType(), auth.getSessionId(), auth.getCustomerId(), auth.getIssuer(),
                auth.getAudiences(), auth.getExpiresAt() == null ? null : Instant.parse(auth.getExpiresAt())));
    }

    private static ActionResult failure(String code, String message) {
        return ActionResult.builder().success(false).errorCode(code).message(message).build();
    }
}
