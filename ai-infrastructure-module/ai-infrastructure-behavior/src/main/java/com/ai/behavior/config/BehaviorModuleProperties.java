package com.ai.behavior.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "ai.behavior")
public class BehaviorModuleProperties {

    private boolean enabled = true;
    private Sink sink = new Sink();
    private Events events = new Events();
    private Ingestion ingestion = new Ingestion();
    private Processing processing = new Processing();
    private Insights insights = new Insights();
    private Retention retention = new Retention();
    private Performance performance = new Performance();
    private Providers providers = new Providers();
    private Schemas schemas = new Schemas();
    private Search search = new Search();
    private Security security = new Security();

    @Data
    public static class Sink {
        private String type = "database";
        private Database database = new Database();
        private Kafka kafka = new Kafka();
        private Redis redis = new Redis();
        private Hybrid hybrid = new Hybrid();
        private S3 s3 = new S3();

        @Data
        public static class Database {
            private int batchSize = 200;
        }

        @Data
        public static class Kafka {
            private String topic = "behavior-events";
            private String compression = "snappy";
        }

        @Data
        public static class Redis {
            private int ttlDays = 7;
            private long ttlSeconds = -1;
        }

        @Data
        public static class Hybrid {
            private String hotStorage = "redis";
            private int hotRetentionDays = 7;
            private String coldStorage = "database";
            private long hotRetentionSeconds = -1;
        }

        @Data
        public static class S3 {
            private boolean compress = true;
            private String bucket;
            private String prefix = "ai-behavior";
            private String storageClass = "STANDARD";
        }
    }

    @Data
    public static class Events {
        private int batchSize = 100;
        private int processingTimeoutSeconds = 30;
        private String storageType = "postgresql";
    }

    @Data
    public static class Ingestion {
        private int maxBatchSize = 500;
        private boolean publishApplicationEvents = true;
    }

    @Data
    public static class Processing {
        private Aggregation aggregation = new Aggregation();
        private PatternDetection patternDetection = new PatternDetection();
        private Embedding embedding = new Embedding();
        private Anomaly anomaly = new Anomaly();
        private Segmentation segmentation = new Segmentation();
        private Metrics metrics = new Metrics();
        private Analyzer analyzer = new Analyzer();
        private Worker worker = new Worker();

        @Data
        public static class Aggregation {
            private boolean enabled = true;
            private boolean async = true;
        }

        @Data
        public static class PatternDetection {
            private boolean enabled = true;
            private String schedule = "0 */5 * * * *";
            private int analysisWindowHours = 24;
            private int minEvents = 10;
        }

        @Data
        public static class Embedding {
            private boolean enabled = true;
            private boolean async = true;
            private List<String> schemaIds = List.of("feedback.message", "intent.search");
            private int minTextLength = 10;
        }

        @Data
        public static class Anomaly {
            private boolean enabled = true;
            private String schedule = "0 * * * * *";
            private double sensitivity = 0.8d;
            private double amountThreshold = 10000.0d;
        }

        @Data
        public static class Segmentation {
            private boolean enabled = true;
            private String schedule = "0 30 2 * * *";
            private int analysisWindowDays = 30;
            private int minEvents = 25;
            private double vipPurchaseThreshold = 1000.0d;
        }

        @Data
        public static class Metrics {
            private List<String> enabledProjectors = new ArrayList<>(List.of(
                "engagementMetricProjector",
                "recencyMetricProjector",
                "diversityMetricProjector"
            ));
        }

        @Data
        public static class Analyzer {
            private double engagementThreshold = 0.75;
            private double recencyThreshold = 0.6;
            private boolean enableAiEnrichment = true;
        }

        @Data
        public static class Worker {
            private int poolSize = 5;
            private int delaySeconds = 300;
            private int maxRetries = 3;
        }
    }

    @Data
    public static class Insights {
        private int cacheTtlMinutes = 5;
        private int minEventsForInsights = 10;
        private Duration validity = Duration.ofMinutes(10);
        private int minEventsLookbackDays = 30;
    }

    @Data
    public static class Retention {
        private int eventsDays = 90;
        private int insightsDays = 180;
        private int metricsDays = 365;
        private int embeddingsDays = 90;
        private int alertsDays = 30;
        private int tempEventsTtlDays = 30;
        private int insightRetentionDays = 90;
        private String cleanupSchedule = "0 3 * * *";
    }

    @Data
    public static class Performance {
        private AsyncExecutor asyncExecutor = new AsyncExecutor();

        @Data
        public static class AsyncExecutor {
            private int corePoolSize = 4;
            private int maxPoolSize = 16;
            private int queueCapacity = 1000;
        }
    }

    @Data
    public static class Providers {
        private External external = new External();
        private Aggregated aggregated = new Aggregated();

        @Data
        public static class External {
            private boolean enabled = false;
            private String baseUrl;
            private String apiKey;
            private String queryPath = "/behavior/query";
            private Duration timeout = Duration.ofSeconds(5);
        }

        @Data
        public static class Aggregated {
            private boolean enabled = false;
            private List<String> providerOrder = new ArrayList<>(List.of("database", "external"));
            private int maxProviders = 2;
        }
    }

    @Data
    public static class Schemas {
        private String path = "classpath:/behavior/schemas/*.yml";
        private boolean failOnStartupIfMissing = true;
        private int maxAttributeCount = 128;
        private Duration cacheTtl = Duration.ofMinutes(5);
    }

    @Data
    public static class Search {
        private Orchestrated orchestrated = new Orchestrated();

        @Data
        public static class Orchestrated {
            private boolean enabled = true;
            private boolean piiDetectionEnabled = true;
            private int maxResults = 100;
            private int timeoutSeconds = 30;
            private RateLimit rateLimit = new RateLimit();

            @Data
            public static class RateLimit {
                private String perUser = "100/hour";
                private String perEndpoint = "1000/hour";
            }
        }
    }

    @Data
    public static class Security {
        private RateLimiting rateLimiting = new RateLimiting();

        @Data
        public static class RateLimiting {
            private boolean enabled = true;
            private long requests = 100;
            private Duration refreshPeriod = Duration.ofHours(1);
        }
    }
}
