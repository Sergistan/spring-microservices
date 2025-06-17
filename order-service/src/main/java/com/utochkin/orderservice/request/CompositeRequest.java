package com.utochkin.orderservice.request;

import com.utochkin.orderservice.dto.AddressDto;
import lombok.Data;

import java.util.List;
@Data
public class CompositeRequest {
    private List<OrderRequest> orderRequests;
    private AddressDto addressDto;

}
