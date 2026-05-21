package com.fooddelivery.restaurant.controller;

import com.fooddelivery.restaurant.dto.*;
import com.fooddelivery.restaurant.service.RestaurantService;
import com.fooddelivery.shared.security.AuthenticatedUser;
import com.fooddelivery.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants")
public class RestaurantController {

    private final RestaurantService service;

    public RestaurantController(RestaurantService service) {
        this.service = service;
    }

    // ---- Public ----

    @GetMapping("/search/all")
    public ResponseEntity<List<RestaurantResponse>> all() {
        return ResponseEntity.ok(service.getAllActive());
    }

    @GetMapping("/search/city/{city}")
    public ResponseEntity<List<RestaurantResponse>> byCity(@PathVariable String city) {
        return ResponseEntity.ok(service.searchByCity(city));
    }

    @GetMapping("/search/cuisine/{type}")
    public ResponseEntity<List<RestaurantResponse>> byCuisine(@PathVariable String type) {
        return ResponseEntity.ok(service.searchByCuisine(type));
    }

    @GetMapping("/{id}/menu")
    public ResponseEntity<List<MenuItemResponse>> menu(@PathVariable Long id) {
        return ResponseEntity.ok(service.getMenu(id));
    }

    // ---- Authenticated ----

    @GetMapping("/{id}")
    public ResponseEntity<RestaurantResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<RestaurantResponse> create(@CurrentUser AuthenticatedUser owner,
                                                     @Valid @RequestBody RestaurantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(owner, request));
    }

    @PostMapping("/{restaurantId}/menu")
    public ResponseEntity<MenuItemResponse> addMenuItem(@PathVariable Long restaurantId,
                                                        @CurrentUser AuthenticatedUser caller,
                                                        @Valid @RequestBody MenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addMenuItem(restaurantId, caller, request));
    }

    @PutMapping("/menu/{itemId}")
    public ResponseEntity<MenuItemResponse> updateMenuItem(@PathVariable Long itemId,
                                                           @CurrentUser AuthenticatedUser caller,
                                                           @Valid @RequestBody MenuItemRequest request) {
        return ResponseEntity.ok(service.updateMenuItem(itemId, caller, request));
    }

    @PatchMapping("/menu/{itemId}/toggle")
    public ResponseEntity<Void> toggle(@PathVariable Long itemId,
                                       @CurrentUser AuthenticatedUser caller) {
        service.toggleAvailability(itemId, caller);
        return ResponseEntity.noContent().build();
    }
}
