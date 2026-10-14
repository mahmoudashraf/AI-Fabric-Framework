package com.ai.fabric.platform.backend.customerselfservice.repository;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerOfferRepository extends JpaRepository<CustomerOfferEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOfferEntity o where o.id = :id")
    Optional<CustomerOfferEntity> findByIdForUpdate(@Param("id") String id);
}
