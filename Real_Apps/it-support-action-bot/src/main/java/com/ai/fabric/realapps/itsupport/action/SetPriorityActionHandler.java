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
public class SetPriorityActionHandler extends BaseActionHandler implements ActionHandler {

    private final TicketService ticketService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("set_ticket_priority")
            .description("Change the priority of a ticket")
            .category("it-support")
            .parameters(Map.of(
                "ticketNumber", "Numeric ticket number (ex: 1001)",
                "priority", "LOW|MEDIUM|HIGH|URGENT"
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
        return "Update priority for ticket " + ticketNumber + "?";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        long ticketNumber = requireTicketNumber(params);
        Ticket.Priority priority = parsePriority(stringParam(params, "priority"));
        Ticket updated = ticketService.setPriority(ticketNumber, priority);
        return ActionResult.builder()
            .success(true)
            .message("Ticket priority updated")
            .data(Map.of(
                "ticketNumber", updated.getTicketNumber(),
                "priority", updated.getPriority().name()
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Set priority failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to set priority: " + e.getMessage())
            .errorCode("SET_PRIORITY_FAILED")
            .build();
    }

    private Ticket.Priority parsePriority(String raw) {
        if (raw == null || raw.isBlank()) {
            return Ticket.Priority.MEDIUM;
        }
        try {
            return Ticket.Priority.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Ticket.Priority.MEDIUM;
        }
    }
}
