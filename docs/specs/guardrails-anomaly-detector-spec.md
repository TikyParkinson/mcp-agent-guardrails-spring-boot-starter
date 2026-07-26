# Spec — guardrails-anomaly-detector

> Módulo 10 según ARCHITECTURE.md §6. Autor: spec-architect.
> Paquete raíz: `io.github.tikyparkinson.mcpguardrails.anomaly`.
> Prerequisito cumplido: `guardrails-egress-control-DONE.md` aprobado.

## 1. Problema y alcance

Un agente comprometido o atascado no se delata en una invocación concreta, sino en el **patrón**:
repite la misma llamada decenas de veces, o de golpe empieza a tocar herramientas que nunca había
usado. Ninguno de los guardrails anteriores ve eso, porque todos deciden mirando una única
invocación. `guardrails-anomaly-detector` mantiene una ventana de historial por agente y aplica
dos heurísticas deterministas y explicables —bucle de repetición y ráfaga de herramientas
nuevas—. Cuando una salta, devuelve `Escalate`: la anomalía es una señal, no una prueba, y quien
decide es un humano.

**No-goals:**

- **No usa aprendizaje automático ni modelos estadísticos.** Dos umbrales configurables y un
  motivo que un operador puede leer y verificar a mano. Un `Escalate` que nadie sabe explicar no
  se puede accionar.
- **No devuelve `Deny` nunca**, ni siquiera con la anomalía más clara: un falso positivo que
  bloquea al agente en silencio es peor que uno que pide confirmación. Tampoco devuelve `Allow`
  silencioso ante una anomalía detectada — solo `Allow` cuando ninguna heurística salta.
- No importa `guardrails-audit` ni `guardrails-ratelimit` (ARCHITECTURE.md §5). Define su propio
  puerto de lectura; ver §3.2 y la Decisión de diseño 1.
- No correlaciona señales de otros guardrails dentro de la misma invocación: eso es el módulo 12
  (`trifecta-correlator`), que lo hará leyendo el `ChainVerdict`.
- No detecta anomalías entre agentes distintos ni entre sesiones: la unidad de análisis es
  `agentId`.
- No persiste nada por sí mismo. El adaptador por defecto es una ventana en memoria acotada ⇒
  **sin Testcontainers** (ARCHITECTURE.md §8).
- Sin autoconfiguración aquí: el cableado va en `spring-boot-starter`, en su propia rama.

## 2. Modelo de dominio

Paquete `io.github.tikyparkinson.mcpguardrails.anomaly.domain`. JDK puro, autocontenido.

