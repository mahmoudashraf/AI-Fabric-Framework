package com.ai.infrastructure.it.BehaviouralTests;

import com.ai.behavior.adapter.ExternalAnalyticsAdapter;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorQuery;
import com.ai.behavior.service.BehaviorQueryService;
import com.ai.behavior.storage.BehaviorSignalRepository;
import com.ai.infrastructure.it.AbstractBehaviorIntegrationTest;
import com.ai.infrastructure.it.TestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

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
public class AggregatedBehaviorProviderIntegrationTest extends AbstractBehaviorIntegrationTest {

    @Autowired
    private BehaviorSignalRepository eventRepository;

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

        BehaviorSignal databaseEvent = BehaviorSignal.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .schemaId("engagement.view")
            .timestamp(LocalDateTime.now().minusMinutes(5))
            .attributes(Map.of("source", "database"))
            .build();
        eventRepository.save(databaseEvent);

        BehaviorSignal externalEvent = BehaviorSignal.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .schemaId("engagement.interaction")
            .timestamp(LocalDateTime.now().minusMinutes(1))
            .attributes(Map.of("source", "external"))
            .build();
        externalEventHolder.setEvents(List.of(externalEvent));

        List<BehaviorSignal> result = behaviorQueryService.recentEvents(userId, 10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAttributes().get("source")).isEqualTo("external");
        assertThat(result.get(1).getAttributes().get("source")).isEqualTo("database");
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
                public List<BehaviorSignal> fetchEvents(BehaviorQuery query) {
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
