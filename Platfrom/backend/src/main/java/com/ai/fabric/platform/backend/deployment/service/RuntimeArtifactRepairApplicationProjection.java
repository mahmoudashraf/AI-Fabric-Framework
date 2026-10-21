package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Reviewed Coolify application configuration; provider clocks, derived hashes and status are not configuration. */
public final class RuntimeArtifactRepairApplicationProjection {
    private RuntimeArtifactRepairApplicationProjection() { }
    private static final Set<String> CONFIGURATION = Set.of(
        "uuid", "name", "description", "deleted_at", "environment_id", "project_id", "project_uuid", "environment_uuid",
        "environment_name", "server_id", "server_uuid", "destination_id", "destination_uuid", "destination_type",
        "additional_servers", "additional_servers_count", "additional_networks_count", "fqdn", "domains", "redirect",
        "ports_exposes", "ports_mappings", "custom_network_aliases", "connect_to_docker_network", "custom_labels",
        "custom_docker_run_options", "custom_nginx_configuration", "build_pack", "base_directory", "publish_directory",
        "dockerfile", "dockerfile_location", "dockerfile_target_build", "docker_compose", "docker_compose_raw",
        "docker_compose_location", "docker_compose_domains", "docker_compose_custom_build_command",
        "docker_compose_custom_start_command", "compose_parsing_version", "static_image", "install_command", "build_command",
        "start_command", "pre_deployment_command", "pre_deployment_command_container", "post_deployment_command",
        "post_deployment_command_container", "health_check_enabled", "health_check_path", "health_check_port", "health_check_host",
        "health_check_interval", "health_check_timeout", "health_check_retries", "health_check_start_period", "health_check_method",
        "health_check_response_text", "health_check_scheme", "health_check_return_code", "health_check_command", "health_check_type",
        "limits_cpu_shares", "limits_cpus", "limits_cpuset", "limits_memory", "limits_memory_reservation", "limits_memory_swap",
        "limits_memory_swappiness", "swarm_placement_constraints", "swarm_replicas", "http_basic_auth_password",
        "http_basic_auth_username", "is_http_basic_auth_enabled", "private_key_id", "source_id", "source_type",
        "git_repository", "git_branch", "git_commit_sha", "git_full_url", "repository_project_id", "watch_paths", "preview_url_template",
        "manual_webhook_secret_bitbucket", "manual_webhook_secret_gitea", "manual_webhook_secret_github", "manual_webhook_secret_gitlab",
        "is_auto_deploy_enabled", "is_force_https_enabled", "autogenerate_domain", "settings"
    );
    private static final Set<String> OBSERVATIONS = Set.of("status", "server_status", "created_at", "updated_at", "config_hash",
        "is_configuration_changed", "last_online_at", "last_restart_at", "last_restart_type", "restart_count",
        "custom_healthcheck_found", "deployment_uuid", "deployment_status", "build_status");
    private static final Set<String> DESTINATION = Set.of("id", "uuid", "name", "network", "server_id");
    private static final Set<String> SERVER = Set.of("id", "uuid", "ip", "port", "user", "team_id", "private_key_id", "is_coolify_host");
    public static ObjectNode project(JsonNode raw, boolean imageMayChange) {
        if (raw == null || !raw.isObject() || raw.path("uuid").asText().isBlank() || !raw.path("destination").isObject()) fail();
        var result = ((ObjectNode) raw).objectNode();
        raw.fields().forEachRemaining(entry -> {
            String name = entry.getKey();
            if (CONFIGURATION.contains(name)) result.set(name, entry.getValue().deepCopy());
            else if ("destination".equals(name) || OBSERVATIONS.contains(name)) { /* Projected separately or observed metadata. */ }
            else if (Set.of("docker_registry_image_name", "docker_registry_image_tag").contains(name)) {
                if (!imageMayChange) result.set(name, entry.getValue().deepCopy());
            } else fail();
        });
        var destination = result.putObject("destination");
        DESTINATION.forEach(field -> { if (raw.path("destination").has(field)) destination.set(field, raw.path("destination").get(field).deepCopy()); });
        var server = destination.putObject("server");
        SERVER.forEach(field -> { if (raw.path("destination").path("server").has(field)) server.set(field, raw.path("destination").path("server").get(field).deepCopy()); });
        if (destination.path("uuid").asText().isBlank() || server.path("uuid").asText().isBlank()) fail();
        return result;
    }
    private static void fail() {
        throw new org.springframework.web.server.ResponseStatusException(CONFLICT, "RUNTIME_ARTIFACT_REPAIR_NATIVE_CONFIGURATION_UNREVIEWED");
    }
}
