package com.ai.behavior.ingestion.impl;

import com.ai.behavior.exception.BehaviorStorageException;
import com.ai.behavior.ingestion.BehaviorEventSink;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.repository.BehaviorEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.behavior.sink", name = "type", havingValue = "database", matchIfMissing = true)
public class DatabaseEventSink implements BehaviorEventSink {

    private final BehaviorEventRepository repository;

    @Override
    @Transactional
    public void accept(BehaviorEvent event) {
        try {
            repository.save(event);
        } catch (Exception ex) {
            throw new BehaviorStorageException("Failed to persist behavior event", ex);
        }
    }

    @Override
    @Transactional
    public void acceptBatch(List<BehaviorEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        try {
            repository.saveAll(events);
        } catch (Exception ex) {
            throw new BehaviorStorageException("Failed to persist batch of behavior events", ex);
        }
    }

    @Override
    public String getSinkType() {
        return "database";
    }
}
