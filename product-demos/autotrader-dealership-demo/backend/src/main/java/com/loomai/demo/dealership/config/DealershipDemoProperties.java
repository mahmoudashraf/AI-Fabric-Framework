package com.loomai.demo.dealership.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "dealership")
public class DealershipDemoProperties {

    private boolean productionGuardsEnabled;
    private String id = "dealer-demo-001";
    private String name = "Northfield Motor House";
    private String sourceLabel = "Demonstration inventory";
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://127.0.0.1:4321", "http://localhost:4321"));
    private final Staff staff = new Staff();
    private final Internal internal = new Internal();
    private final Privacy privacy = new Privacy();
    private final Runtime runtime = new Runtime();

    public boolean isProductionGuardsEnabled() { return productionGuardsEnabled; }
    public void setProductionGuardsEnabled(boolean productionGuardsEnabled) { this.productionGuardsEnabled = productionGuardsEnabled; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSourceLabel() { return sourceLabel; }
    public void setSourceLabel(String sourceLabel) { this.sourceLabel = sourceLabel; }
    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins == null ? new ArrayList<>() : new ArrayList<>(allowedOrigins); }
    public Staff getStaff() { return staff; }
    public Internal getInternal() { return internal; }
    public Privacy getPrivacy() { return privacy; }
    public Runtime getRuntime() { return runtime; }

    public static class Staff {
        private String username;
        private String passwordHash;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPasswordHash() { return passwordHash; }
        public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    }

    public static class Internal {
        private String apiKeyHeader = "X-DEALERSHIP-INTERNAL-KEY";
        private String apiKey;
        public String getApiKeyHeader() { return apiKeyHeader; }
        public void setApiKeyHeader(String apiKeyHeader) { this.apiKeyHeader = apiKeyHeader; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    public static class Privacy {
        private String encryptionKeyBase64;
        private Duration leadRetention = Duration.ofDays(30);
        public String getEncryptionKeyBase64() { return encryptionKeyBase64; }
        public void setEncryptionKeyBase64(String encryptionKeyBase64) { this.encryptionKeyBase64 = encryptionKeyBase64; }
        public Duration getLeadRetention() { return leadRetention; }
        public void setLeadRetention(Duration leadRetention) { this.leadRetention = leadRetention; }
    }

    public static class Runtime {
        private boolean enabled;
        private String baseUrl;
        private String publicBootstrapPath = "/api/public/chat/session";
        private String queryPath = "/api/chat/me/query";
        private String suggestionsPath = "/api/chat/me/suggestions";
        private String authContextPath = "/api/chat/me/auth-context";
        private String shellConfigPath = "/api/chat/me/shell-config";
        private String conversationsPath = "/api/chat/me/conversations";
        private String conversationItemPathTemplate = "/api/chat/me/conversations/{conversationId}";
        private String vectorSpace = "dealer-vehicle";
        private Duration assertionTtl = Duration.ofMinutes(2);
        private final PrivateAccess privateAccess = new PrivateAccess();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getPublicBootstrapPath() { return publicBootstrapPath; }
        public void setPublicBootstrapPath(String publicBootstrapPath) { this.publicBootstrapPath = publicBootstrapPath; }
        public String getQueryPath() { return queryPath; }
        public void setQueryPath(String queryPath) { this.queryPath = queryPath; }
        public String getSuggestionsPath() { return suggestionsPath; }
        public void setSuggestionsPath(String suggestionsPath) { this.suggestionsPath = suggestionsPath; }
        public String getAuthContextPath() { return authContextPath; }
        public void setAuthContextPath(String authContextPath) { this.authContextPath = authContextPath; }
        public String getShellConfigPath() { return shellConfigPath; }
        public void setShellConfigPath(String shellConfigPath) { this.shellConfigPath = shellConfigPath; }
        public String getConversationsPath() { return conversationsPath; }
        public void setConversationsPath(String conversationsPath) { this.conversationsPath = conversationsPath; }
        public String getConversationItemPathTemplate() { return conversationItemPathTemplate; }
        public void setConversationItemPathTemplate(String conversationItemPathTemplate) { this.conversationItemPathTemplate = conversationItemPathTemplate; }
        public String getVectorSpace() { return vectorSpace; }
        public void setVectorSpace(String vectorSpace) { this.vectorSpace = vectorSpace; }
        public Duration getAssertionTtl() { return assertionTtl; }
        public void setAssertionTtl(Duration assertionTtl) { this.assertionTtl = assertionTtl; }
        public PrivateAccess getPrivateAccess() { return privateAccess; }
    }

    public static class PrivateAccess {
        private String trustedApiKeyHeader = "X-AIFABRIC-RUNTIME-API-KEY";
        private String trustedApiKey;
        private String authorizationHeader = "X-AIFABRIC-RUNTIME-AUTHORIZATION";
        private String signingKey;
        private String issuer = "dealership-demo-backend";
        private String audience;
        private String deploymentId;
        private String customerId;
        private String tenantId;

        public String getTrustedApiKeyHeader() { return trustedApiKeyHeader; }
        public void setTrustedApiKeyHeader(String trustedApiKeyHeader) { this.trustedApiKeyHeader = trustedApiKeyHeader; }
        public String getTrustedApiKey() { return trustedApiKey; }
        public void setTrustedApiKey(String trustedApiKey) { this.trustedApiKey = trustedApiKey; }
        public String getAuthorizationHeader() { return authorizationHeader; }
        public void setAuthorizationHeader(String authorizationHeader) { this.authorizationHeader = authorizationHeader; }
        public String getSigningKey() { return signingKey; }
        public void setSigningKey(String signingKey) { this.signingKey = signingKey; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }
        public String getDeploymentId() { return deploymentId; }
        public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    }
}
