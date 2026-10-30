package com.ai.fabric.platform.backend.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupportCaseServiceTest {
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void intakeReplayKeepsExactMessageKeyIdentityAndEpisodeWithoutTranscriptStorage() {
        var f = new SupportFixture();
        var input = new CreateCase("Help with setup", "My details", null, List.of(), false);
        var created = f.service.create(input, "submit-once");
        var first = f.service.requireOwnedCase(created.id());
        assertThat(f.service.create(input, "submit-once")).isEqualTo(created);
        var resumed = f.service.requireOwnedCase(created.id());
        assertThat(resumed).isEqualTo(first);
        assertThat(first.initialMessageBody()).isEqualTo("Help with setup\n\nMy details");
        assertThat(first.customerId()).isEqualTo("internal-owner");
        assertThat(first.tenantId()).isEqualTo("internal-tenant");
        assertThat(first.principalId()).startsWith("support-").doesNotContain("alice", "customer-a");
        assertThat(f.store.find(created.id(), false).orElseThrow().requester())
            .isEqualTo(new Requester("customer-a", "alice", "https://identity.example.test/auth/v1", "subject-alice"));
        assertThat(f.jdbc.queryForObject("select count(*) from platform_support_cases", Integer.class)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("select count(*) from platform_support_case_episodes", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> f.service.create(new CreateCase("Edited", "My details", null, List.of(), false), "submit-once"))
            .hasMessageContaining("SUPPORT_SUBMISSION_KEY_REUSED");
    }

    @Test void requesterOnlyEvenForAnotherAdminInSameCustomerAndAfterEmailChange() {
        var f = new SupportFixture(); var c = f.create(false);
        f.login("bob");
        assertThat(f.service.list(0, 25)).isEmpty();
        assertThatThrownBy(() -> f.service.get(c.id())).hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
        assertThatThrownBy(() -> f.service.requireOwnedCase(c.id())).hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
        f.login("eve");
        assertThatThrownBy(() -> f.service.get(c.id())).hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
        f.directory.get("alice").setEmail("new-address@example.test");
        f.login("alice");
        assertThat(f.service.get(c.id()).id()).isEqualTo(c.id());
    }

    @Test void inactiveMembershipRevokedOrReboundSupabaseIdentityAndApiKeysCannotReadCases() {
        var f = new SupportFixture(); var c = f.create(false);
        f.identityLinks.get("alice").setRevokedAt(Instant.now());
        assertThatThrownBy(() -> f.service.get(c.id())).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        f.identityLinks.get("alice").setRevokedAt(null);
        f.identityLinks.get("alice").setSubject("replacement-subject");
        assertThatThrownBy(() -> f.service.get(c.id())).hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
        f.identityLinks.get("alice").setSubject("subject-alice");
        f.directory.get("alice").setCustomerId("customer-b");
        assertThatThrownBy(() -> f.service.get(c.id())).hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
        f.directory.get("alice").setCustomerId("customer-a");
        f.directory.get("alice").setStatus("DISABLED");
        assertThatThrownBy(() -> f.service.get(c.id())).hasMessageContaining("active customer");
        var p = new com.ai.fabric.platform.backend.security.PlatformPrincipal("admin", com.ai.fabric.platform.backend.security.PlatformRole.PLATFORM_ADMIN, "admin", "API_KEY");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, List.of()));
        assertThatThrownBy(() -> f.service.get(c.id())).hasMessageContaining("SUPPORT_ACCESS_DENIED");
    }

    @Test void contextChangeRequiresFreshConsentAndDoesNotRewriteIntake() {
        var f = new SupportFixture(); var c = f.create(true);
        var owned = SupportFixture.deployment("customer-dep", "customer-a", "customer-tenant");
        when(f.deployments.findById(owned.getId())).thenReturn(Optional.of(owned));
        var before = f.service.requireOwnedCase(c.id());
        var updated = f.service.update(c.id(), new UpdateCase("deployment", List.of(new ResourceRef(ResourceType.DEPLOYMENT, owned.getId())), c.revision()));
        assertThat(updated.consent().granted()).isFalse();
        assertThat(updated.consent().version()).isEqualTo(2);
        assertThat(updated.consent().revokedAt()).isNotNull();
        assertThat(f.service.requireOwnedCase(c.id()).initialMessageBody()).isEqualTo(before.initialMessageBody());
        assertThatThrownBy(() -> f.service.consent(c.id(), new ConsentChange(true, 1))).hasMessageContaining("CONSENT_VERSION_CONFLICT");
        var consented = f.service.consent(c.id(), new ConsentChange(true, 2));
        assertThat(consented.consent().granted()).isTrue();
        assertThatThrownBy(() -> f.service.update(c.id(), new UpdateCase(null, List.of(), 0))).hasMessageContaining("REVISION_CONFLICT");
    }

    @Test void foreignSelectedResourcesAreRejectedBeforeIntakeIsPersisted() {
        var f = new SupportFixture();
        when(f.deployments.findById("foreign")).thenReturn(Optional.of(SupportFixture.deployment("foreign", "customer-b", "foreign-tenant")));
        assertThatThrownBy(() -> f.create(true, new ResourceRef(ResourceType.DEPLOYMENT, "foreign"))).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        assertThat(f.jdbc.queryForObject("select count(*) from platform_support_cases", Integer.class)).isZero();
    }

    @Test void resolveCancelAndReopenRemainSeparateFromConversationStateAndNeverReplayIntake() {
        var f = new SupportFixture(); var c = f.create(true);
        var first = f.service.requireOwnedCase(c.id());
        var resolved = f.service.resolve(c.id(), c.revision());
        assertThat(resolved.status()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(resolved.consent().granted()).isFalse();
        assertThat(resolved.episodes().getFirst().terminal()).isFalse();
        var reopened = f.service.reopen(c.id(), resolved.revision());
        assertThat(reopened.episodes()).hasSize(1);
        assertThat(reopened.consent().granted()).isFalse();
        f.service.observeConversationTerminal(c.id(), first.conversationId());
        var cancelled = f.service.cancel(c.id(), reopened.revision());
        assertThat(cancelled.status()).isEqualTo(CaseStatus.CANCELLED);
        var second = f.service.reopen(c.id(), cancelled.revision());
        assertThat(second.episodes()).hasSize(2);
        var access = f.service.requireOwnedCase(c.id());
        assertThat(access.principalId()).isEqualTo(first.principalId());
        assertThat(access.conversationId()).isNotEqualTo(first.conversationId());
        assertThat(access.initialMessageKey()).isEqualTo(first.initialMessageKey());
        assertThatThrownBy(() -> f.service.initialMessageForDelivery(c.id())).hasMessageContaining("INTAKE_EPISODE_CLOSED");
    }

    @Test void uncertainDeliveryKeepsStableKeyAndCompletedObservationCannotRegress() {
        var f = new SupportFixture(); var c = f.create(false);
        var message = f.service.initialMessageForDelivery(c.id());
        f.service.observeInitialDelivery(c.id(), message.episodeId(), DeliveryStatus.UNKNOWN);
        assertThat(f.service.initialMessageForDelivery(c.id()).idempotencyKey()).isEqualTo(message.idempotencyKey());
        f.service.observeInitialDelivery(c.id(), message.episodeId(), DeliveryStatus.USER_MESSAGE_PERSISTED);
        f.service.observeInitialDelivery(c.id(), message.episodeId(), DeliveryStatus.UNKNOWN);
        assertThat(f.service.get(c.id()).initialMessageDelivery()).isEqualTo(DeliveryStatus.USER_MESSAGE_PERSISTED);
        f.service.observeInitialDelivery(c.id(), message.episodeId(), DeliveryStatus.COMPLETED);
        f.service.observeInitialDelivery(c.id(), message.episodeId(), DeliveryStatus.FAILED);
        assertThat(f.service.get(c.id()).initialMessageDelivery()).isEqualTo(DeliveryStatus.COMPLETED);
        assertThatThrownBy(() -> f.service.observeInitialDelivery(c.id(), "foreign-episode", DeliveryStatus.COMPLETED))
            .hasMessageContaining("INVALID_DELIVERY_OBSERVATION");
    }

    @Test void closedCanonicalMessageResponseAllowsExplicitReopenWithoutASeparateHistoryRead() {
        var f = new SupportFixture(); var c = f.create(false);
        var first = f.service.requireOwnedCase(c.id());
        var access = mock(com.ai.fabric.platform.backend.support.runtime.SupportRuntimeAccessService.class);
        var runtime = mock(com.ai.fabric.platform.backend.support.runtime.SupportRuntimeClient.class);
        var destination = new com.ai.fabric.platform.backend.support.runtime.SupportRuntimeAccessService.Access(
            first, null, null, f.json.createObjectNode(), java.util.Map.of());
        when(access.resolve(c.id())).thenReturn(destination);
        var response = f.json.createObjectNode().put("type", "CONVERSATION_CONTROLLED");
        response.putObject("metadata").putObject("conversationControl")
            .put("conversationId", first.conversationId()).put("controlState", "CLOSED");
        when(runtime.exchange(any(), any(), any(), any(), any()))
            .thenReturn(new com.ai.fabric.platform.backend.support.runtime.SupportRuntimeClient.Result(409, response));
        var workspace = new com.ai.fabric.platform.backend.support.runtime.SupportWorkspaceService(
            f.service, f.requesters, access, runtime, f.json);

        var result = workspace.exchange(c.id(), "POST", "chat/me/conversations/" + first.conversationId() + "/messages",
            f.json.createObjectNode().put("query", "Can we continue?"), "after-close", 0);

        assertThat(result.status()).isEqualTo(409);
        assertThat(f.service.get(c.id()).episodes().getFirst().terminal()).isTrue();
        var reopened = f.service.reopen(c.id(), f.service.get(c.id()).revision());
        assertThat(reopened.episodes()).hasSize(2);
        assertThat(reopened.episodes().getLast().conversationId()).isNotEqualTo(first.conversationId());
        assertThatThrownBy(() -> f.service.initialMessageForDelivery(c.id())).hasMessageContaining("INTAKE_EPISODE_CLOSED");
        verify(runtime, times(1)).exchange(any(), any(), any(), any(), any());

        var second = f.service.requireOwnedCase(c.id());
        var reopenedDestination = new com.ai.fabric.platform.backend.support.runtime.SupportRuntimeAccessService.Access(
            second, null, null, f.json.createObjectNode(), java.util.Map.of());
        when(access.resolve(c.id())).thenReturn(reopenedDestination);
        String path = "/api/chat/me/conversations/" + second.conversationId();
        when(runtime.exchange(reopenedDestination, "GET", path + "/control", null, null))
            .thenReturn(new com.ai.fabric.platform.backend.support.runtime.SupportRuntimeClient.Result(200,
                f.json.createObjectNode().put("conversationId", second.conversationId()).put("controlState", "AI_ACTIVE")));
        var empty = f.json.createObjectNode().put("id", second.conversationId());
        empty.putArray("participantMessages");
        when(runtime.exchange(reopenedDestination, "GET", path, null, null))
            .thenReturn(new com.ai.fabric.platform.backend.support.runtime.SupportRuntimeClient.Result(200,
                empty));
        workspace.exchange(c.id(), "GET", path.substring("/api/".length()), null, null, 0);
        var ordered = inOrder(runtime);
        ordered.verify(runtime).exchange(reopenedDestination, "GET", path + "/control", null, null);
        ordered.verify(runtime).exchange(reopenedDestination, "GET", path, null, null);
        verify(runtime, times(1)).exchange(any(), eq("POST"), any(), any(), any());
    }

    @Test void fixedSupportBindingCannotSilentlyFollowAnAssignmentOrConfigurationChange() {
        var f = new SupportFixture(); var c = f.create(false);
        var original = f.service.requireOwnedCase(c.id());
        f.env.setProperty("platform.support.target-profile-id", "other-target");
        when(f.targets.existsById("other-target")).thenReturn(true);
        assertThatThrownBy(() -> f.service.requireOwnedCase(c.id())).hasMessageContaining("EPISODE_BINDING_CHANGED");
        assertThat(f.store.episodes(c.id()).getFirst().binding().targetProfileId()).isEqualTo(original.targetProfileId());
    }

    @Test void explicitReopenOfAnOpenCaseWithTerminalConversationCreatesEpisodeWithoutResolvingTheCaseFirst() {
        var f = new SupportFixture(); var c = f.create(true); var owned = f.service.requireOwnedCase(c.id());
        f.service.observeConversationTerminal(c.id(), owned.conversationId());
        assertThat(f.service.get(c.id()).status()).isEqualTo(CaseStatus.OPEN);
        var reopened = f.service.reopen(c.id(), c.revision());
        assertThat(reopened.status()).isEqualTo(CaseStatus.OPEN);
        assertThat(reopened.episodes()).hasSize(2);
        assertThat(reopened.consent().granted()).isFalse();
        assertThatThrownBy(() -> f.service.initialMessageForDelivery(c.id())).hasMessageContaining("INTAKE_EPISODE_CLOSED");
    }

    @Test void localSessionCapturesAvailableIdentityWithoutEmailOwnershipAndCanLaterLinkSupabase() {
        var f = new SupportFixture(); f.identityLinks.remove("alice");
        var p = new com.ai.fabric.platform.backend.security.PlatformPrincipal("alice@example.test",
            com.ai.fabric.platform.backend.security.PlatformRole.CUSTOMER_ADMIN, "Alice", "SESSION", "alice");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, List.of()));
        var c = f.create(false);
        var requester = f.store.find(c.id(), false).orElseThrow().requester();
        assertThat(requester.issuer()).isNull(); assertThat(requester.subject()).isNull();
        f.addLink("alice"); f.login("alice");
        assertThat(f.service.get(c.id()).id()).isEqualTo(c.id());
        assertThat(f.store.find(c.id(), false).orElseThrow().requester()).isEqualTo(requester);
    }

    @Test void configuredButUnavailableRuntimeDoesNotBlockDurableIntakeOrClaimMessageDelivery() {
        var f = new SupportFixture();
        var runtime = f.deployments.findById("support-runtime").orElseThrow();
        runtime.setStatus("FAILED"); runtime.setRuntimeBaseUrl(null);
        var saved = f.create(false);
        assertThat(saved.status()).isEqualTo(CaseStatus.OPEN);
        assertThat(saved.initialMessageDelivery()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(saved.deliveryObservedAt()).isNull();
        assertThat(f.store.find(saved.id(), false)).isPresent();
        assertThat(saved.episodes()).hasSize(1);
        verifyNoInteractions(f.readiness, f.secrets, f.releases, f.plans, f.runs);
    }
}
