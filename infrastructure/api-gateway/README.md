# api-gateway

Spring Cloud Gateway — the single public entry point for the platform.

> **9.1 scaffold only.** Boots, registers with Eureka, no routes yet.
> Routes, JWT, rate limit, and fallbacks arrive in 9.2 → 9.5. Full README
> in 9.6.

| | |
|---|---|
| Port | `8080` |
| Eureka name | `api-gateway` |
| Stack | Reactive (Spring WebFlux + Netty) — NOT servlet |
| Depends on | Eureka `:8761` |

## Verify the scaffold

```bash
# Eureka must be up first
(cd ../eureka-server && mvn spring-boot:run)

# Then the gateway
mvn spring-boot:run
```

Checks:
- http://localhost:8761 — `API-GATEWAY` should appear in the instances list.
- http://localhost:8080/actuator/health → `{"status":"UP"}`
- http://localhost:8080/actuator/gateway/routes → empty `[]` (no routes yet — that's 9.2).
