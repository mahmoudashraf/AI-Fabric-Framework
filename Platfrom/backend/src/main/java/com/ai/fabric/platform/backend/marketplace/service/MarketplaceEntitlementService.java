package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginEntitlementEntity;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceEntitlementSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginPricingSummary;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceEntitlementRequest;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginEntitlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class MarketplaceEntitlementService {

    private final DeploymentMarketplacePluginEntitlementRepository entitlementRepository;

    public MarketplaceEntitlementService(DeploymentMarketplacePluginEntitlementRepository entitlementRepository) {
        this.entitlementRepository = entitlementRepository;
    }

    @Transactional
    public DeploymentMarketplacePluginEntitlementEntity ensureEntitlement(DeploymentMarketplacePluginInstallEntity install,
                                                                          MarketplaceManifestService.ParsedMarketplaceManifest parsed) {
        MarketplacePluginPricingSummary pricing = parsed.pricing();
        Instant now = Instant.now();
        DeploymentMarketplacePluginEntitlementEntity entitlement = entitlementRepository.findByInstallId(install.getId())
            .orElseGet(() -> {
                DeploymentMarketplacePluginEntitlementEntity created = new DeploymentMarketplacePluginEntitlementEntity();
                created.setId("mpe-" + UUID.randomUUID().toString().substring(0, 8));
                created.setInstallId(install.getId());
                created.setDeploymentId(install.getDeploymentId());
                created.setPluginId(install.getPluginId());
                created.setPluginVersionId(install.getPluginVersionId());
                created.setCreatedAt(now);
                return created;
            });
        entitlement.setDeploymentId(install.getDeploymentId());
        entitlement.setPluginId(install.getPluginId());
        entitlement.setPluginVersionId(install.getPluginVersionId());
        entitlement.setPricingModel(pricing.pricingModel());
        entitlement.setAmount(pricing.amount());
        entitlement.setCurrency(pricing.currency());
        entitlement.setBillingInterval(pricing.billingInterval());
        if (!StringUtils.hasText(entitlement.getStatus())) {
            entitlement.setStatus(defaultStatus(pricing));
        }
        if ("FREE".equals(pricing.pricingModel())) {
            entitlement.setStatus("ACTIVE");
            entitlement.setGraceEndsAt(null);
            entitlement.setAccessEndsAt(null);
            if (!StringUtils.hasText(entitlement.getNote())) {
                entitlement.setNote("Free marketplace plugin.");
            }
        }
        entitlement.setUpdatedAt(now);
        return entitlementRepository.save(entitlement);
    }

    @Transactional
    public DeploymentMarketplacePluginEntitlementEntity updateEntitlement(DeploymentMarketplacePluginInstallEntity install,
                                                                          MarketplaceManifestService.ParsedMarketplaceManifest parsed,
                                                                          UpdateDeploymentMarketplaceEntitlementRequest request) {
        DeploymentMarketplacePluginEntitlementEntity entitlement = ensureEntitlement(install, parsed);
        String status = normalizeStatus(request.status(), entitlement.getPricingModel());
        entitlement.setStatus(status);
        entitlement.setGraceEndsAt(request.graceEndsAt());
        entitlement.setAccessEndsAt(request.accessEndsAt());
        entitlement.setNote(normalizeNote(request.note()));
        entitlement.setUpdatedAt(Instant.now());
        return entitlementRepository.save(entitlement);
    }

    public EntitlementEvaluation evaluate(MarketplaceManifestService.ParsedMarketplaceManifest parsed,
                                          DeploymentMarketplacePluginEntitlementEntity entitlement) {
        MarketplacePluginPricingSummary pricing = parsed.pricing();
        DeploymentMarketplacePluginEntitlementEntity resolved = entitlement;
        if (resolved == null) {
            List<String> warnings = pricing.requiresEntitlement()
                ? List.of("Entitlement record is missing for paid marketplace install.")
                : List.of();
            return new EntitlementEvaluation(
                new DeploymentMarketplaceEntitlementSummary(
                    pricing.pricingModel(),
                    pricing.amount(),
                    pricing.currency(),
                    pricing.billingInterval(),
                    pricing.requiresEntitlement() ? "PENDING" : "ACTIVE",
                    pricing.requiresEntitlement(),
                    !pricing.requiresEntitlement(),
                    null,
                    null,
                    pricing.requiresEntitlement() ? "Install requires entitlement activation." : "Free marketplace plugin.",
                    null
                ),
                !pricing.requiresEntitlement(),
                warnings
            );
        }
        String status = normalizeStoredStatus(resolved.getStatus());
        boolean entitled = false;
        List<String> warnings = new ArrayList<>();
        switch (status) {
            case "ACTIVE" -> entitled = true;
            case "PAST_DUE" -> {
                entitled = true;
                warnings.add("Entitlement is past due and currently in grace period.");
            }
            case "CANCELLED" -> {
                if (resolved.getAccessEndsAt() != null && resolved.getAccessEndsAt().isAfter(Instant.now())) {
                    entitled = true;
                    warnings.add("Entitlement is cancelled and will expire at the end of the current access window.");
                } else {
                    warnings.add("Entitlement is cancelled and no longer grants marketplace access.");
                }
            }
            case "SUSPENDED" -> warnings.add("Entitlement is suspended and the marketplace install is excluded from draft compilation.");
            case "PENDING" -> warnings.add("Entitlement is pending activation before the marketplace install can go live.");
            default -> warnings.add("Unknown entitlement state '" + status + "' blocks marketplace compilation.");
        }
        return new EntitlementEvaluation(
            new DeploymentMarketplaceEntitlementSummary(
                resolved.getPricingModel(),
                resolved.getAmount(),
                resolved.getCurrency(),
                resolved.getBillingInterval(),
                status,
                pricing.requiresEntitlement(),
                entitled,
                resolved.getGraceEndsAt(),
                resolved.getAccessEndsAt(),
                normalizeNote(resolved.getNote()),
                resolved.getUpdatedAt()
            ),
            entitled,
            List.copyOf(warnings)
        );
    }

    public DeploymentMarketplacePluginEntitlementEntity findByInstallId(String installId) {
        return entitlementRepository.findByInstallId(installId).orElse(null);
    }

    private String defaultStatus(MarketplacePluginPricingSummary pricing) {
        return pricing.requiresEntitlement() ? "PENDING" : "ACTIVE";
    }

    private String normalizeStatus(String status, String pricingModel) {
        String normalized = normalizeStoredStatus(status);
        if (!List.of("PENDING", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELLED").contains(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported entitlement status: " + status);
        }
        if ("FREE".equals(pricingModel) && !"ACTIVE".equals(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "Free marketplace plugins must remain ACTIVE.");
        }
        if ("ONE_OFF".equals(pricingModel) && "PAST_DUE".equals(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "One-off marketplace plugins do not support PAST_DUE entitlement state.");
        }
        return normalized;
    }

    private String normalizeStoredStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNote(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record EntitlementEvaluation(
        DeploymentMarketplaceEntitlementSummary summary,
        boolean entitledForCompilation,
        List<String> warnings
    ) {
    }
}
