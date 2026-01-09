package com.ai.infrastructure.it.action;

import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Test-only action handler used by real API integration tests to force a deterministic error path
 * without relying on the model to select an unknown action name.
 */
@Component
public class AlwaysFailActionHandler implements ActionHandler {

    public static final String ACTION_NAME = "always_fail_action";
    public static final String ERROR_CODE = "TEST_ACTION_FAILED";

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name(ACTION_NAME)
            .description("Test-only action that always fails to exercise error recovery.")
            .category("test")
            .parameters(Map.of())
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return true;
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        return "Executing test failure action.";
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        return ActionResult.builder()
            .success(false)
            .errorCode(ERROR_CODE)
            .message("Test-only failure: action executed and failed as expected.")
            .data(Map.of("action", ACTION_NAME))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        return ActionResult.builder()
            .success(false)
            .errorCode(ERROR_CODE)
            .message("Test-only failure (exception): " + (e != null ? e.getMessage() : "unknown"))
            .build();
    }
}

