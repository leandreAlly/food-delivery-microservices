package com.fooddelivery.order.service;

import com.fooddelivery.order.client.CustomerServiceClient;
import com.fooddelivery.order.client.RestaurantServiceClient;
import com.fooddelivery.order.client.dto.CustomerSummary;
import com.fooddelivery.order.client.dto.ValidateOrderRequest;
import com.fooddelivery.order.client.dto.ValidatedOrderResponse;
import com.fooddelivery.order.dto.InternalOrderResponse;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.order.model.Order;
import com.fooddelivery.order.model.OrderItem;
import com.fooddelivery.order.repository.OrderRepository;
import com.fooddelivery.shared.events.OrderCancelledEvent;
import com.fooddelivery.shared.events.OrderPlacedEvent;
import com.fooddelivery.shared.security.AuthenticatedUser;
import com.fooddelivery.shared.security.UnauthorizedException;
import com.fooddelivery.shared.web.exception.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final CustomerServiceClient customerService;
    private final RestaurantServiceClient restaurantService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public OrderService(OrderRepository orders,
                        CustomerServiceClient customerService,
                        RestaurantServiceClient restaurantService,
                        ApplicationEventPublisher applicationEventPublisher) {
        this.orders = orders;
        this.customerService = customerService;
        this.restaurantService = restaurantService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public OrderResponse placeOrder(AuthenticatedUser caller, PlaceOrderRequest request) {
        // 1) Look up customer (delivery address + name).
        CustomerSummary customer = customerService.getById(caller.id());

        // 2) Validate menu items and get live prices in one Feign call.
        ValidateOrderRequest validateReq = new ValidateOrderRequest(
                request.items().stream()
                        .map(i -> new ValidateOrderRequest.Item(i.menuItemId(), i.quantity()))
                        .toList());
        ValidatedOrderResponse validated = restaurantService.validateOrder(
                request.restaurantId(), validateReq);

        if (!validated.restaurantActive()) {
            throw new IllegalStateException("Restaurant is currently not accepting orders");
        }
        for (var item : validated.items()) {
            if (!item.available()) {
                throw new IllegalStateException(
                        "Menu item '" + item.menuItemName() + "' is not available");
            }
        }

        // 3) Snapshot everything into a new Order.
        String deliveryAddress = request.deliveryAddress() != null
                ? request.deliveryAddress()
                : customer.deliveryAddress();

        Order order = Order.builder()
                .customerId(caller.id())
                .customerName(customer.fullName())
                .customerUsername(caller.username())
                .restaurantId(validated.restaurantId())
                .restaurantName(validated.restaurantName())
                .restaurantAddress(validated.restaurantAddress())
                .deliveryAddress(deliveryAddress)
                .specialInstructions(request.specialInstructions())
                .estimatedDeliveryTime(
                        LocalDateTime.now().plusMinutes(validated.estimatedDeliveryMinutes()))
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < validated.items().size(); i++) {
            var validatedItem = validated.items().get(i);
            String itemNotes = request.items().get(i).specialInstructions();

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .menuItemId(validatedItem.menuItemId())
                    .menuItemName(validatedItem.menuItemName())
                    .quantity(validatedItem.quantity())
                    .unitPrice(validatedItem.unitPrice())
                    .subtotal(validatedItem.subtotal())
                    .specialInstructions(itemNotes)
                    .build();
            order.getItems().add(orderItem);
            total = total.add(validatedItem.subtotal());
        }
        order.setTotalAmount(total);

        Order saved = orders.save(order);

        // Publish OrderPlacedEvent as a Spring application event. The actual
        // RabbitMQ publish happens in OrderEventPublisher with
        // @TransactionalEventListener(AFTER_COMMIT) so the broker sees the
        // event only if the DB write commits successfully.
        applicationEventPublisher.publishEvent(new OrderPlacedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                saved.getId(),
                saved.getCustomerId(),
                saved.getCustomerName(),
                saved.getRestaurantId(),
                saved.getRestaurantName(),
                saved.getRestaurantAddress(),
                saved.getDeliveryAddress(),
                saved.getTotalAmount()
        ));

        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(Long id) {
        return OrderResponse.from(findOrder(id));
    }

    @Transactional(readOnly = true)
    public InternalOrderResponse getInternal(Long id) {
        return InternalOrderResponse.from(findOrder(id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(AuthenticatedUser caller) {
        return orders.findByCustomerIdOrderByCreatedAtDesc(caller.id()).stream()
                .map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getByRestaurant(Long restaurantId) {
        return orders.findByRestaurantIdOrderByCreatedAtDesc(restaurantId).stream()
                .map(OrderResponse::from).toList();
    }

    @Transactional
    public OrderResponse updateStatus(Long id, String status) {
        Order order = findOrder(id);
        order.setStatus(Order.OrderStatus.valueOf(status.toUpperCase()));
        return OrderResponse.from(orders.save(order));
    }

    @Transactional
    public OrderResponse cancel(Long id, AuthenticatedUser caller) {
        Order order = findOrder(id);

        if (!order.getCustomerUsername().equals(caller.username())
                && !"ADMIN".equals(caller.role())) {
            throw new UnauthorizedException("You can only cancel your own orders");
        }
        if (order.getStatus() != Order.OrderStatus.PLACED
                && order.getStatus() != Order.OrderStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Cannot cancel order in status: " + order.getStatus());
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        Order saved = orders.save(order);

        // Phase 8: publish OrderCancelledEvent so Delivery Service can mark
        // any in-flight delivery as FAILED. Same after-commit pattern.
        applicationEventPublisher.publishEvent(new OrderCancelledEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                saved.getId(),
                "Cancelled by " + caller.username()
        ));

        return OrderResponse.from(saved);
    }

    private Order findOrder(Long id) {
        return orders.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
    }
}
