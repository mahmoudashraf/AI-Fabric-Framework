package com.ai.infrastructure.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.infrastructure.dto.AISecurityRequest;
import com.ai.infrastructure.dto.AISecurityResponse;
import com.ai.infrastructure.dto.PIIDetectionResult;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import com.ai.infrastructure.security.AISecurityService;
import com.ai.infrastructure.security.policy.SecurityAnalysisPolicy;
import com.ai.infrastructure.security.policy.SecurityAnalysisResult;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration coverage for {@link SecurityAnalysisPolicy} hook behaviour.
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SecurityAnalysisPolicyIntegrationTest {

    @Autowired
    private AISecurityService securityService;

    @MockBean
    private SecurityAnalysisPolicy securityAnalysisPolicy;

    @MockBean
    private PIIDetectionService piiDetectionService;

    @BeforeEach
    void setUp() {
        reset(securityAnalysisPolicy);
        when(piiDetectionService.analyze(any()))
            .thenReturn(PIIDetectionResult.builder()
                .originalQuery("safe")
                .processedQuery("safe")
                .piiDetected(false)
                .build());
    }

    @Test
    void customThreatsFromPolicyAreMerged() {
        when(securityAnalysisPolicy.analyzeSecurity(any()))
            .thenReturn(SecurityAnalysisResult.builder()
                .threats(List.of("CUSTOM_THREAT", "SHADOW_BAN"))
                .score(25.0)
                .timestamp(LocalDateTime.of(2025, 1, 1, 9, 0))
                .build());

        AISecurityResponse response = securityService.analyzeRequest(baseRequest("req-custom", "hello"));

        assertTrue(Boolean.TRUE.equals(response.getShouldBlock()));
        assertThat(response.getThreatsDetected())
            .contains("CUSTOM_THREAT", "SHADOW_BAN");
        verify(securityAnalysisPolicy, times(1)).analyzeSecurity(any());
    }

    @Test
    void policyExceptionDoesNotBreakAnalysisFlow() {
        when(securityAnalysisPolicy.analyzeSecurity(any()))
            .thenThrow(new IllegalStateException("policy failure"));

        AISecurityResponse response = securityService.analyzeRequest(baseRequest("req-ex", "normal content"));

        assertTrue(Boolean.TRUE.equals(response.getSuccess()));
        assertFalse(Boolean.TRUE.equals(response.getShouldBlock()));
        assertThat(response.getThreatsDetected()).isEmpty();
    }

    @Test
    void policyRecommendationsAreRecordedInEvents() {
        when(securityAnalysisPolicy.analyzeSecurity(any()))
            .thenReturn(SecurityAnalysisResult.builder()
                .threats(List.of())
                .recommendations(List.of("require-mfa"))
                .score(95.0)
                .build());

        AISecurityResponse response = securityService.analyzeRequest(baseRequest("req-rec", "monitor traffic"));

        assertTrue(Boolean.TRUE.equals(response.getSuccess()));
        assertThat(response.getThreatsDetected()).isEmpty();

        List<com.ai.infrastructure.dto.AISecurityEvent> events =
            securityService.getSecurityEvents("user-456");
        assertThat(events)
            .isNotEmpty()
            .anyMatch(event -> "SECURITY_CHECK".equals(event.getEventType()));
    }

    private AISecurityRequest baseRequest(String requestId, String content) {
        return AISecurityRequest.builder()
            .requestId(requestId)
            .userId("user-456")
            .operationType("QUERY")
            .content(content)
            .timestamp(LocalDateTime.of(2025, 1, 1, 10, 0))
            .build();
    }
}
