package com.fooddelivery.service;

import com.fooddelivery.dto.*;
import com.fooddelivery.exception.*;
import com.fooddelivery.model.Customer;
import com.fooddelivery.repository.CustomerRepository;
import com.fooddelivery.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public CustomerService(CustomerRepository customerRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (customerRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken");
        }
        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered");
        }

        Customer customer = Customer.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .deliveryAddress(request.getDeliveryAddress())
                .city(request.getCity())
                .role(Customer.Role.CUSTOMER)
                .build();

        customerRepository.save(customer);

        String token = jwtUtil.generateToken(customer.getUsername(), customer.getRole().name());
        return new AuthResponse(token, customer.getId(), customer.getUsername(), customer.getRole().name());
    }

    public AuthResponse login(AuthRequest request) {
        Customer customer = customerRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "username", request.getUsername()));

        if (!passwordEncoder.matches(request.getPassword(), customer.getPassword())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(customer.getUsername(), customer.getRole().name());
        return new AuthResponse(token, customer.getId(), customer.getUsername(), customer.getRole().name());
    }

    @Transactional(readOnly = true)
    public CustomerResponse getProfile(String username) {
        Customer customer = customerRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "username", username));
        return CustomerResponse.fromEntity(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
        return CustomerResponse.fromEntity(customer);
    }

    @Transactional
    public CustomerResponse updateProfile(String username, RegisterRequest request) {
        Customer customer = customerRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "username", username));

        if (request.getFirstName() != null) customer.setFirstName(request.getFirstName());
        if (request.getLastName() != null) customer.setLastName(request.getLastName());
        if (request.getPhone() != null) customer.setPhone(request.getPhone());
        if (request.getDeliveryAddress() != null) customer.setDeliveryAddress(request.getDeliveryAddress());
        if (request.getCity() != null) customer.setCity(request.getCity());

        return CustomerResponse.fromEntity(customerRepository.save(customer));
    }

    // Used internally by other services — MONOLITH COUPLING
    public Customer findEntityByUsername(String username) {
        return customerRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "username", username));
    }
}
