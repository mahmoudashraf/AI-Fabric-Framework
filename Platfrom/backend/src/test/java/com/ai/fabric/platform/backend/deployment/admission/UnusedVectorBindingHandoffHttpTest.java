package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.service.DeploymentExistingVectorBindingService;
import com.ai.fabric.platform.backend.deployment.web.DeploymentExistingVectorBindingController;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {AdmissionUnusedReservationCancellationHttpTest.HttpApp.class, UnusedVectorBindingHandoffHttpTest.Config.class},
    properties = {"spring.config.name=unused-handoff-http-test", "server.address=127.0.0.1", "spring.main.banner-mode=off"})
class UnusedVectorBindingHandoffHttpTest {
    private static final String PATH = "/api/deployments/new/targets/target/existing-vector-binding/handoff-unused";
    private static final String VALID = """
        {"operationKey":"handoff","approvalReference":"approval","sources":[
          {"bindingId":"old","cancellationReceiptId":"receipt","expectedBindingFingerprint":"fingerprint"}],
         "connection":{"versionId":"version","clusterId":"cluster","projectId":"project","regionId":"region",
          "expectedDeploymentOption":"Free","databaseName":"default","approvalReference":"approval","authType":"TOKEN","token":"synthetic-secret"}}
        """;
    @LocalServerPort int port;
    @Autowired DeploymentExistingVectorBindingService service;
    @Autowired ObjectMapper mapper;
    @Autowired AtomicInteger errorDispatches;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach void resetBoundary() { reset(service); errorDispatches.set(0); }

    @Test void exactAdminRequestReachesOnlyTheExplicitHandoffOperation() throws Exception {
        assertThat(post(VALID, "synthetic-admin").statusCode()).isEqualTo(200);
        verify(service).handoffUnused(eq("new"), eq("target"), argThat(value -> value.sources().size() == 1
            && value.connection().token().equals("synthetic-secret")));
        verifyNoMoreInteractions(service);
        assertThat(errorDispatches.get()).isZero();
    }

    @Test void explicitSettlementAndInventoryReachTheSameAdminOnlyBoundary() throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(VALID);
        var source = (com.fasterxml.jackson.databind.node.ObjectNode) node.path("sources").get(0);
        source.remove("cancellationReceiptId"); source.put("knownNoEffectSettlementReceiptId", "settlement");
        node.putObject("inventoryApproval").put("expectedCredentialFingerprint", "credential")
            .put("fullVisibilityReviewReference", "visibility").put("noWriterWindowReference", "window");
        assertThat(post(node.toString(), "synthetic-admin").statusCode()).isEqualTo(200);
        verify(service).handoffUnused(eq("new"), eq("target"), argThat(value -> value.sources().getFirst().cancellationReceiptId() == null
            && "settlement".equals(value.sources().getFirst().knownNoEffectSettlementReceiptId()) && value.inventoryApproval() != null));
    }

    @ParameterizedTest @ValueSource(strings = {"both", "neither", "missing-inventory", "partial-inventory"})
    void settlementProofIsNeverCoercedFromCancellationOrUnreviewedInventory(String change) throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(VALID);
        var source = (com.fasterxml.jackson.databind.node.ObjectNode) node.path("sources").get(0);
        if (!change.equals("both")) source.remove("cancellationReceiptId");
        if (!change.equals("neither")) source.put("knownNoEffectSettlementReceiptId", "settlement");
        if (change.equals("partial-inventory")) node.putObject("inventoryApproval").put("expectedCredentialFingerprint", "credential");
        assertThat(post(node.toString(), "synthetic-admin").statusCode()).isEqualTo(400);
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"unknown", "scalar", "null", "missing", "nestedUnknown", "nestedScalar", "sources"})
    void malformedCommandsFailBeforeAnyBindingOrProviderWork(String mutation) throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(VALID);
        switch (mutation) {
            case "unknown" -> node.put("unreviewed", true);
            case "scalar" -> node.put("operationKey", 9);
            case "null" -> node.putNull("connection");
            case "missing" -> node.remove("approvalReference");
            case "nestedUnknown" -> ((com.fasterxml.jackson.databind.node.ObjectNode) node.get("connection")).put("write", true);
            case "nestedScalar" -> ((com.fasterxml.jackson.databind.node.ObjectNode) node.get("connection")).put("token", 123);
            case "sources" -> node.putArray("sources");
            default -> throw new AssertionError();
        }
        var response = post(node.toString(), "synthetic-admin");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("EXTERNAL_VECTOR_REQUEST_INVALID").doesNotContain("synthetic-secret", "Exception", "column");
        verifyNoInteractions(service);
        assertThat(errorDispatches.get()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"synthetic-operator", "anonymous"})
    void ordinaryOperatorsAndAnonymousCallersCannotHandoffEvenWithMalformedBody(String key) throws Exception {
        for (String body : new String[]{VALID, "{}"}) {
            assertThat(post(body, key.equals("anonymous") ? null : key).statusCode()).isEqualTo(key.equals("anonymous") ? 401 : 403);
        }
        verifyNoInteractions(service);
        assertThat(errorDispatches.get()).isZero();
    }

    private HttpResponse<String> post(String body, String key) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + PATH)).timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (key != null) builder.header("X-Platform-API-Key", key);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({DeploymentExistingVectorBindingController.class, DeploymentExistingVectorBindingController.InvalidBindingRequestHandler.class})
    static class Config {
        @Bean DeploymentExistingVectorBindingService vectorService() { return mock(DeploymentExistingVectorBindingService.class); }
    }
}
