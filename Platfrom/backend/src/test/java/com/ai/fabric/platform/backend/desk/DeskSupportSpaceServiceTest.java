package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.*;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.*;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityBinding;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeskSupportSpaceServiceTest {
    final ObjectMapper mapper = new ObjectMapper();
    final AIWorkspaceInstallationRepository installations = mock(AIWorkspaceInstallationRepository.class);
    final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    final AIWorkspaceAssignmentService assignments = mock(AIWorkspaceAssignmentService.class);
    final DeploymentTargetContextService targets = mock(DeploymentTargetContextService.class);
    final PlatformCustomerAccessService access = mock(PlatformCustomerAccessService.class);
    final DeskBindingRepository bindings = mock(DeskBindingRepository.class);
    final DeskBindingProvisioner provisioner = mock(DeskBindingProvisioner.class);
    final DeskServiceClient desk = mock(DeskServiceClient.class);
    final PlatformAuditService audit = mock(PlatformAuditService.class);
    final EntityManager entities = mock(EntityManager.class);
    final DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
    final AIWorkspaceInstallationEntity installation = new AIWorkspaceInstallationEntity();
    final PlatformConsumerEntity consumer = new PlatformConsumerEntity();
    final DeploymentEntity deployment = new DeploymentEntity();
    final DeploymentReleaseEntity release = new DeploymentReleaseEntity();
    final DeploymentVersionEntity version = new DeploymentVersionEntity();
    final DeploymentTargetProfileEntity target = new DeploymentTargetProfileEntity();
    final ObjectNode security = mapper.createObjectNode();
    final AtomicReference<DeskBindingEntity> registeredBinding = new AtomicReference<>();
    final DeskServiceClient.Connection connection = new DeskServiceClient.Connection("https://desk.example.test", "private-service-key");
    final com.ai.fabric.platform.backend.deployment.service.StandaloneConversationRuntimeArtifactGate canonicalConversations =
        mock(com.ai.fabric.platform.backend.deployment.service.StandaloneConversationRuntimeArtifactGate.class);
    final DeskSupportSpaceService service = new DeskSupportSpaceService(installations, consumers, assignments, targets,
        access, bindings, provisioner, desk, new AIWorkspaceAuthenticationProfileRegistry(mapper), entities, audit, mapper, authority,
        canonicalConversations);
    AIWorkspaceAssignmentService.Assignment assignment;
    DeploymentTargetContextService.Context context;
    DeskBindingEntity binding;

    @BeforeEach void setup() throws Exception {
        authenticate(new PlatformPrincipal("owner@example.test", PlatformRole.CUSTOMER_ADMIN, "Owner", "SUPABASE", "owner-id"));
        installation.setId("app-row"); installation.setInstallationId("awi-owned"); installation.setCustomerId("customer-a");
        installation.setConsumerEntityId("consumer-row"); installation.setStatus(AIWorkspaceInstallationStatus.DRAFT);
        installation.setDisplayName("Acme Support"); installation.setExperiencePackCode("support"); installation.setExperiencePackVersion("1.0.0");
        installation.setConnectionMode(AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS);
        installation.setInteractionPolicy(AIWorkspaceInteractionPolicy.PUBLIC_WITH_PROTECTED_CAPABILITIES);
        installation.setAuthProfileCode("supabase"); installation.setAuthProfileVersion("1.0.0");
        installation.setAuthConfigurationJson("""
            {"projectUrl":"https://auth.example.test","publishableKey":"sb_publishable_example_valid_key","methods":["magic-link"]}
            """);
        installation.setAllowedOriginsJson("[\"https://help.example.test\"]");
        installation.setConfigurationJson("""
            {"schemaVersion":"loomai-support-experience-config-v1","app":{"assistantLabel":"Support"},
             "support":{"topics":[{"code":"setup","label":"Setup"}],"teamVisible":true},
             "capabilities":{"tickets":true,"handover":false}}
            """);
        consumer.setId("consumer-row"); consumer.setConsumerId("acme-web"); consumer.setCustomerId("customer-a"); consumer.setStatus("ACTIVE");
        consumer.setBoundDeploymentId("dep-a"); consumer.setBoundReleaseId("rel-a"); consumer.setBoundTargetProfileId("target-a");
        deployment.setId("dep-a"); deployment.setCustomerId("customer-a"); deployment.setTenantId("tenant-a");
        deployment.setName("Acme Support"); deployment.setEnvironmentName("staging");
        release.setId("rel-a"); release.setDeploymentId("dep-a"); release.setTargetProfileId("target-a");
        release.setStatus("APPLIED_VERIFIED"); release.setVerificationStatus("PASSED"); release.setProvisioningStatus("ACTIVE");
        version.setId("ver-a"); version.setDeploymentId("dep-a"); version.setBehaviorConfigJson("{}");
        target.setId("target-a"); target.setEnvironmentName("staging"); target.setActive(true);
        security.put("externalIdentityBrokerEnabled", true).put("externalIdentityProvider", "SUPABASE")
            .put("externalIdentityIssuer", "https://auth.example.test/auth/v1")
            .put("externalIdentityJwkSetUri", "https://auth.example.test/auth/v1/.well-known/jwks.json")
            .put("externalIdentityAudience", "authenticated").put("externalIdentityGrantedScopes", "chat:query,chat:conversations,chat:messages,chat:handover:read");
        assignment = new AIWorkspaceAssignmentService.Assignment(consumer, deployment, release, version, security, "https://runtime.example.test", "assignment-1");
        context = new DeploymentTargetContextService.Context(deployment, target, release, version,
            "https://runtime.example.test", "https://connector.example.test");
        when(installations.findByCustomerIdAndInstallationId("customer-a", "awi-owned")).thenReturn(Optional.of(installation));
        when(installations.findByCustomerIdOrderByCreatedAtDesc("customer-a")).thenReturn(List.of(installation));
        when(consumers.findById("consumer-row")).thenReturn(Optional.of(consumer));
        when(consumers.findBindingForUpdate("customer-a", "acme-web")).thenReturn(Optional.of(consumer));
        when(assignments.resolveCurrent(consumer)).thenReturn(assignment);
        when(targets.resolve("dep-a", "target-a", "rel-a", true)).thenReturn(context);
        when(targets.resolveOwnedRead("dep-a", "target-a", "rel-a", "customer-a", "tenant-a")).thenReturn(context);
        when(desk.connection("staging")).thenReturn(connection);
        binding = DeskBindingProvisioner.projection(deployment, "target-a"); binding.setStatus("SUPPORT_ONLY");
        when(bindings.findByDeploymentIdAndTargetProfileId("dep-a", "target-a"))
            .thenAnswer(call -> Optional.ofNullable(registeredBinding.get()));
        when(bindings.findById(binding.getId())).thenAnswer(call -> Optional.ofNullable(registeredBinding.get()));
        when(provisioner.prepareSupportBinding(deployment, target, "rel-a", "support-1", "https://connector.example.test"))
            .thenAnswer(call -> { registeredBinding.set(binding); return binding; });
        when(provisioner.ticketConnectorKey(binding)).thenReturn("dedicated-ticket-key-at-least-32-characters");
        var frozen = mock(RuntimeAuthorityBinding.class);
        when(frozen.publicIssuer()).thenReturn("runtime-public:dep-a:target-a");
        when(frozen.publicAudience()).thenReturn("runtime-audience:dep-a:target-a");
        when(authority.requireReleaseUnchangedForRead(any(), any(), any()))
            .thenReturn(new RuntimeAuthorityBinding.Snapshot("snapshot-a", "rel-a", "fingerprint-a", frozen));
        when(desk.put(eq(connection), anyString(), any())).thenAnswer(call -> {
            var body = mapper.valueToTree(call.getArgument(2));
            ObjectNode view = space();
            view.put("enabled", body.path("enabled").asBoolean()).put("humanHandoverEnabled", body.path("humanHandoverEnabled").asBoolean());
            view.set("allowedOrigins", body.path("allowedOrigins")); view.set("topics", body.path("topics"));
            return view;
        });
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void registersAssignedTargetWithDerivedIdentityWithoutPublishingOrEnablingHumans() throws Exception {
        var result = service.configure("customer-a", "awi-owned", request(null, true, false));
        assertThat(result.status()).isEqualTo("CONFIGURED");
        var input = ArgumentCaptor.forClass(Object.class);
        verify(desk).put(eq(connection), eq(path()), input.capture());
        var body = mapper.valueToTree(input.getValue());
        assertThat(body.path("spaceId").asText()).isEqualTo(binding.getId());
        assertThat(body.path("providerBindingId").asText()).isEqualTo(binding.getId());
        assertThat(body.path("providerId").asText()).isEqualTo("desk");
        assertThat(body.path("runtimeBaseUrl").asText()).isEqualTo("https://runtime.example.test");
        assertThat(body.at("/identity/issuer").asText()).isEqualTo("https://auth.example.test/auth/v1");
        assertThat(body.at("/identity/requesterNamespace").asText()).isEqualTo("engage:" + binding.getId());
        assertThat(body.at("/identity/privateServerKey").isMissingNode()).isTrue();
        assertThat(body.path("runtimeTrustedKeyHeader").asText()).isEqualTo(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER);
        assertThat(body.at("/serverConnector/authMode").asText()).isEqualTo("PUBLIC_RUNTIME_AUTHENTICATED");
        assertThat(body.at("/serverConnector/issuer").asText()).isEqualTo("runtime-public:dep-a:target-a");
        assertThat(body.path("serverConnector").has("credentialUrl")).isFalse();
        assertThat(body.path("serverConnector").has("credentialKey")).isFalse();
        assertThat(body.path("enabled").asBoolean()).isTrue(); assertThat(body.path("humanHandoverEnabled").asBoolean()).isFalse();
        assertThat(mapper.writeValueAsString(result)).doesNotContain("private-service-key", "connectorKey", "dedicated-ticket-key", "jwksUri", "credentialKey", "distinct-proof-key");
        verify(audit).record(eq("SUPPORT_SPACE_CONFIGURED"), anyString(), anyString(), anyMap());
    }

    @Test void bothPresentationsRegisterTheirExactOriginsAndShareOneSpace() {
        var embedded = new AIWorkspaceInstallationEntity(); org.springframework.beans.BeanUtils.copyProperties(installation, embedded);
        embedded.setId("embedded-row"); embedded.setAllowedOriginsJson("[\"https://website.example.test\"]");
        when(installations.findByCustomerIdOrderByCreatedAtDesc("customer-a")).thenReturn(List.of(installation, embedded));
        var result = service.configure("customer-a", "awi-owned", request(null, true, false));
        assertThat(result.space().path("allowedOrigins")).extracting(com.fasterxml.jackson.databind.JsonNode::asText)
            .containsExactly("https://help.example.test", "https://website.example.test");
        verify(provisioner, times(1)).prepareSupportBinding(any(), any(), anyString(), anyString(), anyString());
    }

    @Test void publicProjectionOverwritesBrowserSuppliedRoutesWithVerifiedManagedReferences() throws Exception {
        service.configure("customer-a", "awi-owned", request(null, true, false));
        clearInvocations(desk);
        var config = (ObjectNode) mapper.readTree(installation.getConfigurationJson());
        config.with("support").put("spaceId", "foreign").put("ticketProviderBindingId", "foreign").put("requesterOrigin", "https://attacker.example");
        installation.setConfigurationJson(config.toString());
        var publicConfig = service.publicConfiguration(installation, assignment);
        assertThat(publicConfig.at("/support/spaceId").asText()).isEqualTo(binding.getId());
        assertThat(publicConfig.at("/support/requesterOrigin").asText()).isEqualTo(connection.baseUrl());
        assertThat(publicConfig.at("/support/teamVisible").asBoolean()).isFalse();
        assertThat(publicConfig.toString()).doesNotContain("attacker", "private-service-key", "connectorKey");
        assertThat(installation.getConfigurationJson()).contains("attacker");
        verifyNoInteractions(desk);
    }

    @Test void aiOnlyNeedsNoSupportProviderAndStripsUntrustedReferences() throws Exception {
        var config = (ObjectNode) mapper.readTree(installation.getConfigurationJson());
        config.with("capabilities").put("tickets", false);
        config.with("support").put("requesterOrigin", "https://untrusted.example"); installation.setConfigurationJson(config.toString());
        assertThat(service.publicConfiguration(installation, assignment).path("support").has("requesterOrigin")).isFalse();
        verifyNoInteractions(bindings, desk, targets, provisioner);
    }

    @Test void productionCanBePreparedBeforeRebindingWithoutCopyingStageIdentityOrChangingTheConsumer() {
        var prodDeployment = new DeploymentEntity(); org.springframework.beans.BeanUtils.copyProperties(deployment, prodDeployment);
        prodDeployment.setEnvironmentName("staging");
        var prodRelease = new DeploymentReleaseEntity(); org.springframework.beans.BeanUtils.copyProperties(release, prodRelease);
        prodRelease.setId("rel-production"); prodRelease.setTargetProfileId("target-production");
        var prodTarget = new DeploymentTargetProfileEntity(); prodTarget.setId("target-production"); prodTarget.setEnvironmentName("production"); prodTarget.setActive(true);
        var prodAssignment = new AIWorkspaceAssignmentService.Assignment(consumer, prodDeployment, prodRelease, version,
            security, "https://runtime-production.example.test", "candidate-assignment");
        var prodContext = new DeploymentTargetContextService.Context(prodDeployment, prodTarget, prodRelease, version,
            "https://runtime-production.example.test", "https://connector-production.example.test");
        var prodBinding = DeskBindingProvisioner.projection(prodDeployment, "target-production");
        var prodConnection = new DeskServiceClient.Connection("https://desk-production.example.test", "private-production-key");
        when(assignments.resolveTarget(consumer, "dep-a", "rel-production")).thenReturn(prodAssignment);
        when(targets.resolve("dep-a", "target-production", "rel-production", true)).thenReturn(prodContext);
        when(targets.resolveOwnedRead("dep-a", "target-production", "rel-production", "customer-a", "tenant-a")).thenReturn(prodContext);
        when(desk.connection("production")).thenReturn(prodConnection);
        when(provisioner.prepareSupportBinding(prodDeployment, prodTarget, "rel-production", "support-1", "https://connector-production.example.test"))
            .thenReturn(prodBinding);
        when(provisioner.ticketConnectorKey(prodBinding)).thenReturn("distinct-production-connector-key-12345678");
        when(desk.put(eq(prodConnection), anyString(), any())).thenAnswer(call -> {
            var view = space(); view.put("spaceId", prodBinding.getId()).put("bindingId", prodBinding.getId()).put("providerBindingId", prodBinding.getId());
            view.with("identity").put("requesterNamespace", "engage:" + prodBinding.getId());
            return view;
        });
        var input = new DeskSupportSpaceService.ConfigureRequest(0L, "assignment-1", null, true, false, "desk", null,
            "support-1", "target-production", "rel-production");
        var result = service.configure("customer-a", "awi-owned", input);
        assertThat(result.assignmentRevision()).isEqualTo("assignment-1");
        var status = service.status("customer-a", "awi-owned", "target-production", "rel-production");
        assertThat(status.assignmentRevision()).isEqualTo("assignment-1");
        assertThat(status.status()).isEqualTo("NOT_CONFIGURED");
        assertThat(result.space().path("spaceId").asText()).isEqualTo(prodBinding.getId()).isNotEqualTo(binding.getId());
        assertThat(consumer.getBoundReleaseId()).isEqualTo("rel-a");
        assertThat(consumer.getBoundTargetProfileId()).isEqualTo("target-a");
        var captured = ArgumentCaptor.forClass(Object.class);
        verify(desk).put(eq(prodConnection), anyString(), captured.capture());
        assertThat(mapper.valueToTree(captured.getValue()).path("runtimeBaseUrl").asText()).isEqualTo("https://runtime-production.example.test");
        assertThat(mapper.valueToTree(captured.getValue()).path("allowedOrigins")).hasSize(1);
    }

    @Test void staffAndApiKeysCannotConfigureRequesterIdentity() {
        for (var principal : List.of(new PlatformPrincipal("staff", PlatformRole.PLATFORM_ADMIN, "Staff", "SESSION", "staff"),
                new PlatformPrincipal("owner", PlatformRole.CUSTOMER_ADMIN, "Owner", "API_KEY", "owner"))) {
            authenticate(principal);
            assertThatThrownBy(() -> service.configure("customer-a", "awi-owned", request(null, true, false)))
                .isInstanceOf(ResponseStatusException.class);
        }
        verifyNoInteractions(installations, consumers, assignments, targets, provisioner, desk);
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "disabled", "foreign", "issuer", "origin", "capabilities", "private-mode"})
    void readinessFailsClosedForUnavailableOrMismatchedRegisteredServices(String failure) {
        registeredBinding.set(binding); var view = space();
        switch (failure) {
            case "disabled" -> view.put("enabled", false);
            case "foreign" -> view.put("bindingId", "another-binding");
            case "issuer" -> view.with("identity").put("issuer", "https://other.example/auth/v1");
            case "private-mode" -> view.with("identity").put("mode", "TRUSTED_PRIVATE_HOST");
            case "origin" -> view.putArray("allowedOrigins").add("https://other.example");
            case "capabilities" -> view.putArray("capabilities").add("GET");
        }
        when(desk.getOptional(connection, path())).thenReturn(failure.equals("missing") ? Optional.empty() : Optional.of(view));
        assertThatThrownBy(() -> service.validate(installation, assignment)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(provisioner);
    }

    @Test void providerFailureNeverRegistersFallbackOrReturnsSuccess() {
        registeredBinding.set(binding);
        when(desk.getOptional(connection, path())).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Unavailable"));
        assertThatThrownBy(() -> service.configure("customer-a", "awi-owned", request(null, true, false)))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Unavailable");
        verifyNoInteractions(provisioner, audit);
    }

    @Test void staleInstallationAssignmentAndSpaceVersionsStopBeforeSideEffects() {
        var stale = new DeskSupportSpaceService.ConfigureRequest(1L, "assignment-1", null, true, false, "desk", null, "support-1", null, null);
        assertThatThrownBy(() -> service.configure("customer-a", "awi-owned", stale)).isInstanceOf(ResponseStatusException.class);
        var changedAssignment = new DeskSupportSpaceService.ConfigureRequest(0L, "assignment-old", null, true, false, "desk", null, "support-1", null, null);
        assertThatThrownBy(() -> service.configure("customer-a", "awi-owned", changedAssignment)).isInstanceOf(ResponseStatusException.class);
        registeredBinding.set(binding); when(desk.getOptional(connection, path())).thenReturn(Optional.of(space()));
        assertThatThrownBy(() -> service.configure("customer-a", "awi-owned", request("old-version", true, false)))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("opaque version");
        verify(entities).refresh(binding, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        verifyNoInteractions(provisioner, audit);
    }

    @Test void humanOnlyDoesNotRequireTicketProviderEnabledButRequiresTheRealChannelAndTeam() throws Exception {
        registeredBinding.set(binding);
        version.setBehaviorConfigJson(DeskTargetConfigurationResolverTest.managedBehavior(mapper).toString());
        var config = (ObjectNode) mapper.readTree(installation.getConfigurationJson());
        config.with("capabilities").put("tickets", false).put("handover", true); installation.setConfigurationJson(config.toString());
        var space = space(); space.put("enabled", false).put("humanHandoverEnabled", true);
        when(desk.getOptional(connection, path())).thenReturn(Optional.of(space));
        when(desk.get(connection, teamPath())).thenReturn(mapper.createArrayNode());
        assertThatThrownBy(() -> service.validate(installation, assignment)).hasMessageContaining("SUPPORT team");
        var team = mapper.createArrayNode(); team.addObject().put("profileRef", "assigned-agent");
        when(desk.get(connection, teamPath())).thenReturn(team);
        service.validate(installation, assignment);
        var registered = service.configure("customer-a", "awi-owned", request("0", false, true));
        assertThat(registered.space().path("enabled").asBoolean()).isFalse();
        assertThat(registered.space().path("humanHandoverEnabled").asBoolean()).isTrue();
        clearInvocations(desk);
        assertThat(service.publicConfiguration(installation, assignment).at("/capabilities/handover").asBoolean()).isTrue();
        verifyNoInteractions(desk);
    }

    @ParameterizedTest @ValueSource(strings = {"issuer", "messages-scope", "ownership-scope", "control-read-scope", "unverified", "wrong-provider", "foreign-owner"})
    void invalidOwnedSetupFailsBeforeCreatingInfrastructure(String failure) {
        var request = request(null, true, false);
        switch (failure) {
            case "issuer" -> security.put("externalIdentityIssuer", "https://foreign.example/auth/v1");
            case "messages-scope" -> security.put("externalIdentityGrantedScopes", "chat:query");
            case "ownership-scope" -> security.put("externalIdentityGrantedScopes", "chat:query,chat:messages");
            case "control-read-scope" -> security.put("externalIdentityGrantedScopes", "chat:query,chat:messages,chat:conversations");
            case "unverified" -> release.setStatus("VERIFYING");
            case "wrong-provider" -> request = new DeskSupportSpaceService.ConfigureRequest(0L, "assignment-1", null, true, false, "unregistered", null, "support-1", null, null);
            case "foreign-owner" -> doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(access).requireCustomerManagementAccess("customer-a");
        }
        var input = request;
        assertThatThrownBy(() -> service.configure("customer-a", "awi-owned", input)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(provisioner, audit);
    }

    @Test void normalizedPublishedScopesDoNotRequireExactCsvFormatting() {
        security.put("externalIdentityGrantedScopes", "chat:query, chat:messages, chat:conversations, chat:handover:read ");
        assertThat(service.configure("customer-a", "awi-owned", request(null, true, false)).status()).isEqualTo("CONFIGURED");
    }

    @Test void readStatusHasNoProvisioningSideEffectsAndRejectsPartialCandidateSelectors() {
        assertThat(service.status("customer-a", "awi-owned", null, null).status()).isEqualTo("NOT_CONFIGURED");
        for (String[] selection : List.of(new String[]{"target-a", null}, new String[]{null, "rel-a"})) {
            assertThatThrownBy(() -> service.status("customer-a", "awi-owned", selection[0], selection[1]))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("both");
        }
        verifyNoInteractions(provisioner, audit, desk);
    }

    @ParameterizedTest @ValueSource(strings = {"status", "configure", "readiness", "projection"})
    void oldRuntimeCannotQualifySupportEvenWithVerifiedStatusAndUiPreferences(String operation) {
        doThrow(new com.ai.fabric.platform.backend.deployment.service.StandaloneConversationRuntimeArtifactGate.Required())
            .when(canonicalConversations).requireEnabled(version, release);
        assertThatThrownBy(() -> {
            switch (operation) {
                case "status" -> service.status("customer-a", "awi-owned", null, null);
                case "configure" -> service.configure("customer-a", "awi-owned", request(null, true, false));
                case "readiness" -> service.validate(installation, assignment);
                case "projection" -> service.publicConfiguration(installation, assignment);
            }
        }).isInstanceOf(com.ai.fabric.platform.backend.deployment.service.StandaloneConversationRuntimeArtifactGate.Required.class)
            .hasMessageContaining("standalone-conversation-control");
        verifyNoInteractions(desk, provisioner, audit);
    }

    @Test void successfulRegistrationPersistsOnlyVerifiedSafeReferencesAndProviderOutageDoesNotBlockAiProjection() {
        service.configure("customer-a", "awi-owned", request(null, true, false));
        assertThat(binding.getSupportRegistrationJson()).contains("loomai-desk-support-registration-v1", binding.getId())
            .doesNotContain("connectorKey", "dedicated-ticket-key", "credentialKey", "private-service-key", "publishableKey");
        when(desk.connection("staging")).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Desk unavailable"));
        clearInvocations(desk);
        var projected = service.publicConfiguration(installation, assignment);
        assertThat(projected.at("/support/requesterOrigin").asText()).isEqualTo("https://desk.example.test");
        assertThat(projected.at("/support/spaceId").asText()).isEqualTo(binding.getId());
        assertThat(projected.at("/capabilities/tickets").asBoolean()).isTrue();
        verifyNoInteractions(desk);
        assertThatThrownBy(() -> service.validate(installation, assignment)).hasMessageContaining("unavailable");
    }

    @ParameterizedTest @ValueSource(strings = {"customerId", "tenantId", "deploymentId", "targetProfileId", "runtimeBaseUrl", "schemaVersion"})
    void publishedProjectionRejectsStaleOrForeignRegistrationReceiptWithoutNetwork(String field) throws Exception {
        service.configure("customer-a", "awi-owned", request(null, true, false));
        var receipt = (ObjectNode) mapper.readTree(binding.getSupportRegistrationJson()); receipt.put(field, "foreign-or-stale");
        binding.setSupportRegistrationJson(receipt.toString()); clearInvocations(desk);
        assertThatThrownBy(() -> service.publicConfiguration(installation, assignment)).hasMessageContaining("Register this verified target");
        verifyNoInteractions(desk);
    }

    private ObjectNode space() {
        var view = mapper.createObjectNode().put("spaceId", binding.getId()).put("bindingId", binding.getId()).put("name", "Acme Support")
            .put("enabled", true).put("humanHandoverEnabled", false).put("providerId", "desk").put("providerBindingId", binding.getId())
            .put("version", "0").put("closedRetentionDays", 30);
        view.putObject("identity").put("mode", "SUPABASE").put("issuer", "https://auth.example.test/auth/v1")
            .put("audience", "authenticated").put("requesterNamespace", "engage:" + binding.getId());
        view.putArray("allowedOrigins").add("https://help.example.test"); view.putArray("topics").add("setup");
        view.putArray("capabilities").add("CREATE").add("GET").add("LIST").add("LINK_CONVERSATION");
        return view;
    }
    private DeskSupportSpaceService.ConfigureRequest request(String version, boolean tickets, boolean human) {
        return new DeskSupportSpaceService.ConfigureRequest(0L, "assignment-1", version, tickets, human, "desk", null, "support-1", null, null);
    }
    private String path() { return "/api/desk/admin/v1/bindings/" + binding.getId() + "/support-space"; }
    private String teamPath() { return "/api/desk/admin/v1/bindings/" + binding.getId() + "/support-team"; }
    private void authenticate(PlatformPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
