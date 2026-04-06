package com.ai.fabric.runtime.config;

import com.ai.fabric.runtime.auth.RuntimeAuthIngressMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.fabric.runtime.auth")
public class RuntimeAuthProperties {

    private Ingress ingress = new Ingress();

    @Data
    public static class Ingress {
        private RuntimeAuthIngressMode mode = RuntimeAuthIngressMode.LEGACY_COMPATIBLE;
        private boolean legacyRequestIdentityEnabled = true;
        private boolean logLegacyRequestIdentity = true;
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
}
