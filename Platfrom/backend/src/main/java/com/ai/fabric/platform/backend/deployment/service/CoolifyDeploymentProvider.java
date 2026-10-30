package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderPreflightSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceActionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceLogsSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceStatusSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.AiStateStorageMode;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.ai.fabric.platform.backend.deployment.model.RailwayEnvVarSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayServicePlanSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationRunnerProvisioningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import com.ai.fabric.platform.backend.desk.DeskBindingProvisioner;
import com.ai.fabric.platform.backend.desk.DeskTargetConfigurationResolver;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
public class CoolifyDeploymentProvider implements DeploymentProvisioningProvider, CoolifyRetirementOwnership {
    @org.springframework.beans.factory.annotation.Autowired
    private com.ai.fabric.platform.backend.deployment.budget.ProviderUsageReleaseCoordinator providerUsageReleaseCoordinator;
    @Autowired
    private ReleaseArtifactResolver releaseArtifacts;

    private static final Logger log = LoggerFactory.getLogger(CoolifyDeploymentProvider.class);
    private static final Duration TRACKED_STEP_HEARTBEAT_INTERVAL = Duration.ofSeconds(10);
    private static final ScheduledExecutorService TRACKED_STEP_HEARTBEAT_EXECUTOR =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "coolify-provisioning-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    private static final String RESOURCE_KIND_APPLICATION = "APPLICATION";
    private static final String RESOURCE_KIND_CONNECTOR_APPLICATION = "CONNECTOR_APPLICATION";
    private static final String RESOURCE_KIND_VECTORIZATION_RUNNER_APPLICATION = "VECTORIZATION_RUNNER_APPLICATION";
    private static final String RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE = "RUNTIME_POSTGRES_DATABASE";
    private static final String SERVICE_ROLE_RUNTIME = "runtime";
    private static final String SERVICE_ROLE_CONNECTOR = "connector";
    private static final String SERVICE_ROLE_VECTORIZATION_RUNNER = "vectorization-runner";
    private static final String DEFAULT_RUNTIME_PORT = "8097";
    private static final String DEFAULT_CONNECTOR_PORT = "8082";
    private static final String DEFAULT_VECTORIZATION_RUNNER_PORT = "8099";
    private static final String DEFAULT_SERVICE_HEALTH_CHECK_PATH = "/actuator/health/liveness";
    private static final String RUNTIME_DATABASE_MODE_COOLIFY_POSTGRES = "COOLIFY_POSTGRES";
    private static final String DEFAULT_SERVICE_NAME = "ai-fabric-runtime";
    private static final String LOCAL_LUCENE_VECTOR_STRATEGY = "lucene";
    private static final String RUNTIME_DATA_MOUNT_PATH = "/app/data";
    private static final String DOCUMENT_SOURCE_MOUNT_PATH = "/app/document-sources";
    private static final String DOCUMENT_SOURCE_HOST_ROOT = "/srv/loomai/document-sources";
    private static final String DEFAULT_PROMOTION_CHANNEL = "staging";
    private static final Duration DEFAULT_DEPLOY_SETTLE_TIMEOUT = Duration.ofMinutes(6);
    private static final Duration DEFAULT_DEPLOY_SETTLE_POLL_INTERVAL = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REPLACEMENT_STOP_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration DEFAULT_REPLACEMENT_STOP_POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration DEFAULT_STALE_DELETE_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration DEFAULT_STALE_DELETE_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Set<String> CONNECTOR_DATABASE_BOOTSTRAP_ENV_KEYS = Set.of(
        "REST_CONNECTOR_BOOTSTRAP_JDBC_URL",
        "REST_CONNECTOR_BOOTSTRAP_JDBC_USERNAME",
        "REST_CONNECTOR_BOOTSTRAP_JDBC_PASSWORD",
        "REST_CONNECTOR_DATABASE_ROLE"
    );
    private static final SecureRandom SECRET_RANDOM = new SecureRandom();

    private final DeploymentTargetProfileRepository targetProfileRepository;
    private final DeploymentProviderResourceHandleRepository resourceHandleRepository;
    private final DeploymentSourceArtifactService sourceArtifactService;
    private final RailwayProvisioningPlanService railwayProvisioningPlanService;
    private final DeploymentManagedVectorProvisioningService deploymentManagedVectorProvisioningService;
    private final DeploymentManagedVectorResourceService deploymentManagedVectorResourceService;
    private final CoolifyTargetProfileResolver targetProfileResolver;
    private final CoolifyApiClient coolifyApiClient;
    private final PlatformSecretService platformSecretService;
    private final PlatformCustomerRepository platformCustomerRepository;
    private final DeploymentExecutionSecretService deploymentExecutionSecretService;
    private final ObjectMapper objectMapper;
    private VectorizationRunnerProvisioningService vectorizationRunnerProvisioningService;
    private PlatformManagedProductProvisioningService platformManagedProductProvisioningService;
    private DeploymentRuntimeAuthorityService deploymentRuntimeAuthorityService;
    private CustomerOfferDeploymentRepository customerOfferDeploymentRepository;
    private DeploymentAiStateStorageBindingService aiStateStorageBindings;
    @Autowired(required = false)
    private DeskBindingProvisioner deskBindingProvisioner;
    @Autowired
    private CoolifyResourceOwnership resourceOwnership;

    @Autowired
    public CoolifyDeploymentProvider(DeploymentTargetProfileRepository targetProfileRepository,
                                     DeploymentProviderResourceHandleRepository resourceHandleRepository,
                                     DeploymentSourceArtifactService sourceArtifactService,
                                     RailwayProvisioningPlanService railwayProvisioningPlanService,
                                     DeploymentManagedVectorProvisioningService deploymentManagedVectorProvisioningService,
                                     DeploymentManagedVectorResourceService deploymentManagedVectorResourceService,
                                     CoolifyTargetProfileResolver targetProfileResolver,
                                     CoolifyApiClient coolifyApiClient,
                                     PlatformSecretService platformSecretService,
                                     PlatformCustomerRepository platformCustomerRepository,
                                     DeploymentExecutionSecretService deploymentExecutionSecretService,
                                     ObjectMapper objectMapper) {
        this.targetProfileRepository = targetProfileRepository;
        this.resourceHandleRepository = resourceHandleRepository;
        this.sourceArtifactService = sourceArtifactService;
        this.railwayProvisioningPlanService = railwayProvisioningPlanService;
        this.deploymentManagedVectorProvisioningService = deploymentManagedVectorProvisioningService;
        this.deploymentManagedVectorResourceService = deploymentManagedVectorResourceService;
        this.targetProfileResolver = targetProfileResolver;
        this.coolifyApiClient = coolifyApiClient;
        this.platformSecretService = platformSecretService;
        this.platformCustomerRepository = platformCustomerRepository;
        this.deploymentExecutionSecretService = deploymentExecutionSecretService;
        this.objectMapper = objectMapper;
    }

    @Autowired(required = false)
    void setVectorizationRunnerProvisioningService(VectorizationRunnerProvisioningService vectorizationRunnerProvisioningService) {
        this.vectorizationRunnerProvisioningService = vectorizationRunnerProvisioningService;
    }

    @Autowired(required = false)
    void setPlatformManagedProductProvisioningService(PlatformManagedProductProvisioningService platformManagedProductProvisioningService) {
        this.platformManagedProductProvisioningService = platformManagedProductProvisioningService;
    }

    @Autowired(required = false)
    void setDeploymentRuntimeAuthorityService(DeploymentRuntimeAuthorityService deploymentRuntimeAuthorityService) {
        this.deploymentRuntimeAuthorityService = deploymentRuntimeAuthorityService;
    }

    @Autowired(required = false)
    void setCustomerOfferDeploymentRepository(CustomerOfferDeploymentRepository customerOfferDeploymentRepository) {
        this.customerOfferDeploymentRepository = customerOfferDeploymentRepository;
    }

    @Autowired(required = false)
    void setAiStateStorageBindings(DeploymentAiStateStorageBindingService aiStateStorageBindings) {
        this.aiStateStorageBindings = aiStateStorageBindings;
    }

    @Autowired
    private com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationResolver externalIntegrations;

    CoolifyDeploymentProvider(DeploymentTargetProfileRepository targetProfileRepository,
                              DeploymentProviderResourceHandleRepository resourceHandleRepository,
                              DeploymentSourceArtifactService sourceArtifactService,
                              RailwayProvisioningPlanService railwayProvisioningPlanService,
                              CoolifyTargetProfileResolver targetProfileResolver,
                              CoolifyApiClient coolifyApiClient,
                              ObjectMapper objectMapper) {
        this(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            null,
            null,
            targetProfileResolver,
            coolifyApiClient,
            null,
            null,
            null,
            objectMapper
        );
    }

    CoolifyDeploymentProvider(DeploymentTargetProfileRepository targetProfileRepository,
                              DeploymentProviderResourceHandleRepository resourceHandleRepository,
                              DeploymentSourceArtifactService sourceArtifactService,
                              RailwayProvisioningPlanService railwayProvisioningPlanService,
                              CoolifyTargetProfileResolver targetProfileResolver,
                              CoolifyApiClient coolifyApiClient,
                              PlatformSecretService platformSecretService,
                              ObjectMapper objectMapper) {
        this(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            null,
            null,
            targetProfileResolver,
            coolifyApiClient,
            platformSecretService,
            null,
            null,
            objectMapper
        );
    }

    CoolifyDeploymentProvider(DeploymentTargetProfileRepository targetProfileRepository,
                              DeploymentProviderResourceHandleRepository resourceHandleRepository,
                              DeploymentSourceArtifactService sourceArtifactService,
                              RailwayProvisioningPlanService railwayProvisioningPlanService,
                              CoolifyTargetProfileResolver targetProfileResolver,
                              CoolifyApiClient coolifyApiClient,
                              PlatformSecretService platformSecretService,
                              PlatformCustomerRepository platformCustomerRepository,
                              ObjectMapper objectMapper) {
        this(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            null,
            null,
            targetProfileResolver,
            coolifyApiClient,
            platformSecretService,
            platformCustomerRepository,
            null,
            objectMapper
        );
    }

    @Override
    public DeploymentProviderType providerType() {
        return DeploymentProviderType.COOLIFY;
    }

    @Override
    public DeploymentProviderPreflightSummary preflight(DeploymentTargetProfileEntity targetProfile) {
        return targetProfileResolver.preflight(targetProfile);
    }

    @Override
    public ProvisioningResult provision(DeploymentEntity deployment,
                                        DeploymentVersionEntity version,
                                        DeploymentReleaseEntity release,
                                        ProvisioningProgressTracker progressTracker) {
        DeploymentTargetProfileEntity profile = requireActiveProfile(release.getTargetProfileId());
        CoolifyRuntimeDatabaseBinding externalAiState = validateAiStateStorageBeforeEffects(deployment, version, release, profile);
        boolean replacingRuntime = resourceHandleRepository
            .findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
                deployment.getId(), profile.getId(), RESOURCE_KIND_APPLICATION
            )
            .filter(handle -> StringUtils.hasText(handle.getProviderResourceUuid()))
            .isPresent();
        CoolifyConnection connection = tracked(
            progressTracker,
            "coolify_preflight",
            "Verify Coolify target profile credentials before application reconciliation.",
            () -> {
                CoolifyConnection resolved = targetProfileResolver.requireConnection(profile);
                coolifyApiClient.version(resolved);
                return resolved;
            }
        );
        JsonNode resourceDefaults = readJson(profile.getResourceDefaultsJson());
        boolean autogenerateDomain = booleanValue(
            resourceDefaults,
            "autogenerateDomain",
            connection.config().autogenerateDomain()
        );
        String connectorDomain = resolveDomain(
            deployment,
            resourceDefaults,
            connection.config(),
            autogenerateDomain,
            SERVICE_ROLE_CONNECTOR
        );
        validateExpectedWebhookConnectorDomain(version, connectorDomain);
        boolean deskEnabled = DeskTargetConfigurationResolver.usesDesk(readJson(version.getBehaviorConfigJson()));
        if (deskEnabled) {
            if (deskBindingProvisioner == null) throw new IllegalStateException("Desk target provisioner is unavailable.");
            tracked(progressTracker, "prepare_desk_target_binding", "Prepare the selected target's Desk binding and credentials.", () -> {
                deskBindingProvisioner.prepareForApply(deployment, version, release, profile);
                return null;
            });
        }
        if (deploymentExecutionSecretService != null) {
            tracked(
                progressTracker,
                "ensure_deployment_execution_secrets",
                "Create or reuse stable deployment-scoped execution secrets required by the selected behavior.",
                () -> {
                    deploymentExecutionSecretService.ensureRequiredSecrets(deployment, version);
                    return null;
                }
            );
        }
        ManagedVectorProvisioningResult managedVectorProvisioningResult = ensureManagedVectorProvisioned(
            deployment,
            version,
            release,
            progressTracker
        );
        ensureManagedProductDependencies(profile, version, progressTracker);
        CoolifyProvisioningSource source = tracked(
            progressTracker,
            "resolve_coolify_source",
            "Resolve Coolify application source from target profile strategy.",
            () -> resolveProvisioningSource(
                profile,
                resourceDefaults,
                deployment,
                version,
                release,
                managedVectorProvisioningResult.effectiveProviderConfig()
            )
        );
        CoolifyResourceScope resourceScope = tracked(
            progressTracker,
            "resolve_coolify_customer_scope",
            "Resolve the Coolify project and environment that should group this customer's resources.",
            () -> resolveResourceScope(connection, deployment, resourceDefaults)
        );
        RailwayServicePlanSummary vectorizationRunnerPlan = source.vectorizationRunnerPlan();
        if (vectorizationRunnerPlan != null && vectorizationRunnerProvisioningService != null) {
            tracked(
                progressTracker,
                "provision_vectorization_runner_registration",
                "Create or reconcile vectorization runner registration material before runner deployment.",
                () -> vectorizationRunnerProvisioningService.ensureManagedRegistration(deployment)
            );
        } else if (vectorizationRunnerPlan == null && vectorizationRunnerProvisioningService != null) {
            vectorizationRunnerProvisioningService.clearManagedRegistrationSecret(deployment.getId());
        }

        String runtimePortsExposes = servicePortsExposes(resourceDefaults, connection.config(), SERVICE_ROLE_RUNTIME);
        String runtimeHealthCheckPath = serviceHealthCheckPath(resourceDefaults, connection.config(), SERVICE_ROLE_RUNTIME);
        String runtimeHealthCheckPort = serviceHealthCheckPort(resourceDefaults, connection.config(), SERVICE_ROLE_RUNTIME);
        CoolifyApplicationRuntimeSettings runtimeSettings = serviceRuntimeSettings(resourceDefaults, SERVICE_ROLE_RUNTIME);
        String connectorPortsExposes = servicePortsExposes(resourceDefaults, connection.config(), SERVICE_ROLE_CONNECTOR);
        String connectorHealthCheckPath = serviceHealthCheckPath(resourceDefaults, connection.config(), SERVICE_ROLE_CONNECTOR);
        String connectorHealthCheckPort = serviceHealthCheckPort(resourceDefaults, connection.config(), SERVICE_ROLE_CONNECTOR);
        CoolifyApplicationRuntimeSettings connectorSettings = serviceRuntimeSettings(resourceDefaults, SERVICE_ROLE_CONNECTOR);
        String vectorizationRunnerPortsExposes = servicePortsExposes(
            resourceDefaults,
            connection.config(),
            SERVICE_ROLE_VECTORIZATION_RUNNER
        );
        String vectorizationRunnerHealthCheckPath = serviceHealthCheckPath(
            resourceDefaults,
            connection.config(),
            SERVICE_ROLE_VECTORIZATION_RUNNER
        );
        String vectorizationRunnerHealthCheckPort = serviceHealthCheckPort(
            resourceDefaults,
            connection.config(),
            SERVICE_ROLE_VECTORIZATION_RUNNER
        );
        CoolifyApplicationRuntimeSettings vectorizationRunnerSettings = serviceRuntimeSettings(
            resourceDefaults,
            SERVICE_ROLE_VECTORIZATION_RUNNER
        );
        boolean healthCheckEnabled = booleanValue(resourceDefaults, "healthCheckEnabled", StringUtils.hasText(runtimeHealthCheckPath));
        String runtimeAppName = resolveApplicationName(deployment, resourceDefaults, source, source.runtimePlan(), SERVICE_ROLE_RUNTIME);
        String runtimeDomain = resolveDomain(deployment, resourceDefaults, connection.config(), autogenerateDomain, SERVICE_ROLE_RUNTIME);

        CoolifyApplicationSummary runtimeApplication = tracked(
            progressTracker,
            "reconcile_coolify_runtime_application",
            source.gitSource()
                ? "Create or update the Coolify public Git runtime application."
                : "Create or update the Coolify Docker-image runtime application.",
            () -> reconcileApplication(
                connection,
                resourceScope,
                deployment,
                profile,
                RESOURCE_KIND_APPLICATION,
                runtimeAppName,
                source,
                source.runtimePlan(),
                source.baseDirectory(),
                source.dockerfileLocation(),
                runtimePortsExposes,
                healthCheckEnabled,
                runtimeHealthCheckPath,
                runtimeHealthCheckPort,
                runtimeSettings,
                autogenerateDomain,
                runtimeDomain
            )
        );
        DeploymentProviderResourceHandleEntity provisionalRuntimeHandle = tracked(
            progressTracker,
            "record_coolify_runtime_handle_provisional",
            "Persist the Coolify runtime resource handle before mutable configuration begins.",
            () -> upsertRuntimeHandleProvisional(
                deployment,
                release,
                profile,
                resourceScope,
                connection.config(),
                RESOURCE_KIND_APPLICATION,
                runtimeApplication,
                source,
                SERVICE_ROLE_RUNTIME,
                0,
                null
            )
        );
        tracked(
            progressTracker,
            "reconcile_coolify_document_source_storage",
            "Create or verify the optional Coolify mounted document source directory for non-production demos.",
            () -> {
                reconcileMountedDocumentSourceStorage(
                    connection,
                    deployment,
                    profile,
                    runtimeApplication,
                    source.runtimePlan()
                );
                return null;
            }
        );
        boolean localLuceneRuntime = usesLocalLuceneVectorStore(source.runtimePlan());
        if (localLuceneRuntime) {
            tracked(
                progressTracker,
                "reconcile_coolify_runtime_data_storage",
                "Create or verify deployment-owned persistent runtime data storage for the local Lucene vector index.",
                () -> {
                    reconcileLocalLuceneRuntimeStorage(
                        connection,
                        deployment,
                        profile,
                        runtimeApplication,
                        source.runtimePlan()
                    );
                    return null;
                }
            );
        }

        CoolifyApplicationSummary connectorApplication = null;
        DeploymentProviderResourceHandleEntity provisionalConnectorHandle = null;
        if (source.connectorPlan() != null) {
            String connectorAppName = resolveApplicationName(
                deployment,
                resourceDefaults,
                source,
                source.connectorPlan(),
                SERVICE_ROLE_CONNECTOR
            );
            String connectorBaseDirectory = connectorBaseDirectory(source.connectorPlan(), resourceDefaults);
            String connectorDockerfileLocation = connectorDockerfileLocation(source.connectorPlan(), resourceDefaults);
            CoolifyApplicationSummary reconciledConnectorApplication = tracked(
                progressTracker,
                "reconcile_coolify_connector_application",
                "Create or update the Coolify public Git REST connector application.",
                () -> reconcileApplication(
                    connection,
                    resourceScope,
                    deployment,
                    profile,
                    RESOURCE_KIND_CONNECTOR_APPLICATION,
                    connectorAppName,
                    source,
                    source.connectorPlan(),
                    connectorBaseDirectory,
                    connectorDockerfileLocation,
                    connectorPortsExposes,
                    healthCheckEnabled,
                    connectorHealthCheckPath,
                    connectorHealthCheckPort,
                    connectorSettings,
                    autogenerateDomain,
                    connectorDomain
                )
            );
            connectorApplication = reconciledConnectorApplication;
            provisionalConnectorHandle = tracked(
                progressTracker,
                "record_coolify_connector_handle_provisional",
                "Persist the Coolify REST connector resource handle before mutable configuration begins.",
                () -> upsertHandle(
                    deployment,
                    release,
                    profile,
                    resourceScope,
                    connection.config(),
                    RESOURCE_KIND_CONNECTOR_APPLICATION,
                    reconciledConnectorApplication,
                    source,
                    SERVICE_ROLE_CONNECTOR,
                    0,
                    null
                )
            );
        }

