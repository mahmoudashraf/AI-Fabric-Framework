package com.ai.fabric.platform.backend.experiencekit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExperienceKitDefinitionRepository extends JpaRepository<ExperienceKitDefinitionEntity, String> {
    List<ExperienceKitDefinitionEntity> findAllByOrderBySortOrderAsc();
}
