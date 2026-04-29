package com.ai.fabric.platform.backend.thinker.repository;

import com.ai.fabric.platform.backend.thinker.entity.ThinkerIssueSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ThinkerIssueSessionRepository extends JpaRepository<ThinkerIssueSessionEntity, String> {
    List<ThinkerIssueSessionEntity> findTop200ByOrderByStartedAtDesc();
    List<ThinkerIssueSessionEntity> findTop200ByShopDomainIgnoreCaseOrderByStartedAtDesc(String shopDomain);
    List<ThinkerIssueSessionEntity> findTop200ByDeploymentIdOrderByStartedAtDesc(String deploymentId);
}
