# Restaurant Service API contract

Restaurant catalog + menu items. Calls Customer Service via Feign for role promotion.

| | |
|---|---|
| Port | `8082` |
| Owns | `restaurant_db` (Restaurant + MenuItem aggregate) |
| Base paths | `/api/restaurants/**`, `/api/internal/restaurants/**` |

## Public endpoints (no auth)

### `GET /api/restaurants/search/all`

List all active restaurants.

Response — `200 OK`:
```json
[
  {
    "id": 1,
    "name": "Mama's Kitchen",
    "description": "Traditional Nigerian cuisine",
    "cuisineType": "Nigerian",
    "address": "45 Marina Road",
    "city": "Lagos",
    "phone": "+234-1-555-0200",
    "active": true,
    "rating": 0.0,
    "estimatedDeliveryMinutes": 35,
    "ownerId": 1,
    "createdAt": "2026-05-13T09:00:00"
  }
]
```

### `GET /api/restaurants/search/city/{city}`

Filter active restaurants by city (case-insensitive).

Response — `200 OK`: same `RestaurantResponse[]` shape.

### `GET /api/restaurants/search/cuisine/{type}`

Filter by cuisine type (case-insensitive).

Response — `200 OK`: same shape.

### `GET /api/restaurants/{id}/menu`

Read the menu (available items only) for a restaurant.

Response — `200 OK`:
```json
[
  {
    "id": 1,
    "name": "Jollof Rice",
    "description": "Smoky party-style jollof",
    "price": 2500.00,
    "category": "Main",
    "available": true,
    "imageUrl": null,
    "restaurantId": 1
  }
]
```

## Authenticated endpoints

### `GET /api/restaurants/{id}`

Restaurant detail. Same `RestaurantResponse` shape as the search endpoints.

Errors:
- `404` — no restaurant with that ID

### `POST /api/restaurants`

Create a restaurant. The caller becomes the owner.

**Side effect**: triggers a Feign call to Customer Service (`/api/internal/customers/{username}/promote-to-owner`) which bumps the caller's role to `RESTAURANT_OWNER`.

Request:
```json
{
  "name": "Mama's Kitchen",
  "description": "Traditional Nigerian cuisine",
  "cuisineType": "Nigerian",
  "address": "45 Marina Road",
  "city": "Lagos",
  "phone": "+234-1-555-0200",
  "estimatedDeliveryMinutes": 35
}
```

| Field | Constraint |
|---|---|
| `name`, `cuisineType`, `address`, `city` | required, non-blank |
| `description`, `phone` | optional |
| `estimatedDeliveryMinutes` | required, ≥ 5 |

Response — `201 Created`: `RestaurantResponse` with the new `id`.

Errors:
- `400` — validation failure
- `503` — Customer Service unavailable (circuit breaker open). The restaurant **is still saved**; only the role-promotion failed. The caller can re-trigger by re-logging-in after Customer Service recovers.

### `POST /api/restaurants/{restaurantId}/menu`

Add a menu item. **Owner-only** — the caller must match the restaurant's `ownerUsername`.

Request:
```json
{
  "name": "Jollof Rice",
  "description": "Smoky party-style jollof",
  "price": 2500.00,
  "category": "Main",
  "imageUrl": null
}
```

| Field | Constraint |
|---|---|
| `name` | required, non-blank |
| `price` | required, ≥ 0.01 |
| Others | optional |

Response — `201 Created`: `MenuItemResponse`.

Errors:
- `403` — caller doesn't own this restaurant
- `404` — restaurant doesn't exist

### `PUT /api/restaurants/menu/{itemId}`

Update a menu item. Owner-only. Partial update — any null field is left unchanged.

Request: same shape as POST.

Response — `200 OK`: updated `MenuItemResponse`.

### `PATCH /api/restaurants/menu/{itemId}/toggle`

Flip the `available` flag on a menu item. Owner-only.

Response — `204 No Content`.

## Internal endpoints (not gateway-routed)

### `GET /api/internal/restaurants/{id}`

Used by **order-service** for restaurant lookup. Minimal payload:

```json
{
  "id": 1,
  "name": "Mama's Kitchen",
  "address": "45 Marina Road",
  "city": "Lagos",
  "active": true,
  "estimatedDeliveryMinutes": 35
}
```

### `POST /api/internal/restaurants/{id}/validate-order`

Called by **order-service** to validate menu items and capture live prices before snapshotting them into `order_db`.

Request:
```json
{
  "items": [
    { "menuItemId": 1, "quantity": 2 },
    { "menuItemId": 3, "quantity": 1 }
  ]
}
```

Response — `200 OK`:
```json
{
  "restaurantId": 1,
  "restaurantName": "Mama's Kitchen",
  "restaurantAddress": "45 Marina Road",
  "restaurantActive": true,
  "estimatedDeliveryMinutes": 35,
  "items": [
    {
      "menuItemId": 1,
      "menuItemName": "Jollof Rice",
      "unitPrice": 2500.00,
      "quantity": 2,
      "subtotal": 5000.00,
      "available": true
    }
  ]
}
```

Order Service uses this entire payload to populate its own DB — no follow-up calls needed.

Errors:
- `404` — a `menuItemId` doesn't exist in this restaurant
- `400` — request body invalid

## Error envelope

See [`README.md`](./README.md). Restaurant Service maps:

| Exception | Status |
|---|---|
| `ResourceNotFoundException` | 404 |
| `UnauthorizedException` (ownership check) | 403 |
| `CustomerServiceUnavailableException` (Feign CB open) | 503 |
| Validation failure | 400 |
