package com.loomai.desk.ticket;

import static com.loomai.desk.ticket.TicketContracts.*;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/desk/admin/v1/bindings/{bindingId}")
public class SupportTicketAdminController {
  private final SupportSpaces spaces;
  private final SupportTickets tickets;

  public SupportTicketAdminController(SupportSpaces spaces, SupportTickets tickets) {
    this.spaces = spaces;
    this.tickets = tickets;
  }

  @PutMapping("/support-space")
  public SpaceView put(@PathVariable String bindingId, @RequestBody SupportSpaceInput in) {
    return spaces.put(bindingId, in);
  }

  @GetMapping("/support-space")
  public SpaceView get(@PathVariable String bindingId) {
    return spaces.forBinding(bindingId).view();
  }

  @PostMapping("/tickets/{id}/purge")
  public PurgeReceipt purge(
      @PathVariable String bindingId,
      @PathVariable String id,
      @RequestBody PurgeInput in,
      @RequestHeader("Idempotency-Key") String key) {
    return tickets.purge(bindingId, id, in, key);
  }
}
