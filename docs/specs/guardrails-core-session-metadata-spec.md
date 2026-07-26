# Spec — `guardrails-core-session-metadata` (extensión aditiva de core)

> Extensión de `guardrails-core` bajo ARCHITECTURE.md §5.1. Se especifica y construye **antes**
> que `guardrails-trifecta-correlator`, que es su único consumidor, en la misma rama.

## 1. Problema y alcance

`ToolInvocationContext` tiene un campo `metadata` desde el primer día, y
`GuardedToolCallHandler` lo rellena siempre con `Map.of()`. Nadie lo lee y nadie lo escribe.

`guardrails-trifecta-correlator` (módulo 12) necesita saber **en qué sesión MCP** ocurre una
invocación, porque la trifecta se define sobre una sesión: tres capacidades que confluyen en el
mismo hilo de trabajo. Lo único identificable hoy desde un `Guardrail` es el `agentId`, y
verificado contra la demo real eso **no sirve**:

```
tres conexiones distintas del mismo cliente
  sessionId=6a78fc9c-…  clientInfo.name=copilot
  sessionId=92d83d23-…  clientInfo.name=copilot
  sessionId=57bc8401-…  clientInfo.name=copilot
```

El `agentId` por defecto es `clientInfo.name()` — el nombre del producto cliente, no una persona
ni una conversación. Correlacionar sobre él mezclaría a todos los usuarios de Copilot: uno lee un
expediente, otro abre una URL, un tercero manda un correo, y el correlador cerraría el triángulo
con tres personas que no tienen nada que ver.

El dato correcto ya existe: `McpSyncServerExchange.sessionId()`, verificado en `mcp-core 2.0.0` y
comprobado en ejecución — devuelve el mismo valor que la cabecera `Mcp-Session-Id` del transporte,
y es distinto por conexión. El handler lo tiene en la mano y lo descarta.

**No-goals.** No define qué es una sesión para el correlador ni cómo se caduca: eso es del módulo
12. No añade properties. No cambia la semántica de nada existente.

## 2. Cumplimiento de §5.1

| Condición | Cómo se cumple |
|---|---|
| **Solo aditivo** | Una clave nueva en un `Map` que hoy va vacío. Ninguna firma cambia, ningún tipo nuevo. |
| **Neutro sin uso** | `metadata` no lo lee nadie en el proyecto (`grep` sobre los 11 módulos: 0 lecturas fuera del propio record). Un consumidor que lo ignore se comporta igual. |
| **Consumidor real** | `guardrails-trifecta-correlator`, especificado a la vez y en la misma rama. |
| **Spec propio** | Este documento; pasa por los 5 agentes antes del módulo 12. |
| **No es breaking** | Los 11 módulos compilan y se comportan igual. |

## 3. Modelo de dominio

Ninguno. No hay tipos nuevos.

## 4. Puertos

Ninguno nuevo.

## 5. Cambio en `GuardedToolCallHandler` (único archivo modificado)

```java
public static final String SESSION_ID = "mcp.sessionId";
```

En `toContext(...)`, el `Map.of()` pasa a llevar la sesión cuando el exchange la aporta:

```java
private static Map<String, Object> metadataOf(McpSyncServerExchange exchange) {
  if (exchange == null) {
    return Map.of();
  }
  String sessionId = exchange.sessionId();
  return sessionId == null || sessionId.isBlank() ? Map.of() : Map.of(SESSION_ID, sessionId);
}
```

Tres propiedades que el código debe garantizar:

- **La ausencia es un `Map` sin la clave, no una clave con valor vacío.** Un consumidor que
  encuentre `mcp.sessionId` puede confiar en que hay una sesión; si no está, sabe que no la hay.
  Una cadena vacía obligaría a cada consumidor a repetir la comprobación, y alguno se la saltaría.
- **Un `sessionId()` que lance no tumba la invocación.** El transporte lo implementa, no el
  proyecto; una excepción ahí se trata como ausencia de sesión, igual que un `null`.
- **La constante es pública.** Es el contrato: sin ella cada consumidor escribiría el literal
  `"mcp.sessionId"` por su cuenta y una errata sería un fallo silencioso.

## 6. Configuración Spring Boot

Ninguna.

## 7. Dependencias Maven

Ninguna nueva. `mcp-core` ya es dependencia de `guardrails-core`.

## 8. Diagrama

```
  McpSyncServerExchange                 ToolInvocationContext
  ├─ getClientInfo() ──► AgentIdResolver ──► agentId
  └─ sessionId() ──────────────────────────► metadata["mcp.sessionId"]   ← lo que añade esta extensión
                                             (ausente si el transporte no aporta sesión)
```

## 9. Decisiones de diseño

1. **Se rellena `metadata` en vez de añadir un campo a `ToolInvocationContext`.** Un campo nuevo
   en el record cambiaría su constructor canónico y rompería a todo el que lo construya —los tests
   de los 11 módulos, entre otros—, y eso ya no sería aditivo. `metadata` existe exactamente para
   esto y hasta hoy estaba muerto.

2. **La clave es `mcp.sessionId`, con prefijo.** `metadata` es un espacio compartido: prefijar por
   origen deja sitio a lo que venga sin colisionar, y hace evidente al leerlo de dónde sale el
   dato.

3. **Core no interpreta la sesión, solo la transporta.** Qué es una sesión, cuánto dura y qué pasa
   si falta son decisiones del consumidor. Core no puede saber si para un guardrail concreto una
   sesión de transporte es la unidad correcta; meter esa opinión aquí obligaría a todos los
   consumidores futuros a heredarla.
