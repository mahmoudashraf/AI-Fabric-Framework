package com.easyluxury.repository;

import com.easyluxury.entity.AIProfile;
import com.easyluxury.entity.AIProfileStatus;
import com.easyluxury.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AIProfileRepository extends JpaRepository<AIProfile, UUID> {
    
    Optional<AIProfile> findByUserAndStatus(User user, AIProfileStatus status);
    
    List<AIProfile> findByUserOrderByCreatedAtDesc(User user);
    
    boolean existsByUserAndStatus(User user, AIProfileStatus status);
    
    Optional<AIProfile> findFirstByUserOrderByCreatedAtDesc(User user);
}
