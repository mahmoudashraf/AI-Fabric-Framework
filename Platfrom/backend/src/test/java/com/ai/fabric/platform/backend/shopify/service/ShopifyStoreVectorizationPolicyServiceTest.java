package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.ShopifyStoreVectorizationTriggerProperties;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreVectorizationPolicyEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationPolicySummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationSourcePolicySummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreVectorizationPolicyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopifyStoreVectorizationPolicyServiceTest {

    @Test
    void getSummaryIncludesMetaobjectsDefaultState() {
        ShopifyStoreVectorizationPolicyRepository repository = mock(ShopifyStoreVectorizationPolicyRepository.class);
        when(repository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.empty());

        ShopifyStoreVectorizationPolicyService service = service(repository);

        ShopifyStoreVectorizationPolicySummary summary = service.getSummary(store(), null);

        assertThat(summary.sourcePolicies())
            .extracting(ShopifyStoreVectorizationSourcePolicySummary::sourceCategory)
            .containsExactlyElementsOf(ShopifyStoreVectorizationConstants.SOURCE_CATEGORIES);
        ShopifyStoreVectorizationSourcePolicySummary metaobjects = summary.sourcePolicies().stream()
            .filter(policy -> ShopifyStoreVectorizationConstants.SOURCE_METAOBJECTS.equals(policy.sourceCategory()))
            .findFirst()
            .orElseThrow();
        assertThat(metaobjects.enabled()).isTrue();
        assertThat(metaobjects.autoIndexingEnabled()).isFalse();
        assertThat(metaobjects.updateTriggerMode()).isEqualTo(ShopifyStoreVectorizationConstants.UPDATE_MODE_INDEXED_ONLY);
    }

    @Test
    void getSummaryFallsBackToDefaultsWhenPersistedJsonIsMalformed() {
        ShopifyStoreVectorizationPolicyRepository repository = mock(ShopifyStoreVectorizationPolicyRepository.class);
        ShopifyStoreVectorizationPolicyEntity entity = new ShopifyStoreVectorizationPolicyEntity();
        entity.setId("svp-123");
        entity.setShopDomain("alpha.myshopify.com");
        entity.setPolicyVersion(7);
        entity.setAutoIndexingDefault(false);
        entity.setSourcePoliciesJson("{bad-json");
        entity.setUpdatedBy("system");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        when(repository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(entity));

        ShopifyStoreVectorizationPolicyService service = service(repository);

        ShopifyStoreVectorizationPolicySummary summary = service.getSummary(store(), null);

        assertThat(summary.policyVersion()).isEqualTo(7);
        assertThat(summary.sourcePolicies())
            .extracting(ShopifyStoreVectorizationSourcePolicySummary::sourceCategory)
            .containsExactlyElementsOf(ShopifyStoreVectorizationConstants.SOURCE_CATEGORIES);
    }

    private ShopifyStoreVectorizationPolicyService service(ShopifyStoreVectorizationPolicyRepository repository) {
        return new ShopifyStoreVectorizationPolicyService(
            repository,
            mock(ShopifyStoreVectorizationFieldCatalogService.class),
            new ShopifyStoreVectorizationTriggerProperties(false, null, null, null, null, null, null, 0, 0, 0),
            mock(PlatformAuditService.class),
            new ObjectMapper().findAndRegisterModules()
        );
    }

    private ShopifyStoreConnectionEntity store() {
        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setShopDomain("alpha.myshopify.com");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(true);
        entity.setPagesEnabled(true);
        entity.setPoliciesEnabled(true);
        entity.setArticlesEnabled(true);
        entity.setMetaobjectsEnabled(true);
        return entity;
    }
}
