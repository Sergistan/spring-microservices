package com.utochkin.orderservice.request;

import com.utochkin.orderservice.dto.AddressDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompositeRequest {
    private List<OrderRequest> orderRequests;
    private AddressDto addressDto;

}
