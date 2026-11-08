package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.model.CustomerPluginLibraryContracts.*;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.entity.*;
import com.ai.fabric.platform.backend.security.repository.*;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"platform.bootstrap.sample-enabled=false", "platform.auth.bootstrap-admin-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerPluginLibraryIntegrationTest {
    @Autowired CustomerPluginLibraryService library;
    @Autowired MarketplaceCatalogService catalog;
    @Autowired MarketplacePluginRepository plugins;
    @Autowired MarketplacePluginVersionRepository versions;
    @Autowired PlatformCustomerRepository customers;
    @Autowired PlatformUserRepository users;
    @Autowired CustomerStaffAccessRepository grants;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @Autowired com.ai.fabric.platform.backend.deployment.service.DeploymentService deploymentService;
    @Autowired com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository deployments;
    @Autowired com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository installs;
    @Autowired DeploymentMarketplaceInstallService installService;
    @Autowired DeploymentMarketplaceDraftCompilerService compiler;
    @Autowired MarketplacePublishingService publisher;
    PlatformUserEntity owner;
    PlatformUserEntity other;

    @BeforeEach void setUp() {
        owner = customer(); other = customer();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void draftsVersionsAndArchiveRetainExactSourceWithoutSideEffects() throws Exception {
        var manifest = seed();
        manifest.put("ownerCustomerId", other.getCustomerId()).put("visibility", "PUBLIC");
        manifest.set("unknown", mapper.readTree("{\"huge\":123456789012345678901234567890,\"decimal\":0.123456789012345678901234500,\"null\":null,\"array\":[3,null,1]}"));
        Summary created = as(owner, () -> library.create(null, new Create(manifest)));
        assertThat(created.revision()).isEqualTo(1);
        assertThat(created.ownerCustomerId()).isEqualTo(owner.getCustomerId());
        assertThat(created.visibility()).isEqualTo("CUSTOMER_PRIVATE");
        assertThat(created.manifest()).isEqualTo(manifest);
        assertThat(created.installs().savedCount()).isZero();
        assertThat(created.installs().appliedCount()).isZero();
        assertThat(as(owner, () -> library.list(null)).canManage()).isTrue();
        assertThat(as(owner, () -> library.check(created.id(), null, new Save(manifest, 1L))).valid()).isTrue();
        assertThat(as(owner, () -> library.get(created.id(), null)).revision()).isEqualTo(1);
        Summary published = as(owner, () -> library.createVersion(created.id(), null, new Save(manifest, 1L)));
        assertThat(published.revision()).isEqualTo(2);
        assertThat(published.versions()).hasSize(1);
        ObjectNode changed = manifest.deepCopy().put("version", "1.1.0").put("displayName", "Changed");
        Summary saved = as(owner, () -> library.update(created.id(), null, new Save(changed, 2L)));
        assertThat(saved.versions().get(0).manifest()).isEqualTo(manifest);
        assertThat(saved.manifest().path("unknown").toString()).contains("0.123456789012345678901234500");
        assertThatThrownBy(() -> as(owner, () -> library.update(created.id(), null, new Save(manifest, 2L))))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        ObjectNode invalid = changed.deepCopy().put("schemaVersion", 99);
        assertThatThrownBy(() -> as(owner, () -> library.createVersion(created.id(), null, new Save(invalid, 3L))))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        assertThat(as(owner, () -> library.get(created.id(), null)).revision()).isEqualTo(3);
        assertThat(as(owner, () -> library.get(created.id(), null)).manifest()).isEqualTo(changed);
        Summary archived = as(owner, () -> library.archive(created.id(), null, new Revision(3L)));
        assertThat(archived.status()).isEqualTo("ARCHIVED");
        assertThat(as(owner, () -> catalog.listPlugins(null, true))).noneMatch(p -> p.id().equals(created.id()));
        assertThat(as(owner, () -> catalog.getPluginVersion(created.id(), "1.0.0", null)).manifest()).isEqualTo(manifest);
        assertThatThrownBy(() -> as(owner, () -> library.update(created.id(), null, new Save(changed, 4L))))
            .hasMessageContaining("Archived");
    }

    @Test void privateDefinitionsAreHiddenAcrossCustomersAndFromUnscopedInternalCatalogue() throws Exception {
        var manifest = seed();
        var created = as(owner, () -> library.create(null, new Create(manifest)));
        as(owner, () -> library.createVersion(created.id(), null, new Save(manifest, 1L)));
        assertThat(catalog.listPlugins()).noneMatch(p -> p.id().equals(created.id()));
        assertThatThrownBy(() -> catalog.getPlugin(created.id())).hasMessageContaining("404");
        assertThat(as(owner, () -> catalog.listPlugins(null, true))).anyMatch(p -> p.id().equals(created.id())
            && "CUSTOMER_PRIVATE".equals(p.visibility()) && owner.getCustomerId().equals(p.ownerCustomerId()));
        assertThat(as(other, () -> library.list(null)).plugins()).isEmpty();
        for (Supplier<?> operation : List.<Supplier<?>>of(
            () -> library.get(created.id(), null),
            () -> library.versions(created.id(), null),
            () -> library.version(created.id(), "1.0.0", null),
            () -> library.check(created.id(), null, new Save(manifest, 2L)),
            () -> library.update(created.id(), null, new Save(manifest, 2L)),
            () -> library.createVersion(created.id(), null, new Save(manifest, 2L)),
            () -> library.archive(created.id(), null, new Revision(2L)),
            () -> catalog.getPlugin(created.id(), null),
            () -> catalog.getPluginVersion(created.id(), "1.0.0", null))) {
            assertThatThrownBy(() -> as(other, operation)).hasMessageContaining("404");
        }
        assertThatThrownBy(() -> as(owner, () -> catalog.listPlugins(other.getCustomerId(), true))).hasMessageContaining("403");
    }

    @Test void delegatedStaffNeedExplicitCustomerWideGrantAndLoseAccessAfterRevocation() {
        var operator = user(null, "PLATFORM_OPERATOR");
        assertThatThrownBy(() -> as(operator, () -> library.list(null))).hasMessageContaining("explicit customer");
        assertThatThrownBy(() -> as(operator, () -> library.list(owner.getCustomerId()))).hasMessageContaining("403");
        var grant = new CustomerStaffAccessEntity();
        grant.setId(UUID.randomUUID().toString()); grant.setCustomerId(owner.getCustomerId()); grant.setUserId(operator.getId());
        grant.setStatus("ACTIVE"); grant.setGrantedAt(Instant.now()); grant.setGrantedBy(owner.getId());
        grants.saveAndFlush(grant);
        assertThat(as(operator, () -> library.list(owner.getCustomerId())).canManage()).isTrue();
        grant.setStatus("REVOKED"); grant.setRevokedAt(Instant.now()); grant.setRevokedBy(owner.getId()); grants.saveAndFlush(grant);
        assertThatThrownBy(() -> as(operator, () -> library.list(owner.getCustomerId()))).hasMessageContaining("403");
    }

    @Test void concurrentDraftWritersHaveExactlyOneWinner() throws Exception {
        var manifest = seed();
        var created = as(owner, () -> library.create(null, new Create(manifest)));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                int writer = i;
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        as(owner, () -> library.update(created.id(), null, new Save(manifest.deepCopy().put("writer", writer), 1L)));
                        return true;
                    } catch (ResponseStatusException ex) {
                        assertThat(ex.getStatusCode().value()).isEqualTo(409); return false;
                    }
                }));
            }
            start.countDown();
            assertThat(List.of(results.get(0).get(30, TimeUnit.SECONDS), results.get(1).get(30, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        }
        assertThat(as(owner, () -> library.get(created.id(), null)).revision()).isEqualTo(2);
    }

    @Test void rawHttpManifestNumbersRemainExactAndUntypedNumbersKeepExistingPolicy() throws Exception {
        String request = "{\"manifest\":{\"pluginType\":\"DATA\",\"unknown\":{\"n\":90071992547409931234567890,\"d\":1.234567890123456789000,\"z\":null,\"items\":[2,1]}}}";
        String response = mvc.perform(post("/api/customer/plugin-library").with(authentication(token(owner)))
                .contentType("application/json").content(request)).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        assertThat(response).contains("90071992547409931234567890", "1.234567890123456789000", "\"z\":null", "[2,1]");
        String id = mapper.readTree(response).path("id").asText();
        String readback = mvc.perform(get("/api/customer/plugin-library/{id}", id).with(authentication(token(owner))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(readback).contains("90071992547409931234567890", "1.234567890123456789000");
        assertThat(mapper.readValue("{\"n\":1.25}", Map.class).get("n")).isInstanceOf(Double.class);
        mvc.perform(post("/api/customer/plugin-library").with(authentication(token(owner)))
                .contentType("application/json").content("{\"manifest\":{\"pluginType\":\"DATA\",\"nested\":{\"x\":1,\"x\":2}}}"))
            .andExpect(status().isBadRequest());
    }

    @Test void privateVersionInstallsCompileWithExactConfigAndRemainEditableAfterArchive() throws Exception {
        var admin = user(null, "PLATFORM_ADMIN");
        var manifest = seed();
        var created = as(owner, () -> library.create(null, new Create(manifest)));
        as(owner, () -> library.createVersion(created.id(), null, new Save(manifest, 1L)));
        var deployment = as(admin, () -> deploymentService.createDeployment(
            new com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest("Private " + UUID.randomUUID(), "dev", "dev-openai-qdrant", null, "PLATFORM_MANAGED", owner.getCustomerId(), null)));
        as(admin, () -> {
            var draft = deploymentService.getActiveDraftForDeployment(deployment.id());
            ObjectNode provider = draft.providerConfig().deepCopy();
            provider.put("qdrantCloudRegionId", "eu-west-1");
            return deploymentService.updateDraft(draft.id(), new com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest(
                null, null, null, provider, null, null));
        });
        var config = mapper.readTree("{\"documentStorageBindingRef\":\"slot:document-storage\",\"unknown\":{\"precise\":0.123456789012345678900,\"huge\":123456789012345678901234567890,\"null\":null}}");
        var installed = as(admin, () -> installService.createInstall(deployment.id(),
            new com.ai.fabric.platform.backend.marketplace.model.CreateDeploymentMarketplaceInstallRequest(created.id(), "1.0.0", config, mapper.createObjectNode())));
        assertThat(installed.config()).isEqualTo(config);
        assertThat(as(owner, () -> library.get(created.id(), null)).installs().savedCount()).isEqualTo(1);
        assertThat(as(owner, () -> library.get(created.id(), null)).installs().appliedCount()).isZero();
        var next = as(admin, () -> installService.updateInstall(deployment.id(), installed.id(),
            new com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest(null, "DISABLED", null, null, installed.updatedAt())));
        assertThat(next.updatedAt()).isAfter(installed.updatedAt());
        assertThatThrownBy(() -> as(admin, () -> installService.updateInstall(deployment.id(), installed.id(),
            new com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest(null, "ENABLED", null, null, installed.updatedAt()))))
            .hasMessageContaining("409");
        assertThatThrownBy(() -> as(admin, () -> { installService.deleteInstall(deployment.id(), installed.id(), installed.updatedAt()); return null; }))
            .hasMessageContaining("409");
        as(owner, () -> library.createVersion(created.id(), null, new Save(manifest.deepCopy().put("version", "2.0.0"), 2L)));
        as(owner, () -> library.archive(created.id(), null, new Revision(3L)));
        var enabled = as(admin, () -> installService.updateInstall(deployment.id(), installed.id(),
            new com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest(null, "ENABLED", null, null, next.updatedAt())));
        assertThat(enabled.config()).isEqualTo(config);
        assertThat(enabled.pluginVersion()).isEqualTo("1.0.0");
        assertThatThrownBy(() -> as(admin, () -> installService.updateInstall(deployment.id(), installed.id(),
            new com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest("2.0.0", null, null, null, enabled.updatedAt()))))
            .hasMessageContaining("409");
        as(admin, () -> compiler.syncDeploymentDraftForTrustedCaller(deployment.id()));
        var ownedSecond = as(admin, () -> deploymentService.createDeployment(
            new com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest("Second " + UUID.randomUUID(), "dev", "dev-openai-qdrant", null, "PLATFORM_MANAGED", owner.getCustomerId(), null)));
        assertThatThrownBy(() -> as(admin, () -> installService.createInstall(ownedSecond.id(),
            new com.ai.fabric.platform.backend.marketplace.model.CreateDeploymentMarketplaceInstallRequest(created.id(), "1.0.0", config, mapper.createObjectNode()))))
            .hasMessageContaining("409");
        var otherDeployment = as(admin, () -> deploymentService.createDeployment(
            new com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest("Other " + UUID.randomUUID(), "dev", "dev-openai-qdrant", null, "PLATFORM_MANAGED", other.getCustomerId(), null)));
        assertThatThrownBy(() -> as(admin, () -> installService.createInstall(otherDeployment.id(),
            new com.ai.fabric.platform.backend.marketplace.model.CreateDeploymentMarketplaceInstallRequest(created.id(), "1.0.0", config, mapper.createObjectNode()))))
            .hasMessageContaining("404");
        // A corrupt or imported foreign pin must also fail in the trusted compiler path.
        var pin = installs.findById(installed.id()).orElseThrow(); pin.setDeploymentId(otherDeployment.id()); installs.saveAndFlush(pin);
        assertThatThrownBy(() -> as(admin, () -> compiler.syncDeploymentDraftForTrustedCaller(otherDeployment.id())))
            .hasMessageContaining("404");
    }

    @Test void technicalIdentityIsFixedAfterVersionAndDuplicateVersionDoesNotChangeDraft() throws Exception {
        var manifest = seed();
        var created = as(owner, () -> library.create(null, new Create(manifest)));
        as(owner, () -> library.createVersion(created.id(), null, new Save(manifest, 1L)));
        assertThatThrownBy(() -> as(owner, () -> library.createVersion(created.id(), null, new Save(manifest.deepCopy().put("description", "changed"), 2L))))
            .hasMessageContaining("409");
        assertThatThrownBy(() -> as(owner, () -> library.update(created.id(), null, new Save(manifest.deepCopy().put("pluginId", "changed"), 2L))))
            .hasMessageContaining("technical pluginId");
        assertThat(as(owner, () -> library.get(created.id(), null)).manifest()).isEqualTo(manifest);
        assertThat(as(owner, () -> library.get(created.id(), null)).revision()).isEqualTo(2);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"SQL_QUERY", "FILE_FOLDER", "PACKAGED_SEED", "MOUNTED_FOLDER", "STATIC_S3"})
    void unsupportedSourceIsASavableDraftButNotAUsablePrivateVersion(String kind) throws Exception {
        var manifest = GuidedDataPluginSeedsTest.unsafeSeed(mapper, kind);
        var created = as(owner, () -> library.create(null, new Create(manifest)));
        var check = as(owner, () -> library.check(created.id(), null, new Save(manifest, 1L)));
        assertThat(check.valid()).isFalse();
        assertThat(check.issues()).anyMatch(issue -> issue.path().startsWith("/contributions/datasets/0/"));
        assertThatThrownBy(() -> as(owner, () -> library.createVersion(created.id(), null, new Save(manifest, 1L))))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        assertThat(as(owner, () -> library.get(created.id(), null)).revision()).isEqualTo(1);
        assertThat(as(owner, () -> library.get(created.id(), null)).manifest()).isEqualTo(manifest);
    }

    @Test void privateVersionsCannotEnterOrBeMutatedByThePublicPublisherWorkflow() throws Exception {
        var manifest = seed(); var admin = user(null, "PLATFORM_ADMIN");
        var created = as(owner, () -> library.create(null, new Create(manifest)));
        var version = as(owner, () -> library.createVersion(created.id(), null, new Save(manifest, 1L))).versions().getFirst();
        var review = new com.ai.fabric.platform.backend.marketplace.model.ReviewMarketplacePublisherSubmissionRequest("not public");
        for (Supplier<?> command : List.<Supplier<?>>of(
            () -> publisher.validateSubmission(version.id(), review),
            () -> publisher.publishSubmission(version.id(), review),
            () -> publisher.rejectSubmission(version.id(), review))) {
            assertThatThrownBy(() -> as(admin, command)).hasMessageContaining("404");
        }
        assertThat(versions.findById(version.id()).orElseThrow().getStatus()).isEqualTo("PUBLISHED");
        assertThat(plugins.findById(created.id()).orElseThrow().getVisibility()).isEqualTo("CUSTOMER_PRIVATE");
    }

    @Test void archivedUnsupportedPrivateTypesRemainAuditOnlyAndCannotOpenTheDataLibrary() throws Exception {
        var manifest = seed();
        var created = as(owner, () -> library.create(null, new Create(manifest)));
        var audit = plugins.findById(created.id()).orElseThrow();
        audit.setPluginType("ACTION"); audit.setStatus("ARCHIVED"); plugins.saveAndFlush(audit);
        assertThat(as(owner, () -> library.list(null)).plugins()).noneMatch(plugin -> plugin.id().equals(created.id()));
        assertThatThrownBy(() -> as(owner, () -> library.get(created.id(), null))).hasMessageContaining("404");
        assertThatThrownBy(() -> as(owner, () -> library.update(created.id(), null, new Save(created.manifest(), 1L)))).hasMessageContaining("404");
        assertThat(plugins.findById(created.id()).orElseThrow().getDraftManifestJson()).isNotBlank();
    }

    ObjectNode seed() throws Exception { return GuidedDataPluginSeedsTest.seed(mapper, "S3_COMPATIBLE_OBJECT_STORAGE"); }
    PlatformUserEntity customer() {
        var customer = new PlatformCustomerEntity();
        customer.setId("lib-" + UUID.randomUUID()); customer.setSlug(customer.getId()); customer.setName(customer.getId());
        customer.setStatus("ACTIVE"); customer.setCreatedAt(Instant.now()); customer.setUpdatedAt(Instant.now());
        customers.saveAndFlush(customer);
        return user(customer.getId(), "CUSTOMER_ADMIN");
    }
    PlatformUserEntity user(String customerId, String role) {
        var user = new PlatformUserEntity();
        user.setId(UUID.randomUUID().toString()); user.setEmail(user.getId() + "@test.invalid"); user.setDisplayName("Test author");
        user.setRole(role); user.setStatus("ACTIVE"); user.setCustomerId(customerId); user.setPasswordHash("not-a-password");
        user.setCreatedAt(Instant.now()); user.setUpdatedAt(Instant.now()); return users.saveAndFlush(user);
    }
    static UsernamePasswordAuthenticationToken token(PlatformUserEntity user) {
        var role = PlatformRole.valueOf(user.getRole());
        var principal = new PlatformPrincipal(user.getEmail(), role, user.getDisplayName(), "SESSION");
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
    static <T> T as(PlatformUserEntity user, Supplier<T> operation) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(token(user)); SecurityContextHolder.setContext(context);
        try { return operation.get(); } finally { SecurityContextHolder.setContext(previous); }
    }
}
