package com.ai.fabric.platform.backend.deployment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformManagedServiceOwnershipTest {
    PlatformManagedServiceOwnershipFixture f;
    @BeforeEach void setup() { f = new PlatformManagedServiceOwnershipFixture(); }

    @Test void newCreatedReceiptSupportsReconcileScaleRestartRecreateAndVerifiedDecommission() {
        f.provisioning.reconcile("shared","target");
        assertThat(f.row().getStatus()).isEqualTo("ACTIVE");
        assertThat(f.row().getDetailsJson()).contains("serviceOwnershipReceiptId").contains("application-1");
        assertThat(f.journalState()).isEqualTo("OWNED");
        f.provisioning.reconcile("shared");
        f.provisioning.scale("shared",2);
        f.provisioning.restart("shared");
        f.provisioning.requireSharedServiceMutationAuthority("shared");
        assertThat(f.effects).isEqualTo(1);
        f.provisioning.forceRecreate("shared");
        assertThat(f.jdbc.queryForList("SELECT state FROM platform_managed_service_ownership ORDER BY generation",String.class))
            .containsExactly("DELETED","OWNED");
        f.provisioning.decommission("shared");
        assertThat(f.row().getStatus()).isEqualTo("DECOMMISSIONED");
        assertThat(f.applications).isEmpty();
        assertThat(f.journalState()).isEqualTo("DELETED");
        f.provisioning.decommission("shared");
        verify(f.api,times(2)).delete(eq(f.connection),anyString(),eq(true),eq(false),eq(false),eq(false));
        verifyNoInteractions(f.railway);
    }

    @Test void freshDockerImageWorkspaceRemainsSupportedWithoutImplicitStorage() {
        f.standaloneWorkspace();
        f.provisioning.reconcile("shared");
        assertThat(f.row().getStatus()).isEqualTo("ACTIVE");
        verify(f.api).createDockerImageApplication(eq(f.connection),any());
        verify(f.api,never()).createPublicApplication(any(),any());
        verify(f.secrets,never()).upsertManagedSecret(anyString(),anyString(),anyMap());
    }

    @ParameterizedTest @ValueSource(strings={"missingDigest","badDigest","badTag","badCommit","badRepository"})
    void malformedImageReleaseCannotCreateOrChangeProviderResources(String fault) throws Exception {
        f.standaloneWorkspace();
        var details = (com.fasterxml.jackson.databind.node.ObjectNode) f.mapper.readTree(f.row().getDetailsJson());
        switch (fault) {
            case "missingDigest" -> details.remove("imageDigest");
            case "badDigest" -> details.put("imageDigest","sha256:abc");
            case "badTag" -> details.put("imageTag","latest");
            case "badCommit" -> details.put("sourceCommit","c".repeat(40));
            case "badRepository" -> details.put("imageRepository","registry.example.test/workspace:latest");
            default -> throw new AssertionError(fault);
        }
        f.details(details.toString());

        assertThatThrownBy(() -> f.provisioning.reconcile("shared")).isInstanceOf(ResponseStatusException.class);

        assertThat(f.effects).isZero();
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM platform_managed_service_ownership",Integer.class)).isZero();
        verify(f.secrets,never()).upsertManagedSecret(anyString(),anyString(),anyMap());
    }

    @ParameterizedTest @ValueSource(strings={"repository","digest","deploymentDigest"})
    void imageReadbackDriftRemainsMutationPendingInsteadOfMarkingTheServiceActive(String fault) {
        f.standaloneWorkspace();
        if ("deploymentDigest".equals(fault)) {
            when(f.api.start(eq(f.connection),anyString(),anyBoolean(),anyBoolean())).thenAnswer(call -> {
                String uuid = call.getArgument(1);
                var app = f.applications.get(uuid);
                f.applications.put(uuid,f.image(app,app.imageRepository(),"sha256-"+"c".repeat(64)));
                return f.action(uuid);
            });
        } else {
            doAnswer(call -> {
                String uuid = call.getArgument(1);
                var app = f.applications.get(uuid);
                f.applications.put(uuid,f.image(app,"repository".equals(fault) ? "registry.example.test/other" : app.imageRepository(),
                    "digest".equals(fault) ? "sha256-"+"c".repeat(64) : app.imageTag()));
                return null;
            }).when(f.api).updateDockerImageApplication(eq(f.connection),anyString(),any());
        }

        assertThatThrownBy(() -> f.provisioning.reconcile("shared"))
            .hasMessageContaining("MANAGED_PRODUCT_IMAGE_REFERENCE_DRIFT");

        assertThat(f.journalState()).isEqualTo("MUTATION_PENDING");
        assertThat(f.row().getStatus()).isEqualTo("RECONCILIATION_REQUIRED");
        if (!"deploymentDigest".equals(fault)) verify(f.api,never()).start(any(),anyString(),anyBoolean(),anyBoolean());
    }

    @Test void decommissionRetainsSuppliedSharedCredentialEvenWhenItsNameHasTheManagedPrefix() {
        f.provisioning.reconcile("shared");
        when(f.secrets.isManagedSecretName(anyString())).thenReturn(true);
        f.jdbc.update("INSERT INTO platform_managed_product_services(id,details_json,secret_name) VALUES('other','{}','MANAGED_PRODUCT_SHARED_API_KEY')");
        f.provisioning.decommission("shared");
        assertThat(f.row().getDetailsJson()).contains("RETAINED_OWNERSHIP_UNPROVEN");
        assertThat(f.row().getSecretName()).isEqualTo("MANAGED_PRODUCT_SHARED_API_KEY");
        assertThat(f.jdbc.queryForObject("SELECT secret_name FROM platform_managed_product_services WHERE id='other'",String.class))
            .isEqualTo("MANAGED_PRODUCT_SHARED_API_KEY");
        verify(f.secrets,never()).clearManagedSecret(anyString(),anyMap());
    }

    @ParameterizedTest @ValueSource(strings={"restart","decommission"})
    void pathQualifiedUrlOnlyDependencyCannotBeIgnored(String action) {
        f.provisioning.reconcile("shared");
        String endpoint = f.row().getBaseUrl() + "/mcp?client=integration";
        f.jdbc.update("INSERT INTO platform_managed_product_services(id,details_json,base_url) VALUES('other','{}',?)",endpoint);
        clearInvocations(f.api);

        assertThatThrownBy(() -> {
            if ("restart".equals(action)) f.provisioning.restart("shared");
            else f.provisioning.decommission("shared");
        }).hasMessageContaining("RECONCILIATION_REQUIRED");

        verify(f.api,never()).restart(any(),anyString());
        verify(f.api,never()).delete(any(),anyString(),anyBoolean(),anyBoolean(),anyBoolean(),anyBoolean());
        assertThat(f.journalState()).isEqualTo("OWNED");
    }

    @Test void generatedCredentialOwnershipSurvivesAnOwnedApplicationGenerationChange() {
        provisionGeneratedCredential();
        f.provisioning.requireSharedServiceCredentialMutationAuthority("shared",f.row().getSecretName());
        f.provisioning.forceRecreate("shared");
        f.provisioning.requireSharedServiceCredentialMutationAuthority("shared",f.row().getSecretName());

        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM platform_managed_service_ownership WHERE credential_secret_name IS NOT NULL",
            Integer.class)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT state FROM platform_managed_service_ownership WHERE credential_secret_name IS NOT NULL",
            String.class)).isEqualTo("DELETED");
        assertThat(f.journalState()).isEqualTo("OWNED");
    }

    @ParameterizedTest @ValueSource(strings={"supplied","shared","foreignClaim","wrongName","foreignHandle"})
    void applicationOwnershipCannotAuthorizeForeignOrUnprovenCredentialRotation(String fault) {
        if ("supplied".equals(fault)) f.provisioning.reconcile("shared");
        else provisionGeneratedCredential();
        switch (fault) {
            case "shared" -> f.jdbc.update("INSERT INTO platform_managed_product_services(id,details_json,secret_name) VALUES('other','{}','MANAGED_PRODUCT_SHARED_API_KEY')");
            case "wrongName" -> f.jdbc.update("UPDATE platform_managed_product_services SET secret_name='GLOBAL_PROVIDER_KEY' WHERE id='registry'");
            case "foreignClaim" -> {
                f.provisioning.forceRecreate("shared");
                f.jdbc.update("UPDATE platform_managed_service_ownership SET target_profile_id='other-target' WHERE credential_secret_name IS NOT NULL");
            }
            case "foreignHandle" -> f.jdbc.update("INSERT INTO deployment_provider_resource_handles(provider_resource_uuid,status,metadata_json) VALUES('foreign-app','ACTIVE','{\"keyRef\":\"MANAGED_PRODUCT_SHARED_API_KEY\"}')");
            case "supplied" -> { }
            default -> throw new AssertionError(fault);
        }
        clearInvocations(f.api,f.secrets);

        assertThatThrownBy(() -> f.provisioning.requireSharedServiceCredentialMutationAuthority("shared",f.row().getSecretName()))
            .hasMessageContaining("RECONCILIATION_REQUIRED");

        verifyNoInteractions(f.secrets);
        verify(f.api,never()).updateEnvironmentVariables(any(),anyString(),anyList());
        assertThat(f.journalState()).isEqualTo("OWNED");
    }

    private void provisionGeneratedCredential() {
        f.jdbc.update("UPDATE platform_managed_product_services SET secret_name=NULL WHERE id='registry'");
        when(f.secrets.isSecretPresent("MANAGED_PRODUCT_SHARED_API_KEY")).thenReturn(false,true);
        f.provisioning.reconcile("shared");
    }

    @Test void newDefaultCredentialIsCreatedOnlyBehindThePendingMutationAndItsNameIsCommitted() {
        f.jdbc.update("UPDATE platform_managed_product_services SET secret_name=NULL WHERE id='registry'");
        when(f.secrets.isSecretPresent("MANAGED_PRODUCT_SHARED_API_KEY")).thenReturn(false);
        doAnswer(call -> {
            f.outsideTransaction(); f.assertPending("MUTATION_PENDING");
            assertThat(f.row().getSecretName()).isNull();
            when(f.secrets.resolveSecret("MANAGED_PRODUCT_SHARED_API_KEY")).thenReturn(call.getArgument(1));
            return null;
        }).when(f.secrets).upsertManagedSecret(eq("MANAGED_PRODUCT_SHARED_API_KEY"),anyString(),anyMap());
        f.provisioning.reconcile("shared");
        assertThat(f.row().getStatus()).isEqualTo("ACTIVE");
        assertThat(f.row().getSecretName()).isEqualTo("MANAGED_PRODUCT_SHARED_API_KEY");
        verify(f.secrets).upsertManagedSecret(eq("MANAGED_PRODUCT_SHARED_API_KEY"),anyString(),anyMap());
    }

    @ParameterizedTest @ValueSource(strings={"suppliedMissing","existingDefault","foreignReference"})
    void unknownOrReferencedCredentialNamesAreNeverCreatedOrReplaced(String fault) {
        if (!"suppliedMissing".equals(fault)) f.jdbc.update("UPDATE platform_managed_product_services SET secret_name=NULL WHERE id='registry'");
        when(f.secrets.isSecretPresent("MANAGED_PRODUCT_SHARED_API_KEY")).thenReturn("existingDefault".equals(fault));
        if ("foreignReference".equals(fault)) f.jdbc.update("INSERT INTO platform_managed_product_services(id,secret_name,details_json) VALUES('other','MANAGED_PRODUCT_SHARED_API_KEY','{}')");
        assertThatThrownBy(() -> f.provisioning.reconcile("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(f.journalState()).isEqualTo("MUTATION_PENDING");
        assertThat(f.row().getStatus()).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(f.row().getDetailsJson()).contains("MUTATION_PENDING").contains("RECONCILIATION_REQUIRED");
        verify(f.secrets,never()).upsertManagedSecret(anyString(),anyString(),anyMap());
        verify(f.secrets,never()).updateSecret(anyString(),anyString());
        verify(f.api,never()).start(any(),anyString(),anyBoolean(),anyBoolean());
    }

    @ParameterizedTest @ValueSource(strings={"graphUrl","releaseUrl","vectorEndpointUuid","vectorDetails","nullStatusHandle","malformed"})
    void exactSecondaryReferencesAndUncertainDocumentsAlsoDeny(String source) {
        f.provisioning.reconcile("shared");
        var app = f.applications.values().iterator().next();
        switch (source) {
            case "graphUrl" -> f.jdbc.update("INSERT INTO deployment_managed_dependency_graphs VALUES('graph',?)","{\"url\":\""+app.fqdn()+"\"}");
            case "releaseUrl" -> f.jdbc.update("INSERT INTO platform_deployment_releases VALUES(?)","{\"url\":\""+app.fqdn()+"\"}");
            case "vectorEndpointUuid" -> f.jdbc.update("INSERT INTO platform_deployment_managed_vector_resources(endpoint,details_json) VALUES(?,'{}')",app.uuid());
            case "vectorDetails" -> f.jdbc.update("INSERT INTO platform_deployment_managed_vector_resources(details_json) VALUES(?)","{\"owner\":\""+app.uuid()+"\"}");
            case "nullStatusHandle" -> f.jdbc.update("INSERT INTO deployment_provider_resource_handles(provider_resource_uuid,metadata_json) VALUES(?,'{}')",app.uuid());
            case "malformed" -> f.jdbc.update("INSERT INTO deployment_managed_dependency_graphs VALUES('graph','invalid-json')");
        }
        assertThatThrownBy(() -> f.provisioning.decommission("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        verify(f.api,never()).delete(any(),anyString(),anyBoolean(),anyBoolean(),anyBoolean(),anyBoolean());
        assertThat(f.journalState()).isEqualTo("OWNED");
    }

    @ParameterizedTest @ValueSource(strings={"reconcile","restart","scale","recreate","decommission","credential"})
    void historicalNameAndDescriptionNeverGrantLifecycleAuthority(String action) {
        f.details("{\"providerType\":\"COOLIFY\",\"targetProfileId\":\"target\",\"coolifyApplicationUuid\":\"legacy\"}");
        f.jdbc.update("UPDATE platform_managed_product_services SET status='ACTIVE',base_url='https://shared.example.test' WHERE id='registry'");
        f.applications.put("legacy",f.app("legacy","mcp-gateway-shared","https://shared.example.test",null));
        var before = f.jdbc.queryForMap("SELECT * FROM platform_managed_product_services WHERE id='registry'");
        assertThatThrownBy(() -> action(action)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(f.jdbc.queryForMap("SELECT * FROM platform_managed_product_services WHERE id='registry'")).isEqualTo(before);
        assertThat(f.jdbc.queryForList("SELECT * FROM platform_managed_service_ownership")).isEmpty();
        verifyNoInteractions(f.railway,f.secrets);
        verify(f.api,never()).delete(any(),anyString(),anyBoolean(),anyBoolean(),anyBoolean(),anyBoolean());
        verify(f.api,never()).updatePublicApplication(any(),anyString(),any());
    }

    @ParameterizedTest @ValueSource(strings={"name","fqdn"})
    void collisionBlocksInsteadOfAdoptingOrDeleting(String collision) {
        f.applications.put("foreign",f.app("foreign","name".equals(collision)?"mcp-gateway-shared":"other",
            "fqdn".equals(collision)?"https://shared.example.test":"https://other.example.test",null));
        assertThatThrownBy(() -> f.provisioning.reconcile("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(f.effects).isZero();
        assertThat(f.journalState()).isEqualTo("CREATE_PENDING");
        assertThat(f.applications).containsKey("foreign");
        verify(f.api,never()).createPublicApplication(any(),any());
        verify(f.api,never()).delete(any(),anyString(),anyBoolean(),anyBoolean(),anyBoolean(),anyBoolean());
    }

    @Test void visibleSourceCannotBeRetargetedAndCommittedProfileChangesInvalidateExistingBinding() {
        f.provisioning.reconcile("shared");
        var before = f.row().getDetailsJson();
        clearInvocations(f.api);
        assertThatThrownBy(() -> f.provisioning.reconcile("shared","different-target")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(f.row().getDetailsJson()).isEqualTo(before);
        verifyNoInteractions(f.api);
        f.jdbc.update("UPDATE deployment_target_profiles SET provider_config_json='{\"projectUuid\":\"different\"}' WHERE id='target'");
        assertThatThrownBy(() -> f.provisioning.restart("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        verify(f.api,never()).restart(any(),anyString());
    }

    @Test void lostCreateResponseRemainsPendingAndMatchingInventoryDoesNotRecoverIt() {
        when(f.api.createPublicApplication(eq(f.connection),any())).thenAnswer(call -> {
            var request = call.getArgument(1,CoolifyCreatePublicApplicationRequest.class);
            f.create(request.name(),request.domains(),null);
            throw new IllegalStateException("synthetic lost create response");
        });
        assertThatThrownBy(() -> f.provisioning.reconcile("shared")).hasMessageContaining("lost create response");
        assertThat(f.journalState()).isEqualTo("CREATE_PENDING");
        assertThatThrownBy(() -> f.provisioning.reconcile("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(f.effects).isEqualTo(1);
        assertThat(f.row().getDetailsJson()).doesNotContain("coolifyApplicationUuid");
    }

    @ParameterizedTest @ValueSource(strings={"missing","wrongUuid","wrongParent","unknownVolume"})
    void createRequiresExactLiveUuidPlacementAndEmptyStorage(String fault) {
        when(f.api.createPublicApplication(eq(f.connection),any())).thenAnswer(call -> {
            var request = call.getArgument(1,CoolifyCreatePublicApplicationRequest.class);
            String id = f.create(request.name(),request.domains(),null);
            if ("missing".equals(fault)) f.applications.clear();
            if ("wrongUuid".equals(fault)) f.applications.put(id,f.app("foreign",request.name(),request.domains(),null));
            if ("wrongParent".equals(fault)) ((com.fasterxml.jackson.databind.node.ObjectNode)f.applications.get(id).raw()).put("destination_uuid","foreign");
            if ("unknownVolume".equals(fault)) f.storages.put(id,List.of(new CoolifyApplicationStorageSummary("volume","persistent","unknown","/data",null,false,f.mapper.createObjectNode())));
            return id;
        });
        assertThatThrownBy(() -> f.provisioning.reconcile("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(f.journalState()).isEqualTo("CREATE_PENDING");
        verify(f.api,never()).updateEnvironmentVariables(any(),anyString(),anyList());
    }

    @ParameterizedTest @ValueSource(strings={"registry","graph","handle","release","vector","global"})
    void foreignReferencesDenyEveryMutationAndDeletion(String source) {
        f.provisioning.reconcile("shared");
        String uuid = f.applications.keySet().iterator().next();
        String json = "{\"resourceUuid\":\""+uuid+"\"}";
        switch (source) {
            case "registry" -> f.jdbc.update("INSERT INTO platform_managed_product_services(id,details_json) VALUES('other',?)",json);
            case "graph" -> f.jdbc.update("INSERT INTO deployment_managed_dependency_graphs VALUES('graph',?)",json);
            case "handle" -> f.jdbc.update("INSERT INTO deployment_provider_resource_handles(provider_resource_uuid,status,metadata_json) VALUES(?,'ACTIVE','{}')",uuid);
            case "release" -> f.jdbc.update("INSERT INTO platform_deployment_releases VALUES(?)",json);
            case "vector" -> f.jdbc.update("INSERT INTO platform_deployment_managed_vector_resources(resource_reference,details_json) VALUES(?,'{}')",uuid);
            case "global" -> new ManagedResourceOwnershipService(f.jdbc,f.mapper,f.transactions).create(ManagedResourceOwnershipFixture.SCOPE,"coolify","APPLICATION","foreign-parent","foreign-name",() -> uuid,
                value -> new ManagedResourceOwnershipService.Identity(value,f.connection.baseUrl()));
        }
        clearInvocations(f.api);
        assertThatThrownBy(() -> f.provisioning.restart("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThatThrownBy(() -> f.provisioning.decommission("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        verify(f.api,never()).restart(any(),anyString());
        verify(f.api,never()).delete(any(),anyString(),anyBoolean(),anyBoolean(),anyBoolean(),anyBoolean());
        assertThat(f.journalState()).isEqualTo("OWNED");
    }

    @Test void staleOwnedUuidAndInventoriedButMissingResourceNeverAuthorizeDeletion() {
        f.provisioning.reconcile("shared");
        var app = f.applications.values().iterator().next();
        f.applications.clear();
        assertThatThrownBy(() -> f.provisioning.forceRecreate("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        when(f.api.getApplication(f.connection,app.uuid())).thenReturn(Optional.empty());
        f.applications.put(app.uuid(),app);
        assertThatThrownBy(() -> f.provisioning.decommission("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        verify(f.api,never()).delete(any(),anyString(),anyBoolean(),anyBoolean(),anyBoolean(),anyBoolean());
    }

    @Test void unknownMutationAndDeleteOutcomesRemainBlockedWithoutRetryOrSecretClear() {
        f.provisioning.reconcile("shared");
        when(f.api.restart(eq(f.connection),anyString())).thenThrow(new IllegalStateException("lost action response"));
        assertThatThrownBy(() -> f.provisioning.restart("shared")).hasMessageContaining("lost action response");
        assertThat(f.journalState()).isEqualTo("MUTATION_PENDING");
        assertThatThrownBy(() -> f.provisioning.restart("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        verify(f.api,times(1)).restart(any(),anyString());
        assertThatThrownBy(() -> f.provisioning.decommission("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        verify(f.secrets,never()).clearManagedSecret(anyString(),anyMap());
    }

    @Test void deletePendingDoesNotRetryButExactAbsenceCanFinishAndDeletedReappearanceDenies() {
        f.provisioning.reconcile("shared");
        var app = f.applications.values().iterator().next();
        when(f.api.delete(eq(f.connection),anyString(),anyBoolean(),anyBoolean(),anyBoolean(),anyBoolean()))
            .thenThrow(new IllegalStateException("lost delete response"));
        assertThatThrownBy(() -> f.provisioning.decommission("shared")).hasMessageContaining("lost delete response");
        assertThat(f.journalState()).isEqualTo("DELETE_PENDING");
        assertThatThrownBy(() -> f.provisioning.decommission("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        verify(f.api,times(1)).delete(any(),anyString(),anyBoolean(),anyBoolean(),anyBoolean(),anyBoolean());
        f.applications.clear();
        f.provisioning.decommission("shared");
        assertThat(f.journalState()).isEqualTo("DELETED");
        f.provisioning.decommission("shared");
        f.applications.put(app.uuid(),app);
        var binding = f.ownership.existing(f.context());
        assertThatThrownBy(() -> f.ownership.observe(binding,true)).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThatThrownBy(() -> f.provisioning.forceRecreate("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        verify(f.api,times(1)).delete(any(),anyString(),anyBoolean(),anyBoolean(),anyBoolean(),anyBoolean());
        when(f.api.getApplication(f.connection,app.uuid())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> f.provisioning.decommission("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
    }

    private void action(String action) {
        switch (action) {
            case "reconcile" -> f.provisioning.reconcile("shared");
            case "restart" -> f.provisioning.restart("shared");
            case "scale" -> f.provisioning.scale("shared",2);
            case "recreate" -> f.provisioning.forceRecreate("shared");
            case "decommission" -> f.provisioning.decommission("shared");
            case "credential" -> f.provisioning.requireSharedServiceMutationAuthority("shared");
        }
    }

    @ParameterizedTest @ValueSource(strings={"null","blank","missing","wrongDeployment","wrongApplication","failed","unfinished"})
    void healthyApplicationCannotHideUncertainOrUnrelatedActionReadback(String fault) {
        f.provisioning.reconcile("shared");
        var uuid = f.applications.keySet().iterator().next();
        when(f.api.restart(eq(f.connection),eq(uuid))).thenReturn("null".equals(fault) ? null :
            new CoolifyActionResponse("queued","blank".equals(fault)?" ":"next-deployment",f.mapper.createObjectNode()));
        when(f.api.getDeployment(f.connection,"next-deployment")).thenReturn("missing".equals(fault) ? Optional.empty() :
            Optional.of(new CoolifyDeploymentSummary("wrongDeployment".equals(fault)?"foreign":"next-deployment",f.lastName,
                "wrongApplication".equals(fault)?"foreign":uuid,"failed".equals(fault)?"FAILED":"unfinished".equals(fault)?"QUEUED":"FINISHED",
                null,null,null,null,"finished",f.mapper.createObjectNode())));
        assertThatThrownBy(() -> f.provisioning.restart("shared")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(f.journalState()).isEqualTo("MUTATION_PENDING");
        assertThatThrownBy(() -> f.provisioning.restart("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        verify(f.api,times(1)).restart(any(),anyString());
    }
}
