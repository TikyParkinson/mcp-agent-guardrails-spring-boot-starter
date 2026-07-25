# Spec — guardrails-core outbound SPI

> Extensión aditiva de `guardrails-core` según ARCHITECTURE.md §5.1. Autor: spec-architect.
> Paquete raíz: `io.github.tikyparkinson.mcpguardrails.core` (el mismo del módulo).
> Consumidor real que la motiva: `guardrails-credential-leak-guard` (módulo 8, ARCHITECTURE.md §6).
> Prerequisito cumplido: `guardrails-tool-integrity-DONE.md` aprobado.

## 1. Problema y alcance

El SPI actual (`Guardrail`) solo ve la invocación **antes** de que la tool se ejecute:
`GuardedToolCallHandler` evalúa la cadena y, si el veredicto es `Allow`, delega en el handler real
y devuelve su `CallToolResult` **sin inspeccionarlo**. Cualquier guardrail que necesite mirar lo
que la tool *devuelve* —fuga de credenciales, exfiltración de datos, output filtering en general—
no tiene punto de enganche. Esta extensión añade una **cadena de salida**: un segundo SPI,
`ResultGuardrail`, evaluado después de ejecutar la tool y antes de devolver el resultado al
agente, capaz de dejar pasar, **redactar** o bloquear la respuesta.

`Redact` es el motivo de que esto viva en el core y no en un guardrail suelto: sin él, "redactar"
solo puede significar "bloquear la llamada entera". Además, las decisiones de salida quedan en una
traza (`ResultVerdict`) equivalente al `ChainVerdict` de entrada, que es de donde el módulo 12
(`trifecta-correlator`) leerá.

**No-goals:**

- No modifica el SPI `Guardrail` ni `GuardrailDecision`: la cadena de entrada queda intacta.
- No implementa ningún guardrail de salida: esta extensión solo aporta el punto de extensión.
  El primer consumidor es `guardrails-credential-leak-guard`.
- No redacta `structuredContent`: se expone **solo lectura** para que un guardrail pueda
  escanearlo y decidir `Block` (fail-closed). Redacción estructurada queda fuera de alcance —
  ver Decisión de diseño 3.
- No inspecciona contenidos no textuales (`ImageContent`, `EmbeddedResource`…): pasan intactos.
  Ver Decisión de diseño 4.
- No añade propiedades de configuración: sin consumidores registrados la cadena es no-op, así que
  un flag de activación sería especulativo (ARCHITECTURE.md §5.1).
- No persiste nada ni añade dependencias Maven nuevas.

## 2. Modelo de dominio

Paquete `io.github.tikyparkinson.mcpguardrails.core.domain`. JDK puro, cero Spring.

