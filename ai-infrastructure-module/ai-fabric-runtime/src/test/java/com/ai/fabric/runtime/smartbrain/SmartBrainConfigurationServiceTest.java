package com.ai.fabric.runtime.smartbrain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmartBrainConfigurationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsImmutableTriggerScheduleAndPollDelivery() throws Exception {
        Path manifest = writeManifest("SMART_BRAIN", "POLL", null);
        SmartBrainConfigurationService service = new SmartBrainConfigurationService(
            new DefaultResourceLoader(),
            new ObjectMapper(),
            manifest.toUri().toString()
        );

        service.load();

        assertThat(service.triggerCodes()).containsExactly("account-risk-analysis");
        assertThat(service.requireTrigger("account-risk-analysis").specialistRef())
            .isEqualTo(SmartBrainConfigurationService.SUPPORTED_SPECIALIST);
        assertThat(service.current().schedules()).singleElement()
            .satisfies(schedule -> {
                assertThat(schedule.triggerCode()).isEqualTo("account-risk-analysis");
                assertThat(schedule.zoneId()).isEqualTo("UTC");
            });
        assertThat(service.current().delivery().signedWebhook()).isFalse();
    }

    @Test
    void failsClosedWhenManifestDoesNotDeclareSmartBrain() throws Exception {
        Path manifest = writeManifest("CONVERSATIONAL", "POLL", null);
        SmartBrainConfigurationService service = new SmartBrainConfigurationService(
            new DefaultResourceLoader(),
            new ObjectMapper(),
            manifest.toUri().toString()
        );

        assertThatThrownBy(service::load)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to load immutable Smart Brain configuration");
    }

    @Test
    void requiresHttpsForSignedWebhookDelivery() throws Exception {
        Path manifest = writeManifest("SMART_BRAIN", "SIGNED_WEBHOOK", "http://customer.test/results");
        SmartBrainConfigurationService service = new SmartBrainConfigurationService(
            new DefaultResourceLoader(),
            new ObjectMapper(),
            manifest.toUri().toString()
        );

        assertThatThrownBy(service::load)
            .isInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("Smart Brain callback URL must be HTTPS without user info.");
    }

    private Path writeManifest(String behaviorType, String deliveryMode, String callbackUrl) throws Exception {
        String callback = callbackUrl == null ? "null" : "\"" + callbackUrl + "\"";
        Path manifest = tempDir.resolve("deployment-manifest-" + System.nanoTime() + ".json");
        Files.writeString(manifest, """
            {
              "behaviorConfig": {
                "type": "%s",
                "smartBrain": {
                  "contractVersion": "LOOMAI_SMART_BRAIN_CONFIG_V1",
                  "maxEventBytes": 262144,
                  "triggers": [{
                    "code": "account-risk-analysis",
                    "name": "Account risk analysis",
                    "eventTypes": ["com.example.account.risk.requested"],
                    "specialistRef": "smart-brain-event-analyst@1",
                    "enabled": true
                  }],
                  "schedules": [{
                    "code": "nightly-risk-analysis",
                    "triggerCode": "account-risk-analysis",
                    "cron": "0 0 2 * * ?",
                    "zoneId": "UTC",
                    "enabled": true
                  }],
                  "delivery": {
                    "mode": "%s",
                    "callbackUrl": %s
                  }
                }
              }
            }
            """.formatted(behaviorType, deliveryMode, callback));
        return manifest;
    }
}
