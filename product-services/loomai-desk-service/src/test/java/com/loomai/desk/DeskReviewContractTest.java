package com.loomai.desk;

import static com.loomai.desk.model.DeskContracts.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

class DeskReviewContractTest {
  private static final String REVIEWS = "/api/desk/v1/bindings/a/reviews";
  private static final String DECISION =
      "{\"decisionId\":\"decision-one\",\"decision\":\"APPROVE\",\"expectedVersion\":3}";

  @Test
  void defaultBootMvcSerializesEmptyQueueAndNestedTaskAsJsonValues() throws Exception {
    try (var h = new DeskHttpSecurityTest.Harness("h2")) {
      assertThat(h.context.getBean(RequestMappingHandlerAdapter.class).getMessageConverters())
          .anyMatch(JacksonJsonHttpMessageConverter.class::isInstance);
      String reviewer = registerReviewer(h);
      var bridge = new AtomicReference<>(new BridgeResponse(200, "[]"));
      stubBridge(h, bridge);

      HttpResponse<String> empty = h.request("GET", REVIEWS, reviewer, null);
      assertJson(empty, 200, "[]");
      assertThat(empty.body()).isEqualTo("[]");

      String task = task("WAITING_FOR_REVIEW", 4);
      bridge.set(new BridgeResponse(200, "[" + task + "]"));
      assertJson(h.request("GET", REVIEWS, reviewer, null), 200, "[" + task + "]");
      String detail =
          """
          {"task":%s,
           "requestedInformation":{"question":"Which contact time works?",
             "fields":[{"name":"contactTime","required":true}]},
           "suppliedInformation":{"contactTime":"morning",
             "preferences":{"days":["Monday","Tuesday"],"emailOnly":false}},
           "message":"Contact preferences supplied.","outcome":null,
           "successorTaskId":null,"failureReason":null}
          """.formatted(task);
      bridge.set(new BridgeResponse(200, detail));
      assertJson(h.request("GET", REVIEWS + "/task-one", reviewer, null), 200, detail);
      assertThat(h.bridgeRequests)
          .hasSize(3)
          .allSatisfy(
              call -> {
                assertThat(call.bearer()).isEqualTo("Bearer " + reviewer);
                assertThat(call.key()).isEqualTo(DeskHttpSecurityTest.BRIDGE);
                assertThat(call.binding()).isEqualTo("a");
              });
    }
  }

  @Test
  void defaultBootMvcPreservesTypedDecisionFailureOutcomeAndSanitizedForbiddenResponse()
      throws Exception {
    try (var h = new DeskHttpSecurityTest.Harness("h2")) {
      String reviewer = registerReviewer(h);
      String failure =
          """
          {"task":%s,"outcome":null,"successorTaskId":null,
           "failure":{"reason":"STALE_VERSION",
           "publicMessage":"Refresh the task before deciding.","retryable":true}}
          """.formatted(task("WAITING_FOR_REVIEW", 4));
      var bridge = new AtomicReference<>(new BridgeResponse(409, failure));
      stubBridge(h, bridge);
      String decisions = REVIEWS + "/task-one/decisions";
      assertJson(h.request("POST", decisions, reviewer, DECISION), 409, failure);
      assertThat(
              h.context
                  .getBean(org.springframework.jdbc.core.JdbcTemplate.class)
                  .queryForObject(
                      "SELECT COUNT(*) FROM desk_audit WHERE binding_id='a'"
                          + " AND operation='REVIEW_DECISION_REJECTED'",
                      Integer.class))
          .isEqualTo(1);

      String outcome =
          """
          {"task":%s,
           "outcome":{"actionName":"create_request","message":"Request recorded.",
           "data":{"receiptCode":"TEST-1","accepted":true,"items":["v-1"]}},
           "successorTaskId":null,"failure":null}
          """.formatted(task("APPROVED", 5));
      bridge.set(new BridgeResponse(200, outcome));
      assertJson(h.request("POST", decisions, reviewer, DECISION), 200, outcome);

      bridge.set(new BridgeResponse(403, "{\"internalDebug\":\"not a public review result\"}"));
      HttpResponse<String> denied = h.request("POST", decisions, reviewer, DECISION);
      assertJson(
          denied,
          403,
          """
          {"code":"REVIEW_BRIDGE_REJECTED",
           "message":"The review service rejected this operation; refresh the task and permissions."}
          """);
      assertThat(denied.body()).doesNotContain("internalDebug", "not a public review result");
      assertThat(h.bridgeRequests)
          .hasSize(3)
          .allSatisfy(
              call -> {
                assertThat(call.bearer()).isEqualTo("Bearer " + reviewer);
                assertThat(call.key()).isEqualTo(DeskHttpSecurityTest.BRIDGE);
                assertThat(call.binding()).isEqualTo("a");
              });
      for (var call : h.bridgeRequests)
        assertThat(DeskHttpSecurityTest.JSON.readTree(call.body()))
            .isEqualTo(DeskHttpSecurityTest.JSON.readTree(DECISION));

      String owner = h.token("owner", h.issuer, "authenticated", true, 600);
      assertThat(h.request("GET", REVIEWS, owner, null).statusCode()).isEqualTo(403);
      assertThat(
              h.request("GET", "/api/desk/v1/bindings/b/reviews", reviewer, null).statusCode())
          .isEqualTo(403);
      assertThat(h.bridgeRequests).hasSize(3);
    }
  }

