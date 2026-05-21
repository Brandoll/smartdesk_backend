package com.smartdesk.mapper;

import com.smartdesk.model.dto.InitRegistrationDTO;
import com.smartdesk.model.dto.UserResponseDTO;
import com.smartdesk.model.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    User toEntity(InitRegistrationDTO dto);

    UserResponseDTO toResponseDto(User entity);
}
