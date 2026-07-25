# guardrails-tool-integrity — DONE

**Aprobado por code-reviewer: 2026-07-25**
**Rama:** `feature/guardrails-tool-integrity` (flujo por rama de ARCHITECTURE.md §6)

## Evidencia del checklist (ARCHITECTURE.md §9)

- [x] Spec `docs/specs/guardrails-tool-integrity-spec.md` existe; el código la sigue.
  Correcciones del ciclo documentadas: el `case` combinado del switch del guardrail se separó
  en dos cases equivalentes (rama sintética del compilador invisible para Jacoco; sin cambio de
  comportamiento).
- [x] Cero `org.springframework` en `domain`/`application` (rg: 0). Dominio JDK-puro
  (`java.security.MessageDigest` incluido).
- [x] **Regla §5 (nueva) cumplida**: única dependencia `guardrails-*` es `guardrails-core`
  (el SPI que la propia regla señala como punto de unión). Sin `guardrails-audit` —
  las decisiones quedan en la traza del `ChainVerdict` (Decisión 1 del spec).
- [x] Sin versiones nuevas: BOMs verificados. Sin SNAPSHOT/RC/M externos ni
  `TODO(version-check)`.
- [x] `mvn spotless:check checkstyle:check`: exit 0, 0 violaciones.
- [x] `mvn verify`: BUILD SUCCESS, 46 tests, 0 fallos. Cobertura Jacoco:
  **100% líneas (194/194), 100% ramas (43/43)**.
- [x] Adaptador out real (`JdbcToolBaselineStoreAdapter`) cubierto por
  `JdbcToolBaselineStoreAdapterPostgresTest` con `@Testcontainers` + PostgreSQL 17: round-trip
  (incluido padding CHAR(64)), TOFU vía `ON CONFLICT`, upsert de aprobación, y **atomicidad de
  `establishIfAbsent` con 8 hilos concurrentes eligiendo exactamente un ganador**.
- [x] Las 9 dependencias del pom coinciden 1:1 con spec §7. Ninguna extra.
- [x] Header Apache 2.0 en 29/29 archivos `.java`.
- [x] README explica TOFU, el puerto `ToolBaselineStorePort`, el flujo de aprobación por
  fingerprint exacto, y el aviso de que in-memory no protege entre despliegues.
- [x] Ningún método de producción >25 líneas (scan: 0).
- [x] Sin `return null` en `domain`/`application` (rg: 0); ausencia modelada con `Optional` y
  sealed `IntegrityCheckResult`.

## Siguiente paso

Según ARCHITECTURE.md §6: abrir **PR de `feature/guardrails-tool-integrity` → `develop`**.
No se arranca `guardrails-credential-leak-guard` (módulo 8) hasta que esta PR esté mergeada.
