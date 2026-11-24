package com.ai.fabric.platform.backend.partner.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.partner.repository.PartnerAccountRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerMemberRepository;
import com.ai.fabric.platform.backend.partner.repository.PartnerStoreAssignmentRepository;
import com.ai.fabric.platform.backend.partner.security.PartnerSecurityContext;
import com.ai.fabric.platform.backend.partner.security.PartnerForbiddenException;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rechecks the existing store delegation, without granting Console resource permissions. */
@Service
public class PartnerRuntimeAuthorization {
    private final PartnerMemberRepository members;
    private final PartnerAccountRepository accounts;
    private final PartnerStoreAssignmentRepository assignments;
    private final ShopifyStoreConnectionRepository stores;
    private final ObjectMapper mapper;

    public PartnerRuntimeAuthorization(PartnerMemberRepository members, PartnerAccountRepository accounts,
        PartnerStoreAssignmentRepository assignments, ShopifyStoreConnectionRepository stores, ObjectMapper mapper) {
        this.members = members; this.accounts = accounts; this.assignments = assignments; this.stores = stores; this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Access authorize(DeploymentEntity deployment) {
        var principal = PartnerSecurityContext.currentPrincipalOrThrow();
        if (!principal.provisioned() || !principal.emailVerified()) throw denied();
        var member = members.findById(principal.partnerMemberId()).orElseThrow(PartnerRuntimeAuthorization::denied);
        if (!"ACTIVE".equals(member.getStatus()) || !Objects.equals(principal.partnerAccountId(), member.getPartnerAccountId())
            || !Objects.equals(principal.supabaseUserId(), member.getSupabaseUserId())) throw denied();
        var account = accounts.findById(member.getPartnerAccountId()).orElseThrow(PartnerRuntimeAuthorization::denied);
        if (!"ACTIVE".equals(account.getStatus())) throw denied();
        var store = stores.findByDeploymentId(deployment.getId()).orElseThrow(PartnerRuntimeAuthorization::denied);
        if (!"INSTALLED".equalsIgnoreCase(store.getInstallStatus()) || deployment.getCustomerId() == null
            || !Objects.equals(deployment.getCustomerId(), store.getCustomerId())) throw denied();
        var assignment = assignments.findByPartnerAccountIdAndStoreConnectionId(account.getId(), store.getId())
            .orElseThrow(PartnerRuntimeAuthorization::denied);
        if (!"ACTIVE".equals(assignment.getStatus()) || assignment.getRevokedAt() != null || assignment.getSuspendedAt() != null
            || !Objects.equals(store.getShopDomain(), assignment.getShopDomain())) throw denied();
        try {
            var permissions = mapper.readTree(assignment.getPermissionsJson());
            if (!permissions.isArray() || java.util.stream.StreamSupport.stream(permissions.spliterator(), false)
                .noneMatch(value -> value.isTextual() && "PRODUCT_CONFIG_READ".equals(value.textValue()))) throw denied();
            String identity = account.getId() + "|" + member.getId() + "|" + principal.supabaseUserId() + "|" + deployment.getId();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
            return new Access("partner-member:" + member.getId(), "partner-preview-" + hash);
        } catch (PartnerForbiddenException denial) { throw denial; }
        catch (Exception failure) { throw denied(); }
    }

    private static PartnerForbiddenException denied() { return new PartnerForbiddenException("Partner runtime preview is not available for this store."); }
    public record Access(String subjectId, String sessionId) { }
}
