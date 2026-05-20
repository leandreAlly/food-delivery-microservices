package com.fooddelivery.customer.service;

import com.fooddelivery.customer.dto.*;
import com.fooddelivery.customer.model.Customer;
import com.fooddelivery.customer.repository.CustomerRepository;
import com.fooddelivery.shared.security.JwtIssuer;
import com.fooddelivery.shared.security.UnauthorizedException;
import com.fooddelivery.shared.web.exception.DuplicateResourceException;
import com.fooddelivery.shared.web.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;

    public CustomerService(CustomerRepository customers,
                           PasswordEncoder passwordEncoder,
                           JwtIssuer jwtIssuer) {
        this.customers = customers;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (customers.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username already taken");
        }
        if (customers.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already registered");
        }

        Customer customer = Customer.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .deliveryAddress(request.deliveryAddress())
                .city(request.city())
                .role(Customer.Role.CUSTOMER)
                .build();

        customers.save(customer);
        return issueToken(customer);
    }

    public AuthResponse login(LoginRequest request) {
        Customer customer = customers.findByUsername(request.username())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), customer.getPassword())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        return issueToken(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getProfile(String username) {
        return CustomerResponse.from(findByUsername(username));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {
        return CustomerResponse.from(findById(id));
    }

    @Transactional(readOnly = true)
    public InternalCustomerResponse getInternal(Long id) {
        return InternalCustomerResponse.from(findById(id));
    }

    @Transactional
    public CustomerResponse updateProfile(String username, UpdateProfileRequest request) {
        Customer customer = findByUsername(username);

        if (request.firstName() != null)       customer.setFirstName(request.firstName());
        if (request.lastName() != null)        customer.setLastName(request.lastName());
        if (request.phone() != null)           customer.setPhone(request.phone());
        if (request.deliveryAddress() != null) customer.setDeliveryAddress(request.deliveryAddress());
        if (request.city() != null)            customer.setCity(request.city());

        return CustomerResponse.from(customers.save(customer));
    }

    /**
     * Called by Restaurant Service when a customer creates their first restaurant.
     * Replaces the monolith's direct {@code Customer.role} mutation from
     * {@code RestaurantService}.
     */
    @Transactional
    public CustomerResponse promoteToOwner(String username) {
        Customer customer = findByUsername(username);
        if (customer.getRole() == Customer.Role.CUSTOMER) {
            customer.setRole(Customer.Role.RESTAURANT_OWNER);
            customers.save(customer);
        }
        return CustomerResponse.from(customer);
    }

    private Customer findByUsername(String username) {
        return customers.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "username", username));
    }

    private Customer findById(Long id) {
        return customers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
    }

    private AuthResponse issueToken(Customer customer) {
        String token = jwtIssuer.issue(
                customer.getId(),
                customer.getUsername(),
                customer.getRole().name());
        return new AuthResponse(
                token,
                customer.getId(),
                customer.getUsername(),
                customer.getRole().name());
    }
}
