package com.loomai.desk.web;

import static com.loomai.desk.channel.model.InboxContracts.*;
import static com.loomai.desk.model.DeskContracts.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.loomai.desk.config.DeskProperties;
import com.loomai.desk.service.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/desk/v1")
public class DeskController {
  private final DeskProperties properties;
  private final DeskDirectory directory;
  private final DeskIdentityService identities;
  private final DeskChannelService channels;
  private final DeskReviewService reviews;

  public DeskController(
      DeskProperties properties,
      DeskDirectory directory,
      DeskIdentityService identities,
      DeskChannelService channels,
      DeskReviewService reviews) {
    this.properties = properties;
    this.directory = directory;
    this.identities = identities;
    this.channels = channels;
    this.reviews = reviews;
  }

  @GetMapping("/config")
  public Map<String, Object> config() {
    return Map.of(
        "supabaseUrl",
        properties.getSupabaseUrl(),
        "supabasePublishableKey",
        safePublicKey(),
        "issuer",
        properties.getIssuer(),
        "loginUrl",
        "/oauth2/authorization/supabase",
        "registrationEnabled",
        true,
        "oidcEnabled",
        properties.getClientId() != null && !properties.getClientId().isBlank());
  }

  @GetMapping("/session")
  public SessionView session(Authentication auth, HttpServletRequest request) {
    CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (auth == null)
      return new SessionView(false, null, List.of(), csrf.getToken(), csrf.getHeaderName());
    VerifiedIdentity identity = identities.require(auth);
    Operator operator = directory.find(identity);
    List<BindingView> bindings = operator == null ? List.of() : directory.bindings(operator);
    if (bindings.stream().anyMatch(b -> b.roles().contains(Role.OWNER)))
      operator = directory.register(identity);
    return new SessionView(true, operator, bindings, csrf.getToken(), csrf.getHeaderName());
  }

