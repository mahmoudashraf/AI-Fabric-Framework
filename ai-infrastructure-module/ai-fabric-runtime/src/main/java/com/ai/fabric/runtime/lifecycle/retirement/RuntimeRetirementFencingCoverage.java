package com.ai.fabric.runtime.lifecycle.retirement;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
@org.springframework.context.annotation.Role(org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE)
public class RuntimeRetirementFencingCoverage {
    private final Set<String> installed = ConcurrentHashMap.newKeySet();
    public void installed(String boundary) { installed.add(boundary); }
    public boolean covers(String... boundaries) { return installed.containsAll(Set.of(boundaries)); }
}
