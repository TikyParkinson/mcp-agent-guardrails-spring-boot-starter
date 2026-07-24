# guardrails-ratelimit — DONE

**Aprobado por code-reviewer: 2026-07-24**

## Evidencia del checklist (ARCHITECTURE.md §9)

- [x] Spec `docs/specs/guardrails-ratelimit-spec.md` existe; el código la sigue sin desvíos
  (decisiones 1-6 documentadas: fixed window, límite por par, denegados consumen cuota, solo se
  audita el Deny, upsert atómico, desalojo lazy sin schedulers).
- [x] Cero `org.springframework` en `domain`/`application` (rg: 0). Dominio JDK-puro.
- [x] Sin versiones nuevas: BOMs verificados 2026-07-24 (Testcontainers 2.0.5, Postgres 42.7.11
  vía BOM de Boot). Sin SNAPSHOT/RC/M externos ni `TODO(version-check)`.
- [x] `mvn spotless:check checkstyle:check`: exit 0, 0 violaciones (4 módulos del reactor).
- [x] `mvn verify`: BUILD SUCCESS, 27 tests, 0 fallos. Cobertura Jacoco:
  **100% líneas (89/89), 100% ramas (24/24)**.
- [x] Adaptador out real (`JdbcRateLimitStoreAdapter`) cubierto por
  `JdbcRateLimitStoreAdapterPostgresTest` con `@Testcontainers` + PostgreSQL 17 real, incluida
  **atomicidad del upsert bajo concurrencia** (8 hilos × 50: 400 counts únicos, cero perdidos).
- [x] Las 9 dependencias del pom coinciden 1:1 con spec §7. Ninguna extra.
- [x] Header Apache 2.0 en 15/15 archivos `.java`.
- [x] README explica el puerto `RateLimitStorePort`, la semántica fixed-window (con el
  trade-off del borde documentado), propiedades y sustitución del adaptador por defecto.
- [x] Ningún método de producción >25 líneas (scan: 0).
- [x] Sin `return null` en `domain`/`application` (rg: 0).

## Siguiente módulo

Según ARCHITECTURE.md §6: **`spring-boot-starter`** — el ensamblaje final. Los cuatro
guardrails tienen su DONE.md aprobado (core, audit, authz, injection-guard, ratelimit), por lo
que se desbloquea el módulo 6. Empezar con `spec-architect`.
