# Spec — guardrails-core

> Módulo 1 según ARCHITECTURE.md §6. Autor: spec-architect.
> Paquete raíz: `io.github.tikyparkinson.mcpguardrails.core`.

## 1. Problema y alcance

Los cuatro guardrails (audit, authz, injection-guard, ratelimit) necesitan un vocabulario común
para describir "una invocación de tool MCP" y "la decisión de un guardrail", además de un
mecanismo de composición que evalúe todos los guardrails registrados en orden determinista y
combine sus decisiones. `guardrails-core` provee ese contrato compartido: el modelo
`ToolInvocationContext`, la decisión `GuardrailDecision` (`Allow`/`Deny`/`Escalate`), el puerto
`Guardrail` que cada feature implementa, y la cadena `GuardrailChain` que los orquesta. También
provee el adapter-in único de todo el proyecto: el interceptor que envuelve el handler de tools
del MCP Java SDK y consulta la cadena antes de ejecutar la tool real.

**No-goals:**
- No implementa ningún guardrail concreto (ni auditoría, ni authz, ni injection, ni ratelimit).
- No persiste nada: core no tiene estado, por tanto no define puertos de store ni adaptadores out
  con Testcontainers.
- No contiene autoconfiguración de Spring Boot (`@AutoConfiguration` vive en `spring-boot-starter`);
  aquí solo se define la clase `@ConfigurationProperties` base para que el starter la registre.
- No expone API reactiva (`Mono`/`Flux`): la evaluación es síncrona. Si en el futuro se soporta
  `McpAsyncServer`, será otro adapter-in, no un cambio de contrato.
- No decide qué significa `Escalate` operativamente (eso lo definirá cada consumidor); core solo
  lo modela y, en el interceptor, lo trata de forma conservadora (ver §4).

## 2. Modelo de dominio

Paquete `io.github.tikyparkinson.mcpguardrails.core.domain`. Cero imports fuera del JDK.

```java
/** Identidad del agente que invoca. Invariante: value no nulo ni blank. */
record AgentId(String value) {}

/** Nombre de la tool invocada. Invariante: value no nulo ni blank. */
record ToolName(String value) {}

/**
 * Snapshot inmutable de una invocación de tool MCP.
 * Invariantes: ningún campo nulo; arguments y metadata se copian defensivamente
 * (Map.copyOf) en el constructor canónico.
 */
record ToolInvocationContext(
    AgentId agentId,
    ToolName toolName,
    Instant occurredAt,
    Map<String, Object> arguments,
    Map<String, Object> metadata) {}

/** Resultado de evaluar un guardrail. Variantes cerradas, sin null posible. */
sealed interface GuardrailDecision permits Allow, Deny, Escalate {}
record Allow() implements GuardrailDecision {}
record Deny(String reason) implements GuardrailDecision {}      // reason no nulo ni blank
record Escalate(String reason) implements GuardrailDecision {}  // reason no nulo ni blank

/**
 * Resultado agregado de la cadena: la decisión final más la lista (inmutable, en orden
 * de evaluación) de decisiones individuales por guardrail, para trazabilidad.
 * Invariantes: finalDecision no nulo; evaluations copiado defensivamente (List.copyOf).
 */
record ChainVerdict(GuardrailDecision finalDecision, List<GuardrailEvaluation> evaluations) {}

/** Decisión de un guardrail identificado por nombre. Invariante: campos no nulos, name no blank. */
record GuardrailEvaluation(String guardrailName, GuardrailDecision decision) {}
```

**Regla de combinación (dominio puro, clase `DecisionCombiner` o método estático en
`ChainVerdict`):** la severidad es `Deny > Escalate > Allow`. La decisión final de una lista de
evaluaciones es la de mayor severidad; a igual severidad gana la primera en orden de evaluación.
Lista vacía ⇒ `Allow` (cadena sin guardrails no bloquea).

## 3. Puertos (contratos de application)

Paquete `io.github.tikyparkinson.mcpguardrails.core.application.port`.

### 3.1 `Guardrail` — SPI que implementan los demás módulos

- Capa: `application.port.out` (es lo que la cadena necesita "del mundo exterior": cada feature).
- Lo implementan: los módulos `guardrails-audit`, `guardrails-authz`, etc. (sus casos de uso
  serán adaptados a esta interfaz). En core, ninguna implementación de producción; solo dobles
  de test.

