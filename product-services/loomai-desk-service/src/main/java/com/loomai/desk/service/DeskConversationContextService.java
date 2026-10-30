package com.loomai.desk.service;

import static com.loomai.desk.model.ConversationContextContracts.*;
import static com.loomai.desk.model.DeskContracts.*;

import com.loomai.desk.channel.config.BasicInboxProperties;
import com.loomai.desk.channel.service.InboxSecurityService;
import com.loomai.desk.config.DeskProperties;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeskConversationContextService {
  private final JdbcTemplate jdbc;
  private final DeskDirectory directory;
  private final DeskChannelService channels;
  private final ConversationContextEndpointPolicy endpoints;
  private final ConversationContextClient client;
  private final DeskProperties properties;
  private final InboxSecurityService secrets;

  public DeskConversationContextService(
      JdbcTemplate jdbc,
      DeskDirectory directory,
      DeskChannelService channels,
      ConversationContextEndpointPolicy endpoints,
      ConversationContextClient client,
      DeskProperties properties) {
    this.jdbc = jdbc;
    this.directory = directory;
    this.channels = channels;
    this.endpoints = endpoints;
    this.client = client;
    this.properties = properties;
    BasicInboxProperties encryption = new BasicInboxProperties();
    encryption.setDataEncryptionSecret(properties.getDataEncryptionSecret());
    secrets = new InboxSecurityService(encryption);
  }

  public ProviderView configuration(String bindingId) {
    directory.binding(bindingId);
    Provider provider = provider(bindingId);
    return provider == null
        ? new ProviderView(false, null, false)
        : new ProviderView(true, provider.providerUrl(), provider.resolveEnabled());
  }

  @Transactional
  public ProviderView configure(String bindingId, ProviderInput input) {
    Binding binding = directory.binding(bindingId);
    if (input == null) throw badRequest("Context provider configuration is required.");
    endpoints.requireEndpoint(input.providerUrl());
    String key = input.serviceKey();
    if (key == null
        || key.length() < 32
        || key.length() > 4096
        || key.chars().anyMatch(Character::isISOControl)
        || key.equals(properties.getServiceKey())
        || key.equals(properties.getDataEncryptionSecret())
        || key.equals(binding.providerApiKey())
        || key.equals(binding.callbackSigningSecret()))
      throw badRequest("Use a distinct 32 to 4096 character context service key.");
    jdbc.queryForObject(
        "SELECT binding_id FROM desk_binding WHERE binding_id=? FOR UPDATE",
        String.class,
        bindingId);
    jdbc.update("DELETE FROM desk_context_provider WHERE binding_id=?", bindingId);
    jdbc.update(
        "INSERT INTO"
            + " desk_context_provider(binding_id,provider_url,protected_service_key,resolve_enabled)"
            + " VALUES(?,?,?,?)",
        bindingId,
        input.providerUrl(),
        secrets.encrypt(key),
        input.resolveEnabled());
    directory.audit(
        bindingId, binding.customerId(), "PLATFORM", "CONTEXT_PROVIDER_CONFIGURE", bindingId);
    return configuration(bindingId);
  }

  @Transactional
  public void remove(String bindingId) {
    Binding binding = directory.binding(bindingId);
    jdbc.queryForObject(
        "SELECT binding_id FROM desk_binding WHERE binding_id=? FOR UPDATE",
        String.class,
        bindingId);
    jdbc.update("DELETE FROM desk_context_provider WHERE binding_id=?", bindingId);
    directory.audit(
        bindingId, binding.customerId(), "PLATFORM", "CONTEXT_PROVIDER_REMOVE", bindingId);
  }

  public ContextView lookup(
      String bindingId, String handoverId, Operator operator, VerifiedIdentity identity) {
    return invoke(bindingId, handoverId, operator, identity, null);
  }

  public ContextView resolve(
      String bindingId,
      String handoverId,
      Operator operator,
      VerifiedIdentity identity,
      ResolveInput command) {
    if (command == null
        || command.idempotencyKey() == null
        || command.idempotencyKey().isBlank()
        || command.idempotencyKey().length() > 160
        || command.expectedVersion() == null
        || command.expectedVersion() < 0)
      throw badRequest("A stable idempotency key and current context version are required.");
    return invoke(bindingId, handoverId, operator, identity, command);
  }

  private ContextView invoke(
      String bindingId,
      String handoverId,
      Operator operator,
      VerifiedIdentity identity,
      ResolveInput command) {
    Binding binding = directory.authorize(bindingId, operator, Role.SUPPORT);
    Operator actual = identity == null ? null : directory.find(identity);
    if (actual == null
        || !actual.id().equals(operator.id())
        || identity.accessToken() == null
        || identity.accessToken().isBlank())
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "The actual assigned agent identity is required.");
    // IDs and owner come from the established channel mapping, never intake or browser parameters.
    var conversation = channels.engine(binding).repository().requireConversation(handoverId);
    if (!binding.tenantId().equals(conversation.tenantId())
        || !binding.deploymentId().equals(conversation.deploymentId()))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Conversation scope does not match this binding.");
    Provider provider = provider(bindingId);
    if (provider == null) {
      if (command != null)
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "External context is not configured.");
      return new ContextView(State.NOT_CONFIGURED, null);
    }
    if (command != null && !provider.resolveEnabled())
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "External case resolution is not enabled.");
    Scope scope =
        new Scope(
            bindingId,
            binding.customerId(),
            binding.tenantId(),
            binding.deploymentId(),
            binding.targetProfileId(),
            handoverId,
            conversation.conversationId(),
            conversation.ownerRef(),
            conversation.externalConversationRef());
    ProviderRequest request =
        new ProviderRequest(
            1,
            command == null ? Operation.LOOKUP : Operation.RESOLVE,
            scope,
            new Actor(identity.issuer(), identity.subject(), operator.id()),
            command);
    if (command != null)
      directory.audit(
          bindingId,
          binding.customerId(),
          operator.id(),
          "EXTERNAL_CASE_RESOLVE_REQUEST",
          handoverId);
    ProviderResponse response = client.request(provider, identity.accessToken(), request);
    // A revoked assignment or a changed/removed provider must not expose a late lookup result.
    directory.authorize(bindingId, operator, Role.SUPPORT);
    if (!Objects.equals(provider, provider(bindingId)))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Context provider changed; refresh the conversation.");
    ContextView view = project(response, scope, provider.resolveEnabled());
    if (command != null && view.state() != State.AVAILABLE)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "The external case is not available for resolution.");
    directory.audit(
        bindingId,
        binding.customerId(),
        operator.id(),
        command == null ? "EXTERNAL_CONTEXT_READ" : "EXTERNAL_CASE_RESOLVE",
        handoverId);
    return view;
  }

  private Provider provider(String bindingId) {
    return jdbc
        .query(
            "SELECT * FROM desk_context_provider WHERE binding_id=?",
            (rs, n) ->
                new Provider(
                    rs.getString("provider_url"),
                    secrets.decrypt(rs.getString("protected_service_key")),
                    rs.getBoolean("resolve_enabled")),
            bindingId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private ContextView project(ProviderResponse response, Scope scope, boolean resolveEnabled) {
    if (response == null
        || response.schemaVersion() != 1
        || !scope.equals(response.scope())
        || response.state() == null
        || response.state() == State.NOT_CONFIGURED) throw invalid();
    if (response.state() == State.NOT_LINKED) {
      if (response.context() != null) throw invalid();
      return new ContextView(State.NOT_LINKED, null);
    }
    ExternalContext c = response.context();
    if (c == null || c.version() == null || c.version() < 0) throw invalid();
    text(c.reference(), 160, true);
    text(c.title(), 240, true);
    text(c.status(), 64, true);
    text(c.accountLabel(), 160, false);
    if (c.resourceLabels() == null
        || c.resourceLabels().size() > 12
        || c.observations() == null
        || c.observations().size() > 12) throw invalid();
    for (String label : c.resourceLabels()) text(label, 160, true);
    Consent consent = c.consent();
    if (consent != null) {
      if (consent.status() == null || consent.updatedAt() == null) throw invalid();
      text(consent.summary(), 600, false);
    }
    for (Observation o : c.observations()) {
      if (o == null || o.observedAt() == null) throw invalid();
      text(o.label(), 160, true);
      text(o.status(), 64, true);
      text(o.summary(), 1000, true);
    }
    ResolveAction action = resolveEnabled ? c.resolveAction() : null;
    if (action != null) {
      text(action.label(), 80, true);
      text(action.confirmation(), 300, true);
    }
    return new ContextView(
        State.AVAILABLE,
        new ExternalContext(
            c.reference(),
            c.title(),
            c.status(),
            c.version(),
            c.accountLabel(),
            List.copyOf(c.resourceLabels()),
            consent,
            List.copyOf(c.observations()),
            resolveEnabled ? action : null));
  }

  private void text(String value, int max, boolean required) {
    if (value == null
        ? required
        : value.length() > max
            || required && value.isBlank()
            || value.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'))
      throw invalid();
  }

  private ResponseStatusException invalid() {
    return new ResponseStatusException(
        HttpStatus.BAD_GATEWAY, "The context provider returned an invalid projection.");
  }

  private ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