  private static String task(String status, long version) throws Exception {
    var task = DeskHttpSecurityTest.JSON.readTree(
        """
        {"taskId":"task-one","policyId":{"name":"request-review","version":"1.0.0"},
         "type":"OPERATIONAL_REVIEW","title":"Review customer request",
         "summary":"Confirm the customer request can proceed.",
         "status":"WAITING_FOR_REVIEW","version":4,"allowedDecisions":["APPROVE","REJECT"],
         "createdAt":"2026-10-26T04:00:00Z","expiresAt":"2026-10-27T04:00:00Z"}
        """);
    ((com.fasterxml.jackson.databind.node.ObjectNode) task).put("status", status).put("version", version);
    return DeskHttpSecurityTest.JSON.writeValueAsString(task);
  }

  private static String registerReviewer(DeskHttpSecurityTest.Harness h) throws Exception {
    h.provision();
    String reviewer = h.token("reviewer", h.issuer, "authenticated", true, 600);
    HttpResponse<String> registered =
        h.request("POST", "/api/desk/v1/agents/register", reviewer, "{}");
    assertThat(registered.statusCode()).isEqualTo(200);
    String agentId = DeskHttpSecurityTest.JSON.readTree(registered.body()).path("id").asText();
    String owner = h.token("owner", h.issuer, "authenticated", true, 600);
    assertThat(
            h.request(
                    "POST",
                    "/api/desk/v1/bindings/a/agents",
                    owner,
                    DeskHttpSecurityTest.JSON.writeValueAsString(
                        new AssignmentInput(null, agentId, Set.of(Role.REVIEWER))))
                .statusCode())
        .isEqualTo(200);
    return reviewer;
  }

  private static void stubBridge(
      DeskHttpSecurityTest.Harness h, AtomicReference<BridgeResponse> response) {
    h.identity.createContext(
        "/bridge/a/reviews",
        exchange -> {
          h.bridgeRequests.add(
              new DeskHttpSecurityTest.BridgeRequest(
                  exchange.getRequestURI().toString(),
                  exchange.getRequestHeaders().getFirst("Authorization"),
                  exchange.getRequestHeaders().getFirst("X-LoomAI-Desk-Service-Key"),
                  exchange.getRequestHeaders().getFirst("X-LoomAI-Desk-Binding-Id"),
                  new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
          BridgeResponse result = response.get();
          byte[] bytes = result.body().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(result.status(), bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
  }

  private static void assertJson(HttpResponse<String> response, int status, String expected)
      throws Exception {
    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
    assertThat(
            MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElseThrow())
                .isCompatibleWith(MediaType.APPLICATION_JSON))
        .isTrue();
    assertThat(DeskHttpSecurityTest.JSON.readTree(response.body()))
        .isEqualTo(DeskHttpSecurityTest.JSON.readTree(expected));
  }

  private record BridgeResponse(int status, String body) {}
}
