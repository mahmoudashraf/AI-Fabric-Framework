package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.*;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;

@Service
public class CustomerOfferEntitlementService {
    private final CustomerOfferRepository offers;
    private final CustomerOfferEntitlementRepository entitlements;
    private final CustomerEntitlementCommandRepository commands;
    private final PlatformCustomerAccessService customerAccess;
    private final PlatformCustomerRepository customers;
    private final PlatformUserRepository users;
    private final CustomerOfferCodec codec;
    private final CustomerReviewedCompositionService composition;
    private final CustomerTargetPlacementService placement;
    private final com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository profiles;
    private final PlatformAuditService audit;

    public CustomerOfferEntitlementService(CustomerOfferRepository offers,
        CustomerOfferEntitlementRepository entitlements, CustomerEntitlementCommandRepository commands,
        PlatformCustomerAccessService customerAccess, PlatformCustomerRepository customers,
        PlatformUserRepository users, CustomerOfferCodec codec, CustomerReviewedCompositionService composition,
        CustomerTargetPlacementService placement,
        com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository profiles,
        PlatformAuditService audit) {
        this.offers = offers;
        this.entitlements = entitlements;
        this.commands = commands;
        this.customerAccess = customerAccess;
        this.customers = customers;
        this.users = users;
        this.codec = codec;
        this.composition = composition;
        this.placement = placement;
        this.profiles = profiles;
        this.audit = audit;
    }

    @Transactional
    public String publish(PublishOfferRequest request) {
        requireStaffAdmin();
        if (request == null || blank(request.offerId(), 64) || blank(request.reviewReference(), 255)) throw invalid();
        composition.validate(request.definition());
        String hash = codec.hash(request.definition());
        if (offers.existsById(request.offerId())) throw conflict(OFFER_IMMUTABLE);
        for (TargetPermission permission : request.definition().targets()) {
            placement.requireFreshEvidence(profiles.findById(permission.targetProfileId()).orElseThrow(() -> conflict(UNENTITLED_TARGET)),
                permission.intent());
        }
        offers.saveAndFlush(new CustomerOfferEntity(request.offerId(), codec.write(request.definition()), hash,
            request.reviewReference(), PlatformSecurityContext.currentPrincipal().actorId(), Instant.now()));
        audit.record("CUSTOMER_OFFER_REVIEWED", "CUSTOMER_OFFER", request.offerId(),
            Map.of("fingerprint", hash, "reviewReference", request.reviewReference()));
        return hash;
    }

    @Transactional
    public EntitlementSummary invite(String offerId, InviteRequest request) {
        requireStaffAdmin();
        if (request == null || blank(request.customerId(), 64) || blank(request.ownerUserId(), 64)
            || blank(request.invitationReference(), 255) || blank(request.reason(), 1000)
            || request.expectedRevision() < 0 || request.expiresAt() == null) throw invalid();
        requireOperationKey(request.operationKey());
        requireActiveCustomer(request.customerId());
        requireOwner(request.ownerUserId(), request.customerId());
        CustomerOfferEntity offer = offers.findByIdForUpdate(offerId).orElseThrow(() -> deny(ENTITLEMENT_REQUIRED));
        var entity = entitlements.findForUpdate(request.customerId(), offerId).orElse(null);
        String fingerprint = commandFingerprint("INVITE", request);
        if (entity != null) {
            var replay = replay(entity, request.operationKey(), fingerprint);
            if (replay != null) return replay;
        }
        if (!request.expiresAt().isAfter(Instant.now())) throw invalid();
        if (request.expectedRevision() != (entity == null ? 0 : entity.getRevision())) throw conflict(REVISION_CONFLICT);
        composition.validate(definition(offer));
        if (entity == null) {
            entity = new CustomerOfferEntitlementEntity();
            entity.setId("cse-" + UUID.randomUUID());
            entity.setCustomerId(request.customerId());
            entity.setOfferId(offerId);
            entity.setCreatedAt(Instant.now());
        }
        entity.setOwnerUserId(request.ownerUserId());
        entity.setInvitationReference(request.invitationReference());
        entity.setExpiresAt(request.expiresAt());
        entity.setStatus(EntitlementStatus.INVITED);
        entity.setRevision(entity.getRevision() + 1);
        entity.setUpdatedAt(Instant.now());
        entitlements.saveAndFlush(entity);
        return record(entity, "INVITE", request.operationKey(), fingerprint, request.reason());
    }

