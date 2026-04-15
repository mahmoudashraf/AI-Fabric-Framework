package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplaceDatasetDocumentEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplaceDatasetHandleEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginDatasetEntity;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplaceDatasetDocumentRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplaceDatasetHandleRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplaceDatasetSyncRunRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginDatasetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplaceDatasetSyncServiceTest {

    private final MarketplaceDatasetHandleRepository datasetHandleRepository = mock(MarketplaceDatasetHandleRepository.class);
    private final MarketplaceDatasetDocumentRepository datasetDocumentRepository = mock(MarketplaceDatasetDocumentRepository.class);
    private final MarketplaceDatasetSyncRunRepository syncRunRepository = mock(MarketplaceDatasetSyncRunRepository.class);
    private final DeploymentMarketplacePluginInstallRepository installRepository = mock(DeploymentMarketplacePluginInstallRepository.class);
    private final MarketplacePluginDatasetRepository pluginDatasetRepository = mock(MarketplacePluginDatasetRepository.class);
    private final MarketplaceDatasetRuntimeSyncClient runtimeSyncClient = mock(MarketplaceDatasetRuntimeSyncClient.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MarketplaceDatasetSyncService service;

    @BeforeEach
    void setUp() {
        service = new MarketplaceDatasetSyncService(
            datasetHandleRepository,
            datasetDocumentRepository,
            syncRunRepository,
            installRepository,
            pluginDatasetRepository,
            runtimeSyncClient,
            objectMapper,
            jdbcTemplate,
            new PathMatchingResourcePatternResolver()
        );
        when(datasetHandleRepository.save(any(MarketplaceDatasetHandleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(syncRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(datasetDocumentRepository.saveAll(anyCollection())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void packagedSeedSyncCreatesTrackedDocumentsAndUpsertsThem() {
        DeploymentEntity deployment = deployment("dep-1");
        DeploymentVersionEntity version = version(datasetConfig("""
            {
              "contractVersion":"MARKETPLACE_DATASET_CONFIG_V1",
              "datasets":[
                {
                  "marketplaceInstallId":"mpi-help",
                  "marketplacePluginId":"mkp-data-help-center",
                  "datasetId":"help-center-seed",
                  "entityType":"faq-article",
                  "storageScope":"PLUGIN_SCOPED",
                  "sharingScope":"TENANT_SHARED",
                  "ingestionMode":"PACKAGED_SEED",
                  "updateStrategy":"UPSERT_BY_ID",
                  "handleRef":"plugin/mkp-data-help-center/tenant/ten-1/help-center-seed/hash/faq-article",
                  "datasetHash":"hash-help-center",
                  "seedDatasetRef":"classpath:marketplace/datasets/help-center/help-center.jsonl",
                  "config":{"scope":"all"}
                }
              ]
            }
            """));
        DeploymentReleaseEntity release = release("rel-1");
        when(installRepository.findById("mpi-help")).thenReturn(Optional.of(install("mpi-help", "mkv-data-help-center-v1")));
        when(pluginDatasetRepository.findByPluginVersionIdAndDatasetId("mkv-data-help-center-v1", "help-center-seed"))
            .thenReturn(Optional.of(pluginDataset("mkp-data-help-center", "help-center-seed", "faq-article")));
        when(datasetHandleRepository.findByPluginIdAndTenantIdAndDatasetId("mkp-data-help-center", "ten-1", "help-center-seed"))
            .thenReturn(Optional.empty());
        when(datasetDocumentRepository.findByDatasetHandleIdOrderByDocumentIdAsc(anyString())).thenReturn(List.of());
        when(runtimeSyncClient.upsertDocuments(eq(deployment), eq("faq-article"), eq("help-center-seed"), anyString(), eq("hash-help-center"), any()))
            .thenReturn(3);

        MarketplaceDatasetSyncService.DatasetSyncSummary summary = service.syncReleaseDatasets(deployment, version, release);

        assertThat(summary.datasetsCount()).isEqualTo(1);
        assertThat(summary.syncedDatasets()).isEqualTo(1);
        assertThat(summary.skippedDatasets()).isEqualTo(0);
        verify(runtimeSyncClient, never()).deleteDocuments(any(), any(), any(), any(), any(), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarketplaceDatasetDocumentEntity>> trackedDocumentsCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(datasetDocumentRepository).saveAll(trackedDocumentsCaptor.capture());
        assertThat(trackedDocumentsCaptor.getValue()).hasSize(3);
        assertThat(trackedDocumentsCaptor.getValue())
            .extracting(MarketplaceDatasetDocumentEntity::getDocumentId)
            .containsExactlyInAnyOrder("faq-refunds-30-day", "faq-password-reset", "faq-shipping-delay");
    }

    @Test
    void packagedSeedSyncDeletesStaleTrackedDocumentsWhenDatasetChanges() {
        DeploymentEntity deployment = deployment("dep-2");
        DeploymentVersionEntity version = version(datasetConfig("""
            {
              "contractVersion":"MARKETPLACE_DATASET_CONFIG_V1",
              "datasets":[
                {
                  "marketplaceInstallId":"mpi-help",
                  "marketplacePluginId":"mkp-data-help-center",
                  "datasetId":"help-center-seed",
                  "entityType":"faq-article",
                  "storageScope":"PLUGIN_SCOPED",
                  "sharingScope":"TENANT_SHARED",
                  "ingestionMode":"PACKAGED_SEED",
                  "updateStrategy":"UPSERT_BY_ID",
                  "handleRef":"plugin/mkp-data-help-center/tenant/ten-1/help-center-seed/hash/faq-article",
                  "datasetHash":"hash-help-center-v2",
                  "seedDatasetRef":"classpath:marketplace/datasets/help-center/help-center.jsonl",
                  "config":{"scope":"all"}
                }
              ]
            }
            """));
        DeploymentReleaseEntity release = release("rel-2");
        MarketplaceDatasetHandleEntity existingHandle = new MarketplaceDatasetHandleEntity();
        existingHandle.setId("mdh-help");
        existingHandle.setPluginId("mkp-data-help-center");
        existingHandle.setTenantId("ten-1");
        existingHandle.setDatasetId("help-center-seed");
        existingHandle.setStatus("READY");
        existingHandle.setDatasetHash("older-hash");
        existingHandle.setLastSyncAt(Instant.parse("2026-04-15T10:00:00Z"));
        when(installRepository.findById("mpi-help")).thenReturn(Optional.of(install("mpi-help", "mkv-data-help-center-v1")));
        when(pluginDatasetRepository.findByPluginVersionIdAndDatasetId("mkv-data-help-center-v1", "help-center-seed"))
            .thenReturn(Optional.of(pluginDataset("mkp-data-help-center", "help-center-seed", "faq-article")));
        when(datasetHandleRepository.findByPluginIdAndTenantIdAndDatasetId("mkp-data-help-center", "ten-1", "help-center-seed"))
            .thenReturn(Optional.of(existingHandle));
        when(datasetDocumentRepository.findByDatasetHandleIdOrderByDocumentIdAsc("mdh-help"))
            .thenReturn(List.of(existingTrackedDocument("mdh-help", "faq-legacy")));
        when(runtimeSyncClient.upsertDocuments(eq(deployment), eq("faq-article"), eq("help-center-seed"), anyString(), eq("hash-help-center-v2"), any()))
            .thenReturn(3);
        when(runtimeSyncClient.deleteDocuments(eq(deployment), eq("faq-article"), eq("help-center-seed"), anyString(), eq("hash-help-center-v2"), eq(List.of("faq-legacy"))))
            .thenReturn(1);

        service.syncReleaseDatasets(deployment, version, release);

        verify(runtimeSyncClient).deleteDocuments(
            eq(deployment),
            eq("faq-article"),
            eq("help-center-seed"),
            anyString(),
            eq("hash-help-center-v2"),
            eq(List.of("faq-legacy"))
        );
        verify(datasetDocumentRepository).deleteByDatasetHandleIdAndDocumentIdIn(eq("mdh-help"), anyCollection());
    }

    @Test
    void readyDatasetWithSameHashSkipsSync() {
        DeploymentEntity deployment = deployment("dep-3");
        DeploymentVersionEntity version = version(datasetConfig("""
            {
              "contractVersion":"MARKETPLACE_DATASET_CONFIG_V1",
              "datasets":[
                {
                  "marketplaceInstallId":"mpi-help",
                  "marketplacePluginId":"mkp-data-help-center",
                  "datasetId":"help-center-seed",
                  "entityType":"faq-article",
                  "storageScope":"PLUGIN_SCOPED",
                  "sharingScope":"TENANT_SHARED",
                  "ingestionMode":"PACKAGED_SEED",
                  "updateStrategy":"UPSERT_BY_ID",
                  "handleRef":"plugin/mkp-data-help-center/tenant/ten-1/help-center-seed/hash/faq-article",
                  "datasetHash":"hash-help-center",
                  "seedDatasetRef":"classpath:marketplace/datasets/help-center/help-center.jsonl",
                  "config":{"scope":"all"}
                }
              ]
            }
            """));
        DeploymentReleaseEntity release = release("rel-3");
        MarketplaceDatasetHandleEntity existingHandle = new MarketplaceDatasetHandleEntity();
        existingHandle.setId("mdh-help");
        existingHandle.setPluginId("mkp-data-help-center");
        existingHandle.setTenantId("ten-1");
        existingHandle.setDatasetId("help-center-seed");
        existingHandle.setStatus("READY");
        existingHandle.setDatasetHash("hash-help-center");
        existingHandle.setLastSyncAt(Instant.parse("2026-04-15T10:00:00Z"));
        when(installRepository.findById("mpi-help")).thenReturn(Optional.of(install("mpi-help", "mkv-data-help-center-v1")));
        when(pluginDatasetRepository.findByPluginVersionIdAndDatasetId("mkv-data-help-center-v1", "help-center-seed"))
            .thenReturn(Optional.of(pluginDataset("mkp-data-help-center", "help-center-seed", "faq-article")));
        when(datasetHandleRepository.findByPluginIdAndTenantIdAndDatasetId("mkp-data-help-center", "ten-1", "help-center-seed"))
            .thenReturn(Optional.of(existingHandle));

        MarketplaceDatasetSyncService.DatasetSyncSummary summary = service.syncReleaseDatasets(deployment, version, release);

        assertThat(summary.skippedDatasets()).isEqualTo(1);
        verify(runtimeSyncClient, never()).upsertDocuments(any(), any(), any(), any(), any(), any());
        verify(datasetDocumentRepository, never()).saveAll(anyCollection());
    }

    @Test
    void scheduledSyncReimportsExternalSqlDatasetWhenIntervalElapsed() {
        DeploymentEntity deployment = deployment("dep-4");
        DeploymentVersionEntity version = version(datasetConfig("""
            {
              "contractVersion":"MARKETPLACE_DATASET_CONFIG_V1",
              "datasets":[
                {
                  "marketplaceInstallId":"mpi-sql",
                  "marketplacePluginId":"mkp-data-commerce-catalog",
                  "datasetId":"commerce-catalog-sql",
                  "entityType":"product",
                  "storageScope":"PLUGIN_SCOPED",
                  "sharingScope":"TENANT_SHARED",
                  "ingestionMode":"EXTERNAL_SYNC_SQL",
                  "updateStrategy":"UPSERT_BY_ID",
                  "handleRef":"plugin/mkp-data-commerce-catalog/tenant/ten-1/commerce-catalog-sql/hash/product",
                  "datasetHash":"hash-commerce-catalog",
                  "syncConnector":{"connectionRef":"platform-marketplace-demo-sql","query":"select id, title, content, scope, metadata_json from marketplace_catalog_documents"},
                  "config":{"scope":"all"}
                }
              ]
            }
            """));
        DeploymentReleaseEntity release = release("rel-4");
        MarketplaceDatasetHandleEntity existingHandle = new MarketplaceDatasetHandleEntity();
        existingHandle.setId("mdh-sql");
        existingHandle.setPluginId("mkp-data-commerce-catalog");
        existingHandle.setTenantId("ten-1");
        existingHandle.setDatasetId("commerce-catalog-sql");
        existingHandle.setStatus("READY");
        existingHandle.setDatasetHash("hash-commerce-catalog");
        existingHandle.setLastSyncAt(Instant.now().minus(Duration.ofHours(2)));
        when(installRepository.findById("mpi-sql")).thenReturn(Optional.of(install("mpi-sql", "mkv-data-commerce-v1")));
        when(pluginDatasetRepository.findByPluginVersionIdAndDatasetId("mkv-data-commerce-v1", "commerce-catalog-sql"))
            .thenReturn(Optional.of(pluginDataset("mkp-data-commerce-catalog", "commerce-catalog-sql", "product")));
        when(datasetHandleRepository.findByPluginIdAndTenantIdAndDatasetId("mkp-data-commerce-catalog", "ten-1", "commerce-catalog-sql"))
            .thenReturn(Optional.of(existingHandle));
        when(datasetDocumentRepository.findByDatasetHandleIdOrderByDocumentIdAsc("mdh-sql"))
            .thenReturn(List.of());
        when(jdbcTemplate.queryForList("select id, title, content, scope, metadata_json from marketplace_catalog_documents"))
            .thenReturn(List.of(Map.of(
                "id", "sku-1",
                "title", "Alienware m18 R2",
                "content", "Gaming laptop with RTX graphics.",
                "scope", "all",
                "metadata_json", "{\"brand\":\"Alienware\"}"
            )));
        when(runtimeSyncClient.upsertDocuments(eq(deployment), eq("product"), eq("commerce-catalog-sql"), anyString(), eq("hash-commerce-catalog"), any()))
            .thenReturn(1);

        MarketplaceDatasetSyncService.DatasetSyncSummary summary = service.syncScheduledDatasets(
            deployment,
            version,
            release,
            Duration.ofHours(1)
        );

        assertThat(summary.datasetsCount()).isEqualTo(1);
        assertThat(summary.syncedDatasets()).isEqualTo(1);
        verify(runtimeSyncClient).upsertDocuments(eq(deployment), eq("product"), eq("commerce-catalog-sql"), anyString(), eq("hash-commerce-catalog"), any());
    }

    @Test
    void scheduledSyncSkipsExternalDatasetBeforeMinimumInterval() {
        DeploymentEntity deployment = deployment("dep-5");
        DeploymentVersionEntity version = version(datasetConfig("""
            {
              "contractVersion":"MARKETPLACE_DATASET_CONFIG_V1",
              "datasets":[
                {
                  "marketplaceInstallId":"mpi-sql",
                  "marketplacePluginId":"mkp-data-commerce-catalog",
                  "datasetId":"commerce-catalog-sql",
                  "entityType":"product",
                  "storageScope":"PLUGIN_SCOPED",
                  "sharingScope":"TENANT_SHARED",
                  "ingestionMode":"EXTERNAL_SYNC_SQL",
                  "updateStrategy":"UPSERT_BY_ID",
                  "handleRef":"plugin/mkp-data-commerce-catalog/tenant/ten-1/commerce-catalog-sql/hash/product",
                  "datasetHash":"hash-commerce-catalog",
                  "syncConnector":{"connectionRef":"platform-marketplace-demo-sql","query":"select id, title, content, scope, metadata_json from marketplace_catalog_documents"},
                  "config":{"scope":"all"}
                }
              ]
            }
            """));
        DeploymentReleaseEntity release = release("rel-5");
        MarketplaceDatasetHandleEntity existingHandle = new MarketplaceDatasetHandleEntity();
        existingHandle.setId("mdh-sql");
        existingHandle.setPluginId("mkp-data-commerce-catalog");
        existingHandle.setTenantId("ten-1");
        existingHandle.setDatasetId("commerce-catalog-sql");
        existingHandle.setStatus("READY");
        existingHandle.setDatasetHash("hash-commerce-catalog");
        existingHandle.setLastSyncAt(Instant.now().minus(Duration.ofMinutes(10)));
        when(datasetHandleRepository.findByPluginIdAndTenantIdAndDatasetId("mkp-data-commerce-catalog", "ten-1", "commerce-catalog-sql"))
            .thenReturn(Optional.of(existingHandle));

        MarketplaceDatasetSyncService.DatasetSyncSummary summary = service.syncScheduledDatasets(
            deployment,
            version,
            release,
            Duration.ofHours(1)
        );

        assertThat(summary.datasetsCount()).isEqualTo(0);
        verify(runtimeSyncClient, never()).upsertDocuments(any(), any(), any(), any(), any(), any());
    }

    private DeploymentEntity deployment(String id) {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId(id);
        deployment.setCustomerId("cust-1");
        deployment.setTenantId("ten-1");
        deployment.setRuntimeBaseUrl("https://runtime.example");
        return deployment;
    }

    private DeploymentVersionEntity version(String marketplaceDatasetConfigJson) {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        version.setMarketplaceDatasetConfigJson(marketplaceDatasetConfigJson);
        return version;
    }

    private DeploymentReleaseEntity release(String id) {
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId(id);
        return release;
    }

    private DeploymentMarketplacePluginInstallEntity install(String id, String pluginVersionId) {
        DeploymentMarketplacePluginInstallEntity install = new DeploymentMarketplacePluginInstallEntity();
        install.setId(id);
        install.setPluginVersionId(pluginVersionId);
        return install;
    }

    private MarketplacePluginDatasetEntity pluginDataset(String pluginId, String datasetId, String entityType) {
        MarketplacePluginDatasetEntity dataset = new MarketplacePluginDatasetEntity();
        dataset.setId("mpd-" + datasetId);
        dataset.setPluginId(pluginId);
        dataset.setDatasetId(datasetId);
        dataset.setEntityType(entityType);
        dataset.setStorageScope("PLUGIN_SCOPED");
        dataset.setSharingScope("TENANT_SHARED");
        dataset.setDatasetHash("hash");
        return dataset;
    }

    private MarketplaceDatasetDocumentEntity existingTrackedDocument(String datasetHandleId, String documentId) {
        MarketplaceDatasetDocumentEntity entity = new MarketplaceDatasetDocumentEntity();
        entity.setId("mdd-existing");
        entity.setDatasetHandleId(datasetHandleId);
        entity.setDocumentId(documentId);
        entity.setEntityType("faq-article");
        entity.setContentFingerprint("fingerprint");
        entity.setDatasetHash("older-hash");
        entity.setMetadataJson("{}");
        entity.setCreatedAt(Instant.parse("2026-04-15T10:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-04-15T10:00:00Z"));
        return entity;
    }

    private String datasetConfig(String json) {
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(json);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
