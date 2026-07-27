# DONE — guardrails-credential-leak-guard

> Módulo 8 según ARCHITECTURE.md §6. Aprobado por `code-reviewer`.
> Fecha: 2026-07-26. Rama: `feature/guardrails-credential-leak-guard`.
> Primer consumidor del SPI de salida (`guardrails-core-outbound-spi-DONE.md`).

## Cobertura

`mvn -pl guardrails-credential-leak-guard -am verify` ⇒ BUILD SUCCESS.

| Métrica | Resultado |
|---|---|
| Tests | 135 |
| Líneas | 201/201 — **100%** |
| Ramas | 52/52 — **100%** |
| Checkstyle | 0 violaciones |
| Spotless | limpio |

Cobertura por clase, sin excepciones: `SecretPattern` 23/23 y 8/8, `SecretRedactor` 25/25 y 6/6,
`ValueFlattener` 15/15 y 8/8, `SecretScanner` 11/11 y 6/6, `SecretFinding` 10/10 y 4/4,
`BuiltInSecretPatterns` 14/14, `CredentialLeakGuardrail` 21/21 y 6/6,
`CredentialLeakResultGuardrail` 15/15 y 6/6, `RedactToolResultService` 16/16 y 2/2,
`ScanToolArgumentsForSecretsService` 5/5, `GuardrailsCredentialLeakProperties` 13/13 y 6/6,
`InMemorySecretPatternSetAdapter` 4/4.

## Checklist (ARCHITECTURE.md §9)

- [x] **Spec existe y el código no se desvía** — `docs/specs/guardrails-credential-leak-guard-spec.md`.
      Las diez clases de dominio de §2 están presentes con su forma exacta; `MAX_DEPTH = 8`,
      `name()` = `"credential-leak"` y `order()` = `60` coinciden con §5.1. Ver observaciones.
- [x] **Cero imports de Spring en `domain`/`application`** — `grep -rn org.springframework` sobre
      ambos paquetes devuelve 0 resultados. Spring solo aparece en `infrastructure`.
- [x] **Versiones GA verificadas** — el `pom.xml` no fija ninguna versión propia: todas vienen de
      los BOM del parent. El único `-SNAPSHOT` es la versión del artefacto en desarrollo
      (`0.2.0-SNAPSHOT`, heredada) y `${project.version}` para `guardrails-core`. Sin RC, sin
      milestones, sin `TODO(version-check)`.
- [x] **Spotless + Checkstyle** — `mvn -pl guardrails-credential-leak-guard -am spotless:check
      checkstyle:check` ⇒ BUILD SUCCESS, 0 violaciones.
- [x] **Cobertura ≥80/80** — 100% líneas y 100% ramas.
- [x] **Testcontainers si hay store real** — N/A: el único adaptador `out`
      (`InMemorySecretPatternSetAdapter`) es una lista en memoria, no habla con ningún store.
- [x] **Dependencias justificadas** — las 4 del `pom.xml` (`guardrails-core`, `spring-boot`
      provided, `junit-jupiter` test, `mockito-core` test) están en §7 del spec, una por una.
      Ninguna dependencia de otro módulo `guardrails-*` (ARCHITECTURE.md §5).
- [x] **Header Apache 2.0** — presente en los 35 archivos `.java` del módulo, verificado uno a uno.
- [x] **README explica el puerto plugable** — `guardrails-credential-leak-guard/README.md`,
      sección `Replacing the pattern source`: FQN del puerto, limitación del adaptador por defecto
      (solo cambia al reiniciar), ejemplo completo de integración con un gestor de secretos y las
      tres contrapartidas de hacerlo (secretos en memoria, caché con TTL obligatoria, coste
      lineal).
- [x] **Métodos ≤ ~25 líneas** — medición automática sobre las 12 clases de producción: ningún
      método supera 25 líneas. El más largo es `redact(...)` de `RedactToolResultService`, 16.
- [x] **Sin `return null` en `domain`/`application`** — 0 ocurrencias. La ausencia se modela con
      `Optional<SecretSeverity>` en `highestSeverity()` y con listas vacías en el resto.

## Verificación funcional destacada

- Los 11 patrones por defecto están cubiertos uno a uno con muestras reales, y los cuatro que
  declaran `secretGroup` se comprueban por su salida exacta: `DB_PASSWORD=hunter2000` ⇒
  `DB_PASSWORD=[REDACTED:credential-assignment]`, no `DB_[REDACTED:…]`.
- Cuatro textos benignos ("the weather is sunny today", una URL corriente, …) no disparan ningún
  patrón.
- Tests explícitos de que **ningún `reason` contiene el valor detectado**, tanto en `Deny`
  (entrada) como en `Block` (salida).
- Un `Block` por fuga en `structuredContent` no se degrada a `Redact` aunque además haya fuga
  redactable en el texto.
- `SecretPattern.ofLiteral` verificado con un secreto real lleno de metacaracteres
  (`P@ssw0rd.2026+prod(final)`): se redacta, no se filtra, y ni el `.` ni el `+` se interpretan
  como regex.

## Observaciones no bloqueantes

1. ~~`SecretFinding.describe()` no aparece en §2 del spec~~ — **resuelto**: §2 del spec recoge
   ahora `describe()` como derivado de `SecretFinding`, con su formato `patternId@location` y la
   nota de que sigue sin exponer el valor.
2. `CredentialLeakResultGuardrail` reutiliza el helper estático package-private
   `CredentialLeakGuardrail.describe(List)`. Es correcto (mismo paquete, una sola
   responsabilidad) y evita duplicar el formato del motivo, pero si aparece un tercer consumidor
   merecerá una clase propia.

## Siguiente paso

`update-docs` sobre `guardrails-credential-leak-guard`, y después `spec-architect` para el
módulo 9, `guardrails-egress-control` (ARCHITECTURE.md §6) — en su propia rama, tras mergear
esta.
