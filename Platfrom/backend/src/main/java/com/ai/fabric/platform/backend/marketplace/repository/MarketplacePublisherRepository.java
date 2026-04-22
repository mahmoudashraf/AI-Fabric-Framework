package com.ai.fabric.platform.backend.marketplace.repository;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePublisherEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketplacePublisherRepository extends JpaRepository<MarketplacePublisherEntity, String> {

    Optional<MarketplacePublisherEntity> findBySlugIgnoreCase(String slug);

    List<MarketplacePublisherEntity> findByOwnerUserIdOrderByCreatedAtDesc(String ownerUserId);
}
