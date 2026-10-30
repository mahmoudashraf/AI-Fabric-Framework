package com.ai.fabric.platform.backend.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupportDiagnosticServiceTest {
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void authenticatedConnectorResolvesOnlyPersistedPrincipalAndConversationWithoutCustomerParameters() {
        var f = new SupportFixture(); var c = f.create(true);
        var owned = f.service.requireOwnedCase(c.id()); var headers = f.headers(owned);
        SecurityContextHolder.clearContext();
        var result = f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, headers);
        assertThat(result.supportCase().reference()).isEqualTo(c.reference());
        assertThat(result.observedAt()).isNotNull();
        assertThat(result.availability()).isEqualTo(Availability.CURRENT);
        assertThat(f.diagnostics.read("support_get_deployment_status", headers).availability()).isEqualTo(Availability.NOT_APPLICABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = { "X-LOOMAI-SUPPORT-SERVICE-KEY", "X-AIFABRIC-AUTH-SUBJECT-ID",
        "X-AIFABRIC-AUTH-SUBJECT-TYPE", "X-AIFABRIC-AUTH-MODE", "X-AIFABRIC-AUTH-CALLER-TYPE",
        "X-AIFABRIC-AUTH-DEPLOYMENT-ID", "X-AIFABRIC-AUTH-CUSTOMER-ID", "X-AIFABRIC-AUTH-TENANT-ID",
        "X-AIFABRIC-AUTH-ISSUER", "X-AIFABRIC-AUTH-AUDIENCES", "X-AIFABRIC-AUTH-SCOPES", "X-AIFABRIC-AUTH-EXPIRES-AT" })
    void rejectsMissingForgedAndDuplicateHeadersEvenWithValidCustomerSession(String header) {
        var f = new SupportFixture(); var c = f.create(true); var owned = f.service.requireOwnedCase(c.id());
        var missing = f.headers(owned); missing.remove(header);
        assertThatThrownBy(() -> f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, missing)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        var forged = f.headers(owned); forged.set(header, "forged-value");
        assertThatThrownBy(() -> f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, forged)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        var duplicate = f.headers(owned); duplicate.add(header, duplicate.getFirst(header));
        assertThatThrownBy(() -> f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, duplicate)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
    }

    @Test void differentCasePrincipalCannotReadAnotherConversationEvenForTheSameRequester() {
        var f = new SupportFixture(); var first = f.service.requireOwnedCase(f.create(true).id());
        var second = f.service.requireOwnedCase(f.create(true).id());
        var headers = f.headers(first); headers.set("X-AIFABRIC-CONVERSATION-ID", second.conversationId());
        assertThatThrownBy(() -> f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
    }

    @Test void noConsentRevocationClosureAndMembershipRevocationStopFutureReads() {
        var f = new SupportFixture(); var c = f.create(false); var headers = f.headers(f.service.requireOwnedCase(c.id()));
        assertThatThrownBy(() -> f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        var granted = f.service.consent(c.id(), new ConsentChange(true, c.consent().version()));
        assertThat(f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, headers).availability()).isEqualTo(Availability.CURRENT);
        f.identityLinks.get("alice").setRevokedAt(Instant.now());
        assertThatThrownBy(() -> f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        f.identityLinks.get("alice").setRevokedAt(null);
        var revoked = f.service.consent(c.id(), new ConsentChange(false, granted.consent().version()));
        assertThatThrownBy(() -> f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        var allowedAgain = f.service.consent(c.id(), new ConsentChange(true, revoked.consent().version()));
        f.service.resolve(c.id(), allowedAgain.revision());
        assertThatThrownBy(() -> f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
    }

    @Test void expiresAndUnconfiguredDedicatedSecretDoNotUseGlobalCredentials() {
        var f = new SupportFixture(); var c = f.create(true); var headers = f.headers(f.service.requireOwnedCase(c.id()));
        headers.set("X-AIFABRIC-AUTH-EXPIRES-AT", Instant.now().minusSeconds(1).toString());
        assertThatThrownBy(() -> f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        when(f.secrets.resolveSecret("PLATFORM_MANAGED_SUPPORT_DIAGNOSTICS")).thenReturn(null);
        assertThatThrownBy(() -> f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, headers)).hasMessageContaining("SUPPORT_NOT_CONFIGURED");
        verify(f.secrets, never()).resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY");
    }

    @Test void ownershipChangesAreRecheckedEvenForCaseSummaryAndCannotExpandResourceScope() {
        var f = new SupportFixture(); var d = SupportFixture.deployment("selected", "customer-a", "tenant-a");
        when(f.deployments.findById(d.getId())).thenReturn(Optional.of(d));
        var c = f.create(true, new ResourceRef(ResourceType.DEPLOYMENT, d.getId()));
        var headers = f.headers(f.service.requireOwnedCase(c.id()));
        d.setCustomerId("customer-b");
        assertThatThrownBy(() -> f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        assertThatThrownBy(() -> f.diagnostics.read("support_get_deployment_status", headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
    }

    @Test void oldOrTerminalEpisodeCannotRunDiagnosticsAfterExplicitReopening() {
        var f = new SupportFixture(); var c = f.create(true); var owned = f.service.requireOwnedCase(c.id());
        var oldHeaders = f.headers(owned);
        f.service.observeConversationTerminal(c.id(), owned.conversationId());
        var closed = f.service.cancel(c.id(), c.revision());
        var reopened = f.service.reopen(c.id(), closed.revision());
        f.service.consent(c.id(), new ConsentChange(true, reopened.consent().version()));
        assertThatThrownBy(() -> f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, oldHeaders)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        assertThat(f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, f.headers(f.service.requireOwnedCase(c.id()))).availability())
            .isEqualTo(Availability.CURRENT);
    }

    @Test void conversationDisabledChildReadUsesVerifiedCasePrincipalWithoutInventingChildConversation() {
        var f = new SupportFixture(); var c = f.create(true);
        var owned = f.service.requireOwnedCase(c.id()); var headers = f.headers(owned);
        headers.remove("X-AIFABRIC-CONVERSATION-ID");
        SecurityContextHolder.clearContext();
        assertThat(f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, headers).supportCase().reference()).isEqualTo(c.reference());
        assertThat(f.jdbc.queryForObject("select count(*) from platform_support_case_episodes", Integer.class)).isEqualTo(1);
        headers.set("X-AIFABRIC-CONVERSATION-ID", "foreign-conversation");
        assertThatThrownBy(() -> f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        headers.set("X-AIFABRIC-CONVERSATION-ID", "");
        assertThatThrownBy(() -> f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        headers.set("X-AIFABRIC-CONVERSATION-ID", owned.conversationId());
        headers.add("X-AIFABRIC-CONVERSATION-ID", owned.conversationId());
        assertThatThrownBy(() -> f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
    }

    @Test void conversationDisabledReadsStillRequireCurrentConsentAndActiveEpisode() {
        var f = new SupportFixture(); var c = f.create(false);
        var owned = f.service.requireOwnedCase(c.id()); var headers = f.headers(owned); headers.remove("X-AIFABRIC-CONVERSATION-ID");
        assertThatThrownBy(() -> f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        f.service.consent(c.id(), new ConsentChange(true, c.consent().version()));
        f.service.observeConversationTerminal(c.id(), owned.conversationId());
        assertThatThrownBy(() -> f.diagnostics.read(SupportDiagnosticService.CASE_SUMMARY, headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
    }
}
