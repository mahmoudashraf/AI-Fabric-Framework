package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

@Service
public class PineconeControlPlaneClient {

    static final String API_BASE_URL = "https://api.pinecone.io";
    static final String API_VERSION = "2025-10";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public PineconeControlPlaneClient(ObjectMapper objectMapper) {
        this(
            objectMapper,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        );
    }

    PineconeControlPlaneClient(ObjectMapper objectMapper,
                               HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public void verifyControlPlaneAccess(String apiKey) {
        HttpRequest request = requestBuilder(URI.create(API_BASE_URL + "/indexes"), apiKey)
            .GET()
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Pinecone control-plane access check failed", response);
        }
    }

    public PineconeIndexSummary findIndexByName(String indexName,
                                                String apiKey) {
        HttpRequest request = requestBuilder(URI.create(API_BASE_URL + "/indexes/" + indexName), apiKey)
            .GET()
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Pinecone describe index failed", response);
        }
        return toIndexSummary(readJson(response.body()), indexName);
    }

    public void createServerlessIndex(String indexName,
                                      int dimensions,
                                      String metric,
                                      String cloud,
                                      String region,
                                      boolean deletionProtectionEnabled,
                                      String apiKey) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("name", indexName);
        payload.put("dimension", dimensions);
        payload.put("metric", metric);
        payload.put("deletion_protection", deletionProtectionEnabled ? "enabled" : "disabled");
        ObjectNode serverless = payload.putObject("spec").putObject("serverless");
        serverless.put("cloud", cloud);
        serverless.put("region", region);

        HttpRequest request = requestBuilder(URI.create(API_BASE_URL + "/indexes"), apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Pinecone create index failed", response);
        }
    }

    public PineconeIndexSummary awaitIndexReady(String indexName,
                                                String apiKey) {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
        PineconeIndexSummary lastSeen = null;
        while (Instant.now().isBefore(deadline)) {
            lastSeen = findIndexByName(indexName, apiKey);
            if (lastSeen != null && lastSeen.ready() && StringUtils.hasText(lastSeen.host())) {
                return lastSeen;
            }
            try {
                Thread.sleep(2_000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RailwayProvisioningException("Interrupted while waiting for Pinecone index readiness.", ex);
            }
        }
        if (lastSeen != null) {
            return lastSeen;
        }
        throw new RailwayProvisioningException("Timed out waiting for Pinecone index '" + indexName + "' to appear.");
    }

    public PineconeIndexSummary configureDeletionProtection(String indexName,
                                                            boolean enabled,
                                                            String apiKey) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("deletion_protection", enabled ? "enabled" : "disabled");

        HttpRequest request = requestBuilder(URI.create(API_BASE_URL + "/indexes/" + indexName), apiKey)
            .method("PATCH", HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Pinecone configure index failed", response);
        }
        return toIndexSummary(readJson(response.body()), indexName);
    }

    public void deleteIndex(String indexName,
                            String apiKey) {
        HttpRequest request = requestBuilder(URI.create(API_BASE_URL + "/indexes/" + indexName), apiKey)
            .DELETE()
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            return;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Pinecone delete index failed", response);
        }
    }

    public void awaitIndexDeleted(String indexName,
                                  String apiKey) {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
        while (Instant.now().isBefore(deadline)) {
            PineconeIndexSummary snapshot = findIndexByName(indexName, apiKey);
            if (snapshot == null) {
                return;
            }
            try {
                Thread.sleep(2_000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RailwayProvisioningException("Interrupted while waiting for Pinecone index deletion.", ex);
            }
        }
        throw new RailwayProvisioningException("Timed out waiting for Pinecone index '" + indexName + "' to delete.");
    }

    private PineconeIndexSummary toIndexSummary(JsonNode root,
                                                String fallbackIndexName) {
        JsonNode payload = root.path("name").isMissingNode()
            && root.path("host").isMissingNode()
            && root.path("status").isMissingNode()
            ? root.path("result")
            : root;
        String name = fallbackOrTrimmed(payload.path("name").asText(""), fallbackIndexName);
        String host = normalizeHost(payload.path("host").asText(""));
        int dimension = payload.path("dimension").asInt(0);
        String metric = payload.path("metric").asText("").trim();
        boolean ready = payload.path("status").path("ready").asBoolean(false);
        if (!ready && payload.path("ready").isBoolean()) {
            ready = payload.path("ready").asBoolean(false);
        }
        String deletionProtection = payload.path("deletion_protection").asText("").trim();
        return new PineconeIndexSummary(name, host, dimension, metric, ready, deletionProtection);
    }

    private HttpRequest.Builder requestBuilder(URI uri,
                                               String apiKey) {
        return HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Api-Key", apiKey)
            .header("X-Pinecone-Api-Version", API_VERSION);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new RailwayProvisioningException("Pinecone control-plane request failed.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RailwayProvisioningException("Pinecone control-plane request was interrupted.", ex);
        }
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse Pinecone control-plane JSON.", ex);
        }
    }

    private RailwayProvisioningException failure(String action,
                                                 HttpResponse<String> response) {
        String body = response.body() == null ? "" : response.body().trim();
        String message = action + " with HTTP " + response.statusCode();
        if (!body.isEmpty()) {
            message += ": " + body;
        }
        return new RailwayProvisioningException(message);
    }

    private String fallbackOrTrimmed(String value,
                                     String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String normalizeHost(String host) {
        if (!StringUtils.hasText(host)) {
            return "";
        }
        String trimmed = host.trim();
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    public record PineconeIndexSummary(
        String name,
        String host,
        int dimension,
        String metric,
        boolean ready,
        String deletionProtection
    ) {
    }
}