    @Transactional
    public EntitlementSummary accept(String entitlementId, EntitlementCommand request) {
        CustomerActor actor = requireCustomerActor();
        validateCommand(request);
        var entity = entitlements.findByIdForUpdate(entitlementId).orElseThrow(() -> deny(ENTITLEMENT_REQUIRED));
        if (!actor.customerId().equals(entity.getCustomerId()) || !actor.userId().equals(entity.getOwnerUserId())) {
            throw deny(INVITE_OWNER_MISMATCH);
        }
        String fingerprint = commandFingerprint("ACCEPT", request);
        var replay = replay(entity, request.operationKey(), fingerprint);
        if (replay != null) return replay;
        if (!entity.getExpiresAt().isAfter(Instant.now())) throw deny(ENTITLEMENT_EXPIRED);
        if (entity.getRevision() != request.expectedRevision()) throw conflict(REVISION_CONFLICT);
        if (entity.getStatus() != EntitlementStatus.INVITED) throw deny(OPERATION_FORBIDDEN);
        composition.validate(definition(requireOffer(entity.getOfferId())));
        entity.setStatus(EntitlementStatus.ACTIVE);
        entity.setRevision(entity.getRevision() + 1);
        entity.setUpdatedAt(Instant.now());
        entitlements.saveAndFlush(entity);
        return record(entity, "ACCEPT", request.operationKey(), fingerprint, request.reason());
    }

    @Transactional
    public EntitlementSummary revoke(String entitlementId, EntitlementCommand request) {
        requireStaffAdmin();
        validateCommand(request);
        var entity = entitlements.findByIdForUpdate(entitlementId).orElseThrow(() -> deny(ENTITLEMENT_REQUIRED));
        String fingerprint = commandFingerprint("REVOKE", request);
        var replay = replay(entity, request.operationKey(), fingerprint);
        if (replay != null) return replay;
        if (entity.getRevision() != request.expectedRevision()) throw conflict(REVISION_CONFLICT);
        if (entity.getStatus() == EntitlementStatus.REVOKED) throw conflict(REVISION_CONFLICT);
        entity.setStatus(EntitlementStatus.REVOKED);
        entity.setRevision(entity.getRevision() + 1);
        entity.setUpdatedAt(Instant.now());
        entitlements.saveAndFlush(entity);
        return record(entity, "REVOKE", request.operationKey(), fingerprint, request.reason());
    }

    @Transactional(readOnly = true)
    public List<EntitlementSummary> myInvitations() {
        var actor = requireCustomerActor();
        return entitlements.findByCustomerIdOrderByCreatedAtAsc(actor.customerId()).stream()
            .filter(entity -> actor.userId().equals(entity.getOwnerUserId())).map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public List<EntitlementSummary> staffEntitlements(String customerId) {
        requireStaffAdmin();
        return entitlements.findByCustomerIdOrderByCreatedAtAsc(customerId).stream().map(this::summary).toList();
    }

    public CustomerActor requireCustomerActor() {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() != PlatformRole.CUSTOMER_ADMIN
            || !"SESSION".equals(principal.authenticationMode())) throw deny(ACTIVE_CUSTOMER_SCOPE_REQUIRED);
        PlatformCustomerAccessService.CustomerScopeSummary scope;
        try { scope = customerAccess.currentScope(principal); }
        catch (ResponseStatusException ex) { throw deny(ACTIVE_CUSTOMER_SCOPE_REQUIRED); }
        if (scope == null || blank(scope.customerId(), 64)) throw deny(ACTIVE_CUSTOMER_SCOPE_REQUIRED);
        var user = users.findByEmailIgnoreCase(principal.actorId()).orElseThrow(() -> deny(ACTIVE_CUSTOMER_SCOPE_REQUIRED));
        if (!"ACTIVE".equals(user.getStatus()) || !"CUSTOMER_ADMIN".equals(user.getRole())
            || !scope.customerId().equals(user.getCustomerId())) throw deny(ACTIVE_CUSTOMER_SCOPE_REQUIRED);
        requireActiveCustomer(scope.customerId());
        return new CustomerActor(scope.customerId(), user.getId());
    }

