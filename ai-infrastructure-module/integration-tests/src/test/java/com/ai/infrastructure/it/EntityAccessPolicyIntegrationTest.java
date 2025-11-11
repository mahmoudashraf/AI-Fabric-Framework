package com.ai.infrastructure.it;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.ai.infrastructure.dto.AIAccessControlRequest;
import com.ai.infrastructure.dto.AIAccessControlResponse;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Integration coverage for {@link EntityAccessPolicy} hook interactions as defined in the
 * infrastructure-only compliance blueprint.
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EntityAccessPolicyIntegrationTest {

    private static final String CACHE_NAME = "accessDecisions";

    @Autowired
    private AIAccessControlService accessControlService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private EntityAccessPolicy entityAccessPolicy;

    @BeforeEach
    void setUp() {
        Cache cache = cacheManager != null ? cacheManager.getCache(CACHE_NAME) : null;
        if (cache != null) {
            cache.clear();
        }
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
    void decisionIsCachedAfterFirstEvaluation() {
        when(entityAccessPolicy.canUserAccessEntity(any(), any())).thenReturn(true);

        AIAccessControlRequest request = baseRequestBuilder("req-cache")
            .resourceId("CACHE_ME")
            .build();

        AIAccessControlResponse first = accessControlService.checkAccess(request);
        AIAccessControlResponse second = accessControlService.checkAccess(request);

        assertTrue(Boolean.TRUE.equals(first.getAccessGranted()));
        assertTrue(Boolean.TRUE.equals(second.getAccessGranted()));
        assertFalse(Boolean.TRUE.equals(first.getFromCache()));
        assertTrue(Boolean.TRUE.equals(second.getFromCache()));
        verify(entityAccessPolicy, times(1)).canUserAccessEntity(eq("user-123"), any());
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
    void controllerAllowsAccessWhenNoPolicyProvided() {
        EntityAccessPolicy original = entityAccessPolicy;
        ReflectionTestUtils.setField(accessControlService, "entityAccessPolicy", null);
        try {
            AIAccessControlResponse response = accessControlService.checkAccess(
                baseRequestBuilder("req-no-hook").build());
            assertTrue(Boolean.TRUE.equals(response.getAccessGranted()));
        } finally {
            ReflectionTestUtils.setField(accessControlService, "entityAccessPolicy", original);
        }
    }

    private AIAccessControlRequest.AIAccessControlRequestBuilder baseRequestBuilder(String requestId) {
        return AIAccessControlRequest.builder()
            .requestId(requestId)
            .userId("user-123")
            .resourceId("RESOURCE")
            .operationType("READ");
    }
}
