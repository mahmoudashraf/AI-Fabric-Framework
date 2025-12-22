package com.ai.infrastructure.relationship.it.repository;

import com.ai.infrastructure.relationship.it.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {
}
