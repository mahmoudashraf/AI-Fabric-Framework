package com.loomai.demo.dealership.handover;

import com.fasterxml.jackson.databind.JsonNode;
import com.loomai.demo.dealership.config.DealershipDemoProperties;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class HumanInboxClient {

    private static final String OPERATOR_KEY_HEADER = "X-LoomAI-Operator-Key";

    private final DealershipDemoProperties.HumanInbox configuration;
    private final RestClient client;
    private final URI baseUri;

    public HumanInboxClient(DealershipDemoProperties properties, RestClient.Builder builder) {
        this.configuration = properties.getHumanInbox();
        this.baseUri = StringUtils.hasText(configuration.getBaseUrl())
            ? URI.create(configuration.getBaseUrl().trim().replaceAll("/+$", ""))
            : null;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(configuration.getRequestTimeout());
        requestFactory.setReadTimeout(configuration.getRequestTimeout());
        this.client = builder.requestFactory(requestFactory).build();
    }

    public boolean enabled() {
        return configuration.isEnabled() && baseUri != null
            && StringUtils.hasText(configuration.getOperatorApiKey());
    }

    public JsonNode operator() {
        return get(uri("api", "operator", "me"));
    }

    public JsonNode queue(String status, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        UriComponentsBuilder uri = UriComponentsBuilder.fromUri(uri("api", "operator", "conversations"))
            .queryParam("limit", limit);
        if (StringUtils.hasText(status)) {
            uri.queryParam("status", status.trim().toUpperCase());
        }
        return get(uri.build(true).toUri());
    }

    public JsonNode detail(String handoverId) {
        return get(uri("api", "operator", "conversations", requiredId(handoverId)));
    }

    public JsonNode claim(String handoverId, String idempotencyKey) {
        return command(handoverId, "claim", Map.of("idempotencyKey", requiredKey(idempotencyKey)));
    }

    public JsonNode reply(String handoverId, String text, String idempotencyKey) {
        if (!StringUtils.hasText(text)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A human reply is required.");
        }
        return command(handoverId, "messages", Map.of(
            "text", text.trim(),
            "idempotencyKey", requiredKey(idempotencyKey)
        ));
    }

    public JsonNode returnToAi(String handoverId, String idempotencyKey) {
        return command(handoverId, "return-to-ai", Map.of("idempotencyKey", requiredKey(idempotencyKey)));
    }

    public JsonNode close(String handoverId, String idempotencyKey) {
        return command(handoverId, "close", Map.of("idempotencyKey", requiredKey(idempotencyKey)));
    }

    private JsonNode command(String handoverId, String operation, Map<String, Object> body) {
        return post(uri("api", "operator", "conversations", requiredId(handoverId), operation), body);
    }

    private JsonNode get(URI uri) {
        requireEnabled();
        try {
            return client.get()
                .uri(uri)
                .header(OPERATOR_KEY_HEADER, configuration.getOperatorApiKey())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private JsonNode post(URI uri, Map<String, Object> body) {
        requireEnabled();
        try {
            return client.post()
                .uri(uri)
                .header(OPERATOR_KEY_HEADER, configuration.getOperatorApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private URI uri(String... segments) {
        if (baseUri == null) {
            return URI.create("http://127.0.0.1/unconfigured");
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(baseUri);
        for (String segment : segments) {
            builder.pathSegment(segment);
        }
        return builder.build().encode().toUri();
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Human conversation inbox is not configured.");
        }
    }

    private String requiredId(String value) {
        if (!StringUtils.hasText(value) || value.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid handover id is required.");
        }
        return value.trim();
    }

    private String requiredKey(String value) {
        if (!StringUtils.hasText(value) || value.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid idempotency key is required.");
        }
        return value.trim();
    }

    private ResponseStatusException translate(RestClientResponseException exception) {
        HttpStatus status = switch (exception.getStatusCode().value()) {
            case 400, 422 -> HttpStatus.BAD_REQUEST;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return new ResponseStatusException(status, "The human conversation provider rejected the request.", exception);
    }

    private ResponseStatusException unavailable(RuntimeException exception) {
        if (exception instanceof ResponseStatusException responseStatusException) {
            return responseStatusException;
        }
        return new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "The human conversation provider is unavailable.",
            exception
        );
    }
}
