package com.ai.fabric.runtime.smartbrain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URI;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "loomai.smart-brain.enabled", havingValue = "true")
public class SmartBrainConfigurationService {

    public static final String CONTRACT_VERSION = "LOOMAI_SMART_BRAIN_CONFIG_V1";
    public static final String SUPPORTED_SPECIALIST = "smart-brain-event-analyst@1";

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final String manifestLocation;

    private volatile SmartBrainRuntimeConfig config;
    private volatile Map<String, SmartBrainRuntimeConfig.Trigger> triggersByCode = Map.of();

    public SmartBrainConfigurationService(
        ResourceLoader resourceLoader,
        ObjectMapper objectMapper,
        @Value("${loomai.deployment.manifest-url:}") String manifestLocation
    ) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.manifestLocation = manifestLocation;
    }

    @PostConstruct
    void load() {
        if (!StringUtils.hasText(manifestLocation)) {
            throw new IllegalStateException("Smart Brain requires LOOMAI_DEPLOYMENT_MANIFEST_URL.");
        }
        try {
            Resource resource = resourceLoader.getResource(manifestLocation.trim());
            try (InputStream input = resource.getInputStream()) {
                JsonNode manifest = objectMapper.readTree(input);
                if (!"SMART_BRAIN".equals(manifest.path("behaviorConfig").path("type").asText(""))) {
                    throw new IllegalStateException("Deployment manifest does not declare SMART_BRAIN behavior.");
                }
                this.config = parse(manifest.path("behaviorConfig").path("smartBrain"));
                LinkedHashMap<String, SmartBrainRuntimeConfig.Trigger> registry = new LinkedHashMap<>();
                for (SmartBrainRuntimeConfig.Trigger trigger : config.triggers()) {
                    if (trigger.enabled()) {
                        registry.put(trigger.code(), trigger);
                    }
                }
                if (registry.isEmpty()) {
                    throw new IllegalStateException("Smart Brain requires at least one enabled trigger.");
                }
                this.triggersByCode = Map.copyOf(registry);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(
                "Failed to load immutable Smart Brain configuration from deployment manifest.",
                exception
            );
        }
    }

    public SmartBrainRuntimeConfig current() {
        return config;
    }

    public SmartBrainRuntimeConfig.Trigger requireTrigger(String code) {
        SmartBrainRuntimeConfig.Trigger trigger = triggersByCode.get(code);
        if (trigger == null) {
            throw new SmartBrainRequestException("SMART_BRAIN_TRIGGER_NOT_REGISTERED", "Unknown Smart Brain trigger.");
        }
        return trigger;
    }

    public List<String> triggerCodes() {
        return List.copyOf(triggersByCode.keySet());
    }

    private SmartBrainRuntimeConfig parse(JsonNode root) {
        if (!CONTRACT_VERSION.equals(root.path("contractVersion").asText(""))) {
            throw new IllegalStateException("Unsupported Smart Brain configuration contract.");
        }
        int maxEventBytes = root.path("maxEventBytes").asInt(-1);
        if (maxEventBytes < 1_024 || maxEventBytes > 1_048_576) {
            throw new IllegalStateException("Invalid Smart Brain maxEventBytes.");
        }
        List<SmartBrainRuntimeConfig.Trigger> triggers = new ArrayList<>();
        for (JsonNode node : root.path("triggers")) {
            String code = required(node, "code");
            String specialistRef = required(node, "specialistRef");
            if (!SUPPORTED_SPECIALIST.equals(specialistRef)) {
                throw new IllegalStateException("Unsupported Smart Brain specialist: " + specialistRef);
            }
            List<String> eventTypes = strings(node.path("eventTypes"));
            if (eventTypes.isEmpty()) {
                throw new IllegalStateException("Smart Brain trigger has no event types: " + code);
            }
            triggers.add(new SmartBrainRuntimeConfig.Trigger(
                code,
                required(node, "name"),
                eventTypes,
                specialistRef,
                !node.path("enabled").isBoolean() || node.path("enabled").asBoolean()
            ));
        }
        List<SmartBrainRuntimeConfig.Schedule> schedules = new ArrayList<>();
        for (JsonNode node : root.path("schedules")) {
            String zoneId = required(node, "zoneId");
            ZoneId.of(zoneId);
            schedules.add(new SmartBrainRuntimeConfig.Schedule(
                required(node, "code"),
                required(node, "triggerCode"),
                required(node, "cron"),
                zoneId,
                !node.path("enabled").isBoolean() || node.path("enabled").asBoolean()
            ));
        }
        JsonNode deliveryNode = root.path("delivery");
        String mode = required(deliveryNode, "mode");
        String callbackUrl = deliveryNode.path("callbackUrl").asText("").trim();
        if (!List.of("POLL", "SIGNED_WEBHOOK").contains(mode)) {
            throw new IllegalStateException("Unsupported Smart Brain delivery mode.");
        }
        if ("SIGNED_WEBHOOK".equals(mode)) {
            URI uri = URI.create(callbackUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalStateException("Smart Brain callback URL must be HTTPS without user info.");
            }
        } else if (StringUtils.hasText(callbackUrl)) {
            throw new IllegalStateException("POLL delivery cannot have a callback URL.");
        }
        return new SmartBrainRuntimeConfig(
            CONTRACT_VERSION,
            maxEventBytes,
            List.copyOf(triggers),
            List.copyOf(schedules),
            new SmartBrainRuntimeConfig.Delivery(mode, StringUtils.hasText(callbackUrl) ? callbackUrl : null)
        );
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Missing Smart Brain field: " + field);
        }
        return value;
    }

    private List<String> strings(JsonNode values) {
        List<String> result = new ArrayList<>();
        if (values.isArray()) {
            values.forEach(value -> {
                String text = value.asText("").trim();
                if (StringUtils.hasText(text) && !result.contains(text)) {
                    result.add(text);
                }
            });
        }
        return List.copyOf(result);
    }
}
