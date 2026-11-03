package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.desk.DeskServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static com.ai.fabric.platform.backend.support.SupportContracts.Requester;

/** Provider-neutral requester calls using the server-registered, target-scoped Desk origin. */
@Service
public class SupportTicketClient {
    private final SupportSettings settings;
    private final DeploymentTargetProfileRepository targets;
    private final DeskServiceClient desk;
    private final SupportRequesterAssertionService assertions;
    private final ObjectMapper mapper;
    private final HttpClient http;

    @org.springframework.beans.factory.annotation.Autowired
    public SupportTicketClient(SupportSettings settings, DeploymentTargetProfileRepository targets,
        DeskServiceClient desk, SupportRequesterAssertionService assertions, ObjectMapper mapper) {
        this(settings, targets, desk, assertions, mapper,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    SupportTicketClient(SupportSettings settings, DeploymentTargetProfileRepository targets,
        DeskServiceClient desk, SupportRequesterAssertionService assertions, ObjectMapper mapper, HttpClient http) {
        this.settings = settings; this.targets = targets; this.desk = desk;
        this.assertions = assertions; this.mapper = mapper; this.http = http;
    }

    public JsonNode request(Requester owner, String method, String path, Object body, String key) {
        return request(owner, method, path, body, key, Map.of());
    }
    public JsonNode request(Requester owner, String method, String path, Object body, String key, Map<String, String> runtimeHeaders) {
        return request(owner, method, path, body, key, runtimeHeaders, null, null);
    }
    public JsonNode request(Requester owner, String method, String path, Object body, String key,
        Map<String, String> runtimeHeaders, String runtimePrincipal, String runtimeConversationId) {
        if (!path.matches("/[A-Za-z0-9/_?=&.%-]+") || path.contains("..")
            || !java.util.Set.of("GET", "POST", "PUT").contains(method)) throw new IllegalArgumentException("Bounded support requester operation required");
        try {
            var connection = connection();
            var request = HttpRequest.newBuilder(URI.create(connection.baseUrl() + "/api/desk/requester/v1/spaces/" + spaceId() + path))
                .timeout(Duration.ofSeconds(path.endsWith("/start") ? 120 : 20))
                .header("Authorization", "Bearer " + assertions.issue(owner, spaceId(), runtimePrincipal, runtimeConversationId))
                .header("X-LoomAI-Requester-Server-Key", assertions.serverKey()).header("Accept", "application/json");
            if (key != null) {
                if (!key.matches("[A-Za-z0-9._:-]{1,128}")) throw SupportPolicy.bad("SUPPORT_IDEMPOTENCY_KEY_INVALID");
                request.header("Idempotency-Key", key);
            }
            if (!runtimeHeaders.isEmpty()) {
                String assertion = runtimeHeaders.get("X-AIFABRIC-RUNTIME-AUTHORIZATION");
                String runtimeKey = runtimeHeaders.get(com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER);
                if (assertion == null || runtimeKey == null) throw SupportPolicy.unavailable();
                request.header("X-LoomAI-Runtime-Authorization", assertion).header("X-LoomAI-Runtime-Api-Key", runtimeKey);
            }
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (body != null) request.header("Content-Type", "application/json");
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (var stream = response.body()) { bytes = stream.readNBytes(1_048_577); }
            if (bytes.length > 1_048_576) throw SupportPolicy.unavailable();
            JsonNode value = bytes.length == 0 ? mapper.createObjectNode() : mapper.readTree(bytes);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                int status = response.statusCode();
                String code = value.path("code").asText("SUPPORT_TICKET_PROVIDER_UNAVAILABLE");
                if (!code.matches("[A-Z][A-Z0-9_]{1,100}")) code = "SUPPORT_TICKET_PROVIDER_UNAVAILABLE";
                throw new ResponseStatusException(HttpStatusCode.valueOf(java.util.Set.of(400,401,403,404,409,422).contains(status) ? status : 503), code);
            }
            return value;
        } catch (ResponseStatusException error) { throw error; }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw SupportPolicy.unavailable(); }
        catch (Exception error) { throw SupportPolicy.unavailable(); }
    }

    public String spaceId() { return settings.deskBindingId(); }
    public DeskServiceClient.Connection connection() {
        var binding = settings.binding();
        var target = targets.findById(binding.targetProfileId()).filter(value -> value.isActive()).orElseThrow(SupportPolicy::unavailable);
        return desk.connection(target.getEnvironmentName());
    }
}
