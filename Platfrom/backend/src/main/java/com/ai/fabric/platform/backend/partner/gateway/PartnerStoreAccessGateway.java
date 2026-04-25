package com.ai.fabric.platform.backend.partner.gateway;

import java.util.Optional;

public interface PartnerStoreAccessGateway {

    Optional<PartnerShopifyStoreReadModel> findByShopDomain(String shopDomain);

    Optional<PartnerShopifyStoreReadModel> findByStoreConnectionId(String storeConnectionId);
}
