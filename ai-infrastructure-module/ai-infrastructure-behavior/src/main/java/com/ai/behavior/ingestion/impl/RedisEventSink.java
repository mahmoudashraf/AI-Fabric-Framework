package com.ai.behavior.ingestion.impl;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.exception.BehaviorStorageException;
import com.ai.behavior.ingestion.BehaviorSignalSink;
import com.ai.behavior.model.BehaviorSignal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "ai.behavior.sink", name = "type", havingValue = "redis")
public class RedisEventSink implements BehaviorSignalSink {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final BehaviorModuleProperties properties;

    @Override
    @Transactional
    public void accept(BehaviorSignal event) throws BehaviorStorageException {
        store(event);
    }

    @Override
    @Transactional
    public void acceptBatch(List<BehaviorSignal> events) throws BehaviorStorageException {
        for (BehaviorSignal event : events) {
            store(event);
        }
    }

    @Override
    public String getSinkType() {
        return "redis";
    }

    private void store(BehaviorSignal event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            Duration ttl = resolveTtl();
            redisTemplate.opsForValue().set(key(event), payload, ttl);
        } catch (JsonProcessingException ex) {
            throw new BehaviorStorageException("Failed to serialize behavior event for Redis", ex);
        }
    }

    private String key(BehaviorSignal event) {
        return "behavior:event:" + event.getId();
    }

    private Duration resolveTtl() {
        long ttlSeconds = properties.getSink().getRedis().getTtlSeconds();
        if (ttlSeconds > 0) {
            return Duration.ofSeconds(ttlSeconds);
        }
        int ttlDays = Math.max(1, properties.getSink().getRedis().getTtlDays());
        return Duration.ofDays(ttlDays);
    }
}
