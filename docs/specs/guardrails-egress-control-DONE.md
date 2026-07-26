# DONE — guardrails-egress-control

> Módulo 9 según ARCHITECTURE.md §6. Aprobado por `code-reviewer` en la segunda pasada.
> Fecha: 2026-07-26. Rama: `feature/guardrails-egress-control`.
> Prerequisito cumplido: `guardrails-credential-leak-guard-DONE.md` aprobado.

## Cobertura

`mvn -pl guardrails-egress-control -am verify` ⇒ BUILD SUCCESS.

| Métrica | Resultado |
|---|---|
| Tests | 132 |
| Líneas | 193/193 — **100%** |
| Ramas | 90/90 — **100%** |
| Checkstyle | 0 violaciones |
| Spotless | limpio |

Ninguna clase queda incompleta: `DestinationExtractor`, `Destination`, `AllowedDestination`,
`EgressPolicy`, `EgressTool`, `ArgumentPathResolver`, `CheckEgressDestinationService`,
`EgressGuardrail`, `GuardrailsEgressProperties` e `InMemoryEgressPolicyAdapter` están al 100% en
líneas y ramas.

## Checklist (ARCHITECTURE.md §9)

- [x] **Spec existe y el código no se desvía** — `docs/specs/guardrails-egress-control-spec.md`.
      Las doce piezas de dominio de §2 están presentes; `name()` = `"egress-control"` y
      `order()` = `70` coinciden con §5.1; el fail-closed de §4 se comprueba en los tests del
      caso de uso. La simplificación del paso 5 quedó documentada en el propio §4.
- [x] **Cero imports de Spring en `domain`/`application`** — `grep -rn org.springframework` sobre
      ambos paquetes devuelve 0 resultados; Spring solo aparece en `infrastructure`.
- [x] **Versiones GA verificadas** — el `pom.xml` no fija ninguna versión propia: todas vienen de
      los BOM del parent. El único `-SNAPSHOT` es la versión del artefacto en desarrollo
      (`0.2.0-SNAPSHOT`, heredada). Sin RC, sin milestones, sin `TODO(version-check)`.
- [x] **Spotless + Checkstyle** — BUILD SUCCESS, 0 violaciones.
- [x] **Cobertura ≥80/80** — 100% líneas y 100% ramas.
- [x] **Testcontainers si hay store real** — N/A: el único adaptador `out`
      (`InMemoryEgressPolicyAdapter`) devuelve una política en memoria, no habla con ningún store.
- [x] **Dependencias justificadas** — las 4 del `pom.xml` (`guardrails-core`, `spring-boot`
      provided, `junit-jupiter` test, `mockito-core` test) están en §7 del spec, una por una.
      Ninguna dependencia de otro módulo `guardrails-*` (ARCHITECTURE.md §5).
- [x] **Header Apache 2.0** — presente en los 30 archivos `.java` del módulo.
- [x] **README explica el puerto plugable** — `guardrails-egress-control/README.md`, sección
      `Replacing the policy source`: FQN del puerto, limitación del adaptador por defecto (solo
      cambia al reiniciar), ejemplo de política dinámica y la obligación de cachear con TTL.
      Añade `What this guardrail cannot do`, que deja claro que se verifica la declaración y no
      el tráfico real.
- [x] **Métodos ≤ ~25 líneas** — medición automática sobre las clases de producción: ninguno
      supera 25.
- [x] **Sin `return null` en `domain`/`application`** — 0 ocurrencias tras la corrección (ver
      abajo). La ausencia se modela con `Optional`, con `NotDeterminable` y con
      `NotAnEgressTool`.

## Rechazo de la primera pasada y su corrección

`DestinationExtractor.hostOfUri(String)` devolvía `null` por dos vías: el `catch
(URISyntaxException)` y —de forma no evidente— el propio `URI.getHost()`, que devuelve `null`
ante un host internacionalizado, que es justo el caso límite que este módulo documenta. Rechazado
por el checklist de "sin `return null` en dominio". `domain-builder` cambió la firma a
`Optional<String>` con `Optional.ofNullable(...)`, de modo que la firma expresa lo que antes solo
decía el Javadoc del JDK. Los 132 tests siguieron en verde **sin modificar ninguno**, y la
cobertura se mantuvo en 100%/100%.

## Hallazgos previos, ya resueltos durante el ciclo

1. `domain-builder` detectó que el paso 5 del spec preveía un estado inalcanzable (ninguna ruta
   ilegible y ningún destino a la vez); simplificó la condición y actualizó §4 del spec con la
   justificación.
2. `test-engineer` eliminó código muerto en `AllowedDestination` (`suffix.isBlank()` era
   inalcanzable porque `"*."` se normaliza a `"*"`), y corrigió tres tests propios que usaban
   `"garbage"` y `"12"` como valores "no host" cuando son hosts válidos de una etiqueta, igual
   que `localhost`.

## Verificación funcional destacada

- El matching por etiquetas rechaza `notexample.com` y `example.com.evil.com`, que un `endsWith`
  ingenuo aceptaría, y rechaza el apex bajo un patrón `*.dominio`.
- `https://evil.com@good.com/x` resuelve a `good.com`: el userinfo no engaña al parser.
- Con la allowlist vacía —la configuración por defecto— cualquier destino se deniega.
- Un destino ilegible produce `Deny` y nombra el argumento, no el valor.
- El motivo de un `Deny` sobre `https://api.evil.com/x?token=SECRET123` **no contiene el token**;
  las enumeraciones se deduplican y se cortan en cinco entradas con `and N more`.

## Siguiente paso

`update-docs` sobre `guardrails-egress-control`, y después `spec-architect` para el módulo 10,
`guardrails-anomaly-detector` (ARCHITECTURE.md §6) — en su propia rama, tras mergear esta.