```java
/**
 * Huella de los argumentos de una invocación. NUNCA contiene los argumentos: es un SHA-256 sobre
 * una forma canónica con las claves de cada Map ordenadas explícitamente (el orden de iteración
 * de un Map inmutable no es estable entre JVMs).
 * Invariantes: value no blank.
 * Factories: ArgumentsFingerprint.of(Map<String,Object> arguments)
 *            ArgumentsFingerprint.unknown()  -> constante UNKNOWN
 * UNKNOWN representa una fuente de historial que no puede aportar la huella (ver Decisión 3);
 * la heurística de repetición ignora los registros marcados así.
 */
record ArgumentsFingerprint(String value) {
  static final ArgumentsFingerprint UNKNOWN = new ArgumentsFingerprint("unknown");
  boolean isKnown() { return !equals(UNKNOWN); }
}

/**
 * Una invocación tal como la ve este módulo. Sin argumentos, solo su huella.
 * Invariantes: agentId y toolName no blank; fingerprint y occurredAt no null.
 */
record InvocationRecord(
    String agentId, String toolName, ArgumentsFingerprint fingerprint, Instant occurredAt) {}

/**
 * Lo que el detector necesita saber de un agente para decidir, en una sola lectura.
 * withinWindow:            invocaciones del agente dentro de la ventana, la actual incluida.
 * toolsBeforeWindow:       herramientas que el agente usó ANTES de la ventana — la línea base.
 * invocationsBeforeWindow: cuántas invocaciones componen esa línea base (para el arranque en
 *                          frío: sin historial suficiente, la heurística de base no opina).
 * Invariantes: colecciones inmutables y no null; invocationsBeforeWindow >= 0.
 *
 * Nota de implementación para el adaptador: los dos últimos campos NO pueden derivarse solo de
 * los registros retenidos, o el recorte por tamaño cegaría a H2 justo con el agente que más
 * interesa detectar (ver §5.2 y Decisión de diseño 4).
 */
record AgentHistory(
    List<InvocationRecord> withinWindow,
    Set<String> toolsBeforeWindow,
    long invocationsBeforeWindow) {}

/** Qué heurística disparó. El nombre entra en el motivo, así que es parte del contrato. */
enum AnomalyKind { REPETITION_LOOP, NOVEL_TOOL_BURST }

/**
 * Señal detectada, con los números que la justifican para que el operador pueda comprobarla.
 * observed: valor medido (repeticiones, o herramientas nuevas).
 * threshold: umbral configurado que se superó.
 * subject: qué se repitió (nombre de tool) o las tools nuevas, separadas por ", ".
 * Invariantes: kind no null; subject no blank; observed >= threshold; threshold >= 1.
 */
record AnomalySignal(AnomalyKind kind, int observed, int threshold, String subject) {
  String describe() { ... }  // "repetition-loop: 7 identical calls to 'search' (threshold 5)"
}

/** Resultado del análisis. Modela la ausencia con un tipo, no con null ni con lista vacía. */
sealed interface AnomalyVerdict permits NoAnomaly, AnomalyDetected {}
record NoAnomaly() implements AnomalyVerdict {}

/** Invariante: signals inmutable y NO vacía (un veredicto de anomalía sin señal no existe). */
record AnomalyDetected(List<AnomalySignal> signals) implements AnomalyVerdict {}

/**
 * Umbrales del análisis, validados una vez en construcción en vez de en cada invocación.
 * Invariantes: window positiva; repeatThreshold >= 2 (una repetición necesita al menos dos
 * llamadas); novelToolThreshold >= 1; baselineMinInvocations >= 0.
 */
record AnomalyPolicy(
    Duration window, int repeatThreshold, int novelToolThreshold, long baselineMinInvocations) {}

/**
 * Las dos heurísticas, puras. Clase final AnomalyAnalyzer:
 *   static AnomalyVerdict analyze(AgentHistory history, AnomalyPolicy policy)
 *
 * H1 — REPETITION_LOOP: agrupa withinWindow por (toolName, fingerprint) ignorando los registros
 *      con fingerprint UNKNOWN; si algún grupo alcanza repeatThreshold, señal con observed =
 *      tamaño del grupo mayor y subject = su toolName.
 *
 * H2 — NOVEL_TOOL_BURST: solo se evalúa si invocationsBeforeWindow >= baselineMinInvocations
 *      (arranque en frío: sin línea base, todo es nuevo y la heurística mentiría). Cuenta las
 *      tools distintas de withinWindow que NO están en toolsBeforeWindow; si alcanzan
 *      novelToolThreshold, señal con subject = esas tools ordenadas alfabéticamente y unidas
 *      por ", " (orden estable ⇒ motivo reproducible).
 *
 * Las dos son independientes: pueden dispararse a la vez y ambas señales viajan en el veredicto.
 */
final class AnomalyAnalyzer { ... }

/**
 * Forma canónica de los argumentos para la huella. Clase final CanonicalArguments:
 *   static String of(Map<String,Object> arguments)
 * Ordena las claves de cada Map, recorre List por posición, y para el resto usa
 * String.valueOf(...). Profundidad máxima 8 (más allá se emite "…", igual que hace el aplanador
 * de credential-leak-guard) para acotar entradas anidadas artificialmente.
 */
final class CanonicalArguments { ... }
```

## 3. Puertos (contratos de application)

### 3.1 `DetectAnomalyUseCase` — puerto de entrada

- Capa: `application.port.in`. Lo invoca `AnomalyGuardrail` (adapter-in).
- Lo implementa: `DetectAnomalyService`.

