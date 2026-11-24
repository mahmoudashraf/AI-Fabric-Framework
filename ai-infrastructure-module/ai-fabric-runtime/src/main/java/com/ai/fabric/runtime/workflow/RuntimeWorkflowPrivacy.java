package com.ai.fabric.runtime.workflow;

import ai.fabric.execution.state.DurableExecutionRepository;
import ai.fabric.execution.state.DurableExecutionSecurity;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Authoring content erasure retains closed authority tombstones and separate financial receipts. */
@Component
public class RuntimeWorkflowPrivacy {
    private final AuthoringPlatformClient platform;
    private final ObjectProvider<DurableExecutionRepository> repositories;
    private final ObjectProvider<DurableExecutionSecurity> securities;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final boolean protectedHistory;
    public RuntimeWorkflowPrivacy(AuthoringPlatformClient platform, ObjectProvider<DurableExecutionRepository> repositories,
        ObjectProvider<DurableExecutionSecurity> securities, DataSource source, PlatformTransactionManager transactions,
        @org.springframework.beans.factory.annotation.Value("${loomai.configuration-assistant.protect-history:false}") boolean protectedHistory) {
        this.platform = platform; this.repositories = repositories; this.securities = securities;
        this.jdbc = new JdbcTemplate(source); this.transactions = new TransactionTemplate(transactions);
        this.protectedHistory = protectedHistory;
    }
    public void erase(RuntimeResolvedIdentity identity, String conversationId) {
        var auth = identity.getAuthContext();
        if (!protectedHistory || auth == null || !identity.ownerId().startsWith("authoring:")
            || !"TRUSTED_BACKEND".equals(String.valueOf(auth.getCallerType()))
            || !"PRIVATE_RUNTIME_BACKEND_MEDIATED".equals(String.valueOf(auth.getAuthMode()))
            || !auth.getGrantedScopes().containsAll(List.of("chat:conversations", "configuration:author")))
            throw new ResponseStatusException(FORBIDDEN, "AUTHORING_ERASURE_DENIED");
        Object[] scope = { auth.getTenantId(), auth.getDeploymentId(), conversationId, identity.ownerId() };
        transactions.executeWithoutResult(ignored -> {
            var states = jdbc.queryForList("SELECT control_state FROM ai_conversation_control WHERE tenant_id=? AND deployment_id=? AND conversation_id=? AND owner_id=? FOR UPDATE", String.class, scope);
            if (states.size() != 1 || !"CLOSED".equals(states.getFirst())) throw new ResponseStatusException(CONFLICT, "Close the owned authoring conversation before erasing its content.");
            var repository = repositories.getObject(); var security = securities.getObject();
            var ids = jdbc.queryForList("SELECT idempotency_key FROM ai_conversation_ai_interaction WHERE tenant_id=? AND deployment_id=? AND conversation_id=? AND owner_id=?", String.class, scope);
            for (String id : ids) {
                var record = repository.findById(id).orElse(null);
                if (record == null) continue;
                var reference = security.unprotect(record.protectedRequest(), "authoring-work:" + id);
                if (!conversationId.equals(reference.get("conversationId"))
                    || !identity.ownerId().equals("authoring:" + reference.get("sessionId"))) throw new ResponseStatusException(FORBIDDEN, "AUTHORING_ERASURE_SCOPE_MISMATCH");
                if (!repository.delete(record)) throw new ResponseStatusException(CONFLICT, "AUTHORING_ERASURE_BUSY");
            }
            jdbc.update("DELETE FROM ai_conversation_event WHERE tenant_id=? AND deployment_id=? AND conversation_id=? AND owner_id=?", scope);
            jdbc.update("DELETE FROM ai_conversation_ai_interaction WHERE tenant_id=? AND deployment_id=? AND conversation_id=? AND owner_id=?", scope);
            jdbc.update("DELETE FROM ai_conversation_message WHERE tenant_id=? AND deployment_id=? AND conversation_id=? AND owner_id=?", scope);
            jdbc.update("DELETE FROM ai_conversation_orchestration_state WHERE tenant_id=? AND deployment_id=? AND conversation_id=? AND owner_id=?", scope);
            jdbc.update("UPDATE ai_conversation_control_command SET message=NULL WHERE tenant_id=? AND deployment_id=? AND conversation_id=? AND owner_id=?", scope);
        });
    }
}
