package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceDomainEntity;
import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDeliveryMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainType;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceDomainRepository;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AIWorkspaceDomainService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AIWorkspaceDomainRepository repository;
    private final PlatformAIWorkspaceProperties properties;
    private final AIWorkspaceDomainVerifier verifier;
    private final AIWorkspaceDomainRouteService routeService;

    public AIWorkspaceDomainService(AIWorkspaceDomainRepository repository,
                                    PlatformAIWorkspaceProperties properties,
                                    AIWorkspaceDomainVerifier verifier,
                                    AIWorkspaceDomainRouteService routeService) {
        this.repository = repository;
        this.properties = properties;
        this.verifier = verifier;
        this.routeService = routeService;
    }

    @Transactional
    public List<String> synchronize(AIWorkspaceInstallationEntity installation,
                                    AIWorkspaceDeliveryMode deliveryMode,
                                    String appSlug,
                                    List<String> customDomains,
                                    List<String> configuredOrigins) {
        if (deliveryMode == AIWorkspaceDeliveryMode.EMBEDDED) {
            repository.deleteByInstallationEntityId(installation.getId());
            repository.flush();
            routeService.reconcileIfAvailable();
            return configuredOrigins;
        }
        String normalizedSlug = normalizeSlug(appSlug);
        String defaultHostname = normalizedSlug + "." + properties.standaloneBaseDomain();
        LinkedHashMap<String, AIWorkspaceDomainType> desired = new LinkedHashMap<>();
        desired.put(defaultHostname, AIWorkspaceDomainType.PLATFORM_DEFAULT);
        if (customDomains != null) {
            customDomains.forEach(value -> desired.put(normalizeHostname(value), AIWorkspaceDomainType.CUSTOMER_CUSTOM));
        }
        if (desired.size() > 10) {
            throw new ResponseStatusException(BAD_REQUEST, "No more than ten standalone app domains are supported.");
        }
        Map<String, AIWorkspaceDomainEntity> existing = repository
            .findByInstallationEntityIdOrderByHostname(installation.getId()).stream()
            .collect(java.util.stream.Collectors.toMap(AIWorkspaceDomainEntity::getHostname, value -> value));
        Instant now = Instant.now();
        desired.forEach((hostname, type) -> {
            AIWorkspaceDomainEntity domain = existing.remove(hostname);
            if (domain == null) {
                repository.findByHostname(hostname).ifPresent(conflict -> {
                    if (!conflict.getInstallationEntityId().equals(installation.getId())) {
                        throw new ResponseStatusException(CONFLICT, "AI Workspace hostname is already reserved: " + hostname);
                    }
                });
                domain = new AIWorkspaceDomainEntity();
                domain.setId("awd-" + UUID.randomUUID());
                domain.setInstallationEntityId(installation.getId());
                domain.setHostname(hostname);
                domain.setVerificationToken(verificationToken());
                domain.setCreatedAt(now);
                domain.setStatus(AIWorkspaceDomainStatus.PENDING);
            } else if (domain.getDomainType() != type) {
                domain.setStatus(AIWorkspaceDomainStatus.PENDING);
                domain.setVerifiedAt(null);
            }
            domain.setDomainType(type);
            domain.setUpdatedAt(now);
            repository.save(domain);
        });
        repository.deleteAll(existing.values());
        repository.flush();
        routeService.reconcileIfAvailable();

        LinkedHashSet<String> origins = new LinkedHashSet<>();
        desired.keySet().forEach(hostname -> origins.add("https://" + hostname));
        return List.copyOf(origins);
    }

    @Transactional
    public AIWorkspaceDomainSummary verify(String installationEntityId, String hostname) {
        String normalized = normalizeHostname(hostname);
        AIWorkspaceDomainEntity domain = repository.findByHostname(normalized)
            .filter(value -> value.getInstallationEntityId().equals(installationEntityId))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AI Workspace domain not found."));
        Instant now = Instant.now();
        domain.setUpdatedAt(now);
        if (domain.getDomainType() == AIWorkspaceDomainType.CUSTOMER_CUSTOM) {
            var ownership = verifier.verifyOwnership(domain.getHostname(), domain.getVerificationToken());
            if (!ownership.verified()) {
                domain.setStatus(AIWorkspaceDomainStatus.PENDING);
                domain.setVerifiedAt(null);
                domain.setLastVerificationError(ownership.message());
                repository.saveAndFlush(domain);
                routeService.reconcileIfAvailable();
                return summary(domain);
            }
        }
        domain.setStatus(AIWorkspaceDomainStatus.ROUTING_PENDING);
        domain.setVerifiedAt(null);
        domain.setLastVerificationError("The domain is owned; its shared frontend route is being verified.");
        repository.saveAndFlush(domain);
        try {
            routeService.reconcileRequired();
        } catch (RuntimeException ex) {
            domain.setLastVerificationError(routeError(ex));
            repository.saveAndFlush(domain);
            return summary(domain);
        }
        var result = verifier.verifyRoute(domain.getHostname());
        domain.setLastVerificationError(result.message());
        if (result.verified()) {
            domain.setStatus(AIWorkspaceDomainStatus.VERIFIED);
            domain.setVerifiedAt(Instant.now());
        }
        repository.saveAndFlush(domain);
        return summary(domain);
    }

    @Transactional(readOnly = true)
    public List<AIWorkspaceDomainSummary> summaries(String installationEntityId) {
        return repository.findByInstallationEntityIdOrderByHostname(installationEntityId).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public boolean deliveryAvailable(String installationEntityId) {
        return repository.findByInstallationEntityIdOrderByHostname(installationEntityId).stream()
            .filter(domain -> domain.getStatus() == AIWorkspaceDomainStatus.VERIFIED)
            .anyMatch(domain -> verifier.verifyRoute(domain.getHostname()).verified());
    }

    @Transactional(readOnly = true)
    public AIWorkspaceDomainEntity resolveVerifiedOrigin(String origin) {
        String hostname;
        try {
            var uri = java.net.URI.create(origin == null ? "" : origin.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getPort() >= 0
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))) {
                throw new IllegalArgumentException();
            }
            hostname = normalizeHostname(uri.getHost());
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(NOT_FOUND, "AI Workspace app is unavailable.");
        }
        return repository.findByHostnameAndStatusIn(hostname, List.of(AIWorkspaceDomainStatus.VERIFIED))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AI Workspace app is unavailable."));
    }

    public String appUrl(AIWorkspaceInstallationEntity installation) {
        if (installation.getDeliveryMode() != AIWorkspaceDeliveryMode.STANDALONE || installation.getAppSlug() == null) return null;
        return "https://" + installation.getAppSlug() + "." + properties.standaloneBaseDomain();
    }

    public String defaultBaseDomain() {
        return properties.standaloneBaseDomain();
    }

    public String normalizeSlug(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) {
            throw new ResponseStatusException(BAD_REQUEST, "appSlug must be a valid DNS label.");
        }
        return normalized;
    }

    public String normalizeHostname(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.length() > 253 || normalized.isBlank() || normalized.contains("*")
            || !normalized.matches("(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) {
            throw new ResponseStatusException(BAD_REQUEST, "Custom domains must be exact DNS hostnames.");
        }
        return normalized;
    }

    private AIWorkspaceDomainSummary summary(AIWorkspaceDomainEntity domain) {
        boolean customerManaged = domain.getDomainType() == AIWorkspaceDomainType.CUSTOMER_CUSTOM;
        return new AIWorkspaceDomainSummary(
            domain.getHostname(), "https://" + domain.getHostname(), domain.getDomainType().name(), domain.getStatus().name(),
            customerManaged ? "_loomai-workspace." + domain.getHostname() : null,
            customerManaged ? "loomai-workspace-verification=" + domain.getVerificationToken() : null,
            customerManaged ? domain.getHostname() : null,
            customerManaged ? standaloneShellHostname() : null,
            domain.getLastVerificationError(), domain.getVerifiedAt());
    }

    private String standaloneShellHostname() {
        try {
            String baseUrl = properties.standaloneShellBaseUrl();
            return baseUrl == null ? null : java.net.URI.create(baseUrl).getHost();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String routeError(RuntimeException ex) {
        if (ex instanceof ResponseStatusException response && response.getReason() != null) {
            return response.getReason();
        }
        return ex.getMessage() == null || ex.getMessage().isBlank()
            ? "The shared frontend route could not be reconciled."
            : ex.getMessage();
    }

    private String verificationToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
