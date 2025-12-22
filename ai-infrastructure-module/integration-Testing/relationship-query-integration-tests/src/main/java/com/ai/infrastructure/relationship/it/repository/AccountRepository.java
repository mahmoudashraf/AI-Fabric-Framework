package com.ai.infrastructure.relationship.it.repository;

import com.ai.infrastructure.relationship.it.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<AccountEntity, String> {
}
