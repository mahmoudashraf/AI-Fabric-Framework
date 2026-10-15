package com.ai.fabric.platform.backend.deployment.dependency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ManagedDependencySpecRepository extends JpaRepository<ManagedDependencySpecEntity, String> {
    List<ManagedDependencySpecEntity> findByTemplateVersionIdAndActiveTrue(String templateVersionId);
}
