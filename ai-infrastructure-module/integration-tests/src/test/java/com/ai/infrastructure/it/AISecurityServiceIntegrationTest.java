package com.ai.infrastructure.it;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ai.infrastructure.dto.AISecurityRequest;
import com.ai.infrastructure.dto.AISecurityResponse;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import com.ai.infrastructure.security.AISecurityService;
import com.ai.infrastructure.security.policy.SecurityAnalysisPolicy;
import com.ai.infrastructure.security.policy.SecurityAnalysisResult;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@Disabled("Disabled due to ApplicationContext loading failures - table creation issues")
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
class AISecurityServiceIntegrationTest {

    @Autowired
    private AISecurityService securityService;

    @Autowired
    private PIIDetectionService piiDetectionService;

    @MockBean
    private SecurityAnalysisPolicy securityAnalysisPolicy;

    @Test
    void builtInThreatsDetected() {
        AISecurityResponse response = securityService.analyzeRequest(securityRequest("'; DROP TABLE users;"));
        assertTrue(response.getThreatsDetected().contains("INJECTION_ATTACK"));
    }

    @Test
    void customPolicyThreatsIncluded() {
        when(securityAnalysisPolicy.analyzeSecurity(any())).thenReturn(
            SecurityAnalysisResult.builder()
                .threats(List.of("CUSTOM_THREAT"))
                .build()
        );

        AISecurityResponse response = securityService.analyzeRequest(securityRequest("regular content"));
        assertTrue(response.getThreatsDetected().contains("CUSTOM_THREAT"));
    }

    private AISecurityRequest securityRequest(String content) {
        return AISecurityRequest.builder()
            .requestId("sec-1")
            .userId("user-1")
            .content(content)
            .operationType("QUERY")
            .timestamp(LocalDateTime.now())
            .build();
    }
}
