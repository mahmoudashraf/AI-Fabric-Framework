package com.ai.behavior.ingestion.impl;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.exception.BehaviorStorageException;
import com.ai.behavior.ingestion.BehaviorSignalSink;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.storage.BehaviorSignalRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "ai.behavior.sink", name = "type", havingValue = "hybrid")
public class HybridEventSink implements BehaviorSignalSink {

    private final StringRedisTemplate redisTemplate;
    private final BehaviorSignalRepository repository;
    private final ObjectMapper objectMapper;
    private final BehaviorModuleProperties properties;

    @Override
    @Transactional
    public void accept(BehaviorSignal event) throws BehaviorStorageException {
        BehaviorSignal persisted = repository.save(event);
        cache(persisted);
    }

    @Override
    @Transactional
    public void acceptBatch(List<BehaviorSignal> events) throws BehaviorStorageException {
        List<BehaviorSignal> persisted = repository.saveAll(events);
        for (BehaviorSignal event : persisted) {
            cache(event);
        }
    }

    @Override
    public String getSinkType() {
        return "hybrid";
    }

    private void cache(BehaviorSignal event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            Duration ttl = resolveTtl();
            String cacheKey = key(event);
            redisTemplate.opsForValue().set(cacheKey, payload, ttl);
            if (log.isDebugEnabled()) {
                boolean cached = Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey));
                log.debug("Cached behavior event {} (key={}, ttl={}s, cached={})",
                    event.getId(), cacheKey, ttl.toSeconds(), cached);
            }
        } catch (JsonProcessingException ex) {
            throw new BehaviorStorageException("Failed to serialize behavior event for hybrid sink", ex);
        }
    }

    private String key(BehaviorSignal event) {
        return "behavior:hot:" + event.getId();
    }

    private Duration resolveTtl() {
        long ttlSeconds = properties.getSink().getHybrid().getHotRetentionSeconds();
        if (ttlSeconds > 0) {
            return Duration.ofSeconds(ttlSeconds);
        }
        int ttlDays = Math.max(1, properties.getSink().getHybrid().getHotRetentionDays());
        return Duration.ofDays(ttlDays);
    }
}
