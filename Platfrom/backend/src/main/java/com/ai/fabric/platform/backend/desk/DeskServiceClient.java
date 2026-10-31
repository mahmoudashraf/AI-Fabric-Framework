package com.ai.fabric.platform.backend.desk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import static org.springframework.http.HttpStatus.*;

@Component
public class DeskServiceClient {
    private final Environment environment;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public DeskServiceClient(Environment environment, ObjectMapper mapper) {
        this.environment = environment;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public Connection connection(String name) {
        if (!"staging".equals(name) && !"production".equals(name)) {
            throw new ResponseStatusException(CONFLICT, "Desk supports explicit staging or production targets.");
        }
        String prefix = "LOOMAI_DESK_" + name.toUpperCase(java.util.Locale.ROOT);
        String base = environment.getProperty(prefix + "_BASE_URL", "");
        String key = environment.getProperty(prefix + "_SERVICE_KEY", "");
        if (base.isBlank() || key.length() < 32) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "LoomAI Desk is not configured for this environment.");
        }
        URI uri;
        try { uri = URI.create(base); } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Desk service configuration is invalid.");
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
            || uri.getQuery() != null || uri.getFragment() != null || !uri.getPath().matches("/?")) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Desk requires an exact HTTPS service origin.");
        }
        return new Connection(base.replaceAll("/$", ""), key);
    }

    public JsonNode put(Connection connection, String path, Object body) {
        return send(connection, "PUT", path, body);
    }

    public JsonNode get(Connection connection, String path) {
        return send(connection, "GET", path, null);
    }

    public byte[] getPhoto(Connection connection, String path) {
        var response = exchange(connection, "GET", path, null, "image/jpeg", 131072);
        if (response.status() == 404) throw new ResponseStatusException(NOT_FOUND, "Desk profile photo not found.");
        requireSuccess(response);
        byte[] data = response.data();
        if (!"image/jpeg".equalsIgnoreCase(response.contentType()) || data.length < 3
            || data[0] != (byte) 0xff || data[1] != (byte) 0xd8 || data[2] != (byte) 0xff) {
            throw new ResponseStatusException(BAD_GATEWAY, "Desk returned an invalid profile photo.");
        }
        return data;
    }

    private JsonNode send(Connection connection, String method, String path, Object body) {
        var response = exchange(connection, method, path, body, "application/json", 262144);
        requireSuccess(response);
        try {
            return response.data().length == 0 ? mapper.valueToTree(Map.of()) : mapper.readTree(response.data());
        } catch (Exception error) {
            throw new ResponseStatusException(BAD_GATEWAY, "Desk returned an invalid integration response.");
        }
    }

    private void requireSuccess(DeskResponse response) {
        if (response.status() < 200 || response.status() >= 300) {
            throw new ResponseStatusException(BAD_GATEWAY, "Desk rejected the integration operation (HTTP " + response.status() + ").");
        }
    }

    private DeskResponse exchange(Connection connection, String method, String path, Object body, String accept, int maxBytes) {
        if (!path.startsWith("/api/desk/admin/v1/") || path.contains("..")) {
            throw new IllegalArgumentException("A Desk administration path is required.");
        }
        try {
            var request = HttpRequest.newBuilder(URI.create(connection.baseUrl() + path))
                .timeout(Duration.ofSeconds(15)).header("Accept", accept)
                .header("X-LoomAI-Desk-Service-Key", connection.serviceKey());
            if (body == null) request.GET();
            else request.header("Content-Type", "application/json").method(method,
                HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] data;
            try (var input = response.body()) { data = input.readNBytes(maxBytes + 1); }
            if (data.length > maxBytes) throw new IllegalStateException("Oversized Desk response");
            return new DeskResponse(response.statusCode(), response.headers().firstValue("Content-Type").orElse(""), data);
        } catch (ResponseStatusException error) { throw error; }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(BAD_GATEWAY, "Desk integration was interrupted.");
        } catch (Exception error) {
            throw new ResponseStatusException(BAD_GATEWAY, "LoomAI Desk is unavailable; no successful activation was recorded.");
        }
    }

    private record DeskResponse(int status, String contentType, byte[] data) { }

    public record Connection(String baseUrl, String serviceKey) {
        @Override public String toString() { return "DeskConnection[baseUrl=" + baseUrl + "]"; }
    }
}
