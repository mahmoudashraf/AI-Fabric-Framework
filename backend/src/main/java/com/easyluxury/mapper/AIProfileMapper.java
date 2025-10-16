package com.easyluxury.mapper;

import com.easyluxury.dto.AIProfileDto;
import com.easyluxury.entity.AIProfile;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AIProfileMapper {
    
    @Mapping(source = "user.id", target = "userId")
    AIProfileDto toDto(AIProfile aiProfile);
    
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AIProfile toEntity(AIProfileDto aiProfileDto);
}
