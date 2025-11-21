package com.ai.behavior.storage;

import com.ai.behavior.model.BehaviorAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BehaviorAlertRepository extends JpaRepository<BehaviorAlert, UUID> {

    List<BehaviorAlert> findByUserIdOrderByDetectedAtDesc(UUID userId);

    long deleteByDetectedAtBefore(LocalDateTime cutoff);

    void deleteByUserId(UUID userId);
}