    public void requireStaffAdmin() {
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() != PlatformRole.PLATFORM_ADMIN) throw deny(STAFF_ADMIN_REQUIRED);
        requireActiveStaff(principal);
    }

    public void requireActiveStaff(PlatformPrincipal principal) {
        if (principal == null || (principal.role() != PlatformRole.PLATFORM_ADMIN
            && principal.role() != PlatformRole.PLATFORM_OPERATOR)) throw deny(OPERATION_FORBIDDEN);
        if ("API_KEY".equals(principal.authenticationMode())) return;
        if (!"SESSION".equals(principal.authenticationMode())) throw deny(OPERATION_FORBIDDEN);
        var user = users.findByEmailIgnoreCase(principal.actorId()).orElseThrow(() -> deny(OPERATION_FORBIDDEN));
        if (!"ACTIVE".equals(user.getStatus()) || !principal.role().name().equals(user.getRole())) throw deny(OPERATION_FORBIDDEN);
    }

    public void requireActiveCustomer(String customerId) {
        if (blank(customerId, 64) || customers.findById(customerId)
            .filter(customer -> "ACTIVE".equals(customer.getStatus())).isEmpty()) throw deny(ACTIVE_CUSTOMER_SCOPE_REQUIRED);
    }

    private PlatformUserEntity requireOwner(String ownerId, String customerId) {
        return users.findById(ownerId).filter(user -> "ACTIVE".equals(user.getStatus())
            && "CUSTOMER_ADMIN".equals(user.getRole()) && customerId.equals(user.getCustomerId()))
            .orElseThrow(() -> deny(INVITE_OWNER_MISMATCH));
    }

    public CustomerOfferEntitlementEntity requireEntitlement(String customerId, String offerId, String ownerId, boolean lock) {
        requireActiveCustomer(customerId);
        var entity = (lock ? entitlements.findForUpdate(customerId, offerId)
            : entitlements.findByCustomerIdAndOfferId(customerId, offerId)).orElseThrow(() -> deny(ENTITLEMENT_REQUIRED));
        requireOwner(entity.getOwnerUserId(), customerId);
        if (ownerId != null && !ownerId.equals(entity.getOwnerUserId())) throw deny(INVITE_OWNER_MISMATCH);
        if (entity.getStatus() == EntitlementStatus.REVOKED) throw deny(ENTITLEMENT_REVOKED);
        if (!entity.getExpiresAt().isAfter(Instant.now())) throw deny(ENTITLEMENT_EXPIRED);
        if (entity.getStatus() != EntitlementStatus.ACTIVE) throw deny(ENTITLEMENT_REQUIRED);
        return entity;
    }

    public CustomerOfferEntity requireOffer(String id) {
        if (blank(id, 64)) throw invalid();
        return offers.findById(id).orElseThrow(() -> deny(ENTITLEMENT_REQUIRED));
    }

    public Definition definition(CustomerOfferEntity offer) {
        Definition definition = codec.read(offer.getDefinitionJson(), Definition.class);
        if (!offer.getFingerprint().equals(codec.hash(definition))) throw conflict(REVIEWED_COMPOSITION_CHANGED);
        return definition;
    }

    public EntitlementSummary summary(CustomerOfferEntitlementEntity entity) {
        return new EntitlementSummary(entity.getId(), entity.getOfferId(), entity.getCustomerId(), entity.getOwnerUserId(),
            entity.getStatus(), entity.getRevision(), entity.getExpiresAt());
    }

    private EntitlementSummary record(CustomerOfferEntitlementEntity entity, String type, String key, String fingerprint, String reason) {
        EntitlementSummary result = summary(entity);
        commands.save(new CustomerEntitlementCommandEntity("cec-" + UUID.randomUUID(), entity.getId(), key, fingerprint,
            type, PlatformSecurityContext.currentPrincipal().actorId(), codec.write(result), Instant.now()));
        audit.record("CUSTOMER_OFFER_" + type, "CUSTOMER_OFFER_ENTITLEMENT", entity.getId(),
            Map.of("customerId", entity.getCustomerId(), "offerId", entity.getOfferId(), "ownerUserId", entity.getOwnerUserId(),
                "revision", entity.getRevision(), "operationKey", key, "reason", reason));
        return result;
    }

    private EntitlementSummary replay(CustomerOfferEntitlementEntity entity, String key, String fingerprint) {
        var previous = commands.findByEntitlementIdAndOperationKey(entity.getId(), key).orElse(null);
        if (previous == null) return null;
        if (!fingerprint.equals(previous.getRequestFingerprint())) throw conflict(OPERATION_KEY_CONFLICT);
        return codec.read(previous.getResultJson(), EntitlementSummary.class);
    }

    private String commandFingerprint(String command, Object request) {
        return codec.hash(Map.of("command", command, "request", request, "actor", PlatformSecurityContext.currentPrincipal().actorId()));
    }
    private void validateCommand(EntitlementCommand request) {
        if (request == null || request.expectedRevision() < 0 || blank(request.reason(), 1000)) throw invalid();
        requireOperationKey(request.operationKey());
    }
    public void requireOperationKey(String key) { if (blank(key, 128)) throw invalid(); }
    private boolean blank(String value, int limit) { return value == null || value.isBlank() || value.length() > limit || !value.equals(value.trim()); }
    public record CustomerActor(String customerId, String userId) { }
}
