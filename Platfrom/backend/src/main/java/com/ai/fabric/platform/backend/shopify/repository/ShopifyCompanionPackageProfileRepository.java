package com.ai.fabric.platform.backend.shopify.repository;

import com.ai.fabric.platform.backend.shopify.entity.ShopifyCompanionPackageProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopifyCompanionPackageProfileRepository extends JpaRepository<ShopifyCompanionPackageProfileEntity, String> {

    Optional<ShopifyCompanionPackageProfileEntity> findByProfileKeyIgnoreCase(String profileKey);

    Optional<ShopifyCompanionPackageProfileEntity> findFirstByTierKeyIgnoreCaseAndStatusIgnoreCaseOrderByUpdatedAtDesc(String tierKey, String status);

    List<ShopifyCompanionPackageProfileEntity> findAllByStatusIgnoreCaseOrderByProfileKeyAsc(String status);

    List<ShopifyCompanionPackageProfileEntity> findAllByOrderByProfileKeyAsc();

    List<ShopifyCompanionPackageProfileEntity> findAllByPackageKeyIgnoreCaseAndTierKeyIgnoreCaseAndStatusIgnoreCaseOrderByUpdatedAtDesc(
        String packageKey,
        String tierKey,
        String status
    );
}
