package com.loomai.operations.telemetry;

import static com.loomai.operations.OperationsTestConfiguration.API_KEY;
import static com.loomai.operations.OperationsTestConfiguration.NOW;
import static com.loomai.operations.telemetry.TelemetryFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loomai.operations.OperationsTestConfiguration.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

abstract class AbstractTelemetryTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired TelemetryService service;

    @BeforeEach
    void resetTelemetry() {
        for (String table : List.of("operations_host_samples", "operations_telemetry_latest", "operations_telemetry_boots", "operations_telemetry_aggregates")) {
            jdbc.update("DELETE FROM " + table);
        }
        clock.set(NOW);
    }

    @Test
    void noDataIsNotHealthyZeroAndReadRegistryNeverExposesSecrets() throws Exception {
        mvc.perform(get(PREFIX + "/servers").header("Authorization", "Bearer " + READ_KEY))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].status").value("NEVER_RECEIVED"))
            .andExpect(jsonPath("$[0].host").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$[0].observedAt").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$[0].receivedAt").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$[0].resources").isEmpty())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(INGEST_KEY))));
        mvc.perform(get(PREFIX + "/servers/stage-host/history?range=24h").header("Authorization", "Bearer " + READ_KEY))
            .andExpect(status().isOk()).andExpect(jsonPath("$.points").isEmpty())
            .andExpect(jsonPath("$.resourceId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void ingestAndReadUseTheCanonicalDtoWithNullsAndNoInventedOwnership() throws Exception {
        send(sample(mapper, NOW), INGEST_KEY, 204);
        mvc.perform(get(PREFIX + "/servers/stage-host").header("Authorization", "Bearer " + READ_KEY))
            .andExpect(status().isOk()).andExpect(jsonPath("$.serverId").value("stage-host"))
            .andExpect(jsonPath("$.displayName").value("Staging host"))
            .andExpect(jsonPath("$.environment").value("staging"))
            .andExpect(jsonPath("$.status").value("FRESH"))
            .andExpect(jsonPath("$.observedAt").value(NOW.toString()))
            .andExpect(jsonPath("$.receivedAt").value(NOW.toString()))
            .andExpect(jsonPath("$.complete").value(true))
            .andExpect(jsonPath("$.source").value("HOST_NATIVE"))
            .andExpect(jsonPath("$.host.memoryAvailableBytes").value(8000000000L))
            .andExpect(jsonPath("$.host.swapInBytesPerSecond").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.resources[0].resourceId").value(RESOURCE))
            .andExpect(jsonPath("$.resources[0].deploymentId").doesNotExist())
            .andExpect(jsonPath("$.resources[0].cpuCoresUsed").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.length()").value(11));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operations_host_samples", Integer.class)).isEqualTo(1);
    }

    @Test
    void retainedNeverStartedContainersPreserveDockerTimestampAndUnlimitedSwapSentinels() throws Exception {
        ObjectNode node = sample(mapper, NOW);
        ((ObjectNode) node.get("resources").get(0)).put("state", "created")
            .put("startedAt", "0001-01-01T00:00:00Z").put("memorySwapLimitBytes", -1);
        send(node, INGEST_KEY, 204);
        var resource = service.server("stage-host").resources().getFirst();
        assertThat(resource.startedAt()).isEqualTo(Instant.parse("0001-01-01T00:00:00Z"));
        assertThat(resource.memorySwapLimitBytes()).isEqualTo(-1);
        mvc.perform(get(PREFIX + "/servers/stage-host").header("Authorization", "Bearer " + READ_KEY))
            .andExpect(status().isOk()).andExpect(jsonPath("$.resources[0].startedAt").value("0001-01-01T00:00:00Z"))
            .andExpect(jsonPath("$.resources[0].memorySwapLimitBytes").value(-1));
    }

    @Test
    void credentialsCannotCrossHostsCapabilitiesOrExistingPolicyLifecycleBoundary() throws Exception {
        send(sample(mapper, NOW), OTHER_KEY, 403);
        send(sample(mapper, NOW), READ_KEY, 403);
        send(sample(mapper, NOW), API_KEY, 401);
        for (String token : List.of(INGEST_KEY, OTHER_KEY)) {
            mvc.perform(get(PREFIX + "/servers").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        for (String token : List.of(READ_KEY, INGEST_KEY, OTHER_KEY)) {
            for (String path : List.of("/internal/v1/deployment-operations/assess", "/internal/v1/deployment-operations/outcomes",
                "/internal/v1/deployment-operations/lifecycle/jobs")) {
                mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            }
        }
        mvc.perform(get(PREFIX + "/servers").header("Authorization", "Bearer " + API_KEY)).andExpect(status().isUnauthorized());
        mvc.perform(get(PREFIX + "-other/servers").header("Authorization", "Bearer " + READ_KEY)).andExpect(status().isUnauthorized());
        mvc.perform(get(PREFIX + "/servers").header("Authorization", "Bearer " + READ_KEY, "Bearer " + READ_KEY))
            .andExpect(status().isUnauthorized());
        mvc.perform(get(PREFIX + "/servers/stage-host/../../deployment-operations/lifecycle/jobs")
            .header("Authorization", "Bearer " + READ_KEY)).andExpect(status().isNotFound());
    }

    @Test
    void strictJsonRejectsDuplicateUnknownTrailingAndCoercedPayloads() throws Exception {
        String body = mapper.writeValueAsString(sample(mapper, NOW));
        for (String invalid : List.of("{", "null", body + "{}",
            body.replace("\"schemaVersion\":\"1\"", "\"schemaVersion\":\"1\",\"schemaVersion\":\"1\""),
            body.replace("\"schemaVersion\":\"1\"", "\"schemaVersion\":1"),
            body.replace("\"cpuThreads\":8", "\"cpuThreads\":\"8\""),
            body.replace("\"cpuThreads\":8", "\"cpuThreads\":8.5"),
            body.replace("\"complete\":true", "\"complete\":\"true\""),
            body.replace("\"observedAt\":\"" + NOW + "\"", "\"observedAt\":1792850400"),
            body.replace("\"cpuUsagePercent\":20.0", "\"cpuUsagePercent\":1e309"))) {
            sendJson(invalid, INGEST_KEY, 400);
        }
        for (String field : List.of("customerId", "tenantId", "deploymentId", "environment", "url", "metrics")) {
            ObjectNode node = sample(mapper, NOW);
            node.put(field, "forbidden");
            send(node, INGEST_KEY, 400);
        }
        ObjectNode node = sample(mapper, NOW);
        ((ObjectNode) node.get("resources").get(0)).putObject("env").put("SECRET", "not-allowed");
        send(node, INGEST_KEY, 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"schemaVersion", "serverId", "bootId", "observedAt", "collectionDurationMs", "complete", "source", "host", "resources"})
    void requiredBatchFieldsCannotBeOmitted(String field) throws Exception {
        ObjectNode node = sample(mapper, NOW);
        node.remove(field);
        send(node, INGEST_KEY, 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cpuThreads", "load1", "load5", "load15", "memoryTotalBytes", "memoryAvailableBytes", "swapTotalBytes", "swapUsedBytes",
        "diskTotalBytes", "diskFreeBytes", "inodesTotal", "inodesFree"})
    void hostRequiredFieldsAreTypedAndBounded(String field) throws Exception {
        ObjectNode node = sample(mapper, NOW);
        ((ObjectNode) node.get("host")).put(field, -1);
        send(node, INGEST_KEY, 400);
        ((ObjectNode) node.get("host")).remove(field);
        send(node, INGEST_KEY, 400);
    }

    @Test
    void crossFieldLimitsResourceIdentityAndIncarnationAreValidated() throws Exception {
        for (String field : List.of("memoryAvailableBytes", "swapUsedBytes", "diskFreeBytes", "inodesFree")) {
            ObjectNode node = sample(mapper, NOW);
            ((ObjectNode) node.get("host")).put(field, TelemetryContracts.MAX_BYTES);
            send(node, INGEST_KEY, 400);
        }
        for (String field : List.of("cpuUsagePercent", "cpuStealPercent")) {
            ObjectNode node = sample(mapper, NOW);
            ((ObjectNode) node.get("host")).put(field, 100.1);
            send(node, INGEST_KEY, 400);
        }
        for (String field : List.of("resourceId", "name", "image", "providerKind", "role", "state", "memoryWorkingSetBytes", "restartCount", "oomKilled")) {
            ObjectNode node = sample(mapper, NOW);
            ((ObjectNode) node.get("resources").get(0)).remove(field);
            send(node, INGEST_KEY, 400);
        }
        for (String affinity : List.of("7-2", "65536", "0;rm", "-1")) {
            ObjectNode node = sample(mapper, NOW);
            ((ObjectNode) node.get("resources").get(0)).put("cpusetCpus", affinity);
            send(node, INGEST_KEY, 400);
        }
        ObjectNode node = sample(mapper, NOW);
        ((ObjectNode) node.get("resources").get(0)).put("resourceId", "a".repeat(12));
        send(node, INGEST_KEY, 400);
        node = sample(mapper, NOW);
        ((ObjectNode) node.get("resources").get(0)).put("startedAt", NOW.plusSeconds(1).toString());
        send(node, INGEST_KEY, 400);
        node = sample(mapper, NOW);
        ((ObjectNode) node.get("resources").get(0)).put("image", "https://private.example/path");
        send(node, INGEST_KEY, 400);
        node = sample(mapper, NOW);
        ((com.fasterxml.jackson.databind.node.ArrayNode) node.get("resources")).add(node.get("resources").get(0).deepCopy());
        send(node, INGEST_KEY, 400);
    }

    @Test
    void bodyAndResourceLimitsNeverSilentlyTruncate() throws Exception {
        ObjectNode node = sample(mapper, NOW);
        var resources = (com.fasterxml.jackson.databind.node.ArrayNode) node.get("resources");
        ObjectNode template = (ObjectNode) resources.get(0).deepCopy();
        resources.removeAll();
        for (int i = 0; i < 201; i++) resources.add(template.deepCopy().put("resourceId", "%064x".formatted(i)));
        send(node, INGEST_KEY, 400);
        resources.remove(200);
        send(node, INGEST_KEY, 204);
        sendJson(" ".repeat(262145), INGEST_KEY, 413);
        mvc.perform(post(PREFIX + "/samples").header("Authorization", "Bearer " + INGEST_KEY).contentType(MediaType.TEXT_PLAIN).content("{}"))
            .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(PREFIX + "/samples").header("Authorization", "Bearer " + INGEST_KEY).header("Content-Encoding", "gzip")
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void sampleRetriesAreIdempotentConflictsCannotRenewFreshnessOrOverwriteLatest() throws Exception {
        ObjectNode first = sample(mapper, NOW);
        send(first, INGEST_KEY, 204);
        clock.set(NOW.plusSeconds(181));
        send(first, INGEST_KEY, 204);
        assertThat(service.server("stage-host").status()).isEqualTo(TelemetryContracts.Freshness.STALE);
        assertThat(service.server("stage-host").receivedAt()).isEqualTo(NOW);
        ObjectNode changed = first.deepCopy();
        ((ObjectNode) changed.get("host")).put("cpuUsagePercent", 99);
        send(changed, INGEST_KEY, 409);
        send(sample(mapper, NOW.plusSeconds(1)), INGEST_KEY, 409);
        send(sample(mapper, NOW.minusSeconds(60)), INGEST_KEY, 204);
        assertThat(service.server("stage-host").observedAt()).isEqualTo(NOW);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operations_host_samples", Integer.class)).isEqualTo(2);
        clock.set(NOW.plus(Duration.ofDays(2)));
        service.retain();
        send(first, INGEST_KEY, 204);
        assertThat(service.server("stage-host").receivedAt()).isEqualTo(NOW);
    }

    @Test
    void bootTransitionsRejectResurrectionWhileContainerRestartsPreserveIncarnationAndNullRates() throws Exception {
        ObjectNode initial = sample(mapper, NOW);
        ((ObjectNode) initial.get("resources").get(0)).put("restartCount", 8);
        send(initial, INGEST_KEY, 204);
        clock.set(NOW.plusSeconds(60));
        ObjectNode rebooted = sample(mapper, clock.instant());
        rebooted.put("bootId", SECOND_BOOT);
        ((ObjectNode) rebooted.get("resources").get(0)).put("startedAt", clock.instant().minusSeconds(1).toString());
        send(rebooted, INGEST_KEY, 204);
        clock.set(NOW.plusSeconds(120));
        send(sample(mapper, clock.instant()), INGEST_KEY, 409);
        var latest = service.server("stage-host");
        assertThat(latest.resources().getFirst().restartCount()).isZero();
        assertThat(latest.resources().getFirst().startedAt()).isEqualTo(NOW.plusSeconds(59));
        assertThat(latest.resources().getFirst().cpuCoresUsed()).isNull();
        assertThat(service.history("stage-host", "1h", RESOURCE).points()).hasSize(2);
        send(initial, INGEST_KEY, 204);
        assertThat(service.server("stage-host").observedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void futureAndExpiredObservationsAreRejectedButDelayedObservationsRemainStale() throws Exception {
        send(sample(mapper, NOW.plusSeconds(31)), INGEST_KEY, 400);
        send(sample(mapper, NOW.minusSeconds(86401)), INGEST_KEY, 400);
        send(sample(mapper, NOW.minusSeconds(181)), INGEST_KEY, 204);
        var server = service.server("stage-host");
        assertThat(server.status()).isEqualTo(TelemetryContracts.Freshness.STALE);
        assertThat(server.receivedAt()).isEqualTo(NOW);
        assertThat(server.warnings()).anyMatch(warning -> warning.contains("180 seconds"));
    }

    @Test
    void partialAndRiskWarningsAreExplanatoryAndNonMutating() throws Exception {
        ObjectNode node = sample(mapper, NOW);
        node.put("complete", false);
        ((ObjectNode) node.get("host")).put("memoryAvailableBytes", 0).put("diskFreeBytes", 0);
        ((ObjectNode) node.get("resources").get(0)).put("oomKilled", true).put("restartCount", 1).put("health", "unhealthy");
        send(node, INGEST_KEY, 204);
        var server = service.server("stage-host");
        assertThat(server.complete()).isFalse();
        assertThat(server.warnings()).anyMatch(warning -> warning.contains("partial"))
            .anyMatch(warning -> warning.contains("active swapping cannot be inferred"))
            .anyMatch(warning -> warning.contains("cumulative"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_operations_assessment", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_job", Integer.class)).isZero();
    }

    @Test
    void historyHasFixedMeansMaximaNullsAndGapsNotSyntheticZeroes() throws Exception {
        send(sample(mapper, NOW.minusSeconds(120)), INGEST_KEY, 204);
        ObjectNode node = sample(mapper, NOW);
        ((ObjectNode) node.get("host")).put("cpuUsagePercent", 40);
        send(node, INGEST_KEY, 204);
        var raw = service.history("stage-host", "1h", null);
        assertThat(raw.bucketSeconds()).isEqualTo(60);
        assertThat(raw.points()).hasSize(2);
        assertThat(raw.points().getFirst().values()).hasSize(8).containsEntry("cpuUsagePercent", 20.0)
            .containsEntry("networkRxBytesPerSecond", null).containsEntry("networkTxBytesPerSecond", 0.0);
        var aggregates = service.history("stage-host", "7d", null);
        assertThat(aggregates.bucketSeconds()).isEqualTo(1800);
        assertThat(aggregates.points()).hasSize(2);
        assertThat(service.history("stage-host", "24h", "b".repeat(64)).points()).isEmpty();
        assertThat(service.history("prod-host", "7d", null).points()).isEmpty();
    }

    @Test
    void aggregatesSurviveRawRetentionWithoutDoubleCountingOrDroppingOldBucketContributions() throws Exception {
        Instant start = NOW.minusSeconds(60);
        send(sample(mapper, start), INGEST_KEY, 204);
        ObjectNode second = sample(mapper, NOW.minusSeconds(1));
        ((ObjectNode) second.get("host")).put("cpuUsagePercent", 40);
        send(second, INGEST_KEY, 409); // Same UTC minute as the first observation.
        clock.set(NOW.plusSeconds(60));
        second = sample(mapper, NOW);
        ((ObjectNode) second.get("host")).put("cpuUsagePercent", 40);
        send(second, INGEST_KEY, 204);
        send(second, INGEST_KEY, 204);
        var points = service.history("stage-host", "7d", null).points();
        assertThat(points).hasSize(2);
        clock.set(NOW.plus(Duration.ofHours(25)));
        service.retain();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operations_host_samples", Integer.class)).isZero();
        assertThat(service.history("stage-host", "24h", null).points()).isEmpty();
        assertThat(service.history("stage-host", "7d", null).points()).hasSize(2);
        clock.set(NOW.plus(Duration.ofDays(8)));
        service.retain();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operations_telemetry_aggregates", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operations_telemetry_boots", Integer.class)).isZero();
        assertThat(service.server("stage-host").status()).isEqualTo(TelemetryContracts.Freshness.STALE);
    }

    @Test
    void equalCadenceStatisticsUseValidObservationCountsAndRetainTrueMaxima() throws Exception {
        for (int i = 0; i < 3; i++) {
            clock.set(NOW.plusSeconds(i * 60));
            ObjectNode node = sample(mapper, clock.instant());
            ObjectNode host = (ObjectNode) node.get("host");
            if (i == 1) host.putNull("cpuUsagePercent");
            else host.put("cpuUsagePercent", i == 0 ? 20 : 60);
            send(node, INGEST_KEY, 204);
        }
        var point = service.history("stage-host", "7d", null).points().getFirst();
        assertThat(point.values()).containsEntry("cpuUsagePercent", 40.0).containsEntry("networkRxBytesPerSecond", null);
        assertThat(point.maxValues()).containsEntry("cpuUsagePercent", 60.0).containsEntry("networkRxBytesPerSecond", null);
    }

    @Test
    void fullRetentionWindowsRemainBelowSixHundredPointsWithOnlyEightFixedSeries() throws Exception {
        var rawRows = new java.util.ArrayList<Object[]>();
        for (int i = 0; i <= 1440; i++) {
            Instant observed = NOW.minusSeconds(i * 60L);
            rawRows.add(new Object[] {"stage-host", Math.floorDiv(observed.getEpochSecond(), 60), BOOT, observed.toString(),
                observed.atOffset(java.time.ZoneOffset.UTC), observed.atOffset(java.time.ZoneOffset.UTC), "f".repeat(64),
                mapper.writeValueAsString(sample(mapper, observed))});
        }
        jdbc.batchUpdate("""
            INSERT INTO operations_host_samples (server_id, sample_minute, boot_id, observed_key, observed_at, received_at, payload_sha256, payload_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, rawRows);
        assertThat(service.history("stage-host", "1h", null).points()).hasSize(61);
        assertThat(service.history("stage-host", "24h", null).points()).hasSize(481);
        var stats = TelemetryMetrics.host(mapper.treeToValue(sample(mapper, NOW).get("host"), TelemetryContracts.HostMetrics.class));
        var aggregateRows = new java.util.ArrayList<Object[]>();
        String json = mapper.writeValueAsString(stats);
        for (int i = 0; i <= 2016; i++) {
            aggregateRows.add(new Object[] {"stage-host", "", NOW.minusSeconds(i * 300L).atOffset(java.time.ZoneOffset.UTC), json});
        }
        jdbc.batchUpdate("INSERT INTO operations_telemetry_aggregates (server_id, resource_id, bucket_start, stats_json) VALUES (?, ?, ?, ?)", aggregateRows);
        var history = service.history("stage-host", "7d", null);
        assertThat(history.points()).hasSize(337).allSatisfy(point -> {
            assertThat(point.values()).hasSize(8);
            assertThat(point.maxValues()).hasSize(8);
        });
    }

    @Test
    void lateSampleCannotEraseAnAggregateContributionWhoseRawRowHasExpired() throws Exception {
        Instant first = NOW.minusSeconds(120);
        send(sample(mapper, first), INGEST_KEY, 204);
        clock.set(NOW.plusSeconds(86400 - 100));
        service.retain();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operations_host_samples", Integer.class)).isZero();
        ObjectNode delayed = sample(mapper, NOW.minusSeconds(60));
        ((ObjectNode) delayed.get("host")).put("cpuUsagePercent", 60);
        send(delayed, INGEST_KEY, 204);
        var point = service.history("stage-host", "7d", null).points().getFirst();
        assertThat(point.values()).containsEntry("cpuUsagePercent", 40.0);
        assertThat(point.maxValues()).containsEntry("cpuUsagePercent", 60.0);
    }

    @Test
    void retentionDeletesAtMostEightThousandAggregateRowsPerPass() {
        var rows = new java.util.ArrayList<Object[]>();
        for (int i = 0; i < 8100; i++) rows.add(new Object[] {"old-host", String.valueOf(i), java.time.OffsetDateTime.parse("2020-01-01T00:00:00Z"), "{}"});
        jdbc.batchUpdate("INSERT INTO operations_telemetry_aggregates (server_id, resource_id, bucket_start, stats_json) VALUES (?, ?, ?, ?)", rows);
        service.retain();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operations_telemetry_aggregates", Integer.class)).isEqualTo(100);
        service.retain();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operations_telemetry_aggregates", Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"range=30d", "range=1h&range=7d", "range=1h&metric=arbitrary", "range=1h&resourceId=short", "range=1h&url=https://private", ""})
    void historyQueriesAreAllowlistedAndBounded(String query) throws Exception {
        mvc.perform(get(PREFIX + "/servers/stage-host/history?" + query).header("Authorization", "Bearer " + READ_KEY))
            .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void unknownServersAndRoutesAreNotInvented() throws Exception {
        mvc.perform(get(PREFIX + "/servers/unknown").header("Authorization", "Bearer " + READ_KEY)).andExpect(status().isNotFound());
        mvc.perform(get(PREFIX + "/servers/stage-host/resources").header("Authorization", "Bearer " + READ_KEY)).andExpect(status().isNotFound());
        mvc.perform(get(PREFIX + "/servers?url=http://elsewhere").header("Authorization", "Bearer " + READ_KEY)).andExpect(status().isBadRequest());
        mvc.perform(post(PREFIX + "/samples").header("Authorization", "Bearer " + INGEST_KEY).contentType(MediaType.APPLICATION_JSON)
            .content("not-json")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Telemetry JSON or fields are invalid."));
    }

    @Test
    void concurrentDeliveryCommitsOneSampleAndOneAggregateContribution() throws Exception {
        byte[] payload = mapper.writeValueAsBytes(sample(mapper, NOW));
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 4; i++) futures.add(pool.submit(() -> {
                try { start.await(); } catch (InterruptedException exception) { throw new RuntimeException(exception); }
                service.ingest("stage-host", payload);
            }));
            start.countDown();
            for (var future : futures) future.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operations_host_samples", Integer.class)).isEqualTo(1);
        String statistics = jdbc.queryForObject("SELECT stats_json FROM operations_telemetry_aggregates WHERE resource_id = ''", String.class);
        assertThat(mapper.readTree(statistics).get("cpuUsagePercent").get("count").asInt()).isEqualTo(1);
    }

    void send(ObjectNode node, String token, int status) throws Exception {
        sendJson(mapper.writeValueAsString(node), token, status);
    }

    void sendJson(String body, String token, int expectedStatus) throws Exception {
        mvc.perform(post(PREFIX + "/samples").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().is(expectedStatus)).andExpect(header().string("Cache-Control", "no-store"));
    }
}
