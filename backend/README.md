# aptech4-api (backend)

Spring Boot **4.1.2** / Java **21** modular monolith (see ADR-003).

Domain layout: [docs/DOMAIN_LAYERING.md](docs/DOMAIN_LAYERING.md)  
OpenAPI / Swagger: [docs/OPENAPI.md](docs/OPENAPI.md)  
Security / JWT: [docs/SECURITY.md](docs/SECURITY.md) (ADR-005)  
Testing (Compose `IT_*` + Testcontainers): [docs/TESTING.md](docs/TESTING.md) (ADR-009)

## Build without a host JDK

```bash
# from repo root
make up-deps
docker run --rm -v "$PWD/backend:/app" -v aptech4_m2:/root/.m2 -w /app \
  maven:3.9.9-eclipse-temurin-21 ./mvnw -DskipTests package
```

## Smoke against Compose

Run the jar on Docker network `aptech4_net` with `POSTGRES_HOST=postgres` and `REDIS_HOST=redis`, then:

```bash
curl -sS http://localhost:8080/api/v1/health
curl -sS http://localhost:8080/v3/api-docs | head
```

Swagger UI: `http://localhost:8080/swagger-ui`

## Notes

- Schema via Liquibase only; `spring.jpa.hibernate.ddl-auto=validate`.
- Task tracking: `.cursor/state/TASKS.md`.
