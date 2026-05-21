package com.fooddelivery.restaurant.service;

import com.fooddelivery.restaurant.client.CustomerServiceClient;
import com.fooddelivery.restaurant.dto.*;
import com.fooddelivery.restaurant.model.MenuItem;
import com.fooddelivery.restaurant.model.Restaurant;
import com.fooddelivery.restaurant.repository.MenuItemRepository;
import com.fooddelivery.restaurant.repository.RestaurantRepository;
import com.fooddelivery.shared.security.AuthenticatedUser;
import com.fooddelivery.shared.security.UnauthorizedException;
import com.fooddelivery.shared.web.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RestaurantService {

    private final RestaurantRepository restaurants;
    private final MenuItemRepository menuItems;
    private final CustomerServiceClient customerService;

    public RestaurantService(RestaurantRepository restaurants,
                             MenuItemRepository menuItems,
                             CustomerServiceClient customerService) {
        this.restaurants = restaurants;
        this.menuItems = menuItems;
        this.customerService = customerService;
    }

    // ---- Restaurant lifecycle ----

    @Transactional
    public RestaurantResponse create(AuthenticatedUser owner, RestaurantRequest request) {
        Restaurant restaurant = Restaurant.builder()
                .name(request.name())
                .description(request.description())
                .cuisineType(request.cuisineType())
                .address(request.address())
                .city(request.city())
                .phone(request.phone())
                .estimatedDeliveryMinutes(request.estimatedDeliveryMinutes())
                .ownerId(owner.id())
                .ownerUsername(owner.username())
                .build();

        Restaurant saved = restaurants.save(restaurant);

        // Tell Customer Service to promote the user to RESTAURANT_OWNER.
        // Wrapped in Resilience4j — if Customer Service is down, the fallback
        // throws CustomerServiceUnavailableException (HTTP 503).
        //
        // The restaurant has already been persisted; the role bump is a
        // separate effect we accept could lag (the user's JWT still says
        // CUSTOMER until they log in again anyway).
        customerService.promoteToOwner(owner.username());

        return RestaurantResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public RestaurantResponse getById(Long id) {
        return RestaurantResponse.from(findRestaurant(id));
    }

    @Transactional(readOnly = true)
    public InternalRestaurantResponse getInternal(Long id) {
        return InternalRestaurantResponse.from(findRestaurant(id));
    }

    @Transactional(readOnly = true)
    public List<RestaurantResponse> getAllActive() {
        return restaurants.findByActiveTrue().stream()
                .map(RestaurantResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RestaurantResponse> searchByCity(String city) {
        return restaurants.findByCityIgnoreCaseAndActiveTrue(city).stream()
                .map(RestaurantResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RestaurantResponse> searchByCuisine(String cuisineType) {
        return restaurants.findByCuisineTypeIgnoreCaseAndActiveTrue(cuisineType).stream()
                .map(RestaurantResponse::from).toList();
    }

    // ---- Menu management ----

    @Transactional
    public MenuItemResponse addMenuItem(Long restaurantId,
                                        AuthenticatedUser caller,
                                        MenuItemRequest request) {
        Restaurant restaurant = findRestaurant(restaurantId);
        ensureOwner(restaurant, caller);

        MenuItem item = MenuItem.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .category(request.category())
                .imageUrl(request.imageUrl())
                .restaurant(restaurant)
                .build();

        return MenuItemResponse.from(menuItems.save(item));
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponse> getMenu(Long restaurantId) {
        return menuItems.findByRestaurantIdAndAvailableTrue(restaurantId).stream()
                .map(MenuItemResponse::from).toList();
    }

    @Transactional
    public MenuItemResponse updateMenuItem(Long itemId,
                                           AuthenticatedUser caller,
                                           MenuItemRequest request) {
        MenuItem item = findMenuItem(itemId);
        ensureOwner(item.getRestaurant(), caller);

        if (request.name() != null)        item.setName(request.name());
        if (request.description() != null) item.setDescription(request.description());
        if (request.price() != null)       item.setPrice(request.price());
        if (request.category() != null)    item.setCategory(request.category());
        if (request.imageUrl() != null)    item.setImageUrl(request.imageUrl());

        return MenuItemResponse.from(menuItems.save(item));
    }

    @Transactional
    public void toggleAvailability(Long itemId, AuthenticatedUser caller) {
        MenuItem item = findMenuItem(itemId);
        ensureOwner(item.getRestaurant(), caller);

        item.setAvailable(!item.isAvailable());
        menuItems.save(item);
    }

    // ---- Inter-service: order validation ----

    /**
     * Order Service calls this to validate menu items and capture live
     * prices. The returned snapshot is what Order Service writes into
     * order_db so its reads no longer fan out here.
     */
    @Transactional(readOnly = true)
    public ValidatedOrderResponse validateOrder(Long restaurantId,
                                                ValidateOrderRequest request) {
        Restaurant restaurant = findRestaurant(restaurantId);

        List<Long> requestedIds = request.items().stream()
                .map(ValidateOrderRequest.Item::menuItemId).toList();

        Map<Long, MenuItem> byId = menuItems
                .findByIdInAndRestaurantId(requestedIds, restaurantId).stream()
                .collect(Collectors.toMap(MenuItem::getId, Function.identity()));

        List<ValidatedOrderItem> validatedItems = request.items().stream()
                .map(req -> {
                    MenuItem item = byId.get(req.menuItemId());
                    if (item == null) {
                        throw new ResourceNotFoundException(
                                "MenuItem", "id (in restaurant " + restaurantId + ")",
                                req.menuItemId());
                    }
                    BigDecimal subtotal = item.getPrice()
                            .multiply(BigDecimal.valueOf(req.quantity()));
                    return new ValidatedOrderItem(
                            item.getId(), item.getName(), item.getPrice(),
                            req.quantity(), subtotal, item.isAvailable());
                })
                .toList();

        return new ValidatedOrderResponse(
                restaurant.getId(), restaurant.getName(), restaurant.getAddress(),
                restaurant.isActive(), restaurant.getEstimatedDeliveryMinutes(),
                validatedItems);
    }

    // ---- Helpers ----

    private Restaurant findRestaurant(Long id) {
        return restaurants.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", id));
    }

    private MenuItem findMenuItem(Long id) {
        return menuItems.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", id));
    }

    private void ensureOwner(Restaurant restaurant, AuthenticatedUser caller) {
        if (!restaurant.getOwnerUsername().equals(caller.username())
                && !"ADMIN".equals(caller.role())) {
            throw new UnauthorizedException("You don't own this restaurant");
        }
    }
}
