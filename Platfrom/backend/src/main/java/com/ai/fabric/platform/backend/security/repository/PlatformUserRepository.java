package com.ai.fabric.platform.backend.security.repository;

import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformUserRepository extends JpaRepository<PlatformUserEntity, String> {

    Optional<PlatformUserEntity> findByEmailIgnoreCase(String email);
}
