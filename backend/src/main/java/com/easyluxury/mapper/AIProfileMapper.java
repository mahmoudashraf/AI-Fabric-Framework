package com.easyluxury.mapper;

import com.easyluxury.dto.AIProfileDto;
import com.easyluxury.entity.AIProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface AIProfileMapper {
    
    AIProfileMapper INSTANCE = Mappers.getMapper(AIProfileMapper.class);
    
    @Mapping(source = "user.id", target = "userId")
    AIProfileDto toDto(AIProfile aiProfile);
    
    @Mapping(source = "userId", target = "user.id")
    @Mapping(target = "user", ignore = true)
    AIProfile toEntity(AIProfileDto aiProfileDto);
}
