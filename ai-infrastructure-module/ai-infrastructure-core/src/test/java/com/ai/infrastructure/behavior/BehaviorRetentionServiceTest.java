package com.ai.infrastructure.behavior;

import com.ai.infrastructure.audit.AuditService;
import com.ai.infrastructure.behavior.policy.BehaviorRetentionPolicyProvider;
import com.ai.infrastructure.dto.BehaviorRetentionResult;
import com.ai.infrastructure.entity.Behavior;
import com.ai.infrastructure.repository.BehaviorRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BehaviorRetentionServiceTest {

    @Mock
    private BehaviorRepository behaviorRepository;
    @Mock
    private BehaviorRetentionPolicyProvider retentionPolicyProvider;
    @Mock
    private AuditService auditService;

    private Clock clock;
    private BehaviorRetentionService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2025-01-10T00:00:00Z"), ZoneOffset.UTC);
        service = new BehaviorRetentionService(behaviorRepository, retentionPolicyProvider, auditService, clock);
    }

    @Test
    void shouldDeleteExpiredBehaviorsAccordingToPolicy() {
        LocalDateTime now = LocalDateTime.now(clock);
        Behavior expired = behavior(Behavior.BehaviorType.VIEW, now.minusDays(60));
        Behavior recent = behavior(Behavior.BehaviorType.VIEW, now.minusDays(10));
        Behavior indefinite = behavior(Behavior.BehaviorType.SYSTEM_EVENT, now.minusDays(400));

        when(behaviorRepository.findByCreatedAtBefore(now)).thenReturn(List.of(expired, recent, indefinite));
        when(retentionPolicyProvider.getRetentionDays(Behavior.BehaviorType.VIEW)).thenReturn(30);
        when(retentionPolicyProvider.getRetentionDays(Behavior.BehaviorType.SYSTEM_EVENT)).thenReturn(-1);
        when(retentionPolicyProvider.getRetentionDaysForUser(any(), any())).thenReturn(-1);

        BehaviorRetentionResult result = service.cleanupExpiredBehaviors();

        assertThat(result.getEvaluatedCount()).isEqualTo(3);
        assertThat(result.getDeletedCount()).isEqualTo(1);
        assertThat(result.getSkippedIndefiniteCount()).isEqualTo(1);

        verify(retentionPolicyProvider).beforeBehaviorDeletion(anyList());
        verify(behaviorRepository).deleteAllInBatch(List.of(expired));
        verify(auditService).logOperation(any(), isNull(), eq("BEHAVIOR_RETENTION"), any(), any(LocalDateTime.class));
    }

    @Test
    void shouldClearCachesOnRequest() {
        LocalDateTime now = LocalDateTime.now(clock);
        Behavior expired = behavior(Behavior.BehaviorType.VIEW, now.minusDays(60));

        when(behaviorRepository.findByCreatedAtBefore(now)).thenReturn(List.of(expired));
        when(retentionPolicyProvider.getRetentionDays(any())).thenReturn(30);
        when(retentionPolicyProvider.getRetentionDaysForUser(any(), any())).thenReturn(-1);

        service.cleanupExpiredBehaviors();
        service.clearCachedPolicies();
        service.cleanupExpiredBehaviors();

        verify(retentionPolicyProvider, times(2)).getRetentionDays(Behavior.BehaviorType.VIEW);
    }

    @Test
    void shouldFailWhenProviderMissing() {
        BehaviorRetentionService noProviderService = new BehaviorRetentionService(
            behaviorRepository,
            null,
            auditService,
            clock
        );

        assertThatThrownBy(noProviderService::cleanupExpiredBehaviors)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("BehaviorRetentionPolicyProvider");
    }

    private Behavior behavior(Behavior.BehaviorType type, LocalDateTime createdAt) {
        return Behavior.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .behaviorType(type)
            .createdAt(createdAt)
            .build();
    }
}
