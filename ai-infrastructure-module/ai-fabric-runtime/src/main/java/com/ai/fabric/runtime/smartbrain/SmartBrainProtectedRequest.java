package com.ai.fabric.runtime.smartbrain;

import com.fasterxml.jackson.databind.JsonNode;

public record SmartBrainProtectedRequest(
    JsonNode cloudEvent,
    String principalId,
    String principalType,
    String executionSource
) {
}
