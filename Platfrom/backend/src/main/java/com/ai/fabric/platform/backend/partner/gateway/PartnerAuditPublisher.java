package com.ai.fabric.platform.backend.partner.gateway;

public interface PartnerAuditPublisher {

    void publish(String partnerAccountId,
                 String partnerMemberId,
                 String action,
                 String targetType,
                 String targetId,
                 String result,
                 String detailsJson);
}
