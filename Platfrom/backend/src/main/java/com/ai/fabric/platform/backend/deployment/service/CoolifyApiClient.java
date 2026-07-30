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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CoolifyApiClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
    private static final int MAX_RETRY_ATTEMPTS = 6;
    private static final Duration RETRY_BASE_DELAY = Duration.ofSeconds(1);

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

    public List<CoolifyDatabaseSummary> listDatabases(CoolifyConnection connection) {
        JsonNode response = requestJson(connection, "GET", "/databases", null, true);
        List<CoolifyDatabaseSummary> databases = new ArrayList<>();
        if (response.isArray()) {
            for (JsonNode item : response) {
                databases.add(toDatabase(item));
            }
        }
        return databases;
    }

    public List<CoolifyProjectSummary> listProjects(CoolifyConnection connection) {
        JsonNode response = requestJson(connection, "GET", "/projects", null, true);
        List<CoolifyProjectSummary> projects = new ArrayList<>();
        if (response.isArray()) {
            for (JsonNode item : response) {
                projects.add(toProject(item));
            }
        }
        return projects;
    }

    public String createProject(CoolifyConnection connection, String name, String description) {
        ObjectNode body = objectMapper.createObjectNode();
        put(body, "name", name);
        put(body, "description", description);
        JsonNode response = requestJson(connection, "POST", "/projects", body, true);
        String uuid = textFirst(response, "uuid");
        if (!StringUtils.hasText(uuid)) {
            throw new CoolifyApiException("Coolify project create did not return a project UUID.", 201, "/projects");
        }
        return uuid;
    }

    public List<CoolifyEnvironmentSummary> listEnvironments(CoolifyConnection connection, String projectUuid) {
        JsonNode response = requestJson(connection, "GET", "/projects/" + encodePath(projectUuid) + "/environments", null, true);
        List<CoolifyEnvironmentSummary> environments = new ArrayList<>();
        if (response.isArray()) {
            for (JsonNode item : response) {
                environments.add(toEnvironment(projectUuid, item));
            }
        }
        return environments;
    }

    public Optional<CoolifyEnvironmentSummary> getEnvironment(CoolifyConnection connection,
                                                              String projectUuid,
                                                              String environmentNameOrUuid) {
        JsonNode response = requestJson(
            connection,
            "GET",
            "/projects/" + encodePath(projectUuid) + "/" + encodePath(environmentNameOrUuid),
            null,
            false
        );
        if (response == null || response.isMissingNode() || response.isNull()) {
            return Optional.empty();
        }
        return Optional.of(toEnvironment(projectUuid, response));
    }

    public String createEnvironment(CoolifyConnection connection, String projectUuid, String name) {
        ObjectNode body = objectMapper.createObjectNode();
        put(body, "name", name);
        JsonNode response = requestJson(connection, "POST", "/projects/" + encodePath(projectUuid) + "/environments", body, true);
        String uuid = textFirst(response, "uuid");
        if (!StringUtils.hasText(uuid)) {
            throw new CoolifyApiException(
                "Coolify environment create did not return an environment UUID.",
                201,
                "/projects/" + encodePath(projectUuid) + "/environments"
            );
        }
        return uuid;
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

    public Optional<CoolifyDatabaseSummary> getDatabase(CoolifyConnection connection, String uuid) {
        JsonNode response = requestJson(
            connection,
            "GET",
            "/databases/" + encodePath(uuid),
            null,
            false
        );
        if (response == null || response.isMissingNode() || response.isNull()) {
            return Optional.empty();
        }
        return Optional.of(toDatabase(response));
    }

    public Optional<CoolifyDeploymentSummary> getDeployment(CoolifyConnection connection, String deploymentUuid) {
        JsonNode response = requestJson(
            connection,
            "GET",
            "/deployments/" + encodePath(deploymentUuid),
            null,
            false
        );
        if (response == null || response.isMissingNode() || response.isNull()) {
            return Optional.empty();
        }
        return Optional.of(toDeployment(response));
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

    public String createPostgresDatabase(CoolifyConnection connection,
                                         CoolifyCreatePostgresDatabaseRequest request) {
        ObjectNode body = postgresDatabaseBody(request);
        JsonNode response = requestJson(connection, "POST", "/databases/postgresql", body, true);
        String uuid = response.path("uuid").asText(null);
        if (!StringUtils.hasText(uuid)) {
            throw new CoolifyApiException("Coolify PostgreSQL database create did not return a database UUID.", 201, "/databases/postgresql");
        }
        return uuid;
    }

    public void updatePostgresDatabase(CoolifyConnection connection,
                                       String uuid,
                                       CoolifyCreatePostgresDatabaseRequest request) {
        requestJson(
            connection,
            "PATCH",
            "/databases/" + encodePath(uuid),
            postgresDatabaseUpdateBody(request),
            true
        );
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
        List<CoolifyEnvVar> bulkEnvVars = envVars.stream()
            .filter(envVar -> envVar != null && !envVar.preview())
            .toList();
        int updated = 0;
        if (!bulkEnvVars.isEmpty()) {
            updated += updateEnvironmentVariablesBulk(connection, uuid, bulkEnvVars);
        }
        for (CoolifyEnvVar envVar : envVars) {
            if (envVar == null || !envVar.preview()) {
                continue;
            }
            updateEnvironmentVariable(connection, uuid, envVar);
            updated++;
        }
        deduplicateEnvironmentVariables(connection, uuid, envVars);
        return updated;
    }

    private int updateEnvironmentVariablesBulk(CoolifyConnection connection, String uuid, List<CoolifyEnvVar> envVars) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode data = body.putArray("data");
        for (CoolifyEnvVar envVar : envVars) {
            data.add(envBody(envVar));
        }
        JsonNode response = requestJson(connection, "PATCH", "/applications/" + encodePath(uuid) + "/envs/bulk", body, true);
        return response.isArray() ? response.size() : envVars.size();
    }

    private void updateEnvironmentVariable(CoolifyConnection connection, String uuid, CoolifyEnvVar envVar) {
        requestJson(connection, "PATCH", "/applications/" + encodePath(uuid) + "/envs", envBody(envVar), true);
    }

    private void deduplicateEnvironmentVariables(CoolifyConnection connection, String uuid, List<CoolifyEnvVar> updatedEnvVars) {
        Set<EnvIdentity> updatedKeys = updatedEnvVars.stream()
            .filter(envVar -> envVar != null && StringUtils.hasText(envVar.key()))
            .map(envVar -> new EnvIdentity(envVar.key().trim(), envVar.preview()))
            .collect(Collectors.toSet());
        if (updatedKeys.isEmpty()) {
            return;
        }

        JsonNode response = requestJson(connection, "GET", "/applications/" + encodePath(uuid) + "/envs", null, true);
        if (response == null || !response.isArray()) {
            return;
        }

        Map<EnvIdentity, List<CoolifyEnvRecord>> recordsByIdentity = new LinkedHashMap<>();
        int index = 0;
        for (JsonNode item : response) {
            String envUuid = textFirst(item, "uuid", "id");
            String key = textFirst(item, "key");
            if (!StringUtils.hasText(envUuid) || !StringUtils.hasText(key)) {
                index++;
                continue;
            }
            EnvIdentity identity = new EnvIdentity(key.trim(), item.path("is_preview").asBoolean(false));
            if (!updatedKeys.contains(identity)) {
                index++;
                continue;
            }
            recordsByIdentity.computeIfAbsent(identity, ignored -> new ArrayList<>())
                .add(new CoolifyEnvRecord(
                    envUuid,
                    firstNonBlank(textFirst(item, "updated_at", "updatedAt"), textFirst(item, "created_at", "createdAt")),
                    index
                ));
            index++;
        }

        for (List<CoolifyEnvRecord> records : recordsByIdentity.values()) {
            if (records.size() < 2) {
                continue;
            }
            records.sort(Comparator
                .comparing((CoolifyEnvRecord record) -> normalizeTimestamp(record.updatedAt()))
                .thenComparingInt(CoolifyEnvRecord::index));
            for (int i = 0; i < records.size() - 1; i++) {
                deleteEnvironmentVariable(connection, uuid, records.get(i).uuid());
            }
        }
    }

    private void deleteEnvironmentVariable(CoolifyConnection connection, String uuid, String envUuid) {
        requestJson(
            connection,
            "DELETE",
            "/applications/" + encodePath(uuid) + "/envs/" + encodePath(envUuid),
            null,
            true
        );
    }

    private String normalizeTimestamp(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private ObjectNode envBody(CoolifyEnvVar envVar) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("key", envVar.key());
        item.put("value", envVar.value() == null ? "" : envVar.value());
        item.put("is_preview", envVar.preview());
        item.put("is_literal", envVar.literal());
        item.put("is_multiline", envVar.multiline());
        item.put("is_shown_once", envVar.shownOnce());
        item.put("is_buildtime", false);
        item.put("is_runtime", true);
        return item;
    }

    private String normalizePublicGitRepositoryForCreate(String repository) {
        if (!StringUtils.hasText(repository)) {
            return repository;
        }
        String candidate = repository.trim();
        String lower = candidate.toLowerCase();
        if (lower.startsWith("ssh://git@github.com/")) {
            candidate = "git@github.com:" + candidate.substring("ssh://git@github.com/".length());
        } else if (lower.startsWith("git@github.com:")) {
            return trimTrailingGitSuffix(candidate);
        } else if (!lower.startsWith("https://")
            && !lower.startsWith("http://")
            && !lower.startsWith("git://")) {
            candidate = "https://github.com/" + candidate;
        }
        return trimTrailingGitSuffix(candidate.replaceAll("/+$", ""));
    }

    private String normalizePublicGitRepositoryForUpdate(String repository) {
        if (!StringUtils.hasText(repository)) {
            return repository;
        }
        String candidate = repository.trim();
        String lower = candidate.toLowerCase();
        if (lower.startsWith("https://github.com/") || lower.startsWith("http://github.com/")) {
            candidate = candidate.substring(candidate.indexOf("github.com/") + "github.com/".length());
        } else if (lower.startsWith("ssh://git@github.com/")) {
            candidate = candidate.substring("ssh://git@github.com/".length());
        } else if (lower.startsWith("git@github.com:")) {
            candidate = candidate.substring("git@github.com:".length());
        }
        return trimTrailingGitSuffix(candidate.replaceAll("^/+", "").replaceAll("/+$", ""));
    }

    private String trimTrailingGitSuffix(String value) {
        return value.endsWith(".git") ? value.substring(0, value.length() - ".git".length()) : value;
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

    public CoolifyActionResponse startDatabase(CoolifyConnection connection, String uuid) {
        return action(connection, "/databases/" + encodePath(uuid) + "/start");
    }

    public CoolifyActionResponse stopDatabase(CoolifyConnection connection, String uuid) {
        return action(connection, "/databases/" + encodePath(uuid) + "/stop");
    }

    public CoolifyActionResponse restartDatabase(CoolifyConnection connection, String uuid) {
        return action(connection, "/databases/" + encodePath(uuid) + "/restart");
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

    public CoolifyActionResponse deleteDatabase(CoolifyConnection connection,
                                                String uuid,
                                                boolean deleteConfigurations,
                                                boolean deleteVolumes,
                                                boolean dockerCleanup,
                                                boolean deleteConnectedNetworks) {
        String path = "/databases/" + encodePath(uuid)
            + "?delete_configurations=" + deleteConfigurations
            + "&delete_volumes=" + deleteVolumes
            + "&docker_cleanup=" + dockerCleanup
            + "&delete_connected_networks=" + deleteConnectedNetworks;
        return action(connection, "DELETE", path);
    }

    public String databaseLogs(CoolifyConnection connection, String uuid, int lines) {
        int normalizedLines = Math.max(1, Math.min(lines, 1000));
        JsonNode response = requestJson(
            connection,
            "GET",
            "/databases/" + encodePath(uuid) + "/logs?lines=" + normalizedLines,
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
        put(body, "git_repository", normalizePublicGitRepositoryForCreate(request.gitRepository()));
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
        put(body, "git_repository", normalizePublicGitRepositoryForUpdate(request.gitRepository()));
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

    private ObjectNode postgresDatabaseBody(CoolifyCreatePostgresDatabaseRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        put(body, "project_uuid", request.projectUuid());
        put(body, "server_uuid", request.serverUuid());
        put(body, "environment_name", request.environmentName());
        put(body, "environment_uuid", request.environmentUuid());
        put(body, "destination_uuid", request.destinationUuid());
        put(body, "name", request.name());
        put(body, "description", request.description());
        put(body, "image", request.image());
        put(body, "postgres_user", request.postgresUser());
        put(body, "postgres_password", request.postgresPassword());
        put(body, "postgres_db", request.postgresDatabase());
        body.put("is_public", request.isPublic());
        body.put("instant_deploy", request.instantDeploy());
        return body;
    }

    private ObjectNode postgresDatabaseUpdateBody(CoolifyCreatePostgresDatabaseRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        put(body, "name", request.name());
        put(body, "description", request.description());
        put(body, "image", request.image());
        put(body, "postgres_user", request.postgresUser());
        put(body, "postgres_password", request.postgresPassword());
        put(body, "postgres_db", request.postgresDatabase());
        body.put("is_public", request.isPublic());
        return body;
    }

    private JsonNode requestJson(CoolifyConnection connection,
                                 String method,
                                 String path,
                                 JsonNode body,
                                 boolean failOnNotFound) {
        try {
            String serializedBody = body == null ? null : objectMapper.writeValueAsString(body);
            for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
                HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint(connection, path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + connection.token());
                if (serializedBody == null) {
                    builder.method(method, HttpRequest.BodyPublishers.noBody());
                } else {
                    builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(serializedBody));
                }

                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404 && !failOnNotFound) {
                    return null;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    if (isRetryable(response.statusCode()) && attempt < MAX_RETRY_ATTEMPTS) {
                        sleepBeforeRetry(response, attempt);
                        continue;
                    }
                    throw new CoolifyApiException(
                        "Coolify API request failed with HTTP " + response.statusCode() + " for " + sanitizedPath(path) + ".",
                        response.statusCode(),
                        sanitizedPath(path)
                    );
                }
                return readJson(response.body());
            }
            throw new CoolifyApiException(
                "Coolify API request failed after retries for " + sanitizedPath(path) + ".",
                502,
                sanitizedPath(path)
            );
        } catch (CoolifyApiException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw upstreamFailure(path, "Coolify API request interrupted", ex);
        } catch (IOException ex) {
            throw upstreamFailure(path, "Coolify API transport failed", ex);
        } catch (RuntimeException ex) {
            throw upstreamFailure(path, "Coolify API request failed", ex);
        }
    }

    private CoolifyApiException upstreamFailure(String path, String message, Throwable cause) {
        return new CoolifyApiException(
            message + " for " + sanitizedPath(path) + ".",
            502,
            sanitizedPath(path),
            cause
        );
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 429 || (statusCode >= 500 && statusCode < 600);
    }

    private void sleepBeforeRetry(HttpResponse<?> response, int attempt) throws InterruptedException {
        Duration delay = response.headers()
            .firstValue("Retry-After")
            .flatMap(this::parseRetryAfter)
            .orElse(RETRY_BASE_DELAY.multipliedBy(1L << Math.max(0, attempt - 1)));
        Thread.sleep(Math.min(delay.toMillis(), 15_000L));
    }

    private Optional<Duration> parseRetryAfter(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            long seconds = Long.parseLong(value.trim());
            if (seconds < 0) {
                return Optional.empty();
            }
            return Optional.of(Duration.ofSeconds(Math.min(seconds, 30)));
        } catch (NumberFormatException ex) {
            return Optional.empty();
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

    private CoolifyDatabaseSummary toDatabase(JsonNode node) {
        return new CoolifyDatabaseSummary(
            textFirst(node, "uuid", "id"),
            textFirst(node, "name"),
            textFirst(node, "status"),
            firstNonBlank(textFirst(node, "type", "database_type", "databaseType"), textFirst(node, "kind")),
            textFirst(node, "postgres_user", "postgresUser", "username"),
            textFirst(node, "postgres_db", "postgresDb", "database", "databaseName"),
            node
        );
    }

    private CoolifyProjectSummary toProject(JsonNode node) {
        return new CoolifyProjectSummary(
            textFirst(node, "uuid"),
            textFirst(node, "name"),
            textFirst(node, "description"),
            node
        );
    }

    private CoolifyDeploymentSummary toDeployment(JsonNode node) {
        JsonNode application = node.path("application");
        return new CoolifyDeploymentSummary(
            textFirst(node, "deployment_uuid", "deploymentUuid", "uuid"),
            firstNonBlank(textFirst(node, "application_name", "applicationName"), textFirst(application, "name")),
            textFirst(application, "uuid"),
            textFirst(node, "status", "state", "deployment_status"),
            textFirst(node, "commit"),
            textFirst(node, "commit_message", "commitMessage"),
            textFirst(node, "created_at", "createdAt"),
            textFirst(node, "updated_at", "updatedAt"),
            textFirst(node, "finished_at", "finishedAt"),
            node
        );
    }

    private CoolifyEnvironmentSummary toEnvironment(String projectUuid, JsonNode node) {
        return new CoolifyEnvironmentSummary(
            textFirst(node, "uuid"),
            textFirst(node, "name"),
            projectUuid,
            textFirst(node, "description"),
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

    private String textFirst(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isMissingNode() || value.isNull()) {
                continue;
            }
            String text = value.asText(null);
            if (StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        return null;
    }

    private String firstNonBlank(String left, String right) {
        return StringUtils.hasText(left) ? left.trim() : StringUtils.hasText(right) ? right.trim() : null;
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

    private record EnvIdentity(String key, boolean preview) {
    }

    private record CoolifyEnvRecord(String uuid, String updatedAt, int index) {
    }
}
