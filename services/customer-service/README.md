# customer-service

The identity microservice. Owns `customer_db`, issues JWTs at register / login, and exposes profile management endpoints.

| | |
|---|---|
| Port | `8081` |
| Eureka name | `customer-service` |
| Database | `customer_db` (H2 in dev, PostgreSQL in Docker) |
| Depends on | Eureka (`:8761`) |
| Used by | Restaurant Service, Order Service (via internal endpoints) |

## Run locally

```bash
# First — make sure the shared libs are installed locally:
(cd ../../shared/common-security && mvn -q install)
(cd ../../shared/common-web      && mvn -q install)

# Start Eureka in another terminal first:
(cd ../../infrastructure/eureka-server && mvn spring-boot:run)

# Then this service:
mvn spring-boot:run
```

Open http://localhost:8761 — you should now see `CUSTOMER-SERVICE` listed as a registered instance.

## Endpoints

### Public

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/api/auth/register` | `RegisterRequest` | `AuthResponse` (JWT + user info) |
| `POST` | `/api/auth/login` | `LoginRequest` | `AuthResponse` |

### Authenticated (Bearer token)

| Method | Path | Returns |
|---|---|---|
| `GET` | `/api/customers/me` | `CustomerResponse` |
| `PUT` | `/api/customers/me` | `CustomerResponse` |
| `GET` | `/api/customers/{id}` | `CustomerResponse` |

### Internal (inter-service only — never routed by the gateway)

| Method | Path | Caller | Purpose |
|---|---|---|---|
| `GET` | `/api/internal/customers/{id}` | Order Service | Minimal customer info for orders |
| `POST` | `/api/internal/customers/{username}/promote-to-owner` | Restaurant Service | Promote `CUSTOMER` → `RESTAURANT_OWNER` when they create their first restaurant |

## Quick smoke test

```bash
# Register
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"john","email":"john@example.com","password":"password123","firstName":"John","lastName":"Doe"}'

# Login (returns a JWT)
TOKEN=$(curl -sX POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"password123"}' | jq -r .token)

# Get own profile
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/customers/me
```

## Configuration

| Property | Default | Override with |
|---|---|---|
| `server.port` | `8081` | `SERVER_PORT` |
| `app.jwt.secret` | dev placeholder | `JWT_SECRET` (required in prod) |
| `app.jwt.expiration-ms` | `86400000` (24h) | — |
| `eureka.client.service-url.defaultZone` | `http://localhost:8761/eureka/` | `EUREKA_URL` |
| Database URL (docker profile) | `jdbc:postgresql://customer-db:5432/customer_db` | `DB_URL` |

## Notes for the migration

- The monolith's `Customer` entity had `@OneToMany List<Order>` — that's gone. Other services reference customers by ID only.
- `Customer.Role` enum stays here; it travels in the JWT `role` claim so other services know the role without calling Customer Service.
- `promoteToOwner` replaces the monolith's `RestaurantService.createRestaurant` directly mutating `Customer.role`. Restaurant Service (Phase 5) will call this endpoint.
- The local `JwtAuthenticationFilter` will be moved into `common-security` in Phase 5 once Restaurant Service needs the same filter.
