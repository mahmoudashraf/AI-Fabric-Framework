package com.ai.infrastructure.chat.it.actions;

import com.ai.infrastructure.intent.action.ActionContext;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.annotation.AIAction;
import com.ai.infrastructure.intent.action.annotation.ActionAllowed;
import com.ai.infrastructure.intent.action.annotation.ActionConfirmation;
import com.ai.infrastructure.intent.action.annotation.ActionExecute;
import com.ai.infrastructure.intent.action.annotation.Param;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.util.StringUtils;

@AIAction(
    name = ConfirmableUpperEchoActionHandler.ACTION_NAME,
    description = "Test-only action that requires confirmation and uppercases the message.",
    category = "test",
    requiresConfirmation = true
)
public class ConfirmableUpperEchoActionHandler {

    public static final String ACTION_NAME = "confirmable_upper_echo";

    private static final AtomicInteger EXECUTION_COUNT = new AtomicInteger(0);

    @ActionAllowed
    public boolean allowed(ActionContext context) {
        return context != null && StringUtils.hasText(context.identifier());
    }

    @ActionConfirmation
    public String confirm(@Param(value = "message", required = true, description = "Text to uppercase") String message) {
        return "Confirm execute confirmable_upper_echo(message=" + message + ")?";
    }

    @ActionExecute
    public ActionResult execute(@Param(value = "message", required = true, description = "Text to uppercase") String message) {
        EXECUTION_COUNT.incrementAndGet();
        ConfirmableActionExecutionLog.record(ACTION_NAME);

        String upper = StringUtils.hasText(message) ? message.toUpperCase(Locale.ROOT) : "OK";
        return ActionResult.builder()
            .success(true)
            .message("Upper: " + upper)
            .data(Map.of("upper", upper))
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
