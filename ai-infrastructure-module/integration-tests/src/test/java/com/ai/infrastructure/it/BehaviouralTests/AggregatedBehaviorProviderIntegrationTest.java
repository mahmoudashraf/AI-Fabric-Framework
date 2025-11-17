package com.ai.infrastructure.it.BehaviouralTests;

import com.ai.behavior.adapter.ExternalAnalyticsAdapter;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorQuery;
import com.ai.behavior.model.EventType;
import com.ai.behavior.service.BehaviorQueryService;
import com.ai.behavior.storage.BehaviorEventRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = {TestApplication.class, AggregatedBehaviorProviderIntegrationTest.StubConfig.class},
    properties = {
        "ai.behavior.providers.external.enabled=true",
        "ai.behavior.providers.external.base-url=http://stub-provider",
        "ai.behavior.providers.aggregated.enabled=true",
        "ai.behavior.providers.aggregated.provider-order=database,external-rest",
        "ai.behavior.providers.aggregated.max-providers=2"
    }
)
@ActiveProfiles("dev")
class AggregatedBehaviorProviderIntegrationTest {

    @Autowired
    private BehaviorEventRepository eventRepository;

    @Autowired
    private BehaviorQueryService behaviorQueryService;

    @Autowired
    private StubConfig.ExternalEventHolder externalEventHolder;

    @AfterEach
    void clean() {
        eventRepository.deleteAll();
    }

    @Test
    @DisplayName("Aggregated provider merges database and external events respecting timestamps")
    void aggregatedProviderMergesSources() {
        UUID userId = UUID.randomUUID();

        BehaviorEvent databaseEvent = BehaviorEvent.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .eventType(EventType.VIEW)
            .timestamp(LocalDateTime.now().minusMinutes(5))
            .metadata(Map.of("source", "database"))
            .build();
        eventRepository.save(databaseEvent);

        BehaviorEvent externalEvent = BehaviorEvent.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .eventType(EventType.CLICK)
            .timestamp(LocalDateTime.now().minusMinutes(1))
            .metadata(Map.of("source", "external"))
            .build();
        externalEventHolder.setEvents(List.of(externalEvent));

        List<BehaviorEvent> result = behaviorQueryService.recentEvents(userId, 10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMetadata().get("source")).isEqualTo("external");
        assertThat(result.get(1).getMetadata().get("source")).isEqualTo("database");
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        ExternalEventHolder externalEventHolder() {
            return new ExternalEventHolder();
        }

        @Bean
        @Primary
        ExternalAnalyticsAdapter stubExternalAnalyticsAdapter(ExternalEventHolder holder) {
            return new ExternalAnalyticsAdapter() {
                @Override
                public List<BehaviorEvent> fetchEvents(BehaviorQuery query) {
                    if (query.getUserId() == null) {
                        return List.of();
                    }
                    return new ArrayList<>(holder.getEvents());
                }

                @Override
                public String getProviderName() {
                    return "external-rest";
                }
            };
        }

        static class ExternalEventHolder {
            private List<BehaviorEvent> events = List.of();

            void setEvents(List<BehaviorEvent> events) {
                this.events = events;
            }

            List<BehaviorEvent> getEvents() {
                return events;
            }
        }
    }
}
