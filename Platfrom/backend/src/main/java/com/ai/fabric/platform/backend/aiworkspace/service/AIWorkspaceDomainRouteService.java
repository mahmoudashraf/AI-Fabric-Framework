package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApiClient;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApplicationSummary;
import com.ai.fabric.platform.backend.deployment.service.CoolifyTargetProfileResolver;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetProfileIdentity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** One bounded IO step per tick; only AIWorkspaceRouteStore opens write transactions. */
@Service
public class AIWorkspaceDomainRouteService {
    private final AIWorkspaceRouteStore store;
    private final PlatformManagedProductServiceRepository services;
    private final DeploymentTargetProfileRepository profiles;
    private final CoolifyTargetProfileResolver resolver;
    private final CoolifyApiClient coolify;
    private final PlatformAIWorkspaceProperties properties;
    private final ObjectMapper mapper;

    public AIWorkspaceDomainRouteService(AIWorkspaceRouteStore store, PlatformManagedProductServiceRepository services,
                                         DeploymentTargetProfileRepository profiles, CoolifyTargetProfileResolver resolver,
                                         CoolifyApiClient coolify, PlatformAIWorkspaceProperties properties, ObjectMapper mapper) {
        this.store = store; this.services = services; this.profiles = profiles;
        this.resolver = resolver; this.coolify = coolify; this.properties = properties; this.mapper = mapper;
    }

    public boolean isApplied(String hostname) {
        var binding = binding();
        return binding != null && store.isApplied(binding, hostname);
    }

    public List<AIWorkspaceRouteStore.Status> statuses() {
        requireAdmin(); return store.statuses();
    }

