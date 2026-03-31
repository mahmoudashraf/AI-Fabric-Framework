package com.ai.fabric.platform.backend.secret.repository;

import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSecretRepository extends JpaRepository<PlatformSecretEntity, String> {
}
