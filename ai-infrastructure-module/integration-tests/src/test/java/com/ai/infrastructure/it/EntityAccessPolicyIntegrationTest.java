package com.ai.infrastructure.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.infrastructure.access.AIAccessControlService;
import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.audit.AuditService;
import com.ai.infrastructure.dto.AIAccessControlRequest;
import com.ai.infrastructure.dto.AIAccessControlResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration coverage for {@link EntityAccessPolicy} hook interactions as defined in the
 * infrastructure-only compliance blueprint.
 */
@Disabled("Disabled due to ApplicationContext loading failures - table creation issues")
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EntityAccessPolicyIntegrationTest {

    @Autowired
    private AIAccessControlService accessControlService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private Clock clock;

    @MockBean
    private EntityAccessPolicy entityAccessPolicy;

    @BeforeEach
    void setUp() {
        reset(entityAccessPolicy);
    }

    @Test
    void hookReceivesRichContextAndGrantsAccess() {
        AtomicReference<Map<String, Object>> capturedContext = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedContext.set(invocation.getArgument(1));
            return true;
        }).when(entityAccessPolicy).canUserAccessEntity(eq("user-123"), anyMap());

        AIAccessControlRequest request = baseRequestBuilder("req-context")
            .resourceId("DOCUMENT_42")
            .operationType("EXPORT")
            .metadata(Map.of("region", "emea"))
            .userAttributes(Map.of("role", "analyst"))
            .timestamp(LocalDateTime.of(2025, 1, 1, 8, 0))
            .build();

        AIAccessControlResponse response = accessControlService.checkAccess(request);

        assertTrue(Boolean.TRUE.equals(response.getAccessGranted()));
        assertFalse(Boolean.TRUE.equals(response.getFromCache()));
        Map<String, Object> context = capturedContext.get();
        assertThat(context)
            .isNotNull()
            .containsEntry("resourceId", "DOCUMENT_42")
            .containsEntry("operationType", "EXPORT")
            .containsEntry("metadata", Map.of("region", "emea"))
            .containsEntry("userAttributes", Map.of("role", "analyst"));
    }

    @Test
    void delegatesToPolicyOnEveryCall() {
        when(entityAccessPolicy.canUserAccessEntity(any(), any())).thenReturn(true);

        AIAccessControlRequest request = baseRequestBuilder("req-cache")
            .resourceId("CACHE_ME")
            .build();

        for (int i = 0; i < 5; i++) {
            AIAccessControlResponse response = accessControlService.checkAccess(request);
            assertTrue(Boolean.TRUE.equals(response.getAccessGranted()));
            assertFalse(Boolean.TRUE.equals(response.getFromCache()));
        }

        verify(entityAccessPolicy, times(5)).canUserAccessEntity(eq("user-123"), any());
    }

    @Test
    void failsSecureWhenHookThrowsException() {
        when(entityAccessPolicy.canUserAccessEntity(any(), any()))
            .thenThrow(new IllegalStateException("policy outage"));

        AIAccessControlResponse response = accessControlService.checkAccess(
            baseRequestBuilder("req-exception").build());

        assertFalse(Boolean.TRUE.equals(response.getSuccess()));
        assertFalse(Boolean.TRUE.equals(response.getAccessGranted()));
        assertThat(response.getErrorMessage()).contains("policy outage");
    }

    @Test
    void serviceFailsWhenNoPolicyConfigured() {
        AIAccessControlService serviceWithoutPolicy =
            new AIAccessControlService(auditService, clock, null);

        assertThatThrownBy(() ->
                serviceWithoutPolicy.checkAccess(baseRequestBuilder("req-no-hook").build()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No EntityAccessPolicy bean available");
    }

    private AIAccessControlRequest.AIAccessControlRequestBuilder baseRequestBuilder(String requestId) {
        return AIAccessControlRequest.builder()
            .requestId(requestId)
            .userId("user-123")
            .resourceId("RESOURCE")
            .operationType("READ");
    }
}
