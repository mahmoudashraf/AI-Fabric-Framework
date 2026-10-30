package com.ai.fabric.platform.backend.support;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;

@Service
public class SupportDiagnosticService {
    public static final String CASE_SUMMARY = "support_get_case_summary";
    public static final Map<String, ResourceType> RESOURCE_ACTIONS = Map.of(
        "support_get_deployment_status", ResourceType.DEPLOYMENT,
        "support_get_engage_readiness", ResourceType.ENGAGE_APP,
        "support_get_connection_sync_status", ResourceType.CONNECTION);

    private final SupportDiagnosticAuthentication authentication;
    private final SupportCaseStore store;
    private final SupportRequesterService requesters;
    private final SupportResourceService resources;

    public SupportDiagnosticService(SupportDiagnosticAuthentication authentication, SupportCaseStore store,
                                     SupportRequesterService requesters, SupportResourceService resources) {
        this.authentication = authentication; this.store = store; this.requesters = requesters; this.resources = resources;
    }

    @Transactional
    public DiagnosticResult read(String action, HttpHeaders headers) {
        var caller = authentication.verify(headers);
        if (!CASE_SUMMARY.equals(action) && !RESOURCE_ACTIONS.containsKey(action)) throw SupportPolicy.bad("SUPPORT_UNKNOWN_ACTION");
        // Lock the case through projection so revocation cannot complete while an older read is being accepted.
        var c = store.byPrincipal(caller.principal()).orElseThrow(SupportPolicy::denied);
        var episodes = store.episodes(c.id());
        var episode = caller.conversationId() == null ? episodes.getLast()
            : store.episode(c.id(), caller.conversationId()).orElseThrow(SupportPolicy::denied);
        if (!episode.binding().equals(caller.binding()) || episode.terminal()
            || !episodes.getLast().id().equals(episode.id()) || c.status() != CaseStatus.OPEN || !c.consent().granted()) {
            throw SupportPolicy.denied();
        }
        requesters.requireActive(c.requester());
        resources.requireOwned(c.requester(), c.resources());
        if (CASE_SUMMARY.equals(action)) {
            return new DiagnosticResult(action, Instant.now(), Availability.CURRENT,
                new CaseDiagnostic(c.reference(), c.summary(), c.topic(), c.status(), c.consent(), c.resources()), List.of());
        }
        var selected = c.resources().stream().filter(r -> r.type() == RESOURCE_ACTIONS.get(action)).toList();
        var results = selected.stream().map(r -> resources.read(c.requester(), r)).toList();
        var availability = results.isEmpty() ? Availability.NOT_APPLICABLE
            : results.stream().allMatch(r -> r.availability() == Availability.CURRENT) ? Availability.CURRENT : Availability.INCOMPLETE;
        return new DiagnosticResult(action, Instant.now(), availability, null, results);
    }
}
