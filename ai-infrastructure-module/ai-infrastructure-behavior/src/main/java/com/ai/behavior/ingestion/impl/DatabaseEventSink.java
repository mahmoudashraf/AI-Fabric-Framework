package com.ai.behavior.ingestion.impl;

import com.ai.behavior.exception.BehaviorStorageException;
import com.ai.behavior.ingestion.BehaviorSignalSink;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.storage.BehaviorSignalRepository;
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
public class DatabaseEventSink implements BehaviorSignalSink {

    private final BehaviorSignalRepository repository;

    @Override
    @Transactional
    public void accept(BehaviorSignal event) throws BehaviorStorageException {
        try {
            repository.save(event);
        } catch (Exception ex) {
            throw new BehaviorStorageException("Failed to persist behavior event", ex);
        }
    }

    @Override
    @Transactional
    public void acceptBatch(List<BehaviorSignal> events) throws BehaviorStorageException {
        try {
            repository.saveAll(events);
        } catch (Exception ex) {
            throw new BehaviorStorageException("Failed to persist behavior event batch", ex);
        }
    }

    @Override
    public String getSinkType() {
        return "database";
    }
}
