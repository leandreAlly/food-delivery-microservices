# eureka-server

Spring Cloud Netflix Eureka service registry.

| | |
|---|---|
| Port | `8761` |
| Dashboard | http://localhost:8761 |
| Depends on | nothing — this is platform infrastructure |
| Depended on by | all four business services + the API Gateway |

## What it does

When a service starts, it announces itself to Eureka with a logical name (e.g. `customer-service`) and its host/port. Other services and the gateway then ask Eureka "where is `customer-service` running?" and get back a live list. This means:

- Services move ports without breaking callers
- The gateway uses logical names (`lb://customer-service`) instead of hardcoded URLs
- Multiple instances of the same service are discovered automatically (load balancing comes for free)

## Run

```bash
cd infrastructure/eureka-server
mvn spring-boot:run
```

Open http://localhost:8761 — the dashboard is empty until you start the first service. From Phase 4 onward you'll see services register here.

## Configuration notes

- **`register-with-eureka: false`** + **`fetch-registry: false`** — Eureka doesn't try to register with itself. Common newcomer footgun: leaving these `true` causes the server to spam its own logs trying to call `localhost:8761` before it's fully up.
- **`enable-self-preservation: false`** — only set in local dev. In production this should be `true` (the default) — self-preservation keeps the registry from evicting healthy services en masse during a network partition.
- **`eviction-interval-timer-in-ms: 5000`** — faster eviction makes the dashboard reflect reality quickly when you stop services locally.

## What's not here yet

- Multiple Eureka peers (HA setup). Real prod runs at least two replicas peering with each other; out of scope for this project.
- Docker profile. Phase 10 will add `application-docker.yml` with the container hostname (`eureka-server`).
- Authentication on the dashboard. Eureka supports Spring Security on its endpoints; not configured here so the dashboard is reachable for grading.
