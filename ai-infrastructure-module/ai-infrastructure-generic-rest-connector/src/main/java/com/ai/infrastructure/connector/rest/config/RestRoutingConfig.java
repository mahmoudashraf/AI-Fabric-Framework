package com.ai.infrastructure.connector.rest.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class RestRoutingConfig {

    @Valid
    private Connector connector = new Connector();

    @Valid
    private Authz authz = new Authz();

    @Valid
    private Map<String, ActionRoute> actions = new LinkedHashMap<>();

    @Valid
    private Map<String, ConnectionProfile> connectionProfiles = new LinkedHashMap<>();

    @Valid
    private Map<String, ProtectedResourceBinding> protectedResources = new LinkedHashMap<>();

    @Valid
    private Map<String, HttpDataSource> dataSources = new LinkedHashMap<>();

    @Valid
    private Map<String, WebhookSource> webhooks = new LinkedHashMap<>();

    @Valid
    private RuntimeDataSync runtimeDataSync = new RuntimeDataSync();

    @Data
    public static class Connector {
        @Valid
        private InboundAuth inboundAuth = new InboundAuth();

        @Valid
        private Upstream upstream = new Upstream();

        @Valid
        private Http http = new Http();

        @Valid
        private Idempotency idempotency = new Idempotency();
    }

    @Data
    public static class Authz {
        /**
         * When true, {@code POST /api/authz/check} is enabled and forwards to the configured upstream.
         *
         * <p>This endpoint is protected by the same inbound auth filter as {@code /actions/execute}
         * when {@code connector.inbound-auth.allow-unauthenticated=false}.</p>
         */
        private boolean enabled = false;

        /**
         * Upstream path for the authz check endpoint (relative to {@code authz.upstream.base-url}).
         */
        private String path = "/api/authz/check";

        @Valid
        private Upstream upstream = new Upstream();

        @Valid
        private AuthzHttp http = new AuthzHttp();
    }

    @Data
    public static class AuthzHttp {
        @Min(100)
        @Max(120_000)
        private int connectTimeoutMs = 500;

        @Min(100)
        @Max(120_000)
        private int timeoutMs = 1500;
    }

    @Data
    public static class InboundAuth {
        /**
         * When false (default), inbound API key auth must be enabled.
         * Set to true only when protected by network controls and you explicitly accept the risk.
         */
        private boolean allowUnauthenticated = false;

        @Valid
        private ApiKey apiKey = new ApiKey();
    }

    @Data
    public static class ApiKey {
        private boolean enabled = false;
        private String header = "X-AIFABRIC-API-KEY";
        private String value;
    }

    @Data
    public static class Upstream {
        private String baseUrl;

        @Valid
        private UpstreamAuth auth = new UpstreamAuth();
    }

    @Data
    public static class UpstreamAuth {
        private AuthType type = AuthType.NONE;
        private String header = "Authorization";
        private String value;

        public enum AuthType {
            NONE,
            API_KEY
        }
    }

    @Data
    public static class Http {
        @Min(100)
        @Max(120_000)
        private int connectTimeoutMs = 2000;

        @Min(100)
        @Max(120_000)
        private int timeoutMs = 8000;

        @Valid
        private Retry retry = new Retry();
    }

    @Data
    public static class Retry {
        private boolean enabled = false;

        @Min(1)
        @Max(10)
        private int maxAttempts = 1;

        @Min(0)
        @Max(30_000)
        private int backoffMs = 200;

        private List<Integer> retryOn = List.of(429, 502, 503, 504);
    }

    @Data
    public static class Idempotency {
        private boolean enabled = true;

        @Min(60)
        @Max(7 * 24 * 3600)
        private int ttlSeconds = 300;

        @Min(0)
        @Max(120_000)
        private int inProgressMaxWaitMs = 2000;
    }

    @Data
    public static class ActionRoute {
        /**
         * Absolute upstream URL (overrides {@link #path} and {@code connector.upstream.baseUrl}).
         */
        private String url;

        /**
         * Upstream path (relative to {@code connector.upstream.baseUrl}).
         */
        private String path;

        private String method = "POST";

        private Integer timeoutMs;

        private String connectionProfileRef;

        private String protectedResourceBindingRef;

        private List<String> requiredCapabilityGrants = new ArrayList<>();

        private String idempotencyHeader;

        @Valid
        private List<ResourcePlacement> trustedResourcePlacements = new ArrayList<>();

        private Map<String, String> headers = new LinkedHashMap<>();

        @Valid
        private Request request = new Request();

        @Valid
        private Response response = new Response();

        @Valid
        private ActionAuthz authz = new ActionAuthz();
    }

    @Data
    public static class ActionAuthz {
        /**
         * When true, execute a remote authz preflight before the upstream action call.
         */
        private boolean enabled = false;

        /**
         * Resource identifier presented to the authz service. Supports templating from {@code params}
         * and {@code trace} context.
         */
        private String resourceId;

        /**
         * Operation type presented to the authz service. Defaults to EXECUTE_ACTION.
         */
        private String operationType = "EXECUTE_ACTION";

        /**
         * Optional requested scopes presented to the authz service.
         */
        private List<String> requestedScopes = new ArrayList<>();

        /**
         * Optional request context payload presented to the authz service.
         */
        private Object requestContext;
    }

    @Data
    public static class Request {
        private Map<String, Object> query = new LinkedHashMap<>();

        /**
         * Arbitrary JSON template (object/array/scalar) built from {@code params} and {@code trace}.
         */
        private Object body;
    }

    @Data
    public static class Response {
        /**
         * If empty, any 2xx status is considered success.
         */
        private List<Integer> successHttpStatus = new ArrayList<>();

        /**
         * Template for the connector {@code data} payload. If absent, the upstream JSON body is normalized and returned.
         */
        private Object result;

        /**
         * Optional message template. If absent, a stable default is returned.
         */
        private String message;

        /**
         * Optional pinned targets template.
         */
        private Object pinnedTargets;

        /**
         * Optional fail-closed filters applied to provider collections before response templates are resolved.
         */
        @Valid
        private List<ResponseCollectionFilter> collectionFilters = new ArrayList<>();

        /**
         * Optional authoritative override for whether a successful action result can ground an answer.
         * Canonical empty list results default to {@code INSUFFICIENT} when no override is configured.
         */
        private GroundingSufficiency groundingSufficiency;

        public enum GroundingSufficiency {
            SUFFICIENT,
            INSUFFICIENT
        }
    }

    @Data
    public static class ResponseCollectionFilter {
        private String collectionJsonPointer;
        private String countJsonPointer;
        @Valid
        private List<RecordInclusionCondition> inclusionConditions = new ArrayList<>();
    }

    @Data
    public static class ConnectionProfile {
        private String environment;
        private String baseUrl;
        private List<String> allowedHosts = new ArrayList<>();

        @Valid
        private ProviderAuth auth = new ProviderAuth();

        @Valid
        private RatePolicy ratePolicy = new RatePolicy();

        @Valid
        private List<ErrorMapping> errorMappings = new ArrayList<>();

        private List<String> capabilityGrants = new ArrayList<>();
        private List<String> correlationResponseHeaders = new ArrayList<>();
    }

    @Data
    public static class ProviderAuth {
        private Strategy strategy = Strategy.NONE;
        private String apiKeyHeader = "Authorization";
        private String apiKeyValue;
        private String tokenBaseUrl;
        private String tokenPath;
        private String tokenMethod = "POST";
        private Map<String, String> credentialFields = new LinkedHashMap<>();
        private Map<String, String> staticFields = new LinkedHashMap<>();
        private String tokenJsonPointer;
        private String absoluteExpiryJsonPointer;
        private String relativeExpiryJsonPointer;
        private String authorizationHeader = "Authorization";
        private String authorizationScheme = "Bearer";
        @Min(0)
        @Max(3600)
        private int expirySkewSeconds = 60;
        @Min(100)
        @Max(120_000)
        private int timeoutMs = 5000;

        public enum Strategy {
            NONE,
            API_KEY,
            FORM_TOKEN_EXCHANGE
        }
    }

    @Data
    public static class RatePolicy {
        @Min(1)
        @Max(100)
        private int maxConcurrent = 4;
        @Min(0)
        @Max(60_000)
        private int minIntervalMs = 0;
        @Min(0)
        @Max(3_600_000)
        private int rateLimitedPauseMs = 30_000;
        @Min(0)
        @Max(3_600_000)
        private int unavailablePauseMs = 5_000;
        @Min(1)
        @Max(5)
        private int maxAttempts = 1;
        @Min(0)
        @Max(30_000)
        private int retryBackoffMs = 200;
        private List<Integer> retryStatuses = new ArrayList<>(List.of(429, 502, 503, 504));
    }

    @Data
    public static class ErrorMapping {
        @Min(100)
        @Max(599)
        private int status;
        private String bodyJsonPointer;
        private String equalsValue;
        private ErrorClass errorClass;

        public enum ErrorClass {
            BAD_REQUEST,
            AUTHENTICATION_REQUIRED,
            RESOURCE_ACCESS_DENIED,
            CAPABILITY_DENIED,
            RATE_LIMITED,
            SERVICE_UNAVAILABLE,
            TIMEOUT,
            MALFORMED_RESPONSE
        }
    }

    @Data
    public static class ProtectedResourceBinding {
        private String connectionProfileRef;
        private String environment;
        private String resourceType;
        private String resourceId;
        private String displayValue;
        private String policyRef;
        private List<String> capabilityGrants = new ArrayList<>();
    }

    @Data
    public static class ResourcePlacement {
        private Target target;
        private String field;
        private String jsonPointer;

        public enum Target {
            QUERY,
            PATH,
            HEADER,
            BODY
        }
    }

    @Data
    public static class HttpDataSource {
        private boolean enabled = true;
        private String sourceVersion;
        private String knowledgeSourceHandleRef;
        private String connectionProfileRef;
        private String protectedResourceBindingRef;
        private List<String> requiredCapabilityGrants = new ArrayList<>();
        private String path;
        private String method = "GET";
        private List<Integer> completeHttpStatuses = new ArrayList<>(List.of(200));
        private Map<String, Object> query = new LinkedHashMap<>();
        private Map<String, String> headers = new LinkedHashMap<>();
        @Valid
        private List<ResourcePlacement> trustedResourcePlacements = new ArrayList<>();
        @Valid
        private Pagination pagination = new Pagination();
        @Valid
        private RecordMapping mapping = new RecordMapping();
        @Valid
        private TombstonePolicy tombstonePolicy = new TombstonePolicy();
        @Valid
        private TargetedRecordFetch targetedRecordFetch = new TargetedRecordFetch();
        private String vectorSpace;
        private String entityType;
        @Min(10)
        @Max(86_400)
        private int scheduleSeconds = 900;
    }

    @Data
    public static class TargetedRecordFetch {
        private boolean enabled = false;
        private String path;
        private String method = "GET";
        private List<Integer> completeHttpStatuses = new ArrayList<>(List.of(200));
        private List<Integer> absentHttpStatuses = new ArrayList<>(List.of(404));
        private Map<String, Object> query = new LinkedHashMap<>();
        private Map<String, String> headers = new LinkedHashMap<>();
        @Valid
        private RecordKeyPlacement recordKeyPlacement = new RecordKeyPlacement();
    }

    @Data
    public static class RecordKeyPlacement {
        private Target target;
        private String field;

        public enum Target {
            QUERY,
            PATH,
            HEADER
        }
    }

    @Data
    public static class Pagination {
        private Strategy strategy = Strategy.NONE;
        private String pageQuery = "page";
        private String sizeQuery = "pageSize";
        @Min(0)
        @Max(1_000_000)
        private int startPage = 1;
        @Min(1)
        @Max(1000)
        private int pageSize = 100;
        @Min(1)
        @Max(10_000)
        private int maxPages = 100;
        private String cursorQuery = "cursor";
        private String nextCursorJsonPointer;

        public enum Strategy {
            NONE,
            PAGE_SIZE,
            CURSOR
        }
    }

    @Data
    public static class RecordMapping {
        private String recordsJsonPointer = "";
        private String idJsonPointer;
        private String resourceJsonPointer;
        private Map<String, String> contentFields = new LinkedHashMap<>();
        private Map<String, String> entityFields = new LinkedHashMap<>();
        private Map<String, String> metadataFields = new LinkedHashMap<>();
        @Valid
        private List<RecordInclusionCondition> inclusionConditions = new ArrayList<>();
        @Min(1)
        @Max(100_000)
        private int maxRecords = 10_000;
        @Min(1024)
        @Max(50 * 1024 * 1024)
        private int maxResponseBytes = 5 * 1024 * 1024;
    }

    @Data
    public static class RecordInclusionCondition {
        private String jsonPointer;
        private List<String> allowedValues = new ArrayList<>();
    }

    @Data
    public static class TombstonePolicy {
        private Strategy strategy = Strategy.NONE;
        private String operationJsonPointer;
        private List<String> deleteValues = new ArrayList<>();

        public enum Strategy {
            NONE,
            ABSENT_FROM_SNAPSHOT,
            FIELD_VALUE,
            ABSENT_OR_FIELD_VALUE
        }
    }

    @Data
    public static class RuntimeDataSync {
        private boolean enabled = false;
        private String baseUrl;
        private String apiKeyHeader = "X-AIFABRIC-INTEGRATION-KEY";
        private String apiKeyValue;
        private String deploymentId;
        private String tenantId;
        @Min(100)
        @Max(120_000)
        private int timeoutMs = 15_000;
        @Min(100)
        @Max(60_000)
        private int workPollIntervalMs = 500;
        @Min(1)
        @Max(600)
        private int workPollAttempts = 60;
    }

    @Data
    public static class WebhookSource {
        private boolean enabled = true;
        private boolean registrationExpected = false;
        private boolean manualReplayEnabled = false;
        private String method = "PUT";
        private List<String> allowedContentTypes = new ArrayList<>(List.of("application/json"));
        private String protectedResourceBindingRef;
        @Valid
        private WebhookVerification verification = new WebhookVerification();
        private String eventIdJsonPointer;
        private List<String> eventIdentityJsonPointers = new ArrayList<>();
        private String eventTypeJsonPointer;
        private String resourceJsonPointer;
        private String recordKeyJsonPointer;
        private List<String> allowedEventTypes = new ArrayList<>();
        private String reconcileDataSourceRef;
        private ReconciliationStrategy reconciliationStrategy = ReconciliationStrategy.FULL_SOURCE;
        @Valid
        private WebhookResponseStatuses responseStatuses = new WebhookResponseStatuses();
        @Min(1024)
        @Max(10 * 1024 * 1024)
        private int maxBodyBytes = 1024 * 1024;
        @Min(1)
        @Max(20)
        private int maxReconcileAttempts = 3;
        @Min(1)
        @Max(86_400)
        private int retryDelaySeconds = 30;
        private OrderingPolicy orderingPolicy = OrderingPolicy.RECONCILE_LATEST_STATE;

        public enum OrderingPolicy {
            RECONCILE_LATEST_STATE
        }

        public enum ReconciliationStrategy {
            FULL_SOURCE,
            FETCH_CURRENT_RECORD
        }
    }

    @Data
    public static class WebhookResponseStatuses {
        @Min(200)
        @Max(299)
        private int accepted = 202;
        @Min(200)
        @Max(299)
        private int duplicate = 200;
        @Min(400)
        @Max(499)
        private int resourceMismatch = 403;
    }

    @Data
    public static class WebhookVerification {
        private Strategy strategy;
        private String signatureHeader;
        private String secret;
        private String timestampComponent = "t";
        private String signatureComponent = "v1";
        private TimestampUnit timestampUnit = TimestampUnit.SECONDS;
        @Min(0)
        @Max(86_400)
        private int replayWindowSeconds = 300;

        public enum Strategy {
            HMAC_SHA256_TIMESTAMP_DOT_RAW_BODY
        }

        public enum TimestampUnit {
            SECONDS,
            MILLISECONDS
        }
    }
}
