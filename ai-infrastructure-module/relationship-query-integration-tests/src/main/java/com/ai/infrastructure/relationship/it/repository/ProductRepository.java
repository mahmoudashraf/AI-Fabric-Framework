package com.ai.infrastructure.relationship.it.repository;

import com.ai.infrastructure.relationship.it.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, String> {
}
