package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePublisherEntity;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplacePublisherSubmissionRequest;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePublisherDetailSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePublisherSubmissionSummary;
import com.ai.fabric.platform.backend.marketplace.model.ReviewMarketplacePublisherSubmissionRequest;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class MarketplacePublishingService {

    private final MarketplacePublisherService marketplacePublisherService;
    private final MarketplacePluginRepository pluginRepository;
    private final MarketplacePluginVersionRepository versionRepository;
    private final MarketplaceManifestService manifestService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public MarketplacePublishingService(MarketplacePublisherService marketplacePublisherService,
                                        MarketplacePluginRepository pluginRepository,
                                        MarketplacePluginVersionRepository versionRepository,
                                        MarketplaceManifestService manifestService,
                                        PlatformAuditService platformAuditService,
                                        ObjectMapper objectMapper) {
        this.marketplacePublisherService = marketplacePublisherService;
        this.pluginRepository = pluginRepository;
        this.versionRepository = versionRepository;
        this.manifestService = manifestService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    public MarketplacePublisherDetailSummary getPublisherDetail(String publisherIdOrSlug) {
        var publisher = marketplacePublisherService.requirePublisherAccess(publisherIdOrSlug);
        return new MarketplacePublisherDetailSummary(
            marketplacePublisherService.getPublisher(publisher.getId()),
            versionRepository.findBySubmittedByPublisherIdOrderByCreatedAtDesc(publisher.getId()).stream()
                .map(this::toSubmissionSummary)
                .toList()
        );
    }

    @Transactional
    public MarketplacePublisherSubmissionSummary submitPublisherVersion(String publisherIdOrSlug,
                                                                       CreateMarketplacePublisherSubmissionRequest request) {
        MarketplacePublisherEntity publisher = marketplacePublisherService.requireVerifiedPublisherAccess(publisherIdOrSlug);
        JsonNode manifestNode = normalizeManifest(request.manifest());
        String pluginId = requiredText(manifestNode, "pluginId");
        String versionLabel = requiredText(manifestNode, "version");
        String pluginType = requiredText(manifestNode, "pluginType", "type").toUpperCase(Locale.ROOT);
        String displayName = firstText(manifestNode, "displayName");
        String description = firstText(manifestNode, "description");
        String pluginSlug = normalizePluginSlug(StringUtils.hasText(request.pluginSlug()) ? request.pluginSlug() : pluginId);

        MarketplacePluginEntity plugin = pluginRepository.findById(pluginId).orElse(null);
        if (plugin == null) {
            pluginRepository.findBySlugIgnoreCase(pluginSlug).ifPresent(existing -> {
                throw new ResponseStatusException(CONFLICT, "Marketplace plugin slug already exists: " + pluginSlug);
            });
            plugin = new MarketplacePluginEntity();
            plugin.setId(pluginId);
            plugin.setSlug(pluginSlug);
            plugin.setDisplayName(StringUtils.hasText(displayName) ? displayName : pluginId);
            plugin.setPluginType(pluginType);
            plugin.setPublisherSlug(publisher.getSlug());
            plugin.setPublisherDisplayName(publisher.getDisplayName());
            plugin.setShortDescription(StringUtils.hasText(description) ? description : (StringUtils.hasText(displayName) ? displayName : pluginId));
            plugin.setStatus("DRAFT");
            plugin.setCreatedAt(Instant.now());
        } else {
            if (!publisher.getSlug().equalsIgnoreCase(plugin.getPublisherSlug())) {
                throw new ResponseStatusException(CONFLICT, "Marketplace plugin is owned by another publisher: " + plugin.getId());
            }
            if (StringUtils.hasText(request.pluginSlug()) && !plugin.getSlug().equalsIgnoreCase(pluginSlug)) {
                throw new ResponseStatusException(CONFLICT, "Marketplace plugin slug does not match the existing plugin slug: " + plugin.getSlug());
            }
        }
        versionRepository.findByPluginIdAndVersion(pluginId, versionLabel)
            .ifPresent(existing -> {
                throw new ResponseStatusException(CONFLICT, "Marketplace plugin version already exists: " + pluginId + "@" + versionLabel);
            });

        MarketplacePluginVersionEntity version = new MarketplacePluginVersionEntity();
        version.setId("mkv-" + UUID.randomUUID().toString().substring(0, 8));
        version.setPluginId(pluginId);
        version.setVersion(versionLabel);
        version.setReleaseChannel(StringUtils.hasText(request.releaseChannel()) ? request.releaseChannel().trim().toUpperCase(Locale.ROOT) : "DRAFT");
        version.setStatus("SUBMITTED");
        version.setManifestJson(writeJson(manifestNode));
        version.setCreatedAt(Instant.now());
        version.setPublishedAt(Instant.now());
        version.setSubmittedByPublisherId(publisher.getId());
        version.setSubmittedByActorId(requirePrincipal().actorId());
        version.setBundleSha256(sha256(version.getManifestJson()));
        manifestService.parseAndValidate(plugin, version);

        plugin.setUpdatedAt(Instant.now());
        pluginRepository.save(plugin);
        versionRepository.save(version);
        platformAuditService.record(
            "MARKETPLACE_SUBMISSION_CREATED",
            "MARKETPLACE_PLUGIN_VERSION",
            version.getId(),
            Map.of(
                "publisherId", publisher.getId(),
                "pluginId", pluginId,
                "version", versionLabel
            )
        );
        return toSubmissionSummary(version);
    }

    @Transactional
    public MarketplacePublisherSubmissionSummary validateSubmission(String pluginVersionId,
                                                                   ReviewMarketplacePublisherSubmissionRequest request) {
        requireAdmin();
        MarketplacePluginVersionEntity version = requireVersion(pluginVersionId);
        if (!"SUBMITTED".equalsIgnoreCase(version.getStatus()) && !"REJECTED".equalsIgnoreCase(version.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Only submitted or rejected marketplace versions can be validated.");
        }
        MarketplacePluginEntity plugin = requirePlugin(version.getPluginId());
        manifestService.parseAndValidate(plugin, version);
        version.setStatus("VALIDATED");
        version.setReviewedAt(Instant.now());
        version.setReviewedByActorId(requirePrincipal().actorId());
        version.setReviewNotes(normalizeReviewNotes(request.reviewNotes()));
        versionRepository.save(version);
        platformAuditService.record(
            "MARKETPLACE_SUBMISSION_VALIDATED",
            "MARKETPLACE_PLUGIN_VERSION",
            version.getId(),
            Map.of("pluginId", version.getPluginId(), "version", version.getVersion())
        );
        return toSubmissionSummary(version);
    }

    @Transactional
    public MarketplacePublisherSubmissionSummary publishSubmission(String pluginVersionId,
                                                                  ReviewMarketplacePublisherSubmissionRequest request) {
        requireAdmin();
        MarketplacePluginVersionEntity version = requireVersion(pluginVersionId);
        if (!"VALIDATED".equalsIgnoreCase(version.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Marketplace plugin version must be VALIDATED before publication.");
        }
        MarketplacePluginEntity plugin = requirePlugin(version.getPluginId());
        MarketplacePublisherEntity publisher = requirePublisher(version.getSubmittedByPublisherId());
        if (!"ACTIVE".equalsIgnoreCase(publisher.getStatus()) || !"VERIFIED".equalsIgnoreCase(publisher.getVerificationStatus())) {
            throw new ResponseStatusException(CONFLICT, "Marketplace publisher must be active and verified before publication.");
        }
        manifestService.parseAndValidate(plugin, version);
        plugin.setStatus("ACTIVE");
        plugin.setUpdatedAt(Instant.now());
        version.setStatus("PUBLISHED");
        version.setPublishedAt(Instant.now());
        version.setReviewedAt(Instant.now());
        version.setReviewedByActorId(requirePrincipal().actorId());
        version.setReviewNotes(normalizeReviewNotes(request.reviewNotes()));
        pluginRepository.save(plugin);
        versionRepository.save(version);
        platformAuditService.record(
            "MARKETPLACE_SUBMISSION_PUBLISHED",
            "MARKETPLACE_PLUGIN_VERSION",
            version.getId(),
            Map.of("pluginId", version.getPluginId(), "version", version.getVersion())
        );
        return toSubmissionSummary(version);
    }

    @Transactional
    public MarketplacePublisherSubmissionSummary rejectSubmission(String pluginVersionId,
                                                                 ReviewMarketplacePublisherSubmissionRequest request) {
        requireAdmin();
        MarketplacePluginVersionEntity version = requireVersion(pluginVersionId);
        if (!List.of("SUBMITTED", "VALIDATED").contains(version.getStatus().toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(CONFLICT, "Only submitted or validated marketplace versions can be rejected.");
        }
        version.setStatus("REJECTED");
        version.setReviewedAt(Instant.now());
        version.setReviewedByActorId(requirePrincipal().actorId());
        version.setReviewNotes(normalizeReviewNotes(request.reviewNotes()));
        versionRepository.save(version);
        platformAuditService.record(
            "MARKETPLACE_SUBMISSION_REJECTED",
            "MARKETPLACE_PLUGIN_VERSION",
            version.getId(),
            Map.of("pluginId", version.getPluginId(), "version", version.getVersion())
        );
        return toSubmissionSummary(version);
    }

    private MarketplacePublisherSubmissionSummary toSubmissionSummary(MarketplacePluginVersionEntity version) {
        MarketplacePluginEntity plugin = requirePlugin(version.getPluginId());
        return new MarketplacePublisherSubmissionSummary(
            version.getId(),
            version.getSubmittedByPublisherId(),
            plugin.getId(),
            plugin.getSlug(),
            plugin.getDisplayName(),
            plugin.getPluginType(),
            version.getVersion(),
            version.getReleaseChannel(),
            version.getStatus(),
            version.getBundleSha256(),
            version.getReviewNotes(),
            readManifest(version.getManifestJson()),
            version.getCreatedAt(),
            version.getSubmittedByActorId(),
            version.getReviewedAt(),
            version.getReviewedByActorId()
        );
    }

    private MarketplacePluginVersionEntity requireVersion(String pluginVersionId) {
        return versionRepository.findById(pluginVersionId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Marketplace plugin version not found: " + pluginVersionId));
    }

    private MarketplacePluginEntity requirePlugin(String pluginId) {
        return pluginRepository.findById(pluginId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Marketplace plugin not found: " + pluginId));
    }

    private MarketplacePublisherEntity requirePublisher(String publisherId) {
        return marketplacePublisherService.requirePublisherAccess(publisherId);
    }

    private JsonNode normalizeManifest(JsonNode manifest) {
        if (manifest == null || !manifest.isObject()) {
            throw new ResponseStatusException(BAD_REQUEST, "Marketplace submission manifest must be a JSON object.");
        }
        return manifest;
    }

    private String requiredText(JsonNode node, String... fields) {
        String value = firstText(node, fields);
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(BAD_REQUEST, "Marketplace submission manifest is missing required field: " + String.join("/", fields));
        }
        return value;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("").trim();
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String normalizePluginSlug(String pluginSlug) {
        return pluginSlug.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-").replaceAll("^-+|-+$", "");
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Failed to write marketplace submission manifest JSON.");
        }
    }

    private JsonNode readManifest(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read marketplace submission manifest.", ex);
        }
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash marketplace submission manifest.", ex);
        }
    }

    private String normalizeReviewNotes(String reviewNotes) {
        return StringUtils.hasText(reviewNotes) ? reviewNotes.trim() : null;
    }

    private PlatformPrincipal requirePrincipal() {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || !StringUtils.hasText(principal.actorId())) {
            throw new ResponseStatusException(FORBIDDEN, "Authenticated platform principal is required.");
        }
        return principal;
    }

    private void requireAdmin() {
        PlatformPrincipal principal = requirePrincipal();
        if (principal.role() != PlatformRole.PLATFORM_ADMIN) {
            throw new ResponseStatusException(FORBIDDEN, "Marketplace submission review requires PLATFORM_ADMIN.");
        }
    }
}