```java
/**
 * Immutable snapshot of the result of one MCP tool invocation, as seen by the outbound chain.
 * Invariantes: agentId/toolName/occurredAt no null; textContents y structuredContent copiados
 * defensivamente (List.copyOf / Map.copyOf), nunca null (usar vacíos).
 *
 * textContents:      todo el texto redactable del resultado, en orden de aparición: el de cada
 *                    TextContent y el de cada EmbeddedResource cuyo `resource()` sea
 *                    TextResourceContents (una tool que devuelve un fichero como recurso
 *                    embebido es un canal de fuga de primera clase). Es lo único redactable:
 *                    un Redact debe devolver una lista del MISMO tamaño.
 * structuredContent: contenido estructurado del resultado cuando es un Map; vacío si es null o de
 *                    otro tipo. Solo lectura (ver No-goals).
 * error:             valor de isError() del resultado original (null se normaliza a false).
 */
record ToolResultContext(
    AgentId agentId,
    ToolName toolName,
    Instant occurredAt,
    List<String> textContents,
    Map<String, Object> structuredContent,
    boolean error) {

  /** Copia con los textos sustituidos. Falla si el tamaño no coincide. */
  ToolResultContext withTextContents(List<String> replacements) { ... }
}

/** Veredicto de un ResultGuardrail sobre el resultado de una tool. */
sealed interface ResultDecision permits PassThrough, Redact, Block {}

/** El resultado se devuelve tal cual. */
record PassThrough() implements ResultDecision {}

/**
 * El resultado se devuelve con los textos sustituidos.
 * Invariantes: sanitizedContents no null (List.copyOf); reason no null ni blank.
 */
record Redact(List<String> sanitizedContents, String reason) implements ResultDecision {}

/**
 * El resultado no llega al agente; se devuelve un error con la razón.
 * Invariante: reason no null ni blank.
 */
record Block(String reason) implements ResultDecision {}

/** Decisión emitida por un ResultGuardrail concreto, para la traza. */
record ResultEvaluation(String guardrailName, ResultDecision decision) {}

/** Resultado agregado de la cadena de salida: decisión final + traza completa. */
record ResultVerdict(ResultDecision finalDecision, List<ResultEvaluation> evaluations) {}

/**
 * Regla pura de combinación de la cadena de salida. Severidad: Block > Redact > PassThrough.
 * Entre iguales gana el primero en orden de evaluación.
 *
 * combine(evaluations, accumulatedContents):
 *   - si alguna evaluación es Block  -> ese primer Block
 *   - si alguna es Redact            -> Redact(accumulatedContents, razones unidas por "; ")
 *   - lista vacía o todo PassThrough -> PassThrough
 *
 * accumulatedContents es el estado de los textos tras aplicar en cascada los Redact previos
 * (lo aporta la cadena, el combinador sigue siendo puro).
 */
final class ResultDecisionCombiner {
  static ResultDecision combine(List<ResultEvaluation> evaluations, List<String> accumulated);
}
```

## 3. Puertos (contratos de application)

### 3.1 `ResultGuardrail` — puerto de salida (el SPI nuevo)

- Capa: `application.port.out`. Lo implementan los módulos guardrail (el primero,
  `credential-leak-guard`).
- Simétrico a `Guardrail`, con el mismo contrato de nombres y orden.

```java
public interface ResultGuardrail {

  /** Stable, unique name of this outbound guardrail. Never null nor blank. */
  String name();

  /** Evaluation order: lower runs earlier. Ties are broken by name() ascending. */
  default int order() {
    return 0;
  }

  /** Inspects the tool result. Never returns null. */
  ResultDecision inspect(ToolResultContext context);
}
```

### 3.2 `EvaluateToolResultUseCase` — puerto de entrada

- Capa: `application.port.in`. Lo invoca el adapter-in (`GuardedToolCallHandler`).
- Lo implementa: `ResultGuardrailChain` (application.usecase).

```java
public interface EvaluateToolResultUseCase {

  /** Evaluates the tool result through the outbound chain. Never returns null. */
  ResultVerdict evaluate(ToolResultContext context);
}
```

## 4. Caso de uso — `ResultGuardrailChain`

Simétrico a `GuardrailChain` (mismo orden determinista, misma detección de nombres duplicados en
el constructor, misma política fail-closed), con una diferencia: los `Redact` se **componen en
cascada**, porque cada guardrail debe ver lo que ya redactaron los anteriores.

1. Ordena los `ResultGuardrail` por `order()` y luego por `name()`; nombres duplicados ⇒
   `IllegalArgumentException` en el constructor.
2. `current := context`; `evaluations := []`.
3. Para cada guardrail, en orden:
   1. `decision := guardrail.inspect(current)`, protegido:
      - devuelve `null` ⇒ `Block("outbound guardrail <name> returned null")`;
      - lanza `RuntimeException` ⇒ `Block("outbound guardrail <name> failed: <SimpleName>")`
        (**fail-closed**: si el escáner de fugas revienta, el resultado no sale).
   2. Añade `new ResultEvaluation(name, decision)` a `evaluations`.
   3. Si `decision` es `Redact(sanitized, _)`:
      - si `sanitized.size() != current.textContents().size()` ⇒ la evaluación se sustituye por
        `Block("outbound guardrail <name> returned <n> contents, expected <m>")` (contrato roto,
        fail-closed);
      - si no, `current := current.withTextContents(sanitized)`.
   4. No hay corte: **todos** los guardrails se evalúan siempre, para que la traza sea completa
      (igual que la cadena de entrada).
