package com.ai.behavior.adapter;

import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorQuery;

import java.util.List;

/**
 * Adapter abstraction for pulling behavior events from external analytics platforms (Mixpanel, GA, etc.).
 */
public interface ExternalAnalyticsAdapter {

    List<BehaviorEvent> fetchEvents(BehaviorQuery query);

    String getProviderName();
}
