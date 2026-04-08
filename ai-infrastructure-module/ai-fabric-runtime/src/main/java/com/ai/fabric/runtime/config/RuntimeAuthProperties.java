package com.ai.fabric.runtime.config;

import com.ai.fabric.runtime.auth.RuntimeAuthIngressMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "ai.fabric.runtime.auth")
public class RuntimeAuthProperties {

    private Ingress ingress = new Ingress();
    private PublicTokens publicTokens = new PublicTokens();

    @Data
    public static class Ingress {
        private RuntimeAuthIngressMode mode = RuntimeAuthIngressMode.LEGACY_COMPATIBLE;
        private boolean legacyRequestIdentityEnabled = true;
        private boolean logLegacyRequestIdentity = true;
        private boolean rejectConflictingRequestIdentity = false;
        private TrustedBackend trustedBackend = new TrustedBackend();
        private Headers headers = new Headers();
    }

    @Data
    public static class TrustedBackend {
        private String apiKeyHeader = "X-AIFABRIC-RUNTIME-API-KEY";
        private String apiKeyValue;
    }

    @Data
    public static class Headers {
        private String subjectId = "X-AIFABRIC-AUTH-SUBJECT-ID";
        private String subjectType = "X-AIFABRIC-AUTH-SUBJECT-TYPE";
        private String authMode = "X-AIFABRIC-AUTH-MODE";
        private String callerType = "X-AIFABRIC-AUTH-CALLER-TYPE";
        private String sessionId = "X-AIFABRIC-AUTH-SESSION-ID";
        private String deploymentId = "X-AIFABRIC-AUTH-DEPLOYMENT-ID";
        private String customerId = "X-AIFABRIC-AUTH-CUSTOMER-ID";
        private String tenantId = "X-AIFABRIC-AUTH-TENANT-ID";
        private String issuer = "X-AIFABRIC-AUTH-ISSUER";
        private String expiresAt = "X-AIFABRIC-AUTH-EXPIRES-AT";
        private String scopes = "X-AIFABRIC-AUTH-SCOPES";
    }

    @Data
    public static class PublicTokens {
        private String authorizationHeader = "Authorization";
        private String tokenScheme = "Bearer";
        private String signingKey;
        private String issuer = "runtime-public-bootstrap";
        private List<String> acceptedIssuers = new ArrayList<>();
        private List<String> acceptedAudiences = new ArrayList<>();
        private List<String> anonymousGrantedScopes = new ArrayList<>(List.of("chat:query", "chat:suggestions", "chat:conversations"));
        private List<String> authenticatedDefaultScopes = new ArrayList<>(List.of("chat:query", "chat:suggestions", "chat:conversations"));
        private List<String> authenticatedAllowedScopes = new ArrayList<>(List.of("chat:query", "chat:suggestions", "chat:conversations"));
        private String defaultAudience;
        private long ttlSeconds = 900;
        private Bootstrap bootstrap = new Bootstrap();
        private Defaults defaults = new Defaults();
    }

    @Data
    public static class Bootstrap {
        private boolean enabled = false;
        private boolean allowMissingOrigin = false;
        private List<String> allowedOrigins = new ArrayList<>();
        private int maxRequestsPerWindow = 30;
        private int rateLimitWindowSeconds = 60;
    }

    @Data
    public static class Defaults {
        private String deploymentId;
        private String customerId;
        private String tenantId;
    }
}
