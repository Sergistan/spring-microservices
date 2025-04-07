package com.utochkin.historyservice.models;

import com.utochkin.historyservice.dto.OrderDtoForKafka;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.util.List;

@Document(collection = "orderHistory")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class OrderHistory implements Serializable {
    @Id
    @NotEmpty
    private String username;
    private List<OrderDtoForKafka> orders;
}
