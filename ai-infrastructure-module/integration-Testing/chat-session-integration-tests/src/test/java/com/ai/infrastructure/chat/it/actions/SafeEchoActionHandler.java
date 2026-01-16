package com.ai.infrastructure.chat.it.actions;

import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class SafeEchoActionHandler implements ActionHandler {

    public static final String ACTION_NAME = "safe_echo";

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name(ACTION_NAME)
            .description("Test-only safe action that echoes a message. No side effects.")
            .category("test")
            .parameters(Map.of("message", "Text to echo back"))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        return "Executing safe_echo";
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String message = params != null ? (String) params.get("message") : null;
        String echoed = StringUtils.hasText(message) ? message : "ok";
        return ActionResult.builder()
            .success(true)
            .message("Echo: " + echoed)
            .data(Map.of("echo", echoed))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        return ActionResult.builder()
            .success(false)
            .message("safe_echo failed: " + (e != null ? e.getMessage() : "unknown"))
            .errorCode("SAFE_ECHO_ERROR")
            .build();
    }
}