```java
public interface DetectAnomalyUseCase {
  /**
   * Registra la invocación en el historial y analiza al agente con la ventana vigente.
   * Nunca null.
   */
  AnomalyVerdict inspect(String agentId, String toolName, Map<String, Object> arguments,
                         Instant occurredAt);
}
```

### 3.2 `InvocationHistoryPort` — puerto de salida (lo plugable)

- Capa: `application.port.out`.
- Lo implementa: `InMemoryInvocationHistoryAdapter` (por defecto).

```java
public interface InvocationHistoryPort {

  /** Añade la invocación al historial. Debe ser seguro bajo concurrencia. */
  void record(InvocationRecord record);

  /**
   * Historial del agente partido por {@code windowStart}: lo ocurrido desde ese instante
   * (inclusive) y la línea base anterior. Nunca null.
   */
  AgentHistory historyOf(String agentId, Instant windowStart);
}
```

**Prerequisito que este spec resuelve explícitamente.** El usuario pidió consumir el histórico
que ya exponen `guardrails-audit` y `guardrails-ratelimit`. La situación real, verificada en el
código:

| Fuente | Qué expone | ¿Utilizable aquí? |
|---|---|---|
| `guardrails-core` `port.out` | `Guardrail`, `ResultGuardrail` | No hay puerto de histórico |
| `guardrails-ratelimit` | `RateLimitStorePort.incrementAndCount(...)` | **No**: es un contador; no dice qué se invocó |
| `guardrails-audit` | `AuditLogStorePort.findRecent(int)` | Sí es lectura, pero importarlo viola §5 |
| `spring-boot-starter` | depende de todos los módulos | **Sí**: es donde cabe el puente |

Por eso el puerto lo define **este** módulo, en su propio lenguaje, y el puente sobre
`guardrails-audit` es un adaptador que vive en la capa de cableado (`spring-boot-starter`), que
legítimamente depende de ambos. No hace falta extender `guardrails-core` (§5.1). Ver Decisión 1.

## 4. Caso de uso — `DetectAnomalyService`

Constructor: `(InvocationHistoryPort historyPort, AnomalyPolicy policy)`.

1. Valida `agentId`, `toolName`, `arguments` y `occurredAt` no null.
2. `fingerprint = ArgumentsFingerprint.of(arguments)`.
3. `historyPort.record(new InvocationRecord(agentId, toolName, fingerprint, occurredAt))` — la
   invocación en curso **forma parte** de la ventana que se analiza: un bucle se detecta en la
   llamada que alcanza el umbral, no en la siguiente.
4. `windowStart = occurredAt.minus(policy.window())`.
5. `history = historyPort.historyOf(agentId, windowStart)`.
6. Devuelve `AnomalyAnalyzer.analyze(history, policy)`.

La traducción a `GuardrailDecision` es del adaptador.

## 5. Adaptadores esperados

### 5.1 Adapter-in: `AnomalyGuardrail implements Guardrail`

Paquete `anomaly.adapter.in.chain`.

- `name()` = `"anomaly-detector"`; `order()` = `80` — después de `egress-control` (70) y antes de
  `ratelimit` (100). La cadena queda: audit (−100), tool-integrity (−50), authz (0),
  injection-guard (50), credential-leak (60), egress-control (70), **anomaly-detector (80)**,
  ratelimit (100).
- `evaluate(context)`:
  1. `verdict = useCase.inspect(agentId, toolName, arguments, occurredAt)` — los cuatro salen del
     `ToolInvocationContext`, incluido `occurredAt` (no se consulta el reloj aquí: el contexto ya
     trae el instante de la invocación, y usar otro haría los tests dependientes del reloj real).
  2. `NoAnomaly` ⇒ `Allow`.
  3. `AnomalyDetected(signals)` ⇒ **`Escalate`** con motivo
     `"anomalous agent behaviour (" + señales unidas por "; " + ")"`, usando `describe()` de cada
     señal. Nunca `Deny`.

### 5.2 Adapter-out: `InMemoryInvocationHistoryAdapter`

Paquete `anomaly.adapter.out.history`.

Guarda por agente **dos cosas con retenciones distintas**, porque tienen coste y vida útil
distintos:

