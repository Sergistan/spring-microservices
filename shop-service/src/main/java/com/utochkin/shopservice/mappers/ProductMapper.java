package com.utochkin.shopservice.mappers;

import com.utochkin.shopservice.dto.ProductDto;
import com.utochkin.shopservice.models.Product;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductDto toDto(Product product);
    Product toEntity(ProductDto productDto);
    List<ProductDto> toListDto(List<Product> products);
    List<Product> toListEntity(List<ProductDto> productDto);
}
