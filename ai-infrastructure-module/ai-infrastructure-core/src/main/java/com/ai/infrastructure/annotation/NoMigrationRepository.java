package com.ai.infrastructure.annotation;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Marker repository used as a default placeholder for migration.
 */
public interface NoMigrationRepository extends JpaRepository<Object, Object> {
}
