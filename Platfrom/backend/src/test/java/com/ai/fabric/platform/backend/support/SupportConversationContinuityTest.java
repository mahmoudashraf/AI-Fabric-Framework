package com.ai.fabric.platform.backend.support;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

class SupportConversationContinuityTest {
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void resumesSameConversationAcrossMountsAndExplicitNewChatPreservesHistory() {
        var f = new SupportFixture();
        var first = f.conversations.resume(null);
        assertThat(f.conversations.resume(null)).isEqualTo(first);
        var second = f.conversations.create();
        assertThat(second.conversationId()).isNotEqualTo(first.conversationId());
        assertThat(f.conversations.resume(null)).isEqualTo(second);
        assertThat(f.conversations.resume(first.conversationId())).isEqualTo(first);
        assertThat(f.conversations.resume(null)).isEqualTo(first);
        assertThat(f.conversations.history(null, 20).items()).hasSize(2);
    }

    @Test void historySelectionAndConsentAreIsolatedPerUserEvenWithinOneCustomer() {
        var f = new SupportFixture();
        var alice = f.conversations.create();
        f.login("bob");
        assertThat(f.conversations.history(null, 20).items()).isEmpty();
        assertThatThrownBy(() -> f.conversations.resume(alice.conversationId())).hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
        assertThatThrownBy(() -> f.conversations.context(alice.conversationId())).hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
        assertThat(f.conversations.resume(null).conversationId()).isNotEqualTo(alice.conversationId());
        f.login("eve");
        assertThat(f.conversations.history(null, 20).items()).isEmpty();
        assertThatThrownBy(() -> f.conversations.history(alice.conversationId(), 20)).hasMessageContaining("SUPPORT_CASE_NOT_FOUND");
    }

    @Test void historyPaginatesWithoutDuplicatingRowsOrReadingAnotherOwnersCursor() {
        var f = new SupportFixture();
        for (int i=0;i<5;i++) f.conversations.create();
        var first = f.conversations.history(null, 2);
        var second = f.conversations.history(first.nextCursor(), 2);
        var third = f.conversations.history(second.nextCursor(), 2);
        assertThat(first.items()).hasSize(2).doesNotContainAnyElementsOf(second.items());
        assertThat(second.items()).hasSize(2).doesNotContainAnyElementsOf(third.items());
        assertThat(third.items()).hasSize(1);
        assertThat(third.nextCursor()).isNull();
        assertThatThrownBy(() -> f.conversations.history(null, 51)).hasMessageContaining("SUPPORT_INVALID_PAGE");
    }

    @Test void adoptedConversationUsesTicketConsentAndCannotBypassTicketClosureThroughHistory() {
        var f = new SupportFixture();
        var chat = f.conversations.create();
        var ticket = f.service.createForConversation(new SupportTicketService.Create("Help", "Details", "setup", chat.conversationId()), "adopt", chat, java.util.Map.of());
        assertThat(f.conversations.history(null, 20).items()).isEmpty();
        assertThat(f.conversations.resume(null).conversationId()).isNotEqualTo(chat.conversationId());
        assertThatThrownBy(() -> f.conversations.resume(chat.conversationId())).hasMessageContaining("SUPPORT_USE_REQUEST_CONVERSATION");
        assertThatThrownBy(() -> f.conversations.consent(chat.conversationId(), new ConsentChange(true,0))).hasMessageContaining("SUPPORT_USE_REQUEST_CONVERSATION");
        assertThatThrownBy(() -> f.diagnostics.read("support_get_deployment_status", headers(f,chat))).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        assertThat(ticket.path("id").asText()).isNotBlank();
    }

