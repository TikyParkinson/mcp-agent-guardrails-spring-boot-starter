# guardrails-core — extensión aditiva: motivo en las decisiones permisivas

> Extensión de `guardrails-core` bajo ARCHITECTURE.md §5.1. Es la **quinta** del proyecto, después
> de `outbound-spi`, `escalation-spi`, `session-metadata` y `decorator-wiring`.
>
> Consumidor: `guardrails-audit-full-coverage`. Este spec va **antes** que el suyo, y pasa por los
> agentes por separado.

## 1. Problema y alcance

el informe de validación de 0.2.0 fija como requisito que **todo quede auditado, incluidos los `Allow`**. Al
intentar cumplirlo desde un auditor a nivel de veredicto aparece un problema en el modelo de core:

```java
public record Allow() implements GuardrailDecision {}      // sin motivo
public record Deny(String reason) implements GuardrailDecision {}
public record Escalate(String reason) implements GuardrailDecision {}
public record PassThrough() implements ResultDecision {}   // sin motivo
```

`Deny` y `Escalate` llevan su `reason`; `Allow` y `PassThrough` no. Hoy `AuthzGuardrail` publica
`DECISION_ALLOW` con detalle `rule[0]` o `default`, un dato que **no está en la decisión** sino en
un tipo interno de authz. Un auditor genérico que recorra el `ChainVerdict` no puede recuperarlo, y
un `Allow` auditado sin motivo no le dice nada a un auditor: «se permitió», ¿por qué regla?

Este spec añade ese hueco al modelo, sin romper nada de lo publicado.

**No-goals**

- No cambia cómo se combinan las decisiones. `DecisionCombiner` ignora el motivo, y esto no lo toca.
- No obliga a ningún guardrail a aportar motivo. Quien no lo haga sigue construyendo `new Allow()`.
- No añade auditoría: eso es el spec consumidor. Aquí solo se transporta el dato.
- No toca `Deny`, `Escalate`, `Block` ni `Redact`: ya llevan `reason`.

## 2. Modelo de dominio

Dos records existentes ganan un componente, cada uno con un constructor de conveniencia que
preserva la forma antigua:

```java
// io.github.tikyparkinson.mcpguardrails.core.domain
public record Allow(String reason) implements GuardrailDecision {
  public Allow() { this(""); }          // forma histórica: sin motivo
  public Allow { Objects.requireNonNull(reason, "reason"); }
}

public record PassThrough(String reason) implements ResultDecision {
  public PassThrough() { this(""); }
  public PassThrough { Objects.requireNonNull(reason, "reason"); }
}
```

**Invariantes**

- `reason` nunca es `null`. La ausencia de motivo es `""`, no `null`: un auditor que reciba la
  cadena vacía sabe que no hay motivo, mientras que un `null` obliga a cada consumidor a repetir la
  comprobación y convierte el olvido de uno en un `NullPointerException` en tiempo de auditoría.
- `reason` no se valida más allá de eso. Un motivo en blanco es legítimo y frecuente.

**Por qué esto es aditivo, verificado y no supuesto**

| Comprobación | Resultado |
|---|---|
| `new Allow()` en el proyecto | 12 en `main`, 35 en `test` — todos siguen compilando por el constructor de conveniencia |
| Deconstrucción `case Allow()` | **0 ocurrencias**; sería lo único que rompería |
| `case Allow _` / `case Allow a` | siguen compilando: son type patterns, indiferentes a los componentes |
| Compatibilidad binaria | el descriptor `<init>()` sigue existiendo, así que un consumidor ya compilado no ve un `NoSuchMethodError` |

Comprobado ejecutando un caso reducido con JDK 25 antes de escribir este spec.

**Consecuencia que hay que asumir, no esconder:** `equals`, `hashCode` y `toString` pasan a incluir
`reason`. `new Allow().equals(new Allow())` sigue siendo `true`, pero
`new Allow("rule[0]").equals(new Allow())` es `false`. Un test que compare una decisión con motivo
contra `new Allow()` empezará a fallar. Es el comportamiento correcto —son decisiones distintas— y
`test-engineer` debe tratar cualquier fallo así como un test que hay que actualizar, no como una
regresión.

## 3. Puertos

**Ninguno nuevo.** `Guardrail` y `ResultGuardrail` no cambian de firma: siguen devolviendo
`GuardrailDecision` y `ResultDecision`, solo que ahora esas decisiones pueden llevar un motivo.

## 4. Caso de uso

No hay caso de uso nuevo. El flujo afectado es el existente:

