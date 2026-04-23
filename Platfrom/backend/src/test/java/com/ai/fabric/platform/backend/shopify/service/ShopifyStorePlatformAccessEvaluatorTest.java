package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyStoreConnectionRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopifyStorePlatformAccessEvaluatorTest {

    @Test
    void productServiceCanOnlyAccessItsOwnStoreAndConsumerBindings() {
        ShopifyStoreConnectionRepository storeRepository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceRepository productServiceRepository = mock(PlatformManagedProductServiceRepository.class);

        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("psv-123");
        service.setServiceRef("shopify-bridge-prod");

        ShopifyStoreConnectionEntity ownStore = new ShopifyStoreConnectionEntity();
        ownStore.setShopDomain("alpha.myshopify.com");
        ownStore.setProductServiceId("psv-123");
        ownStore.setConsumerId("alpha-storefront");

        ShopifyStoreConnectionEntity otherStore = new ShopifyStoreConnectionEntity();
        otherStore.setShopDomain("beta.myshopify.com");
        otherStore.setProductServiceId("psv-999");
        otherStore.setConsumerId("beta-storefront");

        when(productServiceRepository.findByServiceRefIgnoreCase("shopify-bridge-prod")).thenReturn(Optional.of(service));
        when(storeRepository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(ownStore));
        when(storeRepository.findByShopDomainIgnoreCase("beta.myshopify.com")).thenReturn(Optional.of(otherStore));
        when(storeRepository.findByConsumerIdIgnoreCase("alpha-storefront")).thenReturn(Optional.of(ownStore));
        when(storeRepository.findByConsumerIdIgnoreCase("beta-storefront")).thenReturn(Optional.of(otherStore));

        ShopifyStorePlatformAccessEvaluator evaluator = new ShopifyStorePlatformAccessEvaluator(storeRepository, productServiceRepository);
        UsernamePasswordAuthenticationToken authentication = authentication("shopify-bridge-prod", PlatformRole.PLATFORM_PRODUCT_SERVICE);

        assertThat(evaluator.canList(authentication)).isTrue();
        assertThat(evaluator.canAccess(authentication, "alpha.myshopify.com")).isTrue();
        assertThat(evaluator.canAccess(authentication, "beta.myshopify.com")).isFalse();
        assertThat(evaluator.canAccessConsumer(authentication, "alpha-storefront")).isTrue();
        assertThat(evaluator.canAccessConsumer(authentication, "beta-storefront")).isFalse();
        assertThat(evaluator.canUpsert(authentication, new UpsertShopifyStoreConnectionRequest(
            "alpha.myshopify.com",
            "Alpha",
            "shopify-bridge-prod",
            null,
            null,
            null,
            "INSTALLED",
            "NOT_SYNCED",
            "NOT_RUN",
            "NOT_ENABLED",
            "CONNECTED",
            true,
            true,
            true,
            true,
            false
        ))).isTrue();
        assertThat(evaluator.canUpsert(authentication, new UpsertShopifyStoreConnectionRequest(
            "beta.myshopify.com",
            "Beta",
            "other-service",
            null,
            null,
            null,
            "INSTALLED",
            "NOT_SYNCED",
            "NOT_RUN",
            "NOT_ENABLED",
            "CONNECTED",
            true,
            true,
            true,
            true,
            false
        ))).isFalse();
    }

    @Test
    void operatorRetainsFullAccess() {
        ShopifyStorePlatformAccessEvaluator evaluator = new ShopifyStorePlatformAccessEvaluator(
            mock(ShopifyStoreConnectionRepository.class),
            mock(PlatformManagedProductServiceRepository.class)
        );

        UsernamePasswordAuthenticationToken authentication = authentication("platform-operator", PlatformRole.PLATFORM_OPERATOR);

        assertThat(evaluator.canList(authentication)).isTrue();
        assertThat(evaluator.canAccess(authentication, "any.myshopify.com")).isTrue();
        assertThat(evaluator.canAccessConsumer(authentication, "any-consumer")).isTrue();
    }

    private UsernamePasswordAuthenticationToken authentication(String actorId, PlatformRole role) {
        PlatformPrincipal principal = new PlatformPrincipal(actorId, role, actorId, "TEST");
        return new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority(role.authority()))
        );
    }
}
