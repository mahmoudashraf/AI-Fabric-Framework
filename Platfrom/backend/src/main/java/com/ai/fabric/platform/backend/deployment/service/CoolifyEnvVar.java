package com.ai.fabric.platform.backend.deployment.service;

public record CoolifyEnvVar(
    String key,
    String value,
    boolean preview,
    boolean literal,
    boolean multiline,
    boolean shownOnce
) {
}
