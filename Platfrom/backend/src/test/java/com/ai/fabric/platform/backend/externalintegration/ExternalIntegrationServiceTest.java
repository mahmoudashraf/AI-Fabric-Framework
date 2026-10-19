package com.ai.fabric.platform.backend.externalintegration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Optional;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExternalIntegrationServiceTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void explicitReviewAndCustomerCredentialBindingAreScopedImmutableRedactedAndIdempotent() throws Exception {
        var f = new ExternalIntegrationFixture();
        var approval = f.approve();
        var request = f.bindRequest(approval.approvalId());
        var result = f.service.bind("offer-generic", f.deployment.getId(), request);
        assertThat(f.service.bind("offer-generic", f.deployment.getId(), request)).isEqualTo(result);
        assertThat(result.status()).isEqualTo("CONFIGURED_LOCAL_CREDENTIALS");
        assertThat(f.bindings).hasSize(1); assertThat(f.commandRows).hasSize(1); assertThat(f.secretRows).hasSize(1);
        verify(f.secrets).flush();
        verify(f.vault, times(1)).upsertManagedSecret(anyString(), eq("synthetic-customer-key"), anyMap());
        verify(f.vault, never()).resolveSecret("ZILLIZ_CLOUD_API_KEY");
        assertThat(f.dnsCalls).hasValue(0);
        String safe = f.codec.write(result) + f.codec.write(f.bindings.values().iterator().next().value())
            + f.codec.write(f.approvals.values().iterator().next().value()) + f.codec.write(f.commandRows.values().iterator().next().getResultJson())
            + request + f.mapper.writeValueAsString(request);
        assertThat(safe).doesNotContain("synthetic-customer-key", "credentials\":");
        assertThat(f.bindings.values().iterator().next().value().secretRevisions()).containsValue("2026-10-19T01:00:00.123456Z");
    }

    @Test void changedSecretReplayNeverRotatesOrOverwritesAReleasedVersion() throws Exception {
        var f = new ExternalIntegrationFixture(); var result = f.bind();
        var original = f.bindings.values().iterator().next().value();
        var request = f.bindRequest(result.approvalId());
        var changed = new BindRequest(request.versionId(), request.targetProfileId(), request.bindingSlotId(), request.selectedProfileId(),
            request.approvalId(), request.expectedEntitlementRevision(), request.operationKey(), f.mapper.createObjectNode().put("apiKey", "changed-synthetic-key"));
        assertThatThrownBy(() -> f.service.bind("offer-generic", f.deployment.getId(), changed)).hasMessageContaining("OPERATION_KEY_CONFLICT");
        var newOperation = new BindRequest(request.versionId(), request.targetProfileId(), request.bindingSlotId(), request.selectedProfileId(),
            request.approvalId(), request.expectedEntitlementRevision(), "new-bind-operation", request.credentials());
        assertThatThrownBy(() -> f.service.bind("offer-generic", f.deployment.getId(), newOperation)).hasMessageContaining("IMMUTABLE_BINDING_EXISTS");
        assertThat(f.bindings.values().iterator().next().value()).isEqualTo(original);
        verify(f.vault, times(1)).upsertManagedSecret(anyString(), anyString(), anyMap());
    }

    @Test void customerCannotSubmitUrlsAccountConfigCredentialReferencesOrNontextualCredentials() throws Exception {
        for (String json : new String[]{"null", "[]", "true", "{\"apiKey\":null}", "{\"apiKey\":123}", "{\"apiKey\":false}",
            "{\"apiKey\":{\"secretRef\":\"ZILLIZ_CLOUD_API_KEY\"}}", "{\"apiKey\":[\"raw\"]}",
            "{\"apiKey\":\"${secret:ZILLIZ_CLOUD_API_KEY}\"}", "{\"apiKey\":\"safe\",\"endpointUrl\":\"https://evil.example.com\"}"}) {
            var f = new ExternalIntegrationFixture(); var request = f.bindRequest(f.approve().approvalId());
            JsonNode credentials = f.mapper.readTree(json);
            var invalid = new BindRequest(request.versionId(), request.targetProfileId(), request.bindingSlotId(), request.selectedProfileId(), request.approvalId(),
                3L, request.operationKey(), credentials);
            assertThatThrownBy(() -> f.service.bind("offer-generic", f.deployment.getId(), invalid));
            verify(f.vault, never()).upsertManagedSecret(anyString(), anyString(), anyMap());
            assertThat(f.bindings).isEmpty();
        }
    }

    @Test void staleVersionTargetOfferOrOwnerReviewBlocksBeforeAnyVaultWrite() throws Exception {
        for (String fault : new String[]{"version", "target", "customer", "profile", "signature", "revision"}) {
            var f = new ExternalIntegrationFixture(); String approvalId = f.approve().approvalId();
            var request = f.bindRequest(approvalId);
            switch (fault) {
                case "version" -> f.version.setConfigHash("changed-config-hash");
                case "target" -> f.target.setEnvironmentName("other-environment");
                case "customer" -> f.deployment.setCustomerId("other-customer");
                case "profile" -> {
                    var original = f.approvals.get(approvalId).value();
                    var scope = original.scope(); var wrong = new Scope(scope.offerId(), scope.customerId(), scope.tenantId(), scope.deploymentId(),
                        scope.versionId(), scope.targetProfileId(), scope.bindingSlotId(), "other-profile");
                    var changed = new Approval(original.id(), wrong, original.offerFingerprint(), original.versionConfigHash(), original.targetFingerprint(),
                        original.profile(), original.config(), original.accountScope(), original.reviewReference(), original.reviewedBy(), original.operationKey(), original.requestFingerprint());
                    f.approvals.put(approvalId, new ExternalIntegrationStore.Row<>(changed, f.integrity.seal(changed), "APPROVED", 0));
                }
                case "signature" -> f.approvals.put(approvalId, new ExternalIntegrationStore.Row<>(f.approvals.get(approvalId).value(), "0".repeat(64), "APPROVED", 0));
                case "revision" -> f.grant.setRevision(4);
            }
            assertThatThrownBy(() -> f.service.bind("offer-generic", f.deployment.getId(), request));
            verify(f.vault, never()).upsertManagedSecret(anyString(), anyString(), anyMap());
        }
    }

    @Test void revokeRetainsSecretsAndExternalDataButBlocksFutureResolutionAndReplaysExactly() throws Exception {
        var f = new ExternalIntegrationFixture(); var binding = f.bind(); var request = f.revokeRequest(binding.id());
        var result = f.service.revoke("offer-generic", f.deployment.getId(), request);
        assertThat(f.service.revoke("offer-generic", f.deployment.getId(), request)).isEqualTo(result);
        assertThat(result.status()).isEqualTo("REVOKED_FUTURE_RESOLUTION_EXTERNAL_RETAINED");
        assertThat(f.secretRows).hasSize(1); assertThat(f.approvals).hasSize(1); assertThat(f.bindings).hasSize(1);
        assertThatThrownBy(() -> f.resolver.resolveLocal(f.deployment.getId(), f.version.getId(), f.target.getId()))
            .hasMessageContaining("BINDING_STALE_OR_FOREIGN");
        verify(f.vault, never()).clearManagedSecret(anyString(), anyMap());
    }

    @Test void staffReviewHasStableReplayAndNoImplicitBindingOrCredentialSideEffects() throws Exception {
        var f = new ExternalIntegrationFixture(); f.staff();
        var first = f.service.approve("offer-generic", f.deployment.getId(), f.approvalRequest());
        assertThat(f.service.approve("offer-generic", f.deployment.getId(), f.approvalRequest())).isEqualTo(first);
        assertThat(f.approvals).hasSize(1); assertThat(f.bindings).isEmpty(); assertThat(f.secretRows).isEmpty();
        var request = f.approvalRequest();
        var changed = new ApprovalRequest(request.versionId(), request.targetProfileId(), request.bindingSlotId(), request.selectedProfileId(),
            f.mapper.createObjectNode().put("endpointUrl", "https://provider.example.com/different-path"), request.accountScope(), request.reviewReference(), request.operationKey());
        assertThatThrownBy(() -> f.service.approve("offer-generic", f.deployment.getId(), changed)).hasMessageContaining("OPERATION_KEY_CONFLICT");
        verify(f.vault, never()).upsertManagedSecret(anyString(), anyString(), anyMap());
    }
}
