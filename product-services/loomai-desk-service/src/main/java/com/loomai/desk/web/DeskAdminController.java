package com.loomai.desk.web;

import static com.loomai.desk.model.DeskContracts.*;

import com.loomai.desk.service.DeskDirectory;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/desk/admin/v1")
public class DeskAdminController {
  private final DeskDirectory directory;

  public DeskAdminController(DeskDirectory directory) {
    this.directory = directory;
  }

  @PutMapping("/workspaces/{customerId}")
  public Map<String, Object> workspace(
      @PathVariable String customerId, @RequestBody WorkspaceInput input) {
    directory.workspace(customerId, input);
    return Map.of("customerId", customerId, "name", input.name());
  }

  @PutMapping("/bindings/{bindingId}")
  public BindingView binding(@PathVariable String bindingId, @RequestBody BindingInput input) {
    return directory.putBinding(bindingId, input);
  }

  @GetMapping("/bindings/{bindingId}")
  public Map<String, Object> binding(@PathVariable String bindingId) {
    Binding b = directory.binding(bindingId);
    return Map.of(
        "binding",
        directory.view(b, List.of()),
        "enabled",
        b.enabled(),
        "reviewEnabled",
        b.reviewBridgeUrl() != null);
  }

  @GetMapping("/bindings/{bindingId}/authorizations")
  public Map<String, Boolean> authorization(
      @PathVariable String bindingId,
      @RequestParam String issuer,
      @RequestParam String subject,
      @RequestParam Role permission) {
    return Map.of(
        "authorized", directory.authorizedIdentity(bindingId, issuer, subject, permission));
  }

  @GetMapping("/bindings/{bindingId}/support-team")
  public ResponseEntity<List<SupportTeamProfile>> supportTeam(@PathVariable String bindingId) {
    return ResponseEntity.ok().header("Cache-Control", "private, no-store")
        .body(directory.supportTeam(bindingId));
  }

  @GetMapping("/bindings/{bindingId}/support-team/{profileRef}/photo")
  public ResponseEntity<byte[]> supportTeamPhoto(
      @PathVariable String bindingId, @PathVariable String profileRef) {
    return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
        .header("Cache-Control", "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(directory.supportTeamPhoto(bindingId, profileRef));
  }
}
