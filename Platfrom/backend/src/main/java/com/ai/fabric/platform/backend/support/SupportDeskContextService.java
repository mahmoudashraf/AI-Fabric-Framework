package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static com.ai.fabric.platform.backend.support.SupportDeskContracts.*;

@Service
public class SupportDeskContextService {
    private final SupportDeskAuthorization authorization;
    private final SupportSettings settings;
    private final SupportCaseStore cases;
    private final SupportDeskStore operations;
    private final SupportRequesterService requesters;
    private final SupportResourceService resources;
    private final PlatformCustomerRepository customers;

    public SupportDeskContextService(SupportDeskAuthorization authorization, SupportSettings settings,
        SupportCaseStore cases, SupportDeskStore operations, SupportRequesterService requesters,
        SupportResourceService resources, PlatformCustomerRepository customers) {
        this.authorization = authorization; this.settings = settings; this.cases = cases; this.operations = operations;
        this.requesters = requesters; this.resources = resources; this.customers = customers;
    }

    @Transactional
    public Response handle(Request input, HttpHeaders headers) {
        if (input == null || input.schemaVersion() != 1 || input.operation() == null
            || input.operation() == Operation.LOOKUP && input.command() != null) throw SupportPolicy.bad("SUPPORT_DESK_INVALID_OPERATION");
        var actor = authorization.callback(headers, input);
        var c = cases.byPrincipal(input.scope().ownerRef()).orElse(null);
        if (c == null) return unlinked(input);
        var episode = cases.episode(c.id(), input.scope().conversationId()).orElse(null);
        if (episode == null || !episode.binding().equals(settings.binding())) return unlinked(input);
        requireCaseAuthority(c);
        if (input.operation() == Operation.LOOKUP) {
            var response = available(input, c);
            operations.record(c, input, actor, null, null, null);
            return response;
        }
        if (!settings.deskResolveEnabled()) throw SupportPolicy.denied();
        var command = input.command();
        if (command == null || command.expectedVersion() == null || command.expectedVersion() < 0) throw SupportPolicy.bad("SUPPORT_DESK_RESOLVE_COMMAND_REQUIRED");
        String key = SupportPolicy.required(command.idempotencyKey(), 128, "DESK_IDEMPOTENCY_KEY");
        String receiptKey = digest(operations.json(List.of(input.scope().bindingId(), input.scope().conversationId(),
            actor.issuer(), actor.subject(), input.operation().name(), key)));
        String requestHash = digest(operations.json(input));
        var prior = operations.receipt(receiptKey).orElse(null);
        if (prior != null) {
            if (!prior.caseId().equals(c.id()) || !prior.requestHash().equals(requestHash)) throw SupportPolicy.conflict("SUPPORT_DESK_IDEMPOTENCY_CONFLICT");
            resources.requireOwned(c.requester(), prior.resources());
            return prior.response();
        }
        if (c.revision() != command.expectedVersion()) throw SupportPolicy.conflict("SUPPORT_CASE_REVISION_CONFLICT");
        if (c.status() != CaseStatus.OPEN) throw SupportPolicy.conflict("SUPPORT_CASE_CLOSED");
        var now = Instant.now();
        cases.consent(c.id(), false, now);
        cases.status(c.id(), CaseStatus.RESOLVED, now);
        var resolved = cases.find(c.id(), false).orElseThrow();
        // Null Platform user deliberately: the actual Desk actor is recorded in the correlated operation below.
        cases.event(resolved, "DESK_RESOLVED", null, now);
        var response = available(input, resolved);
        operations.record(resolved, input, actor, receiptKey, requestHash, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<StaffIntake> intake(HttpHeaders headers, int page, int size) {
        authorization.staff(headers, null);
        if (page < 0 || page > 10000 || size < 1 || size > 50) throw SupportPolicy.bad("SUPPORT_INVALID_PAGE");
        var binding = settings.binding();
        return cases.staffIntake(binding, size, page * size).stream().map(c -> {
            if (!cases.episodes(c.id()).getLast().binding().equals(binding)) throw SupportPolicy.denied();
            requireCaseAuthority(c);
            return new StaffIntake(c.id(), c.reference(), c.summary(), c.details(), c.status().name(), c.revision(),
                accountLabel(c), resourceLabels(c), consent(c), c.deliveryStatus().name(), c.createdAt(), c.updatedAt());
        }).toList();
    }

    private void requireCaseAuthority(CaseRecord c) {
        requesters.requireActive(c.requester());
        resources.requireOwned(c.requester(), c.resources());
    }
    private Response available(Request input, CaseRecord c) {
        var context = new Context(c.reference(), c.summary(), c.status().name(), c.revision(), accountLabel(c),
            resourceLabels(c), consent(c), observations(c),
            settings.deskResolveEnabled() && c.status() == CaseStatus.OPEN
                ? new ResolveAction("Resolve case", "Mark this case resolved? The conversation will stay open.") : null);
        var response = new Response(1, input.scope(), State.AVAILABLE, context);
        if (operations.json(response).getBytes(StandardCharsets.UTF_8).length > 32768) throw unrepresentable();
        return response;
    }
    private String accountLabel(CaseRecord c) {
        String label = customers.findById(c.requester().customerId()).orElseThrow(SupportPolicy::denied).getName();
        if (label == null || label.isBlank() || label.length() > 160) throw unrepresentable();
        return label;
    }
    private List<String> resourceLabels(CaseRecord c) {
        return c.resources().stream().map(r -> switch (r.type()) {
            case DEPLOYMENT -> "Deployment: " + r.id();
            case ENGAGE_APP -> "Engage app: " + r.id();
            case CONNECTION -> "Connection: " + r.id();
        }).toList();
    }
    private SupportDeskContracts.Consent consent(CaseRecord c) {
        String status = c.consent().granted() ? "GIVEN" : c.consent().revokedAt() == null ? "DECLINED" : "REVOKED";
        return new SupportDeskContracts.Consent(status,
            "Current status checks for this request's selected resources only; no configuration or infrastructure changes.", c.consent().changedAt());
    }
    private List<Observation> observations(CaseRecord c) {
        if (!c.consent().granted() || c.status() != CaseStatus.OPEN) return List.of();
        return c.resources().stream().map(ref -> {
            var value = resources.read(c.requester(), ref);
            String summary = switch (ref.type()) {
                case DEPLOYMENT -> "Recorded deployment status: " + value.facts().getOrDefault("deploymentStatus", "UNKNOWN") + ". Live health has not been probed.";
                case ENGAGE_APP -> "Local readiness passed: " + value.facts().getOrDefault("localReadinessPassed", false) + ". Delivery and HTTPS require explicit verification.";
                case CONNECTION -> "Recorded sync state: " + value.facts().getOrDefault("syncState", "UNKNOWN") + ". The upstream provider has not been probed.";
            };
            return new Observation(resourceLabel(ref), value.availability().name(), summary, Instant.now());
        }).toList();
    }
    private String resourceLabel(ResourceRef ref) {
        return switch (ref.type()) {
            case DEPLOYMENT -> "Deployment status";
            case ENGAGE_APP -> "Engage readiness";
            case CONNECTION -> "Connection sync";
        };
    }
    private static Response unlinked(Request input) { return new Response(1, input.scope(), State.NOT_LINKED, null); }
    private static org.springframework.web.server.ResponseStatusException unrepresentable() {
        return SupportPolicy.error(HttpStatus.UNPROCESSABLE_ENTITY, "SUPPORT_DESK_CONTEXT_NOT_REPRESENTABLE");
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception failure) { throw new IllegalStateException("Cannot fingerprint Desk support command", failure); }
    }
}
