package com.ai.fabric.runtime.tasks;

import com.fasterxml.jackson.databind.JsonNode;

public record ManagedTaskRequest(JsonNode input, String correlationId) {}
