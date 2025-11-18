package com.ai.behavior.adapter;

import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorQuery;

import java.util.List;

/**
 * Adapter abstraction for pulling behavior events from external analytics platforms (Mixpanel, GA, etc.).
 */
public interface ExternalAnalyticsAdapter {

    List<BehaviorSignal> fetchEvents(BehaviorQuery query);

    String getProviderName();
}
