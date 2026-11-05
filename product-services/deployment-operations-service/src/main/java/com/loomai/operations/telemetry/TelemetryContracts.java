package com.loomai.operations.telemetry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class TelemetryContracts {
    private TelemetryContracts() {}

    public static final String ID = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}";
    public static final String CONTAINER_ID = "[a-f0-9]{64}";
    public static final long MAX_BYTES = 1_125_899_906_842_624L;

    public enum Source { HOST_NATIVE }
    public enum ProviderKind { APPLICATION, DATABASE, SHARED, UNMAPPED }
    public enum Freshness { FRESH, STALE, NEVER_RECEIVED }

    public record SampleBatch(
        @NotNull @Pattern(regexp = "1") String schemaVersion,
        @NotNull @Pattern(regexp = ID) String serverId,
        @NotNull @Pattern(regexp = "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}") String bootId,
        @NotNull Instant observedAt,
        @NotNull @Min(0) @Max(60_000) Long collectionDurationMs,
        @NotNull Boolean complete,
        @NotNull Source source,
        @NotNull @Valid HostMetrics host,
        @NotNull @Size(max = 200) List<@NotNull @Valid ResourceSample> resources
    ) {}

    public record HostMetrics(
        @NotNull @Min(1) @Max(65_536) Integer cpuThreads,
        @DecimalMin("0") @DecimalMax("100") Double cpuUsagePercent,
        @NotNull @DecimalMin("0") @DecimalMax("1000000") Double load1,
        @NotNull @DecimalMin("0") @DecimalMax("1000000") Double load5,
        @NotNull @DecimalMin("0") @DecimalMax("1000000") Double load15,
        @NotNull @Min(1) @Max(MAX_BYTES) Long memoryTotalBytes,
        @NotNull @Min(0) @Max(MAX_BYTES) Long memoryAvailableBytes,
        @NotNull @Min(0) @Max(MAX_BYTES) Long swapTotalBytes,
        @NotNull @Min(0) @Max(MAX_BYTES) Long swapUsedBytes,
        @DecimalMin("0") @DecimalMax("1125899906842624") Double swapInBytesPerSecond,
        @DecimalMin("0") @DecimalMax("1125899906842624") Double swapOutBytesPerSecond,
        @NotNull @Min(1) @Max(MAX_BYTES) Long diskTotalBytes,
        @NotNull @Min(0) @Max(MAX_BYTES) Long diskFreeBytes,
        @NotNull @Min(0) @Max(MAX_BYTES) Long inodesTotal,
        @NotNull @Min(0) @Max(MAX_BYTES) Long inodesFree,
        @DecimalMin("0") @DecimalMax("1125899906842624") Double networkRxBytesPerSecond,
        @DecimalMin("0") @DecimalMax("1125899906842624") Double networkTxBytesPerSecond,
        @DecimalMin("0") @DecimalMax("100") Double cpuStealPercent
    ) {}

    public record ResourceSample(
        @NotNull @Pattern(regexp = CONTAINER_ID) String resourceId,
        @NotNull @Pattern(regexp = ID) String name,
        @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:/@-]{0,255}") String image,
        @Pattern(regexp = ID) String providerResourceId,
        @NotNull ProviderKind providerKind,
        @NotNull @Pattern(regexp = "[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}") String role,
        @NotNull Instant startedAt,
        @NotNull @Pattern(regexp = "created|running|paused|restarting|removing|exited|dead") String state,
        @Pattern(regexp = "starting|healthy|unhealthy|none") String health,
        @NotNull @Min(0) @Max(MAX_BYTES) Long memoryWorkingSetBytes,
        @NotNull @Min(0) @Max(MAX_BYTES) Long memoryLimitBytes,
        @NotNull @Min(0) @Max(MAX_BYTES) Long memoryReservationBytes,
        @NotNull @Min(-1) @Max(MAX_BYTES) Long memorySwapLimitBytes,
        @DecimalMin("0") @DecimalMax("65536") Double cpuCoresUsed,
        @DecimalMin("0") @DecimalMax("65536") Double cpuQuota,
        @NotNull @Size(max = 1024) @Pattern(regexp = "(?:[0-9]{1,5}(?:-[0-9]{1,5})?(?:,[0-9]{1,5}(?:-[0-9]{1,5})?)*)?") String cpusetCpus,
        @DecimalMin("0") @DecimalMax("100") Double cpuThrottledPeriodsPercent,
        @NotNull @Min(0) @Max(2_147_483_647) Long restartCount,
        @NotNull Boolean oomKilled
    ) {}

    public record ServerProjection(
        String serverId, String displayName, String environment, Freshness status,
        Instant observedAt, Instant receivedAt, boolean complete, Source source,
        HostMetrics host, List<ResourceSample> resources, List<String> warnings
    ) {}

    public record HistoryProjection(
        String serverId, String resourceId, String range, int bucketSeconds, List<HistoryPoint> points
    ) {}

    public record HistoryPoint(Instant observedAt, Map<String, Double> values, Map<String, Double> maxValues) {}
}
