package com.loomai.demo.dealership.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;

/** Server-configured stock validation; never resolves targets from website inventory. */
@Component
public class ProviderStockClient {
    private final RestClient client;
    private final String baseUrl;
    private final String key;
    private final String secret;
    private final String advertiserId;
    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    @Autowired
    public ProviderStockClient(
        @Value("${dealership.provider-stock.base-url:${AUTOTRADER_PROVIDER_BASE_URL:}}") String baseUrl,
        @Value("${dealership.provider-stock.key:${AUTOTRADER_PROVIDER_KEY:}}") String key,
        @Value("${dealership.provider-stock.secret:${AUTOTRADER_PROVIDER_SECRET:}}") String secret,
        @Value("${dealership.provider-stock.advertiser-id:${AUTOTRADER_ADVERTISER_ID:}}") String advertiserId,
        @Value("${dealership.provider-stock.request-timeout:PT8S}") Duration timeout) {
        this(httpClient(timeout), baseUrl, key, secret, advertiserId);
    }

    ProviderStockClient(RestClient client, String baseUrl, String key, String secret, String advertiserId) {
        this.client = client;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.key = key;
        this.secret = secret;
        this.advertiserId = advertiserId == null ? "" : advertiserId.trim();
    }

    public Stock requireAvailableStock(String reference) {
        String stockId = canonicalStockId(reference);
        requireConfigured();
        URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/stock")
            .queryParam("advertiserId", advertiserId)
            .queryParam("stockId", stockId)
            .queryParam("page", 1)
            .queryParam("pageSize", 2)
            .build().encode().toUri();
        JsonNode body;
        try {
            body = client.get().uri(uri).headers(headers -> headers.setBearerAuth(token()))
                .accept(MediaType.APPLICATION_JSON).retrieve()
                .onStatus(status -> status.value() != 200, (request, response) -> { throw unavailable(); })
                .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw unavailable();
        }
        if (body == null || !body.path("results").isArray()
            || !body.path("totalResults").isIntegralNumber()) throw invalidResponse();
        JsonNode results = body.path("results");
        long total = body.path("totalResults").asLong(-1);
        if (total == 0 && results.isEmpty()) throw notAvailable();
        if (total != 1 || results.size() != 1) throw invalidResponse();
        JsonNode record = results.get(0);
        if (!stockId.equals(record.path("metadata").path("stockId").asText())
            || !advertiserId.equals(record.path("advertiser").path("advertiserId").asText())) {
            throw invalidResponse();
        }
        if (!"FORECOURT".equals(record.path("metadata").path("lifecycleState").asText())
            || !"PUBLISHED".equals(record.path("adverts").path("retailAdverts")
                .path("advertiserAdvert").path("status").asText())
            || (!record.path("adverts").path("reservationStatus").isNull()
                && !record.path("adverts").path("reservationStatus").isMissingNode())) {
            throw notAvailable();
        }
        JsonNode vehicle = record.path("vehicle");
        String make = text(vehicle, "make");
        String model = text(vehicle, "model");
        Instant updatedAt;
        try {
            updatedAt = Instant.parse(text(record.path("metadata"), "lastUpdated"));
        } catch (RuntimeException ex) {
            throw invalidResponse();
        }
        if (!StringUtils.hasText(make) || !StringUtils.hasText(model)) throw invalidResponse();
        String year = manufactureYear(vehicle.path("yearOfManufacture"));
        String displayName = (StringUtils.hasText(year) ? year + " " : "") + make + " " + model;
        return new Stock(stockId, advertiserId, displayName, updatedAt, Instant.now());
    }

    public static String canonicalStockId(String reference) {
        if (reference == null || !reference.matches("[A-Za-z0-9._-]{1,80}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A canonical provider stock ID is required.");
        }
        return reference;
    }

    public String configuredAdvertiserId() {
        if (!advertiserId.matches("[A-Za-z0-9._-]{1,160}")) throw unavailable();
        return advertiserId;
    }

    private String manufactureYear(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) return null;
        try {
            int year;
            if (value.isIntegralNumber() && value.canConvertToInt()) year = value.intValue();
            else if (value.isTextual()) year = Integer.parseInt(value.textValue());
            else throw invalidResponse();
            if (year < 1 || year > 9999) throw invalidResponse();
            return Integer.toString(year);
        } catch (NumberFormatException ex) {
            throw invalidResponse();
        }
    }

    private synchronized String token() {
        if (accessToken != null && tokenExpiresAt.isAfter(Instant.now().plusSeconds(30))) return accessToken;
        var form = new LinkedMultiValueMap<String, String>();
        form.add("key", key);
        form.add("secret", secret);
        JsonNode response = client.post().uri(baseUrl + "/authenticate")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON)
            .body(form).retrieve()
            .onStatus(status -> status.value() != 200, (request, upstream) -> { throw unavailable(); })
            .body(JsonNode.class);
        try {
            String candidate = text(response, "access_token");
            Instant expiry = Instant.parse(text(response, "expires_at"));
            if (!StringUtils.hasText(candidate) || candidate.chars().anyMatch(Character::isISOControl)
                || !expiry.isAfter(Instant.now().plusSeconds(30))) throw invalidResponse();
            accessToken = candidate;
            tokenExpiresAt = expiry;
            return candidate;
        } catch (RuntimeException ex) {
            throw invalidResponse();
        }
    }

    private void requireConfigured() {
        try {
            URI uri = URI.create(baseUrl);
            boolean local = "http".equals(uri.getScheme())
                && ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()));
            if ((!"https".equals(uri.getScheme()) && !local) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !StringUtils.hasText(key) || !StringUtils.hasText(secret)
                || !advertiserId.matches("[A-Za-z0-9._-]{1,160}")) throw unavailable();
        } catch (IllegalArgumentException ex) {
            throw unavailable();
        }
    }

    private static RestClient httpClient(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Provider request timeout must be positive and at most 30 seconds.");
        }
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(factory).build();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Trusted provider stock validation is unavailable.");
    }

    private ResponseStatusException invalidResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The provider did not return one verifiable scoped stock record.");
    }

    private ResponseStatusException notAvailable() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "The selected provider stock is not currently available.");
    }

    public record Stock(String stockId, String advertiserId, String displayName, Instant providerUpdatedAt, Instant validatedAt) { }
}
