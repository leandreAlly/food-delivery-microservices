package com.fooddelivery.controller;

import com.fooddelivery.dto.*;
import com.fooddelivery.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/me")
    public ResponseEntity<CustomerResponse> getMyProfile(Authentication auth) {
        return ResponseEntity.ok(customerService.getProfile(auth.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getById(id));
    }

    @PutMapping("/me")
    public ResponseEntity<CustomerResponse> updateProfile(
            Authentication auth, @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(customerService.updateProfile(auth.getName(), request));
    }
}
