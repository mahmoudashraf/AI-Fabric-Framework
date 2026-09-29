package com.loomai.demo.dealership.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.loomai.demo.dealership.config.DealershipDemoProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class RuntimeIngestionClient {

    private final DealershipDemoProperties properties;
    private final RuntimePrivateAssertionSigner signer;
    private final RestClient restClient;

    public RuntimeIngestionClient(DealershipDemoProperties properties,
                                  RuntimePrivateAssertionSigner signer,
                                  RestClient.Builder builder) {
        this.properties = properties;
        this.signer = signer;
        this.restClient = builder.build();
    }

    public JsonNode readiness() {
        return get("/api/private/ingestion/readiness", List.of("runtime:index:overview"));
    }

    public JsonNode work(String workId) {
        if (!StringUtils.hasText(workId) || !workId.matches("[A-Za-z0-9._:-]{1,120}")) {
            throw new IllegalArgumentException("Invalid indexing work id.");
        }
        return get("/api/private/ingestion/indexing/work/" + workId, List.of("runtime:index:overview"));
    }

    public JsonNode batch(Map<String, Object> body) {
        requireEnabled();
        return restClient.post()
            .uri(baseUrl() + "/api/private/ingestion/data-sync/batch")
            .headers(headers -> applyPrivateHeaders(headers, requiredBatchScopes(body)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode.class);
    }

    List<String> requiredBatchScopes(Map<String, Object> body) {
        LinkedHashSet<String> scopes = new LinkedHashSet<>();
        Object operations = body == null ? null : body.get("operations");
        if (!(operations instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("Runtime ingestion batch must contain operations.");
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> operation)) {
                throw new IllegalArgumentException("Runtime ingestion operation must be an object.");
            }
            String type = String.valueOf(operation.get("type")).trim().toUpperCase();
            switch (type) {
                case "UPSERT" -> scopes.add("data-sync:upsert");
                case "DELETE" -> scopes.add("data-sync:delete");
                default -> throw new IllegalArgumentException("Unsupported runtime ingestion operation type.");
            }
        }
        scopes.add("runtime:index:overview");
        return new ArrayList<>(scopes);
    }

    private JsonNode get(String path, List<String> scopes) {
        requireEnabled();
        return restClient.get()
            .uri(baseUrl() + path)
            .headers(headers -> applyPrivateHeaders(headers, scopes))
            .retrieve()
            .body(JsonNode.class);
    }

    private void applyPrivateHeaders(HttpHeaders headers, List<String> scopes) {
        DealershipDemoProperties.PrivateAccess access = properties.getRuntime().getPrivateAccess();
        if (!StringUtils.hasText(access.getTrustedApiKeyHeader()) || !StringUtils.hasText(access.getTrustedApiKey())) {
            throw new IllegalStateException("Runtime trusted-backend authentication is not configured.");
        }
        headers.set(access.getTrustedApiKeyHeader().trim(), access.getTrustedApiKey().trim());
        headers.set(access.getAuthorizationHeader().trim(), signer.authorizationValue(scopes));
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    }

    private String baseUrl() {
        String value = properties.getRuntime().getBaseUrl().trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private void requireEnabled() {
        if (!properties.getRuntime().isEnabled() || !StringUtils.hasText(properties.getRuntime().getBaseUrl())) {
            throw new IllegalStateException("LoomAI runtime integration is not configured.");
        }
    }
}
