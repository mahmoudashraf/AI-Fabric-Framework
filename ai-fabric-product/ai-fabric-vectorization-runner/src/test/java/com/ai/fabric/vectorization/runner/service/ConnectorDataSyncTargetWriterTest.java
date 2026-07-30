package com.ai.fabric.vectorization.runner.service;

import com.ai.fabric.integration.connection.ConnectionDescriptor;
import com.ai.fabric.integration.credential.ResolvedSourceAuthMaterial;
import com.ai.fabric.vectorization.runner.config.VectorizationRunnerProperties;
import com.ai.fabric.vectorization.model.TargetConnectionDescriptor;
import com.ai.fabric.vectorization.model.VectorizationExecutionBundle;
import com.ai.fabric.vectorization.model.VectorizationMappedRecord;
import com.ai.fabric.vectorization.model.VectorizationRunReason;
import com.ai.fabric.vectorization.model.VectorizationRunnerMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ConnectorDataSyncTargetWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConnectorDataSyncTargetWriter writer = new ConnectorDataSyncTargetWriter(
        objectMapper,
        new VectorizationRunnerProperties(
            "https://platform.example",
            "token",
            "runner-1",
            "dep-1",
            "2026.04.track-b",
            "1",
            Duration.ofSeconds(15),
            Duration.ofMinutes(5)
        )
    );

    @Test
    void upsertBatchSendsVerifiedAuthContextToRuntimeDataSync() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedApiKey = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ai/data-sync/batch", exchange -> {
            capturedApiKey.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-RUNTIME-API-KEY"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                {"success":true,"totalOperations":1,"succeededOperations":1,"failedOperations":0,"results":[]}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();
        try {
            VectorizationExecutionBundle bundle = bundle("http://localhost:" + server.getAddress().getPort());

            var result = writer.upsertBatch(
                bundle,
                "policy",
                List.of(new VectorizationMappedRecord("policy", "policy-1", "policy-1", null, Map.of("title", "Policy"), Map.of()))
            );

            assertThat(result.succeeded()).isEqualTo(1);
            assertThat(result.failed()).isZero();
            assertThat(capturedApiKey.get()).isEqualTo("runtime-secret");
            JsonNode request = objectMapper.readTree(capturedBody.get());
            assertThat(request.path("trace").path("authContext").path("subjectId").asText()).isEqualTo("system:platform-vectorization-runner");
            assertThat(request.path("trace").path("authContext").path("deploymentId").asText()).isEqualTo("dep-1");
            assertThat(request.path("operations")).hasSize(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void upsertBatchRejectsUnstructuredSuccessEnvelope() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ai/data-sync/batch", exchange -> {
            byte[] body = """
                {"success":false,"errorCode":"SERVICE_UNAVAILABLE","message":"Connector service unavailable."}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();
        try {
            VectorizationExecutionBundle bundle = bundle("http://localhost:" + server.getAddress().getPort());

            DataSyncTargetWriteException failure = catchThrowableOfType(() -> writer.upsertBatch(
                bundle,
                "policy",
                List.of(new VectorizationMappedRecord("policy", "policy-1", "policy-1", null, Map.of("title", "Policy"), Map.of()))
            ), DataSyncTargetWriteException.class);
            assertThat(failure.failures()).singleElement()
                .satisfies(item -> {
                    assertThat(item.errorCode()).isEqualTo("SERVICE_UNAVAILABLE");
                    assertThat(item.retryDisposition()).isEqualTo(DataSyncRetryDisposition.UNKNOWN);
                });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void upsertBatchIncludesFirstFailureDetailsWhenBatchReturnsOperationFailures() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ai/data-sync/batch", exchange -> {
            byte[] body = """
                {
                  "success": false,
                  "message": "Completed with failures",
                  "failedOperations": 2,
                  "results": [
                    {
                      "success": false,
                      "errorCode": "VECTOR_STORE_FAILED",
                      "vectorSpace": "product",
                      "id": "sku-1",
                      "message": "Vector store failed.",
                      "metadata": {
                        "cause": "Field [vector] vector's dimensions must be <= [1024]; got 1536"
                      }
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();
        try {
            VectorizationExecutionBundle bundle = bundle("http://localhost:" + server.getAddress().getPort());

            DataSyncTargetWriteException failure = catchThrowableOfType(() -> writer.upsertBatch(
                bundle,
                "product",
                List.of(new VectorizationMappedRecord("product", "sku-1", "sku-1", null, Map.of("name", "Laptop"), Map.of()))
            ), DataSyncTargetWriteException.class);
            assertThat(failure.failedOperations()).isEqualTo(2);
            assertThat(failure.failures()).singleElement()
                .satisfies(item -> {
                    assertThat(item.errorCode()).isEqualTo("VECTOR_STORE_FAILED");
                    assertThat(item.vectorSpace()).isEqualTo("product");
                    assertThat(item.entityId()).isEqualTo("sku-1");
                    assertThat(item.message()).isEqualTo("Vector store failed.");
                });
            assertThat(failure.getMessage())
                .contains("VECTOR_STORE_FAILED", "product/sku-1")
                .doesNotContain("dimensions must be", "cause=");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void upsertBatchUsesMappedRecordVectorSpaceAndTombstoneDeleteType() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ai/data-sync/batch", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                {"success":true,"totalOperations":2,"succeededOperations":2,"failedOperations":0,"results":[]}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();
        try {
            VectorizationExecutionBundle bundle = bundle("http://localhost:" + server.getAddress().getPort());

            writer.upsertBatch(
                bundle,
                "produs-safe-knowledge",
                List.of(
                    new VectorizationMappedRecord(
                        "service-module",
                        "module-1",
                        "module-1",
                        "v1",
                        Map.of("title", "Scanner orchestration"),
                        Map.of()
                    ),
                    new VectorizationMappedRecord(
                        "team-profile",
                        "team-1",
                        "team-1",
                        "v2",
                        Map.of("deleted", true),
                        Map.of("source", "produs")
                    )
                )
            );

            JsonNode request = objectMapper.readTree(capturedBody.get());
            JsonNode first = request.path("operations").get(0);
            assertThat(first.path("type").asText()).isEqualTo("UPSERT");
            assertThat(first.path("vectorSpace").asText()).isEqualTo("service-module");
            assertThat(first.path("entity").path("title").asText()).isEqualTo("Scanner orchestration");

            JsonNode second = request.path("operations").get(1);
            assertThat(second.path("type").asText()).isEqualTo("DELETE");
            assertThat(second.path("vectorSpace").asText()).isEqualTo("team-profile");
            assertThat(second.has("entity")).isFalse();
            assertThat(second.path("identity").path("sourceRecordVersion").asText()).isEqualTo("v2");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void non2xxProjectionRejectionPreservesBoundedFrameworkFields() throws Exception {
        DataSyncTargetWriteException failure = failure(
            400,
            """
                {
                  "success": false,
                  "errorCode": "PROJECTION_REJECTED",
                  "message": "Projection rejected: SEARCHABLE_FIELDS_REQUIRED",
                  "providerRequestId": "sync-projection-1",
                  "failedOperations": 1,
                  "results": [
                    {
                      "success": false,
                      "errorCode": "PROJECTION_REJECTED",
                      "message": "Projection rejected: SEARCHABLE_FIELDS_REQUIRED",
                      "vectorSpace": "policy",
                      "id": "policy-1"
                    }
                  ]
                }
                """
        );

        assertThat(failure.httpStatus()).isEqualTo(400);
        assertThat(failure.providerRequestId()).isEqualTo("sync-projection-1");
        assertThat(failure.failures()).singleElement()
            .satisfies(item -> {
                assertThat(item.errorCode()).isEqualTo("PROJECTION_REJECTED");
                assertThat(item.retryDisposition()).isEqualTo(DataSyncRetryDisposition.PERMANENT_INPUT);
                assertThat(item.durableHandoffAccepted()).isFalse();
                assertThat(item.providerRequestId()).isEqualTo("sync-projection-1");
            });
    }

    @Test
    void accessDeniedRequiresIdentityOrPolicyChange() throws Exception {
        DataSyncTargetWriteException failure = failure(
            403,
            """
                {
                  "success": false,
                  "errorCode": "ACCESS_DENIED",
                  "message": "Access denied for one or more operations.",
                  "providerRequestId": "sync-denied-1",
                  "failedOperations": 1,
                  "results": [
                    {
                      "success": false,
                      "errorCode": "ACCESS_DENIED",
                      "message": "Access denied for one or more operations."
                    }
                  ]
                }
                """
        );

        assertThat(failure.failures()).singleElement()
            .extracting(DataSyncTargetFailure::retryDisposition)
            .isEqualTo(DataSyncRetryDisposition.IDENTITY_OR_POLICY_CHANGE_REQUIRED);
    }

    @Test
    void durableRetryableOutcomeRequiresWorkReconciliation() throws Exception {
        DataSyncTargetWriteException failure = failure(
            503,
            """
                {
                  "success": false,
                  "errorCode": "INDEXING_RETRYABLE",
                  "message": "Indexing was accepted for retry but is not yet complete.",
                  "providerRequestId": "sync-retry-1",
                  "failedOperations": 1,
                  "results": [
                    {
                      "success": false,
                      "errorCode": "INDEXING_RETRYABLE",
                      "message": "Indexing was accepted for retry but is not yet complete.",
                      "vectorSpace": "policy",
                      "id": "policy-1",
                      "metadata": {
                        "indexingWorkId": "71",
                        "indexingStatus": "FAILED_RETRYABLE"
                      }
                    }
                  ]
                }
                """
        );

        assertThat(failure.hasDurableHandoff()).isTrue();
        assertThat(failure.failures()).singleElement()
            .satisfies(item -> {
                assertThat(item.indexingWorkId()).isEqualTo("71");
                assertThat(item.indexingStatus()).isEqualTo("FAILED_RETRYABLE");
                assertThat(item.retryDisposition()).isEqualTo(DataSyncRetryDisposition.RECONCILE_DURABLE_WORK);
                assertThat(item.durableHandoffAccepted()).isTrue();
            });
    }

    @Test
    void permanentIndexingOutcomeRequiresOperatorReview() throws Exception {
        DataSyncTargetWriteException failure = failure(
            500,
            """
                {
                  "success": false,
                  "errorCode": "INDEXING_PERMANENT",
                  "message": "Indexing failed permanently and requires operator review.",
                  "failedOperations": 1,
                  "results": [
                    {
                      "success": false,
                      "errorCode": "INDEXING_PERMANENT",
                      "message": "Indexing failed permanently and requires operator review.",
                      "vectorSpace": "policy",
                      "id": "policy-1",
                      "metadata": {
                        "indexingWorkId": "72",
                        "indexingStatus": "FAILED_PERMANENT"
                      }
                    }
                  ]
                }
                """
        );

        assertThat(failure.failures()).singleElement()
            .extracting(DataSyncTargetFailure::retryDisposition)
            .isEqualTo(DataSyncRetryDisposition.OPERATOR_REVIEW);
    }

    @Test
    void submissionFailureIsSafeToResubmitWithStableIdentity() throws Exception {
        DataSyncTargetWriteException failure = failure(
            500,
            """
                {
                  "success": false,
                  "errorCode": "INDEXING_SUBMISSION_FAILED",
                  "message": "Indexing submission could not be accepted.",
                  "failedOperations": 1
                }
                """
        );

        assertThat(failure.failures()).singleElement()
            .satisfies(item -> {
                assertThat(item.retryDisposition()).isEqualTo(DataSyncRetryDisposition.SAFE_RESUBMIT);
                assertThat(item.durableHandoffAccepted()).isFalse();
                assertThat(item.indexingWorkId()).isNull();
            });
    }

    @Test
    void mixedBatchClassifiesEveryFailedOperation() throws Exception {
        DataSyncTargetWriteException failure = failure(
            200,
            """
                {
                  "success": false,
                  "message": "Completed with failures",
                  "providerRequestId": "sync-mixed-1",
                  "totalOperations": 3,
                  "succeededOperations": 1,
                  "failedOperations": 2,
                  "results": [
                    {
                      "success": true,
                      "vectorSpace": "policy",
                      "id": "policy-ok"
                    },
                    {
                      "success": false,
                      "errorCode": "VECTOR_SPACE_NOT_FOUND",
                      "message": "Vector space is not configured.",
                      "vectorSpace": "missing",
                      "id": "policy-missing"
                    },
                    {
                      "success": false,
                      "errorCode": "INDEXING_SUBMISSION_FAILED",
                      "message": "Indexing submission could not be accepted.",
                      "vectorSpace": "policy",
                      "id": "policy-retry"
                    }
                  ]
                }
                """
        );

        assertThat(failure.succeededOperations()).isEqualTo(1);
        assertThat(failure.failedOperations()).isEqualTo(2);
        assertThat(failure.failures())
            .extracting(DataSyncTargetFailure::errorCode)
            .containsExactly("VECTOR_SPACE_NOT_FOUND", "INDEXING_SUBMISSION_FAILED");
        assertThat(failure.failures())
            .extracting(DataSyncTargetFailure::retryDisposition)
            .containsExactly(DataSyncRetryDisposition.CONTRACT_DRIFT, DataSyncRetryDisposition.SAFE_RESUBMIT);
    }

    @Test
    void malformedAndEmptyBodiesAreSanitizedWithoutRequestContent() throws Exception {
        DataSyncTargetWriteException malformed = failure(
            503,
            "{\"secret\":\"do-not-leak\",\"entity\":{\"customer\":\"private\"}"
        );
        DataSyncTargetWriteException empty = failure(503, "");

        assertThat(malformed.failures()).singleElement()
            .extracting(DataSyncTargetFailure::errorCode)
            .isEqualTo("MALFORMED_RESPONSE");
        assertThat(empty.failures()).singleElement()
            .extracting(DataSyncTargetFailure::errorCode)
            .isEqualTo("EMPTY_RESPONSE");
        assertThat(malformed.getMessage()).doesNotContain("do-not-leak", "customer", "private");
        assertThat(empty.getMessage()).doesNotContain("policy-1", "Policy");
    }

    @Test
    void readWorkStatusUsesBothPrivateHeadersAndReturnsBoundedState()
        throws Exception {
        AtomicReference<String> capturedApiKey = new AtomicReference<>();
        AtomicReference<String> capturedAuthorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/admin/indexing/work/71", exchange -> {
            capturedApiKey.set(
                exchange.getRequestHeaders().getFirst(
                    "X-AIFABRIC-RUNTIME-API-KEY"
                )
            );
            capturedAuthorization.set(
                exchange.getRequestHeaders().getFirst(
                    "X-AIFABRIC-RUNTIME-AUTHORIZATION"
                )
            );
            byte[] body = """
                {
                  "success": true,
                  "workId": "71",
                  "status": "COMPLETED",
                  "entityType": "policy",
                  "entityId": "policy-1",
                  "retryCount": 1,
                  "maxRetries": 3
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();
        try {
            DataSyncWorkStatus status = writer.readWorkStatus(
                bundle("http://localhost:" + server.getAddress().getPort()),
                "71"
            );

            assertThat(capturedApiKey.get()).isEqualTo("runtime-secret");
            assertThat(capturedAuthorization.get())
                .isEqualTo("Bearer private-assertion");
            assertThat(status.workId()).isEqualTo("71");
            assertThat(status.status()).isEqualTo("COMPLETED");
            assertThat(status.entityType()).isEqualTo("policy");
            assertThat(status.entityId()).isEqualTo("policy-1");
            assertThat(status.isSuccessfulTerminal()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readWorkStatusRejectsMalformedSuccessWithoutLeakingBody()
        throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/admin/indexing/work/71", exchange -> {
            byte[] body = """
                {"secret":"do-not-leak","payload":{"customer":"private"}}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();
        try {
            DataSyncWorkReconciliationException failure =
                catchThrowableOfType(
                    () -> writer.readWorkStatus(
                        bundle(
                            "http://localhost:"
                                + server.getAddress().getPort()
                        ),
                        "71"
                    ),
                    DataSyncWorkReconciliationException.class
                );

            assertThat(failure.errorCode())
                .isEqualTo("INDEXING_WORK_STATUS_INVALID");
            assertThat(failure.getMessage())
                .doesNotContain("do-not-leak", "customer", "private");
        } finally {
            server.stop(0);
        }
    }

    private DataSyncTargetWriteException failure(int status, String responseBody) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ai/data-sync/batch", exchange -> {
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();
        try {
            VectorizationExecutionBundle bundle = bundle("http://localhost:" + server.getAddress().getPort());
            return catchThrowableOfType(() -> writer.upsertBatch(
                bundle,
                "policy",
                List.of(new VectorizationMappedRecord(
                    "policy",
                    "policy-1",
                    "policy-1",
                    "v1",
                    Map.of("title", "Policy"),
                    Map.of()
                ))
            ), DataSyncTargetWriteException.class);
        } finally {
            server.stop(0);
        }
    }

    private VectorizationExecutionBundle bundle(String baseUrl) {
        ObjectNode empty = objectMapper.createObjectNode();
        ObjectNode authContext = objectMapper.createObjectNode();
        authContext.put("subjectId", "system:platform-vectorization-runner");
        authContext.put("subjectType", "SYSTEM_PROCESS");
        authContext.put("authMode", "PRIVATE_RUNTIME_BACKEND_MEDIATED");
        authContext.put("callerType", "SYSTEM_PROCESS");
        authContext.put("sessionId", "run-1");
        authContext.put("deploymentId", "dep-1");
        authContext.put("customerId", "cus-1");
        authContext.put("tenantId", "ten-1");
        authContext.put("issuer", "platform-vectorization-runner");
        authContext.putArray("grantedScopes").add("data-sync:upsert").add("data-sync:delete").add("vectorization:runner");
        return new VectorizationExecutionBundle(
            "dep-1",
            "run-1",
            VectorizationRunReason.BOOTSTRAP,
            "rev-1",
            VectorizationRunnerMode.PLATFORM_MANAGED_AUTO,
            "vcn-1",
            List.of("policy"),
            empty,
            empty,
            new ConnectionDescriptor("vcn-1", "Primary", "REST_API", "NONE", empty, empty),
            new ResolvedSourceAuthMaterial(Map.of(), Map.of()),
            authContext,
            new TargetConnectionDescriptor(
                "RUNTIME_DATA_SYNC",
                baseUrl,
                "/api/ai/data-sync/batch",
                "/api/ai/data-sync/vector-spaces",
                "X-AIFABRIC-RUNTIME-API-KEY",
                "runtime-secret",
                "/api/admin/indexing/work/{workId}",
                "X-AIFABRIC-RUNTIME-AUTHORIZATION",
                "Bearer private-assertion"
            )
        );
    }
}
