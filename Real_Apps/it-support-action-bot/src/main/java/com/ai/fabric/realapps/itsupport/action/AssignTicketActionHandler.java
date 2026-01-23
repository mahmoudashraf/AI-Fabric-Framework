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
public class AssignTicketActionHandler extends BaseActionHandler implements ActionHandler {

    private final TicketService ticketService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("assign_ticket")
            .description("Assign a ticket to an agent and mark it IN_PROGRESS")
            .category("it-support")
            .parameters(Map.of(
                "ticketNumber", "Numeric ticket number (ex: 1001)",
                "assigneeUsername", "Agent username (ex: agent_maya)"
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
        String assignee = stringParam(params, "assigneeUsername");
        return "Assign ticket " + ticketNumber + " to " + assignee + "?";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        long ticketNumber = requireTicketNumber(params);
        String assignee = stringParam(params, "assigneeUsername");
        Ticket updated = ticketService.assign(ticketNumber, assignee);
        return ActionResult.builder()
            .success(true)
            .message("Ticket assigned")
            .data(Map.of(
                "ticketNumber", updated.getTicketNumber(),
                "assignedTo", updated.getAssignedTo(),
                "status", updated.getStatus().name()
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Assign ticket failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to assign ticket: " + e.getMessage())
            .errorCode("ASSIGN_TICKET_FAILED")
            .build();
    }
}
