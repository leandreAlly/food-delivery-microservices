# Docker setup — runbook

Everything you need to bring the full platform up with one command.

## Prereqs

- Docker 24+ with Docker Compose v2 (`docker compose`, no hyphen).
- ~6 GB free RAM. 4 Postgres + Eureka + Gateway + 4 services is chunky.
- Ports `8080`, `8081–8084`, `8761`, `5672`, `15672`, `6379`, `5433–5436` free on the host.

## First-time setup

```bash
# 1. Create a local .env (gitignored). For dev defaults are fine.
cp .env.example .env

# 2. Build all images + bring everything up
docker compose up --build
```

The first `--build` takes 5–10 minutes — each service's Dockerfile installs its shared libs and packages a jar. Subsequent runs use cached layers and start in seconds.

`docker compose up` (no `--build`) skips the rebuild step if the images already exist.

## Boot order

Compose strictly waits for healthchecks before starting dependents:

```
postgres x4 ─┐
redis       ─┤
rabbitmq    ─┴────► eureka-server ────► customer-service
                                  ├────► restaurant-service ────► order-service ────► delivery-service ────► api-gateway
```

Expect ~2 minutes for everything to be `healthy` on a cold start. Watch progress:

```bash
docker compose ps
docker compose logs -f api-gateway
```

When `api-gateway` shows `healthy`, the whole stack is ready.

## What's running and where

| Component | Container | Host port | Purpose |
|---|---|---|---|
| API Gateway | `api-gateway` | `8080` | Single entry point for clients |
| Eureka | `eureka-server` | `8761` | Service registry dashboard at http://localhost:8761 |
| Customer Service | `customer-service` | `8081` | Direct access (debug only) |
| Restaurant Service | `restaurant-service` | `8082` | Direct access (debug only) |
| Order Service | `order-service` | `8083` | Direct access (debug only) |
| Delivery Service | `delivery-service` | `8084` | Direct access (debug only) |
| RabbitMQ | `rabbitmq` | `5672` AMQP + `15672` UI | http://localhost:15672 (`fooddelivery` / `fooddelivery`) |
| Redis | `redis` | `6379` | Rate-limit token-bucket store |
| customer_db | `customer-db` | `5433` | Postgres |
| restaurant_db | `restaurant-db` | `5434` | Postgres |
| order_db | `order-db` | `5435` | Postgres |
| delivery_db | `delivery-db` | `5436` | Postgres |

Connect to a specific DB:

```bash
psql -h 127.0.0.1 -p 5435 -U order -d order_db
```

## Common operations

```bash
# Background mode
docker compose up -d --build

# Tail one service's logs
docker compose logs -f order-service

# Restart a single service (e.g. after code change + rebuild)
docker compose up -d --build order-service

# Stop everything (keeps volumes — data survives)
docker compose down

# Stop and DELETE all data
docker compose down -v

# Drop into a shell inside a service container
docker compose exec order-service sh

# See what's healthy / unhealthy
docker compose ps
```

## End-to-end smoke test (through the gateway)

```bash
# Register
TOKEN=$(curl -sX POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username":"john","email":"john@example.com","password":"password123",
    "firstName":"John","lastName":"Doe",
    "deliveryAddress":"123 Main St","city":"Lagos"
  }' | jq -r .token)

# Create a restaurant
curl -sX POST http://localhost:8080/api/restaurants \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Mamas Kitchen","cuisineType":"Nigerian",
       "address":"45 Marina Rd","city":"Lagos","estimatedDeliveryMinutes":30}' | jq

# Re-login (your role just changed CUSTOMER → RESTAURANT_OWNER)
TOKEN=$(curl -sX POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"password123"}' | jq -r .token)

# Add a menu item
curl -sX POST http://localhost:8080/api/restaurants/1/menu \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Jollof Rice","price":2500.00,"category":"Main"}' | jq

# Place an order — fires OrderPlacedEvent into RabbitMQ
ORDER_ID=$(curl -sX POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"restaurantId":1,"items":[{"menuItemId":1,"quantity":2}]}' | jq -r .id)

# Wait a beat, then check the delivery was auto-created by event consumption
sleep 2
curl -sH "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/deliveries/order/$ORDER_ID | jq

# Check the order's status — should have advanced to CONFIRMED via the
# DeliveryStatusUpdatedEvent that came back.
curl -sH "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/orders/$ORDER_ID | jq .status
```

## Troubleshooting

### A service is stuck `unhealthy`

`docker compose logs -f <service>` — usually it's a config issue (wrong DB URL, missing env var, can't reach Eureka). Healthchecks have a 60s `start_period` so don't panic in the first minute.

### "Port is already allocated"

Something else on your host is using one of the ports. Stop it or remap in `docker-compose.yml`. Common conflict: a local Postgres on `5432` — but that's why I remapped the four DBs to `5433–5436`.

### Rebuilding takes forever even after a tiny change

Docker Compose rebuilds only the services whose Dockerfile / build context changed. A change in `services/customer-service/` invalidates only that service's cache. A change in `shared/common-security/` invalidates **every** service that copies it. Expected.

### "guest user can only connect via localhost" from RabbitMQ

The compose file uses a dedicated `fooddelivery` user precisely to avoid this. If you see it, you've either changed the env vars or `RABBITMQ_USER` / `RABBITMQ_PASSWORD` env vars don't match between RabbitMQ and the order/delivery services. Check `docker compose config | grep -A2 RABBITMQ`.

### `OutOfMemoryError` during build

Each Maven build uses ~512 MB. Building all 6 services in parallel can blow your heap. Build serially:

```bash
docker compose build customer-service
docker compose build restaurant-service
docker compose build order-service
docker compose build delivery-service
docker compose build eureka-server
docker compose build api-gateway
docker compose up -d
```
