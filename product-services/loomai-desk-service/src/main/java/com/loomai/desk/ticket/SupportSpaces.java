package com.loomai.desk.ticket;

import static com.loomai.desk.ticket.TicketContracts.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.desk.channel.config.BasicInboxProperties;
import com.loomai.desk.channel.service.InboxSecurityService;
import com.loomai.desk.config.DeskProperties;
import com.loomai.desk.service.DeskDirectory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportSpaces {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final InboxSecurityService secrets;
  private final DeskDirectory directory;
  private final DeskProperties properties;
  private final TicketEndpointPolicy endpoints;
  private final Map<String, TicketProvider> providers;

  public SupportSpaces(
      JdbcTemplate jdbc,
      ObjectMapper json,
      DeskProperties properties,
      DeskDirectory directory,
      TicketEndpointPolicy endpoints,
      List<TicketProvider> providers) {
    this.jdbc = jdbc;
    this.json = json;
    this.directory = directory;
    this.properties = properties;
    this.endpoints = endpoints;
    this.providers =
        providers.stream()
            .collect(Collectors.toUnmodifiableMap(TicketProvider::id, Function.identity()));
    BasicInboxProperties p = new BasicInboxProperties();
    p.setDataEncryptionSecret(properties.getDataEncryptionSecret());
    secrets = new InboxSecurityService(p);
  }

  @Transactional
  public SpaceView put(String bindingId, SupportSpaceInput in) {
    var binding = directory.binding(bindingId);
    text(in.spaceId(), 160, "spaceId");
    text(in.name(), 240, "name");
    text(in.providerBindingId(), 160, "providerBindingId");
    provider(in.providerId());
    endpoints.requireEndpoint(in.runtimeBaseUrl());
    if (!java.util.Set.of("X-API-KEY", "X-AIFABRIC-RUNTIME-API-KEY")
        .contains(in.runtimeTrustedKeyHeader()))
      throw TicketException.invalid("runtimeTrustedKeyHeader");
    if (!java.net.URI.create(in.runtimeBaseUrl()).getPath().isEmpty())
      throw TicketException.invalid("runtimeBaseUrl");
    IdentityInput identity = in.identity();
    if (identity == null || identity.mode() == null) throw TicketException.invalid("identity");
    endpoints.requireEndpoint(identity.issuer());
    endpoints.requireEndpoint(identity.jwksUri());
    text(identity.audience(), 240, "audience");
    text(identity.requesterNamespace(), 160, "requesterNamespace");
    if (identity.mode() == IdentityMode.TRUSTED_PRIVATE_HOST) secret(identity.privateServerKey());
    else if (identity.privateServerKey() != null) throw TicketException.invalid("privateServerKey");
    secret(in.connectorKey());
    if (java.util.Set.of(properties.getServiceKey(), properties.getDataEncryptionSecret())
            .contains(in.connectorKey())
        || identity.privateServerKey() != null
            && java.util.Set.of(properties.getServiceKey(), properties.getDataEncryptionSecret())
                .contains(identity.privateServerKey()))
      throw TicketException.invalid("distinct requester/connector/admin/encryption secrets");
    if (Objects.equals(in.connectorKey(), identity.privateServerKey())
        || Objects.equals(in.connectorKey(), binding.providerApiKey())
        || Objects.equals(in.connectorKey(), binding.callbackSigningSecret()))
      throw TicketException.invalid("distinct connector key");
    ServerConnectorInput connector = in.serverConnector();
    if (connector != null) {
      if (identity.mode() != IdentityMode.SUPABASE
          || !"PUBLIC_RUNTIME_AUTHENTICATED".equals(connector.authMode())
          || !"PUBLIC_BROWSER".equals(connector.callerType()))
        throw TicketException.invalid("serverConnector profile");
      endpoints.requireEndpoint(connector.issuer());
      text(connector.audience(), 240, "serverConnector audience");
      if (connector.requiredScopes() == null
          || connector.requiredScopes().size() > 16
          || !connector.requiredScopes().contains("chat:conversations"))
        throw TicketException.invalid("serverConnector requiredScopes");
      connector.requiredScopes().forEach(scope -> text(scope, 128, "serverConnector scope"));
    }
    if (in.closedRetentionDays() < 1 || in.closedRetentionDays() > 3650)
      throw TicketException.invalid("retention");
    if (in.allowedOrigins() == null
        || in.allowedOrigins().size() > 32
        || in.topics() == null
        || in.topics().size() > 50) throw TicketException.invalid("origins/topics");
    for (String origin : in.allowedOrigins()) {
      var uri = endpoints.requireEndpoint(origin);
      if (!uri.getPath().isEmpty()) throw TicketException.invalid("origin");
    }
    in.topics().forEach(t -> text(t, 100, "topic"));
    jdbc.queryForObject(
        "SELECT binding_id FROM desk_binding WHERE binding_id=? FOR UPDATE",
        String.class,
        bindingId);
    List<Space> old =
        jdbc.query(
            "SELECT * FROM desk_support_space WHERE binding_id=?", (r, n) -> read(r), bindingId);
    long version = 0;
    if (!old.isEmpty()) {
      Space s = old.getFirst();
      if (!s.view().spaceId().equals(in.spaceId()))
        throw TicketException.invalid("immutable spaceId");
      if (!s.view().version().equals(in.expectedVersion()))
        throw new TicketException(
            409, "SPACE_VERSION_CONFLICT", "Refresh support configuration.", s.view());
      if (s.identity().mode() != identity.mode()
          || !s.identity().issuer().equals(identity.issuer())
          || !s.identity().requesterNamespace().equals(identity.requesterNamespace()))
        throw TicketException.invalid("immutable identity namespace");
      version = Long.parseLong(s.view().version()) + 1;
      jdbc.update(
          "UPDATE desk_support_space SET"
              + " name=?,enabled=?,human_enabled=?,provider_id=?,provider_binding_id=?,runtime_base_url=?,runtime_trusted_key_header=?,protected_identity=?,protected_connector_key=?,protected_server_connector=?,origins_json=?,topics_json=?,retention_days=?,conversation_json=?,version=?"
              + " WHERE space_id=?",
          in.name(),
          in.enabled(),
          in.humanHandoverEnabled(),
          in.providerId(),
          in.providerBindingId(),
          in.runtimeBaseUrl(),
          in.runtimeTrustedKeyHeader(),
          secrets.encrypt(write(identity)),
          secrets.encrypt(in.connectorKey()),
          connector == null ? null : secrets.encrypt(write(connector)),
          write(in.allowedOrigins()),
          write(in.topics()),
          in.closedRetentionDays(),
          write(in.conversation()),
          version,
          in.spaceId());
    } else {
      if (in.expectedVersion() != null)
        throw new TicketException(
            409, "SPACE_VERSION_CONFLICT", "Support space does not exist yet.");
      jdbc.update(
          "INSERT INTO"
              + " desk_support_space(space_id,binding_id,name,enabled,human_enabled,provider_id,provider_binding_id,runtime_base_url,runtime_trusted_key_header,protected_identity,protected_connector_key,protected_server_connector,origins_json,topics_json,retention_days,conversation_json,version)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
          in.spaceId(),
          bindingId,
          in.name(),
          in.enabled(),
          in.humanHandoverEnabled(),
          in.providerId(),
          in.providerBindingId(),
          in.runtimeBaseUrl(),
          in.runtimeTrustedKeyHeader(),
          secrets.encrypt(write(identity)),
          secrets.encrypt(in.connectorKey()),
          connector == null ? null : secrets.encrypt(write(connector)),
          write(in.allowedOrigins()),
          write(in.topics()),
          in.closedRetentionDays(),
          write(in.conversation()),
          version);
    }
    directory.audit(
        bindingId, binding.customerId(), "PLATFORM", "SUPPORT_SPACE_SYNC", in.spaceId());
    return get(in.spaceId()).view();
  }

  public Space get(String id) {
    return jdbc
        .query("SELECT * FROM desk_support_space WHERE space_id=?", (r, n) -> read(r), id)
        .stream()
        .findFirst()
        .orElseThrow(TicketException::missing);
  }

  public Space forBinding(String bindingId) {
    return jdbc
        .query("SELECT * FROM desk_support_space WHERE binding_id=?", (r, n) -> read(r), bindingId)
        .stream()
        .findFirst()
        .orElseThrow(TicketException::missing);
  }

  public Space available(String id) {
    Space s = get(id);
    if (!directory.binding(s.view().bindingId()).enabled()) throw TicketException.missing();
    return s;
  }

  public com.loomai.desk.model.DeskContracts.Binding binding(Space space) {
    return directory.binding(space.view().bindingId());
  }

  public void lock(String id) {
    jdbc.queryForObject(
        "SELECT space_id FROM desk_support_space WHERE space_id=? FOR UPDATE", String.class, id);
  }

  JdbcTemplate jdbc() {
    return jdbc;
  }

  InboxSecurityService secrets() {
    return secrets;
  }

  public TicketProvider provider(String id) {
    TicketProvider provider = providers.get(id);
    if (provider == null)
      throw new TicketException(
          503, "TICKET_PROVIDER_UNAVAILABLE", "The configured ticket provider is unavailable.");
    return provider;
  }

  public Requester requester(Space space, String subject, java.util.Set<String> operations) {
    text(subject, 256, "subject");
    String id =
        "requester-"
            + secrets.sha256(
                write(
                    List.of(
                        space.view().spaceId(),
                        space.identity().requesterNamespace(),
                        space.identity().issuer(),
                        subject)));
    return new Requester(id, subject, operations, null, null);
  }

  public void register(Space space, Requester requester) {
    jdbc.update(
        "INSERT INTO desk_support_requester(space_id,requester_id) SELECT ?,? WHERE NOT"
            + " EXISTS(SELECT 1 FROM desk_support_requester WHERE space_id=? AND requester_id=?)",
        space.view().spaceId(),
        requester.id(),
        space.view().spaceId(),
        requester.id());
  }

  public String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Ticket serialization failed.");
    }
  }

  public <T> T readJson(String value, Class<T> type) {
    try {
      return json.readValue(value, type);
    } catch (Exception e) {
      throw new IllegalStateException("Ticket storage is invalid.");
    }
  }

  private List<String> strings(String value) {
    try {
      return json.readValue(value, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Ticket configuration is invalid.");
    }
  }

  private Space read(ResultSet r) throws SQLException {
    IdentityInput i =
        readJson(secrets.decrypt(r.getString("protected_identity")), IdentityInput.class);
    SpaceView view =
        new SpaceView(
            r.getString("space_id"),
            r.getString("binding_id"),
            r.getString("name"),
            r.getBoolean("enabled"),
            r.getBoolean("human_enabled"),
            r.getString("provider_id"),
            r.getString("provider_binding_id"),
            Long.toString(r.getLong("version")),
            new IdentityView(i.mode(), i.issuer(), i.audience(), i.requesterNamespace()),
            strings(r.getString("origins_json")),
            strings(r.getString("topics_json")),
            r.getInt("retention_days"),
            r.getBoolean("enabled")
                ? provider(r.getString("provider_id")).capabilities()
                : java.util.Set.of(),
            readJson(r.getString("conversation_json"), ConversationSettings.class));
    return new Space(
        view,
        r.getString("runtime_base_url"),
        r.getString("runtime_trusted_key_header"),
        i,
        secrets.decrypt(r.getString("protected_connector_key")),
        r.getString("protected_server_connector") == null
            ? null
            : readJson(
                secrets.decrypt(r.getString("protected_server_connector")),
                ServerConnectorInput.class));
  }

  public static void text(String value, int max, String field) {
    if (value == null || value.isBlank() || value.length() > max)
      throw TicketException.invalid(field);
  }

  private void secret(String value) {
    text(value, 4096, "secret");
    if (value.length() < 32) throw TicketException.invalid("secret");
  }
}
