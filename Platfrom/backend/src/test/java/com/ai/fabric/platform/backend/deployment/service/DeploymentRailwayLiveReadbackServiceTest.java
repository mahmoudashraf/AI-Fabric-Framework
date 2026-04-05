package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRailwayLiveReadbackSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRailwayLiveServiceSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningServicesSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayServicePlanSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayEnvVarSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentRailwayLiveReadbackServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildReturnsReadyWhenRailwayLiveStateMatchesManagedPlan() throws Exception {
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        DeploymentRailwayLiveReadbackService service = new DeploymentRailwayLiveReadbackService(
            railwayGraphqlClient,
            platformSecretService,
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity release = release();
        RailwayProvisioningPlanSummary livePlan = livePlan(
            "feature/platform-v2",
            List.of(
                new RailwayEnvVarSummary("APP_ADMIN_API_KEY", "${secret:APP_ADMIN_API_KEY}"),
                new RailwayEnvVarSummary("CORS_ALLOWED_ORIGINS", "https://ai-fabric.dev")
            ),
            List.of(
                new RailwayEnvVarSummary("CONNECTOR_API_KEY", "${secret:CONNECTOR_API_KEY}"),
                new RailwayEnvVarSummary("REST_CONNECTOR_RUNTIME_PROXY_BASE_URL", "https://runtime.example")
            )
        );

        when(platformSecretService.resolveSecret("APP_ADMIN_API_KEY")).thenReturn("admin-secret");
        when(platformSecretService.resolveSecret("CONNECTOR_API_KEY")).thenReturn("connector-secret");
        stubProject(railwayGraphqlClient);

        when(railwayGraphqlClient.getServiceInstance("env-123", "svc-runtime")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceInstanceSummary(
                "inst-runtime",
                "svc-runtime",
                "runtime-dep-123-dev",
                "ai-infrastructure-module/ai-fabric-runtime",
                "ai-infrastructure-module/ai-fabric-runtime/Dockerfile",
                "/actuator/health",
                null,
                "mahmoudashraf/AI-Fabric-Framework",
                null
            )
        );
        when(railwayGraphqlClient.getServiceSource("svc-runtime")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceSourceSummary(
                "svc-runtime",
                "runtime-dep-123-dev",
                List.of(new RailwayGraphqlClient.RailwayDeploymentTriggerSummary(
                    "trigger-runtime",
                    "svc-runtime",
                    "mahmoudashraf/AI-Fabric-Framework",
                    "feature/platform-v2",
                    "github"
                ))
            )
        );
        when(railwayGraphqlClient.getVariables("proj-123", "env-123", "svc-runtime", true)).thenReturn(
            objectMapper.readTree("""
                {
                  "APP_ADMIN_API_KEY": "admin-secret",
                  "CORS_ALLOWED_ORIGINS": "https://ai-fabric.dev"
                }
                """)
        );
        when(railwayGraphqlClient.listServiceDomains("proj-123", "env-123", "svc-runtime")).thenReturn(
            List.of(new RailwayGraphqlClient.RailwayServiceDomainSummary("dom-runtime", "runtime.example"))
        );

        when(railwayGraphqlClient.getServiceInstance("env-123", "svc-rest")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceInstanceSummary(
                "inst-rest",
                "svc-rest",
                "rest-connector-dep-123-dev",
                "ai-infrastructure-module/ai-fabric-generic-rest-connector",
                "ai-infrastructure-module/ai-fabric-generic-rest-connector/Dockerfile",
                "/actuator/health",
                null,
                "mahmoudashraf/AI-Fabric-Framework",
                null
            )
        );
        when(railwayGraphqlClient.getServiceSource("svc-rest")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceSourceSummary(
                "svc-rest",
                "rest-connector-dep-123-dev",
                List.of(new RailwayGraphqlClient.RailwayDeploymentTriggerSummary(
                    "trigger-rest",
                    "svc-rest",
                    "mahmoudashraf/AI-Fabric-Framework",
                    "feature/platform-v2",
                    "github"
                ))
            )
        );
        when(railwayGraphqlClient.getVariables("proj-123", "env-123", "svc-rest", true)).thenReturn(
            objectMapper.readTree("""
                {
                  "CONNECTOR_API_KEY": "connector-secret",
                  "REST_CONNECTOR_RUNTIME_PROXY_BASE_URL": "https://runtime.example"
                }
                """)
        );
        when(railwayGraphqlClient.listServiceDomains("proj-123", "env-123", "svc-rest")).thenReturn(
            List.of(new RailwayGraphqlClient.RailwayServiceDomainSummary("dom-rest", "rest.example"))
        );

        DeploymentRailwayLiveReadbackSummary summary = service.build(deployment, release, livePlan);

        assertThat(summary.available()).isTrue();
        assertThat(summary.status()).isEqualTo("READY");
        assertThat(summary.projectId()).isEqualTo("proj-123");
        assertThat(summary.environmentId()).isEqualTo("env-123");
        assertServiceReady(summary.runtime(), "svc-runtime");
        assertServiceReady(summary.restConnector(), "svc-rest");
        assertThat(summary.runtime().envVars())
            .anySatisfy(item -> {
                assertThat(item.key()).isEqualTo("APP_ADMIN_API_KEY");
                assertThat(item.sensitive()).isTrue();
                assertThat(item.expectedValue()).isNull();
                assertThat(item.actualValue()).isNull();
            });
    }

    @Test
    void buildFlagsRailwayDriftAndDoesNotExposeSensitiveValues() throws Exception {
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        DeploymentRailwayLiveReadbackService service = new DeploymentRailwayLiveReadbackService(
            railwayGraphqlClient,
            platformSecretService,
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity release = release();
        RailwayProvisioningPlanSummary livePlan = livePlan(
            "feature/platform-v2",
            List.of(
                new RailwayEnvVarSummary("APP_ADMIN_API_KEY", "${secret:APP_ADMIN_API_KEY}"),
                new RailwayEnvVarSummary("CORS_ALLOWED_ORIGINS", "https://ai-fabric.dev")
            ),
            List.of(
                new RailwayEnvVarSummary("CONNECTOR_API_KEY", "${secret:CONNECTOR_API_KEY}"),
                new RailwayEnvVarSummary("REST_CONNECTOR_RUNTIME_PROXY_BASE_URL", "https://runtime.example")
            )
        );

        when(platformSecretService.resolveSecret("APP_ADMIN_API_KEY")).thenReturn("admin-secret");
        when(platformSecretService.resolveSecret("CONNECTOR_API_KEY")).thenReturn("connector-secret");
        stubProject(railwayGraphqlClient);

        when(railwayGraphqlClient.getServiceInstance("env-123", "svc-runtime")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceInstanceSummary(
                "inst-runtime",
                "svc-runtime",
                "runtime-dep-123-dev",
                "ai-infrastructure-module/ai-fabric-runtime",
                "ai-infrastructure-module/ai-fabric-runtime/Dockerfile",
                "/actuator/health",
                null,
                "mahmoudashraf/AI-Fabric-Framework",
                null
            )
        );
        when(railwayGraphqlClient.getServiceSource("svc-runtime")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceSourceSummary(
                "svc-runtime",
                "runtime-dep-123-dev",
                List.of(new RailwayGraphqlClient.RailwayDeploymentTriggerSummary(
                    "trigger-runtime",
                    "svc-runtime",
                    "mahmoudashraf/AI-Fabric-Framework",
                    "feature/platform-drift",
                    "github"
                ))
            )
        );
        when(railwayGraphqlClient.getVariables("proj-123", "env-123", "svc-runtime", true)).thenReturn(
            objectMapper.readTree("""
                {
                  "APP_ADMIN_API_KEY": "wrong-secret",
                  "CORS_ALLOWED_ORIGINS": "https://ai-fabric.dev"
                }
                """)
        );
        when(railwayGraphqlClient.listServiceDomains("proj-123", "env-123", "svc-runtime")).thenReturn(
            List.of(new RailwayGraphqlClient.RailwayServiceDomainSummary("dom-runtime", "runtime.example"))
        );

        when(railwayGraphqlClient.getServiceInstance("env-123", "svc-rest")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceInstanceSummary(
                "inst-rest",
                "svc-rest",
                "rest-connector-dep-123-dev",
                "ai-infrastructure-module/ai-fabric-generic-rest-connector",
                "ai-infrastructure-module/ai-fabric-generic-rest-connector/Dockerfile",
                "/actuator/health",
                null,
                "mahmoudashraf/AI-Fabric-Framework",
                null
            )
        );
        when(railwayGraphqlClient.getServiceSource("svc-rest")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceSourceSummary(
                "svc-rest",
                "rest-connector-dep-123-dev",
                List.of(new RailwayGraphqlClient.RailwayDeploymentTriggerSummary(
                    "trigger-rest",
                    "svc-rest",
                    "mahmoudashraf/AI-Fabric-Framework",
                    "feature/platform-v2",
                    "github"
                ))
            )
        );
        when(railwayGraphqlClient.getVariables("proj-123", "env-123", "svc-rest", true)).thenReturn(
            objectMapper.readTree("""
                {
                  "CONNECTOR_API_KEY": "connector-secret"
                }
                """)
        );
        when(railwayGraphqlClient.listServiceDomains("proj-123", "env-123", "svc-rest")).thenReturn(
            List.of(new RailwayGraphqlClient.RailwayServiceDomainSummary("dom-rest", "rest.example"))
        );

        DeploymentRailwayLiveReadbackSummary summary = service.build(deployment, release, livePlan);

        assertThat(summary.available()).isTrue();
        assertThat(summary.status()).isEqualTo("WARNING");
        assertThat(summary.runtime().status()).isEqualTo("WARNING");
        assertThat(summary.runtime().branch().driftState()).isEqualTo("MISMATCHED");
        assertThat(summary.runtime().envVars())
            .filteredOn(item -> item.key().equals("APP_ADMIN_API_KEY"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.driftState()).isEqualTo("MISMATCHED");
                assertThat(item.sensitive()).isTrue();
                assertThat(item.expectedValue()).isNull();
                assertThat(item.actualValue()).isNull();
            });
        assertThat(summary.restConnector().status()).isEqualTo("WARNING");
        assertThat(summary.restConnector().missingEnvCount()).isEqualTo(1);
        assertThat(summary.restConnector().envVars())
            .filteredOn(item -> item.key().equals("REST_CONNECTOR_RUNTIME_PROXY_BASE_URL"))
            .singleElement()
            .satisfies(item -> assertThat(item.driftState()).isEqualTo("MISSING"));
    }

    @Test
    void buildIncludesVectorizationRunnerWhenPlanned() throws Exception {
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        DeploymentRailwayLiveReadbackService service = new DeploymentRailwayLiveReadbackService(
            railwayGraphqlClient,
            platformSecretService,
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity release = releaseWithRunner();
        RailwayProvisioningPlanSummary livePlan = livePlan(
            "feature/platform-v2",
            List.of(new RailwayEnvVarSummary("APP_ADMIN_API_KEY", "${secret:APP_ADMIN_API_KEY}")),
            List.of(new RailwayEnvVarSummary("CONNECTOR_API_KEY", "${secret:CONNECTOR_API_KEY}")),
            List.of(
                new RailwayEnvVarSummary("PLATFORM_PUBLIC_BASE_URL", "https://platform.example"),
                new RailwayEnvVarSummary("RUNNER_MODE", "PLATFORM_MANAGED_AUTO")
            )
        );

        when(platformSecretService.resolveSecret("APP_ADMIN_API_KEY")).thenReturn("admin-secret");
        when(platformSecretService.resolveSecret("CONNECTOR_API_KEY")).thenReturn("connector-secret");
        stubProject(railwayGraphqlClient);

        when(railwayGraphqlClient.getServiceInstance("env-123", "svc-runtime")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceInstanceSummary(
                "inst-runtime",
                "svc-runtime",
                "runtime-dep-123-dev",
                "ai-infrastructure-module/ai-fabric-runtime",
                "ai-infrastructure-module/ai-fabric-runtime/Dockerfile",
                "/actuator/health",
                null,
                "mahmoudashraf/AI-Fabric-Framework",
                null
            )
        );
        when(railwayGraphqlClient.getServiceSource("svc-runtime")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceSourceSummary(
                "svc-runtime",
                "runtime-dep-123-dev",
                List.of(new RailwayGraphqlClient.RailwayDeploymentTriggerSummary(
                    "trigger-runtime",
                    "svc-runtime",
                    "mahmoudashraf/AI-Fabric-Framework",
                    "feature/platform-v2",
                    "github"
                ))
            )
        );
        when(railwayGraphqlClient.getVariables("proj-123", "env-123", "svc-runtime", true)).thenReturn(
            objectMapper.readTree("""
                {
                  "APP_ADMIN_API_KEY": "admin-secret"
                }
                """)
        );
        when(railwayGraphqlClient.listServiceDomains("proj-123", "env-123", "svc-runtime")).thenReturn(
            List.of(new RailwayGraphqlClient.RailwayServiceDomainSummary("dom-runtime", "runtime.example"))
        );

        when(railwayGraphqlClient.getServiceInstance("env-123", "svc-rest")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceInstanceSummary(
                "inst-rest",
                "svc-rest",
                "rest-connector-dep-123-dev",
                "ai-infrastructure-module/ai-fabric-generic-rest-connector",
                "ai-infrastructure-module/ai-fabric-generic-rest-connector/Dockerfile",
                "/actuator/health",
                null,
                "mahmoudashraf/AI-Fabric-Framework",
                null
            )
        );
        when(railwayGraphqlClient.getServiceSource("svc-rest")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceSourceSummary(
                "svc-rest",
                "rest-connector-dep-123-dev",
                List.of(new RailwayGraphqlClient.RailwayDeploymentTriggerSummary(
                    "trigger-rest",
                    "svc-rest",
                    "mahmoudashraf/AI-Fabric-Framework",
                    "feature/platform-v2",
                    "github"
                ))
            )
        );
        when(railwayGraphqlClient.getVariables("proj-123", "env-123", "svc-rest", true)).thenReturn(
            objectMapper.readTree("""
                {
                  "CONNECTOR_API_KEY": "connector-secret"
                }
                """)
        );
        when(railwayGraphqlClient.listServiceDomains("proj-123", "env-123", "svc-rest")).thenReturn(
            List.of(new RailwayGraphqlClient.RailwayServiceDomainSummary("dom-rest", "rest.example"))
        );

        when(railwayGraphqlClient.getServiceInstance("env-123", "svc-runner")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceInstanceSummary(
                "inst-runner",
                "svc-runner",
                "vectorization-runner-dep-123",
                "ai-fabric-product/ai-fabric-vectorization-runner",
                "ai-fabric-product/ai-fabric-vectorization-runner/deploy/railway/Dockerfile",
                "/actuator/health",
                null,
                "mahmoudashraf/AI-Fabric-Framework",
                null
            )
        );
        when(railwayGraphqlClient.getServiceSource("svc-runner")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceSourceSummary(
                "svc-runner",
                "vectorization-runner-dep-123",
                List.of(new RailwayGraphqlClient.RailwayDeploymentTriggerSummary(
                    "trigger-runner",
                    "svc-runner",
                    "mahmoudashraf/AI-Fabric-Framework",
                    "feature/platform-v2",
                    "github"
                ))
            )
        );
        when(railwayGraphqlClient.getVariables("proj-123", "env-123", "svc-runner", true)).thenReturn(
            objectMapper.readTree("""
                {
                  "PLATFORM_PUBLIC_BASE_URL": "https://platform.example",
                  "RUNNER_MODE": "PLATFORM_MANAGED_AUTO"
                }
                """)
        );
        when(railwayGraphqlClient.listServiceDomains("proj-123", "env-123", "svc-runner")).thenReturn(List.of());

        DeploymentRailwayLiveReadbackSummary summary = service.build(deployment, release, livePlan);

        assertThat(summary.available()).isTrue();
        assertThat(summary.status()).isEqualTo("READY");
        assertThat(summary.vectorizationRunner()).isNotNull();
        assertServiceReady(summary.vectorizationRunner(), "svc-runner");
        assertThat(summary.vectorizationRunner().publicBaseUrl().driftState()).isEqualTo("MATCHED");
    }

    private void assertServiceReady(DeploymentRailwayLiveServiceSummary service, String serviceId) {
        assertThat(service.status()).isEqualTo("READY");
        assertThat(service.serviceId()).isEqualTo(serviceId);
        assertThat(service.missingEnvCount()).isZero();
        assertThat(service.mismatchedEnvCount()).isZero();
        assertThat(service.rootDirectory().driftState()).isEqualTo("MATCHED");
        assertThat(service.dockerfilePath().driftState()).isEqualTo("MATCHED");
        assertThat(service.repository().driftState()).isEqualTo("MATCHED");
        assertThat(service.branch().driftState()).isEqualTo("MATCHED");
        assertThat(service.publicBaseUrl().driftState()).isEqualTo("MATCHED");
    }

    private void stubProject(RailwayGraphqlClient railwayGraphqlClient) {
        when(railwayGraphqlClient.getProject("proj-123")).thenReturn(
            new RailwayGraphqlClient.RailwayProjectSnapshot(
                "proj-123",
                "ai-fabric-dep-123",
                List.of(new RailwayGraphqlClient.RailwayEnvironmentSummary("env-123", "dev")),
                List.of(
                    new RailwayGraphqlClient.RailwayServiceSummary("svc-runtime", "runtime-dep-123-dev"),
                    new RailwayGraphqlClient.RailwayServiceSummary("svc-rest", "rest-connector-dep-123-dev"),
                    new RailwayGraphqlClient.RailwayServiceSummary("svc-runner", "vectorization-runner-dep-123")
                )
            )
        );
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setName("Commerce");
        deployment.setEnvironmentName("dev");
        deployment.setRuntimeBaseUrl("https://runtime.example");
        deployment.setConnectorBaseUrl("https://rest.example");
        return deployment;
    }

    private DeploymentReleaseEntity release() {
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setProvisioningDetailsJson("""
            {
              "railway": {
                "workspaceId": "ws-123",
                "projectId": "proj-123",
                "environmentId": "env-123",
                "services": {
                  "runtime": {
                    "serviceId": "svc-runtime"
                  },
                  "restConnector": {
                    "serviceId": "svc-rest"
                  }
                }
              }
            }
            """);
        return release;
    }

    private DeploymentReleaseEntity releaseWithRunner() {
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setProvisioningDetailsJson("""
            {
              "railway": {
                "workspaceId": "ws-123",
                "projectId": "proj-123",
                "environmentId": "env-123",
                "services": {
                  "runtime": {
                    "serviceId": "svc-runtime"
                  },
                  "restConnector": {
                    "serviceId": "svc-rest"
                  },
                  "vectorizationRunner": {
                    "serviceId": "svc-runner"
                  }
                }
              }
            }
            """);
        return release;
    }

    private RailwayProvisioningPlanSummary livePlan(String branch,
                                                    List<RailwayEnvVarSummary> runtimeEnv,
                                                    List<RailwayEnvVarSummary> restEnv) {
        return livePlan(branch, runtimeEnv, restEnv, null);
    }

    private RailwayProvisioningPlanSummary livePlan(String branch,
                                                    List<RailwayEnvVarSummary> runtimeEnv,
                                                    List<RailwayEnvVarSummary> restEnv,
                                                    List<RailwayEnvVarSummary> runnerEnv) {
        return new RailwayProvisioningPlanSummary(
            "dep-123",
            "Commerce",
            "dev",
            "dev-openai-lucene",
            "ver-123",
            "v1",
            "hash-123",
            "RAILWAY_API",
            "ai-fabric-dep-123",
            "mahmoudashraf/AI-Fabric-Framework",
            branch,
            "ws-123",
            "PLATFORM_SIGNED_URL",
            null,
            new RailwayProvisioningServicesSummary(
                new RailwayServicePlanSummary(
                    "runtime-dep-123-dev",
                    "ai-infrastructure-module/ai-fabric-runtime",
                    "ai-infrastructure-module/ai-fabric-runtime/Dockerfile",
                    "https://runtime.example",
                    runtimeEnv
                ),
                new RailwayServicePlanSummary(
                    "rest-connector-dep-123-dev",
                    "ai-infrastructure-module/ai-fabric-generic-rest-connector",
                    "ai-infrastructure-module/ai-fabric-generic-rest-connector/Dockerfile",
                    "https://rest.example",
                    restEnv
                ),
                runnerEnv == null
                    ? null
                    : new RailwayServicePlanSummary(
                        "vectorization-runner-dep-123",
                        "ai-fabric-product/ai-fabric-vectorization-runner",
                        "ai-fabric-product/ai-fabric-vectorization-runner/deploy/railway/Dockerfile",
                        null,
                        runnerEnv
                    )
            ),
            List.of()
        );
    }
}
