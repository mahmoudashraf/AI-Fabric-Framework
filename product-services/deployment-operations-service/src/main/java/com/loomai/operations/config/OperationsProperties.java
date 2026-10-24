package com.loomai.operations.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loomai.deployment-operations")
public record OperationsProperties(
    String apiKey,
    String capacityPolicyJson,
    Duration holdTtl,
    Duration maxRequestAge,
    Duration maxClockSkew,
    int maxBodyBytes
) {}
