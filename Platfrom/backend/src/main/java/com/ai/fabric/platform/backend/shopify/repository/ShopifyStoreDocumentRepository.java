package com.ai.fabric.platform.backend.shopify.repository;

import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ShopifyStoreDocumentRepository extends JpaRepository<ShopifyStoreDocumentEntity, String> {

    List<ShopifyStoreDocumentEntity> findByStoreConnectionIdOrderByDocumentIdAsc(String storeConnectionId);

    void deleteByStoreConnectionIdAndDocumentIdIn(String storeConnectionId, Collection<String> documentIds);
}
