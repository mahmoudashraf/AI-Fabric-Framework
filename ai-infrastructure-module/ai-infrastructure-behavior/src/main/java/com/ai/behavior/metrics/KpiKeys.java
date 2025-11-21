package com.ai.behavior.metrics;

/**
 * Shared KPI key definitions so that projectors, analyzers, DTOs, and tests all speak
 * the same language when referencing neutral behavior metrics.
 */
public final class KpiKeys {

    private KpiKeys() {
    }

    public static final String ENGAGEMENT_SCORE = "kpi.engagement.score";
    public static final String ENGAGEMENT_VELOCITY = "kpi.engagement.velocity";
    public static final String ENGAGEMENT_INTERACTION_DENSITY = "kpi.engagement.interaction_density";

    public static final String RECENCY_SCORE = "kpi.recency.score";
    public static final String RECENCY_HOURS_SINCE_LAST = "kpi.recency.hours_since_last";

    public static final String DIVERSITY_SCORE = "kpi.diversity.score";
    public static final String DIVERSITY_UNIQUE_SCHEMA_RATIO = "kpi.diversity.unique_schema_ratio";
    public static final String DIVERSITY_UNIQUE_SCHEMA_COUNT = "kpi.diversity.unique_schema_count";
}
