package com.ai.behavior.ingestion;

import com.ai.behavior.exception.BehaviorStorageException;
import com.ai.behavior.model.BehaviorSignal;

import java.util.List;

/**
 * Pluggable storage abstraction for behavior event ingestion.
 */
public interface BehaviorSignalSink {

    void accept(BehaviorSignal event) throws BehaviorStorageException;

    void acceptBatch(List<BehaviorSignal> events) throws BehaviorStorageException;

    default void flush() {
        // no-op by default
    }

    String getSinkType();
}
