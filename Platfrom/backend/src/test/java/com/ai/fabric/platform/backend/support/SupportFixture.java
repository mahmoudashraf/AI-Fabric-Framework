package com.ai.fabric.platform.backend.support;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceReadinessService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.entity.*;
import com.ai.fabric.platform.backend.security.repository.*;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.*;
import com.ai.fabric.platform.backend.vectorization.repository.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/** Real identity/resource/context authority; only the remote ticket port is mocked. */
class SupportFixture {
  final JdbcTemplate jdbc;
  final DataSource dataSource;
  final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  final PlatformUserRepository users = mock(PlatformUserRepository.class);
  final PlatformCustomerRepository customers = mock(PlatformCustomerRepository.class);
  final PlatformCustomerIdentityLinkRepository links =
      mock(PlatformCustomerIdentityLinkRepository.class);
  final DeploymentRepository deployments = mock(DeploymentRepository.class);
  final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
  final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
  final AIWorkspaceInstallationRepository apps = mock(AIWorkspaceInstallationRepository.class);
  final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
  final AIWorkspaceReadinessService readiness = mock(AIWorkspaceReadinessService.class);
  final VectorizationSourceConnectionRepository connections =
      mock(VectorizationSourceConnectionRepository.class);
  final VectorizationPlanRepository plans = mock(VectorizationPlanRepository.class);
  final VectorizationRunRepository runs = mock(VectorizationRunRepository.class);
  final SupportDocumentStatusService documents = mock(SupportDocumentStatusService.class);
  final PlatformSecretService secrets = mock(PlatformSecretService.class);
  final SupportTicketClient ticketClient = mock(SupportTicketClient.class);
  final Map<String, PlatformUserEntity> directory = new HashMap<>();
  final Map<String, PlatformCustomerIdentityLinkEntity> identityLinks = new HashMap<>();
  final Map<String, ObjectNode> remoteTickets = new ConcurrentHashMap<>();
  final Map<String, Requester> remoteOwners = new ConcurrentHashMap<>();
  final Map<String, String> commandTickets = new ConcurrentHashMap<>();
  final Map<String, Object> commandInputs = new ConcurrentHashMap<>();
  final MockEnvironment env =
      new MockEnvironment()
          .withProperty("platform.support.enabled", "true")
          .withProperty("platform.support.customer-id", "internal-owner")
          .withProperty("platform.support.tenant-id", "internal-tenant")
          .withProperty("platform.support.deployment-id", "support-runtime")
          .withProperty("platform.support.target-profile-id", "staging-target")
          .withProperty("platform.support.assertion-issuer", "loomai-support")
          .withProperty("platform.support.assertion-audience", "support-runtime")
          .withProperty("platform.support.desk.binding-id", "support-binding")
          .withProperty(
              "platform.support.diagnostics.service-key-secret-name",
              "PLATFORM_MANAGED_SUPPORT_DIAGNOSTICS");
  final SupportSettings settings;
  final SupportRequesterService requesters;
  final SupportResourceService resources;
  final SupportIntegrationContextStore contextStore;
  final SupportConversationScopeService conversations;
  final SupportTicketService service;
  final SupportDiagnosticAuthentication authentication;
  final SupportDiagnosticService diagnostics;

