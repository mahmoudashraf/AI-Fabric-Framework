package com.ai.fabric.platform.backend.deployment.service;

public record CoolifyConnection(
    String baseUrl,
    String token,
    CoolifyTargetProfileConfig config
) {
}
