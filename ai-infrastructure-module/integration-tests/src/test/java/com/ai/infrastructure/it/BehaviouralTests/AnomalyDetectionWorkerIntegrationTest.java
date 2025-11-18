package com.ai.infrastructure.it.BehaviouralTests;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorAlert;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.processing.worker.AnomalyDetectionWorker;
import com.ai.behavior.storage.BehaviorAlertRepository;
import com.ai.behavior.storage.BehaviorDataProvider;
import com.ai.infrastructure.it.TestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = {TestApplication.class, AnomalyDetectionWorkerIntegrationTest.StubConfig.class},
    properties = {
        "ai.behavior.processing.anomaly.enabled=true"
    }
)
@ActiveProfiles("dev")
public class AnomalyDetectionWorkerIntegrationTest {

    @Autowired
    private AnomalyDetectionWorker anomalyDetectionWorker;

    @Autowired
    private BehaviorAlertRepository behaviorAlertRepository;

    @Autowired
    private StubConfig.EventBuffer eventBuffer;

    @AfterEach
    void reset() {
        behaviorAlertRepository.deleteAll();
        eventBuffer.setEvents(List.of());
    }

    @Test
    @DisplayName("Anomaly worker emits alerts for high velocity and value purchases")
    void workerDetectsHighVelocityAndValue() {
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        BehaviorSignal highValuePurchase = purchaseEvent(userId, 25_000, now);
        BehaviorSignal highVelocityPurchase = purchaseEvent(userId, 1_000, now.minusSeconds(5));
        BehaviorSignal additionalVelocityPurchase = purchaseEvent(userId, 900, now.minusSeconds(10));

        eventBuffer.setEvents(List.of(highValuePurchase, highVelocityPurchase, additionalVelocityPurchase));

        anomalyDetectionWorker.detect();

        List<BehaviorAlert> alerts = behaviorAlertRepository.findAll();
        assertThat(alerts).hasSize(3);
        assertThat(alerts)
            .anyMatch(alert -> "value_anomaly".equals(alert.getAlertType()) && "CRITICAL".equals(alert.getSeverity()))
            .anyMatch(alert -> "velocity_anomaly".equals(alert.getAlertType()) && !"LOW".equals(alert.getSeverity()));
    }

    private BehaviorSignal purchaseEvent(UUID userId, double amount, LocalDateTime timestamp) {
        return BehaviorSignal.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .schemaId("conversion.transaction")
            .timestamp(timestamp)
            .attributes(new java.util.HashMap<>(java.util.Map.of("amount", amount)))
            .build();
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        EventBuffer eventBuffer() {
            return new EventBuffer();
        }

        @Bean
        @Primary
        BehaviorDataProvider stubBehaviorDataProvider(EventBuffer buffer, BehaviorModuleProperties properties) {
            return new BehaviorDataProvider() {
                @Override
                public List<BehaviorSignal> query(com.ai.behavior.model.BehaviorQuery query) {
                    return buffer.getEvents();
                }

                @Override
                public String getProviderType() {
                    return "stub-provider";
                }
            };
        }

        static class EventBuffer {
            private List<BehaviorSignal> events = List.of();

            void setEvents(List<BehaviorSignal> events) {
                this.events = events;
            }

            List<BehaviorSignal> getEvents() {
                return events;
            }
        }
    }
}
