package com.loomai.operations.lifecycle;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("loomai.deployment-operations.lifecycle")
public record LifecycleProperties(
    @DefaultValue("false") boolean schedulerEnabled,
    @DefaultValue("") String gatewayBaseUrl,
    @DefaultValue("") String gatewayKey,
    @DefaultValue("false") boolean allowInsecureHttp,
    @DefaultValue("2s") Duration httpTimeout,
    @DefaultValue("30s") Duration workerLease,
    @DefaultValue("24h") Duration noticeLead,
    @DefaultValue("") String webhookUrl,
    @DefaultValue("") String webhookKey
) { }
