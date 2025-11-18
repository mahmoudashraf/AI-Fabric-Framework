package com.ai.behavior.service;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorEmbedding;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.schema.BehaviorSchemaRegistry;
import com.ai.behavior.schema.BehaviorSignalDefinition;
import com.ai.behavior.schema.EmbeddingPolicy;
import com.ai.behavior.storage.BehaviorEmbeddingRepository;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorEmbeddingService {

    private final BehaviorModuleProperties properties;
    private final BehaviorEmbeddingRepository embeddingRepository;
    private final BehaviorSchemaRegistry schemaRegistry;
    private final AICoreService aiCoreService;

    @Transactional
    public void handleEmbedding(BehaviorSignal signal) {
        var embeddingConfig = properties.getProcessing().getEmbedding();
        if (!embeddingConfig.isEnabled()) {
            return;
        }
        BehaviorSignalDefinition definition = schemaRegistry.find(signal.getSchemaId()).orElse(null);
        if (definition == null) {
            return;
        }
        EmbeddingPolicy policy = definition.getEmbedding();
        if (policy == null || !policy.isEnabled()) {
            return;
        }
        List<String> configuredSchemas = embeddingConfig.getSchemaIds();
        if (!CollectionUtils.isEmpty(configuredSchemas) && !configuredSchemas.contains(definition.getId())) {
            return;
        }

        String textField = StringUtils.hasText(policy.getTextField()) ? policy.getTextField() : "text";
        String text = signal.attributeValue(textField).orElse(null);
        if (!StringUtils.hasText(text)) {
            return;
        }
        int minLength = Math.max(policy.getMinTextLength(), embeddingConfig.getMinTextLength());
        if (text.length() < minLength) {
            return;
        }

        AIEmbeddingRequest request = AIEmbeddingRequest.builder()
            .text(text)
            .entityType("behavior_signal")
            .entityId(signal.getId().toString())
            .build();

        AIEmbeddingResponse response = aiCoreService.generateEmbedding(request);
        BehaviorEmbedding embedding = BehaviorEmbedding.builder()
            .behaviorSignalId(signal.getId())
            .embeddingType(definition.getId())
            .originalText(text)
            .embedding(response.getEmbedding())
            .model(response.getModel())
            .build();
        embeddingRepository.save(embedding);
        log.debug("Generated embedding for signal {}", signal.getId());
    }
}
