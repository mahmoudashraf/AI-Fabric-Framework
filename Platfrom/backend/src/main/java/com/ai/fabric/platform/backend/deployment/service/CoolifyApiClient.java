package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CoolifyApiClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CoolifyApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    public JsonNode health(CoolifyConnection connection) {
        return requestJson(connection, "GET", "/health", null, true);
    }

    public String version(CoolifyConnection connection) {
        JsonNode response = requestJson(connection, "GET", "/version", null, true);
        if (response.isTextual()) {
            return response.asText();
        }
        String version = response.path("version").asText(null);
        return StringUtils.hasText(version) ? version : response.toString();
    }

    public List<CoolifyApplicationSummary> listApplications(CoolifyConnection connection) {
        JsonNode response = requestJson(connection, "GET", "/applications", null, true);
        List<CoolifyApplicationSummary> applications = new ArrayList<>();
        if (response.isArray()) {
            for (JsonNode item : response) {
                applications.add(toApplication(item));
            }
        }
        return applications;
    }

    public Optional<CoolifyApplicationSummary> getApplication(CoolifyConnection connection, String uuid) {
        JsonNode response = requestJson(
            connection,
            "GET",
            "/applications/" + encodePath(uuid),
            null,
            false
        );
        if (response == null || response.isMissingNode() || response.isNull()) {
            return Optional.empty();
        }
        return Optional.of(toApplication(response));
    }

    public String createDockerImageApplication(CoolifyConnection connection,
                                               CoolifyCreateDockerImageApplicationRequest request) {
        ObjectNode body = dockerImageApplicationBody(request);
        JsonNode response = requestJson(connection, "POST", "/applications/dockerimage", body, true);
        String uuid = response.path("uuid").asText(null);
        if (!StringUtils.hasText(uuid)) {
            throw new CoolifyApiException("Coolify application create did not return an application UUID.", 201, "/applications/dockerimage");
        }
        return uuid;
    }

    public String createPublicApplication(CoolifyConnection connection,
                                          CoolifyCreatePublicApplicationRequest request) {
        ObjectNode body = publicApplicationBody(request);
        JsonNode response = requestJson(connection, "POST", "/applications/public", body, true);
        String uuid = response.path("uuid").asText(null);
        if (!StringUtils.hasText(uuid)) {
            throw new CoolifyApiException("Coolify public application create did not return an application UUID.", 201, "/applications/public");
        }
        return uuid;
    }

    public void updateDockerImageApplication(CoolifyConnection connection,
                                             String uuid,
                                             CoolifyCreateDockerImageApplicationRequest request) {
        requestJson(
            connection,
            "PATCH",
            "/applications/" + encodePath(uuid),
            dockerImageApplicationUpdateBody(request),
            true
        );
    }

    public void updatePublicApplication(CoolifyConnection connection,
                                        String uuid,
                                        CoolifyCreatePublicApplicationRequest request) {
        requestJson(
            connection,
            "PATCH",
            "/applications/" + encodePath(uuid),
            publicApplicationUpdateBody(request),
            true
        );
    }

    public int updateEnvironmentVariables(CoolifyConnection connection, String uuid, List<CoolifyEnvVar> envVars) {
        if (envVars == null || envVars.isEmpty()) {
            return 0;
        }
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode data = body.putArray("data");
        for (CoolifyEnvVar envVar : envVars) {
            ObjectNode item = data.addObject();
            item.put("key", envVar.key());
            item.put("value", envVar.value() == null ? "" : envVar.value());
            item.put("is_preview", envVar.preview());
            item.put("is_literal", envVar.literal());
            item.put("is_multiline", envVar.multiline());
            item.put("is_shown_once", envVar.shownOnce());
        }
        JsonNode response = requestJson(connection, "PATCH", "/applications/" + encodePath(uuid) + "/envs/bulk", body, true);
        return response.isArray() ? response.size() : envVars.size();
    }

    public CoolifyActionResponse start(CoolifyConnection connection, String uuid, boolean force, boolean instantDeploy) {
        return action(
            connection,
            "/applications/" + encodePath(uuid) + "/start?force=" + force + "&instant_deploy=" + instantDeploy
        );
    }

    public CoolifyActionResponse stop(CoolifyConnection connection, String uuid, boolean dockerCleanup) {
        return action(connection, "/applications/" + encodePath(uuid) + "/stop?docker_cleanup=" + dockerCleanup);
    }

    public CoolifyActionResponse restart(CoolifyConnection connection, String uuid) {
        return action(connection, "/applications/" + encodePath(uuid) + "/restart");
    }

    public CoolifyActionResponse delete(CoolifyConnection connection,
                                        String uuid,
                                        boolean deleteConfigurations,
                                        boolean deleteVolumes,
                                        boolean dockerCleanup,
                                        boolean deleteConnectedNetworks) {
        String path = "/applications/" + encodePath(uuid)
            + "?delete_configurations=" + deleteConfigurations
            + "&delete_volumes=" + deleteVolumes
            + "&docker_cleanup=" + dockerCleanup
            + "&delete_connected_networks=" + deleteConnectedNetworks;
        return action(connection, "DELETE", path);
    }

    public String logs(CoolifyConnection connection, String uuid, int lines) {
        int normalizedLines = Math.max(1, Math.min(lines, 1000));
        JsonNode response = requestJson(
            connection,
            "GET",
            "/applications/" + encodePath(uuid) + "/logs?lines=" + normalizedLines,
            null,
            true
        );
        return response.path("logs").asText("");
    }

    private CoolifyActionResponse action(CoolifyConnection connection, String path) {
        return action(connection, "GET", path);
    }

    private CoolifyActionResponse action(CoolifyConnection connection, String method, String path) {
        JsonNode response = requestJson(connection, method, path, null, true);
        return new CoolifyActionResponse(
            response.path("message").asText("Coolify action accepted."),
            response.path("deployment_uuid").asText(null),
            response
        );
    }

    private ObjectNode dockerImageApplicationBody(CoolifyCreateDockerImageApplicationRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        put(body, "project_uuid", request.projectUuid());
        put(body, "server_uuid", request.serverUuid());
        put(body, "environment_name", request.environmentName());
        put(body, "environment_uuid", request.environmentUuid());
        put(body, "docker_registry_image_name", request.imageRepository());
        put(body, "docker_registry_image_tag", request.imageTag());
        put(body, "ports_exposes", request.portsExposes());
        put(body, "destination_uuid", request.destinationUuid());
        put(body, "name", request.name());
        put(body, "description", request.description());
        put(body, "domains", request.domains());
        body.put("health_check_enabled", request.healthCheckEnabled());
        put(body, "health_check_path", request.healthCheckPath());
        put(body, "health_check_port", request.healthCheckPort());
        body.put("instant_deploy", request.instantDeploy());
        body.put("is_force_https_enabled", request.forceHttps());
        body.put("autogenerate_domain", request.autogenerateDomain());
        return body;
    }

    private ObjectNode dockerImageApplicationUpdateBody(CoolifyCreateDockerImageApplicationRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        put(body, "docker_registry_image_name", request.imageRepository());
        put(body, "docker_registry_image_tag", request.imageTag());
        put(body, "ports_exposes", request.portsExposes());
        put(body, "name", request.name());
        put(body, "description", request.description());
        put(body, "domains", request.domains());
        body.put("health_check_enabled", request.healthCheckEnabled());
        put(body, "health_check_path", request.healthCheckPath());
        put(body, "health_check_port", request.healthCheckPort());
        body.put("instant_deploy", request.instantDeploy());
        body.put("is_force_https_enabled", request.forceHttps());
        return body;
    }

    private ObjectNode publicApplicationBody(CoolifyCreatePublicApplicationRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        put(body, "project_uuid", request.projectUuid());
        put(body, "server_uuid", request.serverUuid());
        put(body, "environment_name", request.environmentName());
        put(body, "environment_uuid", request.environmentUuid());
        put(body, "git_repository", request.gitRepository());
        put(body, "git_branch", request.gitBranch());
        put(body, "build_pack", request.buildPack());
        put(body, "base_directory", request.baseDirectory());
        put(body, "dockerfile_location", request.dockerfileLocation());
        put(body, "ports_exposes", request.portsExposes());
        put(body, "destination_uuid", request.destinationUuid());
        put(body, "name", request.name());
        put(body, "description", request.description());
        put(body, "domains", request.domains());
        body.put("health_check_enabled", request.healthCheckEnabled());
        put(body, "health_check_path", request.healthCheckPath());
        put(body, "health_check_port", request.healthCheckPort());
        body.put("instant_deploy", request.instantDeploy());
        body.put("is_auto_deploy_enabled", request.autoDeployEnabled());
        body.put("is_force_https_enabled", request.forceHttps());
        body.put("autogenerate_domain", request.autogenerateDomain());
        return body;
    }

    private ObjectNode publicApplicationUpdateBody(CoolifyCreatePublicApplicationRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        put(body, "git_repository", request.gitRepository());
        put(body, "git_branch", request.gitBranch());
        put(body, "build_pack", request.buildPack());
        put(body, "base_directory", request.baseDirectory());
        put(body, "dockerfile_location", request.dockerfileLocation());
        put(body, "ports_exposes", request.portsExposes());
        put(body, "name", request.name());
        put(body, "description", request.description());
        put(body, "domains", request.domains());
        body.put("health_check_enabled", request.healthCheckEnabled());
        put(body, "health_check_path", request.healthCheckPath());
        put(body, "health_check_port", request.healthCheckPort());
        body.put("instant_deploy", request.instantDeploy());
        body.put("is_auto_deploy_enabled", request.autoDeployEnabled());
        body.put("is_force_https_enabled", request.forceHttps());
        return body;
    }

    private JsonNode requestJson(CoolifyConnection connection,
                                 String method,
                                 String path,
                                 JsonNode body,
                                 boolean failOnNotFound) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint(connection, path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + connection.token());
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 && !failOnNotFound) {
                return null;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CoolifyApiException(
                    "Coolify API request failed with HTTP " + response.statusCode() + " for " + sanitizedPath(path) + ".",
                    response.statusCode(),
                    sanitizedPath(path)
                );
            }
            return readJson(response.body());
        } catch (CoolifyApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Coolify API request failed for " + sanitizedPath(path) + ".", ex);
        }
    }

    private URI endpoint(CoolifyConnection connection, String path) {
        String baseUrl = connection.baseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!baseUrl.endsWith("/api/v1")) {
            baseUrl += "/api/v1";
        }
        return URI.create(baseUrl + (path.startsWith("/") ? path : "/" + path));
    }

    private CoolifyApplicationSummary toApplication(JsonNode node) {
        return new CoolifyApplicationSummary(
            node.path("uuid").asText(null),
            node.path("name").asText(null),
            node.path("fqdn").asText(null),
            node.path("status").asText(null),
            node.path("docker_registry_image_name").asText(null),
            node.path("docker_registry_image_tag").asText(null),
            node
        );
    }

    private JsonNode readJson(String value) {
        try {
            if (value == null || value.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            return objectMapper.getNodeFactory().textNode(value);
        }
    }

    private void put(ObjectNode body, String field, String value) {
        if (StringUtils.hasText(value)) {
            body.put(field, value.trim());
        }
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String sanitizedPath(String path) {
        int queryStart = path.indexOf('?');
        return queryStart < 0 ? path : path.substring(0, queryStart);
    }
}
