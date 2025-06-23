package com.utochkin.orderservice.mappers;

import com.utochkin.orderservice.dto.AddressDto;
import com.utochkin.orderservice.models.Address;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AddressMapper {
    AddressDto toDto(Address address);

    @Mapping(target = "id", ignore = true)
    Address toEntity(AddressDto addressDto);
}
