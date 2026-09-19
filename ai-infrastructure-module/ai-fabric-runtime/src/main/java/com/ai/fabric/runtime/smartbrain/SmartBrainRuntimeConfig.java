package com.ai.fabric.runtime.smartbrain;

import java.util.List;

public record SmartBrainRuntimeConfig(
    String contractVersion,
    int maxEventBytes,
    List<Trigger> triggers,
    List<Schedule> schedules,
    Delivery delivery
) {
    public record Trigger(
        String code,
        String name,
        List<String> eventTypes,
        String specialistRef,
        boolean enabled
    ) {
    }

    public record Schedule(
        String code,
        String triggerCode,
        String cron,
        String zoneId,
        boolean enabled
    ) {
    }

    public record Delivery(String mode, String callbackUrl) {
        public boolean signedWebhook() {
            return "SIGNED_WEBHOOK".equals(mode);
        }
    }
}
