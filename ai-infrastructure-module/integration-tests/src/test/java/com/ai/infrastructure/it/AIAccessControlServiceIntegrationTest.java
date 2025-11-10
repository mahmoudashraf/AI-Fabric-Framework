package com.ai.infrastructure.it;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.infrastructure.access.AIAccessControlService;
import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.dto.AIAccessControlRequest;
import com.ai.infrastructure.dto.AIAccessControlResponse;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
class AIAccessControlServiceIntegrationTest {

    @Autowired
    private AIAccessControlService accessControlService;

    @MockBean
    private EntityAccessPolicy entityAccessPolicy;

    @Test
    void hookCalledWithEntityContext() {
        when(entityAccessPolicy.canUserAccessEntity(any(), any())).thenReturn(true);

        AIAccessControlResponse response = accessControlService.checkAccess(accessRequest("user-1", "resource-1"));

        assertTrue(Boolean.TRUE.equals(response.getAccessGranted()));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(entityAccessPolicy).canUserAccessEntity(eq("user-1"), captor.capture());
        Map<String, Object> entity = captor.getValue();
        assertTrue(entity.containsKey("resourceId"));
        assertTrue(entity.containsKey("operationType"));
    }

    @Test
    void decisionServedFromCacheOnSubsequentRequests() {
        when(entityAccessPolicy.canUserAccessEntity(any(), any())).thenReturn(true);

        AIAccessControlRequest request = accessRequest("user-2", "resource-2");

        AIAccessControlResponse first = accessControlService.checkAccess(request);
        AIAccessControlResponse second = accessControlService.checkAccess(request);

        assertFalse(Boolean.TRUE.equals(first.getFromCache()));
        assertTrue(Boolean.TRUE.equals(second.getFromCache()));
        verify(entityAccessPolicy, times(1)).canUserAccessEntity(eq("user-2"), any());
    }

    private AIAccessControlRequest accessRequest(String userId, String resourceId) {
        return AIAccessControlRequest.builder()
            .requestId("req-" + userId)
            .userId(userId)
            .resourceId(resourceId)
            .operationType("READ")
            .timestamp(LocalDateTime.now())
            .build();
    }
}
