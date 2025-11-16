package com.ai.infrastructure.deletion;

import com.ai.infrastructure.audit.AIAuditService;
import com.ai.infrastructure.audit.AuditService;
import com.ai.infrastructure.deletion.policy.UserDataDeletionProvider;
import com.ai.infrastructure.deletion.policy.UserDataDeletionProvider.UserEntityReference;
import com.ai.infrastructure.deletion.port.BehaviorDeletionPort;
import com.ai.infrastructure.dto.AIAuditLog;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.rag.VectorDatabaseService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDataDeletionServiceTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Mock
    private AISearchableEntityRepository searchableEntityRepository;
    @Mock
    private VectorDatabaseService vectorDatabaseService;
    @Mock
    private AIAuditService aiAuditService;
    @Mock
    private AuditService auditService;
    @Mock
    private UserDataDeletionProvider provider;
    @Mock
    private org.springframework.beans.factory.ObjectProvider<BehaviorDeletionPort> behaviorDeletionPortProvider;
    @Mock
    private BehaviorDeletionPort behaviorDeletionPort;

    private Clock clock;
    private UserDataDeletionService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        when(behaviorDeletionPortProvider.getIfAvailable()).thenReturn(behaviorDeletionPort);
        service = new UserDataDeletionService(
            searchableEntityRepository,
            vectorDatabaseService,
            aiAuditService,
            auditService,
            clock,
            provider,
            behaviorDeletionPortProvider
        );
    }

    @Test
    void shouldDeleteDataWhenProviderApproves() {
        when(provider.canDeleteUser(USER_ID)).thenReturn(true);
        when(provider.findIndexedEntities(USER_ID))
            .thenReturn(List.of(new UserEntityReference("doc", "id-1")));
        when(vectorDatabaseService.removeVector("doc", "id-1")).thenReturn(true);
        when(behaviorDeletionPort.deleteUserBehaviors(UUID.fromString(USER_ID))).thenReturn(1);
        when(aiAuditService.getAuditLogs(USER_ID))
            .thenReturn(List.of(AIAuditLog.builder().logId("log-1").build()));
        when(provider.deleteUserDomainData(USER_ID)).thenReturn(3);

        UserDataDeletionResult result = service.deleteUser(USER_ID);

        assertThat(result.getStatus()).isEqualTo(UserDataDeletionResult.Status.COMPLETED);
        assertThat(result.getBehaviorsDeleted()).isEqualTo(1);
        assertThat(result.getIndexedEntitiesDeleted()).isEqualTo(1);
        assertThat(result.getVectorsDeleted()).isEqualTo(1);
        assertThat(result.getDomainRecordsDeleted()).isEqualTo(3);
        assertThat(result.getAuditEntriesDeleted()).isEqualTo(1);

        verify(behaviorDeletionPort).deleteUserBehaviors(UUID.fromString(USER_ID));
        verify(searchableEntityRepository).deleteByEntityTypeAndEntityId("doc", "id-1");
        verify(aiAuditService).clearAuditLogs(USER_ID);
        verify(provider).notifyAfterDeletion(USER_ID);
        verify(auditService).logOperation(any(), eq(USER_ID), eq("USER_DATA_DELETION"), any(), any(LocalDateTime.class));
    }

    @Test
    void shouldSkipWhenProviderBlocksDeletion() {
        when(provider.canDeleteUser(USER_ID)).thenReturn(false);

        UserDataDeletionResult result = service.deleteUser(USER_ID);

        assertThat(result.getStatus()).isEqualTo(UserDataDeletionResult.Status.SKIPPED);
        verifyNoInteractions(searchableEntityRepository, vectorDatabaseService, aiAuditService, behaviorDeletionPort);
    }

    @Test
    void shouldThrowWhenProviderMissing() {
        UserDataDeletionService noProviderService = new UserDataDeletionService(
            searchableEntityRepository,
            vectorDatabaseService,
            aiAuditService,
            auditService,
            clock,
            null,
            behaviorDeletionPortProvider
        );

        assertThatThrownBy(() -> noProviderService.deleteUser(USER_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("UserDataDeletionProvider");
    }
}
