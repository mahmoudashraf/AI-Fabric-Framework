package com.ai.infrastructure.it;

import com.ai.infrastructure.dto.BehaviorRequest;
import com.ai.infrastructure.dto.BehaviorResponse;
import com.ai.infrastructure.entity.Behavior;
import com.ai.infrastructure.repository.BehaviorRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.service.BehaviorService;
import com.ai.infrastructure.it.AbstractBehaviorIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration coverage for TEST-BEHAVIOR-003: Time-Series Analysis.
 * <p>
 * The test seeds behaviors across a synthetic 10-day period and verifies that
 * date-range filtering on {@link BehaviorService} returns the expected slice of data.
 * </p>
 */
@SpringBootTest(classes = TestApplication.class)
class BehaviorDateRangeAnalysisIntegrationTest extends AbstractBehaviorIntegrationTest {

    @Autowired
    private BehaviorService behaviorService;

    @Autowired
    private BehaviorRepository behaviorRepository;

    @MockBean
    private AICapabilityService aiCapabilityService;

    @AfterEach
    void tearDown() {
        behaviorRepository.deleteAll();
        Mockito.reset(aiCapabilityService);
    }

    @Test
    @DisplayName("Behavior service filters events accurately within rolling date windows")
    void dateRangeFilteringCapturesExpectedSlice() {
        UUID userId = UUID.randomUUID();
        IntStream.range(0, 10)
            .mapToObj(day -> behaviorService.createBehavior(
                BehaviorRequest.builder()
                    .userId(userId.toString())
                    .behaviorType(Behavior.BehaviorType.PAGE_VIEW.name())
                    .entityType("content")
                    .entityId("article-" + day)
                    .action("READ")
                    .sessionId("session-" + day)
                    .durationSeconds(60L + day)
                    .build()
            ))
            .toList();

        List<BehaviorResponse> ascendingTimeline = behaviorService.getBehaviorsByUserId(userId).stream()
            .sorted(java.util.Comparator.comparing(BehaviorResponse::getCreatedAt))
            .toList();

        LocalDateTime rangeStart = ascendingTimeline.get(2).getCreatedAt().minusNanos(1);
        LocalDateTime rangeEnd = ascendingTimeline.get(6).getCreatedAt().plusNanos(1);

        List<BehaviorResponse> filtered = behaviorService.getBehaviorsByUserIdAndDateRange(userId, rangeStart, rangeEnd);
        assertEquals(5, filtered.size(), "Exactly five behaviors should fall within the selected date window");
        filtered.forEach(response -> {
            assertTrue(!response.getCreatedAt().isBefore(rangeStart), "Behavior should not precede the start of the window");
            assertTrue(!response.getCreatedAt().isAfter(rangeEnd), "Behavior should not exceed the end of the window");
        });

        LocalDateTime earliest = ascendingTimeline.getFirst().getCreatedAt();
        LocalDateTime latest = ascendingTimeline.getLast().getCreatedAt();
        List<BehaviorResponse> entireSpan = behaviorService.getBehaviorsByDateRange(earliest.minusSeconds(1), latest.plusSeconds(1));
        assertEquals(10, entireSpan.size(), "Full-span query should retrieve all seeded behaviors");
    }
}

