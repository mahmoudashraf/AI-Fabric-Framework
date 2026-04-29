package com.ai.fabric.platform.backend.partner.gateway;

import com.ai.fabric.platform.backend.partner.model.PartnerCatalogEntrySummary;

import java.util.List;

public interface PartnerCatalogSource {

    List<PartnerCatalogEntrySummary> listCatalog();
}
