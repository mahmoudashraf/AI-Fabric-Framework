package com.loomai.desk.ticket;

import static com.loomai.desk.ticket.TicketContracts.*;

import com.loomai.desk.model.DeskContracts.Operator;
import com.loomai.desk.service.DeskDirectory;
import com.loomai.desk.service.DeskIdentityService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/desk/v1/bindings/{bindingId}/tickets")
public class AgentTicketController {
  private final SupportTickets tickets;
  private final DeskDirectory directory;
  private final DeskIdentityService identities;

  public AgentTicketController(
      SupportTickets tickets, DeskDirectory directory, DeskIdentityService identities) {
    this.tickets = tickets;
    this.directory = directory;
    this.identities = identities;
  }

  private Operator operator(Authentication auth) {
    return directory.find(identities.require(auth));
  }

  @GetMapping
  public Page list(
      @PathVariable String bindingId,
      Authentication auth,
      @RequestParam(required = false) Status status,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int limit) {
    return tickets.agentList(bindingId, operator(auth), status, cursor, limit);
  }

  @GetMapping("/{id}")
  public Ticket get(@PathVariable String bindingId, @PathVariable String id, Authentication auth) {
    return tickets.agentGet(bindingId, operator(auth), id);
  }

  @PostMapping("/{id}/resolve")
  public Ticket resolve(
      @PathVariable String bindingId,
      @PathVariable String id,
      Authentication auth,
      @RequestBody VersionInput in,
      @RequestHeader("Idempotency-Key") String key) {
    return tickets.agentResolve(bindingId, operator(auth), id, in, key);
  }
}
