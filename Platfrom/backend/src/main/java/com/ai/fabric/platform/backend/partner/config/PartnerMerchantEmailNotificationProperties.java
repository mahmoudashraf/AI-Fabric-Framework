package com.ai.fabric.platform.backend.partner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.notifications.merchant-email")
public record PartnerMerchantEmailNotificationProperties(
    boolean enabled,
    boolean dryRun,
    String from,
    String replyTo,
    String subjectPrefix
) {
}
