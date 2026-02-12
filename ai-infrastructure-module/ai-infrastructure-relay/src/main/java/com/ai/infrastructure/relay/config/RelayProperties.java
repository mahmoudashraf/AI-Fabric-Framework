package com.ai.infrastructure.relay.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "relay")
public class RelayProperties {

    @Valid
    private Auth auth = new Auth();

    @Valid
    private Limits limits = new Limits();

    @Valid
    private RateLimits rateLimits = new RateLimits();

    @Valid
    private Idempotency idempotency = new Idempotency();

    @Valid
    private Audit audit = new Audit();

    @Valid
    private Routing routing = new Routing();

    @Data
    public static class Auth {
        @Valid
        private ApiKey apiKey = new ApiKey();

        @Valid
        private Hmac hmac = new Hmac();
    }

    @Data
    public static class ApiKey {
        private boolean enabled = false;
        private String header = "X-AIFABRIC-API-KEY";
        private String value;
    }

    @Data
    public static class Hmac {
        private boolean enabled = false;
        private String timestampHeader = "X-AIFABRIC-TIMESTAMP";
        private String nonceHeader = "X-AIFABRIC-NONCE";
        private String signatureHeader = "X-AIFABRIC-SIGNATURE";
        private String secret;

        @Min(0)
        @Max(3600)
        private int maxClockSkewSeconds = 300;

        @Min(1)
        @Max(86400)
        private int nonceTtlSeconds = 600;
    }

    @Data
    public static class Limits {
        @Min(1024)
        @Max(5 * 1024 * 1024)
        private int maxBodyBytes = 256 * 1024;

        @Min(100)
        @Max(120_000)
        private int defaultTimeoutMs = 5000;

        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(5);
    }

    @Data
    public static class RateLimits {
        @Valid
        private Window perUser = new Window();

        @Valid
        private Map<String, Window> perAction = new LinkedHashMap<>();
    }

    @Data
    public static class Window {
        @Min(1)
        @Max(86_400)
        private int windowSeconds = 60;

        @Min(1)
        @Max(1_000_000)
        private int maxRequests = 100;
    }

    @Data
    public static class Idempotency {
        private boolean enabled = true;

        @Min(60)
        @Max(7 * 24 * 3600)
        private int ttlSeconds = 48 * 3600;
    }

    @Data
    public static class Audit {
        private boolean enabled = true;
    }

    @Data
    public static class Routing {
        private Mode mode = Mode.MAPPING;

        @Valid
        private Dispatcher dispatcher = new Dispatcher();

        @Valid
        private Retrieval retrieval = new Retrieval();

        @Valid
        private Map<String, Route> actions = new LinkedHashMap<>();

        public enum Mode {
            MAPPING,
            DISPATCHER
        }
    }

    @Data
    public static class Dispatcher {
        private String url;
        private Integer timeoutMs;
    }

    @Data
    public static class Retrieval {
        private String url;
        private Integer timeoutMs;
    }

    @Data
    public static class Route {
        @NotBlank
        private String url;

        private String method = "POST";

        private Integer timeoutMs;
    }
}
