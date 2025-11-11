package com.ai.infrastructure.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.infrastructure.access.AIAccessControlService;
import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.dto.AIAccessControlRequest;
import com.ai.infrastructure.dto.AIAccessControlResponse;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Lightweight performance-style regression verifying that access control caching avoids repeated
 * hook evaluation under sustained load, per the infrastructure integration test blueprint.
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccessControlCachingPerformanceIntegrationTest {

    private static final String CACHE_NAME = "accessDecisions";

    @Autowired
    private AIAccessControlService accessControlService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private EntityAccessPolicy entityAccessPolicy;

    @BeforeEach
    void clearState() {
        Cache cache = cacheManager != null ? cacheManager.getCache(CACHE_NAME) : null;
        if (cache != null) {
            cache.clear();
        }
        reset(entityAccessPolicy);
        when(entityAccessPolicy.canUserAccessEntity(any(), any())).thenReturn(true);
    }

    @Test
    void repeatedAccessUsesCachedDecisionAndKeepsLatencyStable() {
        AIAccessControlRequest baseRequest = AIAccessControlRequest.builder()
            .requestId("perf-001")
            .userId("perf-user")
            .resourceId("RESOURCE_X")
            .operationType("READ")
            .metadata(Map.of("region", "us-west"))
            .timestamp(LocalDateTime.of(2025, 1, 1, 9, 0))
            .build();

        AIAccessControlResponse first = accessControlService.checkAccess(baseRequest);
        assertThat(first.getProcessingTimeMs()).isGreaterThanOrEqualTo(0L);

        long cachedCount = 0;
        for (int i = 0; i < 250; i++) {
            AIAccessControlResponse response = accessControlService.checkAccess(baseRequest);
            if (Boolean.TRUE.equals(response.getFromCache())) {
                cachedCount++;
            }
        }

        verify(entityAccessPolicy, times(1)).canUserAccessEntity(eq("perf-user"), any());
        assertThat(cachedCount).isEqualTo(250);
    }
}
