package com.loomai.operations.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;

final class TelemetryFixtures {
    static final String READ_KEY = "telemetry-test-only-read-credential-32-chars";
    static final String INGEST_KEY = "telemetry-test-only-staging-ingest-32-chars";
    static final String OTHER_KEY = "telemetry-test-only-production-ingest-32-chars";
    static final String SOURCES = "[{\"serverId\":\"stage-host\",\"displayName\":\"Staging host\",\"environment\":\"staging\",\"ingestKey\":\""
        + INGEST_KEY + "\"},{\"serverId\":\"prod-host\",\"displayName\":\"Production host\",\"environment\":\"production\",\"ingestKey\":\"" + OTHER_KEY + "\"}]";
    static final String BOOT = "12345678-1234-1234-1234-123456789abc";
    static final String SECOND_BOOT = "87654321-1234-1234-1234-123456789abc";
    static final String RESOURCE = "a".repeat(64);
    static final String PREFIX = TelemetryApiFilter.PREFIX;

    static ObjectNode sample(ObjectMapper mapper, Instant observedAt) throws Exception {
        ObjectNode node = (ObjectNode) mapper.readTree("""
            {
              "schemaVersion":"1", "serverId":"stage-host", "bootId":"12345678-1234-1234-1234-123456789abc",
              "observedAt":"2026-10-24T14:00:00Z", "collectionDurationMs":150, "complete":true, "source":"HOST_NATIVE",
              "host": {
                "cpuThreads":8,"cpuUsagePercent":20.0,"load1":0.5,"load5":0.3,"load15":0.1,
                "memoryTotalBytes":16000000000,"memoryAvailableBytes":8000000000,
                "swapTotalBytes":4000000000,"swapUsedBytes":1000000000,
                "swapInBytesPerSecond":null,"swapOutBytesPerSecond":null,
                "diskTotalBytes":100000000000,"diskFreeBytes":50000000000,"inodesTotal":1000000,"inodesFree":500000,
                "networkRxBytesPerSecond":null,"networkTxBytesPerSecond":0,"cpuStealPercent":null
              },
              "resources":[{
                "resourceId":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "name":"runtime-one", "image":"ghcr.io/example/runtime:stable", "providerResourceId":null,
                "providerKind":"UNMAPPED", "role":"runtime", "startedAt":"2026-10-01T00:00:00Z",
                "state":"running", "health":null, "memoryWorkingSetBytes":500000000,"memoryLimitBytes":1000000000,
                "memoryReservationBytes":0,"memorySwapLimitBytes":-1,"cpuCoresUsed":null,"cpuQuota":1.0,
                "cpusetCpus":"7","cpuThrottledPeriodsPercent":null,"restartCount":0,"oomKilled":false
              }]
            }
            """);
        node.put("observedAt", observedAt.toString());
        return node;
    }
}
