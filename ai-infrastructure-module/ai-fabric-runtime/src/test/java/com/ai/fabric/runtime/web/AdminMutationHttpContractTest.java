package com.ai.fabric.runtime.web;

import com.ai.fabric.runtime.admin.RuntimeAdminMutationService;
import com.ai.fabric.runtime.admin.RuntimeConnectorAdminProxyService;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.documents.DocumentKnowledgeService;
import com.ai.fabric.runtime.handover.RuntimeConversationHandoverService;
import com.ai.fabric.runtime.web.admin.ConversationHandoverAdminController;
import com.ai.fabric.runtime.web.admin.RuntimeConnectorAdminProxyController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminMutationHttpContractTest {
    @Test
    void mutationEndpointsRequireCallerKeysBeforeReachingAnyService() throws Exception {
        var auth = mock(RuntimeRequestAuthResolver.class);
        var mutations = mock(RuntimeAdminMutationService.class);
        var proxy = mock(RuntimeConnectorAdminProxyService.class);
        var documents = mock(DocumentKnowledgeService.class);
        var handover = mock(RuntimeConversationHandoverService.class);
        var mvc = MockMvcBuilders.standaloneSetup(
            new ConversationHandoverAdminController(auth, handover, mutations),
            new DocumentKnowledgeController(auth, documents, mutations),
            new RuntimeConnectorAdminProxyController(proxy, auth)).build();

        for (String path : List.of("/api/admin/conversation-handover/dispatches/dispatch-1/retry",
            "/api/admin/conversation-handover/handovers/handover-1/expire", "/api/documents/retention/cleanup",
            "/api/admin/connector/integrations/sources/source-1/reconcile",
            "/api/admin/connector/integrations/webhooks/hook-1/events/event-1/replay")) {
            mvc.perform(post(path)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(auth, mutations, proxy, documents, handover);
    }
}
