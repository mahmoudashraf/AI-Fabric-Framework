package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerPlacementEvidenceRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApiClient;
import com.ai.fabric.platform.backend.deployment.service.CoolifyTargetProfileResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;

@Service
public class CustomerTargetPlacementService {
    private final CustomerSelfServiceProperties properties;
    private final CoolifyTargetProfileResolver resolver;
    private final CoolifyApiClient client;
    private final CustomerReviewedCompositionService composition;
    private final CustomerOfferCodec codec;
    private final CustomerPlacementEvidenceRepository evidenceRepository;

    public CustomerTargetPlacementService(CustomerSelfServiceProperties properties,
                                         CoolifyTargetProfileResolver resolver, CoolifyApiClient client,
                                         CustomerReviewedCompositionService composition, CustomerOfferCodec codec,
                                         CustomerPlacementEvidenceRepository evidenceRepository) {
        this.properties = properties;
        this.resolver = resolver;
        this.client = client;
        this.composition = composition;
        this.codec = codec;
        this.evidenceRepository = evidenceRepository;
    }

    /** Reject an ambient transaction; suspending one would still leave its locks held during HTTP. */
    @Transactional(propagation = Propagation.NEVER)
    public CustomerPlacementEvidenceEntity probeAndRecord(DeploymentTargetProfileEntity profile, TargetIntent intent) {
        var trusted = trusted(intent);
        String fingerprint = composition.profileFingerprint(profile);
        if (profile.getProviderType() != DeploymentProviderType.COOLIFY || !profile.isActive()) throw conflict(PLACEMENT_DENIED);
        try {
            var connection = resolver.requireConnection(profile);
            if (!trusted.uuid().equals(connection.config().serverUuid()) || blank(connection.config().destinationUuid())) throw conflict(PLACEMENT_DENIED);
            var preflight = resolver.preflight(profile);
            Instant now = Instant.now();
            if (preflight == null || !"PASSED".equals(preflight.status())
                || !profile.getId().equals(preflight.targetProfileId())
                || preflight.providerType() != DeploymentProviderType.COOLIFY || preflight.checkedAt() == null
                || preflight.checkedAt().isAfter(now) || preflight.checkedAt().isBefore(now.minusSeconds(properties.evidenceMaxAgeSeconds()))
                || preflight.details() == null || preflight.checks() == null
                || !preflight.checks().contains("destination_server_matches")
                || !trusted.uuid().equals(preflight.details().path("serverUuid").asText())
                || !trusted.uuid().equals(preflight.details().path("destinationServerUuid").asText())
                || !connection.config().destinationUuid().equals(preflight.details().path("destinationUuid").asText())
                || !preflight.details().path("serverReachable").asBoolean(false)
                || !preflight.details().path("serverUsable").asBoolean(false)) throw conflict(PREFLIGHT_REQUIRED);
            var server = client.getServer(connection, trusted.uuid());
            if (server == null || !trusted.uuid().equals(server.path("uuid").asText())
                || !trusted.address().equals(server.path("ip").asText())
                || !server.path("settings").path("is_reachable").asBoolean(false)
                || !server.path("settings").path("is_usable").asBoolean(false)) throw conflict(PLACEMENT_DENIED);
            Instant expiresAt = preflight.checkedAt().plusSeconds(properties.evidenceMaxAgeSeconds());
            if (!expiresAt.isAfter(Instant.now())) throw conflict(PLACEMENT_EVIDENCE_STALE);
            // saveAndFlush opens only the short repository transaction, after every remote call has finished.
            return evidenceRepository.saveAndFlush(new CustomerPlacementEvidenceEntity("cpe-" + UUID.randomUUID(), profile.getId(), intent,
                fingerprint, trusted.uuid(), trusted.address(), connection.config().destinationUuid(), preflight.checkedAt(), expiresAt));
        } catch (com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException ex) { throw ex; }
        catch (RuntimeException ex) { throw conflict(PREFLIGHT_REQUIRED); }
    }

    /** Pure database/config validation, safe to use while the owning command holds entitlement locks. */
    public CustomerPlacementEvidenceEntity requireFreshEvidence(DeploymentTargetProfileEntity profile, TargetIntent intent) {
        trusted(intent);
        String fingerprint = composition.profileFingerprint(profile);
        var evidence = evidenceRepository.findFirstByTargetProfileIdAndIntentAndProfileFingerprintOrderByCheckedAtDesc(profile.getId(), intent, fingerprint)
            .orElseThrow(() -> conflict(PLACEMENT_EVIDENCE_REQUIRED));
        return validateEvidence(profile, intent, fingerprint, evidence);
    }

    public CustomerPlacementEvidenceEntity requireEvidenceById(DeploymentTargetProfileEntity profile, TargetIntent intent, String id) {
        if (blank(id)) throw conflict(PLACEMENT_EVIDENCE_REQUIRED);
        var evidence = evidenceRepository.findById(id).orElseThrow(() -> conflict(PLACEMENT_EVIDENCE_REQUIRED));
        return validateEvidence(profile, intent, composition.profileFingerprint(profile), evidence);
    }

    private CustomerPlacementEvidenceEntity validateEvidence(DeploymentTargetProfileEntity profile, TargetIntent intent,
        String fingerprint, CustomerPlacementEvidenceEntity evidence) {
        var trusted = trusted(intent);
        var config = codec.tree(profile.getProviderConfigJson());
        if (!profile.isActive() || profile.getProviderType() != DeploymentProviderType.COOLIFY
            || !profile.getId().equals(evidence.getTargetProfileId()) || intent != evidence.getIntent()
            || !fingerprint.equals(evidence.getProfileFingerprint()) || !trusted.uuid().equals(evidence.getServerUuid())
            || !trusted.address().equals(evidence.getServerAddress()) || !trusted.uuid().equals(config.path("serverUuid").asText())
            || blank(evidence.getDestinationUuid()) || !evidence.getDestinationUuid().equals(config.path("destinationUuid").asText())) {
            throw conflict(PLACEMENT_DENIED);
        }
        Instant now = Instant.now();
        if (evidence.getCheckedAt() == null || evidence.getExpiresAt() == null || evidence.getCheckedAt().isAfter(now)
            || evidence.getCheckedAt().isBefore(now.minusSeconds(properties.evidenceMaxAgeSeconds()))
            || !evidence.getExpiresAt().isAfter(now)) throw conflict(PLACEMENT_EVIDENCE_STALE);
        return evidence;
    }

    private CustomerSelfServiceProperties.TrustedServer trusted(TargetIntent intent) {
        var trusted = properties.trustedServers().get(intent);
        var other = properties.trustedServers().get(intent == TargetIntent.CUSTOMER_STAGING
            ? TargetIntent.CUSTOMER_PRODUCTION : TargetIntent.CUSTOMER_STAGING);
        if (trusted == null || other == null || blank(trusted.uuid()) || blank(trusted.address())
            || blank(other.uuid()) || blank(other.address()) || trusted.uuid().equals(other.uuid())
            || trusted.address().equals(other.address()) || properties.evidenceMaxAgeSeconds() <= 0) {
            throw conflict(TRUSTED_PLACEMENT_REQUIRED);
        }
        return trusted;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
