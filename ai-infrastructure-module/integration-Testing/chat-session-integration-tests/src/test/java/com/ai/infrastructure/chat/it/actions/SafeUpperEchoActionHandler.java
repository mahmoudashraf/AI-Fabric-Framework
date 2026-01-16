package com.ai.infrastructure.chat.it.actions;

import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

@Component
public class SafeUpperEchoActionHandler implements ActionHandler {

    public static final String ACTION_NAME = "safe_upper_echo";

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name(ACTION_NAME)
            .description("Test-only safe action that echoes an upper-cased message. No side effects.")
            .category("test")
            .parameters(Map.of("message", "Text to echo back upper-cased"))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        return "Executing safe_upper_echo";
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String message = params != null ? (String) params.get("message") : null;
        String echoed = StringUtils.hasText(message) ? message.toUpperCase(Locale.ROOT) : "OK";
        return ActionResult.builder()
            .success(true)
            .message("Upper Echo: " + echoed)
            .data(Map.of("echo", echoed))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        return ActionResult.builder()
            .success(false)
            .message("safe_upper_echo failed: " + (e != null ? e.getMessage() : "unknown"))
            .errorCode("SAFE_UPPER_ECHO_ERROR")
            .build();
    }
}

