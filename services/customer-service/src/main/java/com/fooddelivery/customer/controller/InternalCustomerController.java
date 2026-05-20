package com.fooddelivery.customer.controller;

import com.fooddelivery.customer.dto.CustomerResponse;
import com.fooddelivery.customer.dto.InternalCustomerResponse;
import com.fooddelivery.customer.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints reserved for inter-service calls. These are NOT exposed via the
 * API Gateway — the gateway only routes {@code /api/customers/**} and
 * {@code /api/auth/**}. In a real deployment, network policy restricts these
 * to the internal cluster network.
 */
@RestController
@RequestMapping("/api/internal/customers")
public class InternalCustomerController {

    private final CustomerService customerService;

    public InternalCustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<InternalCustomerResponse> getInternal(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getInternal(id));
    }

    @PostMapping("/{username}/promote-to-owner")
    public ResponseEntity<CustomerResponse> promoteToOwner(@PathVariable String username) {
        return ResponseEntity.ok(customerService.promoteToOwner(username));
    }
}