4. Devuelve `new ResultVerdict(ResultDecisionCombiner.combine(evaluations, current.textContents()), evaluations)`.

Con la lista de guardrails vacía el resultado es `ResultVerdict(PassThrough, [])`: comportamiento
idéntico al de la versión anterior (ARCHITECTURE.md §5.1, "neutral when unused").

## 5. Adaptadores esperados

### 5.1 Adapter `in` — `GuardedToolCallHandler` (único archivo modificado)

Hoy: `case Allow _ -> delegate.apply(exchange, request)`. Pasa a delegar y después pasar el
resultado por la cadena de salida:

1. `result := delegate.apply(exchange, request)`.
2. `context := toResultContext(result, ...)`:
   - `textContents`: el texto de cada `McpSchema.TextContent` y el de cada
     `McpSchema.EmbeddedResource` cuyo `resource()` sea `TextResourceContents`, en orden de
     aparición;
   - `structuredContent`: `result.structuredContent()` si es `Map<String,Object>`, si no vacío;
   - `error`: `Boolean.TRUE.equals(result.isError())`;
   - `agentId`, `toolName`, `occurredAt`: los mismos que se usaron en la entrada.
3. `switch (resultUseCase.evaluate(context).finalDecision())`:
   - `PassThrough` ⇒ devuelve `result` **sin reconstruirlo** (identidad, coste cero);
   - `Redact(sanitized, _)` ⇒ reconstruye:
     `new McpSchema.CallToolResult(newContent, result.isError(), result.structuredContent(), result.meta())`,
     sustituyendo posicionalmente cada contenido redactable —`TextContent` por
     `new McpSchema.TextContent(tc.annotations(), sanitized.get(i), tc.meta())` y
     `EmbeddedResource` por uno equivalente con un `TextResourceContents` de mismo `uri`,
     `mimeType` y `meta`— y dejando intacto cualquier otro `Content`;
   - `Block(reason)` ⇒ `errorResult("Tool result blocked by guardrails: " + reason)`, el mismo
     helper que ya existe.

**Retrocompatibilidad (obligatoria, ARCHITECTURE.md §5.1)**: se añade un constructor con el
parámetro `EvaluateToolResultUseCase`; el constructor actual de 4 parámetros se conserva y delega
en el nuevo con una cadena de salida vacía (`new ResultGuardrailChain(List.of())`). Ningún
llamador existente se rompe ni cambia de comportamiento.

### 5.2 Adapter `in` — `GuardrailToolDecorator`

Se añade una sobrecarga `decorate(specification, useCase, resultUseCase, agentIdResolver, clock)`.
La firma actual se conserva y delega en la nueva con cadena de salida vacía.

### 5.3 Adaptadores `out`

Ninguno. Esta extensión no habla con ningún store ⇒ **sin Testcontainers** (ARCHITECTURE.md §8).

## 6. Configuración Spring Boot

Ninguna propiedad nueva (ver No-goals). El cableado en `spring-boot-starter` —bean
`ResultGuardrailChain` a partir de la lista de `ResultGuardrail` del contexto, y paso al
decorador— se especifica y ejecuta junto al módulo consumidor, no aquí.

## 7. Dependencias Maven propuestas

Ninguna nueva. Se usan las que `guardrails-core` ya declara y justifica en
`docs/specs/guardrails-core-spec.md`:

| Dependencia | Por qué |
|---|---|
| `io.modelcontextprotocol.sdk:mcp-core` | `McpSchema.CallToolResult`, `Content`, `TextContent` en el adapter-in. Ya presente. |
| `org.junit.jupiter:junit-jupiter` (test) | Tests de la cadena y del handler. Ya presente. |
| `org.mockito:mockito-core` (test) | Dobles de `ResultGuardrail` y del handler delegado. Ya presente. |

Cualquier dependencia adicional que aparezca en el `pom.xml` es rechazo automático.

## 8. Diagrama del hexágono

```
             MCP tool call
                  │
                  ▼
   ┌──────────────────────────────┐
   │  adapter/in/mcp              │
   │  GuardedToolCallHandler      │
   └───────┬──────────────▲───────┘
     (1)   │              │  (3) ResultVerdict
  inbound  │              │      PassThrough | Redact | Block
   chain   │              │
           │   ┌──────────┴──────────────────────────┐
           │   │ application                          │
           │   │  port.in  EvaluateToolResultUseCase  │
           │   │  usecase  ResultGuardrailChain       │
           │   │  port.out ResultGuardrail  ◄─────────┼── implementado por
           │   └──────────┬───────────────────────────┘   guardrails-credential-leak-guard
           │              │                                (módulo 8)
           │              ▼
           │   ┌──────────────────────────────────────┐
           │   │ domain (JDK puro)                     │
           │   │  ToolResultContext                    │
           │   │  ResultDecision: PassThrough|Redact|Block
           │   │  ResultEvaluation, ResultVerdict      │
           │   │  ResultDecisionCombiner               │
           │   └──────────────────────────────────────┘
           │
           ▼  (2) la tool real se ejecuta entre (1) y (3)
      delegate.apply(...)
```

## 9. Decisiones de diseño

1. **La cadena de salida es un SPI separado (`ResultGuardrail`), no un método `default` en
   `Guardrail`.** Un método `default` habría sido igual de retrocompatible, pero obligaría a todo
   guardrail de entrada a arrastrar un método que no usa y mezclaría dos contratos con veredictos
   distintos (`GuardrailDecision` vs `ResultDecision`). Interfaces separadas permiten además que
   un módulo implemente solo salida, solo entrada, o ambas.

2. **Fail-closed también a la salida.** Una excepción, un `null` o un `Redact` de tamaño
   incorrecto se traducen en `Block`, no en `PassThrough`. Un escáner de fugas que falla no puede
   convertirse en un "adelante": es exactamente el escenario en que el secreto se escaparía.

3. **`structuredContent` se expone pero no se redacta.** Redactarlo exigiría recorrer y reescribir
   una estructura arbitraria y devolverla por el mismo canal, lo que duplicaría el contrato de
   `Redact`. Exponerlo en solo lectura permite al guardrail **detectar** el secreto y responder
   `Block`, que es fail-closed y suficiente. Una extensión posterior puede añadir redacción
   estructurada de forma aditiva si aparece un consumidor real.

4. **Solo se redacta texto: `TextContent` y `EmbeddedResource` con `TextResourceContents`.** Ahí
   viaja el output textual de las tools, incluido el caso canónico de fuga —una tool que devuelve
   un fichero como recurso embebido—. Los contenidos binarios (`ImageContent`,
   `BlobResourceContents`) y los `ResourceLink` se devuelven intactos; un guardrail que los
   considere peligrosos siempre puede responder `Block`.

5. **`PassThrough` devuelve el objeto original por identidad**, sin reconstruir el
   `CallToolResult`. Así el caso mayoritario (nadie redacta) no paga ningún coste ni riesgo de
   perder campos del resultado.

6. **La traza de salida es un `ResultVerdict` propio, no se mezcla en `ChainVerdict`.** Son dos
   momentos distintos del ciclo de vida con veredictos de tipos distintos; unificarlos obligaría a
   cambiar `ChainVerdict`, que es exactamente lo que §5.1 prohíbe.
