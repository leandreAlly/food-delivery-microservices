package com.fooddelivery.service;

import com.fooddelivery.dto.*;
import com.fooddelivery.exception.*;
import com.fooddelivery.model.*;
import com.fooddelivery.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MONOLITH COUPLING — THIS IS THE WORST OFFENDER:
 *
 * OrderService directly depends on:
 *  - CustomerService  (to get customer entity)
 *  - RestaurantService (to get restaurant and menu item entities)
 *  - DeliveryService  (to create delivery SYNCHRONOUSLY)
 *
 * In microservices:
 *  1. Store customerId / restaurantId as Long values
 *  2. Validate via Feign calls to Customer Service / Restaurant Service
 *  3. Publish OrderPlacedEvent — Delivery Service subscribes asynchronously
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerService customerService;       // CROSS-DOMAIN
    private final RestaurantService restaurantService;   // CROSS-DOMAIN
    private final DeliveryService deliveryService;       // CROSS-DOMAIN

    public OrderService(OrderRepository orderRepository,
                        CustomerService customerService,
                        RestaurantService restaurantService,
                        DeliveryService deliveryService) {
        this.orderRepository = orderRepository;
        this.customerService = customerService;
        this.restaurantService = restaurantService;
        this.deliveryService = deliveryService;
    }

    @Transactional
    public OrderResponse placeOrder(String customerUsername, PlaceOrderRequest request) {
        // MONOLITH: directly fetching Customer entity from Customer domain
        Customer customer = customerService.findEntityByUsername(customerUsername);

        // MONOLITH: directly fetching Restaurant entity from Restaurant domain
        Restaurant restaurant = restaurantService.findEntityById(request.getRestaurantId());

        if (!restaurant.isActive()) {
            throw new IllegalStateException("Restaurant is currently not accepting orders");
        }

        // Build order
        Order order = Order.builder()
                .customer(customer)       // MONOLITH: direct entity reference
                .restaurant(restaurant)   // MONOLITH: direct entity reference
                .deliveryAddress(request.getDeliveryAddress() != null
                        ? request.getDeliveryAddress()
                        : customer.getDeliveryAddress())
                .specialInstructions(request.getSpecialInstructions())
                .estimatedDeliveryTime(
                        LocalDateTime.now().plusMinutes(restaurant.getEstimatedDeliveryMinutes()))
                .build();

        // MONOLITH: directly fetching MenuItem entities from Restaurant domain
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest itemReq : request.getItems()) {
            MenuItem menuItem = restaurantService.findMenuItemById(itemReq.getMenuItemId());

            if (!menuItem.isAvailable()) {
                throw new IllegalStateException("Menu item '" + menuItem.getName() + "' is not available");
            }
            if (!menuItem.getRestaurant().getId().equals(restaurant.getId())) {
                throw new IllegalStateException("Menu item '" + menuItem.getName()
                        + "' does not belong to restaurant '" + restaurant.getName() + "'");
            }

            BigDecimal subtotal = menuItem.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .menuItem(menuItem)  // MONOLITH: cross-domain entity reference
                    .quantity(itemReq.getQuantity())
                    .unitPrice(menuItem.getPrice())
                    .subtotal(subtotal)
                    .specialInstructions(itemReq.getSpecialInstructions())
                    .build();

            order.getItems().add(orderItem);
            total = total.add(subtotal);
        }

        order.setTotalAmount(total);
        Order savedOrder = orderRepository.save(order);

        // MONOLITH PROBLEM: Delivery created SYNCHRONOUSLY — blocks the order response!
        // In microservices, publish OrderPlacedEvent instead.
        deliveryService.createDeliveryForOrder(savedOrder);

        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        return OrderResponse.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getCustomerOrders(String username) {
        Customer customer = customerService.findEntityByUsername(username);
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream().map(OrderResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getRestaurantOrders(Long restaurantId) {
        return orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
                .stream().map(OrderResponse::fromEntity).toList();
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        Order.OrderStatus newStatus = Order.OrderStatus.valueOf(status.toUpperCase());
        order.setStatus(newStatus);

        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, String username) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // MONOLITH: cross-domain entity check
        if (!order.getCustomer().getUsername().equals(username)) {
            throw new UnauthorizedException("You can only cancel your own orders");
        }

        if (order.getStatus() != Order.OrderStatus.PLACED
                && order.getStatus() != Order.OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot cancel order in status: " + order.getStatus());
        }

        order.setStatus(Order.OrderStatus.CANCELLED);

        // MONOLITH: synchronously cancel delivery
        if (order.getDelivery() != null) {
            deliveryService.cancelDelivery(order.getDelivery().getId());
        }

        return OrderResponse.fromEntity(orderRepository.save(order));
    }
}
