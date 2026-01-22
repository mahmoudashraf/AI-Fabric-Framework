package com.ai.infrastructure.intent.action;

import com.ai.infrastructure.intent.orchestration.OrchestrationContext;

import java.util.Map;
import java.util.Optional;

/**
 * Contract implemented by user-defined action handlers. Each handler manages exactly one action.
 */
public interface ActionHandler {

    /**
     * @return metadata describing the action handled by this component.
     */
    AIActionMetaData getActionMetadata();

    /**
     * Validate whether the current user may perform the action.
     *
     * @param userId identifier for the current user (may be {@code null} for anonymous requests)
     * @return {@code true} when the action is allowed, otherwise {@code false}
     */
    boolean validateActionAllowed(String userId);

    /**
     * Resolve the confirmation message presented to the user prior to executing the action.
     *
     * @param params action parameters supplied by the intent extractor
     * @return confirmation message text
     */
    String getConfirmationMessage(Map<String, Object> params);

    /**
     * Execute the business logic associated with the action.
     *
     * @param params action parameters supplied by the intent extractor
     * @param userId identifier for the current user (may be {@code null} for anonymous requests)
     * @return structured result describing the outcome
     */
    ActionResult executeAction(Map<String, Object> params, String userId);

    /**
     * Indicates whether this action requires explicit user confirmation before execution.
     *
     * <p>When {@code true}, the framework stores the action as "pending" in the conversation metadata and
     * asks the user to confirm (e.g., "yes" / "no") in a subsequent turn.</p>
     *
     * <p>Default is {@code false} to preserve backward compatibility.</p>
     */
    default boolean requiresConfirmation() {
        return false;
    }

    /**
     * Provide a bounded, explicitly shaped facts payload that is safe to send to an LLM after the action executes.
     *
     * <p>Framework behavior:</p>
     * <ul>
     *   <li>If this method returns {@link Optional#empty()}, the framework will not run post-action generation.</li>
     *   <li>Returned facts are treated as the <strong>only</strong> source of truth for grounded generation.</li>
     * </ul>
     *
     * <p>This method must be side-effect free and must not re-run the action. It is invoked only after
     * {@link #executeAction(Map, String)} returns.</p>
     *
     * @param actionResult result returned by {@link #executeAction(Map, String)}
     * @param context      orchestration context for the request
     * @return an optional facts map (primitive/map/list structures recommended); empty disables post-action generation
     */
    default Optional<Map<String, Object>> buildPostActionLlmFacts(ActionResult actionResult, OrchestrationContext context) {
        return Optional.empty();
    }

    /**
     * Fallback invoked when {@link #executeAction(Map, String)} raises an exception.
     *
     * @param e      error thrown during execution
     * @param userId identifier for the current user
     * @return structured error result
     */
    ActionResult handleError(Exception e, String userId);
}
