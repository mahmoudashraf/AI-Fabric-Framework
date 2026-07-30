package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.model.ReviewMarketplacePublisherSubmissionRequest;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.security.PlatformTestSecurity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplacePublishingServiceTest {

    private final MarketplacePublisherService publisherService = mock(MarketplacePublisherService.class);
    private final MarketplacePluginRepository pluginRepository = mock(MarketplacePluginRepository.class);
    private final MarketplacePluginVersionRepository versionRepository = mock(MarketplacePluginVersionRepository.class);
    private final MarketplaceManifestService manifestService = mock(MarketplaceManifestService.class);
    private final PlatformAuditService auditService = mock(PlatformAuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MarketplacePublishingService service = new MarketplacePublishingService(
        publisherService,
        pluginRepository,
        versionRepository,
        manifestService,
        auditService,
        objectMapper
    );

    @BeforeEach
    void authenticate() {
        PlatformTestSecurity.authenticateAsPlatformAdmin();
    }

    @AfterEach
    void clearAuthentication() {
        PlatformTestSecurity.clearAuthentication();
    }

    @Test
    void publishesAdminManagedMcpImportWithoutPublisherOwner() {
        MarketplacePluginEntity plugin = plugin("mkp-action-produs-productization-read-mcp", "ACTION");
        MarketplacePluginVersionEntity version = version("mkv-admin-mcp", plugin.getId(), """
            {
              "schemaVersion": 1,
              "pluginId": "mkp-action-produs-productization-read-mcp",
              "version": "0.1.1",
              "pluginType": "ACTION",
              "contributions": {
                "mcpServers": [
                  {"serverRef": "produs-staging", "transport": "STREAMABLE_HTTP", "endpointUrl": "https://example.com/mcp"}
                ],
                "actions": []
              }
            }
            """);
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(pluginRepository.findById(plugin.getId())).thenReturn(Optional.of(plugin));

        var summary = service.publishSubmission(
            version.getId(),
            new ReviewMarketplacePublisherSubmissionRequest("Publish discovered ProdUS MCP read actions.")
        );

        assertThat(summary.status()).isEqualTo("PUBLISHED");
        assertThat(summary.releaseChannel()).isEqualTo("STAGING");
        assertThat(plugin.getStatus()).isEqualTo("ACTIVE");
        assertThat(version.getReviewedByActorId()).isEqualTo("test-admin@example.com");
        verify(publisherService, never()).requirePublisherAccess(null);
    }

    @Test
    void rejectsOwnerlessNonMcpVersion() {
        MarketplacePluginEntity plugin = plugin("mkp-action-manual", "ACTION");
        MarketplacePluginVersionEntity version = version("mkv-ownerless", plugin.getId(), """
            {
              "schemaVersion": 1,
              "pluginId": "mkp-action-manual",
              "version": "1.0.0",
              "pluginType": "ACTION",
              "contributions": {
                "actions": []
              }
            }
            """);
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(pluginRepository.findById(plugin.getId())).thenReturn(Optional.of(plugin));

        assertThatThrownBy(() -> service.publishSubmission(
            version.getId(),
            new ReviewMarketplacePublisherSubmissionRequest("Publish ownerless non-MCP version.")
        ))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getReason()).isEqualTo("Marketplace plugin version has no publisher owner."));
    }

    private MarketplacePluginEntity plugin(String pluginId, String pluginType) {
        MarketplacePluginEntity plugin = new MarketplacePluginEntity();
        plugin.setId(pluginId);
        plugin.setSlug(pluginId);
        plugin.setDisplayName(pluginId);
        plugin.setPluginType(pluginType);
        plugin.setPublisherSlug("loom");
        plugin.setPublisherDisplayName("Loom AI");
        plugin.setShortDescription(pluginId);
        plugin.setStatus("DRAFT");
        plugin.setCreatedAt(Instant.now());
        plugin.setUpdatedAt(Instant.now());
        return plugin;
    }

    private MarketplacePluginVersionEntity version(String versionId, String pluginId, String manifestJson) {
        MarketplacePluginVersionEntity version = new MarketplacePluginVersionEntity();
        version.setId(versionId);
        version.setPluginId(pluginId);
        version.setVersion("0.1.1");
        version.setReleaseChannel("DRAFT");
        version.setStatus("VALIDATED");
        version.setManifestJson(manifestJson);
        version.setCreatedAt(Instant.now());
        version.setPublishedAt(Instant.now());
        return version;
    }
}
