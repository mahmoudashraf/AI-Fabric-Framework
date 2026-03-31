package com.ai.fabric.platform.backend.security.service;

import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.entity.PlatformUserPreferenceEntity;
import com.ai.fabric.platform.backend.security.model.DeploymentActivityViewPreferences;
import com.ai.fabric.platform.backend.security.model.DeploymentApprovalsViewPreferences;
import com.ai.fabric.platform.backend.security.model.DeploymentListViewPreferences;
import com.ai.fabric.platform.backend.security.model.DeploymentRevisionsViewPreferences;
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
    private static final String KEY_DEPLOYMENT_ACTIVITY_VIEW = "DEPLOYMENT_ACTIVITY_VIEW";
    private static final String KEY_DEPLOYMENT_APPROVALS_VIEW = "DEPLOYMENT_APPROVALS_VIEW";
    private static final String KEY_DEPLOYMENT_REVISIONS_VIEW = "DEPLOYMENT_REVISIONS_VIEW";
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
    private static final DeploymentActivityViewPreferences DEFAULT_ACTIVITY_VIEW = new DeploymentActivityViewPreferences(
        "ALL",
        "ALL",
        ""
    );
    private static final DeploymentApprovalsViewPreferences DEFAULT_APPROVALS_VIEW =
        new DeploymentApprovalsViewPreferences("ALL", "ALL", false, "");
    private static final DeploymentRevisionsViewPreferences DEFAULT_REVISIONS_VIEW =
        new DeploymentRevisionsViewPreferences("", "ALL", "ALL", "ALL");

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
    private static final Set<String> ACTIVITY_CATEGORY_FILTERS = Set.of(
        "ALL",
        "APPROVAL",
        "ACCESS",
        "RELEASE",
        "POC",
        "LIFECYCLE",
        "CONFIGURATION"
    );
    private static final Set<String> ACTIVITY_ACTOR_FILTERS = Set.of(
        "ALL",
        "PLATFORM_ADMIN",
        "PLATFORM_OPERATOR",
        "PUBLIC_API_CLIENT",
        "SYSTEM"
    );
    private static final Set<String> APPROVAL_STATUS_FILTERS = Set.of(
        "ALL",
        "PENDING",
        "APPROVED",
        "CONSUMED",
        "REJECTED",
        "EXPIRED"
    );
    private static final Set<String> APPROVAL_OPERATION_FILTERS = Set.of(
        "ALL",
        "APPLY_VERSION",
        "DELETE_DEPLOYMENT"
    );
    private static final Set<String> VERSION_STATUS_FILTERS = Set.of(
        "ALL",
        "PUBLISHED"
    );
    private static final Set<String> RELEASE_STATUS_FILTERS = Set.of(
        "ALL",
        "UNAPPLIED",
        "APPLY_REQUESTED",
        "PRE_APPLY_VERIFYING",
        "PRE_APPLY_BLOCKED",
        "PROVISIONING",
        "VERIFYING",
        "APPLIED_VERIFIED",
        "APPLIED_VERIFICATION_FAILED",
        "FAILED"
    );
    private static final Set<String> REINDEX_FILTERS = Set.of(
        "ALL",
        "REINDEX_REQUIRED",
        "READY"
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

        DeploymentActivityViewPreferences activityView = readPreference(
            preferences,
            KEY_DEPLOYMENT_ACTIVITY_VIEW,
            DeploymentActivityViewPreferences.class
        ).map(this::normalizeActivityView)
            .orElse(DEFAULT_ACTIVITY_VIEW);
        DeploymentApprovalsViewPreferences approvalsView = readPreference(
            preferences,
            KEY_DEPLOYMENT_APPROVALS_VIEW,
            DeploymentApprovalsViewPreferences.class
        ).map(this::normalizeApprovalsView)
            .orElse(DEFAULT_APPROVALS_VIEW);
        DeploymentRevisionsViewPreferences revisionsView = readPreference(
            preferences,
            KEY_DEPLOYMENT_REVISIONS_VIEW,
            DeploymentRevisionsViewPreferences.class
        ).map(this::normalizeRevisionsView)
            .orElse(DEFAULT_REVISIONS_VIEW);

        return new PlatformUserPreferences(listView, workspace, activityView, approvalsView, revisionsView);
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
        if (request != null && request.deploymentActivityView() != null) {
            savePreference(user, KEY_DEPLOYMENT_ACTIVITY_VIEW, normalizeActivityView(request.deploymentActivityView()));
        }
        if (request != null && request.deploymentApprovalsView() != null) {
            savePreference(user, KEY_DEPLOYMENT_APPROVALS_VIEW, normalizeApprovalsView(request.deploymentApprovalsView()));
        }
        if (request != null && request.deploymentRevisionsView() != null) {
            savePreference(user, KEY_DEPLOYMENT_REVISIONS_VIEW, normalizeRevisionsView(request.deploymentRevisionsView()));
        }

        return getPreferences();
    }

    private PlatformUserPreferences defaultPreferences() {
        return new PlatformUserPreferences(
            DEFAULT_LIST_VIEW,
            DEFAULT_WORKSPACE,
            DEFAULT_ACTIVITY_VIEW,
            DEFAULT_APPROVALS_VIEW,
            DEFAULT_REVISIONS_VIEW
        );
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

    private DeploymentActivityViewPreferences normalizeActivityView(DeploymentActivityViewPreferences input) {
        if (input == null) {
            return DEFAULT_ACTIVITY_VIEW;
        }
        String categoryFilter = normalizeFilter(input.categoryFilter(), ACTIVITY_CATEGORY_FILTERS, "ALL");
        String actorRoleFilter = normalizeFilter(input.actorRoleFilter(), ACTIVITY_ACTOR_FILTERS, "ALL");
        String searchTerm = normalizeString(input.searchTerm(), MAX_SEARCH_LENGTH);
        return new DeploymentActivityViewPreferences(categoryFilter, actorRoleFilter, searchTerm);
    }

    private DeploymentApprovalsViewPreferences normalizeApprovalsView(DeploymentApprovalsViewPreferences input) {
        if (input == null) {
            return DEFAULT_APPROVALS_VIEW;
        }
        String statusFilter = normalizeFilter(input.statusFilter(), APPROVAL_STATUS_FILTERS, "ALL");
        String operationFilter = normalizeFilter(input.operationFilter(), APPROVAL_OPERATION_FILTERS, "ALL");
        String searchTerm = normalizeString(input.searchTerm(), MAX_SEARCH_LENGTH);
        return new DeploymentApprovalsViewPreferences(statusFilter, operationFilter, input.mineOnly(), searchTerm);
    }

    private DeploymentRevisionsViewPreferences normalizeRevisionsView(DeploymentRevisionsViewPreferences input) {
        if (input == null) {
            return DEFAULT_REVISIONS_VIEW;
        }
        String searchTerm = normalizeString(input.searchTerm(), MAX_SEARCH_LENGTH);
        String versionStatusFilter = normalizeFilter(input.versionStatusFilter(), VERSION_STATUS_FILTERS, "ALL");
        String releaseStatusFilter = normalizeFilter(input.releaseStatusFilter(), RELEASE_STATUS_FILTERS, "ALL");
        String reindexFilter = normalizeFilter(input.reindexFilter(), REINDEX_FILTERS, "ALL");
        return new DeploymentRevisionsViewPreferences(searchTerm, versionStatusFilter, releaseStatusFilter, reindexFilter);
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
