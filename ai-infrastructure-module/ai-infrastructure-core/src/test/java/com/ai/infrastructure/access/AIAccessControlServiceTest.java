package com.ai.infrastructure.access;

import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.dto.AIAccessControlRequest;
import com.ai.infrastructure.dto.AIAccessControlResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class AIAccessControlServiceTest {

    private final Clock clock = Clock.systemUTC();

    @Test
    void throwsWhenPolicyMissing() {
        AIAccessControlService service = new AIAccessControlService(clock, null);

        assertThatThrownBy(() -> service.checkAccess(buildRequest("user-1")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No EntityAccessPolicy bean available");
    }

    @Test
    void deniesAccessWhenPolicyThrowsError() {
        EntityAccessPolicy policy = mock(EntityAccessPolicy.class);
        doThrow(new IllegalStateException("boom"))
            .when(policy).canUserAccessEntity(any(), any());

        AIAccessControlService service = new AIAccessControlService(clock, policy);

        AIAccessControlResponse response = service.checkAccess(buildRequest("user-2"));

        assertThat(response.getAccessGranted()).isFalse();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("boom");
    }

    @Test
    void shouldAllowAnonymousSessionIdWhenUserIdMissing() {
        EntityAccessPolicy policy = mock(EntityAccessPolicy.class);
        doReturn(true).when(policy).canUserAccessEntity(any(), any());

        AIAccessControlService service = new AIAccessControlService(clock, policy);

        AIAccessControlResponse response = service.checkAccess(AIAccessControlRequest.builder()
            .requestId("req-session-1")
            .sessionId("session-1")
            .resourceId("resource-1")
            .operationType("READ")
            .metadata(Map.of("test", true))
            .build());

        assertThat(response.getAccessGranted()).isTrue();
        assertThat(response.getUserId()).isEqualTo("session-1");
    }

    private AIAccessControlRequest buildRequest(String userId) {
        return AIAccessControlRequest.builder()
            .requestId("req-" + userId)
            .userId(userId)
            .resourceId("resource-" + userId)
            .operationType("READ")
            .metadata(Map.of("test", true))
            .build();
    }
}
