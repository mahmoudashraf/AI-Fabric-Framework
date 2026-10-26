package com.loomai.desk.service;

import static com.loomai.desk.model.DeskContracts.*;

import com.loomai.desk.channel.config.BasicInboxProperties;
import com.loomai.desk.channel.service.InboxSecurityService;
import com.loomai.desk.config.DeskProperties;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeskDirectory {
  private final JdbcTemplate jdbc;
  private final DeskProperties properties;
  private final InboxSecurityService secrets;

  public DeskDirectory(JdbcTemplate jdbc, DeskProperties properties) {
    this.jdbc = jdbc;
    this.properties = properties;
    BasicInboxProperties p = new BasicInboxProperties();
    p.setDataEncryptionSecret(properties.getDataEncryptionSecret());
    secrets = new InboxSecurityService(p);
  }

  @Transactional
  public Operator register(VerifiedIdentity identity) {
    if (!identity.emailVerified() || identity.email() == null)
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Verify your Supabase email before registering.");
    Operator operator =
        profile(identity.issuer(), identity.subject(), identity.email(), identity.displayName());
    jdbc.update(
        "UPDATE desk_operator SET registered=TRUE,email=?,display_name=? WHERE operator_id=?",
        identity.email().toLowerCase(Locale.ROOT),
        identity.displayName(),
        operator.id());
    return operator(operator.id());
  }

  public Operator find(VerifiedIdentity identity) {
    return jdbc
        .query(
            "SELECT * FROM desk_operator WHERE issuer=? AND subject=?",
            (rs, n) -> operator(rs),
            identity.issuer(),
            identity.subject())
        .stream()
        .findFirst()
        .orElse(null);
  }

  private Operator profile(String issuer, String subject, String email, String displayName) {
    if (!properties.getIssuer().equals(issuer))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Owner issuer is not the configured Supabase issuer.");
    require(subject, 256, "subject");
    String name = displayName == null || displayName.isBlank() ? "Desk user" : displayName;
    require(name, 160, "displayName");
    String id = "agent-" + secrets.sha256(issuer + "\n" + subject).substring(0, 40);
    jdbc.update(
        "INSERT INTO desk_operator(operator_id,issuer,subject,email,display_name,registered,created_at) SELECT ?,?,?,?,?,FALSE,? WHERE NOT EXISTS(SELECT 1 FROM desk_operator WHERE issuer=? AND subject=?)",
        id,
        issuer,
        subject,
        email == null ? null : email.toLowerCase(Locale.ROOT),
        name,
        Timestamp.from(Instant.now()),
        issuer,
        subject);
    return operator(id);
  }

  private Operator operator(String id) {
    return jdbc
        .query("SELECT * FROM desk_operator WHERE operator_id=?", (rs, n) -> operator(rs), id)
        .stream()
        .findFirst()
        .orElseThrow(() -> notFound());
  }

  private Operator operator(ResultSet rs) throws SQLException {
    return new Operator(
        rs.getString("operator_id"),
        rs.getString("display_name"),
        rs.getString("email"),
        rs.getString("operator_id"));
  }

  @Transactional
  public void workspace(String customer, WorkspaceInput input) {
    require(customer, 160, "customerId");
    require(input.name(), 240, "workspace name");
    if (input.owners() == null || input.owners().isEmpty() || input.owners().size() > 100)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "One or more trusted owners are required.");
    jdbc.update(
        "INSERT INTO desk_workspace(customer_id,name) SELECT ?,? WHERE NOT EXISTS(SELECT 1 FROM desk_workspace WHERE customer_id=?)",
        customer,
        input.name(),
        customer);
    jdbc.queryForObject(
        "SELECT customer_id FROM desk_workspace WHERE customer_id=? FOR UPDATE",
        String.class,
        customer);
    jdbc.update("UPDATE desk_workspace SET name=? WHERE customer_id=?", input.name(), customer);
    jdbc.update("DELETE FROM desk_owner WHERE customer_id=?", customer);
    for (OwnerInput owner : input.owners()) {
      Operator profile =
          profile(owner.issuer(), owner.subject(), owner.email(), owner.displayName());
      jdbc.update(
          "INSERT INTO desk_owner(customer_id,operator_id) SELECT ?,? WHERE NOT EXISTS(SELECT 1 FROM desk_owner WHERE customer_id=? AND operator_id=?)",
          customer,
          profile.id(),
          customer,
          profile.id());
    }
    audit(null, customer, "PLATFORM", "WORKSPACE_SYNC", customer);
  }

  @Transactional
  public BindingView putBinding(String id, BindingInput input) {
    require(id, 160, "bindingId");
    require(input.customerId(), 160, "customerId");
    require(input.tenantId(), 128, "tenantId");
    require(input.deploymentId(), 128, "deploymentId");
    require(input.targetProfileId(), 160, "targetProfileId");
    require(input.deploymentName(), 240, "deploymentName");
    require(input.environment(), 64, "environment");
    trustedEndpoint(input.callbackUrl());
    requireSecret(input.providerApiKey());
    requireSecret(input.callbackSigningSecret());
    if (input.callbackSigningSecret().equals(properties.getDataEncryptionSecret())
        || input.providerApiKey().equals(input.callbackSigningSecret()))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Binding secrets must be distinct.");
    boolean bridge = input.reviewBridgeUrl() != null && !input.reviewBridgeUrl().isBlank();
    if (bridge) {
      trustedEndpoint(input.reviewBridgeUrl());
      requireSecret(input.reviewBridgeKey());
    } else if (input.reviewBridgeKey() != null && !input.reviewBridgeKey().isBlank())
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Review bridge URL and key must be configured together.");
    List<Binding> old =
        jdbc.query(
            "SELECT * FROM desk_binding WHERE binding_id=? FOR UPDATE", (rs, n) -> binding(rs), id);
    if (!old.isEmpty()) {
      Binding b = old.getFirst();
      if (!Objects.equals(b.customerId(), input.customerId())
          || !Objects.equals(b.tenantId(), input.tenantId())
          || !Objects.equals(b.deploymentId(), input.deploymentId())
          || !Objects.equals(b.targetProfileId(), input.targetProfileId()))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Existing binding ownership and target cannot change.");
      if (!input.enabled() && outstanding(id) > 0)
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Complete active conversations and callbacks before disabling this binding.");
      jdbc.update(
          "UPDATE desk_binding SET deployment_name=?,environment=?,callback_url=?,provider_key_hash=?,protected_provider_key=?,protected_signing_secret=?,review_bridge_url=?,protected_review_bridge_key=?,enabled=? WHERE binding_id=?",
          input.deploymentName(),
          input.environment(),
          input.callbackUrl(),
          secrets.sha256(input.providerApiKey()),
          secrets.encrypt(input.providerApiKey()),
          secrets.encrypt(input.callbackSigningSecret()),
          bridge ? input.reviewBridgeUrl() : null,
          bridge ? secrets.encrypt(input.reviewBridgeKey()) : null,
          input.enabled(),
          id);
    } else {
      jdbc.update(
          "INSERT INTO desk_binding(binding_id,customer_id,tenant_id,deployment_id,target_profile_id,deployment_name,environment,callback_url,provider_key_hash,protected_provider_key,protected_signing_secret,review_bridge_url,protected_review_bridge_key,enabled) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
          id,
          input.customerId(),
          input.tenantId(),
          input.deploymentId(),
          input.targetProfileId(),
          input.deploymentName(),
          input.environment(),
          input.callbackUrl(),
          secrets.sha256(input.providerApiKey()),
          secrets.encrypt(input.providerApiKey()),
          secrets.encrypt(input.callbackSigningSecret()),
          bridge ? input.reviewBridgeUrl() : null,
          bridge ? secrets.encrypt(input.reviewBridgeKey()) : null,
          input.enabled());
    }
    audit(id, input.customerId(), "PLATFORM", "BINDING_SYNC", id);
    return binding(id).view(List.of());
  }

  private long outstanding(String binding) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM inbox_conversation WHERE binding_id=? AND status NOT IN ('CLOSED','CANCELLED','RETURNED_TO_AI')",
            Long.class,
            binding)
        + jdbc.queryForObject(
            "SELECT COUNT(*) FROM inbox_callback_outbox WHERE binding_id=? AND status<>'COMPLETED'",
            Long.class,
            binding);
  }

  public Binding binding(String id) {
    return jdbc
        .query("SELECT * FROM desk_binding WHERE binding_id=?", (rs, n) -> binding(rs), id)
        .stream()
        .findFirst()
        .orElseThrow(() -> notFound());
  }

  public Binding provider(String key) {
    if (key == null || key.length() < 32)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Provider authentication failed.");
    Binding b =
        jdbc
            .query(
                "SELECT * FROM desk_binding WHERE provider_key_hash=? AND enabled=TRUE",
                (rs, n) -> binding(rs),
                secrets.sha256(key))
            .stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Provider authentication failed."));
    if (!InboxSecurityService.constantTime(b.providerApiKey(), key))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Provider authentication failed.");
    return b;
  }

  public List<String> enabledBindings() {
    return jdbc.query(
        "SELECT binding_id FROM desk_binding WHERE enabled=TRUE ORDER BY binding_id",
        (rs, n) -> rs.getString(1));
  }

  public List<BindingView> bindings(Operator operator) {
    return jdbc
        .query(
            "SELECT DISTINCT b.* FROM desk_binding b WHERE b.enabled=TRUE AND (EXISTS(SELECT 1 FROM desk_owner o WHERE o.customer_id=b.customer_id AND o.operator_id=?) OR EXISTS(SELECT 1 FROM desk_assignment a WHERE a.binding_id=b.binding_id AND a.operator_id=?)) ORDER BY b.customer_id,b.deployment_name,b.target_profile_id",
            (rs, n) -> binding(rs),
            operator.id(),
            operator.id())
        .stream()
        .map(b -> b.view(roles(b, operator.id())))
        .toList();
  }

  public Binding authorize(String id, Operator operator, Role role) {
    Binding b = binding(id);
    if (operator == null || !b.enabled() || !roles(b, operator.id()).contains(role))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "This deployment permission is not assigned.");
    return b;
  }

  public boolean authorizedIdentity(
      String bindingId, String issuer, String subject, Role permission) {
    if (!Set.of(Role.SUPPORT, Role.REVIEWER).contains(permission)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose SUPPORT or REVIEWER.");
    }
    if (!properties.getIssuer().equals(issuer) || subject == null || subject.isBlank())
      return false;
    return jdbc.queryForObject(
            """
        SELECT COUNT(*) FROM desk_assignment a
        JOIN desk_operator o ON o.operator_id=a.operator_id
        JOIN desk_binding b ON b.binding_id=a.binding_id
        WHERE a.binding_id=? AND b.enabled=TRUE AND o.registered=TRUE
          AND o.issuer=? AND o.subject=? AND a.role=?
        """,
            Long.class,
            bindingId,
            issuer,
            subject,
            permission.name())
        > 0;
  }

  public List<String> allBindings() {
    return jdbc.query(
        "SELECT binding_id FROM desk_binding ORDER BY binding_id", (rs, n) -> rs.getString(1));
  }

  public List<Role> roles(Binding b, String operator) {
    List<Role> roles = new ArrayList<>();
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM desk_owner WHERE customer_id=? AND operator_id=?",
            Long.class,
            b.customerId(),
            operator)
        > 0) roles.add(Role.OWNER);
    roles.addAll(
        jdbc.query(
            "SELECT role FROM desk_assignment WHERE binding_id=? AND operator_id=? ORDER BY role",
            (rs, n) -> Role.valueOf(rs.getString(1)),
            b.bindingId(),
            operator));
    return List.copyOf(roles);
  }

  public List<AgentView> agents(String binding) {
    return jdbc
        .query(
            "SELECT DISTINCT o.* FROM desk_operator o JOIN desk_assignment a ON a.operator_id=o.operator_id WHERE a.binding_id=? ORDER BY o.display_name,o.operator_id",
            (rs, n) -> operator(rs),
            binding)
        .stream()
        .map(
            o ->
                new AgentView(
                    o.id(),
                    o.agentReference(),
                    o.displayName(),
                    o.email(),
                    jdbc.query(
                        "SELECT role FROM desk_assignment WHERE binding_id=? AND operator_id=? ORDER BY role",
                        (rs, n) -> Role.valueOf(rs.getString(1)),
                        binding,
                        o.id())))
        .toList();
  }

  @Transactional
  public AgentView assign(String binding, Operator owner, AssignmentInput input) {
    Binding b = authorize(binding, owner, Role.OWNER);
    if (input.roles() == null
        || input.roles().isEmpty()
        || !Set.of(Role.SUPPORT, Role.REVIEWER).containsAll(input.roles()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose SUPPORT and/or REVIEWER.");
    boolean byRef = input.agentReference() != null && !input.agentReference().isBlank();
    boolean byEmail = input.email() != null && !input.email().isBlank();
    if (byRef == byEmail)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Provide one registered agent reference or verified email.");
    List<Operator> found =
        byRef
            ? jdbc.query(
                "SELECT * FROM desk_operator WHERE registered=TRUE AND operator_id=?",
                (rs, n) -> operator(rs),
                input.agentReference())
            : jdbc.query(
                "SELECT * FROM desk_operator WHERE registered=TRUE AND LOWER(email)=?",
                (rs, n) -> operator(rs),
                input.email().trim().toLowerCase(Locale.ROOT));
    if (found.size() != 1)
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "No unique registered agent was found; ask the agent for their reference.");
    Operator agent = found.getFirst();
    jdbc.queryForObject(
        "SELECT binding_id FROM desk_binding WHERE binding_id=? FOR UPDATE", String.class, binding);
    jdbc.update(
        "DELETE FROM desk_assignment WHERE binding_id=? AND operator_id=?", binding, agent.id());
    for (Role role : input.roles())
      jdbc.update(
          "INSERT INTO desk_assignment(binding_id,operator_id,role) VALUES(?,?,?)",
          binding,
          agent.id(),
          role.name());
    audit(binding, b.customerId(), owner.id(), "AGENT_ASSIGN", agent.id());
    return new AgentView(
        agent.id(),
        agent.agentReference(),
        agent.displayName(),
        agent.email(),
        input.roles().stream().sorted().toList());
  }

  @Transactional
  public void revoke(String binding, Operator owner, String agent) {
    Binding b = authorize(binding, owner, Role.OWNER);
    jdbc.queryForObject(
        "SELECT binding_id FROM desk_binding WHERE binding_id=? FOR UPDATE", String.class, binding);
    jdbc.update("DELETE FROM desk_assignment WHERE binding_id=? AND operator_id=?", binding, agent);
    audit(binding, b.customerId(), owner.id(), "AGENT_REVOKE", agent);
  }

  public void audit(
      String binding, String customer, String actor, String operation, String target) {
    jdbc.update(
        "INSERT INTO desk_audit(audit_id,binding_id,customer_id,actor_id,operation,target_id,created_at) VALUES(?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        binding,
        customer,
        actor,
        operation,
        target,
        Timestamp.from(Instant.now()));
  }

  private Binding binding(ResultSet rs) throws SQLException {
    return new Binding(
        rs.getString("binding_id"),
        rs.getString("customer_id"),
        rs.getString("tenant_id"),
        rs.getString("deployment_id"),
        rs.getString("target_profile_id"),
        rs.getString("deployment_name"),
        rs.getString("environment"),
        rs.getString("callback_url"),
        secrets.decrypt(rs.getString("protected_provider_key")),
        secrets.decrypt(rs.getString("protected_signing_secret")),
        rs.getString("review_bridge_url"),
        secrets.decrypt(rs.getString("protected_review_bridge_key")),
        rs.getBoolean("enabled"));
  }

  private void require(String value, int max, String field) {
    if (value == null || value.isBlank() || value.length() > max)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + field + ".");
  }

  private void requireSecret(String value) {
    if (value == null || value.length() < 32 || value.length() > 4096)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Secrets require 32 to 4096 characters.");
  }

  private ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Desk resource is not available.");
  }

  private void trustedEndpoint(String url) {
    try {
      URI uri = URI.create(url);
      if (uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getFragment() != null
          || uri.getQuery() != null
          || !("https".equals(uri.getScheme())
              || ("http".equals(uri.getScheme())
                  && Set.of("localhost", "127.0.0.1", "::1").contains(uri.getHost()))))
        throw new IllegalArgumentException();
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Trusted callback/bridge must use HTTPS (HTTP loopback only for tests).");
    }
  }
}
