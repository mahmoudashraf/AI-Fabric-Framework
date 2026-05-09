package com.ai.fabric.platform.backend.partner.audit;

import com.ai.fabric.platform.backend.partner.entity.PartnerActionAuditEntity;
import com.ai.fabric.platform.backend.partner.gateway.PartnerAuditPublisher;
import com.ai.fabric.platform.backend.partner.repository.PartnerActionAuditRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class DatabasePartnerAuditPublisher implements PartnerAuditPublisher {

    private final PartnerActionAuditRepository repository;

    public DatabasePartnerAuditPublisher(PartnerActionAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(String partnerAccountId,
                        String partnerMemberId,
                        String action,
                        String targetType,
                        String targetId,
                        String result,
                        String detailsJson) {
        PartnerActionAuditEntity entity = new PartnerActionAuditEntity();
        entity.setId("paud-" + UUID.randomUUID());
        entity.setPartnerAccountId(partnerAccountId);
        entity.setPartnerMemberId(partnerMemberId);
        entity.setAction(action);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setResult(result);
        entity.setDetailsJson(detailsJson == null || detailsJson.isBlank() ? "{}" : detailsJson);
        entity.setCreatedAt(Instant.now());
        repository.save(entity);
    }
}
