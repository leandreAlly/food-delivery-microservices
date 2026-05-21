package com.fooddelivery.restaurant.config;

import com.fooddelivery.shared.security.JwtAuthenticationFilter;
import com.fooddelivery.shared.security.JwtProperties;
import com.fooddelivery.shared.security.JwtVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.bind.annotation.RequestMethod;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public JwtVerifier jwtVerifier(JwtProperties properties) {
        return new JwtVerifier(properties);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtVerifier verifier) {
        // Shared filter from common-security — promoted from customer-service
        // in Phase 5 once a second caller existed.
        return new JwtAuthenticationFilter(verifier);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public catalog browse
                .requestMatchers(RequestMethod.GET.name(), "/api/restaurants/search/**").permitAll()
                .requestMatchers(RequestMethod.GET.name(), "/api/restaurants/*/menu").permitAll()
                // Inter-service (network-policy enforced in real prod)
                .requestMatchers("/api/internal/**").permitAll()
                // Actuator
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
