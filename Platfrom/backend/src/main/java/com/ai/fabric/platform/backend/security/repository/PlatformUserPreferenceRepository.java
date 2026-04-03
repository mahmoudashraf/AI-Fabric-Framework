package com.ai.fabric.platform.backend.security.repository;

import com.ai.fabric.platform.backend.security.entity.PlatformUserPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformUserPreferenceRepository extends JpaRepository<PlatformUserPreferenceEntity, String> {

    List<PlatformUserPreferenceEntity> findByUserIdOrderByUpdatedAtDesc(String userId);

    Optional<PlatformUserPreferenceEntity> findByUserIdAndPreferenceKey(String userId, String preferenceKey);
}
