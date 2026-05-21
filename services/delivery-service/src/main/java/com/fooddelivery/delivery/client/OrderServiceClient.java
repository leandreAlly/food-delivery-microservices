package com.fooddelivery.delivery.client;

import com.fooddelivery.delivery.client.dto.OrderSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "order-service",
        fallback = OrderServiceClientFallback.class
)
public interface OrderServiceClient {

    @GetMapping("/api/internal/orders/{id}")
    OrderSummary getById(@PathVariable Long id);
}
