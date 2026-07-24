# guardrails-core — DONE

**Aprobado por code-reviewer: 2026-07-24**

## Evidencia del checklist (ARCHITECTURE.md §9)

- [x] Spec `docs/specs/guardrails-core-spec.md` existe; el código no se desvía (decisiones
  documentadas en §9 del spec: sin short-circuit, fail-closed, Escalate⇒bloquear, scopes provided,
  parent pom incluido en este módulo).
- [x] Cero imports `org.springframework` en `domain` y `application` (rg: 0 resultados).
- [x] Versiones GA verificadas en Maven Central el 2026-07-24: Spring Boot 4.1.0, MCP SDK 2.0.0,
  JUnit 6.1.2, Mockito 5.23.0, Jacoco 0.8.15, Spotless 3.8.0, Checkstyle 13.8.0/plugin 3.6.0,
  compiler 3.15.0, surefire 3.5.6. Sin SNAPSHOT/RC/M en dependencias (0.1.0-SNAPSHOT es la
  versión del propio proyecto en desarrollo). Sin `TODO(version-check)` pendientes.
- [x] `mvn spotless:check checkstyle:check`: exit 0, 0 violaciones.
- [x] `mvn verify`: BUILD SUCCESS, 36 tests, 0 fallos. Cobertura Jacoco:
  **100% líneas (106/106), 100% ramas (38/38)** — umbral 80/80 superado.
  (Actualizado 2026-07-24: +2 tests de contrato para `Guardrail.order()` default y
  `AgentIdResolver` ante `Implementation.name()` null.)
- [x] Adaptador out con store real: **N/A** — core es stateless (spec §5); Testcontainers no
  aplica en este módulo.
- [x] Las 4 dependencias del pom (`mcp-core` provided, `spring-boot` provided, `junit-jupiter`
  test, `mockito-core` test) están justificadas en spec §7. Ninguna extra.
- [x] Header Apache 2.0 en 25/25 archivos `.java`.
- [x] `guardrails-core/README.md` explica el SPI `Guardrail`, la propiedad
  `mcp.guardrails.enabled` y cómo enchufar guardrails propios.
- [x] Ningún método de producción supera ~25 líneas (scan automatizado: 0 hallazgos).
- [x] Sin `return null` en `domain`/`application` (rg: 0 resultados); ausencia modelada con
  sealed interface + fail-closed.

## Siguiente módulo

Según ARCHITECTURE.md §6: **`guardrails-audit`** — empezar con `spec-architect`.
