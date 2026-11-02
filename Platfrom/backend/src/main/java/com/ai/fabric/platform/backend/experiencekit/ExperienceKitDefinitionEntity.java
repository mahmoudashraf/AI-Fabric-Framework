package com.ai.fabric.platform.backend.experiencekit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Getter @NoArgsConstructor
@Table(name = "platform_experience_kit_definitions")
public class ExperienceKitDefinitionEntity {
    @Id @Column(length = 64) private String code;
    @Column(nullable = false, length = 32) private String version;
    @Column(nullable = false, columnDefinition = "text") private String definitionJson;
    @Column(nullable = false, columnDefinition = "text") private String templatePluginIdsJson;
    @Column(nullable = false) private int sortOrder;
}
