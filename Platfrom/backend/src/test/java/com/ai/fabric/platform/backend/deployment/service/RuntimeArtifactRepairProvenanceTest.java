package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RuntimeArtifactRepairProvenanceTest {
    final ObjectMapper mapper = new ObjectMapper();
    @Test void sourceCommitMayChangeButRecordIdentityFlagsAndOtherEnvironmentCannot() {
        var before = environment("a".repeat(40)); var after = environment("b".repeat(40));
        ((ObjectNode) after.get(0)).put("updated_at", "new-provider-clock");
        var original = RuntimeArtifactRepairProvenance.inspect(before); var replacement = RuntimeArtifactRepairProvenance.inspect(after);
        assertThat(original.commit()).isEqualTo("a".repeat(40)); assertThat(replacement.commit()).isEqualTo("b".repeat(40));
        assertThat(original.stable()).isEqualTo(replacement.stable());
        ((ObjectNode) after.get(4)).put("value", "changed-private-value");
        assertThat(RuntimeArtifactRepairProvenance.inspect(after).stable()).isNotEqualTo(original.stable());
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "duplicate", "conflicting", "alias", "runtime", "literal", "buildtime", "preview", "shared", "shown-once"})
    void ambiguousOrNonliteralSourceRecordsAreNotRewritten(String change) {
        var values = environment("a".repeat(40)); var first = (ObjectNode) values.get(0);
        switch (change) {
            case "missing" -> values.remove(0);
            case "duplicate" -> values.add(first.deepCopy().put("uuid", "different-identity"));
            case "conflicting" -> first.put("value", "c".repeat(40)).put("real_value", "\"" + "c".repeat(40) + "\"");
            case "alias" -> first.put("real_value", "unrelated-expression");
            case "runtime" -> first.put("is_runtime", false);
            case "literal" -> first.put("is_literal", false);
            case "buildtime" -> first.put("is_buildtime", true);
            case "preview" -> first.put("is_preview", "false");
            case "shared" -> first.put("is_shared", true);
            case "shown-once" -> first.put("is_shown_once", true);
            default -> throw new AssertionError(change);
        }
        assertThatThrownBy(() -> RuntimeArtifactRepairProvenance.inspect(values)).hasMessageContaining("SOURCE_PROVENANCE_UNPROVEN");
    }
    @Test void sourceRecordFlagsAndIdentityRemainInTheStableProjection() {
        var before = environment("a".repeat(40)); var after = before.deepCopy();
        ((ObjectNode) after.get(0)).put("uuid", "new-uuid");
        assertThat(RuntimeArtifactRepairProvenance.inspect(after).stable()).isNotEqualTo(RuntimeArtifactRepairProvenance.inspect(before).stable());
    }
    ArrayNode environment(String commit) {
        var rows = mapper.createArrayNode(); int i = 0;
        for (String key : java.util.List.of("APP_BUILD_COMMIT", "PLATFORM_SOURCE_COMMIT")) for (boolean preview : new boolean[]{false, true})
            rows.addObject().put("uuid", "source-" + i++).put("key", key).put("value", commit).put("real_value", "\"" + commit + "\"")
                .put("is_preview", preview).put("is_runtime", true).put("is_buildtime", false).put("is_literal", true)
                .put("is_multiline", false).put("is_shared", false).put("is_shown_once", false).put("updated_at", "original-clock");
        rows.addObject().put("uuid", "unrelated").put("key", "PRIVATE_SETTING").put("value", "synthetic-private-value");
        return rows;
    }
}
