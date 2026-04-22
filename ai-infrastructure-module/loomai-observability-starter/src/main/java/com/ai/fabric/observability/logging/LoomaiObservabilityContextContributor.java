package com.ai.fabric.observability.logging;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

@FunctionalInterface
public interface LoomaiObservabilityContextContributor {

    Map<String, String> contribute(HttpServletRequest request);
}
