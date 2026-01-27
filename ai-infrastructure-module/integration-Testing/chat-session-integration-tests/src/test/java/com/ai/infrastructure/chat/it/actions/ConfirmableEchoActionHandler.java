package com.ai.infrastructure.chat.it.actions;

import com.ai.infrastructure.intent.action.ActionContext;
import com.ai.infrastructure.intent.action.ActionAccessMode;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.ActionResultContracts;
import com.ai.infrastructure.intent.action.annotation.AIAction;
import com.ai.infrastructure.intent.action.annotation.ActionAllowed;
import com.ai.infrastructure.intent.action.annotation.ActionConfirmation;
import com.ai.infrastructure.intent.action.annotation.ActionExecute;
import com.ai.infrastructure.intent.action.annotation.Param;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.util.StringUtils;

@AIAction(
    name = ConfirmableEchoActionHandler.ACTION_NAME,
    description = "Test-only action that requires confirmation and echoes a message.",
    category = "test",
    accessMode = ActionAccessMode.WRITE_ONLY,
    requiresConfirmation = true
)
public class ConfirmableEchoActionHandler {

    public static final String ACTION_NAME = "confirmable_echo";

    private static final AtomicInteger EXECUTION_COUNT = new AtomicInteger(0);

    @ActionAllowed
    public boolean allowed(ActionContext context) {
        return context != null && StringUtils.hasText(context.identifier());
    }

    @ActionConfirmation
    public String confirm(@Param(value = "message", required = true, description = "Text to echo back") String message) {
        return "Confirm execute confirmable_echo(message=" + message + ")?";
    }

    @ActionExecute
    public ActionResult execute(@Param(value = "message", required = true, description = "Text to echo back") String message) {
        EXECUTION_COUNT.incrementAndGet();
        ConfirmableActionExecutionLog.record(ACTION_NAME);

        String echoed = StringUtils.hasText(message) ? message : "ok";
        return ActionResult.builder()
            .success(true)
            .message("Echo: " + echoed)
            .data(ActionResultContracts.object(Map.of("echo", echoed)))
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
