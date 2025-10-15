package com.easyluxury.mapper;

import com.easyluxury.dto.UserDto;
import com.easyluxury.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {
    UserDto toDto(User user);
    User toEntity(UserDto userDto);
}
