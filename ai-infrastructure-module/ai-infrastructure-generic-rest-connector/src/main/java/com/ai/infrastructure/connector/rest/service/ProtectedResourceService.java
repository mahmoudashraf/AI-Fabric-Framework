package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.util.Hashing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProtectedResourceService {

    private final RestRoutingConfig config;
    private final ObjectMapper objectMapper;

    public ProtectedResourceService(RestRoutingConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public BoundRequest apply(
        String bindingRef,
        String connectionProfileRef,
        List<String> requiredCapabilityGrants,
        List<RestRoutingConfig.ResourcePlacement> placements,
        String path,
        Map<String, Object> query,
        Map<String, String> headers,
        Object body,
        Map<String, Object> callerParams
    ) {
        RestRoutingConfig.ProtectedResourceBinding binding = requireBinding(
            bindingRef,
            connectionProfileRef,
            requiredCapabilityGrants
        );
        Map<String, Object> safeQuery = new LinkedHashMap<>(query != null ? query : Map.of());
        Map<String, String> safeHeaders = new LinkedHashMap<>(headers != null ? headers : Map.of());
        JsonNode safeBody = body != null ? objectMapper.valueToTree(body) : objectMapper.createObjectNode();
        String safePath = path;
        for (RestRoutingConfig.ResourcePlacement placement : placements != null ? placements : List.<RestRoutingConfig.ResourcePlacement>of()) {
            if (placement == null || placement.getTarget() == null) {
                continue;
            }
            rejectCallerOverride(callerParams, placement);
            String field = placement.getField();
            switch (placement.getTarget()) {
                case QUERY -> safeQuery.put(field, binding.getResourceId());
                case HEADER -> safeHeaders.put(field, binding.getResourceId());
                case PATH -> safePath = safePath.replace(
                    "{" + field + "}",
                    URLEncoder.encode(binding.getResourceId(), StandardCharsets.UTF_8).replace("+", "%20")
                );
                case BODY -> safeBody = setAtPointer(safeBody, placement.getJsonPointer(), binding.getResourceId());
            }
        }
        Object normalizedBody = safeBody.isMissingNode() || safeBody.isNull() ? null : objectMapper.convertValue(safeBody, Object.class);
        return new BoundRequest(safePath, Map.copyOf(safeQuery), Map.copyOf(safeHeaders), normalizedBody, binding);
    }

    public RestRoutingConfig.ProtectedResourceBinding requireBinding(String bindingRef, String connectionProfileRef) {
        return requireBinding(bindingRef, connectionProfileRef, List.of());
    }

    public RestRoutingConfig.ProtectedResourceBinding requireBinding(
        String bindingRef,
        String connectionProfileRef,
        List<String> requiredCapabilityGrants
    ) {
        RestRoutingConfig.ProtectedResourceBinding binding = StringUtils.hasText(bindingRef)
            ? config.getProtectedResources().get(bindingRef.trim())
            : null;
        if (binding == null) {
            throw new ProviderCallException(ProviderErrorClass.RESOURCE_ACCESS_DENIED, 0, "Protected resource binding is not configured.");
        }
        if (!StringUtils.hasText(binding.getResourceId())) {
            throw new ProviderCallException(ProviderErrorClass.RESOURCE_ACCESS_DENIED, 0, "Protected resource binding has no resource identifier.");
        }
        if (!StringUtils.hasText(connectionProfileRef)
            || !connectionProfileRef.trim().equals(binding.getConnectionProfileRef())) {
            throw new ProviderCallException(ProviderErrorClass.RESOURCE_ACCESS_DENIED, 0, "Protected resource binding does not belong to the selected connection profile.");
        }
        RestRoutingConfig.ConnectionProfile profile = config.getConnectionProfiles().get(connectionProfileRef.trim());
        if (profile == null || !StringUtils.hasText(profile.getEnvironment())
            || !profile.getEnvironment().trim().equalsIgnoreCase(binding.getEnvironment())) {
            throw new ProviderCallException(ProviderErrorClass.RESOURCE_ACCESS_DENIED, 0, "Protected resource environment does not match the connection profile.");
        }
        if (requiredCapabilityGrants != null
            && (binding.getCapabilityGrants() == null || !binding.getCapabilityGrants().containsAll(requiredCapabilityGrants))) {
            throw new ProviderCallException(ProviderErrorClass.CAPABILITY_DENIED, 0, "Protected resource capability grant is incomplete.");
        }
        if (requiredCapabilityGrants != null
            && (profile.getCapabilityGrants() == null || !profile.getCapabilityGrants().containsAll(requiredCapabilityGrants))) {
            throw new ProviderCallException(ProviderErrorClass.CAPABILITY_DENIED, 0, "Connection profile capability grant is incomplete.");
        }
        return binding;
    }

    public String fingerprint(RestRoutingConfig.ProtectedResourceBinding binding) {
        return Hashing.sha256Hex(binding.getResourceType() + ":" + binding.getResourceId());
    }

    private void rejectCallerOverride(Map<String, Object> callerParams, RestRoutingConfig.ResourcePlacement placement) {
        if (callerParams == null || callerParams.isEmpty() || !StringUtils.hasText(placement.getField())) {
            return;
        }
        if (callerParams.containsKey(placement.getField())) {
            throw new ProviderCallException(ProviderErrorClass.RESOURCE_ACCESS_DENIED, 0, "Caller input attempted to override a protected resource value.");
        }
    }

    private JsonNode setAtPointer(JsonNode root, String pointer, String value) {
        if (!StringUtils.hasText(pointer) || !pointer.startsWith("/") || !(root instanceof ObjectNode objectRoot)) {
            throw new ProviderCallException(ProviderErrorClass.BAD_REQUEST, 0, "Protected body placement requires an object body and JSON Pointer.");
        }
        String[] segments = pointer.substring(1).split("/");
        ObjectNode current = objectRoot;
        for (int i = 0; i < segments.length - 1; i++) {
            String segment = unescape(segments[i]);
            JsonNode next = current.get(segment);
            ObjectNode nextObject;
            if (next instanceof ObjectNode existingObject) {
                nextObject = existingObject;
            } else {
                nextObject = objectMapper.createObjectNode();
                current.set(segment, nextObject);
            }
            current = nextObject;
        }
        current.put(unescape(segments[segments.length - 1]), value);
        return objectRoot;
    }

    private String unescape(String value) {
        return value.replace("~1", "/").replace("~0", "~");
    }

    public record BoundRequest(
        String path,
        Map<String, Object> query,
        Map<String, String> headers,
        Object body,
        RestRoutingConfig.ProtectedResourceBinding binding
    ) {
    }
}