```java
public interface Guardrail {
  /** Nombre estable y único del guardrail (ej. "authz"). Nunca nulo ni blank. */
  String name();

  /** Orden de evaluación: menor = antes. Default 0; desempate por name() ascendente. */
  default int order() { return 0; }

  /** Evalúa el contexto. Nunca devuelve null. No debe lanzar para casos de negocio (usa Deny). */
  GuardrailDecision evaluate(ToolInvocationContext context);
}
```

### 3.2 `EvaluateToolInvocationUseCase` — puerto de entrada

- Capa: `application.port.in`.
- Lo invoca: el adapter-in (interceptor MCP). Lo implementa: `GuardrailChain` (application).

```java
public interface EvaluateToolInvocationUseCase {
  ChainVerdict evaluate(ToolInvocationContext context);
}
```

## 4. Caso de uso — `GuardrailChain`

Clase `GuardrailChain` en `io.github.tikyparkinson.mcpguardrails.core.application`, implementa
`EvaluateToolInvocationUseCase`. Recibe en el constructor `List<Guardrail>` (puede ser vacía).

1. En construcción: ordena los guardrails por `order()` ascendente, desempate por `name()`
   ascendente; guarda copia inmutable. Rechaza (IllegalArgumentException) nombres duplicados.
2. `evaluate(context)`:
   1. Para cada guardrail en orden, invoca `evaluate(context)`.
   2. Si un guardrail lanza una excepción inesperada, se registra como
      `Deny("guardrail <name> failed: <exceptionClass>")` — fail-closed, la cadena no revienta.
   3. Acumula `GuardrailEvaluation(name, decision)` en orden.
   4. **No hay short-circuit**: se evalúan todos siempre, para que la trazabilidad (y la futura
      auditoría) vea el cuadro completo. Decisión de diseño, ver §9.
   5. Combina con la regla de severidad de §2 y devuelve `ChainVerdict`.
3. Ramas de retorno:
   - Todos `Allow` (o lista vacía) ⇒ `ChainVerdict(Allow, evaluations)`.
   - Algún `Deny` ⇒ `ChainVerdict` con el **primer** `Deny`.
   - Sin `Deny` pero algún `Escalate` ⇒ `ChainVerdict` con el **primer** `Escalate`.

## 5. Adaptadores esperados

### Adapter-in: `GuardedToolCallHandler`

Paquete `io.github.tikyparkinson.mcpguardrails.core.adapter.in.mcp`.

Punto de intercepción verificado contra MCP Java SDK 2.0.0: el handler de una tool síncrona es
`BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>`
(campo `callHandler` de `McpServerFeatures.SyncToolSpecification`). El adaptador es un
**decorador** de ese `BiFunction`:

```java
public final class GuardedToolCallHandler
    implements BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> {
  // ctor: (BiFunction delegate, EvaluateToolInvocationUseCase useCase, AgentIdResolver resolver, Clock clock)
}
```

Comportamiento:
1. Construye `ToolInvocationContext` desde el `CallToolRequest` (name, arguments) + `AgentId`
   resuelto + `clock.instant()`. `AgentIdResolver` es una interfaz funcional del adapter-in
   (`AgentId resolve(McpSyncServerExchange exchange)`) con default que usa
   `exchange.getClientInfo().name()` si existe, o `AgentId("unknown")`.
2. Invoca el caso de uso y hace pattern matching del `finalDecision`:
   - `Allow` ⇒ delega en el handler original.
   - `Deny(reason)` ⇒ devuelve `CallToolResult` con `isError=true` y el reason como contenido
     de texto; **no** ejecuta la tool.
   - `Escalate(reason)` ⇒ tratamiento conservador: igual que Deny (no ejecuta), con mensaje que
     indica que requiere aprobación. Decisión de diseño, ver §9.

Se acompaña de un helper estático `GuardrailToolDecorator.decorate(SyncToolSpecification, ...)`
que devuelve una copia de la spec con el handler envuelto, para que el starter (módulo 6) lo
aplique a todas las tools registradas.

### Adapter-out

**Ninguno.** Core no tiene estado ni I/O de salida. Las implementaciones del puerto `Guardrail`
llegan de los demás módulos. Por tanto: sin adaptador in-memory por defecto y sin Testcontainers
en este módulo (ARCHITECTURE.md §8 solo exige Testcontainers para adaptadores out con store real,
que aquí no existen).

