package com.ai.fabric.product.shopify.bridge.customeraccount.repository;

import com.ai.fabric.product.shopify.bridge.customeraccount.entity.ShopifyCustomerAccountAuthClaimEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopifyCustomerAccountAuthClaimRepository extends JpaRepository<ShopifyCustomerAccountAuthClaimEntity, String> {
}
