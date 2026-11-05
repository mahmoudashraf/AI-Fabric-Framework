package com.loomai.operations.telemetry;

import static com.loomai.operations.telemetry.TelemetryContracts.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class TelemetryMetrics {
    private TelemetryMetrics() {}

    record Statistic(double sum, long count, Double max) {
        Statistic merge(Statistic other) {
            Double maximum = max;
            if (maximum == null) maximum = other.max;
            else if (other.max != null) maximum = Math.max(maximum, other.max);
            return new Statistic(sum + other.sum, count + other.count, maximum);
        }
        Double mean() { return count == 0 ? null : sum / count; }
    }

    static Map<String, Statistic> host(HostMetrics host) {
        var result = new LinkedHashMap<String, Statistic>();
        add(result, "cpuUsagePercent", host.cpuUsagePercent());
        add(result, "memoryAvailableBytes", host.memoryAvailableBytes());
        add(result, "swapUsedBytes", host.swapUsedBytes());
        add(result, "swapInBytesPerSecond", host.swapInBytesPerSecond());
        add(result, "swapOutBytesPerSecond", host.swapOutBytesPerSecond());
        add(result, "diskFreeBytes", host.diskFreeBytes());
        add(result, "networkRxBytesPerSecond", host.networkRxBytesPerSecond());
        add(result, "networkTxBytesPerSecond", host.networkTxBytesPerSecond());
        return result;
    }

    static Map<String, Statistic> resource(ResourceSample resource) {
        var result = new LinkedHashMap<String, Statistic>();
        add(result, "memoryWorkingSetBytes", resource.memoryWorkingSetBytes());
        add(result, "memoryLimitBytes", resource.memoryLimitBytes());
        add(result, "memoryReservationBytes", resource.memoryReservationBytes());
        add(result, "memorySwapLimitBytes", resource.memorySwapLimitBytes());
        add(result, "cpuCoresUsed", resource.cpuCoresUsed());
        add(result, "cpuQuota", resource.cpuQuota());
        add(result, "cpuThrottledPeriodsPercent", resource.cpuThrottledPeriodsPercent());
        add(result, "restartCount", resource.restartCount());
        return result;
    }

    private static void add(Map<String, Statistic> map, String name, Number value) {
        map.put(name, value == null ? new Statistic(0, 0, null) : new Statistic(value.doubleValue(), 1, value.doubleValue()));
    }

    static void merge(Map<String, Statistic> into, Map<String, Statistic> from) {
        from.forEach((name, value) -> into.merge(name, value, Statistic::merge));
    }

    static HistoryPoint point(Instant bucket, Map<String, Statistic> statistics) {
        Map<String, Double> means = new LinkedHashMap<>();
        Map<String, Double> maxima = new LinkedHashMap<>();
        statistics.forEach((name, statistic) -> {
            means.put(name, statistic.mean());
            maxima.put(name, statistic.max());
        });
        return new HistoryPoint(bucket, means, maxima);
    }

    static Instant bucket(Instant instant, int seconds) {
        return Instant.ofEpochSecond(Math.floorDiv(instant.getEpochSecond(), seconds) * seconds);
    }
}
