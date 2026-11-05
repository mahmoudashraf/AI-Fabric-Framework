package com.loomai.operations.telemetry;

import static com.loomai.operations.telemetry.TelemetryContracts.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = TelemetryApiFilter.PREFIX, produces = MediaType.APPLICATION_JSON_VALUE)
public class TelemetryController {
    private final TelemetryService service;

    public TelemetryController(TelemetryService service) { this.service = service; }

    @ModelAttribute
    public void requireScopedAuthentication(HttpServletRequest request) {
        // A normalized/forwarded route must not inherit the powerful Operations credential.
        if (!(request.getAttribute(TelemetryApiFilter.CREDENTIAL_ATTRIBUTE) instanceof TelemetryRegistry.Credential credential)) {
            throw new TelemetryException(401, "AUTHENTICATION_REQUIRED", "Telemetry authentication is required.");
        }
        if (request.getMethod().equals("GET") != credential.reader()) {
            throw new TelemetryException(403, "TELEMETRY_SCOPE_DENIED", "Credential does not permit this telemetry operation.");
        }
    }

    @PostMapping("/samples")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ingest(HttpServletRequest request, @RequestParam MultiValueMap<String, String> query) {
        query(query, Set.of());
        // Only the explicit telemetry auth boundary can supply these attributes.
        if (!(request.getAttribute(TelemetryApiFilter.SOURCE_ATTRIBUTE) instanceof String serverId)
            || !(request.getAttribute(TelemetryApiFilter.BODY_ATTRIBUTE) instanceof byte[] body)) {
            throw new TelemetryException(401, "AUTHENTICATION_REQUIRED", "Telemetry authentication is required.");
        }
        service.ingest(serverId, body);
    }

    @GetMapping("/servers")
    public List<ServerProjection> servers(@RequestParam MultiValueMap<String, String> query) {
        query(query, Set.of());
        return service.servers();
    }

    @GetMapping("/servers/{serverId}")
    public ServerProjection server(@PathVariable String serverId, @RequestParam MultiValueMap<String, String> query) {
        query(query, Set.of());
        return service.server(serverId);
    }

    @GetMapping("/servers/{serverId}/history")
    public HistoryProjection history(@PathVariable String serverId, @RequestParam MultiValueMap<String, String> query) {
        query(query, Set.of("range", "resourceId"));
        return service.history(serverId, query.getFirst("range"), query.getFirst("resourceId"));
    }

    private static void query(MultiValueMap<String, String> query, Set<String> allowed) {
        if (!allowed.containsAll(query.keySet()) || query.values().stream().anyMatch(values -> values.size() != 1)) {
            throw new TelemetryException(400, "TELEMETRY_QUERY_INVALID", "Telemetry query is invalid.");
        }
    }
}
