package com.ai.behavior.processing.worker;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.ingestion.event.BehaviorEventIngested;
import com.ai.behavior.service.BehaviorEmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmbeddingGenerationWorker {

    private final BehaviorModuleProperties properties;
    private final BehaviorEmbeddingService embeddingService;

    @Async("behaviorAsyncExecutor")
    @EventListener
    public void onEvent(BehaviorEventIngested ingested) {
        if (!properties.getProcessing().getEmbedding().isEnabled()) {
            return;
        }
        embeddingService.handleEmbedding(ingested.event());
    }
}