        CoolifyApplicationSummary vectorizationRunnerApplication = null;
        DeploymentProviderResourceHandleEntity provisionalVectorizationRunnerHandle = null;
        if (vectorizationRunnerPlan != null) {
            String runnerAppName = resolveApplicationName(
                deployment,
                resourceDefaults,
                source,
                vectorizationRunnerPlan,
                SERVICE_ROLE_VECTORIZATION_RUNNER
            );
            String runnerBaseDirectory = serviceBaseDirectory(
                vectorizationRunnerPlan,
                resourceDefaults,
                "vectorizationRunnerBaseDirectory"
            );
            String runnerDockerfileLocation = serviceDockerfileLocation(
                vectorizationRunnerPlan,
                resourceDefaults,
                "vectorizationRunnerDockerfilePath"
            );
            String runnerDomain = resolveDomain(
                deployment,
                resourceDefaults,
                connection.config(),
                autogenerateDomain,
                SERVICE_ROLE_VECTORIZATION_RUNNER
            );
            CoolifyApplicationSummary reconciledVectorizationRunnerApplication = tracked(
                progressTracker,
                "reconcile_coolify_vectorization_runner_application",
                "Create or update the Coolify public Git vectorization runner application.",
                () -> reconcileApplication(
                    connection,
                    resourceScope,
                    deployment,
                    profile,
                    RESOURCE_KIND_VECTORIZATION_RUNNER_APPLICATION,
                    runnerAppName,
                    source,
                    vectorizationRunnerPlan,
                    runnerBaseDirectory,
                    runnerDockerfileLocation,
                    vectorizationRunnerPortsExposes,
                    healthCheckEnabled,
                    vectorizationRunnerHealthCheckPath,
                    vectorizationRunnerHealthCheckPort,
                    vectorizationRunnerSettings,
                    autogenerateDomain,
                    runnerDomain
                )
            );
            vectorizationRunnerApplication = reconciledVectorizationRunnerApplication;
            provisionalVectorizationRunnerHandle = tracked(
                progressTracker,
                "record_coolify_vectorization_runner_handle_provisional",
                "Persist the Coolify vectorization runner resource handle before mutable configuration begins.",
                () -> upsertHandle(
                    deployment,
                    release,
                    profile,
                    resourceScope,
                    connection.config(),
                    RESOURCE_KIND_VECTORIZATION_RUNNER_APPLICATION,
                    reconciledVectorizationRunnerApplication,
                    source,
                    SERVICE_ROLE_VECTORIZATION_RUNNER,
                    0,
                    null
                )
            );
        }

