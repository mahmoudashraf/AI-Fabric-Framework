package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.service.UnusedVectorTransferJson;
import com.ai.fabric.platform.backend.deployment.service.UnusedVectorTransferService;
import com.ai.fabric.platform.backend.deployment.web.UnusedVectorTransferController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {AdmissionUnusedReservationCancellationHttpTest.HttpApp.class, UnusedVectorTransferHttpTest.Config.class},
    properties = {"spring.config.name=unused-transfer-http-test", "server.address=127.0.0.1", "spring.main.banner-mode=off"})
class UnusedVectorTransferHttpTest {
    private static final String ROOT = "/api/platform/external-vector-transfers/";
    private static final String RELEASE = """
        {"operationKey":"release","approvalReference":"approval","bindingId":"binding","destination":{
          "controlPlane":{"id":"destination","origin":"https://destination.example.test"},"customerId":"customer",
          "tenantId":"tenant","deploymentId":"deployment","targetProfileId":"target","versionId":"version",
          "versionConfigHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","collectionPrefix":"new_"},
         "sources":[{"bindingId":"binding","expectedBindingFingerprint":"fingerprint","cancellationReceiptId":null}],
         "inventoryApproval":{"expectedCredentialFingerprint":"fingerprint","fullVisibilityReviewReference":"review","noWriterWindowReference":"window"}}
        """;
    private static final String REVIEW = """
        {"operationKey":"review","approvalReference":"approval","authenticatedReadbackJson":"synthetic-exact-response",
         "expectedSourceReceiptFingerprint":"fingerprint","retrievalReference":"authenticated-tls-retrieval"}
        """;
    private static final String CLAIM = """
        {"operationKey":"claim","reviewId":"review","connection":{"versionId":"version","clusterId":"cluster",
          "projectId":"project","regionId":"region","expectedDeploymentOption":"Free","databaseName":"default",
          "approvalReference":"approval","authType":"TOKEN","token":"synthetic-sensitive-token"},
         "inventoryApproval":{"expectedCredentialFingerprint":"fingerprint","fullVisibilityReviewReference":"review","noWriterWindowReference":"window"}}
        """;
    @LocalServerPort int port;
    @Autowired UnusedVectorTransferService service;
    @Autowired AtomicInteger errorDispatches;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach void boundary() { reset(service); errorDispatches.set(0); }

