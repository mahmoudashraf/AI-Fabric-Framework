package com.ai.behavior.ingestion.impl;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.exception.BehaviorStorageException;
import com.ai.behavior.ingestion.BehaviorEventSink;
import com.ai.behavior.model.BehaviorEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "ai.behavior.sink", name = "type", havingValue = "kafka")
public class KafkaEventSink implements BehaviorEventSink {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final BehaviorModuleProperties properties;

    @Override
    @Transactional
    public void accept(BehaviorEvent event) throws BehaviorStorageException {
        send(event);
    }

    @Override
    @Transactional
    public void acceptBatch(List<BehaviorEvent> events) throws BehaviorStorageException {
        for (BehaviorEvent event : events) {
            send(event);
        }
    }

    @Override
    public String getSinkType() {
        return "kafka";
    }

    private void send(BehaviorEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String key = event.getUserIdentifier();
            kafkaTemplate.send(properties.getSink().getKafka().getTopic(), key, payload).join();
        } catch (JsonProcessingException ex) {
            throw new BehaviorStorageException("Failed to serialize behavior event for Kafka", ex);
        } catch (Exception ex) {
            throw new BehaviorStorageException("Failed to publish behavior event to Kafka", ex);
        }
    }
}
