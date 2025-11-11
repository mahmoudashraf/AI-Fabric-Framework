package com.ai.infrastructure.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.infrastructure.compliance.AIComplianceService;
import com.ai.infrastructure.compliance.policy.ComplianceCheckProvider;
import com.ai.infrastructure.compliance.policy.ComplianceCheckResult;
import com.ai.infrastructure.dto.AIComplianceReport;
import com.ai.infrastructure.dto.AIComplianceRequest;
import com.ai.infrastructure.dto.AIComplianceResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Integration coverage for {@link ComplianceCheckProvider} hook as described in the
 * infrastructure integration test blueprint.
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ComplianceCheckProviderIntegrationTest {

    @Autowired
    private AIComplianceService complianceService;

    @MockBean
    private ComplianceCheckProvider complianceCheckProvider;

    @BeforeEach
    void setUp() {
        reset(complianceCheckProvider);
    }

    @Test
    void providerViolationsArePropagated() {
        when(complianceCheckProvider.checkCompliance(any()))
            .thenReturn(ComplianceCheckResult.builder()
                .compliant(false)
                .violations(List.of("GDPR_ARTICLE_5", "PCI_DSS"))
                .details("Shared data outside approved region")
                .timestamp(LocalDateTime.of(2025, 1, 1, 12, 0))
                .build());

        AIComplianceResponse response = complianceService.checkCompliance(baseRequest("req-violation"));

        assertFalse(Boolean.TRUE.equals(response.getOverallCompliant()));
        assertThat(response.getViolations()).contains("GDPR_ARTICLE_5", "PCI_DSS");
        verify(complianceCheckProvider, times(1)).checkCompliance(any());

        List<AIComplianceReport> reports = complianceService.getComplianceReports("user-789");
        assertThat(reports)
            .isNotEmpty()
            .anyMatch(report -> report.getViolations().contains("GDPR_ARTICLE_5"));
    }

    @Test
    void defaultsToCompliantWhenProviderMissing() {
        ComplianceCheckProvider original = complianceCheckProvider;
        ReflectionTestUtils.setField(complianceService, "complianceProvider", null);

        try {
            AIComplianceResponse response = complianceService.checkCompliance(baseRequest("req-default"));
            assertTrue(Boolean.TRUE.equals(response.getOverallCompliant()));
            assertThat(response.getViolations()).isEmpty();
        } finally {
            ReflectionTestUtils.setField(complianceService, "complianceProvider", original);
        }
    }

    @Test
    void providerExceptionRegistersComplianceError() {
        when(complianceCheckProvider.checkCompliance(any()))
            .thenThrow(new IllegalStateException("policy backend offline"));

        AIComplianceResponse response = complianceService.checkCompliance(baseRequest("req-ex"));

        assertFalse(Boolean.TRUE.equals(response.getOverallCompliant()));
        assertThat(response.getViolations()).contains("COMPLIANCE_PROVIDER_ERROR");

        List<AIComplianceReport> reports = complianceService.getComplianceReports("user-789");
        assertThat(reports)
            .isNotEmpty()
            .anyMatch(report -> report.getViolations().contains("COMPLIANCE_PROVIDER_ERROR"));
    }

    private AIComplianceRequest baseRequest(String requestId) {
        return AIComplianceRequest.builder()
            .requestId(requestId)
            .userId("user-789")
            .content("process payroll export")
            .dataClassification("CONFIDENTIAL")
            .regulationTypes(List.of("GDPR", "CCPA"))
            .metadata(Map.of("region", "eu"))
            .timestamp(LocalDateTime.of(2025, 1, 1, 13, 0))
            .build();
    }
}
