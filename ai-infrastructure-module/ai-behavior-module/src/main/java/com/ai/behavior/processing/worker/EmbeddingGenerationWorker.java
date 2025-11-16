package com.ai.behavior.processing.worker;

import com.ai.behavior.config.BehaviorProperties;
import com.ai.behavior.ingestion.event.BehaviorEventIngested;
import com.ai.behavior.model.BehaviorEmbedding;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.repository.BehaviorEmbeddingRepository;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingGenerationWorker {

    private final BehaviorProperties properties;
    private final AICoreService aiCoreService;
    private final BehaviorEmbeddingRepository embeddingRepository;

    @Async("aiBehaviorExecutor")
    @EventListener
    public void onEvent(BehaviorEventIngested ingested) {
        if (!properties.getProcessing().getEmbedding().isEnabled()) {
            return;
        }
        BehaviorEvent event = ingested.event();
        if (!shouldEmbed(event)) {
            return;
        }
        String text = extractText(event);
        if (text == null || text.length() < properties.getProcessing().getEmbedding().getMinTextLength()) {
            return;
        }
        try {
            var response = aiCoreService.generateEmbedding(AIEmbeddingRequest.builder()
                .text(text)
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .build());
            BehaviorEmbedding embedding = BehaviorEmbedding.builder()
                .behaviorEventId(event.getId())
                .embeddingType(event.getEventType().name().toLowerCase(Locale.ROOT))
                .originalText(text)
                .embedding(toFloatArray(response.getEmbedding()))
                .model(response.getModel())
                .build();
            embeddingRepository.save(embedding);
        } catch (Exception ex) {
            log.warn("Failed to generate embedding for event {}: {}", event.getId(), ex.getMessage());
        }
    }

    private boolean shouldEmbed(BehaviorEvent event) {
        Set<String> allowed = properties.getProcessing().getEmbedding().getEventTypes().stream()
            .map(String::toUpperCase)
            .collect(java.util.stream.Collectors.toSet());
        return event.getEventType() != null && allowed.contains(event.getEventType().name());
    }

    private String extractText(BehaviorEvent event) {
        if (event.getMetadata() == null) {
            return null;
        }
        return switch (event.getEventType()) {
            case FEEDBACK -> stringValue(event, "feedback_text");
            case REVIEW -> stringValue(event, "review_text");
            case SEARCH -> stringValue(event, "query");
            default -> null;
        };
    }

    private String stringValue(BehaviorEvent event, String key) {
        Object value = event.getMetadata().get(key);
        return value != null ? value.toString() : null;
    }

    private float[] toFloatArray(List<Double> source) {
        if (source == null || source.isEmpty()) {
            return new float[0];
        }
        float[] result = new float[source.size()];
        for (int i = 0; i < source.size(); i++) {
            result[i] = source.get(i).floatValue();
        }
        return result;
    }
}
