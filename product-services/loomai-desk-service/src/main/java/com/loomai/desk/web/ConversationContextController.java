package com.loomai.desk.web;

import static com.loomai.desk.model.ConversationContextContracts.*;
import static com.loomai.desk.model.DeskContracts.*;

import com.loomai.desk.service.DeskConversationContextService;
import com.loomai.desk.service.DeskDirectory;
import com.loomai.desk.service.DeskIdentityService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ConversationContextController {
  private final DeskConversationContextService contexts;
  private final DeskIdentityService identities;
  private final DeskDirectory directory;

  public ConversationContextController(
      DeskConversationContextService contexts,
      DeskIdentityService identities,
      DeskDirectory directory) {
    this.contexts = contexts;
    this.identities = identities;
    this.directory = directory;
  }

  @GetMapping("/api/desk/admin/v1/bindings/{bindingId}/conversation-context")
  public ProviderView configuration(@PathVariable String bindingId) {
    return contexts.configuration(bindingId);
  }

  @PutMapping("/api/desk/admin/v1/bindings/{bindingId}/conversation-context")
  public ProviderView configure(@PathVariable String bindingId, @RequestBody ProviderInput input) {
    return contexts.configure(bindingId, input);
  }

  @DeleteMapping("/api/desk/admin/v1/bindings/{bindingId}/conversation-context")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@PathVariable String bindingId) {
    contexts.remove(bindingId);
  }

  @GetMapping("/api/desk/v1/bindings/{bindingId}/conversations/{handoverId}/context")
  public ResponseEntity<ContextView> lookup(
      Authentication auth, @PathVariable String bindingId, @PathVariable String handoverId) {
    VerifiedIdentity identity = identities.require(auth);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(contexts.lookup(bindingId, handoverId, operator(identity), identity));
  }

  @PostMapping("/api/desk/v1/bindings/{bindingId}/conversations/{handoverId}/context/resolve")
  public ResponseEntity<ContextView> resolve(
      Authentication auth,
      @PathVariable String bindingId,
      @PathVariable String handoverId,
      @RequestBody ResolveInput input) {
    VerifiedIdentity identity = identities.require(auth);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(contexts.resolve(bindingId, handoverId, operator(identity), identity, input));
  }

  private Operator operator(VerifiedIdentity identity) {
    Operator operator = directory.find(identity);
    if (operator == null)
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Register your Desk agent profile first.");
    return operator;
  }
}
