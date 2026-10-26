package com.loomai.desk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.desk.config.DeskProperties;
import com.loomai.desk.model.DeskContracts.*;
import com.loomai.desk.service.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DeskReviewContractTest {
  @Test
  void preservesActualRuntimeDecisionFailureAndOutcomeContracts() throws Exception {
    var peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var status = new AtomicReference<>(409);
    var payload = new AtomicReference<>("{\"task\":{\"status\":\"WAITING_FOR_REVIEW\",\"version\":4},\"failure\":{\"reason\":\"STALE_VERSION\",\"publicMessage\":\"Refresh the task before deciding.\",\"retryable\":true}}");
    var bearer = new AtomicReference<String>();
    var bridgeKey = new AtomicReference<String>();
    peer.createContext("/reviews/task-one/decisions", exchange -> {
      bearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
      bridgeKey.set(exchange.getRequestHeaders().getFirst("X-LoomAI-Desk-Service-Key"));
      exchange.getRequestBody().readAllBytes();
      byte[] data = payload.get().getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status.get(), data.length);
      exchange.getResponseBody().write(data);
      exchange.close();
    });
    peer.start();
    try {
      var directory = mock(DeskDirectory.class);
      var operator = new Operator("reviewer-one", "Reviewer", "reviewer@example.test", "reviewer-one");
      var binding = new Binding("binding-one", "customer-one", "tenant-one", "deployment-one", "production",
          "Workspace", "production", "https://connector.example.test/events", "provider-key", "callback-key",
          "http://127.0.0.1:" + peer.getAddress().getPort() + "/reviews", "binding-bridge-key", true);
      when(directory.authorize("binding-one", operator, Role.REVIEWER)).thenReturn(binding);
      var mapper = new ObjectMapper();
      var service = new DeskReviewService(directory, new DeskProperties(), mapper);
      var identity = new VerifiedIdentity("https://identity.example.test", "actual-subject", "reviewer@example.test", "Reviewer", true, "actual-user-token");
      var decision = Map.of("decisionId", "decision-one", "decision", "APPROVE", "expectedVersion", 3);
      var rejected = service.request("binding-one", operator, identity, "/task-one/decisions", decision);
      assertThat(rejected.getStatusCode().value()).isEqualTo(409);
      assertThat(rejected.getBody()).isEqualTo(mapper.readTree(payload.get()));
      assertThat(rejected.getBody().path("failure").path("publicMessage").asText()).isEqualTo("Refresh the task before deciding.");
      assertThat(bearer.get()).isEqualTo("Bearer actual-user-token");
      assertThat(bridgeKey.get()).isEqualTo("binding-bridge-key");
      verify(directory).audit("binding-one", "customer-one", "reviewer-one", "REVIEW_DECISION_REJECTED", "/task-one/decisions");

      status.set(200);
      payload.set("{\"task\":{\"status\":\"APPROVED\"},\"outcome\":{\"actionName\":\"create_request\",\"message\":\"Request recorded.\",\"data\":{\"receiptCode\":\"TEST-1\"}},\"successorTaskId\":null,\"failure\":null}");
      var accepted = service.request("binding-one", operator, identity, "/task-one/decisions", decision);
      assertThat(accepted.getStatusCode().value()).isEqualTo(200);
      assertThat(accepted.getBody()).isEqualTo(mapper.readTree(payload.get()));
      assertThat(accepted.getBody().path("outcome").path("data").path("receiptCode").asText()).isEqualTo("TEST-1");

      status.set(403);
      payload.set("{\"internalDebug\":\"not a public review result\"}");
      var denied = service.request("binding-one", operator, identity, "/task-one/decisions", decision);
      assertThat(denied.getStatusCode().value()).isEqualTo(403);
      assertThat(denied.getBody().path("code").asText()).isEqualTo("REVIEW_BRIDGE_REJECTED");
      assertThat(denied.getBody().has("internalDebug")).isFalse();
    } finally {
      peer.stop(0);
    }
  }
}
