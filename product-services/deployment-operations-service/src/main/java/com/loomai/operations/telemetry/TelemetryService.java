package com.loomai.operations.telemetry;

import static com.loomai.operations.telemetry.TelemetryContracts.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TelemetryService {
    private static final Logger LOG = LoggerFactory.getLogger(TelemetryService.class);
    private final TelemetryRegistry registry;
    private final TelemetryRepository repository;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final Clock clock;

    public TelemetryService(TelemetryRegistry registry, TelemetryRepository repository,
                            ObjectMapper mapper, Validator validator, Clock clock) {
        this.registry = registry;
        this.repository = repository;
        this.mapper = mapper;
        this.validator = validator;
        this.clock = clock;
    }

    public void ingest(String authenticatedServerId, byte[] body) {
        SampleBatch parsed;
        try {
            if (body == null || body.length > TelemetryApiFilter.MAX_BODY_BYTES) throw TelemetryException.invalid();
            parsed = mapper.readValue(body, SampleBatch.class);
        } catch (Exception exception) {
            throw TelemetryException.invalid();
        }
        validate(parsed);
        if (!registry.enabled()) throw new TelemetryException(503, "TELEMETRY_DISABLED", "Telemetry is disabled.");
        registry.source(authenticatedServerId);
        if (!parsed.serverId().equals(authenticatedServerId)) {
            throw new TelemetryException(403, "TELEMETRY_SOURCE_MISMATCH", "Credential is scoped to another server.");
        }
        SampleBatch batch = new SampleBatch(parsed.schemaVersion(), parsed.serverId(), parsed.bootId(), parsed.observedAt(),
            parsed.collectionDurationMs(), parsed.complete(), parsed.source(), parsed.host(),
            parsed.resources().stream().sorted(Comparator.comparing(ResourceSample::resourceId)).toList());
        String digest = repository.digest(batch);
        repository.locked(() -> {
            var latest = repository.latest(batch.serverId());
            if (latest.isPresent() && sameIdentity(latest.get().sample(), batch)) {
                requireSameDigest(latest.get().digest(), digest);
                return null;
            }
            var occupied = repository.minute(batch.serverId(), Math.floorDiv(batch.observedAt().getEpochSecond(), 60));
            if (occupied.isPresent()) {
                if (sameIdentity(occupied.get().sample(), batch)) {
                    requireSameDigest(occupied.get().digest(), digest);
                    return null;
                }
                throw new TelemetryException(409, "TELEMETRY_MINUTE_CONFLICT", "A distinct sample already occupies this server minute.");
            }
            Instant now = clock.instant();
            // These bounds protect source integrity and finite storage, not deployment authorization.
            if (batch.observedAt().isBefore(now.minus(Duration.ofHours(24)))
                || batch.observedAt().isAfter(now.plusSeconds(30))) {
                throw new TelemetryException(400, "TELEMETRY_TIME_INVALID", "Observation must be within 24 hours past and 30 seconds future.");
            }
            var boot = repository.boot(batch.serverId(), batch.bootId());
            if (latest.isPresent() && !latest.get().sample().bootId().equals(batch.bootId())) {
                var current = latest.get().sample();
                var currentBoot = repository.boot(batch.serverId(), current.bootId());
                boolean afterLatest = batch.observedAt().isAfter(current.observedAt());
                if ((afterLatest && boot.isPresent()) || (!afterLatest && currentBoot.isPresent()
                    && !batch.observedAt().isBefore(currentBoot.get().firstObservedAt()))) {
                    throw new TelemetryException(409, "TELEMETRY_BOOT_CONFLICT", "Observation conflicts with the recorded host boot sequence.");
                }
            }
            if (latest.isPresent() && latest.get().sample().observedAt().equals(batch.observedAt())) {
                throw new TelemetryException(409, "TELEMETRY_SAMPLE_CONFLICT", "Observation identity conflicts with an existing sample.");
            }
            repository.saveRaw(batch, now, digest);
            repository.saveBoot(batch, boot);
            addAggregates(batch);
            if (latest.isEmpty() || batch.observedAt().isAfter(latest.get().sample().observedAt())) {
                repository.saveLatest(batch, now, digest, latest.isPresent());
            }
            return null;
        });
    }

    public List<ServerProjection> servers() {
        return registry.sources().stream().map(source -> server(source.serverId())).toList();
    }

    public ServerProjection server(String serverId) {
        var source = registry.source(serverId);
        var latest = repository.latest(serverId);
        if (latest.isEmpty()) {
            return new ServerProjection(serverId, source.displayName(), source.environment(), Freshness.NEVER_RECEIVED,
                null, null, false, Source.HOST_NATIVE, null, List.of(), List.of("No observations have been received."));
        }
        var stored = latest.get();
        var batch = stored.sample();
        boolean stale = batch.observedAt().isBefore(clock.instant().minusSeconds(180));
        return new ServerProjection(serverId, source.displayName(), source.environment(), stale ? Freshness.STALE : Freshness.FRESH,
            batch.observedAt(), stored.receivedAt(), batch.complete(), batch.source(), batch.host(), batch.resources(), warnings(batch, stale));
    }

    public HistoryProjection history(String serverId, String range, String resourceId) {
        registry.source(serverId);
        int hours = switch (range == null ? "" : range) {
            case "1h" -> 1;
            case "24h" -> 24;
            case "7d" -> 168;
            default -> throw new TelemetryException(400, "TELEMETRY_QUERY_INVALID", "History range must be 1h, 24h or 7d.");
        };
        if (resourceId != null && !resourceId.matches(CONTAINER_ID)) {
            throw new TelemetryException(400, "TELEMETRY_QUERY_INVALID", "Resource ID must be a full container identity.");
        }
        int bucketSeconds = hours == 1 ? 60 : hours == 24 ? 180 : 1800;
        Instant now = clock.instant();
        Instant from = now.minus(Duration.ofHours(hours));
        var buckets = new TreeMap<Instant, Map<String, TelemetryMetrics.Statistic>>();
        if (hours == 168) {
            // Exclude the leading partial stored bucket rather than inventing its exact coverage.
            Instant first = TelemetryMetrics.bucket(from, 300);
            if (first.isBefore(from)) first = first.plusSeconds(300);
            repository.aggregates(serverId, resourceId, first, now, (time, stats) ->
                TelemetryMetrics.merge(buckets.computeIfAbsent(TelemetryMetrics.bucket(time, bucketSeconds), ignored -> new LinkedHashMap<>()), stats));
        } else {
            repository.raw(serverId, from, now, batch -> {
                Map<String, TelemetryMetrics.Statistic> stats = resourceId == null ? TelemetryMetrics.host(batch.host())
                    : batch.resources().stream().filter(resource -> resource.resourceId().equals(resourceId))
                        .findFirst().map(TelemetryMetrics::resource).orElse(null);
                if (stats != null) TelemetryMetrics.merge(buckets.computeIfAbsent(TelemetryMetrics.bucket(batch.observedAt(), bucketSeconds),
                    ignored -> new LinkedHashMap<>()), stats);
            });
        }
        if (buckets.size() > 600) throw new IllegalStateException("Telemetry query exceeded its fixed point bound.");
        return new HistoryProjection(serverId, resourceId, range, bucketSeconds,
            buckets.entrySet().stream().map(entry -> TelemetryMetrics.point(entry.getKey(), entry.getValue())).toList());
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void retain() {
        try {
            repository.purge(clock.instant(), registry.sources().stream().map(TelemetryRegistry.RegisteredSource::serverId).toList(), registry.enabled());
        } catch (RuntimeException exception) {
            // Do not log database addresses, credentials, sample metadata or request bodies.
            LOG.warn("Telemetry retention is unavailable; the next bounded scheduled pass will retry.");
        }
    }

    private void addAggregates(SampleBatch batch) {
        Instant bucket = TelemetryMetrics.bucket(batch.observedAt(), 300);
        var stats = repository.bucketStatistics(batch.serverId(), bucket);
        TelemetryMetrics.merge(stats.computeIfAbsent("", ignored -> new LinkedHashMap<>()), TelemetryMetrics.host(batch.host()));
        for (ResourceSample resource : batch.resources()) {
            TelemetryMetrics.merge(stats.computeIfAbsent(resource.resourceId(), ignored -> new LinkedHashMap<>()), TelemetryMetrics.resource(resource));
        }
        repository.replaceAggregates(batch.serverId(), bucket, stats);
    }

    private void validate(SampleBatch batch) {
        if (batch == null || !validator.validate(batch).isEmpty()) throw TelemetryException.invalid();
        var host = batch.host();
        if (host.memoryAvailableBytes() > host.memoryTotalBytes() || host.swapUsedBytes() > host.swapTotalBytes()
            || host.diskFreeBytes() > host.diskTotalBytes() || host.inodesFree() > host.inodesTotal()
            || batch.bootId().equals("00000000-0000-0000-0000-000000000000")) throw TelemetryException.invalid();
        var identities = new HashSet<String>();
        for (ResourceSample resource : batch.resources()) {
            if (!identities.add(resource.resourceId()) || resource.startedAt().isAfter(batch.observedAt())
                || resource.startedAt().isBefore(Instant.parse("0001-01-01T00:00:00Z"))
                || resource.image().contains("://")) throw TelemetryException.invalid();
            for (String interval : resource.cpusetCpus().split(",")) {
                if (interval.isEmpty()) continue;
                String[] ends = interval.split("-");
                int first = Integer.parseInt(ends[0]);
                int last = ends.length == 1 ? first : Integer.parseInt(ends[1]);
                if (first > last || last > 65535) throw TelemetryException.invalid();
            }
        }
    }

    private static boolean sameIdentity(SampleBatch left, SampleBatch right) {
        return left.bootId().equals(right.bootId()) && left.observedAt().equals(right.observedAt());
    }

    private static void requireSameDigest(String left, String right) {
        if (!left.equals(right)) throw new TelemetryException(409, "TELEMETRY_SAMPLE_CONFLICT", "Sample identity already has a different payload.");
    }

    private static List<String> warnings(SampleBatch batch, boolean stale) {
        var warnings = new ArrayList<String>();
        var host = batch.host();
        if (stale) warnings.add("Collector observations are older than 180 seconds; displayed values are last known.");
        if (!batch.complete()) warnings.add("Collection is partial; resource totals may not represent the complete host.");
        if (host.memoryAvailableBytes() < host.memoryTotalBytes() / 10) warnings.add("Available host memory is below 10% of total memory.");
        if (host.diskFreeBytes() < host.diskTotalBytes() / 10) warnings.add("Free host filesystem space is below 10% of total space.");
        if ((host.swapInBytesPerSecond() != null && host.swapInBytesPerSecond() > 0)
            || (host.swapOutBytesPerSecond() != null && host.swapOutBytesPerSecond() > 0)) {
            warnings.add("Swap activity was observed in the collector interval; occupied swap alone does not establish current pressure.");
        } else if (host.swapUsedBytes() > 0 && (host.swapInBytesPerSecond() == null || host.swapOutBytesPerSecond() == null)) {
            warnings.add("Swap is occupied but activity rates are unavailable; active swapping cannot be inferred.");
        }
        if (batch.resources().stream().anyMatch(resource -> resource.oomKilled())) warnings.add("A container reports an OOM-killed state; this is not a timestamped new event.");
        if (batch.resources().stream().anyMatch(resource -> "unhealthy".equals(resource.health()))) warnings.add("A container reports unhealthy status.");
        if (batch.resources().stream().anyMatch(resource -> resource.restartCount() > 0)) warnings.add("Container restart counters are nonzero; they are cumulative, not a count of recent incidents.");
        var affinities = new HashSet<String>();
        if (batch.resources().stream().filter(resource -> resource.state().equals("running") && !resource.cpusetCpus().isEmpty())
            .anyMatch(resource -> !affinities.add(resource.cpusetCpus()))) {
            warnings.add("Running containers share an explicit CPU affinity; this does not establish a measured performance bottleneck.");
        }
        return List.copyOf(warnings);
    }
}
