package com.ai.fabric.platform.backend.security.repository;

import com.ai.fabric.platform.backend.security.entity.PlatformCustomerIdentityLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlatformCustomerIdentityLinkRepository extends JpaRepository<PlatformCustomerIdentityLinkEntity, String> {
    Optional<PlatformCustomerIdentityLinkEntity> findByIssuerAndSubject(String issuer, String subject);
    Optional<PlatformCustomerIdentityLinkEntity> findByPlatformUserId(String userId);
    boolean existsByPlatformUserId(String userId);
}
