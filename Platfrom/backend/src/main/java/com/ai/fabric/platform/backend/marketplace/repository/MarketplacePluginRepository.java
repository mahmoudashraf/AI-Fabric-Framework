package com.ai.fabric.platform.backend.marketplace.repository;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketplacePluginRepository extends JpaRepository<MarketplacePluginEntity, String> {

    List<MarketplacePluginEntity> findAllByOrderByDisplayNameAsc();
}
