package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerBindingHistoryEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerBindingHistoryRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TargetRetirementConsumerAssignmentsTest {
    private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    private final PlatformConsumerBindingHistoryRepository history = mock(PlatformConsumerBindingHistoryRepository.class);
    private final PlatformCustomerConsumerService consumerService = mock(PlatformCustomerConsumerService.class);
    private final TargetRetirementConsumerAssignments assignments =
        new TargetRetirementConsumerAssignments(consumers, history, consumerService);

    @Test
    void detachesOnlyTheFrozenStagingAssignment() {
        PlatformConsumerEntity consumer = consumer("staging", "staging-release", 7);
        when(consumers.findById("consumer")).thenReturn(Optional.of(consumer));
        doAnswer(invocation -> {
            consumer.setBoundDeploymentId(null);
            consumer.setBoundTargetProfileId(null);
            consumer.setBoundReleaseId(null);
            consumer.setBindingRevision(8);
            var receipt = new PlatformConsumerBindingHistoryEntity();
            receipt.setFromDeploymentId("deployment");
            receipt.setFromTargetProfileId("staging");
            receipt.setFromBindingRevision(7L);
            receipt.setToBindingRevision(8L);
            when(history.findByConsumerEntityIdAndOperationKey("consumer", "operation-key"))
                .thenReturn(Optional.of(receipt));
            return null;
        }).when(consumerService).detachTargetForRetirement(
            "customer", "consumer", "deployment", "staging", java.util.Set.of("staging-release"),
            7, "operation-key");

        assertThat(assignments.inspect(context()).complete()).isFalse();
        assignments.detach(context());

        verify(consumerService).detachTargetForRetirement(
            "customer", "consumer", "deployment", "staging", java.util.Set.of("staging-release"),
            7, "operation-key");
    }

    @Test
    void acceptsOnlyTheReceiptBackedPostDetachRevision() {
        PlatformConsumerEntity consumer = consumer(null, null, 8);
        consumer.setBoundDeploymentId(null);
        var receipt = new PlatformConsumerBindingHistoryEntity();
        receipt.setFromDeploymentId("deployment");
        receipt.setFromTargetProfileId("staging");
        receipt.setFromBindingRevision(7L);
        receipt.setToBindingRevision(8L);
        when(consumers.findById("consumer")).thenReturn(Optional.of(consumer));
        when(history.findByConsumerEntityIdAndOperationKey("consumer", "operation-key"))
            .thenReturn(Optional.of(receipt));

        assertThat(assignments.inspect(context()).complete()).isTrue();
    }

    @Test
    void preservesAnExpectedProductionAssignment() {
        PlatformConsumerEntity consumer = consumer("production", "production-release", 7);
        when(consumers.findById("consumer")).thenReturn(Optional.of(consumer));

        assertThat(assignments.inspect(context()).complete()).isTrue();
        assignments.detach(context());

        verifyNoInteractions(consumerService);
    }

    private PlatformConsumerEntity consumer(String target, String release, long revision) {
        var consumer = new PlatformConsumerEntity();
        consumer.setId("consumer");
        consumer.setCustomerId("customer");
        consumer.setConsumerId("consumer-public");
        consumer.setBoundDeploymentId("deployment");
        consumer.setBoundTargetProfileId(target);
        consumer.setBoundReleaseId(release);
        consumer.setBindingRevision(revision);
        return consumer;
    }

    private Context context() {
        var snapshot = new Snapshot("customer", "tenant", "deployment", "staging", "generation", 1,
            List.of(new ReleaseGeneration("staging-release", "version", "release-generation")), List.of(),
            List.of(new ConsumerExpectation("consumer", 7)));
        return new Context("operation", "operation-key", "request-fingerprint", snapshot, 2, "worker",
            "system", "SYSTEM", Instant.now().plusSeconds(60),
            new Retention(RetentionDecision.RECLAIM, null, null, "backup"));
    }
}
