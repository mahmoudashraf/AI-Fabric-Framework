package com.ai.fabric.platform.backend.deployment.service;

import static org.assertj.core.api.Assertions.*;

import com.ai.fabric.platform.backend.assistant.AuthoringProperties;
import com.ai.fabric.platform.backend.assistant.ConfigurationAssistantTemplate;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformTestSecurity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {"platform.bootstrap.sample-enabled=false", "platform.auth.bootstrap-admin-enabled=false",
    "spring.datasource.url=jdbc:h2:mem:managed-assistant-bootstrap;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@ActiveProfiles("test")
@Transactional
class ManagedAssistantBootstrapIntegrationTest {
    @Autowired DeploymentService service;
    @Autowired DeploymentRepository deployments;
    @Autowired DeploymentDraftRepository drafts;
    @Autowired DeploymentVersionRepository versions;
    @Autowired PlatformCustomerRepository customers;
    @Autowired PlatformTenantRepository tenants;
    @Autowired ConfigurationAssistantTemplate template;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager entities;
    AuthoringProperties settings;

    @BeforeEach void prepare() {
        PlatformTestSecurity.authenticateAsPlatformAdmin();
        var owner = owner(true);
        settings = new AuthoringProperties();
        settings.setDeploymentId("dep-bootstrap-" + UUID.randomUUID());
        settings.setCustomerId(owner.customerId()); settings.setTenantId(owner.tenantId());
    }
    @AfterEach void clearAuthentication() { PlatformTestSecurity.clearAuthentication(); }

    @Test void newInternalAssistantSeedsOnlyTheGenerationBudgetWithItsExistingModelAndAuthority() throws Exception {
        service.createManagedConfigurationAssistant("staging", settings, template);
        var deployment = deployments.findById(settings.getDeploymentId()).orElseThrow();
        var draft = drafts.findById(deployment.getActiveDraftId()).orElseThrow();
        var provider = mapper.readTree(draft.getProviderConfigJson());
        assertThat(provider.path("generationMaxTokens").asInt()).isEqualTo(8192);
        assertThat(provider.path("generationLlmProvider").asText()).isEqualTo("openai");
        assertThat(provider.path("generationModel").asText()).isEqualTo("gpt-5.4-mini");
        assertThat(provider.path("openaiModel").asText()).isEqualTo("gpt-5.4-mini");
        assertThat(provider.path("openaiMaxTokens").asInt()).isEqualTo(ManagedBrainQualityDefaults.openAiMaxTokens());
        assertThat(provider.path("generationTimeout").asInt()).isEqualTo(40);
        assertThat(provider.path("canonicalConversationsEnabled").asBoolean()).isTrue();
        assertThat(mapper.readTree(draft.getBehaviorConfigJson()).path("workflowAssistance"))
            .isEqualTo(template.behavior().path("workflowAssistance"));
        var security = mapper.readTree(draft.getSecurityConfigJson());
        assertThat(security.path("authzMode").asText()).isEqualTo("ALLOW_VERIFIED");
        assertThat(security.path("publicRuntimeBootstrapEnabled").asBoolean(true)).isFalse();
        assertThat(deployment.getCustomerId()).isEqualTo(settings.getCustomerId());
        assertThat(deployment.getTenantId()).isEqualTo(settings.getTenantId());
        assertThat(deployment.getTemplateId()).isEqualTo("managed-configuration-assistant");
        assertThat(deployment.getStatus()).isEqualTo("DRAFT");
        assertThat(deployment.getActiveVersionId()).isNull();
        assertThat(deployment.getRuntimeBaseUrl()).isNull();
        assertThat(versions.countByDeploymentId(deployment.getId())).isZero();
    }

    @ParameterizedTest @ValueSource(ints = {1536, 4096, 8192})
    void retainedInternalDraftPublishedVersionAndServiceIdentityAreNeverReset(int retainedBudget) throws Exception {
        service.createManagedConfigurationAssistant("staging", settings, template);
        var deployment = deployments.findById(settings.getDeploymentId()).orElseThrow();
        var draft = drafts.findById(deployment.getActiveDraftId()).orElseThrow();
        var published = new DeploymentVersionEntity(); BeanUtils.copyProperties(draft, published);
        published.setId("ver-retained"); published.setSourceDraftId(draft.getId()); published.setVersionLabel("v1");
        published.setStatus("PUBLISHED"); published.setConfigHash("retained-published-hash");
        published.setPublishedAt(Instant.parse("2026-11-20T12:00:00Z")); published.setAiFabricFrameworkVersion("0.8.69");
        published.setActionsArtifactYaml("actions: []"); published.setEntityArtifactYaml("entities: []");
        published.setRoutingArtifactYaml("actions: {}"); published.setManifestJson("{}");
        var legacy = (ObjectNode) mapper.readTree(published.getProviderConfigJson()); legacy.put("generationMaxTokens", 4096);
        published.setProviderConfigJson(legacy.toString()); versions.saveAndFlush(published);
        var custom = (ObjectNode) mapper.readTree(draft.getProviderConfigJson());
        custom.put("generationMaxTokens", retainedBudget).put("generationModel", "retained-model")
            .put("generationApiKeySecretRef", "RETAINED_OWN_KEY").putObject("futureSetting").put("keep", true);
        draft.setProviderConfigJson(custom.toString()); draft.setPromptConfigJson("{\"systemPrompt\":\"Retained manual prompt\"}");
        draft.setCreatedAt(Instant.parse("2026-11-20T12:00:00.123456789Z"));
        draft.setUpdatedAt(Instant.parse("2026-11-20T12:01:00.987654321Z"));
        drafts.saveAndFlush(draft);
        deployment.setActiveVersionId(published.getId()); deployment.setStatus("ACTIVE");
        deployment.setRuntimeBaseUrl("https://retained-runtime.example.test"); deployments.saveAndFlush(deployment);
        // Compare persisted rows on both sides, including the database's timestamp precision.
        entities.flush(); entities.clear();
        JsonNode beforeDraft = mapper.valueToTree(drafts.findById(draft.getId()).orElseThrow());
        JsonNode beforeVersion = mapper.valueToTree(versions.findById(published.getId()).orElseThrow());
        JsonNode beforeDeployment = mapper.valueToTree(deployments.findById(deployment.getId()).orElseThrow());
        long draftCount = drafts.count(); entities.clear();
        service.createManagedConfigurationAssistant("staging", settings, template);
        entities.flush(); entities.clear();
        assertThat(mapper.<JsonNode>valueToTree(drafts.findById(draft.getId()).orElseThrow())).isEqualTo(beforeDraft);
        assertThat(mapper.<JsonNode>valueToTree(versions.findById(published.getId()).orElseThrow())).isEqualTo(beforeVersion);
        assertThat(mapper.<JsonNode>valueToTree(deployments.findById(deployment.getId()).orElseThrow())).isEqualTo(beforeDeployment);
        assertThat(drafts.count()).isEqualTo(draftCount);
        assertThat(versions.countByDeploymentId(deployment.getId())).isOne();
    }

    @Test void ordinaryCustomerBootstrapKeepsItsExistingProviderDefaults() throws Exception {
        var before = ordinary("Before managed bootstrap");
        service.createManagedConfigurationAssistant("staging", settings, template);
        var after = ordinary("After managed bootstrap");
        assertThat(after).isEqualTo(before);
        assertThat(after.has("generationMaxTokens")).isFalse();
        assertThat(after.path("openaiMaxTokens").asInt()).isEqualTo(ManagedBrainQualityDefaults.openAiMaxTokens());
        assertThat(after.path("openaiModel").asText()).isEqualTo(ManagedBrainQualityDefaults.openAiModel());
    }

    @Test void bootstrapStillRequiresAnAdministratorBeforeCreatingAnInternalDeployment() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal("customer@example.test", PlatformRole.CUSTOMER_ADMIN, "Customer", "TEST"), null, List.of()));
        assertThatThrownBy(() -> service.createManagedConfigurationAssistant("staging", settings, template))
            .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(deployments.existsById(settings.getDeploymentId())).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"customer", "tenant", "environment"})
    void retainedInternalOwnershipAndEnvironmentCannotBeRebound(String boundary) {
        service.createManagedConfigurationAssistant("staging", settings, template);
        if (boundary.equals("customer")) settings.setCustomerId("foreign-customer");
        if (boundary.equals("tenant")) settings.setTenantId("foreign-tenant");
        assertThatThrownBy(() -> service.createManagedConfigurationAssistant(boundary.equals("environment") ? "production" : "staging", settings, template))
            .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private JsonNode ordinary(String name) throws Exception {
        var owner = owner(false);
        var created = service.createDeploymentConfiguration(new CreateDeploymentRequest(name, "staging", "dev-openai-lucene", "default",
            null, owner.customerId(), owner.tenantId(), "CONVERSATIONAL"));
        var deployment = deployments.findById(created.id()).orElseThrow();
        return mapper.readTree(drafts.findById(deployment.getActiveDraftId()).orElseThrow().getProviderConfigJson());
    }
    private Owner owner(boolean managed) {
        String suffix = UUID.randomUUID().toString(); Instant now = Instant.now();
        var customer = new PlatformCustomerEntity(); customer.setId("cus-" + suffix); customer.setSlug("customer-" + suffix);
        customer.setName("Bootstrap fixture"); customer.setStatus("ACTIVE"); customer.setPlatformManaged(managed);
        customer.setCreatedAt(now); customer.setUpdatedAt(now); customers.saveAndFlush(customer);
        var tenant = new PlatformTenantEntity(); tenant.setId("ten-" + suffix); tenant.setSlug("tenant-" + suffix);
        tenant.setCustomerId(customer.getId()); tenant.setName("Bootstrap fixture"); tenant.setStatus("ACTIVE"); tenant.setPlatformManaged(managed);
        tenant.setCreatedAt(now); tenant.setUpdatedAt(now); tenants.saveAndFlush(tenant);
        return new Owner(customer.getId(), tenant.getId());
    }
    private record Owner(String customerId, String tenantId) { }
}
