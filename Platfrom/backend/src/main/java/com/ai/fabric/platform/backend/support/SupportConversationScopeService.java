package com.ai.fabric.platform.backend.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;
import static com.ai.fabric.platform.backend.support.SupportContracts.*;

/** Private host identity linkage only, independent of ticketing and transcript storage. */
@Service
public class SupportConversationScopeService {
    public record Scope(String conversationId, String runtimePrincipal, Requester requester) { }
    private final JdbcTemplate jdbc;
    private final SupportSettings settings;
    private final SupportRequesterService requesters;
    public SupportConversationScopeService(JdbcTemplate jdbc, SupportSettings settings, SupportRequesterService requesters) {
        this.jdbc = jdbc; this.settings = settings; this.requesters = requesters;
    }
    public Scope create() {
        var owner = requesters.current(); settings.requireEnabled();
        String id = "chat-" + UUID.randomUUID();
        String principal = "support-conversation:" + UUID.randomUUID();
        jdbc.update("""
            insert into platform_support_conversation_scope
            (support_space_id,conversation_id,runtime_principal,customer_id,requester_user_id,requester_issuer,requester_subject,created_at)
            values(?,?,?,?,?,?,?,?)
            """, settings.deskBindingId(), id, principal, owner.customerId(), owner.platformUserId(), owner.issuer(), owner.subject(),
            java.sql.Timestamp.from(Instant.now()));
        return new Scope(id, principal, owner);
    }
    public Scope current(String conversationId) {
        var scope = find("conversation_id", conversationId);
        requesters.requireCurrent(scope.requester()); return scope;
    }
    public Scope verifiedPrincipal(String principal) {
        var scope = find("runtime_principal", principal);
        requesters.requireActive(scope.requester()); return scope;
    }
    private Scope find(String column, String value) {
        settings.requireEnabled();
        return jdbc.query("select * from platform_support_conversation_scope where support_space_id=? and " + column + "=?",
            (row,n) -> new Scope(row.getString("conversation_id"), row.getString("runtime_principal"),
                new Requester(row.getString("customer_id"), row.getString("requester_user_id"), row.getString("requester_issuer"), row.getString("requester_subject"))),
            settings.deskBindingId(), value).stream().findFirst().orElseThrow(SupportPolicy::missing);
    }
}