    @Test void explicitAdminCommandsAndReadbacksReachTheirOwnOperationsOnly() throws Exception {
        for (String path : new String[]{"source-releases", "source-reviews", "claims"}) {
            assertThat(send(path, body(path), "synthetic-admin", null).statusCode()).isEqualTo(200);
            assertThat(send(path + "/receipt", null, "synthetic-admin", null).statusCode()).isEqualTo(200);
        }
        verify(service).release(any()); verify(service).registerReviewedSourceProof(any()); verify(service).claim(any());
        verify(service).releaseReadback("receipt"); verify(service).review("receipt"); verify(service).claimReceipt("receipt");
        verifyNoMoreInteractions(service);
        assertThat(errorDispatches.get()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"unknown", "duplicate", "case", "scalar", "boolean", "array", "trailing"})
    void allCommandBoundariesRejectUntrustedJsonWithoutParserOrCredentialLeak(String mutation) throws Exception {
        for (String path : new String[]{"source-releases", "source-reviews", "claims"}) {
            String original = body(path);
            String invalid = switch (mutation) {
                case "unknown" -> original.replace("\"operationKey\":", "\"untrusted\":true,\"operationKey\":");
                case "duplicate" -> original.replace("\"operationKey\":", "\"operationKey\":\"another\",\"operationKey\":");
                case "case" -> original.replace("\"operationKey\"", "\"OperationKey\"");
                case "scalar" -> original.replaceFirst("\"operationKey\":\"[^\"]+\"", "\"operationKey\":17");
                case "boolean" -> original.replaceFirst("\"operationKey\":\"[^\"]+\"", "\"operationKey\":true");
                case "array" -> "[" + original + "]";
                case "trailing" -> original + "{}";
                default -> throw new AssertionError();
            };
            var response = send(path, invalid, "synthetic-admin", null);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).contains("UNUSED_VECTOR_TRANSFER_JSON_INVALID")
                .doesNotContain("synthetic-sensitive-token", "Exception", "column", "JsonParse");
        }
        verifyNoInteractions(service);
        assertThat(errorDispatches.get()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"unknown", "duplicate", "scalar", "enum-case", "enum-number"})
    void nestedConnectionAndScopeRemainStrict(String mutation) throws Exception {
        String invalid = switch (mutation) {
            case "unknown" -> CLAIM.replace("\"token\":", "\"providerWrite\":true,\"token\":");
            case "duplicate" -> RELEASE.replace("\"id\":\"destination\"", "\"id\":\"other\",\"id\":\"destination\"");
            case "scalar" -> CLAIM.replace("\"token\":\"synthetic-sensitive-token\"", "\"token\":123");
            case "enum-case" -> CLAIM.replace("\"TOKEN\"", "\"token\"");
            case "enum-number" -> CLAIM.replace("\"TOKEN\"", "1");
            default -> throw new AssertionError();
        };
        assertThat(send(mutation.equals("duplicate") ? "source-releases" : "claims", invalid, "synthetic-admin", null).statusCode()).isEqualTo(400);
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"operator", "customer", "anonymous", "invalid-key"})
    void nonAdminAndUnauthenticatedCallsCannotReachAnyTransferBoundary(String caller) throws Exception {
        String key = caller.equals("operator") ? "synthetic-operator" : caller.equals("invalid-key") ? "invalid" : null;
        String cookie = caller.equals("customer") ? "session=synthetic-customer-session" : null;
        int status = caller.equals("operator") || caller.equals("customer") ? 403 : 401;
        for (String path : new String[]{"source-releases", "source-reviews", "claims"}) {
            assertThat(send(path, body(path), key, cookie).statusCode()).isEqualTo(status);
            assertThat(send(path, "{}", key, cookie).statusCode()).isEqualTo(status);
            assertThat(send(path + "/receipt", null, key, cookie).statusCode()).isEqualTo(status);
        }
        verifyNoInteractions(service);
        assertThat(errorDispatches.get()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"describe", "collections"})
    void providerFailureCannotEscapeAsSecretLikeHttpMessage(String operation) throws Exception {
        var json = new UnusedVectorTransferJson();
        var secrets = mock(com.ai.fabric.platform.backend.secret.service.PlatformSecretService.class);
        var provider = mock(com.ai.fabric.platform.backend.deployment.service.ZillizCloudControlPlaneClient.class);
        var reader = new com.ai.fabric.platform.backend.deployment.service.ExternalVectorClusterReadback(secrets, provider, json);
        String marker = "synthetic-upstream-sensitive-token";
        when(secrets.resolveSecret("ZILLIZ_CLOUD_API_KEY")).thenReturn(marker);
        when(provider.getCluster("cluster", marker)).thenReturn(new com.ai.fabric.platform.backend.deployment.service.ZillizCloudControlPlaneClient.ZillizClusterSummary(
            "cluster", "synthetic", "project", "region", "Serverless", "", 0, "RUNNING", "https://free.example.test", "Free"));
        var upstream = new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,
            "Authorization Bearer " + marker, new IllegalStateException(marker));
        if (operation.equals("describe")) when(provider.getCluster("cluster", marker)).thenThrow(upstream);
        else when(provider.listServingCollections("https://free.example.test", marker)).thenThrow(upstream);
        when(service.claim(any())).thenAnswer(invocation -> {
            reader.requireEmpty(new com.ai.fabric.platform.backend.deployment.model.UnusedVectorTransferContracts.VectorIdentity(
                "cluster", "project", "region", "Free", "free.example.test", "default", "COOLIFY"),
                new com.ai.fabric.platform.backend.deployment.model.UnusedVectorTransferContracts.InventoryApproval(json.bytesHash(marker), "review", "window"));
            return null;
        });
        var response = send("claims", CLAIM, "synthetic-admin", null);
        assertThat(response.statusCode()).isEqualTo(412);
        assertThat(response.body()).contains("UNUSED_VECTOR_TRANSFER_EMPTY_INVENTORY_UNPROVEN")
            .doesNotContain(marker, "Authorization", "Bearer", "Exception");
        assertThat(errorDispatches.get()).isZero();
    }

    private String body(String path) { return switch (path) { case "source-releases" -> RELEASE; case "source-reviews" -> REVIEW; case "claims" -> CLAIM; default -> throw new AssertionError(); }; }
    private HttpResponse<String> send(String path, String body, String key, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + ROOT + path)).timeout(Duration.ofSeconds(5));
        if (body == null) request.GET();
        else request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (key != null) request.header("X-Platform-API-Key", key);
        if (cookie != null) request.header("Cookie", cookie);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({UnusedVectorTransferController.class, UnusedVectorTransferController.InvalidTransferHandler.class})
    static class Config {
        @Bean UnusedVectorTransferJson transferJson() { return new UnusedVectorTransferJson(); }
        @Bean UnusedVectorTransferService transferService() { return mock(UnusedVectorTransferService.class); }
    }
}
