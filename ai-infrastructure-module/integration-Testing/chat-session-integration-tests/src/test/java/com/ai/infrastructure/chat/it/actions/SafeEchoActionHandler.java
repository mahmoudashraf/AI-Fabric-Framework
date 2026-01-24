package com.ai.infrastructure.chat.it.actions;

import com.ai.infrastructure.intent.action.ActionContext;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.annotation.AIAction;
import com.ai.infrastructure.intent.action.annotation.ActionAllowed;
import com.ai.infrastructure.intent.action.annotation.ActionExecute;
import com.ai.infrastructure.intent.action.annotation.Param;
import org.springframework.util.StringUtils;

import java.util.Map;

@AIAction(
    name = SafeEchoActionHandler.ACTION_NAME,
    description = "Test-only safe action that echoes a message. No side effects.",
    category = "test",
    requiresConfirmation = false
)
public class SafeEchoActionHandler {

    public static final String ACTION_NAME = "safe_echo";

    @ActionAllowed
    public boolean allowed(ActionContext context) {
        return context != null && StringUtils.hasText(context.identifier());
    }

    @ActionExecute
    public ActionResult execute(@Param(value = "message", description = "Text to echo back") String message) {
        String echoed = StringUtils.hasText(message) ? message : "ok";
        return ActionResult.builder()
            .success(true)
            .message("Echo: " + echoed)
            .data(Map.of("echo", echoed))
            .build();
    }
}
