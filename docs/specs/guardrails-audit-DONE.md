# guardrails-audit — DONE

**Aprobado por code-reviewer: 2026-07-24**

## Evidencia del checklist (ARCHITECTURE.md §9)

- [x] Spec `docs/specs/guardrails-audit-spec.md` existe; el código la sigue. Única corrección
  durante el ciclo: mapeo `timestamptz` → `OffsetDateTime.toInstant()` en el adaptador JDBC
  (bug real detectado por el test de Testcontainers; el contrato del puerto no cambió).
- [x] Cero `org.springframework` en `domain`/`application` (rg: 0). Dominio además JDK-puro:
  cero imports de guardrails-core (decisión 4 del spec).
- [x] Versiones GA verificadas 2026-07-24: Testcontainers 2.0.5 (BOM, artefactos 2.x
  `testcontainers-postgresql`/`testcontainers-junit-jupiter`), driver Postgres 42.7.11 vía BOM
  de Spring Boot 4.1.0. Sin SNAPSHOT/RC/M externos ni `TODO(version-check)`.
- [x] `mvn spotless:check checkstyle:check`: exit 0, 0 violaciones (3 módulos del reactor).
- [x] `mvn verify`: BUILD SUCCESS, 25 tests, 0 fallos. Cobertura Jacoco:
  **100% líneas (107/107), 100% ramas (16/16)**.
- [x] Adaptador out real (`JdbcAuditLogStoreAdapter`) cubierto por
  `JdbcAuditLogStoreAdapterPostgresTest` con `@Testcontainers` + PostgreSQL 17 real
  (round-trip, orden/limit, tabla vacía, violación de PK).
- [x] Las 8 dependencias del pom coinciden 1:1 con spec §7. Ninguna extra.
- [x] Header Apache 2.0 en 16/16 archivos `.java`.
- [x] README explica el puerto `AuditLogStorePort`, las propiedades y cómo sustituir el
  adaptador por defecto.
- [x] Ningún método de producción >25 líneas (scan: 0 hallazgos).
- [x] Sin `return null` en `domain`/`application` (rg: 0).

## Siguiente módulo

Según ARCHITECTURE.md §6: **`guardrails-authz`** — empezar con `spec-architect`.
