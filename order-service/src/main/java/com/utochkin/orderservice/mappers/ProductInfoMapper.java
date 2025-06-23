package com.utochkin.orderservice.mappers;

import com.utochkin.orderservice.models.ProductInfo;
import com.utochkin.orderservice.request.OrderRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductInfoMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    ProductInfo toEntity(OrderRequest orderRequest);

    List<ProductInfo> toListEntity(List<OrderRequest> orderRequests);
}
