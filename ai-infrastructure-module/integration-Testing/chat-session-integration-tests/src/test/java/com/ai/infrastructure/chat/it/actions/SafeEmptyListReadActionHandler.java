package com.ai.infrastructure.chat.it.actions;

import com.ai.infrastructure.intent.action.ActionAccessMode;
import com.ai.infrastructure.intent.action.ActionContext;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.ActionResultContracts;
import com.ai.infrastructure.intent.action.annotation.AIAction;
import com.ai.infrastructure.intent.action.annotation.ActionAllowed;
import com.ai.infrastructure.intent.action.annotation.ActionExecute;
import org.springframework.util.StringUtils;

import java.util.List;

@AIAction(
    name = SafeEmptyListReadActionHandler.ACTION_NAME,
    description = "Test-only safe READ action that returns an empty list payload.",
    category = "test",
    accessMode = ActionAccessMode.READ,
    requiresConfirmation = false
)
public class SafeEmptyListReadActionHandler {

    public static final String ACTION_NAME = "safe_empty_list_read";

    @ActionAllowed
    public boolean allowed(ActionContext context) {
        return context != null && StringUtils.hasText(context.identifier());
    }

    @ActionExecute
    public ActionResult execute() {
        return ActionResult.builder()
            .success(true)
            .message("No items")
            .data(ActionResultContracts.list(List.of()))
            .build();
    }
}

