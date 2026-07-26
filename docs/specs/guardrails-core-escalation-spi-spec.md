# Spec — `guardrails-core-escalation-spi` (extensión aditiva de core)

> Extensión de `guardrails-core` bajo ARCHITECTURE.md §5.1. Se especifica y construye **antes**
> que `guardrails-approval-gate`, que es su único consumidor, en la misma rama.

## 1. Problema y alcance

`guardrails-core` ya modela `Escalate` como decisión de la cadena, pero **no tiene dónde
ejecutarla**. Hoy `GuardedToolCallHandler.apply()` la resuelve así:

```java
case Escalate(String reason) ->
    errorResult("Tool call requires approval (escalated by guardrails): " + reason);
```

Es decir: escalar y denegar producen el mismo efecto observable, un error, y la palabra
"approval" del mensaje describe algo que no existe.

`guardrails-approval-gate` (módulo 11) tiene que convertir esa escalación en una pausa real
seguida de una decisión humana. **No puede hacerlo como un `Guardrail`**, y esto no es una
preferencia de diseño sino una consecuencia verificable del código actual:

1. `DecisionCombiner.combine()` es monótono hacia el bloqueo (`Deny` > `Escalate` > `Allow`):
   toma el primer `Deny`, si no el primer `Escalate`, si no `Allow`. Un guardrail que devolviera
   `Allow` tras la aprobación humana **no puede rebajar** el `Escalate` que emitió otro
   guardrail. La aprobación sería ignorada.
2. Un `Guardrail` se ejecuta *construyendo* el veredicto, no después de él: no ve
   `ChainVerdict.finalDecision()`, así que no puede saber que la cadena acabó en `Escalate`.
3. El único punto donde "escalado" se traduce en un efecto es el `switch` de
   `GuardedToolCallHandler`, dentro de core.

Por eso hace falta un punto de extensión en core: un SPI que el handler consulte cuando el
veredicto final sea `Escalate`.

**No-goals.** No define cómo se pide la aprobación, quién la concede, dónde se almacenan las
solicitudes pendientes ni cuánto se espera: todo eso es `guardrails-approval-gate`. Core solo
gana el hueco donde enchufarlo. Tampoco cambia la semántica de `Escalate` en la cadena ni el
combinador.

## 2. Cumplimiento de §5.1

| Condición | Cómo se cumple |
|---|---|
| **Solo aditivo** | Un tipo sellado nuevo, una interfaz nueva y una sobrecarga nueva del constructor de `GuardedToolCallHandler`. Ninguna firma existente cambia. |
| **Neutro sin uso** | Sin resolver registrado, el `switch` produce exactamente el mismo `errorResult` de hoy. Byte a byte el mismo mensaje. |
| **Consumidor real** | `guardrails-approval-gate`, especificado a la vez y en la misma rama. |
| **Spec propio** | Este documento; pasa por los 5 agentes antes del módulo 11. |
| **No es breaking** | Los 9 módulos publicados compilan y se comportan igual sin tocar nada. |

## 3. Modelo de dominio (añadido a `core.domain`)

```java
/**
 * Resultado de resolver una escalación. Cerrado a dos casos: o se ejecuta o no. No existe un
 * tercer estado "pendiente" — eso vive dentro del resolver, no en su respuesta.
 */
sealed interface EscalationOutcome permits ApprovedExecution, RejectedExecution {}

/**
 * Un humano (o el sistema en que delega) autorizó la ejecución.
 * approvedBy: identidad de quien aprobó, para la traza. No blank.
 */
record ApprovedExecution(String approvedBy) implements EscalationOutcome {}

/**
 * La ejecución no se autoriza: rechazo explícito, expiración del plazo, o imposibilidad de
 * preguntar. Los tres casos convergen aquí a propósito — desde el punto de vista del handler
 * son el mismo hecho: nadie autorizó esto.
 * reason: motivo legible que viaja al agente. No blank.
 */
record RejectedExecution(String reason) implements EscalationOutcome {}
```

## 4. Puerto (añadido a `core.application.port.out`)

```java
/**
 * Punto de extensión que decide qué ocurre cuando la cadena resuelve Escalate.
 *
 * <p>Sin implementación registrada, una escalación devuelve un error al agente, que es el
 * comportamiento histórico. Con una, la invocación se retiene hasta que este método responde.
 *
 * <p>Se invoca de forma síncrona en el hilo de la llamada MCP: una implementación que espere a
 * un humano bloquea ese hilo mientras tanto. Es responsabilidad del implementador acotar cuánto.
 */
public interface EscalationResolver {

  /** Resuelve la escalación. Nunca devuelve null. */
  EscalationOutcome resolve(ToolInvocationContext context, ChainVerdict verdict);
}
```

