package com.ai.fabric.platform.backend.deployment.dependency;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ManagedDependencyResourceTest {
    @Test void retainsOwnedIdentityAndDefensivelyCopiesReclamationEvidence() {
        var volumes = new HashMap<>(Map.of("/data", "volume"));
        var plan = new HashMap<>(Map.of("volume", "native-volume-name"));
        var reclaimed = new HashSet<>(Set.of("volume"));
        var resource = new ManagedDependencyResource("inbox", "service", "application", "database", volumes, plan, reclaimed);
        volumes.clear(); plan.clear(); reclaimed.clear();

        assertThat(resource.slot()).isEqualTo("inbox");
        assertThat(resource.serviceRef()).isEqualTo("service");
        assertThat(resource.applicationUuid()).isEqualTo("application");
        assertThat(resource.databaseUuid()).isEqualTo("database");
        assertThat(resource.volumes()).containsExactlyEntriesOf(Map.of("/data", "volume"));
        assertThat(resource.volumeReclamationPlan()).containsExactlyEntriesOf(Map.of("volume", "native-volume-name"));
        assertThat(resource.reclaimedVolumeUuids()).containsExactly("volume");
        assertThatThrownBy(() -> resource.volumes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> resource.volumeReclamationPlan().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> resource.reclaimedVolumeUuids().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void preservesConvenienceConstructorsWithoutInventingReclamationEvidence() {
        var planned = new ManagedDependencyResource("inbox", "service", "application", null, null);
        assertThat(planned.volumes()).isEmpty();
        assertThat(planned.volumeReclamationPlan()).isEmpty();
        assertThat(planned.reclaimedVolumeUuids()).isEmpty();

        var legacy = new ManagedDependencyResource("inbox", "service", "application", null,
            Map.of("/data", "volume"), Set.of("volume"));
        assertThat(legacy.volumeReclamationPlan()).containsExactlyEntriesOf(Map.of("volume", "volume"));
        assertThat(legacy.reclaimedVolumeUuids()).containsExactly("volume");

        var unspecified = new ManagedDependencyResource("inbox", "service", "application", null, null, null, null);
        assertThat(unspecified).isEqualTo(planned);
    }
}
