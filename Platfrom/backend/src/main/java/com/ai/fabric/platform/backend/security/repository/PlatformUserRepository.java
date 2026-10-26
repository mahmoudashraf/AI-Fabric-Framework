package com.ai.fabric.platform.backend.security.repository;

import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformUserRepository extends JpaRepository<PlatformUserEntity, String> {

    static Optional<PlatformUserEntity> findForPrincipal(PlatformUserRepository repository, com.ai.fabric.platform.backend.security.PlatformPrincipal principal) {
        if (principal == null) return Optional.empty();
        if (principal.platformUserId() != null) return repository.findById(principal.platformUserId());
        // Supabase identities must never fall back to an email-based account lookup.
        if ("SUPABASE".equals(principal.authenticationMode())) return Optional.empty();
        return repository.findByEmailIgnoreCase(principal.actorId());
    }

    Optional<PlatformUserEntity> findByEmailIgnoreCase(String email);

    List<PlatformUserEntity> findAllByOrderByCreatedAtDesc();

    List<PlatformUserEntity> findByCustomerIdAndRoleIgnoreCaseAndStatusIgnoreCase(String customerId, String role, String status);

    long countByRoleIgnoreCaseAndStatusIgnoreCase(String role, String status);

    long countByRoleIgnoreCaseAndStatusIgnoreCaseAndCustomerId(String role, String status, String customerId);
}
