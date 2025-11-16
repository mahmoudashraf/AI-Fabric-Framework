package com.ai.behavior.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "ai.behavior")
public class BehaviorProperties {

    private boolean enabled = true;
    private Ingestion ingestion = new Ingestion();
    private Sink sink = new Sink();
    private Processing processing = new Processing();
    private Insights insights = new Insights();
    private Retention retention = new Retention();

    @Data
    public static class Ingestion {
        private boolean validationEnabled = true;
        private boolean strictMode = false;
        private int batchSize = 1000;
    }

    @Data
    public static class Sink {
        private String type = "database";
        private Database database = new Database();

        @Data
        public static class Database {
            private boolean batchInsert = true;
            private int batchSize = 100;
        }
    }

    @Data
    public static class Processing {
        private Aggregation aggregation = new Aggregation();
        private PatternDetection patternDetection = new PatternDetection();
        private Embedding embedding = new Embedding();
        private AsyncExecutor asyncExecutor = new AsyncExecutor();

        @Data
        public static class Aggregation {
            private boolean enabled = true;
            private boolean async = true;
        }

        @Data
        public static class PatternDetection {
            private boolean enabled = true;
            private Duration fixedDelay = Duration.ofMinutes(5);
            private int analysisWindowHours = 24;
            private int minEventsForAnalysis = 10;
        }

        @Data
        public static class Embedding {
            private boolean enabled = true;
            private boolean async = true;
            private List<String> eventTypes = List.of("feedback", "review", "search");
            private int minTextLength = 10;
        }

        @Data
        public static class AsyncExecutor {
            private int corePoolSize = 4;
            private int maxPoolSize = 16;
            private int queueCapacity = 1000;
        }
    }

    @Data
    public static class Insights {
        private Duration cacheTtl = Duration.ofMinutes(5);
        private int minEventsForInsights = 10;
        private Duration validity = Duration.ofMinutes(5);
    }

    @Data
    public static class Retention {
        private int eventsDays = 90;
        private int insightsDays = 180;
        private int metricsDays = 365;
        private int embeddingsDays = 90;
    }
}
