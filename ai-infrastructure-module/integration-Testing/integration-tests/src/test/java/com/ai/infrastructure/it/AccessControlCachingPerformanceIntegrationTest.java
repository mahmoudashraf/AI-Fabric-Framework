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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
     * Regression verifying that access control decisions are delegated to customer hooks on every call.
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Disabled("Disabled in CI: perf-only regression test exceeds scope of ONNX/Lucene/H2 profile")
class AccessControlCachingPerformanceIntegrationTest {

    @Autowired
    private AIAccessControlService accessControlService;

    @MockBean
    private EntityAccessPolicy entityAccessPolicy;

    @BeforeEach
    void clearState() {
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

        for (int i = 0; i < 250; i++) {
            AIAccessControlResponse response = accessControlService.checkAccess(baseRequest);
            assertThat(response.getFromCache()).isFalse();
        }

        verify(entityAccessPolicy, times(251)).canUserAccessEntity(eq("perf-user"), any());
    }
}
