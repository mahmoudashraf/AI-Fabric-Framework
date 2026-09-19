package com.ai.fabric.runtime.smartbrain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SmartBrainDeliveryRepository extends JpaRepository<SmartBrainDeliveryEntity, String> {
    Optional<SmartBrainDeliveryEntity> findByOperationId(String operationId);
    List<SmartBrainDeliveryEntity> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
        Collection<String> statuses,
        Instant now
    );
}
