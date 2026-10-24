package com.ai.fabric.platform.backend.deployment.operations;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Definition;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.ReviewedTopology;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.deployment.budget.ProviderUsageModeResolver;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;

import static com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsProperties.Mode;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Explicit cross-artifact qualification; the service is not a Platform build dependency. */
@EnabledIfSystemProperty(named = "deploymentOperationsJar", matches = ".+")
class DeploymentOperationsServiceProcessTest {
    @TempDir Path temporary;

    @Test void realServiceAndIndependentDatabaseHonorPolicyAndPermitAnOutageBypass() throws Exception {
        Path jar = Path.of(System.getProperty("deploymentOperationsJar")).toAbsolutePath();
        assertThat(jar).isRegularFile();
        var mapper = new ObjectMapper().findAndRegisterModules();
        var properties = new DeploymentOperationsProperties();
        properties.setMode(Mode.ENFORCE);
        properties.setAllowInsecureHttp(true);
        properties.setApiKey("process-test-only-service-key-0000000000000000");
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        properties.setServiceUrl("http://127.0.0.1:" + port);
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms32m", "-Xmx256m", "-jar", jar.toString());
            builder.environment().putAll(Map.of(
                "PORT", Integer.toString(port),
                "DEPLOYMENT_OPERATIONS_API_KEY", properties.getApiKey(),
                "DEPLOYMENT_OPERATIONS_JDBC_URL", database.getJdbcUrl(),
                "DEPLOYMENT_OPERATIONS_JDBC_USERNAME", database.getUsername(),
                "DEPLOYMENT_OPERATIONS_JDBC_PASSWORD", database.getPassword(),
                "DEPLOYMENT_OPERATIONS_CAPACITY_POLICY_JSON", """
                    {"revision":"wire-process-v1","targets":{"staging":{"cpuMillis":2000,
                    "memoryMiB":4096,"diskMiB":6144,"databases":1,"indexes":2,
                    "dependencies":1,"replicas":1,"concurrentApplies":1}}}
                    """));
            Path output = temporary.resolve("service.log");
            Process process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
            try {
                awaitReady(process, properties.getServiceUrl(), output);
                var store = mock(CoreReleaseAuthorizationStore.class);
                var modes = mock(ProviderUsageModeResolver.class);
                when(modes.resolve(any(), any())).thenReturn("TEAM_AUTHORIZED");
                var offers = mock(CustomerOfferDeploymentRepository.class);
                var entitlements = mock(CustomerOfferEntitlementService.class);
                var definition = mock(Definition.class);
                when(definition.topology()).thenReturn(new ReviewedTopology(1, 2000,4096,6144,1,2,1,1));
                when(definition.aiStateStorageTopologies()).thenReturn(Map.of());
                var offer = new CustomerOfferEntity("offer", "{}", "hash", "review", "admin", Instant.now());
                when(entitlements.requireOffer("offer")).thenReturn(offer);
                when(entitlements.definition(offer)).thenReturn(definition);
                var deployment = new DeploymentEntity();
                deployment.setId("deployment-one"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
                when(offers.findById(anyString())).thenAnswer(call -> Optional.of(new CustomerOfferDeploymentEntity(
                    call.getArgument(0), "customer", "tenant", "offer", "staging", "hash",1,"bootstrap",Instant.now())));
                var version = new DeploymentVersionEntity();
                version.setId("version"); version.setDeploymentId(deployment.getId()); version.setConfigHash("a".repeat(64));
                version.setStatus("PUBLISHED"); version.setProviderConfigJson("{}");
                var release = release("release-one", deployment.getId());
                var service = new DeploymentOperationsService(properties, new DeploymentOperationsClient(properties, mapper),
                    store,modes,offers,entitlements,mapper,new CustomerOfferCodec(mapper));

                var allowed = service.authorize(deployment,version,release,"first");
                assertThat(allowed.status()).isEqualTo("ALLOWED");
                assertThat(allowed.policyRevision()).isEqualTo("wire-process-v1");
                verify(store).save(deployment,version,release,"first","TEAM_AUTHORIZED",allowed);
                when(store.find(release.getId())).thenReturn(Optional.of(new CoreReleaseAuthorizationStore.Authorization(
                    release.getId(),"customer","tenant",deployment.getId(),"staging",version.getId(),version.getConfigHash(),
                    "artifact","first","actor","TEAM_AUTHORIZED",allowed,Instant.now())));
                service.report(release.getId(),"ACTIVE");

                deployment.setId("deployment-two"); version.setDeploymentId(deployment.getId());
                var second = release("release-two",deployment.getId());
                assertThatThrownBy(() -> service.authorize(deployment,version,second,"second"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("DEPLOYMENT_OPERATIONS_DENIED");
                properties.setMode(Mode.OBSERVE);
                var observed = service.authorize(deployment,version,release("release-three",deployment.getId()),"observe");
                assertThat(observed.status()).isEqualTo("OBSERVED");
                assertThat(observed.reason()).isEqualTo("TARGET_CAPACITY_EXCEEDED");

                process.destroy();
                assertThat(process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                properties.setMode(Mode.ENFORCE);
                var unavailable = service.authorize(deployment,version,release("release-four",deployment.getId()),"outage");
                assertThat(unavailable.status()).isEqualTo("BYPASSED");
                assertThat(unavailable.reason()).isEqualTo("OPERATIONS_UNAVAILABLE");
            } finally {
                if (process.isAlive()) {
                    process.destroy();
                    if (!process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly().waitFor();
                }
            }
        }
    }

    private DeploymentReleaseEntity release(String id, String deploymentId) {
        var release = new DeploymentReleaseEntity(); release.setId(id); release.setDeploymentId(deploymentId);
        release.setTargetProfileId("staging"); release.setDeploymentVersionId("version"); release.setSourceArtifactId("artifact");
        return release;
    }

    private void awaitReady(Process process, String base, Path output) throws Exception {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (process.isAlive() && System.nanoTime() < deadline) {
            try {
                var response = http.send(HttpRequest.newBuilder(URI.create(base + "/actuator/health/readiness"))
                    .timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) return;
            } catch (java.io.IOException ignored) { }
            Thread.sleep(100);
        }
        fail("Independent service did not become ready: " + Files.readString(output));
    }
}
