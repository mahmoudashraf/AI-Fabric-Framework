package com.ai.behavior.metrics;

import com.ai.behavior.model.BehaviorMetrics;

import java.util.Map;

public class MetricAccumulator {

    private final BehaviorMetrics metrics;

    public MetricAccumulator(BehaviorMetrics metrics) {
        this.metrics = metrics;
    }

    public void increment(String key) {
        increment(key, 1.0d);
    }

    public void increment(String key, double delta) {
        metrics.incrementMetric(key, delta);
    }

    public void set(String key, double value) {
        metrics.setMetric(key, value);
    }

    public void max(String key, double candidate) {
        Map<String, Double> store = metrics.safeMetrics();
        Double existing = store.get(key);
        if (existing == null || candidate > existing) {
            store.put(key, candidate);
        }
    }

    public void min(String key, double candidate) {
        Map<String, Double> store = metrics.safeMetrics();
        Double existing = store.get(key);
        if (existing == null || candidate < existing) {
            store.put(key, candidate);
        }
    }

    public double value(String key) {
        return metrics.metricValue(key);
    }

    public BehaviorMetrics getMetrics() {
        return metrics;
    }

    public Map<String, Double> snapshot() {
        return metrics.safeMetrics();
    }
}
