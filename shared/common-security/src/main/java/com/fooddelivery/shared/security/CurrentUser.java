package com.fooddelivery.shared.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a controller parameter to receive the {@link AuthenticatedUser} from the
 * security context. Resolved by {@link CurrentUserArgumentResolver}.
 *
 * <p>Example:
 * <pre>
 *   {@code @GetMapping("/me")}
 *   public CustomerResponse me({@code @CurrentUser AuthenticatedUser user}) { ... }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
