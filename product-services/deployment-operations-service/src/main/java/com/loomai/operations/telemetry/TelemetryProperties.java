package com.loomai.operations.telemetry;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loomai.telemetry")
public record TelemetryProperties(boolean enabled, String readKey, String sourcesJson) {}
