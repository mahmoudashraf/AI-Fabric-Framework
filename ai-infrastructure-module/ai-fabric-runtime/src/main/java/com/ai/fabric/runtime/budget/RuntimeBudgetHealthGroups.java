package com.ai.fabric.runtime.budget;

import java.util.LinkedHashMap;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.health.actuate.endpoint.AdditionalHealthEndpointPath;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroupsPostProcessor;
import org.springframework.boot.health.actuate.endpoint.HttpCodeStatusMapper;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;

/** Add accounting to readiness, explicitly exclude it from liveness, preserve other membership/policy. */
public final class RuntimeBudgetHealthGroups implements HealthEndpointGroupsPostProcessor {
    @Override
    public HealthEndpointGroups postProcessHealthEndpointGroups(HealthEndpointGroups groups) {
        var named = new LinkedHashMap<String, HealthEndpointGroup>();
        for (String name : groups.getNames()) named.put(name, groups.get(name));
        var readiness = groups.get("readiness");
        named.put("readiness", group(readiness == null ? groups.getPrimary() : readiness, true, readiness == null));
        var liveness = groups.get("liveness");
        if (liveness != null) named.put("liveness", group(liveness, false, false));
        return HealthEndpointGroups.of(groups.getPrimary(), named);
    }

    private static HealthEndpointGroup group(HealthEndpointGroup delegate, boolean includeBudget, boolean newReadiness) {
        return new HealthEndpointGroup() {
            @Override public boolean isMember(String name) {
                if ("providerBudget".equals(name)) return includeBudget;
                return newReadiness ? "readinessState".equals(name) : delegate.isMember(name);
            }
            @Override public boolean showComponents(SecurityContext context) { return delegate.showComponents(context); }
            @Override public boolean showDetails(SecurityContext context) { return delegate.showDetails(context); }
            @Override public StatusAggregator getStatusAggregator() { return delegate.getStatusAggregator(); }
            @Override public HttpCodeStatusMapper getHttpCodeStatusMapper() { return delegate.getHttpCodeStatusMapper(); }
            @Override public AdditionalHealthEndpointPath getAdditionalPath() { return delegate.getAdditionalPath(); }
        };
    }
}
