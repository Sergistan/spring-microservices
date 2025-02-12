package com.utochkin.orderservice.mappers;

import com.utochkin.orderservice.dto.UserDto;
import com.utochkin.orderservice.models.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto toDto(User user);
    User toEntity(UserDto userDto);
}
