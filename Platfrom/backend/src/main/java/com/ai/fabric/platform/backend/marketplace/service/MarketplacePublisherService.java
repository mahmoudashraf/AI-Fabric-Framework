package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePublisherEntity;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplacePublisherRequest;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePublisherSummary;
import com.ai.fabric.platform.backend.marketplace.model.UpdateMarketplacePublisherVerificationRequest;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePublisherRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class MarketplacePublisherService {

    private final MarketplacePublisherRepository publisherRepository;
    private final MarketplacePluginVersionRepository versionRepository;
    private final PlatformAuditService platformAuditService;

    public MarketplacePublisherService(MarketplacePublisherRepository publisherRepository,
                                       MarketplacePluginVersionRepository versionRepository,
                                       PlatformAuditService platformAuditService) {
        this.publisherRepository = publisherRepository;
        this.versionRepository = versionRepository;
        this.platformAuditService = platformAuditService;
    }

    public List<MarketplacePublisherSummary> listPublishers() {
        PlatformPrincipal principal = requirePrincipal();
        List<MarketplacePublisherEntity> publishers = principal.role() == PlatformRole.PLATFORM_ADMIN
            ? publisherRepository.findAll()
            : publisherRepository.findByOwnerUserIdOrderByCreatedAtDesc(principal.actorId());
        return publishers.stream()
            .sorted(Comparator.comparing(MarketplacePublisherEntity::getDisplayName, String.CASE_INSENSITIVE_ORDER))
            .map(this::toSummary)
            .toList();
    }

    public MarketplacePublisherSummary getPublisher(String publisherIdOrSlug) {
        return toSummary(requirePublisherAccess(publisherIdOrSlug));
    }

    @Transactional
    public MarketplacePublisherSummary createPublisher(CreateMarketplacePublisherRequest request) {
        PlatformPrincipal principal = requirePrincipal();
        String slug = normalizeSlug(request.slug());
        if (!StringUtils.hasText(slug)) {
            throw new ResponseStatusException(BAD_REQUEST, "Publisher slug is required.");
        }
        if (!StringUtils.hasText(request.displayName())) {
            throw new ResponseStatusException(BAD_REQUEST, "Publisher displayName is required.");
        }
        if (!StringUtils.hasText(request.contactEmail())) {
            throw new ResponseStatusException(BAD_REQUEST, "Publisher contactEmail is required.");
        }
        publisherRepository.findBySlugIgnoreCase(slug)
            .ifPresent(existing -> {
                throw new ResponseStatusException(CONFLICT, "Marketplace publisher slug already exists: " + slug);
            });
        Instant now = Instant.now();
        MarketplacePublisherEntity publisher = new MarketplacePublisherEntity();
        publisher.setId("mpub-" + UUID.randomUUID().toString().substring(0, 8));
        publisher.setSlug(slug);
        publisher.setDisplayName(request.displayName().trim());
        publisher.setContactEmail(request.contactEmail().trim().toLowerCase(Locale.ROOT));
        publisher.setOwnerUserId(principal.actorId());
        publisher.setVerificationStatus("PENDING");
        publisher.setStatus("ACTIVE");
        publisher.setCreatedAt(now);
        publisher.setUpdatedAt(now);
        publisherRepository.save(publisher);
        platformAuditService.record(
            "MARKETPLACE_PUBLISHER_CREATED",
            "MARKETPLACE_PUBLISHER",
            publisher.getId(),
            java.util.Map.of(
                "publisherSlug", publisher.getSlug(),
                "ownerUserId", publisher.getOwnerUserId()
            )
        );
        return toSummary(publisher);
    }

    @Transactional
    public MarketplacePublisherSummary updatePublisherVerification(String publisherIdOrSlug,
                                                                  UpdateMarketplacePublisherVerificationRequest request) {
        requireAdmin();
        MarketplacePublisherEntity publisher = requirePublisherInternal(publisherIdOrSlug);
        if (StringUtils.hasText(request.verificationStatus())) {
            publisher.setVerificationStatus(normalizeVerificationStatus(request.verificationStatus()));
        }
        if (StringUtils.hasText(request.status())) {
            publisher.setStatus(normalizePublisherStatus(request.status()));
        }
        publisher.setUpdatedAt(Instant.now());
        publisherRepository.save(publisher);
        platformAuditService.record(
            "MARKETPLACE_PUBLISHER_VERIFICATION_UPDATED",
            "MARKETPLACE_PUBLISHER",
            publisher.getId(),
            java.util.Map.of(
                "verificationStatus", publisher.getVerificationStatus(),
                "status", publisher.getStatus()
            )
        );
        return toSummary(publisher);
    }

    MarketplacePublisherEntity requirePublisherAccess(String publisherIdOrSlug) {
        PlatformPrincipal principal = requirePrincipal();
        MarketplacePublisherEntity publisher = requirePublisherInternal(publisherIdOrSlug);
        if (principal.role() == PlatformRole.PLATFORM_ADMIN || publisher.getOwnerUserId().equals(principal.actorId())) {
            return publisher;
        }
        throw new ResponseStatusException(FORBIDDEN, "Publisher access is restricted to its owner or platform admin.");
    }

    MarketplacePublisherEntity requireVerifiedPublisherAccess(String publisherIdOrSlug) {
        MarketplacePublisherEntity publisher = requirePublisherAccess(publisherIdOrSlug);
        if (!"ACTIVE".equalsIgnoreCase(publisher.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Marketplace publisher is not active: " + publisher.getSlug());
        }
        if (!"VERIFIED".equalsIgnoreCase(publisher.getVerificationStatus())) {
            throw new ResponseStatusException(CONFLICT, "Marketplace publisher is not verified: " + publisher.getSlug());
        }
        return publisher;
    }

    private MarketplacePublisherEntity requirePublisherInternal(String publisherIdOrSlug) {
        return publisherRepository.findById(publisherIdOrSlug)
            .or(() -> publisherRepository.findBySlugIgnoreCase(publisherIdOrSlug))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Marketplace publisher not found: " + publisherIdOrSlug));
    }

    private MarketplacePublisherSummary toSummary(MarketplacePublisherEntity publisher) {
        int submissionCount = versionRepository.findBySubmittedByPublisherIdOrderByCreatedAtDesc(publisher.getId()).size();
        return new MarketplacePublisherSummary(
            publisher.getId(),
            publisher.getSlug(),
            publisher.getDisplayName(),
            publisher.getContactEmail(),
            publisher.getOwnerUserId(),
            publisher.getVerificationStatus(),
            publisher.getStatus(),
            submissionCount,
            publisher.getCreatedAt(),
            publisher.getUpdatedAt()
        );
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
            throw new ResponseStatusException(FORBIDDEN, "Marketplace publisher review requires PLATFORM_ADMIN.");
        }
    }

    private String normalizeSlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            return "";
        }
        return slug.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-").replaceAll("^-+|-+$", "");
    }

    private String normalizeVerificationStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("PENDING", "VERIFIED", "REJECTED").contains(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "verificationStatus must be PENDING, VERIFIED, or REJECTED.");
        }
        return normalized;
    }

    private String normalizePublisherStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "SUSPENDED").contains(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "status must be ACTIVE or SUSPENDED.");
        }
        return normalized;
    }
}
