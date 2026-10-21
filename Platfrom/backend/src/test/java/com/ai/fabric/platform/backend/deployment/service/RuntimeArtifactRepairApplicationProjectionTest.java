package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RuntimeArtifactRepairApplicationProjectionTest {
    @Test void derivedHashesAndLifecycleMetadataDoNotMasqueradeAsConfiguration() {
        var before = app(); var after = before.deepCopy().put("config_hash", "changed-after-image-patch")
            .put("is_configuration_changed", true).put("last_restart_type", "restart").put("restart_count", 4);
        after.withObject("/destination/server").put("sentinel_updated_at", "new-clock");
        assertThat(RuntimeArtifactRepairApplicationProjection.project(after, true)).isEqualTo(RuntimeArtifactRepairApplicationProjection.project(before, true));
    }
    @ParameterizedTest @ValueSource(strings = {"limits_cpus", "limits_memory", "limits_cpuset", "health_check_start_period", "health_check_path", "ports_exposes", "fqdn", "dockerfile", "pre_deployment_command", "custom_labels", "watch_paths", "environment_id"})
    void preservesEachReviewedConfigurationSetting(String field) {
        var before = app(); var after = before.deepCopy().put(field, "changed-setting");
        assertThat(RuntimeArtifactRepairApplicationProjection.project(after, true)).isNotEqualTo(RuntimeArtifactRepairApplicationProjection.project(before, true));
    }
    @Test void imageIsExcludedOnlyForTheRuntimeNotForUnchangedInboxOrConnector() {
        var before = app(); var after = before.deepCopy().put("docker_registry_image_tag", "new-immutable-tag");
        assertThat(RuntimeArtifactRepairApplicationProjection.project(after, true)).isEqualTo(RuntimeArtifactRepairApplicationProjection.project(before, true));
        assertThat(RuntimeArtifactRepairApplicationProjection.project(after, false)).isNotEqualTo(RuntimeArtifactRepairApplicationProjection.project(before, false));
    }
    @Test void unknownTopLevelSettingRequiresReviewInsteadOfBeingSilentlyDropped() {
        assertThatThrownBy(() -> RuntimeArtifactRepairApplicationProjection.project(app().put("new_execution_override", "unexpected"), true)).hasMessageContaining("CONFIGURATION_UNREVIEWED");
    }
    static ObjectNode app() {
        var app = new ObjectMapper().createObjectNode().put("uuid", "owned-app").put("name", "owned-runtime")
            .put("docker_registry_image_name", "reviewed/runtime").put("docker_registry_image_tag", "old-immutable-tag");
        app.putObject("destination").put("uuid", "destination").putObject("server").put("uuid", "server").put("ip", "192.0.2.1");
        return app;
    }
}
