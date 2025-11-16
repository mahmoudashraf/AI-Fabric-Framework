package com.ai.behavior.ingestion;

import com.ai.behavior.exception.BehaviorStorageException;
import com.ai.behavior.model.BehaviorEvent;

import java.util.List;

public interface BehaviorEventSink {

    void accept(BehaviorEvent event) throws BehaviorStorageException;

    void acceptBatch(List<BehaviorEvent> events) throws BehaviorStorageException;

    default void flush() {
        // no-op
    }

    String getSinkType();
}
