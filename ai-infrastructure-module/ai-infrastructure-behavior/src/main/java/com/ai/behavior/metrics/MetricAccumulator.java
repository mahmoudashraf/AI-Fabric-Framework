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

    public double value(String key) {
        return metrics.metricValue(key);
    }

    public void attribute(String key, Object value) {
        metrics.setAttribute(key, value);
    }

    public void addDistinctAttributeValue(String key, String value) {
        metrics.addDistinctAttributeValue(key, value);
    }

    public int distinctAttributeCount(String key) {
        return metrics.distinctAttributeCount(key);
    }

    public BehaviorMetrics getMetrics() {
        return metrics;
    }

    public Map<String, Double> snapshot() {
        return metrics.safeMetrics();
    }

    public Map<String, Object> attributes() {
        return metrics.safeAttributes();
    }
}
