package com.loomai.desk.service;

import static com.loomai.desk.model.DeskContracts.*;

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

  public ResponseEntity<JsonNode> request(
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
      HttpResponse<String> response =
          client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        int status = response.statusCode();
        if (status == 401 || status == 403 || status == 404 || status == 409 || status == 422) {
          return ResponseEntity.status(status)
              .body(
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
      JsonNode result =
          response.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(response.body());
      directory.audit(
          bindingId,
          binding.customerId(),
          reviewer.id(),
          body == null ? "REVIEW_READ" : "REVIEW_DECISION",
          path);
      return ResponseEntity.status(response.statusCode()).body(result);
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
