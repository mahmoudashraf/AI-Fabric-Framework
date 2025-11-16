package com.ai.behavior.ingestion.event;

import com.ai.behavior.model.BehaviorEvent;

import java.util.List;

public record BehaviorEventBatchIngested(List<BehaviorEvent> events) {
}