Recibe el `ChainVerdict` completo, no solo el motivo: quien decide necesita ver qué dijo **cada**
guardrail, no solo el que ganó la combinación. Una escalación de `anomaly-detector` acompañada de
un `Allow` de `authz` se lee distinto que la misma escalación con `authz` al límite.

## 5. Cambio en `GuardedToolCallHandler` (único archivo de core modificado)

```java
case Escalate(String reason) -> resolveEscalation(exchange, request, context, verdict, reason);
```

```java
private CallToolResult resolveEscalation(...) {
  if (escalationResolver == null) {
    return errorResult("Tool call requires approval (escalated by guardrails): " + reason);
  }
  return switch (safeResolve(context, verdict, reason)) {
    case ApprovedExecution _ -> guardedDelegate(exchange, request, context);
    case RejectedExecution(String why) -> errorResult("Tool call not approved: " + why);
  };
}
```

Tres propiedades que el código debe garantizar:

- **Fail-closed ante fallo del resolver.** Si `resolve` lanza `RuntimeException` o devuelve
  `null`, se trata como `RejectedExecution`. Un canal de aprobación caído no puede convertirse en
  una vía de ejecución libre. Mismo criterio que `GuardrailChain.safeEvaluate`.
- **Una ejecución aprobada sigue pasando por la cadena de salida.** La rama aprobada llama a
  `guardedDelegate`, no a `delegate` directamente: aprobar la ejecución no autoriza a saltarse la
  redacción del resultado. Aprobar *lanzar* la tool y aprobar *ver su salida en crudo* son cosas
  distintas.
- **Un solo resolver.** El constructor acepta uno o ninguno. No hay combinación de resolvers: dos
  autoridades de aprobación sobre la misma invocación no tienen un orden evidente, y adivinarlo
  sería peor que exigir que el operador elija.

## 6. Configuración Spring Boot

Ninguna. Esta extensión no añade properties: la presencia o ausencia del bean `EscalationResolver`
es toda la configuración. Las properties del comportamiento las define el módulo 11.

## 7. Dependencias Maven

Ninguna nueva. Solo tipos del JDK y de `guardrails-core`.

## 8. Diagrama

```
                       ChainVerdict.finalDecision()
                                 │
        ┌────────────────────────┼────────────────────────┐
     Allow                     Deny                   Escalate
        │                        │                        │
        │                        │              ┌─────────┴─────────┐
        │                        │         resolver ausente     resolver presente
        │                        │              │                   │
        │                        │              │        EscalationResolver.resolve(ctx, verdict)
        │                        │              │                   │
        │                        │              │        ┌──────────┴──────────┐
        │                        │              │   ApprovedExecution    RejectedExecution
        │                        │              │          │                   │
        ▼                        ▼              ▼          ▼                   ▼
  guardedDelegate           errorResult    errorResult  guardedDelegate    errorResult
        │                                                  │
        └──────────────► cadena de salida ◄────────────────┘
                    (PassThrough / Redact / Block)
```

## 9. Decisiones de diseño

1. **El SPI vive en core, no en el módulo 11.** El único punto del código donde `Escalate` se
   convierte en un efecto está en `GuardedToolCallHandler`, dentro de core. Cualquier alternativa
   —que el módulo 11 envuelva el handler, o que reemplace el decorador— exigiría duplicar la
   traducción MCP↔dominio que core ya hace, y dejaría dos caminos por los que puede ejecutarse
   una tool. Uno solo es auditable; dos, no.

2. **`RejectedExecution` no distingue rechazo de expiración.** El handler haría lo mismo con
   ambos, y separarlos en el tipo obligaría a todo consumidor a tratar un caso que no le cambia
   la conducta. La distinción sí importa para quien opera el sistema, y por eso viaja en el
   texto del motivo, que es donde se lee.

3. **El resolver recibe el veredicto, no el motivo.** Cuesta lo mismo y da al aprobador el
   contexto completo. Reducirlo a un `String` habría obligado al módulo 11 a pedir a core una
   segunda extensión en cuanto quisiera mostrar qué guardrail escaló.

4. **Aprobar no salta la cadena de salida.** Es la diferencia entre "puedes ejecutar esto" y
   "puedes ver todo lo que esto devuelva". Un `credential-leak-guard` configurado para redactar
   debe seguir redactando una respuesta cuya ejecución aprobó un humano.