    @Test void generalDiagnosticsExplainSelectionAndConsentWithoutInventingAStatus() {
        var f = new SupportFixture();
        var chat = f.conversations.create();
        var headers = headers(f,chat);
        var missing = f.diagnostics.read("support_get_deployment_status", headers);
        assertThat(missing.requirement().code()).isEqualTo("SELECT_RESOURCE");
        assertThat(missing.resources()).isEmpty();
        var d = SupportFixture.deployment("my-brain", "customer-a", "tenant-a");
        when(f.deployments.findById(d.getId())).thenReturn(Optional.of(d));
        var selected = f.conversations.update(chat.conversationId(), new SupportTicketService.ContextInput(List.of(new ResourceRef(ResourceType.DEPLOYMENT,d.getId())),0));
        assertThat(f.diagnostics.read("support_get_deployment_status",headers).requirement().code()).isEqualTo("DIAGNOSTIC_CONSENT_REQUIRED");
        f.conversations.consent(chat.conversationId(), new ConsentChange(true, selected.consent().version()));
        var result = f.diagnostics.read("support_get_deployment_status",headers);
        assertThat(result.requirement()).isNull();
        assertThat(result.resources()).hasSize(1);
        assertThat(result.resources().getFirst().facts()).containsEntry("deploymentStatus","ACTIVE").containsEntry("liveHealthProbed",false);
        assertThat(f.jdbc.queryForObject("select count(*) from platform_support_integration_context",Integer.class)).isZero();
    }

    @Test void resourceChangeRevokesConsentAndStaleRevisionsCannotOverwriteIt() {
        var f = new SupportFixture();
        var chat = f.conversations.create();
        for (String id : List.of("first","second")) when(f.deployments.findById(id)).thenReturn(Optional.of(SupportFixture.deployment(id,"customer-a","tenant-a")));
        var first = f.conversations.update(chat.conversationId(), new SupportTicketService.ContextInput(List.of(new ResourceRef(ResourceType.DEPLOYMENT,"first")),0));
        var allowed = f.conversations.consent(chat.conversationId(),new ConsentChange(true,first.consent().version()));
        var changed = f.conversations.update(chat.conversationId(),new SupportTicketService.ContextInput(List.of(new ResourceRef(ResourceType.DEPLOYMENT,"second")),allowed.revision()));
        assertThat(changed.consent().granted()).isFalse();
        assertThatThrownBy(() -> f.conversations.consent(chat.conversationId(),new ConsentChange(true,allowed.consent().version()))).hasMessageContaining("SUPPORT_CONSENT_VERSION_CONFLICT");
        assertThatThrownBy(() -> f.conversations.update(chat.conversationId(),new SupportTicketService.ContextInput(List.of(),0))).hasMessageContaining("SUPPORT_CONTEXT_REVISION_CONFLICT");
        assertThat(f.diagnostics.read("support_get_deployment_status",headers(f,chat)).requirement().code()).isEqualTo("DIAGNOSTIC_CONSENT_REQUIRED");
    }

    @Test void rechecksOwnershipAndMembershipAndPreservesChildConversationBoundary() {
        var f = new SupportFixture();
        var chat = f.conversations.create();
        var d = SupportFixture.deployment("selected","customer-a","tenant-a");
        when(f.deployments.findById(d.getId())).thenReturn(Optional.of(d));
        var c = f.conversations.update(chat.conversationId(),new SupportTicketService.ContextInput(List.of(new ResourceRef(ResourceType.DEPLOYMENT,d.getId())),0));
        f.conversations.consent(chat.conversationId(),new ConsentChange(true,c.consent().version()));
        var headers = headers(f,chat);
        headers.remove("X-AIFABRIC-CONVERSATION-ID");
        SecurityContextHolder.clearContext();
        assertThat(f.diagnostics.read("support_get_deployment_status",headers).resources()).hasSize(1);
        headers.set("X-AIFABRIC-CONVERSATION-ID","another-chat");
        assertThatThrownBy(() -> f.diagnostics.read("support_get_deployment_status",headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        headers.remove("X-AIFABRIC-CONVERSATION-ID");
        d.setCustomerId("customer-b");
        assertThatThrownBy(() -> f.diagnostics.read("support_get_deployment_status",headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
        d.setCustomerId("customer-a");
        f.identityLinks.get("alice").setRevokedAt(Instant.now());
        assertThatThrownBy(() -> f.diagnostics.read("support_get_deployment_status",headers)).hasMessageContaining("SUPPORT_ACCESS_DENIED");
    }

    private org.springframework.http.HttpHeaders headers(SupportFixture f, SupportConversationScopeService.Scope scope) {
        return f.headers(new OwnedCase(null,null,"support-runtime","staging-target","internal-owner","internal-tenant",
            scope.runtimePrincipal(),scope.conversationId(),scope.conversationId(),1,false,null,null,DeliveryStatus.COMPLETED,CaseStatus.OPEN,"loomai-support","support-runtime"));
    }
}
