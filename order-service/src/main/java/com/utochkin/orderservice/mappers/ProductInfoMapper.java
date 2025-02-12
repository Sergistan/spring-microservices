package com.utochkin.orderservice.mappers;

import com.utochkin.orderservice.models.ProductInfo;
import com.utochkin.orderservice.request.OrderRequest;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductInfoMapper {

    ProductInfo toEntity(OrderRequest orderRequest);
    List<ProductInfo> toListEntity(List<OrderRequest> orderRequests);
}
