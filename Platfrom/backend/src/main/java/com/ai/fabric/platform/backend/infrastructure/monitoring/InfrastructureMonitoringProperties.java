package com.ai.fabric.platform.backend.infrastructure.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;

@Component
@ConfigurationProperties("platform.infrastructure-monitoring")
public class InfrastructureMonitoringProperties {
    private boolean enabled;
    private boolean allowInsecureHttp;
    private String sourcesJson = "[]";
    private Duration timeout = Duration.ofSeconds(2);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public boolean isAllowInsecureHttp() { return allowInsecureHttp; }
    public void setAllowInsecureHttp(boolean value) { allowInsecureHttp = value; }
    public String getSourcesJson() { return sourcesJson; }
    public void setSourcesJson(String value) { sourcesJson = value; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration value) { timeout = value; }

    public List<Source> sources(ObjectMapper mapper) {
        if (!enabled) return List.of();
        try {
            if (sourcesJson == null || sourcesJson.length() > 32768 || timeout == null
                || timeout.toMillis() < 100 || timeout.toMillis() > 5000) throw new IllegalArgumentException();
            var values = mapper.readValue(sourcesJson, Source[].class);
            if (values == null || values.length > 8) throw new IllegalArgumentException();
            var ids = new HashSet<String>();
            for (var value : values) {
                if (value == null || !validId(value.id()) || !ids.add(value.id())
                    || value.displayName() == null || value.displayName().isBlank() || value.displayName().length() > 100
                    || !List.of("staging", "production", "development").contains(value.environment())
                    || value.readKey() == null || value.readKey().length() < 32 || value.readKey().length() > 512
                    || value.readKey().chars().anyMatch(c -> c < 33 || c > 126)) throw new IllegalArgumentException();
                var uri = URI.create(value.url());
                if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !("https".equals(uri.getScheme()) || allowInsecureHttp && "http".equals(uri.getScheme()))) {
                    throw new IllegalArgumentException();
                }
                if (value.providerServerUuid() != null && !validId(value.providerServerUuid())) throw new IllegalArgumentException();
            }
            return List.of(values);
        } catch (Exception exception) {
            // Monitoring is optional: invalid configuration must not stop Platform startup or releases.
            throw new MonitoringUnavailableException("MONITORING_CONFIGURATION_INVALID");
        }
    }

    static boolean validId(String value) { return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"); }

    public record Source(String id, String displayName, String environment, String url,
                         String readKey, String providerServerUuid) {
        @Override public String toString() { return "MonitoringSource[" + id + "]"; }
    }
}
