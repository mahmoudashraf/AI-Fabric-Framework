package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class DeploymentReleaseEndpointService {
    private final ObjectMapper objectMapper;

    public DeploymentReleaseEndpointService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String runtimeBaseUrl(DeploymentReleaseEntity release) {
        JsonNode details = details(release);
        return endpoint(List.of(
            details.path("runtimeBaseUrl"),
            details.path("runtime").path("baseUrl"),
            details.path("railway").path("services").path("runtime").path("baseUrl"),
            details.path("runtimeFqdn"),
            details.path("fqdn")
        ), true);
    }

    public String connectorBaseUrl(DeploymentReleaseEntity release) {
        JsonNode details = details(release);
        return endpoint(List.of(
            details.path("connectorBaseUrl"),
            details.path("connector").path("baseUrl"),
            details.path("railway").path("services").path("connector").path("baseUrl"),
            details.path("railway").path("services").path("restConnector").path("baseUrl"),
            details.path("connectorFqdn")
        ), false);
    }

    public JsonNode details(DeploymentReleaseEntity release) {
        try {
            JsonNode details = objectMapper.readTree(release.getProvisioningDetailsJson());
            if (details == null || !details.isObject()) throw new IllegalArgumentException();
            return details;
        } catch (Exception error) {
            throw conflict("Release provider evidence is unavailable; reconcile the selected release.");
        }
    }

    private String endpoint(List<JsonNode> candidates, boolean required) {
        LinkedHashSet<String> endpoints = new LinkedHashSet<>();
        for (JsonNode candidate : candidates) {
            if (candidate.isMissingNode() || candidate.isNull()) continue;
            if (!candidate.isTextual()) throw conflict("Release endpoint evidence is invalid.");
            if (StringUtils.hasText(candidate.asText())) endpoints.add(normalize(candidate.asText()));
        }
        if (endpoints.size() > 1) throw conflict("Release endpoint evidence is ambiguous; reconcile the selected release.");
        if (endpoints.isEmpty() && required) throw conflict("Release runtime endpoint is unavailable; reconcile the selected release.");
        return endpoints.stream().findFirst().orElse(null);
    }

    private String normalize(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException();
            String normalized = uri.toString();
            return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        } catch (Exception error) {
            throw conflict("Release endpoint evidence must contain one valid HTTP(S) base URL.");
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(CONFLICT, message);
    }
}
