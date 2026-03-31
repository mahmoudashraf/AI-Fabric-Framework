package com.ai.fabric.platform.backend.security.service;

import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.entity.PlatformUserPreferenceEntity;
import com.ai.fabric.platform.backend.security.model.DeploymentListViewPreferences;
import com.ai.fabric.platform.backend.security.model.DeploymentWorkspacePreferences;
import com.ai.fabric.platform.backend.security.model.PlatformUserPreferences;
import com.ai.fabric.platform.backend.security.model.UpdatePlatformUserPreferencesRequest;
import com.ai.fabric.platform.backend.security.repository.PlatformUserPreferenceRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlatformUserPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(PlatformUserPreferenceService.class);

    private static final String KEY_DEPLOYMENT_LIST_VIEW = "DEPLOYMENT_LIST_VIEW";
    private static final String KEY_DEPLOYMENT_WORKSPACE_STATE = "DEPLOYMENT_WORKSPACE_STATE";
    private static final int MAX_SEARCH_LENGTH = 160;
    private static final int MAX_TEMPLATE_LENGTH = 128;

    private static final DeploymentListViewPreferences DEFAULT_LIST_VIEW = new DeploymentListViewPreferences(
        false,
        "",
        "ALL",
        "ALL",
        "ALL"
    );
    private static final DeploymentWorkspacePreferences DEFAULT_WORKSPACE = new DeploymentWorkspacePreferences(null, null);

    private static final Set<String> HEALTH_FILTERS = Set.of(
        "ALL",
        "HEALTHY",
        "PROVISIONING",
        "ATTENTION"
    );
    private static final Set<String> ROLE_FILTERS = Set.of(
        "ALL",
        "DEPLOYMENT_ADMIN",
        "DEPLOYMENT_EDITOR",
        "DEPLOYMENT_OPERATOR",
        "DEPLOYMENT_VIEWER"
    );
    private static final Set<String> WORKSPACE_SECTIONS = Set.of(
        "/overview",
        "/activity",
        "/actions",
        "/approvals",
        "/access",
        "/knowledge",
        "/poc",
        "/prompts",
        "/providers",
        "/security",
        "/verification",
        "/revisions",
        "/diagnostics",
        "/users"
    );

    private final PlatformUserPreferenceRepository preferenceRepository;
    private final PlatformUserRepository userRepository;
    private final ObjectMapper objectMapper;

    public PlatformUserPreferenceService(PlatformUserPreferenceRepository preferenceRepository,
                                         PlatformUserRepository userRepository,
                                         ObjectMapper objectMapper) {
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public PlatformUserPreferences getPreferences() {
        PlatformUserEntity user = currentUserOrNull();
        if (user == null) {
            return defaultPreferences();
        }
        Map<String, PlatformUserPreferenceEntity> preferences = preferenceRepository
            .findByUserIdOrderByUpdatedAtDesc(user.getId())
            .stream()
            .collect(Collectors.toMap(
                PlatformUserPreferenceEntity::getPreferenceKey,
                Function.identity(),
                (first, second) -> first
            ));

        DeploymentListViewPreferences listView = readPreference(
            preferences,
            KEY_DEPLOYMENT_LIST_VIEW,
            DeploymentListViewPreferences.class
        ).map(this::normalizeListView)
            .orElse(DEFAULT_LIST_VIEW);

        DeploymentWorkspacePreferences workspace = readPreference(
            preferences,
            KEY_DEPLOYMENT_WORKSPACE_STATE,
            DeploymentWorkspacePreferences.class
        ).map(this::normalizeWorkspace)
            .orElse(DEFAULT_WORKSPACE);

        return new PlatformUserPreferences(listView, workspace);
    }

    @Transactional
    public PlatformUserPreferences updatePreferences(UpdatePlatformUserPreferencesRequest request) {
        PlatformUserEntity user = currentUserOrNull();
        if (user == null) {
            return defaultPreferences();
        }

        if (request != null && request.deploymentListView() != null) {
            savePreference(user, KEY_DEPLOYMENT_LIST_VIEW, normalizeListView(request.deploymentListView()));
        }
        if (request != null && request.deploymentWorkspace() != null) {
            savePreference(user, KEY_DEPLOYMENT_WORKSPACE_STATE, normalizeWorkspace(request.deploymentWorkspace()));
        }

        return getPreferences();
    }

    private PlatformUserPreferences defaultPreferences() {
        return new PlatformUserPreferences(DEFAULT_LIST_VIEW, DEFAULT_WORKSPACE);
    }

    private Optional<PlatformUserEntity> currentUser() {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null) {
            return Optional.empty();
        }
        return userRepository.findByEmailIgnoreCase(principal.actorId());
    }

    private PlatformUserEntity currentUserOrNull() {
        return currentUser().orElse(null);
    }

    private <T> Optional<T> readPreference(Map<String, PlatformUserPreferenceEntity> preferences,
                                           String key,
                                           Class<T> targetClass) {
        PlatformUserPreferenceEntity entry = preferences.get(key);
        if (entry == null || !StringUtils.hasText(entry.getPreferenceJson())) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(entry.getPreferenceJson(), targetClass));
        } catch (Exception ex) {
            log.warn("Failed to parse preference {} for user {}", key, entry.getUserId(), ex);
            return Optional.empty();
        }
    }

    private void savePreference(PlatformUserEntity user, String key, Object value) {
        PlatformUserPreferenceEntity entry = preferenceRepository
            .findByUserIdAndPreferenceKey(user.getId(), key)
            .orElseGet(PlatformUserPreferenceService::newPreference);
        entry.setUserId(user.getId());
        entry.setPreferenceKey(key);
        entry.setPreferenceJson(serializePreference(value));
        Instant now = Instant.now();
        if (entry.getCreatedAt() == null) {
            entry.setCreatedAt(now);
        }
        entry.setUpdatedAt(now);
        preferenceRepository.save(entry);
    }

    private static PlatformUserPreferenceEntity newPreference() {
        PlatformUserPreferenceEntity entry = new PlatformUserPreferenceEntity();
        entry.setId("pref-" + UUID.randomUUID().toString().substring(0, 8));
        entry.setCreatedAt(Instant.now());
        entry.setUpdatedAt(Instant.now());
        return entry;
    }

    private String serializePreference(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize platform user preferences.", ex);
        }
    }

    private DeploymentListViewPreferences normalizeListView(DeploymentListViewPreferences input) {
        if (input == null) {
            return DEFAULT_LIST_VIEW;
        }
        boolean showArchived = input.showArchived();
        String searchTerm = normalizeString(input.searchTerm(), MAX_SEARCH_LENGTH);
        String healthFilter = normalizeFilter(input.healthFilter(), HEALTH_FILTERS, "ALL");
        String roleFilter = normalizeFilter(input.roleFilter(), ROLE_FILTERS, "ALL");
        String templateFilter = normalizeString(input.templateFilter(), MAX_TEMPLATE_LENGTH);
        if (!StringUtils.hasText(templateFilter) || "ALL".equalsIgnoreCase(templateFilter)) {
            templateFilter = "ALL";
        }
        return new DeploymentListViewPreferences(showArchived, searchTerm, healthFilter, roleFilter, templateFilter);
    }

    private DeploymentWorkspacePreferences normalizeWorkspace(DeploymentWorkspacePreferences input) {
        if (input == null) {
            return DEFAULT_WORKSPACE;
        }
        String lastDeploymentId = normalizeString(input.lastDeploymentId(), 64);
        String lastSection = normalizeSection(input.lastSection());
        return new DeploymentWorkspacePreferences(
            StringUtils.hasText(lastDeploymentId) ? lastDeploymentId : null,
            lastSection
        );
    }

    private String normalizeSection(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String candidate = value.trim();
        return WORKSPACE_SECTIONS.contains(candidate) ? candidate : null;
    }

    private String normalizeFilter(String value, Set<String> allowed, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private String normalizeString(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            return trimmed.substring(0, maxLength);
        }
        return trimmed;
    }
}
