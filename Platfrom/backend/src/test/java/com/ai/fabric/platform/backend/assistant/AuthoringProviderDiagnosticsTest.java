package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthoringProviderDiagnosticsTest {
    @Test void diagnosticsKeepBoundedMetadataWithoutRawTextOrExecutionContext() {
        var mapper = new ObjectMapper(); var source = mapper.createObjectNode().put("droppedObservations", 2);
        var values = source.putArray("observations");
        for (int i = 0; i < 35; i++) values.addObject().put("operationId", "op-" + i).put("model", "configured-model")
            .put("finishReason", "length").put("effectiveOutputCap", 8192).put("contentEmpty", true).putNull("reasoningTokens")
            .put("responseText", "private source/model content").put("Authorization", "private credential")
            .putObject("request").put("query", "private user content");
        var projected = AuthoringProviderDiagnostics.project(mapper, source);
        assertThat(projected.path("observations")).hasSize(32); assertThat(projected.path("droppedObservations").asLong()).isEqualTo(5);
        assertThat(projected.at("/observations/0/operationId").asText()).isEqualTo("op-3");
        assertThat(projected.at("/observations/0/effectiveOutputCap").asInt()).isEqualTo(8192);
        assertThat(projected.at("/observations/0/reasoningTokens").isNull()).isTrue();
        assertThat(projected.toString()).doesNotContain("private", "responseText", "Authorization", "request");
    }
}
