# common-web

Cross-cutting HTTP concerns shared by every microservice.

## What's inside

| Class | Purpose |
|---|---|
| `ErrorResponse` | Uniform error envelope — same JSON shape across every service |
| `CorrelationIdFilter` | Reads or generates `X-Correlation-Id`, pushes into MDC, echoes on response |
| `CorrelationIdConstants` | `X-Correlation-Id` header name + MDC key |
| `ResourceNotFoundException` | Maps to HTTP 404 |
| `DuplicateResourceException` | Maps to HTTP 409 |
| `AbstractGlobalExceptionHandler` | Base class — each service extends with `@RestControllerAdvice` |

## Why same error shape everywhere?

The gateway (Spring Cloud Gateway) can rewrite or augment error responses based on downstream status codes. Keeping the body shape identical across services means the gateway logic is simple and clients see one consistent format regardless of which service answered.

```json
{
  "timestamp": "2026-05-14T10:32:18.521Z",
  "status": 404,
  "error": "Not Found",
  "message": "Restaurant not found with id = 42",
  "path": "/api/restaurants/42",
  "fieldErrors": null
}
```

## How services wire it up

```java
@RestControllerAdvice
public class MyServiceExceptionHandler extends AbstractGlobalExceptionHandler {
    // add service-specific @ExceptionHandler methods here if needed
}

@Configuration
public class WebConfig {
    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
}
```

In `logback-spring.xml`, include `%X{correlationId}` in the pattern so every log line is tagged.

## Build & install

```bash
cd shared/common-web
mvn clean install
```
