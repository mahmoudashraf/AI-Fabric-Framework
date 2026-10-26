package com.loomai.desk.service;

import static com.loomai.desk.model.DeskContracts.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.desk.config.DeskProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeskReviewService {
  private final DeskDirectory directory;
  private final DeskProperties properties;
  private final ObjectMapper mapper;
  private final HttpClient client;

  public DeskReviewService(
      DeskDirectory directory, DeskProperties properties, ObjectMapper mapper) {
    this.directory = directory;
    this.properties = properties;
    this.mapper = mapper;
    client =
        HttpClient.newBuilder()
            .connectTimeout(properties.getRequestTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public ResponseEntity<byte[]> request(
      String bindingId, Operator reviewer, VerifiedIdentity identity, String path, Object body) {
    Binding binding = directory.authorize(bindingId, reviewer, Role.REVIEWER);
    if (binding.reviewBridgeUrl() == null || binding.reviewBridgeKey() == null)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Action review is not configured for this target.");
    try {
      String base =
          binding.reviewBridgeUrl().endsWith("/")
              ? binding.reviewBridgeUrl().substring(0, binding.reviewBridgeUrl().length() - 1)
              : binding.reviewBridgeUrl();
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(URI.create(base + path))
              .timeout(properties.getRequestTimeout())
              .header("Authorization", "Bearer " + identity.accessToken())
              .header("X-LoomAI-Desk-Service-Key", binding.reviewBridgeKey())
              .header("X-LoomAI-Desk-Binding-Id", bindingId)
              .header("Accept", "application/json");
      if (body == null) builder.GET();
      else
        builder
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
      HttpResponse<java.io.InputStream> response =
          client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
      byte[] bytes;
      try (var stream = response.body()) {
        bytes = stream.readNBytes(1048577);
      }
      if (bytes.length > 1048576)
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Action review response is too large.");
      JsonNode result = bytes.length == 0 ? mapper.createObjectNode() : mapper.readTree(bytes);
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        int status = response.statusCode();
        if (status == 409 && result.path("failure").isObject()) {
          directory.audit(bindingId, binding.customerId(), reviewer.id(), "REVIEW_DECISION_REJECTED", path);
          return jsonResponse(status, result);
        }
        if (status == 401 || status == 403 || status == 404 || status == 409 || status == 422) {
          return jsonResponse(
              status,
              mapper
                  .createObjectNode()
                  .put("code", "REVIEW_BRIDGE_REJECTED")
                  .put(
                      "message",
                      "The review service rejected this operation; refresh the task and permissions."));
        }
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "Action review service is unavailable.");
      }
      directory.audit(
          bindingId,
          binding.customerId(),
          reviewer.id(),
          body == null ? "REVIEW_READ" : "REVIEW_DECISION",
          path);
      return jsonResponse(response.statusCode(), result);
    } catch (ResponseStatusException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Action review interrupted.");
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Action review service is unavailable.");
    }
  }

  private ResponseEntity<byte[]> jsonResponse(int status, JsonNode result)
      throws JsonProcessingException {
    // Boot 4's Jackson 3 converter treats Jackson 2 trees as beans, not JSON values.
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(mapper.writeValueAsBytes(result));
  }

  public static String segment(String value) {
    if (value == null
        || value.isBlank()
        || value.length() > 160
        || value.contains("/")
        || value.equals(".")
        || value.equals(".."))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid review task ID.");
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
