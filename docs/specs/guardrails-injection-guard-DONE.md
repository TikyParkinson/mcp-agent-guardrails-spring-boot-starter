# guardrails-injection-guard — DONE

**Aprobado por code-reviewer: 2026-07-24**

## Evidencia del checklist (ARCHITECTURE.md §9)

- [x] Spec `docs/specs/guardrails-injection-guard-spec.md` existe; el código la sigue sin
  desvíos (decisiones 1-7 documentadas: dos severidades, no auditar caso limpio, `ruleId@path`
  sin contenido, tope de profundidad 8, built-ins en dominio, order 50, paquete
  `injectionguard`).
- [x] Cero `org.springframework` en `domain`/`application` (rg: 0). Dominio JDK-puro (regex =
  `java.util.regex`).
- [x] Sin versiones nuevas: todo gestionado por BOMs verificados. Sin SNAPSHOT/RC/M externos ni
  `TODO(version-check)`.
- [x] `mvn spotless:check checkstyle:check`: exit 0, 0 violaciones (4 módulos del reactor).
- [x] `mvn verify`: BUILD SUCCESS, 39 tests, 0 fallos. Cobertura Jacoco:
  **100% líneas (107/107), 100% ramas (32/32)**.
  (Actualizado 2026-07-24: +1 test de invariante blank en `ScanResult.Finding`.)
- [x] Adaptador out con store real: **N/A** — el rule set default es in-memory (spec §5);
  Testcontainers no aplica.
- [x] Las 5 dependencias del pom coinciden 1:1 con spec §7 (core, audit, spring-boot provided,
  junit, mockito). Ninguna extra.
- [x] Header Apache 2.0 en 18/18 archivos `.java`.
- [x] README explica el puerto `InjectionRuleSetPort`, las reglas built-in con ids estables,
  ejemplo YAML de regla custom y cómo sustituir el adaptador por defecto.
- [x] Ningún método de producción >25 líneas (scan excluyendo declaraciones de tipo: 0).
- [x] Sin `return null` en `domain`/`application` (rg: 0); ausencia modelada con
  `Optional<InjectionSeverity>` en `ScanResult.highestSeverity()`.

## Siguiente módulo

Según ARCHITECTURE.md §6: **`guardrails-ratelimit`** — empezar con `spec-architect`.