  @PostMapping("/agents/register")
  public Operator register(
      Authentication auth, @RequestBody(required = false) RegistrationInput input) {
    VerifiedIdentity identity = identities.require(auth);
    if (input != null && input.displayName() != null) {
      String displayName = input.displayName().trim();
      if (displayName.isEmpty() || displayName.length() > 160) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Display name must contain 1 to 160 characters.");
      }
      identity =
          new VerifiedIdentity(
              identity.issuer(),
              identity.subject(),
              identity.email(),
              displayName,
              identity.emailVerified(),
              identity.accessToken());
    }
    return directory.register(identity);
  }

  private Operator operator(Authentication auth) {
    Operator operator = directory.find(identities.require(auth));
    if (operator == null)
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Register your Desk agent profile first.");
    return operator;
  }

  @GetMapping("/bindings/{bindingId}/conversations")
  public List<ConversationSummary> queue(
      Authentication auth,
      @PathVariable String bindingId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "100") int limit) {
    Operator o = operator(auth);
    return channels.support(
        bindingId,
        o,
        null,
        null,
        s -> s.queue(channels.channelOperator(o, bindingId), status, limit));
  }

  @GetMapping("/bindings/{bindingId}/conversations/{handoverId}")
  public ConversationDetail detail(
      Authentication auth, @PathVariable String bindingId, @PathVariable String handoverId) {
    Operator o = operator(auth);
    return channels.support(
        bindingId,
        o,
        handoverId,
        null,
        s -> s.detail(channels.channelOperator(o, bindingId), handoverId));
  }

  @PostMapping("/bindings/{bindingId}/conversations/{handoverId}/claim")
  public OperatorCommandResult claim(
      Authentication auth,
      @PathVariable String bindingId,
      @PathVariable String handoverId,
      @RequestBody OperatorCommandRequest request) {
    Operator o = operator(auth);
    return channels.support(
        bindingId,
        o,
        handoverId,
        "CONVERSATION_CLAIM",
        s -> s.claim(channels.channelOperator(o, bindingId), handoverId, request.idempotencyKey()));
  }

  @PostMapping("/bindings/{bindingId}/conversations/{handoverId}/messages")
  public OperatorCommandResult reply(
      Authentication auth,
      @PathVariable String bindingId,
      @PathVariable String handoverId,
      @RequestBody ReplyRequest request) {
    Operator o = operator(auth);
    return channels.support(
        bindingId,
        o,
        handoverId,
        "CONVERSATION_REPLY",
        s -> s.reply(channels.channelOperator(o, bindingId), handoverId, request));
  }

  @PostMapping("/bindings/{bindingId}/conversations/{handoverId}/return-to-ai")
  public OperatorCommandResult release(
      Authentication auth,
      @PathVariable String bindingId,
      @PathVariable String handoverId,
      @RequestBody OperatorCommandRequest request) {
    Operator o = operator(auth);
    return channels.support(
        bindingId,
        o,
        handoverId,
        "CONVERSATION_RETURN",
        s ->
            s.returnToAi(
                channels.channelOperator(o, bindingId), handoverId, request.idempotencyKey()));
  }

  @PostMapping("/bindings/{bindingId}/conversations/{handoverId}/close")
  public OperatorCommandResult close(
      Authentication auth,
      @PathVariable String bindingId,
      @PathVariable String handoverId,
      @RequestBody OperatorCommandRequest request) {
    Operator o = operator(auth);
    return channels.support(
        bindingId,
        o,
        handoverId,
        "CONVERSATION_CLOSE",
        s -> s.close(channels.channelOperator(o, bindingId), handoverId, request.idempotencyKey()));
  }

  @GetMapping("/bindings/{bindingId}/operations/callbacks")
  public List<CallbackOperationView> callbacks(
      Authentication auth,
      @PathVariable String bindingId,
      @RequestParam(defaultValue = "100") int limit) {
    Operator o = operator(auth);
    return channels.support(
        bindingId,
        o,
        null,
        null,
        s -> s.callbackOperations(channels.channelOperator(o, bindingId), limit));
  }

  @PostMapping("/bindings/{bindingId}/operations/callbacks/{callbackId}/retry")
  public CallbackRecoveryResult retry(
      Authentication auth,
      @PathVariable String bindingId,
      @PathVariable String callbackId,
      @RequestBody OperatorCommandRequest request) {
    Operator o = operator(auth);
    return channels.support(
        bindingId,
        o,
        callbackId,
        "CALLBACK_RETRY",
        s ->
            s.retryCallback(
                channels.channelOperator(o, bindingId), callbackId, request.idempotencyKey()));
  }

  @GetMapping("/bindings/{bindingId}/agents")
  public List<AgentView> agents(Authentication auth, @PathVariable String bindingId) {
    directory.authorize(bindingId, operator(auth), Role.OWNER);
    return directory.agents(bindingId);
  }

  @PostMapping("/bindings/{bindingId}/agents")
  public AgentView assign(
      Authentication auth, @PathVariable String bindingId, @RequestBody AssignmentInput request) {
    return directory.assign(bindingId, operator(auth), request);
  }

  @DeleteMapping("/bindings/{bindingId}/agents/{agentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(
      Authentication auth, @PathVariable String bindingId, @PathVariable String agentId) {
    directory.revoke(bindingId, operator(auth), agentId);
  }

  @GetMapping("/bindings/{bindingId}/reviews")
  public ResponseEntity<JsonNode> reviews(
      Authentication auth,
      @PathVariable String bindingId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "100") int limit) {
    String query =
        "?limit="
            + Math.max(1, Math.min(200, limit))
            + (status == null || status.isBlank()
                ? ""
                : "&status=" + DeskReviewService.segment(status));
    return reviews.request(bindingId, operator(auth), identities.require(auth), query, null);
  }

  @GetMapping("/bindings/{bindingId}/reviews/{taskId}")
  public ResponseEntity<JsonNode> review(
      Authentication auth, @PathVariable String bindingId, @PathVariable String taskId) {
    return reviews.request(
        bindingId,
        operator(auth),
        identities.require(auth),
        "/" + DeskReviewService.segment(taskId),
        null);
  }

  @PostMapping("/bindings/{bindingId}/reviews/{taskId}/decisions")
  public ResponseEntity<JsonNode> decide(
      Authentication auth,
      @PathVariable String bindingId,
      @PathVariable String taskId,
      @RequestBody DecisionInput input) {
    if (input.decisionId() == null
        || input.decisionId().isBlank()
        || input.decisionId().length() > 200
        || input.decision() == null
        || !List.of("APPROVE", "REJECT").contains(input.decision())
        || input.expectedVersion() < 0)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "A valid decision ID, APPROVE/REJECT and expected version are required.");
    return reviews.request(
        bindingId,
        operator(auth),
        identities.require(auth),
        "/" + DeskReviewService.segment(taskId) + "/decisions",
        input);
  }

  private String safePublicKey() {
    String key = properties.getSupabasePublishableKey();
    if (key == null || key.isBlank()) return "";
    if (key.startsWith("sb_publishable_")) return key;
    try {
      String[] parts = key.split("\\.");
      if (parts.length == 3) {
        com.fasterxml.jackson.databind.JsonNode body =
            new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(java.util.Base64.getUrlDecoder().decode(parts[1]));
        if ("anon".equals(body.path("role").asText())) return key;
      }
    } catch (Exception ignored) {
    }
    throw new IllegalStateException(
        "SUPABASE_PUBLISHABLE_KEY must be public publishable/anon, never a service key.");
  }
}
