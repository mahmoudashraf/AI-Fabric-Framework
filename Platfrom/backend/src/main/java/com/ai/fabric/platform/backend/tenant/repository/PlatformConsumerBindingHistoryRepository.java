package com.ai.fabric.platform.backend.tenant.repository;

import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerBindingHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformConsumerBindingHistoryRepository extends JpaRepository<PlatformConsumerBindingHistoryEntity, String> {

    List<PlatformConsumerBindingHistoryEntity> findByConsumerEntityIdOrderByCreatedAtDesc(String consumerEntityId);
}