| Dato | Sirve a | Retención |
|---|---|---|
| `Deque<InvocationRecord>` (pesado: huella por invocación) | H1 | tiempo **y** tamaño |
| Resumen de línea base: `foldedCount` + `foldedTools` | H2 | solo tiempo |

- Estructura: `ConcurrentHashMap<String, AgentWindow>` por agente, con la escritura sobre cada
  `AgentWindow` sincronizada (un `Deque` no es seguro bajo concurrencia por sí mismo).
- Al superar `maxRecordsPerAgent` se descartan los registros **más antiguos**, pero no se
  olvidan: cada uno **se pliega** en el resumen (`foldedCount++`, `foldedTools.add(toolName)`).
  Plegar en vez de borrar es lo que impide que el recorte por tamaño ciegue a H2.
- `retention` descarta por tiempo tanto los registros como el resumen del agente.
- `historyOf(agentId, windowStart)` compone:
  - `withinWindow` = registros retenidos con `occurredAt >= windowStart`;
  - `toolsBeforeWindow` = `foldedTools` ∪ tools de los registros anteriores a la ventana;
  - `invocationsBeforeWindow` = `foldedCount` + nº de registros anteriores a la ventana.
- Caso límite documentado: si la ventana contiene **más** invocaciones que
  `maxRecordsPerAgent`, parte de lo plegado pertenece en realidad a la ventana y se contabiliza
  como línea base. El sesgo es hacia **detectar**, no hacia callar: en ese escenario —un agente
  enumerando cientos de herramientas por minuto— H2 dispara, que es lo que se busca.
- El resumen es barato: su tamaño lo acota el número de herramientas del servidor (decenas), no
  el tráfico.

### 5.3 Nota para el cableado del starter (fuera de esta rama)

El registro de auditoría **no guarda los argumentos ni su huella** —por diseño, riesgo de PII—,
así que un puente que solo lea `AuditLogStorePort` entregaría todos los registros con
`ArgumentsFingerprint.UNKNOWN` y **H1 quedaría inactiva**. Un operador que lo cablee creyendo que
tiene detección de bucles y no la tenga es exactamente el tipo de falsa sensación de seguridad
que este proyecto evita.

Por eso lo recomendado allí **no es un puente, es un adaptador híbrido**: mantener la ventana en
memoria para H1 —donde las huellas sí existen— y leer el registro de auditoría solo para la línea
base de H2, que gana profundidad histórica y sobrevive a los reinicios. Cada heurística se
alimenta de la fuente que puede sostenerla.

Si aun así alguien implementa el puente puro, debe **avisar al arrancar** de que H1 queda
desactivada, en lugar de degradarse en silencio. Y `record(...)` sería un no-op en él: el
guardrail de auditoría ya escribe.

## 6. Configuración Spring Boot

Prefijo `mcp.guardrails.anomaly`. Record `GuardrailsAnomalyProperties` con `@ConstructorBinding` y
`@DefaultValue` en cada parámetro.

| Propiedad | Tipo | Default | Significado |
|---|---|---|---|
| `enabled` | `boolean` | `true` | Registra el guardrail. |
| `window` | `Duration` | `PT1M` | Ventana de análisis. |
| `repeat-threshold` | `int` | `5` | Invocaciones idénticas (misma tool y mismos argumentos) que disparan H1. |
| `novel-tool-threshold` | `int` | `3` | Herramientas nunca vistas antes que disparan H2. |
| `baseline-min-invocations` | `long` | `20` | Invocaciones previas necesarias para que H2 se active (arranque en frío). |
| `retention` | `Duration` | `PT30M` | Cuánto conserva el adaptador por defecto, registros y resumen. Debe ser ≥ `window`. |
| `max-records-per-agent` | `int` | `500` | Tope de registros **detallados** por agente. Al superarlo, los más antiguos se pliegan en el resumen de línea base en vez de perderse (§5.2). |

```yaml
mcp:
  guardrails:
    anomaly:
      window: PT1M
      repeat-threshold: 5
      novel-tool-threshold: 3
      baseline-min-invocations: 20
```

## 7. Dependencias Maven propuestas

