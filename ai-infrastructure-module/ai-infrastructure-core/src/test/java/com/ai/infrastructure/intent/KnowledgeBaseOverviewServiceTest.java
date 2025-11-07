package com.ai.infrastructure.intent;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseOverviewServiceTest {

    @Test
    void shouldBuildOverviewFromStatisticsAndRepositoryFallbacks() {
        VectorDatabaseService vectorDatabaseService = Mockito.mock(VectorDatabaseService.class);
        AISearchableEntityRepository repository = Mockito.mock(AISearchableEntityRepository.class);

        Mockito.when(vectorDatabaseService.getStatistics()).thenReturn(Map.of(
            "totalVectors", 12,
            "entityTypeCounts", Map.of("faq", 7, "policies", 5)
        ));

        LocalDateTime updatedAt = LocalDateTime.now();
        Mockito.when(repository.findFirstByVectorUpdatedAtIsNotNullOrderByVectorUpdatedAtDesc())
            .thenReturn(Optional.of(AISearchableEntity.builder().vectorUpdatedAt(updatedAt).build()));

        KnowledgeBaseOverviewService service = new KnowledgeBaseOverviewService(vectorDatabaseService, repository);

        KnowledgeBaseOverview overview = service.getOverview();

        assertThat(overview.getTotalIndexedDocuments()).isEqualTo(12);
        assertThat(overview.getDocumentsByType()).containsEntry("faq", 7L);
        assertThat(overview.getLastIndexUpdateTime()).isEqualTo(updatedAt);
    }
}