  SupportFixture() {
    this(
        new DriverManagerDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
  }

  SupportFixture(DataSource source) {
    dataSource = source;
    jdbc = new JdbcTemplate(source);
    jdbc.execute("create table platform_customers(id varchar(64) primary key)");
    jdbc.execute("create table platform_users(id varchar(64) primary key)");
    new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V226__console_support_integration_context.sql"))
        .execute(source);
    when(users.findById(anyString()))
        .thenAnswer(a -> Optional.ofNullable(directory.get(a.getArgument(0))));
    when(links.findByPlatformUserId(anyString()))
        .thenAnswer(a -> Optional.ofNullable(identityLinks.get(a.getArgument(0))));
    addUser("alice", "customer-a");
    addUser("bob", "customer-a");
    addUser("eve", "customer-b");
    addLink("alice");
    addLink("bob");
    addLink("eve");
    var internal = deployment("support-runtime", "internal-owner", "internal-tenant");
    when(deployments.findById(internal.getId())).thenReturn(Optional.of(internal));
    when(targets.existsById("staging-target")).thenReturn(true);
    settings = new SupportSettings(env, deployments, targets);
    requesters =
        new SupportRequesterService(
            users, links, new PlatformCustomerAccessService(users, customers));
    resources =
        new SupportResourceService(
            deployments, releases, apps, consumers, readiness, connections, plans, runs, documents);
    contextStore = new SupportIntegrationContextStore(jdbc, json);
    conversations = new SupportConversationScopeService(jdbc, settings, requesters);
    when(ticketClient.spaceId()).thenReturn("support-binding");
    when(ticketClient.request(
            any(), anyString(), anyString(), nullable(Object.class), nullable(String.class)))
        .thenAnswer(a -> remote(a.getArguments()));
    when(ticketClient.request(
            any(),
            anyString(),
            anyString(),
            nullable(Object.class),
            nullable(String.class),
            anyMap()))
        .thenAnswer(a -> remote(a.getArguments()));
    when(ticketClient.request(
            any(),
            anyString(),
            anyString(),
            nullable(Object.class),
            nullable(String.class),
            anyMap(),
            nullable(String.class),
            nullable(String.class)))
        .thenAnswer(a -> remote(a.getArguments()));
    service =
        transactional(
            new SupportTicketService(
                ticketClient, requesters, contextStore, resources, settings, conversations));
    authentication = new SupportDiagnosticAuthentication(settings, secrets);
    diagnostics =
        transactional(new SupportDiagnosticService(authentication, service, requesters, resources));
    when(secrets.resolveSecret("PLATFORM_MANAGED_SUPPORT_DIAGNOSTICS"))
        .thenReturn("dedicated-diagnostic-test-key");
    login("alice");
  }

  private synchronized JsonNode remote(Object[] args) {
    Requester owner = (Requester) args[0];
    String method = (String) args[1], path = (String) args[2];
    if (method.equals("POST") && path.equals("/requests")) {
      var input = (SupportTicketService.Create) args[3];
      String command = owner.toString() + ":" + args[4];
      String existing = commandTickets.get(command);
      if (existing != null) {
        if (!Objects.equals(commandInputs.get(command), input))
          throw SupportPolicy.conflict("IDEMPOTENCY_CONFLICT");
        return remoteTickets.get(existing).deepCopy();
      }
      String id = "request-" + UUID.randomUUID();
      String principal =
          args.length == 8 && args[6] != null ? (String) args[6] : "support-ticket:" + id;
      String conversation =
          input.existingConversationId() == null ? "chat-" + id : input.existingConversationId();
      ObjectNode t =
          json.createObjectNode()
              .put("id", id)
              .put("reference", "SUP-" + id)
              .put("subject", input.subject())
              .put("details", input.details())
              .put("topic", input.topic())
              .put("status", "OPEN")
              .put("version", "W/\"0\"")
              .put("runtimePrincipal", principal)
              .put("providerBindingId", "provider-binding");
      t.putObject("conversation")
          .put("episodeId", "episode-" + id)
          .put("conversationId", conversation)
          .put("delivery", input.existingConversationId() == null ? "PENDING" : "NOT_REQUIRED");
      t.putArray("episodes")
          .addObject()
          .put("episodeId", "episode-" + id)
          .put("conversationId", conversation)
          .put("number", 1);
      t.putArray("capabilities").add("GET").add("LIST").add("RESOLVE").add("REOPEN");
      remoteTickets.put(id, t);
      remoteOwners.put(id, owner);
      commandTickets.put(command, id);
      commandInputs.put(command, input);
      return t.deepCopy();
    }
    if (path.equals("/session")) return json.createObjectNode().put("authenticated", true);
    if (path.startsWith("/requests?")) {
      ObjectNode page = json.createObjectNode();
      var items = page.putArray("items");
      remoteTickets.forEach(
          (id, t) -> {
            if (owner.equals(remoteOwners.get(id))) items.add(t.deepCopy());
          });
      return page;
    }
    String[] parts = path.split("/");
    String id = parts[2];
    if (!owner.equals(remoteOwners.get(id))) throw SupportPolicy.missing();
    ObjectNode ticket = remoteTickets.get(id);
    if (method.equals("GET")) return ticket.deepCopy();
    if (path.endsWith("/resolve")) ticket.put("status", "RESOLVED");
    if (path.endsWith("/cancel")) ticket.put("status", "CANCELLED");
    if (path.endsWith("/reopen")) ticket.put("status", "OPEN");
    return ticket.deepCopy();
  }

  void addUser(String id, String customerId) {
    if (jdbc.queryForObject(
            "select count(*) from platform_customers where id=?", Integer.class, customerId)
        == 0) jdbc.update("insert into platform_customers(id) values (?)", customerId);
    jdbc.update("insert into platform_users(id) values (?)", id);
    var user = new PlatformUserEntity();
    user.setId(id);
    user.setCustomerId(customerId);
    user.setEmail(id + "@example.test");
    user.setRole("CUSTOMER_ADMIN");
    user.setStatus("ACTIVE");
    user.setDisplayName(id);
    directory.put(id, user);
    var customer = new PlatformCustomerEntity();
    customer.setId(customerId);
    customer.setName(customerId);
    customer.setStatus("ACTIVE");
    when(customers.findById(customerId)).thenReturn(Optional.of(customer));
  }

  void addLink(String id) {
    var link = new PlatformCustomerIdentityLinkEntity();
    link.setId("link-" + id);
    link.setPlatformUserId(id);
    link.setIssuer("https://identity.example.test/auth/v1");
    link.setSubject("subject-" + id);
    identityLinks.put(id, link);
  }

  void login(String id) {
    var user = directory.get(id);
    var principal =
        new PlatformPrincipal(user.getEmail(), PlatformRole.CUSTOMER_ADMIN, id, "SUPABASE", id);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  record Saved(String id, String reference, SupportTicketService.ContextView context) {
    Consent consent() {
      return context.consent();
    }

    long revision() {
      return context.revision();
    }
  }

  Saved create(boolean consent, ResourceRef... refs) {
    JsonNode ticket =
        service.create(
            new SupportTicketService.Create(
                "Help with setup", "The app is not ready.", "setup", null),
            UUID.randomUUID().toString());
    String id = ticket.path("id").asText();
    if (refs.length > 0)
      service.updateContext(id, new SupportTicketService.ContextInput(List.of(refs), 0));
    var c = service.contextView(id);
    if (consent) c = service.consent(id, new ConsentChange(true, c.consent().version()));
    return new Saved(id, ticket.path("reference").asText(), c);
  }

  void nextEpisode(String id) {
    var t = remoteTickets.get(id);
    String conversation = "next-chat-" + UUID.randomUUID();
    t.withObject("/conversation").put("conversationId", conversation);
    t.withArray("episodes")
        .addObject()
        .put("episodeId", "next-episode")
        .put("conversationId", conversation)
        .put("number", 2);
  }

  org.springframework.http.HttpHeaders headers(OwnedCase c) {
    var h = new org.springframework.http.HttpHeaders();
    h.set(SupportDiagnosticAuthentication.SERVICE_KEY_HEADER, "dedicated-diagnostic-test-key");
    h.set("X-AIFABRIC-AUTH-SUBJECT-TYPE", "END_USER");
    h.set("X-AIFABRIC-AUTH-MODE", "PRIVATE_RUNTIME_BACKEND_MEDIATED");
    h.set("X-AIFABRIC-AUTH-CALLER-TYPE", "TRUSTED_BACKEND");
    h.set("X-AIFABRIC-AUTH-DEPLOYMENT-ID", c.deploymentId());
    h.set("X-AIFABRIC-AUTH-CUSTOMER-ID", c.customerId());
    h.set("X-AIFABRIC-AUTH-TENANT-ID", c.tenantId());
    h.set("X-AIFABRIC-AUTH-ISSUER", c.assertionIssuer());
    h.set("X-AIFABRIC-AUTH-AUDIENCES", c.assertionAudience());
    h.set(
        "X-AIFABRIC-AUTH-SCOPES",
        "chat:messages," + SupportDiagnosticAuthentication.DIAGNOSTIC_SCOPE);
    h.set("X-AIFABRIC-AUTH-EXPIRES-AT", Instant.now().plusSeconds(180).toString());
    h.set("X-AIFABRIC-AUTH-SUBJECT-ID", c.principalId());
    h.set("X-AIFABRIC-CONVERSATION-ID", c.conversationId());
    return h;
  }

  static DeploymentEntity deployment(String id, String customer, String tenant) {
    var d = new DeploymentEntity();
    d.setId(id);
    d.setCustomerId(customer);
    d.setTenantId(tenant);
    d.setStatus("ACTIVE");
    return d;
  }

  @SuppressWarnings("unchecked")
  <T> T transactional(T target) {
    var factory = new ProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAdvice(
        new TransactionInterceptor(
            new DataSourceTransactionManager(dataSource),
            new AnnotationTransactionAttributeSource()));
    return (T) factory.getProxy();
  }
}
