# Spec — `guardrails-core-decorator-wiring` (extensión aditiva de core)

> Extensión de `guardrails-core` bajo ARCHITECTURE.md §5.1. Se especifica y construye **antes**
> que la integración del starter, que es su único consumidor, en la misma rama.

## 1. Problema y alcance

`GuardrailToolDecorator` es el punto por el que el starter envuelve cada tool MCP. Tiene dos
sobrecargas de `decorate(...)`: una de 4 argumentos (solo cadena de entrada) y otra de 5 (entrada
y salida). Ninguna acepta un `EscalationResolver`, aunque el constructor de 6 argumentos de
`GuardedToolCallHandler` sí lo admite desde la extensión `guardrails-core-escalation-spi`.

Consecuencia verificada: **`guardrails-approval-gate` no se puede cablear**. Su `ApprovalGate`
implementa `EscalationResolver`, pero el decorador no tiene por dónde recibirlo, así que el bean
existiría en el contexto y nunca sería invocado — un módulo cargado que no hace nada, que es
exactamente lo que ARCHITECTURE.md §5.2 prohíbe dejar en silencio.

**No-goals.** No cambia qué hace el handler con un `Escalate`: eso ya está en el spec de
`guardrails-core-escalation-spi`. No añade properties. No decide cómo el starter construye el
resolver: eso es del spec de integración.

## 2. Cumplimiento de §5.1

| Condición | Cómo se cumple |
|---|---|
| **Solo aditivo** | Una sobrecarga nueva de un método estático. Las dos existentes quedan intactas y siguen delegando en los mismos constructores. |
| **Neutro sin uso** | Quien llame a las sobrecargas de 4 o 5 argumentos obtiene exactamente el mismo comportamiento. La nueva solo se alcanza pasando un resolver. |
| **Consumidor real** | La integración del starter, especificada a la vez y en la misma rama. |
| **Spec propio** | Este documento; pasa por los agentes antes que su consumidor. |
| **No es breaking** | Los 11 módulos compilan y se comportan igual. |

## 3. Modelo de dominio

Ninguno. No hay tipos nuevos.

## 4. Puertos

Ninguno nuevo. `EscalationResolver` ya existe en `core.application.port.out`.

## 5. Cambio en `GuardrailToolDecorator` (único archivo modificado)

Tercera sobrecarga, con el resolver como último parámetro:

```java
public static McpServerFeatures.SyncToolSpecification decorate(
    McpServerFeatures.SyncToolSpecification specification,
    EvaluateToolInvocationUseCase useCase,
    EvaluateToolResultUseCase resultUseCase,
    AgentIdResolver agentIdResolver,
    Clock clock,
    EscalationResolver escalationResolver) { ... }
```

Delega en el constructor de 6 argumentos de `GuardedToolCallHandler`, que ya trata `null` como
«sin resolver registrado» y devuelve el error histórico. El orden de parámetros repite el de las
otras dos sobrecargas y añade el nuevo al final, para que la progresión 4 → 5 → 6 se lea como lo
que es: cada una añade una capacidad.

**No se añade una sobrecarga de 5 argumentos con resolver pero sin `resultUseCase`.** Sería
ambigua para el compilador frente a la existente y, sobre todo, no responde a ningún caso real:
un despliegue que quiere aprobación humana no tiene motivo para renunciar a la cadena de salida.

## 6. Configuración Spring Boot

Ninguna.

## 7. Dependencias Maven

Ninguna nueva.

## 8. Diagrama

```
  decorate(spec, useCase, agentIdResolver, clock)                        → entrada
  decorate(spec, useCase, resultUseCase, agentIdResolver, clock)         → entrada + salida
  decorate(spec, useCase, resultUseCase, agentIdResolver, clock, resolver) → + escalación   ← nueva
                                     │
                                     ▼
                      new GuardedToolCallHandler(..., escalationResolver)
```

## 9. Decisiones de diseño

1. **Una sobrecarga nueva en vez de cambiar la de 5 argumentos.** Cambiar la existente sería
   breaking para cualquiera que la llame, y §5.1 lo prohíbe. Tres sobrecargas es el precio de
   mantener compatible lo publicado.

2. **El resolver va al final y admite `null`.** El handler ya define `null` como «comportamiento
   histórico», así que el decorador no necesita una regla propia: pasarlo tal cual mantiene una
   sola definición de qué significa no tener resolver, en lugar de dos que puedan divergir.
