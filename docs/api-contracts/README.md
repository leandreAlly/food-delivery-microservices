# API contracts

One file per public-facing service plus the gateway. Each file lists every endpoint that service exposes — its method, path, auth requirement, request shape, response shape, and error codes.

| File | Service | Base path(s) |
|---|---|---|
| [`gateway.md`](./gateway.md) | API Gateway | Routes everything; actuator endpoints on `:8080` |
| [`customer.md`](./customer.md) | Customer Service | `/api/auth/**`, `/api/customers/**` |
| [`restaurant.md`](./restaurant.md) | Restaurant Service | `/api/restaurants/**` |
| [`order.md`](./order.md) | Order Service | `/api/orders/**` |
| [`delivery.md`](./delivery.md) | Delivery Service | `/api/deliveries/**` |

## Conventions used in these docs

- **All public client traffic** hits the gateway on `http://localhost:8080`. Service ports (`:8081`–`:8084`) are exposed for direct debugging only.
- **Authentication**: `Authorization: Bearer <jwt>`. JWT is issued by Customer Service at register/login. The gateway validates it and forwards `X-User-Id`, `X-User-Username`, `X-User-Role` headers downstream.
- **`/api/internal/**` paths** are not routed by the gateway. They exist for inter-service calls (Feign) on the internal Docker network only.
- **Error envelope** is identical across services:

  ```json
  {
    "timestamp": "2026-05-20T12:00:00Z",
    "status": 404,
    "error": "Not Found",
    "message": "Restaurant not found with id = 42",
    "path": "/api/restaurants/42",
    "fieldErrors": null
  }
  ```

  Validation failures populate `fieldErrors` with a `{field: message}` map; everything else leaves it `null`.

- **Status codes** follow standard semantics:
  - `200` OK — successful read or update
  - `201` Created — successful creation
  - `204` No Content — successful action with no body
  - `400` Bad Request — validation failure
  - `401` Unauthorized — missing / invalid JWT (from gateway, or service-side defense in depth)
  - `403` Forbidden — authenticated but not allowed (ownership / role check)
  - `404` Not Found — resource doesn't exist (or path not routed by gateway)
  - `409` Conflict — duplicate resource (e.g. username already taken)
  - `429` Too Many Requests — rate limiter tripped (from gateway, `POST /api/orders` only)
  - `503` Service Unavailable — downstream service is down or circuit breaker is open
