package com.loomai.desk;

import static com.loomai.desk.model.DeskContracts.*;
import static org.assertj.core.api.Assertions.*;

import ai.fabric.chat.channel.*;
import ai.fabric.chat.control.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.desk.channel.model.InboxContracts.*;
import com.loomai.desk.channel.persistence.BasicInboxRepository.InboxConflictException;
import com.loomai.desk.config.DeskProperties;
import com.loomai.desk.service.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class DeskIsolationTest {
  static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
  static final String ISSUER = "https://desk-test.supabase.co/auth/v1";

  static DeskProperties properties() {
    DeskProperties p = new DeskProperties();
    p.setIssuer(ISSUER);
    p.setServiceKey("service-key-separate-1234567890-abcdef");
    p.setDataEncryptionSecret("data-key-separate-1234567890-abcdefgh");
    p.setRequestTimeout(Duration.ofSeconds(2));
    return p;
  }

  static VerifiedIdentity identity(String subject) {
    return new VerifiedIdentity(
        ISSUER,
        subject,
        subject + "@example.test",
        subject,
        true,
        "actual-access-token-" + subject);
  }

  static BindingInput binding(String customer, String callback) {
    return new BindingInput(
        customer,
        "tenant-" + customer,
        "deployment-" + customer,
        "production",
        customer,
        "PRODUCTION",
        callback,
        "provider-" + customer + "-123456789012345678901234567890",
        "signing-" + customer + "-123456789012345678901234567890",
        null,
        null,
        true);
  }

  static ConversationChannelDispatch open(String binding, String customer, String text) {
    return new ConversationChannelDispatch(
        "same-dispatch",
        ConversationChannelDispatchType.OPEN_HANDOVER,
        new ConversationScope(
            "tenant-" + customer, "deployment-" + customer, "same-conversation", "same-owner"),
        "same-handover",
        binding,
        1,
        null,
        null,
        List.of(),
        List.of(
            new ConversationChannelMessage(
                "same-message",
                ConversationParticipantRole.USER,
                "visitor",
                "Visitor",
                text,
                List.of(),
                Instant.parse("2026-10-01T10:00:00Z"))),
        Map.of("name", customer),
        Instant.parse("2026-10-01T10:00:01Z"));
  }

  static void workspace(DeskDirectory d, String customer, String owner) {
    d.workspace(
        customer,
        new WorkspaceInput(
            customer, List.of(new OwnerInput(ISSUER, owner, owner + "@example.test", owner))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void identicalLocalIdsAreIsolatedAndCallbacksUseBindingCredentials(String db) throws Exception {
    try (DeskTestDatabase database = new DeskTestDatabase(db)) {
      DeskDirectory d = new DeskDirectory(new JdbcTemplate(database.dataSource), properties());
      List<String> delivered = new CopyOnWriteArrayList<>();
      HttpServer callbacks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      callbacks.createContext(
          "/",
          exchange -> {
            String body =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String selected = JSON.readTree(body).path("channelBindingId").asText();
            Binding b = d.binding(selected);
            DeskChannelService channel =
                new DeskChannelService(database.dataSource, JSON, properties(), d);
            com.loomai.desk.channel.config.BasicInboxProperties p =
                new com.loomai.desk.channel.config.BasicInboxProperties();
            p.setCallbackSigningSecret(b.callbackSigningSecret());
            String expected =
                new com.loomai.desk.channel.service.InboxSecurityService(p)
                    .sign(
                        exchange.getRequestHeaders().getFirst("X-LoomAI-Provider-Timestamp"),
                        exchange.getRequestHeaders().getFirst("X-LoomAI-Provider-Nonce"),
                        body);
            boolean valid =
                expected.equals(
                        exchange.getRequestHeaders().getFirst("X-LoomAI-Provider-Signature"))
                    && exchange.getRequestURI().getPath().equals("/" + b.customerId());
            delivered.add(selected + ":" + valid + ":" + JSON.readTree(body).path("type").asText());
            byte[] response = "{\"controlVersion\":2}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(valid ? 200 : 403, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
          });
      callbacks.start();
      try {
        workspace(d, "a", "owner-a");
        workspace(d, "b", "owner-b");
        d.putBinding(
            "binding-a",
            binding("a", "http://127.0.0.1:" + callbacks.getAddress().getPort() + "/a"));
        d.putBinding(
            "binding-b",
            binding("b", "http://127.0.0.1:" + callbacks.getAddress().getPort() + "/b"));
        Operator a = d.register(identity("owner-a"));
        Operator b = d.register(identity("owner-b"));
        d.assign(
            "binding-a", a, new AssignmentInput(null, a.agentReference(), Set.of(Role.SUPPORT)));
        d.assign(
            "binding-b", b, new AssignmentInput(null, b.agentReference(), Set.of(Role.SUPPORT)));
        DeskChannelService channel =
            new DeskChannelService(database.dataSource, JSON, properties(), d);
        String rawA = JSON.writeValueAsString(open("binding-a", "a", "Customer A private text"));
        String rawB = JSON.writeValueAsString(open("binding-b", "b", "Customer B private text"));
        assertThat(channel.dispatch(d.binding("binding-a").providerApiKey(), rawA).accepted())
            .isTrue();
        assertThat(channel.dispatch(d.binding("binding-b").providerApiKey(), rawB).accepted())
            .isTrue();
        assertThat(channel.dispatch(d.binding("binding-a").providerApiKey(), rawA).accepted())
            .isTrue();
        assertThat(
                new JdbcTemplate(database.dataSource)
                    .queryForObject("SELECT COUNT(*) FROM inbox_dispatch_receipt", Long.class))
            .isEqualTo(2);
        var ea = channel.engine(d.binding("binding-a"));
        var eb = channel.engine(d.binding("binding-b"));
        assertThat(
                ea.service()
                    .detail(channel.channelOperator(a, "binding-a"), "same-handover")
                    .messages())
            .extracting(MessageView::content)
            .containsExactly("Customer A private text");
        assertThat(
                eb.service()
                    .detail(channel.channelOperator(b, "binding-b"), "same-handover")
                    .messages())
            .extracting(MessageView::content)
            .containsExactly("Customer B private text");
        assertThatThrownBy(() -> channel.dispatch(d.binding("binding-a").providerApiKey(), rawB))
            .isInstanceOf(InboxConflictException.class);
        assertThatThrownBy(
                () ->
                    channel.support(
                        "binding-b",
                        a,
                        null,
                        null,
                        s -> s.queue(channel.channelOperator(a, "binding-b"), null, 100)))
            .isInstanceOf(ResponseStatusException.class);
        ea.service().claim(channel.channelOperator(a, "binding-a"), "same-handover", "same-claim");
        eb.service().claim(channel.channelOperator(b, "binding-b"), "same-handover", "same-claim");
        ea.worker().deliverNext();
        eb.worker().deliverNext();
        ea.service()
            .reply(
                channel.channelOperator(a, "binding-a"),
                "same-handover",
                new ReplyRequest("Reply A", "same-reply"));
        eb.service()
            .reply(
                channel.channelOperator(b, "binding-b"),
                "same-handover",
                new ReplyRequest("Reply B", "same-reply"));
        ea.worker().deliverNext();
        eb.worker().deliverNext();
        ea.service().returnToAi(channel.channelOperator(a, "binding-a"), "same-handover", "return");
        ea.worker().deliverNext();
        eb.service().close(channel.channelOperator(b, "binding-b"), "same-handover", "close");
        eb.worker().deliverNext();
        assertThat(ea.repository().requireConversation("same-handover").status())
            .isEqualTo("RETURNED_TO_AI");
        assertThat(eb.repository().requireConversation("same-handover").status())
            .isEqualTo("CLOSED");
        assertThat(delivered).hasSize(6).allMatch(v -> v.contains(":true:"));
        assertThat(
                ea.service()
                    .reply(
                        channel.channelOperator(a, "binding-a"),
                        "same-handover",
                        new ReplyRequest("Reply A", "same-reply"))
                    .outcome())
            .isEqualTo("MESSAGE_REPLAYED");
        assertThatThrownBy(
                () ->
                    ea.service()
                        .reply(
                            channel.channelOperator(a, "binding-a"),
                            "same-handover",
                            new ReplyRequest("Modified", "same-reply")))
            .isInstanceOf(InboxConflictException.class);
        assertThat(
                new JdbcTemplate(database.dataSource)
                    .queryForList(
                        "SELECT protected_signing_secret FROM desk_binding", String.class))
            .allMatch(v -> !v.contains("signing-"));
      } finally {
        callbacks.stop(0);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void assignmentsAreRevocableAndOwnerSupportReviewerAndTargetsStaySeparate(String db) {
    try (DeskTestDatabase database = new DeskTestDatabase(db)) {
      DeskDirectory d = new DeskDirectory(new JdbcTemplate(database.dataSource), properties());
      workspace(d, "a", "owner-a");
      workspace(d, "b", "owner-b");
      d.putBinding("a-prod", binding("a", "http://localhost/callback"));
      d.putBinding("b-prod", binding("b", "http://localhost/callback"));
      BindingInput input = binding("a", "http://localhost/callback");
      d.putBinding(
          "a-stage",
          new BindingInput(
              "a",
              input.tenantId(),
              input.deploymentId(),
              "staging",
              "A stage",
              "STAGING",
              input.callbackUrl(),
              input.providerApiKey() + "stage",
              input.callbackSigningSecret() + "stage",
              null,
              null,
              true));
      Operator owner = d.register(identity("owner-a"));
      Operator agent = d.register(identity("agent"));
      Operator reviewer = d.register(identity("reviewer"));
      assertThatThrownBy(() -> d.authorize("a-prod", owner, Role.SUPPORT))
          .isInstanceOf(ResponseStatusException.class);
      assertThat(d.bindings(agent)).isEmpty();
      d.assign("a-prod", owner, new AssignmentInput(agent.email(), null, Set.of(Role.SUPPORT)));
      d.assign(
          "a-prod",
          owner,
          new AssignmentInput(null, reviewer.agentReference(), Set.of(Role.REVIEWER)));
      assertThat(d.authorize("a-prod", agent, Role.SUPPORT).bindingId()).isEqualTo("a-prod");
      assertThatThrownBy(() -> d.authorize("a-stage", agent, Role.SUPPORT))
          .isInstanceOf(ResponseStatusException.class);
      assertThatThrownBy(() -> d.authorize("b-prod", agent, Role.SUPPORT))
          .isInstanceOf(ResponseStatusException.class);
      assertThatThrownBy(() -> d.authorize("a-prod", reviewer, Role.SUPPORT))
          .isInstanceOf(ResponseStatusException.class);
      assertThatThrownBy(() -> d.authorize("a-prod", agent, Role.REVIEWER))
          .isInstanceOf(ResponseStatusException.class);
      assertThatThrownBy(
              () ->
                  d.assign(
                      "a-prod", agent, new AssignmentInput(null, agent.id(), Set.of(Role.OWNER))))
          .isInstanceOf(ResponseStatusException.class);
      assertThatThrownBy(
              () ->
                  d.assign(
                      "a-prod",
                      owner,
                      new AssignmentInput(
                          "not-registered@example.test", null, Set.of(Role.SUPPORT))))
          .isInstanceOf(ResponseStatusException.class);
      d.revoke("a-prod", owner, agent.id());
      assertThatThrownBy(() -> d.authorize("a-prod", agent, Role.SUPPORT))
          .isInstanceOf(ResponseStatusException.class);
      assertThat(d.authorize("a-prod", reviewer, Role.REVIEWER).bindingId()).isEqualTo("a-prod");
      assertThatThrownBy(
              () ->
                  d.register(
                      new VerifiedIdentity(
                          ISSUER, "unverified", "x@example.test", "X", false, "token")))
          .isInstanceOf(ResponseStatusException.class);
      assertThatThrownBy(
              () ->
                  d.workspace(
                      "another",
                      new WorkspaceInput(
                          "another",
                          List.of(
                              new OwnerInput(
                                  "https://evil.test", "owner-a", owner.email(), "fake")))))
          .isInstanceOf(ResponseStatusException.class);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void callbackRetriesAreFifoLeasedAndScoped(String db) throws Exception {
    try (DeskTestDatabase database = new DeskTestDatabase(db)) {
      DeskDirectory d = new DeskDirectory(new JdbcTemplate(database.dataSource), properties());
      workspace(d, "a", "owner-a");
      workspace(d, "b", "owner-b");
      d.putBinding("a", binding("a", "http://localhost/callback"));
      d.putBinding("b", binding("b", "http://localhost/callback"));
      DeskChannelService channel =
          new DeskChannelService(database.dataSource, JSON, properties(), d);
      channel.dispatch(
          d.binding("a").providerApiKey(), JSON.writeValueAsString(open("a", "a", "private A")));
      channel.dispatch(
          d.binding("b").providerApiKey(), JSON.writeValueAsString(open("b", "b", "private B")));
      var a = channel.engine(d.binding("a"));
      var b = channel.engine(d.binding("b"));
      a.repository()
          .transactional(
              () -> {
                a.repository()
                    .enqueueCallback("same-callback", "same-handover", "HUMAN_MESSAGE", "{}");
                a.repository().enqueueCallback("next", "same-handover", "HANDOVER_RELEASED", "{}");
                return true;
              });
      b.repository()
          .transactional(
              () -> {
                b.repository()
                    .enqueueCallback("same-callback", "same-handover", "HUMAN_MESSAGE", "{}");
                return true;
              });
      CallbackClaim first = a.repository().claimCallback(Duration.ofMinutes(1)).orElseThrow();
      assertThat(a.repository().claimCallback(Duration.ofMinutes(1))).isEmpty();
      CallbackClaim other = b.repository().claimCallback(Duration.ofMinutes(1)).orElseThrow();
      assertThat(other.callbackId()).isEqualTo(first.callbackId());
      assertThat(
              a.repository()
                  .completeCallbackAndUpdateConversation(
                      first.callbackId(),
                      "same-handover",
                      first.attempt(),
                      "stale-owner",
                      "ACTIVE",
                      2))
          .isFalse();
      assertThat(
              a.repository()
                  .failCallback(
                      first.callbackId(),
                      first.attempt(),
                      first.leaseOwner(),
                      false,
                      1,
                      Duration.ZERO,
                      "DEAD_TEST"))
          .isTrue();
      assertThat(a.repository().claimCallback(Duration.ofMinutes(1))).isEmpty();
      assertThat(a.repository().retryDeadCallback("a", first.callbackId())).isTrue();
      CallbackClaim retry = a.repository().claimCallback(Duration.ofMinutes(1)).orElseThrow();
      assertThat(
              a.repository()
                  .completeCallbackAndUpdateConversation(
                      retry.callbackId(),
                      "same-handover",
                      retry.attempt(),
                      retry.leaseOwner(),
                      "ACTIVE",
                      2))
          .isTrue();
      assertThat(a.repository().claimCallback(Duration.ofMinutes(1)).orElseThrow().callbackId())
          .isEqualTo("next");
      assertThat(b.repository().requireConversation("same-handover").status()).isEqualTo("WAITING");
    }
  }
}
