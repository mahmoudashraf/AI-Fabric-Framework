package com.ai.fabric.platform.backend.deployment.dependency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedDependencySpecRepository extends JpaRepository<ManagedDependencySpecEntity, String> {}
