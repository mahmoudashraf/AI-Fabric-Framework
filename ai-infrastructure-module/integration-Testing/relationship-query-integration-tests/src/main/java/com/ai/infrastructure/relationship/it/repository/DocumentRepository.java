package com.ai.infrastructure.relationship.it.repository;

import com.ai.infrastructure.relationship.it.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {
}
