package com.ai.fabric.platform.backend.deployment.service;

public record CoolifyConnection(
    String baseUrl,
    String token,
    CoolifyTargetProfileConfig config
) {

    @Override
    public String toString() {
        return "CoolifyConnection[baseUrl=%s, token=***, config=%s]".formatted(baseUrl, config);
    }
}
