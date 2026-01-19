package com.ai.infrastructure.intent;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.dto.VectorRecord;
import com.ai.infrastructure.rag.VectorDatabaseService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseOverviewServiceTest {

    @Test
    void shouldBuildOverviewFromStatistics() {
        VectorDatabaseService vectorDatabaseService = Mockito.mock(VectorDatabaseService.class);
        AIEntityConfigurationLoader configurationLoader = Mockito.mock(AIEntityConfigurationLoader.class);

        Mockito.when(vectorDatabaseService.getStatistics()).thenReturn(Map.of(
            "totalVectors", 12,
            "entityTypeCounts", Map.of("faq", 7, "policies", 5)
        ));

        LocalDateTime updatedAt = LocalDateTime.now();
        Mockito.when(vectorDatabaseService.getVectorsByEntityType("faq"))
            .thenReturn(List.of(VectorRecord.builder().updatedAt(updatedAt).build()));
        Mockito.when(vectorDatabaseService.getVectorsByEntityType("policies"))
            .thenReturn(List.of());

        KnowledgeBaseOverviewService service = new KnowledgeBaseOverviewService(vectorDatabaseService, configurationLoader);

        KnowledgeBaseOverview overview = service.getOverview();

        assertThat(overview.getTotalIndexedDocuments()).isEqualTo(12);
        assertThat(overview.getDocumentsByType()).containsEntry("faq", 7L);
        assertThat(overview.getLastIndexUpdateTime()).isEqualTo(updatedAt);
    }
}

