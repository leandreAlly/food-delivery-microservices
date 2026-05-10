# Food Delivery Platform — Monolith (Starter Code)

> **Module 9 — Microservices Architecture Project**
> This is the monolithic application you will refactor into microservices.

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+

### Run the application
```bash
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080` with an embedded H2 database (no external DB needed).

### H2 Console
Access at `http://localhost:8080/h2-console` with:
- JDBC URL: `jdbc:h2:mem:fooddelivery`
- Username: `sa`
- Password: *(empty)*

---

## API Endpoints

### Auth (public)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register a new customer |
| POST | `/api/auth/login` | Login and get JWT token |

### Customers (authenticated)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/customers/me` | Get my profile |
| GET | `/api/customers/{id}` | Get customer by ID |
| PUT | `/api/customers/me` | Update my profile |

### Restaurants (mixed)
| Method | Endpoint | Auth? | Description |
|--------|----------|-------|-------------|
| GET | `/api/restaurants/search/all` | No | List all active restaurants |
| GET | `/api/restaurants/search/city/{city}` | No | Search by city |
| GET | `/api/restaurants/search/cuisine/{type}` | No | Search by cuisine type |
| GET | `/api/restaurants/{id}` | Yes | Get restaurant details |
| GET | `/api/restaurants/{id}/menu` | No | Get restaurant menu |
| POST | `/api/restaurants` | Yes | Create a restaurant (become owner) |
| POST | `/api/restaurants/{id}/menu` | Yes | Add menu item (owner only) |
| PUT | `/api/restaurants/menu/{itemId}` | Yes | Update menu item (owner only) |
| PATCH | `/api/restaurants/menu/{itemId}/toggle` | Yes | Toggle item availability |

### Orders (authenticated)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/orders` | Place a new order |
| GET | `/api/orders/{id}` | Get order by ID |
| GET | `/api/orders/my-orders` | Get my order history |
| GET | `/api/orders/restaurant/{id}` | Get restaurant's orders |
| PATCH | `/api/orders/{id}/status?status=X` | Update order status |
| POST | `/api/orders/{id}/cancel` | Cancel an order |

### Deliveries (authenticated)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/deliveries/{id}` | Get delivery by ID |
| GET | `/api/deliveries/order/{orderId}` | Get delivery for an order |
| GET | `/api/deliveries/status/{status}` | List deliveries by status |
| PATCH | `/api/deliveries/{id}/status?status=X` | Update delivery status |

---

## Sample Test Flow (Postman)

### 1. Register a customer
```json
POST /api/auth/register
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

### 2. Login (use the returned token for all subsequent requests)
```json
POST /api/auth/login
{ "username": "john", "password": "password123" }
```
Set header: `Authorization: Bearer <token>`

### 3. Create a restaurant
```json
POST /api/restaurants
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

### 4. Add menu items
```json
POST /api/restaurants/1/menu
{ "name": "Jollof Rice", "description": "Smoky party-style jollof", "price": 2500.00, "category": "Main" }

POST /api/restaurants/1/menu
{ "name": "Suya", "description": "Spicy grilled beef skewers", "price": 1500.00, "category": "Appetizer" }

POST /api/restaurants/1/menu
{ "name": "Chapman", "description": "Classic Nigerian cocktail", "price": 800.00, "category": "Drink" }
```

### 5. Place an order
```json
POST /api/orders
{
  "restaurantId": 1,
  "items": [
    { "menuItemId": 1, "quantity": 2 },
    { "menuItemId": 2, "quantity": 1 }
  ],
  "specialInstructions": "Extra pepper please"
}
```

### 6. Check delivery status
```
GET /api/deliveries/order/1
```

### 7. Update delivery status (simulate driver flow)
```
PATCH /api/deliveries/1/status?status=PICKED_UP
PATCH /api/deliveries/1/status?status=DELIVERED
```

---

## Monolith Architecture (What to Refactor)

```
┌─────────────────────────────────────────────────────┐
│                 SINGLE APPLICATION                   │
│                   Port 8080                          │
│                                                     │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │  Customer    │  │  Restaurant  │  │   Order    │ │
│  │  Controller  │  │  Controller  │  │ Controller │ │
│  └──────┬──────┘  └──────┬───────┘  └─────┬──────┘ │
│         │                │                 │        │
│  ┌──────▼──────┐  ┌──────▼───────┐  ┌─────▼──────┐ │
│  │  Customer   │◄─│  Restaurant  │◄─│   Order    │ │
│  │  Service    │  │  Service     │  │  Service   │ │
│  └──────┬──────┘  └──────┬───────┘  └─────┬──────┘ │
│         │                │                 │        │
│         └────────────────┼─────────────────┘        │
│                          │                          │
│                ┌─────────▼──────────┐               │
│                │  SINGLE DATABASE   │               │
│                │  (H2 / PostgreSQL) │               │
│                │                    │               │
│                │  customers table   │               │
│                │  restaurants table │               │
│                │  menu_items table  │               │
│                │  orders table      │               │
│                │  order_items table │               │
│                │  deliveries table  │               │
│                └────────────────────┘               │
└─────────────────────────────────────────────────────┘
```

### Key Coupling Points to Break

1. **`OrderService`** directly injects `CustomerService`, `RestaurantService`, and `DeliveryService`
2. **`RestaurantService`** directly accesses `CustomerRepository` for ownership validation
3. **`DeliveryService.createDeliveryForOrder()`** is called synchronously during order placement
4. **`DeliveryService.updateStatus()`** directly modifies `Order` entity status
5. **Entity relationships** (`@ManyToOne`, `@OneToOne`) cross domain boundaries
6. **DTO mapping** traverses entities across domains (e.g., `OrderResponse` accesses `Customer.firstName`)

---

## Target Microservices Architecture

```
Client → API Gateway (:8080)
              │
              ├── /api/customers/**   → Customer Service   (:8081) → customer_db
              ├── /api/restaurants/**  → Restaurant Service (:8082) → restaurant_db
              ├── /api/orders/**      → Order Service      (:8083) → order_db
              └── /api/deliveries/**  → Delivery Service   (:8084) → delivery_db

Eureka Server (:8761) — Service Registry
RabbitMQ (:5672)      — Event Bus
```

Good luck with the refactoring!
