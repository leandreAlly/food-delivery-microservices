package com.fooddelivery.order.exception;

import com.fooddelivery.shared.security.UnauthorizedException;
import com.fooddelivery.shared.web.ErrorResponse;
import com.fooddelivery.shared.web.exception.AbstractGlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OrderExceptionHandler extends AbstractGlobalExceptionHandler {

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(CustomerServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleCustomerDown(CustomerServiceUnavailableException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    @ExceptionHandler(RestaurantServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleRestaurantDown(RestaurantServiceUnavailableException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }
}
