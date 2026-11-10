package com.ai.fabric.runtime.tasks;

import java.util.LinkedHashMap;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.health.actuate.endpoint.AdditionalHealthEndpointPath;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroupsPostProcessor;
import org.springframework.boot.health.actuate.endpoint.HttpCodeStatusMapper;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;
import org.springframework.stereotype.Component;

/** Registration failure affects readiness, never liveness or existing group policy. */
@Component
public final class ManagedTasksHealthGroups implements HealthEndpointGroupsPostProcessor {
    @Override public HealthEndpointGroups postProcessHealthEndpointGroups(HealthEndpointGroups groups) {
        var named = new LinkedHashMap<String, HealthEndpointGroup>();
        for (String name : groups.getNames()) named.put(name, groups.get(name));
        var readiness = groups.get("readiness");
        named.put("readiness", wrap(readiness == null ? groups.getPrimary() : readiness, true, readiness == null));
        var liveness = groups.get("liveness");
        if (liveness != null) named.put("liveness", wrap(liveness, false, false));
        return HealthEndpointGroups.of(groups.getPrimary(), named);
    }

    private static HealthEndpointGroup wrap(HealthEndpointGroup delegate, boolean ready, boolean created) {
        return new HealthEndpointGroup() {
            @Override public boolean isMember(String name) {
                if ("managedTasks".equals(name)) return ready;
                return created ? "readinessState".equals(name) : delegate.isMember(name);
            }
            @Override public boolean showComponents(SecurityContext context) { return delegate.showComponents(context); }
            @Override public boolean showDetails(SecurityContext context) { return delegate.showDetails(context); }
            @Override public StatusAggregator getStatusAggregator() { return delegate.getStatusAggregator(); }
            @Override public HttpCodeStatusMapper getHttpCodeStatusMapper() { return delegate.getHttpCodeStatusMapper(); }
            @Override public AdditionalHealthEndpointPath getAdditionalPath() { return delegate.getAdditionalPath(); }
        };
    }
}
