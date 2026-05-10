package com.fooddelivery.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class PlaceOrderRequest {
    @NotNull private Long restaurantId;
    @NotEmpty private List<OrderItemRequest> items;
    private String deliveryAddress;  // optional override of customer's default address
    private String specialInstructions;
}
