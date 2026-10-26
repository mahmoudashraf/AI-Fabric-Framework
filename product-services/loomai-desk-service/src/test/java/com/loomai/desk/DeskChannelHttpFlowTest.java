package com.loomai.desk;

import static com.loomai.desk.model.DeskContracts.*;
import static org.assertj.core.api.Assertions.*;

import ai.fabric.chat.channel.*;
import ai.fabric.chat.control.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.loomai.desk.service.DeskChannelService;
import com.loomai.desk.service.DeskDirectory;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeskChannelHttpFlowTest {
  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void realHttpDispatchClaimReplyReturnCloseAndWrongCustomerIsolation(String database)
      throws Exception {
    try (var h = new DeskHttpSecurityTest.Harness(database)) {
      h.provision();
      var json = DeskHttpSecurityTest.JSON;
      var directory = h.context.getBean(DeskDirectory.class);
      var channels = h.context.getBean(DeskChannelService.class);
      h.identity.createContext(
          "/callback/",
          exchange -> {
            byte[] response = "{\"controlVersion\":2}".getBytes(StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
          });
      String ownerToken = h.token("owner", h.issuer, "authenticated", true, 600);
      String otherToken = h.token("other-owner", h.issuer, "authenticated", true, 600);
      JsonNode owner =
          json.readTree(h.request("POST", "/api/desk/v1/agents/register", ownerToken, "{}").body());
      for (String customer : List.of("a", "b")) {
        Binding b = directory.binding(customer);
        var input =
            new BindingInput(
                b.customerId(),
                b.tenantId(),
                b.deploymentId(),
                b.targetProfileId(),
                b.deploymentName(),
                b.environment(),
                "http://127.0.0.1:" + h.identity.getAddress().getPort() + "/callback/" + customer,
                b.providerApiKey(),
                b.callbackSigningSecret(),
                b.reviewBridgeUrl(),
                b.reviewBridgeKey(),
                true);
        assertThat(h.admin("/bindings/" + customer, json.writeValueAsString(input)).statusCode())
            .isEqualTo(200);
        String body =
            json.writeValueAsString(
                new ConversationChannelDispatch(
                    "same-dispatch",
                    ConversationChannelDispatchType.OPEN_HANDOVER,
                    new ConversationScope(
                        b.tenantId(), b.deploymentId(), "same-conversation", "same-visitor"),
                    "same-handover",
                    customer,
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
                            "private-" + customer,
                            List.of(),
                            Instant.parse("2026-10-01T10:00:00Z"))),
                    Map.of("name", customer),
                    Instant.parse("2026-10-01T10:00:01Z")));
        HttpResponse<String> accepted = provider(h, b.providerApiKey(), body);
        assertThat(accepted.statusCode()).withFailMessage(accepted.body()).isEqualTo(200);
        assertThat(json.readTree(accepted.body()).path("accepted").asBoolean()).isTrue();
        assertThat(provider(h, b.providerApiKey(), body).body()).isEqualTo(accepted.body());
        String wrongKey = directory.binding(customer.equals("a") ? "b" : "a").providerApiKey();
        assertThat(provider(h, wrongKey, body).statusCode()).isEqualTo(409);
      }
      assertThat(
              h.request(
                      "POST",
                      "/api/desk/v1/bindings/a/agents",
                      ownerToken,
                      json.writeValueAsString(
                          new AssignmentInput(
                              null, owner.path("id").asText(), Set.of(Role.SUPPORT))))
                  .statusCode())
          .isEqualTo(200);
      String path = "/api/desk/v1/bindings/a/conversations/same-handover";
      HttpResponse<String> detail = h.request("GET", path, ownerToken, null);
      assertThat(detail.statusCode()).isEqualTo(200);
      assertThat(detail.body()).contains("private-a").doesNotContain("private-b");
      assertThat(h.request("GET", path, otherToken, null).statusCode()).isEqualTo(403);
      assertThat(
              h.request(
                      "POST",
                      path + "/messages",
                      ownerToken,
                      "{\"text\":\"premature\",\"idempotencyKey\":\"early\"}")
                  .statusCode())
          .isEqualTo(409);
      assertThat(
              h.request("POST", path + "/claim", ownerToken, "{\"idempotencyKey\":\"claim\"}")
                  .statusCode())
          .isEqualTo(200);
      channels.engine(directory.binding("a")).worker().deliverNext();
      String reply = "{\"text\":\"A real human reply\",\"idempotencyKey\":\"reply\"}";
      assertThat(h.request("POST", path + "/messages", ownerToken, reply).statusCode())
          .isEqualTo(200);
      assertThat(h.request("POST", path + "/messages", ownerToken, reply).body())
          .contains("MESSAGE_REPLAYED");
      channels.engine(directory.binding("a")).worker().deliverNext();
      assertThat(
              h.request(
                      "POST", path + "/return-to-ai", ownerToken, "{\"idempotencyKey\":\"return\"}")
                  .statusCode())
          .isEqualTo(200);
      channels.engine(directory.binding("a")).worker().deliverNext();
      assertThat(h.request("GET", path, ownerToken, null).body())
          .contains("RETURNED_TO_AI", "A real human reply");
      // The other customer's colliding handover is untouched by all commands above.
      assertThat(
              channels
                  .engine(directory.binding("b"))
                  .repository()
                  .requireConversation("same-handover")
                  .status())
          .isEqualTo("WAITING");
    }
  }

  private HttpResponse<String> provider(DeskHttpSecurityTest.Harness h, String key, String body)
      throws Exception {
    return h.http.send(
        HttpRequest.newBuilder(URI.create(h.base + "/api/conversation-channel/v1/dispatches"))
            .header("X-LoomAI-Inbox-Key", key)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
