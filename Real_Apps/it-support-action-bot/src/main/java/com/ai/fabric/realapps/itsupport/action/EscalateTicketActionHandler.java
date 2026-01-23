package com.ai.fabric.realapps.itsupport.action;

import com.ai.fabric.realapps.itsupport.domain.Ticket;
import com.ai.fabric.realapps.itsupport.service.TicketService;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EscalateTicketActionHandler extends BaseActionHandler implements ActionHandler {

    private final TicketService ticketService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("escalate_ticket")
            .description("Escalate a ticket to an on-call escalation path")
            .category("it-support")
            .parameters(Map.of(
                "ticketNumber", "Numeric ticket number (ex: 1002)",
                "reason", "Why escalation is needed (optional)"
            ))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return userId != null && !userId.isBlank();
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        long ticketNumber = requireTicketNumber(params);
        return "Escalate ticket " + ticketNumber + "?";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        long ticketNumber = requireTicketNumber(params);
        String reason = stringParam(params, "reason");
        Ticket updated = ticketService.escalate(ticketNumber, reason);
        return ActionResult.builder()
            .success(true)
            .message("Ticket escalated")
            .data(Map.of(
                "ticketNumber", updated.getTicketNumber(),
                "escalated", updated.isEscalated()
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Escalate failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to escalate ticket: " + e.getMessage())
            .errorCode("ESCALATE_FAILED")
            .build();
    }
}
