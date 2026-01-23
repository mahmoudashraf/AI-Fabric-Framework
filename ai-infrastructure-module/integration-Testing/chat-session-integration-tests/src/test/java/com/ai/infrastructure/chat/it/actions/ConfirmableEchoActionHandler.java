package com.ai.infrastructure.chat.it.actions;

import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ConfirmableEchoActionHandler implements ActionHandler {

    public static final String ACTION_NAME = "confirmable_echo";

    private static final AtomicInteger EXECUTION_COUNT = new AtomicInteger(0);

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name(ACTION_NAME)
            .description("Test-only action that requires confirmation and echoes a message.")
            .category("test")
            .parameters(Map.of("message", "Text to echo back"))
            .requiredParameters(java.util.Set.of("message"))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        String message = params != null ? (String) params.get("message") : null;
        return "Confirm execute confirmable_echo(message=" + (StringUtils.hasText(message) ? message : "ok") + ")?";
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        EXECUTION_COUNT.incrementAndGet();
        ConfirmableActionExecutionLog.record(ACTION_NAME);

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
            .message("confirmable_echo failed: " + (e != null ? e.getMessage() : "unknown"))
            .errorCode("CONFIRMABLE_ECHO_ERROR")
            .build();
    }

    public static void resetExecutions() {
        EXECUTION_COUNT.set(0);
        ConfirmableActionExecutionLog.reset();
    }

    public static int getExecutionCount() {
        return EXECUTION_COUNT.get();
    }

    public static java.util.List<String> getExecutionOrder() {
        return ConfirmableActionExecutionLog.getExecutions();
    }
}
