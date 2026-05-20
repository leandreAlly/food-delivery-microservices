package com.fooddelivery.customer.controller;

import com.fooddelivery.customer.dto.CustomerResponse;
import com.fooddelivery.customer.dto.UpdateProfileRequest;
import com.fooddelivery.customer.service.CustomerService;
import com.fooddelivery.shared.security.AuthenticatedUser;
import com.fooddelivery.shared.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/me")
    public ResponseEntity<CustomerResponse> me(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(customerService.getProfile(user.username()));
    }

    @PutMapping("/me")
    public ResponseEntity<CustomerResponse> updateMe(@CurrentUser AuthenticatedUser user,
                                                    @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(customerService.updateProfile(user.username(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getById(id));
    }
}
