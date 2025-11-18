package com.ai.behavior.service;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorEmbedding;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.storage.BehaviorEmbeddingRepository;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorEmbeddingService {

    private final BehaviorModuleProperties properties;
    private final BehaviorEmbeddingRepository embeddingRepository;
    private final AICoreService aiCoreService;

    @Transactional
    public void handleEmbedding(BehaviorSignal event) {
        var embeddingConfig = properties.getProcessing().getEmbedding();
        if (!embeddingConfig.isEnabled()) {
            return;
        }
        if (!embeddingConfig.getEventTypes().contains(event.getEventType().name())) {
            return;
        }
        String text = extractText(event);
        if (text == null || text.length() < embeddingConfig.getMinTextLength()) {
            return;
        }

        AIEmbeddingRequest request = AIEmbeddingRequest.builder()
            .text(text)
            .entityType("behavior_event")
            .entityId(event.getId().toString())
            .build();

        AIEmbeddingResponse response = aiCoreService.generateEmbedding(request);
        BehaviorEmbedding embedding = BehaviorEmbedding.builder()
            .behaviorEventId(event.getId())
            .embeddingType(event.getEventType().name().toLowerCase(Locale.ROOT))
            .originalText(text)
            .embedding(response.getEmbedding())
            .model(response.getModel())
            .build();
        embeddingRepository.save(embedding);
        log.debug("Generated embedding for event {}", event.getId());
    }

    private String extractText(BehaviorSignal event) {
        return switch (event.getEventType()) {
            case FEEDBACK -> event.metadataValue("feedback_text").orElse(null);
            case REVIEW -> event.metadataValue("review_text").orElse(null);
            case RATING -> event.metadataValue("review_text").orElse(event.metadataValue("comment").orElse(null));
            case SEARCH -> event.metadataValue("query").orElse(event.metadataValue("search_text").orElse(null));
            default -> null;
        };
    }
}
