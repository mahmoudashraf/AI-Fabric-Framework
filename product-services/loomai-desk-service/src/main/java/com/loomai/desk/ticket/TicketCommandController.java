package com.loomai.desk.ticket;

import static com.loomai.desk.ticket.RequesterTicketController.*;
import static com.loomai.desk.ticket.TicketContracts.*;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/desk/integrations/v1/spaces/{spaceId}")
public class TicketCommandController {
  private final SupportTickets tickets;

  public TicketCommandController(SupportTickets tickets) {
    this.tickets = tickets;
  }

  @PostMapping("/ticket-commands")
  public Object command(
      HttpServletRequest r,
      @RequestBody CommandInput in,
      @RequestHeader("Idempotency-Key") String key) {
    return execute(r, in, key, credential(r));
  }

  @PostMapping("/server-ticket-commands")
  public Object serverCommand(
      HttpServletRequest r,
      @RequestBody CommandInput in,
      @RequestHeader("Idempotency-Key") String key) {
    Space s = space(r);
    Requester owner = requester(r);
    if (in.operation() == null) throw TicketException.invalid("operation");
    if ("CREATE".equals(in.operation())) {
      var proof = (VerifiedRuntimeTrace) r.getAttribute("ticket.runtimeTrace");
      return tickets.createFromRuntimeTrace(
          s,
          owner,
          new CreateInput(in.subject(), in.details(), in.topic(), owner.runtimeConversationId()),
          key,
          proof);
    }
    if (!java.util.Set.of("GET", "LIST").contains(in.operation()))
      throw TicketException.unsupported();
    return execute(r, in, key, null);
  }

  private Object execute(
      HttpServletRequest r, CommandInput in, String key, RuntimeCredential proof) {
    Space s = space(r);
    Requester requester = requester(r);
    if (in.operation() == null) throw TicketException.invalid("operation");
    if (s.identity().mode() == IdentityMode.TRUSTED_PRIVATE_HOST
        && !requester.operations().contains(in.operation()))
      throw new TicketException(
          403, "REQUESTER_OPERATION_DENIED", "The caller does not permit this ticket operation.");
    return switch (in.operation()) {
      case "CREATE" -> {
        if (in.conversationId() == null)
          throw TicketException.invalid("persisted conversationId for action origin");
        yield tickets.create(
            s,
            requester,
            new CreateInput(in.subject(), in.details(), in.topic(), in.conversationId()),
            key,
            proof);
      }
      case "LIST" -> tickets.list(s, requester, null, null, 20);
      case "GET" -> tickets.get(s, requester, in.requestId());
      case "LINK_CONVERSATION" ->
          tickets.link(
              s,
              requester,
              in.requestId(),
              new LinkInput(in.expectedVersion(), in.conversationId()),
              key,
              proof);
      default -> throw TicketException.unsupported();
    };
  }
}
