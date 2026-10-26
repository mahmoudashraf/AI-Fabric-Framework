package com.loomai.desk.channel.service;

import static com.loomai.desk.channel.model.InboxContracts.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.desk.channel.config.BasicInboxProperties;
import com.loomai.desk.channel.persistence.BasicInboxRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InboxCallbackWorker {

  private static final Logger LOGGER = LoggerFactory.getLogger(InboxCallbackWorker.class);

  private final BasicInboxProperties properties;
  private final BasicInboxRepository repository;
  private final InboxSecurityService security;
  private final ObjectMapper objectMapper;
  private final HttpClient client;

  public InboxCallbackWorker(
      BasicInboxProperties properties,
      BasicInboxRepository repository,
      InboxSecurityService security,
      ObjectMapper objectMapper) {
    this(
        properties,
        repository,
        security,
        objectMapper,
        HttpClient.newBuilder().connectTimeout(properties.getRequestTimeout()).build());
  }

  public InboxCallbackWorker(
      BasicInboxProperties properties,
      BasicInboxRepository repository,
      InboxSecurityService security,
      ObjectMapper objectMapper,
      HttpClient client) {
    this.properties = properties;
    this.repository = repository;
    this.security = security;
    this.objectMapper = objectMapper;
    this.client = client;
  }

  public void deliverNext() {
    CallbackClaim claim = repository.claimCallback(Duration.ofSeconds(30)).orElse(null);
    if (claim == null) {
      return;
    }
    try {
      String timestamp = String.valueOf(Instant.now().getEpochSecond());
      String nonce = UUID.randomUUID().toString();
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(properties.getConnectorCallbackUrl()))
              .timeout(properties.getRequestTimeout())
              .header("Content-Type", "application/json")
              .header("X-LoomAI-Provider-Timestamp", timestamp)
              .header("X-LoomAI-Provider-Nonce", nonce)
              .header(
                  "X-LoomAI-Provider-Signature", security.sign(timestamp, nonce, claim.payload()))
              .POST(HttpRequest.BodyPublishers.ofString(claim.payload()))
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        repository.failCallback(
            claim.callbackId(),
            claim.attempt(),
            claim.leaseOwner(),
            response.statusCode() >= 500 || response.statusCode() == 429,
            properties.getMaxCallbackAttempts(),
            properties.getCallbackRetryDelay(),
            "CONNECTOR_HTTP_" + response.statusCode());
        return;
      }
      JsonNode result = objectMapper.readTree(response.body());
      long controlVersion = result.path("controlVersion").asLong(-1);
      if (controlVersion < 0) {
        repository.failCallback(
            claim.callbackId(),
            claim.attempt(),
            claim.leaseOwner(),
            true,
            properties.getMaxCallbackAttempts(),
            properties.getCallbackRetryDelay(),
            "CONNECTOR_CONTROL_VERSION_MISSING");
        return;
      }
      String status =
          switch (claim.eventType()) {
            case "HANDOVER_ACCEPTED", "HANDOVER_CLAIMED", "HUMAN_MESSAGE" -> "ACTIVE";
            case "HANDOVER_RELEASED" -> "RETURNED_TO_AI";
            case "HANDOVER_CLOSED" -> "CLOSED";
            default -> repository.requireConversation(claim.handoverId()).status();
          };
      if (!repository.completeCallbackAndUpdateConversation(
          claim.callbackId(),
          claim.handoverId(),
          claim.attempt(),
          claim.leaseOwner(),
          status,
          controlVersion)) {
        LOGGER.warn("Ignored stale basic inbox callback completion for {}.", claim.callbackId());
      }
    } catch (Exception exception) {
      LOGGER.warn("Basic inbox callback delivery failed: {}", exception.getClass().getSimpleName());
      repository.failCallback(
          claim.callbackId(),
          claim.attempt(),
          claim.leaseOwner(),
          true,
          properties.getMaxCallbackAttempts(),
          properties.getCallbackRetryDelay(),
          "CONNECTOR_UNAVAILABLE");
    }
  }

  public void retention() {
    repository.purgeBefore(Instant.now().minus(properties.getRetention()));
  }
}
