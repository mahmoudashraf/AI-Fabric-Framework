package com.ai.fabric.platform.backend.thinker.repository;

import com.ai.fabric.platform.backend.thinker.entity.ThinkerResolutionPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ThinkerResolutionPlanRepository extends JpaRepository<ThinkerResolutionPlanEntity, String> {
    Optional<ThinkerResolutionPlanEntity> findBySessionId(String sessionId);
}
