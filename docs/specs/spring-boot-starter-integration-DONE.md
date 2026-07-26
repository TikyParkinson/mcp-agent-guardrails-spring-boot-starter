# spring-boot-starter — integración de los 12 módulos — APROBADO

**Fecha:** 2026-07-27
**Rama:** `feature/starter-integration`
**Specs:** `spring-boot-starter-integration-spec.md`, `guardrails-core-decorator-wiring-spec.md`

## Cobertura

| Contador | Resultado | Umbral |
|---|---|---|
| Líneas | **100 %** (99/99) | 80 % |
| Ramas | **100 %** (16/16) | 80 % |

34 tests, 0 fallos. `mvn -pl spring-boot-starter -am spotless:check checkstyle:check verify`
→ BUILD SUCCESS, 0 violaciones de Checkstyle.

## Checklist de ARCHITECTURE.md §9

| # | Check | Evidencia |
|---|---|---|
| 1 | Código fiel al spec | Las 8 decisiones de diseño cubren toda desviación. La 8 documenta el back-off conjunto de approval/trifecta |
| 2 | Sin Spring en `domain`/`application` | `grep org.springframework` en core → 0 |
| 3 | Versiones GA | Sin RC/milestone. `0.2.0-SNAPSHOT` es la versión propia en desarrollo |
| 4 | Spotless + Checkstyle | BUILD SUCCESS, 0 violaciones |
| 5 | Jacoco ≥80/80 | 100 % / 100 % |
| 6 | Testcontainers | N/A: el starter no aporta adaptadores `out` con store real |
| 7 | Dependencias justificadas | Las 6 nuevas en el spec de integración §7; las 11 previas en `spring-boot-starter-spec.md` §7 |
| 8 | Header Apache 2.0 | 14/14 ficheros nuevos y modificados |
| 9 | README | **Pendiente de `update-docs`**: solo cubre 5 de los 11 módulos |
| 10 | Métodos ≤25 líneas | 0 infracciones |
| 11 | Sin `return null` en dominio | 0 |

## Los tres bloqueos, resueltos

1. **Cadena de salida muerta** — el post-processor llamaba a `decorate()` de 4 args, así que
   `EvaluateToolResultUseCase` nunca llegaba. Sin esto, `credential-leak-guard` habría detectado
   secretos en los argumentos pero no habría redactado los de las respuestas: media protección
   presentada como completa.
2. **`EscalationResolver` inalcanzable** — `GuardrailToolDecorator` no tenía sobrecarga que lo
   aceptara. Resuelto con una extensión aditiva de core bajo §5.1, la cuarta del proyecto.
3. **Catálogo de tool-integrity vacío** — nadie lo poblaba. Se puebla desde el post-processor al
   decorar, no desde un `ApplicationRunner`, que dejaría una ventana para la primera invocación.

## Defecto encontrado durante la revisión

`test-engineer` detectó que los 4 métodos `@Bean` de use cases en approval y trifecta eran código
muerto y, peor, rompían el arranque si el operador registraba el suyo
(`expected single matching bean but found 2`). Corregido y verificado: el contexto arranca y el
guardrail consume el bean del operador.

## Ciclo de revisión

1. **Rechazo** — la corrección anterior contradecía la regla de §5.1 sin decisión documentada.
2. `spec-architect` — decisión de diseño 8, excepción en §5.1 y tabla de §5.1 corregida.
3. **Aprobado.**

## Siguiente

`vulnerability-scanner` sobre las dependencias nuevas del artefacto publicado, y después
`update-docs` para el check 9.
