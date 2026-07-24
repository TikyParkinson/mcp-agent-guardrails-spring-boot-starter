# guardrails-authz — DONE

**Aprobado por code-reviewer: 2026-07-24**

## Evidencia del checklist (ARCHITECTURE.md §9)

- [x] Spec `docs/specs/guardrails-authz-spec.md` existe; el código la sigue sin desvíos
  (decisiones 1-6 documentadas en §9 del spec: default ALLOW, first-match-wins, matching
  exacto/`*`, decisión en dominio, auditoría en adapter-in).
- [x] Cero `org.springframework` en `domain`/`application` (rg: 0). Dominio JDK-puro: solo
  imports `java.*`.
- [x] Sin versiones nuevas: todo gestionado por BOMs ya verificados (2026-07-24). Sin
  SNAPSHOT/RC/M externos ni `TODO(version-check)`.
- [x] `mvn spotless:check checkstyle:check`: exit 0, 0 violaciones (4 módulos del reactor).
- [x] `mvn verify`: BUILD SUCCESS, 23 tests, 0 fallos. Cobertura Jacoco:
  **100% líneas (80/80), 100% ramas (28/28)**.
- [x] Adaptador out con store real: **N/A** — la política default es in-memory desde
  properties (spec §5); Testcontainers no aplica.
- [x] Las 5 dependencias del pom coinciden 1:1 con spec §7 (core, audit, spring-boot provided,
  junit, mockito). Ninguna extra.
- [x] Header Apache 2.0 en 15/15 archivos `.java`.
- [x] README explica el puerto `AccessPolicyPort`, la semántica first-match-wins, ejemplo YAML
  (incluida postura default-deny) y cómo sustituir el adaptador por defecto.
- [x] Ningún método de producción >25 líneas (scan: 1 falso positivo verificado manualmente —
  PolicyRule.java:25 es la declaración del record, sus 4 métodos tienen 3-5 líneas).
- [x] Sin `return null` en `domain`/`application` (rg: 0).

## Siguiente módulo

Según ARCHITECTURE.md §6: **`guardrails-injection-guard`** — empezar con `spec-architect`.