| Dependencia | Scope | Por qué |
|---|---|---|
| `io.github.tikyparkinson:guardrails-core` | compile | El SPI `Guardrail`, `ToolInvocationContext` y los tipos de decisión que emite el adapter-in. |
| `org.springframework.boot:spring-boot` (BOM 4.1.0) | provided | Única anotación usada: `@ConfigurationProperties` (+ `@ConstructorBinding`/`@DefaultValue`) en `infrastructure`. |
| `org.junit.jupiter:junit-jupiter` (BOM 6.1.2) | test | Tests unitarios. |
| `org.mockito:mockito-core` | test | Doble de `InvocationHistoryPort` en los tests del servicio. |

Ninguna dependencia nueva respecto a lo que el proyecto ya usa. Ningún módulo `guardrails-*`
distinto de core (ARCHITECTURE.md §5). El hash usa `java.security.MessageDigest` del JDK.

## 8. Diagrama del hexágono

```
                      MCP tool call
                            │
                            ▼
        ┌───────────────────────────────────────────┐
        │ adapter/in/chain                           │
        │ AnomalyGuardrail (Guardrail, order 80)     │
        │ Allow | Escalate   (nunca Deny)            │
        └─────────────────────┬─────────────────────┘
                              │
                              ▼
        ┌───────────────────────────────────────────────────────┐
        │ application                                            │
        │  port.in  DetectAnomalyUseCase                         │
        │  usecase  DetectAnomalyService                         │
        │  port.out InvocationHistoryPort  ◄────────────────────┼── implementado por
        └─────────────────────┬─────────────────────────────────┘   InMemoryInvocationHistoryAdapter
                              │                                      (adapter/out/history)
                              ▼                                      …y, desde el starter, por un
        ┌───────────────────────────────────────────────────────┐    puente sobre AuditLogStorePort
        │ domain (JDK puro)                                      │
        │  ArgumentsFingerprint, CanonicalArguments              │
        │  InvocationRecord, AgentHistory, AnomalyPolicy         │
        │  AnomalyKind, AnomalySignal                            │
        │  AnomalyVerdict: NoAnomaly | AnomalyDetected           │
        │  AnomalyAnalyzer  (H1 bucle, H2 ráfaga de tools nuevas)│
        └───────────────────────────────────────────────────────┘
```

## 9. Decisiones de diseño

1. **El puerto de histórico lo define este módulo, no lo toma prestado.** `guardrails-audit` tiene
   una lectura utilizable (`findRecent`), pero §5 prohíbe la dependencia entre guardrails, y
   promover un puerto de histórico a `guardrails-core` sería peor: core es **sin estado**, y
   meterle un almacén contradiría su papel. La solución hexagonal es que el consumidor declare el
   puerto que necesita, en su propio lenguaje (`AgentHistory` responde exactamente la pregunta que
   hacen las dos heurísticas, ni más ni menos), y que el puente hacia otra fuente viva en la capa
   de cableado, que sí puede depender de ambos. `guardrails-ratelimit` queda descartado por un
   motivo distinto y anterior: su puerto es un contador, no registra qué se invocó.

2. **Solo `Escalate`, nunca `Deny`.** Una anomalía es una señal estadística sobre un patrón, no la
   prueba de un ataque: `repeat-threshold: 5` también lo alcanza un reintento legítimo con
   backoff. Bloquear en silencio por una heurística de umbral produce incidencias de soporte
   irreproducibles; escalar deja la decisión donde corresponde. Es la diferencia con
   `egress-control`, donde un destino fuera de la allowlist sí es una violación de una regla
   explícita del operador y por eso deniega. Conviene saber que hoy `Escalate` **también detiene
   la llamada** (`GuardedToolCallHandler` devuelve un error sin ejecutar la tool): la elección no
   es "bloquear o no", sino cómo quedará etiquetada cuando exista `approval-gate`.

   **Se descarta añadir histéresis o cooldown** —"no volver a escalar al mismo agente durante N
   segundos"— aunque evitaría una tormenta de escalados en un agente en bucle: no escalar
   significa **permitir**, y sería justo el `Allow` silencioso ante una anomalía ya detectada que
   este módulo se prohíbe. Deduplicar solicitudes de aprobación es responsabilidad de
   `approval-gate` (módulo 11), que es quien las materializa; el detector solo reporta.

