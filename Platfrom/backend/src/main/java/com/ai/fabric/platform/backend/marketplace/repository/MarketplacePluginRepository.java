package com.ai.fabric.platform.backend.marketplace.repository;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface MarketplacePluginRepository extends JpaRepository<MarketplacePluginEntity, String> {

    Optional<MarketplacePluginEntity> findBySlugIgnoreCase(String slug);

    java.util.List<MarketplacePluginEntity> findByOwnerCustomerIdOrderByUpdatedAtDesc(String customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MarketplacePluginEntity p where p.id = :id")
    Optional<MarketplacePluginEntity> findByIdForUpdate(String id);
}
