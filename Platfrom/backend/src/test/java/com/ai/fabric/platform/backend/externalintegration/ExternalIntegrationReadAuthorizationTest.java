package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.customerselfservice.entity.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.*;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.customerselfservice.web.CustomerSelfServiceExceptionHandler;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity;
import com.ai.fabric.platform.backend.tenant.repository.*;
import com.ai.fabric.platform.backend.web.PlatformApiExceptionHandler;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExternalIntegrationReadAuthorizationTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void absentBindingIsNotFoundOnlyAfterExactScopeAuthorization() throws Exception {
        var r = new ReadFixture(false);
        assertThatThrownBy(r::read).isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
            error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
        verify(r.f.store).binding(r.f.deployment.getId(), r.f.version.getId(), r.f.target.getId(), r.f.profile.bindingSlotId());
        r.assertNoEffects();
    }

    @Test void approvalOptionsExposeOnlyPublicReviewedFieldsWithoutBindingOrCredentialEffects() throws Exception {
        var r = new ReadFixture(false);
        var approval = r.approvalOption();
        String response = r.mvc.perform(r.optionsRequest()).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(approval.id()))
            .andExpect(jsonPath("$[0].config.endpointUrl").value("https://provider.example.com/actions"))
            .andExpect(jsonPath("$[0].accountScope.externalAccount").value("reviewed-account"))
            .andReturn().getResponse().getContentAsString();
        var fields = r.f.mapper.readTree(response).get(0);
        assertThat(fields.size()).isEqualTo(8);
        assertThat(r.f.codec.read(response, ApprovalOption[].class)).containsExactly(new ApprovalOption(approval.id(),
            r.f.version.getId(), r.f.target.getId(), r.f.profile.bindingSlotId(), r.f.profile.id(),
            approval.config(), approval.accountScope(), approval.reviewReference()));
        assertThat(response).doesNotContain("synthetic-customer-key", "synthetic-integration-signing-key", "apiKey",
            "integrity", "seal", "requestFingerprint", "reviewedBy", "secretReferences", "secretRevisions",
            "secretFields", "MANAGED_EXTERNAL_INTEGRATION", "operationKey", "offerFingerprint",
            "private-reviewer", "private-operation", "private-request-fingerprint", r.f.integrity.seal(approval));
        verify(r.f.store).approvals(approval.scope());
        var request = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(r.authorization).authorize(eq(r.customer.getId()), request.capture());
        assertThat(request.getValue().operation()).isEqualTo(Operation.READ);
        assertThat(request.getValue().bindingSlotId()).isNull();
        assertThat(request.getValue().selectedProfileId()).isNull();
        verify(r.composition, never()).requireVersion(any(), any());
        verify(r.placement).requireConfiguredPlacement(same(r.f.target), eq(TargetIntent.CUSTOMER_STAGING));
        verify(r.placement, never()).requireFreshEvidence(any(), any());
        r.assertNoEffects();
    }

    @Test void missingApprovalReturnsEmptyOnlyAfterNormalReadAuthorization() throws Exception {
        var r = new ReadFixture(false);
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) r.f.mapper.valueToTree(r.f.definition);
        tree.set("operations", r.f.mapper.valueToTree(Set.of(Operation.READ)));
        r.publish(r.f.mapper.treeToValue(tree, Definition.class), true);
        r.mvc.perform(r.optionsRequest()).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(r.authorization, never()).authorize(anyString(), argThat(request -> request.operation() != Operation.READ));
        verify(r.f.store).approvals(any(Scope.class));
        r.assertNoEffects();
    }

    @Test void approvalOptionsKeepAccountPlacementAndActualSelectionGates() throws Exception {
        for (String fault : List.of("customer", "tenant", "placement", "version", "target", "slot", "profile")) {
            var r = new ReadFixture(false);
            switch (fault) {
                case "customer" -> r.f.deployment.setCustomerId("another-customer");
                case "tenant" -> r.f.deployment.setTenantId("another-tenant");
                case "placement" -> doThrow(com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.conflict(
                    com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.PLACEMENT_DENIED))
                    .when(r.placement).requireConfiguredPlacement(any(), any());
                case "version" -> r.f.version.setStatus("DRAFT");
                case "target" -> r.f.target.setEnvironmentName("changed-environment");
                case "slot", "profile" -> { }
            }
            assertThatThrownBy(() -> r.service.approvals("offer-generic", r.f.deployment.getId(), r.f.version.getId(),
                r.f.target.getId(), fault.equals("slot") ? "foreign-slot" : r.f.profile.bindingSlotId(),
                fault.equals("profile") ? "foreign-profile" : r.f.profile.id())).as(fault).isNotNull();
            verify(r.f.store, never()).approvals(any(Scope.class));
            r.assertNoEffects();
        }
    }

    @Test void approvalOptionsRejectTamperedOrStaleRowsInsteadOfReturningPartialResults() throws Exception {
        for (String fault : List.of("integrity", "version", "target", "profile", "config")) {
            var r = new ReadFixture(false);
            var original = r.approvalOption();
            var changed = new Approval(original.id(), original.scope(),
                fault.equals("offer") ? "stale-offer" : original.offerFingerprint(),
                fault.equals("version") ? "stale-version" : original.versionConfigHash(),
                fault.equals("target") ? "stale-target" : original.targetFingerprint(),
                fault.equals("profile") ? new Profile(original.profile().schemaVersion(), "foreign-profile",
                    original.profile().bindingSlotId(), original.profile().pluginId(), original.profile().pluginVersionId(),
                    original.profile().manifestSha256(), original.profile().targetProfileIds(), original.profile().configFields(),
                    original.profile().secretFields(), original.profile().endpointFields(), original.profile().accountScopeFields(),
                    original.profile().allowedHosts()) : original.profile(),
                fault.equals("config") ? Map.of("endpointUrl", "https://unreviewed.example.com") : original.config(),
                original.accountScope(), original.reviewReference(), original.reviewedBy(), original.operationKey(), original.requestFingerprint());
            var rows = List.of(
                new ExternalIntegrationStore.Row<>(original, r.f.integrity.seal(original), "APPROVED", 0),
                new ExternalIntegrationStore.Row<>(changed, fault.equals("integrity") ? "0".repeat(64) : r.f.integrity.seal(changed), "APPROVED", 0));
            when(r.f.store.approvals(original.scope())).thenReturn(rows);
            assertThatThrownBy(r::options).as(fault).hasMessageContaining(switch (fault) {
                case "integrity" -> "INTEGRITY";
                case "config" -> "ENDPOINT";
                default -> "APPROVAL_STALE_OR_FOREIGN";
            });
            r.assertNoEffects();
        }
    }

    @Test void signedApprovalCannotCrossAnyRequestedScopeDimension() throws Exception {
        for (UnaryOperator<Scope> change : List.<UnaryOperator<Scope>>of(
            s -> new Scope("other-offer", s.customerId(), s.tenantId(), s.deploymentId(), s.versionId(), s.targetProfileId(), s.bindingSlotId(), s.profileId()),
            s -> new Scope(s.offerId(), "other-customer", s.tenantId(), s.deploymentId(), s.versionId(), s.targetProfileId(), s.bindingSlotId(), s.profileId()),
            s -> new Scope(s.offerId(), s.customerId(), "other-tenant", s.deploymentId(), s.versionId(), s.targetProfileId(), s.bindingSlotId(), s.profileId()),
            s -> new Scope(s.offerId(), s.customerId(), s.tenantId(), "other-deployment", s.versionId(), s.targetProfileId(), s.bindingSlotId(), s.profileId()),
            s -> new Scope(s.offerId(), s.customerId(), s.tenantId(), s.deploymentId(), "other-version", s.targetProfileId(), s.bindingSlotId(), s.profileId()),
            s -> new Scope(s.offerId(), s.customerId(), s.tenantId(), s.deploymentId(), s.versionId(), "other-target", s.bindingSlotId(), s.profileId()),
            s -> new Scope(s.offerId(), s.customerId(), s.tenantId(), s.deploymentId(), s.versionId(), s.targetProfileId(), "other-slot", s.profileId()),
            s -> new Scope(s.offerId(), s.customerId(), s.tenantId(), s.deploymentId(), s.versionId(), s.targetProfileId(), s.bindingSlotId(), "other-profile"))) {
            var r = new ReadFixture(false);
            var original = r.approvalOption();
            var foreign = new Approval(original.id(), change.apply(original.scope()), original.offerFingerprint(), original.versionConfigHash(),
                original.targetFingerprint(), original.profile(), original.config(), original.accountScope(), original.reviewReference(),
                original.reviewedBy(), original.operationKey(), original.requestFingerprint());
            var row = new ExternalIntegrationStore.Row<>(foreign, r.f.integrity.seal(foreign), "APPROVED", 0);
            when(r.f.store.approvals(original.scope())).thenReturn(List.of(row));
            assertThatThrownBy(r::options).hasMessageContaining("APPROVAL_STALE_OR_FOREIGN");
            r.assertNoEffects();
        }
    }

    @Test void customerSummaryUsesRealReadAuthorizationAndStillRequiresExactPublishedExternalSelection() throws Exception {
        var r = new ReadFixture(true);
        var result = r.read();
        assertThat(result).isEqualTo(new Summary(r.bound.id(), r.bound.approvalId(), r.f.deployment.getId(), r.f.version.getId(),
            r.f.target.getId(), r.f.profile.bindingSlotId(), r.f.profile.id(), "CONFIGURED", 0));
        var request = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(r.authorization).authorize(eq(r.customer.getId()), request.capture());
        assertThat(request.getValue().operation()).isEqualTo(Operation.READ);
        assertThat(request.getValue().bindingSlotId()).isNull();
        assertThat(request.getValue().selectedProfileId()).isNull();
        assertThat(request.getValue().changedSettings()).isEmpty();
        assertThat(request.getValue().deploymentId()).isEqualTo(r.f.deployment.getId());
        assertThat(request.getValue().tenantId()).isEqualTo(r.tenant.getId());
        assertThat(request.getValue().targetProfileId()).isEqualTo(r.f.target.getId());
        verify(r.composition, never()).requireVersion(any(), any());
        verify(r.composition, never()).requireProfile(any(), any());
        verify(r.placement).requireConfiguredPlacement(same(r.f.target), eq(TargetIntent.CUSTOMER_STAGING));
        verify(r.placement, never()).requireFreshEvidence(any(), any());
        r.assertNoEffects();
    }

    @Test void realHttpReadReturnsOnlyRedactedScopedMetadataAndMissingBindingIsNotAnAuthDenial() throws Exception {
        var r = new ReadFixture(true);
        String response = r.mvc.perform(r.request()).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(r.bound.id()))
            .andExpect(jsonPath("$.approvalId").value(r.bound.approvalId()))
            .andExpect(jsonPath("$.status").value("CONFIGURED"))
            .andExpect(jsonPath("$.revision").value(0)).andReturn().getResponse().getContentAsString();
        var fields = r.f.mapper.readTree(response);
        assertThat(fields.size()).isEqualTo(9);
        assertThat(response).doesNotContain("synthetic-customer-key", "provider.example.com", "reviewed-account",
            "secretReferences", "secretRevisions", "MANAGED_EXTERNAL_INTEGRATION", "requestFingerprint", "credentials");
        r.assertNoEffects();

        var missing = new ReadFixture(false);
        missing.mvc.perform(missing.request()).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("EXTERNAL_INTEGRATION_BINDING_REQUIRED"));
        missing.assertNoEffects();
    }

    @Test void readDoesNotRequireBindingReplacePermissionOrAuthorizeAMutationOperation() throws Exception {
        var r = new ReadFixture(true);
        var tree = r.f.mapper.valueToTree(r.f.definition);
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree).set("operations", r.f.mapper.valueToTree(Set.of(Operation.READ)));
        r.publish(r.f.mapper.treeToValue(tree, Definition.class), true);
        assertThat(r.read().status()).isEqualTo("CONFIGURED");
        verify(r.authorization, never()).authorize(anyString(), argThat(request -> request.operation() != Operation.READ));
        r.assertNoEffects();
    }

    @Test void sharedReadAuthorizesTheDeploymentAndLeavesSelectionValidationToTheOwningService() throws Exception {
        var r = new ReadFixture(true);
        var request = new AuthorizationRequest("offer-generic", r.f.target.getId(), r.f.deployment.getId(), r.tenant.getId(), null,
            Operation.READ, "read-operation", Set.of(), null, null, null);
        assertThat(r.authorization.authorize(r.customer.getId(), request).definition()).isNull();
        assertThat(r.read().status()).isEqualTo("CONFIGURED");
        r.assertNoEffects();
    }

    @Test void customerSessionAndActiveUserAccountRemainRequired() throws Exception {
        for (String fault : List.of("anonymous", "staff", "operator", "public-api", "customer-api-key", "inactive-user", "inactive-customer")) {
            var r = new ReadFixture(true);
            switch (fault) {
                case "anonymous" -> SecurityContextHolder.clearContext();
                case "staff" -> r.authenticate(PlatformRole.PLATFORM_ADMIN, "SESSION");
                case "operator" -> r.authenticate(PlatformRole.PLATFORM_OPERATOR, "SESSION");
                case "public-api" -> r.authenticate(PlatformRole.PUBLIC_API_CLIENT, "API_KEY");
                case "customer-api-key" -> r.authenticate(PlatformRole.CUSTOMER_ADMIN, "API_KEY");
                case "inactive-user" -> r.owner.setStatus("DISABLED");
                case "inactive-customer" -> r.customer.setStatus("DISABLED");
            }
            assertThatThrownBy(r::read).hasMessageContaining("ACTIVE_CUSTOMER_SCOPE_REQUIRED");
            verify(r.f.store, never()).binding(anyString(), anyString(), anyString(), anyString());
            r.assertNoEffects();
        }
    }

    @Test void foreignAccountTenantTamperedDefaultsAndPlacementRemainDeniedBeforeBindingLookup() throws Exception {
        for (String fault : List.of("customer", "tenant", "inactive-tenant", "offer-integrity", "placement", "target")) {
            var r = new ReadFixture(true);
            switch (fault) {
                case "customer" -> r.f.deployment.setCustomerId("foreign-customer");
                case "tenant" -> r.f.deployment.setTenantId("foreign-tenant");
                case "inactive-tenant" -> r.tenant.setStatus("DISABLED");
                case "offer-integrity" -> when(r.offers.findById("offer-generic")).thenReturn(Optional.of(new CustomerOfferEntity(
                    "offer-generic", r.f.codec.write(r.f.definition), "0".repeat(64), "review", "staff", Instant.now())));
                case "placement" -> doThrow(com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.conflict(
                    com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.PLACEMENT_DENIED))
                    .when(r.placement).requireConfiguredPlacement(any(), any());
                case "target" -> r.f.target.setEnvironmentName("foreign-environment");
            }
            String denial = switch (fault) {
                case "placement" -> "PLACEMENT_DENIED";
                case "target" -> "UNENTITLED_TARGET";
                case "offer-integrity" -> "REVIEWED_COMPOSITION_CHANGED";
                default -> "RESOURCE_SCOPE_MISMATCH";
            };
            assertThatThrownBy(() -> r.service.read("offer-generic", r.f.deployment.getId(), r.f.version.getId(), r.f.target.getId(),
                r.f.profile.bindingSlotId(), r.f.profile.id())).hasMessageContaining(denial);
            verify(r.f.store, never()).binding(anyString(), anyString(), anyString(), anyString());
            r.assertNoEffects();
        }
    }

    @Test void missingOrWrongSlotProfileAndTargetCannotBeDroppedOrInferredAfterGenericReadAuthorization() throws Exception {
        for (String[] selection : List.of(new String[]{"other-slot", "generic-profile", "target-stage"},
            new String[]{"business-actions", "other-profile", "target-stage"}, new String[]{"", "generic-profile", "target-stage"},
            new String[]{"business-actions", "", "target-stage"}, new String[]{"business-actions", "generic-profile", "other-target"})) {
            var r = new ReadFixture(true);
            r.mvc.perform(r.request(selection[2], selection[0], selection[1]))
                .andExpect(status().is4xxClientError());
            assertThatThrownBy(() -> r.service.read("offer-generic", r.f.deployment.getId(), r.f.version.getId(), selection[2], selection[0], selection[1]))
                .hasMessageContaining(selection[2].equals("other-target") ? "UNENTITLED_TARGET" : "PROFILE_SCOPE_MISMATCH");
            verify(r.f.store, never()).binding(anyString(), anyString(), anyString(), anyString());
            r.assertNoEffects();
        }
    }

    @Test void publishedVersionCompilerEvidenceAndCurrentManifestPinsRemainMandatoryForRead() throws Exception {
        for (String fault : List.of("version-owner", "draft", "version-hash", "compiler-profile", "compiler-install", "manifest", "manifest-status", "plugin-status")) {
            var r = new ReadFixture(true);
            switch (fault) {
                case "version-owner" -> r.f.version.setDeploymentId("foreign-deployment");
                case "draft" -> r.f.version.setStatus("DRAFT");
                case "version-hash" -> r.f.version.setConfigHash(" ");
                case "compiler-profile" -> {
                    var provenance = (com.fasterxml.jackson.databind.node.ObjectNode) r.f.mapper.readTree(r.f.version.getCompositionProvenanceJson());
                    provenance.remove("externalIntegrationProfiles"); r.f.version.setCompositionProvenanceJson(provenance.toString());
                }
                case "compiler-install" -> {
                    var provenance = (com.fasterxml.jackson.databind.node.ObjectNode) r.f.mapper.readTree(r.f.version.getCompositionProvenanceJson());
                    provenance.putArray("marketplaceInstalls"); r.f.version.setCompositionProvenanceJson(provenance.toString());
                }
                case "manifest" -> r.f.pluginVersions.findById(r.f.profile.pluginVersionId()).orElseThrow().setManifestJson("{}");
                case "manifest-status" -> r.f.pluginVersions.findById(r.f.profile.pluginVersionId()).orElseThrow().setStatus("DRAFT");
                case "plugin-status" -> r.f.plugins.findById(r.f.profile.pluginId()).orElseThrow().setStatus("RETIRED");
            }
            String denial = switch (fault) {
                case "version-owner", "draft", "version-hash" -> "VERSION_SCOPE_MISMATCH";
                case "compiler-profile" -> "PROFILE_SCOPE_MISMATCH";
                case "compiler-install" -> "COMPILER_PROFILE_MISMATCH";
                default -> "PLUGIN_PIN_MISMATCH";
            };
            assertThatThrownBy(r::read).hasMessageContaining(denial);
            verify(r.f.store, never()).binding(anyString(), anyString(), anyString(), anyString());
            r.assertNoEffects();
        }
    }

    @Test void bootstrapSlotChangesDoNotVetoThePublishedContractButActualProfileChangesDo() throws Exception {
        for (String fault : List.of("kind", "plugin", "supported-profile", "mutable-config-field", "profile-target")) {
            var r = new ReadFixture(true);
            var tree = (com.fasterxml.jackson.databind.node.ObjectNode) r.f.mapper.valueToTree(r.f.definition);
            var slot = (com.fasterxml.jackson.databind.node.ObjectNode) tree.path("bindingSlots").get(0);
            switch (fault) {
                case "kind" -> slot.put("kind", "CUSTOMER_S3");
                case "plugin" -> slot.put("pluginId", "foreign-plugin");
                case "supported-profile" -> slot.putArray("supportedProfileIds").add("other-profile");
                case "mutable-config-field" -> slot.put("installConfigField", "endpointUrl");
                case "profile-target" -> ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path("externalIntegrationProfiles").get(0))
                    .putArray("targetProfileIds").add("target-production");
            }
            r.publish(r.f.mapper.treeToValue(tree, Definition.class), true);
            if (fault.equals("profile-target")) {
                assertThatThrownBy(r::read).hasMessageContaining("COMPILER_PROFILE_MISMATCH");
                verify(r.f.store, never()).binding(anyString(), anyString(), anyString(), anyString());
            } else assertThat(r.read().status()).isEqualTo("CONFIGURED");
            r.assertNoEffects();
        }
    }

    @Test void bootstrapInvitationExpiryRevisionAndOperationListDoNotRevokeOwnedIntegrationReads() throws Exception {
        var r = new ReadFixture(true);
        r.f.grant.setStatus(EntitlementStatus.REVOKED); r.f.grant.setExpiresAt(Instant.EPOCH);
        r.f.grant.setRevision(99);
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) r.f.mapper.valueToTree(r.f.definition);
        tree.putArray("operations");
        r.publish(r.f.mapper.treeToValue(tree, Definition.class), true);
        assertThat(r.read().status()).isEqualTo("CONFIGURED");
        r.assertNoEffects();
    }

    @Test void httpReadRequiresEveryExplicitScopeQueryParameter() throws Exception {
        var r = new ReadFixture(true);
        var parameters = java.util.Map.of("versionId", r.f.version.getId(), "targetProfileId", r.f.target.getId(),
            "bindingSlotId", r.f.profile.bindingSlotId(), "selectedProfileId", r.f.profile.id());
        for (String missing : parameters.keySet()) {
            var request = get("/api/customer-self-service/catalogue/offer-generic/deployments/" + r.f.deployment.getId() + "/external-integrations");
            parameters.forEach((key, value) -> { if (!key.equals(missing)) request.param(key, value); });
            r.mvc.perform(request).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(r.authorization);
        verify(r.f.store, never()).binding(anyString(), anyString(), anyString(), anyString());
        r.assertNoEffects();
    }

    @Test void evenValidlySignedBindingCannotCrossAnyRequestedScopeDimension() throws Exception {
        for (UnaryOperator<Scope> change : List.<UnaryOperator<Scope>>of(
            s -> new Scope("other-offer", s.customerId(), s.tenantId(), s.deploymentId(), s.versionId(), s.targetProfileId(), s.bindingSlotId(), s.profileId()),
            s -> new Scope(s.offerId(), "other-customer", s.tenantId(), s.deploymentId(), s.versionId(), s.targetProfileId(), s.bindingSlotId(), s.profileId()),
            s -> new Scope(s.offerId(), s.customerId(), "other-tenant", s.deploymentId(), s.versionId(), s.targetProfileId(), s.bindingSlotId(), s.profileId()),
            s -> new Scope(s.offerId(), s.customerId(), s.tenantId(), "other-deployment", s.versionId(), s.targetProfileId(), s.bindingSlotId(), s.profileId()),
            s -> new Scope(s.offerId(), s.customerId(), s.tenantId(), s.deploymentId(), "other-version", s.targetProfileId(), s.bindingSlotId(), s.profileId()),
            s -> new Scope(s.offerId(), s.customerId(), s.tenantId(), s.deploymentId(), s.versionId(), "other-target", s.bindingSlotId(), s.profileId()),
            s -> new Scope(s.offerId(), s.customerId(), s.tenantId(), s.deploymentId(), s.versionId(), s.targetProfileId(), "other-slot", s.profileId()),
            s -> new Scope(s.offerId(), s.customerId(), s.tenantId(), s.deploymentId(), s.versionId(), s.targetProfileId(), s.bindingSlotId(), "other-profile"))) {
            var r = new ReadFixture(true);
            var row = r.f.bindings.values().iterator().next(); var original = row.value();
            var foreign = new Binding(original.id(), change.apply(original.scope()), original.approvalId(), original.secretReferences(), original.secretRevisions());
            r.f.bindings.replaceAll((key, ignored) -> new ExternalIntegrationStore.Row<>(foreign,
                r.f.integrity.seal(ExternalIntegrationService.bindingEvidence(foreign, row.status(), row.revision())), row.status(), row.revision()));
            assertThatThrownBy(r::read).hasMessageContaining("RESOURCE_SCOPE_MISMATCH");
            r.assertNoEffects();
        }
    }

    @Test void readVerifiesBindingIntegrityAndReportsRevocationWithoutRebindingOrHidingHistory() throws Exception {
        var r = new ReadFixture(true);
        var row = r.f.bindings.values().iterator().next();
        r.f.bindings.replaceAll((key, ignored) -> new ExternalIntegrationStore.Row<>(row.value(), "0".repeat(64), row.status(), row.revision()));
        assertThatThrownBy(r::read).hasMessageContaining("INTEGRITY");
        r.f.bindings.replaceAll((key, ignored) -> new ExternalIntegrationStore.Row<>(row.value(),
            r.f.integrity.seal(ExternalIntegrationService.bindingEvidence(row.value(), "REVOKED", 1)), "REVOKED", 1));
        var result = r.read();
        assertThat(result.status()).isEqualTo("REVOKED"); assertThat(result.revision()).isEqualTo(1);
        assertThat(result.id()).isEqualTo(row.value().id()); r.assertNoEffects();
    }

    private static final class ReadFixture {
        final ExternalIntegrationFixture f = new ExternalIntegrationFixture();
        final CustomerOfferRepository offers = mock(CustomerOfferRepository.class);
        final CustomerOfferEntitlementRepository grants = mock(CustomerOfferEntitlementRepository.class);
        final PlatformCustomerRepository customers = mock(PlatformCustomerRepository.class);
        final PlatformUserRepository users = mock(PlatformUserRepository.class);
        final PlatformTenantRepository tenants = mock(PlatformTenantRepository.class);
        final CustomerTargetPlacementService placement = mock(CustomerTargetPlacementService.class);
        final PlatformUserEntity owner = new PlatformUserEntity();
        final PlatformCustomerEntity customer = new PlatformCustomerEntity();
        final PlatformTenantEntity tenant = new PlatformTenantEntity();
        final CustomerReviewedCompositionService composition;
        final CustomerSelfServiceAuthorizationService authorization;
        final ExternalIntegrationService service;
        final Summary bound;
        final MockMvc mvc;

        ReadFixture(boolean bindingPresent) throws Exception {
            bound = bindingPresent ? f.bind() : null;
            owner.setId("owner"); owner.setEmail("owner@test.invalid"); owner.setRole("CUSTOMER_ADMIN");
            owner.setStatus("ACTIVE"); owner.setCustomerId(f.deployment.getCustomerId());
            customer.setId(f.deployment.getCustomerId()); customer.setStatus("ACTIVE");
            tenant.setId(f.deployment.getTenantId()); tenant.setCustomerId(customer.getId()); tenant.setStatus("ACTIVE");
            when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
            when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
            when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));
            when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
            f.deployment.setTemplateId(f.definition.templateId());
            f.grant.setCustomerId(customer.getId()); f.grant.setOwnerUserId(owner.getId()); f.grant.setOfferId("offer-generic");
            f.grant.setStatus(EntitlementStatus.ACTIVE); f.grant.setExpiresAt(Instant.now().plusSeconds(3600));
            var validator = Validation.buildDefaultValidatorFactory().getValidator();
            composition = spy(new CustomerReviewedCompositionService(f.codec, validator, f.plugins, f.pluginVersions,
                new MarketplaceManifestService(f.mapper), mock(DeploymentSourceArtifactRepository.class), f.targets,
                mock(CustomerWorkspaceProfileValidator.class), f.mapper,
                mock(com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyArtifactPolicy.class), f.policy));
            // Publication's unrelated artifact graph is mocked; version, target and external manifest policy are real.
            doAnswer(call -> { f.policy.validateDefinition(call.getArgument(0)); return null; }).when(composition).validate(any());
            var provenance = f.mapper.createObjectNode().put("templateId", f.definition.templateId());
            provenance.putArray("externalIntegrationProfiles").add(f.compiler.compilerEvidence(f.profile));
            var installs = provenance.putArray("marketplaceInstalls");
            for (PluginPin pin : List.of(f.definition.template(), f.definition.plugins().getFirst())) installs.addObject()
                .put("pluginId", pin.pluginId()).put("pluginVersionId", pin.versionId()).put("manifestSha256", "sha256:" + pin.manifestSha256())
                .put("pluginType", pin.pluginId().equals(f.definition.template().pluginId()) ? "TEMPLATE" : "ACTION");
            f.version.setCompositionProvenanceJson(provenance.toString());
            var customerAccess = new PlatformCustomerAccessService(users, customers);
            var entitlements = new CustomerOfferEntitlementService(offers, grants, f.commands, customerAccess, customers, users,
                f.codec, composition, placement, f.targets, f.audit);
            var access = new DeploymentAccessService(mock(DeploymentAssignmentRepository.class), mock(PublicApiDeploymentRepository.class), users, customerAccess);
            authorization = spy(new CustomerSelfServiceAuthorizationService(entitlements, composition, placement, f.deployments, f.releases, f.versions,
                f.targets, tenants, mock(PlatformConsumerRepository.class), access, f.associations,
                mock(com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver.class)));
            publish(f.definition, true);
            service = new ExternalIntegrationService(entitlements, authorization, composition, f.commands, f.deployments, f.versions, f.targets,
                f.policy, f.integrity, f.store, f.vault, f.secrets, f.codec, f.mapper, f.audit, validator,
                new ExternalIntegrationOwnerEligibility(grants, entitlements, tenants));
            mvc = MockMvcBuilders.standaloneSetup(new ExternalIntegrationController(service))
                .setControllerAdvice(new CustomerSelfServiceExceptionHandler(), new PlatformApiExceptionHandler()).build();
            authenticate(PlatformRole.CUSTOMER_ADMIN, "SESSION");
            clearInvocations(f.store, f.vault, f.commands, f.audit, f.secrets);
        }

        void publish(Definition definition, boolean associate) { publish(definition, associate, "offer-generic"); }
        void publish(Definition definition, boolean associate, String offerId) {
            var offer = new CustomerOfferEntity(offerId, f.codec.write(definition), f.codec.hash(definition), "review", "staff", Instant.now());
            when(offers.findById(offerId)).thenReturn(Optional.of(offer));
            when(grants.findForUpdate(customer.getId(), offerId)).thenReturn(Optional.of(f.grant));
            if (associate) when(f.associations.findById(f.deployment.getId())).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
                f.deployment.getId(), customer.getId(), tenant.getId(), offerId, f.target.getId(), offer.getFingerprint(), 3, "bootstrap", Instant.now())));
        }
        Summary read() { return service.read("offer-generic", f.deployment.getId(), f.version.getId(), f.target.getId(), f.profile.bindingSlotId(), f.profile.id()); }
        List<ApprovalOption> options() { return service.approvals("offer-generic", f.deployment.getId(), f.version.getId(), f.target.getId(), f.profile.bindingSlotId(), f.profile.id()); }
        MockHttpServletRequestBuilder optionsRequest() { return get("/api/customer-self-service/catalogue/offer-generic/deployments/"
            + f.deployment.getId() + "/external-integrations/approvals").param("versionId", f.version.getId())
            .param("targetProfileId", f.target.getId()).param("bindingSlotId", f.profile.bindingSlotId()).param("selectedProfileId", f.profile.id()); }
        Approval approvalOption() {
            var approval = new Approval("approval-option", new Scope("offer-generic", customer.getId(), tenant.getId(), f.deployment.getId(),
                f.version.getId(), f.target.getId(), f.profile.bindingSlotId(), f.profile.id()), f.codec.hash(f.definition), f.version.getConfigHash(),
                com.ai.fabric.platform.backend.deployment.service.DeploymentTargetProfileIdentity.fingerprint(f.mapper, f.target),
                f.profile, Map.of("endpointUrl", "https://provider.example.com/actions"), Map.of("externalAccount", "reviewed-account"),
                "review-ticket", "private-reviewer", "private-operation", "private-request-fingerprint");
            var row = new ExternalIntegrationStore.Row<>(approval, f.integrity.seal(approval), "APPROVED", 0);
            when(f.store.approvals(approval.scope())).thenReturn(List.of(row));
            return approval;
        }
        MockHttpServletRequestBuilder request() { return request(f.target.getId(), f.profile.bindingSlotId(), f.profile.id()); }
        MockHttpServletRequestBuilder request(String targetId, String slotId, String profileId) {
            return get("/api/customer-self-service/catalogue/offer-generic/deployments/" + f.deployment.getId() + "/external-integrations")
                .param("versionId", f.version.getId()).param("targetProfileId", targetId)
                .param("bindingSlotId", slotId).param("selectedProfileId", profileId);
        }
        void authenticate(PlatformRole role, String mode) {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new PlatformPrincipal(owner.getEmail(), role, "Owner", mode), "", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        }
        void assertNoEffects() {
            verify(f.store, never()).insertApproval(any(), anyString()); verify(f.store, never()).insertBinding(any(), anyString());
            verify(f.store, never()).revokeBinding(anyString(), anyLong(), anyString());
            verify(f.vault, never()).upsertManagedSecret(anyString(), anyString(), anyMap());
            verify(f.vault, never()).upsertTargetManagedSecret(anyString(), anyString(), anyString(), anyString(), anyMap());
            verify(f.vault, never()).clearManagedSecret(anyString(), anyMap());
            verify(f.vault, never()).resolveSecret(startsWith("MANAGED_EXTERNAL_INTEGRATION"));
            verifyNoInteractions(f.commands, f.audit, f.secrets);
            verify(grants, never()).saveAndFlush(any()); verify(offers, never()).saveAndFlush(any());
            assertThat(f.dnsCalls).hasValue(0);
        }
    }
}
