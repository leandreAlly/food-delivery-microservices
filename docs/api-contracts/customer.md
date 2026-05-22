# Customer Service API contract

Identity and profile management. Issues JWTs at register / login.

| | |
|---|---|
| Port | `8081` |
| Owns | `customer_db` |
| Base paths | `/api/auth/**`, `/api/customers/**`, `/api/internal/customers/**` |

## Public endpoints

### `POST /api/auth/register`

Create a new customer and return a signed JWT.

Request:
```json
{
  "username": "john",
  "email": "john@example.com",
  "password": "password123",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+1-555-0100",
  "deliveryAddress": "123 Main St",
  "city": "Lagos"
}
```

| Field | Constraint |
|---|---|
| `username` | required, 3–50 chars, unique |
| `email` | required, valid email format, unique |
| `password` | required, 8–100 chars |
| `firstName` | required |
| `lastName` | required |
| `phone` | optional, unique if provided |
| `deliveryAddress`, `city` | optional |

Response — `201 Created`:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": 1,
  "username": "john",
  "role": "CUSTOMER"
}
```

Errors:
- `400` — validation failure (`fieldErrors` populated)
- `409` — username, email, or phone already in use

### `POST /api/auth/login`

Verify credentials and return a fresh JWT.

Request:
```json
{ "username": "john", "password": "password123" }
```

Response — `200 OK`: same `AuthResponse` shape as register.

Errors:
- `400` — missing fields
- `401` — invalid credentials (unknown user or wrong password)

## Authenticated endpoints

Require `Authorization: Bearer <token>`. Through the gateway, the service also receives `X-User-*` headers.

### `GET /api/customers/me`

Return the caller's own profile.

Response — `200 OK`:
```json
{
  "id": 1,
  "username": "john",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+1-555-0100",
  "deliveryAddress": "123 Main St",
  "city": "Lagos",
  "role": "CUSTOMER",
  "createdAt": "2026-05-12T09:15:00"
}
```

### `PUT /api/customers/me`

Partial update of the caller's own profile. Any non-null field is applied; `username`, `email`, `password`, and `role` cannot be changed here.

Request:
```json
{
  "firstName": "John",
  "lastName": "Doe-Updated",
  "phone": "+1-555-0199",
  "deliveryAddress": "45 New Address St",
  "city": "Lagos"
}
```

Response — `200 OK`: updated `CustomerResponse`.

### `GET /api/customers/{id}`

Look up any customer by ID.

Response — `200 OK`: `CustomerResponse` (same shape as `/me`).

Errors:
- `404` — no customer with that ID

## Internal endpoints (not gateway-routed)

These exist for inter-service Feign calls only. The gateway has no route for `/api/internal/**`, so external requests return `404`.

### `GET /api/internal/customers/{id}`

Minimal customer view used by **order-service** at order-placement time to capture name + delivery address snapshots.

Response — `200 OK`:
```json
{
  "id": 1,
  "username": "john",
  "firstName": "John",
  "lastName": "Doe",
  "deliveryAddress": "123 Main St",
  "city": "Lagos",
  "role": "CUSTOMER"
}
```

Note the absence of `email` and `password` — only what other services need.

### `POST /api/internal/customers/{username}/promote-to-owner`

Called by **restaurant-service** when a customer creates their first restaurant. Promotes `CUSTOMER` → `RESTAURANT_OWNER`.

Idempotent: calling it on a user who is already `RESTAURANT_OWNER` or `ADMIN` is a no-op.

Response — `200 OK`: full `CustomerResponse` reflecting the new role.

**Caller-side caveat**: the existing JWT the user is holding still contains the old `CUSTOMER` role until they log in again. JWTs don't auto-refresh.

## Error envelope

See [`README.md`](./README.md). Customer Service's exception handler maps:

| Exception | Status |
|---|---|
| `ResourceNotFoundException` | 404 |
| `DuplicateResourceException` | 409 |
| `IllegalStateException` / `IllegalArgumentException` | 400 |
| `MethodArgumentNotValidException` | 400 with `fieldErrors` |
| `UnauthorizedException` | 401 |
| Anonymous user → `.authenticated()` endpoint | 401 (via `JsonAccessDeniedHandler`) |
| Authenticated user → endpoint they're not authorized for | 403 |
