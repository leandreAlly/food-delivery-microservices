package com.fooddelivery.order.client;

import com.fooddelivery.order.client.dto.ValidateOrderRequest;
import com.fooddelivery.order.client.dto.ValidatedOrderResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "restaurant-service",
        fallback = RestaurantServiceClientFallback.class
)
public interface RestaurantServiceClient {

    @PostMapping("/api/internal/restaurants/{id}/validate-order")
    ValidatedOrderResponse validateOrder(@PathVariable Long id,
                                         @RequestBody ValidateOrderRequest request);
}
