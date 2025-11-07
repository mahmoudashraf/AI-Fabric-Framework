package com.ai.infrastructure.intent.action;

import java.util.Map;

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
     * Fallback invoked when {@link #executeAction(Map, String)} raises an exception.
     *
     * @param e      error thrown during execution
     * @param userId identifier for the current user
     * @return structured error result
     */
    ActionResult handleError(Exception e, String userId);
}
