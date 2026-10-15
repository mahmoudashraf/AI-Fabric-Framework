package com.loomai.inbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loomai.managed-dependency")
public record ManagedDependencyProperties(String key, String customerId, String tenantId,
    String deploymentId, String targetProfileId, String versionId, String slot,
    String serviceRef, String accountId, String bindingId) { }