3. **La huella nunca es el argumento, y "no hay huella" es un valor propio.** Guardar argumentos
   para comparar invocaciones reintroduciría el riesgo que `credential-leak-guard` combate, así
   que se compara un SHA-256 de la forma canónica. Y una fuente de historial que no pueda aportar
   la huella —el registro de auditoría, que a propósito no guarda argumentos— marca
   `ArgumentsFingerprint.UNKNOWN`, que H1 ignora. Es preferible a inventar una huella vacía que
   haría parecer idénticas todas las invocaciones y dispararía falsos positivos en masa.

   La consecuencia —H1 inactiva sobre una fuente sin huellas— no se resuelve con un valor
   centinela, sino eligiendo bien la fuente de cada heurística: por eso §5.3 recomienda un
   adaptador **híbrido** (memoria para H1, auditoría para la línea base de H2) en lugar de un
   puente puro, y exige avisar al arrancar si alguien opta por el puente.

4. **H2 no opina hasta tener línea base, y esa línea base sobrevive al recorte.** Sin
   `baseline-min-invocations`, el primer minuto de cualquier agente dispara la heurística: todas
   sus herramientas son "nuevas". Es el problema de arranque en frío, y la única solución honesta
   es callar hasta tener con qué comparar.

   Pero derivar la línea base únicamente de los registros retenidos abría un agujero que
   invalidaba la heurística en el peor caso: un agente enumerando 600 herramientas distintas en
   un minuto, con `max-records-per-agent: 500`, dejaba `invocationsBeforeWindow = 0` ⇒ H2 callada
   por arranque en frío, mientras H1 tampoco disparaba porque todas las huellas son distintas.
   **El agente que más interesa detectar pasaba inadvertido.** Por eso el resumen de línea base
   (§5.2) es un dato aparte que el recorte por tamaño alimenta en vez de destruir.

5. **La invocación en curso entra en la ventana antes de analizar.** Así el bucle se corta en la
   llamada que alcanza el umbral y no una después. El coste es que el guardrail escribe en el
   historial durante `evaluate`, un efecto lateral — el mismo patrón que ya usa `guardrails-audit`
   en este proyecto, y acotado: si el puerto falla, la excepción la convierte en `Deny` la propia
   cadena de core (fail-closed).

6. **`occurredAt` sale del `ToolInvocationContext`, no de un `Clock` propio.** El contexto ya trae
   el instante de la invocación; introducir un reloj aquí desincronizaría la ventana respecto al
   resto de la cadena y obligaría a los tests a inyectar tiempo dos veces.

7. **El adaptador por defecto separa dos formas de dato con dos retenciones.** Los registros
   detallados (con huella) son caros y solo los necesita H1 en la ventana corta: se acotan por
   tiempo **y** por tamaño. El resumen de línea base es barato —lo acota el número de
   herramientas del servidor, no el tráfico— y H2 lo necesita íntegro: se acota solo por tiempo.
   Mezclar ambas cosas en una única cola con un único límite era lo que abría el agujero de la
   Decisión 4.

8. **`CanonicalArguments` se reimplementa en vez de reutilizar `CanonicalForm` de
   `tool-integrity`.** §5 lo impone. Conviene medir bien la deuda antes de invocarla como
   argumento: las tres piezas "parecidas" del proyecto **no son la misma función** —
   `CanonicalForm.render` va de `ToolDefinition` a `String`, `ValueFlattener.flatten` va de `Map`
   a `List<FlattenedValue>` (otra forma distinta), y `CanonicalArguments.of` va de `Map` a
   `String`—. Solo la primera y la tercera comparten algoritmo, así que esta es la **segunda**
   duplicación de "Map → cadena determinista", no la cuarta de la misma cosa. Dos consumidores
   son poco para justificar tocar `guardrails-core` otra vez: §5.1 pide un consumidor real y
   concreto, no una tendencia. Si aparece un tercero, entonces sí procede promoverlo como
   extensión aditiva, nunca importar el módulo vecino.
