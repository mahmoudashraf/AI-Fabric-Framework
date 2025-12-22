package com.ai.infrastructure.relationship.it.repository;

import com.ai.infrastructure.relationship.it.entity.BrandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<BrandEntity, String> {
}
