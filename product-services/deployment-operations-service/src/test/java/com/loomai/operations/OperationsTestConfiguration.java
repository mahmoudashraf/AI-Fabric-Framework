package com.loomai.operations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class OperationsTestConfiguration {

    public static final Instant NOW = Instant.parse("2026-10-24T14:00:00Z");
    public static final String API_KEY = "test-only-deployment-operations-key-32-chars";
    public static final String POLICY = "{\"revision\":\"test-policy-v1\",\"targets\":{"
        + "\"stage\":{\"cpuMillis\":2000,\"memoryMiB\":2048,\"diskMiB\":4096,\"databases\":2,\"indexes\":2,\"dependencies\":2,\"replicas\":2,\"concurrentApplies\":2},"
        + "\"production\":{\"cpuMillis\":2000,\"memoryMiB\":2048,\"diskMiB\":4096,\"databases\":2,\"indexes\":2,\"dependencies\":2,\"replicas\":2,\"concurrentApplies\":2}}}";

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock();
    }

    public static final class MutableClock extends Clock {
        private volatile Instant now = NOW;

        public void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
