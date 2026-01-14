package com.ai.fabric.realapps.itsupport.action;

import com.ai.fabric.realapps.itsupport.domain.Ticket;
import com.ai.fabric.realapps.itsupport.repo.TicketRepository;
import com.ai.fabric.realapps.itsupport.service.TicketService;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreateTicketActionHandler extends BaseActionHandler implements ActionHandler {

    private final TicketService ticketService;
    private final TicketRepository ticketRepository;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("create_ticket")
            .description("Create a new IT support ticket")
            .category("it-support")
            .parameters(Map.of(
                "title", "Short title for the issue",
                "description", "Longer description (optional)",
                "priority", "LOW|MEDIUM|HIGH|URGENT (optional)"
            ))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return userId != null && !userId.isBlank();
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        String title = stringParam(params, "title");
        return title != null ? "Create a new ticket: " + title + "?" : "Create a new ticket?";
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String title = stringParam(params, "title");
        if (title == null || title.isBlank()) {
            return ActionResult.builder().success(false).message("title is required").build();
        }

        String description = stringParam(params, "description");
        Ticket.Priority priority = parsePriority(stringParam(params, "priority"));

        long ticketNumber = nextTicketNumber();
        Ticket created = ticketService.createTicket(ticketNumber, title, description, priority);

        return ActionResult.builder()
            .success(true)
            .message("Ticket created")
            .data(Map.of(
                "ticketNumber", created.getTicketNumber(),
                "status", created.getStatus().name(),
                "priority", created.getPriority().name()
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Create ticket failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to create ticket: " + e.getMessage())
            .errorCode("CREATE_TICKET_FAILED")
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

    private long nextTicketNumber() {
        long base = 1000L + ThreadLocalRandom.current().nextLong(1, 9000);
        for (int attempt = 0; attempt < 25; attempt++) {
            long candidate = base + attempt;
            if (!ticketRepository.existsByTicketNumber(candidate)) {
                return candidate;
            }
        }
        // fallback (rare)
        long candidate;
        do {
            candidate = 1000L + ThreadLocalRandom.current().nextLong(1, 9000);
        } while (ticketRepository.existsByTicketNumber(candidate));
        return candidate;
    }
}

