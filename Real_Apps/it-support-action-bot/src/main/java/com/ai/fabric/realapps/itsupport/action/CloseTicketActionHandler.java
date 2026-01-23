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
public class CloseTicketActionHandler extends BaseActionHandler implements ActionHandler {

    private final TicketService ticketService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("close_ticket")
            .description("Close a ticket and add a resolution note")
            .category("it-support")
            .parameters(Map.of(
                "ticketNumber", "Numeric ticket number (ex: 1003)",
                "resolutionNote", "What fixed the issue (optional)"
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
        return "Close ticket " + ticketNumber + "?";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        long ticketNumber = requireTicketNumber(params);
        String note = stringParam(params, "resolutionNote");
        Ticket updated = ticketService.close(ticketNumber, note);
        return ActionResult.builder()
            .success(true)
            .message("Ticket closed")
            .data(Map.of(
                "ticketNumber", updated.getTicketNumber(),
                "status", updated.getStatus().name()
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Close ticket failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to close ticket: " + e.getMessage())
            .errorCode("CLOSE_TICKET_FAILED")
            .build();
    }
}
