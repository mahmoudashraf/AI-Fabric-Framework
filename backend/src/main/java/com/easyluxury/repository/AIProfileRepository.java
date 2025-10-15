package com.easyluxury.repository;

import com.easyluxury.entity.AIProfile;
import com.easyluxury.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AIProfileRepository extends JpaRepository<AIProfile, UUID> {
    
    Optional<AIProfile> findByUserAndStatus(User user, AIProfile.AIProfileStatus status);
    
    List<AIProfile> findByUserOrderByCreatedAtDesc(User user);
    
    @Query("SELECT ap FROM AIProfile ap WHERE ap.user = :user AND ap.status IN :statuses ORDER BY ap.createdAt DESC")
    List<AIProfile> findByUserAndStatusInOrderByCreatedAtDesc(@Param("user") User user, @Param("statuses") List<AIProfile.AIProfileStatus> statuses);
    
    boolean existsByUserAndStatus(User user, AIProfile.AIProfileStatus status);
}