        String runtimeBaseUrl = normalizeRuntimeBaseUrl(runtimeApplication.fqdn());
        String connectorBaseUrl = connectorApplication == null
            ? runtimeBaseUrl
            : normalizeRuntimeBaseUrl(connectorApplication.fqdn());
        if (deskEnabled) {
            if (connectorApplication == null) throw new IllegalStateException("Desk requires a deployment connector.");
            tracked(progressTracker, "register_desk_target_callback", "Register Desk with this target's resolved connector callback.", () -> {
                deskBindingProvisioner.registerCallback(deployment, profile, connectorBaseUrl);
                return null;
            });
        }
        String connectorRuntimeBaseUrl;
        String runtimeConnectorBaseUrl = connectorBaseUrl;
        if (requiresDurableConnectorPersistence(version) && connectorApplication != null) {
            String runtimeInternalName = integrationInternalName(deployment, SERVICE_ROLE_RUNTIME);
            String connectorInternalName = integrationInternalName(deployment, SERVICE_ROLE_CONNECTOR);
            CoolifyApplicationSummary finalConnectorApplication = connectorApplication;
            tracked(
                progressTracker,
                "configure_coolify_runtime_private_network",
                "Configure the deployment runtime with a stable private Coolify network identity.",
                () -> {
                    coolifyApiClient.configurePrivateApplicationNetwork(
                        connection,
                        runtimeApplication.uuid(),
                        runtimeInternalName
                    );
                    return null;
                }
            );
            tracked(
                progressTracker,
                "configure_coolify_connector_private_network",
                "Attach the deployment connector to the private Coolify application network.",
                () -> {
                    coolifyApiClient.configurePrivateApplicationNetwork(
                        connection,
                        finalConnectorApplication.uuid(),
                        connectorInternalName
                    );
                    return null;
                }
            );
            connectorRuntimeBaseUrl = "http://" + runtimeInternalName + ":" + internalServicePort(
                runtimePortsExposes,
                runtimeHealthCheckPort,
                DEFAULT_RUNTIME_PORT
            );
            runtimeConnectorBaseUrl = "http://" + connectorInternalName + ":" + internalServicePort(
                connectorPortsExposes,
                connectorHealthCheckPort,
                DEFAULT_CONNECTOR_PORT
            );
        } else {
            connectorRuntimeBaseUrl = runtimeBaseUrl;
        }
        String resolvedRuntimeConnectorBaseUrl = runtimeConnectorBaseUrl;
        CoolifyRuntimeDatabaseBinding runtimeDatabaseBinding = tracked(
            progressTracker,
            "reconcile_coolify_runtime_database",
            "Resolve the explicitly selected durable AI-state store without provisioning customer-owned storage.",
            () -> externalAiState != null ? externalAiState
                : reconcileRuntimeDatabaseBinding(connection, resourceScope, deployment, profile, resourceDefaults,
                    readJson(version.getProviderConfigJson()))
        );
        if (requiresDurableConnectorPersistence(version) && runtimeDatabaseBinding == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Durable connector capabilities require an explicitly configured PostgreSQL AI-state store."
            );
        }
        DeploymentProviderResourceHandleEntity provisionalRuntimeDatabaseHandle = runtimeDatabaseBinding == null || !runtimeDatabaseBinding.managed() ? null : tracked(
            progressTracker,
            "record_coolify_runtime_database_handle_provisional",
            "Persist the Coolify runtime database handle before runtime configuration begins.",
            () -> upsertDatabaseHandle(
                deployment,
                release,
                profile,
                resourceScope,
                connection.config(),
                runtimeDatabaseBinding
            )
        );

        List<CoolifyEnvVar> runtimeEnvironment = buildEnvironment(
            deployment,
            version,
            release,
            profile,
            resourceScope,
            source,
            source.runtimePlan(),
            SERVICE_ROLE_RUNTIME,
            runtimeBaseUrl,
            resolvedRuntimeConnectorBaseUrl,
            connectorBaseUrl,
            runtimeDatabaseBinding
        );
        int runtimeEnvCount = tracked(
            progressTracker,
            "configure_coolify_runtime_environment",
            "Update runtime environment variables in Coolify.",
            () -> coolifyApiClient.updateEnvironmentVariables(
                connection,
                runtimeApplication.uuid(),
                runtimeEnvironment
            )
        );

        int connectorEnvCount = 0;
        if (connectorApplication != null) {
            CoolifyApplicationSummary finalConnectorApplication = connectorApplication;
            if (externalAiState != null) {
                tracked(progressTracker, "remove_customer_database_bootstrap_credentials",
                    "Remove privileged bootstrap and role-creation configuration from customer PostgreSQL connectors.",
                    () -> coolifyApiClient.deleteEnvironmentVariablesByKey(connection, finalConnectorApplication.uuid(),
                        CONNECTOR_DATABASE_BOOTSTRAP_ENV_KEYS));
            }
            connectorEnvCount = tracked(
                progressTracker,
                "configure_coolify_connector_environment",
                "Update REST connector environment variables in Coolify.",
                () -> coolifyApiClient.updateEnvironmentVariables(
                    connection,
                    finalConnectorApplication.uuid(),
                    buildEnvironment(
                        deployment,
                        version,
                        release,
                        profile,
                        resourceScope,
                        source,
                        source.connectorPlan(),
                        SERVICE_ROLE_CONNECTOR,
                        connectorRuntimeBaseUrl,
                        connectorBaseUrl,
                        connectorBaseUrl,
                        runtimeDatabaseBinding
                    )
                )
            );
        }

        int vectorizationRunnerEnvCount = 0;
        if (vectorizationRunnerApplication != null) {
            CoolifyApplicationSummary finalVectorizationRunnerApplication = vectorizationRunnerApplication;
            vectorizationRunnerEnvCount = tracked(
                progressTracker,
                "configure_coolify_vectorization_runner_environment",
                "Update vectorization runner environment variables in Coolify.",
                () -> coolifyApiClient.updateEnvironmentVariables(
                    connection,
                    finalVectorizationRunnerApplication.uuid(),
                    buildEnvironment(
                        deployment,
                        version,
                        release,
                        profile,
                        resourceScope,
                        source,
                        vectorizationRunnerPlan,
                        SERVICE_ROLE_VECTORIZATION_RUNNER,
                        runtimeBaseUrl,
                        connectorBaseUrl,
                        connectorBaseUrl,
                        null
                    )
                )
            );
        }

        CoolifyActionResponse connectorDeployResponse = null;
        CoolifyApplicationSummary observedConnector = null;
        CoolifyActionResponse vectorizationRunnerDeployResponse = null;
        CoolifyApplicationSummary observedVectorizationRunner = null;

        if (replacingRuntime || localLuceneRuntime) {
            stopBeforeColdReplacement(connection, runtimeApplication, connectorApplication, vectorizationRunnerApplication,
                resourceDefaults, progressTracker);
        }

        CoolifyActionResponse runtimeDeployResponse = tracked(
            progressTracker,
            "trigger_coolify_runtime_deploy",
            "Trigger Coolify deployment for the runtime application.",
            () -> coolifyApiClient.start(connection, runtimeApplication.uuid(), true, true)
        );

        CoolifyApplicationSummary observedRuntime = tracked(
            progressTracker,
            "wait_for_coolify_runtime",
            "Wait for Coolify to report the application running before Platform verification.",
            () -> waitForApplicationReady(
                connection,
                runtimeApplication.uuid(),
                resourceDefaults,
                runtimeApplication,
                runtimeDeployResponse,
                progressTracker
            )
        );

        if (source.sourceArtifact() != null) requireImmutableRuntimeImage(source.sourceArtifact(), observedRuntime);

        if (connectorApplication != null) {
            CoolifyApplicationSummary finalConnectorApplication = connectorApplication;
            connectorDeployResponse = tracked(
                progressTracker,
                "trigger_coolify_connector_deploy",
                "Trigger Coolify deployment for the REST connector application after the runtime generation is active.",
                () -> coolifyApiClient.start(connection, finalConnectorApplication.uuid(), true, true)
            );
            CoolifyActionResponse finalConnectorDeployResponse = connectorDeployResponse;
            observedConnector = tracked(
                progressTracker,
                "wait_for_coolify_connector",
                "Wait for Coolify to report the REST connector application running.",
                () -> waitForApplicationReady(
                    connection,
                    finalConnectorApplication.uuid(),
                    resourceDefaults,
                    finalConnectorApplication,
                    finalConnectorDeployResponse,
                    progressTracker
                )
            );
            if (requiresDurableConnectorPersistence(version) && runtimeDatabaseBinding != null && runtimeDatabaseBinding.managed()) {
                int removedBootstrapVariables = tracked(
                    progressTracker,
                    "remove_coolify_connector_database_bootstrap_credentials",
                    "Remove one-time database bootstrap credentials after the restricted connector role is ready.",
                    () -> coolifyApiClient.deleteEnvironmentVariablesByKey(
                        connection,
                        finalConnectorApplication.uuid(),
                        CONNECTOR_DATABASE_BOOTSTRAP_ENV_KEYS
                    )
                );
                if (removedBootstrapVariables > 0) {
                    connectorDeployResponse = tracked(
                        progressTracker,
                        "redeploy_coolify_connector_without_database_bootstrap_credentials",
                        "Redeploy the connector using only its restricted database role.",
                        () -> coolifyApiClient.start(connection, finalConnectorApplication.uuid(), true, true)
                    );
                    CoolifyActionResponse scrubbedConnectorDeployResponse = connectorDeployResponse;
                    observedConnector = tracked(
                        progressTracker,
                        "wait_for_coolify_connector_after_database_credential_scrub",
                        "Verify the connector starts after privileged database credentials are removed.",
                        () -> waitForApplicationReady(
                            connection,
                            finalConnectorApplication.uuid(),
                            resourceDefaults,
                            finalConnectorApplication,
                            scrubbedConnectorDeployResponse,
                            progressTracker
                        )
                    );
                }
            }
        }

        if (vectorizationRunnerApplication != null) {
            CoolifyApplicationSummary finalVectorizationRunnerApplication = vectorizationRunnerApplication;
            vectorizationRunnerDeployResponse = tracked(
                progressTracker,
                "trigger_coolify_vectorization_runner_deploy",
                "Trigger Coolify deployment for the vectorization runner after the runtime generation is active.",
                () -> coolifyApiClient.start(connection, finalVectorizationRunnerApplication.uuid(), true, true)
            );
            CoolifyActionResponse finalVectorizationRunnerDeployResponse = vectorizationRunnerDeployResponse;
            observedVectorizationRunner = tracked(
                progressTracker,
                "wait_for_coolify_vectorization_runner",
                "Wait for Coolify to report the vectorization runner application running.",
                () -> waitForApplicationReady(
                    connection,
                    finalVectorizationRunnerApplication.uuid(),
                    resourceDefaults,
                    finalVectorizationRunnerApplication,
                    finalVectorizationRunnerDeployResponse,
                    progressTracker
                )
            );
        }

        CoolifyApplicationSummary finalObservedConnector = observedConnector;
        int finalConnectorEnvCount = connectorEnvCount;
        CoolifyActionResponse finalConnectorDeployResponse = connectorDeployResponse;
        CoolifyApplicationSummary finalObservedVectorizationRunner = observedVectorizationRunner;
        int finalVectorizationRunnerEnvCount = vectorizationRunnerEnvCount;
        CoolifyActionResponse finalVectorizationRunnerDeployResponse = vectorizationRunnerDeployResponse;
        DeploymentProviderResourceHandleEntity connectorHandleToFinalize = provisionalConnectorHandle;
        DeploymentProviderResourceHandleEntity connectorHandle = connectorApplication == null ? null : tracked(
            progressTracker,
            "record_coolify_connector_handle",
            "Persist Coolify REST connector resource handle for operator actions.",
            () -> persistApplicationHandle(
                connectorHandleToFinalize,
                finalObservedConnector,
                deployment,
                release,
                profile,
                resourceScope,
                connection.config(),
                RESOURCE_KIND_CONNECTOR_APPLICATION,
                source,
                SERVICE_ROLE_CONNECTOR,
                finalConnectorEnvCount,
                finalConnectorDeployResponse
            )
        );
        DeploymentProviderResourceHandleEntity vectorizationRunnerHandleToFinalize = provisionalVectorizationRunnerHandle;
        DeploymentProviderResourceHandleEntity vectorizationRunnerHandle = vectorizationRunnerApplication == null ? null : tracked(
            progressTracker,
            "record_coolify_vectorization_runner_handle",
            "Persist Coolify vectorization runner resource handle for operator actions.",
            () -> persistApplicationHandle(
                vectorizationRunnerHandleToFinalize,
                finalObservedVectorizationRunner,
                deployment,
                release,
                profile,
                resourceScope,
                connection.config(),
                RESOURCE_KIND_VECTORIZATION_RUNNER_APPLICATION,
                source,
                SERVICE_ROLE_VECTORIZATION_RUNNER,
                finalVectorizationRunnerEnvCount,
                finalVectorizationRunnerDeployResponse
            )
        );
        DeploymentProviderResourceHandleEntity runtimeHandle = tracked(
            progressTracker,
            "record_coolify_runtime_handle",
            "Persist Coolify runtime resource handle for operator actions.",
            () -> persistApplicationHandle(
                provisionalRuntimeHandle,
                observedRuntime,
                deployment,
                release,
                profile,
                resourceScope,
                connection.config(),
                RESOURCE_KIND_APPLICATION,
                source,
                SERVICE_ROLE_RUNTIME,
                runtimeEnvCount,
                runtimeDeployResponse
            )
        );
        DeploymentProviderResourceHandleEntity runtimeDatabaseHandle = runtimeDatabaseBinding == null || !runtimeDatabaseBinding.managed() ? null : tracked(
            progressTracker,
            "record_coolify_runtime_database_handle",
            "Persist Coolify runtime database resource handle for operator actions.",
            () -> persistDatabaseHandle(
                provisionalRuntimeDatabaseHandle,
                runtimeDatabaseBinding
            )
        );
        String details = buildProvisioningDetails(
            profile,
            runtimeHandle,
            runtimeDatabaseHandle,
            connectorHandle,
            observedRuntime,
            finalObservedConnector,
            vectorizationRunnerHandle,
            finalObservedVectorizationRunner,
            resourceScope,
            source,
            runtimeEnvCount,
            finalConnectorEnvCount,
            finalVectorizationRunnerEnvCount,
            runtimeDeployResponse,
            finalConnectorDeployResponse,
            finalVectorizationRunnerDeployResponse,
            runtimeDatabaseBinding,
            managedVectorProvisioningResult
        );
        progressTracker.mergeDetails(details);

        return new ProvisioningResult(
            applicationsReady(observedRuntime, finalObservedConnector, finalObservedVectorizationRunner) ? "ACTIVE" : "DEPLOY_REQUESTED",
            DeploymentProviderType.COOLIFY.legacyTarget(),
            runtimeBaseUrl,
            connectorBaseUrl,
            details
        );
    }

    void reconcileMountedDocumentSourceStorage(
        CoolifyConnection connection,
        DeploymentEntity deployment,
        DeploymentTargetProfileEntity profile,
        CoolifyApplicationSummary runtimeApplication,
        RailwayServicePlanSummary runtimePlan
    ) {
        if (!hasEnvironmentValue(
            runtimePlan,
            "LOOMAI_DOCUMENTS_CONNECTOR_TYPE",
            DeploymentDocumentStorageBindingService.MOUNTED_CONNECTOR
        )) {
            return;
        }
        if ("production".equalsIgnoreCase(profile.getEnvironmentName())
            || "production".equalsIgnoreCase(deployment.getEnvironmentName())) {
            throw new IllegalStateException("Mounted document source storage is not supported for production deployments.");
        }
        String deploymentSegment = safePathSegment(deployment.getId());
        String profileSegment = safePathSegment(profile.getId());
        coolifyApiClient.reconcilePersistentDirectoryStorage(
            connection,
            runtimeApplication.uuid(),
            "loomai-documents-" + deploymentSegment + "-" + profileSegment,
            DOCUMENT_SOURCE_HOST_ROOT + "/" + deploymentSegment + "/" + profileSegment,
            DOCUMENT_SOURCE_MOUNT_PATH
        );
    }

    void reconcileLocalLuceneRuntimeStorage(
        CoolifyConnection connection,
        DeploymentEntity deployment,
        DeploymentTargetProfileEntity profile,
        CoolifyApplicationSummary runtimeApplication,
        RailwayServicePlanSummary runtimePlan
    ) {
        if (!usesLocalLuceneVectorStore(runtimePlan)) {
            return;
        }
        String volumeName = normalizeName(
            "loomai-runtime-data-" + safePathSegment(deployment.getId()) + "-" + safePathSegment(profile.getId())
        );
        coolifyApiClient.reconcilePersistentVolumeStorage(
            connection,
            runtimeApplication.uuid(),
            volumeName,
            RUNTIME_DATA_MOUNT_PATH
        );
    }

    void stopBeforeColdReplacement(CoolifyConnection connection, CoolifyApplicationSummary runtime,
        CoolifyApplicationSummary connector, CoolifyApplicationSummary runner, JsonNode defaults,
        ProvisioningProgressTracker tracker) {
        if (connector != null) tracked(tracker, "cold_stop_connector", "Stop and confirm the owned connector before cold replacement.",
            () -> stopApplicationBeforeSingleWriterReplacement(connection, connector, defaults, tracker));
        if (runner != null) tracked(tracker, "cold_stop_indexing_runner", "Stop and confirm the owned indexing runner before cold replacement.",
            () -> stopApplicationBeforeSingleWriterReplacement(connection, runner, defaults, tracker));
        tracked(tracker, "cold_stop_previous_runtime", "Stop and confirm the owned previous runtime before cold replacement.",
            () -> stopApplicationBeforeSingleWriterReplacement(connection, runtime, defaults, tracker));
    }

    String stopApplicationBeforeSingleWriterReplacement(
        CoolifyConnection connection,
        CoolifyApplicationSummary application,
        JsonNode resourceDefaults,
        ProvisioningProgressTracker progressTracker
    ) {
        CoolifyApplicationSummary current = observeCoolifyApplication(connection, application.uuid()).orElse(application);
        if (!applicationHasActiveContainer(current)) {
            if (!applicationStopped(current)) {
                throw new IllegalStateException(
                    "Coolify did not authoritatively report application " + application.uuid() + " stopped."
                );
            }
            return providerStopEvidence(current, application.uuid());
        }
        // Coolify cleanup may prune the now-unused named volume before the replacement starts.
        coolifyApiClient.stop(connection, application.uuid(), false);

        Duration timeout = durationSeconds(
            resourceDefaults,
            "replacementStopTimeoutSeconds",
            DEFAULT_REPLACEMENT_STOP_TIMEOUT
        );
        Duration pollInterval = durationSeconds(
            resourceDefaults,
            "replacementStopPollSeconds",
            DEFAULT_REPLACEMENT_STOP_POLL_INTERVAL
        );
        Instant deadline = Instant.now().plus(timeout);
        Optional<CoolifyApplicationSummary> observed = observeCoolifyApplication(connection, application.uuid());
        while ((observed.isEmpty() || !applicationStopped(observed.get())) && Instant.now().isBefore(deadline)) {
            progressTracker.heartbeat();
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                    "Interrupted while waiting for Coolify application " + application.uuid() + " to stop.",
                    ex
                );
            }
            observed = observeCoolifyApplication(connection, application.uuid());
        }
        if (observed.isEmpty() || !applicationStopped(observed.get())) {
            throw new IllegalStateException(
                "Timed out waiting for Coolify application " + application.uuid()
                    + " to stop before single-writer replacement."
            );
        }
        return providerStopEvidence(observed.get(), application.uuid());
    }

    private String providerStopEvidence(CoolifyApplicationSummary observed, String applicationUuid) {
        String state = observed == null || observed.status() == null ? "unknown" : observed.status();
        String payload = String.join("\n", "coolify", applicationUuid, state, Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        try {
            String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return "coolify-stop:" + applicationUuid + ":" + digest;
        } catch (Exception exception) { throw new IllegalStateException("Provider stop evidence could not be encoded.", exception); }
    }

    private boolean hasEnvironmentValue(RailwayServicePlanSummary plan, String key, String expectedValue) {
        if (plan == null || plan.env() == null) {
            return false;
        }
        return plan.env().stream().anyMatch(item -> item != null
            && key.equals(item.key())
            && expectedValue.equalsIgnoreCase(item.value()));
    }

    private boolean usesLocalLuceneVectorStore(RailwayServicePlanSummary runtimePlan) {
        return hasEnvironmentValue(runtimePlan, "AI_VECTOR_DB_TYPE", LOCAL_LUCENE_VECTOR_STRATEGY);
    }

    private String safePathSegment(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Document source storage identity is missing.");
        }
        String safe = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        if (safe.isBlank()) {
            throw new IllegalStateException("Document source storage identity is invalid.");
        }
        return safe.length() <= 64 ? safe : safe.substring(0, 64);
    }

    @Override
    public DeploymentProviderResourceActionSummary start(DeploymentProviderResourceHandleEntity handle, String reason) {
        CoolifyConnection connection = connectionForHandle(handle);
        requireOwnedHandle(connection, handle, false);
        if (isRuntimePostgresDatabase(handle)) {
            CoolifyActionResponse response = coolifyApiClient.startDatabase(connection, handle.getProviderResourceUuid());
            return actionSummary(handle, "START", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
        }
        CoolifyActionResponse response = coolifyApiClient.start(connection, handle.getProviderResourceUuid(), false, true);
        return actionSummary(handle, "START", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
    }

    @Override
    public DeploymentProviderResourceActionSummary stop(DeploymentProviderResourceHandleEntity handle, String reason) {
        CoolifyConnection connection = connectionForHandle(handle);
        requireOwnedHandle(connection, handle, false);
        if (isRuntimePostgresDatabase(handle)) {
            CoolifyActionResponse response = coolifyApiClient.stopDatabase(connection, handle.getProviderResourceUuid());
            return actionSummary(handle, "STOP", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
        }
        CoolifyActionResponse response = coolifyApiClient.stop(connection, handle.getProviderResourceUuid(), false);
        return actionSummary(handle, "STOP", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
    }

    @Override
    public DeploymentProviderResourceActionSummary restart(DeploymentProviderResourceHandleEntity handle, String reason) {
        CoolifyConnection connection = connectionForHandle(handle);
        requireOwnedHandle(connection, handle, false);
        if (isRuntimePostgresDatabase(handle)) {
            CoolifyActionResponse response = coolifyApiClient.restartDatabase(connection, handle.getProviderResourceUuid());
            return actionSummary(handle, "RESTART", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
        }
        CoolifyActionResponse response = coolifyApiClient.restart(connection, handle.getProviderResourceUuid());
        return actionSummary(handle, "RESTART", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
    }

    @Override
    public DeploymentProviderResourceActionSummary delete(DeploymentProviderResourceHandleEntity handle, String reason) {
        CoolifyConnection connection = connectionForHandle(handle);
        var authorization = requireOwnedHandle(connection, handle, true);
        if (authorization.present()) requireDirectDeleteWithoutStorage(connection, handle);
        requireResourceOwnership().beginDeletion(authorization.receipt(), handle);
        if (!authorization.present()) {
            finishRetirementDeletion(handle);
            if (isRuntimePostgresDatabase(handle)) clearRuntimeDatabaseSecret(handle, reason);
            return absentDeleteSummary(handle);
        }
        try {
            if (isRuntimePostgresDatabase(handle)) {
                CoolifyActionResponse response = coolifyApiClient.deleteDatabase(connection, handle.getProviderResourceUuid(), true, false, false, false);
                waitForDatabaseAbsent(connection, handle.getProviderResourceUuid());
                finishRetirementDeletion(handle);
                clearRuntimeDatabaseSecret(handle, reason);
                return actionSummary(handle, "DELETE", "COMPLETED", response.message(), response.deploymentUuid(), response.raw());
            }
            CoolifyActionResponse response = coolifyApiClient.delete(connection, handle.getProviderResourceUuid(), true, false, false, false);
            waitForApplicationAbsent(connection, handle.getProviderResourceUuid());
            finishRetirementDeletion(handle);
            return actionSummary(handle, "DELETE", "COMPLETED", response.message(), response.deploymentUuid(), response.raw());
        } catch (CoolifyApiException ex) {
            if (ex.statusCode() == 404) {
                if (isRuntimePostgresDatabase(handle)) {
                    waitForDatabaseAbsent(connection, handle.getProviderResourceUuid());
                    finishRetirementDeletion(handle);
                    clearRuntimeDatabaseSecret(handle, reason);
                } else {
                    waitForApplicationAbsent(connection, handle.getProviderResourceUuid());
                    finishRetirementDeletion(handle);
                }
                return absentDeleteSummary(handle);
            }
            throw ex;
        }
    }

    private void requireDirectDeleteWithoutStorage(CoolifyConnection connection,
                                                  DeploymentProviderResourceHandleEntity handle) {
        List<CoolifyApplicationStorageSummary> storages;
        try {
            storages = isRuntimePostgresDatabase(handle)
                ? coolifyApiClient.listDatabaseStorages(connection, handle.getProviderResourceUuid())
                : coolifyApiClient.listApplicationStorages(connection, handle.getProviderResourceUuid());
        } catch (CoolifyApiException failure) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                "COOLIFY_RESOURCE_STORAGE_RETIREMENT_REQUIRED", failure);
        }
        // Resource ownership alone does not authorize deleting attached storage.
        if (storages == null || !storages.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                "COOLIFY_RESOURCE_STORAGE_RETIREMENT_REQUIRED");
        }
    }

    @Override
    public void requireRetirementOwnership(DeploymentProviderResourceHandleEntity handle) {
        requireOwnedHandle(connectionForHandle(handle), handle, true);
    }

    @Override
    public void beginRetirementDeletion(DeploymentProviderResourceHandleEntity handle) {
        var authorization = requireOwnedHandle(connectionForHandle(handle), handle, true);
        requireResourceOwnership().beginDeletion(authorization.receipt(), handle);
    }

    @Override
    public ManagedResourceOwnershipService.DeletionPreparation preflightRetirementDeletion(DeploymentProviderResourceHandleEntity handle) {
        return new ManagedResourceOwnershipService.DeletionPreparation(
            requireOwnedHandle(connectionForHandle(handle), handle, true).receipt(), List.of());
    }

    @Override
    public boolean prepareRetirementDeletion(DeploymentProviderResourceHandleEntity handle,
        ManagedResourceOwnershipService.DeletionPreparation preparation, Runnable guard) {
        return requireResourceOwnership().prepareDeletion(preparation, handle, guard);
    }

    @Override
    public void finishRetirementDeletion(DeploymentProviderResourceHandleEntity handle) {
        var authorization = requireOwnedHandle(connectionForHandle(handle), handle, true);
        if (authorization.present()) throw CoolifyResourceOwnership.reconciliationRequired();
        requireResourceOwnership().deleted(authorization.receipt());
    }

    private DeploymentProviderResourceActionSummary absentDeleteSummary(DeploymentProviderResourceHandleEntity handle) {
        String message = isRuntimePostgresDatabase(handle)
            ? "Coolify database was already absent." : "Coolify application was already absent.";
        return actionSummary(handle, "DELETE", "COMPLETED", message, null,
            objectMapper.createObjectNode().put("message", message));
    }

    private CoolifyResourceOwnership requireResourceOwnership() {
        if (resourceOwnership == null) throw CoolifyResourceOwnership.reconciliationRequired();
        return resourceOwnership;
    }

    private record AuthorizedCoolifyResource(ManagedResourceOwnershipService.Receipt receipt, boolean present) { }

    private AuthorizedCoolifyResource requireOwnedHandle(CoolifyConnection connection, DeploymentProviderResourceHandleEntity handle,
                                                        boolean allowAbsent) {
        if (!List.of(RESOURCE_KIND_APPLICATION, RESOURCE_KIND_CONNECTOR_APPLICATION,
            RESOURCE_KIND_VECTORIZATION_RUNNER_APPLICATION, RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE).contains(handle.getResourceKind()))
            throw CoolifyResourceOwnership.reconciliationRequired();
        CoolifyResourceOwnership ownership = requireResourceOwnership();
        var ownerScope = ownership.scope(handle);
        var location = ownership.location(connection, handle.getProviderProjectUuid(), handle.getProviderEnvironmentUuid());
        String name = text(readJson(handle.getMetadataJson()),
            isRuntimePostgresDatabase(handle) ? "databaseName" : "applicationName", null);
        var receipt = ownership.requireHandle(ownerScope, location, handle.getResourceKind(), name, handle, allowAbsent);
        boolean referencedElsewhere = resourceHandleRepository.findByProviderResourceUuidOrderByIdAsc(handle.getProviderResourceUuid())
            .stream().anyMatch(other -> other.getProviderType() == DeploymentProviderType.COOLIFY
                && !"DELETED".equals(other.getStatus()) && !java.util.Objects.equals(handle.getId(), other.getId()));
        if (referencedElsewhere) throw CoolifyResourceOwnership.reconciliationRequired();
        ownership.requireNoSharedReferences(handle.getProviderResourceUuid());
        if (isRuntimePostgresDatabase(handle)) {
            String secretName = text(readJson(handle.getMetadataJson()), "passwordSecretName", null);
            if (secretName != null && !runtimePostgresPasswordSecretName(handle.getDeploymentId(), handle.getTargetProfileId()).equals(secretName))
                throw CoolifyResourceOwnership.reconciliationRequired();
            var observed = coolifyApiClient.getDatabase(connection, handle.getProviderResourceUuid());
            if (observed.isEmpty() && allowAbsent) {
                requireAbsentMembership(connection, handle);
                return new AuthorizedCoolifyResource(receipt, false);
            }
            var database = observed.orElseThrow(CoolifyResourceOwnership::reconciliationRequired);
            requireLiveMembership(connection, location, handle.getResourceKind(), name, handle.getProviderResourceUuid(),
                database.uuid(), database.name(), database.raw());
        } else {
            var observed = coolifyApiClient.getApplication(connection, handle.getProviderResourceUuid());
            if (observed.isEmpty() && allowAbsent) {
                requireAbsentMembership(connection, handle);
                return new AuthorizedCoolifyResource(receipt, false);
            }
            var application = observed.orElseThrow(CoolifyResourceOwnership::reconciliationRequired);
            requireLiveMembership(connection, location, handle.getResourceKind(), name, handle.getProviderResourceUuid(),
                application.uuid(), application.name(), application.raw());
        }
        if ("DELETED".equals(receipt.state())) throw CoolifyResourceOwnership.reconciliationRequired();
        return new AuthorizedCoolifyResource(receipt, true);
    }

    private void requireLiveMembership(CoolifyConnection connection, CoolifyResourceOwnership.Location location,
                                        String kind, String name, String uuid, String observedUuid,
                                        String observedName, JsonNode raw) {
        List<CoolifyProjectSummary> projects = coolifyApiClient.listProjects(connection).stream()
            .filter(project -> location.project().equals(project.uuid())).toList();
        if (projects.size() != 1) throw CoolifyResourceOwnership.reconciliationRequired();
        CoolifyEnvironmentSummary environment = coolifyApiClient.getEnvironment(connection, location.project(), location.environment())
            .orElseThrow(CoolifyResourceOwnership::reconciliationRequired);
        requireResourceOwnership().requireLive(location, kind, name, uuid, observedUuid, observedName, raw,
            projects.getFirst().raw(), environment.raw());
    }

    private void requireAbsentMembership(CoolifyConnection connection, DeploymentProviderResourceHandleEntity handle) {
        var location = requireResourceOwnership().location(connection, handle.getProviderProjectUuid(), handle.getProviderEnvironmentUuid());
        List<CoolifyProjectSummary> projects = coolifyApiClient.listProjects(connection).stream()
            .filter(project -> location.project().equals(project.uuid())).toList();
        if (projects.size() != 1) throw CoolifyResourceOwnership.reconciliationRequired();
        CoolifyEnvironmentSummary environment = coolifyApiClient.getEnvironment(connection, location.project(), location.environment())
            .orElseThrow(CoolifyResourceOwnership::reconciliationRequired);
        requireResourceOwnership().requireAbsent(location, handle.getResourceKind(), handle.getProviderResourceUuid(),
            projects.getFirst().raw(), environment.raw());
    }

    @Override
    public DeploymentProviderResourceStatusSummary status(DeploymentProviderResourceHandleEntity handle) {
        CoolifyConnection connection = connectionForHandle(handle);
        if (isRuntimePostgresDatabase(handle)) {
            CoolifyDatabaseSummary database = coolifyApiClient.getDatabase(connection, handle.getProviderResourceUuid())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Coolify database not found: " + handle.getProviderResourceUuid()
                ));
            return new DeploymentProviderResourceStatusSummary(
                handle.getId(),
                handle.getProviderType(),
                handle.getProviderResourceUuid(),
                normalizeStatus(database.status(), "OBSERVED"),
                database.status(),
                null,
                safeDatabaseDetails(database),
                Instant.now()
            );
        }
        CoolifyApplicationSummary application = coolifyApiClient.getApplication(connection, handle.getProviderResourceUuid())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Coolify application not found: " + handle.getProviderResourceUuid()
            ));
        return new DeploymentProviderResourceStatusSummary(
            handle.getId(),
            handle.getProviderType(),
            handle.getProviderResourceUuid(),
            normalizeStatus(application.status(), "OBSERVED"),
            application.status(),
            application.fqdn(),
            safeApplicationDetails(application),
            Instant.now()
        );
    }

    @Override
    public DeploymentProviderResourceLogsSummary logs(DeploymentProviderResourceHandleEntity handle, int lines) {
        CoolifyConnection connection = connectionForHandle(handle);
        int normalizedLines = Math.max(1, Math.min(lines, 1000));
        return new DeploymentProviderResourceLogsSummary(
            handle.getId(),
            handle.getProviderType(),
            handle.getProviderResourceUuid(),
            normalizedLines,
            isRuntimePostgresDatabase(handle)
                ? coolifyApiClient.databaseLogs(connection, handle.getProviderResourceUuid(), normalizedLines)
                : coolifyApiClient.logs(connection, handle.getProviderResourceUuid(), normalizedLines),
            Instant.now()
        );
    }

    private boolean isRuntimePostgresDatabase(DeploymentProviderResourceHandleEntity handle) {
        return handle != null && RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE.equals(handle.getResourceKind());
    }

    private void clearRuntimeDatabaseSecret(DeploymentProviderResourceHandleEntity handle, String reason) {
        if (platformSecretService == null) {
            return;
        }
        String secretName = text(readJson(handle.getMetadataJson()), "passwordSecretName", null);
        if (!StringUtils.hasText(secretName) || !platformSecretService.isManagedSecretName(secretName)) {
            return;
        }
        if (!runtimePostgresPasswordSecretName(handle.getDeploymentId(), handle.getTargetProfileId()).equals(secretName))
            throw CoolifyResourceOwnership.reconciliationRequired();
        platformSecretService.clearManagedSecret(
            secretName,
            Map.of(
                "deploymentId", handle.getDeploymentId(),
                "targetProfileId", handle.getTargetProfileId(),
                "reason", StringUtils.hasText(reason) ? reason : "provider_resource_delete",
                "action", "DELETE_RUNTIME_POSTGRES_DATABASE"
            )
        );
    }

    private CoolifyApplicationSummary reconcileApplication(CoolifyConnection connection,
                                                          CoolifyResourceScope scope,
                                                          DeploymentEntity deployment,
                                                          DeploymentTargetProfileEntity profile,
                                                          String resourceKind,
                                                          String appName,
                                                          CoolifyProvisioningSource source,
                                                          RailwayServicePlanSummary servicePlan,
                                                          String baseDirectory,
                                                          String dockerfileLocation,
                                                          String portsExposes,
                                                          boolean healthCheckEnabled,
                                                          String healthCheckPath,
                                                          String healthCheckPort,
                                                          CoolifyApplicationRuntimeSettings runtimeSettings,
                                                          boolean autogenerateDomain,
                                                          String domain) {
        if (source.gitSource()) {
            CoolifyCreatePublicApplicationRequest request = publicApplicationRequest(
                connection,
                scope,
                source,
                servicePlan,
                baseDirectory,
                dockerfileLocation,
                appName,
                deployment.getId(),
                portsExposes,
                healthCheckEnabled,
                healthCheckPath,
                healthCheckPort,
                runtimeSettings,
                autogenerateDomain,
                domain
            );
            return reconcileApplication(
                connection,
                scope,
                deployment,
                profile,
                resourceKind,
                appName,
                () -> createPublicApplication(connection, request),
                uuid -> coolifyApiClient.updatePublicApplication(connection, uuid, request)
            );
        }

        CoolifyCreateDockerImageApplicationRequest request = dockerImageApplicationRequest(
            connection,
            scope,
            source,
            appName,
            deployment.getId(),
            portsExposes,
            healthCheckEnabled,
            healthCheckPath,
            healthCheckPort,
            runtimeSettings,
            autogenerateDomain,
            domain
        );
        return reconcileApplication(
            connection,
            scope,
            deployment,
            profile,
            resourceKind,
            appName,
            () -> createDockerImageApplication(connection, request),
            uuid -> coolifyApiClient.updateDockerImageApplication(connection, uuid, request)
        );
    }

    private CoolifyApplicationSummary reconcileApplication(CoolifyConnection connection,
                                                          CoolifyResourceScope scope,
                                                          DeploymentEntity deployment,
                                                          DeploymentTargetProfileEntity profile,
                                                          String resourceKind,
                                                          String appName,
                                                          Supplier<CoolifyApplicationSummary> creator,
                                                          Consumer<String> updater) {
        CoolifyResourceOwnership ownership = requireResourceOwnership();
        var ownerScope = ownership.scope(deployment, profile.getId());
        var location = ownership.location(connection, scope.projectUuid(), scope.environmentUuid());
        DeploymentProviderResourceHandleEntity existingHandle = resourceHandleRepository
            .findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
                deployment.getId(),
                profile.getId(),
                resourceKind
            )
            .orElse(null);
        if (existingHandle != null) {
            ownership.requireHandle(ownerScope, location, resourceKind, appName, existingHandle);
            String uuid = existingHandle.getProviderResourceUuid();
            requireLiveApplication(connection, location, appName, uuid);
            updater.accept(uuid);
            return requireLiveApplication(connection, location, appName, uuid);
        }

        List<CoolifyApplicationSummary> namedApplications = coolifyApiClient.listApplications(connection).stream()
            .filter(application -> appName.equals(application.name()))
            .toList();
        if (namedApplications.size() > 1) {
            throw CoolifyResourceOwnership.reconciliationRequired();
        }
        CoolifyApplicationSummary namedApplication = namedApplications.isEmpty() ? null : namedApplications.getFirst();
        if (namedApplication != null) {
            ownership.requireOwned(ownerScope, location, resourceKind, appName, namedApplication.uuid());
            requireLiveApplication(connection, location, appName, namedApplication.uuid());
            updater.accept(namedApplication.uuid());
            return requireLiveApplication(connection, location, appName, namedApplication.uuid());
        }
        CoolifyApplicationSummary created = createApplicationWithReceipt(
            connection, scope, deployment, profile, resourceKind, appName, creator);
        ownership.requireOwned(ownerScope, location, resourceKind, appName, created.uuid());
        updater.accept(created.uuid());
        return requireLiveApplication(connection, location, appName, created.uuid());
    }

    private CoolifyApplicationSummary requireLiveApplication(CoolifyConnection connection,
                                                              CoolifyResourceOwnership.Location location,
                                                              String appName, String uuid) {
        CoolifyApplicationSummary observed = coolifyApiClient.getApplication(connection, uuid)
            .orElseThrow(CoolifyResourceOwnership::reconciliationRequired);
        requireLiveMembership(connection, location, RESOURCE_KIND_APPLICATION, appName, uuid,
            observed.uuid(), observed.name(), observed.raw());
        return observed;
    }

    private CoolifyApplicationSummary createApplicationWithReceipt(CoolifyConnection connection,
                                                                CoolifyResourceScope scope,
                                                                DeploymentEntity deployment,
                                                                DeploymentTargetProfileEntity profile,
                                                                String resourceKind,
                                                                String appName,
                                                                Supplier<CoolifyApplicationSummary> creator) {
        CoolifyResourceOwnership ownership = requireResourceOwnership();
        var location = ownership.location(connection, scope.projectUuid(), scope.environmentUuid());
        return ownership.create(ownership.scope(deployment, profile.getId()), location, resourceKind, appName, () -> {
            CoolifyApplicationSummary created = creator.get();
            requireLiveMembership(connection, location, resourceKind, appName, created.uuid(),
                created.uuid(), created.name(), created.raw());
            return created;
        }, CoolifyApplicationSummary::uuid);
    }

    private CoolifyApplicationSummary createDockerImageApplication(CoolifyConnection connection,
                                                                  CoolifyCreateDockerImageApplicationRequest request) {
        String uuid = coolifyApiClient.createDockerImageApplication(connection, request);
        if (!StringUtils.hasText(uuid)) throw CoolifyResourceOwnership.reconciliationRequired();
        return coolifyApiClient.getApplication(connection, uuid)
            .filter(application -> uuid.equals(application.uuid()))
            .orElseThrow(CoolifyResourceOwnership::reconciliationRequired);
    }

    private CoolifyApplicationSummary createPublicApplication(CoolifyConnection connection,
                                                             CoolifyCreatePublicApplicationRequest request) {
        String uuid = coolifyApiClient.createPublicApplication(connection, request);
        if (!StringUtils.hasText(uuid)) throw CoolifyResourceOwnership.reconciliationRequired();
        return coolifyApiClient.getApplication(connection, uuid)
            .filter(application -> uuid.equals(application.uuid()))
            .orElseThrow(CoolifyResourceOwnership::reconciliationRequired);
    }

    private CoolifyRuntimeDatabaseBinding reconcileRuntimeDatabaseBinding(CoolifyConnection connection,
                                                                          CoolifyResourceScope scope,
                                                                          DeploymentEntity deployment,
                                                                          DeploymentTargetProfileEntity profile,
                                                                          JsonNode resourceDefaults,
                                                                          JsonNode providerConfig) {
        AiStateStorageMode selected = DeploymentAiStateStorageSelection.selected(providerConfig);
        if (selected == AiStateStorageMode.CUSTOMER_POSTGRES) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "AI_STATE_STORAGE_BINDING_REQUIRED");
        }
        String mode = text(resourceDefaults, "runtimeDatabaseMode", null);
        if (selected == null && !RUNTIME_DATABASE_MODE_COOLIFY_POSTGRES.equalsIgnoreCase(mode)) {
            return null;
        }
        if (platformSecretService == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Platform secret service is required for managed runtime Postgres provisioning."
            );
        }
        CoolifyCreatePostgresDatabaseRequest request = runtimePostgresDatabaseRequest(
            connection,
            scope,
            deployment,
            profile,
            resourceDefaults
        );
        CoolifyDatabaseSummary database = reconcilePostgresDatabase(connection, scope, deployment, profile, request);
        String passwordSecretName = runtimePostgresPasswordSecretName(deployment.getId(), profile.getId());
        String password = platformSecretService.resolveSecret(passwordSecretName);
        if (!StringUtils.hasText(password)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Managed runtime Postgres password secret disappeared during provisioning."
            );
        }
        String internalHost = text(
            resourceDefaults,
            "runtimeDatabaseInternalHost",
            resolveRuntimeDatabaseInternalHost(database)
        );
        String port = text(resourceDefaults, "runtimeDatabasePort", "5432");
        String jdbcDatabaseName = StringUtils.hasText(database.postgresDatabase())
            ? database.postgresDatabase()
            : request.postgresDatabase();
        String managedJdbcUrl = "jdbc:postgresql://" + internalHost + ":" + port + "/" + jdbcDatabaseName;
        String jdbcUrl = selected == null ? text(resourceDefaults, "runtimeDatabaseJdbcUrl", managedJdbcUrl) : managedJdbcUrl;
        return new CoolifyRuntimeDatabaseBinding(
            selected == null ? RUNTIME_DATABASE_MODE_COOLIFY_POSTGRES : selected.name(),
            database,
            jdbcUrl,
            request.postgresUser(),
            password,
            passwordSecretName
        );
    }

    CoolifyRuntimeDatabaseBinding validateAiStateStorageBeforeEffects(DeploymentEntity deployment,
        DeploymentVersionEntity version, DeploymentReleaseEntity release, DeploymentTargetProfileEntity profile) {
        JsonNode config = readJson(version.getProviderConfigJson());
        AiStateStorageMode selected = DeploymentAiStateStorageSelection.selected(config);
        boolean productized = customerOfferDeploymentRepository != null
            && customerOfferDeploymentRepository.existsById(deployment.getId());
        if (productized && selected == null) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "AI_STATE_STORAGE_EXPLICIT_CHOICE_REQUIRED");
        }
        if (selected == null) return null;
        if (!deployment.getId().equals(version.getDeploymentId()) || !deployment.getId().equals(release.getDeploymentId())
            || !version.getId().equals(release.getDeploymentVersionId()) || !profile.getId().equals(release.getTargetProfileId())
            || !"PUBLISHED".equals(version.getStatus())) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "AI_STATE_STORAGE_RELEASE_SCOPE_CHANGED");
        }
        if (aiStateStorageBindings == null) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "AI_STATE_STORAGE_BINDING_SERVICE_REQUIRED");
        }
        aiStateStorageBindings.requireUnchanged(deployment, version, release);
        if (selected == AiStateStorageMode.CUSTOMER_POSTGRES) {
            return aiStateStorageBindings.resolve(deployment, version, profile);
        }
        if (readJson(profile.getResourceDefaultsJson()).has("runtimeDatabaseJdbcUrl")) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "AI_STATE_STORAGE_RAW_JDBC_OVERRIDE_FORBIDDEN");
        }
        boolean external = resourceHandleRepository.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(
            deployment.getId(), profile.getId()).stream()
            .anyMatch(handle -> PreservedExternalBindingKinds.AI_STATE_STORAGE.equals(handle.getResourceKind()));
        if (external) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "AI_STATE_STORAGE_EXISTING_CUSTOMER_STORE_REQUIRES_MIGRATION");
        }
        return null;
    }

    private String resolveRuntimeDatabaseInternalHost(CoolifyDatabaseSummary database) {
        String internalDbUrl = textFirst(database.raw(), "internal_db_url", "internalDbUrl");
        if (StringUtils.hasText(internalDbUrl)) {
            try {
                URI uri = URI.create(internalDbUrl);
                if (StringUtils.hasText(uri.getHost())) {
                    return uri.getHost();
                }
            } catch (RuntimeException ignored) {
                // Fall back to Coolify identifiers below; do not propagate or log URL credentials.
            }
        }
        if (StringUtils.hasText(database.uuid())) {
            return database.uuid();
        }
        return database.name();
    }

    private CoolifyDatabaseSummary reconcilePostgresDatabase(CoolifyConnection connection,
                                                             CoolifyResourceScope scope,
                                                             DeploymentEntity deployment,
                                                             DeploymentTargetProfileEntity profile,
                                                             CoolifyCreatePostgresDatabaseRequest request) {
        CoolifyResourceOwnership ownership = requireResourceOwnership();
        var ownerScope = ownership.scope(deployment, profile.getId());
        var location = ownership.location(connection, scope.projectUuid(), scope.environmentUuid());
        Optional<DeploymentProviderResourceHandleEntity> existingHandle =
            resourceHandleRepository.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
                deployment.getId(),
                profile.getId(),
                RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE
            );
        if (existingHandle.isPresent()) {
            ownership.requireHandle(ownerScope, location, RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE, request.name(), existingHandle.get());
            String uuid = existingHandle.get().getProviderResourceUuid();
            requireLiveDatabase(connection, location, request.name(), uuid);
            coolifyApiClient.updatePostgresDatabase(connection, uuid, request);
            return requireDatabaseCapacity(requireLiveDatabase(connection, location, request.name(), uuid), request);
        }

        List<CoolifyDatabaseSummary> namedDatabases = coolifyApiClient.listDatabases(connection).stream()
            .filter(database -> request.name().equals(database.name()))
            .toList();
        if (namedDatabases.size() > 1) {
            throw CoolifyResourceOwnership.reconciliationRequired();
        }
        CoolifyDatabaseSummary namedDatabase = namedDatabases.isEmpty() ? null : namedDatabases.getFirst();
        if (namedDatabase != null) {
            ownership.requireOwned(ownerScope, location, RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE, request.name(), namedDatabase.uuid());
            requireLiveDatabase(connection, location, request.name(), namedDatabase.uuid());
            coolifyApiClient.updatePostgresDatabase(connection, namedDatabase.uuid(), request);
            return requireDatabaseCapacity(requireLiveDatabase(connection, location, request.name(), namedDatabase.uuid()), request);
        }

        CoolifyDatabaseSummary database = ownership.create(ownerScope, location, RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE,
            request.name(), () -> {
            String uuid = coolifyApiClient.createPostgresDatabase(connection, request);
            if (!StringUtils.hasText(uuid)) throw CoolifyResourceOwnership.reconciliationRequired();
            return requireDatabaseCapacity(requireLiveDatabase(connection, location, request.name(), uuid), request);
        }, CoolifyDatabaseSummary::uuid);
        ownership.requireOwned(ownerScope, location, RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE, request.name(), database.uuid());
        coolifyApiClient.startDatabase(connection, database.uuid());
        return database;
    }

    private CoolifyDatabaseSummary requireLiveDatabase(CoolifyConnection connection,
                                                       CoolifyResourceOwnership.Location location,
                                                       String name, String uuid) {
        CoolifyDatabaseSummary observed = coolifyApiClient.getDatabase(connection, uuid)
            .orElseThrow(CoolifyResourceOwnership::reconciliationRequired);
        requireLiveMembership(connection, location, RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE, name, uuid,
            observed.uuid(), observed.name(), observed.raw());
        return observed;
    }

    private CoolifyDatabaseSummary requireDatabaseCapacity(CoolifyDatabaseSummary database,
                                                            CoolifyCreatePostgresDatabaseRequest request) {
        CoolifyDatabaseCapacity.requireReadback(
            database,
            request.runtimeSettings(),
            "RUNTIME_DATABASE_CAPACITY_DRIFT"
        );
        return database;
    }

    private CoolifyCreatePostgresDatabaseRequest runtimePostgresDatabaseRequest(CoolifyConnection connection,
                                                                                CoolifyResourceScope scope,
                                                                                DeploymentEntity deployment,
                                                                                DeploymentTargetProfileEntity profile,
                                                                                JsonNode resourceDefaults) {
        String databaseName = normalizeDatabaseIdentifier(text(resourceDefaults, "runtimeDatabaseName", "runtime_chat"));
        String username = normalizeDatabaseIdentifier(text(resourceDefaults, "runtimeDatabaseUsername", "runtime_user"));
        String prefix = text(resourceDefaults, "runtimeDatabaseNamePrefix", "ai-fabric-runtime-postgres");
        String resourceName = normalizeName(prefix + "-" + deployment.getId());
        String passwordSecretName = runtimePostgresPasswordSecretName(deployment.getId(), profile.getId());
        String password = platformSecretService.resolveSecret(passwordSecretName);
        if (!StringUtils.hasText(password)) {
            CoolifyResourceOwnership ownership = requireResourceOwnership();
            var location = ownership.location(connection, scope.projectUuid(), scope.environmentUuid());
            boolean existingHandle = resourceHandleRepository
                .findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
                    deployment.getId(), profile.getId(), RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE).isPresent();
            if (existingHandle || ownership.hasCreationIntent(ownership.scope(deployment, profile.getId()), location,
                    RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE, resourceName)
                || coolifyApiClient.listDatabases(connection).stream().anyMatch(database -> resourceName.equals(database.name())))
                throw CoolifyResourceOwnership.reconciliationRequired();
            password = generateSecretValue();
            platformSecretService.upsertManagedSecret(
                passwordSecretName,
                password,
                Map.of(
                    "deploymentId", deployment.getId(),
                    "targetProfileId", profile.getId(),
                    "purpose", "RUNTIME_POSTGRES_PASSWORD"
                )
            );
        }
        CoolifyDatabaseRuntimeSettings databaseCapacity = runtimeDatabaseCapacity(
            deployment,
            profile,
            resourceDefaults
        );
        return new CoolifyCreatePostgresDatabaseRequest(
            scope.projectUuid(),
            connection.config().serverUuid(),
            scope.environmentName(),
            scope.environmentUuid(),
            connection.config().destinationUuid(),
            resourceName,
            "Durable runtime chat database for AI Fabric deployment " + deployment.getId(),
            text(resourceDefaults, "runtimeDatabaseImage", "postgres:16-alpine"),
            username,
            password,
            databaseName,
            booleanValue(resourceDefaults, "runtimeDatabasePublic", false),
            false,
            databaseCapacity
        );
    }

    CoolifyDatabaseRuntimeSettings runtimeDatabaseCapacity(DeploymentEntity deployment,
                                                            DeploymentTargetProfileEntity profile,
                                                            JsonNode resourceDefaults) {
        boolean productizedCustomerDeployment = customerOfferDeploymentRepository != null
            && customerOfferDeploymentRepository.existsById(deployment.getId());
        CoolifyDatabaseRuntimeSettings databaseCapacity = CoolifyDatabaseCapacity.fromTargetDefaults(
            resourceDefaults, productizedCustomerDeployment);
        return databaseCapacity;
    }

    private DeploymentProviderResourceHandleEntity upsertDatabaseHandle(DeploymentEntity deployment,
                                                                        DeploymentReleaseEntity release,
                                                                        DeploymentTargetProfileEntity profile,
                                                                        CoolifyResourceScope scope,
                                                                        CoolifyTargetProfileConfig config,
                                                                        CoolifyRuntimeDatabaseBinding binding) {
        Instant now = Instant.now();
        DeploymentProviderResourceHandleEntity handle = resourceHandleRepository
            .findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
                deployment.getId(),
                profile.getId(),
                RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE
            )
            .orElseGet(() -> {
                DeploymentProviderResourceHandleEntity created = new DeploymentProviderResourceHandleEntity();
                created.setId("dprh-" + UUID.randomUUID().toString().substring(0, 8));
                created.setCreatedAt(now);
                return created;
            });
        handle.setDeploymentId(deployment.getId());
        handle.setReleaseId(release.getId());
        handle.setTargetProfileId(profile.getId());
        handle.setProviderType(DeploymentProviderType.COOLIFY);
        handle.setResourceKind(RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE);
        handle.setProviderProjectUuid(scope.projectUuid());
        handle.setProviderEnvironmentUuid(scope.environmentUuid());
        handle.setProviderServerUuid(config.serverUuid());
        handle.setFqdn(null);
        handle.setMetadataJson(withOwnershipReceipt(databaseHandleMetadata(binding, scope), deployment, profile, scope, config,
            RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE, binding.database().name(), binding.database().uuid()));
        return persistDatabaseHandle(handle, binding);
    }

    private DeploymentProviderResourceHandleEntity persistDatabaseHandle(
        DeploymentProviderResourceHandleEntity handle,
        CoolifyRuntimeDatabaseBinding binding
    ) {
        Instant now = Instant.now();
        handle.setProviderResourceUuid(binding.database().uuid());
        handle.setStatus(databaseReady(binding.database()) ? "ACTIVE" : "PROVISIONED");
        handle.setLastObservedStatus(binding.database().status());
        handle.setLastObservedAt(now);
        handle.setUpdatedAt(now);
        return resourceHandleRepository.save(handle);
    }

    private CoolifyCreateDockerImageApplicationRequest dockerImageApplicationRequest(CoolifyConnection connection,
                                                                                    CoolifyResourceScope scope,
                                                                                    CoolifyProvisioningSource source,
                                                                                    String appName,
                                                                                    String deploymentId,
                                                                                    String portsExposes,
                                                                                    boolean healthCheckEnabled,
                                                                                    String healthCheckPath,
                                                                                    String healthCheckPort,
                                                                                    CoolifyApplicationRuntimeSettings runtimeSettings,
                                                                                    boolean autogenerateDomain,
                                                                                    String domain) {
        DeploymentSourceArtifactEntity artifact = source.sourceArtifact();
        return new CoolifyCreateDockerImageApplicationRequest(
            scope.projectUuid(),
            connection.config().serverUuid(),
            scope.environmentName(),
            scope.environmentUuid(),
            artifact.getImageRepository(),
            sourceImageTag(artifact),
            portsExposes,
            connection.config().destinationUuid(),
            appName,
            "Managed by AI Fabric deployment " + deploymentId,
            domain,
            healthCheckEnabled,
            healthCheckPath,
            healthCheckPort,
            runtimeSettings,
            false,
            connection.config().forceHttps(),
            autogenerateDomain
        );
    }

    private CoolifyCreatePublicApplicationRequest publicApplicationRequest(CoolifyConnection connection,
                                                                          CoolifyResourceScope scope,
                                                                          CoolifyProvisioningSource source,
                                                                          RailwayServicePlanSummary servicePlan,
                                                                          String baseDirectory,
                                                                          String dockerfileLocation,
                                                                          String appName,
                                                                          String deploymentId,
                                                                          String portsExposes,
                                                                          boolean healthCheckEnabled,
                                                                          String healthCheckPath,
                                                                          String healthCheckPort,
                                                                          CoolifyApplicationRuntimeSettings runtimeSettings,
                                                                          boolean autogenerateDomain,
                                                                          String domain) {
        return new CoolifyCreatePublicApplicationRequest(
            scope.projectUuid(),
            connection.config().serverUuid(),
            scope.environmentName(),
            scope.environmentUuid(),
            source.gitRepository(),
            source.gitBranch(),
            source.buildPack(),
            baseDirectory,
            dockerfileLocation,
            portsExposes,
            connection.config().destinationUuid(),
            appName,
            "Managed by AI Fabric deployment " + deploymentId,
            domain,
            healthCheckEnabled,
            healthCheckPath,
            healthCheckPort,
            runtimeSettings,
            false,
            false,
            connection.config().forceHttps(),
            autogenerateDomain
        );
    }

    private DeploymentProviderResourceHandleEntity upsertHandle(DeploymentEntity deployment,
                                                               DeploymentReleaseEntity release,
                                                               DeploymentTargetProfileEntity profile,
                                                               CoolifyResourceScope scope,
                                                               CoolifyTargetProfileConfig config,
                                                               String resourceKind,
                                                               CoolifyApplicationSummary application,
                                                               CoolifyProvisioningSource source,
                                                               String serviceRole,
                                                               int envCount,
                                                               CoolifyActionResponse deployResponse) {
        Instant now = Instant.now();
        DeploymentProviderResourceHandleEntity handle = resourceHandleRepository
            .findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
                deployment.getId(),
                profile.getId(),
                resourceKind
            )
            .orElseGet(() -> {
                DeploymentProviderResourceHandleEntity created = new DeploymentProviderResourceHandleEntity();
                created.setId("dprh-" + UUID.randomUUID().toString().substring(0, 8));
                created.setCreatedAt(now);
                return created;
            });
        return persistApplicationHandle(
            handle,
            application,
            deployment,
            release,
            profile,
            scope,
            config,
            resourceKind,
            source,
            serviceRole,
            envCount,
            deployResponse
        );
    }

    private DeploymentProviderResourceHandleEntity upsertRuntimeHandleProvisional(
        DeploymentEntity deployment,
        DeploymentReleaseEntity release,
        DeploymentTargetProfileEntity profile,
        CoolifyResourceScope scope,
        CoolifyTargetProfileConfig config,
        String resourceKind,
        CoolifyApplicationSummary application,
        CoolifyProvisioningSource source,
        String serviceRole,
        int envCount,
        CoolifyActionResponse deployResponse
    ) {
        Optional<DeploymentProviderResourceHandleEntity> existing = resourceHandleRepository
            .findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
                deployment.getId(), profile.getId(), resourceKind
            );
        DeploymentProviderResourceHandleEntity preserved = preserveExistingRuntimeGenerationHandle(
            existing, application
        );
        if (preserved != null) {
            // Keep the prior release identity durable until the old generation is fenced,
            // stopped and the replacement has adopted authority. The final handle write
            // below advances it only after that sequence succeeds.
            return preserved;
        }
        return upsertHandle(
            deployment, release, profile, scope, config, resourceKind, application,
            source, serviceRole, envCount, deployResponse
        );
    }

    DeploymentProviderResourceHandleEntity preserveExistingRuntimeGenerationHandle(
        Optional<DeploymentProviderResourceHandleEntity> existing,
        CoolifyApplicationSummary application
    ) {
        if (existing == null || existing.isEmpty()) return null;
        DeploymentProviderResourceHandleEntity handle = existing.get();
        // Legacy/stale handles without a release identity are not generation authority.
        // Their provider absence or scope replacement was already reconciled by the caller.
        if (!StringUtils.hasText(handle.getReleaseId())) return null;
        if (!application.uuid().equals(handle.getProviderResourceUuid())) {
            throw new IllegalStateException("RUNTIME_GENERATION_PROVIDER_RESOURCE_CHANGED");
        }
        return handle;
    }

    private DeploymentProviderResourceHandleEntity persistApplicationHandle(
        DeploymentProviderResourceHandleEntity handle,
        CoolifyApplicationSummary application,
        DeploymentEntity deployment,
        DeploymentReleaseEntity release,
        DeploymentTargetProfileEntity profile,
        CoolifyResourceScope scope,
        CoolifyTargetProfileConfig config,
        String resourceKind,
        CoolifyProvisioningSource source,
        String serviceRole,
        int envCount,
        CoolifyActionResponse deployResponse
    ) {
        Instant now = Instant.now();
        handle.setDeploymentId(deployment.getId());
        handle.setReleaseId(release.getId());
        handle.setTargetProfileId(profile.getId());
        handle.setProviderType(DeploymentProviderType.COOLIFY);
        handle.setResourceKind(resourceKind);
        handle.setProviderResourceUuid(application.uuid());
        handle.setProviderProjectUuid(scope.projectUuid());
        handle.setProviderEnvironmentUuid(scope.environmentUuid());
        handle.setProviderServerUuid(config.serverUuid());
        handle.setFqdn(application.fqdn());
        handle.setStatus(applicationReady(application) ? "ACTIVE" : "DEPLOY_REQUESTED");
        handle.setLastObservedStatus(application.status());
        handle.setLastObservedAt(now);
        handle.setMetadataJson(withOwnershipReceipt(handleMetadata(application, source, scope, serviceRole, envCount, deployResponse),
            deployment, profile, scope, config, resourceKind, application.name(), application.uuid()));
        handle.setUpdatedAt(now);
        return resourceHandleRepository.save(handle);
    }

    private String withOwnershipReceipt(String metadataJson, DeploymentEntity deployment, DeploymentTargetProfileEntity profile,
                                         CoolifyResourceScope scope, CoolifyTargetProfileConfig config,
                                         String kind, String name, String uuid) {
        CoolifyResourceOwnership ownership = requireResourceOwnership();
        var location = ownership.location(new CoolifyConnection(config.baseUrl(), null, config), scope.projectUuid(), scope.environmentUuid());
        var receipt = ownership.requireOwned(ownership.scope(deployment, profile.getId()), location, kind, name, uuid);
        ObjectNode metadata = (ObjectNode) readJson(metadataJson);
        metadata.put("ownershipReceiptId", receipt.id());
        return metadata.toPrettyString();
    }

    private CoolifyResourceScope resolveResourceScope(CoolifyConnection connection,
                                                      DeploymentEntity deployment,
                                                      JsonNode resourceDefaults) {
        boolean customerGroupingEnabled = booleanValue(
            resourceDefaults,
            "customerProjectGroupingEnabled",
            platformCustomerRepository != null
        );
        if (!customerGroupingEnabled) {
            return defaultResourceScope(connection);
        }
        CoolifyTargetProfileConfig config = connection.config();
        String projectName = resolveCustomerProjectName(deployment, resourceDefaults);
        String projectDescription = resolveCustomerProjectDescription(deployment, projectName, config.environmentName());
        CoolifyProjectSummary project = ensureProject(connection, projectName, projectDescription);
        String environmentName = text(resourceDefaults, "customerProjectEnvironmentName", config.environmentName());
        CoolifyEnvironmentSummary environment = ensureEnvironment(connection, project.uuid(), environmentName);
        return new CoolifyResourceScope(
            project.uuid(),
            project.name(),
            environment.name(),
            environment.uuid(),
            true
        );
    }

    private CoolifyResourceScope defaultResourceScope(CoolifyConnection connection) {
        CoolifyTargetProfileConfig config = connection.config();
        return new CoolifyResourceScope(
            config.projectUuid(),
            null,
            config.environmentName(),
            config.environmentUuid(),
            false
        );
    }

    private CoolifyProjectSummary ensureProject(CoolifyConnection connection, String name, String description) {
        Optional<CoolifyProjectSummary> existing = findProject(connection, name);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            String uuid = coolifyApiClient.createProject(connection, name, description);
            return new CoolifyProjectSummary(
                uuid,
                name,
                description,
                objectMapper.createObjectNode()
                    .put("uuid", uuid)
                    .put("name", name)
                    .put("description", description)
            );
        } catch (CoolifyApiException ex) {
            if (ex.statusCode() != 409 && ex.statusCode() != 422) {
                throw ex;
            }
            return findProject(connection, name)
                .orElseThrow(() -> ex);
        }
    }

    private Optional<CoolifyProjectSummary> findProject(CoolifyConnection connection, String name) {
        return coolifyApiClient.listProjects(connection).stream()
            .filter(project -> name.equalsIgnoreCase(project.name()))
            .findFirst();
    }

    private CoolifyEnvironmentSummary ensureEnvironment(CoolifyConnection connection,
                                                        String projectUuid,
                                                        String environmentName) {
        Optional<CoolifyEnvironmentSummary> existing = findEnvironment(connection, projectUuid, environmentName);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            String uuid = coolifyApiClient.createEnvironment(connection, projectUuid, environmentName);
            return new CoolifyEnvironmentSummary(
                uuid,
                environmentName,
                projectUuid,
                null,
                objectMapper.createObjectNode()
                    .put("uuid", uuid)
                    .put("name", environmentName)
                    .put("project_uuid", projectUuid)
            );
        } catch (CoolifyApiException ex) {
            if (ex.statusCode() != 409 && ex.statusCode() != 422) {
                throw ex;
            }
            return findEnvironment(connection, projectUuid, environmentName)
                .orElseThrow(() -> ex);
        }
    }

    private Optional<CoolifyEnvironmentSummary> findEnvironment(CoolifyConnection connection,
                                                                String projectUuid,
                                                                String environmentName) {
        Optional<CoolifyEnvironmentSummary> listed = coolifyApiClient.listEnvironments(connection, projectUuid).stream()
            .filter(environment -> environmentName.equalsIgnoreCase(environment.name()))
            .findFirst();
        if (listed.isEmpty()) {
            return Optional.empty();
        }
        if (StringUtils.hasText(listed.get().uuid())) {
            return listed;
        }
        return coolifyApiClient.getEnvironment(connection, projectUuid, environmentName)
            .or(() -> listed);
    }

    private String resolveCustomerProjectName(DeploymentEntity deployment, JsonNode resourceDefaults) {
        String configuredPrefix = text(resourceDefaults, "customerProjectNamePrefix", "customer");
        String prefix = normalizeScopedName(configuredPrefix, 32);
        String configuredSlug = text(resourceDefaults, "customerProjectSlug", null);
        String rawCustomerName = StringUtils.hasText(configuredSlug)
            ? configuredSlug
            : resolveCustomerSlug(deployment);
        String slug = normalizeScopedName(rawCustomerName, 48);
        return normalizeScopedName(prefix + "-" + slug, 80);
    }

    private String resolveCustomerSlug(DeploymentEntity deployment) {
        String customerId = deployment.getCustomerId();
        if (platformCustomerRepository != null && StringUtils.hasText(customerId)) {
            Optional<PlatformCustomerEntity> customer = platformCustomerRepository.findById(customerId);
            if (customer.isPresent()) {
                PlatformCustomerEntity entity = customer.get();
                if (StringUtils.hasText(entity.getSlug())) {
                    return entity.getSlug();
                }
                if (StringUtils.hasText(entity.getName())) {
                    return entity.getName();
                }
            }
        }
        if (StringUtils.hasText(customerId)) {
            return customerId;
        }
        if (StringUtils.hasText(deployment.getTenantId())) {
            return deployment.getTenantId();
        }
        return deployment.getId();
    }

    private String resolveCustomerProjectDescription(DeploymentEntity deployment,
                                                     String projectName,
                                                     String environmentName) {
        String customerId = StringUtils.hasText(deployment.getCustomerId())
            ? deployment.getCustomerId()
            : "unknown";
        return "Managed by AI Fabric for Platform customer " + customerId
            + " (" + projectName + "), environment " + environmentName + ".";
    }

    private ManagedVectorProvisioningResult ensureManagedVectorProvisioned(DeploymentEntity deployment,
                                                                           DeploymentVersionEntity version,
                                                                           DeploymentReleaseEntity release,
                                                                           ProvisioningProgressTracker progressTracker) {
        if (deploymentManagedVectorProvisioningService == null) {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("enabled", false);
            details.put("mode", "UNAVAILABLE");
            details.put("message", "Managed vector provisioning service is not available in this Coolify provider context.");
            return new ManagedVectorProvisioningResult(null, details);
        }

        ManagedVectorProvisioningResult result = deploymentManagedVectorProvisioningService.requiresProvisioning(version)
            ? tracked(
                progressTracker,
                "ensure_vector_backend",
                "Create or reconcile managed external vector resources before runtime deployment.",
                () -> deploymentManagedVectorProvisioningService.ensureProvisioned(deployment, version, release.getTargetProfileId())
            )
            : deploymentManagedVectorProvisioningService.ensureProvisioned(deployment, version, release.getTargetProfileId());
        if (deploymentManagedVectorResourceService != null) {
            deploymentManagedVectorResourceService.syncProvisionedResources(deployment, version, release, result);
        }
        return result;
    }

    private void ensureManagedProductDependencies(DeploymentTargetProfileEntity profile,
                                                  DeploymentVersionEntity version,
                                                  ProvisioningProgressTracker progressTracker) {
        if (!hasMcpToolActions(readJson(version.getActionsConfigJson()))) {
            return;
        }
        if (platformManagedProductProvisioningService == null) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "MCP tool actions require managed product dependency reconciliation support."
            );
        }
        tracked(
            progressTracker,
            "ensure_managed_product_dependencies",
            "Reconcile managed product service dependencies required by runtime actions.",
            () -> {
                platformManagedProductProvisioningService.reconcile("mcp-execution-gateway", profile.getId());
                return true;
            }
        );
    }

    private boolean hasMcpToolActions(JsonNode actionsConfig) {
        JsonNode actions = actionsConfig == null ? null : actionsConfig.path("actions");
        if (actions == null || !actions.isArray()) {
            return false;
        }
        for (JsonNode action : actions) {
            String adapterType = text(action, "adapterType", null);
            if ("mcp-tool".equalsIgnoreCase(adapterType)) {
                return true;
            }
            JsonNode execution = action.path("execution");
            if (execution.isObject()) {
                String executionAdapterType = text(execution, "adapterType", null);
                if ("mcp-tool".equalsIgnoreCase(executionAdapterType) || execution.path("mcp").isObject()) {
                    return true;
                }
            }
        }
        return false;
    }

    private CoolifyProvisioningSource resolveProvisioningSource(DeploymentTargetProfileEntity profile,
                                                               JsonNode resourceDefaults,
                                                               DeploymentEntity deployment,
                                                               DeploymentVersionEntity version,
                                                               DeploymentReleaseEntity release,
                                                               JsonNode providerConfigOverride) {
        String sourceStrategy = resolveSourceStrategy(profile, resourceDefaults);
        if ("GIT_SOURCE".equals(sourceStrategy)) {
            RailwayProvisioningPlanSummary plan = railwayProvisioningPlanService.buildPlan(
                deployment,
                version,
                providerConfigOverride,
                profile.getId(),
                release.getRuntimeAuthorityFingerprint()
            );
            RailwayServicePlanSummary runtime = plan.services() == null ? null : plan.services().runtime();
            if (runtime == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coolify Git source requires a runtime service plan.");
            }
            RailwayServicePlanSummary connector = plan.services() == null ? null : plan.services().restConnector();
            RailwayServicePlanSummary vectorizationRunner = plan.services() == null ? null : plan.services().vectorizationRunner();
            String repository = text(resourceDefaults, "gitRepository", plan.repository());
            String branch = text(resourceDefaults, "gitBranch", plan.branch());
            String dockerfilePath = text(resourceDefaults, "dockerfilePath", runtime.dockerfilePath());
            String baseDirectory = text(
                resourceDefaults,
                "baseDirectory",
                StringUtils.hasText(runtime.rootDir()) ? normalizeCoolifyDirectory(runtime.rootDir()) : "/"
            );
            String buildPack = text(resourceDefaults, "buildPack", "dockerfile");
            return new CoolifyProvisioningSource(
                sourceStrategy,
                null,
                plan,
                runtime,
                connector,
                vectorizationRunner,
                normalizeGitRepositoryForCoolify(repository),
                requireText(branch, "Coolify Git source requires a git branch."),
                normalizeCoolifyDirectory(baseDirectory),
                normalizeDockerfileLocation(dockerfilePath),
                buildPack
            );
        }
        if ("IMAGE_SOURCE".equals(sourceStrategy)) {
            RailwayProvisioningPlanSummary plan = railwayProvisioningPlanService.buildPlan(
                deployment,
                version,
                providerConfigOverride,
                profile.getId(),
                release.getRuntimeAuthorityFingerprint()
            );
            RailwayServicePlanSummary runtime = plan.services() == null ? null : plan.services().runtime();
            if (runtime == null) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Coolify image source requires a runtime environment plan."
                );
            }
            RailwayServicePlanSummary connector = plan.services() == null ? null : plan.services().restConnector();
            RailwayServicePlanSummary vectorizationRunner = plan.services() == null ? null : plan.services().vectorizationRunner();
            return new CoolifyProvisioningSource(
                sourceStrategy,
                resolveSourceArtifact(release, resourceDefaults),
                plan,
                runtime,
                connector,
                vectorizationRunner,
                null,
                null,
                null,
                null,
                null
            );
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported Coolify source strategy: " + sourceStrategy);
    }

    private String resolveSourceStrategy(DeploymentTargetProfileEntity profile, JsonNode resourceDefaults) {
        String value = text(resourceDefaults, "sourceStrategy", profile.getSourceStrategy());
        if (!StringUtils.hasText(value)) {
            return "IMAGE_SOURCE";
        }
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private DeploymentSourceArtifactEntity resolveSourceArtifact(DeploymentReleaseEntity release, JsonNode resourceDefaults) {
        if (StringUtils.hasText(release.getSourceArtifactId())) {
            if (releaseArtifacts == null) throw new IllegalStateException("RELEASE_ARTIFACT_RESOLVER_REQUIRED");
            DeploymentSourceArtifactEntity artifact = releaseArtifacts.resolve(release);
            validateDockerImageArtifact(artifact);
            return artifact;
        }
        String serviceName = text(resourceDefaults, "serviceName", DEFAULT_SERVICE_NAME);
        String promotionChannel = text(resourceDefaults, "promotionChannel", DEFAULT_PROMOTION_CHANNEL);
        DeploymentSourceArtifactEntity artifact = sourceArtifactService.latestPromoted(serviceName, promotionChannel);
        validateDockerImageArtifact(artifact);
        return artifact;
    }

    private void validateDockerImageArtifact(DeploymentSourceArtifactEntity artifact) {
        if (!"DOCKER_IMAGE".equalsIgnoreCase(artifact.getArtifactType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coolify source artifact must be DOCKER_IMAGE: " + artifact.getId());
        }
        if (!StringUtils.hasText(artifact.getImageRepository()) || !StringUtils.hasText(artifact.getImageTag())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coolify source artifact must include imageRepository and imageTag: " + artifact.getId());
        }
    }

    static String sourceImageTag(DeploymentSourceArtifactEntity artifact) {
        String digest = artifact.getImageDigest();
        if (!StringUtils.hasText(digest)) return artifact.getImageTag();
        if (!digest.matches("sha256:[a-f0-9]{64}")) throw new IllegalStateException("COOLIFY_SOURCE_IMAGE_DIGEST_INVALID");
        // Coolify converts its documented hash-tag form into repository@sha256:<digest>.
        return "sha256-" + digest.substring("sha256:".length());
    }

    static void requireImmutableRuntimeImage(DeploymentSourceArtifactEntity artifact, CoolifyApplicationSummary application) {
        if (!StringUtils.hasText(artifact.getImageDigest())) return;
        if (application == null || !artifact.getImageRepository().equals(application.imageRepository())
            || !sourceImageTag(artifact).equals(application.imageTag())) {
            throw new IllegalStateException("COOLIFY_SOURCE_IMAGE_DIGEST_DRIFT");
        }
    }

    private String connectorBaseDirectory(RailwayServicePlanSummary connectorPlan, JsonNode resourceDefaults) {
        return serviceBaseDirectory(connectorPlan, resourceDefaults, "connectorBaseDirectory");
    }

    private String serviceBaseDirectory(RailwayServicePlanSummary servicePlan,
                                        JsonNode resourceDefaults,
                                        String overrideField) {
        return normalizeCoolifyDirectory(text(
            resourceDefaults,
            overrideField,
            servicePlan != null && StringUtils.hasText(servicePlan.rootDir())
                ? normalizeCoolifyDirectory(servicePlan.rootDir())
                : "/"
        ));
    }

    private String connectorDockerfileLocation(RailwayServicePlanSummary connectorPlan, JsonNode resourceDefaults) {
        return serviceDockerfileLocation(connectorPlan, resourceDefaults, "connectorDockerfilePath");
    }

    private String serviceDockerfileLocation(RailwayServicePlanSummary servicePlan,
                                             JsonNode resourceDefaults,
                                             String overrideField) {
        return normalizeDockerfileLocation(text(
            resourceDefaults,
            overrideField,
            servicePlan == null ? null : servicePlan.dockerfilePath()
        ));
    }

    private List<CoolifyEnvVar> buildEnvironment(DeploymentEntity deployment,
                                                 DeploymentVersionEntity version,
                                                 DeploymentReleaseEntity release,
                                                 DeploymentTargetProfileEntity profile,
                                                 CoolifyResourceScope scope,
                                                 CoolifyProvisioningSource source,
                                                 RailwayServicePlanSummary servicePlan,
                                                 String serviceRole,
                                                 String runtimeBaseUrl,
                                                 String connectorBaseUrl,
                                                 String publicConnectorBaseUrl,
                                                 CoolifyRuntimeDatabaseBinding runtimeDatabaseBinding) {
        LinkedHashMap<String, CoolifyEnvVar> env = new LinkedHashMap<>();
        if (servicePlan != null && servicePlan.env() != null) {
            for (RailwayEnvVarSummary serviceEnv : servicePlan.env()) {
                putEnv(
                    env,
                    serviceEnv.key(),
                    resolveEnvVarValue(serviceEnv, deployment.getId(), release.getTargetProfileId(), runtimeBaseUrl, connectorBaseUrl),
                    secretPlaceholderName(serviceEnv.value()) != null
                );
            }
        }
        String javaOpts = serviceSetting(
            readJson(profile.getResourceDefaultsJson()),
            serviceRole,
            "JavaOpts",
            "javaOpts"
        );
        if (StringUtils.hasText(javaOpts)) {
            putEnv(env, "JAVA_OPTS", javaOpts);
        }
        putEnv(env, "PLATFORM_DEPLOYMENT_ID", deployment.getId());
        putEnv(env, "PLATFORM_DEPLOYMENT_VERSION_ID", version.getId());
        putEnv(env, "PLATFORM_DEPLOYMENT_RELEASE_ID", release.getId());
        putEnv(env, "PLATFORM_TARGET_PROFILE_ID", profile.getId());
        if (SERVICE_ROLE_RUNTIME.equals(serviceRole)) {
            putEnv(env, "LOOMAI_RUNTIME_RELEASE_ID", release.getId());
            putEnv(env, "LOOMAI_RUNTIME_TARGET_PROFILE_ID", profile.getId());
        }
        putEnv(env, "PLATFORM_COOLIFY_SERVICE_ROLE", serviceRole);
        putEnv(env, "PLATFORM_COOLIFY_PROJECT_UUID", scope.projectUuid());
        putEnv(env, "PLATFORM_COOLIFY_PROJECT_NAME", scope.projectName());
        putEnv(env, "PLATFORM_COOLIFY_ENVIRONMENT_NAME", scope.environmentName());
        putEnv(env, "PLATFORM_COOLIFY_ENVIRONMENT_UUID", scope.environmentUuid());
        putEnv(env, "PLATFORM_SOURCE_STRATEGY", source.sourceStrategy());
        if (source.sourceArtifact() != null) {
            putEnv(env, "PLATFORM_SOURCE_ARTIFACT_ID", source.sourceArtifact().getId());
            putEnv(env, "PLATFORM_SOURCE_ARTIFACT_DIGEST", source.sourceArtifact().getImageDigest());
            putEnv(env, "PLATFORM_SOURCE_ARTIFACT_MANIFEST_HASH", source.sourceArtifact().getCapabilityManifestHash());
            putEnv(env, "PLATFORM_SOURCE_COMMIT", source.sourceArtifact().getGitCommitSha());
            putEnv(env, "APP_BUILD_COMMIT", source.sourceArtifact().getGitCommitSha());
        }
        if (source.gitSource()) {
            putEnv(env, "PLATFORM_SOURCE_REPOSITORY", source.gitRepository());
            putEnv(env, "PLATFORM_SOURCE_BRANCH", source.gitBranch());
            putEnv(env, "PLATFORM_DOCKERFILE_LOCATION", servicePlan != null
                ? normalizeDockerfileLocation(servicePlan.dockerfilePath())
                : source.dockerfileLocation());
        }
        if (SERVICE_ROLE_RUNTIME.equals(serviceRole) && runtimeDatabaseBinding != null) {
            putEnv(env, "SPRING_DATASOURCE_URL", runtimeDatabaseBinding.jdbcUrl(), !runtimeDatabaseBinding.managed());
            putEnv(env, "SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.postgresql.Driver");
            putEnv(env, "SPRING_DATASOURCE_USERNAME", runtimeDatabaseBinding.username(), true);
            putEnv(env, "SPRING_DATASOURCE_PASSWORD", runtimeDatabaseBinding.password(), true);
            putEnv(env, "PLATFORM_RUNTIME_DATABASE_MODE", runtimeDatabaseBinding.mode());
            if (runtimeDatabaseBinding.managed()) {
                putEnv(env, "PLATFORM_RUNTIME_DATABASE_RESOURCE_UUID", runtimeDatabaseBinding.database().uuid());
                putEnv(env, "PLATFORM_RUNTIME_DATABASE_PASSWORD_SECRET", runtimeDatabaseBinding.passwordSecretName(), true);
            } else {
                putEnv(env, "PLATFORM_AI_STATE_STORAGE_BINDING_ID", runtimeDatabaseBinding.bindingId());
                putEnv(env, "PLATFORM_AI_STATE_STORAGE_FINGERPRINT", runtimeDatabaseBinding.fingerprint());
                putEnv(env, "SPRING_FLYWAY_ENABLED", "true");
                putEnv(env, "SPRING_FLYWAY_DEFAULT_SCHEMA", runtimeDatabaseBinding.runtimeSchema(), true);
                putEnv(env, "SPRING_FLYWAY_SCHEMAS", runtimeDatabaseBinding.runtimeSchema(), true);
                putEnv(env, "SPRING_FLYWAY_CREATE_SCHEMAS", "false");
                putEnv(env, "SPRING_FLYWAY_URL", runtimeDatabaseBinding.jdbcUrl(), true);
                putEnv(env, "SPRING_FLYWAY_USER", runtimeDatabaseBinding.username(), true);
                putEnv(env, "SPRING_FLYWAY_PASSWORD", runtimeDatabaseBinding.password(), true);
                putEnv(env, "SPRING_FLYWAY_CLEAN_DISABLED", "true");
                putEnv(env, "SPRING_DATASOURCE_HIKARI_SCHEMA", runtimeDatabaseBinding.runtimeSchema(), true);
                putEnv(env, "SPRING_DATASOURCE_HIKARI_JDBC_URL", runtimeDatabaseBinding.jdbcUrl(), true);
                putEnv(env, "SPRING_DATASOURCE_HIKARI_USERNAME", runtimeDatabaseBinding.username(), true);
                putEnv(env, "SPRING_DATASOURCE_HIKARI_PASSWORD", runtimeDatabaseBinding.password(), true);
                putEnv(env, "SPRING_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA", runtimeDatabaseBinding.runtimeSchema(), true);
            }
        }
        if (SERVICE_ROLE_RUNTIME.equals(serviceRole)
            && requiresDurableConnectorPersistence(version)
            && StringUtils.hasText(publicConnectorBaseUrl)) {
            putEnv(env, "AI_FABRIC_CONNECTOR_PUBLIC_BASE_URL", publicConnectorBaseUrl);
        }
        if (SERVICE_ROLE_CONNECTOR.equals(serviceRole)
            && runtimeDatabaseBinding != null
            && requiresDurableConnectorPersistence(version)) {
            putEnv(env, "REST_CONNECTOR_JDBC_URL", runtimeDatabaseBinding.connectorJdbcUrl(), !runtimeDatabaseBinding.managed());
            if (runtimeDatabaseBinding.managed()) {
                putEnv(env, "REST_CONNECTOR_BOOTSTRAP_JDBC_URL", runtimeDatabaseBinding.jdbcUrl());
                putEnv(env, "REST_CONNECTOR_BOOTSTRAP_JDBC_USERNAME", runtimeDatabaseBinding.username(), true);
                putEnv(env, "REST_CONNECTOR_BOOTSTRAP_JDBC_PASSWORD", runtimeDatabaseBinding.password(), true);
                putEnv(env, "PLATFORM_INTEGRATION_DATABASE_RESOURCE_UUID", runtimeDatabaseBinding.database().uuid());
            } else {
                CONNECTOR_DATABASE_BOOTSTRAP_ENV_KEYS.forEach(env::remove);
                putEnv(env, "REST_CONNECTOR_PERSISTENCE_ENABLED", "true");
                putEnv(env, "REST_CONNECTOR_JDBC_USERNAME", runtimeDatabaseBinding.username(), true);
                putEnv(env, "REST_CONNECTOR_JDBC_PASSWORD", runtimeDatabaseBinding.password(), true);
                putEnv(env, "REST_CONNECTOR_PERSISTENCE_SCHEMA", runtimeDatabaseBinding.connectorSchema(), true);
                putEnv(env, "PLATFORM_AI_STATE_STORAGE_BINDING_ID", runtimeDatabaseBinding.bindingId());
                putEnv(env, "PLATFORM_AI_STATE_STORAGE_FINGERPRINT", runtimeDatabaseBinding.fingerprint());
            }
        }
        mergeFrozenRuntimeAuthorityEnvironment(deployment, version, release, serviceRole, env);
        mergeFrozenExternalIntegrationEnvironment(deployment, version, release, serviceRole, env);
        if (SERVICE_ROLE_RUNTIME.equals(serviceRole)) {
            if (providerUsageReleaseCoordinator != null) {
                providerUsageReleaseCoordinator.environment(deployment, release)
                    .forEach((key, value) -> putEnv(env, key, value));
            } else if (customerOfferDeploymentRepository != null
                && customerOfferDeploymentRepository.existsById(deployment.getId())) {
                throw new IllegalStateException("Customer provider-usage accounting coordinator is required.");
            }
            Map<String, String> runtimeEnvironment = new LinkedHashMap<>();
            env.forEach((key, value) -> runtimeEnvironment.put(key, value.value()));
            new com.ai.fabric.platform.backend.deployment.operations.lifecycle.runtimeadapter.RuntimeDrainEnvironmentPlan(objectMapper)
                .plan(deployment, version, release, runtimeEnvironment, runtimeDatabaseBinding != null,
                    source != null && source.connectorPlan() != null,
                    source != null && source.vectorizationRunnerPlan() != null,
                    source != null && source.sourceArtifact() != null ? source.sourceArtifact().getCapabilityManifestJson() : null)
                .forEach((key, value) -> putEnv(env, key, value));
        }
        putEnv(env, "PLATFORM_ENVIRONMENT_NAME", profile.getEnvironmentName());
        return withPreviewEnvironment(new ArrayList<>(env.values()));
    }

    void mergeFrozenRuntimeAuthorityEnvironment(DeploymentEntity deployment,
                                                DeploymentVersionEntity version,
                                                DeploymentReleaseEntity release,
                                                String serviceRole,
                                                LinkedHashMap<String, CoolifyEnvVar> env) {
        boolean hasSnapshot = StringUtils.hasText(release.getRuntimeAuthoritySnapshotId());
        boolean hasFingerprint = StringUtils.hasText(release.getRuntimeAuthorityFingerprint());
        boolean authorityRequired = ManagedDeploymentProfileCatalog.publicRuntimeRequested(
            readJson(version.getSecurityConfigJson())
        ) || (customerOfferDeploymentRepository != null
            && customerOfferDeploymentRepository.existsById(deployment.getId()));
        if (hasSnapshot != hasFingerprint || (authorityRequired && !hasSnapshot)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Coolify provisioning requires complete frozen target authority evidence for this release."
            );
        }
        if (!hasSnapshot) return;
        if (deploymentRuntimeAuthorityService == null) {
            throw new IllegalStateException("Runtime authority materializer is unavailable for Coolify provisioning.");
        }
        var plan = deploymentRuntimeAuthorityService.releaseEnvironmentPlanForProvisioning(
            deployment,
            version,
            release
        );
        Map<String, String> values = SERVICE_ROLE_CONNECTOR.equals(serviceRole)
            ? plan.connector()
            : SERVICE_ROLE_RUNTIME.equals(serviceRole) ? plan.runtime() : Map.of();
        values.forEach((key, value) -> {
            String secretName = secretPlaceholderName(value);
            putEnv(env, key, resolveAuthorityEnvironmentValue(secretName, value, deployment.getId(), release.getTargetProfileId()), secretName != null);
        });
    }

    private String resolveAuthorityEnvironmentValue(String secretName, String value, String deploymentId, String targetProfileId) {
        if (secretName == null) return value;
        if (platformSecretService == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Platform secret service is unavailable for target authority provisioning.");
        }
        String resolved = platformSecretService.resolveSecretForDeployment(secretName, deploymentId, targetProfileId);
        if (!StringUtils.hasText(resolved)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Missing managed target authority secret required for Coolify provisioning."
            );
        }
        return resolved;
    }

    void mergeFrozenExternalIntegrationEnvironment(DeploymentEntity deployment, DeploymentVersionEntity version,
        DeploymentReleaseEntity release, String serviceRole, LinkedHashMap<String, CoolifyEnvVar> env) {
        if (!SERVICE_ROLE_CONNECTOR.equals(serviceRole)) return;
        if (!com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationRequirement
            .required(readJson(version.getCompositionProvenanceJson()))) return;
        var plan = externalIntegrations.releaseEnvironmentForProvisioning(deployment, version, release);
        plan.configEnvironment().forEach((name, value) -> {
            if (env.containsKey(name)) throw new IllegalStateException("EXTERNAL_INTEGRATION_ENVIRONMENT_COLLISION");
            putEnv(env, name, value, false);
        });
        plan.secretEnvironment().forEach((name, reference) -> {
            if (env.containsKey(name)) throw new IllegalStateException("EXTERNAL_INTEGRATION_ENVIRONMENT_COLLISION");
            putEnv(env, name, resolveAuthorityEnvironmentValue(reference, null, deployment.getId(), release.getTargetProfileId()), true);
        });
    }

    private List<CoolifyEnvVar> withPreviewEnvironment(List<CoolifyEnvVar> envVars) {
        List<CoolifyEnvVar> expanded = new ArrayList<>();
        for (CoolifyEnvVar envVar : envVars) {
            expanded.add(envVar);
            if (!envVar.preview()) {
                expanded.add(new CoolifyEnvVar(
                    envVar.key(),
                    envVar.value(),
                    true,
                    envVar.literal(),
                    envVar.multiline(),
                    envVar.shownOnce()
                ));
            }
        }
        return expanded;
    }

    private void putEnv(LinkedHashMap<String, CoolifyEnvVar> env, String key, String value) {
        putEnv(env, key, value, false);
    }

    private void putEnv(LinkedHashMap<String, CoolifyEnvVar> env, String key, String value, boolean sensitive) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        env.put(key, env(key, value, sensitive));
    }

    private CoolifyEnvVar env(String key, String value, boolean sensitive) {
        return new CoolifyEnvVar(key, value, false, true, false, sensitive);
    }

    private String resolveEnvVarValue(RailwayEnvVarSummary envVar, String deploymentId, String targetProfileId,
                                      String runtimeBaseUrl, String connectorBaseUrl) {
        String serviceBaseUrl = RailwayApiProvisioningProvider.resolveServiceBaseUrl(
            envVar.key(),
            runtimeBaseUrl,
            connectorBaseUrl
        );
        if (serviceBaseUrl != null) {
            return serviceBaseUrl;
        }
        String secretName = secretPlaceholderName(envVar.value());
        if (secretName == null) {
            return envVar.value();
        }
        if (platformSecretService == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Platform secret service is unavailable for Coolify provisioning.");
        }
        String resolved = platformSecretService.resolveSecretForDeployment(secretName, deploymentId, targetProfileId);
        if (!StringUtils.hasText(resolved)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Missing platform secret '" + secretName + "' required for Coolify provisioning."
            );
        }
        return resolved;
    }

    private String secretPlaceholderName(String value) {
        if (value == null || !value.startsWith("${secret:") || !value.endsWith("}")) {
            return null;
        }
        String secretName = value.substring("${secret:".length(), value.length() - 1).trim();
        return StringUtils.hasText(secretName) ? secretName : null;
    }

    private String resolveApplicationName(DeploymentEntity deployment,
                                          JsonNode resourceDefaults,
                                          CoolifyProvisioningSource source,
                                          RailwayServicePlanSummary servicePlan,
                                          String serviceRole) {
        String configured = SERVICE_ROLE_CONNECTOR.equals(serviceRole)
            ? text(resourceDefaults, "connectorApplicationName", null)
            : SERVICE_ROLE_VECTORIZATION_RUNNER.equals(serviceRole)
                ? text(resourceDefaults, "vectorizationRunnerApplicationName", null)
            : text(resourceDefaults, "applicationName", null);
        if (StringUtils.hasText(configured)) {
            return normalizeName(configured);
        }
        String prefix = SERVICE_ROLE_CONNECTOR.equals(serviceRole)
            ? text(resourceDefaults, "connectorApplicationNamePrefix", null)
            : SERVICE_ROLE_VECTORIZATION_RUNNER.equals(serviceRole)
                ? text(resourceDefaults, "vectorizationRunnerApplicationNamePrefix", null)
            : text(resourceDefaults, "applicationNamePrefix", null);
        if (StringUtils.hasText(prefix)) {
            return normalizeName(prefix + "-" + deployment.getId());
        }
        if (source.gitSource()
            && servicePlan != null
            && StringUtils.hasText(servicePlan.serviceName())) {
            return normalizeName(servicePlan.serviceName());
        }
        return normalizeName("ai-fabric-" + serviceRole + "-" + deployment.getId());
    }

    private String servicePortsExposes(JsonNode resourceDefaults,
                                       CoolifyTargetProfileConfig config,
                                       String serviceRole) {
        String configured = SERVICE_ROLE_CONNECTOR.equals(serviceRole)
            ? text(resourceDefaults, "connectorPortsExposes", null)
            : SERVICE_ROLE_VECTORIZATION_RUNNER.equals(serviceRole)
                ? text(resourceDefaults, "vectorizationRunnerPortsExposes", null)
                : SERVICE_ROLE_RUNTIME.equals(serviceRole)
                    ? text(resourceDefaults, "runtimePortsExposes", null)
                    : text(resourceDefaults, "portsExposes", null);
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        String defaultPort = defaultServicePort(serviceRole);
        return StringUtils.hasText(defaultPort) ? defaultPort : config.defaultPortsExposes();
    }

    private String serviceHealthCheckPort(JsonNode resourceDefaults,
                                          CoolifyTargetProfileConfig config,
                                          String serviceRole) {
        String configured = SERVICE_ROLE_CONNECTOR.equals(serviceRole)
            ? text(resourceDefaults, "connectorHealthCheckPort", null)
            : SERVICE_ROLE_VECTORIZATION_RUNNER.equals(serviceRole)
                ? text(resourceDefaults, "vectorizationRunnerHealthCheckPort", null)
                : SERVICE_ROLE_RUNTIME.equals(serviceRole)
                    ? text(resourceDefaults, "runtimeHealthCheckPort", null)
                    : text(resourceDefaults, "healthCheckPort", null);
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        String defaultPort = defaultServicePort(serviceRole);
        return StringUtils.hasText(defaultPort) ? defaultPort : config.defaultHealthCheckPort();
    }

    private String serviceHealthCheckPath(JsonNode resourceDefaults,
                                          CoolifyTargetProfileConfig config,
                                          String serviceRole) {
        String configured = SERVICE_ROLE_CONNECTOR.equals(serviceRole)
            ? text(resourceDefaults, "connectorHealthCheckPath", null)
            : SERVICE_ROLE_VECTORIZATION_RUNNER.equals(serviceRole)
                ? text(resourceDefaults, "vectorizationRunnerHealthCheckPath", null)
                : SERVICE_ROLE_RUNTIME.equals(serviceRole)
                    ? text(resourceDefaults, "runtimeHealthCheckPath", null)
                    : text(resourceDefaults, "healthCheckPath", null);
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        return defaultServiceHealthCheckPath(serviceRole, config);
    }

    private CoolifyApplicationRuntimeSettings serviceRuntimeSettings(JsonNode resourceDefaults,
                                                                     String serviceRole) {
        // Every role starts a JVM, including roles without their own target-profile health overrides.
        return new CoolifyApplicationRuntimeSettings(
            positiveIntegerSetting(resourceDefaults, serviceRole, "HealthCheckIntervalSeconds", "healthCheckIntervalSeconds", 10),
            positiveIntegerSetting(resourceDefaults, serviceRole, "HealthCheckTimeoutSeconds", "healthCheckTimeoutSeconds", 5),
            positiveIntegerSetting(resourceDefaults, serviceRole, "HealthCheckRetries", "healthCheckRetries", 18),
            positiveIntegerSetting(resourceDefaults, serviceRole, "HealthCheckStartPeriodSeconds", "healthCheckStartPeriodSeconds", 180),
            serviceSetting(resourceDefaults, serviceRole, "LimitsMemory", "limitsMemory"),
            serviceSetting(resourceDefaults, serviceRole, "LimitsMemorySwap", "limitsMemorySwap"),
            serviceSetting(resourceDefaults, serviceRole, "LimitsMemoryReservation", "limitsMemoryReservation"),
            serviceSetting(resourceDefaults, serviceRole, "LimitsCpus", "limitsCpus"),
            serviceSetting(resourceDefaults, serviceRole, "LimitsCpuSet", "limitsCpuSet")
        );
    }

    private Integer positiveIntegerSetting(JsonNode resourceDefaults,
                                           String serviceRole,
                                           String roleSuffix,
                                           String genericField,
                                           int defaultValue) {
        String field = serviceRolePrefix(serviceRole) + roleSuffix;
        String value = textFirst(resourceDefaults, field, genericField);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Coolify target profile resource default " + field + " must be a positive integer."
            );
        }
    }

    private String serviceSetting(JsonNode resourceDefaults,
                                  String serviceRole,
                                  String roleSuffix,
                                  String genericField) {
        return textFirst(resourceDefaults, serviceRolePrefix(serviceRole) + roleSuffix, genericField);
    }

    private String serviceRolePrefix(String serviceRole) {
        return switch (serviceRole) {
            case SERVICE_ROLE_RUNTIME -> "runtime";
            case SERVICE_ROLE_CONNECTOR -> "connector";
            case SERVICE_ROLE_VECTORIZATION_RUNNER -> "vectorizationRunner";
            default -> "";
        };
    }

    private String defaultServiceHealthCheckPath(String serviceRole, CoolifyTargetProfileConfig config) {
        return switch (serviceRole) {
            case SERVICE_ROLE_RUNTIME, SERVICE_ROLE_CONNECTOR, SERVICE_ROLE_VECTORIZATION_RUNNER ->
                DEFAULT_SERVICE_HEALTH_CHECK_PATH;
            default -> config.defaultHealthCheckPath();
        };
    }

    private String defaultServicePort(String serviceRole) {
        return switch (serviceRole) {
            case SERVICE_ROLE_RUNTIME -> DEFAULT_RUNTIME_PORT;
            case SERVICE_ROLE_CONNECTOR -> DEFAULT_CONNECTOR_PORT;
            case SERVICE_ROLE_VECTORIZATION_RUNNER -> DEFAULT_VECTORIZATION_RUNNER_PORT;
            default -> null;
        };
    }

    private void waitForApplicationAbsent(CoolifyConnection connection, String applicationUuid) {
        Instant deadline = Instant.now().plus(DEFAULT_STALE_DELETE_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Optional<CoolifyApplicationSummary> observed = coolifyApiClient.getApplication(connection, applicationUuid);
            if (observed == null) throw CoolifyResourceOwnership.reconciliationRequired();
            if (observed.isEmpty()) {
                return;
            }
            try {
                Thread.sleep(DEFAULT_STALE_DELETE_POLL_INTERVAL.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                    "Interrupted while waiting for Coolify application " + applicationUuid + " to be deleted.",
                    ex
                );
            }
        }
        throw new IllegalStateException(
            "Timed out waiting for Coolify application " + applicationUuid + " to be deleted."
        );
    }

    private void waitForDatabaseAbsent(CoolifyConnection connection, String databaseUuid) {
        Instant deadline = Instant.now().plus(DEFAULT_STALE_DELETE_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Optional<CoolifyDatabaseSummary> observed = coolifyApiClient.getDatabase(connection, databaseUuid);
            if (observed == null) throw CoolifyResourceOwnership.reconciliationRequired();
            if (observed.isEmpty()) {
                return;
            }
            try {
                Thread.sleep(DEFAULT_STALE_DELETE_POLL_INTERVAL.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                    "Interrupted while waiting for Coolify database " + databaseUuid + " to be deleted.",
                    ex
                );
            }
        }
        throw new IllegalStateException(
            "Timed out waiting for Coolify database " + databaseUuid + " to be deleted."
        );
    }

    private CoolifyApplicationSummary waitForApplicationReady(CoolifyConnection connection,
                                                              String applicationUuid,
                                                              JsonNode resourceDefaults,
                                                              CoolifyApplicationSummary fallback,
                                                              CoolifyActionResponse deployResponse,
                                                              ProvisioningProgressTracker progressTracker) {
        Duration timeout = durationSeconds(
            resourceDefaults,
            "deploySettleTimeoutSeconds",
            positiveDurationSeconds(connection.config().deploymentTimeoutSeconds(), DEFAULT_DEPLOY_SETTLE_TIMEOUT)
        );
        Duration pollInterval = durationSeconds(
            resourceDefaults,
            "deploySettlePollSeconds",
            positiveDurationSeconds(connection.config().deploymentPollIntervalSeconds(), DEFAULT_DEPLOY_SETTLE_POLL_INTERVAL)
        );
        Instant deadline = Instant.now().plus(timeout);
        CoolifyApplicationSummary latest = observeCoolifyApplication(connection, applicationUuid).orElse(fallback);
        String deploymentUuid = deployResponse == null ? null : deployResponse.deploymentUuid();
        CoolifyDeploymentSummary deployment = observeCoolifyDeployment(connection, deploymentUuid);
        while (!coolifyDeploymentReady(deployment, deploymentUuid) && Instant.now().isBefore(deadline)) {
            progressTracker.heartbeat();
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                    "Interrupted while waiting for Coolify deployment "
                        + deploymentUuid + " to finish.",
                    ex
                );
            }
            latest = observeCoolifyApplication(connection, applicationUuid).orElse(latest);
            deployment = observeCoolifyDeployment(connection, deploymentUuid);
        }
        if (!coolifyDeploymentReady(deployment, deploymentUuid)) {
            throw new IllegalStateException(
                "Timed out waiting for Coolify deployment to finish for application " + applicationUuid + "."
            );
        }
        if (coolifyDeploymentFailed(deployment)) {
            throw new IllegalStateException(
                "Coolify deployment failed for application " + applicationUuid
                    + " (deployment=" + deployment.deploymentUuid()
                    + ", status=" + deployment.status() + ")."
            );
        }
        latest = observeCoolifyApplication(connection, applicationUuid).orElse(latest);
        while (!applicationReady(latest) && Instant.now().isBefore(deadline)) {
            progressTracker.heartbeat();
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                    "Interrupted while waiting for Coolify application "
                        + applicationUuid + " to become ready.",
                    ex
                );
            }
            latest = observeCoolifyApplication(connection, applicationUuid).orElse(latest);
        }
        if (!applicationReady(latest)) {
            throw new IllegalStateException(
                "Timed out waiting for Coolify application " + applicationUuid + " to become ready."
            );
        }
        return latest;
    }

    private Optional<CoolifyApplicationSummary> observeCoolifyApplication(CoolifyConnection connection, String applicationUuid) {
        try {
            return coolifyApiClient.getApplication(connection, applicationUuid);
        } catch (CoolifyApiException ex) {
            if (isTransientCoolifyObservationFailure(ex)) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    private CoolifyDeploymentSummary observeCoolifyDeployment(CoolifyConnection connection, String deploymentUuid) {
        if (!StringUtils.hasText(deploymentUuid)) {
            return null;
        }
        try {
            return coolifyApiClient.getDeployment(connection, deploymentUuid).orElse(null);
        } catch (CoolifyApiException ex) {
            if (isTransientCoolifyObservationFailure(ex)) {
                return null;
            }
            throw ex;
        }
    }

    private boolean isTransientCoolifyObservationFailure(CoolifyApiException ex) {
        return ex != null && ex.statusCode() == 502;
    }

    private boolean coolifyDeploymentReady(CoolifyDeploymentSummary deployment, String deploymentUuid) {
        if (!StringUtils.hasText(deploymentUuid)) {
            return true;
        }
        return deployment != null && (coolifyDeploymentFinished(deployment) || coolifyDeploymentFailed(deployment));
    }

    private boolean coolifyDeploymentFinished(CoolifyDeploymentSummary deployment) {
        if (deployment == null) {
            return false;
        }
        String normalized = normalizeStatus(deployment.status(), "");
        return normalized.equals("FINISHED")
            || normalized.equals("SUCCESS")
            || normalized.equals("SUCCEEDED")
            || normalized.equals("COMPLETED")
            || StringUtils.hasText(deployment.finishedAt()) && !coolifyDeploymentFailed(deployment);
    }

    private boolean coolifyDeploymentFailed(CoolifyDeploymentSummary deployment) {
        if (deployment == null) {
            return false;
        }
        String normalized = normalizeStatus(deployment.status(), "");
        return normalized.contains("FAILED")
            || normalized.contains("ERROR")
            || normalized.contains("CANCELLED")
            || normalized.contains("CANCELED");
    }

    private Duration durationSeconds(JsonNode root, String field, Duration fallback) {
        if (root == null || !root.has(field)) {
            return fallback;
        }
        long seconds = root.path(field).asLong(fallback.toSeconds());
        if (seconds <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofSeconds(seconds);
    }

    private Duration positiveDurationSeconds(int seconds, Duration fallback) {
        return seconds > 0 ? Duration.ofSeconds(seconds) : fallback;
    }

    private boolean applicationReady(CoolifyApplicationSummary application) {
        if (application == null || !StringUtils.hasText(application.status())) {
            return false;
        }
        String normalized = normalizeStatus(application.status(), "");
        return normalized.startsWith("RUNNING") && !normalized.contains("UNHEALTHY");
    }

    private boolean applicationHasActiveContainer(CoolifyApplicationSummary application) {
        if (application == null || !StringUtils.hasText(application.status())) {
            return false;
        }
        String normalized = normalizeStatus(application.status(), "");
        return normalized.startsWith("RUNNING")
            || normalized.startsWith("STARTING")
            || normalized.startsWith("RESTARTING")
            || normalized.startsWith("DEPLOYING");
    }

    private boolean applicationStopped(CoolifyApplicationSummary application) {
        if (application == null || !StringUtils.hasText(application.status())) {
            return false;
        }
        String normalized = normalizeStatus(application.status(), "");
        return normalized.startsWith("EXITED")
            || normalized.startsWith("STOPPED")
            || normalized.startsWith("INACTIVE")
            || normalized.startsWith("OFFLINE");
    }

    private boolean databaseReady(CoolifyDatabaseSummary database) {
        if (database == null || !StringUtils.hasText(database.status())) {
            return false;
        }
        String normalized = normalizeStatus(database.status(), "");
        return normalized.startsWith("RUNNING")
            || normalized.equals("HEALTHY")
            || normalized.equals("STARTED")
            || normalized.equals("ACTIVE");
    }

    private boolean applicationsReady(CoolifyApplicationSummary runtimeApplication,
                                      CoolifyApplicationSummary connectorApplication,
                                      CoolifyApplicationSummary vectorizationRunnerApplication) {
        return applicationReady(runtimeApplication)
            && (connectorApplication == null || applicationReady(connectorApplication))
            && (vectorizationRunnerApplication == null || applicationReady(vectorizationRunnerApplication));
    }

    private String normalizeName(String value) {
        String normalized = value == null ? "ai-fabric-runtime" : value.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        return normalized.length() > 48 ? normalized.substring(0, 48).replaceAll("-+$", "") : normalized;
    }

    private boolean hasExternalHttpDataset(DeploymentVersionEntity version) {
        if (version == null) {
            return false;
        }
        JsonNode config = readJson(version.getMarketplaceDatasetConfigJson());
        JsonNode datasets = config.path("datasets");
        if (!datasets.isArray()) {
            return false;
        }
        for (JsonNode dataset : datasets) {
            if ("EXTERNAL_SYNC_HTTP".equals(dataset.path("ingestionMode").asText(""))) {
                return true;
            }
        }
        return false;
    }

    private boolean requiresDurableConnectorPersistence(DeploymentVersionEntity version) {
        return hasExternalHttpDataset(version) || hasExecutionExtension(version, "CONVERSATION_HANDOVER");
    }

    private boolean hasExecutionExtension(DeploymentVersionEntity version, String extension) {
        if (version == null || !StringUtils.hasText(extension)) {
            return false;
        }
        JsonNode extensions = readJson(version.getBehaviorConfigJson()).path("executionExtensions");
        if (!extensions.isArray()) {
            return false;
        }
        for (JsonNode configuredExtension : extensions) {
            if (extension.equals(configuredExtension.asText(""))) {
                return true;
            }
        }
        return false;
    }

    void validateExpectedWebhookConnectorDomain(DeploymentVersionEntity version, String connectorDomain) {
        if (!hasExpectedProviderWebhook(version)) {
            return;
        }
        if (!StringUtils.hasText(connectorDomain) || !connectorDomain.trim().startsWith("https://")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Provider webhook registration requires a deterministic HTTPS connector domain."
            );
        }
    }

    private boolean hasExpectedProviderWebhook(DeploymentVersionEntity version) {
        if (version == null) {
            return false;
        }
        JsonNode datasets = readJson(version.getMarketplaceDatasetConfigJson()).path("datasets");
        if (!datasets.isArray()) {
            return false;
        }
        for (JsonNode dataset : datasets) {
            if (!"EXTERNAL_SYNC_HTTP".equals(dataset.path("ingestionMode").asText(""))) {
                continue;
            }
            JsonNode webhook = dataset.path("syncConnector").path("webhook");
            if (webhook.isObject() && webhook.path("registrationExpected").asBoolean(false)) {
                return true;
            }
        }
        return false;
    }

    private String integrationInternalName(DeploymentEntity deployment, String serviceRole) {
        return normalizeName("loomai-" + serviceRole + "-" + deployment.getId());
    }

    private String internalServicePort(String portsExposes, String healthCheckPort, String fallback) {
        String candidate = StringUtils.hasText(portsExposes)
            ? portsExposes.split(",", 2)[0].trim()
            : StringUtils.hasText(healthCheckPort) ? healthCheckPort.trim() : fallback;
        if (candidate.contains(":")) {
            candidate = candidate.substring(candidate.lastIndexOf(':') + 1).trim();
        }
        return candidate.matches("[0-9]{1,5}") ? candidate : fallback;
    }

    private String normalizeScopedName(String value, int maxLength) {
        String normalized = value == null ? "unknown" : value.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        if (!StringUtils.hasText(normalized)) {
            normalized = "unknown";
        }
        return normalized.length() > maxLength
            ? normalized.substring(0, maxLength).replaceAll("-+$", "")
            : normalized;
    }

    private String normalizeDatabaseIdentifier(String value) {
        String normalized = value == null ? "runtime" : value.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_]+", "_").replaceAll("_{2,}", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        if (!StringUtils.hasText(normalized)) {
            normalized = "runtime";
        }
        if (!Character.isLetter(normalized.charAt(0))) {
            normalized = "r_" + normalized;
        }
        return normalized.length() > 48 ? normalized.substring(0, 48).replaceAll("_+$", "") : normalized;
    }

    private String runtimePostgresPasswordSecretName(String deploymentId, String profileId) {
        return "MANAGED_RUNTIME_POSTGRES_PASSWORD_DEP_"
            + secretToken(deploymentId)
            + "_PROFILE_"
            + secretToken(profileId);
    }

    private String secretToken(String value) {
        String normalized = value == null ? "UNKNOWN" : value.toUpperCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^A-Z0-9]+", "_").replaceAll("_{2,}", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        return StringUtils.hasText(normalized) ? normalized : "UNKNOWN";
    }

    private String generateSecretValue() {
        byte[] bytes = new byte[36];
        SECRET_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String resolveDomain(DeploymentEntity deployment,
                                 JsonNode resourceDefaults,
                                 CoolifyTargetProfileConfig config,
                                 boolean autogenerateDomain,
                                 String serviceRole) {
        if (autogenerateDomain) {
            return null;
        }
        String configured = SERVICE_ROLE_CONNECTOR.equals(serviceRole)
            ? text(resourceDefaults, "connectorDomain", null)
            : text(resourceDefaults, "domain", null);
        if (StringUtils.hasText(configured)) {
            return normalizeCoolifyDomain(configured, config.forceHttps());
        }
        String suffix = config.defaultPublicDomainSuffix();
        if (!StringUtils.hasText(suffix)) {
            return null;
        }
        String name = SERVICE_ROLE_CONNECTOR.equals(serviceRole)
            ? deployment.getId() + "-connector"
            : SERVICE_ROLE_VECTORIZATION_RUNNER.equals(serviceRole)
                ? deployment.getId() + "-vectorization-runner"
            : deployment.getId();
        return normalizeCoolifyDomain(normalizeName(name) + "." + suffix, config.forceHttps());
    }

    private String normalizeCoolifyDomain(String domain, boolean forceHttps) {
        if (!StringUtils.hasText(domain)) {
            return null;
        }
        String trimmed = domain.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        String scheme = forceHttps ? "https://" : "http://";
        return scheme + trimmed;
    }

    private CoolifyConnection connectionForHandle(DeploymentProviderResourceHandleEntity handle) {
        if (PreservedExternalBindingKinds.isPreserved(handle.getResourceKind())) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "CUSTOMER_SOURCE_PRESERVED");
        }
        DeploymentTargetProfileEntity profile = targetProfileRepository.findById(handle.getTargetProfileId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Deployment target profile not found: " + handle.getTargetProfileId()
            ));
        return targetProfileResolver.requireConnection(profile);
    }

    private DeploymentTargetProfileEntity requireActiveProfile(String targetProfileId) {
        DeploymentTargetProfileEntity profile = targetProfileRepository.findById(targetProfileId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Deployment target profile not found: " + targetProfileId
            ));
        if (!profile.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deployment target profile is not active: " + targetProfileId);
        }
        return profile;
    }

    private DeploymentProviderResourceActionSummary actionSummary(DeploymentProviderResourceHandleEntity handle,
                                                                  String action,
                                                                  String status,
                                                                  String message,
                                                                  String providerOperationId,
                                                                  JsonNode details) {
        return new DeploymentProviderResourceActionSummary(
            handle.getId(),
            handle.getProviderType(),
            action,
            status,
            message,
            providerOperationId,
            safeActionDetails(details),
            Instant.now()
        );
    }

    private JsonNode safeApplicationDetails(CoolifyApplicationSummary application) {
        ObjectNode details = objectMapper.createObjectNode();
        putIfText(details, "uuid", application.uuid());
        putIfText(details, "name", application.name());
        putIfText(details, "status", application.status());
        putIfText(details, "fqdn", application.fqdn());
        putIfText(details, "imageRepository", application.imageRepository());
        putIfText(details, "imageTag", application.imageTag());

        JsonNode raw = application.raw();
        copyText(raw, details, "git_repository");
        copyText(raw, details, "git_branch");
        copyText(raw, details, "build_pack");
        copyText(raw, details, "base_directory");
        copyText(raw, details, "dockerfile_location");
        copyText(raw, details, "ports_exposes");
        copyText(raw, details, "health_check_enabled");
        copyText(raw, details, "health_check_path");
        copyText(raw, details, "health_check_port");
        copyText(raw, details, "health_check_interval");
        copyText(raw, details, "health_check_timeout");
        copyText(raw, details, "health_check_retries");
        copyText(raw, details, "health_check_start_period");
        copyText(raw, details, "limits_memory");
        copyText(raw, details, "limits_memory_swap");
        copyText(raw, details, "limits_memory_reservation");
        copyText(raw, details, "limits_cpus");
        copyText(raw, details, "limits_cpuset");
        copyText(raw, details, "project_uuid");
        copyText(raw, details, "environment_uuid");
        copyText(raw, details, "environment_name");
        copyText(raw, details, "created_at");
        copyText(raw, details, "updated_at");
        putIfText(details, "projectName", raw.path("project").path("name").asText(null));
        putIfText(details, "projectUuid", raw.path("project").path("uuid").asText(null));
        putIfText(details, "environmentName", raw.path("environment").path("name").asText(null));
        putIfText(details, "environmentUuid", raw.path("environment").path("uuid").asText(null));
        putIfText(details, "destinationUuid", raw.path("destination").path("uuid").asText(null));
        putIfText(details, "destinationNetwork", raw.path("destination").path("network").asText(null));
        putIfText(details, "serverUuid", raw.path("destination").path("server").path("uuid").asText(null));
        putIfText(details, "serverName", raw.path("destination").path("server").path("name").asText(null));
        return details;
    }

    private JsonNode safeDatabaseDetails(CoolifyDatabaseSummary database) {
        ObjectNode details = objectMapper.createObjectNode();
        putIfText(details, "uuid", database.uuid());
        putIfText(details, "name", database.name());
        putIfText(details, "status", database.status());
        putIfText(details, "databaseType", database.databaseType());
        putIfText(details, "postgresUser", database.postgresUser());
        putIfText(details, "postgresDatabase", database.postgresDatabase());
        JsonNode raw = database.raw();
        copyText(raw, details, "project_uuid");
        copyText(raw, details, "environment_uuid");
        copyText(raw, details, "environment_name");
        copyText(raw, details, "created_at");
        copyText(raw, details, "updated_at");
        putIfText(details, "projectName", raw.path("project").path("name").asText(null));
        putIfText(details, "projectUuid", raw.path("project").path("uuid").asText(null));
        putIfText(details, "environmentName", raw.path("environment").path("name").asText(null));
        putIfText(details, "environmentUuid", raw.path("environment").path("uuid").asText(null));
        return details;
    }

    private JsonNode safeActionDetails(JsonNode raw) {
        ObjectNode details = objectMapper.createObjectNode();
        if (raw == null || raw.isMissingNode() || raw.isNull()) {
            return details;
        }
        copyText(raw, details, "message");
        copyText(raw, details, "deployment_uuid");
        copyText(raw, details, "uuid");
        copyText(raw, details, "status");
        return details;
    }

    private String buildProvisioningDetails(DeploymentTargetProfileEntity profile,
                                            DeploymentProviderResourceHandleEntity runtimeHandle,
                                            DeploymentProviderResourceHandleEntity runtimeDatabaseHandle,
                                            DeploymentProviderResourceHandleEntity connectorHandle,
                                            CoolifyApplicationSummary runtimeApplication,
                                            CoolifyApplicationSummary connectorApplication,
                                            DeploymentProviderResourceHandleEntity vectorizationRunnerHandle,
                                            CoolifyApplicationSummary vectorizationRunnerApplication,
                                            CoolifyResourceScope scope,
                                            CoolifyProvisioningSource source,
                                            int runtimeEnvCount,
                                            int connectorEnvCount,
                                            int vectorizationRunnerEnvCount,
                                            CoolifyActionResponse runtimeDeployResponse,
                                            CoolifyActionResponse connectorDeployResponse,
                                            CoolifyActionResponse vectorizationRunnerDeployResponse,
                                            CoolifyRuntimeDatabaseBinding runtimeDatabaseBinding,
                                            ManagedVectorProvisioningResult managedVectorProvisioningResult) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("provider", "COOLIFY");
        details.put("targetProfileId", profile.getId());
        if (managedVectorProvisioningResult != null) {
            details.set("managedVectorProvisioning", managedVectorProvisioningResult.details());
            if (managedVectorProvisioningResult.effectiveProviderConfig() != null) {
                details.set("effectiveProviderConfig", managedVectorProvisioningResult.effectiveProviderConfig());
            }
        }
        writeScopeDetails(details, scope);
        details.put("providerResourceHandleId", runtimeHandle.getId());
        details.put("applicationUuid", runtimeApplication.uuid());
        details.put("applicationName", runtimeApplication.name());
        details.put("fqdn", runtimeApplication.fqdn());
        details.put("status", runtimeApplication.status());
        details.put("runtimeProviderResourceHandleId", runtimeHandle.getId());
        details.put("runtimeApplicationUuid", runtimeApplication.uuid());
        details.put("runtimeFqdn", runtimeApplication.fqdn());
        details.put("runtimeStatus", runtimeApplication.status());
        if (runtimeDatabaseHandle != null && runtimeDatabaseBinding != null) {
            details.put("runtimeDatabaseProviderResourceHandleId", runtimeDatabaseHandle.getId());
            details.put("runtimeDatabaseUuid", runtimeDatabaseBinding.database().uuid());
            details.put("runtimeDatabaseName", runtimeDatabaseBinding.database().name());
            details.put("runtimeDatabaseMode", runtimeDatabaseBinding.mode());
            details.put("runtimeDatabaseStatus", runtimeDatabaseBinding.database().status());
            details.put("runtimeDatabasePasswordSecret", runtimeDatabaseBinding.passwordSecretName());
            ObjectNode services = objectNode(objectNode(details, "coolify"), "services");
            ObjectNode database = objectNode(services, "runtimeDatabase");
            database.put("serviceId", runtimeDatabaseBinding.database().uuid());
            database.put("serviceName", runtimeDatabaseBinding.database().name());
            database.put("databaseMode", runtimeDatabaseBinding.mode());
            database.put("databaseName", runtimeDatabaseBinding.database().postgresDatabase());
            database.put("username", runtimeDatabaseBinding.database().postgresUser());
            database.put("status", runtimeDatabaseBinding.database().status());
            database.put("passwordSecret", runtimeDatabaseBinding.passwordSecretName());
        }
        if (runtimeDatabaseBinding != null && !runtimeDatabaseBinding.managed()) {
            details.put("runtimeDatabaseMode", runtimeDatabaseBinding.mode());
            details.put("aiStateStorageBindingId", runtimeDatabaseBinding.bindingId());
            details.put("aiStateStorageFingerprint", runtimeDatabaseBinding.fingerprint());
            details.put("aiStateStorageLifecycle", "RETAIN_EXTERNAL");
        }
        if (connectorHandle != null && connectorApplication != null) {
            details.put("connectorProviderResourceHandleId", connectorHandle.getId());
            details.put("connectorApplicationUuid", connectorApplication.uuid());
            details.put("connectorFqdn", connectorApplication.fqdn());
            details.put("connectorStatus", connectorApplication.status());
        }
        if (vectorizationRunnerHandle != null && vectorizationRunnerApplication != null) {
            details.put("vectorizationRunnerProviderResourceHandleId", vectorizationRunnerHandle.getId());
            details.put("vectorizationRunnerApplicationUuid", vectorizationRunnerApplication.uuid());
            details.put("vectorizationRunnerFqdn", vectorizationRunnerApplication.fqdn());
            details.put("vectorizationRunnerStatus", vectorizationRunnerApplication.status());
            ObjectNode services = objectNode(objectNode(details, "coolify"), "services");
            ObjectNode runner = objectNode(services, "vectorizationRunner");
            runner.put("serviceId", vectorizationRunnerApplication.uuid());
            runner.put("serviceName", vectorizationRunnerApplication.name());
            runner.put("deploymentStatus", applicationReady(vectorizationRunnerApplication) ? "SUCCESS" : "DEPLOY_REQUESTED");
            if (vectorizationRunnerDeployResponse != null) {
                runner.put("deploymentId", vectorizationRunnerDeployResponse.deploymentUuid());
            }
        }
        writeSourceDetails(details, source);
        details.put("environmentVariableCount", runtimeEnvCount + connectorEnvCount + vectorizationRunnerEnvCount);
        details.put("runtimeEnvironmentVariableCount", runtimeEnvCount);
        details.put("connectorEnvironmentVariableCount", connectorEnvCount);
        details.put("vectorizationRunnerEnvironmentVariableCount", vectorizationRunnerEnvCount);
        details.put("deploymentUuid", runtimeDeployResponse.deploymentUuid());
        details.put("statusMessage", runtimeDeployResponse.message());
        if (connectorDeployResponse != null) {
            details.put("connectorDeploymentUuid", connectorDeployResponse.deploymentUuid());
            details.put("connectorStatusMessage", connectorDeployResponse.message());
        }
        if (vectorizationRunnerDeployResponse != null) {
            details.put("vectorizationRunnerDeploymentUuid", vectorizationRunnerDeployResponse.deploymentUuid());
            details.put("vectorizationRunnerStatusMessage", vectorizationRunnerDeployResponse.message());
        }
        details.put("dnsSkipped", !StringUtils.hasText(runtimeApplication.fqdn()));
        details.put("generatedAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        return details.toPrettyString();
    }

    private String handleMetadata(CoolifyApplicationSummary application,
                                  CoolifyProvisioningSource source,
                                  CoolifyResourceScope scope,
                                  String serviceRole,
                                  int envCount,
                                  CoolifyActionResponse deployResponse) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("applicationName", application.name());
        metadata.put("serviceRole", serviceRole);
        writeScopeDetails(metadata, scope);
        writeSourceDetails(metadata, source);
        metadata.put("environmentVariableCount", envCount);
        if (deployResponse != null) {
            metadata.put("deploymentUuid", deployResponse.deploymentUuid());
            metadata.put("statusMessage", deployResponse.message());
        }
        return metadata.toPrettyString();
    }

    private String databaseHandleMetadata(CoolifyRuntimeDatabaseBinding binding,
                                          CoolifyResourceScope scope) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("databaseName", binding.database().name());
        metadata.put("serviceRole", SERVICE_ROLE_RUNTIME);
        metadata.put("databaseMode", binding.mode());
        metadata.put("postgresDatabase", binding.database().postgresDatabase());
        metadata.put("postgresUser", binding.database().postgresUser());
        metadata.put("passwordSecretName", binding.passwordSecretName());
        writeScopeDetails(metadata, scope);
        return metadata.toPrettyString();
    }

    private void writeSourceDetails(ObjectNode target, CoolifyProvisioningSource source) {
        target.put("sourceStrategy", source.sourceStrategy());
        if (source.sourceArtifact() != null) {
            target.put("sourceArtifactId", source.sourceArtifact().getId());
            target.put("imageRepository", source.sourceArtifact().getImageRepository());
            target.put("imageTag", source.sourceArtifact().getImageTag());
            putIfText(target, "imageDigest", source.sourceArtifact().getImageDigest());
            putIfText(target, "sourceGitCommit", source.sourceArtifact().getGitCommitSha());
            putIfText(target, "sourceCapabilityManifestHash", source.sourceArtifact().getCapabilityManifestHash());
        }
        if (source.gitSource()) {
            target.put("gitRepository", source.gitRepository());
            target.put("gitBranch", source.gitBranch());
            target.put("buildPack", source.buildPack());
            target.put("baseDirectory", source.baseDirectory());
            target.put("dockerfileLocation", source.dockerfileLocation());
            if (source.connectorPlan() != null) {
                target.put("connectorDockerfileLocation", normalizeDockerfileLocation(source.connectorPlan().dockerfilePath()));
            }
            if (source.vectorizationRunnerPlan() != null) {
                target.put(
                    "vectorizationRunnerDockerfileLocation",
                    normalizeDockerfileLocation(source.vectorizationRunnerPlan().dockerfilePath())
                );
            }
            if (source.plan() != null) {
                target.put("railwayPlanRepository", source.plan().repository());
                target.put("railwayPlanBranch", source.plan().branch());
            }
        }
    }

    private void writeScopeDetails(ObjectNode target, CoolifyResourceScope scope) {
        target.put("customerProjectGroupingEnabled", scope.customerGrouped());
        putIfText(target, "projectUuid", scope.projectUuid());
        putIfText(target, "projectName", scope.projectName());
        putIfText(target, "environmentName", scope.environmentName());
        putIfText(target, "environmentUuid", scope.environmentUuid());
    }

    private ObjectNode objectNode(ObjectNode parent, String field) {
        JsonNode existing = parent.path(field);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = objectMapper.createObjectNode();
        parent.set(field, created);
        return created;
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read Coolify provider JSON.", ex);
        }
    }

    private String text(JsonNode json, String field, String fallback) {
        String value = json.path(field).asText(null);
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String textFirst(JsonNode json, String... fields) {
        if (json == null || json.isMissingNode() || json.isNull()) {
            return null;
        }
        for (String field : fields) {
            String value = json.path(field).asText(null);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean booleanValue(JsonNode json, String field, boolean fallback) {
        return json.has(field) ? json.path(field).asBoolean(fallback) : fallback;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String normalizeGitRepositoryForCoolify(String repository) {
        String value = requireText(repository, "Coolify Git source requires a git repository.");
        String candidate = value.trim();
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://github.com/") || lower.startsWith("http://github.com/")) {
            candidate = candidate.substring(candidate.indexOf("github.com/") + "github.com/".length());
        } else if (lower.startsWith("ssh://git@github.com/")) {
            candidate = candidate.substring("ssh://git@github.com/".length());
        } else if (lower.startsWith("git@github.com:")) {
            candidate = candidate.substring("git@github.com:".length());
            String slug = candidate.replaceAll("^/+", "").replaceAll("/+$", "");
            if (slug.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(\\.git)?")) {
                return "git@github.com:" + (slug.endsWith(".git") ? slug : slug + ".git");
            }
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Coolify Git source repository must be a GitHub owner/repo slug or github.com URL."
            );
        } else if (lower.contains("://") || lower.startsWith("git@")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Coolify Git source repository must be a GitHub owner/repo slug or github.com URL."
            );
        }
        String slug = candidate.replaceAll("^/+", "").replaceAll("/+$", "");
        if (slug.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(\\.git)?")) {
            return slug;
        }
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Coolify Git source repository must be a GitHub owner/repo slug or github.com URL."
        );
    }

    private String normalizeCoolifyDirectory(String value) {
        if (!StringUtils.hasText(value) || ".".equals(value.trim())) {
            return "/";
        }
        String normalized = value.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String normalizeDockerfileLocation(String value) {
        String normalized = requireText(value, "Coolify Git source requires a dockerfile path.");
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String normalizeRuntimeBaseUrl(String fqdn) {
        if (!StringUtils.hasText(fqdn)) {
            return null;
        }
        String trimmed = fqdn.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private String normalizeStatus(String providerStatus, String fallback) {
        if (!StringUtils.hasText(providerStatus)) {
            return fallback;
        }
        return providerStatus.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
    }

    private boolean sameText(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private void copyText(JsonNode source, ObjectNode target, String field) {
        if (source == null || source.isMissingNode() || source.isNull() || !source.has(field)) {
            return;
        }
        JsonNode value = source.path(field);
        if (value.isBoolean()) {
            target.put(field, value.asBoolean());
            return;
        }
        if (value.isNumber()) {
            target.put(field, value.asText());
            return;
        }
        putIfText(target, field, value.asText(null));
    }

    private void putIfText(ObjectNode target, String field, String value) {
        if (StringUtils.hasText(value)) {
            target.put(field, value.trim());
        }
    }

    private record CoolifyProvisioningSource(
        String sourceStrategy,
        DeploymentSourceArtifactEntity sourceArtifact,
        RailwayProvisioningPlanSummary plan,
        RailwayServicePlanSummary runtimePlan,
        RailwayServicePlanSummary connectorPlan,
        RailwayServicePlanSummary vectorizationRunnerPlan,
        String gitRepository,
        String gitBranch,
        String baseDirectory,
        String dockerfileLocation,
        String buildPack
    ) {
        boolean gitSource() {
            return "GIT_SOURCE".equals(sourceStrategy);
        }
    }

    private record CoolifyResourceScope(
        String projectUuid,
        String projectName,
        String environmentName,
        String environmentUuid,
        boolean customerGrouped
    ) {
    }

    private <T> T tracked(ProvisioningProgressTracker progressTracker,
                          String key,
                          String description,
                          Supplier<T> supplier) {
        return trackedWithHeartbeat(
            progressTracker,
            key,
            description,
            supplier,
            TRACKED_STEP_HEARTBEAT_INTERVAL
        );
    }

    <T> T trackedWithHeartbeat(ProvisioningProgressTracker progressTracker,
                               String key,
                               String description,
                               Supplier<T> supplier,
                               Duration heartbeatInterval) {
        progressTracker.stepStarted(key, description);
        ScheduledFuture<?> heartbeat = TRACKED_STEP_HEARTBEAT_EXECUTOR.scheduleWithFixedDelay(
            () -> emitHeartbeat(progressTracker, key),
            heartbeatInterval.toMillis(),
            heartbeatInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );
        try {
            T result = supplier.get();
            progressTracker.stepCompleted(key, description);
            return result;
        } catch (RuntimeException ex) {
            progressTracker.stepFailed(key, description, ex.getMessage());
            throw ex;
        } finally {
            heartbeat.cancel(false);
        }
    }

    private void emitHeartbeat(ProvisioningProgressTracker progressTracker, String stepKey) {
        try {
            progressTracker.heartbeat();
        } catch (RuntimeException ex) {
            log.warn("Failed to record Coolify provisioning heartbeat for step '{}'.", stepKey, ex);
        }
    }
}
