package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.*;
import com.ai.fabric.platform.backend.marketplace.repository.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;

class MarketplaceArchivedPrivateAuditTest {
    @Test void archivedUnsupportedPrivateVersionsAreRetainedButNotValidatedAsExecutableCatalogueEntries() {
        var plugins = mock(MarketplacePluginRepository.class);
        var versions = mock(MarketplacePluginVersionRepository.class);
        var manifests = mock(MarketplaceManifestService.class);
        var plugin = new MarketplacePluginEntity(); plugin.setId("old-action"); plugin.setOwnerCustomerId("owner");
        plugin.setVisibility("CUSTOMER_PRIVATE"); plugin.setPluginType("ACTION"); plugin.setStatus("ARCHIVED");
        var version = new MarketplacePluginVersionEntity(); version.setPluginId(plugin.getId());
        when(plugins.findAll()).thenReturn(List.of(plugin)); when(versions.findAll()).thenReturn(List.of(version));
        new MarketplaceCatalogStartupValidator(plugins, versions, manifests).afterSingletonsInstantiated();
        verifyNoInteractions(manifests);
        plugin.setPluginType("DATA");
        new MarketplaceCatalogStartupValidator(plugins, versions, manifests).afterSingletonsInstantiated();
        verify(manifests).parseAndValidate(plugin, version);
    }
}
