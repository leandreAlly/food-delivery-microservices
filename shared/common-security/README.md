# common-security

Shared security primitives for every downstream microservice + the API Gateway.

## What's inside

| Class | Used by | Purpose |
|---|---|---|
| `JwtProperties` | Customer Service, Gateway | Binds `app.jwt.secret` + `app.jwt.expiration-ms` |
| `JwtIssuer` | Customer Service | Signs HS256 JWTs at register / login |
| `JwtVerifier` | Gateway | Verifies JWTs and extracts claims |
| `SecurityHeaders` | Gateway + all services | Constants: `X-User-Id`, `X-User-Username`, `X-User-Role` |
| `AuthenticatedUser` | All services | Record placed in `SecurityContext` (id, username, role) |
| `HeaderAuthenticationFilter` | All 4 services | Reads gateway-forwarded headers → Spring `Authentication` |
| `CurrentUser` + `CurrentUserArgumentResolver` | Controllers in all services | `@CurrentUser AuthenticatedUser user` sugar |
| `UnauthorizedException` | All services | Maps to 401 |

## Trust model

The **gateway** is the only component that validates JWTs. After validation it forwards three headers downstream. Each microservice trusts these headers — it never re-verifies the token. In production, network policy ensures traffic only reaches services via the gateway.

```
Client ──JWT──▶ Gateway ──verifies JWT──▶ injects X-User-* ──▶ Service
                                                                  │
                                                       HeaderAuthenticationFilter
                                                                  │
                                                       SecurityContextHolder
```

## Build & install locally

```bash
cd shared/common-security
mvn clean install
```

This puts `com.fooddelivery.shared:common-security:1.0.0` in your local `~/.m2/repository`. Services then declare it as a normal Maven dependency.
