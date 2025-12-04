package com.ai.behavior.ingestion.event;

import com.ai.behavior.model.BehaviorSignal;

import java.util.List;

public record BehaviorSignalBatchIngested(List<BehaviorSignal> events) {
}
