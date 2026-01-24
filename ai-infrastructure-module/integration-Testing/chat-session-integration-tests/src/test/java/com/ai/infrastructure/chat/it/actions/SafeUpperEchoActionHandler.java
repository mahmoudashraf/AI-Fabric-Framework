package com.ai.infrastructure.chat.it.actions;

import com.ai.infrastructure.intent.action.ActionContext;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.annotation.AIAction;
import com.ai.infrastructure.intent.action.annotation.ActionAllowed;
import com.ai.infrastructure.intent.action.annotation.ActionExecute;
import com.ai.infrastructure.intent.action.annotation.Param;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

@AIAction(
    name = SafeUpperEchoActionHandler.ACTION_NAME,
    description = "Test-only safe action that echoes an upper-cased message. No side effects.",
    category = "test",
    requiresConfirmation = false
)
public class SafeUpperEchoActionHandler {

    public static final String ACTION_NAME = "safe_upper_echo";

    @ActionAllowed
    public boolean allowed(ActionContext context) {
        return context != null && StringUtils.hasText(context.identifier());
    }

    @ActionExecute
    public ActionResult execute(@Param(value = "message", description = "Text to echo back upper-cased") String message) {
        String echoed = StringUtils.hasText(message) ? message.toUpperCase(Locale.ROOT) : "OK";
        return ActionResult.builder()
            .success(true)
            .message("Upper Echo: " + echoed)
            .data(Map.of("echo", echoed))
            .build();
    }
}