1. `GuardrailChain` recorre los guardrails y construye una `GuardrailEvaluation` por cada uno, con
   la decisión que devolvió — motivo incluido si lo aportó.
2. `DecisionCombiner` combina las decisiones. **Sin cambios**: sigue quedándose con la más
   restrictiva, y el motivo viaja dentro de la decisión elegida.
3. El `ChainVerdict` resultante ya contiene, por cada guardrail, su decisión con su motivo. Eso es
   todo lo que el auditor del spec consumidor necesita.

Único cambio de comportamiento en un módulo publicado: `AuthzGuardrail` pasa a construir
`new Allow(decision.source())` en vez de `new Allow()`, para que el `rule[0]` / `default` que hoy
solo existe dentro de su evento de auditoría viaje en la decisión. Sin esto, F-1b pierde
información al quitarle a authz su dependencia de `guardrails-audit`.

Los demás guardrails **no se tocan**. Aportar motivo en un `Allow` es opcional, y salvo authz
ninguno tiene hoy nada que decir.

## 5. Adaptadores esperados

Ninguno. Este spec no añade adaptadores; modifica dos value objects de `domain` y una línea de un
adaptador `in` existente (`AuthzGuardrail`).

## 6. Configuración Spring Boot

Ninguna property nueva.

## 7. Dependencias Maven propuestas

Ninguna. `guardrails-core` no gana dependencias y `guardrails-authz` tampoco.

## 8. Diagrama del hexágono

```
guardrails-authz (adapter-in)                    guardrails-core
┌──────────────────────────┐                     ┌────────────────────────────┐
│ AuthzGuardrail           │                     │ domain                     │
│   PolicyDecision.source  │──── new Allow(src) ─┼─▶ Allow(reason)     ← nuevo│
│   "rule[0]" / "default"  │                     │   PassThrough(reason)← nuevo│
└──────────────────────────┘                     │   Deny(reason)   (sin tocar)│
                                                 │   Escalate(reason)(sin tocar)│
                                                 └─────────────┬──────────────┘
                                                               │
                                     GuardrailChain            ▼
                                     construye  ──▶ GuardrailEvaluation(name, decision)
                                                               │
                                                               ▼
                                                       ChainVerdict
                                                               │
                            (lo consume el auditor del spec guardrails-audit-full-coverage,
                             que vive en spring-boot-starter — §5: el puente es un adaptador)
```

## Decisiones de diseño

1. **Se añade el motivo a la decisión, no a `GuardrailEvaluation`.** Ambas eran viables y ambas
   resultaron aditivas al comprobarlo (nadie deconstruye `GuardrailEvaluation` en `main`). Se
   elige la decisión porque es donde nace el dato: el guardrail sabe por qué permite, y la cadena
   solo transporta. Ponerlo en la evaluación obligaría a `GuardrailChain` a preguntarle el motivo
   al guardrail por un canal aparte, que es exactamente el método extra que §5.1 desaconseja.

2. **La ausencia es `""`, no `null` ni `Optional`.** Coherente con el resto del proyecto —
   `NewAuditEvent.detail` ya es un `String` no nulo que admite vacío— y con la decisión tomada en
   `session-metadata`, donde se prefirió una clave ausente a un valor vacío por el mismo motivo:
   que el consumidor no tenga que repetir una comprobación que se puede eliminar de raíz.

3. **`PassThrough` cambia aunque hoy nadie lo aproveche.** Los tres `new PassThrough()` del
   proyecto no tienen motivo que dar. Se incluye igualmente porque el requisito de
   el informe de validación de 0.2.0 cubre explícitamente «el resultado de la cadena de salida — redacción,
   bloqueo o **paso limpio**», y dejar la cadena de salida a medias obligaría a una sexta extensión
   de core dentro de dos semanas. El coste ahora es de dos líneas.

4. **Solo `AuthzGuardrail` se modifica.** Es el único módulo publicado que hoy tiene un motivo para
   un `Allow` y lo está perdiendo. Recorrer los otros ocho buscando motivos que inventar sería
   scope creep, y un motivo inventado en un log de auditoría es peor que un campo vacío.

5. **No se toca `DecisionCombiner`.** Podría parecer que al combinar habría que concatenar motivos.
   No: el combinador elige **una** decisión, y esa decisión conserva su motivo. Los motivos del
   resto no se pierden — siguen en sus `GuardrailEvaluation` respectivas dentro del `ChainVerdict`,
   que es precisamente lo que el auditor va a recorrer.

---

**Listo para `domain-builder`: implementar `docs/specs/guardrails-core-decision-detail-spec.md`.**
