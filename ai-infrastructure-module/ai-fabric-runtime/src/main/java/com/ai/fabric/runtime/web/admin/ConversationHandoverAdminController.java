package com.ai.fabric.runtime.web.admin;

import static com.ai.fabric.runtime.handover.ConversationHandoverContracts.HandoverOperationsOverview;

import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.admin.RuntimeAdminMutationService;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.handover.RuntimeConversationHandoverService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/conversation-handover")
@ConditionalOnProperty(prefix = "loomai.conversation-handover", name = "enabled", havingValue = "true")
public class ConversationHandoverAdminController {

    private final RuntimeRequestAuthResolver authResolver;
    private final RuntimeConversationHandoverService handoverService;
    private final RuntimeAdminMutationService mutations;

    public ConversationHandoverAdminController(
        RuntimeRequestAuthResolver authResolver,
        RuntimeConversationHandoverService handoverService,
        RuntimeAdminMutationService mutations
    ) {
        this.authResolver = authResolver;
        this.handoverService = handoverService;
        this.mutations = mutations;
    }

    @GetMapping("/overview")
    public HandoverOperationsOverview overview(HttpServletRequest request) {
        authorize(
            request,
            RuntimeAdminScopeCatalog.RUNTIME_CONVERSATION_HANDOVER_READ,
            "/api/admin/conversation-handover/overview"
        );
        return handoverService.operationsOverview();
    }

    @PostMapping("/dispatches/{dispatchId}/retry")
    public HandoverOperationsOverview retryDispatch(
        @PathVariable String dispatchId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeAdminScopeCatalog.RUNTIME_CONVERSATION_HANDOVER_RECOVER,
            "/api/admin/conversation-handover/dispatches/{dispatchId}/retry"
        );
        return mutations.execute(identity, "HANDOVER_DISPATCH_RETRY", dispatchId, idempotencyKey,
            HandoverOperationsOverview.class,
            () -> handoverService.retryDispatch(identity.getAuthContext().getDeploymentId(), dispatchId));
    }

    @PostMapping("/handovers/{handoverId}/expire")
    public HandoverOperationsOverview expirePending(
        @PathVariable String handoverId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeAdminScopeCatalog.RUNTIME_CONVERSATION_HANDOVER_RECOVER,
            "/api/admin/conversation-handover/handovers/{handoverId}/expire"
        );
        return mutations.execute(identity, "HANDOVER_EXPIRE", handoverId, idempotencyKey,
            HandoverOperationsOverview.class,
            () -> handoverService.expirePendingHandover(identity.getAuthContext().getDeploymentId(), handoverId));
    }

    private RuntimeResolvedIdentity authorize(HttpServletRequest request, String scope, String surface) {
        RuntimeResolvedIdentity identity = authResolver.resolveVerifiedPrivateContext(request, surface);
        authResolver.requireScope(identity, scope, surface);
        return identity;
    }
}
