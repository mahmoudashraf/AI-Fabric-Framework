package com.ai.infrastructure.relationship.it.repository;

import com.ai.infrastructure.relationship.it.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, String> {
}
