package com.ai.fabric.realapps.chat.returns.action;

import com.ai.fabric.realapps.chat.returns.domain.ReturnRequest;
import com.ai.fabric.realapps.chat.returns.service.ReturnRequestService;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreateReturnRequestActionHandler implements ActionHandler {

    private final ReturnRequestService returnRequestService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("create_return_request")
            .description("Create a return request (RMA) for an order (by order number or id)")
            .category("commerce")
            .parameters(Map.of(
                "orderNumber", "Order number (PO-...) or order id (required)",
                "reason", "Reason for return (required)"
            ))
            .requiredParameters(Set.of("orderNumber", "reason"))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        return "Create a return request for this order?";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String orderNumberOrId = requiredString(params, "orderNumber");
        String reason = requiredString(params, "reason");

        ReturnRequest created = returnRequestService.create(userId, orderNumberOrId, reason);

        return ActionResult.builder()
            .success(true)
            .message(created.isEligible() ? "Return request created" : "Return request created (not eligible)")
            .data(Map.of(
                "returnRequestId", created.getId(),
                "status", created.getStatus() != null ? created.getStatus().name() : null,
                "eligible", created.isEligible()
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Create return request failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to create return request: " + e.getMessage())
            .errorCode("CREATE_RETURN_REQUEST_FAILED")
            .build();
    }

    private String requiredString(Map<String, Object> params, String key) {
        String value = stringParam(params, key);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.trim();
    }

    private String stringParam(Map<String, Object> params, String key) {
        Object raw = params != null ? params.get(key) : null;
        return raw != null ? raw.toString() : null;
    }
}

