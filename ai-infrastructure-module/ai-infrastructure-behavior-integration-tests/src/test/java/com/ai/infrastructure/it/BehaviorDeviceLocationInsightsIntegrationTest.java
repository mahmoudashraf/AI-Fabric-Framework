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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration coverage for TEST-BEHAVIOR-009: Session Analysis with Device/Location.
 * <p>
 * Captures how the system records device and location metadata, enabling downstream
 * analytics to build device/location insights per session.
 * </p>
 */
@SpringBootTest(classes = TestApplication.class)
class BehaviorDeviceLocationInsightsIntegrationTest extends AbstractBehaviorIntegrationTest {

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
    @DisplayName("Behavior records capture device and location metadata for session insights")
    void deviceAndLocationMetadataCapturedPerSession() {
        UUID userId = UUID.randomUUID();

        recordSession(userId, "session-mobile-home", "Mobile App", "US-CA-San Francisco", 3);
        recordSession(userId, "session-desktop-office", "Desktop Web", "US-CA-San Francisco", 2);
        recordSession(userId, "session-tablet-travel", "Tablet", "US-NY-New York", 1);

        List<BehaviorResponse> behaviors = behaviorService.getBehaviorsByUserId(userId);
        assertEquals(6, behaviors.size(), "All recorded behaviors should be returned for the user");

        Map<String, Long> deviceInsights = behaviors.stream()
            .collect(groupingBy(BehaviorResponse::getDeviceInfo, counting()));
        Map<String, Long> locationInsights = behaviors.stream()
            .collect(groupingBy(BehaviorResponse::getLocationInfo, counting()));

        assertEquals(3L, deviceInsights.get("Mobile App"));
        assertEquals(2L, deviceInsights.get("Desktop Web"));
        assertEquals(1L, deviceInsights.get("Tablet"));

        assertEquals(5L, locationInsights.get("US-CA-San Francisco"));
        assertEquals(1L, locationInsights.get("US-NY-New York"));

        assertTrue(deviceInsights.values().stream().mapToLong(Long::longValue).sum() >= behaviors.size(),
            "Device insight totals should cover all recorded behaviors");
        assertTrue(locationInsights.values().stream().mapToLong(Long::longValue).sum() >= behaviors.size(),
            "Location insight totals should cover all recorded behaviors");
    }

    private void recordSession(UUID userId, String sessionId, String deviceInfo, String locationInfo, int interactions) {
        Stream.generate(() -> BehaviorRequest.builder()
                .userId(userId.toString())
                .behaviorType(Behavior.BehaviorType.SESSION_START.name())
                .entityType("session")
                .entityId(sessionId)
                .action("START")
                .sessionId(sessionId)
                .deviceInfo(deviceInfo)
                .locationInfo(locationInfo)
                .build())
            .limit(interactions)
            .forEach(behaviorService::createBehavior);
    }
}

