package com.ai.fabric.platform.backend.infrastructure.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpStatus.*;

@Service
public class InfrastructureMonitoringService {
    private static final List<String> SERVER_FIELDS = List.of("serverId", "status", "observedAt", "receivedAt", "complete", "source", "host", "resources", "warnings");
    private static final List<String> HOST_FIELDS = List.of("cpuThreads", "cpuUsagePercent", "load1", "load5", "load15", "memoryTotalBytes", "memoryAvailableBytes", "swapTotalBytes", "swapUsedBytes", "swapInBytesPerSecond", "swapOutBytesPerSecond", "diskTotalBytes", "diskFreeBytes", "inodesTotal", "inodesFree", "networkRxBytesPerSecond", "networkTxBytesPerSecond", "cpuStealPercent");
    private static final List<String> RESOURCE_FIELDS = List.of("resourceId", "name", "image", "providerResourceId", "providerKind", "role", "startedAt", "state", "health", "memoryWorkingSetBytes", "memoryLimitBytes", "memoryReservationBytes", "memorySwapLimitBytes", "cpuCoresUsed", "cpuQuota", "cpusetCpus", "cpuThrottledPeriodsPercent", "restartCount", "oomKilled");
    private final InfrastructureMonitoringProperties properties;
    private final InfrastructureMonitoringClient client;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final Map<InfrastructureMonitoringProperties.Source, Cached> latest = new ConcurrentHashMap<>();

    public InfrastructureMonitoringService(InfrastructureMonitoringProperties properties, InfrastructureMonitoringClient client,
                                           ObjectMapper mapper, JdbcTemplate jdbc) {
        this.properties = properties; this.client = client; this.mapper = mapper; this.jdbc = jdbc;
    }

    public JsonNode fleet() {
        var result = mapper.createObjectNode();
        var sources = result.putArray("sources");
        var servers = result.putArray("servers");
        if (!properties.isEnabled()) {
            sources.addObject().put("id", "monitoring").put("status", "DISABLED").put("message", "Infrastructure monitoring is disabled.");
            return result;
        }
        List<InfrastructureMonitoringProperties.Source> configured;
        try { configured = properties.sources(mapper); }
        catch (MonitoringUnavailableException error) {
            sources.addObject().put("id", "monitoring").put("status", "UNAVAILABLE").put("message", error.getMessage());
            return result;
        }
        long deadline = System.nanoTime() + 5_000_000_000L;
        for (var source : configured) {
            try {
                if (System.nanoTime() >= deadline) throw new MonitoringUnavailableException("MONITORING_QUERY_DEADLINE");
                servers.add(server(source));
                sources.addObject().put("id", source.id()).put("status", "AVAILABLE").putNull("message");
            } catch (MonitoringUnavailableException error) {
                sources.addObject().put("id", source.id()).put("status", "UNAVAILABLE").put("message", error.getMessage());
            }
        }
        return result;
    }

    public JsonNode server(String serverId) { return server(source(serverId)); }

    private ObjectNode server(InfrastructureMonitoringProperties.Source source) {
        var key = source;
        var cached = latest.get(key);
        JsonNode observed;
        if (cached != null && cached.expiresNanos() > System.nanoTime()) observed = cached.value().deepCopy();
        else {
            observed = client.read(source, "servers/" + source.id());
            if (!observed.path("resources").isArray() || observed.path("resources").size() > 200) {
                throw new MonitoringUnavailableException("MONITORING_SOURCE_INVALID");
            }
            latest.put(key, new Cached(observed.deepCopy(), System.nanoTime() + 10_000_000_000L));
            if (latest.size() > 16) latest.keySet().removeIf(item -> !item.equals(key));
        }
        var view = project(observed, SERVER_FIELDS);
        view.put("displayName", source.displayName()).put("environment", source.environment());
        if (observed.path("host").isObject()) view.set("host", project(observed.path("host"), HOST_FIELDS));
        else view.putNull("host");
        var resources = view.putArray("resources");
        var ownership = ownership(source);
        for (var item : observed.path("resources")) {
            var resource = project(item, RESOURCE_FIELDS);
            var owner = ownership.get(item.path("providerResourceId").asText());
            if (owner != null) {
                resource.put("deploymentId", owner.deploymentId()).put("deploymentName", owner.deploymentName())
                    .put("targetProfileId", owner.targetProfileId());
            }
            resources.add(resource);
        }
        return view;
    }

    public JsonNode history(String serverId, String range, String resourceId) {
        if (!List.of("1h", "24h", "7d").contains(range)
            || resourceId != null && !resourceId.matches("[a-f0-9]{12,64}")) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid monitoring history scope.");
        }
        var source = source(serverId);
        var observed = client.read(source, "servers/" + serverId + "/history?range=" + range
            + (resourceId == null ? "" : "&resourceId=" + resourceId));
        if (!observed.path("points").isArray() || observed.path("points").size() > 600
            || !range.equals(observed.path("range").asText())
            || !java.util.Objects.equals(resourceId, observed.path("resourceId").isNull() ? null : observed.path("resourceId").textValue())) {
            throw new MonitoringUnavailableException("MONITORING_SOURCE_INVALID");
        }
        return project(observed, List.of("serverId", "resourceId", "range", "bucketSeconds", "points"));
    }

    private InfrastructureMonitoringProperties.Source source(String id) {
        if (!InfrastructureMonitoringProperties.validId(id)) throw new ResponseStatusException(BAD_REQUEST, "Invalid server id.");
        return properties.sources(mapper).stream().filter(item -> item.id().equals(id)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Monitoring server not configured."));
    }

    private Map<String, Ownership> ownership(InfrastructureMonitoringProperties.Source source) {
        var values = new LinkedHashMap<String, Ownership>();
        var ambiguous = new java.util.HashSet<String>();
        if (source.providerServerUuid() == null) return values;
        jdbc.query("""
            SELECT h.provider_resource_uuid, h.deployment_id, d.name, h.target_profile_id,
                   h.provider_server_uuid, p.provider_config_json
            FROM deployment_provider_resource_handles h
            JOIN platform_deployments d ON d.id=h.deployment_id
            JOIN deployment_target_profiles p ON p.id=h.target_profile_id
            WHERE h.provider_type='COOLIFY' AND h.status<>'DELETED'
              AND (h.provider_server_uuid=? OR h.provider_server_uuid IS NULL)
            ORDER BY h.updated_at DESC
            """, rs -> {
                String id = rs.getString(1);
                if (rs.getString(5) == null) {
                    try {
                        if (!source.providerServerUuid().equals(mapper.readTree(rs.getString(6)).path("serverUuid").asText())) return;
                    } catch (com.fasterxml.jackson.core.JsonProcessingException error) { return; }
                }
                var owner = new Ownership(rs.getString(2), rs.getString(3), rs.getString(4));
                var existing = values.putIfAbsent(id, owner);
                if (existing != null && !existing.deploymentId().equals(owner.deploymentId())) ambiguous.add(id);
            }, source.providerServerUuid());
        ambiguous.forEach(values::remove);
        return values;
    }

    private ObjectNode project(JsonNode source, List<String> fields) {
        var target = mapper.createObjectNode();
        for (var field : fields) if (source.has(field)) target.set(field, source.get(field).deepCopy());
        return target;
    }

    private record Ownership(String deploymentId, String deploymentName, String targetProfileId) { }
    private record Cached(JsonNode value, long expiresNanos) { }
}
