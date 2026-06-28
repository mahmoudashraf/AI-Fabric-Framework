package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.RailwayArtifactUrlsSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayEnvVarSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningServicesSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayServicePlanSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationRunnerProvisioningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoolifyDeploymentProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void provisionsDockerImageApplicationAndPersistsResourceHandle() throws Exception {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        DeploymentProviderResourceHandleRepository resourceHandleRepository = mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentSourceArtifactService sourceArtifactService = mock(DeploymentSourceArtifactService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        CoolifyTargetProfileResolver targetProfileResolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);

        DeploymentTargetProfileEntity profile = profile();
        DeploymentSourceArtifactEntity artifact = artifact();
        CoolifyConnection connection = new CoolifyConnection(
            "http://coolify.example",
            "mock-token",
            new CoolifyTargetProfileConfig(
                "http://coolify.example",
                "project",
                "staging",
                "env",
                "server",
                "destination",
                "runtime.example.test",
                "4.0.0",
                5,
                600,
                false,
                false,
                "8080",
                "/actuator/health",
                "8080"
            )
        );
        CoolifyApplicationSummary application = new CoolifyApplicationSummary(
            "app-uuid",
            "ai-fabric-runtime-dep-123",
            "http://dep-123.runtime.example.test",
            "running",
            "ghcr.io/example/runtime",
            "sha",
            objectMapper.readTree("{\"uuid\":\"app-uuid\",\"status\":\"running\"}")
        );

        when(targetProfileRepository.findById("dtp-coolify-staging")).thenReturn(Optional.of(profile));
        when(targetProfileResolver.requireConnection(profile)).thenReturn(connection);
        when(coolifyApiClient.health(connection)).thenReturn(objectMapper.readTree("{\"status\":\"ok\"}"));
        when(sourceArtifactService.require("dsa-123")).thenReturn(artifact);
        when(resourceHandleRepository.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            eq("dep-123"),
            eq("dtp-coolify-staging"),
            eq("APPLICATION")
        )).thenReturn(Optional.empty());
        when(coolifyApiClient.listApplications(connection)).thenReturn(List.of());
        when(coolifyApiClient.createDockerImageApplication(eq(connection), any())).thenReturn("app-uuid");
        when(coolifyApiClient.getApplication(connection, "app-uuid")).thenReturn(Optional.of(application));
        when(coolifyApiClient.updateEnvironmentVariables(eq(connection), eq("app-uuid"), any())).thenReturn(6);
        when(coolifyApiClient.start(connection, "app-uuid", true, true))
            .thenReturn(new CoolifyActionResponse("Deployment request queued.", "deploy-uuid", objectMapper.createObjectNode()));
        stubFinishedDeployments(coolifyApiClient, connection);
        when(resourceHandleRepository.save(any(DeploymentProviderResourceHandleEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CoolifyDeploymentProvider provider = new CoolifyDeploymentProvider(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            targetProfileResolver,
            coolifyApiClient,
            objectMapper
        );
        DeploymentReleaseEntity release = release();

        ProvisioningResult result = provider.provision(deployment(), version(), release, ProvisioningProgressTracker.noop());

        assertThat(result.target()).isEqualTo("COOLIFY");
        assertThat(result.runtimeBaseUrl()).isEqualTo("http://dep-123.runtime.example.test");
        assertThat(result.detailsJson()).contains("providerResourceHandleId", "app-uuid", "dsa-123");
        ArgumentCaptor<CoolifyCreateDockerImageApplicationRequest> request =
            ArgumentCaptor.forClass(CoolifyCreateDockerImageApplicationRequest.class);
        verify(coolifyApiClient).createDockerImageApplication(eq(connection), request.capture());
        assertThat(request.getValue().domains()).isEqualTo("http://dep-123.runtime.example.test");
        assertThat(request.getValue().portsExposes()).isEqualTo("8097");
        assertThat(request.getValue().healthCheckPort()).isEqualTo("8097");
    }

    @Test
    void provisionsPublicGitApplicationFromRailwayPlan() throws Exception {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        DeploymentProviderResourceHandleRepository resourceHandleRepository = mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentSourceArtifactService sourceArtifactService = mock(DeploymentSourceArtifactService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        CoolifyTargetProfileResolver targetProfileResolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);

        DeploymentTargetProfileEntity profile = profile();
        profile.setSourceStrategy("GIT_SOURCE");
        CoolifyConnection connection = new CoolifyConnection(
            "http://coolify.example",
            "mock-token",
            new CoolifyTargetProfileConfig(
                "http://coolify.example",
                "project",
                "staging",
                "env",
                "server",
                "destination",
                "runtime.example.test",
                "4.0.0",
                5,
                600,
                false,
                false,
                "8080",
                "/actuator/health",
                "8080"
            )
        );
        CoolifyApplicationSummary application = new CoolifyApplicationSummary(
            "app-uuid",
            "runtime-dep-123",
            "http://dep-123.runtime.example.test",
            "running:healthy",
            null,
            null,
            objectMapper.readTree("{\"uuid\":\"app-uuid\",\"status\":\"running:healthy\"}")
        );

        when(targetProfileRepository.findById("dtp-coolify-staging")).thenReturn(Optional.of(profile));
        when(targetProfileResolver.requireConnection(profile)).thenReturn(connection);
        when(coolifyApiClient.health(connection)).thenReturn(objectMapper.readTree("{\"status\":\"ok\"}"));
        when(railwayProvisioningPlanService.buildPlan(any(), any())).thenReturn(railwayPlan());
        when(resourceHandleRepository.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            eq("dep-123"),
            eq("dtp-coolify-staging"),
            eq("APPLICATION")
        )).thenReturn(Optional.empty());
        when(coolifyApiClient.listApplications(connection)).thenReturn(List.of());
        when(coolifyApiClient.createPublicApplication(eq(connection), any())).thenReturn("app-uuid");
        when(coolifyApiClient.getApplication(connection, "app-uuid")).thenReturn(Optional.of(application));
        when(coolifyApiClient.updateEnvironmentVariables(eq(connection), eq("app-uuid"), any())).thenReturn(10);
        when(coolifyApiClient.start(connection, "app-uuid", true, true))
            .thenReturn(new CoolifyActionResponse("Deployment request queued.", "deploy-uuid", objectMapper.createObjectNode()));
        stubFinishedDeployments(coolifyApiClient, connection);
        when(resourceHandleRepository.save(any(DeploymentProviderResourceHandleEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CoolifyDeploymentProvider provider = new CoolifyDeploymentProvider(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            targetProfileResolver,
            coolifyApiClient,
            objectMapper
        );
        DeploymentReleaseEntity release = release();
        release.setSourceArtifactId(null);

        ProvisioningResult result = provider.provision(deployment(), version(), release, ProvisioningProgressTracker.noop());

        assertThat(result.target()).isEqualTo("COOLIFY");
        assertThat(result.detailsJson()).contains(
            "GIT_SOURCE",
            "mahmoudashraf/AI-Fabric-Framework",
            "Platform-V8",
            "/ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile"
        );
        ArgumentCaptor<CoolifyCreatePublicApplicationRequest> request =
            ArgumentCaptor.forClass(CoolifyCreatePublicApplicationRequest.class);
        verify(coolifyApiClient).createPublicApplication(eq(connection), request.capture());
        assertThat(request.getValue().gitRepository()).isEqualTo("mahmoudashraf/AI-Fabric-Framework");
        assertThat(request.getValue().gitBranch()).isEqualTo("Platform-V8");
        assertThat(request.getValue().buildPack()).isEqualTo("dockerfile");
        assertThat(request.getValue().baseDirectory()).isEqualTo("/");
        assertThat(request.getValue().dockerfileLocation()).isEqualTo("/ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile");
        assertThat(request.getValue().autoDeployEnabled()).isFalse();
        assertThat(request.getValue().portsExposes()).isEqualTo("8097");
        assertThat(request.getValue().healthCheckPort()).isEqualTo("8097");
        verifyNoInteractions(sourceArtifactService);
    }

    @Test
    void provisionsProductionRuntimeWithManagedPostgresDatasource() throws Exception {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        DeploymentProviderResourceHandleRepository resourceHandleRepository = mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentSourceArtifactService sourceArtifactService = mock(DeploymentSourceArtifactService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        CoolifyTargetProfileResolver targetProfileResolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);

        DeploymentTargetProfileEntity profile = profile();
        profile.setId("dtp-coolify-production");
        profile.setEnvironmentName("production");
        profile.setResourceDefaultsJson("""
            {
              "runtimeDatabaseMode": "COOLIFY_POSTGRES",
              "runtimeDatabaseNamePrefix": "ai-fabric-runtime-postgres",
              "runtimeDatabaseName": "runtime_chat",
              "runtimeDatabaseUsername": "runtime_user",
              "runtimeDatabaseImage": "postgres:16-alpine",
              "runtimeDatabasePort": "5432",
              "runtimeDatabasePublic": false
            }
            """);
        DeploymentSourceArtifactEntity artifact = artifact();
        CoolifyConnection connection = new CoolifyConnection(
            "http://coolify.example",
            "mock-token",
            new CoolifyTargetProfileConfig(
                "http://coolify.example",
                "project",
                "production",
                "env",
                "server",
                "destination",
                "runtime.example.test",
                "4.0.0",
                5,
                600,
                false,
                false,
                "8080",
                "/actuator/health",
                "8080"
            )
        );
        CoolifyApplicationSummary application = new CoolifyApplicationSummary(
            "app-uuid",
            "ai-fabric-runtime-dep-123",
            "http://dep-123.runtime.example.test",
            "running",
            "ghcr.io/example/runtime",
            "sha",
            objectMapper.readTree("{\"uuid\":\"app-uuid\",\"status\":\"running\"}")
        );
        CoolifyDatabaseSummary database = new CoolifyDatabaseSummary(
            "db-uuid",
            "ai-fabric-runtime-postgres-dep-123",
            "running",
            "postgresql",
            "runtime_user",
            "runtime_chat",
            objectMapper.readTree("{\"uuid\":\"db-uuid\",\"name\":\"ai-fabric-runtime-postgres-dep-123\",\"status\":\"running\"}")
        );

        when(targetProfileRepository.findById("dtp-coolify-production")).thenReturn(Optional.of(profile));
        when(targetProfileResolver.requireConnection(profile)).thenReturn(connection);
        when(coolifyApiClient.health(connection)).thenReturn(objectMapper.readTree("{\"status\":\"ok\"}"));
        when(sourceArtifactService.require("dsa-123")).thenReturn(artifact);
        when(resourceHandleRepository.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            eq("dep-123"),
            eq("dtp-coolify-production"),
            eq("APPLICATION")
        )).thenReturn(Optional.empty());
        when(resourceHandleRepository.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            eq("dep-123"),
            eq("dtp-coolify-production"),
            eq("RUNTIME_POSTGRES_DATABASE")
        )).thenReturn(Optional.empty());
        when(coolifyApiClient.listApplications(connection)).thenReturn(List.of());
        when(coolifyApiClient.createDockerImageApplication(eq(connection), any())).thenReturn("app-uuid");
        when(coolifyApiClient.getApplication(connection, "app-uuid")).thenReturn(Optional.of(application));
        when(platformSecretService.resolveSecret("MANAGED_RUNTIME_POSTGRES_PASSWORD_DEP_DEP_123_PROFILE_DTP_COOLIFY_PRODUCTION"))
            .thenReturn("pg-password");
        when(coolifyApiClient.listDatabases(connection)).thenReturn(List.of());
        when(coolifyApiClient.createPostgresDatabase(eq(connection), any())).thenReturn("db-uuid");
        when(coolifyApiClient.getDatabase(connection, "db-uuid")).thenReturn(Optional.of(database));
        when(coolifyApiClient.startDatabase(connection, "db-uuid"))
            .thenReturn(new CoolifyActionResponse("Database start queued.", "db-deploy-uuid", objectMapper.createObjectNode()));
        when(coolifyApiClient.updateEnvironmentVariables(eq(connection), eq("app-uuid"), any())).thenReturn(14);
        when(coolifyApiClient.start(connection, "app-uuid", true, true))
            .thenReturn(new CoolifyActionResponse("Deployment request queued.", "deploy-uuid", objectMapper.createObjectNode()));
        stubFinishedDeployments(coolifyApiClient, connection);
        when(resourceHandleRepository.save(any(DeploymentProviderResourceHandleEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CoolifyDeploymentProvider provider = new CoolifyDeploymentProvider(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            targetProfileResolver,
            coolifyApiClient,
            platformSecretService,
            objectMapper
        );
        DeploymentReleaseEntity release = release();
        release.setTargetProfileId("dtp-coolify-production");

        ProvisioningResult result = provider.provision(deployment(), version(), release, ProvisioningProgressTracker.noop());

        assertThat(result.detailsJson()).contains(
            "runtimeDatabaseProviderResourceHandleId",
            "db-uuid",
            "COOLIFY_POSTGRES"
        );
        ArgumentCaptor<CoolifyCreatePostgresDatabaseRequest> databaseRequest =
            ArgumentCaptor.forClass(CoolifyCreatePostgresDatabaseRequest.class);
        verify(coolifyApiClient).createPostgresDatabase(eq(connection), databaseRequest.capture());
        assertThat(databaseRequest.getValue().name()).isEqualTo("ai-fabric-runtime-postgres-dep-123");
        assertThat(databaseRequest.getValue().postgresUser()).isEqualTo("runtime_user");
        assertThat(databaseRequest.getValue().postgresDatabase()).isEqualTo("runtime_chat");
        assertThat(databaseRequest.getValue().isPublic()).isFalse();

        ArgumentCaptor<List<CoolifyEnvVar>> envCaptor = ArgumentCaptor.forClass(List.class);
        verify(coolifyApiClient).updateEnvironmentVariables(eq(connection), eq("app-uuid"), envCaptor.capture());
        Map<String, String> env = envCaptor.getValue().stream()
            .filter(envVar -> !envVar.preview())
            .filter(envVar -> envVar.key() != null && envVar.value() != null)
            .collect(Collectors.toMap(CoolifyEnvVar::key, CoolifyEnvVar::value));
        assertThat(env).containsEntry("SPRING_DATASOURCE_URL", "jdbc:postgresql://db-uuid:5432/runtime_chat");
        assertThat(env).containsEntry("SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.postgresql.Driver");
        assertThat(env).containsEntry("SPRING_DATASOURCE_USERNAME", "runtime_user");
        assertThat(env).containsEntry("SPRING_DATASOURCE_PASSWORD", "pg-password");
        assertThat(env).containsEntry("PLATFORM_RUNTIME_DATABASE_MODE", "COOLIFY_POSTGRES");
        assertThat(env).containsEntry("PLATFORM_RUNTIME_DATABASE_RESOURCE_UUID", "db-uuid");
    }

    @Test
    void provisionsPublicGitApplicationWithResolvedManagedVectorProviderConfig() throws Exception {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        DeploymentProviderResourceHandleRepository resourceHandleRepository = mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentSourceArtifactService sourceArtifactService = mock(DeploymentSourceArtifactService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        DeploymentManagedVectorProvisioningService managedVectorProvisioningService =
            mock(DeploymentManagedVectorProvisioningService.class);
        DeploymentManagedVectorResourceService managedVectorResourceService =
            mock(DeploymentManagedVectorResourceService.class);
        CoolifyTargetProfileResolver targetProfileResolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);

        DeploymentTargetProfileEntity profile = profile();
        profile.setSourceStrategy("GIT_SOURCE");
        CoolifyConnection connection = new CoolifyConnection(
            "http://coolify.example",
            "mock-token",
            new CoolifyTargetProfileConfig(
                "http://coolify.example",
                "project",
                "staging",
                "env",
                "server",
                "destination",
                "runtime.example.test",
                "4.0.0",
                5,
                600,
                false,
                false,
                "8080",
                "/actuator/health",
                "8080"
            )
        );
        CoolifyApplicationSummary application = new CoolifyApplicationSummary(
            "app-uuid",
            "runtime-dep-123",
            "http://dep-123.runtime.example.test",
            "running:healthy",
            null,
            null,
            objectMapper.readTree("{\"uuid\":\"app-uuid\",\"status\":\"running:healthy\"}")
        );
        JsonNode effectiveProviderConfig = objectMapper.readTree("""
            {
              "vectorStrategy": "qdrant",
              "qdrantHost": "https://managed-qdrant.example",
              "qdrantRuntimeApiKeySecretName": "MANAGED_QDRANT_RUNTIME_KEY"
            }
            """);
        ManagedVectorProvisioningResult managedVectorResult = new ManagedVectorProvisioningResult(
            effectiveProviderConfig,
            objectMapper.readTree("{\"enabled\":true,\"vectorStrategy\":\"qdrant\",\"mode\":\"MANAGED_CLOUD_CLUSTER\"}")
        );
        RailwayProvisioningPlanSummary plan = railwayPlanWithRuntimeEnv(List.of(
            new RailwayEnvVarSummary("AI_CONFIG_DEFAULT_FILE", "https://artifacts.example/entities.yaml"),
            new RailwayEnvVarSummary("AI_PROVIDERS_QDRANT_HOST", "https://managed-qdrant.example")
        ));

        when(targetProfileRepository.findById("dtp-coolify-staging")).thenReturn(Optional.of(profile));
        when(targetProfileResolver.requireConnection(profile)).thenReturn(connection);
        when(coolifyApiClient.health(connection)).thenReturn(objectMapper.readTree("{\"status\":\"ok\"}"));
        when(managedVectorProvisioningService.requiresProvisioning(any())).thenReturn(true);
        when(managedVectorProvisioningService.ensureProvisioned(any(), any())).thenReturn(managedVectorResult);
        when(railwayProvisioningPlanService.buildPlan(any(), any(), eq(effectiveProviderConfig))).thenReturn(plan);
        when(resourceHandleRepository.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            eq("dep-123"),
            eq("dtp-coolify-staging"),
            eq("APPLICATION")
        )).thenReturn(Optional.empty());
        when(coolifyApiClient.listApplications(connection)).thenReturn(List.of());
        when(coolifyApiClient.createPublicApplication(eq(connection), any())).thenReturn("app-uuid");
        when(coolifyApiClient.getApplication(connection, "app-uuid")).thenReturn(Optional.of(application));
        when(coolifyApiClient.updateEnvironmentVariables(eq(connection), eq("app-uuid"), any())).thenReturn(3);
        when(coolifyApiClient.start(connection, "app-uuid", true, true))
            .thenReturn(new CoolifyActionResponse("Deployment request queued.", "deploy-uuid", objectMapper.createObjectNode()));
        stubFinishedDeployments(coolifyApiClient, connection);
        when(resourceHandleRepository.save(any(DeploymentProviderResourceHandleEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CoolifyDeploymentProvider provider = new CoolifyDeploymentProvider(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            managedVectorProvisioningService,
            managedVectorResourceService,
            targetProfileResolver,
            coolifyApiClient,
            null,
            null,
            objectMapper
        );
        DeploymentReleaseEntity release = release();
        release.setSourceArtifactId(null);

        ProvisioningResult result = provider.provision(deployment(), version(), release, ProvisioningProgressTracker.noop());

        verify(railwayProvisioningPlanService).buildPlan(any(), any(), eq(effectiveProviderConfig));
        verify(managedVectorResourceService).syncProvisionedResources(any(), any(), any(), eq(managedVectorResult));
        ArgumentCaptor<List<CoolifyEnvVar>> env = ArgumentCaptor.forClass(List.class);
        verify(coolifyApiClient).updateEnvironmentVariables(eq(connection), eq("app-uuid"), env.capture());
        Map<String, String> values = env.getValue().stream()
            .filter(value -> !value.preview())
            .filter(value -> value.value() != null)
            .collect(Collectors.toMap(CoolifyEnvVar::key, CoolifyEnvVar::value));
        assertThat(values).containsEntry("AI_PROVIDERS_QDRANT_HOST", "https://managed-qdrant.example");
        assertThat(result.detailsJson()).contains(
            "\"managedVectorProvisioning\"",
            "\"effectiveProviderConfig\"",
            "managed-qdrant.example"
        );
    }

    @Test
    void provisionsApplicationInsideCustomerCoolifyProjectAndEnvironment() throws Exception {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        DeploymentProviderResourceHandleRepository resourceHandleRepository = mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentSourceArtifactService sourceArtifactService = mock(DeploymentSourceArtifactService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        CoolifyTargetProfileResolver targetProfileResolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);
        PlatformCustomerRepository platformCustomerRepository = mock(PlatformCustomerRepository.class);

        DeploymentTargetProfileEntity profile = profile();
        profile.setSourceStrategy("GIT_SOURCE");
        CoolifyConnection connection = new CoolifyConnection(
            "http://coolify.example",
            "mock-token",
            new CoolifyTargetProfileConfig(
                "http://coolify.example",
                "default-project",
                "staging",
                "default-env",
                "server",
                "destination",
                "runtime.example.test",
                "4.0.0",
                5,
                600,
                false,
                false,
                "8080",
                "/actuator/health",
                "8080"
            )
        );
        CoolifyApplicationSummary application = new CoolifyApplicationSummary(
            "app-uuid",
            "runtime-dep-123",
            "http://dep-123.runtime.example.test",
            "running:healthy",
            null,
            null,
            objectMapper.readTree("{\"uuid\":\"app-uuid\",\"status\":\"running:healthy\",\"project_uuid\":\"customer-project\"}")
        );
        PlatformCustomerEntity customer = new PlatformCustomerEntity();
        customer.setId("customer");
        customer.setName("Shopping Companion Test");
        customer.setSlug("shopping-companion-test");
        customer.setStatus("ACTIVE");
        customer.setPlatformManaged(true);
        customer.setCreatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        customer.setUpdatedAt(Instant.parse("2026-05-01T00:00:00Z"));

        when(targetProfileRepository.findById("dtp-coolify-staging")).thenReturn(Optional.of(profile));
        when(targetProfileResolver.requireConnection(profile)).thenReturn(connection);
        when(coolifyApiClient.health(connection)).thenReturn(objectMapper.readTree("{\"status\":\"ok\"}"));
        when(railwayProvisioningPlanService.buildPlan(any(), any())).thenReturn(railwayPlan());
        when(platformCustomerRepository.findById("customer")).thenReturn(Optional.of(customer));
        when(coolifyApiClient.listProjects(connection)).thenReturn(List.of());
        when(coolifyApiClient.createProject(eq(connection), eq("customer-shopping-companion-test"), anyString()))
            .thenReturn("customer-project");
        when(coolifyApiClient.listEnvironments(connection, "customer-project")).thenReturn(List.of());
        when(coolifyApiClient.createEnvironment(connection, "customer-project", "staging")).thenReturn("customer-env");
        when(resourceHandleRepository.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            eq("dep-123"),
            eq("dtp-coolify-staging"),
            eq("APPLICATION")
        )).thenReturn(Optional.empty());
        when(coolifyApiClient.listApplications(connection)).thenReturn(List.of());
        when(coolifyApiClient.createPublicApplication(eq(connection), any())).thenReturn("app-uuid");
        when(coolifyApiClient.getApplication(connection, "app-uuid")).thenReturn(Optional.of(application));
        when(coolifyApiClient.updateEnvironmentVariables(eq(connection), eq("app-uuid"), any())).thenReturn(10);
        when(coolifyApiClient.start(connection, "app-uuid", true, true))
            .thenReturn(new CoolifyActionResponse("Deployment request queued.", "deploy-uuid", objectMapper.createObjectNode()));
        stubFinishedDeployments(coolifyApiClient, connection);
        when(resourceHandleRepository.save(any(DeploymentProviderResourceHandleEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CoolifyDeploymentProvider provider = new CoolifyDeploymentProvider(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            targetProfileResolver,
            coolifyApiClient,
            null,
            platformCustomerRepository,
            objectMapper
        );
        DeploymentReleaseEntity release = release();
        release.setSourceArtifactId(null);

        ProvisioningResult result = provider.provision(deployment(), version(), release, ProvisioningProgressTracker.noop());

        assertThat(result.detailsJson()).contains(
            "\"customerProjectGroupingEnabled\" : true",
            "\"projectUuid\" : \"customer-project\"",
            "\"projectName\" : \"customer-shopping-companion-test\"",
            "\"environmentUuid\" : \"customer-env\""
        );
        ArgumentCaptor<CoolifyCreatePublicApplicationRequest> request =
            ArgumentCaptor.forClass(CoolifyCreatePublicApplicationRequest.class);
        verify(coolifyApiClient).createPublicApplication(eq(connection), request.capture());
        assertThat(request.getValue().projectUuid()).isEqualTo("customer-project");
        assertThat(request.getValue().environmentName()).isEqualTo("staging");
        assertThat(request.getValue().environmentUuid()).isEqualTo("customer-env");

        ArgumentCaptor<DeploymentProviderResourceHandleEntity> handle =
            ArgumentCaptor.forClass(DeploymentProviderResourceHandleEntity.class);
        verify(resourceHandleRepository).save(handle.capture());
        assertThat(handle.getValue().getProviderProjectUuid()).isEqualTo("customer-project");
        assertThat(handle.getValue().getProviderEnvironmentUuid()).isEqualTo("customer-env");
        assertThat(handle.getValue().getMetadataJson()).contains("customer-shopping-companion-test");
        verifyNoInteractions(sourceArtifactService);
    }

    @Test
    void replacesExistingHandleWhenCoolifyProjectScopeChanges() throws Exception {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        DeploymentProviderResourceHandleRepository resourceHandleRepository = mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentSourceArtifactService sourceArtifactService = mock(DeploymentSourceArtifactService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        CoolifyTargetProfileResolver targetProfileResolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);
        PlatformCustomerRepository platformCustomerRepository = mock(PlatformCustomerRepository.class);

        DeploymentTargetProfileEntity profile = profile();
        CoolifyConnection connection = new CoolifyConnection(
            "http://coolify.example",
            "mock-token",
            new CoolifyTargetProfileConfig(
                "http://coolify.example",
                "default-project",
                "staging",
                "default-env",
                "server",
                "destination",
                "runtime.example.test",
                "4.0.0",
                5,
                600,
                false,
                false,
                "8080",
                "/actuator/health",
                "8080"
            )
        );
        PlatformCustomerEntity customer = new PlatformCustomerEntity();
        customer.setId("customer");
        customer.setName("Acme");
        customer.setSlug("acme");
        customer.setStatus("ACTIVE");
        customer.setPlatformManaged(true);
        customer.setCreatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        customer.setUpdatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        DeploymentProviderResourceHandleEntity oldHandle = new DeploymentProviderResourceHandleEntity();
        oldHandle.setId("dprh-old");
        oldHandle.setDeploymentId("dep-123");
        oldHandle.setTargetProfileId("dtp-coolify-staging");
        oldHandle.setResourceKind("APPLICATION");
        oldHandle.setProviderType(DeploymentProviderType.COOLIFY);
        oldHandle.setProviderResourceUuid("old-app");
        oldHandle.setProviderProjectUuid("default-project");
        oldHandle.setProviderEnvironmentUuid("default-env");
        oldHandle.setCreatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        CoolifyApplicationSummary newApplication = new CoolifyApplicationSummary(
            "new-app",
            "ai-fabric-runtime-dep-123",
            "http://dep-123.runtime.example.test",
            "running:healthy",
            "ghcr.io/example/runtime",
            "sha",
            objectMapper.readTree("{\"uuid\":\"new-app\",\"status\":\"running:healthy\",\"project_uuid\":\"customer-project\"}")
        );
        CoolifyApplicationSummary conflictingOldApplication = new CoolifyApplicationSummary(
            "old-app",
            "ai-fabric-runtime-dep-123",
            "http://dep-123.runtime.example.test",
            "running:healthy",
            "ghcr.io/example/runtime",
            "sha",
            objectMapper.readTree("{\"uuid\":\"old-app\",\"status\":\"running:healthy\",\"project_uuid\":\"default-project\"}")
        );

        when(targetProfileRepository.findById("dtp-coolify-staging")).thenReturn(Optional.of(profile));
        when(targetProfileResolver.requireConnection(profile)).thenReturn(connection);
        when(coolifyApiClient.health(connection)).thenReturn(objectMapper.readTree("{\"status\":\"ok\"}"));
        when(sourceArtifactService.require("dsa-123")).thenReturn(artifact());
        when(platformCustomerRepository.findById("customer")).thenReturn(Optional.of(customer));
        when(coolifyApiClient.listProjects(connection)).thenReturn(List.of());
        when(coolifyApiClient.createProject(eq(connection), eq("customer-acme"), anyString()))
            .thenReturn("customer-project");
        when(coolifyApiClient.listEnvironments(connection, "customer-project")).thenReturn(List.of());
        when(coolifyApiClient.createEnvironment(connection, "customer-project", "staging")).thenReturn("customer-env");
        when(resourceHandleRepository.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            eq("dep-123"),
            eq("dtp-coolify-staging"),
            eq("APPLICATION")
        )).thenReturn(Optional.of(oldHandle));
        when(coolifyApiClient.delete(connection, "old-app", true, false, true, true))
            .thenReturn(new CoolifyActionResponse("Deleted.", null, objectMapper.createObjectNode()));
        when(coolifyApiClient.getApplication(connection, "old-app")).thenReturn(Optional.empty());
        when(coolifyApiClient.listApplications(connection))
            .thenReturn(List.of())
            .thenReturn(List.of(conflictingOldApplication));
        when(coolifyApiClient.createDockerImageApplication(eq(connection), any()))
            .thenThrow(new CoolifyApiException("duplicate app", 409, "/applications/dockerimage"))
            .thenReturn("new-app");
        when(coolifyApiClient.getApplication(connection, "new-app")).thenReturn(Optional.of(newApplication));
        when(coolifyApiClient.updateEnvironmentVariables(eq(connection), eq("new-app"), any())).thenReturn(8);
        when(coolifyApiClient.start(connection, "new-app", true, true))
            .thenReturn(new CoolifyActionResponse("Deployment request queued.", "deploy-uuid", objectMapper.createObjectNode()));
        stubFinishedDeployments(coolifyApiClient, connection);
        when(resourceHandleRepository.save(any(DeploymentProviderResourceHandleEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CoolifyDeploymentProvider provider = new CoolifyDeploymentProvider(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            targetProfileResolver,
            coolifyApiClient,
            null,
            platformCustomerRepository,
            objectMapper
        );

        provider.provision(deployment(), version(), release(), ProvisioningProgressTracker.noop());

        verify(coolifyApiClient, times(2)).delete(connection, "old-app", true, false, true, true);
        verify(coolifyApiClient, never()).updateDockerImageApplication(eq(connection), eq("old-app"), any());
        ArgumentCaptor<CoolifyCreateDockerImageApplicationRequest> request =
            ArgumentCaptor.forClass(CoolifyCreateDockerImageApplicationRequest.class);
        verify(coolifyApiClient, times(2)).createDockerImageApplication(eq(connection), request.capture());
        assertThat(request.getAllValues().getLast().projectUuid()).isEqualTo("customer-project");

        ArgumentCaptor<DeploymentProviderResourceHandleEntity> handle =
            ArgumentCaptor.forClass(DeploymentProviderResourceHandleEntity.class);
        verify(resourceHandleRepository).save(handle.capture());
        assertThat(handle.getValue().getId()).isEqualTo("dprh-old");
        assertThat(handle.getValue().getProviderResourceUuid()).isEqualTo("new-app");
        assertThat(handle.getValue().getProviderProjectUuid()).isEqualTo("customer-project");
    }

    @Test
    void provisionsPublicGitRuntimeAndConnectorApplicationsWithResolvedSecrets() throws Exception {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        DeploymentProviderResourceHandleRepository resourceHandleRepository = mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentSourceArtifactService sourceArtifactService = mock(DeploymentSourceArtifactService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        CoolifyTargetProfileResolver targetProfileResolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);

        DeploymentTargetProfileEntity profile = profile();
        profile.setSourceStrategy("GIT_SOURCE");
        CoolifyConnection connection = new CoolifyConnection(
            "http://coolify.example",
            "mock-token",
            new CoolifyTargetProfileConfig(
                "http://coolify.example",
                "project",
                "staging",
                "env",
                "server",
                "destination",
                "runtime.example.test",
                "4.0.0",
                5,
                600,
                false,
                false,
                "8080",
                "/actuator/health",
                "8080"
            )
        );
        CoolifyApplicationSummary runtimeApplication = new CoolifyApplicationSummary(
            "runtime-uuid",
            "runtime-dep-123",
            "http://dep-123.runtime.example.test",
            "running:healthy",
            null,
            null,
            objectMapper.readTree("{\"uuid\":\"runtime-uuid\",\"status\":\"running:healthy\"}")
        );
        CoolifyApplicationSummary connectorApplication = new CoolifyApplicationSummary(
            "connector-uuid",
            "rest-connector-dep-123",
            "http://dep-123-connector.runtime.example.test",
            "running:healthy",
            null,
            null,
            objectMapper.readTree("{\"uuid\":\"connector-uuid\",\"status\":\"running:healthy\"}")
        );

        when(targetProfileRepository.findById("dtp-coolify-staging")).thenReturn(Optional.of(profile));
        when(targetProfileResolver.requireConnection(profile)).thenReturn(connection);
        when(coolifyApiClient.health(connection)).thenReturn(objectMapper.readTree("{\"status\":\"ok\"}"));
        when(railwayProvisioningPlanService.buildPlan(any(), any())).thenReturn(railwayPlanWithConnectorAndSecrets());
        when(resourceHandleRepository.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            eq("dep-123"),
            eq("dtp-coolify-staging"),
            eq("APPLICATION")
        )).thenReturn(Optional.empty());
        when(resourceHandleRepository.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            eq("dep-123"),
            eq("dtp-coolify-staging"),
            eq("CONNECTOR_APPLICATION")
        )).thenReturn(Optional.empty());
        when(coolifyApiClient.listApplications(connection)).thenReturn(List.of());
        when(coolifyApiClient.createPublicApplication(eq(connection), any()))
            .thenReturn("runtime-uuid")
            .thenReturn("connector-uuid");
        when(coolifyApiClient.getApplication(connection, "runtime-uuid")).thenReturn(Optional.of(runtimeApplication));
        when(coolifyApiClient.getApplication(connection, "connector-uuid")).thenReturn(Optional.of(connectorApplication));
        when(coolifyApiClient.updateEnvironmentVariables(eq(connection), eq("runtime-uuid"), any())).thenReturn(12);
        when(coolifyApiClient.updateEnvironmentVariables(eq(connection), eq("connector-uuid"), any())).thenReturn(8);
        when(coolifyApiClient.start(connection, "runtime-uuid", true, true))
            .thenReturn(new CoolifyActionResponse("Runtime deployment queued.", "runtime-deploy", objectMapper.createObjectNode()));
        when(coolifyApiClient.start(connection, "connector-uuid", true, true))
            .thenReturn(new CoolifyActionResponse("Connector deployment queued.", "connector-deploy", objectMapper.createObjectNode()));
        stubFinishedDeployments(coolifyApiClient, connection);
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn("runtime-secret");
        when(resourceHandleRepository.save(any(DeploymentProviderResourceHandleEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CoolifyDeploymentProvider provider = new CoolifyDeploymentProvider(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            targetProfileResolver,
            coolifyApiClient,
            platformSecretService,
            objectMapper
        );
        DeploymentReleaseEntity release = release();
        release.setSourceArtifactId(null);

        ProvisioningResult result = provider.provision(deployment(), version(), release, ProvisioningProgressTracker.noop());

        assertThat(result.runtimeBaseUrl()).isEqualTo("http://dep-123.runtime.example.test");
        assertThat(result.connectorBaseUrl()).isEqualTo("http://dep-123-connector.runtime.example.test");
        assertThat(result.detailsJson()).contains(
            "runtimeProviderResourceHandleId",
            "connectorProviderResourceHandleId",
            "connector-uuid"
        );

        ArgumentCaptor<CoolifyCreatePublicApplicationRequest> request =
            ArgumentCaptor.forClass(CoolifyCreatePublicApplicationRequest.class);
        verify(coolifyApiClient, times(2)).createPublicApplication(eq(connection), request.capture());
        assertThat(request.getAllValues())
            .extracting(CoolifyCreatePublicApplicationRequest::dockerfileLocation)
            .containsExactly(
                "/ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile",
                "/ai-infrastructure-module/ai-infrastructure-generic-rest-connector/deploy/railway/Dockerfile"
            );
        assertThat(request.getAllValues())
            .extracting(CoolifyCreatePublicApplicationRequest::domains)
            .containsExactly(
                "http://dep-123.runtime.example.test",
                "http://dep-123-connector.runtime.example.test"
            );
        assertThat(request.getAllValues())
            .extracting(CoolifyCreatePublicApplicationRequest::portsExposes)
            .containsExactly("8097", "8082");
        assertThat(request.getAllValues())
            .extracting(CoolifyCreatePublicApplicationRequest::healthCheckPort)
            .containsExactly("8097", "8082");

        ArgumentCaptor<List<CoolifyEnvVar>> runtimeEnv = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<CoolifyEnvVar>> connectorEnv = ArgumentCaptor.forClass(List.class);
        verify(coolifyApiClient).updateEnvironmentVariables(eq(connection), eq("runtime-uuid"), runtimeEnv.capture());
        verify(coolifyApiClient).updateEnvironmentVariables(eq(connection), eq("connector-uuid"), connectorEnv.capture());
        Map<String, CoolifyEnvVar> runtimeEnvByKey = envByKey(runtimeEnv.getValue());
        Map<String, CoolifyEnvVar> connectorEnvByKey = envByKey(connectorEnv.getValue());

        assertThat(runtimeEnvByKey.get("ACTIONS_CONNECTOR_BASE_URL").value())
            .isEqualTo("http://dep-123-connector.runtime.example.test");
        assertThat(runtimeEnvByKey.get("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY").value())
            .isEqualTo("runtime-secret");
        assertThat(runtimeEnvByKey.get("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY").shownOnce()).isTrue();
        assertThat(runtimeEnv.getValue())
            .filteredOn(env -> "PLATFORM_DEPLOYMENT_VERSION_ID".equals(env.key()))
            .extracting(CoolifyEnvVar::preview)
            .containsExactly(false, true);
        assertThat(connectorEnvByKey.get("REST_CONNECTOR_RUNTIME_PROXY_BASE_URL").value())
            .isEqualTo("http://dep-123.runtime.example.test");
        assertThat(connectorEnvByKey.get("REST_CONNECTOR_RUNTIME_PROXY_API_KEY").value())
            .isEqualTo("runtime-secret");
        assertThat(connectorEnvByKey.get("REST_CONNECTOR_RUNTIME_PROXY_API_KEY").shownOnce()).isTrue();
        assertThat(connectorEnv.getValue())
            .filteredOn(env -> "PLATFORM_DEPLOYMENT_VERSION_ID".equals(env.key()))
            .extracting(CoolifyEnvVar::preview)
            .containsExactly(false, true);
        verifyNoInteractions(sourceArtifactService);
    }

    @Test
    void provisionsCoolifyVectorizationRunnerApplicationWhenPlanRequiresIt() throws Exception {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        DeploymentProviderResourceHandleRepository resourceHandleRepository = mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentSourceArtifactService sourceArtifactService = mock(DeploymentSourceArtifactService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        CoolifyTargetProfileResolver targetProfileResolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        VectorizationRunnerProvisioningService vectorizationRunnerProvisioningService =
            mock(VectorizationRunnerProvisioningService.class);

        DeploymentTargetProfileEntity profile = profile();
        profile.setSourceStrategy("GIT_SOURCE");
        CoolifyConnection connection = new CoolifyConnection(
            "http://coolify.example",
            "mock-token",
            new CoolifyTargetProfileConfig(
                "http://coolify.example",
                "project",
                "staging",
                "env",
                "server",
                "destination",
                "runtime.example.test",
                "4.0.0",
                5,
                600,
                false,
                false,
                "8080",
                "/actuator/health",
                "8080"
            )
        );
        CoolifyApplicationSummary runtimeApplication = new CoolifyApplicationSummary(
            "runtime-uuid",
            "runtime-dep-123",
            "http://dep-123.runtime.example.test",
            "running:healthy",
            null,
            null,
            objectMapper.readTree("{\"uuid\":\"runtime-uuid\",\"status\":\"running:healthy\"}")
        );
        CoolifyApplicationSummary connectorApplication = new CoolifyApplicationSummary(
            "connector-uuid",
            "rest-connector-dep-123",
            "http://dep-123-connector.runtime.example.test",
            "running:healthy",
            null,
            null,
            objectMapper.readTree("{\"uuid\":\"connector-uuid\",\"status\":\"running:healthy\"}")
        );
        CoolifyApplicationSummary runnerApplication = new CoolifyApplicationSummary(
            "runner-uuid",
            "vectorization-runner-dep-123",
            null,
            "running:healthy",
            null,
            null,
            objectMapper.readTree("{\"uuid\":\"runner-uuid\",\"status\":\"running:healthy\"}")
        );

        when(targetProfileRepository.findById("dtp-coolify-staging")).thenReturn(Optional.of(profile));
        when(targetProfileResolver.requireConnection(profile)).thenReturn(connection);
        when(coolifyApiClient.health(connection)).thenReturn(objectMapper.readTree("{\"status\":\"ok\"}"));
        when(railwayProvisioningPlanService.buildPlan(any(), any())).thenReturn(railwayPlanWithConnectorRunnerAndSecrets());
        when(resourceHandleRepository.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            eq("dep-123"),
            eq("dtp-coolify-staging"),
            anyString()
        )).thenReturn(Optional.empty());
        when(coolifyApiClient.listApplications(connection)).thenReturn(List.of());
        when(coolifyApiClient.createPublicApplication(eq(connection), any()))
            .thenReturn("runtime-uuid")
            .thenReturn("connector-uuid")
            .thenReturn("runner-uuid");
        when(coolifyApiClient.getApplication(connection, "runtime-uuid")).thenReturn(Optional.of(runtimeApplication));
        when(coolifyApiClient.getApplication(connection, "connector-uuid")).thenReturn(Optional.of(connectorApplication));
        when(coolifyApiClient.getApplication(connection, "runner-uuid")).thenReturn(Optional.of(runnerApplication));
        when(coolifyApiClient.updateEnvironmentVariables(eq(connection), eq("runtime-uuid"), any())).thenReturn(12);
        when(coolifyApiClient.updateEnvironmentVariables(eq(connection), eq("connector-uuid"), any())).thenReturn(8);
        when(coolifyApiClient.updateEnvironmentVariables(eq(connection), eq("runner-uuid"), any())).thenReturn(10);
        when(coolifyApiClient.start(connection, "runtime-uuid", true, true))
            .thenReturn(new CoolifyActionResponse("Runtime deployment queued.", "runtime-deploy", objectMapper.createObjectNode()));
        when(coolifyApiClient.start(connection, "connector-uuid", true, true))
            .thenReturn(new CoolifyActionResponse("Connector deployment queued.", "connector-deploy", objectMapper.createObjectNode()));
        when(coolifyApiClient.start(connection, "runner-uuid", true, true))
            .thenReturn(new CoolifyActionResponse("Runner deployment queued.", "runner-deploy", objectMapper.createObjectNode()));
        stubFinishedDeployments(coolifyApiClient, connection);
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn("runtime-secret");
        when(platformSecretService.resolveSecret("MANAGED_VECTORIZATION_RUNNER_TOKEN_DEP_DEP_123")).thenReturn("runner-token");
        when(resourceHandleRepository.save(any(DeploymentProviderResourceHandleEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CoolifyDeploymentProvider provider = new CoolifyDeploymentProvider(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            targetProfileResolver,
            coolifyApiClient,
            platformSecretService,
            objectMapper
        );
        provider.setVectorizationRunnerProvisioningService(vectorizationRunnerProvisioningService);
        DeploymentReleaseEntity release = release();
        release.setSourceArtifactId(null);

        ProvisioningResult result = provider.provision(deployment(), version(), release, ProvisioningProgressTracker.noop());

        assertThat(result.detailsJson()).contains(
            "\"coolify\"",
            "\"vectorizationRunner\"",
            "\"vectorizationRunnerProviderResourceHandleId\"",
            "\"deploymentStatus\" : \"SUCCESS\""
        );
        ArgumentCaptor<CoolifyCreatePublicApplicationRequest> request =
            ArgumentCaptor.forClass(CoolifyCreatePublicApplicationRequest.class);
        verify(coolifyApiClient, times(3)).createPublicApplication(eq(connection), request.capture());
        assertThat(request.getAllValues())
            .extracting(CoolifyCreatePublicApplicationRequest::dockerfileLocation)
            .contains(
                "/ai-fabric-product/ai-fabric-vectorization-runner/deploy/railway/Dockerfile"
            );
        assertThat(request.getAllValues())
            .extracting(CoolifyCreatePublicApplicationRequest::portsExposes)
            .containsExactly("8097", "8082", "8099");
        assertThat(request.getAllValues())
            .extracting(CoolifyCreatePublicApplicationRequest::healthCheckPort)
            .containsExactly("8097", "8082", "8099");
        verify(vectorizationRunnerProvisioningService).ensureManagedRegistration(any());
        verifyNoInteractions(sourceArtifactService);
    }

    @Test
    void statusRedactsRawCoolifySecrets() throws Exception {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        DeploymentProviderResourceHandleRepository resourceHandleRepository = mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentSourceArtifactService sourceArtifactService = mock(DeploymentSourceArtifactService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        CoolifyTargetProfileResolver targetProfileResolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);

        DeploymentTargetProfileEntity profile = profile();
        CoolifyConnection connection = new CoolifyConnection(
            "http://coolify.example",
            "mock-token",
            new CoolifyTargetProfileConfig(
                "http://coolify.example",
                "project",
                "staging",
                "env",
                "server",
                "destination",
                "runtime.example.test",
                "4.0.0",
                5,
                600,
                false,
                false,
                "8080",
                "/actuator/health",
                "8080"
            )
        );
        CoolifyApplicationSummary application = new CoolifyApplicationSummary(
            "app-uuid",
            "runtime-dep-123",
            "http://dep-123.runtime.example.test",
            "running:healthy",
            null,
            null,
            objectMapper.readTree("""
                {
                  "uuid": "app-uuid",
                  "name": "runtime-dep-123",
                  "status": "running:healthy",
                  "fqdn": "http://dep-123.runtime.example.test",
                  "git_branch": "Platform-V8",
                  "manual_webhook_secret_github": "should-not-leak",
                  "destination": {
                    "uuid": "destination-uuid",
                    "network": "coolify",
                    "server": {
                      "uuid": "server-uuid",
                      "name": "localhost",
                      "settings": {
                        "sentinel_token": "should-not-leak-either"
                      }
                    }
                  }
                }
                """)
        );
        DeploymentProviderResourceHandleEntity handle = new DeploymentProviderResourceHandleEntity();
        handle.setId("dprh-123");
        handle.setProviderType(DeploymentProviderType.COOLIFY);
        handle.setProviderResourceUuid("app-uuid");
        handle.setTargetProfileId("dtp-coolify-staging");

        when(targetProfileRepository.findById("dtp-coolify-staging")).thenReturn(Optional.of(profile));
        when(targetProfileResolver.requireConnection(profile)).thenReturn(connection);
        when(coolifyApiClient.getApplication(connection, "app-uuid")).thenReturn(Optional.of(application));

        CoolifyDeploymentProvider provider = new CoolifyDeploymentProvider(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            targetProfileResolver,
            coolifyApiClient,
            objectMapper
        );

        var summary = provider.status(handle);

        assertThat(summary.status()).isEqualTo("RUNNING_HEALTHY");
        assertThat(summary.details().path("git_branch").asText()).isEqualTo("Platform-V8");
        assertThat(summary.details().path("destinationUuid").asText()).isEqualTo("destination-uuid");
        assertThat(summary.details().path("serverUuid").asText()).isEqualTo("server-uuid");
        assertThat(summary.details().toString())
            .doesNotContain("should-not-leak")
            .doesNotContain("manual_webhook_secret")
            .doesNotContain("sentinel_token");
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setName("Demo");
        deployment.setEnvironmentName("staging");
        deployment.setTemplateId("template");
        deployment.setStatus("VERSION_PUBLISHED");
        deployment.setCustomerId("customer");
        deployment.setTenantId("tenant");
        deployment.setCreatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        return deployment;
    }

    private DeploymentVersionEntity version() {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setDeploymentId("dep-123");
        version.setSourceDraftId("draft");
        version.setVersionLabel("v1");
        version.setStatus("PUBLISHED");
        version.setConfigHash("hash");
        version.setProviderConfigJson("{}");
        version.setSecurityConfigJson("{}");
        version.setEntityConfigJson("{}");
        version.setActionsConfigJson("{}");
        version.setRoutingConfigJson("{}");
        version.setPromptConfigJson("{}");
        version.setActionsArtifactYaml("");
        version.setEntityArtifactYaml("");
        version.setRoutingArtifactYaml("");
        version.setManifestJson("{}");
        version.setPublishedAt(Instant.parse("2026-05-01T00:00:00Z"));
        return version;
    }

    private DeploymentReleaseEntity release() {
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setTargetProfileId("dtp-coolify-staging");
        release.setProviderType(DeploymentProviderType.COOLIFY);
        release.setProvisioningTarget("COOLIFY");
        release.setSourceArtifactId("dsa-123");
        return release;
    }

    private DeploymentTargetProfileEntity profile() {
        DeploymentTargetProfileEntity profile = new DeploymentTargetProfileEntity();
        profile.setId("dtp-coolify-staging");
        profile.setName("Coolify staging");
        profile.setProviderType(DeploymentProviderType.COOLIFY);
        profile.setEnvironmentName("staging");
        profile.setActive(true);
        profile.setSourceStrategy("IMAGE_SOURCE");
        profile.setProviderConfigJson("{}");
        profile.setNetworkPolicyJson("{}");
        profile.setResourceDefaultsJson("{}");
        profile.setCreatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        profile.setUpdatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        return profile;
    }

    private DeploymentSourceArtifactEntity artifact() {
        DeploymentSourceArtifactEntity artifact = new DeploymentSourceArtifactEntity();
        artifact.setId("dsa-123");
        artifact.setServiceName("ai-fabric-runtime");
        artifact.setArtifactType("DOCKER_IMAGE");
        artifact.setImageRepository("ghcr.io/example/runtime");
        artifact.setImageTag("sha");
        artifact.setCreatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        return artifact;
    }

    private RailwayProvisioningPlanSummary railwayPlan() {
        return railwayPlanWithRuntimeEnv(List.of(
            new RailwayEnvVarSummary("AI_CONFIG_DEFAULT_FILE", "https://artifacts.example/entities.yaml")
        ));
    }

    private RailwayProvisioningPlanSummary railwayPlanWithRuntimeEnv(List<RailwayEnvVarSummary> runtimeEnv) {
        RailwayServicePlanSummary runtime = new RailwayServicePlanSummary(
            "runtime-dep-123",
            null,
            "ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile",
            "https://runtime-dep-123.placeholder.local",
            runtimeEnv
        );
        return new RailwayProvisioningPlanSummary(
            "dep-123",
            "Demo",
            "staging",
            "template",
            "ver-123",
            "v1",
            "hash",
            "api",
            "demo-staging",
            "mahmoudashraf/AI-Fabric-Framework",
            "Platform-V8",
            null,
            "REMOTE_CONFIG_BUNDLES",
            new RailwayArtifactUrlsSummary(
                "https://artifacts.example/actions.yaml",
                "https://artifacts.example/entities.yaml",
                "https://artifacts.example/routing.yaml",
                "https://artifacts.example/prompts.yaml",
                "https://artifacts.example/manifest.json"
            ),
            new RailwayProvisioningServicesSummary(runtime, null),
            List.of()
        );
    }

    private RailwayProvisioningPlanSummary railwayPlanWithConnectorAndSecrets() {
        RailwayServicePlanSummary runtime = new RailwayServicePlanSummary(
            "runtime-dep-123",
            null,
            "ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile",
            "https://runtime-dep-123.placeholder.local",
            List.of(
                new RailwayEnvVarSummary("AI_CONFIG_DEFAULT_FILE", "https://artifacts.example/entities.yaml"),
                new RailwayEnvVarSummary("ACTIONS_CONNECTOR_BASE_URL", "https://connector-dep-123.placeholder.local"),
                new RailwayEnvVarSummary(
                    "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY",
                    "${secret:AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY}"
                )
            )
        );
        RailwayServicePlanSummary connector = new RailwayServicePlanSummary(
            "rest-connector-dep-123",
            null,
            "ai-infrastructure-module/ai-infrastructure-generic-rest-connector/deploy/railway/Dockerfile",
            "https://connector-dep-123.placeholder.local",
            List.of(
                new RailwayEnvVarSummary("REST_CONNECTOR_ROUTING_CONFIG_LOCATION", "https://artifacts.example/routing.yaml"),
                new RailwayEnvVarSummary("REST_CONNECTOR_RUNTIME_PROXY_BASE_URL", "https://runtime-dep-123.placeholder.local"),
                new RailwayEnvVarSummary(
                    "REST_CONNECTOR_RUNTIME_PROXY_API_KEY",
                    "${secret:AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY}"
                )
            )
        );
        return new RailwayProvisioningPlanSummary(
            "dep-123",
            "Demo",
            "staging",
            "template",
            "ver-123",
            "v1",
            "hash",
            "api",
            "demo-staging",
            "mahmoudashraf/AI-Fabric-Framework",
            "Platform-V8",
            null,
            "REMOTE_CONFIG_BUNDLES",
            new RailwayArtifactUrlsSummary(
                "https://artifacts.example/actions.yaml",
                "https://artifacts.example/entities.yaml",
                "https://artifacts.example/routing.yaml",
                "https://artifacts.example/prompts.yaml",
                "https://artifacts.example/manifest.json"
            ),
            new RailwayProvisioningServicesSummary(runtime, connector),
            List.of()
        );
    }

    private RailwayProvisioningPlanSummary railwayPlanWithConnectorRunnerAndSecrets() {
        RailwayServicePlanSummary runtime = new RailwayServicePlanSummary(
            "runtime-dep-123",
            null,
            "ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile",
            "https://runtime-dep-123.placeholder.local",
            List.of(
                new RailwayEnvVarSummary("AI_CONFIG_DEFAULT_FILE", "https://artifacts.example/entities.yaml"),
                new RailwayEnvVarSummary("ACTIONS_CONNECTOR_BASE_URL", "https://connector-dep-123.placeholder.local"),
                new RailwayEnvVarSummary(
                    "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY",
                    "${secret:AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY}"
                )
            )
        );
        RailwayServicePlanSummary connector = new RailwayServicePlanSummary(
            "rest-connector-dep-123",
            null,
            "ai-infrastructure-module/ai-infrastructure-generic-rest-connector/deploy/railway/Dockerfile",
            "https://connector-dep-123.placeholder.local",
            List.of(
                new RailwayEnvVarSummary("REST_CONNECTOR_ROUTING_CONFIG_LOCATION", "https://artifacts.example/routing.yaml"),
                new RailwayEnvVarSummary("REST_CONNECTOR_RUNTIME_PROXY_BASE_URL", "https://runtime-dep-123.placeholder.local"),
                new RailwayEnvVarSummary(
                    "REST_CONNECTOR_RUNTIME_PROXY_API_KEY",
                    "${secret:AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY}"
                )
            )
        );
        RailwayServicePlanSummary runner = new RailwayServicePlanSummary(
            "vectorization-runner-dep-123",
            null,
            "ai-fabric-product/ai-fabric-vectorization-runner/deploy/railway/Dockerfile",
            null,
            List.of(
                new RailwayEnvVarSummary("AI_FABRIC_VECTORIZATION_RUNNER_PLATFORM_BASE_URL", "https://platform.example"),
                new RailwayEnvVarSummary(
                    "AI_FABRIC_VECTORIZATION_RUNNER_REGISTRATION_TOKEN",
                    "${secret:MANAGED_VECTORIZATION_RUNNER_TOKEN_DEP_DEP_123}"
                ),
                new RailwayEnvVarSummary("AI_FABRIC_VECTORIZATION_RUNNER_RUNNER_INSTANCE_ID", "vectorization-runner-dep-123"),
                new RailwayEnvVarSummary("AI_FABRIC_VECTORIZATION_RUNNER_DEPLOYMENT_ID", "dep-123")
            )
        );
        return new RailwayProvisioningPlanSummary(
            "dep-123",
            "Demo",
            "staging",
            "template",
            "ver-123",
            "v1",
            "hash",
            "api",
            "demo-staging",
            "mahmoudashraf/AI-Fabric-Framework",
            "Platform-V8",
            null,
            "REMOTE_CONFIG_BUNDLES",
            new RailwayArtifactUrlsSummary(
                "https://artifacts.example/actions.yaml",
                "https://artifacts.example/entities.yaml",
                "https://artifacts.example/routing.yaml",
                "https://artifacts.example/prompts.yaml",
                "https://artifacts.example/manifest.json"
            ),
            new RailwayProvisioningServicesSummary(runtime, connector, runner),
            List.of()
        );
    }

    private Map<String, CoolifyEnvVar> envByKey(List<CoolifyEnvVar> env) {
        return env.stream()
            .filter(item -> !item.preview())
            .collect(Collectors.toMap(CoolifyEnvVar::key, item -> item));
    }

    private void stubFinishedDeployments(CoolifyApiClient coolifyApiClient, CoolifyConnection connection) {
        when(coolifyApiClient.getDeployment(eq(connection), anyString()))
            .thenAnswer(invocation -> Optional.of(finishedDeployment(invocation.getArgument(1))));
    }

    private CoolifyDeploymentSummary finishedDeployment(String deploymentUuid) {
        return new CoolifyDeploymentSummary(
            deploymentUuid,
            "runtime-dep-123",
            "app-uuid",
            "finished",
            "HEAD",
            "test deploy",
            "2026-05-05T00:00:00Z",
            "2026-05-05T00:00:01Z",
            "2026-05-05T00:00:01Z",
            objectMapper.createObjectNode()
        );
    }
}
