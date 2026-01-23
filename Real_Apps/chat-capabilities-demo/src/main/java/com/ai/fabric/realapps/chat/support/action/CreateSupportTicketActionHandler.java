package com.ai.fabric.realapps.chat.support.action;

import com.ai.fabric.realapps.chat.support.domain.SupportTicket;
import com.ai.fabric.realapps.chat.support.service.SupportTicketService;
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
public class CreateSupportTicketActionHandler implements ActionHandler {

    private final SupportTicketService supportTicketService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("create_support_ticket")
            .description("Create a support ticket for an ecommerce issue")
            .category("support")
            .parameters(Map.of(
                "issueType", "Issue type (required, e.g., delivery, payment, refund, damaged_item)",
                "description", "Issue description (required)",
                "orderNumber", "Order number or id (optional)"
            ))
            .requiredParameters(Set.of("issueType", "description"))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        return "Create a support ticket?";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String issueType = requiredString(params, "issueType");
        String description = requiredString(params, "description");
        String orderNumber = stringParam(params, "orderNumber");

        SupportTicket created = supportTicketService.create(userId, issueType, description, orderNumber);

        return ActionResult.builder()
            .success(true)
            .message("Support ticket created")
            .data(Map.of(
                "ticketId", created.getId(),
                "status", created.getStatus() != null ? created.getStatus().name() : null,
                "issueType", created.getIssueType()
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Create support ticket failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to create support ticket: " + e.getMessage())
            .errorCode("CREATE_SUPPORT_TICKET_FAILED")
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

