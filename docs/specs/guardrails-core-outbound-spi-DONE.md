# DONE — guardrails-core outbound SPI

> Extensión aditiva de `guardrails-core` (ARCHITECTURE.md §5.1). Aprobada por `code-reviewer`.
> Fecha: 2026-07-26. Rama: `feature/guardrails-credential-leak-guard`.
> Consumidor que la motiva: `guardrails-credential-leak-guard` (módulo 8).

## Cobertura

`mvn -pl guardrails-core -am verify` ⇒ BUILD SUCCESS.

| Métrica | Resultado |
|---|---|
| Tests | 99 (36 preexistentes + 63 nuevos) |
| Líneas | 253/253 — **100%** |
| Ramas | 69/69 — **100%** |
| Checkstyle | 0 violaciones |
| Spotless | limpio |

Cobertura de las clases nuevas, una a una: `ToolResultContext` 12/12 líneas 2/2 ramas,
`ResultDecisionCombiner` 15/15 y 2/2, `ResultGuardrailChain` 41/41 y 12/12,
`GuardedToolCallHandler` 74/74 y 23/23, `Redact` 6/6 y 2/2, `Block` 5/5 y 2/2,
`ResultEvaluation` 6/6 y 2/2, `ResultVerdict` 4/4, `PassThrough` 1/1,
`GuardrailToolDecorator` 8/8.

## Checklist (ARCHITECTURE.md §9)

- [x] **Spec existe y el código no se desvía** — `docs/specs/guardrails-core-outbound-spi-spec.md`.
      Modelo, puertos, cadena y adaptador implementados según las secciones 2–5. Ver observación 1.
- [x] **Cero imports de Spring en `domain`/`application`** — `grep -rn org.springframework` sobre
      ambos paquetes devuelve 0 resultados.
- [x] **Versiones GA verificadas** — el `pom.xml` de `guardrails-core` **no se modificó**
      (`git status` limpio para ese archivo): 0 dependencias nuevas. El único `-SNAPSHOT` es la
      versión del propio artefacto en desarrollo (`0.2.0-SNAPSHOT`), no una dependencia.
      La API del SDK usada se verificó con `javap` sobre `mcp-core-2.0.0.jar`, no de memoria.
- [x] **Spotless + Checkstyle** — `mvn -pl guardrails-core -am spotless:check checkstyle:check`
      ⇒ BUILD SUCCESS, 0 violaciones.
- [x] **Cobertura ≥80/80** — 100% líneas y 100% ramas (ver tabla).
- [x] **Testcontainers si hay store real** — N/A: la extensión no añade ningún adaptador `out`;
      `guardrails-core/src/main/java/.../adapter/` solo contiene `in/`.
- [x] **Dependencias justificadas** — sección 7 del spec: ninguna nueva. Las 4 del módulo
      (`mcp-core`, `spring-boot`, `junit-jupiter`, `mockito-core`) ya estaban justificadas en
      `docs/specs/guardrails-core-spec.md` §7.
- [x] **Header Apache 2.0** — presente en los 17 archivos nuevos (11 de producción, 6 de test),
      verificado uno a uno.
- [x] **README explica el puerto plugable** — `guardrails-core/README.md`, sección `Extending`:
      cómo registrar un `ResultGuardrail`, que sin ninguno la cadena devuelve el resultado por
      identidad, y que `structuredContent` es de solo lectura.
- [x] **Métodos ≤ ~25 líneas** — el más largo es `redacted(...)` con 15; medición automática
      sobre todas las clases nuevas y modificadas: ningún método supera 25.
- [x] **Sin `return null` en `domain`/`application`** — 0 ocurrencias. La ausencia se modela con
      tipos (`PassThrough`) y el `null` devuelto por un guardrail externo se traduce a `Block`.

## Retrocompatibilidad (condición de ARCHITECTURE.md §5.1)

- **Aditivo**: `Guardrail`, `GuardrailDecision`, `ChainVerdict` y `GuardrailChain` no se tocan.
- **Neutral sin uso**: sin `ResultGuardrail` registrados el veredicto es `PassThrough` y el
  resultado se devuelve **por identidad**, sin reconstruirlo.
- **Demostrado, no afirmado**: los 36 tests preexistentes de core pasan **sin modificar ni una
  línea de test**; el constructor de 4 parámetros de `GuardedToolCallHandler` y la firma
  original de `GuardrailToolDecorator.decorate` se conservan. Reactor completo (8 módulos):
  BUILD SUCCESS.

## Hallazgo corregido durante el ciclo

`test-engineer` detectó dos ramas **inalcanzables** en `GuardedToolCallHandler`
(`content() == null` y `text() == null`): el SDK MCP 2.0 valida ambos en el constructor
(`IllegalArgumentException`). Código muerto eliminado; sin él la cobertura de ramas llega a 100%.

## Ampliación posterior a la aprobación inicial

Al validar el spec del módulo 8 se detectó una **vía de fuga no cubierta**: el mapeo original solo
exponía `TextContent`, dejando fuera los `EmbeddedResource` con `TextResourceContents` — es decir,
una tool que devuelve un fichero como recurso embebido (`read_file` sobre un `.env`, el escenario
canónico) pasaba sin ser inspeccionada. `textContents` ahora incluye también ese texto y la
redacción reconstruye el `EmbeddedResource` preservando `uri`, `mimeType`, `meta` y anotaciones.
Cubierto por 4 tests nuevos; sigue en 100%/100%. El cambio es igualmente aditivo: los contenidos
binarios y los `ResourceLink` continúan devolviéndose por identidad.

## Observaciones no bloqueantes

1. El spec §5.1 no especifica qué hacer con valores `null` dentro de `structuredContent`; la
   implementación los omite porque `Map.copyOf` los rechaza. Comportamiento correcto y cubierto
   por test (`shouldSkipNullStructuredValuesWhenBuildingContext`), pero conviene registrarlo como
   Decisión de diseño 7 en el spec al pasar por `update-docs`.

## Siguiente paso

`update-docs` sobre `guardrails-core`, y después `spec-architect` para el módulo 8,
`guardrails-credential-leak-guard` (ARCHITECTURE.md §6), primer consumidor de este SPI.
