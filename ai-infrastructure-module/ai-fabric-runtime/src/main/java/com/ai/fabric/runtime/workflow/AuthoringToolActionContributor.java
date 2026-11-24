package com.ai.fabric.runtime.workflow;

import ai.fabric.intent.action.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Native first-party API adapters. Planning, prompts and action choice remain in the configured Brain/framework. */
@Component
public final class AuthoringToolActionContributor implements AIActionRegistryContributor {
    private final WorkflowAssistanceBundle bundle;
    private final AuthoringPlatformClient client;
    private final ObjectMapper mapper;
    public AuthoringToolActionContributor(WorkflowAssistanceBundle bundle, AuthoringPlatformClient client, ObjectMapper mapper) {
        this.bundle = bundle; this.client = client; this.mapper = mapper;
    }
    @Override public List<AIActionHandler> getHandlers() {
        if (!bundle.enabled() || !client.enabled()) return List.of();
        var result = new ArrayList<AIActionHandler>();
        AuthoringPlatformClient.READS.stream().sorted().forEach(name -> result.add(new Handler(name, false)));
        AuthoringPlatformClient.WRITES.stream().sorted().forEach(name -> result.add(new Handler(name, true)));
        return List.copyOf(result);
    }
    private final class Handler implements AIActionHandler {
        private final AIActionMetaData metadata;
        Handler(String name, boolean write) {
            var schemas = new java.util.LinkedHashMap<String, AIActionParamSchema>();
            var required = new java.util.LinkedHashSet<String>();
            if ("authoring_check_candidate".equals(name) || "authoring_update_draft".equals(name)) {
                schemas.put("changes", AIActionParamSchema.builder().name("changes").type(AIActionParamType.ARRAY).required(true)
                    .description("Structured changes following the selected resource's contracts: operation SET, REMOVE, UPSERT or REMOVE_ITEM; path; optional key; value. Read the context/contracts before proposing changes.")
                    .items(AIActionParamSchema.builder().type(AIActionParamType.OBJECT).build()).build());
                required.add("changes");
            }
            if (write) {
                schemas.put("operationId", AIActionParamSchema.builder().name("operationId").type(AIActionParamType.STRING).required(true)
                    .visibility("INTERNAL").resolveFrom(Map.of("source", "RUNTIME_CONTEXT", "field", "workflowOperationId")).build());
                required.add("operationId");
            }
            if ("authoring_undo_request".equals(name)) {
                schemas.put("savedOperationId", AIActionParamSchema.builder().name("savedOperationId").type(AIActionParamType.STRING)
                    .description("Exact saved operation ID from this request's actual work receipts.").required(true).build());
                required.add("savedOperationId");
            }
            metadata = AIActionMetaData.builder().name(name).displayName(name).description(description(name))
                .category("configuration-authoring").accessMode(write ? ActionAccessMode.WRITE_ONLY : ActionAccessMode.READ)
                .sideEffectLevel(write ? ActionSideEffectLevel.MUTATING : ActionSideEffectLevel.NONE)
                .anonymousAllowed(false).confirmationRequired(false).groundingEligible(true).readActionResolutionEligible(!write)
                .parameterSchemas(schemas).requiredParameters(required).build();
        }
        @Override public AIActionMetaData getActionMetadata() { return metadata; }
        @Override public boolean requiresConfirmation() { return false; }
        @Override public String getConfirmationMessage(Map<String, Object> parameters, ActionContext context) { return null; }
        @Override public boolean validateActionAllowed(ActionContext context) {
            return context != null && context.orchestrationContext() != null && context.orchestrationContext().getWorkflowExecution() != null
                && context.authContext() != null && context.authContext().getGrantedScopes().contains("configuration:author")
                && context.metadata().containsKey("authoringSessionId") && context.metadata().containsKey("authoringTurnId");
        }
        @Override public ActionResult executeAction(Map<String, Object> parameters, ActionContext context) {
            var response = client.call(metadata.getName(), parameters, context);
            var body = mapper.convertValue(response.body(), new TypeReference<Object>() {});
            boolean success = response.status() >= 200 && response.status() < 300;
            var data = new java.util.LinkedHashMap<String, Object>(); data.put("result", body);
            if (!success) data.put("httpStatus", response.status());
            return ActionResult.builder().success(success).data(ActionPayload.object(data))
                .errorCode(success ? null : response.status() == 400 ? "AUTHORING_CANDIDATE_REJECTED" : "AUTHORING_RECEIVER_DENIED")
                .build();
        }
        @Override public Optional<Map<String, Object>> buildPostActionLlmFacts(ActionResult result, ActionContext context) {
            return result == null || result.getData() == null ? Optional.empty() : Optional.of(result.getData().toMap());
        }
        @Override public ActionResult handleError(Exception failure, ActionContext context) {
            return ActionResult.builder().success(false).errorCode("AUTHORING_TOOL_OUTCOME_UNAVAILABLE").build();
        }
    }
    private String description(String name) {
        return switch (name) {
            case "authoring_get_context" -> "Read the current owned draft, allowed fields, supported schemas, safe credential references and selected editor/request context. This is not a permission to publish or provision.";
            case "authoring_read_selected_sources" -> "Read only the documentation explicitly selected for this request, narrowed to the selected formal API operations when applicable.";
            case "authoring_check_candidate" -> "Validate proposed field changes without saving. Observe typed findings and correct the candidate if the remaining correction allowance permits.";
            case "authoring_get_work_status" -> "Read actual operation receipts for this exact request. Reconcile an uncertain write before considering further changes.";
            case "authoring_update_draft" -> "WRITE requested changes into the current owned draft using its existing merge/validation service. The user reviews before publication; this never publishes, deploys, indexes, provisions or grants access.";
            case "authoring_undo_request" -> "WRITE an inverse of one saved operation from this request. Existing later conflicting manual edits remain protected; never undo unrelated work.";
            default -> throw new IllegalArgumentException("Unknown authoring capability");
        };
    }
}
