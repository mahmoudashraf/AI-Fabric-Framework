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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

/**
 * Integration coverage for TEST-BEHAVIOR-001: User Session Tracking.
 * <p>
 * This test verifies that {@link BehaviorService} persists behavioral events for a session,
 * preserves ordering metadata, and triggers the AI capability pipeline for each saved record.
 * </p>
 */
@SpringBootTest(classes = TestApplication.class)
class BehaviorSessionTrackingIntegrationTest extends AbstractBehaviorIntegrationTest {

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
    @DisplayName("Behavior service tracks sequential events within a session and invokes AI processing")
    void sessionTrackingPersistsOrderedBehaviors() {
        UUID userId = UUID.randomUUID();
        String sessionId = "session-" + userId;

        IntStream.range(0, 20).forEach(index -> behaviorService.createBehavior(
            BehaviorRequest.builder()
                .userId(userId.toString())
                .behaviorType(Behavior.BehaviorType.PRODUCT_VIEW.name())
                .entityType("product")
                .entityId("product-" + index)
                .action("VIEW")
                .sessionId(sessionId)
                .deviceInfo(index % 2 == 0 ? "iOS" : "Android")
                .locationInfo("US-CA-San Francisco")
                .durationSeconds(5L + index)
                .build()
        ));

        List<BehaviorResponse> recorded = behaviorService.getBehaviorsBySession(sessionId);
        assertEquals(20, recorded.size(), "All behaviors in the session should be persisted");
        recorded.forEach(response -> assertEquals(sessionId, response.getSessionId(), "Session ID must match for each behavior"));

        List<LocalDateTime> timestamps = recorded.stream()
            .map(BehaviorResponse::getCreatedAt)
            .toList();
        for (int i = 0; i < timestamps.size() - 1; i++) {
            LocalDateTime current = timestamps.get(i);
            LocalDateTime next = timestamps.get(i + 1);
            assertTrue(!current.isAfter(next), "Behavior timestamps should be non-decreasing within a session");
        }

        assertEquals(20L, behaviorRepository.count(), "Repository should contain exactly the created behaviors");
        Mockito.verify(aiCapabilityService, times(20)).processEntityForAI(any(Behavior.class), eq("behavior"));
    }
}

