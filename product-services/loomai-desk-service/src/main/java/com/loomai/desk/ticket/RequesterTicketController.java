package com.loomai.desk.ticket;

import static com.loomai.desk.ticket.TicketContracts.*;

import com.loomai.desk.service.DeskDirectory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/desk/requester/v1/spaces/{spaceId}")
public class RequesterTicketController {
  private final SupportTickets tickets;
  private final DeskDirectory directory;

  public RequesterTicketController(SupportTickets tickets, DeskDirectory directory) {
    this.tickets = tickets;
    this.directory = directory;
  }

  static Space space(HttpServletRequest r) {
    return (Space) r.getAttribute("ticket.space");
  }

  static Requester requester(HttpServletRequest r) {
    return (Requester) r.getAttribute("ticket.requester");
  }

  static RuntimeCredential credential(HttpServletRequest r) {
    return new RuntimeCredential(
        r.getHeader("X-LoomAI-Runtime-Authorization"), r.getHeader("X-LoomAI-Runtime-Api-Key"));
  }

  @GetMapping("/session")
  public Session session(HttpServletRequest r) {
    Space s = space(r);
    return new Session(true, requester(r).id(), s.view(), s.view().capabilities());
  }

  @GetMapping("/team")
  public List<com.loomai.desk.model.DeskContracts.SupportTeamProfile> team(HttpServletRequest r) {
    Space s = space(r);
    return s.view().humanHandoverEnabled()
        ? directory.supportTeam(s.view().bindingId())
        : List.of();
  }

  @GetMapping("/team/{profileRef}/photo")
  public ResponseEntity<byte[]> teamPhoto(HttpServletRequest r, @PathVariable String profileRef) {
    Space s = space(r);
    if (!s.view().humanHandoverEnabled()) throw TicketException.missing();
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_JPEG)
        .header("Cache-Control", "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(directory.supportTeamPhoto(s.view().bindingId(), profileRef));
  }

  @PostMapping("/requests")
  @ResponseStatus(HttpStatus.CREATED)
  public Ticket create(
      HttpServletRequest r,
      @RequestBody CreateInput in,
      @RequestHeader("Idempotency-Key") String key) {
    return tickets.create(space(r), requester(r), in, key, credential(r));
  }

  @GetMapping("/requests")
  public Page list(
      HttpServletRequest r,
      @RequestParam(required = false) Status status,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int limit) {
    return tickets.list(space(r), requester(r), status, cursor, limit);
  }

  @GetMapping("/requests/{id}")
  public Ticket get(HttpServletRequest r, @PathVariable String id) {
    return tickets.get(space(r), requester(r), id);
  }

  @PostMapping("/requests/{id}/start")
  public Ticket start(
      HttpServletRequest r,
      @PathVariable String id,
      @RequestBody VersionInput in,
      @RequestHeader("Idempotency-Key") String key) {
    return tickets.start(space(r), requester(r), id, in, key, credential(r));
  }

  @PostMapping("/requests/{id}/link")
  public Ticket link(
      HttpServletRequest r,
      @PathVariable String id,
      @RequestBody LinkInput in,
      @RequestHeader("Idempotency-Key") String key) {
    return tickets.link(space(r), requester(r), id, in, key, credential(r));
  }

  @PostMapping("/requests/{id}/episodes")
  public Ticket episode(
      HttpServletRequest r,
      @PathVariable String id,
      @RequestBody VersionInput in,
      @RequestHeader("Idempotency-Key") String key) {
    return tickets.newEpisode(space(r), requester(r), id, in, key, credential(r));
  }

  @PostMapping("/requests/{id}/{action:resolve|cancel|reopen}")
  public Ticket change(
      HttpServletRequest r,
      @PathVariable String id,
      @PathVariable String action,
      @RequestBody VersionInput in,
      @RequestHeader("Idempotency-Key") String key) {
    return tickets.change(
        space(r), requester(r), id, action.toUpperCase(java.util.Locale.ROOT), in, key);
  }

  @PutMapping("/requests/{id}/seen")
  public Ticket seen(
      HttpServletRequest r,
      @PathVariable String id,
      @RequestBody SeenInput in,
      @RequestHeader("Idempotency-Key") String key) {
    return tickets.seen(space(r), requester(r), id, in, key);
  }

  @PostMapping("/requests/{id}/activity")
  public Ticket activity(
      HttpServletRequest r,
      @PathVariable String id,
      @RequestBody VersionInput in,
      @RequestHeader("Idempotency-Key") String key) {
    return tickets.activity(space(r), requester(r), id, in, key, credential(r));
  }
}
