package com.utochkin.orderservice.mappers;

import com.utochkin.orderservice.dto.AddressDto;
import com.utochkin.orderservice.models.Address;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AddressMapper {
    AddressDto toDto(Address address);
    Address toEntity(AddressDto addressDto);
}