    /** Staff-reviewed receipt attachment is read-only at the provider, never an effect retry. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<AIWorkspaceRouteStore.Status> observeDeployment(String routeKey, AIWorkspaceRouteStore.ObserveDeployment command) {
        requireAdmin();
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Route provider IO cannot run in a database transaction");
        if (command == null || command.operationKey() == null || !command.operationKey().matches("[A-Za-z0-9._:-]{1,128}")
            || command.expectedGeneration() < 1 || command.expectedFingerprint() == null || !command.expectedFingerprint().matches("[a-f0-9]{64}")
            || command.expectedInflightFingerprint() == null || !command.expectedInflightFingerprint().matches("[a-f0-9]{64}")
            || command.deploymentUuid() == null || !command.deploymentUuid().matches("[A-Za-z0-9_-]{1,128}")
            || command.operatorEvidenceRef() == null || command.operatorEvidenceRef().isBlank() || command.operatorEvidenceRef().length() > 256) {
            throw new ResponseStatusException(BAD_REQUEST, "An exact reviewed route observation command is required.");
        }
        var binding = binding();
        if (binding == null || !binding.key().equals(routeKey)) throw new ResponseStatusException(NOT_FOUND, "Current route binding not found.");
        var observation = store.beginObservation(binding, command, PlatformSecurityContext.actorIdOrSystem());
        if (observation.replay()) return store.statuses();
        try {
            var profile = profiles.findById(binding.profileId()).orElseThrow();
            if (!profileIdentity(profile).equals(binding.profileFingerprint())) throw new IllegalStateException("Route profile changed");
            var connection = resolver.requireConnection(profile);
            var app = coolify.getApplication(connection, binding.applicationUuid()).orElseThrow();
            var deployment = coolify.getDeployment(connection, command.deploymentUuid()).orElseThrow();
            Instant createdAt = Instant.parse(deployment.createdAt());
            if (!binding.equals(binding()) || !binding.applicationUuid().equals(app.uuid())
                || !command.deploymentUuid().equals(deployment.deploymentUuid())
                || !binding.applicationUuid().equals(deployment.applicationUuid())
                || createdAt.isBefore(observation.intentAt())
                || !same(app.fqdn(), observation.claim().expected())
                || !Set.of("QUEUED", "IN_PROGRESS", "RUNNING", "FINISHED", "SUCCESS", "SUCCEEDED", "COMPLETED")
                    .contains(normalized(deployment.status()))) {
                throw new IllegalStateException("Unmatched native observation");
            }
        } catch (RuntimeException ex) {
            store.failed(observation.claim(), "OBSERVATION_NOT_PROVEN", false);
            throw new ResponseStatusException(CONFLICT, "Native deployment observation could not be proven.");
        }
        if (!store.attachObservation(observation, command))
            throw new ResponseStatusException(CONFLICT, "Route changed during native deployment observation.");
        return store.statuses();
    }

    private static void requireAdmin() {
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null) throw new ResponseStatusException(UNAUTHORIZED, "Staff authentication required.");
        if (principal.role() != PlatformRole.PLATFORM_ADMIN) throw new ResponseStatusException(FORBIDDEN, "Platform administrator required.");
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void runNext() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Route provider IO cannot run in a database transaction");
        }
        var binding = binding();
        if (binding == null) return;
        var claim = store.claim(binding);
        if (claim == null) return;
        try {
            // FQDN config alone cannot prove an unreceipted native deployment.
            if (Set.of("UNKNOWN", "FAILED").contains(claim.phase())) { store.waiting(claim); return; }
            if (!binding.equals(binding())) { store.failed(claim, "BINDING_CHANGED", false); return; }
            var profile = profiles.findById(binding.profileId()).orElseThrow();
            if (!profileIdentity(profile).equals(binding.profileFingerprint())) throw new IllegalStateException("Route profile changed");
            var connection = resolver.requireConnection(profile);
            var app = coolify.getApplication(connection, binding.applicationUuid()).orElseThrow();
            if (!binding.applicationUuid().equals(app.uuid())) {
                store.failed(claim, "APPLICATION_IDENTITY_MISMATCH", false); return;
            }
            switch (claim.phase()) {
                case "IDLE" -> {
                    List<String> expected = merge(app.fqdn(), claim.managed(), claim.inflight(), claim.desired());
                    if (same(app.fqdn(), expected)) {
                        if (running(app)) store.unchanged(claim); else store.waiting(claim);
                    } else if (store.preparePatch(claim, expected)) {
                        if (!binding.equals(binding())) { store.failed(claim, "BINDING_CHANGED", true); return; }
                        coolify.updateApplicationDomains(connection, binding.applicationUuid(), String.join(",", expected));
                        store.patched(claim);
                    }
                }
                case "PATCHED" -> {
                    if (!same(app.fqdn(), claim.expected())) {
                        store.failed(claim, "PROVIDER_STATE_CHANGED", false); return;
                    }
                    if (store.prepareStart(claim)) {
                        if (!binding.equals(binding())) { store.failed(claim, "BINDING_CHANGED", true); return; }
                        var response = coolify.start(connection, binding.applicationUuid(), false, true);
                        store.started(claim, response == null ? null : response.deploymentUuid());
                    }
                }
                case "POLLING" -> {
                    var deployment = coolify.getDeployment(connection, claim.deploymentUuid()).orElse(null);
                    if (deployment == null) { store.failed(claim, "DEPLOYMENT_READBACK_MISSING", false); return; }
                    if (!claim.deploymentUuid().equals(deployment.deploymentUuid())
                        || !binding.applicationUuid().equals(deployment.applicationUuid())) {
                        store.failed(claim, "DEPLOYMENT_IDENTITY_MISMATCH", false); return;
                    }
                    String status = normalized(deployment.status());
                    if (Set.of("FAILED", "ERROR", "CANCELLED", "CANCELED").contains(status)) {
                        store.failed(claim, "DEPLOYMENT_FAILED", false);
                    } else if (Set.of("FINISHED", "SUCCESS", "SUCCEEDED", "COMPLETED").contains(status)) {
                        if (!same(app.fqdn(), claim.expected())) store.failed(claim, "PROVIDER_STATE_CHANGED", false);
                        else if (running(app)) store.completed(claim);
                        else store.waiting(claim);
                    } else store.waiting(claim);
                }
                default -> store.failed(claim, "UNEXPECTED_ROUTE_PHASE", true);
            }
        } catch (RuntimeException ex) {
            // Durable phase determines safety: reads retry, dispatched effects become
            // UNKNOWN. Exception bodies never enter state, audit, logs or responses.
            store.failed(claim, "PROVIDER_OUTCOME_UNAVAILABLE", false);
        }
    }

    private AIWorkspaceRouteStore.Binding binding() {
        var candidates = services.findAllByOrderByDisplayNameAsc().stream()
            .filter(s -> PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE.equals(s.getServiceKind()))
            .filter(s -> !"DECOMMISSIONED".equals(s.getStatus()))
            .filter(s -> text(details(s), "coolifyApplicationUuid") != null).toList();
        if (candidates.size() > 1 && properties.standaloneShellBaseUrl() != null) {
            String host = key(properties.standaloneShellBaseUrl());
            candidates = candidates.stream().filter(s -> domains(s.getBaseUrl()).stream().anyMatch(d -> key(d).equals(host))).toList();
        }
        if (candidates.isEmpty()) return null;
        if (candidates.size() != 1) throw new IllegalStateException("Ambiguous workspace route binding");
        var service = candidates.get(0);
        var details = details(service);
        String profile = text(details, "targetProfileId");
        if (profile == null) throw new IllegalStateException("Workspace route profile missing");
        var target = profiles.findById(profile).orElseThrow(() -> new IllegalStateException("Workspace route profile missing"));
        if (!target.isActive() || !target.isPlatformServicesAllowed() || target.getProviderType() != DeploymentProviderType.COOLIFY)
            throw new ResponseStatusException(CONFLICT, "Workspace route profile is not available for platform services.");
        var historic = details.get("aiWorkspaceManagedDomains");
        List<String> managed = new ArrayList<>();
        if (historic != null) {
            if (!historic.isArray()) throw new IllegalStateException("Invalid historic route ownership");
            historic.forEach(value -> {
                if (!value.isTextual()) throw new IllegalStateException("Invalid historic route ownership");
                managed.add(key(value.textValue()));
            });
        }
        return new AIWorkspaceRouteStore.Binding(service.getId(), profile, profileIdentity(target), text(details, "coolifyApplicationUuid"), managed);
    }

    private String profileIdentity(DeploymentTargetProfileEntity profile) {
        return AIWorkspaceRouteStore.fingerprint(List.of(DeploymentTargetProfileIdentity.fingerprint(mapper, profile),
            Boolean.toString(profile.isActive()), Boolean.toString(profile.isPlatformServicesAllowed())));
    }

    static List<String> merge(String fqdn, List<String> managed, List<String> inflight, List<String> desired) {
        Set<String> owned = new LinkedHashSet<>(managed); owned.addAll(inflight);
        var result = new LinkedHashMap<String, String>();
        for (String value : domains(fqdn)) {
            String host = key(value);
            if (!owned.contains(host)) {
                if (desired.contains(host)) throw new IllegalStateException("Unmanaged route ownership collision");
                result.put(host, value);
            }
        }
        desired.forEach(host -> result.put(host, "https://" + host));
        if (result.isEmpty()) throw new IllegalStateException("Cannot remove every shared frontend domain");
        return List.copyOf(result.values());
    }

    private JsonNode details(PlatformManagedProductServiceEntity service) {
        try {
            var result = mapper.readTree(service.getDetailsJson());
            if (result == null || !result.isObject()) throw new IllegalStateException();
            return result;
        } catch (Exception ex) { throw new IllegalStateException("Invalid workspace route binding"); }
    }
    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || !value.isTextual() || value.textValue().isBlank() ? null : value.textValue();
    }
    static boolean same(String current, List<String> expected) {
        return new LinkedHashSet<>(domains(current)).equals(new LinkedHashSet<>(expected));
    }
    static List<String> domains(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(",")).map(String::trim).map(s -> { key(s); return s; }).toList();
    }
    static String key(String value) {
        URI uri = URI.create(value.contains("://") ? value : "https://" + value);
        if (!Set.of("https", "http").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
            || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null
            || uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")) {
            throw new IllegalStateException("Invalid provider domain configuration");
        }
        return uri.getHost().toLowerCase(Locale.ROOT);
    }
    private static boolean running(CoolifyApplicationSummary app) {
        return Set.of("RUNNING", "RUNNING:HEALTHY").contains(normalized(app.status()));
    }
    private static String normalized(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
}
