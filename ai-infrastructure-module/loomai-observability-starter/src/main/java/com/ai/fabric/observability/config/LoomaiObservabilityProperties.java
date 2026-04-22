package com.ai.fabric.observability.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("loomai.observability")
public class LoomaiObservabilityProperties {

    private boolean enabled = true;
    private String product = "loomai";
    private String service = "application";
    private String environment = "local";
    private final Http http = new Http();
    private final Metrics metrics = new Metrics();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Http getHttp() {
        return http;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public static class Http {

        private String requestIdHeader = "X-Request-Id";
        private String responseIdHeader = "X-Request-Id";
        private final List<String> pathVariableKeys = new ArrayList<>(List.of(
            "deploymentId",
            "releaseId",
            "verificationRunId",
            "tenantId",
            "merchantId",
            "serviceRef",
            "productServiceRef",
            "customerId",
            "shopDomain"
        ));

        public String getRequestIdHeader() {
            return requestIdHeader;
        }

        public void setRequestIdHeader(String requestIdHeader) {
            this.requestIdHeader = requestIdHeader;
        }

        public String getResponseIdHeader() {
            return responseIdHeader;
        }

        public void setResponseIdHeader(String responseIdHeader) {
            this.responseIdHeader = responseIdHeader;
        }

        public List<String> getPathVariableKeys() {
            return pathVariableKeys;
        }
    }

    public static class Metrics {

        private int maxUriTags = 100;
        private final List<String> deniedTagKeys = new ArrayList<>(List.of(
            "requestId",
            "traceId",
            "spanId",
            "actorId",
            "sessionId",
            "merchantId",
            "tenantId",
            "deploymentId",
            "releaseId",
            "verificationRunId",
            "shopDomain",
            "destination",
            "userId"
        ));
        private final Map<String, String> commonTags = new LinkedHashMap<>();

        public int getMaxUriTags() {
            return maxUriTags;
        }

        public void setMaxUriTags(int maxUriTags) {
            this.maxUriTags = maxUriTags;
        }

        public List<String> getDeniedTagKeys() {
            return deniedTagKeys;
        }

        public Map<String, String> getCommonTags() {
            return commonTags;
        }
    }
}