## 6. Configuración Spring Boot

Solo la clase de propiedades (en `io.github.tikyparkinson.mcpguardrails.core.infrastructure`);
el registro `@AutoConfiguration` vive en el módulo `spring-boot-starter`.

| Propiedad | Tipo | Default | Significado |
|---|---|---|---|
| `mcp.guardrails.enabled` | boolean | `true` | Interruptor global: si es false, el starter no envuelve handlers. |

Clase: `@ConfigurationProperties(prefix = "mcp.guardrails") GuardrailsCoreProperties(boolean enabled)`.

## 7. Dependencias Maven propuestas

Versiones GA verificadas en Maven Central el 2026-07-24. El parent pom (raíz) fija:
`spring-boot-dependencies:4.1.0` (import), `mcp-bom:2.0.0` (import), `junit-bom:6.1.2` (import),
Java 25 `--release 25`, `jacoco-maven-plugin:0.8.15`, `spotless-maven-plugin:3.8.0`
(google-java-format `1.35.0`), `maven-checkstyle-plugin:3.6.0` (checkstyle `13.8.0`).
Nota: Spring AI GA vigente es `2.0.0`, pero **core no la necesita** (el interceptor decora el
SDK MCP directamente, no la capa Spring AI).

Dependencias de `guardrails-core/pom.xml`:

| Dependencia | Scope | Justificación |
|---|---|---|
| `io.modelcontextprotocol.sdk:mcp-core` (BOM 2.0.0) | provided | Tipos del adapter-in (`McpSyncServerExchange`, `McpSchema`, `SyncToolSpecification`). `provided`: el runtime lo aporta la app del usuario/starter. |
| `org.springframework.boot:spring-boot` (BOM 4.1.0) | provided | Única anotación usada: `@ConfigurationProperties` en `infrastructure`. |
| `org.junit.jupiter:junit-jupiter` (BOM 6.1.2) | test | Framework de tests (ARCHITECTURE.md §8). |
| `org.mockito:mockito-core:5.23.0` | test | Mock de `McpSyncServerExchange` y dobles del delegate en tests del adapter-in. |

Ninguna otra. `code-reviewer` debe rechazar cualquier import que requiera algo fuera de esta lista.

## 8. Diagrama del hexágono

```
            MCP Java SDK (tool call)
                     │
        ┌────────────▼────────────────┐
        │ adapter.in.mcp              │
        │ GuardedToolCallHandler      │  decora BiFunction<Exchange,Request,Result>
        │ GuardrailToolDecorator      │
        └────────────┬────────────────┘
                     │ EvaluateToolInvocationUseCase (port.in)
        ┌────────────▼────────────────┐
        │ application                 │
        │ GuardrailChain              │──── Guardrail (port.out, SPI) ◄─── guardrails-audit/
        └────────────┬────────────────┘                                    authz/injection/ratelimit
                     │
        ┌────────────▼────────────────┐
        │ domain                      │
        │ ToolInvocationContext,      │
        │ GuardrailDecision (sealed), │
        │ ChainVerdict, combinación   │
        └─────────────────────────────┘
```

## 9. Decisiones de diseño

1. **Sin short-circuit en la cadena**: todos los guardrails se evalúan siempre. Simplifica la
   trazabilidad y permite que audit vea todas las decisiones. Coste despreciable (evaluaciones
   in-process). Si ratelimit necesitara side-effects solo-si-permitido, se resolverá en ese módulo.
2. **Fail-closed**: excepción inesperada en un guardrail ⇒ `Deny`, nunca `Allow` silencioso.
3. **`Escalate` ⇒ no ejecutar** en el interceptor: sin un humano en el loop definido todavía, la
   interpretación segura es bloquear informando. Los módulos futuros pueden refinarlo.
4. **`mcp-core` y `spring-boot` en scope `provided`**: core es una librería; imponer el runtime
   concreto es responsabilidad del starter y de la app final.
5. **Este spec incluye también el esqueleto del parent pom** (packaging=pom con los BOMs y
   plugins de calidad de §7), porque core es el primer módulo y sin parent no compila nada.
   `domain-builder` crea parent pom + módulo core con su pom mínimo.

---
Estado: **PENDIENTE de aprobación por code-reviewer al final del ciclo del módulo.**
